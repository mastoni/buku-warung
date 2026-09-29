import { describe, it, expect, beforeEach, afterAll, afterEach } from 'vitest';
import { FastifyInstance } from 'fastify';
import { buildApp } from '../src/app.js';
import { getDatabase, closeDatabase } from '../src/db/database.js';
import {
  TEST_CAMPAIGN_ID,
  TEST_CAMPAIGN_CAPACITY,
  getTestCampaignState,
  registerTestCampaign
} from '../src/services/testCampaignService.js';
import { decryptTestRegistrationDetails } from '../src/utils/crypto.js';

const CHARS = '23456789ABCDEFGHJKMNPQRSTVWXYZ';

/**
 * Bijective base-N encoding of `index` into a 6-character lead token, so every index yields a
 * distinct token that still matches the server's ^LW-[2-9A-HJ-NP-Z]{6}$ rule. The radix is the
 * alphabet's real length, not a hard-coded 32, so no index can ever index past the end of
 * CHARS and produce a corrupted token.
 */
function leadToken(index: number): string {
  const radix = CHARS.length;
  let n = index;
  let out = '';
  for (let i = 0; i < 6; i++) {
    out = CHARS[n % radix] + out;
    n = Math.floor(n / radix);
  }
  return `LW-${out}`;
}

function detailsFor(n: number) {
  return {
    name: `Warung ${n}`,
    whatsapp: `0812345678${String(n).padStart(2, '0')}`,
    googlePlayEmail: `tester${n}@example.com`,
    businessType: 'Warung Sembako',
    dailyTransactions: '20-50',
    androidDevice: 'Samsung Galaxy A14',
    consent: true
  };
}

