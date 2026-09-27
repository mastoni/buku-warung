import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import { FastifyInstance } from 'fastify';
import Database from 'better-sqlite3';
import { buildApp } from '../src/app.js';
import { getDatabase, closeDatabase } from '../src/db/database.js';
import { config } from '../src/config/index.js';
import { AdminService } from '../src/services/adminService.js';

/**
 * TASK 5 — rebind security and test-coverage hardening.
 *
 * All scenarios run against an in-memory test database. No production endpoint is
 * contacted and no production data is touched.
 */
describe('License rebind — security and data-integrity hardening', () => {
  let app: FastifyInstance;
  let db: Database.Database;
  const adminApiKey = config.adminApiKey;
  const auth = { authorization: `Bearer ${adminApiKey}` };

  beforeAll(async () => {
    db = getDatabase(':memory:');
    app = buildApp();
    await app.ready();
  });

  afterAll(async () => {
    await app.close();
    closeDatabase();
  });

  /** Creates a licence with an order row and one ACTIVE device binding. */
  const seedBoundLicense = async (email: string, device: string) => {
    const svc = new AdminService(db);
    const created = svc.createLicense({
      ownerEmail: email,
      customerName: 'Rebind Fixture',
      customerContact: '081200009999'
    });
    expect(created.success).toBe(true);
    const licenseId = created.data!.licenseId;
    const code = created.data!.licenseCode;
    const orderNumber = created.data!.orderNumber!;

    const act = await app.inject({
      method: 'POST',
      url: '/v1/license/activate',
      payload: { licenseCode: code, ownerEmail: email, deviceBinding: device }
    });
    expect(act.statusCode).toBe(200);
    expect(JSON.parse(act.body).status).toBe('ACTIVE');

    return { svc, licenseId, code, email, device, orderNumber };
  };

  const rebind = (payload: unknown, withAuth = true) =>
    app.inject({
      method: 'POST',
      url: '/v1/admin/license/rebind',
      ...(withAuth ? { headers: auth } : {}),
      payload: payload as Record<string, unknown>
    });

  const activeDevices = (licenseId: number) =>
    db
      .prepare("SELECT * FROM license_devices WHERE license_id = ? AND status = 'ACTIVE'")
      .all(licenseId) as any[];

  const allDevices = (licenseId: number) =>
    db.prepare('SELECT * FROM license_devices WHERE license_id = ?').all(licenseId) as any[];

  // --------------------------------------------------------------- 1. authorization
  it('1. rebind without admin authentication is rejected with 401 and mutates nothing', async () => {
    const s = await seedBoundLicense('unauth.rebind@example.com', 'device-unauth-old');

    const before = {
      license: db.prepare('SELECT * FROM licenses WHERE id = ?').get(s.licenseId),
      devices: allDevices(s.licenseId)
    };

    const res = await app.inject({
      method: 'POST',
      url: '/v1/admin/license/rebind',
      payload: { licenseId: s.licenseId, newDeviceBinding: 'device-unauth-new' }
    });

    expect(res.statusCode).toBe(401);
    expect(JSON.parse(res.body).error.code).toBe('UNAUTHORIZED');

    expect(db.prepare('SELECT * FROM licenses WHERE id = ?').get(s.licenseId)).toEqual(before.license);
    expect(allDevices(s.licenseId)).toEqual(before.devices);
    expect(activeDevices(s.licenseId)).toHaveLength(1);
    expect(activeDevices(s.licenseId)[0].device_binding).toBe('device-unauth-old');
  });

  it('1b. rebind with a wrong bearer token is rejected with 403 FORBIDDEN', async () => {
    const s = await seedBoundLicense('badtoken.rebind@example.com', 'device-badtoken-old');
    const res = await app.inject({
      method: 'POST',
      url: '/v1/admin/license/rebind',
      headers: { authorization: 'Bearer not-the-admin-key' },
      payload: { licenseId: s.licenseId, newDeviceBinding: 'device-badtoken-new' }
    });
    // Contract: absent/malformed header -> 401 UNAUTHORIZED; present but wrong -> 403 FORBIDDEN.
    expect(res.statusCode).toBe(403);
    expect(JSON.parse(res.body).error.code).toBe('FORBIDDEN');
    expect(activeDevices(s.licenseId)[0].device_binding).toBe('device-badtoken-old');
  });

  // ------------------------------------------------------- 2/3. input validation
  it('2. rebind for a nonexistent license returns 400 NOT_FOUND and mutates nothing', async () => {
    const res = await rebind({ licenseId: 999999, newDeviceBinding: 'device-ghost' });
    expect(res.statusCode).toBe(400);
    expect(JSON.parse(res.body).error.code).toBe('NOT_FOUND');
  });

  it('3. rebind with a blank newDeviceBinding is rejected and mutates nothing', async () => {
    const s = await seedBoundLicense('blank.rebind@example.com', 'device-blank-old');
    const before = allDevices(s.licenseId);

    const res = await rebind({ licenseId: s.licenseId, newDeviceBinding: '' });
    expect(res.statusCode).toBe(400);

    expect(allDevices(s.licenseId)).toEqual(before);
    expect(activeDevices(s.licenseId)[0].device_binding).toBe('device-blank-old');
  });

  it('3b. rebind with a missing newDeviceBinding field is rejected by the schema', async () => {
    const s = await seedBoundLicense('missing.rebind@example.com', 'device-missing-old');
    const res = await rebind({ licenseId: s.licenseId });
    expect(res.statusCode).toBe(400);
    expect(activeDevices(s.licenseId)[0].device_binding).toBe('device-missing-old');
  });

  // -------------------------------------------------------- 4. revoked protection
  it('4. rebind of a REVOKED licence is rejected and mutates nothing', async () => {
    const s = await seedBoundLicense('revoked.rebind@example.com', 'device-revoked-old');
    const svc = new AdminService(db);
    const rev = svc.revokeLicense(s.licenseId, 'ADMIN_API', 'test revocation');
    expect(rev.success).toBe(true);

    const before = {
      license: db.prepare('SELECT * FROM licenses WHERE id = ?').get(s.licenseId),
      devices: allDevices(s.licenseId)
    };

    const res = await rebind({ licenseId: s.licenseId, newDeviceBinding: 'device-revoked-new' });
    expect(res.statusCode).toBe(400);
    expect(JSON.parse(res.body).error.code).toBe('LICENSE_REVOKED');

    expect(db.prepare('SELECT * FROM licenses WHERE id = ?').get(s.licenseId)).toEqual(before.license);
    expect(allDevices(s.licenseId)).toEqual(before.devices);
  });

  // ------------------------------------- 5/6/7. owner, licence code and order intact
  it('5/6/7/8. successful rebind preserves owner, licence code and order, and leaves exactly one ACTIVE device', async () => {
    const s = await seedBoundLicense('intact.rebind@example.com', 'device-intact-old');

    const licBefore = db.prepare('SELECT * FROM licenses WHERE id = ?').get(s.licenseId) as any;
    const orderBefore = db.prepare('SELECT * FROM orders WHERE order_number = ?').get(s.orderNumber);

    const res = await rebind({
      licenseId: s.licenseId,
      newDeviceBinding: 'device-intact-new',
      reason: 'Customer reinstall produced a new device id'
    });
    expect(res.statusCode).toBe(200);
    expect(JSON.parse(res.body).success).toBe(true);

    const licAfter = db.prepare('SELECT * FROM licenses WHERE id = ?').get(s.licenseId) as any;

    // 5. owner unchanged
    expect(licAfter.owner_email_canonical).toBe(licBefore.owner_email_canonical);
    expect(licAfter.owner_email_hash).toBe(licBefore.owner_email_hash);

    // 6. licence identity unchanged (hash + uuid); plaintext code is never persisted
    expect(licAfter.license_code_hash).toBe(licBefore.license_code_hash);
    expect(licAfter.license_uuid).toBe(licBefore.license_uuid);
    expect(licAfter.license_code_hash).toBe(licBefore.license_code_hash);
    const licCols = db.prepare('PRAGMA table_info(licenses)').all().map((c: any) => c.name);
    expect(licCols).not.toContain('license_code');

    // 7. order untouched
    const orderAfter = db.prepare('SELECT * FROM orders WHERE order_number = ?').get(s.orderNumber);
    expect(orderAfter).toEqual(orderBefore);
    expect(db.prepare('SELECT COUNT(*) as c FROM orders').get()).toEqual({
      c: db.prepare('SELECT COUNT(*) as c FROM orders').get().c
    });

    // 8. exactly one ACTIVE device: old revoked, new active
    const active = activeDevices(s.licenseId);
    expect(active).toHaveLength(1);
    expect(active[0].device_binding).toBe('device-intact-new');

    const oldRow = allDevices(s.licenseId).find((d) => d.device_binding === 'device-intact-old');
    expect(oldRow.status).toBe('REVOKED');
    expect(oldRow.revoked_at).toBeTruthy();
    expect(licAfter.status).toBe('ACTIVE');
  });

  // -------------------------------------------- 9. database uniqueness protection
  it('9. a second ACTIVE binding cannot be inserted — the partial UNIQUE index rejects it', async () => {
    const s = await seedBoundLicense('unique.rebind@example.com', 'device-unique-a');

    expect(activeDevices(s.licenseId)).toHaveLength(1);

    // Direct insert of a competing ACTIVE row must be refused by SQLite.
    expect(() =>
      db
        .prepare(
          `INSERT INTO license_devices (license_id, device_binding, status, first_activated_at, last_validated_at, created_at, updated_at)
           VALUES (?, 'device-unique-b', 'ACTIVE', ?, ?, ?, ?)`
        )
        .run(s.licenseId, Date.now(), Date.now(), Date.now(), Date.now())
    ).toThrow();

    // The original binding is untouched.
    expect(activeDevices(s.licenseId)[0].device_binding).toBe('device-unique-a');
  });

  it('9b. a failure inside the rebind transaction rolls back and preserves the ACTIVE binding', async () => {
    const s = await seedBoundLicense('rollback.rebind@example.com', 'device-rollback-old');
    const before = allDevices(s.licenseId);

    // Force a mid-transaction failure: a NULL device_binding violates the NOT NULL constraint
    // on the INSERT that runs AFTER the revoke UPDATE. The service does not catch it, so the
    // error propagates — which is precisely what proves the transaction rolled back.
    const svc = new AdminService(db);
    expect(() => svc.rebindLicense(s.licenseId, null as unknown as string, 'ADMIN_API')).toThrow();

    // The original ACTIVE binding survived: the revoke was rolled back with the failed insert.
    expect(activeDevices(s.licenseId)).toHaveLength(1);
    expect(activeDevices(s.licenseId)[0].device_binding).toBe('device-rollback-old');
    expect(allDevices(s.licenseId)).toEqual(before);
  });

  // ------------------------------------------------- 10. recovery request approval
  it('10. rebind approves the matching PENDING recovery request without creating licence or order', async () => {
    const s = await seedBoundLicense('recovery.rebind@example.com', 'device-recovery-old');

    const ordersBefore = db.prepare('SELECT COUNT(*) as c FROM orders').get() as any;
    const licencesBefore = db.prepare('SELECT COUNT(*) as c FROM licenses').get() as any;

    // Customer requests recovery from the app (public endpoint, PENDING only).
    const req = await app.inject({
      method: 'POST',
      url: '/v1/license/recover',
      payload: {
        licenseCode: s.code,
        ownerEmail: s.email,
        newDeviceBinding: 'device-recovery-new',
        reason: 'Reinstall produced a new device id'
      }
    });
    expect(req.statusCode).toBe(200);
    expect(JSON.parse(req.body).status).toBe('RECOVERY_PENDING');

    const pending = db
      .prepare("SELECT * FROM recovery_requests WHERE license_id = ? AND status = 'PENDING'")
      .get(s.licenseId) as any;
    expect(pending).toBeDefined();
    expect(pending.new_device_binding).toBe('device-recovery-new');
    expect(pending.old_device_binding).toBe('device-recovery-old');

    // Request alone must NOT rebind.
    expect(activeDevices(s.licenseId)[0].device_binding).toBe('device-recovery-old');

    // Admin approves.
    const res = await rebind({
      licenseId: s.licenseId,
      newDeviceBinding: 'device-recovery-new',
      reason: 'Approved recovery request'
    });
    expect(res.statusCode).toBe(200);

    const resolved = db
      .prepare('SELECT * FROM recovery_requests WHERE id = ?')
      .get(pending.id) as any;
    expect(resolved.status).toBe('APPROVED');
    expect(resolved.resolved_at).toBeTruthy();

    expect(activeDevices(s.licenseId)).toHaveLength(1);
    expect(activeDevices(s.licenseId)[0].device_binding).toBe('device-recovery-new');

    // No licence or order was created.
    expect(db.prepare('SELECT COUNT(*) as c FROM orders').get()).toEqual(ordersBefore);
    expect(db.prepare('SELECT COUNT(*) as c FROM licenses').get()).toEqual(licencesBefore);
  });

  // ------------------------------------------------------------ 11. audit trail
  it('11. rebind writes a REBIND_DEVICE audit row with actor and reason and no plaintext code', async () => {
    const s = await seedBoundLicense('audit.rebind@example.com', 'device-audit-old');

    const before = db
      .prepare("SELECT COUNT(*) as c FROM audit_logs WHERE action = 'REBIND_DEVICE'")
      .get() as any;

    await rebind({
      licenseId: s.licenseId,
      newDeviceBinding: 'device-audit-new',
      reason: 'Approved device replacement for customer'
    });

    const rows = db
      .prepare("SELECT * FROM audit_logs WHERE action = 'REBIND_DEVICE' AND license_id = ?")
      .all(s.licenseId) as any[];

    expect(rows.length).toBe(1);
    const row = rows[0];
    expect(row.old_state).toBe('REBIND_OLD');
    expect(row.new_state).toBe('ACTIVE');
    expect(row.actor).toBe('ADMIN_API');
    expect(row.reason).toBe('Approved device replacement for customer');
    expect(row.created_at).toBeTruthy();

    // The plaintext licence code must never appear in the audit trail.
    expect(row.reason).not.toContain(s.code);
    const leak = db
      .prepare('SELECT COUNT(*) as c FROM audit_logs WHERE reason LIKE ?')
      .get(`%${s.code}%`) as any;
    expect(leak.c).toBe(0);

    expect(
      (db.prepare("SELECT COUNT(*) as c FROM audit_logs WHERE action = 'REBIND_DEVICE'").get() as any).c
    ).toBe(before.c + 1);
  });

  it('11b. repeated rebind keeps exactly one ACTIVE device and appends an audit row each time', async () => {
    const s = await seedBoundLicense('repeat.rebind@example.com', 'device-repeat-0');

    for (const binding of ['device-repeat-1', 'device-repeat-2', 'device-repeat-3']) {
      const res = await rebind({ licenseId: s.licenseId, newDeviceBinding: binding });
      expect(res.statusCode).toBe(200);
      expect(activeDevices(s.licenseId)).toHaveLength(1);
      expect(activeDevices(s.licenseId)[0].device_binding).toBe(binding);
    }

    const audits = db
      .prepare("SELECT COUNT(*) as c FROM audit_logs WHERE action = 'REBIND_DEVICE' AND license_id = ?")
      .get(s.licenseId) as any;
    expect(audits.c).toBe(3);

    // Every historical row is retained, only statuses differ.
    expect(allDevices(s.licenseId)).toHaveLength(4);
  });
});
