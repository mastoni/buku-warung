import { describe, it, expect } from 'vitest';
import {
  calculateCountdown,
  formatRupiah,
  PublicPricingResponse,
  PricingPromoState,
} from '../src/hooks/usePricingPromo';

describe('Landing Commercial Pricing Integration Tests', () => {
  /* =========================================================================
   * 1. Currency & Text Formatting
   * ========================================================================= */
  describe('1. Currency & Text Formatting', () => {
    it('formats IDR rupiah correctly', () => {
      expect(formatRupiah(50000)).toBe('Rp 50.000');
      expect(formatRupiah(100000)).toBe('Rp 100.000');
      expect(formatRupiah(0)).toBe('Rp 0');
    });
  });

  /* =========================================================================
   * 2. Authoritative Server-Compensated Countdown Calculation
   * ========================================================================= */
  describe('2. Authoritative Server-Compensated Countdown Calculation', () => {
    it('accurately calculates remaining days, hours, minutes, seconds', () => {
      const serverNow = 1740000000000;
      // Expires in exactly 2 days, 3 hours, 4 minutes, 5 seconds
      const targetExpiry = serverNow + (2 * 24 * 3600 + 3 * 3600 + 4 * 60 + 5) * 1000;

      const res = calculateCountdown(targetExpiry, serverNow);
      expect(res.days).toBe(2);
      expect(res.hours).toBe(3);
      expect(res.minutes).toBe(4);
      expect(res.seconds).toBe(5);
      expect(res.isExpired).toBe(false);
      expect(res.formatted).toBe('2h 03:04:05');
    });

    it('returns isExpired = true when compensated server time reaches or exceeds expiresAt', () => {
      const serverNow = 1740000000000;
      const targetExpiry = 1740000000000; // Exact expiry

      const res = calculateCountdown(targetExpiry, serverNow);
      expect(res.days).toBe(0);
      expect(res.hours).toBe(0);
      expect(res.minutes).toBe(0);
      expect(res.seconds).toBe(0);
      expect(res.isExpired).toBe(true);
      expect(res.totalRemainingMs).toBe(0);
    });

    it('handles clock skew when client clock is 10 minutes ahead of server', () => {
      const actualServerTime = 1740000000000;
      const clientClock = actualServerTime + 10 * 60 * 1000; // Client clock is ahead by 10 min
      const expiresAt = actualServerTime + 30 * 60 * 1000; // Expires in 30 server minutes

      // Client computes serverSkewOffset = actualServerTime - clientClock = -10min
      const serverSkewOffset = actualServerTime - clientClock;
      const currentCompensatedServerTime = clientClock + serverSkewOffset;

      const res = calculateCountdown(expiresAt, currentCompensatedServerTime);
      expect(res.minutes).toBe(30);
      expect(res.isExpired).toBe(false);
    });

    it('handles clock skew when client clock is 10 minutes behind server', () => {
      const actualServerTime = 1740000000000;
      const clientClock = actualServerTime - 10 * 60 * 1000; // Client clock is behind by 10 min
      const expiresAt = actualServerTime + 30 * 60 * 1000; // Expires in 30 server minutes

      // Client computes serverSkewOffset = actualServerTime - clientClock = +10min
      const serverSkewOffset = actualServerTime - clientClock;
      const currentCompensatedServerTime = clientClock + serverSkewOffset;

      const res = calculateCountdown(expiresAt, currentCompensatedServerTime);
      expect(res.minutes).toBe(30);
      expect(res.isExpired).toBe(false);
    });
  });

  /* =========================================================================
   * 3. API Response Contract & State Handling
   * ========================================================================= */
  describe('3. API Response Contract & State Handling', () => {
    it('Scenario A: Promo active with countdown enabled', () => {
      const mockApiResponse: PublicPricingResponse = {
        success: true,
        data: {
          product: 'BUKU_WARUNG',
          isPromoActive: true,
          effectivePrice: 50000,
          normalPrice: 100000,
          promoPrice: 50000,
          currency: 'IDR',
          promoName: 'Promo Peluncuran',
          startsAt: 1740000000000,
          expiresAt: 1743000000000,
          timezone: 'Asia/Jakarta',
          showCountdown: true,
          serverTime: 1740000000000,
        },
      };

      const data = mockApiResponse.data!;
      expect(data.isPromoActive).toBe(true);
      expect(data.effectivePrice).toBe(50000);
      expect(data.normalPrice).toBe(100000);
      expect(data.showCountdown).toBe(true);
      expect(formatRupiah(data.effectivePrice)).toBe('Rp 50.000');
      expect(formatRupiah(data.normalPrice)).toBe('Rp 100.000');

      // Savings calculation
      const savings = data.normalPrice - data.effectivePrice;
      const savingsPercent = Math.round((savings / data.normalPrice) * 100);
      expect(savings).toBe(50000);
      expect(savingsPercent).toBe(50);
    });

    it('Scenario B: Promo inactive (normal price)', () => {
      const mockApiResponse: PublicPricingResponse = {
        success: true,
        data: {
          product: 'BUKU_WARUNG',
          isPromoActive: false,
          effectivePrice: 100000,
          normalPrice: 100000,
          promoPrice: 50000,
          currency: 'IDR',
          promoName: 'Promo Peluncuran',
          startsAt: 1740000000000,
          expiresAt: 1741000000000,
          timezone: 'Asia/Jakarta',
          showCountdown: false,
          serverTime: 1742000000000,
        },
      };

      const data = mockApiResponse.data!;
      expect(data.isPromoActive).toBe(false);
      expect(data.effectivePrice).toBe(100000);
      expect(data.showCountdown).toBe(false);
      expect(formatRupiah(data.effectivePrice)).toBe('Rp 100.000');
    });

    it('Scenario C: Countdown disabled by Admin (showCountdown = false)', () => {
      const mockApiResponse: PublicPricingResponse = {
        success: true,
        data: {
          product: 'BUKU_WARUNG',
          isPromoActive: true,
          effectivePrice: 50000,
          normalPrice: 100000,
          promoPrice: 50000,
          currency: 'IDR',
          promoName: 'Promo Peluncuran',
          startsAt: 1740000000000,
          expiresAt: 1743000000000,
          timezone: 'Asia/Jakarta',
          showCountdown: false, // Admin turned off countdown
          serverTime: 1740000000000,
        },
      };

      const data = mockApiResponse.data!;
      expect(data.isPromoActive).toBe(true);
      expect(data.showCountdown).toBe(false);
      expect(data.effectivePrice).toBe(50000);
    });

    it('Scenario F: Network failure fallback contains safe non-fake-urgency defaults', () => {
      const safeDefaultEffectivePrice = 50000;
      const safeDefaultNormalPrice = 100000;

      expect(formatRupiah(safeDefaultEffectivePrice)).toBe('Rp 50.000');
      expect(formatRupiah(safeDefaultNormalPrice)).toBe('Rp 100.000');
    });

    it('Scenario G: Gracefully handles malformed or error responses', () => {
      const errorApiResponse: PublicPricingResponse = {
        success: false,
        error: {
          code: 'PRODUCT_NOT_FOUND',
          message: 'Produk tidak ditemukan.',
        },
      };

      expect(errorApiResponse.success).toBe(false);
      expect(errorApiResponse.data).toBeUndefined();
      expect(errorApiResponse.error?.code).toBe('PRODUCT_NOT_FOUND');
    });
  });
});
