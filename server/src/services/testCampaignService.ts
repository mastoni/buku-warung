import Database from 'better-sqlite3';
import { getDatabase } from '../db/database.js';
import {
  encryptTestRegistrationDetails,
  decryptTestRegistrationDetails,
  hashEmail,
  timingSafeCompare
} from '../utils/crypto.js';
import { isValidLeadToken } from '../utils/attribution.js';

export const TEST_CAMPAIGN_ID = 'BUKU_WARUNG_TEST_BATCH_1';
export const TEST_CAMPAIGN_CAPACITY = 50;

export type TestCampaignStatus = 'OPEN' | 'FULL';

export interface TestCampaignState {
  campaignId: string;
  status: TestCampaignStatus;
  capacity: number;
  registered: number;
  remaining: number;
}

export interface TestRegistrationDetails {
  name: string;
  whatsapp: string;
  googlePlayEmail: string;
  businessType: string;
  dailyTransactions: string;
  androidDevice: string;
  consent: boolean;
}

export type TestRegistrationOutcome =
  | { kind: 'REGISTERED'; slotNumber: number; state: TestCampaignState }
  | { kind: 'DUPLICATE'; slotNumber: number; state: TestCampaignState }
  | { kind: 'FULL'; state: TestCampaignState };

/**
 * Closed Testing workflow. Registration is NOT a Google Play tester entry - moving a tester
 * through READY_FOR_PLAY -> INVITED -> OPTED_IN is a manual admin action in the Play Console,
 * tracked here. Google Play itself is not integrated and this system does not read Play state.
 */
export const TESTER_STATUSES = [
  'REGISTERED',
  'REVIEWED',
  'READY_FOR_PLAY',
  'INVITED',
  'OPTED_IN',
  'TESTING',
  'FEEDBACK_RECEIVED',
  'COMPLETED',
  'REJECTED'
] as const;

export type TesterStatus = (typeof TESTER_STATUSES)[number];

/** Statuses that no longer hold a campaign slot, so a replacement can be admitted. */
const SLOT_RELEASING_STATUSES: TesterStatus[] = ['REJECTED'];

export function isTesterStatus(value: string): value is TesterStatus {
  return (TESTER_STATUSES as readonly string[]).includes(value);
}

function countRegistered(db: Database.Database, campaignId: string): number {
  // REJECTED frees the slot so a replacement business can be admitted. Capacity remains
  // authoritative here and nowhere else - Tester Management only reads this.
  const row = db
    .prepare(
      `SELECT COUNT(*) AS c FROM test_campaign_registrations
        WHERE campaign_id = ? AND status NOT IN (${SLOT_RELEASING_STATUSES.map(() => '?').join(',')})`
    )
    .get(campaignId, ...SLOT_RELEASING_STATUSES) as { c: number };
  return row.c;
}

function buildState(db: Database.Database, campaignId: string, capacity: number): TestCampaignState {
  const registered = countRegistered(db, campaignId);
  const remaining = Math.max(0, capacity - registered);
  return {
    campaignId,
    status: remaining === 0 ? 'FULL' : 'OPEN',
    capacity,
    registered,
    remaining
  };
}

/**
 * Public, unauthenticated campaign availability. This is the ONLY source the landing page is
 * allowed to render "X dari 50 slot" from - there is deliberately no client-side counter.
 */
export function getTestCampaignState(customDb?: Database.Database): TestCampaignState {
  const db = customDb ?? getDatabase();
  return buildState(db, TEST_CAMPAIGN_ID, TEST_CAMPAIGN_CAPACITY);
}

function findByLeadToken(
  db: Database.Database,
  campaignId: string,
  leadToken: string
): { slot_number: number } | undefined {
  return db
    .prepare('SELECT slot_number FROM test_campaign_registrations WHERE campaign_id = ? AND lead_token = ?')
    .get(campaignId, leadToken) as { slot_number: number } | undefined;
}

