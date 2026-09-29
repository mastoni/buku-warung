import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { FastifyInstance } from 'fastify';
import { buildApp } from '../src/app.js';
import { config } from '../src/config/index.js';
import { getDatabase, closeDatabase } from '../src/db/database.js';
import {
  TEST_CAMPAIGN_ID,
  TEST_CAMPAIGN_CAPACITY,
  TESTER_STATUSES,
  registerTestCampaign,
  getTestCampaignState
} from '../src/services/testCampaignService.js';

const ADMIN = config.adminApiKey;
const CHARS = '23456789ABCDEFGHJKMNPQRSTVWXYZ';

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
    name: `Usaha ${n}`,
    whatsapp: `0812345678${String(n).padStart(2, '0')}`,
    googlePlayEmail: `tester${n}@example.com`,
    businessType: 'Warung Sembako',
    dailyTransactions: '20-50',
    androidDevice: 'Samsung Galaxy A14',
    consent: true
  };
}

function admin(app: FastifyInstance, opts: { method: 'GET' | 'PUT' | 'POST'; url: string; payload?: unknown; token?: string | null }) {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (opts.token !== null) headers.Authorization = `Bearer ${opts.token ?? ADMIN}`;
  return app.inject({ method: opts.method, url: opts.url, headers, ...(opts.payload ? { payload: opts.payload as any } : {}) });
}

