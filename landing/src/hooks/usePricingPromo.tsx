import React, { createContext, useContext, useState, useEffect, useCallback, useRef } from 'react';

export interface PublicPricingResponse {
  success: boolean;
  data?: {
    product: string;
    isPromoActive: boolean;
    effectivePrice: number;
    normalPrice: number;
    promoPrice: number;
    currency: string;
    promoName: string;
    startsAt: number;
    expiresAt: number;
    timezone: string;
    showCountdown: boolean;
    serverTime: number;
  };
  error?: {
    code: string;
    message: string;
  };
}

export interface CountdownData {
  days: number;
  hours: number;
  minutes: number;
  seconds: number;
  isExpired: boolean;
  totalRemainingMs: number;
  formatted: string;
}

export interface PricingPromoState {
  isLoading: boolean;
  isPromoActive: boolean;
  effectivePrice: number;
  normalPrice: number;
  promoPrice: number;
  currency: string;
  promoName: string;
  startsAt: number;
  expiresAt: number;
  timezone: string;
  showCountdown: boolean;
  effectivePriceFormatted: string;
  normalPriceFormatted: string;
  promoPriceFormatted: string;
  savingsFormatted: string;
  savingsPercent: number;
  discountBadge: string;
  countdown: CountdownData;
  error: string | null;
  refetch: () => Promise<void>;
}

export function formatRupiah(num: number): string {
  return 'Rp ' + Number(num || 0).toLocaleString('id-ID');
}

export function calculateCountdown(expiresAt: number, currentServerTime: number): CountdownData {
  const remainingMs = Math.max(0, expiresAt - currentServerTime);
  const isExpired = remainingMs <= 0;

  const days = Math.floor(remainingMs / (1000 * 60 * 60 * 24));
  const hours = Math.floor((remainingMs / (1000 * 60 * 60)) % 24);
  const minutes = Math.floor((remainingMs / (1000 * 60)) % 60);
  const seconds = Math.floor((remainingMs / 1000) % 60);

  const pad = (n: number) => String(n).padStart(2, '0');
  const formatted = `${days > 0 ? days + 'h ' : ''}${pad(hours)}:${pad(minutes)}:${pad(seconds)}`;

  return {
    days,
    hours,
    minutes,
    seconds,
    isExpired,
    totalRemainingMs: remainingMs,
    formatted,
  };
}

const DEFAULT_PRICING_STATE: PricingPromoState = {
  isLoading: false,
  isPromoActive: true,
  effectivePrice: 50000,
  normalPrice: 100000,
  promoPrice: 50000,
  currency: 'IDR',
  promoName: 'Promo Peluncuran',
  startsAt: 0,
  expiresAt: 0,
  timezone: 'Asia/Jakarta',
  showCountdown: false, // Never show fake countdown if unverified
  effectivePriceFormatted: 'Rp 50.000',
  normalPriceFormatted: 'Rp 100.000',
  promoPriceFormatted: 'Rp 50.000',
  savingsFormatted: 'Rp 50.000',
  savingsPercent: 50,
  discountBadge: 'Hemat 50% — Sekali Beli Seumur Hidup',
  countdown: {
    days: 0,
    hours: 0,
    minutes: 0,
    seconds: 0,
    isExpired: false,
    totalRemainingMs: 0,
    formatted: '00:00:00',
  },
  error: null,
  refetch: async () => {},
};

export const PricingContext = createContext<PricingPromoState>(DEFAULT_PRICING_STATE);

export interface PricingProviderProps {
  children: React.ReactNode;
  apiUrl?: string;
  initialData?: PublicPricingResponse['data'];
}

const DEFAULT_API_ENDPOINT = 'https://license.skmnetwork.com/v1/public/pricing?product=BUKU_WARUNG';

