import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import { FastifyInstance } from 'fastify';
import Database from 'better-sqlite3';
import { buildApp } from '../src/app.js';
import { getDatabase, closeDatabase } from '../src/db/database.js';
import { PricingService } from '../src/services/pricingService.js';
import { config } from '../src/config/index.js';

describe('Commercial Pricing & Promotion Foundation', () => {
  let app: FastifyInstance;
  let db: Database.Database;
  let pricingService: PricingService;
  const adminApiKey = config.adminApiKey;

  beforeAll(async () => {
    db = getDatabase(':memory:');
    pricingService = new PricingService(db);
    app = buildApp();
    await app.ready();
  });

  afterAll(async () => {
    await app.close();
    closeDatabase();
  });

  /* -------------------------------------------------------------------------
   * 1. Database & Initial Seeding
   * ------------------------------------------------------------------------- */
  it('1. Initializes promotions table and seeds default BUKU_WARUNG promotion', () => {
    const defaultPromo = pricingService.getPromotion('BUKU_WARUNG');
    expect(defaultPromo).not.toBeNull();
    expect(defaultPromo?.product).toBe('BUKU_WARUNG');
    expect(defaultPromo?.normal_price).toBe(100000);
    expect(defaultPromo?.promo_price).toBe(50000);
    expect(defaultPromo?.enabled).toBe(1);
    expect(defaultPromo?.show_countdown).toBe(1);
    expect(defaultPromo?.timezone).toBe('Asia/Jakarta');
  });

  /* -------------------------------------------------------------------------
   * 2. PricingService Resolution Logic
   * ------------------------------------------------------------------------- */
  describe('PricingService.resolvePrice() rules', () => {
    const startsAt = 1700000000000; // Reference T0
    const expiresAt = 1700003600000; // Reference T0 + 1 hour

    it('returns promo price during active window (enabled=1, now within starts_at..expires_at)', () => {
      pricingService.updatePromotion('TEST_PROD', {
        name: 'Flash Sale',
        enabled: 1,
        normalPrice: 100000,
        promoPrice: 45000,
        startsAt,
        expiresAt,
        timezone: 'Asia/Jakarta',
        showCountdown: 1
      });

      // Mid-point
      const res = pricingService.resolvePrice('TEST_PROD', startsAt + 1800000);
      expect(res.isPromoActive).toBe(true);
      expect(res.effectivePrice).toBe(45000);
      expect(res.normalPrice).toBe(100000);
      expect(res.promoPrice).toBe(45000);
      expect(res.showCountdown).toBe(true);
    });

    it('returns normal price when promo is disabled (enabled=0)', () => {
      pricingService.updatePromotion('TEST_PROD', {
        enabled: 0
      });

      const res = pricingService.resolvePrice('TEST_PROD', startsAt + 1800000);
      expect(res.isPromoActive).toBe(false);
      expect(res.effectivePrice).toBe(100000);
      expect(res.normalPrice).toBe(100000);
      expect(res.promoPrice).toBe(45000);
    });

    it('returns normal price before starts_at', () => {
      pricingService.updatePromotion('TEST_PROD', { enabled: 1 });

      const res = pricingService.resolvePrice('TEST_PROD', startsAt - 1);
      expect(res.isPromoActive).toBe(false);
      expect(res.effectivePrice).toBe(100000);
    });

    it('returns promo price exactly at starts_at boundary (now == starts_at)', () => {
      const res = pricingService.resolvePrice('TEST_PROD', startsAt);
      expect(res.isPromoActive).toBe(true);
      expect(res.effectivePrice).toBe(45000);
    });

    it('returns normal price exactly at expires_at boundary (now == expires_at)', () => {
      const res = pricingService.resolvePrice('TEST_PROD', expiresAt);
      expect(res.isPromoActive).toBe(false);
      expect(res.effectivePrice).toBe(100000);
    });

    it('returns normal price after expires_at', () => {
      const res = pricingService.resolvePrice('TEST_PROD', expiresAt + 1000);
      expect(res.isPromoActive).toBe(false);
      expect(res.effectivePrice).toBe(100000);
    });

    it('handles future / multi-product codes without hardcoding', () => {
      pricingService.updatePromotion('SKM_ERP_LITE', {
        name: 'ERP Early Bird',
        enabled: 1,
        normalPrice: 500000,
        promoPrice: 250000,
        startsAt,
        expiresAt
      });

      const res = pricingService.resolvePrice('SKM_ERP_LITE', startsAt + 100);
      expect(res.product).toBe('SKM_ERP_LITE');
      expect(res.isPromoActive).toBe(true);
      expect(res.effectivePrice).toBe(250000);
      expect(res.normalPrice).toBe(500000);
    });
  });

  /* -------------------------------------------------------------------------
   * 3. Pricing Validation Rules
   * ------------------------------------------------------------------------- */
  describe('Promotion Validation', () => {
    it('rejects invalid product code (empty/whitespace)', () => {
      const res = pricingService.updatePromotion('  ', { normalPrice: 100000 });
      expect(res.success).toBe(false);
      expect(res.error?.code).toBe('INVALID_PRODUCT');
    });

    it('rejects normalPrice <= 0', () => {
      const res = pricingService.updatePromotion('VAL_TEST', { normalPrice: 0 });
      expect(res.success).toBe(false);
      expect(res.error?.code).toBe('INVALID_NORMAL_PRICE');
    });

    it('rejects promoPrice <= 0', () => {
      const res = pricingService.updatePromotion('VAL_TEST', { promoPrice: -100 });
      expect(res.success).toBe(false);
      expect(res.error?.code).toBe('INVALID_PROMO_PRICE');
    });

    it('rejects promoPrice > normalPrice', () => {
      const res = pricingService.updatePromotion('VAL_TEST', {
        normalPrice: 50000,
        promoPrice: 60000
      });
      expect(res.success).toBe(false);
      expect(res.error?.code).toBe('INVALID_PRICE_RELATION');
    });

    it('rejects startsAt >= expiresAt', () => {
      const res = pricingService.updatePromotion('VAL_TEST', {
        normalPrice: 100000,
        promoPrice: 50000,
        startsAt: 2000,
        expiresAt: 1000
      });
      expect(res.success).toBe(false);
      expect(res.error?.code).toBe('INVALID_DATE_RANGE');
    });
  });

  /* -------------------------------------------------------------------------
   * 4. Admin REST API
   * ------------------------------------------------------------------------- */
  describe('Admin REST API (/v1/admin/promotions)', () => {
    it('rejects unauthenticated request to /v1/admin/promotions with 401', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/v1/admin/promotions'
      });
      expect(res.statusCode).toBe(401);
    });

    it('authenticated GET /v1/admin/promotions returns all promotions', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/v1/admin/promotions',
        headers: { authorization: `Bearer ${adminApiKey}` }
      });
      expect(res.statusCode).toBe(200);
      const body = JSON.parse(res.body);
      expect(body.success).toBe(true);
      expect(Array.isArray(body.data)).toBe(true);
      expect(body.data.some((p: any) => p.product === 'BUKU_WARUNG')).toBe(true);
    });

    it('authenticated GET /v1/admin/promotions/:product returns single product promo', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/v1/admin/promotions/BUKU_WARUNG',
        headers: { authorization: `Bearer ${adminApiKey}` }
      });
      expect(res.statusCode).toBe(200);
      const body = JSON.parse(res.body);
      expect(body.success).toBe(true);
      expect(body.data.product).toBe('BUKU_WARUNG');
      expect(body.data.normalPrice).toBe(100000);
    });

    it('authenticated PUT /v1/admin/promotions/:product updates configuration and records audit log', async () => {
      const now = Date.now();
      const res = await app.inject({
        method: 'PUT',
        url: '/v1/admin/promotions/BUKU_WARUNG',
        headers: { authorization: `Bearer ${adminApiKey}` },
        payload: {
          name: 'Promo Akhir Tahun',
          normalPrice: 120000,
          promoPrice: 60000,
          enabled: true,
          showCountdown: true,
          startsAt: now - 10000,
          expiresAt: now + 3600000
        }
      });

      expect(res.statusCode).toBe(200);
      const body = JSON.parse(res.body);
      expect(body.success).toBe(true);
      expect(body.data.promoName).toBe('Promo Akhir Tahun');
      expect(body.data.effectivePrice).toBe(60000);
      expect(body.data.normalPrice).toBe(120000);

      // Verify audit log entry was created
      const auditLog = db
        .prepare("SELECT * FROM audit_logs WHERE action = 'UPDATE_PROMOTION' ORDER BY id DESC LIMIT 1")
        .get() as any;
      expect(auditLog).toBeDefined();
      expect(auditLog.action).toBe('UPDATE_PROMOTION');
      expect(auditLog.actor).toBe('ADMIN_API');
      expect(auditLog.new_state).toContain('Promo Akhir Tahun');
    });

    it('authenticated PUT rejects invalid payload with 400', async () => {
      const res = await app.inject({
        method: 'PUT',
        url: '/v1/admin/promotions/BUKU_WARUNG',
        headers: { authorization: `Bearer ${adminApiKey}` },
        payload: {
          normalPrice: 40000,
          promoPrice: 80000 // Invalid: promo > normal
        }
      });
      expect(res.statusCode).toBe(400);
      const body = JSON.parse(res.body);
      expect(body.success).toBe(false);
      expect(body.error.code).toBe('INVALID_PRICE_RELATION');
    });
  });
});