function findByContactHash(
  db: Database.Database,
  campaignId: string,
  contactHash: string
): { slot_number: number; lead_token: string } | undefined {
  return db
    .prepare('SELECT slot_number, lead_token FROM test_campaign_registrations WHERE campaign_id = ? AND contact_hash = ?')
    .get(campaignId, contactHash) as { slot_number: number; lead_token: string } | undefined;
}

/**
 * Atomically consumes one slot.
 *
 * Correctness rests on three things working together, not on a pre-check:
 *  1. SQLite runs the whole body in a single IMMEDIATE transaction, so the count-then-insert
 *     pair cannot interleave with another writer.
 *  2. UNIQUE(campaign_id, slot_number) rejects a slot that was taken concurrently.
 *  3. UNIQUE(campaign_id, lead_token) and the contact lookup stop one lead from ever
 *     consuming a second slot.
 *
 * A lead is identified by leadToken (the existing attribution identifier) or, failing that,
 * by a peppered HMAC of the Google Play email. If either already holds a slot the request is
 * reported as DUPLICATE and consumes nothing.
 */
export function registerTestCampaign(
  details: TestRegistrationDetails,
  leadToken: string | undefined,
  customDb?: Database.Database
): TestRegistrationOutcome {
  const db = customDb ?? getDatabase();
  const campaignId = TEST_CAMPAIGN_ID;
  const capacity = TEST_CAMPAIGN_CAPACITY;
  const contactHash = hashEmail(details.googlePlayEmail);
  const token = leadToken && isValidLeadToken(leadToken) ? leadToken : `LW-NOCONTACT-${contactHash.slice(0, 12)}`;

  const run = db.transaction((): TestRegistrationOutcome => {
    const existing =
      findByLeadToken(db, campaignId, token) ?? findByContactHash(db, campaignId, contactHash);

    if (existing) {
      return { kind: 'DUPLICATE', slotNumber: existing.slot_number, state: buildState(db, campaignId, capacity) };
    }

    if (countRegistered(db, campaignId) >= capacity) {
      return { kind: 'FULL', state: buildState(db, campaignId, capacity) };
    }

    // The lowest free slot keeps numbering human-meaningful and is still resolved inside the
    // transaction, so the UNIQUE index remains the final arbiter rather than the SELECT.
    const nextSlot =
      (db
        .prepare(
          'SELECT COALESCE(MAX(slot_number), 0) + 1 AS next FROM test_campaign_registrations WHERE campaign_id = ?'
        )
        .get(campaignId) as { next: number }).next;

    db.prepare(
      `INSERT INTO test_campaign_registrations
         (campaign_id, slot_number, lead_token, contact_hash, details_encrypted, status, created_at)
       VALUES (?, ?, ?, ?, ?, 'REGISTERED', ?)`
    ).run(campaignId, nextSlot, token, contactHash, encryptTestRegistrationDetails(details), Date.now());

    return { kind: 'REGISTERED', slotNumber: nextSlot, state: buildState(db, campaignId, capacity) };
  });

  try {
    return run.immediate();
  } catch (err) {
    // A UNIQUE violation can only mean a concurrent writer won the same slot or the same
    // lead. Re-read and report the truth rather than surfacing a 500.
    if (isUniqueViolation(err)) {
      const state = buildState(db, campaignId, capacity);
      const existing = findByLeadToken(db, campaignId, token) ?? findByContactHash(db, campaignId, contactHash);
      if (existing) {
        return { kind: 'DUPLICATE', slotNumber: existing.slot_number, state };
      }
      if (state.status === 'FULL') {
        return { kind: 'FULL', state };
      }
    }
    throw err;
  }
}

function isUniqueViolation(err: unknown): boolean {
  return typeof err === 'object' && err !== null && 'code' in err && (err as { code?: string }).code === 'SQLITE_CONSTRAINT_UNIQUE';
}

/**
 * Constant-time comparison helper kept next to the campaign service so the waiting-list and
 * contact-lookup paths cannot accidentally degrade to === comparisons.
 */
export function contactHashMatches(a: string, b: string): boolean {
  return timingSafeCompare(a, b);
}

