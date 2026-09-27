import Database from 'better-sqlite3';
import { getDatabase } from '../db/database.js';
import {
  FunnelEventRecord,
  LandingTrackRequest,
  LandingTrackResponse,
  FunnelAnalytics,
  AttributionEntry,
  FunnelMetricKind
} from '../types/index.js';
import {
  generateLeadToken,
  isValidLeadToken,
  validateUtm,
  validateFunnelEvent,
  hashIpAddress,
  sanitizeUserAgent
} from '../utils/attribution.js';
import { ALLOWED_UTM_PARAMS } from '../utils/attribution.js';

export class AttributionService {
  private db: Database.Database;

  constructor(customDb?: Database.Database) {
    this.db = customDb || getDatabase();
  }

  findOrdersByToken(token: string): Array<{ id: number; order_number: string; status: string; payment_status: string }> {
    return this.db
      .prepare('SELECT id, order_number, status, payment_status FROM orders WHERE lead_token = ? ORDER BY id DESC')
      .all(token) as Array<{ id: number; order_number: string; status: string; payment_status: string }>;
  }

  findOrderByToken(token: string): { id: number; order_number: string } | null {
    const row = this.db
      .prepare('SELECT id, order_number FROM orders WHERE lead_token = ? ORDER BY id DESC LIMIT 1')
      .get(token) as { id: number; order_number: string } | undefined;
    return row || null;
  }

  getTokenMetrics(leadToken: string): {
    orders: Array<{ id: number; order_number: string; status: string; payment_status: string; created_at: number }>;
    events: FunnelEventRecord[];
    totalEvents: number;
    orderCount: number;
  } {
    const orders = this.db
      .prepare('SELECT id, order_number, status, payment_status, created_at FROM orders WHERE lead_token = ? ORDER BY id DESC')
      .all(leadToken) as Array<{ id: number; order_number: string; status: string; payment_status: string; created_at: number }>;

    const events = this.getFunnelEvents(leadToken);

    return {
      orders,
      events,
      totalEvents: events.length,
      orderCount: orders.length
    };
  }

  findLicenseByOrder(orderId: number): { license_id: number | null } | null {
    const row = this.db
      .prepare('SELECT license_id FROM orders WHERE id = ?')
      .get(orderId) as { license_id: number | null } | undefined;
    return row || null;
  }

  processLandingTrack(
    payload: LandingTrackRequest,
    ipAddress?: string,
    userAgent?: string
  ): LandingTrackResponse {
    const { eventType, leadToken, installationId, ...utmParams } = payload;

    // installation_id is app-scoped analytics identity. It is stored as-is when it matches a
    // UUID and dropped otherwise. It is NOT an attribution token: lead_token remains the
    // web-campaign attribution artifact, and may still be minted per request for app events.
    const normalizedInstallationId =
      installationId && /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/.test(installationId)
        ? installationId
        : null;

    if (!validateFunnelEvent(eventType)) {
      return {
        success: false,
        data: {
          leadToken: '',
          action: 'created'
        }
      };
    }

    const now = Date.now();
    let token: string;
    let action: 'created' | 'matched' | 'updated';

    if (leadToken) {
      if (!isValidLeadToken(leadToken)) {
        return {
          success: false,
          data: {
            leadToken: '',
            action: 'created'
          }
        };
      }

      const existingEvents = this.db
        .prepare('SELECT COUNT(*) as count FROM funnel_events WHERE lead_token = ?')
        .get(leadToken) as { count: number };

      if (existingEvents.count === 0) {
        return {
          success: false,
          data: {
            leadToken: '',
            action: 'created'
          }
        };
      }

      token = leadToken;
      action = 'matched';
    } else {
      const maxAttempts = 10;
      for (let i = 0; i < maxAttempts; i++) {
        const newToken = generateLeadToken();
        const existing = this.db
          .prepare('SELECT 1 FROM funnel_events WHERE lead_token = ?')
          .get(newToken) as unknown;

        if (!existing) {
          token = newToken;
          break;
        }
      }

      if (!token!) {
        token = generateLeadToken();
      }
      action = 'created';
    }

    this.db
      .prepare(`
        INSERT INTO funnel_events (lead_token, event_type, event_data, ip_hash, user_agent, created_at, installation_id)
        VALUES (?, ?, ?, ?, ?, ?, ?)
      `)
      .run(
        token,
        eventType,
        JSON.stringify(
          ALLOWED_UTM_PARAMS.reduce(
            (acc, param) => {
              const value = utmParams[param];
              if (value && validateUtm(value)) {
                acc[param] = value;
              }
              return acc;
            },
            {} as Record<string, string>
          )
        ),
        ipAddress ? hashIpAddress(ipAddress) : null,
        sanitizeUserAgent(userAgent) || null,
        now,
        normalizedInstallationId
      );

    return {
      success: true,
      data: {
        leadToken: token,
        action
      }
    };
  }

