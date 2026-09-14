import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import { FastifyInstance } from 'fastify';
import Database from 'better-sqlite3';
import { buildApp } from '../src/app.js';
import { getDatabase, closeDatabase } from '../src/db/database.js';
import { config } from '../src/config/index.js';

describe('Sales & Order Management (C.10.1)', () => {
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

  let createdOrderId = 0;
  let createdOrderNumber = '';
  let generatedLicenseCode = '';

  // 1. Create order initially PENDING_PAYMENT / UNPAID
  it('1. Create order -> status PENDING_PAYMENT / payment_status UNPAID', async () => {
    const res = await app.inject({
      method: 'POST',
      url: '/v1/admin/orders',
      headers: { authorization: `Bearer ${adminApiKey}` },
      payload: {
        customerName: 'Budi Santoso',
        customerContact: '085157056604',
        ownerEmail: 'budi.santoso@warung.id',
        product: 'BUKU_WARUNG',
        amount: 50000,
        notes: 'Pesan via WhatsApp landing'
      }
    });

    expect(res.statusCode).toBe(201);
    const body = JSON.parse(res.body);
    expect(body.success).toBe(true);
    expect(body.data.id).toBeGreaterThan(0);
    expect(body.data.customer_name).toBe('Budi Santoso');
    expect(body.data.customer_contact).toBe('085157056604');
    expect(body.data.owner_email).toBe('budi.santoso@warung.id');
    expect(body.data.status).toBe('PENDING_PAYMENT');
    expect(body.data.payment_status).toBe('UNPAID');
    expect(body.data.amount).toBe(50000);
    expect(body.data.license_id).toBeNull();

    createdOrderId = body.data.id;
    createdOrderNumber = body.data.order_number;
  });

  // 2. Reject invalid order inputs
  it('2. Reject invalid order input (missing email / invalid amount)', async () => {
    const res1 = await app.inject({
      method: 'POST',
      url: '/v1/admin/orders',
      headers: { authorization: `Bearer ${adminApiKey}` },
      payload: {
        customerName: 'Andi',
        customerContact: '0812345678',
        ownerEmail: 'invalid-email',
        amount: 50000
      }
    });
    expect(res1.statusCode).toBe(400);

    const res2 = await app.inject({
      method: 'POST',
      url: '/v1/admin/orders',
      headers: { authorization: `Bearer ${adminApiKey}` },
      payload: {
        customerName: '',
        customerContact: '0812345678',
        ownerEmail: 'andi@example.com',
        amount: 50000
      }
    });
    expect(res2.statusCode).toBe(400);
  });

  // 3. List orders with metrics
  it('3. List orders -> returns orders and metrics', async () => {
    const res = await app.inject({
      method: 'GET',
      url: '/v1/admin/orders',
      headers: { authorization: `Bearer ${adminApiKey}` }
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.body);
    expect(body.success).toBe(true);
    expect(Array.isArray(body.data)).toBe(true);
    expect(body.data.length).toBeGreaterThan(0);
    expect(body.metrics.totalOrders).toBeGreaterThan(0);
    expect(body.metrics.pendingPayment).toBeGreaterThan(0);
  });

  // 4. Cannot generate license for unpaid order
  it('4. Reject license generation for unpaid order', async () => {
    const res = await app.inject({
      method: 'POST',
      url: `/v1/admin/orders/${createdOrderId}/generate-license`,
      headers: { authorization: `Bearer ${adminApiKey}` }
    });

    expect(res.statusCode).toBe(400);
    const body = JSON.parse(res.body);
    expect(body.success).toBe(false);
    expect(body.error.code).toBe('ORDER_UNPAID');
  });

  // 5. Verify payment
  it('5. Verify payment -> status PAID, payment_status PAID', async () => {
    const res = await app.inject({
      method: 'POST',
      url: `/v1/admin/orders/${createdOrderId}/verify-payment`,
      headers: { authorization: `Bearer ${adminApiKey}` },
      payload: {
        paymentMethod: 'BANK_TRANSFER_BCA',
        paymentReference: 'TRX-BCA-987654',
        notes: 'Transfer verified via m-Banking'
      }
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.body);
    expect(body.success).toBe(true);
    expect(body.data.payment_status).toBe('PAID');
    expect(body.data.status).toBe('PAID');
    expect(body.data.verified_at).toBeGreaterThan(0);
    expect(body.data.payment_method).toBe('BANK_TRANSFER_BCA');
    expect(body.data.payment_reference).toBe('TRX-BCA-987654');
  });

  // 6. Duplicate payment protection
  it('6. Duplicate payment protection -> cannot double-pay already PAID order', async () => {
    const res = await app.inject({
      method: 'POST',
      url: `/v1/admin/orders/${createdOrderId}/verify-payment`,
      headers: { authorization: `Bearer ${adminApiKey}` },
      payload: {
        paymentMethod: 'QRIS',
        paymentReference: 'TRX-DUP-001'
      }
    });

    expect(res.statusCode).toBe(400);
    const body = JSON.parse(res.body);
    expect(body.error.code).toBe('ALREADY_PAID');
  });

  // 7. Generate license for PAID order
  it('7. Generate license for PAID order -> status LICENSE_CREATED', async () => {
    const res = await app.inject({
      method: 'POST',
      url: `/v1/admin/orders/${createdOrderId}/generate-license`,
      headers: { authorization: `Bearer ${adminApiKey}` }
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.body);
    expect(body.success).toBe(true);
    expect(body.data.licenseId).toBeGreaterThan(0);
    expect(body.data.licenseCode).toMatch(/^BW-[2-9A-HJ-NP-Z]{4}-[2-9A-HJ-NP-Z]{4}-[2-9A-HJ-NP-Z]{4}$/);
    expect(body.data.ownerEmail).toBe('budi.santoso@warung.id');
    expect(body.data.order.status).toBe('LICENSE_CREATED');
    expect(body.data.order.license_id).toBe(body.data.licenseId);

    generatedLicenseCode = body.data.licenseCode;
  });

  // 8. Idempotent license generation (prevent duplicate licenses)
  it('8. Idempotent license generation -> returns existing license without creating duplicates', async () => {
    const res = await app.inject({
      method: 'POST',
      url: `/v1/admin/orders/${createdOrderId}/generate-license`,
      headers: { authorization: `Bearer ${adminApiKey}` }
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.body);
    expect(body.success).toBe(true);
    expect(body.data.licenseCode).toBe('(Already Generated)');
  });

  // 9. Mark order as DELIVERED
  it('9. Mark order as DELIVERED -> status DELIVERED', async () => {
    const res = await app.inject({
      method: 'POST',
      url: `/v1/admin/orders/${createdOrderId}/mark-delivered`,
      headers: { authorization: `Bearer ${adminApiKey}` },
      payload: {
        notes: 'Sent APK and license via WhatsApp'
      }
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.body);
    expect(body.success).toBe(true);
    expect(body.data.status).toBe('DELIVERED');
    expect(body.data.delivered_at).toBeGreaterThan(0);
  });

  // 10. Customer activates license on Android -> order effective_status becomes ACTIVE
  it('10. Customer activation on Android -> order effective status reflects ACTIVE', async () => {
    // Activate license via Android activation endpoint
    const actRes = await app.inject({
      method: 'POST',
      url: '/v1/license/activate',
      payload: {
        licenseCode: generatedLicenseCode,
        ownerEmail: 'budi.santoso@warung.id',
        deviceBinding: 'DEVICE_UUID_BUDI_ANDROID_01'
      }
    });

    expect(actRes.statusCode).toBe(200);
    const actBody = JSON.parse(actRes.body);
    expect(actBody.status).toBe('ACTIVE');

    // Fetch order detail
    const orderRes = await app.inject({
      method: 'GET',
      url: `/v1/admin/orders/${createdOrderId}`,
      headers: { authorization: `Bearer ${adminApiKey}` }
    });

    expect(orderRes.statusCode).toBe(200);
    const orderBody = JSON.parse(orderRes.body);
    expect(orderBody.data.order.effective_status).toBe('ACTIVE');
    expect(orderBody.data.devices.length).toBe(1);
    expect(orderBody.data.devices[0].status).toBe('ACTIVE');
  });

  // 11. Security: Unauthorized access rejected
  it('11. Security -> unauthorized access rejected without admin token', async () => {
    const res = await app.inject({
      method: 'GET',
      url: '/v1/admin/orders'
    });

    expect(res.statusCode).toBe(401);
  });
});