/* ============================================================================
 * TESTER MANAGEMENT (admin only)
 *
 * Registration PII is decrypted here only, on the admin path, using the existing domain-separated
 * AES-256-GCM key. Nothing in this section is reachable from a public route.
 * ========================================================================== */

export interface TesterSummary {
  id: number;
  campaignId: string;
  slotNumber: number;
  name: string;
  googlePlayEmail: string;
  whatsapp: string;
  businessType: string;
  dailyTransactions: string;
  androidDevice: string;
  status: TesterStatus;
  registeredAt: number;
  updatedAt: number | null;
}

export interface TesterDetail extends TesterSummary {
  consent: boolean;
  leadToken: string;
  adminNotes: string | null;
  playOptInUrl: string | null;
  playInvitedAt: number | null;
  playOptedInAt: number | null;
  history: Array<{
    fromStatus: string | null;
    toStatus: string;
    note: string | null;
    actor: string;
    createdAt: number;
  }>;
}

export interface TesterStatusBreakdown {
  status: TesterStatus;
  count: number;
}

function decryptDetails(row: { details_encrypted: string | null }): Partial<TestRegistrationDetails> {
  const parsed = decryptTestRegistrationDetails(row.details_encrypted) as Partial<TestRegistrationDetails> | null;
  return parsed ?? {};
}

function toSummary(db: Database.Database, row: any): TesterSummary {
  const details = decryptDetails(row);
  return {
    id: row.id,
    campaignId: row.campaign_id,
    slotNumber: row.slot_number,
    name: String(details.name ?? ''),
    googlePlayEmail: String(details.googlePlayEmail ?? ''),
    whatsapp: String(details.whatsapp ?? ''),
    businessType: String(details.businessType ?? ''),
    dailyTransactions: String(details.dailyTransactions ?? ''),
    androidDevice: String(details.androidDevice ?? ''),
    status: row.status as TesterStatus,
    registeredAt: row.created_at,
    updatedAt: row.updated_at ?? null
  };
}

export interface ListTestersFilter {
  status?: TesterStatus;
  search?: string;
}

export function listTesters(filter: ListTestersFilter = {}, customDb?: Database.Database): {
  testers: TesterSummary[];
  total: number;
} {
  const db = customDb ?? getDatabase();
  const campaignId = TEST_CAMPAIGN_ID;

  // Decryption happens in JS, so filtering on name/email/WhatsApp has to happen after
  // decryption. Bounded by the 50-slot campaign size, so no pagination is needed.
  const rows = db
    .prepare(
      `SELECT id, campaign_id, slot_number, details_encrypted, status, created_at, updated_at
         FROM test_campaign_registrations
        WHERE campaign_id = ?${filter.status ? ' AND status = ?' : ''}
        ORDER BY slot_number ASC`
    )
    .all(...(filter.status ? [campaignId, filter.status] : [campaignId])) as any[];

  let testers = rows.map((row) => toSummary(db, row));

  const needle = (filter.search ?? '').trim().toLowerCase();
  if (needle) {
    testers = testers.filter(
      (t) =>
        t.name.toLowerCase().includes(needle) ||
        t.googlePlayEmail.toLowerCase().includes(needle) ||
        t.whatsapp.toLowerCase().includes(needle) ||
        t.businessType.toLowerCase().includes(needle)
    );
  }

  return { testers, total: testers.length };
}

export function getTester(testerId: number, customDb?: Database.Database): TesterDetail | null {
  const db = customDb ?? getDatabase();
  const row = db
    .prepare(
      `SELECT id, campaign_id, slot_number, lead_token, details_encrypted, status,
              admin_notes, play_opt_in_url, play_invited_at, play_opted_in_at,
              created_at, updated_at
         FROM test_campaign_registrations
        WHERE id = ? AND campaign_id = ?`
    )
    .get(testerId, TEST_CAMPAIGN_ID) as any;

  if (!row) return null;

  const details = decryptDetails(row);
  const history = db
    .prepare(
      `SELECT from_status, to_status, note, actor, created_at
         FROM tester_status_history
        WHERE tester_id = ? ORDER BY created_at DESC, id DESC`
    )
    .all(testerId) as any[];

  return {
    ...toSummary(db, row),
    consent: details.consent === true,
    leadToken: row.lead_token,
    adminNotes: row.admin_notes ?? null,
    playOptInUrl: row.play_opt_in_url ?? null,
    playInvitedAt: row.play_invited_at ?? null,
    playOptedInAt: row.play_opted_in_at ?? null,
    history: history.map((h) => ({
      fromStatus: h.from_status ?? null,
      toStatus: h.to_status,
      note: h.note ?? null,
      actor: h.actor,
      createdAt: h.created_at
    }))
  };
}

