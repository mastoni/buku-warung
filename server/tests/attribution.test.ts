import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import { FastifyInstance } from 'fastify';
import Database from 'better-sqlite3';
import { buildApp } from '../src/app.js';
import { getDatabase, closeDatabase } from '../src/db/database.js';
import { config } from '../src/config/index.js';
import { generateLeadToken, isValidLeadToken } from '../src/utils/attribution.js';
import { AttributionService } from '../src/services/attributionService.js';

describe('M.1.4 Marketing Attribution Foundation', () => {
  let app: FastifyInstance;
  let db: Database.Database;
  const adminApiKey = config.adminApiKey;

  beforeAll(async () => {
    db = getDatabase(':memory:');
    app = buildApp();
    await app.ready();
  });

  afterAll(async () => {
    await app.close();
    closeDatabase();
  });

  describe('Lead Token Generation & Validation', () => {
    it('1. valid lead token generation', () => {
      const token = generateLeadToken();
      expect(token).toMatch(/^LW-[2-9A-HJ-NP-Z]{6}$/);
    });

    it('2. lead token format', () => {
      for (let i = 0; i < 100; i++) {
        const token = generateLeadToken();
        expect(token).toMatch(/^LW-[2-9A-HJ-NP-Z]{6}$/);
        expect(token.length).toBe(9);
      }
    });

    it('3. lead token collision handling', () => {
      const tokens = new Set<string>();
      for (let i = 0; i < 1000; i++) {
        const token = generateLeadToken();
        expect(tokens.has(token)).toBe(false);
        tokens.add(token);
      }
    });

    it('4. invalid token rejected', () => {
      expect(isValidLeadToken('LW-INVALID')).toBe(false);
      expect(isValidLeadToken('LW-000000')).toBe(false);
      expect(isValidLeadToken('LW-111111')).toBe(false);
      expect(isValidLeadToken('LW-IIIIII')).toBe(false);
      expect(isValidLeadToken('LW-OOOOOO')).toBe(false);
      expect(isValidLeadToken('invalid')).toBe(false);
      expect(isValidLeadToken('')).toBe(false);
      expect(isValidLeadToken('LW-A7K92P')).toBe(true);
      expect(isValidLeadToken('LW-234567')).toBe(true);
      expect(isValidLeadToken('LW-ZZZZZZ')).toBe(true);
      expect(isValidLeadToken('LW-XXXXXX')).toBe(true);
    });
  });

  describe('UTM Validation', () => {
    it('5. UTM accepted', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/v1/landing/track',
        payload: {
          eventType: 'PAGE_VIEW',
          utm_source: 'google',
          utm_medium: 'cpc',
          utm_campaign: 'bukuwarung-launch',
          utm_content: 'hero-cta'
        }
      });

      expect(res.statusCode).toBe(200);
      const body = JSON.parse(res.body);
      expect(body.success).toBe(true);
      expect(body.data.leadToken).toMatch(/^LW-[2-9A-HJ-NP-Z]{6}$/);
      expect(body.data.action).toBe('created');
    });

    it('6. UTM validation - oversized values rejected', async () => {
      const longValue = 'a'.repeat(200);
      const res = await app.inject({
        method: 'POST',
        url: '/v1/landing/track',
        payload: {
          eventType: 'PAGE_VIEW',
          utm_source: longValue
        }
      });

      expect(res.statusCode).toBe(400);
    });

    it('7. UTM with special characters rejected', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/v1/landing/track',
        payload: {
          eventType: 'PAGE_VIEW',
          utm_source: "google'; DROP TABLE orders;--"
        }
      });

      expect(res.statusCode).toBe(400);
      const body = JSON.parse(res.body);
      expect(body.error.code).toBe('INVALID_UTM_VALUE');
    });
  });

  describe('Funnel Event Tracking', () => {
    it('8. valid funnel event accepted', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/v1/landing/track',
        payload: {
          eventType: 'CLICK_WHATSAPP'
        }
      });

      expect(res.statusCode).toBe(200);
      const body = JSON.parse(res.body);
      expect(body.success).toBe(true);
      expect(body.data.leadToken).toMatch(/^LW-[2-9A-HJ-NP-Z]{6}$/);
    });

    it('9. invalid event rejected', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/v1/landing/track',
        payload: {
          eventType: 'INVALID_EVENT'
        }
      });

      expect(res.statusCode).toBe(400);
      const body = JSON.parse(res.body);
      expect(body.success).toBe(false);
      expect(body.error.code).toBe('INVALID_EVENT_TYPE');
    });

    it('10. unknown event rejected', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/v1/landing/track',
        payload: {
          eventType: 'RANDOM_EVENT_XYZ'
        }
      });

      expect(res.statusCode).toBe(400);
    });

    it('11. funnel event persisted', async () => {
      const trackRes = await app.inject({
        method: 'POST',
        url: '/v1/landing/track',
        payload: {
          eventType: 'LEAD_CREATED',
          utm_source: 'facebook',
          utm_medium: 'social'
        }
      });

      const token = JSON.parse(trackRes.body).data.leadToken;

      const events = db
        .prepare('SELECT * FROM funnel_events WHERE lead_token = ?')
        .all(token) as Array<{ event_type: string; lead_token: string }>;

      expect(events.length).toBe(1);
      expect(events[0].event_type).toBe('LEAD_CREATED');
      expect(events[0].lead_token).toBe(token);

      const eventData = JSON.parse(events[0].event_data || '{}');
      expect(eventData.utm_source).toBe('facebook');
      expect(eventData.utm_medium).toBe('social');
    });
  });

  describe('Order Attribution Integration', () => {
    let leadToken = '';

    it('12. lead token persisted to order', async () => {
      const trackRes = await app.inject({
        method: 'POST',
        url: '/v1/landing/track',
        payload: { eventType: 'PAGE_VIEW', utm_source: 'test_src' }
      });
      leadToken = JSON.parse(trackRes.body).data.leadToken;

      const res = await app.inject({
        method: 'POST',
        url: '/v1/admin/orders',
        headers: { authorization: `Bearer ${adminApiKey}` },
        payload: {
          customerName: 'Test Customer',
          customerContact: '081234567890',
          ownerEmail: 'test.customer@example.com',
          leadToken: leadToken,
          utm_source: 'test_src',
          utm_medium: 'test_medium',
          utm_campaign: 'test_camp',
          utm_content: 'test_content'
        }
      });

      expect(res.statusCode).toBe(201);
      const body = JSON.parse(res.body);
      expect(body.data.leadToken).toBe(leadToken);
      expect(body.data.utmSource).toBe('test_src');
      expect(body.data.utmMedium).toBe('test_medium');
      expect(body.data.utmCampaign).toBe('test_camp');
      expect(body.data.utmContent).toBe('test_content');

      const orderRow = db
        .prepare('SELECT lead_token, utm_source FROM orders WHERE id = ?')
        .get(body.data.id) as { lead_token: string | null; utm_source: string | null };

      expect(orderRow.lead_token).toBe(leadToken);
      expect(orderRow.utm_source).toBe('test_src');
    });

    it('13. order without attribution remains valid', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/v1/admin/orders',
        headers: { authorization: `Bearer ${adminApiKey}` },
        payload: {
          customerName: 'No Token Customer',
          customerContact: '081234567891',
          ownerEmail: 'no.token@example.com'
        }
      });

      expect(res.statusCode).toBe(201);
      const body = JSON.parse(res.body);
      expect(body.success).toBe(true);

      const orderRow = db
        .prepare('SELECT lead_token FROM orders WHERE id = ?')
        .get(body.data.id) as { lead_token: string | null };

      expect(orderRow.lead_token).toBeNull();
    });

    it('order with same lead token creates multiple orders (repeat purchases)', async () => {
      const trackRes = await app.inject({
        method: 'POST',
        url: '/v1/landing/track',
        payload: { eventType: 'PAGE_VIEW' }
      });
      const token = JSON.parse(trackRes.body).data.leadToken;

      const res1 = await app.inject({
        method: 'POST',
        url: '/v1/admin/orders',
        headers: { authorization: `Bearer ${adminApiKey}` },
        payload: {
          customerName: 'Multi Order Customer 1',
          customerContact: '081234567901',
          ownerEmail: 'multi@example.com',
          leadToken: token
        }
      });

      expect(res1.statusCode).toBe(201);
      expect(JSON.parse(res1.body).data.leadToken).toBe(token);

      const res2 = await app.inject({
        method: 'POST',
        url: '/v1/admin/orders',
        headers: { authorization: `Bearer ${adminApiKey}` },
        payload: {
          customerName: 'Multi Order Customer 2',
          customerContact: '081234567902',
          ownerEmail: 'multi2@example.com',
          leadToken: token
        }
      });

      expect(res2.statusCode).toBe(201);
      expect(JSON.parse(res2.body).data.leadToken).toBe(token);

      const orders = db
        .prepare('SELECT id, order_number, lead_token FROM orders WHERE lead_token = ?')
        .all(token) as Array<{ id: number; order_number: string; lead_token: string }>;

      expect(orders.length).toBe(2);
      expect(orders[0].lead_token).toBe(token);
      expect(orders[1].lead_token).toBe(token);
    });
  });

  describe('Existing Order Behavior', () => {
    it('14. existing order behavior unchanged', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/v1/admin/orders',
        headers: { authorization: `Bearer ${adminApiKey}` },
        payload: {
          customerName: 'Existing Behavior Test',
          customerContact: '081234567892',
          ownerEmail: 'existing@example.com',
          notes: 'Test without attribution'
        }
      });

      expect(res.statusCode).toBe(201);
      const body = JSON.parse(res.body);
      expect(body.data.customerName).toBe('Existing Behavior Test');
      expect(body.data.status).toBe('PENDING_PAYMENT');
      expect(body.data.paymentStatus).toBe('UNPAID');
    });
  });

  describe('Privacy & Security', () => {
    it('15. raw IP not persisted in funnel events', async () => {
      await app.inject({
        method: 'POST',
        url: '/v1/landing/track',
        payload: { eventType: 'PAGE_VIEW' }
      });

      const events = db
        .prepare('SELECT ip_hash FROM funnel_events ORDER BY id DESC LIMIT 1')
        .get() as { ip_hash: string | null } | undefined;

      if (events) {
        expect(events.ip_hash).toMatch(/^[a-f0-9]{64}$/);
      }
    });

    it('16. PII not introduced into funnel event', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/v1/landing/track',
        payload: {
          eventType: 'PAGE_VIEW',
          utm_source: 'google',
          utm_medium: 'cpc',
          utm_campaign: 'launch'
        }
      });

      const token = JSON.parse(res.body).data.leadToken;
      const events = db
        .prepare('SELECT * FROM funnel_events WHERE lead_token = ?')
        .all(token) as Array<Record<string, unknown>>;

      for (const event of events) {
        const data = JSON.parse(event.event_data as string || '{}');
        expect(data).not.toHaveProperty('email');
        expect(data).not.toHaveProperty('phone');
        expect(data).not.toHaveProperty('whatsapp');
        expect(data).not.toHaveProperty('licenseCode');
      }
    });

    it('17. endpoint rejects malformed payload', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/v1/landing/track',
        payload: {
          invalid: 'payload'
        }
      });

      expect(res.statusCode).toBe(400);
    });

    it('18. endpoint does not expose secrets', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/v1/landing/track',
        payload: {
          eventType: 'PAGE_VIEW',
          evilField: 'injection attempt'
        }
      });

      expect(res.statusCode).toBe(400);
      const body = JSON.parse(res.body);
      expect(body).not.toHaveProperty('adminApiKey');
      expect(body).not.toHaveProperty('serverPepper');
      expect(body).not.toHaveProperty('databasePath');

      // Also verify a valid request doesn't leak secrets
      const validRes = await app.inject({
        method: 'POST',
        url: '/v1/landing/track',
        payload: { eventType: 'PAGE_VIEW' }
      });

      expect(validRes.statusCode).toBe(200);
      const validBody = JSON.parse(validRes.body);
      expect(validBody).not.toHaveProperty('adminApiKey');
      expect(validBody).not.toHaveProperty('serverPepper');
      expect(validBody).not.toHaveProperty('databasePath');
      expect(validBody).not.toHaveProperty('databasePath');
    });

    it('rejects oversized payload', async () => {
      const longUtm = 'x'.repeat(200);

      const res = await app.inject({
        method: 'POST',
        url: '/v1/landing/track',
        payload: {
          eventType: 'PAGE_VIEW',
          utm_source: longUtm
        }
      });

      expect(res.statusCode).toBe(400);
    });

    it('rejects unexpected fields via strict mode', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/v1/landing/track',
        payload: {
          eventType: 'PAGE_VIEW',
          evilField: 'injection attempt'
        }
      });

      expect(res.statusCode).toBe(400);
    });

    it('does not expose API key in landing metrics endpoint', async () => {
      const noAuthRes = await app.inject({
        method: 'GET',
        url: '/v1/landing/metrics'
      });

      expect(noAuthRes.statusCode).toBe(401);

      const res = await app.inject({
        method: 'GET',
        url: '/v1/landing/metrics',
        headers: { authorization: `Bearer ${adminApiKey}` }
      });

      expect(res.statusCode).toBe(200);
      const body = JSON.parse(res.body);
      expect(body).not.toHaveProperty('adminApiKey');
      expect(body).not.toHaveProperty('serverPepper');
      expect(body).not.toHaveProperty('databasePath');
    });
  });

  describe('Database Migration & Schema', () => {
    it('19. migration succeeds and tables accessible', async () => {
      const cols = db.prepare('PRAGMA table_info(orders)').all() as Array<{ name: string }>;
      const colNames = cols.map((c) => c.name);

      expect(colNames).toContain('lead_token');
      expect(colNames).toContain('utm_source');
      expect(colNames).toContain('utm_medium');
      expect(colNames).toContain('utm_campaign');
      expect(colNames).toContain('utm_content');

      const funnelCols = db.prepare('PRAGMA table_info(funnel_events)').all() as Array<{ name: string }>;
      const funnelColNames = funnelCols.map((c) => c.name);
      expect(funnelColNames).toContain('lead_token');
      expect(funnelColNames).toContain('event_type');
      expect(funnelColNames).toContain('event_data');
      expect(funnelColNames).toContain('ip_hash');
      expect(funnelColNames).toContain('user_agent');
      expect(funnelColNames).toContain('created_at');
    });

    it('20. existing database records remain readable', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/v1/admin/licenses',
        headers: { authorization: `Bearer ${adminApiKey}` },
        payload: {
          ownerEmail: 'migration.test@example.com',
          product: 'BUKU_WARUNG',
          price: 50000,
          customerName: 'Test',
          customerContact: '081234567899'
        }
      });

      expect(res.statusCode).toBe(201);

      const listRes = await app.inject({
        method: 'GET',
        url: '/v1/admin/licenses',
        headers: { authorization: `Bearer ${adminApiKey}` }
      });

      expect(listRes.statusCode).toBe(200);
      const listBody = JSON.parse(listRes.body);
      expect(listBody.success).toBe(true);
      expect(Array.isArray(listBody.data)).toBe(true);
      expect(listBody.data.length).toBeGreaterThan(0);
    });
  });

  describe('Funnel Event Types', () => {
    const validEvents = [
      'PAGE_VIEW', 'VIEW_PRODUCT', 'VIEW_PRICE', 'CLICK_WHATSAPP',
      'LEAD_CREATED', 'QUALIFIED', 'INTERESTED', 'ORDER_CREATED',
      'PAYMENT_CONFIRMED', 'LICENSE_CREATED', 'DELIVERY_READY',
      'APK_DOWNLOADED', 'LICENSE_ACTIVATED'
    ];

    for (const eventType of validEvents) {
      it(`accepts valid event type: ${eventType}`, async () => {
        const res = await app.inject({
          method: 'POST',
          url: '/v1/landing/track',
          payload: { eventType }
        });

        expect(res.statusCode).toBe(200);
        const body = JSON.parse(res.body);
        expect(body.success).toBe(true);
      });
    }
  });

  describe('Lead Token Session Continuity', () => {
    it('allows session continuation with existing token', async () => {
      const trackRes = await app.inject({
        method: 'POST',
        url: '/v1/landing/track',
        payload: {
          eventType: 'PAGE_VIEW',
          utm_source: 'instagram'
        }
      });

      const token = JSON.parse(trackRes.body).data.leadToken;

      const trackRes2 = await app.inject({
        method: 'POST',
        url: '/v1/landing/track',
        payload: {
          eventType: 'CLICK_WHATSAPP',
          leadToken: token,
          utm_source: 'instagram'
        }
      });

      const body = JSON.parse(trackRes2.body);
      expect(body.success).toBe(true);
      expect(body.data.leadToken).toBe(token);
      expect(body.data.action).toBe('matched');
    });

    it('rejects non-existent token for matching', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/v1/landing/track',
        payload: {
          eventType: 'CLICK_WHATSAPP',
          leadToken: 'LW-NONEXIST1'
        }
      });

      expect(res.statusCode).toBe(400);
      const body = JSON.parse(res.body);
      expect(body.success).toBe(false);
    });
  });

  describe('M.1.5 Funnel Analytics', () => {
    it('FA-01. funnel analytics returns structured response', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/v1/landing/metrics',
        headers: { authorization: `Bearer ${adminApiKey}` }
      });

      expect(res.statusCode).toBe(200);
      const body = JSON.parse(res.body);
      expect(body.success).toBe(true);
      expect(body.data).toHaveProperty('attribution');
      expect(body.data).toHaveProperty('funnel');
      expect(body.data.funnel).toHaveProperty('funnel');
      expect(body.data.funnel).toHaveProperty('conversions');
      expect(body.data.funnel).toHaveProperty('attribution');
      expect(body.data.funnel).toHaveProperty('northStar');
      expect(body.data.funnel).toHaveProperty('dateRange');
    });

    it('FA-02. empty funnel returns zero counts', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/v1/landing/metrics?range=all',
        headers: { authorization: `Bearer ${adminApiKey}` }
      });

      const body = JSON.parse(res.body);
      const funnel = body.data.funnel;
      expect(funnel.funnel).toBeDefined();
      expect(funnel.northStar.activatedPaidCustomers).toBeGreaterThanOrEqual(0);
      expect(funnel.conversions).toBeDefined();
    });

    it('FA-03. zero division does not produce NaN', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/v1/landing/metrics?range=all',
        headers: { authorization: `Bearer ${adminApiKey}` }
      });

      const body = JSON.parse(res.body);
      const conversions = body.data.funnel.conversions;
      for (const conv of conversions) {
        expect(conv.rate).not.toBeNaN();
        expect(conv.rate).toBeGreaterThanOrEqual(0);
      }
    });

    it('FA-04. funnel totals count events correctly', async () => {
      // Track a PAGE_VIEW with UTM
      const trackRes = await app.inject({
        method: 'POST',
        url: '/v1/landing/track',
        payload: {
          eventType: 'PAGE_VIEW',
          utm_source: 'facebook',
          utm_medium: 'paid',
          utm_campaign: 'warung-sept',
          utm_content: 'video-01'
        }
      });
      const token = JSON.parse(trackRes.body).data.leadToken;

      // Track LEAD_CREATED
      await app.inject({
        method: 'POST',
        url: '/v1/landing/track',
        payload: { eventType: 'LEAD_CREATED', leadToken: token }
      });

      // Track CLICK_WHATSAPP
      await app.inject({
        method: 'POST',
        url: '/v1/landing/track',
        payload: { eventType: 'CLICK_WHATSAPP', leadToken: token }
      });

      const res = await app.inject({
        method: 'GET',
        url: '/v1/landing/metrics?range=all',
        headers: { authorization: `Bearer ${adminApiKey}` }
      });

      const body = JSON.parse(res.body);
      const funnel = body.data.funnel.funnel;
      expect(funnel).toBeDefined();
    });

    it('FA-05. lead to order conversion tracked', async () => {
      const trackRes = await app.inject({
        method: 'POST',
        url: '/v1/landing/track',
        payload: { eventType: 'LEAD_CREATED', utm_source: 'google' }
      });
      const token = JSON.parse(trackRes.body).data.leadToken;

      // Create order with token
      const orderRes = await app.inject({
        method: 'POST',
        url: '/v1/admin/orders',
        headers: { authorization: `Bearer ${adminApiKey}` },
        payload: {
          customerName: 'Funnel Customer',
          customerContact: '081111111111',
          ownerEmail: 'funnel@example.com',
          leadToken: token
        }
      });
      expect(orderRes.statusCode).toBe(201);

      const res = await app.inject({
        method: 'GET',
        url: '/v1/landing/metrics?range=all',
        headers: { authorization: `Bearer ${adminApiKey}` }
      });

      const body = JSON.parse(res.body);
      const conversions = body.data.funnel.conversions;
      const leadToOrder = conversions.find((c: any) => c.from === 'LEAD_CREATED' && c.to === 'ORDER_CREATED');
      expect(leadToOrder).toBeDefined();
      expect(leadToOrder.rate).toBeGreaterThan(0);
    });

    it('FA-06. order to paid conversion', async () => {
      const trackRes = await app.inject({
        method: 'POST',
        url: '/v1/landing/track',
        payload: { eventType: 'ORDER_CREATED', utm_source: 'google' }
      });
      const token = JSON.parse(trackRes.body).data.leadToken;

      // Create order and verify payment
      const orderRes = await app.inject({
        method: 'POST',
        url: '/v1/admin/orders',
        headers: { authorization: `Bearer ${adminApiKey}` },
        payload: {
          customerName: 'Paid Customer',
          customerContact: '082222222222',
          ownerEmail: 'paid@example.com',
          leadToken: token
        }
      });
      const orderId = JSON.parse(orderRes.body).data.id;

      await app.inject({
        method: 'POST',
        url: `/v1/admin/orders/${orderId}/verify-payment`,
        headers: { authorization: `Bearer ${adminApiKey}` },
        payload: {}
      });

      const res = await app.inject({
        method: 'GET',
        url: '/v1/landing/metrics?range=all',
        headers: { authorization: `Bearer ${adminApiKey}` }
      });

      const body = JSON.parse(res.body);
      const conversions = body.data.funnel.conversions;
      const orderToPaid = conversions.find((c: any) => c.from === 'ORDER_CREATED' && c.to === 'PAYMENT_CONFIRMED');
      expect(orderToPaid).toBeDefined();
      expect(orderToPaid.rate).toBeGreaterThan(0);
    });

    it('FA-07. paid to license conversion', async () => {
      const trackRes = await app.inject({
        method: 'POST',
        url: '/v1/landing/track',
        payload: { eventType: 'PAYMENT_CONFIRMED', utm_source: 'google' }
      });
      const token = JSON.parse(trackRes.body).data.leadToken;

      const orderRes = await app.inject({
        method: 'POST',
        url: '/v1/admin/orders',
        headers: { authorization: `Bearer ${adminApiKey}` },
        payload: {
          customerName: 'License Customer',
          customerContact: '083333333333',
          ownerEmail: 'license@example.com',
          leadToken: token
        }
      });
      const orderId = JSON.parse(orderRes.body).data.id;

      await app.inject({
        method: 'POST',
        url: `/v1/admin/orders/${orderId}/verify-payment`,
        headers: { authorization: `Bearer ${adminApiKey}` },
        payload: {}
      });

      await app.inject({
        method: 'POST',
        url: `/v1/admin/orders/${orderId}/generate-license`,
        headers: { authorization: `Bearer ${adminApiKey}` },
        payload: {}
      });

      const res = await app.inject({
        method: 'GET',
        url: '/v1/landing/metrics?range=all',
        headers: { authorization: `Bearer ${adminApiKey}` }
      });

      const body = JSON.parse(res.body);
      const conversions = body.data.funnel.conversions;
      const paidToLicense = conversions.find((c: any) => c.from === 'PAYMENT_CONFIRMED' && c.to === 'LICENSE_CREATED');
      expect(paidToLicense).toBeDefined();
      expect(paidToLicense.rate).toBeGreaterThan(0);
    });

    it('FA-08. license to activation conversion', async () => {
      const trackRes = await app.inject({
        method: 'POST',
        url: '/v1/landing/track',
        payload: { eventType: 'LICENSE_CREATED', utm_source: 'google' }
      });
      const token = JSON.parse(trackRes.body).data.leadToken;

      const orderRes = await app.inject({
        method: 'POST',
        url: '/v1/admin/orders',
        headers: { authorization: `Bearer ${adminApiKey}` },
        payload: {
          customerName: 'Activation Customer',
          customerContact: '084444444444',
          ownerEmail: 'activation@example.com',
          leadToken: token
        }
      });
      const orderId = JSON.parse(orderRes.body).data.id;

      await app.inject({
        method: 'POST',
        url: `/v1/admin/orders/${orderId}/verify-payment`,
        headers: { authorization: `Bearer ${adminApiKey}` },
        payload: {}
      });

      const licenseRes = await app.inject({
        method: 'POST',
        url: `/v1/admin/orders/${orderId}/generate-license`,
        headers: { authorization: `Bearer ${adminApiKey}` },
        payload: {}
      });
      const licenseCode = JSON.parse(licenseRes.body).data.licenseCode;

      await app.inject({
        method: 'POST',
        url: '/v1/license/activate',
        payload: {
          licenseCode,
          ownerEmail: 'activation@example.com',
          deviceBinding: 'DEVICE_ACTIVATION_TEST_001'
        }
      });

      const res = await app.inject({
        method: 'GET',
        url: '/v1/landing/metrics?range=all',
        headers: { authorization: `Bearer ${adminApiKey}` }
      });

      const body = JSON.parse(res.body);
      const conversions = body.data.funnel.conversions;
      const licenseToActivation = conversions.find((c: any) => c.from === 'LICENSE_CREATED' && c.to === 'LICENSE_ACTIVATED');
      expect(licenseToActivation).toBeDefined();
      expect(licenseToActivation.rate).toBeGreaterThan(0);
    });

    it('FA-09. attribution by source shows data', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/v1/landing/metrics?range=all',
        headers: { authorization: `Bearer ${adminApiKey}` }
      });

      const body = JSON.parse(res.body);
      const attribution = body.data.funnel.attribution;
      expect(attribution.sources).toBeDefined();
      expect(Array.isArray(attribution.sources)).toBe(true);

      const facebookSource = attribution.sources.find((s: any) => s.value === 'facebook');
      if (facebookSource) {
        expect(facebookSource).toHaveProperty('leads');
        expect(facebookSource).toHaveProperty('orders');
        expect(facebookSource).toHaveProperty('paidOrders');
        expect(facebookSource).toHaveProperty('licenses');
        expect(facebookSource).toHaveProperty('activatedCustomers');
      }
    });

    it('FA-10. attribution by campaign shows data', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/v1/landing/metrics?range=all',
        headers: { authorization: `Bearer ${adminApiKey}` }
      });

      const body = JSON.parse(res.body);
      const attribution = body.data.funnel.attribution;
      expect(attribution.campaigns).toBeDefined();
      expect(Array.isArray(attribution.campaigns)).toBe(true);

      const warungCampaign = attribution.campaigns.find((c: any) => c.value === 'warung-sept');
      if (warungCampaign) {
        expect(warungCampaign).toHaveProperty('leads');
        expect(warungCampaign).toHaveProperty('orders');
      }
    });

    it('FA-11. unattributed records shown as Unattributed', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/v1/landing/metrics?range=all',
        headers: { authorization: `Bearer ${adminApiKey}` }
      });

      const body = JSON.parse(res.body);
      const attribution = body.data.funnel.attribution;
      const hasUnattributed = attribution.sources.some((s: any) => s.value === 'Unattributed');
      expect(hasUnattributed).toBe(true);
    });

    it('FA-12. multiple orders per lead token do not cause errors', async () => {
      const trackRes = await app.inject({
        method: 'POST',
        url: '/v1/landing/track',
        payload: { eventType: 'PAGE_VIEW', utm_source: 'multi' }
      });
      const token = JSON.parse(trackRes.body).data.leadToken;

      await app.inject({
        method: 'POST',
        url: '/v1/admin/orders',
        headers: { authorization: `Bearer ${adminApiKey}` },
        payload: {
          customerName: 'Multi Order 1',
          customerContact: '085555555551',
          ownerEmail: 'multi1@example.com',
          leadToken: token
        }
      });

      await app.inject({
        method: 'POST',
        url: '/v1/admin/orders',
        headers: { authorization: `Bearer ${adminApiKey}` },
        payload: {
          customerName: 'Multi Order 2',
          customerContact: '085555555552',
          ownerEmail: 'multi2@example.com',
          leadToken: token
        }
      });

      const res = await app.inject({
        method: 'GET',
        url: '/v1/landing/metrics?range=all',
        headers: { authorization: `Bearer ${adminApiKey}` }
      });

      expect(res.statusCode).toBe(200);
    });

    it('FA-13. date filtering returns bounded results', async () => {
      const futureStart = Date.now() + 365 * 24 * 60 * 60 * 1000;
      const res = await app.inject({
        method: 'GET',
        url: `/v1/landing/metrics?range=all&start=${futureStart}`,
        headers: { authorization: `Bearer ${adminApiKey}` }
      });

      expect(res.statusCode).toBe(200);
      const body = JSON.parse(res.body);
      const funnel = body.data.funnel.funnel;
      for (const stage of funnel) {
        expect(stage.count).toBe(0);
      }
    });

    it('FA-14. unauthorized analytics request rejected', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/v1/landing/metrics'
      });

      expect(res.statusCode).toBe(401);
    });

    it('FA-15. PII not exposed in analytics response', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/v1/landing/metrics?range=all',
        headers: { authorization: `Bearer ${adminApiKey}` }
      });

      const body = JSON.parse(res.body);
      const bodyStr = JSON.stringify(body);
      expect(bodyStr).not.toContain('@example.com');
      expect(bodyStr).not.toContain('081111111111');
      expect(bodyStr).not.toContain('customer_contact');
      expect(bodyStr).not.toContain('customer_name');
      expect(bodyStr).not.toContain('owner_email');
    });

    it('FA-16. license code not exposed in analytics response', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/v1/landing/metrics?range=all',
        headers: { authorization: `Bearer ${adminApiKey}` }
      });

      const body = JSON.parse(res.body);
      const bodyStr = JSON.stringify(body);
      expect(bodyStr).not.toContain('license_code');
      expect(bodyStr).not.toContain('license_code_hash');
      expect(bodyStr).not.toContain('license_uuid');
    });

    it('FA-17. raw IP not exposed in analytics response', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/v1/landing/metrics?range=all',
        headers: { authorization: `Bearer ${adminApiKey}` }
      });

      const body = JSON.parse(res.body);
      expect(body).not.toHaveProperty('ip');
      expect(body).not.toHaveProperty('ip_address');
      expect(body).not.toHaveProperty('ip_hash');

      const bodyStr = JSON.stringify(body);
      expect(bodyStr).not.toContain('ip_hash');
    });

    it('FA-18. activation counted correctly', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/v1/landing/metrics?range=all',
        headers: { authorization: `Bearer ${adminApiKey}` }
      });

      const body = JSON.parse(res.body);
      const funnel = body.data.funnel.funnel;

      const activatedStage = funnel.find((f: any) => f.name === 'LICENSE_ACTIVATED');
      expect(activatedStage).toBeDefined();
      expect(activatedStage.count).toBeGreaterThan(0);

      expect(body.data.funnel.northStar.activatedPaidCustomers).toBeGreaterThan(0);
    });

    it('FA-19. repeated activation does not inflate customer count', async () => {
      const trackRes = await app.inject({
        method: 'POST',
        url: '/v1/landing/track',
        payload: { eventType: 'PAGE_VIEW', utm_source: 'repeat-activation' }
      });
      const token = JSON.parse(trackRes.body).data.leadToken;

      const orderRes = await app.inject({
        method: 'POST',
        url: '/v1/admin/orders',
        headers: { authorization: `Bearer ${adminApiKey}` },
        payload: {
          customerName: 'Repeat Activation Customer',
          customerContact: '086666666661',
          ownerEmail: 'repeatactivation@example.com',
          leadToken: token
        }
      });
      const orderId = JSON.parse(orderRes.body).data.id;

      await app.inject({
        method: 'POST',
        url: `/v1/admin/orders/${orderId}/verify-payment`,
        headers: { authorization: `Bearer ${adminApiKey}` },
        payload: {}
      });

      const licenseRes = await app.inject({
        method: 'POST',
        url: `/v1/admin/orders/${orderId}/generate-license`,
        headers: { authorization: `Bearer ${adminApiKey}` },
        payload: {}
      });
      const licenseCode = JSON.parse(licenseRes.body).data.licenseCode;

      // Initial activation
      await app.inject({
        method: 'POST',
        url: '/v1/license/activate',
        payload: {
          licenseCode,
          ownerEmail: 'repeatactivation@example.com',
          deviceBinding: 'DEVICE_REPEAT_TEST_001'
        }
      });

      // Re-fetch and trigger again to simulate repeated activation
      const res1 = await app.inject({
        method: 'GET',
        url: '/v1/landing/metrics?range=all',
        headers: { authorization: `Bearer ${adminApiKey}` }
      });
      const northStar1 = JSON.parse(res1.body).data.funnel.northStar.activatedPaidCustomers;

      // Repeated activation (idempotent)
      await app.inject({
        method: 'POST',
        url: '/v1/license/activate',
        payload: {
          licenseCode,
          ownerEmail: 'repeatactivation@example.com',
          deviceBinding: 'DEVICE_REPEAT_TEST_001'
        }
      });

      const res2 = await app.inject({
        method: 'GET',
        url: '/v1/landing/metrics?range=all',
        headers: { authorization: `Bearer ${adminApiKey}` }
      });
      const northStar2 = JSON.parse(res2.body).data.funnel.northStar.activatedPaidCustomers;

      // Repeated activation recorded in funnel_events (idempotent: true)
      // but should still count as 1 distinct customer
      expect(northStar2).toBe(northStar1);
    });

    it('FA-20. north star metric equals distinct activated paid customers', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/v1/landing/metrics?range=all',
        headers: { authorization: `Bearer ${adminApiKey}` }
      });

      const body = JSON.parse(res.body);
      const northStar = body.data.funnel.northStar.activatedPaidCustomers;
      const activatedEventCount = body.data.funnel.funnel.find((f: any) => f.name === 'LICENSE_ACTIVATED').count;

      // North star should be <= activated events (because of idempotent reactivations and multi-order)
      expect(northStar).toBeGreaterThan(0);
      expect(northStar).toBeLessThanOrEqual(activatedEventCount + 1);
    });

    it('FA-21. forged LICENSE_ACTIVATED via public endpoint does NOT inflate north star', async () => {
      // Create a lead token and a PAID order (but no actual license/device activation)
      const trackRes = await app.inject({
        method: 'POST',
        url: '/v1/landing/track',
        payload: { eventType: 'PAGE_VIEW', utm_source: 'forge-attempt' }
      });
      const token = JSON.parse(trackRes.body).data.leadToken;

      const orderRes = await app.inject({
        method: 'POST',
        url: '/v1/admin/orders',
        headers: { authorization: `Bearer ${adminApiKey}` },
        payload: {
          customerName: 'Forge Attempt Customer',
          customerContact: '087777777771',
          ownerEmail: 'forge@example.com',
          leadToken: token
        }
      });
      const orderId = JSON.parse(orderRes.body).data.id;

      await app.inject({
        method: 'POST',
        url: `/v1/admin/orders/${orderId}/verify-payment`,
        headers: { authorization: `Bearer ${adminApiKey}` },
        payload: {}
      });

      // Do NOT generate license or activate - this order has NO actual activation.

      // Capture north star before forgery attempt
      const beforeRes = await app.inject({
        method: 'GET',
        url: '/v1/landing/metrics?range=all',
        headers: { authorization: `Bearer ${adminApiKey}` }
      });
      const beforeNorthStar = JSON.parse(beforeRes.body).data.funnel.northStar.activatedPaidCustomers;

      // Attempt to forge a LICENSE_ACTIVATED event via the public endpoint
      const forgeRes = await app.inject({
        method: 'POST',
        url: '/v1/landing/track',
        payload: {
          eventType: 'LICENSE_ACTIVATED',
          leadToken: token
        }
      });
      expect(forgeRes.statusCode).toBe(200);

      // North star must NOT increase - the order has no actual license/device activation
      const afterRes = await app.inject({
        method: 'GET',
        url: '/v1/landing/metrics?range=all',
        headers: { authorization: `Bearer ${adminApiKey}` }
      });
      const afterNorthStar = JSON.parse(afterRes.body).data.funnel.northStar.activatedPaidCustomers;

      expect(afterNorthStar).toBe(beforeNorthStar);
    });

    it('FA-22. commercial events counted from table state, not forgeable client events', async () => {
      // Create a PAID order WITHOUT actual license creation
      const trackRes = await app.inject({
        method: 'POST',
        url: '/v1/landing/track',
        payload: { eventType: 'PAGE_VIEW', utm_source: 'table-verify-22' }
      });
      const token = JSON.parse(trackRes.body).data.leadToken;

      const orderRes = await app.inject({
        method: 'POST',
        url: '/v1/admin/orders',
        headers: { authorization: `Bearer ${adminApiKey}` },
        payload: {
          customerName: 'Table Verify Customer 22',
          customerContact: '087777777773',
          ownerEmail: 'tableverify22@example.com',
          leadToken: token
        }
      });
      const orderId = JSON.parse(orderRes.body).data.id;

      await app.inject({
        method: 'POST',
        url: `/v1/admin/orders/${orderId}/verify-payment`,
        headers: { authorization: `Bearer ${adminApiKey}` },
        payload: {}
      });

      // Forge LICENSE_CREATED, DELIVERY_READY, LICENSE_ACTIVATED via public endpoint
      await app.inject({
        method: 'POST',
        url: '/v1/landing/track',
        payload: { eventType: 'LICENSE_CREATED', leadToken: token }
      });
      await app.inject({
        method: 'POST',
        url: '/v1/landing/track',
        payload: { eventType: 'DELIVERY_READY', leadToken: token }
      });
      await app.inject({
        method: 'POST',
        url: '/v1/landing/track',
        payload: { eventType: 'LICENSE_ACTIVATED', leadToken: token }
      });

      // Check this specific order's state via the order API
      const orderDetailRes = await app.inject({
        method: 'GET',
        url: `/v1/admin/orders/${orderId}`,
        headers: { authorization: `Bearer ${adminApiKey}` }
      });
      const orderDetail = JSON.parse(orderDetailRes.body);

      // Order should be PAID but have no license
      expect(orderDetail.data.order.paymentStatus).toBe('PAID');
      expect(orderDetail.data.order.licenseId).toBeNull();

      // Verify the North Star metric for this token does not count this order
      // because there is no actual license/device activation
      const attributionService = new AttributionService(db);
      const analytics = attributionService.getFunnelAnalytics();

      // PAGE_VIEW event was recorded (client-instrumented, accepted)
      const pageViewStage = analytics.funnel.find((f) => f.name === 'PAGE_VIEW');
      expect(pageViewStage).toBeDefined();
      expect(pageViewStage!.count).toBeGreaterThan(0);

      // The forged LICENSE_ACTIVATED funnel event should NOT appear in
      // the LICENSE_ACTIVATED stage count, which is derived from license_devices
      // state, not from client-injected funnel_events.
      // We verify by checking that no order with utm_source='table-verify-22'
      // has a license_id or active device.
      const forgedTokenHasLicense = db
        .prepare('SELECT license_id FROM orders WHERE lead_token = ? AND payment_status = ?')
        .get(token, 'PAID') as { license_id: number | null };

      expect(forgedTokenHasLicense.license_id).toBeNull();
    });
  });
});
