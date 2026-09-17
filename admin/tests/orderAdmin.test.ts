import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import { FastifyInstance } from 'fastify';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { buildAdminApp } from '../src/app.js';
import { buildApp as buildLicenseServerApp } from '../../server/src/app.js';
import { getDatabase as getLicenseDb, closeDatabase as closeLicenseDb } from '../../server/src/db/database.js';
import { config as adminConfig } from '../src/config/index.js';
import { config as licenseServerConfig } from '../../server/src/config/index.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

describe('Buku Warung Sales & Order Management Admin BFF (C.10.1)', () => {
  let adminApp: FastifyInstance;
  let licenseServerApp: FastifyInstance;
  let licenseServerPort: number;

  let sessionCookie: string = '';
  let testOrderId: number = 0;
  let testOrderCode: string = '';
  let generatedLicenseCode: string = '';

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

  it('1. Create order via Admin Proxy', async () => {
    const res = await adminApp.inject({
      method: 'POST',
      url: '/api/orders',
      headers: { cookie: sessionCookie },
      payload: {
        customerName: 'Budi Santoso',
        customerWhatsapp: '081234567890',
        ownerEmail: 'budi.santoso@warung.com',
        notes: 'Order perdana v0.1.0'
      }
    });

    expect(res.statusCode).toBe(201);
    const body = JSON.parse(res.body);
    expect(body.success).toBe(true);
    expect(body.data.orderCode).toMatch(/^BW-/);
    expect(body.data.status).toBe('PENDING_PAYMENT');
    expect(body.data.paymentStatus).toBe('UNPAID');
    expect(body.data.amount).toBe(50000);

    testOrderId = body.data.orderId;
    testOrderCode = body.data.orderCode;
  });

  it('2. List orders and verify summary metrics via Admin Proxy', async () => {
    const res = await adminApp.inject({
      method: 'GET',
      url: '/api/orders',
      headers: { cookie: sessionCookie }
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.body);
    expect(body.success).toBe(true);
    expect(Array.isArray(body.data)).toBe(true);
    expect(body.metrics.totalOrders).toBeGreaterThanOrEqual(1);
    expect(body.metrics.pendingPayment).toBeGreaterThanOrEqual(1);
  });

  it('3. Get order detail via Admin Proxy', async () => {
    const res = await adminApp.inject({
      method: 'GET',
      url: `/api/orders/${testOrderId}`,
      headers: { cookie: sessionCookie }
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.body);
    expect(body.success).toBe(true);
    expect(body.data.order.id).toBe(testOrderId);
    expect(body.data.order.customer_name).toBe('Budi Santoso');
    expect(body.data.order.owner_email).toBe('budi.santoso@warung.com');
  });

  it('4. Verify payment (UNPAID -> PAID) via Admin Proxy', async () => {
    const res = await adminApp.inject({
      method: 'POST',
      url: `/api/orders/${testOrderId}/verify-payment`,
      headers: { cookie: sessionCookie },
      payload: {
        paymentMethod: 'Transfer BCA',
        paymentReference: 'TRX-BCA-99281',
        notes: 'Dikonfirmasi admin via proxy'
      }
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.body);
    expect(body.success).toBe(true);
    expect(body.data.paymentStatus).toBe('PAID');
    expect(body.data.status).toBe('PAID');
  });

  it('5. Generate license for PAID order via Admin Proxy', async () => {
    const res = await adminApp.inject({
      method: 'POST',
      url: `/api/orders/${testOrderId}/generate-license`,
      headers: { cookie: sessionCookie }
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.body);
    expect(body.success).toBe(true);
    expect(body.data.licenseCode).toMatch(/^BW-[2-9A-HJ-NP-Z]{4}-[2-9A-HJ-NP-Z]{4}-[2-9A-HJ-NP-Z]{4}$/);
    expect(body.data.status).toBe('LICENSE_CREATED');
    expect(body.data.ownerEmail).toBe('budi.santoso@warung.com');

    generatedLicenseCode = body.data.licenseCode;
  });

  it('6. Duplicate license generation protection (idempotency)', async () => {
    const res = await adminApp.inject({
      method: 'POST',
      url: `/api/orders/${testOrderId}/generate-license`,
      headers: { cookie: sessionCookie }
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.body);
    expect(body.success).toBe(true);
    expect(body.data.licenseId).toBeDefined();
    expect(body.data.licenseUuid).toBeDefined();
  });

  it('7. Mark order as DELIVERED via Admin Proxy', async () => {
    const res = await adminApp.inject({
      method: 'POST',
      url: `/api/orders/${testOrderId}/mark-delivered`,
      headers: { cookie: sessionCookie }
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.body);
    expect(body.success).toBe(true);
    expect(body.data.status).toBe('DELIVERED');
    expect(body.data.deliveredAt).toBeDefined();
  });

  it('8. Reflect client device activation as ACTIVE order status', async () => {
    // Activate license on device
    const actRes = await licenseServerApp.inject({
      method: 'POST',
      url: '/v1/license/activate',
      payload: {
        licenseCode: generatedLicenseCode,
        ownerEmail: 'budi.santoso@warung.com',
        deviceBinding: 'SAMSUNG_GALAXY_A54_PROD_1'
      }
    });
    expect(actRes.statusCode).toBe(200);

    // Fetch order detail from Admin Proxy
    const detailRes = await adminApp.inject({
      method: 'GET',
      url: `/api/orders/${testOrderId}`,
      headers: { cookie: sessionCookie }
    });

    expect(detailRes.statusCode).toBe(200);
    const body = JSON.parse(detailRes.body);
    expect(body.data.order.status).toBe('ACTIVE');
    expect(body.data.license.status).toBe('ACTIVE');
    expect(body.data.devices.length).toBe(1);
    expect(body.data.devices[0].device_binding).toBe('SAMSUNG_GALAXY_A54_PROD_1');
  });

  it('9. Unauthorized access without session is rejected (401)', async () => {
    const res = await adminApp.inject({
      method: 'GET',
      url: '/api/orders'
    });
    expect(res.statusCode).toBe(401);
  });

  it('10. Authenticated Admin retrieves delivery license code via /api/orders/:id/delivery-license', async () => {
    const res = await adminApp.inject({
      method: 'GET',
      url: `/api/orders/${testOrderId}/delivery-license`,
      headers: { cookie: sessionCookie }
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.body);
    expect(body.success).toBe(true);
    expect(body.data.orderId).toBe(testOrderId);
    expect(body.data.licenseCode).toBe(generatedLicenseCode);
    expect(body.data.hasDeliveryCode).toBe(true);
  });

  it('11. Unauthenticated request to /api/orders/:id/delivery-license is rejected with 401', async () => {
    const res = await adminApp.inject({
      method: 'GET',
      url: `/api/orders/${testOrderId}/delivery-license`
    });
    expect(res.statusCode).toBe(401);
  });

  it('12. Frontend app.js correctly invokes /api/orders/:id/delivery-license and never emits fake BW-XXXX-XXXX-XXXX placeholder', () => {
    const appJsPath = path.resolve(__dirname, '../public/app.js');
    const appJs = fs.readFileSync(appJsPath, 'utf8');

    expect(appJs).toContain('/delivery-license');
    expect(appJs).toContain('openDeliveryPreparationModal');
    expect(appJs).not.toContain('BW-XXXX-XXXX-XXXX');
    expect(appJs).toContain('[Hubungi Admin untuk Kode Lisensi]');
    expect(appJs).toContain('(Kode lisensi historis tidak tersimpan)');
  });
});
