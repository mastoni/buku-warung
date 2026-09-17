import { describe, it, expect, beforeAll, afterAll, beforeEach } from 'vitest';
import { FastifyInstance } from 'fastify';
import Database from 'better-sqlite3';
import { buildApp } from '../src/app.js';
import { getDatabase, closeDatabase } from '../src/db/database.js';
import { config } from '../src/config/index.js';
import { PricingService } from '../src/services/pricingService.js';
import { AdminService } from '../src/services/adminService.js';

describe('Public Pricing API & Public Order Integration', () => {
  let app: FastifyInstance;
  let db: Database.Database;
  let pricingService: PricingService;
  let adminService: AdminService;
  const adminApiKey = config.adminApiKey;

  beforeAll(async () => {
    db = getDatabase(':memory:');
    pricingService = new PricingService(db);
    adminService = new AdminService(db);
    app = buildApp();
    await app.ready();
  });

  afterAll(async () => {
    await app.close();
    closeDatabase();
  });

  // Reset promotion to default active promo before each test group
  const resetPromoToDefault = () => {
    const now = Date.now();
    db.prepare(`
      UPDATE promotions
      SET name = 'Promo Peluncuran',
          enabled = 1,
          normal_price = 100000,
          promo_price = 50000,
          starts_at = ?,
          expires_at = ?,
          timezone = 'Asia/Jakarta',
          show_countdown = 1,
          updated_at = ?
      WHERE product = 'BUKU_WARUNG'
    `).run(now - 10000, now + 30 * 24 * 60 * 60 * 1000, now);
  };

  beforeEach(() => {
    resetPromoToDefault();
  });

  /* =========================================================================
   * 1. GET /v1/public/pricing
   * ========================================================================= */
  describe('1. GET /v1/public/pricing', () => {
    it('returns 200 with default product BUKU_WARUNG and correct public payload', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/v1/public/pricing'
      });

      expect(res.statusCode).toBe(200);
      const body = JSON.parse(res.body);
      expect(body.success).toBe(true);
      expect(body.data).toBeDefined();

      const data = body.data;
      expect(data.product).toBe('BUKU_WARUNG');
      expect(data.isPromoActive).toBe(true);
      expect(data.effectivePrice).toBe(50000);
      expect(data.normalPrice).toBe(100000);
      expect(data.promoPrice).toBe(50000);
      expect(data.currency).toBe('IDR');
      expect(data.promoName).toBe('Promo Peluncuran');
      expect(typeof data.startsAt).toBe('number');
      expect(typeof data.expiresAt).toBe('number');
      expect(data.showCountdown).toBe(true);
      expect(typeof data.serverTime).toBe('number');

      // Security check: No secret credentials or internal columns exposed
      expect(data.adminApiKey).toBeUndefined();
      expect(data.apiKey).toBeUndefined();
      expect(data.id).toBeUndefined();
      expect(data.created_at).toBeUndefined();
      expect(data.updated_at).toBeUndefined();
    });

    it('returns 200 when querying ?product=BUKU_WARUNG explicitly', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/v1/public/pricing?product=BUKU_WARUNG'
      });

      expect(res.statusCode).toBe(200);
      const body = JSON.parse(res.body);
      expect(body.success).toBe(true);
      expect(body.data.product).toBe('BUKU_WARUNG');
      expect(body.data.effectivePrice).toBe(50000);
    });

    it('returns 404 for unknown/nonexistent product', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/v1/public/pricing?product=NONEXISTENT_APP'
      });

      expect(res.statusCode).toBe(404);
      const body = JSON.parse(res.body);
      expect(body.success).toBe(false);
      expect(body.error.code).toBe('PRODUCT_NOT_FOUND');
    });

    it('reflects disabled promo state (isPromoActive = false, effectivePrice = normalPrice)', async () => {
      pricingService.updatePromotion('BUKU_WARUNG', { enabled: false }, 'TEST_ADMIN');

      const res = await app.inject({
        method: 'GET',
        url: '/v1/public/pricing'
      });

      expect(res.statusCode).toBe(200);
      const body = JSON.parse(res.body);
      expect(body.data.isPromoActive).toBe(false);
      expect(body.data.effectivePrice).toBe(100000);
      expect(body.data.normalPrice).toBe(100000);
      expect(body.data.promoPrice).toBe(50000);
    });

    it('reflects expired promo state (isPromoActive = false, effectivePrice = normalPrice)', async () => {
      const pastTime = Date.now() - 100000;
      pricingService.updatePromotion('BUKU_WARUNG', {
        startsAt: pastTime - 50000,
        expiresAt: pastTime - 1000
      }, 'TEST_ADMIN');

      const res = await app.inject({
        method: 'GET',
        url: '/v1/public/pricing'
      });

      expect(res.statusCode).toBe(200);
      const body = JSON.parse(res.body);
      expect(body.data.isPromoActive).toBe(false);
      expect(body.data.effectivePrice).toBe(100000);
    });

    it('reflects future/unstarted promo state (isPromoActive = false, effectivePrice = normalPrice)', async () => {
      const futureTime = Date.now() + 100000;
      pricingService.updatePromotion('BUKU_WARUNG', {
        startsAt: futureTime,
        expiresAt: futureTime + 200000
      }, 'TEST_ADMIN');

      const res = await app.inject({
        method: 'GET',
        url: '/v1/public/pricing'
      });

      expect(res.statusCode).toBe(200);
      const body = JSON.parse(res.body);
      expect(body.data.isPromoActive).toBe(false);
      expect(body.data.effectivePrice).toBe(100000);
    });
  });

  /* =========================================================================
   * 2. Authoritative Price Resolution & Order Creation
   * ========================================================================= */
  describe('2. POST /v1/public/orders Authoritative Price Resolution', () => {
    it('creates order with promo price (50.000) when promo is active', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/v1/public/orders',
        payload: {
          customerName: 'Promo Buyer',
          customerContact: '081211112222',
          ownerEmail: 'buyer.promo@warung.id'
        }
      });

      expect(res.statusCode).toBe(201);
      const body = JSON.parse(res.body);
      expect(body.success).toBe(true);
      expect(body.data.amount).toBe(50000);
      expect(body.data.qris.nominal).toBe(50000);

      const dbOrder = db.prepare('SELECT * FROM orders WHERE order_number = ?').get(body.data.orderNumber) as any;
      expect(dbOrder.amount).toBe(50000);
    });

    it('creates order with normal price (100.000) when promo is expired', async () => {
      // Set promo to expired
      const past = Date.now() - 50000;
      pricingService.updatePromotion('BUKU_WARUNG', {
        startsAt: past - 10000,
        expiresAt: past - 1000
      }, 'TEST_ADMIN');

      const res = await app.inject({
        method: 'POST',
        url: '/v1/public/orders',
        payload: {
          customerName: 'Expired Promo Buyer',
          customerContact: '081233334444',
          ownerEmail: 'buyer.expired@warung.id'
        }
      });

      expect(res.statusCode).toBe(201);
      const body = JSON.parse(res.body);
      expect(body.success).toBe(true);
      expect(body.data.amount).toBe(100000);
      expect(body.data.qris.nominal).toBe(100000);

      const dbOrder = db.prepare('SELECT * FROM orders WHERE order_number = ?').get(body.data.orderNumber) as any;
      expect(dbOrder.amount).toBe(100000);
    });

    it('creates order with normal price (100.000) when promo is disabled', async () => {
      pricingService.updatePromotion('BUKU_WARUNG', { enabled: false }, 'TEST_ADMIN');

      const res = await app.inject({
        method: 'POST',
        url: '/v1/public/orders',
        payload: {
          customerName: 'Disabled Promo Buyer',
          customerContact: '081255556666',
          ownerEmail: 'buyer.disabled@warung.id'
        }
      });

      expect(res.statusCode).toBe(201);
      const body = JSON.parse(res.body);
      expect(body.success).toBe(true);
      expect(body.data.amount).toBe(100000);
      expect(body.data.qris.nominal).toBe(100000);

      const dbOrder = db.prepare('SELECT * FROM orders WHERE order_number = ?').get(body.data.orderNumber) as any;
      expect(dbOrder.amount).toBe(100000);
    });

    it('strictly ignores client tampering attempts (amount: 1, price: 1, promoCode: fake)', async () => {
      // Active promo -> should be 50000, ignoring any client-sent fields
      const res = await app.inject({
        method: 'POST',
        url: '/v1/public/orders',
        payload: {
          customerName: 'Tamper Tester',
          customerContact: '081277778888',
          ownerEmail: 'tamper@warung.id',
          amount: 1,
          price: 1,
          effectivePrice: 1,
          promoCode: 'DISCOUNT100'
        }
      });

      expect(res.statusCode).toBe(201);
      const body = JSON.parse(res.body);
      expect(body.data.amount).toBe(50000);

      const dbOrder = db.prepare('SELECT * FROM orders WHERE order_number = ?').get(body.data.orderNumber) as any;
      expect(dbOrder.amount).toBe(50000);
    });
  });

  /* =========================================================================
   * 3. Immutable Snapshot & Historical Order Integrity
   * ========================================================================= */
  describe('3. Immutable Snapshot & Historical Order Integrity', () => {
    it('preserves historical order amount unchanged after admin updates promotion', async () => {
      // Step 1: Create Order 1 during promo (50.000)
      const res1 = await app.inject({
        method: 'POST',
        url: '/v1/public/orders',
        payload: {
          customerName: 'Customer Promo Snapshot',
          customerContact: '081311110001',
          ownerEmail: 'promo.snap@warung.id'
        }
      });
      expect(res1.statusCode).toBe(201);
      const order1 = JSON.parse(res1.body).data;
      expect(order1.amount).toBe(50000);

      // Step 2: Admin changes pricing configuration (e.g. promo expired / normal price updated to 150.000)
      pricingService.updatePromotion('BUKU_WARUNG', {
        enabled: false,
        normalPrice: 150000
      }, 'ADMIN');

      // Step 3: Verify existing Order 1 is completely untouched
      const dbOrder1 = db.prepare('SELECT * FROM orders WHERE order_number = ?').get(order1.orderNumber) as any;
      expect(dbOrder1.amount).toBe(50000);

      // Lookup via public capability token
      const lookup1 = await app.inject({
        method: 'GET',
        url: `/v1/public/orders/${order1.publicToken}`
      });
      expect(lookup1.statusCode).toBe(200);
      const lookupBody1 = JSON.parse(lookup1.body).data;
      expect(lookupBody1.amount).toBe(50000);
      expect(lookupBody1.qris.nominal).toBe(50000);

      // Step 4: Create new Order 2 under new config -> should resolve to 150.000
      const res2 = await app.inject({
        method: 'POST',
        url: '/v1/public/orders',
        payload: {
          customerName: 'Customer New Snapshot',
          customerContact: '081311110002',
          ownerEmail: 'new.snap@warung.id'
        }
      });
      expect(res2.statusCode).toBe(201);
      const order2 = JSON.parse(res2.body).data;
      expect(order2.amount).toBe(150000);
      expect(order2.qris.nominal).toBe(150000);
    });

    it('order submitted after expiry receives normal price even if form was opened earlier', async () => {
      // T0: form opened (promo active)
      // T1: promo expires
      const past = Date.now() - 50000;
      pricingService.updatePromotion('BUKU_WARUNG', {
        startsAt: past - 10000,
        expiresAt: past - 1000
      }, 'ADMIN');

      // T2: customer submits order
      const res = await app.inject({
        method: 'POST',
        url: '/v1/public/orders',
        payload: {
          customerName: 'Late Submission Buyer',
          customerContact: '081311110003',
          ownerEmail: 'late.buyer@warung.id'
        }
      });

      expect(res.statusCode).toBe(201);
      const body = JSON.parse(res.body);
      expect(body.data.amount).toBe(100000);
    });
  });

  /* =========================================================================
   * 4. Payment Verification & License Generation Invariants
   * ========================================================================= */
  describe('4. Payment Verification & License Generation Invariants', () => {
    it('generates license with price = 50000 from promo order', async () => {
      // 1. Create promo order (50.000)
      const res = await app.inject({
        method: 'POST',
        url: '/v1/public/orders',
        payload: {
          customerName: 'License Test Promo',
          customerContact: '081322220001',
          ownerEmail: 'lic.promo@warung.id'
        }
      });
      const order = JSON.parse(res.body).data;
      expect(order.amount).toBe(50000);

      const dbOrder = db.prepare('SELECT * FROM orders WHERE order_number = ?').get(order.orderNumber) as any;

      // 2. Admin verifies payment
      const verifyRes = await app.inject({
        method: 'POST',
        url: `/v1/admin/orders/${dbOrder.id}/verify-payment`,
        headers: { authorization: `Bearer ${adminApiKey}` },
        payload: {
          paymentMethod: 'QRIS — KIOS KIARA',
          paymentReference: 'REF-PROMO-123'
        }
      });
      expect(verifyRes.statusCode).toBe(200);

      // 3. Admin generates license
      const licRes = await app.inject({
        method: 'POST',
        url: `/v1/admin/orders/${dbOrder.id}/generate-license`,
        headers: { authorization: `Bearer ${adminApiKey}` }
      });
      expect(licRes.statusCode).toBe(200);
      const licData = JSON.parse(licRes.body).data;

      // Verify license record price equals order.amount (50000)
      const dbLic = db.prepare('SELECT * FROM licenses WHERE id = ?').get(licData.licenseId) as any;
      expect(dbLic.price).toBe(50000);
    });

    it('generates license with price = 100000 from normal order', async () => {
      // Promo expired
      const past = Date.now() - 50000;
      pricingService.updatePromotion('BUKU_WARUNG', {
        startsAt: past - 10000,
        expiresAt: past - 1000
      }, 'ADMIN');

      // 1. Create normal order (100.000)
      const res = await app.inject({
        method: 'POST',
        url: '/v1/public/orders',
        payload: {
          customerName: 'License Test Normal',
          customerContact: '081322220002',
          ownerEmail: 'lic.normal@warung.id'
        }
      });
      const order = JSON.parse(res.body).data;
      expect(order.amount).toBe(100000);

      const dbOrder = db.prepare('SELECT * FROM orders WHERE order_number = ?').get(order.orderNumber) as any;

      // 2. Admin verifies payment
      await app.inject({
        method: 'POST',
        url: `/v1/admin/orders/${dbOrder.id}/verify-payment`,
        headers: { authorization: `Bearer ${adminApiKey}` },
        payload: {
          paymentMethod: 'QRIS — KIOS KIARA',
          paymentReference: 'REF-NORMAL-456'
        }
      });

      // 3. Admin generates license
      const licRes = await app.inject({
        method: 'POST',
        url: `/v1/admin/orders/${dbOrder.id}/generate-license`,
        headers: { authorization: `Bearer ${adminApiKey}` }
      });
      expect(licRes.statusCode).toBe(200);
      const licData = JSON.parse(licRes.body).data;

      // Verify license record price equals order.amount (100000)
      const dbLic = db.prepare('SELECT * FROM licenses WHERE id = ?').get(licData.licenseId) as any;
      expect(dbLic.price).toBe(100000);

      // Change promo config now
      pricingService.updatePromotion('BUKU_WARUNG', { enabled: true, promoPrice: 45000 }, 'ADMIN');

      // Existing license must stay 100000
      const dbLicRecheck = db.prepare('SELECT * FROM licenses WHERE id = ?').get(licData.licenseId) as any;
      expect(dbLicRecheck.price).toBe(100000);
    });
  });

  /* =========================================================================
   * 5. Public Order UI Price Dynamic Reflection
   * ========================================================================= */
  describe('5. GET /beli/buku-warung Dynamic Price Reflection', () => {
    it('renders Rp 50.000 when promo is active', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/beli/buku-warung'
      });

      expect(res.statusCode).toBe(200);
      expect(res.body).toContain('Rp 50.000');
      expect(res.body).toContain('Lanjutkan Pesanan (Rp 50.000)');
      expect(res.body).toContain('Promo Peluncuran');
    });

    it('renders Rp 100.000 when promo is disabled', async () => {
      pricingService.updatePromotion('BUKU_WARUNG', { enabled: false }, 'ADMIN');

      const res = await app.inject({
        method: 'GET',
        url: '/beli/buku-warung'
      });

      expect(res.statusCode).toBe(200);
      expect(res.body).toContain('Rp 100.000');
      expect(res.body).toContain('Lanjutkan Pesanan (Rp 100.000)');
    });
  });
});