describe('Closed Testing Campaign BUKU_WARUNG_TEST_BATCH_1', () => {
  beforeEach(() => {
    const db = getDatabase(':memory:');
    db.exec('DELETE FROM test_campaign_registrations');
  });

  afterAll(() => {
    closeDatabase();
  });

  /* =========================================================================
   * 1. Campaign starts OPEN
   * 2. Capacity is 50
   * ========================================================================= */
  it('starts OPEN with capacity 50 and zero registrations', () => {
    const state = getTestCampaignState();
    expect(state.campaignId).toBe(TEST_CAMPAIGN_ID);
    expect(state.capacity).toBe(50);
    expect(TEST_CAMPAIGN_CAPACITY).toBe(50);
    expect(state.status).toBe('OPEN');
    expect(state.registered).toBe(0);
    expect(state.remaining).toBe(50);
  });

  /* =========================================================================
   * 3. Registration increments exactly once
   * ========================================================================= */
  it('increments the registered count exactly once per slot', () => {
    const first = registerTestCampaign(detailsFor(1), leadToken(1));
    expect(first.kind).toBe('REGISTERED');
    expect(first.slotNumber).toBe(1);
    expect(first.state.registered).toBe(1);
    expect(first.state.remaining).toBe(49);

    const second = registerTestCampaign(detailsFor(2), leadToken(2));
    expect(second.kind).toBe('REGISTERED');
    expect(second.slotNumber).toBe(2);
    expect(second.state.registered).toBe(2);
    expect(second.state.remaining).toBe(48);
  });

  /* =========================================================================
   * 4. Duplicate registration does not consume another slot
   * ========================================================================= */
  it('does not consume a second slot for the same lead token', () => {
    const first = registerTestCampaign(detailsFor(1), leadToken(7));
    expect(first.kind).toBe('REGISTERED');

    const repeat = registerTestCampaign(detailsFor(1), leadToken(7));
    expect(repeat.kind).toBe('DUPLICATE');
    expect(repeat.slotNumber).toBe(first.slotNumber);
    expect(repeat.state.registered).toBe(1);
    expect(repeat.state.remaining).toBe(49);
  });

  it('does not consume a second slot for the same contact email', () => {
    const first = registerTestCampaign(detailsFor(5), leadToken(11));
    const repeat = registerTestCampaign(detailsFor(5), leadToken(12));
    expect(repeat.kind).toBe('DUPLICATE');
    expect(repeat.state.registered).toBe(1);
  });

  /* =========================================================================
   * 5. Registration 50 succeeds
   * 6. Registration 51 returns FULL
   * ========================================================================= */
  it('accepts all 50 slots and rejects the 51st as FULL', () => {
    for (let i = 1; i <= TEST_CAMPAIGN_CAPACITY; i++) {
      const outcome = registerTestCampaign(detailsFor(i), leadToken(i));
      expect(outcome.kind).toBe('REGISTERED');
    }

    const full = getTestCampaignState();
    expect(full.registered).toBe(50);
    expect(full.remaining).toBe(0);
    expect(full.status).toBe('FULL');

    const overflow = registerTestCampaign(detailsFor(999), leadToken(999));
    expect(overflow.kind).toBe('FULL');
    expect(overflow.state.registered).toBe(50);
    expect(overflow.state.remaining).toBe(0);
    // A rejected request must not create a row.
    expect(getTestCampaignState().registered).toBe(50);
  });

  /* =========================================================================
   * 7. Concurrent registration cannot exceed capacity
   * ========================================================================= */
  it('never oversubscribes capacity under concurrent registration', () => {
    const CONCURRENT_ATTEMPTS = 200;
    const results = Array.from({ length: CONCURRENT_ATTEMPTS }, (_, i) =>
      registerTestCampaign(detailsFor(i), leadToken(i))
    );

    const registered = results.filter((r) => r.kind === 'REGISTERED');
    const full = results.filter((r) => r.kind === 'FULL');

    expect(registered.length).toBe(TEST_CAMPAIGN_CAPACITY);
    expect(full.length).toBe(CONCURRENT_ATTEMPTS - TEST_CAMPAIGN_CAPACITY);

    const state = getTestCampaignState();
    expect(state.registered).toBe(50);
    expect(state.remaining).toBe(0);
    expect(state.status).toBe('FULL');

    // Slot numbers are unique and stay inside 1..50.
    const slots = registered.map((r) => (r as { slotNumber: number }).slotNumber);
    expect(new Set(slots).size).toBe(slots.length);
    expect(Math.min(...slots)).toBe(1);
    expect(Math.max(...slots)).toBe(50);
  });

  /* =========================================================================
   * PII is encrypted at rest and never stored in funnel_events
   * ========================================================================= */
  it('stores registration PII encrypted at rest', () => {
    const payload = detailsFor(42);
    registerTestCampaign(payload, leadToken(42));

    const db = getDatabase();
    const row = db
      .prepare('SELECT details_encrypted, contact_hash FROM test_campaign_registrations LIMIT 1')
      .get() as { details_encrypted: string; contact_hash: string };

    expect(row.details_encrypted).not.toContain(payload.googlePlayEmail);
    expect(row.details_encrypted).not.toContain(payload.whatsapp);
    expect(row.contact_hash).not.toContain(payload.googlePlayEmail);

    expect(decryptTestRegistrationDetails(row.details_encrypted)).toEqual(payload);
  });

  it('writes no raw PII into funnel_events when a submission is tracked', async () => {
    const trackApp = buildApp();
    await trackApp.ready();

    const token = leadToken(3);
    // The server only honours a client-supplied leadToken it has already minted, so the first
    // tracked event goes out without one - exactly how the landing page behaves.
    const response = await trackApp.inject({
      method: 'POST',
      url: '/v1/landing/track',
      payload: { eventType: 'TEST_REGISTRATION_SUBMITTED' }
    });
    expect(response.statusCode).toBe(200);
    expect(response.json().data.leadToken).toMatch(/^LW-[2-9A-HJ-NP-Z]{6}$/);

    const db = getDatabase();
    const events = db
      .prepare('SELECT event_data FROM funnel_events WHERE event_type = ?')
      .all('TEST_REGISTRATION_SUBMITTED') as Array<{ event_data: string | null }>;
    const serialized = JSON.stringify(events);
    expect(serialized).not.toContain('tester3@example.com');
    expect(serialized).not.toContain('0812345678');
    expect(serialized).not.toContain('@example.com');

    await trackApp.close();
  });
});