describe('Tester Management (admin only)', () => {
  let app: FastifyInstance;

  beforeEach(async () => {
    const db = getDatabase(':memory:');
    db.exec('DELETE FROM tester_status_history');
    db.exec('DELETE FROM test_campaign_registrations');
    app = buildApp();
    await app.ready();
  });

  afterEach(async () => {
    if (app) await app.close();
  });

  /* =======================================================================
   * 1. ADMIN AUTHORIZATION
   * ======================================================================= */
  describe('1. Admin authorization', () => {
    it('rejects a request with no Authorization header', async () => {
      const res = await admin(app, { method: 'GET', url: '/v1/admin/testers', token: null });
      expect(res.statusCode).toBe(401);
      expect(res.json().error.code).toBe('UNAUTHORIZED');
    });

    it('rejects an invalid admin token', async () => {
      const res = await admin(app, { method: 'GET', url: '/v1/admin/testers', token: 'wrong-key' });
      expect(res.statusCode).toBe(403);
      expect(res.json().error.code).toBe('FORBIDDEN');
    });

    it.each([
      ['GET', '/v1/admin/testers/summary'],
      ['GET', '/v1/admin/testers'],
      ['GET', '/v1/admin/testers/1'],
      ['PUT', '/v1/admin/testers/1']
    ])('protects %s %s behind admin auth', async (method, url) => {
      const res = await admin(app, { method: method as 'GET' | 'PUT', url, token: null, payload: {} });
      expect(res.statusCode).toBe(401);
    });

    it('allows a valid admin token', async () => {
      const res = await admin(app, { method: 'GET', url: '/v1/admin/testers' });
      expect(res.statusCode).toBe(200);
      expect(res.json().success).toBe(true);
    });
  });

  /* =======================================================================
   * 8. NO PUBLIC EXPOSURE
   * ======================================================================= */
  describe('2. No public exposure', () => {
    it('does not expose tester PII through any public route', async () => {
      registerTestCampaign(detailsFor(1), leadToken(1));

      for (const url of [
        '/v1/landing/test-campaign',
        '/v1/public/pricing?product=BUKU_WARUNG',
        '/beli/buku-warung',
        '/download/buku-warung/info',
        '/v1/landing/metrics'
      ]) {
        const res = await app.inject({ method: 'GET', url });
        const body = res.body;
        expect(body).not.toContain('tester1@example.com');
        expect(body).not.toContain('081234567801');
        expect(body).not.toContain('Usaha 1');
      }
    });

    it('public campaign status exposes only aggregate counters, never PII', async () => {
      registerTestCampaign(detailsFor(1), leadToken(1));
      const res = await app.inject({ method: 'GET', url: '/v1/landing/test-campaign' });
      const data = res.json().data;

      expect(data).toEqual({
        campaignId: TEST_CAMPAIGN_ID,
        status: 'OPEN',
        capacity: TEST_CAMPAIGN_CAPACITY,
        registered: 1,
        remaining: TEST_CAMPAIGN_CAPACITY - 1
      });
      expect(JSON.stringify(data)).not.toContain('@example.com');
    });

    it('admin console HTML embeds no tester data', async () => {
      registerTestCampaign(detailsFor(1), leadToken(1));
      const res = await app.inject({ method: 'GET', url: '/admin/testers' });
      expect(res.statusCode).toBe(200);
      expect(res.body).not.toContain('tester1@example.com');
      expect(res.body).not.toContain('081234567801');
      // It must be a shell that fetches with the admin token at runtime.
      expect(res.body).toContain('/v1/admin/testers');
    });
  });

  /* =======================================================================
   * 3/9. EMPTY STATE + LIST
   * ======================================================================= */
  describe('3. Empty state and listing', () => {
    it('returns an empty list and zero counters when nobody has registered', async () => {
      const list = await admin(app, { method: 'GET', url: '/v1/admin/testers' });
      expect(list.json().data).toEqual({ testers: [], total: 0 });

      const summary = await admin(app, { method: 'GET', url: '/v1/admin/testers/summary' });
      const d = summary.json().data;
      expect(d.registered).toBe(0);
      expect(d.remaining).toBe(TEST_CAMPAIGN_CAPACITY);
      expect(d.capacity).toBe(TEST_CAMPAIGN_CAPACITY);
      // Google Play is not integrated - the dashboard must say so.
      expect(d.googlePlayIntegrated).toBe(false);
    });

    it('lists testers with the required PII fields', async () => {
      registerTestCampaign(detailsFor(1), leadToken(1));
      const res = await admin(app, { method: 'GET', url: '/v1/admin/testers' });
      const { testers, total } = res.json().data;

      expect(total).toBe(1);
      expect(testers[0]).toMatchObject({
        name: 'Usaha 1',
        googlePlayEmail: 'tester1@example.com',
        whatsapp: '081234567801',
        businessType: 'Warung Sembako',
        dailyTransactions: '20-50',
        androidDevice: 'Samsung Galaxy A14',
        status: 'REGISTERED',
        slotNumber: 1
      });
    });

    it('decrypts PII correctly at rest (roundtrip)', async () => {
      const payload = detailsFor(9);
      registerTestCampaign(payload, leadToken(9));
      const res = await admin(app, { method: 'GET', url: '/v1/admin/testers' });
      const t = res.json().data.testers[0];
      expect(t.name).toBe(payload.name);
      expect(t.googlePlayEmail).toBe(payload.googlePlayEmail);
      expect(t.whatsapp).toBe(payload.whatsapp);
    });
  });

  /* =======================================================================
   * 4. DETAIL
   * ======================================================================= */
  describe('4. Tester detail', () => {
    it('returns full detail and 404s for an unknown id', async () => {
      registerTestCampaign(detailsFor(1), leadToken(1));
      const res = await admin(app, { method: 'GET', url: '/v1/admin/testers/1' });
      expect(res.statusCode).toBe(200);
      const t = res.json().data;
      expect(t.consent).toBe(true);
      expect(t.leadToken).toBe(leadToken(1));
      expect(t.history).toEqual([]);

      const missing = await admin(app, { method: 'GET', url: '/v1/admin/testers/9999' });
      expect(missing.statusCode).toBe(404);
      expect(missing.json().error.code).toBe('TESTER_NOT_FOUND');
    });

    it('never returns licence or order data', async () => {
      registerTestCampaign(detailsFor(1), leadToken(1));
      const res = await admin(app, { method: 'GET', url: '/v1/admin/testers/1' });
      const body = res.body;
      for (const forbidden of ['licenseCode', 'deviceBinding', 'order', 'deliveryLicenseCode']) {
        expect(body).not.toContain(forbidden);
      }
    });
  });

  /* =======================================================================
   * 5. STATUS LIFECYCLE + 6. AUDIT TRAIL
   * ======================================================================= */
  describe('5. Status lifecycle and audit trail', () => {
    it('exposes exactly the nine workflow statuses', async () => {
      const res = await admin(app, { method: 'GET', url: '/v1/admin/testers/meta/statuses' });
      expect(res.json().data.statuses).toEqual([
        'REGISTERED', 'REVIEWED', 'READY_FOR_PLAY', 'INVITED', 'OPTED_IN',
        'TESTING', 'FEEDBACK_RECEIVED', 'COMPLETED', 'REJECTED'
      ]);
      expect(TESTER_STATUSES).toHaveLength(9);
    });

    it('walks a tester through the full Closed Testing workflow', async () => {
      registerTestCampaign(detailsFor(1), leadToken(1));

      for (const status of ['REVIEWED', 'READY_FOR_PLAY', 'INVITED', 'OPTED_IN', 'TESTING', 'FEEDBACK_RECEIVED', 'COMPLETED']) {
        const res = await admin(app, {
          method: 'PUT',
          url: '/v1/admin/testers/1',
          payload: { status }
        });
        expect(res.statusCode, `transition to ${status}`).toBe(200);
        expect(res.json().data.status).toBe(status);
      }
    });

    it('records Play timestamps only on the manual transitions', async () => {
      registerTestCampaign(detailsFor(1), leadToken(1));

      let res = await admin(app, { method: 'PUT', url: '/v1/admin/testers/1', payload: { status: 'INVITED' } });
      expect(res.json().data.playInvitedAt).toBeGreaterThan(0);
      expect(res.json().data.playOptedInAt).toBeNull();

      res = await admin(app, { method: 'PUT', url: '/v1/admin/testers/1', payload: { status: 'OPTED_IN' } });
      expect(res.json().data.playOptedInAt).toBeGreaterThan(0);
    });

    it('keeps an immutable audit trail of every transition', async () => {
      registerTestCampaign(detailsFor(1), leadToken(1));
      await admin(app, { method: 'PUT', url: '/v1/admin/testers/1', payload: { status: 'REVIEWED' } });
      await admin(app, { method: 'PUT', url: '/v1/admin/testers/1', payload: { status: 'READY_FOR_PLAY' } });

      const res = await admin(app, { method: 'GET', url: '/v1/admin/testers/1' });
      const history = res.json().data.history;

      expect(history).toHaveLength(2);
      expect(history.map((h: any) => h.toStatus)).toEqual(['READY_FOR_PLAY', 'REVIEWED']);
      expect(history.every((h: any) => h.actor === 'ADMIN_API')).toBe(true);
      expect(history[0].fromStatus).toBe('REVIEWED');
      expect(history[1].fromStatus).toBe('REGISTERED');
    });

    it('rejects an unknown status value', async () => {
      registerTestCampaign(detailsFor(1), leadToken(1));
      const res = await admin(app, { method: 'PUT', url: '/v1/admin/testers/1', payload: { status: 'NOT_A_STATUS' } });
      expect(res.statusCode).toBe(400);
    });

    it('rejects a no-op transition to the current status', async () => {
      registerTestCampaign(detailsFor(1), leadToken(1));
      const res = await admin(app, { method: 'PUT', url: '/v1/admin/testers/1', payload: { status: 'REGISTERED' } });
      expect(res.statusCode).toBe(400);
      expect(res.json().error.code).toBe('STATUS_UNCHANGED');
    });

    it('rejects unknown fields on update', async () => {
      registerTestCampaign(detailsFor(1), leadToken(1));
      const res = await admin(app, {
        method: 'PUT',
        url: '/v1/admin/testers/1',
        payload: { status: 'REVIEWED', slotNumber: 99 }
      });
      expect(res.statusCode).toBe(400);
    });
  });

  /* =======================================================================
   * 9. ADMIN NOTES
   * ======================================================================= */
  describe('6. Admin notes and opt-in link', () => {
    it('stores an admin note without creating a history entry', async () => {
      registerTestCampaign(detailsFor(1), leadToken(1));
      const res = await admin(app, {
        method: 'PUT',
        url: '/v1/admin/testers/1',
        payload: { adminNotes: 'Warung aktif, visited 12 Sep' }
      });
      expect(res.json().data.adminNotes).toBe('Warung aktif, visited 12 Sep');
      expect(res.json().data.history).toHaveLength(0);
    });

    it('stores the configured Closed Testing opt-in link', async () => {
      registerTestCampaign(detailsFor(1), leadToken(1));
      const url = 'https://play.google.com/apps/testing/join/abc123';
      const res = await admin(app, {
        method: 'PUT',
        url: '/v1/admin/testers/1',
        payload: { status: 'INVITED', playOptInUrl: url }
      });
      expect(res.json().data.playOptInUrl).toBe(url);
    });
  });

  /* =======================================================================
   * 7. CAPACITY CONSISTENCY
   * ======================================================================= */
  describe('7. Capacity consistency', () => {
    it('admin summary never disagrees with the public campaign counter', async () => {
      for (let i = 1; i <= 5; i++) registerTestCampaign(detailsFor(i), leadToken(i));

      const summary = await admin(app, { method: 'GET', url: '/v1/admin/testers/summary' });
      const publicState = getTestCampaignState();
      const d = summary.json().data;

      expect(d.registered).toBe(publicState.registered);
      expect(d.remaining).toBe(publicState.remaining);
      expect(d.capacity).toBe(publicState.capacity);
      expect(d.capacity).toBe(TEST_CAMPAIGN_CAPACITY);
    });

    it('breakdown totals never exceed capacity and sum to active registrations', async () => {
      for (let i = 1; i <= 5; i++) registerTestCampaign(detailsFor(i), leadToken(i));
      const res = await admin(app, { method: 'GET', url: '/v1/admin/testers/summary' });
      const d = res.json().data;
      const sum = d.breakdown.reduce((a: number, b: any) => a + b.count, 0);
      expect(sum).toBe(5);
      expect(d.registered).toBe(5);
    });

    it('releasing a rejected slot is reflected in the authoritative counter', async () => {
      registerTestCampaign(detailsFor(1), leadToken(1));
      expect(getTestCampaignState().remaining).toBe(TEST_CAMPAIGN_CAPACITY - 1);

      await admin(app, { method: 'PUT', url: '/v1/admin/testers/1', payload: { status: 'REJECTED' } });

      // The public campaign endpoint and the admin summary must agree after the change.
      const publicRes = await app.inject({ method: 'GET', url: '/v1/landing/test-campaign' });
      const adminRes = await admin(app, { method: 'GET', url: '/v1/admin/testers/summary' });
      expect(publicRes.json().data.remaining).toBe(TEST_CAMPAIGN_CAPACITY);
      expect(adminRes.json().data.remaining).toBe(TEST_CAMPAIGN_CAPACITY);
    });

    it('tester management never creates a second capacity counter', async () => {
      registerTestCampaign(detailsFor(1), leadToken(1));
      const rows = getDatabase()
        .prepare("SELECT name FROM sqlite_master WHERE type='table' AND name LIKE '%capacity%'")
        .all();
      expect(rows).toHaveLength(0);
    });
  });

  /* =======================================================================
   * 10. SEARCH / FILTER
   * ======================================================================= */
  describe('8. Search and status filter', () => {
    beforeEach(() => {
      registerTestCampaign({ ...detailsFor(1), name: 'Warung Berkah', businessType: 'Warung Sembako' }, leadToken(1));
      registerTestCampaign({ ...detailsFor(2), name: 'Tokoelectronik Jaya', businessType: 'Elektronik' }, leadToken(2));
      registerTestCampaign({ ...detailsFor(3), name: 'Laundry Sari', businessType: 'Jasa' }, leadToken(3));
    });

    it('filters by status', async () => {
      await admin(app, { method: 'PUT', url: '/v1/admin/testers/2', payload: { status: 'INVITED' } });

      const res = await admin(app, { method: 'GET', url: '/v1/admin/testers?status=INVITED' });
      const { testers, total } = res.json().data;
      expect(total).toBe(1);
      expect(testers[0].id).toBe(2);
    });

    it('searches across name, email, WhatsApp and business type', async () => {
      expect((await admin(app, { method: 'GET', url: '/v1/admin/testers?search=Berkah' })).json().data.total).toBe(1);
      expect((await admin(app, { method: 'GET', url: '/v1/admin/testers?search=tester2' })).json().data.total).toBe(1);
      expect((await admin(app, { method: 'GET', url: '/v1/admin/testers?search=081234567803' })).json().data.total).toBe(1);
      expect((await admin(app, { method: 'GET', url: '/v1/admin/testers?search=Elektronik' })).json().data.total).toBe(1);
    });

    it('returns an empty result for a search that matches nothing', async () => {
      const res = await admin(app, { method: 'GET', url: '/v1/admin/testers?search=tidak-ada' });
      expect(res.json().data).toEqual({ testers: [], total: 0 });
    });
  });
});