export function getTesterStatusBreakdown(customDb?: Database.Database): TesterStatusBreakdown[] {
  const db = customDb ?? getDatabase();
  const rows = db
    .prepare(
      `SELECT status, COUNT(*) AS c FROM test_campaign_registrations
        WHERE campaign_id = ? GROUP BY status`
    )
    .all(TEST_CAMPAIGN_ID) as Array<{ status: string; c: number }>;

  const counts = new Map(rows.map((r) => [r.status, r.c]));
  return TESTER_STATUSES.map((status) => ({ status, count: counts.get(status) ?? 0 }));
}

export interface UpdateTesterInput {
  status?: TesterStatus;
  adminNotes?: string | null;
  playOptInUrl?: string | null;
}

export type UpdateTesterOutcome =
  | { ok: true; tester: TesterDetail }
  | { ok: false; code: 'NOT_FOUND' | 'SAME_STATUS' };

/**
 * Applies a status/note change and records it in the audit trail.
 *
 * Play timestamps are derived from the transition, never invented: INVITED means the admin
 * actually invited the address in the Play Console, OPTED_IN means the tester actually followed
 * the opt-in link. The system does not and cannot observe Google Play itself.
 */
export function updateTester(
  testerId: number,
  input: UpdateTesterInput,
  actor: string = 'ADMIN',
  customDb?: Database.Database
): UpdateTesterOutcome {
  const db = customDb ?? getDatabase();

  const run = db.transaction((): UpdateTesterOutcome => {
    const row = db
      .prepare(
        `SELECT id, status FROM test_campaign_registrations WHERE id = ? AND campaign_id = ?`
      )
      .get(testerId, TEST_CAMPAIGN_ID) as { id: number; status: string } | undefined;

    if (!row) return { ok: false, code: 'NOT_FOUND' };
    if (input.status && input.status === row.status && input.adminNotes === undefined) {
      return { ok: false, code: 'SAME_STATUS' };
    }

    const now = Date.now();
    const nextStatus = (input.status ?? row.status) as TesterStatus;

    db.prepare(
      `UPDATE test_campaign_registrations
          SET status = ?,
              admin_notes = COALESCE(?, admin_notes),
              play_opt_in_url = COALESCE(?, play_opt_in_url),
              play_invited_at = CASE WHEN ? = 'INVITED' AND play_invited_at IS NULL THEN ? ELSE play_invited_at END,
              play_opted_in_at = CASE WHEN ? = 'OPTED_IN' AND play_opted_in_at IS NULL THEN ? ELSE play_opted_in_at END,
              updated_at = ?
        WHERE id = ? AND campaign_id = ?`
    ).run(
      nextStatus,
      input.adminNotes ?? null,
      input.playOptInUrl ?? null,
      nextStatus,
      now,
      nextStatus,
      now,
      now,
      testerId,
      TEST_CAMPAIGN_ID
    );

    if (input.status && input.status !== row.status) {
      db.prepare(
        `INSERT INTO tester_status_history
           (campaign_id, tester_id, from_status, to_status, note, actor, created_at)
         VALUES (?, ?, ?, ?, ?, ?, ?)`
      ).run(
        TEST_CAMPAIGN_ID,
        testerId,
        row.status,
        input.status,
        input.adminNotes ?? null,
        actor,
        now
      );
    }

    return { ok: true, tester: getTester(testerId, db)! };
  });

  return run.immediate();
}