  getFunnelEvents(leadToken?: string, eventType?: string): FunnelEventRecord[] {
    if (leadToken && eventType) {
      return this.db
        .prepare(
          'SELECT * FROM funnel_events WHERE lead_token = ? AND event_type = ? ORDER BY created_at DESC'
        )
        .all(leadToken, eventType) as FunnelEventRecord[];
    }
    if (leadToken) {
      return this.db
        .prepare('SELECT * FROM funnel_events WHERE lead_token = ? ORDER BY created_at DESC')
        .all(leadToken) as FunnelEventRecord[];
    }
    if (eventType) {
      return this.db
        .prepare(
          'SELECT * FROM funnel_events WHERE event_type = ? ORDER BY created_at DESC LIMIT 200'
        )
        .all(eventType) as FunnelEventRecord[];
    }
    return this.db
      .prepare('SELECT * FROM funnel_events ORDER BY created_at DESC LIMIT 200')
      .all() as FunnelEventRecord[];
  }

  getAttributionMetrics(): {
    totalLeads: number;
    totalEvents: number;
    eventsByType: Record<string, number>;
    ordersWithToken: number;
    ordersWithoutToken: number;
  } {
    const totalLeads = this.db
      .prepare('SELECT COUNT(DISTINCT lead_token) as count FROM funnel_events')
      .get() as { count: number };

    const totalEvents = this.db
      .prepare('SELECT COUNT(*) as count FROM funnel_events')
      .get() as { count: number };

    const typeRows = this.db
      .prepare(
        'SELECT event_type, COUNT(*) as count FROM funnel_events GROUP BY event_type'
      )
      .all() as Array<{ event_type: string; count: number }>;

    const eventsByType: Record<string, number> = {};
    for (const row of typeRows) {
      eventsByType[row.event_type] = row.count;
    }

    const ordersWithToken = this.db
      .prepare('SELECT COUNT(*) as count FROM orders WHERE lead_token IS NOT NULL')
      .get() as { count: number };

    const ordersWithoutToken = this.db
      .prepare('SELECT COUNT(*) as count FROM orders WHERE lead_token IS NULL')
      .get() as { count: number };

    return {
      totalLeads: totalLeads.count,
      totalEvents: totalEvents.count,
      eventsByType,
      ordersWithToken: ordersWithToken.count,
      ordersWithoutToken: ordersWithoutToken.count
    };
  }

