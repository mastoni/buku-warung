import React from 'react';
import { Download, MessageCircle, CheckCircle2, ShieldCheck, Zap, Beaker, ShoppingBag } from 'lucide-react';
import { LANDING_CONFIG, TEST_CAMPAIGN_CONFIG } from '../data/landingData';
import { usePricing } from '../hooks/usePricingPromo';
import { PromoCountdown } from './PromoCountdown';
import {
  trackBuyClick,
  trackDownloadClick,
  trackTestRegistrationStarted,
  trackWhatsAppClick
} from '../tracking';
import { getOrderUrl } from '../tracking';

export const Hero: React.FC = () => {
  const { isPromoActive, effectivePriceFormatted, normalPriceFormatted, showCountdown, promoName } =
    usePricing();

  return (
    <section id="beranda" className="pt-28 pb-16 md:pt-36 md:pb-24 relative overflow-hidden">
      {/* Background decoration */}
      <div className="absolute top-0 left-1/2 -translate-x-1/2 w-full max-w-7xl h-[550px] bg-radial from-emerald-100/60 via-emerald-50/20 to-transparent -z-10 pointer-events-none" />

      <div className="max-w-6xl mx-auto px-4 sm:px-6">
        <div className="text-center max-w-3xl mx-auto space-y-6">
          {/* Badge - small eyebrow that frames the two commercial paths below */}
          <div className="pt-1">
            <span className="inline-flex items-center gap-1.5 rounded-full bg-emerald-100 text-emerald-800 px-3.5 py-1.5 text-xs sm:text-sm font-extrabold">
              <Zap className="w-3.5 h-3.5" />
              {LANDING_CONFIG.heroBadge}
            </span>
          </div>

          {/* H1 - the primary message */}
          <h1 className="text-3xl sm:text-5xl lg:text-6xl font-extrabold tracking-tight text-slate-900 leading-[1.12] text-balance">
            {LANDING_CONFIG.headline}
          </h1>

          {/* H2 - explains the solution and price model */}
          <h2 className="text-lg sm:text-2xl font-bold tracking-tight text-slate-700 leading-snug text-balance">
            {LANDING_CONFIG.subheadline}
          </h2>

          {/* Description - what the product actually does */}
          <p className="text-base sm:text-lg text-slate-600 leading-relaxed max-w-2xl mx-auto font-normal">
            {LANDING_CONFIG.heroDescription}
          </p>

          {/* Promo/Pricing Message - always mirrors the server-authoritative pricing state.
              No "seumur hidup": the licence is a one-time purchase bound to one device and is
              transferable by admin, so a permanence claim would be inaccurate. */}
          <p className="text-sm sm:text-base text-slate-600 max-w-2xl mx-auto">
            {isPromoActive
              ? `Tanpa langganan bulanan maupun tahunan. Selama ${promoName || 'promo'} berlangsung, cukup ${effectivePriceFormatted} sekali beli.`
              : 'Tanpa langganan bulanan maupun tahunan. Sekali beli untuk satu perangkat Android.'}
          </p>

          {/* Price Display - effective price is the server-authoritative payable amount.
              The crossed-out reference only renders while the server says the promo runs. */}
          <div className="flex items-baseline justify-center gap-2">
            <span className="text-2xl sm:text-3xl font-extrabold text-slate-900">{effectivePriceFormatted}</span>
            {isPromoActive && normalPriceFormatted && normalPriceFormatted !== effectivePriceFormatted && (
              <span className="text-base sm:text-lg text-slate-400 line-through font-semibold">{normalPriceFormatted}</span>
            )}
            <span className="text-xs sm:text-sm font-semibold text-slate-500">
              &bull; Sekali beli &bull; 1 perangkat Android
            </span>
          </div>

          {/* Hero Countdown if active */}
          {isPromoActive && showCountdown && (
            <div className="pt-1 flex justify-center">
              <PromoCountdown variant="hero" />
            </div>
          )}

          {/* Action Buttons — two clearly separated commercial paths */}
          <div className="pt-2 flex flex-col sm:flex-row items-center justify-center gap-3.5">
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

          <div className="pt-1 flex flex-col sm:flex-row items-center justify-center gap-3.5">
            <a
              href={LANDING_CONFIG.whatsappConsultationUrl}
              target="_blank"
              rel="noopener noreferrer"
              onClick={() => trackWhatsAppClick('hero')}
              className="w-full sm:w-auto inline-flex items-center justify-center gap-2 bg-white hover:bg-slate-50 text-slate-700 border border-slate-300/80 font-bold text-sm px-6 py-3 rounded-2xl shadow-xs transition-colors"
            >
              <MessageCircle className="w-4 h-4 text-emerald-600" />
              <span>Tanya Paket via WhatsApp</span>
            </a>
            <a
              href={LANDING_CONFIG.downloadApkUrl}
              onClick={() => trackDownloadClick('hero')}
              className="w-full sm:w-auto inline-flex items-center justify-center gap-2.5 text-slate-600 font-bold text-sm px-6 py-3 rounded-2xl transition-colors hover:text-slate-900"
            >
              <Download className="w-4 h-4" />
              <span>Download Buku Warung (APK)</span>
            </a>
          </div>

          {/* Trust Value Badges */}
          <div className="pt-6 flex flex-wrap items-center justify-center gap-y-2 gap-x-6 text-xs sm:text-sm font-semibold text-slate-600">
            <div className="flex items-center gap-1.5">
              <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
              <span>Sekali Bayar, Tanpa Langganan</span>
            </div>
            <div className="flex items-center gap-1.5">
              <Zap className="w-4 h-4 text-emerald-600 shrink-0" />
              <span>Offline-First, Tanpa Kuota Jualan</span>
            </div>
            <div className="flex items-center gap-1.5">
              <ShieldCheck className="w-4 h-4 text-emerald-600 shrink-0" />
              <span>Data Usaha Utama di HP Anda</span>
            </div>
          </div>
        </div>

        {/* Hero Visual Mockup */}
        <div className="mt-12 sm:mt-16 max-w-4xl mx-auto">
          <div className="relative rounded-2xl md:rounded-3xl p-2 sm:p-4 bg-gradient-to-b from-slate-200 to-slate-100 shadow-2xl border border-slate-200">
            <div className="relative rounded-xl md:rounded-2xl overflow-hidden bg-slate-900 aspect-16/9 sm:aspect-2/1 flex items-center justify-center">
              <img
                src="/img/feature-graphic.png"
                alt="Tampilan Antarmuka Buku Warung"
                className="w-full h-full object-cover object-center"
                loading="eager"
              />
            </div>
          </div>
        </div>
      </div>
    </section>
  );
};
