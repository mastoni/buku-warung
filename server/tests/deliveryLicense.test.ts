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

  describe('4. Secure Re-delivery for an Existing (ACTIVE) License', () => {
    let app2: FastifyInstance;
    let db2: Database.Database;
    const key2 = config.adminApiKey;

    beforeAll(async () => {
      db2 = getDatabase(':memory:');
      app2 = buildApp();
      await app2.ready();
    });

    afterAll(async () => {
      await app2.close();
    });

    /** Creates a paid order, generates its license, and returns ids + the plaintext code. */
    const seedDeliveredLicense = (email: string) => {
      const svc = new AdminService(db2);
      const order = svc.createOrder({
        customerName: 'Recovery User',
        customerContact: '081200000001',
        ownerEmail: email,
        amount: 50000
      });
      const oid = order.data!.id;
      svc.verifyOrderPayment(oid, { paymentMethod: 'QRIS' });
      const gen = svc.generateLicenseForOrder(oid);
      return { svc, oid, licenseId: gen.data!.licenseId, code: gen.data!.licenseCode };
    };

    const getDelivery = (oid: number) =>
      app2.inject({
        method: 'GET',
        url: `/v1/admin/orders/${oid}/delivery-license`,
        headers: { authorization: `Bearer ${key2}` }
      });

    const activate = (code: string, email: string, device: string) =>
      app2.inject({
        method: 'POST',
        url: '/v1/license/activate',
        payload: { licenseCode: code, ownerEmail: email, deviceBinding: device }
      });

    it('TEST 1: ACTIVE license still returns the SAME code with hasDeliveryCode=true', async () => {
      const { oid, code, licenseId } = seedDeliveredLicense('active.recover@example.com');
      const act = await activate(code, 'active.recover@example.com', 'device-active-1');
      expect(act.statusCode).toBe(200);
      expect(JSON.parse(act.body).status).toBe('ACTIVE');
      expect(db2.prepare('SELECT status FROM licenses WHERE id = ?').get(licenseId)).toEqual({
        status: 'ACTIVE'
      });

      const res = await getDelivery(oid);
      expect(res.statusCode).toBe(200);
      const body = JSON.parse(res.body);
      expect(body.data.licenseCode).toBe(code);
      expect(body.data.hasDeliveryCode).toBe(true);
    });

    it('TEST 2: REVOKED license is still retrievable by admin but no longer validates', async () => {
      const { svc, oid, code, licenseId } = seedDeliveredLicense('revoked.recover@example.com');
      await activate(code, 'revoked.recover@example.com', 'device-revoked-1');
      svc.revokeLicense(licenseId, 'ADMIN_API', 'audit test revocation');

      // Retrieval path is status-agnostic: the code is still returned to an authenticated admin.
      const res = await getDelivery(oid);
      expect(res.statusCode).toBe(200);
      expect(JSON.parse(res.body).data.licenseCode).toBe(code);

      // But the entitlement is dead: activation/validation must not succeed.
      const reAct = await activate(code, 'revoked.recover@example.com', 'device-revoked-2');
      expect(JSON.parse(reAct.body).status).not.toBe('ACTIVE');
    });

    it('TEST 3: NULL delivery code on an ACTIVE license is reported as unavailable, not silently empty', async () => {
      const { oid, code, licenseId } = seedDeliveredLicense('legacy.active@example.com');
      await activate(code, 'legacy.active@example.com', 'device-legacy-1');
      // Reproduce the real production gap: order predates the encrypted delivery code.
      db2.prepare('UPDATE orders SET encrypted_delivery_license_code = NULL WHERE id = ?').run(oid);
      expect(db2.prepare('SELECT status FROM licenses WHERE id = ?').get(licenseId)).toEqual({
        status: 'ACTIVE'
      });

      const res = await getDelivery(oid);
      expect(res.statusCode).toBe(200);
      const data = JSON.parse(res.body).data;
      expect(data.licenseCode).toBeNull();
      expect(data.hasDeliveryCode).toBe(false);
      // Machine-readable signal so the frontend can tell "unrecoverable" from "request failed".
      expect(data).toHaveProperty('hasDeliveryCode', false);
    });

    it('TEST 4: reconcile on an ACTIVE license with the correct code restores retrieval', async () => {
      const { svc, oid, code, licenseId } = seedDeliveredLicense('reconcile.active@example.com');
      await activate(code, 'reconcile.active@example.com', 'device-reconcile-1');
      db2.prepare('UPDATE orders SET encrypted_delivery_license_code = NULL WHERE id = ?').run(oid);

      const rec = svc.reconcileOrderDeliveryLicense(oid, code);
      expect(rec.success).toBe(true);

      const stored = db2.prepare('SELECT encrypted_delivery_license_code FROM orders WHERE id = ?').get(oid) as any;
      expect(stored.encrypted_delivery_license_code).toBeTruthy();
      expect(stored.encrypted_delivery_license_code).not.toContain(code);

      const res = await getDelivery(oid);
      expect(JSON.parse(res.body).data.licenseCode).toBe(code);
      // Reconciliation must not disturb the entitlement.
      expect(db2.prepare('SELECT status FROM licenses WHERE id = ?').get(licenseId)).toEqual({
        status: 'ACTIVE'
      });
    });

    it('TEST 5: reconcile with a wrong code fails and leaves the database untouched', async () => {
      const { svc, oid, licenseId } = seedDeliveredLicense('wrong.code@example.com');
      db2.prepare('UPDATE orders SET encrypted_delivery_license_code = NULL WHERE id = ?').run(oid);
      const before = db2.prepare('SELECT * FROM orders WHERE id = ?').get(oid);

      const rec = svc.reconcileOrderDeliveryLicense(oid, 'BW-WRONG-CODE-9999');
      expect(rec.success).toBe(false);
      expect(rec.error?.code).toBe('HASH_MISMATCH');

      const after = db2.prepare('SELECT * FROM orders WHERE id = ?').get(oid);
      expect(after.encrypted_delivery_license_code).toBeNull();
      expect(after.license_id).toBe(before.license_id);
      expect(after.status).toBe(before.status);
      expect(db2.prepare('SELECT status FROM licenses WHERE id = ?').get(licenseId)).toEqual({
        status: 'PENDING'
      });
    });

    it('TEST 6: retrieval is read-only — it mutates no license, order, or device state', async () => {
      const { oid, code, licenseId } = seedDeliveredLicense('readonly.check@example.com');
      await activate(code, 'readonly.check@example.com', 'device-readonly-1');

      const beforeOrder = db2.prepare('SELECT * FROM orders WHERE id = ?').get(oid);
      const beforeLicense = db2.prepare('SELECT * FROM licenses WHERE id = ?').get(licenseId);
      const beforeDevices = db2.prepare('SELECT * FROM license_devices WHERE license_id = ?').all(licenseId);

      await getDelivery(oid);
      await getDelivery(oid);

      expect(db2.prepare('SELECT * FROM orders WHERE id = ?').get(oid)).toEqual(beforeOrder);
      expect(db2.prepare('SELECT * FROM licenses WHERE id = ?').get(licenseId)).toEqual(beforeLicense);
      expect(db2.prepare('SELECT * FROM license_devices WHERE license_id = ?').all(licenseId)).toEqual(
        beforeDevices
      );
    });

    it('TEST 7: each retrieval writes exactly one audit row that never contains the plaintext code', async () => {
      const { svc, oid, code, licenseId } = seedDeliveredLicense('audit.redeliver@example.com');
      const before = db2
        .prepare("SELECT COUNT(*) as c FROM audit_logs WHERE action = 'GET_DELIVERY_LICENSE' AND license_id = ?")
        .get(licenseId) as { c: number };

      await getDelivery(oid);

      const after = db2
        .prepare("SELECT COUNT(*) as c FROM audit_logs WHERE action = 'GET_DELIVERY_LICENSE' AND license_id = ?")
        .get(licenseId) as { c: number };
      expect(after.c).toBe(before.c + 1);

      const rows = db2
        .prepare("SELECT reason FROM audit_logs WHERE action = 'GET_DELIVERY_LICENSE' AND license_id = ?")
        .all(licenseId) as Array<{ reason: string }>;
      for (const r of rows) {
        expect(r.reason).not.toContain(code);
      }
      expect(svc).toBeTruthy();
    });
  });

  describe('5. createLicense always has a delivery/recovery path', () => {
    let app3: FastifyInstance;
    let db3: Database.Database;
    const key3 = config.adminApiKey;

    beforeAll(async () => {
      db3 = getDatabase(':memory:');
      app3 = buildApp();
      await app3.ready();
    });

    afterAll(async () => {
      await app3.close();
    });

    const createViaApi = (payload: Record<string, unknown>) =>
      app3.inject({
        method: 'POST',
        url: '/v1/admin/licenses',
        headers: { authorization: `Bearer ${key3}` },
        payload
      });

    it('createLicense WITH customer info persists encrypted delivery code and creates an order', async () => {
      const res = await createViaApi({
        ownerEmail: 'with.info@example.com',
        customerName: 'With Info',
        customerContact: '081200000111'
      });
      expect(res.statusCode).toBe(201);
      const data = JSON.parse(res.body).data;
      expect(data.orderNumber).toBeTruthy();

      const order = db3.prepare('SELECT * FROM orders WHERE order_number = ?').get(data.orderNumber) as any;
      expect(order).toBeDefined();
      expect(order.license_id).toBe(data.licenseId);
      expect(order.status).toBe('LICENSE_CREATED');
      expect(order.encrypted_delivery_license_code).toBeTruthy();
      expect(order.encrypted_delivery_license_code).not.toContain(data.licenseCode);
    });

    it('createLicense WITHOUT customerName/customerContact STILL creates an order with a retrievable code', async () => {
      const res = await createViaApi({ ownerEmail: 'no.info@example.com' });
      expect(res.statusCode).toBe(201);
      const data = JSON.parse(res.body).data;
      expect(data.orderNumber).toBeTruthy();

      // The regression this guards: previously no order row existed, so the License Code
      // could never be retrieved again.
      const order = db3.prepare('SELECT * FROM orders WHERE order_number = ?').get(data.orderNumber) as any;
      expect(order).toBeDefined();
      expect(order.license_id).toBe(data.licenseId);
      expect(order.encrypted_delivery_license_code).toBeTruthy();
      expect(order.encrypted_delivery_license_code).not.toContain(data.licenseCode);

      // Existing fallbacks preserved.
      expect(order.customer_name).toBe('no.info@example.com');
      expect(order.customer_contact).toBe('no.info@example.com');
      expect(order.owner_email).toBe('no.info@example.com');

      // And it is retrievable through the authenticated admin endpoint.
      const deliv = await app3.inject({
        method: 'GET',
        url: `/v1/admin/orders/${order.id}/delivery-license`,
        headers: { authorization: `Bearer ${key3}` }
      });
      expect(deliv.statusCode).toBe(200);
      expect(JSON.parse(deliv.body).data.licenseCode).toBe(data.licenseCode);
      expect(JSON.parse(deliv.body).data.hasDeliveryCode).toBe(true);
    });

    it('createLicense stores only a hash on the license row, never plaintext', async () => {
      const res = await createViaApi({ ownerEmail: 'hash.only@example.com' });
      const data = JSON.parse(res.body).data;
      const lic = db3.prepare('SELECT * FROM licenses WHERE id = ?').get(data.licenseId) as any;
      expect(lic.license_code_hash).toBe(hashLicenseCode(data.licenseCode));
      expect(lic.license_code_hash).not.toBe(data.licenseCode);
      const cols = db3.prepare('PRAGMA table_info(licenses)').all().map((c: any) => c.name);
      expect(cols).not.toContain('license_code');
      expect(cols).not.toContain('license_code_plain');
    });
  });
});
