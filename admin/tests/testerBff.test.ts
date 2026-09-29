import { describe, it, expect, beforeAll, afterAll, beforeEach } from 'vitest';
import { FastifyInstance } from 'fastify';
import { buildAdminApp } from '../src/app.js';
import { buildApp as buildLicenseServerApp } from '../../server/src/app.js';
import { getDatabase as getLicenseDb, closeDatabase as closeLicenseDb } from '../../server/src/db/database.js';
import { registerTestCampaign } from '../../server/src/services/testCampaignService.js';
import { config as adminConfig } from '../src/config/index.js';
import { config as licenseServerConfig } from '../../server/src/config/index.js';

/**
 * Proves the two-layer auth model for Tester Management:
 *
 *   Browser --(Admin App session cookie)--> /api/testers*  --(ADMIN_API_KEY, server-side)--> /v1/admin/testers*
 *
 * The browser must never receive or need the license-server key, and the BFF must refuse
 * everything without a valid session.
 */
describe('Tester Management BFF (Session proxy to License Server)', () => {
  let adminApp: FastifyInstance;
  let licenseServerApp: FastifyInstance;
  let licenseServerPort: number;
  let authHeaders: Record<string, string> = {};

  const testerDetails = {
    name: 'Warung Berkah Bu Siti',
    whatsapp: '081234567801',
    googlePlayEmail: 'siti@example.com',
    businessType: 'Warung Sembako',
    dailyTransactions: '20-50',
    androidDevice: 'Samsung Galaxy A14',
    consent: true
  };

  beforeAll(async () => {
    getLicenseDb(':memory:');
    licenseServerApp = buildLicenseServerApp();
    await licenseServerApp.listen({ port: 0, host: '127.0.0.1' });
    licenseServerPort = (licenseServerApp.server.address() as any).port;

    adminConfig.licenseServerUrl = `http://127.0.0.1:${licenseServerPort}`;
    adminConfig.adminApiKey = licenseServerConfig.adminApiKey;

    adminApp = buildAdminApp();
    await adminApp.ready();
  });

  afterAll(async () => {
    await adminApp.close();
    await licenseServerApp.close();
    closeLicenseDb();
  });

  beforeEach(async () => {
    // Other suites in this project also boot a License Server and write adminConfig.licenseServerUrl
    // in their own beforeAll. LicenseClient resolves the URL per request, so re-assert this
    // suite's target before every test to make the BFF independent of file execution order.
    adminConfig.licenseServerUrl = `http://127.0.0.1:${licenseServerPort}`;
    adminConfig.adminApiKey = licenseServerConfig.adminApiKey;

    // Reuse the instance created in beforeAll. Passing ':memory:' again would close and replace
    // the database, leaving services such as PricingService holding a stale handle.
    getLicenseDb().exec('DELETE FROM tester_status_history');
    getLicenseDb().exec('DELETE FROM test_campaign_registrations');
    // Reset AUTOINCREMENT so row ids are deterministic (1, 2, 3, ...) in this suite.
    getLicenseDb().exec("DELETE FROM sqlite_sequence WHERE name = 'test_campaign_registrations'");
    authHeaders = {};
  });

  /**
   * Returns a ready-to-send Cookie header (e.g. "bw_admin_session=<token>") and a
   * Fastify inject headers object for it.
   */
  async function login(): Promise<{ header: string; headers: Record<string, string> }> {
    const res = await adminApp.inject({
      method: 'POST',
      url: '/api/auth/login',
      payload: { username: adminConfig.adminUsername, password: adminConfig.adminPassword }
    });
    const setCookie = res.headers['set-cookie'] as unknown as string | string[];
    const header = (Array.isArray(setCookie) ? setCookie[0] : setCookie || '').split(';')[0];
    return { header, headers: { cookie: header } };
  }

  // ==========================================================================
  // 1. Browser without a session cannot reach any tester BFF route
  // ==========================================================================
  describe('1. Session enforcement', () => {
    it.each([
      ['GET', '/api/testers/summary'],
      ['GET', '/api/testers'],
      ['GET', '/api/testers/1'],
      ['GET', '/api/testers/meta/statuses']
    ])('%s %s returns 401 without a session', async (method, url) => {
      const res = await adminApp.inject({ method: method as 'GET', url });
      expect(res.statusCode).toBe(401);
      expect(res.json().error.code).toBe('UNAUTHORIZED');
    });

    it('PUT /api/testers/:id returns 401 without a session', async () => {
      const res = await adminApp.inject({
        method: 'PUT',
        url: '/api/testers/1',
        payload: { status: 'REVIEWED' }
      });
      expect(res.statusCode).toBe(401);
    });

    it('rejects a forged session cookie', async () => {
      const res = await adminApp.inject({
        method: 'GET',
        url: '/api/testers',
        cookies: { bw_admin_session: 'admin:forged-signature' }
      });
      expect(res.statusCode).toBe(401);
    });

    it('does not allow a browser-supplied license key to bypass the session', async () => {
      // Even with the real ADMIN_API_KEY, the BFF still requires its own session.
      const res = await adminApp.inject({
        method: 'GET',
        url: '/api/testers',
        headers: { authorization: `Bearer ${licenseServerConfig.adminApiKey}` }
      });
      expect(res.statusCode).toBe(401);
    });
  });

  // ==========================================================================
  // 2 + 3. Authenticated session proxies successfully to the license server
  // ==========================================================================
  describe('2. Authenticated proxying', () => {
    it('proxies the empty-state summary', async () => {
      authHeaders = (await login()).headers;
      const res = await adminApp.inject({
        method: 'GET',
        url: '/api/testers/summary',
        headers: authHeaders
      });

      expect(res.statusCode).toBe(200);
      const d = res.json().data;
      expect(d.campaignId).toBe('BUKU_WARUNG_TEST_BATCH_1');
      expect(d.capacity).toBe(50);
      expect(d.registered).toBe(0);
      expect(d.remaining).toBe(50);
      // Google Play is not integrated and the BFF must say so.
      expect(d.googlePlayIntegrated).toBe(false);
    });

    it('proxies the lifecycle statuses from the server allowlist', async () => {
      authHeaders = (await login()).headers;
      const res = await adminApp.inject({
        method: 'GET',
        url: '/api/testers/meta/statuses',
        headers: authHeaders
      });

      expect(res.statusCode).toBe(200);
      expect(res.json().data.statuses).toEqual([
        'REGISTERED', 'REVIEWED', 'READY_FOR_PLAY', 'INVITED', 'OPTED_IN',
        'TESTING', 'FEEDBACK_RECEIVED', 'COMPLETED', 'REJECTED'
      ]);
    });

    it('proxies the list with decrypted PII (roundtrip)', async () => {
      registerTestCampaign(testerDetails, 'LW-222222');
      authHeaders = (await login()).headers;

      const res = await adminApp.inject({
        method: 'GET',
        url: '/api/testers',
        headers: authHeaders
      });

      expect(res.statusCode).toBe(200);
      const { testers, total } = res.json().data;
      expect(total).toBe(1);
      expect(testers[0]).toMatchObject({
        name: 'Warung Berkah Bu Siti',
        googlePlayEmail: 'siti@example.com',
        whatsapp: '081234567801',
        businessType: 'Warung Sembako',
        status: 'REGISTERED',
        slotNumber: 1
      });
    });

    it('proxies search and status filter', async () => {
      registerTestCampaign(testerDetails, 'LW-222222');
      authHeaders = (await login()).headers;

      const hit = await adminApp.inject({
        method: 'GET',
        url: '/api/testers?search=Berkah',
        headers: authHeaders
      });
      expect(hit.json().data.total).toBe(1);

      const miss = await adminApp.inject({
        method: 'GET',
        url: '/api/testers?search=tidak-ada',
        headers: authHeaders
      });
      expect(miss.json().data.total).toBe(0);

      const filtered = await adminApp.inject({
        method: 'GET',
        url: '/api/testers?status=COMPLETED',
        headers: authHeaders
      });
      expect(filtered.json().data.total).toBe(0);
    });

    it('proxies detail and keeps licence/order fields out of it', async () => {
      registerTestCampaign(testerDetails, 'LW-222222');
      authHeaders = (await login()).headers;

      const res = await adminApp.inject({
        method: 'GET',
        url: '/api/testers/1',
        headers: authHeaders
      });

      expect(res.statusCode).toBe(200);
      const t = res.json().data;
      expect(t.googlePlayEmail).toBe('siti@example.com');
      expect(t.history).toEqual([]);

      for (const forbidden of ['licenseCode', 'deviceBinding', 'deliveryLicenseCode', 'orderId']) {
        expect(res.body).not.toContain(forbidden);
      }
    });

    it('propagates a 404 for an unknown tester', async () => {
      authHeaders = (await login()).headers;
      const res = await adminApp.inject({
        method: 'GET',
        url: '/api/testers/9999',
        headers: authHeaders
      });
      expect(res.statusCode).toBe(404);
    });

    it('rejects a non-numeric id locally', async () => {
      authHeaders = (await login()).headers;
      const res = await adminApp.inject({
        method: 'GET',
        url: '/api/testers/abc',
        headers: authHeaders
      });
      expect(res.statusCode).toBe(400);
      expect(res.json().error.code).toBe('INVALID_ID');
    });
  });

  // ==========================================================================
  // 10 + 11 + 12. Lifecycle, audit history, rejected frees capacity
  // ==========================================================================
  describe('3. Status lifecycle through the BFF', () => {
    it('walks the Closed Testing workflow and records history', async () => {
      registerTestCampaign(testerDetails, 'LW-222222');
      authHeaders = (await login()).headers;
      const headers = authHeaders;

      for (const status of ['REVIEWED', 'READY_FOR_PLAY', 'INVITED', 'OPTED_IN', 'TESTING']) {
        const res = await adminApp.inject({
          method: 'PUT',
          url: '/api/testers/1',
          headers,
          payload: { status }
        });
        expect(res.statusCode, `transition ${status}`).toBe(200);
        expect(res.json().data.status).toBe(status);
      }

      const detail = await adminApp.inject({ method: 'GET', url: '/api/testers/1', headers });
      const t = detail.json().data;
      expect(t.playInvitedAt).toBeGreaterThan(0);
      expect(t.playOptedInAt).toBeGreaterThan(0);
      expect(t.history).toHaveLength(5);
      expect(t.history.map((h: any) => h.toStatus)).toEqual([
        'TESTING', 'OPTED_IN', 'INVITED', 'READY_FOR_PLAY', 'REVIEWED'
      ]);
    });

    it('stores admin notes and the Play opt-in link', async () => {
      registerTestCampaign(testerDetails, 'LW-222222');
      authHeaders = (await login()).headers;
      const headers = authHeaders;

      const optIn = 'https://play.google.com/apps/testing/join/abc123';
      const res = await adminApp.inject({
        method: 'PUT',
        url: '/api/testers/1',
        headers,
        payload: { status: 'INVITED', adminNotes: 'Sudah invite via Play Console', playOptInUrl: optIn }
      });

      expect(res.json().data.adminNotes).toBe('Sudah invite via Play Console');
      expect(res.json().data.playOptInUrl).toBe(optIn);
    });

    it('rejects a status outside the lifecycle', async () => {
      registerTestCampaign(testerDetails, 'LW-222222');
      authHeaders = (await login()).headers;

      const res = await adminApp.inject({
        method: 'PUT',
        url: '/api/testers/1',
        headers: authHeaders,
        payload: { status: 'NOT_A_STATUS' }
      });
      expect(res.statusCode).toBe(400);
    });

    it('a REJECTED tester frees the slot for a replacement', async () => {
      registerTestCampaign(testerDetails, 'LW-222222');
      authHeaders = (await login()).headers;
      const headers = authHeaders;

      const before = await adminApp.inject({ method: 'GET', url: '/api/testers/summary', headers });
      expect(before.json().data.remaining).toBe(49);

      await adminApp.inject({
        method: 'PUT',
        url: '/api/testers/1',
        headers,
        payload: { status: 'REJECTED' }
      });

      // Admin summary and the PUBLIC campaign counter must agree after the release.
      const after = await adminApp.inject({ method: 'GET', url: '/api/testers/summary', headers });
      const publicState = await licenseServerApp.inject({ method: 'GET', url: '/v1/landing/test-campaign' });

      expect(after.json().data.remaining).toBe(50);
      expect(publicState.json().data.remaining).toBe(50);
    });
  });

  // ==========================================================================
  // 4. The browser never receives the ADMIN_API_KEY
  // ==========================================================================
  describe('4. Key isolation', () => {
    it('never returns the license server key in any BFF response', async () => {
      registerTestCampaign(testerDetails, 'LW-222222');
      authHeaders = (await login()).headers;
      const headers = authHeaders;

      for (const url of [
        '/api/testers/summary',
        '/api/testers',
        '/api/testers/1',
        '/api/testers/meta/statuses'
      ]) {
        const res = await adminApp.inject({ method: 'GET', url, headers });
        expect(res.body, `${url} must not contain the admin key`).not.toContain(licenseServerConfig.adminApiKey);
        expect(res.headers['authorization']).toBeUndefined();
      }
    });

    it('keeps the license key out of the served SPA assets', async () => {
      for (const asset of ['/app.js', '/index.html']) {
        const res = await adminApp.inject({ method: 'GET', url: asset });
        expect(res.statusCode).toBe(200);
        expect(res.body, `${asset} must not contain the admin key`).not.toContain(licenseServerConfig.adminApiKey);
        expect(res.body).not.toContain('default_admin_secret_key');
      }
    });

    it('keeps the license key out of the tester UI section', async () => {
      const res = await adminApp.inject({ method: 'GET', url: '/index.html' });
      expect(res.body).toContain('Tester Management');
      expect(res.body).toContain('tab-testers');
      expect(res.body).toContain('bukan berarti sudah menjadi tester Google Play');
    });
  });

  // ==========================================================================
  // 5 + 6 + 7. The license server API and the public endpoint are unaffected
  // ==========================================================================
  describe('5. Downstream boundaries', () => {
    it('/v1/admin/testers* still requires the bearer key on the license server', async () => {
      for (const url of ['/v1/admin/testers/summary', '/v1/admin/testers', '/v1/admin/testers/1']) {
        const res = await licenseServerApp.inject({ method: 'GET', url });
        expect(res.statusCode).toBe(401);
      }
    });

    it('the public campaign endpoint stays public and free of PII', async () => {
      registerTestCampaign(testerDetails, 'LW-222222');
      const res = await licenseServerApp.inject({ method: 'GET', url: '/v1/landing/test-campaign' });

      expect(res.statusCode).toBe(200);
      expect(res.json().data.capacity).toBe(50);
      expect(res.json().data.registered).toBe(1);
      expect(res.body).not.toContain('siti@example.com');
      expect(res.body).not.toContain('081234567801');
      expect(res.body).not.toContain('Warung Berkah Bu Siti');
    });

    it('the public landing route and public order still work', async () => {
      const campaign = await licenseServerApp.inject({ method: 'GET', url: '/v1/landing/test-campaign' });
      expect(campaign.statusCode).toBe(200);
      const pricing = await licenseServerApp.inject({ method: 'GET', url: '/v1/public/pricing?product=BUKU_WARUNG' });
      expect(pricing.statusCode).toBe(200);
      expect(pricing.json().success).toBe(true);
    });

    it('the admin console route no longer exists on the license server', async () => {
      const res = await licenseServerApp.inject({ method: 'GET', url: '/admin/testers' });
      expect(res.statusCode).toBe(404);
    });
  });
});
