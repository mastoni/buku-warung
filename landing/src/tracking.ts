const TRACKING_ENDPOINT = 'https://license.skmnetwork.com/v1/landing/track';

const LEAD_TOKEN_KEY = 'lw_lead_token';
const UTM_KEY = 'lw_utm';
const PAGE_VIEWED_KEY = 'lw_pv_done';
const VIEW_PRODUCT_KEY = 'lw_vp_done';
const VIEW_PRICE_KEY = 'lw_vpr_done';
const CTA_THROTTLE_PREFIX = 'lw_cta_';

const UTM_PARAM_NAMES = ['utm_source', 'utm_medium', 'utm_campaign', 'utm_content'] as const;

function getUtmFromUrl(): Record<string, string> {
  const params = new URLSearchParams(window.location.search);
  const utm: Record<string, string> = {};
  for (const param of UTM_PARAM_NAMES) {
    const value = params.get(param);
    if (value) {
      utm[param] = value;
    }
  }
  return utm;
}

function getUtmParams(): Record<string, string> {
  const stored = sessionStorage.getItem(UTM_KEY);
  if (stored) {
    try {
      return JSON.parse(stored);
    } catch {
      // fall through to URL capture
    }
  }
  const utm = getUtmFromUrl();
  if (Object.keys(utm).length > 0) {
    sessionStorage.setItem(UTM_KEY, JSON.stringify(utm));
  }
  return utm;
}

async function sendTrackEvent(
  eventType: string,
  extraPayload?: Record<string, string>
): Promise<void> {
  try {
    const leadToken = sessionStorage.getItem(LEAD_TOKEN_KEY) || undefined;
    const utm = getUtmParams();

    const payload: Record<string, string | undefined> = {
      eventType,
      leadToken,
      utm_source: utm.utm_source,
      utm_medium: utm.utm_medium,
      utm_campaign: utm.utm_campaign,
      utm_content: utm.utm_content,
    };

    if (extraPayload) {
      for (const [key, value] of Object.entries(extraPayload)) {
        if (
          key === 'utm_source' ||
          key === 'utm_medium' ||
          key === 'utm_campaign' ||
          key === 'utm_content'
        ) {
          payload[key] = value;
        }
      }
    }

    const response = await fetch(TRACKING_ENDPOINT, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });

    if (response.ok) {
      const result = (await response.json()) as { success: boolean; data?: { leadToken?: string } };
      if (result.success && result.data?.leadToken) {
        sessionStorage.setItem(LEAD_TOKEN_KEY, result.data.leadToken);
      }
    }
  } catch {
    // Tracking must never break the user experience
  }
}

export function trackPageView(): void {
  if (sessionStorage.getItem(PAGE_VIEWED_KEY)) return;
  sessionStorage.setItem(PAGE_VIEWED_KEY, '1');
  sendTrackEvent('PAGE_VIEW');
}

export function trackViewProduct(): void {
  if (sessionStorage.getItem(VIEW_PRODUCT_KEY)) return;
  sessionStorage.setItem(VIEW_PRODUCT_KEY, '1');
  sendTrackEvent('VIEW_PRODUCT');
}

export function trackViewPrice(): void {
  if (sessionStorage.getItem(VIEW_PRICE_KEY)) return;
  sessionStorage.setItem(VIEW_PRICE_KEY, '1');
  sendTrackEvent('VIEW_PRICE');
}

export function trackWhatsAppClick(ctaLocation: string): void {
  const throttleKey = CTA_THROTTLE_PREFIX + 'WHATSAPP_' + ctaLocation;
  const now = Date.now();
  const last = sessionStorage.getItem(throttleKey);
  if (last && now - parseInt(last, 10) < 1000) return;
  sessionStorage.setItem(throttleKey, now.toString());
  sendTrackEvent('CLICK_WHATSAPP');
}

export function trackBuyClick(ctaLocation: string): void {
  const throttleKey = CTA_THROTTLE_PREFIX + 'BUY_' + ctaLocation;
  const now = Date.now();
  const last = sessionStorage.getItem(throttleKey);
  if (last && now - parseInt(last, 10) < 1000) return;
  sessionStorage.setItem(throttleKey, now.toString());
  sendTrackEvent('CLICK_BUY');
}
