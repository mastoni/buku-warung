import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import { FastifyInstance } from 'fastify';
import Database from 'better-sqlite3';
import { buildApp } from '../src/app.js';
import { getDatabase, closeDatabase } from '../src/db/database.js';
import { config } from '../src/config/index.js';

describe('Buku Warung Commercial License Server MVP (C.2)', () => {
  let app: FastifyInstance;
  let db: Database.Database;
  const adminApiKey = config.adminApiKey;

  beforeAll(async () => {
    // Use an in-memory database for testing
    db = getDatabase(':memory:');
    app = buildApp();
    await app.ready();
  });

  afterAll(async () => {
    await app.close();
    closeDatabase();
  });

  let test01LicenseCode = '';
  let test01LicenseId = 0;
  const ownerEmail = 'warung.berkah@example.com';
  const deviceA = 'DEVICE_BINDING_ANDROID_A15_001';
  const deviceB = 'DEVICE_BINDING_ANDROID_REDMI_002';
  const deviceC = 'DEVICE_BINDING_ANDROID_PIXEL_003';

  // TEST 01: Create license -> PASS
  it('TEST 01: Create license -> PASS', async () => {
    const res = await app.inject({
      method: 'POST',
      url: '/v1/admin/licenses',
      headers: {
        authorization: `Bearer ${adminApiKey}`
      },
      payload: {
        ownerEmail: ownerEmail,
        product: 'BUKU_WARUNG',
        price: 50000,
        customerName: 'Pak Budi',
        customerContact: '081234567890'
      }
    });

    expect(res.statusCode).toBe(201);
    const body = JSON.parse(res.body);
    expect(body.success).toBe(true);
    expect(body.data.licenseCode).toMatch(/^BW-[2-9A-HJ-NP-Z]{4}-[2-9A-HJ-NP-Z]{4}-[2-9A-HJ-NP-Z]{4}$/);
    expect(body.data.status).toBe('PENDING');
    expect(body.data.ownerEmail).toBe('warung.berkah@example.com');

    test01LicenseCode = body.data.licenseCode;
    test01LicenseId = body.data.licenseId;
  });

  // TEST 02: Activate dengan email benar -> PASS
  it('TEST 02: Activate dengan email benar -> PASS', async () => {
    const res = await app.inject({
      method: 'POST',
      url: '/v1/license/activate',
      payload: {
        licenseCode: test01LicenseCode,
        ownerEmail: '   WARUNG.BERKAH@EXAMPLE.COM   ', // Mixed case + whitespace to test normalization
        deviceBinding: deviceA
      }
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.body);
    expect(body.success).toBe(true);
    expect(body.status).toBe('ACTIVE');
  });

  // TEST 03: Activate license yang sama + device yang sama -> idempotent PASS
  it('TEST 03: Activate license yang sama + device yang sama -> idempotent PASS', async () => {
    const res = await app.inject({
      method: 'POST',
      url: '/v1/license/activate',
      payload: {
        licenseCode: test01LicenseCode,
        ownerEmail: ownerEmail,
        deviceBinding: deviceA
      }
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.body);
    expect(body.success).toBe(true);
    expect(body.status).toBe('ACTIVE');
  });

  // TEST 04: Activate dengan email berbeda -> REJECT
  it('TEST 04: Activate dengan email berbeda -> REJECT', async () => {
    // Create another fresh license for test 04
    const createRes = await app.inject({
      method: 'POST',
      url: '/v1/admin/licenses',
      headers: { authorization: `Bearer ${adminApiKey}` },
      payload: { ownerEmail: 'owner.asli@example.com' }
    });
    const freshCode = JSON.parse(createRes.body).data.licenseCode;

    const res = await app.inject({
      method: 'POST',
      url: '/v1/license/activate',
      payload: {
        licenseCode: freshCode,
        ownerEmail: 'hacker.lain@example.com',
        deviceBinding: deviceA
      }
    });

    expect(res.statusCode).toBe(400);
    const body = JSON.parse(res.body);
    expect(body.success).toBe(false);
    expect(body.error.code).toBe('EMAIL_MISMATCH');
  });

  // TEST 05: Activate dengan device berbeda -> REJECT
  it('TEST 05: Activate dengan device berbeda -> REJECT', async () => {
    const res = await app.inject({
      method: 'POST',
      url: '/v1/license/activate',
      payload: {
        licenseCode: test01LicenseCode,
        ownerEmail: ownerEmail,
        deviceBinding: deviceB // Device B tries to activate active license
      }
    });

    expect(res.statusCode).toBe(400);
    const body = JSON.parse(res.body);
    expect(body.success).toBe(false);
    expect(body.error.code).toBe('DEVICE_MISMATCH');
  });

  // TEST 06: Validate device pertama -> VALID
  it('TEST 06: Validate device pertama -> VALID', async () => {
    const res = await app.inject({
      method: 'POST',
      url: '/v1/license/validate',
      payload: {
        licenseCode: test01LicenseCode,
        ownerEmail: ownerEmail,
        deviceBinding: deviceA
      }
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.body);
    expect(body.success).toBe(true);
    expect(body.status).toBe('VALID');
  });

  // TEST 07: Validate device kedua -> DEVICE_MISMATCH
  it('TEST 07: Validate device kedua -> DEVICE_MISMATCH', async () => {
    const res = await app.inject({
      method: 'POST',
      url: '/v1/license/validate',
      payload: {
        licenseCode: test01LicenseCode,
        ownerEmail: ownerEmail,
        deviceBinding: deviceB
      }
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.body);
    expect(body.success).toBe(false);
    expect(body.status).toBe('DEVICE_MISMATCH');
  });

  // TEST 08: Revoke license -> validation REVOKED
  it('TEST 08: Revoke license -> validation REVOKED', async () => {
    // Revoke license 1 via admin
    const revokeRes = await app.inject({
      method: 'POST',
      url: `/v1/admin/licenses/${test01LicenseId}/revoke`,
      headers: { authorization: `Bearer ${adminApiKey}` },
      payload: { reason: 'Test revocation' }
    });
    expect(revokeRes.statusCode).toBe(200);

    // Now validate
    const valRes = await app.inject({
      method: 'POST',
      url: '/v1/license/validate',
      payload: {
        licenseCode: test01LicenseCode,
        ownerEmail: ownerEmail,
        deviceBinding: deviceA
      }
    });

    expect(valRes.statusCode).toBe(200);
    const body = JSON.parse(valRes.body);
    expect(body.success).toBe(false);
    expect(body.status).toBe('REVOKED');
  });

  // TEST 09: Revoked license cannot activate -> REJECT
  it('TEST 09: Revoked license cannot activate -> REJECT', async () => {
    const res = await app.inject({
      method: 'POST',
      url: '/v1/license/activate',
      payload: {
        licenseCode: test01LicenseCode,
        ownerEmail: ownerEmail,
        deviceBinding: deviceA
      }
    });

    expect(res.statusCode).toBe(400);
    const body = JSON.parse(res.body);
    expect(body.success).toBe(false);
    expect(body.error.code).toBe('LICENSE_REVOKED');
  });

  // TEST 10: Recovery request -> sesuai policy (RECOVERY_PENDING)
  let recoveryLicenseCode = '';
  let recoveryLicenseId = 0;
  it('TEST 10: Recovery request -> sesuai policy (RECOVERY_PENDING)', async () => {
    // Create & activate a new license on Device A
    const createRes = await app.inject({
      method: 'POST',
      url: '/v1/admin/licenses',
      headers: { authorization: `Bearer ${adminApiKey}` },
      payload: { ownerEmail: 'pemilik.recovery@example.com' }
    });
    recoveryLicenseCode = JSON.parse(createRes.body).data.licenseCode;
    recoveryLicenseId = JSON.parse(createRes.body).data.licenseId;

    await app.inject({
      method: 'POST',
      url: '/v1/license/activate',
      payload: {
        licenseCode: recoveryLicenseCode,
        ownerEmail: 'pemilik.recovery@example.com',
        deviceBinding: deviceA
      }
    });

    // Request recovery for Device C (New device because Device A was lost/broken)
    const recRes = await app.inject({
      method: 'POST',
      url: '/v1/license/recover',
      payload: {
        licenseCode: recoveryLicenseCode,
        ownerEmail: 'pemilik.recovery@example.com',
        newDeviceBinding: deviceC,
        reason: 'HP lama rusak, pindah ke HP baru'
      }
    });

    expect(recRes.statusCode).toBe(200);
    const body = JSON.parse(recRes.body);
    expect(body.success).toBe(true);
    expect(body.status).toBe('RECOVERY_PENDING');
  });

  // TEST 11: Admin rebind: old device revoked, new device active
  it('TEST 11: Admin rebind: old device revoked, new device active', async () => {
    // Admin executes rebind to Device C
    const rebindRes = await app.inject({
      method: 'POST',
      url: '/v1/admin/license/rebind',
      headers: { authorization: `Bearer ${adminApiKey}` },
      payload: {
        licenseId: recoveryLicenseId,
        newDeviceBinding: deviceC,
        reason: 'Approved device replacement for customer'
      }
    });

    expect(rebindRes.statusCode).toBe(200);

    // Old Device A validation must now fail
    const valOldRes = await app.inject({
      method: 'POST',
      url: '/v1/license/validate',
      payload: {
        licenseCode: recoveryLicenseCode,
        ownerEmail: 'pemilik.recovery@example.com',
        deviceBinding: deviceA
      }
    });
    expect(JSON.parse(valOldRes.body).status).toBe('DEVICE_MISMATCH');

    // New Device C validation must now succeed
    const valNewRes = await app.inject({
      method: 'POST',
      url: '/v1/license/validate',
      payload: {
        licenseCode: recoveryLicenseCode,
        ownerEmail: 'pemilik.recovery@example.com',
        deviceBinding: deviceC
      }
    });
    expect(JSON.parse(valNewRes.body).status).toBe('VALID');
  });

  // TEST 12: Concurrent activation: dua device bersamaan -> hanya SATU active device
  it('TEST 12: Concurrent activation: dua device bersamaan -> hanya SATU active device', async () => {
    // Create new license
    const createRes = await app.inject({
      method: 'POST',
      url: '/v1/admin/licenses',
      headers: { authorization: `Bearer ${adminApiKey}` },
      payload: { ownerEmail: 'concurrent.owner@example.com' }
    });
    const concCode = JSON.parse(createRes.body).data.licenseCode;
    const concLicenseId = JSON.parse(createRes.body).data.licenseId;

    // Simulate concurrent activation requests from two devices
    const [res1, res2] = await Promise.all([
      app.inject({
        method: 'POST',
        url: '/v1/license/activate',
        payload: {
          licenseCode: concCode,
          ownerEmail: 'concurrent.owner@example.com',
          deviceBinding: 'CONCURRENT_DEVICE_X'
        }
      }),
      app.inject({
        method: 'POST',
        url: '/v1/license/activate',
        payload: {
          licenseCode: concCode,
          ownerEmail: 'concurrent.owner@example.com',
          deviceBinding: 'CONCURRENT_DEVICE_Y'
        }
      })
    ]);

    const results = [JSON.parse(res1.body), JSON.parse(res2.body)];
    const successCount = results.filter((r) => r.success === true).length;
    const failureCount = results.filter((r) => r.success === false).length;

    // Exactly one must succeed and one must fail
    expect(successCount).toBe(1);
    expect(failureCount).toBe(1);

    // Check DB active devices count for this license
    const activeDevices = db
      .prepare('SELECT * FROM license_devices WHERE license_id = ? AND status = ?')
      .all(concLicenseId, 'ACTIVE');

    expect(activeDevices.length).toBe(1);
  });

  // TEST 13: License code invalid -> REJECT
  it('TEST 13: License code invalid -> REJECT', async () => {
    const res = await app.inject({
      method: 'POST',
      url: '/v1/license/activate',
      payload: {
        licenseCode: 'BW-XXXX-INVALID-CODE',
        ownerEmail: 'anyone@example.com',
        deviceBinding: deviceA
      }
    });

    expect(res.statusCode).toBe(400);
    const body = JSON.parse(res.body);
    expect(body.success).toBe(false);
    expect(body.error.code).toBe('LICENSE_NOT_FOUND');
  });

  // TEST 14: Malformed request -> validation error
  it('TEST 14: Malformed request -> validation error', async () => {
    const res = await app.inject({
      method: 'POST',
      url: '/v1/license/activate',
      payload: {
        licenseCode: '', // Empty
        ownerEmail: 'not-valid'
        // Missing deviceBinding
      }
    });

    expect(res.statusCode).toBe(400);
    const body = JSON.parse(res.body);
    expect(body.success).toBe(false);
    expect(body.error.code).toBe('VALIDATION_ERROR');
  });

  // TEST 15: Unauthorized admin request -> REJECT
  it('TEST 15: Unauthorized admin request -> REJECT', async () => {
    const resNoAuth = await app.inject({
      method: 'GET',
      url: '/v1/admin/licenses'
    });
    expect(resNoAuth.statusCode).toBe(401);

    const resWrongAuth = await app.inject({
      method: 'GET',
      url: '/v1/admin/licenses',
      headers: { authorization: 'Bearer wrong_secret_key_9999' }
    });
    expect(resWrongAuth.statusCode).toBe(403);
  });

  // TEST 16: Admin audit log created
  it('TEST 16: Admin audit log created', async () => {
    const res = await app.inject({
      method: 'GET',
      url: '/v1/admin/audit-logs',
      headers: { authorization: `Bearer ${adminApiKey}` }
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.body);
    expect(body.success).toBe(true);
    expect(Array.isArray(body.data)).toBe(true);
    expect(body.data.length).toBeGreaterThan(0);

    const actions = body.data.map((l: any) => l.action);
    expect(actions).toContain('CREATE_LICENSE');
    expect(actions).toContain('ACTIVATE_LICENSE');
    expect(actions).toContain('REVOKE_LICENSE');
    expect(actions).toContain('REBIND_DEVICE');
  });

  // Extra TEST: Health check endpoint
  it('GET /health -> returns 200 OK', async () => {
    const res = await app.inject({
      method: 'GET',
      url: '/health'
    });
    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.body);
    expect(body.status).toBe('ok');
    expect(body.service).toBe('bukuwarung-license-server');
  });
});
