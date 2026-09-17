import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import { FastifyInstance } from 'fastify';
import Database from 'better-sqlite3';
import { buildApp } from '../src/app.js';
import { getDatabase, closeDatabase } from '../src/db/database.js';
import { config } from '../src/config/index.js';
import { generatePublicOrderToken, verifyPublicOrderToken } from '../src/utils/token.js';
import { normalizeIndonesianPhone } from '../src/utils/phone.js';
import { maskCustomerName, maskPhone, maskEmail } from '../src/utils/masking.js';

describe('M.3.2-G1 Public Order API & Security Foundation', () => {
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

  let createdPublicToken = '';
  let createdOrderNumber = '';

  // 1. Phone Normalization Unit Tests
  describe('Phone Normalization Utility', () => {
    it('normalizes 08xx into 628xx', () => {
      const res = normalizeIndonesianPhone('085157056604');
      expect(res.valid).toBe(true);
      expect(res.normalized).toBe('6285157056604');
    });

    it('normalizes +628xx with dashes and spaces', () => {
      const res = normalizeIndonesianPhone('+62 851-5705-6604');
      expect(res.valid).toBe(true);
      expect(res.normalized).toBe('6285157056604');
    });

    it('normalizes 8xx prefix into 628xx', () => {
      const res = normalizeIndonesianPhone('81234567890');
      expect(res.valid).toBe(true);
      expect(res.normalized).toBe('6281234567890');
    });

    it('rejects landline or invalid numbers', () => {
      expect(normalizeIndonesianPhone('0211234567').valid).toBe(false);
      expect(normalizeIndonesianPhone('12345').valid).toBe(false);
      expect(normalizeIndonesianPhone('not-a-number').valid).toBe(false);
      expect(normalizeIndonesianPhone('').valid).toBe(false);
    });
  });

  // 2. Data Masking Utility Tests
  describe('Data Masking Utility', () => {
    it('masks customer name correctly', () => {
      expect(maskCustomerName('Budi Santoso')).toBe('Budi S******');
      expect(maskCustomerName('Andi')).toBe('An**');
      expect(maskCustomerName('John Doe Smith')).toBe('John D*** S****');
    });

    it('masks phone number correctly', () => {
      expect(maskPhone('6285157056604')).toBe('6285****6604');
      expect(maskPhone('081234567890')).toBe('0812****7890');
    });

    it('masks email correctly', () => {
      expect(maskEmail('budi.santoso@warung.id')).toBe('b***@warung.id');
      expect(maskEmail('andi@gmail.com')).toBe('a***@gmail.com');
    });
  });

  // 3. Cryptographic Capability Token Tests
  describe('Capability Token', () => {
    it('generates unpredictable AES-GCM token with pot_ prefix', () => {
      const token1 = generatePublicOrderToken('BW-ORD-001');
      const token2 = generatePublicOrderToken('BW-ORD-001');

      expect(token1.startsWith('pot_')).toBe(true);
      expect(token2.startsWith('pot_')).toBe(true);
      // Because of random IV and random entropy, identical order numbers produce different tokens
      expect(token1).not.toBe(token2);
    });

    it('verifies and decrypts token back to order number', () => {
      const token = generatePublicOrderToken('BW-ORD-TEST-1234');
      const result = verifyPublicOrderToken(token);

      expect(result.valid).toBe(true);
      expect(result.orderNumber).toBe('BW-ORD-TEST-1234');
    });

    it('rejects tampered or forged tokens', () => {
      const token = generatePublicOrderToken('BW-ORD-TEST-1234');
      const tampered = token.slice(0, -4) + 'AAAA';
      const result = verifyPublicOrderToken(tampered);

      expect(result.valid).toBe(false);
    });

    it('rejects arbitrary non-token strings or integer IDs', () => {
      expect(verifyPublicOrderToken('1').valid).toBe(false);
      expect(verifyPublicOrderToken('12345').valid).toBe(false);
      expect(verifyPublicOrderToken('admin').valid).toBe(false);
      expect(verifyPublicOrderToken('').valid).toBe(false);
    });
  });

  // 4. POST /v1/public/orders (Happy Path)
  describe('POST /v1/public/orders', () => {
    it('creates order with 201 Created and authoritative defaults', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/v1/public/orders',
        payload: {
          customerName: 'Ahmad Dahlan',
          customerContact: '081234567890',
          ownerEmail: 'ahmad@warung.co.id',
          leadToken: 'LW-7K9M2P',
          utm_source: 'meta_ads',
          utm_medium: 'cpc',
          utm_campaign: 'promo_warung'
        }
      });

      expect(res.statusCode).toBe(201);
      const body = JSON.parse(res.body);
      expect(body.success).toBe(true);
      expect(body.data).toBeDefined();

      const data = body.data;
      expect(data.orderNumber).toMatch(/^BW-ORD-\d{6}-\d{4}$/);
      expect(data.status).toBe('PENDING_PAYMENT');
      expect(data.paymentStatus).toBe('UNPAID');
      expect(data.amount).toBe(50000);
      expect(data.product).toBe('Buku Warung v0.1.0');
      expect(data.paymentMethod).toBe('QRIS — KIOS KIARA');
      expect(data.publicToken).toMatch(/^pot_/);
      expect(data.qrisImageUrl).toBe('https://license.skmnetwork.com/img/qris-kios-kiara.png');
      expect(data.qris.merchant).toBe('KIOS KIARA');
      expect(data.qris.nmid).toBe('ID1026512762125');

      createdOrderNumber = data.orderNumber;
      createdPublicToken = data.publicToken;

      // Verify row in existing orders table
      const row = db.prepare('SELECT * FROM orders WHERE order_number = ?').get(createdOrderNumber) as any;
      expect(row).toBeDefined();
      expect(row.customer_name).toBe('Ahmad Dahlan');
      expect(row.customer_contact).toBe('6281234567890');
      expect(row.owner_email).toBe('ahmad@warung.co.id');
      expect(row.amount).toBe(50000);
      expect(row.product).toBe('BUKU_WARUNG');
      expect(row.lead_token).toBe('LW-7K9M2P');
      expect(row.utm_source).toBe('meta_ads');
    });

    // 5. Product & Price Tampering Protection
    it('forces authoritative product BUKU_WARUNG even if client attempts tampering', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/v1/public/orders',
        payload: {
          customerName: 'Hacker Tamper',
          customerContact: '085811112222',
          ownerEmail: 'hacker@tamper.id',
          product: 'FREE_EDITION_VIP',
          amount: 1
        }
      });

      expect(res.statusCode).toBe(201);
      const body = JSON.parse(res.body);
      expect(body.data.amount).toBe(50000); // Forced to 50000
      expect(body.data.product).toBe('Buku Warung v0.1.0');

      const row = db.prepare('SELECT * FROM orders WHERE order_number = ?').get(body.data.orderNumber) as any;
      expect(row.amount).toBe(50000);
      expect(row.product).toBe('BUKU_WARUNG');
    });

    // 6. Validation Errors
    it('rejects invalid email format', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/v1/public/orders',
        payload: {
          customerName: 'Budi',
          customerContact: '081234567899',
          ownerEmail: 'not-an-email'
        }
      });

      expect(res.statusCode).toBe(400);
      const body = JSON.parse(res.body);
      expect(body.success).toBe(false);
      expect(body.error.code).toBe('INVALID_EMAIL');
    });

    it('rejects invalid phone number', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/v1/public/orders',
        payload: {
          customerName: 'Budi',
          customerContact: '12345',
          ownerEmail: 'budi@warung.id'
        }
      });

      expect(res.statusCode).toBe(400);
      const body = JSON.parse(res.body);
      expect(body.success).toBe(false);
      expect(body.error.code).toBe('INVALID_PHONE');
    });

    it('rejects empty customer name', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/v1/public/orders',
        payload: {
          customerName: '   ',
          customerContact: '081234567899',
          ownerEmail: 'budi@warung.id'
        }
      });

      expect(res.statusCode).toBe(400);
      const body = JSON.parse(res.body);
      expect(body.success).toBe(false);
      expect(body.error.code).toBe('INVALID_NAME');
    });

    // 7. Anti-Spam: Honeypot Protection
    it('rejects requests with filled honeypot fields', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/v1/public/orders',
        payload: {
          customerName: 'Spam Bot',
          customerContact: '081299998888',
          ownerEmail: 'bot@spam.com',
          website: 'http://spam-link.ru'
        }
      });

      expect(res.statusCode).toBe(400);
      const body = JSON.parse(res.body);
      expect(body.success).toBe(false);
      expect(body.error.code).toBe('SPAM_DETECTED');
    });

    // 8. Idempotency / Double Submit Protection
    it('returns existing pending order on rapid double-submit without duplicate row creation', async () => {
      const contact = '081987654321';
      const email = 'siti@warung.com';

      // First submit
      const res1 = await app.inject({
        method: 'POST',
        url: '/v1/public/orders',
        payload: {
          customerName: 'Siti Aminah',
          customerContact: contact,
          ownerEmail: email
        }
      });
      expect(res1.statusCode).toBe(201);
      const body1 = JSON.parse(res1.body);

      // Immediate second submit (e.g. double click)
      const res2 = await app.inject({
        method: 'POST',
        url: '/v1/public/orders',
        payload: {
          customerName: 'Siti Aminah',
          customerContact: contact,
          ownerEmail: email
        }
      });
      expect(res2.statusCode).toBe(200);
      const body2 = JSON.parse(res2.body);

      // Must return identical orderNumber and idempotent flag
      expect(body2.data.orderNumber).toBe(body1.data.orderNumber);
      expect(body2.data.idempotent).toBe(true);

      // Verify database count for this contact remains 1
      const count = db
        .prepare('SELECT COUNT(*) as cnt FROM orders WHERE customer_contact = ?')
        .get('6281987654321') as { cnt: number };
      expect(count.cnt).toBe(1);
    });
  });

  // 9. GET /v1/public/orders/:publicToken
  describe('GET /v1/public/orders/:publicToken', () => {
    it('returns safe masked order representation for valid token', async () => {
      const res = await app.inject({
        method: 'GET',
        url: `/v1/public/orders/${createdPublicToken}`
      });

      expect(res.statusCode).toBe(200);
      const body = JSON.parse(res.body);
      expect(body.success).toBe(true);

      const data = body.data;
      expect(data.orderNumber).toBe(createdOrderNumber);
      expect(data.status).toBe('PENDING_PAYMENT');
      expect(data.paymentStatus).toBe('UNPAID');
      expect(data.amount).toBe(50000);
      expect(data.paymentMethod).toBe('QRIS — KIOS KIARA');

      // Masked fields
      expect(data.customerNameMasked).toBe('Ahmad D*****');
      expect(data.customerContactMasked).toBe('6281****7890');
      expect(data.ownerEmailMasked).toBe('a***@warung.co.id');

      // QRIS details
      expect(data.qris.merchant).toBe('KIOS KIARA');
      expect(data.qris.nmid).toBe('ID1026512762125');
      expect(data.qrisImageUrl).toBe('https://license.skmnetwork.com/img/qris-kios-kiara.png');

      // CRITICAL SECURITY CHECKS: No secrets exposed
      expect(data.id).toBeUndefined();
      expect(data.orderId).toBeUndefined();
      expect(data.licenseCode).toBeUndefined();
      expect(data.license_code).toBeUndefined();
      expect(data.ownerEmail).toBeUndefined();
      expect(data.customerContact).toBeUndefined();
      expect(data.lead_token).toBeUndefined();
    });

    it('returns 404 for invalid capability token', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/v1/public/orders/pot_invalid_token_123456789'
      });

      expect(res.statusCode).toBe(404);
      const body = JSON.parse(res.body);
      expect(body.success).toBe(false);
      expect(body.error.code).toBe('ORDER_NOT_FOUND');
    });

    it('returns 404 for integer ID enumeration attempt', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/v1/public/orders/1'
      });

      expect(res.statusCode).toBe(404);
      const body = JSON.parse(res.body);
      expect(body.success).toBe(false);
    });
  });

  // 10. Security Invariants
  describe('Security Invariants & Non-manipulability', () => {
    it('customer cannot call verify-payment publicly (must 404 / not exist)', async () => {
      const res = await app.inject({
        method: 'POST',
        url: `/v1/public/orders/${createdPublicToken}/verify-payment`,
        payload: { paymentStatus: 'PAID' }
      });
      // Route does not exist on public interface
      expect(res.statusCode).toBe(404);
    });

    it('customer cannot generate license publicly', async () => {
      const res = await app.inject({
        method: 'POST',
        url: `/v1/public/orders/${createdPublicToken}/generate-license`
      });
      // Route does not exist on public interface
      expect(res.statusCode).toBe(404);
    });
  });

  // 11. Existing Admin Workflow Regression Verification
  describe('Admin Workflow Regression (M.3.1 Compatibility)', () => {
    let adminOrderId = 0;
    let adminOrderCode = '';

    it('admin can still create orders via /v1/admin/orders', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/v1/admin/orders',
        headers: { authorization: `Bearer ${adminApiKey}` },
        payload: {
          customerName: 'Admin Created Customer',
          customerContact: '085299990000',
          ownerEmail: 'admincust@warung.id',
          amount: 50000
        }
      });

      expect(res.statusCode).toBe(201);
      const body = JSON.parse(res.body);
      expect(body.success).toBe(true);
      adminOrderId = body.data.id;
      adminOrderCode = body.data.order_number;
    });

    it('admin can verify payment for both admin-created and public-created orders', async () => {
      // Find the public order ID
      const publicOrderRow = db.prepare('SELECT id FROM orders WHERE order_number = ?').get(createdOrderNumber) as any;

      const res = await app.inject({
        method: 'POST',
        url: `/v1/admin/orders/${publicOrderRow.id}/verify-payment`,
        headers: { authorization: `Bearer ${adminApiKey}` },
        payload: {
          paymentMethod: 'QRIS — KIOS KIARA',
          paymentReference: 'QRIS-MUTASI-987654'
        }
      });

      expect(res.statusCode).toBe(200);
      const body = JSON.parse(res.body);
      expect(body.success).toBe(true);
      expect(body.data.paymentStatus).toBe('PAID');
      expect(body.data.status).toBe('PAID');

      // Now verify public status endpoint reflects the verified payment!
      const publicCheck = await app.inject({
        method: 'GET',
        url: `/v1/public/orders/${createdPublicToken}`
      });
      expect(publicCheck.statusCode).toBe(200);
      const publicBody = JSON.parse(publicCheck.body);
      expect(publicBody.data.paymentStatus).toBe('PAID');
      expect(publicBody.data.status).toBe('PAID');
      expect(publicBody.data.verifiedAt).toBeGreaterThan(0);
    });

    it('admin can generate license for verified order and mark delivered', async () => {
      const publicOrderRow = db.prepare('SELECT id FROM orders WHERE order_number = ?').get(createdOrderNumber) as any;

      // Generate license
      const genRes = await app.inject({
        method: 'POST',
        url: `/v1/admin/orders/${publicOrderRow.id}/generate-license`,
        headers: { authorization: `Bearer ${adminApiKey}` }
      });
      expect(genRes.statusCode).toBe(200);
      const genBody = JSON.parse(genRes.body);
      expect(genBody.success).toBe(true);
      expect(genBody.data.licenseCode).toMatch(/^BW-[2-9A-HJ-NP-Z]{4}-[2-9A-HJ-NP-Z]{4}-[2-9A-HJ-NP-Z]{4}$/);

      // Mark delivered
      const delRes = await app.inject({
        method: 'POST',
        url: `/v1/admin/orders/${publicOrderRow.id}/mark-delivered`,
        headers: { authorization: `Bearer ${adminApiKey}` },
        payload: { notes: 'Sent via WhatsApp' }
      });
      expect(delRes.statusCode).toBe(200);
      const delBody = JSON.parse(delRes.body);
      expect(delBody.data.status).toBe('DELIVERED');
    });
  });
});
