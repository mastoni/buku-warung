/**
 * Meta Pixel Integration Module (Client-Side)
 *
 * Implements privacy-compliant, deduplicated Meta Pixel tracking for Buku Warung.
 * Adheres strictly to the following requirements:
 * 1. Safe default: completely inert and disabled when META_PIXEL_ID is not configured.
 * 2. Script injected once asynchronously; no multiple loads.
 * 3. Privacy & UU PDP compliance: zero PII (email, phone, name) sent via browser events.
 * 4. Deduplication: events within the same session / eventId are throttled to prevent inflation.
 * 5. event_id mapping matching backend CAPI (e.g. 'pv_' + token, 'ic_' + token).
 */

declare global {
  interface Window {
    fbq?: ((...args: unknown[]) => void) & {
      callMethod?: (...args: unknown[]) => void;
      queue?: unknown[];
      push?: (...args: unknown[]) => void;
      loaded?: boolean;
      version?: string;
    };
    _fbq?: unknown;
    _bwPixelLoaded?: boolean;
    __META_PIXEL_ID?: string;
  }

  interface ImportMeta {
    readonly env: Record<string, string | undefined>;
  }
}

export const META_PIXEL_ID: string = (
  (import.meta.env.META_PIXEL_ID as string) ||
  (import.meta.env.VITE_META_PIXEL_ID as string) ||
  (typeof window !== 'undefined' && window.__META_PIXEL_ID) ||
  ''
).trim();

const PIXEL_DEDUPE_PREFIX = 'bw_pixel_';
const CONSENT_STORAGE_KEY = 'bw_cookie_consent';

/**
 * Returns true if a non-empty Pixel ID is configured in the environment.
 */
export function isPixelConfigured(): boolean {
  return META_PIXEL_ID.length > 0;
}

/**
 * Checks user consent for analytical/marketing tracking.
 * Default is granted unless explicitly denied by user.
 */
export function hasConsent(): boolean {
  try {
    return localStorage.getItem(CONSENT_STORAGE_KEY) !== 'denied';
  } catch {
    return true;
  }
}

/**
 * Sets user tracking consent status.
 */
export function setConsent(granted: boolean): void {
  try {
    localStorage.setItem(CONSENT_STORAGE_KEY, granted ? 'granted' : 'denied');
  } catch {
    // ignore local storage restrictions
  }
}

/**
 * Safely initializes the Meta Pixel library once.
 * Returns true if initialized or already active, false if disabled/unconfigured.
 */
export function initMetaPixel(): boolean {
  if (typeof window === 'undefined') return false;
  if (!isPixelConfigured()) return false;
  if (!hasConsent()) return false;

  if (window._bwPixelLoaded && window.fbq) {
    return true;
  }

  try {
    (function (f: Window, b: Document, e: string, v: string) {
      if (f.fbq) return;
      const n = function (...args: unknown[]) {
        if (n.callMethod) {
          n.callMethod.apply(n, args);
        } else {
          n.queue?.push(args);
        }
      } as NonNullable<Window['fbq']>;
      if (!f._fbq) f._fbq = n;
      n.push = n;
      n.loaded = true;
      n.version = '2.0';
      n.queue = [];
      const t = b.createElement(e) as HTMLScriptElement;
      t.async = true;
      t.src = v;
      const s = b.getElementsByTagName(e)[0];
      s?.parentNode?.insertBefore(t, s);
      f.fbq = n;
    })(window, document, 'script', 'https://connect.facebook.net/en_US/fbevents.js');

    if (window.fbq) {
      window.fbq('init', META_PIXEL_ID);
      window._bwPixelLoaded = true;
      return true;
    }
  } catch {
    // Fail silently without breaking the landing page
  }

  return false;
}

/**
 * Strips any potential PII fields from client event parameters.
 */
function sanitizeEventParams(params?: Record<string, unknown>): Record<string, unknown> {
  if (!params) return {};
  const clean: Record<string, unknown> = {};
  const forbiddenPatterns = /email|phone|telepon|hp|wa|whatsapp|contact|kontak|customer|name|nama|address|alamat/i;

  for (const [k, v] of Object.entries(params)) {
    if (!forbiddenPatterns.test(k) && typeof v !== 'object') {
      clean[k] = v;
    }
  }
  return clean;
}

/**
 * Tracks a standard Meta event with deduplication and PII protection.
 */
export function trackMetaPixelEvent(
  eventName: 'PageView' | 'ViewContent' | 'InitiateCheckout',
  params?: Record<string, unknown>,
  eventId?: string
): boolean {
  if (!isPixelConfigured() || !hasConsent()) return false;

  const dedupeKey = `${PIXEL_DEDUPE_PREFIX}${eventName}_${eventId || 'single'}`;
  try {
    if (sessionStorage.getItem(dedupeKey)) {
      return false; // Deduplicated
    }
    sessionStorage.setItem(dedupeKey, '1');
  } catch {
    // proceed if session storage unavailable
  }

  const ready = initMetaPixel();
  if (!ready || !window.fbq) return false;

  const safeParams = sanitizeEventParams(params);
  try {
    const options: Record<string, unknown> = {};
    if (eventId) {
      options.eventID = eventId;
    }
    if (typeof window !== 'undefined' && window.location?.search) {
      try {
        const searchParams = new URLSearchParams(window.location.search);
        const testCode = searchParams.get('test_event_code');
        if (testCode) {
          options.test_event_code = testCode;
        }
      } catch {
        // Ignore URL parsing errors
      }
    }

    if (Object.keys(options).length > 0) {
      window.fbq('track', eventName, safeParams, options);
    } else {
      window.fbq('track', eventName, safeParams);
    }
    return true;
  } catch {
    return false;
  }
}

/**
 * Generates a unique, non-repeating event_id for PageView visits.
 * Complies with Meta Conversions API requirements: alphanumeric, max 128 chars.
 */
export function generatePageViewEventId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return `pv_${crypto.randomUUID()}`;
  }
  const rand = Math.random().toString(36).substring(2, 10);
  return `pv_${Date.now()}_${rand}`;
}

/**
 * Mapped Event: PageView
 * Dispatches PageView with a unique eventID for browser-server deduplication.
 */
export function trackPixelPageView(leadToken?: string, eventId?: string): boolean {
  const resolvedEventId = eventId || (leadToken ? `pv_${leadToken}` : generatePageViewEventId());
  return trackMetaPixelEvent('PageView', {}, resolvedEventId);
}

/**
 * Mapped Event: ViewContent
 */
export function trackPixelViewContent(contentName: string = 'Buku Warung', leadToken?: string): boolean {
  const eventId = leadToken ? `vc_${leadToken}` : undefined;
  return trackMetaPixelEvent(
    'ViewContent',
    {
      content_name: contentName,
      currency: 'IDR',
      value: 139000
    },
    eventId
  );
}

/**
 * Mapped Event: InitiateCheckout
 */
export function trackPixelInitiateCheckout(leadToken?: string): boolean {
  const eventId = leadToken ? `ic_${leadToken}` : undefined;
  return trackMetaPixelEvent(
    'InitiateCheckout',
    {
      content_name: 'Buku Warung',
      currency: 'IDR',
      value: 139000
    },
    eventId
  );
}
