import { describe, it, expect, afterEach, vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import React from 'react';
import {
  PricingProvider,
  PublicPricingResponse,
  DEFAULT_PRICING_STATE,
} from '../src/hooks/usePricingPromo';
import { Hero } from '../src/components/Hero';
import { FinalCta } from '../src/components/FinalCta';
import { PricingSection } from '../src/components/PricingSection';
import { PromoCountdown } from '../src/components/PromoCountdown';
import { LANDING_CONFIG } from '../src/data/landingData';

const SERVER_NOW = 1740000000000;
const HOUR = 3600 * 1000;

function pricingResponse(overrides: Partial<NonNullable<PublicPricingResponse['data']>> = {}): PublicPricingResponse {
  return {
    success: true,
    data: {
      product: 'BUKU_WARUNG',
      isPromoActive: true,
      effectivePrice: 50000,
      normalPrice: 100000,
      promoPrice: 50000,
      currency: 'IDR',
      promoName: 'Promo Peluncuran',
      startsAt: SERVER_NOW - HOUR,
      expiresAt: SERVER_NOW + 72 * HOUR,
      timezone: 'Asia/Jakarta',
      showCountdown: true,
      serverTime: SERVER_NOW,
      ...overrides,
    },
  };
}

/**
 * Renders a component with the pricing provider pre-seeded from a mocked server response.
 * Using initialData means no fetch happens, so each state is deterministic and no live
 * pricing API is contacted.
 */
function renderWithPricing(
  Component: React.FC,
  data: PublicPricingResponse['data'] | undefined
): string {
  return renderToStaticMarkup(
    React.createElement(
      PricingProvider,
      { initialData: data, apiUrl: 'http://127.0.0.1:1/unused' },
      React.createElement(Component)
    )
  );
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('Promo state rendering follows the server-authoritative pricing response', () => {
  /* =========================================================================
   * A. PROMO ACTIVE
   * ========================================================================= */
  describe('A. Promo ACTIVE', () => {
    it('Hero shows the promo price, the struck normal price, and promo copy', () => {
      const html = renderWithPricing(Hero, pricingResponse().data);

      // Rp 50.000 displayed as the payable price
      expect(html).toContain('Rp 50.000');
      // Rp 100.000 displayed as a crossed-out reference
      expect(html).toContain('Rp 100.000');
      expect(html).toContain('line-through');
      // Promo copy
      expect(html).toContain('Selama Promo Peluncuran berlangsung');
      expect(html).toContain('sekali beli');
    });

    it('Hero renders the countdown when the server enables it', () => {
      const html = renderWithPricing(Hero, pricingResponse({ showCountdown: true }).data);
      expect(html).toContain('Berakhir Dalam');
    });

    it('PricingSection shows the promo price and struck normal price', () => {
      const html = renderWithPricing(PricingSection, pricingResponse().data);
      expect(html).toContain('Rp 50.000');
      expect(html).toContain('Rp 100.000');
      expect(html).toContain('line-through');
      expect(html).toContain('Promo Peluncuran');
    });

    it('FinalCta shows promo copy', () => {
      const html = renderWithPricing(FinalCta, pricingResponse().data);
      expect(html).toContain('Selama Promo Peluncuran berlangsung');
    });

    it('shows no "seumur hidup" / "selamanya" claim in any state while promo runs', () => {
      for (const C of [Hero, PricingSection, FinalCta]) {
        const html = renderWithPricing(C, pricingResponse().data);
        expect(html).not.toContain('seumur hidup');
        expect(html.toLowerCase()).not.toContain('selamanya');
      }
    });
  });

  /* =========================================================================
   * B. PROMO INACTIVE
   * ========================================================================= */
  describe('B. Promo INACTIVE', () => {
    const inactive = pricingResponse({
      isPromoActive: false,
      effectivePrice: 100000,
      normalPrice: 100000,
      promoPrice: 50000,
      promoName: 'Promo Peluncuran',
      showCountdown: false,
    }).data;

    it('Hero shows only the normal price with no strikethrough', () => {
      const html = renderWithPricing(Hero, inactive);

      expect(html).toContain('Rp 100.000');
      // The promo price must not be advertised
      expect(html).not.toContain('Rp 50.000');
      // No crossed-out reference when there is no discount
      expect(html).not.toContain('line-through');
    });

    it('Hero shows no countdown', () => {
      const html = renderWithPricing(Hero, inactive);
      expect(html).not.toContain('Berakhir Dalam');
      expect(html).not.toContain('Sisa Waktu');
    });

    it('Hero shows normal purchase copy and no promo wording', () => {
      const html = renderWithPricing(Hero, inactive);
      expect(html).toContain('Sekali beli untuk satu perangkat Android');
      expect(html).not.toContain('Selama');
      expect(html).not.toContain('promo');
    });

    it('PricingSection shows the normal price with no promo badge or countdown', () => {
      const html = renderWithPricing(PricingSection, inactive);

      expect(html).toContain('Rp 100.000');
      expect(html).not.toContain('Rp 50.000');
      expect(html).not.toContain('line-through');
      expect(html).not.toContain('Promo Peluncuran');
      expect(html).not.toContain('Sisa Waktu');
      // Falls back to the non-promo badge
      expect(html).toContain('Lisensi Komersial');
    });

    it('FinalCta shows normal purchase copy and no promo wording', () => {
      const html = renderWithPricing(FinalCta, inactive);
      expect(html).toContain('Sekali beli untuk satu perangkat Android');
      expect(html).not.toContain('Selama');
      expect(html).not.toContain('promo');
    });

    it('shows no "seumur hidup" / "selamanya" claim when promo is inactive', () => {
      for (const C of [Hero, PricingSection, FinalCta]) {
        const html = renderWithPricing(C, inactive);
        expect(html).not.toContain('seumur hidup');
        expect(html.toLowerCase()).not.toContain('selamanya');
      }
    });
  });

  /* =========================================================================
   * C. LOADING / API FAILURE - no unverified promo claim
   * ========================================================================= */
  describe('C. Loading and API failure', () => {
    it('default state claims no promo before the server answers', () => {
      // This is the safe presentation used while loading and after a failed request.
      expect(DEFAULT_PRICING_STATE.isPromoActive).toBe(false);
      expect(DEFAULT_PRICING_STATE.showCountdown).toBe(false);
      expect(DEFAULT_PRICING_STATE.effectivePriceFormatted).toBe(
        DEFAULT_PRICING_STATE.normalPriceFormatted
      );
      expect(DEFAULT_PRICING_STATE.savingsPercent).toBe(0);
      expect(DEFAULT_PRICING_STATE.discountBadge).toBe('Pembelian Sekali');
    });

    it('renders no promo claim when the pricing API fails', () => {
      // undefined initialData => no server verdict => no promo is advertised.
      for (const C of [Hero, PricingSection, FinalCta]) {
        const html = renderWithPricing(C, undefined);
        expect(html).not.toContain('Selama');
        expect(html).not.toContain('Berakhir Dalam');
        expect(html).not.toContain('Promo Peluncuran');
        expect(html).not.toContain('seumur hidup');
        // Falls back to the normal price rather than a promo price
        expect(html).toContain('Rp 100.000');
        expect(html).not.toContain('Rp 50.000');
      }
    });

    it('PromoCountdown renders nothing when the promo is inactive', () => {
      const inactive = pricingResponse({ isPromoActive: false, showCountdown: false }).data;
      const html = renderWithPricing(PromoCountdown, inactive);
      expect(html).toBe('');
    });
  });

  /* =========================================================================
   * D. CTA and attribution remain intact
   * ========================================================================= */
  describe('D. CTA and attribution', () => {
    it('Hero keeps the Public Order CTA in both promo states', () => {
      const active = renderWithPricing(Hero, pricingResponse().data);
      const inactive = renderWithPricing(
        Hero,
        pricingResponse({ isPromoActive: false, effectivePrice: 100000, normalPrice: 100000 }).data
      );

      expect(active).toContain('license.skmnetwork.com/beli/buku-warung');
      expect(inactive).toContain('license.skmnetwork.com/beli/buku-warung');
    });

    it('keeps the Test Dulu and direct-buy paths distinct', () => {
      const html = renderWithPricing(Hero, pricingResponse().data);
      // Compare the order URL with HTML entity escaping applied (& renders as &amp;).
      const expected = LANDING_CONFIG.publicOrderUrl.replace(/&/g, '&amp;');
      expect(html).toContain(expected);
      expect(html).toContain('DAFTAR TEST DULU');
      expect(html).toContain('BELI LANGSUNG');
    });

    it('PricingSection order CTA still points at Public Order', () => {
      const html = renderWithPricing(PricingSection, pricingResponse().data);
      expect(html).toContain('license.skmnetwork.com/beli/buku-warung');
    });
  });
});
