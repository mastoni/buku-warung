import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import { FastifyInstance } from 'fastify';
import Database from 'better-sqlite3';
import crypto from 'crypto';
import { buildApp } from '../src/app.js';
import { getDatabase, closeDatabase } from '../src/db/database.js';
import { config } from '../src/config/index.js';
import { generatePublicOrderToken, verifyPublicOrderToken } from '../src/utils/token.js';

describe('M.3.2-G3 Security + End-to-End Validation', () => {
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

  // ==========================================
  // 1. CAPABILITY TOKEN SECURITY
  // ==========================================
  describe('1. Capability Token Security', () => {
    it('uses AES-256-GCM with fresh 96-bit IV producing unique ciphertexts for identical inputs', () => {
      const orderNumber = 'BW-ORD-260917-1001';
      const token1 = generatePublicOrderToken(orderNumber);
      const token2 = generatePublicOrderToken(orderNumber);

      expect(token1).not.toBe(token2);
      expect(token1.startsWith('pot_')).toBe(true);
      expect(token2.startsWith('pot_')).toBe(true);
    });

    it('rejects tampered authentication tag', () => {
      const token = generatePublicOrderToken('BW-ORD-260917-1002');
      const raw = Buffer.from(token.slice(4), 'base64url');
      // Tamper byte in authTag (bytes 12..27)
      raw[15] ^= 0xff;
      const tamperedToken = 'pot_' + raw.toString('base64url');

      const result = verifyPublicOrderToken(tamperedToken);
      expect(result.valid).toBe(false);
      expect(result.orderNumber).toBeUndefined();
    });

    it('rejects altered ciphertext bytes', () => {
      const token = generatePublicOrderToken('BW-ORD-260917-1003');
      const raw = Buffer.from(token.slice(4), 'base64url');
      // Tamper byte in ciphertext (bytes 28+)
      raw[30] ^= 0xaa;
      const tamperedToken = 'pot_' + raw.toString('base64url');

      const result = verifyPublicOrderToken(tamperedToken);
      expect(result.valid).toBe(false);
    });

    it('rejects token encrypted with wrong secret/key', () => {
      const foreignKey = crypto.createHash('sha256').update('wrong_secret_key_123').digest();
      const iv = crypto.randomBytes(12);
      const cipher = crypto.createCipheriv('aes-256-gcm', foreignKey, iv);
      const ciphertext = Buffer.concat([cipher.update('BW-ORD-FORGED-001', 'utf8'), cipher.final()]);
      const authTag = cipher.getAuthTag();
      const combined = Buffer.concat([iv, authTag, ciphertext]);
      const forgedToken = 'pot_' + combined.toString('base64url');

      const result = verifyPublicOrderToken(forgedToken);
      expect(result.valid).toBe(false);
    });

    it('rejects malformed or truncated tokens', () => {
      expect(verifyPublicOrderToken('pot_AQID').valid).toBe(false);
      expect(verifyPublicOrderToken('pot_').valid).toBe(false);
      expect(verifyPublicOrderToken('not_pot_prefix').valid).toBe(false);
      expect(verifyPublicOrderToken('').valid).toBe(false);
      expect(verifyPublicOrderToken('1').valid).toBe(false);
    });

    it('rejects token if decrypted payload does not match expected format', () => {
      const key = crypto.createHash('sha256').update(`${config.serverPepper}:public_order_token_v1`).digest();
      const iv = crypto.randomBytes(12);
      const cipher = crypto.createCipheriv('aes-256-gcm', key, iv);
      const ciphertext = Buffer.concat([cipher.update('MALICIOUS-PAYLOAD-NO-BW-ORD', 'utf8'), cipher.final()]);
      const authTag = cipher.getAuthTag();
      const token = 'pot_' + Buffer.concat([iv, authTag, ciphertext]).toString('base64url');

      const result = verifyPublicOrderToken(token);
      expect(result.valid).toBe(false);
    });
  });

  // ==========================================
  // 2. ORDER ISOLATION
  // ==========================================
  describe('2. Order Isolation', () => {
    let orderA: { orderNumber: string; token: string };
    let orderB: { orderNumber: string; token: string };

    beforeAll(async () => {
      const resA = await app.inject({
        method: 'POST',
        url: '/v1/public/orders',
        payload: {
          customerName: 'Customer Alpha',
          customerContact: '081111111111',
          ownerEmail: 'alpha@warung.id'
        }
      });
      const dataA = JSON.parse(resA.body).data;
      orderA = { orderNumber: dataA.orderNumber, token: dataA.publicToken };

      const resB = await app.inject({
        method: 'POST',
        url: '/v1/public/orders',
        payload: {
          customerName: 'Customer Beta',
          customerContact: '082222222222',
          ownerEmail: 'beta@warung.id'
        }
      });
      const dataB = JSON.parse(resB.body).data;
      orderB = { orderNumber: dataB.orderNumber, token: dataB.publicToken };
    });

    it('Token A strictly returns Order A data', async () => {
      const res = await app.inject({
        method: 'GET',
        url: `/v1/public/orders/${orderA.token}`
      });
      expect(res.statusCode).toBe(200);
      const body = JSON.parse(res.body);
      expect(body.data.orderNumber).toBe(orderA.orderNumber);
      expect(body.data.customerNameMasked).toBe('Customer A****');
    });

    it('Token B strictly returns Order B data', async () => {
      const res = await app.inject({
        method: 'GET',
        url: `/v1/public/orders/${orderB.token}`
      });
      expect(res.statusCode).toBe(200);
      const body = JSON.parse(res.body);
      expect(body.data.orderNumber).toBe(orderB.orderNumber);
      expect(body.data.customerNameMasked).toBe('Customer B***');
    });

    it('Token A cannot be used with parameter manipulation or enumeration', async () => {
      const resInt = await app.inject({
        method: 'GET',
        url: '/v1/public/orders/1'
      });
      expect(resInt.statusCode).toBe(404);

      const resRand = await app.inject({
        method: 'GET',
        url: '/v1/public/orders/pot_99999999999999999999999999999999'
      });
      expect(resRand.statusCode).toBe(404);
    });
  });

  // ==========================================
  // 3. PUBLIC DATA EXPOSURE AUDIT
  // ==========================================
  describe('3. Public Data Exposure Audit', () => {
    it('POST /v1/public/orders does not leak database ID, internal fields, or unmasked data', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/v1/public/orders',
        payload: {
          customerName: 'Audited Customer',
          customerContact: '083333333333',
          ownerEmail: 'audited@warung.id'
        }
      });
      expect(res.statusCode).toBe(201);
      const data = JSON.parse(res.body).data;

      expect(data.id).toBeUndefined();
      expect(data.orderId).toBeUndefined();
      expect(data.licenseCode).toBeUndefined();
      expect(data.license_code).toBeUndefined();
      expect(data.adminApiKey).toBeUndefined();
      expect(data.serverPepper).toBeUndefined();
    });

    it('GET /v1/public/orders/:token strictly returns masked identity and zero secret tokens', async () => {
      const createRes = await app.inject({
        method: 'POST',
        url: '/v1/public/orders',
        payload: {
          customerName: 'Sensitive User',
          customerContact: '084444444444',
          ownerEmail: 'sensitive.user@gmail.com'
        }
      });
      const token = JSON.parse(createRes.body).data.publicToken;

      const res = await app.inject({
        method: 'GET',
        url: `/v1/public/orders/${token}`
      });
      const data = JSON.parse(res.body).data;

      // Masked properties present
      expect(data.customerNameMasked).toBe('Sensitive U***');
      expect(data.customerContactMasked).toBe('6284****4444');
      expect(data.ownerEmailMasked).toBe('s***@gmail.com');

      // Sensitive properties absent
      expect(data.id).toBeUndefined();
      expect(data.customer_name).toBeUndefined();
      expect(data.customer_contact).toBeUndefined();
      expect(data.owner_email).toBeUndefined();
      expect(data.ownerEmail).toBeUndefined();
      expect(data.customerContact).toBeUndefined();
      expect(data.licenseCode).toBeUndefined();
      expect(data.notes).toBeUndefined();
    });
  });

  // ==========================================
  // 4. PAYMENT AUTHORITY
  // ==========================================
  describe('4. Payment Authority', () => {
    it('client tampering on amount, product, or status is ignored; server is authoritative', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/v1/public/orders',
        payload: {
          customerName: 'Cheater User',
          customerContact: '085555555555',
          ownerEmail: 'cheater@warung.id',
          amount: 1, // Tamper amount
          product: 'FREE_EDITION', // Tamper product
          status: 'PAID', // Tamper status
          paymentStatus: 'PAID' // Tamper paymentStatus
        }
      });

      expect(res.statusCode).toBe(201);
      const data = JSON.parse(res.body).data;
      expect(data.amount).toBe(50000);
      expect(data.product).toBe('Buku Warung v0.1.0');
      expect(data.status).toBe('PENDING_PAYMENT');
      expect(data.paymentStatus).toBe('UNPAID');

      // Verify database
      const row = db.prepare('SELECT * FROM orders WHERE order_number = ?').get(data.orderNumber) as any;
      expect(row.amount).toBe(50000);
      expect(row.product).toBe('BUKU_WARUNG');
      expect(row.status).toBe('PENDING_PAYMENT');
      expect(row.payment_status).toBe('UNPAID');
    });

    it('customer cannot call verify-payment endpoint or alter state to PAID', async () => {
      const createRes = await app.inject({
        method: 'POST',
        url: '/v1/public/orders',
        payload: {
          customerName: 'No Verify User',
          customerContact: '086666666666',
          ownerEmail: 'noverify@warung.id'
        }
      });
      const token = JSON.parse(createRes.body).data.publicToken;

      const res = await app.inject({
        method: 'POST',
        url: `/v1/public/orders/${token}/verify`,
        payload: { status: 'PAID' }
      });
      expect(res.statusCode).toBe(404);
    });
  });

  // ==========================================
  // 5. LICENSE AUTHORITY
  // ==========================================
  describe('5. License Authority', () => {
    it('public order endpoint cannot generate license codes', async () => {
      const createRes = await app.inject({
        method: 'POST',
        url: '/v1/public/orders',
        payload: {
          customerName: 'License Probe',
          customerContact: '087777777777',
          ownerEmail: 'probe@warung.id'
        }
      });
      const token = JSON.parse(createRes.body).data.publicToken;

      const res = await app.inject({
        method: 'POST',
        url: `/v1/public/orders/${token}/generate-license`
      });
      expect(res.statusCode).toBe(404);
    });
  });

  // ==========================================
  // 6. HONEYPOT & IDEMPOTENCY
  // ==========================================
  describe('6. Honeypot & Idempotency', () => {
    it('honeypot field `website` blocks automated spam bots with 400 SPAM_DETECTED', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/v1/public/orders',
        payload: {
          customerName: 'Bot Spammer',
          customerContact: '088888888888',
          ownerEmail: 'bot@spam.com',
          website: 'https://spam.xyz'
        }
      });
      expect(res.statusCode).toBe(400);
      const body = JSON.parse(res.body);
      expect(body.error.code).toBe('SPAM_DETECTED');
    });

    it('honeypot field `_hp` also blocks automated spam bots', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/v1/public/orders',
        payload: {
          customerName: 'Bot Spammer 2',
          customerContact: '088888888889',
          ownerEmail: 'bot2@spam.com',
          _hp: 'random_bot_input'
        }
      });
      expect(res.statusCode).toBe(400);
      const body = JSON.parse(res.body);
      expect(body.error.code).toBe('SPAM_DETECTED');
    });

    it('idempotency prevents duplicate order rows within 5-minute window for same phone', async () => {
      const contact = '081299990001';
      const email = 'idem@warung.id';

      const res1 = await app.inject({
        method: 'POST',
        url: '/v1/public/orders',
        payload: { customerName: 'Idem User', customerContact: contact, ownerEmail: email }
      });
      expect(res1.statusCode).toBe(201);
      const num1 = JSON.parse(res1.body).data.orderNumber;

      const res2 = await app.inject({
        method: 'POST',
        url: '/v1/public/orders',
        payload: { customerName: 'Idem User', customerContact: contact, ownerEmail: email }
      });
      expect(res2.statusCode).toBe(200);
      const data2 = JSON.parse(res2.body).data;
      expect(data2.orderNumber).toBe(num1);
      expect(data2.idempotent).toBe(true);

      const count = db.prepare('SELECT COUNT(*) as c FROM orders WHERE customer_contact = ?').get('6281299990001') as any;
      expect(count.c).toBe(1);
    });
  });

  // ==========================================
  // 7. HTTP SECURITY & HEADERS
  // ==========================================
  describe('7. HTTP Security & Headers', () => {
    it('Helmet security headers are properly attached', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/beli/buku-warung'
      });
      expect(res.statusCode).toBe(200);
      expect(res.headers['x-dns-prefetch-control']).toBeDefined();
      expect(res.headers['x-frame-options']).toBeDefined();
      expect(res.headers['x-content-type-options']).toBe('nosniff');
    });

    it('Malformed JSON returns error response without stack trace or SQL leakage', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/v1/public/orders',
        headers: { 'content-type': 'application/json' },
        payload: '{ invalid_json '
      });
      expect(res.statusCode).toBeGreaterThanOrEqual(400);
      expect(res.body).not.toContain('stack');
      expect(res.body).not.toContain('SQLITE');
      expect(res.body).not.toContain('node_modules');
    });
  });
});
