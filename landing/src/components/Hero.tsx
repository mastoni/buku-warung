import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  Download,
  MessageCircle,
  CheckCircle2,
  ShieldCheck,
  Zap,
  Beaker,
  ShoppingBag,
  Cloud,
  ChevronLeft,
  ChevronRight
} from 'lucide-react';
import { HERO_CAROUSEL_SLIDES, LANDING_CONFIG, TEST_CAMPAIGN_CONFIG } from '../data/landingData';
import { usePricing } from '../hooks/usePricingPromo';
import { PromoCountdown } from './PromoCountdown';
import {
  trackBuyClick,
  trackDownloadClick,
  trackTestRegistrationStarted,
  trackWhatsAppClick
} from '../tracking';
import { getOrderUrl } from '../tracking';

const AUTOPLAY_MS = 7000;
const SWIPE_THRESHOLD_PX = 45;

// Intrinsic size of every hero banner (1672x941). Declaring it on the image lets the browser reserve
// the exact box before the artwork loads, so swapping slides never shifts the layout.
const ARTWORK_WIDTH = 1672;
const ARTWORK_HEIGHT = 941;

const TRUST_BADGES: Array<{ icon: React.ReactNode; label: string }> = [
  { icon: <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />, label: 'Sekali Beli, Tanpa Langganan Bulanan' },
  { icon: <Cloud className="w-4 h-4 text-emerald-600 shrink-0" />, label: 'Backup Manual ke Google Sheets' },
  { icon: <ShieldCheck className="w-4 h-4 text-emerald-600 shrink-0" />, label: 'Data Usaha Utama di HP Anda' },
  { icon: <Zap className="w-4 h-4 text-emerald-600 shrink-0" />, label: 'Offline-First, Tanpa Kuota Jualan' }
];

