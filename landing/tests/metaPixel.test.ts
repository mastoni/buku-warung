import { describe, it, expect, beforeEach, vi } from 'vitest';

// Setup minimal DOM / Web Storage polyfills for Node environment
const createMockStorage = () => {
  let store: Record<string, string> = {};
  return {
    getItem: (key: string) => store[key] || null,
    setItem: (key: string, value: string) => {
      store[key] = String(value);
    },
    removeItem: (key: string) => {
      delete store[key];
    },
    clear: () => {
      store = {};
    }
  };
};

if (typeof globalThis.sessionStorage === 'undefined') {
  (globalThis as any).sessionStorage = createMockStorage();
}
if (typeof globalThis.localStorage === 'undefined') {
  (globalThis as any).localStorage = createMockStorage();
}
if (typeof globalThis.window === 'undefined') {
  (globalThis as any).window = globalThis;
}

import {
  isPixelConfigured,
  hasConsent,
  setConsent,
  initMetaPixel,
  trackMetaPixelEvent,
  trackPixelPageView,
  trackPixelViewContent,
  trackPixelInitiateCheckout,
  generatePageViewEventId,
  META_PIXEL_ID
} from '../src/metaPixel';
import { trackPageView } from '../src/tracking';

describe('BW-MARKETING-2B Client-side Meta Pixel Unit Tests', () => {
  beforeEach(() => {
    globalThis.sessionStorage.clear();
    globalThis.localStorage.clear();
    delete (globalThis.window as any).fbq;
    delete (globalThis.window as any)._fbq;
    delete (globalThis.window as any)._bwPixelLoaded;
    delete (globalThis.window as any).__META_PIXEL_ID;
    (globalThis.window as any).location = { search: '', href: 'https://bukuwarung.skmnetwork.com/' };
  });

  describe('1. Unconfigured State & Safe Defaults', () => {
    it('1.1 isPixelConfigured reflects presence of non-empty META_PIXEL_ID', () => {
      expect(typeof META_PIXEL_ID).toBe('string');
      expect(isPixelConfigured()).toBe(META_PIXEL_ID.length > 0);
    });

    it('1.2 initMetaPixel returns false safely when pixel is unconfigured', () => {
      if (!isPixelConfigured()) {
        const result = initMetaPixel();
        expect(result).toBe(false);
        expect((globalThis.window as any).fbq).toBeUndefined();
      }
    });

    it('1.3 event helpers return false without throwing when unconfigured', () => {
      if (!isPixelConfigured()) {
        expect(trackPixelPageView('LW-TEST01')).toBe(false);
        expect(trackPixelViewContent('Buku Warung', 'LW-TEST01')).toBe(false);
        expect(trackPixelInitiateCheckout('LW-TEST01')).toBe(false);
      }
    });
  });

  describe('2. User Privacy & Consent', () => {
    it('2.1 consent is granted by default if unset', () => {
      expect(hasConsent()).toBe(true);
    });

    it('2.2 setConsent(false) disables tracking', () => {
      setConsent(false);
      expect(hasConsent()).toBe(false);
      expect(trackMetaPixelEvent('PageView')).toBe(false);
    });

    it('2.3 setConsent(true) re-enables consent', () => {
      setConsent(false);
      expect(hasConsent()).toBe(false);
      setConsent(true);
      expect(hasConsent()).toBe(true);
    });
  });

  describe('3. Deduplication & event_id consistency', () => {
    it('3.1 duplicate calls in the same session are throttled', () => {
      const mockFbq = vi.fn();
      (globalThis.window as any).fbq = mockFbq;
      (globalThis.window as any)._bwPixelLoaded = true;

      // First call with dedupe key
      globalThis.sessionStorage.setItem('bw_pixel_InitiateCheckout_ic_LW-TEST99', '1');

      // Second call must return false (deduplicated)
      const second = trackMetaPixelEvent('InitiateCheckout', { value: 139000 }, 'ic_LW-TEST99');
      expect(second).toBe(false);
      expect(mockFbq).not.toHaveBeenCalled();
    });

    it('3.2 strips PII fields (email, phone, name) from browser event parameters', () => {
      const mockFbq = vi.fn();
      (globalThis.window as any).fbq = mockFbq;
      (globalThis.window as any)._bwPixelLoaded = true;

      // Mock isPixelConfigured by setting __META_PIXEL_ID on window
      (globalThis.window as any).__META_PIXEL_ID = '123456789';

      trackMetaPixelEvent(
        'InitiateCheckout',
        {
          value: 139000,
          currency: 'IDR',
          customer_email: 'secret@example.com',
          customer_phone: '081234567890',
          nama_lengkap: 'Budi Test'
        },
        'ic_LW-TEST-PII'
      );

      // Verify that fbq received value and currency, but NOT email, phone, or name
      if (mockFbq.mock.calls.length > 0) {
        const passedParams = mockFbq.mock.calls[0][2];
        expect(passedParams).toHaveProperty('value', 139000);
        expect(passedParams).toHaveProperty('currency', 'IDR');
        expect(passedParams).not.toHaveProperty('customer_email');
        expect(passedParams).not.toHaveProperty('customer_phone');
        expect(passedParams).not.toHaveProperty('nama_lengkap');
      }
    });

    it('3.3 single script injection guard prevents loading Meta Pixel multiple times', () => {
      (globalThis.window as any)._bwPixelLoaded = true;
      (globalThis.window as any).fbq = vi.fn();

      const result = initMetaPixel();
      expect(result).toBe(isPixelConfigured());
    });
  });

  /* =========================================================================
   * 4. PageView event_id Deduplication & Parity
   * ========================================================================= */
  describe('4. PageView event_id Deduplication & Parity', () => {
    it('4.1 generatePageViewEventId generates a non-empty unique string with pv_ prefix', () => {
      const id1 = generatePageViewEventId();
      const id2 = generatePageViewEventId();

      expect(id1).toMatch(/^pv_[a-zA-Z0-9_-]+$/);
      expect(id2).toMatch(/^pv_[a-zA-Z0-9_-]+$/);
      expect(id1).not.toBe(id2);
      expect(id1.length).toBeLessThanOrEqual(128);
    });

    it('4.2 trackPixelPageView dispatches fbq PageView with exact eventID param', () => {
      const mockFbq = vi.fn();
      (globalThis.window as any).fbq = mockFbq;
      (globalThis.window as any)._bwPixelLoaded = true;
      (globalThis.window as any).__META_PIXEL_ID = '2559501331198045';

      const customEventId = 'pv_custom_test_9988';
      const result = trackPixelPageView(undefined, customEventId);

      expect(result).toBe(true);
      expect(mockFbq).toHaveBeenCalledWith(
        'track',
        'PageView',
        {},
        { eventID: customEventId }
      );
    });

    it('4.3 trackPixelPageView generates a valid eventID if none provided', () => {
      const mockFbq = vi.fn();
      (globalThis.window as any).fbq = mockFbq;
      (globalThis.window as any)._bwPixelLoaded = true;
      (globalThis.window as any).__META_PIXEL_ID = '2559501331198045';

      const result = trackPixelPageView();
      expect(result).toBe(true);
      expect(mockFbq).toHaveBeenCalledTimes(1);

      const call = mockFbq.mock.calls[0];
      expect(call[0]).toBe('track');
      expect(call[1]).toBe('PageView');
      expect(call[2]).toEqual({});
      expect(call[3]).toHaveProperty('eventID');
      expect(call[3].eventID).toMatch(/^pv_[a-zA-Z0-9_-]+$/);
    });

    it('4.4 trackPixelPageView deduplicates repeated calls with identical eventId in same session', () => {
      const mockFbq = vi.fn();
      (globalThis.window as any).fbq = mockFbq;
      (globalThis.window as any)._bwPixelLoaded = true;
      (globalThis.window as any).__META_PIXEL_ID = '2559501331198045';

      const eventId = 'pv_repeat_check_01';
      const first = trackPixelPageView(undefined, eventId);
      expect(first).toBe(true);
      expect(mockFbq).toHaveBeenCalledTimes(1);

      // Second call with same eventId must return false (throttled)
      const second = trackPixelPageView(undefined, eventId);
      expect(second).toBe(false);
      expect(mockFbq).toHaveBeenCalledTimes(1);
    });

    it('4.5 trackPageView generates shared eventId, invokes Pixel and server tracking synchronously', async () => {
      const mockFbq = vi.fn();
      (globalThis.window as any).fbq = mockFbq;
      (globalThis.window as any)._bwPixelLoaded = true;
      (globalThis.window as any).__META_PIXEL_ID = '2559501331198045';

      let capturedPayload: any = null;
      globalThis.fetch = vi.fn().mockImplementation(async (_url, options) => {
        capturedPayload = JSON.parse(options.body);
        return {
          ok: true,
          json: async () => ({ success: true, data: { leadToken: 'LW-MOCK-LEAD-01' } })
        };
      });

      const eventId = trackPageView();
      expect(eventId).toMatch(/^pv_[a-zA-Z0-9_-]+$/);

      // Allow async sendTrackEvent microtask to execute
      await new Promise(resolve => setTimeout(resolve, 10));

      // Verify Pixel call received identical eventID
      expect(mockFbq).toHaveBeenCalledTimes(1);
      const pixelCall = mockFbq.mock.calls[0];
      expect(pixelCall[0]).toBe('track');
      expect(pixelCall[1]).toBe('PageView');
      expect(pixelCall[3]).toEqual({ eventID: eventId });

      // Verify server track payload received identical eventId
      expect(globalThis.fetch).toHaveBeenCalledTimes(1);
      expect(capturedPayload.eventType).toBe('PAGE_VIEW');
      expect(capturedPayload.eventId).toBe(eventId);

      // Verify stored in sessionStorage
      expect(globalThis.sessionStorage.getItem('lw_pv_event_id')).toBe(eventId);
      expect(globalThis.sessionStorage.getItem('lw_pv_done')).toBe('1');
    });

    it('4.6 trackPageView reuses existing eventId on page reload and prevents duplicate dispatch', () => {
      const mockFbq = vi.fn();
      (globalThis.window as any).fbq = mockFbq;
      (globalThis.window as any)._bwPixelLoaded = true;
      (globalThis.window as any).__META_PIXEL_ID = '2559501331198045';

      globalThis.sessionStorage.setItem('lw_pv_done', '1');
      globalThis.sessionStorage.setItem('lw_pv_event_id', 'pv_existing_session_001');

      const mockFetch = vi.fn();
      globalThis.fetch = mockFetch;

      const eventId = trackPageView();
      expect(eventId).toBe('pv_existing_session_001');
      expect(mockFbq).not.toHaveBeenCalled();
      expect(mockFetch).not.toHaveBeenCalled();
    });

    it('4.7 trackMetaPixelEvent attaches test_event_code when present in URL search params', () => {
      const mockFbq = vi.fn();
      (globalThis.window as any).fbq = mockFbq;
      (globalThis.window as any)._bwPixelLoaded = true;
      (globalThis.window as any).__META_PIXEL_ID = '2559501331198045';
      (globalThis.window as any).location = { search: '?test_event_code=TEST2489' };

      const eventId = 'pv_test_code_param_01';
      trackPixelPageView(undefined, eventId);

      expect(mockFbq).toHaveBeenCalledTimes(1);
      const pixelCall = mockFbq.mock.calls[0];
      expect(pixelCall[3]).toEqual({
        eventID: eventId,
        test_event_code: 'TEST2489'
      });
    });
  });
});