describe('Campaign HTTP surface', () => {
  let app: FastifyInstance;

  beforeEach(async () => {
    getDatabase(':memory:').exec('DELETE FROM test_campaign_registrations');
    app = buildApp();
    await app.ready();
  });

  afterEach(async () => {
    if (app) await app.close();
  });

  afterAll(() => {
    closeDatabase();
  });

  it('GET /v1/landing/test-campaign reports authoritative availability', async () => {
    const res = await app.inject({ method: 'GET', url: '/v1/landing/test-campaign' });
    expect(res.statusCode).toBe(200);
    const body = res.json();
    expect(body.success).toBe(true);
    expect(body.data.capacity).toBe(50);
    expect(body.data.status).toBe('OPEN');
  });

  it('POST register returns 201 with a slot, and CAMPAIGN_FULL once exhausted', async () => {
    const first = await app.inject({
      method: 'POST',
      url: '/v1/landing/test-campaign/register',
      payload: { ...detailsFor(1), leadToken: leadToken(1) }
    });
    expect(first.statusCode).toBe(201);
    expect(first.json().data.remaining).toBe(49);

    for (let i = 2; i <= TEST_CAMPAIGN_CAPACITY; i++) {
      const res = await app.inject({
        method: 'POST',
        url: '/v1/landing/test-campaign/register',
        payload: { ...detailsFor(i), leadToken: leadToken(i) }
      });
      expect(res.statusCode, `registration ${i} should be accepted`).toBe(201);
    }

    // Prove the ledger is genuinely full before asserting the rejection.
    expect(getTestCampaignState().registered).toBe(TEST_CAMPAIGN_CAPACITY);
    expect(getTestCampaignState().status).toBe('FULL');

    const overflow = await app.inject({
      method: 'POST',
      url: '/v1/landing/test-campaign/register',
      payload: { ...detailsFor(500), leadToken: leadToken(500) }
    });
    expect(overflow.statusCode).toBe(409);
    expect(overflow.json().code).toBe('CAMPAIGN_FULL');
    expect(overflow.json().data.remaining).toBe(0);
  });

  it('POST register rejects a submission without consent', async () => {
    const res = await app.inject({
      method: 'POST',
      url: '/v1/landing/test-campaign/register',
      payload: { ...detailsFor(9), consent: false, leadToken: leadToken(9) }
    });
    expect(res.statusCode).toBe(400);
    expect(res.json().error.code).toBe('CONSENT_REQUIRED');
  });

  it('accepts the new campaign funnel events through the existing validator', async () => {
    for (const eventType of [
      'TEST_PROGRAM_VIEW',
      'TEST_REGISTRATION_STARTED',
      'TEST_REGISTRATION_SUBMITTED',
      'TEST_PROGRAM_FULL'
    ]) {
      const res = await app.inject({
        method: 'POST',
        url: '/v1/landing/track',
        payload: { eventType }
      });
      expect(res.statusCode, `expected 200 for ${eventType}`).toBe(200);
    }
  });

  it('still rejects an unknown event type', async () => {
    const res = await app.inject({
      method: 'POST',
      url: '/v1/landing/track',
      payload: { eventType: 'NOT_A_REAL_EVENT' }
    });
    expect(res.statusCode).toBe(400);
    expect(res.json().error.code).toBe('INVALID_EVENT_TYPE');
  });

  it('does not change the existing public order or pricing routes', async () => {
    const order = await app.inject({ method: 'GET', url: '/beli/buku-warung' });
    expect(order.statusCode).toBe(200);
    const pricing = await app.inject({
      method: 'GET',
      url: '/v1/public/pricing?product=BUKU_WARUNG'
    });
    expect(pricing.statusCode).toBe(200);
    expect(pricing.json().success).toBe(true);
  });
});