export const Hero: React.FC = () => {
  const { isPromoActive, effectivePriceFormatted, normalPriceFormatted, showCountdown, promoName } =
    usePricing();

  const slideCount = HERO_CAROUSEL_SLIDES.length;
  const [activeIndex, setActiveIndex] = useState(0);
  const [autoplayPaused, setAutoplayPaused] = useState(false);
  const touchStartX = useRef<number | null>(null);

  const selectSlide = useCallback(
    (next: number) => {
      setActiveIndex(((next % slideCount) + slideCount) % slideCount);
    },
    [slideCount]
  );

  useEffect(() => {
    if (autoplayPaused || typeof window === 'undefined') return;
    if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) return;
    const timer = window.setInterval(() => {
      setActiveIndex(current => (current + 1) % slideCount);
    }, AUTOPLAY_MS);
    return () => window.clearInterval(timer);
  }, [autoplayPaused, activeIndex, slideCount]);

  const handleTouchStart = (event: React.TouchEvent<HTMLDivElement>) => {
    touchStartX.current = event.touches[0]?.clientX ?? null;
  };

  const handleTouchEnd = (event: React.TouchEvent<HTMLDivElement>) => {
    const startX = touchStartX.current;
    touchStartX.current = null;
    if (startX === null) return;
    const endX = event.changedTouches[0]?.clientX ?? startX;
    const delta = endX - startX;
    if (Math.abs(delta) < SWIPE_THRESHOLD_PX) return;
    selectSlide(activeIndex + (delta < 0 ? 1 : -1));
  };

  return (
    <section id="beranda" className="pt-24 md:pt-28 pb-14 md:pb-20 relative">
      <div className="absolute inset-0 overflow-hidden pointer-events-none -z-10" aria-hidden="true">
        <div className="absolute top-0 left-1/2 -translate-x-1/2 w-full max-w-7xl h-[420px] bg-radial from-emerald-100/60 via-emerald-50/20 to-transparent" />
      </div>

      <div className="max-w-6xl mx-auto px-4 sm:px-6">
        {/* The banner is the hero message, so no headline sits above it. This keeps the document
            outline intact for assistive tech and search engines without repeating the artwork. */}
        <h1 className="sr-only">{LANDING_CONFIG.subheadline}</h1>

        <div
          onMouseEnter={() => setAutoplayPaused(true)}
          onMouseLeave={() => setAutoplayPaused(false)}
          onFocus={() => setAutoplayPaused(true)}
          onBlur={() => setAutoplayPaused(false)}
          onTouchStart={handleTouchStart}
          onTouchEnd={handleTouchEnd}
        >
          {/* `overflow-hidden` below only keeps the neighbouring slides outside the viewport. It does
              not crop the artwork: each banner is never wider than the viewport itself. */}
          <div className="overflow-hidden">
            <div
              className="flex"
              style={{
                transform: `translateX(-${activeIndex * 100}%)`,
                transition: 'transform 500ms cubic-bezier(0.16, 1, 0.3, 1)'
              }}
            >
              {HERO_CAROUSEL_SLIDES.map((item, itemIndex) => (
                <div key={item.id} className="w-full shrink-0" aria-hidden={itemIndex !== activeIndex}>
                  <img
                    src={item.artworkSrc}
                    alt={item.artworkAlt}
                    width={ARTWORK_WIDTH}
                    height={ARTWORK_HEIGHT}
                    className="block w-full h-auto"
                    loading={itemIndex === 0 ? 'eager' : 'lazy'}
                  />
                </div>
              ))}
            </div>
          </div>

          <div className="mt-4 flex items-center justify-center gap-3">
            <button
              type="button"
              onClick={() => selectSlide(activeIndex - 1)}
              aria-label="Tampilkan jenis usaha sebelumnya"
              className="hidden sm:flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-white text-slate-700 border border-slate-300 hover:text-emerald-700 hover:border-emerald-400 transition-colors"
            >
              <ChevronLeft className="w-5 h-5" />
            </button>

            <div className="flex flex-wrap justify-center gap-2">
              {HERO_CAROUSEL_SLIDES.map((item, itemIndex) => (
                <button
                  key={item.id}
                  type="button"
                  onClick={() => selectSlide(itemIndex)}
                  aria-label={`Tampilkan ${item.businessLabel}`}
                  aria-current={itemIndex === activeIndex}
                  className={`h-2.5 rounded-full transition-all ${
                    itemIndex === activeIndex ? 'w-7 bg-slate-900' : 'w-2.5 bg-slate-300 hover:bg-slate-500'
                  }`}
                />
              ))}
            </div>

            <button
              type="button"
              onClick={() => selectSlide(activeIndex + 1)}
              aria-label="Tampilkan jenis usaha berikutnya"
              className="hidden sm:flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-white text-slate-700 border border-slate-300 hover:text-emerald-700 hover:border-emerald-400 transition-colors"
            >
              <ChevronRight className="w-5 h-5" />
            </button>
          </div>
        </div>

        <div className="mt-8 md:mt-10 mx-auto max-w-3xl space-y-5 text-center">
          <p className="text-sm sm:text-base text-slate-600">
            {isPromoActive
              ? `Selama ${promoName || 'promo'} berlangsung, cukup ${effectivePriceFormatted} sekali beli.`
              : 'Sekali beli untuk satu perangkat Android.'}
          </p>

          <div className="flex items-baseline flex-wrap justify-center gap-x-2 gap-y-1">
            <span className="text-3xl sm:text-4xl font-extrabold text-slate-900">{effectivePriceFormatted}</span>
            {isPromoActive && normalPriceFormatted && normalPriceFormatted !== effectivePriceFormatted && (
              <span className="text-base sm:text-lg text-slate-400 line-through font-semibold">{normalPriceFormatted}</span>
            )}
            <span className="text-xs sm:text-sm font-semibold text-slate-500">
              &bull; Sekali beli &bull; 1 perangkat Android
            </span>
          </div>

          {isPromoActive && showCountdown && (
            <div className="flex justify-center">
              <PromoCountdown variant="hero" />
            </div>
          )}

          <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-center gap-3">
            <a
              href={`#${TEST_CAMPAIGN_CONFIG.anchorId}`}
              onClick={trackTestRegistrationStarted}
              className="w-full sm:w-auto inline-flex items-center justify-center gap-2.5 bg-emerald-600 hover:bg-emerald-700 active:scale-98 text-white font-extrabold text-base px-7 py-4 rounded-2xl shadow-lg shadow-emerald-600/25 hover:shadow-xl hover:shadow-emerald-600/30 transition-all group"
            >
              <Beaker className="w-5 h-5 group-hover:-translate-y-0.5 transition-transform" />
              <span>{LANDING_CONFIG.heroPrimaryCta}</span>
            </a>
            <a
              href={getOrderUrl(LANDING_CONFIG.publicOrderUrl)}
              target="_blank"
              rel="noopener noreferrer"
              onClick={() => trackBuyClick('hero')}
              className="w-full sm:w-auto inline-flex items-center justify-center gap-2.5 bg-white hover:bg-slate-50 text-slate-900 border border-slate-300/80 font-extrabold text-base px-7 py-4 rounded-2xl shadow-xs transition-colors"
            >
              <ShoppingBag className="w-5 h-5 text-emerald-600" />
              <span>{LANDING_CONFIG.heroSecondaryCta}</span>
            </a>
          </div>

          <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-center gap-2.5">
            <a
              href={LANDING_CONFIG.whatsappConsultationUrl}
              target="_blank"
              rel="noopener noreferrer"
              onClick={() => trackWhatsAppClick('hero')}
              className="w-full sm:w-auto inline-flex items-center justify-center gap-2 bg-white hover:bg-slate-50 text-slate-700 border border-slate-300/80 font-bold text-sm px-5 py-3 rounded-2xl shadow-xs transition-colors"
            >
              <MessageCircle className="w-4 h-4 text-emerald-600" />
              <span>Tanya Paket via WhatsApp</span>
            </a>
            <a
              href={LANDING_CONFIG.downloadApkUrl}
              onClick={() => trackDownloadClick('hero')}
              className="w-full sm:w-auto inline-flex items-center justify-center gap-2.5 text-slate-600 font-bold text-sm px-5 py-3 rounded-2xl transition-colors hover:text-slate-900"
            >
              <Download className="w-4 h-4" />
              <span>Download Buku Warung (APK)</span>
            </a>
          </div>

          <div className="flex flex-wrap items-center justify-center gap-y-2 gap-x-5 text-xs font-semibold text-slate-600">
            {TRUST_BADGES.map(badge => (
              <div key={badge.label} className="flex items-center gap-1.5">
                {badge.icon}
                <span>{badge.label}</span>
              </div>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
};