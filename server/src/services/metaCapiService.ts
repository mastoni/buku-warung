import crypto from 'crypto';
import { config } from '../config/index.js';
import { normalizeIndonesianPhone } from '../utils/phone.js';
import { OrderRecord } from '../types/index.js';

export interface MetaCapiUserData {
  em?: string[]; // SHA-256 hashed emails
  ph?: string[]; // SHA-256 hashed phone numbers
  client_ip_address?: string;
  client_user_agent?: string;
  fbc?: string;
  fbp?: string;
}

export interface MetaCapiCustomData {
  currency?: string;
  value?: number;
  content_name?: string;
  order_id?: string;
  status?: string;
  [key: string]: unknown;
}

export interface MetaCapiEventPayload {
  event_name: 'PageView' | 'ViewContent' | 'InitiateCheckout' | 'Purchase';
  event_time: number; // Unix timestamp in seconds
  event_id: string; // Deduplication ID matching client Pixel
  event_source_url?: string;
  action_source: 'website';
  user_data: MetaCapiUserData;
  custom_data?: MetaCapiCustomData;
}

export interface MetaCapiResult {
  success: boolean;
  sent: boolean;
  reason?: string;
  eventId?: string;
  httpStatus?: number;
  eventsReceived?: number;
  fbtraceId?: string;
  messages?: unknown[];
  error?: string;
}

export interface MetaCapiConfig {
  pixelId?: string;
  accessToken?: string;
  apiVersion?: string;
  testEventCode?: string;
  enabled?: boolean;
  timeoutMs?: number;
  fetchFn?: typeof fetch;
}

/**
 * Normalizes and hashes string values with SHA-256 for Meta Conversions API compliance.
 * Returns empty string if input is empty.
 */
export function hashForMeta(value?: string | null): string {
  if (!value || typeof value !== 'string') return '';
  const cleaned = value.trim().toLowerCase();
  if (!cleaned) return '';
  return crypto.createHash('sha256').update(cleaned).digest('hex');
}

/**
 * Normalizes phone number to digits only (e.g., 628123456789) and computes SHA-256.
 */
export function hashPhoneForMeta(phone?: string | null): string {
  if (!phone || typeof phone !== 'string') return '';
  const normalized = normalizeIndonesianPhone(phone);
  if (!normalized.valid) {
    // If not standard Indonesian format, strip non-digits
    const digitsOnly = phone.replace(/\D/g, '');
    if (!digitsOnly) return '';
    return crypto.createHash('sha256').update(digitsOnly).digest('hex');
  }
  return crypto.createHash('sha256').update(normalized.normalized).digest('hex');
}

export class MetaCapiService {
  private readonly pixelId: string;
  private readonly accessToken: string;
  private readonly apiVersion: string;
  private readonly testEventCode: string;
  private readonly enabled: boolean;
  private readonly timeoutMs: number;
  private readonly fetchFn: typeof fetch;

  constructor(options: MetaCapiConfig = {}) {
    this.pixelId = (options.pixelId !== undefined ? options.pixelId : config.meta.pixelId || '').trim();
    this.accessToken = (options.accessToken !== undefined ? options.accessToken : config.meta.capiAccessToken || '').trim();
    this.apiVersion = (options.apiVersion !== undefined ? options.apiVersion : config.meta.apiVersion || 'v19.0').trim();
    this.testEventCode = (options.testEventCode !== undefined ? options.testEventCode : config.meta.testEventCode || '').trim();
    this.enabled = options.enabled !== undefined ? options.enabled : config.meta.enabled;
    this.timeoutMs = options.timeoutMs || 5000;
    this.fetchFn = options.fetchFn || fetch;
  }

  /**
   * Returns true only when Pixel ID and Access Token are both configured with non-empty values.
   */
  isConfigured(): boolean {
    return this.pixelId.length > 0 && this.accessToken.length > 0;
  }

  /**
   * Returns true only when CAPI is explicitly enabled and properly configured.
   */
  isEnabled(): boolean {
    return this.enabled && this.isConfigured();
  }

  /**
   * Public inspector for integration status without revealing sensitive secrets.
   */
  getStatus(): {
    configured: boolean;
    enabled: boolean;
    apiVersion: string;
    hasPixelId: boolean;
    hasAccessToken: boolean;
    hasTestEventCode: boolean;
  } {
    return {
      configured: this.isConfigured(),
      enabled: this.isEnabled(),
      apiVersion: this.apiVersion,
      hasPixelId: this.pixelId.length > 0,
      hasAccessToken: this.accessToken.length > 0,
      hasTestEventCode: this.testEventCode.length > 0
    };
  }

