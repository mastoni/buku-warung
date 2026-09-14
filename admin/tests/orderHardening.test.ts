import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import { FastifyInstance } from 'fastify';
import { buildAdminApp } from '../src/app.js';
import { buildApp as buildLicenseServerApp } from '../../server/src/app.js';
import { getDatabase as getLicenseDb, closeDatabase as closeLicenseDb } from '../../server/src/db/database.js';
import { config as adminConfig } from '../src/config/index.js';
import { config as licenseServerConfig } from '../../server/src/config/index.js';

describe('Buku Warung Commercial Operations Hardening (C.10.3)', () => {
  let adminApp: FastifyInstance;
  let licenseServerApp: FastifyInstance;
  let licenseServerPort: number;
  let sessionCookie: string = '';

  let order1Id: number;
  let order2Id: number;
  let order3Id: number;
  let order1LicenseCode: string = '';

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

  it('1. Create multiple controlled orders with various profiles', async () => {
    // Order 1: Budi
    const res1 = await adminApp.inject({
      method: 'POST',
      url: '/api/orders',
      headers: { cookie: sessionCookie },
      payload: {
        customerName: 'Budi Santoso',
        customerWhatsapp: '085157056604',
        ownerEmail: 'budi.santoso@warung.id',
        amount: 50000,
        notes: 'Pesan via WhatsApp landing'
      }
    });
    expect(res1.statusCode).toBe(201);
    const body1 = JSON.parse(res1.body);
    order1Id = body1.data.orderId;

    // Order 2: Siti
    const res2 = await adminApp.inject({
      method: 'POST',
      url: '/api/orders',
      headers: { cookie: sessionCookie },
      payload: {
        customerName: 'Siti Rahma',
        customerWhatsapp: '081298765432',
        ownerEmail: 'siti.rahma@toko.id',
        amount: 50000,
        notes: 'Toko Kelontong Siti'
      }
    });
    expect(res2.statusCode).toBe(201);
    const body2 = JSON.parse(res2.body);
    order2Id = body2.data.orderId;

    // Order 3: Ahmad
    const res3 = await adminApp.inject({
      method: 'POST',
      url: '/api/orders',
      headers: { cookie: sessionCookie },
      payload: {
        customerName: 'Ahmad Dahlan',
        customerWhatsapp: '081311223344',
        ownerEmail: 'ahmad@warungku.id',
        amount: 50000,
        notes: 'Warung Kopi Ahmad'
      }
    });
    expect(res3.statusCode).toBe(201);
    const body3 = JSON.parse(res3.body);
    order3Id = body3.data.orderId;
  });

  it('2. Dashboard metrics correctly reflect initial UNPAID state', async () => {
    const res = await adminApp.inject({
      method: 'GET',
      url: '/api/orders',
      headers: { cookie: sessionCookie }
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.body);
    expect(body.success).toBe(true);
    expect(body.data.length).toBe(3);
    expect(body.metrics.totalOrders).toBe(3);
    expect(body.metrics.pendingPayment).toBe(3);
    expect(body.metrics.paid).toBe(0);
    expect(body.metrics.paidRevenue).toBe(0); // Paid revenue must exclude UNPAID
  });

  it('3. Verify payment for Order 1 & Order 2 and verify metrics & paid revenue update', async () => {
    // Verify Order 1
    const vRes1 = await adminApp.inject({
      method: 'POST',
      url: `/api/orders/${order1Id}/verify-payment`,
      headers: { cookie: sessionCookie },
      payload: { paymentMethod: 'Transfer BCA', paymentReference: 'TRX-BCA-001' }
    });
    expect(vRes1.statusCode).toBe(200);

    // Verify Order 2
    const vRes2 = await adminApp.inject({
      method: 'POST',
      url: `/api/orders/${order2Id}/verify-payment`,
      headers: { cookie: sessionCookie },
      payload: { paymentMethod: 'QRIS', paymentReference: 'TRX-QRIS-002' }
    });
    expect(vRes2.statusCode).toBe(200);

    // Fetch updated metrics
    const res = await adminApp.inject({
      method: 'GET',
      url: '/api/orders',
      headers: { cookie: sessionCookie }
    });

    const body = JSON.parse(res.body);
    expect(body.metrics.totalOrders).toBe(3);
    expect(body.metrics.pendingPayment).toBe(1); // Order 3
    expect(body.metrics.paid).toBe(2);           // Order 1 & 2
    expect(body.metrics.pendingLicense).toBe(2);  // Order 1 & 2 need licenses
    expect(body.metrics.paidRevenue).toBe(100000);// 2 * 50000 (excludes order 3)
  });

  it('4. Filter Menunggu Pembayaran (PENDING_PAYMENT)', async () => {
    const res = await adminApp.inject({
      method: 'GET',
      url: '/api/orders?filter=PENDING_PAYMENT',
      headers: { cookie: sessionCookie }
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.body);
    expect(body.data.length).toBe(1);
    expect(body.data[0].id).toBe(order3Id);
    expect(body.data[0].paymentStatus).toBe('UNPAID');
  });

  it('5. Filter Sudah Bayar (PAID)', async () => {
    const res = await adminApp.inject({
      method: 'GET',
      url: '/api/orders?filter=PAID',
      headers: { cookie: sessionCookie }
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.body);
    expect(body.data.length).toBe(2);
    expect(body.data.every((o: any) => o.paymentStatus === 'PAID')).toBe(true);
  });

  it('6. Filter License Belum Dibuat (PENDING_LICENSE)', async () => {
    const res = await adminApp.inject({
      method: 'GET',
      url: '/api/orders?filter=PENDING_LICENSE',
      headers: { cookie: sessionCookie }
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.body);
    expect(body.data.length).toBe(2);
    expect(body.data.every((o: any) => o.paymentStatus === 'PAID' && !o.licenseId)).toBe(true);
  });

  it('7. Generate license for Order 1 and verify Action Required & Delivery state', async () => {
    const genRes = await adminApp.inject({
      method: 'POST',
      url: `/api/orders/${order1Id}/generate-license`,
      headers: { cookie: sessionCookie }
    });

    expect(genRes.statusCode).toBe(200);
    const genBody = JSON.parse(genRes.body);
    expect(genBody.data.licenseCode).toMatch(/^BW-/);
    order1LicenseCode = genBody.data.licenseCode;

    // Check metrics
    const res = await adminApp.inject({
      method: 'GET',
      url: '/api/orders',
      headers: { cookie: sessionCookie }
    });
    const body = JSON.parse(res.body);
    expect(body.metrics.pendingLicense).toBe(1); // Only Order 2 remains
  });

  it('8. Mark Order 1 as DELIVERED and test Filter Sudah Dikirim (DELIVERED)', async () => {
    const delRes = await adminApp.inject({
      method: 'POST',
      url: `/api/orders/${order1Id}/mark-delivered`,
      headers: { cookie: sessionCookie },
      payload: { notes: 'Sent APK and license via WhatsApp' }
    });
    expect(delRes.statusCode).toBe(200);

    const res = await adminApp.inject({
      method: 'GET',
      url: '/api/orders?filter=DELIVERED',
      headers: { cookie: sessionCookie }
    });
    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.body);
    expect(body.data.length).toBe(1);
    expect(body.data[0].id).toBe(order1Id);
    expect(body.data[0].status).toBe('DELIVERED');
  });

  it('9. Customer activation on Android -> Filter Sudah Aktif (ACTIVE)', async () => {
    // Activate license on device
    const actRes = await licenseServerApp.inject({
      method: 'POST',
      url: '/v1/license/activate',
      payload: {
        licenseCode: order1LicenseCode,
        ownerEmail: 'budi.santoso@warung.id',
        deviceBinding: 'SAMSUNG_GALAXY_A54_TEST_01'
      }
    });
    expect(actRes.statusCode).toBe(200);

    // Test filter ACTIVE
    const res = await adminApp.inject({
      method: 'GET',
      url: '/api/orders?filter=ACTIVE',
      headers: { cookie: sessionCookie }
    });
    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.body);
    expect(body.data.length).toBe(1);
    expect(body.data[0].id).toBe(order1Id);
    expect(body.data[0].status).toBe('ACTIVE');
  });

  it('10. Search by customer name (case-insensitive)', async () => {
    const res = await adminApp.inject({
      method: 'GET',
      url: '/api/orders?search=siti',
      headers: { cookie: sessionCookie }
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.body);
    expect(body.data.length).toBe(1);
    expect(body.data[0].customerName).toBe('Siti Rahma');
  });

  it('11. Search by WhatsApp', async () => {
    const res = await adminApp.inject({
      method: 'GET',
      url: '/api/orders?search=085157056604',
      headers: { cookie: sessionCookie }
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.body);
    expect(body.data.length).toBe(1);
    expect(body.data[0].id).toBe(order1Id);
  });

  it('12. Search by Owner Email', async () => {
    const res = await adminApp.inject({
      method: 'GET',
      url: '/api/orders?search=ahmad@warungku.id',
      headers: { cookie: sessionCookie }
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.body);
    expect(body.data.length).toBe(1);
    expect(body.data[0].id).toBe(order3Id);
  });

  it('13. Order detail returns complete grouped information and audit history', async () => {
    const res = await adminApp.inject({
      method: 'GET',
      url: `/api/orders/${order1Id}`,
      headers: { cookie: sessionCookie }
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.body);
    expect(body.success).toBe(true);
    expect(body.data.order.id).toBe(order1Id);
    expect(body.data.order.customerName).toBe('Budi Santoso');
    expect(body.data.order.paymentStatus).toBe('PAID');
    expect(body.data.license).toBeDefined();
    expect(body.data.devices.length).toBe(1);
    expect(body.data.auditLogs.length).toBeGreaterThanOrEqual(3); // CREATE_ORDER, VERIFY_PAYMENT, GENERATE_ORDER_LICENSE
  });

  it('14. Duplicate payment prevention (idempotency safety)', async () => {
    const res = await adminApp.inject({
      method: 'POST',
      url: `/api/orders/${order1Id}/verify-payment`,
      headers: { cookie: sessionCookie },
      payload: { paymentMethod: 'Transfer BCA' }
    });

    expect(res.statusCode).toBe(400);
    const body = JSON.parse(res.body);
    expect(body.error.code).toBe('ALREADY_PAID');
  });

  it('15. Unauthorized access rejected (401)', async () => {
    const res = await adminApp.inject({
      method: 'GET',
      url: '/api/orders'
    });

    expect(res.statusCode).toBe(401);
  });
});
