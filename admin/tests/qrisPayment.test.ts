import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import { FastifyInstance } from 'fastify';
import { buildAdminApp } from '../src/app.js';
import { buildApp as buildLicenseServerApp } from '../../server/src/app.js';
import { getDatabase as getLicenseDb, closeDatabase as closeLicenseDb } from '../../server/src/db/database.js';
import { config as adminConfig } from '../src/config/index.js';
import { config as licenseServerConfig } from '../../server/src/config/index.js';

describe('Buku Warung C.13 — QRIS Semi-Manual Payment Integration', () => {
  let adminApp: FastifyInstance;
  let licenseServerApp: FastifyInstance;
  let licenseServerPort: number;
  let sessionCookie: string = '';

  let qrisOrderId: number;
  let transferOrderId: number;

  beforeAll(async () => {
    // 1. Boot in-memory License Server on dynamic port
    getLicenseDb(':memory:');
    licenseServerApp = buildLicenseServerApp();
    await licenseServerApp.listen({ port: 0, host: '127.0.0.1' });
    const addr = licenseServerApp.server.address() as any;
    licenseServerPort = addr.port;

    // Point Admin BFF to this running License Server instance
    adminConfig.licenseServerUrl = `http://127.0.0.1:${licenseServerPort}`;
    adminConfig.adminApiKey = licenseServerConfig.adminApiKey;

    // 2. Boot Admin BFF
    adminApp = buildAdminApp();
    await adminApp.ready();

    // Login to obtain session cookie
    const res = await adminApp.inject({
      method: 'POST',
      url: '/api/auth/login',
      payload: {
        username: adminConfig.adminUsername,
        password: adminConfig.adminPassword
      }
    });
    const setCookie = res.headers['set-cookie'] as string | string[];
    const cookieHeader = Array.isArray(setCookie) ? setCookie[0] : setCookie;
    sessionCookie = cookieHeader.split(';')[0];
  });

  afterAll(async () => {
    await adminApp.close();
    await licenseServerApp.close();
    closeLicenseDb();
  });

  it('1. Create Order with QRIS payment method', async () => {
    const res = await adminApp.inject({
      method: 'POST',
      url: '/api/orders',
      headers: {
        cookie: sessionCookie,
        'content-type': 'application/json'
      },
      payload: {
        customerName: 'Toko Kiara Mitra',
        customerWhatsapp: '085123456789',
        ownerEmail: 'kiara@warung.id',
        notes: 'Order QRIS Kios Kiara'
      }
    });

    expect(res.statusCode).toBe(201);
    const json = res.json();
    expect(json.success).toBe(true);
    expect(json.data.paymentStatus).toBe('UNPAID');
    expect(json.data.amount).toBe(50000);
    expect(json.data.licenseId).toBeNull();
    qrisOrderId = json.data.id;
    expect(qrisOrderId).toBeGreaterThan(0);
  });

  it('2. Order details show UNPAID status before admin verification', async () => {
    const res = await adminApp.inject({
      method: 'GET',
      url: `/api/orders/${qrisOrderId}`,
      headers: { cookie: sessionCookie }
    });

    expect(res.statusCode).toBe(200);
    const json = res.json();
    expect(json.success).toBe(true);
    expect(json.data.order.payment_status).toBe('UNPAID');
    expect(json.data.order.amount).toBe(50000);
    expect(json.data.license).toBeNull();
  });

  it('3. Admin manually verifies QRIS payment (Merchant: KIOS KIARA, NMID: ID1026512762125)', async () => {
    const res = await adminApp.inject({
      method: 'POST',
      url: `/api/orders/${qrisOrderId}/verify-payment`,
      headers: {
        cookie: sessionCookie,
        'content-type': 'application/json'
      },
      payload: {
        paymentMethod: 'QRIS',
        paymentReference: 'QRIS-KIOSKIARA-NMID1026512762125-REF001',
        notes: 'Mutasi QRIS Kios Kiara terkonfirmasi masuk oleh admin'
      }
    });

    expect(res.statusCode).toBe(200);
    const json = res.json();
    expect(json.success).toBe(true);
    expect(json.data.paymentStatus).toBe('PAID');
    expect(json.data.paymentMethod).toBe('QRIS');
    expect(json.data.paymentReference).toBe('QRIS-KIOSKIARA-NMID1026512762125-REF001');
    expect(json.data.verifiedBy).toBe('ADMIN_API');
    expect(json.data.verifiedAt).toBeGreaterThan(0);
  });

  it('4. Idempotency & ALREADY_PAID protection prevents duplicate verification', async () => {
    const res = await adminApp.inject({
      method: 'POST',
      url: `/api/orders/${qrisOrderId}/verify-payment`,
      headers: {
        cookie: sessionCookie,
        'content-type': 'application/json'
      },
      payload: {
        paymentMethod: 'QRIS',
        paymentReference: 'DUPLICATE-ATTEMPT'
      }
    });

    expect(res.statusCode).toBe(400);
    const json = res.json();
    expect(json.success).toBe(false);
    expect(json.error.code).toBe('ALREADY_PAID');
  });

  it('5. License generation remains an explicit separate Admin action after PAID', async () => {
    // Check order detail: license is not automatically created
    const detailRes = await adminApp.inject({
      method: 'GET',
      url: `/api/orders/${qrisOrderId}`,
      headers: { cookie: sessionCookie }
    });
    expect(detailRes.json().data.license).toBeNull();

    // Now admin explicitly generates the license
    const genRes = await adminApp.inject({
      method: 'POST',
      url: `/api/orders/${qrisOrderId}/generate-license`,
      headers: { cookie: sessionCookie }
    });

    expect(genRes.statusCode).toBe(200);
    const genJson = genRes.json();
    expect(genJson.success).toBe(true);
    expect(genJson.data.licenseCode).toMatch(/^BW-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$/);
    expect(genJson.data.licenseId).toBeGreaterThan(0);
  });

  it('6. Legacy / alternate payment methods (Transfer Bank) remain fully supported', async () => {
    const createRes = await adminApp.inject({
      method: 'POST',
      url: '/api/orders',
      headers: {
        cookie: sessionCookie,
        'content-type': 'application/json'
      },
      payload: {
        customerName: 'Pak Wahyu',
        customerWhatsapp: '081399887766',
        ownerEmail: 'wahyu@warung.id'
      }
    });
    transferOrderId = createRes.json().data.id;

    const verifyRes = await adminApp.inject({
      method: 'POST',
      url: `/api/orders/${transferOrderId}/verify-payment`,
      headers: {
        cookie: sessionCookie,
        'content-type': 'application/json'
      },
      payload: {
        paymentMethod: 'Transfer Bank BCA',
        paymentReference: 'BCA-TRF-998811'
      }
    });

    expect(verifyRes.statusCode).toBe(200);
    const json = verifyRes.json();
    expect(json.success).toBe(true);
    expect(json.data.paymentStatus).toBe('PAID');
    expect(json.data.paymentMethod).toBe('Transfer Bank BCA');
  });
});