  /**
   * Internal transport for sending events to Meta Graph API.
   * Never throws; failures return a safe MetaCapiResult.
   */
  async sendEvent(event: MetaCapiEventPayload): Promise<MetaCapiResult> {
    if (!this.isEnabled()) {
      return {
        success: false,
        sent: false,
        reason: 'CAPI_DISABLED_OR_UNCONFIGURED',
        eventId: event.event_id
      };
    }

    const endpoint = `https://graph.facebook.com/${this.apiVersion}/${this.pixelId}/events`;

    const bodyPayload: Record<string, unknown> = {
      data: [event],
      access_token: this.accessToken
    };

    if (this.testEventCode) {
      bodyPayload.test_event_code = this.testEventCode;
    }

    const controller = new AbortController();
    const timeoutHandle = setTimeout(() => controller.abort(), this.timeoutMs);

    try {
      const response = await this.fetchFn(endpoint, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(bodyPayload),
        signal: controller.signal
      });

      clearTimeout(timeoutHandle);

      if (response.ok) {
        const body = (await response.json().catch(() => ({}))) as {
          events_received?: number;
          fbtrace_id?: string;
          messages?: unknown[];
        };
        return {
          success: true,
          sent: true,
          eventId: event.event_id,
          httpStatus: response.status,
          eventsReceived: body.events_received,
          fbtraceId: body.fbtrace_id,
          messages: body.messages
        };
      }

      const errorBody = (await response.json().catch(() => ({}))) as {
        error?: { message?: string; fbtrace_id?: string; code?: number };
      };
      const metaErrorMessage = errorBody?.error?.message || `Meta Graph API responded with status ${response.status}`;

      return {
        success: false,
        sent: false,
        eventId: event.event_id,
        httpStatus: response.status,
        reason: 'API_ERROR',
        error: metaErrorMessage,
        fbtraceId: errorBody?.error?.fbtrace_id
      };
    } catch (err: unknown) {
      clearTimeout(timeoutHandle);
      const isAbort = (err as Error)?.name === 'AbortError';
      return {
        success: false,
        sent: false,
        eventId: event.event_id,
        reason: isAbort ? 'TIMEOUT' : 'NETWORK_ERROR',
        error: isAbort ? 'Request timed out' : (err as Error)?.message || 'Unknown network error'
      };
    }
  }

  /**
   * Dispatches server-side Purchase event.
   *
   * STRICT GOVERNANCE RULES:
   * 1. ONLY dispatched if order.payment_status === 'PAID'.
   * 2. NEVER called on button clicks, form opens, or pending orders.
   * 3. event_id matches 'pur_' + order.order_number for deduplication.
   * 4. Customer PII (email, phone) is hashed with SHA-256 before sending.
   * 5. Non-blocking: failures will never interrupt order fulfillment or license issuance.
   */
  async sendPurchase(
    order: OrderRecord,
    clientContext?: { ip?: string; userAgent?: string; fbp?: string; fbc?: string }
  ): Promise<MetaCapiResult> {
    if (order.payment_status !== 'PAID') {
      return {
        success: false,
        sent: false,
        reason: 'ORDER_NOT_PAID',
        eventId: `pur_${order.order_number}`
      };
    }

    const eventId = `pur_${order.order_number}`;
    const hashedEmail = hashForMeta(order.owner_email);
    const hashedPhone = hashPhoneForMeta(order.customer_contact);

    const userData: MetaCapiUserData = {
      client_ip_address: clientContext?.ip,
      client_user_agent: clientContext?.userAgent,
      fbp: clientContext?.fbp,
      fbc: clientContext?.fbc
    };

    if (hashedEmail) userData.em = [hashedEmail];
    if (hashedPhone) userData.ph = [hashedPhone];

    const payload: MetaCapiEventPayload = {
      event_name: 'Purchase',
      event_time: Math.floor((order.verified_at || order.created_at || Date.now()) / 1000),
      event_id: eventId,
      event_source_url: 'https://license.skmnetwork.com/beli/buku-warung',
      action_source: 'website',
      user_data: userData,
      custom_data: {
        currency: 'IDR',
        value: order.amount,
        content_name: 'Buku Warung',
        order_id: order.order_number,
        status: order.payment_status
      }
    };

    return this.sendEvent(payload);
  }

  /**
   * Dispatches server-side InitiateCheckout event.
   * Uses event_id 'ic_' + leadToken to match the client-side browser event.
   */
  async sendInitiateCheckout(
    leadToken: string,
    clientContext?: { ip?: string; userAgent?: string }
  ): Promise<MetaCapiResult> {
    const eventId = `ic_${leadToken}`;
    const payload: MetaCapiEventPayload = {
      event_name: 'InitiateCheckout',
      event_time: Math.floor(Date.now() / 1000),
      event_id: eventId,
      event_source_url: 'https://license.skmnetwork.com/beli/buku-warung',
      action_source: 'website',
      user_data: {
        client_ip_address: clientContext?.ip,
        client_user_agent: clientContext?.userAgent
      },
      custom_data: {
        currency: 'IDR',
        value: 139000,
        content_name: 'Buku Warung'
      }
    };

    return this.sendEvent(payload);
  }

  /**
   * Dispatches server-side PageView event.
   * Uses the exact event_id passed from the browser client to guarantee deduplication.
   */
  async sendPageView(
    eventId: string,
    clientContext?: { ip?: string; userAgent?: string; fbp?: string; fbc?: string; eventSourceUrl?: string }
  ): Promise<MetaCapiResult> {
    const payload: MetaCapiEventPayload = {
      event_name: 'PageView',
      event_time: Math.floor(Date.now() / 1000),
      event_id: eventId,
      event_source_url: clientContext?.eventSourceUrl || 'https://bukuwarung.skmnetwork.com/',
      action_source: 'website',
      user_data: {
        client_ip_address: clientContext?.ip,
        client_user_agent: clientContext?.userAgent,
        fbp: clientContext?.fbp,
        fbc: clientContext?.fbc
      }
    };

    return this.sendEvent(payload);
  }
}
