import React from 'react';
import { ShoppingCart, MessageCircle, CheckCircle2, ShieldCheck, Zap, Sparkles } from 'lucide-react';
import { LANDING_CONFIG } from '../data/landingData';
import { usePricing } from '../hooks/usePricingPromo';
import { PromoCountdown } from './PromoCountdown';

export const Hero: React.FC = () => {
  const { isPromoActive, effectivePriceFormatted, promoName, showCountdown } = usePricing();

  return (
    <section id="beranda" className="pt-28 pb-16 md:pt-36 md:pb-24 relative overflow-hidden">
      {/* Background decoration */}
      <div className="absolute top-0 left-1/2 -translate-x-1/2 w-full max-w-7xl h-[550px] bg-radial from-emerald-100/60 via-emerald-50/20 to-transparent -z-10 pointer-events-none" />

      <div className="max-w-6xl mx-auto px-4 sm:px-6">
        <div className="text-center max-w-3xl mx-auto space-y-6">
          {/* Tagline Badge */}
          <div className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-full bg-emerald-100/90 text-emerald-900 border border-emerald-200/80 text-xs sm:text-sm font-bold shadow-xs">
            <Sparkles className="w-4 h-4 text-emerald-600" />
            <span>
              {isPromoActive
                ? `${promoName || 'Promo Peluncuran'}: ${effectivePriceFormatted} Sekali Beli Seumur Hidup`
                : `Lisensi Resmi: ${effectivePriceFormatted} Sekali Beli Seumur Hidup`}
            </span>
          </div>

          {/* Headline */}
          <h1 className="text-3xl sm:text-5xl lg:text-6xl font-extrabold tracking-tight text-slate-900 leading-[1.15]">
            Kasir & Pembukuan Warung Praktis{' '}
            <span className="text-transparent bg-clip-text bg-gradient-to-r from-emerald-600 to-teal-600">
              Tanpa Iuran Bulanan
            </span>
          </h1>

          {/* Subheadline */}
          <p className="text-base sm:text-lg text-slate-600 leading-relaxed max-w-2xl mx-auto font-normal">
            Solusi kasir POS, stok barang, buku hutang piutang, cetak struk Bluetooth, dan laporan keuangan rapi. 100%
            Offline-first untuk warung sembako, apotek, toko bangunan, bengkel, dan UMKM.
          </p>

          {/* Hero Countdown if active */}
          {isPromoActive && showCountdown && (
            <div className="pt-1 flex justify-center">
              <PromoCountdown variant="hero" />
            </div>
          )}

          {/* Action Buttons */}
          <div className="pt-2 flex flex-col sm:flex-row items-center justify-center gap-3.5">
            <a
              href={LANDING_CONFIG.publicOrderUrl}
              className="w-full sm:w-auto inline-flex items-center justify-center gap-2.5 bg-emerald-600 hover:bg-emerald-700 active:scale-98 text-white font-extrabold text-base px-7 py-4 rounded-2xl shadow-lg shadow-emerald-600/25 hover:shadow-xl hover:shadow-emerald-600/30 transition-all group"
            >
              <ShoppingCart className="w-5 h-5 group-hover:-translate-y-0.5 transition-transform" />
              <span>Beli Sekarang — {effectivePriceFormatted} (Sekali Beli)</span>
            </a>
            <a
              href={LANDING_CONFIG.whatsappConsultationUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="w-full sm:w-auto inline-flex items-center justify-center gap-2 bg-white hover:bg-slate-50 text-slate-700 border border-slate-300/80 font-bold text-sm px-6 py-4 rounded-2xl shadow-xs transition-colors"
            >
              <MessageCircle className="w-4 h-4 text-emerald-600" />
              <span>Tanya Admin via WhatsApp</span>
            </a>
          </div>

          {/* Trust Value Badges */}
          <div className="pt-6 flex flex-wrap items-center justify-center gap-y-2 gap-x-6 text-xs sm:text-sm font-semibold text-slate-600">
            <div className="flex items-center gap-1.5">
              <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
              <span>Sekali Beli Seumur Hidup</span>
            </div>
            <div className="flex items-center gap-1.5">
              <Zap className="w-4 h-4 text-emerald-600 shrink-0" />
              <span>100% Offline (Tanpa Kuota)</span>
            </div>
            <div className="flex items-center gap-1.5">
              <ShieldCheck className="w-4 h-4 text-emerald-600 shrink-0" />
              <span>Data Aman di HP Sendiri</span>
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
