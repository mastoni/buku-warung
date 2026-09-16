import crypto from 'crypto';

const LEAD_TOKEN_PREFIX = 'LW-';
const LEAD_TOKEN_LENGTH = 6;
const LEAD_TOKEN_REGEX = /^LW-[2-9A-HJ-NP-Z]{6}$/;

export const ALLOWED_UTM_PARAMS = ['utm_source', 'utm_medium', 'utm_campaign', 'utm_content'] as const;

export const ALLOWED_FUNNEL_EVENTS = [
  'PAGE_VIEW',
  'VIEW_PRODUCT',
  'VIEW_PRICE',
  'CLICK_WHATSAPP',
  'LEAD_CREATED',
  'QUALIFIED',
  'INTERESTED',
  'ORDER_CREATED',
  'PAYMENT_CONFIRMED',
  'LICENSE_CREATED',
  'DELIVERY_READY',
  'APK_DOWNLOADED',
  'LICENSE_ACTIVATED'
] as const;

export type FunnelEventType = (typeof ALLOWED_FUNNEL_EVENTS)[number];

const UTM_MAX_LENGTH = 100;
const UTM_PATTERN = /^[a-zA-Z0-9\-_.\s]+$/;

export function generateLeadToken(): string {
  const chars = '23456789ABCDEFGHJKMNPQRSTVWXYZ';
  const bytes = crypto.randomBytes(LEAD_TOKEN_LENGTH);
  let segment = '';
  for (let i = 0; i < LEAD_TOKEN_LENGTH; i++) {
    segment += chars[bytes[i] % chars.length];
  }
  return `${LEAD_TOKEN_PREFIX}${segment}`;
}

export function isValidLeadToken(token: string): boolean {
  return LEAD_TOKEN_REGEX.test(token);
}

export function hashIpAddress(ip: string): string {
  return crypto.createHash('sha256').update(ip).digest('hex');
}

export function validateUtm(value: string): boolean {
  if (!value) return true;
  if (value.length > UTM_MAX_LENGTH) return false;
  return UTM_PATTERN.test(value);
}

export function validateFunnelEvent(eventType: string): eventType is FunnelEventType {
  return (ALLOWED_FUNNEL_EVENTS as readonly string[]).includes(eventType);
}

export function sanitizeUserAgent(ua: string | undefined): string | undefined {
  if (!ua) return undefined;
  return ua.substring(0, 255);
}
