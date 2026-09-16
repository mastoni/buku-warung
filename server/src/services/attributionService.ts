import Database from 'better-sqlite3';
import { getDatabase } from '../db/database.js';
import {
  FunnelEventRecord,
  LandingTrackRequest,
  LandingTrackResponse,
  FunnelAnalytics,
  AttributionEntry
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
    const { eventType, leadToken, ...utmParams } = payload;

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
        INSERT INTO funnel_events (lead_token, event_type, event_data, ip_hash, user_agent, created_at)
        VALUES (?, ?, ?, ?, ?, ?)
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
        now
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
    const MARKETING_EVENTS = [
      'PAGE_VIEW',
      'VIEW_PRODUCT',
      'VIEW_PRICE',
      'CLICK_WHATSAPP',
      'LEAD_CREATED',
      'QUALIFIED',
      'INTERESTED',
      'APK_DOWNLOADED'
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

    // Funnel stage counts: marketing events from funnel_events,
    // commercial events from actual table state.
    const funnel: Array<{ name: string; count: number }> = [];

    const marketingCounts: Record<string, number> = {};
    for (const et of MARKETING_EVENTS) {
      marketingCounts[et] = eventCount(et);
      funnel.push({ name: et, count: marketingCounts[et] });
    }

    // ORDER_CREATED: count orders with lead_token (server-side created via admin)
    const orderCreatedCount = this.db
      .prepare(
        `SELECT COUNT(*) as count FROM orders WHERE created_at >= ? AND created_at <= ? AND lead_token IS NOT NULL`
      )
      .get(start, end) as { count: number };
    funnel.push({ name: 'ORDER_CREATED', count: orderCreatedCount.count });

    // PAYMENT_CONFIRMED: count orders with payment_status = 'PAID' and lead_token
    const paymentConfirmedCount = this.db
      .prepare(
        `SELECT COUNT(*) as count FROM orders WHERE created_at >= ? AND created_at <= ? AND payment_status = 'PAID' AND lead_token IS NOT NULL`
      )
      .get(start, end) as { count: number };
    funnel.push({ name: 'PAYMENT_CONFIRMED', count: paymentConfirmedCount.count });

    // LICENSE_CREATED: count orders with license_id set and lead_token
    const licenseCreatedCount = this.db
      .prepare(
        `SELECT COUNT(*) as count FROM orders WHERE created_at >= ? AND created_at <= ? AND license_id IS NOT NULL AND lead_token IS NOT NULL`
      )
      .get(start, end) as { count: number };
    funnel.push({ name: 'LICENSE_CREATED', count: licenseCreatedCount.count });

    // DELIVERY_READY: count orders with status = 'DELIVERED' and lead_token
    const deliveryReadyCount = this.db
      .prepare(
        `SELECT COUNT(*) as count FROM orders WHERE created_at >= ? AND created_at <= ? AND status = 'DELIVERED' AND lead_token IS NOT NULL`
      )
      .get(start, end) as { count: number };
    funnel.push({ name: 'DELIVERY_READY', count: deliveryReadyCount.count });

    // LICENSE_ACTIVATED: count licenses with active device bindings via orders
    // This is the SOURCE OF TRUTH for actual license activation, not forgeable
    // via the public tracking endpoint.
    const licenseActivatedCount = this.db
      .prepare(
        `SELECT COUNT(*) as count
         FROM orders o
         INNER JOIN licenses l ON o.license_id = l.id
         INNER JOIN license_devices ld ON ld.license_id = l.id
         WHERE o.created_at >= ? AND o.created_at <= ?
           AND l.status = 'ACTIVE'
           AND ld.status = 'ACTIVE'
           AND o.lead_token IS NOT NULL`
      )
      .get(start, end) as { count: number };
    funnel.push({ name: 'LICENSE_ACTIVATED', count: licenseActivatedCount.count });

    // North Star Metric: Activated Paid Customers
    // Uses actual license/device state as the source of truth.
    // COUNT(DISTINCT) ensures repeated activations don't inflate the count.
    const activatedPaidCustomersQuery = this.db
      .prepare(
        `SELECT COUNT(DISTINCT o.lead_token) as count
         FROM orders o
         INNER JOIN licenses l ON o.license_id = l.id
         INNER JOIN license_devices ld ON ld.license_id = l.id
         WHERE o.created_at >= ? AND o.created_at <= ?
           AND o.payment_status = 'PAID'
           AND o.lead_token IS NOT NULL
           AND l.status = 'ACTIVE'
           AND ld.status = 'ACTIVE'`
      )
      .get(start, end) as { count: number };

    const activatedPaidCustomers = activatedPaidCustomersQuery.count;

    const safeRate = (num: number, den: number): number => {
      if (den === 0) return 0;
      return Math.round((num / den) * 10000) / 100;
    };

    // Conversion rates: marketing events from funnel_events, commercial events
    // from actual table state. This ensures conversions reflect real data.
    const conversions = [
      {
        from: 'PAGE_VIEW', to: 'CLICK_WHATSAPP',
        numerator: marketingCounts['CLICK_WHATSAPP'],
        denominator: marketingCounts['PAGE_VIEW']
      },
      {
        from: 'CLICK_WHATSAPP', to: 'LEAD_CREATED',
        numerator: marketingCounts['LEAD_CREATED'],
        denominator: marketingCounts['CLICK_WHATSAPP']
      },
      {
        from: 'LEAD_CREATED', to: 'ORDER_CREATED',
        numerator: orderCreatedCount.count,
        denominator: marketingCounts['LEAD_CREATED']
      },
      {
        from: 'ORDER_CREATED', to: 'PAYMENT_CONFIRMED',
        numerator: paymentConfirmedCount.count,
        denominator: orderCreatedCount.count
      },
      {
        from: 'PAYMENT_CONFIRMED', to: 'LICENSE_CREATED',
        numerator: licenseCreatedCount.count,
        denominator: paymentConfirmedCount.count
      },
      {
        from: 'LICENSE_CREATED', to: 'LICENSE_ACTIVATED',
        numerator: licenseActivatedCount.count,
        denominator: licenseCreatedCount.count
      }
    ].map(c => ({
      from: c.from,
      to: c.to,
      rate: safeRate(c.numerator, c.denominator),
      numerator: c.numerator,
      denominator: c.denominator
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
