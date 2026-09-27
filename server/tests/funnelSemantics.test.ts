import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import { FastifyInstance } from 'fastify';
import Database from 'better-sqlite3';
import { buildApp } from '../src/app.js';
import { getDatabase, closeDatabase } from '../src/db/database.js';
import { config } from '../src/config/index.js';
import { AttributionService } from '../src/services/attributionService.js';

const INSTALLATION_A = '11111111-1111-4111-8111-111111111111';
const INSTALLATION_B = '22222222-2222-4222-8222-222222222222';

describe('FUNNEL-FIX — honest funnel semantics', () => {
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

  const track = async (eventType: string, installationId?: string, extra: Record<string, unknown> = {}) => {
    return app.inject({
      method: 'POST',
      url: '/v1/landing/track',
      payload: { eventType, ...(installationId ? { installationId } : {}), ...extra }
    });
  };

  const rows = (type: string) =>
    db.prepare('SELECT * FROM funnel_events WHERE event_type = ? ORDER BY id').all(type) as Array<{
      installation_id: string | null;
      lead_token: string;
    }>;

  describe('1. Identity is stored and is app-scoped', () => {
    it('1.1 event stores the installation id it was given', async () => {
      const res = await track('APP_FIRST_OPEN', INSTALLATION_A);
      expect(res.statusCode).toBe(200);

      const stored = rows('APP_FIRST_OPEN').at(-1);
      expect(stored?.installation_id).toBe(INSTALLATION_A);
    });

    it('1.2 same installation retains the same id across separate events', async () => {
      await track('LICENSE_GATE_VIEWED', INSTALLATION_A);
      await track('LICENSE_PURCHASE_CLICKED', INSTALLATION_A);

      const gate = rows('LICENSE_GATE_VIEWED').at(-1);
      const click = rows('LICENSE_PURCHASE_CLICKED').at(-1);
      expect(gate?.installation_id).toBe(INSTALLATION_A);
      expect(click?.installation_id).toBe(INSTALLATION_A);
    });

    it('1.3 a different installation gets a different id', async () => {
      await track('LICENSE_GATE_VIEWED', INSTALLATION_B);
      const stored = rows('LICENSE_GATE_VIEWED').at(-1);
      expect(stored?.installation_id).toBe(INSTALLATION_B);
      expect(stored?.installation_id).not.toBe(INSTALLATION_A);
    });

    it('1.4 an event without identity is still accepted and valid (backward compatibility)', async () => {
      const before = db.prepare('SELECT COUNT(*) as c FROM funnel_events').get() as { c: number };
      const res = await track('PAGE_VIEW');
      expect(res.statusCode).toBe(200);

      const after = db.prepare('SELECT COUNT(*) as c FROM funnel_events').get() as { c: number };
      expect(after.c).toBe(before.c + 1);

      const stored = rows('PAGE_VIEW').at(-1);
      expect(stored?.installation_id).toBeNull();
    });

    it('1.5 a malformed installation id is rejected by validation, not silently trusted', async () => {
      const res = await track('PAGE_VIEW', 'not-a-uuid');
      expect(res.statusCode).toBe(400);
    });

    it('1.6 raw hardware identifiers are not accepted as identity', async () => {
      // The endpoint must not grow an IMEI/Android-ID style field.
      const res = await app.inject({
        method: 'POST',
        url: '/v1/landing/track',
        payload: { eventType: 'PAGE_VIEW', imei: '356938035643809' }
      });
      expect(res.statusCode).toBe(400);
    });
  });

  describe('2. Click events are NOT globally deduplicated', () => {
    it('2.1 two purchase taps produce two click rows from one installation', async () => {
      const before = rows('LICENSE_PURCHASE_CLICKED').length;
      await track('LICENSE_PURCHASE_CLICKED', INSTALLATION_A);
      await track('LICENSE_PURCHASE_CLICKED', INSTALLATION_A);

      const after = rows('LICENSE_PURCHASE_CLICKED');
      expect(after.length).toBe(before + 2);
    });

    it('2.2 two WhatsApp taps produce two click rows from one installation', async () => {
      const before = rows('LICENSE_WHATSAPP_CLICKED').length;
      await track('LICENSE_WHATSAPP_CLICKED', INSTALLATION_A);
      await track('LICENSE_WHATSAPP_CLICKED', INSTALLATION_A);

      const after = rows('LICENSE_WHATSAPP_CLICKED');
      expect(after.length).toBe(before + 2);
    });

    it('2.3 no unique constraint blocks a repeated click event', async () => {
      // Would throw if a UNIQUE(event_type, installation_id) constraint existed.
      expect(() => {
        const stmt = db.prepare(
          'INSERT INTO funnel_events (lead_token, event_type, event_data, created_at, installation_id) VALUES (?,?,?,?,?)'
        );
        stmt.run('LW-TESTAA', 'LICENSE_PURCHASE_CLICKED', '{}', Date.now(), INSTALLATION_A);
        stmt.run('LW-TESTAB', 'LICENSE_PURCHASE_CLICKED', '{}', Date.now(), INSTALLATION_A);
      }).not.toThrow();
    });
  });

  describe('3. Lifecycle events count INSTALLATIONS, not rows', () => {
    it('3.1 22 gate views from one installation report as 1 installation', async () => {
      const gateInstall = '33333333-3333-4333-8333-333333333333';
      for (let i = 0; i < 22; i += 1) {
        await track('LICENSE_GATE_VIEWED', gateInstall);
      }

      // Raw rows for that installation: 22 emissions.
      const rawForInstall = db
        .prepare('SELECT COUNT(*) as c FROM funnel_events WHERE event_type = ? AND installation_id = ?')
        .get('LICENSE_GATE_VIEWED', gateInstall) as { c: number };
      expect(rawForInstall.c).toBe(22);

      // Distinct installations for that installation: exactly 1.
      const distinctForInstall = db
        .prepare(
          'SELECT COUNT(DISTINCT COALESCE(NULLIF(installation_id, \'\'), lead_token)) as c FROM funnel_events WHERE event_type = ? AND installation_id = ?'
        )
        .get('LICENSE_GATE_VIEWED', gateInstall) as { c: number };
      expect(distinctForInstall.c).toBe(1);

      // The service reports the same collapse through its unique-installation metric.
      const analytics = new AttributionService(db).getFunnelAnalytics();
      const stage = analytics.funnel.find((s) => s.name === 'LICENSE_GATE_VIEWED');
      const totalDistinctInstallations = db
        .prepare(
          `SELECT COUNT(DISTINCT COALESCE(NULLIF(installation_id, ''), lead_token)) as c
           FROM funnel_events WHERE event_type = 'LICENSE_GATE_VIEWED'`
        )
        .get() as { c: number };

      expect(stage?.metric).toBe('unique_installations');
      expect(stage?.count).toBe(totalDistinctInstallations.c);
      // 22 rows from one device must not add 22 to the installation total.
      expect(stage?.count).toBeLessThan(rawForInstall.c);
    });

    it('3.2 click stage stays a raw event count', async () => {
      const analytics = new AttributionService(db).getFunnelAnalytics();
      const stage = analytics.funnel.find((s) => s.name === 'LICENSE_PURCHASE_CLICKED');
      expect(stage?.metric).toBe('event_count');
      expect(stage?.unit).toBe('klik');
    });
  });

  describe('4. Commercial funnel reflects business truth, not attribution', () => {
    it('4.1 an order with NO lead_token is still counted as ORDER_CREATED', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/v1/admin/orders',
        headers: { authorization: `Bearer ${adminApiKey}` },
        payload: {
          customerName: 'Unattributed Buyer',
          customerContact: '081200000001',
          ownerEmail: 'unattributed@example.com'
        }
      });
      expect(res.statusCode).toBe(201);
      const created = JSON.parse(res.body).data.id;

      const row = db.prepare('SELECT lead_token FROM orders WHERE id = ?').get(created) as {
        lead_token: string | null;
      };
      expect(row.lead_token).toBeNull();

      const analytics = new AttributionService(db).getFunnelAnalytics();
      const stage = analytics.funnel.find((s) => s.name === 'ORDER_CREATED');
      expect(stage?.count).toBeGreaterThan(0);
      expect(stage?.metric).toBe('orders');
    });

    it('4.2 an order WITH a lead_token is also counted as ORDER_CREATED', () => {
      const now = Date.now();
      db.prepare(
        `INSERT INTO orders (order_number, customer_name, customer_contact, owner_email, amount,
                             status, payment_status, lead_token, created_at, updated_at)
         VALUES ('TEST-TOKEN-ORDER','Token Buyer','081200000002','token@example.com',50000,
                 'PENDING_PAYMENT','UNPAID','LW-TOKN01',?,?)`
      ).run(now, now);

      const analytics = new AttributionService(db).getFunnelAnalytics();
      const stage = analytics.funnel.find((s) => s.name === 'ORDER_CREATED');
      // The unattributed order from 4.1 plus this attributed one.
      expect(stage?.count).toBe(2);
    });

    it('4.3 payment/license/delivery are counted from business records without lead_token', async () => {
      // Attach a license + active device to the order that has NO lead_token at all.
      const order = db
        .prepare(`SELECT id FROM orders WHERE lead_token IS NULL ORDER BY id LIMIT 1`)
        .get() as { id: number };

      const lic = db
        .prepare(
          `INSERT INTO licenses (license_uuid, license_code_hash, owner_email_canonical, owner_email_hash, status, created_at, updated_at)
           VALUES ('uuid-funnel-fix','hash-funnel-fix','paid@example.com','ehash-funnel-fix','ACTIVE',?,?)`
        )
        .run(Date.now(), Date.now());

      db.prepare(
        `INSERT INTO license_devices (license_id, device_binding, status, first_activated_at, last_validated_at, created_at, updated_at)
         VALUES (?, 'device-funnel-fix', 'ACTIVE', ?, ?, ?, ?)`
      ).run(lic.lastInsertRowid, Date.now(), Date.now(), Date.now(), Date.now());

      db.prepare(
        `UPDATE orders SET payment_status='PAID', status='DELIVERED', license_id=?, delivered_at=? WHERE id=?`
      ).run(lic.lastInsertRowid, Date.now(), order.id);

      const analytics = new AttributionService(db).getFunnelAnalytics();
      const paid = analytics.funnel.find((s) => s.name === 'PAYMENT_CONFIRMED');
      const licCreated = analytics.funnel.find((s) => s.name === 'LICENSE_CREATED');
      const delivered = analytics.funnel.find((s) => s.name === 'DELIVERY_READY');
      const activated = analytics.funnel.find((s) => s.name === 'LICENSE_ACTIVATED');

      // All four must be non-zero even though the order has lead_token = NULL.
      expect(paid?.count).toBeGreaterThan(0);
      expect(licCreated?.count).toBeGreaterThan(0);
      expect(delivered?.count).toBeGreaterThan(0);
      expect(activated?.count).toBeGreaterThan(0);
      expect(analytics.northStar.activatedPaidCustomers).toBeGreaterThan(0);

      expect(paid?.metric).toBe('payments');
      expect(licCreated?.metric).toBe('licenses');
      expect(delivered?.metric).toBe('deliveries');
      expect(activated?.metric).toBe('activations');
    });
  });

  describe('5. Conversion rates never mislead', () => {
    it('5.1 a valid cohort conversion still computes correctly (15/22 = 68.2%)', async () => {
      const analytics = new AttributionService(db).getFunnelAnalytics();
      const linked = analytics.conversions.filter((c) => c.cohortLinked);
      expect(linked.length).toBeGreaterThan(0);
      for (const c of linked) {
        // A genuine conversion is always a finite, non-null percentage.
        expect(Number.isFinite(c.rate)).toBe(true);
        expect(c.rate).toBe(c.eventRatio);
      }

      // Exact arithmetic the API relies on: 15/22 must render as 68.18 -> 68.2.
      const safeRate = (num: number, den: number): number => {
        if (den === 0) return 0;
        return Math.round((num / den) * 10000) / 100;
      };
      expect(safeRate(15, 22)).toBe(68.18);
      expect(safeRate(15, 22).toFixed(1)).toBe('68.2');
    });

    it('5.2 an incomparable pair is flagged, and must not be presented as a conversion', () => {
      const analytics = new AttributionService(db).getFunnelAnalytics();
      const apkToOpen = analytics.conversions.find(
        (c) => c.from === 'APK_DOWNLOADED' && c.to === 'APP_FIRST_OPEN'
      );
      expect(apkToOpen).toBeDefined();
      expect(apkToOpen?.cohortLinked).toBe(false);
      // rate stays finite for compatibility, but the flag is what consumers must honour.
      expect(Number.isFinite(apkToOpen?.rate ?? Number.NaN)).toBe(true);
      expect(apkToOpen?.eventRatio).toBe(apkToOpen?.rate);
    });

    it('5.3 installations vs clicks is reported as a ratio, not a conversion', () => {
      const analytics = new AttributionService(db).getFunnelAnalytics();
      const pair = analytics.conversions.find(
        (c) => c.from === 'LICENSE_GATE_VIEWED' && c.to === 'LICENSE_PURCHASE_CLICKED'
      );
      expect(pair?.cohortLinked).toBe(false);
    });

    it('5.4 no conversion rate is silently clamped below its true value', () => {
      const analytics = new AttributionService(db).getFunnelAnalytics();
      for (const c of analytics.conversions) {
        // rate must always equal the true ratio: no 100% cap, no rounding that hides >100%.
        expect(c.rate).toBe(c.eventRatio);
        if (c.denominator > 0) {
          const expected = Math.round((c.numerator / c.denominator) * 10000) / 100;
          expect(c.rate).toBe(expected);
        }
      }
    });

    it('5.5 app-to-app stages are cohort linked; cross-surface stages are not', () => {
      const analytics = new AttributionService(db).getFunnelAnalytics();
      const appToApp = analytics.conversions.find(
        (c) => c.from === 'APP_FIRST_OPEN' && c.to === 'LICENSE_GATE_VIEWED'
      );
      expect(appToApp?.cohortLinked).toBe(true);

      const crossSurface = analytics.conversions.find(
        (c) => c.from === 'APK_DOWNLOADED' && c.to === 'APP_FIRST_OPEN'
      );
      expect(crossSurface?.cohortLinked).toBe(false);

      // Every commercial chain step shares the orders population and stays linked.
      for (const step of [
        ['ORDER_CREATED', 'PAYMENT_CONFIRMED'],
        ['PAYMENT_CONFIRMED', 'LICENSE_CREATED'],
        ['LICENSE_CREATED', 'LICENSE_ACTIVATED']
      ]) {
        const conv = analytics.conversions.find((c) => c.from === step[0] && c.to === step[1]);
        expect(conv?.cohortLinked).toBe(true);
      }
    });
  });

  describe('6. Migration is additive and non-destructive', () => {
    it('6.1 installation_id column exists and is nullable', () => {
      const cols = db.pragma('table_info(funnel_events)') as Array<{ name: string; notnull: number }>;
      const col = cols.find((c) => c.name === 'installation_id');
      expect(col).toBeDefined();
      expect(col?.notnull).toBe(0);
    });

    it('6.2 pre-existing rows are preserved, not rewritten', () => {
      const total = db.prepare('SELECT COUNT(*) as c FROM funnel_events').get() as { c: number };
      const nullIdentity = db
        .prepare('SELECT COUNT(*) as c FROM funnel_events WHERE installation_id IS NULL')
        .get() as { c: number };
      expect(total.c).toBeGreaterThan(0);
      // Rows recorded before this change keep a NULL identity rather than being backfilled.
      expect(nullIdentity.c).toBeGreaterThan(0);
    });

    it('6.3 historical rows still count via attribution-token fallback', async () => {
      db.prepare(
        `INSERT INTO funnel_events (lead_token, event_type, event_data, created_at, installation_id)
         VALUES ('LW-HISTOR1','LICENSE_GATE_VIEWED','{}',?,NULL)`
      ).run(Date.now());

      const analytics = new AttributionService(db).getFunnelAnalytics();
      const stage = analytics.funnel.find((s) => s.name === 'LICENSE_GATE_VIEWED');
      // The historical row is not dropped just because it predates installation identity.
      expect(stage?.count).toBeGreaterThan(0);
    });
  });
});
