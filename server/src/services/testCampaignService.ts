import Database from 'better-sqlite3';
import { getDatabase } from '../db/database.js';
import { encryptTestRegistrationDetails, hashEmail, timingSafeCompare } from '../utils/crypto.js';
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

function countRegistered(db: Database.Database, campaignId: string): number {
  const row = db
    .prepare("SELECT COUNT(*) AS c FROM test_campaign_registrations WHERE campaign_id = ? AND status != 'REVOKED'")
    .get(campaignId) as { c: number };
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
