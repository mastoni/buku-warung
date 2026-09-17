import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import { FastifyInstance } from 'fastify';
import Database from 'better-sqlite3';
import { buildApp } from '../src/app.js';
import { getDatabase, closeDatabase } from '../src/db/database.js';
import { config } from '../src/config/index.js';
import {
  encryptDeliveryLicenseCode,
  decryptDeliveryLicenseCode,
  hashLicenseCode
} from '../src/utils/crypto.js';
import { AdminService } from '../src/services/adminService.js';

describe('Admin License Delivery Code Persistence & Encryption', () => {
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

  describe('1. Cryptographic Unit Tests (AES-256-GCM)', () => {
    it('encrypts and decrypts license code successfully (roundtrip)', () => {
      const code = 'BW-ET72-CCPY-ZAYN';
      const encrypted = encryptDeliveryLicenseCode(code);

      expect(encrypted).toBeDefined();
      expect(typeof encrypted).toBe('string');
      expect(encrypted).not.toContain(code);

      const decrypted = decryptDeliveryLicenseCode(encrypted);
      expect(decrypted).toBe(code);
    });

    it('generates different ciphertexts for identical plaintext due to random 96-bit IV', () => {
      const code = 'BW-ET72-CCPY-ZAYN';
      const enc1 = encryptDeliveryLicenseCode(code);
      const enc2 = encryptDeliveryLicenseCode(code);

      expect(enc1).not.toBe(enc2);
      expect(decryptDeliveryLicenseCode(enc1)).toBe(code);
      expect(decryptDeliveryLicenseCode(enc2)).toBe(code);
    });

    it('rejects tampered ciphertext safely (returns null)', () => {
      const code = 'BW-ABCD-1234-WXYZ';
      const encrypted = encryptDeliveryLicenseCode(code);

      // Modify one byte in the base64 string
      const buf = Buffer.from(encrypted, 'base64url');
      buf[buf.length - 1] ^= 0xff;
      const tampered = buf.toString('base64url');

      const decrypted = decryptDeliveryLicenseCode(tampered);
      expect(decrypted).toBeNull();
    });

    it('rejects truncated or malformed ciphertext safely (returns null)', () => {
      expect(decryptDeliveryLicenseCode('too_short')).toBeNull();
      expect(decryptDeliveryLicenseCode('')).toBeNull();
      expect(decryptDeliveryLicenseCode(null)).toBeNull();
      expect(decryptDeliveryLicenseCode(undefined)).toBeNull();
      expect(decryptDeliveryLicenseCode('not-a-valid-base64-payload!!!')).toBeNull();
    });

    it('throws error when encrypting invalid or empty license code', () => {
      expect(() => encryptDeliveryLicenseCode('')).toThrow();
      expect(() => encryptDeliveryLicenseCode(null as any)).toThrow();
    });
  });

  describe('2. End-to-End License Delivery Persistence & API Endpoints', () => {
    let orderId: number;
    let orderNumber: string;
    let generatedPlaintextCode: string;

    it('creates order, verifies payment, and generates license with encrypted delivery persistence', async () => {
      // 1. Create order
      const createRes = await app.inject({
        method: 'POST',
        url: '/v1/admin/orders',
        headers: { authorization: `Bearer ${adminApiKey}` },
        payload: {
          customerName: 'Kang Toni',
          customerContact: '081234567890',
          ownerEmail: 'mastoni75@yahoo.com',
          amount: 50000
        }
      });
      expect(createRes.statusCode).toBe(201);
      const createBody = JSON.parse(createRes.body);
      orderId = createBody.data.id;
      orderNumber = createBody.data.order_number;

      // 2. Verify payment
      const verifyRes = await app.inject({
        method: 'POST',
        url: `/v1/admin/orders/${orderId}/verify-payment`,
        headers: { authorization: `Bearer ${adminApiKey}` },
        payload: { paymentMethod: 'QRIS', paymentReference: 'QRIS-12345' }
      });
      expect(verifyRes.statusCode).toBe(200);

      // 3. Generate license
      const genRes = await app.inject({
        method: 'POST',
        url: `/v1/admin/orders/${orderId}/generate-license`,
        headers: { authorization: `Bearer ${adminApiKey}` },
        payload: {}
      });
      expect(genRes.statusCode).toBe(200);
      const genBody = JSON.parse(genRes.body);
      generatedPlaintextCode = genBody.data.licenseCode;
      expect(generatedPlaintextCode).toMatch(/^BW-[2-9A-HJ-NP-Z]{4}-[2-9A-HJ-NP-Z]{4}-[2-9A-HJ-NP-Z]{4}$/);

      // 4. Verify DB stores encrypted delivery code and not plaintext
      const dbOrder = db.prepare('SELECT * FROM orders WHERE id = ?').get(orderId) as any;
      expect(dbOrder.encrypted_delivery_license_code).toBeDefined();
      expect(dbOrder.encrypted_delivery_license_code).not.toBeNull();
      expect(dbOrder.encrypted_delivery_license_code).not.toContain(generatedPlaintextCode);

      // Check licenses table only stores hash
      const dbLicense = db.prepare('SELECT * FROM licenses WHERE id = ?').get(dbOrder.license_id) as any;
      expect(dbLicense.license_code_hash).toBe(hashLicenseCode(generatedPlaintextCode));
    });

    it('GET /v1/admin/orders (listing) does NOT expose plaintext license code', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/v1/admin/orders',
        headers: { authorization: `Bearer ${adminApiKey}` }
      });
      expect(res.statusCode).toBe(200);
      const body = JSON.parse(res.body);
      const foundOrder = body.data.find((o: any) => o.id === orderId);
      expect(foundOrder).toBeDefined();
      expect(foundOrder.licenseCode).toBeUndefined();
      expect(foundOrder.license_code).toBeUndefined();
      expect(foundOrder.encrypted_delivery_license_code).toBeUndefined();
    });

    it('GET /v1/admin/orders/:id (detail) does NOT expose plaintext license code', async () => {
      const res = await app.inject({
        method: 'GET',
        url: `/v1/admin/orders/${orderId}`,
        headers: { authorization: `Bearer ${adminApiKey}` }
      });
      expect(res.statusCode).toBe(200);
      const body = JSON.parse(res.body);
      expect(body.data.order.licenseCode).toBeUndefined();
      expect(body.data.order.license_code).toBeUndefined();
      expect(body.data.order.encrypted_delivery_license_code).toBeUndefined();
    });

    it('GET /v1/admin/orders/:id/delivery-license returns plaintext license code for authenticated admin', async () => {
      const res = await app.inject({
        method: 'GET',
        url: `/v1/admin/orders/${orderId}/delivery-license`,
        headers: { authorization: `Bearer ${adminApiKey}` }
      });
      expect(res.statusCode).toBe(200);
      const body = JSON.parse(res.body);
      expect(body.success).toBe(true);
      expect(body.data.orderId).toBe(orderId);
      expect(body.data.orderNumber).toBe(orderNumber);
      expect(body.data.licenseCode).toBe(generatedPlaintextCode);
      expect(body.data.customerName).toBe('Kang Toni');
      expect(body.data.hasDeliveryCode).toBe(true);
    });

    it('GET /v1/admin/orders/:id/delivery-license rejects unauthenticated requests with 401', async () => {
      const res = await app.inject({
        method: 'GET',
        url: `/v1/admin/orders/${orderId}/delivery-license`
      });
      expect(res.statusCode).toBe(401);
    });

    it('records GET_DELIVERY_LICENSE in audit logs without exposing plaintext license code', () => {
      const auditLog = db
        .prepare('SELECT * FROM audit_logs WHERE action = ? ORDER BY id DESC LIMIT 1')
        .get('GET_DELIVERY_LICENSE') as any;

      expect(auditLog).toBeDefined();
      expect(auditLog.action).toBe('GET_DELIVERY_LICENSE');
      expect(auditLog.reason).toContain(`Order #${orderId}`);
      expect(auditLog.reason).not.toContain(generatedPlaintextCode);
      expect(auditLog.old_state).toBeNull();
      expect(auditLog.new_state).toBeNull();
    });

    it('handles legacy order with missing encrypted delivery code safely (returns null + hasDeliveryCode false)', async () => {
      // Simulate legacy order created without encrypted code
      const adminService = new AdminService(db);
      const legacyRes = adminService.createOrder({
        customerName: 'Legacy User',
        customerContact: '0811111111',
        ownerEmail: 'legacy@warung.id',
        amount: 50000
      });
      expect(legacyRes.success).toBe(true);
      const legacyOrderId = legacyRes.data!.id;

      const delivRes = await app.inject({
        method: 'GET',
        url: `/v1/admin/orders/${legacyOrderId}/delivery-license`,
        headers: { authorization: `Bearer ${adminApiKey}` }
      });

      expect(delivRes.statusCode).toBe(200);
      const delivBody = JSON.parse(delivRes.body);
      expect(delivBody.success).toBe(true);
      expect(delivBody.data.licenseCode).toBeNull();
      expect(delivBody.data.hasDeliveryCode).toBe(false);
    });

    it('reconcileOrderDeliveryLicense strictly verifies hash before updating and rejects mismatched codes', () => {
      const adminService = new AdminService(db);

      // Create a test order and license
      const testRes = adminService.createOrder({
        customerName: 'Reconcile User',
        customerContact: '0899999999',
        ownerEmail: 'reconcile@warung.id',
        amount: 50000
      });
      const recOrderId = testRes.data!.id;
      adminService.verifyOrderPayment(recOrderId, { paymentMethod: 'QRIS' });
      const genRes = adminService.generateLicenseForOrder(recOrderId);
      const realCode = genRes.data!.licenseCode;

      // Wipe the encrypted delivery code to simulate historical state
      db.prepare('UPDATE orders SET encrypted_delivery_license_code = NULL WHERE id = ?').run(recOrderId);

      // 1. Attempt reconciliation with wrong code -> fails
      const failRec = adminService.reconcileOrderDeliveryLicense(recOrderId, 'BW-WRONG-CODE-9999');
      expect(failRec.success).toBe(false);
      expect(failRec.error?.code).toBe('HASH_MISMATCH');

      // 2. Attempt reconciliation with real code -> succeeds
      const passRec = adminService.reconcileOrderDeliveryLicense(recOrderId, realCode);
      expect(passRec.success).toBe(true);

      // 3. Verify retrieval now succeeds
      const getRes = adminService.getOrderDeliveryLicense(recOrderId);
      expect(getRes.success).toBe(true);
      expect(getRes.data?.licenseCode).toBe(realCode);
      expect(getRes.data?.hasDeliveryCode).toBe(true);
    });
  });
});
