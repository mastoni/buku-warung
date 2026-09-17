import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import { FastifyInstance } from 'fastify';
import Database from 'better-sqlite3';
import { buildApp } from '../src/app.js';
import { getDatabase, closeDatabase } from '../src/db/database.js';
import { OFFICIAL_QRIS } from '../src/controllers/publicOrderController.js';

describe('M.3.2-G2 Customer-Facing Public Order UI Tests', () => {
  let app: FastifyInstance;
  let db: Database.Database;

  beforeAll(async () => {
    db = getDatabase(':memory:');
    app = buildApp();
    await app.ready();
  });

  afterAll(async () => {
    await app.close();
    closeDatabase();
  });

  /* =========================================================================
   * 1. GET /beli/buku-warung — Page Load & HTML Shell
   * ========================================================================= */
  describe('GET /beli/buku-warung (Page Load)', () => {
    it('returns HTTP 200 with HTML content-type and valid CSP nonce', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/beli/buku-warung'
      });

      expect(res.statusCode).toBe(200);
      expect(res.headers['content-type']).toContain('text/html');

      const csp = (res.headers['content-security-policy'] as string) || '';
      expect(csp).toBeDefined();
      expect(csp).toContain("script-src 'self' 'nonce-");
      expect(csp).not.toContain("script-src 'self' 'unsafe-inline'");

      // Extract nonce from CSP header
      const match = csp.match(/script-src 'self' 'nonce-([^']+)'/);
      expect(match).not.toBeNull();
      const nonce = match![1];
      expect(nonce.length).toBeGreaterThan(10);

      // Verify script tag in body uses the exact matching nonce
      expect(res.body).toContain(`<script nonce="${nonce}">`);
    });

    it('generates unique cryptographic nonces on consecutive requests', async () => {
      const res1 = await app.inject({ method: 'GET', url: '/beli/buku-warung' });
      const res2 = await app.inject({ method: 'GET', url: '/beli/buku-warung' });

      const csp1 = (res1.headers['content-security-policy'] as string) || '';
      const csp2 = (res2.headers['content-security-policy'] as string) || '';

      const nonce1 = csp1.match(/nonce-([^']+)/)?.[1];
      const nonce2 = csp2.match(/nonce-([^']+)/)?.[1];

      expect(nonce1).toBeDefined();
      expect(nonce2).toBeDefined();
      expect(nonce1).not.toBe(nonce2);
    });

    it('contains mobile-first viewport meta tag', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/beli/buku-warung'
      });

      expect(res.body).toContain('<meta name="viewport" content="width=device-width, initial-scale=1.0">');
    });

    it('displays product name Buku Warung v0.1.0 and price Rp 50.000', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/beli/buku-warung'
      });

      expect(res.body).toContain('Buku Warung v0.1.0');
      expect(res.body).toContain('Rp 50.000');
      expect(res.body).toContain('Sekali Beli');
    });

    it('contains required customer form fields: nama, whatsapp, email', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/beli/buku-warung'
      });

      expect(res.body).toContain('name="customerName"');
      expect(res.body).toContain('name="customerContact"');
      expect(res.body).toContain('name="ownerEmail"');
      expect(res.body).toContain('Lanjutkan Pesanan (Rp 50.000)');
    });

    it('contains anti-spam honeypot field', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/beli/buku-warung'
      });

      expect(res.body).toContain('name="website"');
      expect(res.body).toContain('hp-wrap');
    });

    it('contains existing official QRIS image and Kios Kiara details', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/beli/buku-warung'
      });

      expect(res.body).toContain(OFFICIAL_QRIS.imageUrl);
      expect(res.body).toContain(OFFICIAL_QRIS.merchant);
      expect(res.body).toContain(OFFICIAL_QRIS.nmid);
    });

    it('contains official WhatsApp Admin number 6285157056604 and confirmation button', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/beli/buku-warung'
      });

      expect(res.body).toContain('6285157056604');
      expect(res.body).toContain('Saya Sudah Membayar');
      expect(res.body).toContain('btn-whatsapp');
    });

    it('implements token fragment handling (#token=) rather than query parameters', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/beli/buku-warung'
      });

      expect(res.body).toContain('getFragmentToken');
      expect(res.body).toContain('setFragmentToken');
      expect(res.body).toContain('token=');
    });

    it('enforces safe visibilitychange polling pause', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/beli/buku-warung'
      });

      expect(res.body).toContain('visibilitychange');
      expect(res.body).toContain('document.hidden');
      expect(res.body).toContain('stopPolling');
    });
  });

  /* =========================================================================
   * 2. Public API Interaction Validation (UI Contract)
   * ========================================================================= */
  describe('Public API Contract Verification for UI', () => {
    let testPublicToken = '';
    let testOrderNumber = '';

    it('creates order via POST /v1/public/orders and receives 201 with publicToken', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/v1/public/orders',
        payload: {
          customerName: 'Joko Widodo Warung',
          customerContact: '081298765432',
          ownerEmail: 'joko@warungjawa.id'
        }
      });

      expect(res.statusCode).toBe(201);
      const json = JSON.parse(res.body);
      expect(json.success).toBe(true);
      expect(json.data.amount).toBe(50000);
      expect(json.data.product).toBe('Buku Warung v0.1.0');
      expect(json.data.status).toBe('PENDING_PAYMENT');
      expect(json.data.paymentStatus).toBe('UNPAID');
      expect(json.data.publicToken).toBeDefined();
      expect(json.data.qrisImageUrl).toBe(OFFICIAL_QRIS.imageUrl);

      testPublicToken = json.data.publicToken;
      testOrderNumber = json.data.orderNumber;
    });

    it('allows safe public lookup via GET /v1/public/orders/:publicToken', async () => {
      const res = await app.inject({
        method: 'GET',
        url: `/v1/public/orders/${testPublicToken}`
      });

      expect(res.statusCode).toBe(200);
      const json = JSON.parse(res.body);
      expect(json.success).toBe(true);
      expect(json.data.orderNumber).toBe(testOrderNumber);
      expect(json.data.amount).toBe(50000);
      expect(json.data.qrisImageUrl).toBe(OFFICIAL_QRIS.imageUrl);
      expect(json.data.customerNameMasked).toBe('Joko W***** W*****');
      expect(json.data.customerContactMasked).toBe('6281****5432');
    });

    it('prevents customer from verifying payment or modifying payment_status to PAID', async () => {
      // 1. Attempt unauthenticated verify-payment on admin endpoint
      const adminRes = await app.inject({
        method: 'POST',
        url: `/v1/admin/orders/1/verify-payment`,
        payload: {
          paymentMethod: 'QRIS',
          paymentReference: 'FAKE-PAYMENT'
        }
      });
      // Admin endpoint must reject without credentials
      expect(adminRes.statusCode).toBe(401);

      // 2. Public endpoint does NOT have verify-payment route
      const publicVerifyRes = await app.inject({
        method: 'POST',
        url: `/v1/public/orders/${testPublicToken}/verify-payment`
      });
      // Must not exist (404)
      expect(publicVerifyRes.statusCode).toBe(404);
    });

    it('prevents customer from generating licenses on public endpoints', async () => {
      const publicLicenseRes = await app.inject({
        method: 'POST',
        url: `/v1/public/orders/${testPublicToken}/generate-license`
      });
      expect(publicLicenseRes.statusCode).toBe(404);
    });
  });

  /* =========================================================================
   * 3. Regression Safeguards
   * ========================================================================= */
  describe('Regression Invariants', () => {
    it('preserves existing download endpoints unchanged', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/download/buku-warung'
      });
      expect(res.statusCode).toBe(200);
      expect(res.headers['content-type']).toContain('text/html');
    });

    it('preserves health endpoint', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/health'
      });
      expect(res.statusCode).toBe(200);
      const json = JSON.parse(res.body);
      expect(json.status).toBe('ok');
    });
  });
});