  getFunnelAnalytics(dateStart?: number, dateEnd?: number): FunnelAnalytics {
    const now = Date.now();
    const start = dateStart || 0;
    const end = dateEnd || now;

    const dateFilter = ' WHERE created_at >= ? AND created_at <= ?';

    // Marketing/client events are counted from funnel_events (these are legitimately
    // client-instrumented via the public /v1/landing/track endpoint).
    // Phantom stages (QUALIFIED, INTERESTED) removed per MARKETING-01 governance.
    const MARKETING_EVENTS = [
      'PAGE_VIEW',
      'VIEW_PRODUCT',
      'VIEW_PRICE',
      'CLICK_WHATSAPP',
      'LEAD_CREATED'
    ];

    // Commercial lifecycle events are counted from actual database state
    // (orders/licenses/license_devices tables) because these represent
    // server-verified truth, NOT client-supplied events.
    // This prevents clients from forging commercial events via the public
    // tracking endpoint to inflate metrics.

    const eventCount = (eventType: string): number => {
      const row = this.db
        .prepare(
          `SELECT COUNT(*) as count FROM funnel_events${dateFilter} AND event_type = ?`
        )
        .get(start, end, eventType) as { count: number };
      return row.count;
    };

    /**
     * Counts DISTINCT installations rather than raw rows.
     *
     * Historical rows predate installation_id and have it NULL. Those fall back to their
     * lead_token so pre-fix history stays visible instead of silently dropping to zero.
     * Post-fix app rows all share one installation_id, so repeated emissions collapse to one
     * installation. This is the metric that makes "22 gate views" read as what it was:
     * one installation emitting 22 rows.
     */
    const uniqueInstallationCount = (eventType: string): number => {
      const row = this.db
        .prepare(
          `SELECT COUNT(DISTINCT COALESCE(NULLIF(installation_id, ''), lead_token)) as count
           FROM funnel_events${dateFilter} AND event_type = ?`
        )
        .get(start, end, eventType) as { count: number };
      return row.count;
    };

    // Funnel stage counts: marketing events from funnel_events,
    // commercial events from actual table state.
    const funnel: Array<{ name: string; count: number; metric?: FunnelMetricKind; unit?: string }> = [];

    const marketingCounts: Record<string, number> = {};
    for (const et of MARKETING_EVENTS) {
      marketingCounts[et] = eventCount(et);
      funnel.push({ name: et, count: marketingCounts[et], metric: 'event_count', unit: 'event' });
    }

    // Commercial stages are counted as BUSINESS TRUTH from the orders table.
    // Attribution (lead_token) is deliberately NOT a filter here: an order that exists is an
    // order, regardless of whether a campaign token was attached. Requiring lead_token made
    // these stages report 0 for every real order in the system.
    const orderCreatedCount = this.db
      .prepare(
        `SELECT COUNT(*) as count FROM orders WHERE created_at >= ? AND created_at <= ?`
      )
      .get(start, end) as { count: number };
    funnel.push({ name: 'ORDER_CREATED', count: orderCreatedCount.count, metric: 'orders', unit: 'pesanan' });

    const paymentConfirmedCount = this.db
      .prepare(
        `SELECT COUNT(*) as count FROM orders WHERE created_at >= ? AND created_at <= ? AND payment_status = 'PAID'`
      )
      .get(start, end) as { count: number };
    funnel.push({
      name: 'PAYMENT_CONFIRMED',
      count: paymentConfirmedCount.count,
      metric: 'payments',
      unit: 'pembayaran'
    });

    const licenseCreatedCount = this.db
      .prepare(
        `SELECT COUNT(*) as count FROM orders WHERE created_at >= ? AND created_at <= ? AND license_id IS NOT NULL`
      )
      .get(start, end) as { count: number };
    funnel.push({
      name: 'LICENSE_CREATED',
      count: licenseCreatedCount.count,
      metric: 'licenses',
      unit: 'lisensi'
    });

    const deliveryReadyCount = this.db
      .prepare(
        `SELECT COUNT(*) as count FROM orders WHERE created_at >= ? AND created_at <= ? AND status = 'DELIVERED'`
      )
      .get(start, end) as { count: number };
    funnel.push({
      name: 'DELIVERY_READY',
      count: deliveryReadyCount.count,
      metric: 'deliveries',
      unit: 'pengiriman'
    });

    // LICENSE_ACTIVATED: licenses with an ACTIVE device binding, reached through the order
    // that produced them. Source of truth is license state, not client events.
    const licenseActivatedCount = this.db
      .prepare(
        `SELECT COUNT(*) as count
         FROM orders o
         INNER JOIN licenses l ON o.license_id = l.id
         INNER JOIN license_devices ld ON ld.license_id = l.id
         WHERE o.created_at >= ? AND o.created_at <= ?
           AND l.status = 'ACTIVE'
           AND ld.status = 'ACTIVE'`
      )
      .get(start, end) as { count: number };
    funnel.push({
      name: 'LICENSE_ACTIVATED',
      count: licenseActivatedCount.count,
      metric: 'activations',
      unit: 'aktivasi'
    });

    // PATH B: Product-Led Acquisition & Mobile Conversion Stages (MARKETING-04 & MARKETING-05)
    //
    // Semantics are now explicit per stage:
    //   APK_DOWNLOADED             -> raw event count (a browser-side download click)
    //   APP_FIRST_OPEN             -> UNIQUE INSTALLATIONS
    //   LICENSE_GATE_VIEWED        -> UNIQUE INSTALLATIONS
    //   LICENSE_PURCHASE_CLICKED   -> raw click count (repeat taps are real, keep them)
    //   LICENSE_WHATSAPP_CLICKED   -> raw click count
    const productLedEvents = [
      'APK_DOWNLOADED',
      'APP_FIRST_OPEN',
      'LICENSE_GATE_VIEWED',
      'LICENSE_PURCHASE_CLICKED',
      'LICENSE_WHATSAPP_CLICKED'
    ];

    const productLedCounts: Record<string, number> = {};
    for (const et of productLedEvents) {
      productLedCounts[et] = eventCount(et);
    }
    funnel.push({
      name: 'APK_DOWNLOADED',
      count: productLedCounts['APK_DOWNLOADED'],
      metric: 'event_count',
      unit: 'event'
    });
    productLedCounts['APP_FIRST_OPEN'] = uniqueInstallationCount('APP_FIRST_OPEN');
    funnel.push({
      name: 'APP_FIRST_OPEN',
      count: productLedCounts['APP_FIRST_OPEN'],
      metric: 'unique_installations',
      unit: 'instalasi'
    });
    productLedCounts['LICENSE_GATE_VIEWED'] = uniqueInstallationCount('LICENSE_GATE_VIEWED');
    funnel.push({
      name: 'LICENSE_GATE_VIEWED',
      count: productLedCounts['LICENSE_GATE_VIEWED'],
      metric: 'unique_installations',
      unit: 'instalasi'
    });
    funnel.push({
      name: 'LICENSE_PURCHASE_CLICKED',
      count: productLedCounts['LICENSE_PURCHASE_CLICKED'],
      metric: 'event_count',
      unit: 'klik'
    });
    funnel.push({
      name: 'LICENSE_WHATSAPP_CLICKED',
      count: productLedCounts['LICENSE_WHATSAPP_CLICKED'],
      metric: 'event_count',
      unit: 'klik'
    });

    // North Star Metric: Activated Paid Customers.
    // Source of truth is license/device state. COUNT(DISTINCT license_id) is used instead of
    // COUNT(DISTINCT o.lead_token) because lead_token is optional attribution metadata and is
    // NULL for real orders — the previous form reported 0 for every activated customer.
    const activatedPaidCustomersQuery = this.db
      .prepare(
        `SELECT COUNT(DISTINCT l.id) as count
         FROM orders o
         INNER JOIN licenses l ON o.license_id = l.id
         INNER JOIN license_devices ld ON ld.license_id = l.id
         WHERE o.created_at >= ? AND o.created_at <= ?
           AND o.payment_status = 'PAID'
           AND l.status = 'ACTIVE'
           AND ld.status = 'ACTIVE'`
      )
      .get(start, end) as { count: number };

    const activatedPaidCustomers = activatedPaidCustomersQuery.count;

    const safeRate = (num: number, den: number): number => {
      if (den === 0) return 0;
      return Math.round((num / den) * 10000) / 100;
    };

    // Conversion rates.
    //
    // A conversion rate is only meaningful when numerator and denominator are measured on a
    // comparable population. `cohortLinked` records that judgement explicitly instead of
    // letting an incomparable ratio render as a confident percentage.
    //
    // cohortLinked = true  : both sides are the same kind of unit (both commercial records,
    //                       or both distinct app installations from the same event stream).
    // cohortLinked = false : the two sides count different things and have no shared identity
    //                       (e.g. a browser-side download event vs an app-side installation).
    //                       rate is null; eventRatio is still reported and labelled as a ratio.
    const conversions = [
      // PATH A: Sales-Assisted Conversions
      {
        from: 'PAGE_VIEW', to: 'CLICK_WHATSAPP',
        numerator: marketingCounts['CLICK_WHATSAPP'],
        denominator: marketingCounts['PAGE_VIEW'],
        cohortLinked: false
      },
      {
        from: 'CLICK_WHATSAPP', to: 'LEAD_CREATED',
        numerator: marketingCounts['LEAD_CREATED'],
        denominator: marketingCounts['CLICK_WHATSAPP'],
        cohortLinked: false
      },
      {
        from: 'LEAD_CREATED', to: 'ORDER_CREATED',
        numerator: orderCreatedCount.count,
        denominator: marketingCounts['LEAD_CREATED'],
        cohortLinked: false
      },
      {
        from: 'ORDER_CREATED', to: 'PAYMENT_CONFIRMED',
        numerator: paymentConfirmedCount.count,
        denominator: orderCreatedCount.count,
        cohortLinked: true
      },
      {
        from: 'PAYMENT_CONFIRMED', to: 'LICENSE_CREATED',
        numerator: licenseCreatedCount.count,
        denominator: paymentConfirmedCount.count,
        cohortLinked: true
      },
      {
        from: 'LICENSE_CREATED', to: 'LICENSE_ACTIVATED',
        numerator: licenseActivatedCount.count,
        denominator: licenseCreatedCount.count,
        cohortLinked: true
      },
      // PATH B: Product-Led Conversions
      {
        from: 'APK_DOWNLOADED', to: 'APP_FIRST_OPEN',
        numerator: productLedCounts['APP_FIRST_OPEN'],
        denominator: productLedCounts['APK_DOWNLOADED'],
        // APK_DOWNLOADED is a browser-side event; APP_FIRST_OPEN is a distinct app
        // installation. No shared identity exists between them.
        cohortLinked: false
      },
      {
        from: 'APP_FIRST_OPEN', to: 'LICENSE_GATE_VIEWED',
        numerator: productLedCounts['LICENSE_GATE_VIEWED'],
        denominator: productLedCounts['APP_FIRST_OPEN'],
        // Both are distinct app installations from the same event stream.
        cohortLinked: true
      },
      {
        from: 'LICENSE_GATE_VIEWED', to: 'LICENSE_PURCHASE_CLICKED',
        numerator: productLedCounts['LICENSE_PURCHASE_CLICKED'],
        denominator: productLedCounts['LICENSE_GATE_VIEWED'],
        // Denominator is installations, numerator is raw clicks. Repeat taps are real, so the
        // ratio is a clicks-per-installation figure, not a conversion rate.
        cohortLinked: false
      },
      {
        from: 'LICENSE_PURCHASE_CLICKED', to: 'ORDER_CREATED',
        numerator: orderCreatedCount.count,
        denominator: productLedCounts['LICENSE_PURCHASE_CLICKED'],
        cohortLinked: false
      }
    ].map(c => ({
      from: c.from,
      to: c.to,
      // `rate` stays a plain finite number for API compatibility. It is NOT necessarily a
      // conversion: when cohortLinked is false the value is only a raw event ratio. Consumers
      // must read `cohortLinked` before presenting anything as a conversion rate.
      rate: safeRate(c.numerator, c.denominator),
      numerator: c.numerator,
      denominator: c.denominator,
      cohortLinked: c.cohortLinked,
      eventRatio: c.denominator === 0 ? 0 : safeRate(c.numerator, c.denominator)
    }));

    const computeAttribution = (column: string): AttributionEntry[] => {
      const rows = this.db
        .prepare(
          `SELECT 
             COALESCE(o.${column}, 'Unattributed') as dim_value,
             COUNT(DISTINCT o.lead_token) as leads,
             COUNT(o.id) as orders,
             SUM(CASE WHEN o.payment_status = 'PAID' THEN 1 ELSE 0 END) as paid_orders,
             SUM(CASE WHEN o.license_id IS NOT NULL THEN 1 ELSE 0 END) as licenses,
             COUNT(DISTINCT CASE WHEN l.status = 'ACTIVE' AND EXISTS (
               SELECT 1 FROM license_devices ld 
               WHERE ld.license_id = l.id AND ld.status = 'ACTIVE'
             ) THEN o.lead_token END) as activated_customers
           FROM orders o
           LEFT JOIN licenses l ON l.id = o.license_id
           WHERE o.created_at >= ? AND o.created_at <= ? AND o.lead_token IS NOT NULL
           GROUP BY COALESCE(o.${column}, 'Unattributed')
           ORDER BY orders DESC, leads DESC
           LIMIT 50`
        )
        .all(start, end) as Array<{
          dim_value: string;
          leads: number;
          orders: number;
          paid_orders: number;
          licenses: number;
          activated_customers: number;
        }>;

      return rows.map(r => ({
        dimension: column,
        value: r.dim_value,
        leads: r.leads,
        orders: r.orders,
        paidOrders: r.paid_orders,
        licenses: r.licenses,
        activatedCustomers: r.activated_customers
      }));
    };

    return {
      dateRange: { start, end },
      funnel,
      conversions,
      attribution: {
        sources: computeAttribution('utm_source'),
        mediums: computeAttribution('utm_medium'),
        campaigns: computeAttribution('utm_campaign'),
        contents: computeAttribution('utm_content')
      },
      northStar: {
        activatedPaidCustomers
      }
    };
  }
}
