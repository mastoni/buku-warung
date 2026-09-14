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

describe('Buku Warung License Admin Dashboard MVP (C.3)', () => {
  let adminApp: FastifyInstance;
  let licenseServerApp: FastifyInstance;
  let licenseServerPort: number;

  let sessionCookie: string = '';
  let testLicenseId: number = 0;
  let testLicenseCode: string = '';
  const ownerEmail = 'juragan.sembako@example.com';
  const deviceOld = 'DEVICE_BINDING_SAMSUNG_OLD_888';
  const deviceNew = 'DEVICE_BINDING_SAMSUNG_NEW_999';

  beforeAll(async () => {
    // 1. Boot in-memory C.2 License Server on dynamic port
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
  });

  afterAll(async () => {
    await adminApp.close();
    await licenseServerApp.close();
    closeLicenseDb();
  });

  // TEST C3-01: Admin login success
  it('TEST C3-01: Admin login success', async () => {
    const res = await adminApp.inject({
      method: 'POST',
      url: '/api/auth/login',
      payload: {
        username: adminConfig.adminUsername,
        password: adminConfig.adminPassword
      }
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.body);
    expect(body.success).toBe(true);
    expect(body.data.username).toBe(adminConfig.adminUsername);

    // Capture session cookie
    const setCookie = res.headers['set-cookie'] as string | string[];
    const cookieHeader = Array.isArray(setCookie) ? setCookie[0] : setCookie;
    expect(cookieHeader).toContain('bw_admin_session=');
    sessionCookie = cookieHeader.split(';')[0];
  });

  // TEST C3-02: Invalid admin authentication rejected
  it('TEST C3-02: Invalid admin authentication rejected', async () => {
    const res = await adminApp.inject({
      method: 'POST',
      url: '/api/auth/login',
      payload: {
        username: 'wrong_admin',
        password: 'wrong_password_123'
      }
    });

    expect(res.statusCode).toBe(401);
    const body = JSON.parse(res.body);
    expect(body.success).toBe(false);
    expect(body.error.code).toBe('INVALID_CREDENTIALS');
  });

  // TEST C3-03: Dashboard loads
  it('TEST C3-03: Dashboard loads', async () => {
    const res = await adminApp.inject({
      method: 'GET',
      url: '/api/dashboard/summary',
      headers: {
        cookie: sessionCookie
      }
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.body);
    expect(body.success).toBe(true);
    expect(body.data).toHaveProperty('totalLicenses');
    expect(body.data).toHaveProperty('activeLicenses');
    expect(body.data).toHaveProperty('pendingLicenses');
    expect(body.data).toHaveProperty('revokedLicenses');
  });

  // TEST C3-04: License list loads
  it('TEST C3-04: License list loads', async () => {
    const res = await adminApp.inject({
      method: 'GET',
      url: '/api/licenses',
      headers: {
        cookie: sessionCookie
      }
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.body);
    expect(body.success).toBe(true);
    expect(Array.isArray(body.data)).toBe(true);
  });

  // TEST C3-05: Create license
  it('TEST C3-05: Create license', async () => {
    const res = await adminApp.inject({
      method: 'POST',
      url: '/api/licenses',
      headers: {
        cookie: sessionCookie
      },
      payload: {
        ownerEmail: ownerEmail,
        customerName: 'Juragan Sembako',
        customerContact: '081234567899',
        product: 'BUKU_WARUNG',
        price: 50000
      }
    });

    expect(res.statusCode).toBe(201);
    const body = JSON.parse(res.body);
    expect(body.success).toBe(true);
    expect(body.data.licenseCode).toMatch(/^BW-[2-9A-HJ-NP-Z]{4}-[2-9A-HJ-NP-Z]{4}-[2-9A-HJ-NP-Z]{4}$/);
    expect(body.data.status).toBe('PENDING');

    testLicenseId = body.data.licenseId;
    testLicenseCode = body.data.licenseCode;
  });

  // TEST C3-06: Created license appears
  it('TEST C3-06: Created license appears in list', async () => {
    const res = await adminApp.inject({
      method: 'GET',
      url: '/api/licenses',
      headers: {
        cookie: sessionCookie
      }
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.body);
    const found = body.data.find((l: any) => l.id === testLicenseId);
    expect(found).toBeDefined();
    expect(found.owner_email_canonical).toBe(ownerEmail);
  });

  // TEST C3-07: License detail loads
  it('TEST C3-07: License detail loads', async () => {
    const res = await adminApp.inject({
      method: 'GET',
      url: `/api/licenses/${testLicenseId}`,
      headers: {
        cookie: sessionCookie
      }
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.body);
    expect(body.success).toBe(true);
    expect(body.data.license.id).toBe(testLicenseId);
    expect(body.data.license.owner_email_canonical).toBe(ownerEmail);
  });

  // Simulate client activation of test license on deviceOld
  it('Simulate device activation on C.2 Server', async () => {
    const actRes = await licenseServerApp.inject({
      method: 'POST',
      url: '/v1/license/activate',
      payload: {
        licenseCode: testLicenseCode,
        ownerEmail: ownerEmail,
        deviceBinding: deviceOld
      }
    });
    expect(actRes.statusCode).toBe(200);
  });

  // TEST C3-08: Revoke license confirmation & execution
  it('TEST C3-08: Revoke license execution', async () => {
    // Create another license specifically to test revocation
    const createRes = await adminApp.inject({
      method: 'POST',
      url: '/api/licenses',
      headers: { cookie: sessionCookie },
      payload: { ownerEmail: 'revoke.target@example.com' }
    });
    const revLicId = JSON.parse(createRes.body).data.licenseId;

    const revokeRes = await adminApp.inject({
      method: 'POST',
      url: `/api/licenses/${revLicId}/revoke`,
      headers: { cookie: sessionCookie },
      payload: { reason: 'Test license revocation from admin dashboard' }
    });

    expect(revokeRes.statusCode).toBe(200);
    const body = JSON.parse(revokeRes.body);
    expect(body.success).toBe(true);
  });

  // TEST C3-09: Revoke license reflected
  it('TEST C3-09: Revoke license reflected in detail', async () => {
    const listRes = await adminApp.inject({
      method: 'GET',
      url: '/api/licenses',
      headers: { cookie: sessionCookie }
    });
    const body = JSON.parse(listRes.body);
    const revokedLic = body.data.find((l: any) => l.owner_email_canonical === 'revoke.target@example.com');
    expect(revokedLic.status).toBe('REVOKED');
  });

  // TEST C3-10: Device detail loads
  it('TEST C3-10: Device detail loads', async () => {
    const res = await adminApp.inject({
      method: 'GET',
      url: `/api/licenses/${testLicenseId}`,
      headers: { cookie: sessionCookie }
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.body);
    expect(body.data.devices.length).toBe(1);
    expect(body.data.devices[0].device_binding).toBe(deviceOld);
    expect(body.data.devices[0].status).toBe('ACTIVE');
  });

  // TEST C3-11: Device revoke
  it('TEST C3-11: Device revoke', async () => {
    // Get device ID
    const detailRes = await adminApp.inject({
      method: 'GET',
      url: `/api/licenses/${testLicenseId}`,
      headers: { cookie: sessionCookie }
    });
    const deviceId = JSON.parse(detailRes.body).data.devices[0].id;

    const revokeDevRes = await adminApp.inject({
      method: 'POST',
      url: `/api/devices/${deviceId}/revoke`,
      headers: { cookie: sessionCookie },
      payload: { reason: 'Device binding revoked by admin test' }
    });

    expect(revokeDevRes.statusCode).toBe(200);
  });

  // TEST C3-12: Recovery pending displayed
  it('TEST C3-12: Recovery pending displayed', async () => {
    // Reactivate deviceOld first
    await licenseServerApp.inject({
      method: 'POST',
      url: '/v1/license/activate',
      payload: {
        licenseCode: testLicenseCode,
        ownerEmail: ownerEmail,
        deviceBinding: deviceOld
      }
    });

    // Client requests recovery for deviceNew
    const recRes = await licenseServerApp.inject({
      method: 'POST',
      url: '/v1/license/recover',
      payload: {
        licenseCode: testLicenseCode,
        ownerEmail: ownerEmail,
        newDeviceBinding: deviceNew,
        reason: 'HP rusak, ganti baru'
      }
    });
    expect(recRes.statusCode).toBe(200);

    // Query license detail via Admin BFF
    const detailRes = await adminApp.inject({
      method: 'GET',
      url: `/api/licenses/${testLicenseId}`,
      headers: { cookie: sessionCookie }
    });

    const body = JSON.parse(detailRes.body);
    const pendingRecovery = body.data.recoveryRequests.find((r: any) => r.status === 'PENDING');
    expect(pendingRecovery).toBeDefined();
    expect(pendingRecovery.new_device_binding).toBe(deviceNew);
  });

  // TEST C3-13: Approve/rebind
  it('TEST C3-13: Approve/rebind', async () => {
    const rebindRes = await adminApp.inject({
      method: 'POST',
      url: '/api/license/rebind',
      headers: { cookie: sessionCookie },
      payload: {
        licenseId: testLicenseId,
        newDeviceBinding: deviceNew,
        reason: 'Approved customer device replacement'
      }
    });

    expect(rebindRes.statusCode).toBe(200);
    const body = JSON.parse(rebindRes.body);
    expect(body.success).toBe(true);
  });

  // TEST C3-14: Old device becomes revoked
  it('TEST C3-14: Old device becomes revoked', async () => {
    const detailRes = await adminApp.inject({
      method: 'GET',
      url: `/api/licenses/${testLicenseId}`,
      headers: { cookie: sessionCookie }
    });
    const body = JSON.parse(detailRes.body);
    const oldDev = body.data.devices.find((d: any) => d.device_binding === deviceOld);
    expect(oldDev.status).toBe('REVOKED');
  });

  // TEST C3-15: New device becomes active
  it('TEST C3-15: New device becomes active', async () => {
    const detailRes = await adminApp.inject({
      method: 'GET',
      url: `/api/licenses/${testLicenseId}`,
      headers: { cookie: sessionCookie }
    });
    const body = JSON.parse(detailRes.body);
    const newDev = body.data.devices.find((d: any) => d.device_binding === deviceNew);
    expect(newDev.status).toBe('ACTIVE');
  });

  // TEST C3-16: Audit log appears
  it('TEST C3-16: Audit log appears in dashboard', async () => {
    const res = await adminApp.inject({
      method: 'GET',
      url: '/api/audit-logs',
      headers: { cookie: sessionCookie }
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.body);
    expect(body.success).toBe(true);
    expect(Array.isArray(body.data)).toBe(true);
    expect(body.data.length).toBeGreaterThan(0);
  });

  // TEST C3-17: Unauthorized admin API cannot be accessed
  it('TEST C3-17: Unauthorized admin API cannot be accessed without session cookie', async () => {
    const resNoAuth = await adminApp.inject({
      method: 'GET',
      url: '/api/licenses'
    });
    expect(resNoAuth.statusCode).toBe(401);

    const resFakeCookie = await adminApp.inject({
      method: 'GET',
      url: '/api/licenses',
      headers: { cookie: 'bw_admin_session=invalid_tampered_token_xyz' }
    });
    expect(resFakeCookie.statusCode).toBe(401);
  });

  // TEST C3-18: ADMIN_API_KEY does not appear in frontend bundle
  it('TEST C3-18: ADMIN_API_KEY does not appear in frontend static files', () => {
    const publicDir = path.resolve(__dirname, '../../admin/public');
    const files = fs.readdirSync(publicDir);

    for (const file of files) {
      const content = fs.readFileSync(path.join(publicDir, file), 'utf8');
      expect(content).not.toContain(adminConfig.adminApiKey);
      expect(content).not.toContain('ADMIN_API_KEY');
      expect(content).not.toContain(adminConfig.adminPassword);
    }
  });

  // TEST C3-19: License code is not persisted to localStorage
  it('TEST C3-19: License code is not persisted to localStorage in frontend code', () => {
    const appJsPath = path.resolve(__dirname, '../../admin/public/app.js');
    const appJsContent = fs.readFileSync(appJsPath, 'utf8');
    expect(appJsContent).not.toContain('localStorage');
    expect(appJsContent).not.toContain('sessionStorage');
  });

  // TEST C3-20: Logout
  it('TEST C3-20: Logout clears session', async () => {
    const res = await adminApp.inject({
      method: 'POST',
      url: '/api/auth/logout',
      headers: { cookie: sessionCookie }
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.body);
    expect(body.success).toBe(true);

    // Old session should now be rejected
    const testOldRes = await adminApp.inject({
      method: 'GET',
      url: '/api/licenses',
      headers: { cookie: sessionCookie }
    });
    expect(testOldRes.statusCode).toBe(401);
  });
});
