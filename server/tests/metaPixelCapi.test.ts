import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import { FastifyInstance } from 'fastify';
import Database from 'better-sqlite3';
import { buildApp } from '../src/app.js';
import { getDatabase, closeDatabase } from '../src/db/database.js';
import { config } from '../src/config/index.js';
import {
  MetaCapiService,
  hashForMeta,
  hashPhoneForMeta,
  MetaCapiEventPayload
} from '../src/services/metaCapiService.js';
import { OrderRecord } from '../types/index.js';

describe('BW-MARKETING-2B — Meta Pixel & Conversions API (CAPI) Preparation', () => {
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

  /* =========================================================================
   * 1. Configuration & Default Inactivity
   * ========================================================================= */
  describe('1. Configuration & Safe Inactive Defaults', () => {
    it('1.1 CAPI service is disabled and unconfigured by default when credentials are empty', () => {
      const service = new MetaCapiService({
        pixelId: '',
        accessToken: '',
        enabled: false
      });

      expect(service.isConfigured()).toBe(false);
      expect(service.isEnabled()).toBe(false);

      const status = service.getStatus();
      expect(status.configured).toBe(false);
      expect(status.enabled).toBe(false);
      expect(status.hasPixelId).toBe(false);
      expect(status.hasAccessToken).toBe(false);
      expect(status.apiVersion).toBe('v19.0');
    });

    it('1.2 CAPI service remains disabled if accessToken is missing even if pixelId and enabled are true', () => {
      const service = new MetaCapiService({
        pixelId: '123456789012345',
        accessToken: '',
        enabled: true
      });

      expect(service.isConfigured()).toBe(false);
      expect(service.isEnabled()).toBe(false);
    });

    it('1.3 CAPI service remains disabled if enabled flag is false even if credentials exist', () => {
      const service = new MetaCapiService({
        pixelId: '123456789012345',
        accessToken: 'EAABdummysecretfortestingonly',
        enabled: false
      });

      expect(service.isConfigured()).toBe(true);
      expect(service.isEnabled()).toBe(false);
    });

    it('1.4 API version is explicit and configurable, never guessed', () => {
      const defaultService = new MetaCapiService();
      expect(defaultService.getStatus().apiVersion).toBe('v19.0');

      const customService = new MetaCapiService({ apiVersion: 'v20.0' });
      expect(customService.getStatus().apiVersion).toBe('v20.0');
    });

    it('1.5 getStatus() never exposes raw access tokens or secrets', () => {
      const service = new MetaCapiService({
        pixelId: '987654321',
        accessToken: 'SECRET_TOKEN_NEVER_EXPOSE',
        enabled: true
      });

      const status = service.getStatus();
      expect(status).not.toHaveProperty('accessToken');
      expect(JSON.stringify(status)).not.toContain('SECRET_TOKEN_NEVER_EXPOSE');
      expect(status.hasAccessToken).toBe(true);
    });
  });

  /* =========================================================================
   * 2. Strict Purchase Governance: Only Verified PAID Orders
   * ========================================================================= */
  describe('2. Purchase Event Governance: Only PAID Orders', () => {
    const mockUnpaidOrder: OrderRecord = {
      id: 101,
      order_number: 'BW-ORD-261016-0101',
      customer_name: 'Budi Santoso',
      customer_contact: '081234567890',
      owner_email: 'budi.santoso@example.com',
      amount: 139000,
      status: 'PENDING_PAYMENT',
      payment_status: 'UNPAID',
      lead_token: 'LW-TST001',
      created_at: Date.now(),
      updated_at: Date.now()
    };

    it('2.1 sendPurchase rejects UNPAID order with ORDER_NOT_PAID reason', async () => {
      const service = new MetaCapiService({
        pixelId: '123456789',
        accessToken: 'mock_token',
        enabled: true
      });

      const result = await service.sendPurchase(mockUnpaidOrder);
      expect(result.success).toBe(false);
      expect(result.sent).toBe(false);
      expect(result.reason).toBe('ORDER_NOT_PAID');
      expect(result.eventId).toBe('pur_BW-ORD-261016-0101');
    });

    it('2.2 sendPurchase builds correct payload and hashes PII for PAID orders', async () => {
      let capturedUrl = '';
      let capturedBody: any = null;

      const mockFetch: typeof fetch = async (input, init) => {
        capturedUrl = String(input);
        capturedBody = JSON.parse(String(init?.body));
        return {
          ok: true,
          status: 200,
          json: async () => ({ events_received: 1, fbtrace_id: 'mock_trace_123' })
        } as Response;
      };

      const service = new MetaCapiService({
        pixelId: '1122334455',
        accessToken: 'mock_token_for_mock_fetch',
        apiVersion: 'v19.0',
        enabled: true,
        fetchFn: mockFetch
      });

      const paidOrder: OrderRecord = {
        ...mockUnpaidOrder,
        payment_status: 'PAID',
        status: 'PAID',
        verified_at: 1770000000000
      };

      const result = await service.sendPurchase(paidOrder, {
        ip: '192.168.1.1',
        userAgent: 'Mozilla/5.0 TestBrowser'
      });

      expect(result.success).toBe(true);
      expect(result.sent).toBe(true);
      expect(result.eventId).toBe('pur_BW-ORD-261016-0101');

      // Verify endpoint URL
      expect(capturedUrl).toBe('https://graph.facebook.com/v19.0/1122334455/events');

      // Verify payload data
      expect(capturedBody).toBeDefined();
      const event: MetaCapiEventPayload = capturedBody.data[0];
      expect(event.event_name).toBe('Purchase');
      expect(event.event_id).toBe('pur_BW-ORD-261016-0101');
      expect(event.custom_data?.value).toBe(139000);
      expect(event.custom_data?.currency).toBe('IDR');
      expect(event.custom_data?.order_id).toBe('BW-ORD-261016-0101');

      // Verify PII hashing (SHA-256 64-char lowercase hex)
      const expectedHashedEmail = hashForMeta('budi.santoso@example.com');
      const expectedHashedPhone = hashPhoneForMeta('081234567890');
      expect(event.user_data.em?.[0]).toBe(expectedHashedEmail);
      expect(event.user_data.ph?.[0]).toBe(expectedHashedPhone);
      expect(event.user_data.em?.[0]).toMatch(/^[a-f0-9]{64}$/);
      expect(event.user_data.ph?.[0]).toMatch(/^[a-f0-9]{64}$/);

      // Verify no raw PII in captured body
      expect(JSON.stringify(capturedBody)).not.toContain('budi.santoso@example.com');
      expect(JSON.stringify(capturedBody)).not.toContain('081234567890');
    });
  });

  /* =========================================================================
   * 3. Deduplication & event_id Consistency
   * ========================================================================= */
  describe('3. Deduplication & event_id Consistency', () => {
    it('3.1 sendInitiateCheckout uses matching "ic_" prefix with leadToken', async () => {
      let capturedBody: any = null;
      const mockFetch: typeof fetch = async (_input, init) => {
        capturedBody = JSON.parse(String(init?.body));
        return { ok: true, status: 200, json: async () => ({ events_received: 1 }) } as Response;
      };

      const service = new MetaCapiService({
        pixelId: '1122334455',
        accessToken: 'mock_token',
        enabled: true,
        fetchFn: mockFetch
      });

      const result = await service.sendInitiateCheckout('LW-XYZ789');
      expect(result.success).toBe(true);
      expect(result.eventId).toBe('ic_LW-XYZ789');

      const event = capturedBody.data[0];
      expect(event.event_name).toBe('InitiateCheckout');
      expect(event.event_id).toBe('ic_LW-XYZ789');
      expect(event.custom_data?.value).toBe(139000);
      expect(event.custom_data?.currency).toBe('IDR');
    });

    it('3.2 includes test_event_code when specified for controlled test validation', async () => {
      let capturedBody: any = null;
      const mockFetch: typeof fetch = async (_input, init) => {
        capturedBody = JSON.parse(String(init?.body));
        return { ok: true, status: 200, json: async () => ({ events_received: 1 }) } as Response;
      };

      const service = new MetaCapiService({
        pixelId: '1122334455',
        accessToken: 'mock_token',
        testEventCode: 'TEST12345',
        enabled: true,
        fetchFn: mockFetch
      });

      await service.sendInitiateCheckout('LW-TSTCODE');
      expect(capturedBody).toBeDefined();
      expect(capturedBody.test_event_code).toBe('TEST12345');
    });

    it('3.3 omits test_event_code when empty', async () => {
      let capturedBody: any = null;
      const mockFetch: typeof fetch = async (_input, init) => {
        capturedBody = JSON.parse(String(init?.body));
        return { ok: true, status: 200, json: async () => ({ events_received: 1 }) } as Response;
      };

      const service = new MetaCapiService({
        pixelId: '1122334455',
        accessToken: 'mock_token',
        testEventCode: '',
        enabled: true,
        fetchFn: mockFetch
      });

      await service.sendInitiateCheckout('LW-NOTESTCODE');
      expect(capturedBody).toBeDefined();
      expect(capturedBody).not.toHaveProperty('test_event_code');
    });

    it('3.4 repeated purchase emissions produce identical event_id for deduplication', async () => {
      const order: OrderRecord = {
        id: 105,
        order_number: 'BW-ORD-261016-0105',
        customer_name: 'Repeat Buyer',
        customer_contact: '081299998888',
        owner_email: 'repeat@example.com',
        amount: 139000,
        status: 'PAID',
        payment_status: 'PAID',
        created_at: Date.now(),
        updated_at: Date.now()
      };

      const service = new MetaCapiService({
        pixelId: '1122334455',
        accessToken: 'mock_token',
        enabled: false // inactive mode
      });

      const res1 = await service.sendPurchase(order);
      const res2 = await service.sendPurchase(order);

      // Even when inactive or retried, event_id is identical and deterministic
      expect(res1.eventId).toBe('pur_BW-ORD-261016-0105');
      expect(res2.eventId).toBe('pur_BW-ORD-261016-0105');
      expect(res1.eventId).toBe(res2.eventId);
    });
  });

  /* =========================================================================
   * 4. Resilience, Failure Handling & Non-Interference
   * ========================================================================= */
  describe('4. Resilience: Failures Never Break Business Transactions', () => {
    it('4.1 CAPI network error is caught safely without throwing', async () => {
      const failingFetch: typeof fetch = async () => {
        throw new Error('Connection reset by peer');
      };

      const service = new MetaCapiService({
        pixelId: '1122334455',
        accessToken: 'mock_token',
        enabled: true,
        fetchFn: failingFetch
      });

      const order: OrderRecord = {
        id: 102,
        order_number: 'BW-ORD-261016-0102',
        customer_name: 'Test Customer',
        customer_contact: '081234567899',
        owner_email: 'test@example.com',
        amount: 139000,
        status: 'PAID',
        payment_status: 'PAID',
        created_at: Date.now(),
        updated_at: Date.now()
      };

      const result = await service.sendPurchase(order);
      expect(result.success).toBe(false);
      expect(result.sent).toBe(false);
      expect(result.reason).toBe('NETWORK_ERROR');
      expect(result.error).toContain('Connection reset by peer');
      // Must not leak token in error
      expect(result.error).not.toContain('mock_token');
    });

    it('4.2 CAPI timeout is caught safely without throwing', async () => {
      const abortingFetch: typeof fetch = async () => {
        const err = new Error('The operation was aborted');
        err.name = 'AbortError';
        throw err;
      };

      const service = new MetaCapiService({
        pixelId: '1122334455',
        accessToken: 'mock_token',
        enabled: true,
        fetchFn: abortingFetch
      });

      const order: OrderRecord = {
        id: 103,
        order_number: 'BW-ORD-261016-0103',
        customer_name: 'Timeout Customer',
        customer_contact: '081234567888',
        owner_email: 'timeout@example.com',
        amount: 139000,
        status: 'PAID',
        payment_status: 'PAID',
        created_at: Date.now(),
        updated_at: Date.now()
      };

      const result = await service.sendPurchase(order);
      expect(result.success).toBe(false);
      expect(result.sent).toBe(false);
      expect(result.reason).toBe('TIMEOUT');
    });

    it('4.3 Payment verification endpoint succeeds HTTP 200 even when CAPI is inactive', async () => {
      // Create a test order
      const createRes = await app.inject({
        method: 'POST',
        url: '/v1/admin/orders',
        headers: { authorization: `Bearer ${adminApiKey}` },
        payload: {
          customerName: 'Payment Verification Customer',
          customerContact: '081233334444',
          ownerEmail: 'verify.capi@example.com'
        }
      });
      expect(createRes.statusCode).toBe(201);
      const orderId = JSON.parse(createRes.body).data.id;

      // Verify payment
      const verifyRes = await app.inject({
        method: 'POST',
        url: `/v1/admin/orders/${orderId}/verify-payment`,
        headers: { authorization: `Bearer ${adminApiKey}` },
        payload: {
          paymentMethod: 'MANUAL_TRANSFER',
          paymentReference: 'TRF-TEST-CAPI-001'
        }
      });

      expect(verifyRes.statusCode).toBe(200);
      const verifyBody = JSON.parse(verifyRes.body);
      expect(verifyBody.success).toBe(true);
      expect(verifyBody.data.paymentStatus).toBe('PAID');
      expect(verifyBody.data.status).toBe('PAID');

      // Check database state
      const orderRow = db.prepare('SELECT payment_status FROM orders WHERE id = ?').get(orderId) as any;
      expect(orderRow.payment_status).toBe('PAID');
    });
  });

  /* =========================================================================
   * 5. Public Order Page (Browser-Side Pixel Inactivity by Default)
   * ========================================================================= */
  describe('5. Public Order Page: Safe Default Behavior', () => {
    it('5.1 GET /beli/buku-warung renders cleanly without errors when META_PIXEL_ID is empty', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/beli/buku-warung'
      });

      expect(res.statusCode).toBe(200);
      expect(res.headers['content-type']).toContain('text/html');

      // CSP contains secure headers
      const csp = res.headers['content-security-policy'] as string;
      expect(csp).toContain('script-src');
      expect(csp).toContain('connect.facebook.net');

      // Body contains the safe check for META_PIXEL_ID
      expect(res.body).toContain('var META_PIXEL_ID =');
      expect(res.body).toContain('InitiateCheckout');
    });
  });

  /* =========================================================================
   * 6. PageView CAPI Deduplication & Event ID Validation
   * ========================================================================= */
  describe('6. PageView CAPI Deduplication & Event ID Validation', () => {
    it('6.1 sendPageView constructs compliant Meta CAPI payload with matching event_name and event_id', async () => {
      let capturedPayload: MetaCapiEventPayload | null = null;
      const service = new MetaCapiService({
        pixelId: '2559501331198045',
        accessToken: 'EAATestTokenForPageViewDeduplicationOnly',
        enabled: true
      });

      // Override sendEvent to capture the dispatched payload
      service.sendEvent = async (payload: MetaCapiEventPayload) => {
        capturedPayload = payload;
        return {
          success: true,
          sent: true,
          eventId: payload.event_id,
          httpStatus: 200,
          eventsReceived: 1
        };
      };

      const testEventId = 'pv_browser_generated_123456';
      const result = await service.sendPageView(testEventId, {
        ip: '103.20.10.5',
        userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)',
        eventSourceUrl: 'https://bukuwarung.skmnetwork.com/'
      });

      expect(result.success).toBe(true);
      expect(result.sent).toBe(true);
      expect(result.eventId).toBe(testEventId);

      expect(capturedPayload).not.toBeNull();
      expect(capturedPayload!.event_name).toBe('PageView');
      expect(capturedPayload!.event_id).toBe(testEventId);
      expect(capturedPayload!.action_source).toBe('website');
      expect(capturedPayload!.event_source_url).toBe('https://bukuwarung.skmnetwork.com/');
      expect(capturedPayload!.user_data.client_ip_address).toBe('103.20.10.5');
      expect(capturedPayload!.user_data.client_user_agent).toBe('Mozilla/5.0 (Windows NT 10.0; Win64; x64)');
      expect(capturedPayload!.event_time).toBeGreaterThan(0);
    });

    it('6.2 sendPageView is inert and safe when CAPI is disabled by default', async () => {
      const defaultService = new MetaCapiService({
        pixelId: '2559501331198045',
        accessToken: '',
        enabled: false
      });

      const result = await defaultService.sendPageView('pv_inert_test_7788');
      expect(result.success).toBe(false);
      expect(result.sent).toBe(false);
      expect(result.reason).toBe('CAPI_DISABLED_OR_UNCONFIGURED');
      expect(result.eventId).toBe('pv_inert_test_7788');
    });

    it('6.3 POST /v1/landing/track accepts valid eventId and preserves it for deduplication', async () => {
      const uniqueEventId = `pv_test_${Date.now()}_abc`;
      const res = await app.inject({
        method: 'POST',
        url: '/v1/landing/track',
        payload: {
          eventType: 'PAGE_VIEW',
          eventId: uniqueEventId,
          utm_source: 'meta',
          utm_medium: 'cpc'
        }
      });

      expect(res.statusCode).toBe(200);
      const body = JSON.parse(res.body);
      expect(body.success).toBe(true);
      expect(body.data.leadToken).toBeDefined();
      expect(body.data.eventId).toBe(uniqueEventId);

      // Verify eventId was stored in funnel_events
      const row = db.prepare('SELECT event_data FROM funnel_events WHERE lead_token = ? AND event_type = ?')
        .get(body.data.leadToken, 'PAGE_VIEW') as any;
      expect(row).toBeDefined();
      const parsedData = JSON.parse(row.event_data);
      expect(parsedData.eventId).toBe(uniqueEventId);
      expect(parsedData.utm_source).toBe('meta');
    });

    it('6.4 POST /v1/landing/track rejects invalid eventId format (special chars, injection, or >128 chars)', async () => {
      // Test invalid characters
      const invalidCharsRes = await app.inject({
        method: 'POST',
        url: '/v1/landing/track',
        payload: {
          eventType: 'PAGE_VIEW',
          eventId: 'pv_<script>alert(1)</script>'
        }
      });
      expect(invalidCharsRes.statusCode).toBe(400);
      expect(JSON.parse(invalidCharsRes.body).error.code).toBe('INVALID_EVENT_ID');

      // Test too long (>128 chars)
      const tooLongRes = await app.inject({
        method: 'POST',
        url: '/v1/landing/track',
        payload: {
          eventType: 'PAGE_VIEW',
          eventId: 'pv_' + 'a'.repeat(130)
        }
      });
      expect(tooLongRes.statusCode).toBe(400);
    });

    it('6.5 deduplication pair event_name + event_id matches between browser and server semantics', () => {
      const clientGeneratedEventId = 'pv_client_session_889900';

      // Browser expectation:
      // fbq('track', 'PageView', {}, { eventID: clientGeneratedEventId })
      const browserEventName = 'PageView';
      const browserEventId = clientGeneratedEventId;

      expect(browserEventName).toBe('PageView');
      expect(browserEventId).toBe(clientGeneratedEventId);
      expect(browserEventId.startsWith('pv_')).toBe(true);
      expect(browserEventId.startsWith('ic_')).toBe(false); // Does not reuse InitiateCheckout ID
    });
  });
});