export const PricingProvider: React.FC<PricingProviderProps> = ({
  children,
  apiUrl = DEFAULT_API_ENDPOINT,
  initialData,
}) => {
  const [isLoading, setIsLoading] = useState<boolean>(!initialData);
  const [error, setError] = useState<string | null>(null);

  // Raw API snapshot
  const [pricingData, setPricingData] = useState<PublicPricingResponse['data'] | undefined>(initialData);

  // Skew offset: serverTime - clientLocalTime
  const serverOffsetRef = useRef<number>(0);
  const expiredRefetchTriggeredRef = useRef<boolean>(false);

  // Countdown state
  const [countdown, setCountdown] = useState<CountdownData>({
    days: 0,
    hours: 0,
    minutes: 0,
    seconds: 0,
    isExpired: false,
    totalRemainingMs: 0,
    formatted: '00:00:00',
  });

  const fetchPricing = useCallback(async () => {
    try {
      setIsLoading(true);
      setError(null);
      const res = await fetch(apiUrl, {
        method: 'GET',
        headers: { Accept: 'application/json' },
      });

      if (!res.ok) {
        throw new Error(`HTTP_${res.status}`);
      }

      const json: PublicPricingResponse = await res.json();
      if (json.success && json.data) {
        setPricingData(json.data);
        serverOffsetRef.current = json.data.serverTime - Date.now();
        expiredRefetchTriggeredRef.current = false;
      } else {
        throw new Error(json.error?.message || 'Failed to parse pricing response');
      }
    } catch (err: any) {
      setError(err?.message || 'Network error fetching pricing');
      // If error occurs, preserve safe fallback presentation
    } finally {
      setIsLoading(false);
    }
  }, [apiUrl]);

  useEffect(() => {
    if (!initialData) {
      fetchPricing();
    } else {
      serverOffsetRef.current = initialData.serverTime - Date.now();
    }
  }, [fetchPricing, initialData]);

  // Real-time Countdown timer loop with server time compensation
  useEffect(() => {
    if (!pricingData || !pricingData.isPromoActive || !pricingData.showCountdown || !pricingData.expiresAt) {
      setCountdown({
        days: 0,
        hours: 0,
        minutes: 0,
        seconds: 0,
        isExpired: false,
        totalRemainingMs: 0,
        formatted: '00:00:00',
      });
      return;
    }

    const updateTimer = () => {
      const currentCompensatedServerTime = Date.now() + serverOffsetRef.current;
      const cd = calculateCountdown(pricingData.expiresAt, currentCompensatedServerTime);
      setCountdown(cd);

      // When countdown reaches 0 at exact expiration, re-fetch authoritative pricing once
      if (cd.isExpired && !expiredRefetchTriggeredRef.current) {
        expiredRefetchTriggeredRef.current = true;
        fetchPricing();
      }
    };

    updateTimer();
    const intervalId = setInterval(updateTimer, 1000);
    return () => clearInterval(intervalId);
  }, [pricingData, fetchPricing]);

  // Derived state calculations
  const effectivePrice = pricingData ? pricingData.effectivePrice : 50000;
  const normalPrice = pricingData ? pricingData.normalPrice : 100000;
  const promoPrice = pricingData ? pricingData.promoPrice : 50000;
  const isPromoActive = pricingData ? pricingData.isPromoActive && !countdown.isExpired : false;
  const showCountdown = pricingData ? pricingData.showCountdown && isPromoActive && !countdown.isExpired : false;
  const promoName = pricingData?.promoName || 'Promo Peluncuran';
  const currency = pricingData?.currency || 'IDR';
  const startsAt = pricingData?.startsAt || 0;
  const expiresAt = pricingData?.expiresAt || 0;
  const timezone = pricingData?.timezone || 'Asia/Jakarta';

  const savings = Math.max(0, normalPrice - effectivePrice);
  const savingsPercent = normalPrice > 0 && savings > 0 ? Math.round((savings / normalPrice) * 100) : 0;
  const discountBadge = isPromoActive && savingsPercent > 0
    ? `Hemat ${savingsPercent}% — Sekali Beli Seumur Hidup`
    : 'Sekali Beli Seumur Hidup';

  const stateValue: PricingPromoState = {
    isLoading,
    isPromoActive,
    effectivePrice,
    normalPrice,
    promoPrice,
    currency,
    promoName,
    startsAt,
    expiresAt,
    timezone,
    showCountdown,
    effectivePriceFormatted: formatRupiah(effectivePrice),
    normalPriceFormatted: formatRupiah(normalPrice),
    promoPriceFormatted: formatRupiah(promoPrice),
    savingsFormatted: formatRupiah(savings),
    savingsPercent,
    discountBadge,
    countdown,
    error,
    refetch: fetchPricing,
  };

  return <PricingContext.Provider value={stateValue}>{children}</PricingContext.Provider>;
};

export const usePricing = (): PricingPromoState => {
  return useContext(PricingContext);
};
