import React, { useEffect, useRef } from 'react';
import { ShoppingCart, Check, MessageCircle, Sparkles } from 'lucide-react';
import { LANDING_CONFIG, COMPARISON_POINTS } from '../data/landingData';
import { usePricing } from '../hooks/usePricingPromo';
import { PromoCountdown } from './PromoCountdown';
import { trackViewPrice, trackBuyClick, trackWhatsAppClick } from '../tracking';

export const PricingSection: React.FC = () => {
  const {
    isPromoActive,
    effectivePriceFormatted,
    normalPriceFormatted,
    promoName,
    showCountdown,
    normalPrice,
    effectivePrice,
  } = usePricing();
  const sectionRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const node = sectionRef.current;
    if (!node) return;

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            trackViewPrice();
            observer.disconnect();
          }
        });
      },
      { threshold: 0.25 }
    );

    observer.observe(node);
    return () => observer.disconnect();
  }, []);

  return (
    <section ref={sectionRef} id="harga" className="py-16 md:py-24 bg-white border-t border-slate-200 reveal-on-scroll">
      <div className="max-w-6xl mx-auto px-4 sm:px-6">
        {/* Section Header */}
        <div className="text-center max-w-2xl mx-auto space-y-3 mb-12 sm:mb-16">
          <div className="inline-block px-3 py-1 rounded-lg bg-emerald-100 text-emerald-800 text-xs font-bold uppercase tracking-wider">
            Investasi Hemat UMKM
          </div>
          <h2 className="text-2xl sm:text-4xl font-extrabold text-slate-900 tracking-tight">
            Sekali Beli Seumur Hidup, Hemat Jutaan Rupiah
          </h2>
          <p className="text-sm sm:text-base text-slate-600">
            Hindari jebakan iuran kasir bulanan yang terus menguras keuntungan usaha Anda setiap bulan.
          </p>
        </div>

        {/* Pricing Card & Comparison Grid */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-stretch">
          {/* Main Lifetime Deal Card */}
          <div className="lg:col-span-5 bg-gradient-to-b from-emerald-900 via-emerald-900 to-slate-900 rounded-3xl p-6 sm:p-8 text-white shadow-2xl relative flex flex-col justify-between overflow-hidden border border-emerald-700">
            {/* Background Glow */}
            <div className="absolute top-0 right-0 w-64 h-64 bg-emerald-500/10 rounded-full blur-3xl pointer-events-none" />

            <div>
              {/* Header Badge */}
              <div className="flex items-center justify-between gap-2 mb-6">
                <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-emerald-500/20 text-emerald-300 border border-emerald-400/30 text-xs font-extrabold">
                  <Sparkles className="w-3.5 h-3.5" />
                  {isPromoActive ? (promoName || 'Promo Peluncuran') : 'Lisensi Komersial'}
                </span>
                {isPromoActive && normalPrice > effectivePrice && (
                  <span className="text-xs text-emerald-300/80 font-semibold line-through">
                    {normalPriceFormatted}
                  </span>
                )}
              </div>

              {/* Title & Price */}
              <h3 className="text-2xl font-extrabold text-white">Lisensi Komersial Lifetime</h3>
              <p className="text-xs text-emerald-200/80 mt-1">Akses penuh semua fitur kasir & pembukuan</p>

              <div className="mt-6 mb-4 flex items-baseline gap-2">
                <span className="text-4xl sm:text-5xl font-extrabold text-white tracking-tight">
                  {effectivePriceFormatted}
                </span>
                <span className="text-xs sm:text-sm font-semibold text-emerald-300">/ sekali beli seumur hidup</span>
              </div>

              {/* Dynamic Countdown Display */}
              {isPromoActive && showCountdown && <PromoCountdown variant="card" />}

              {/* Feature Highlights */}
              <ul className="space-y-3 text-sm text-emerald-100/90 font-medium mt-6">
                <li className="flex items-start gap-2.5">
                  <Check className="w-5 h-5 text-emerald-400 shrink-0 mt-0.5" />
                  <span>100% Offline-First (Jualan tanpa kuota internet)</span>
                </li>
                <li className="flex items-start gap-2.5">
                  <Check className="w-5 h-5 text-emerald-400 shrink-0 mt-0.5" />
                  <span>9 Modul Lengkap (Kasir, Stok, Kulakan, Hutang, Kas, Laporan)</span>
                </li>
                <li className="flex items-start gap-2.5">
                  <Check className="w-5 h-5 text-emerald-400 shrink-0 mt-0.5" />
                  <span>Katalog Produk WhatsApp & Cetak Struk Bluetooth</span>
                </li>
                <li className="flex items-start gap-2.5">
                  <Check className="w-5 h-5 text-emerald-400 shrink-0 mt-0.5" />
                  <span>Backup data otomatis ke Google Sheets pribadi</span>
                </li>
                <li className="flex items-start gap-2.5">
                  <Check className="w-5 h-5 text-emerald-400 shrink-0 mt-0.5" />
                  <span>Tanpa Iuran Bulanan / Tahunan (Rp 0 selamanya)</span>
                </li>
              </ul>
            </div>

            {/* Card CTA */}
            <div className="mt-8 pt-6 border-t border-emerald-800/80 space-y-3">
              <a
                href={LANDING_CONFIG.publicOrderPricingUrl}
                onClick={() => trackBuyClick('pricing')}
                className="w-full flex items-center justify-center gap-2 bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-extrabold text-base py-4 rounded-xl shadow-lg hover:shadow-emerald-500/25 transition-all active:scale-98"
              >
                <ShoppingCart className="w-5 h-5" />
                <span>Beli Sekarang — {effectivePriceFormatted}</span>
              </a>
              <p className="text-[11px] text-center text-emerald-300/70 font-medium">
                Aktivasi instan via kode lisensi resmi SKMNetwork
              </p>
            </div>
          </div>

          {/* Comparison Table */}
          <div className="lg:col-span-7 bg-slate-50 rounded-3xl p-6 sm:p-8 border border-slate-200/80 flex flex-col justify-between">
            <div>
              <h3 className="text-xl font-extrabold text-slate-900 mb-2">Bandingkan Biaya Nyata</h3>
              <p className="text-xs sm:text-sm text-slate-600 mb-6">
                Lihat perbedaan signifikan antara Buku Warung sekali beli vs aplikasi kasir langganan bulanan.
              </p>

              <div className="space-y-4">
                {COMPARISON_POINTS.map((item, index) => {
                  const displayBukuWarung =
                    item.feature === 'Model Pembayaran'
                      ? `${effectivePriceFormatted} Sekali Beli Seumur Hidup`
                      : item.bukuWarung;

                  return (
                    <div
                      key={index}
                      className={`p-4 rounded-xl border transition-all ${
                        item.highlight
                          ? 'bg-emerald-50/70 border-emerald-200'
                          : 'bg-white border-slate-200/70'
                      }`}
                    >
                      <div className="text-xs font-bold text-slate-500 uppercase tracking-wider mb-2">
                        {item.feature}
                      </div>
                      <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 text-sm">
                        <div className="flex items-start gap-1.5 text-emerald-900 font-bold">
                          <Check className="w-4 h-4 text-emerald-600 shrink-0 mt-0.5" />
                          <span>Buku Warung: {displayBukuWarung}</span>
                        </div>
                        <div className="text-slate-500 text-xs sm:text-sm pl-5 sm:pl-0">
                          Lainnya: {item.others}
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>

            <div className="mt-6 pt-4 border-t border-slate-200 flex items-center justify-between text-xs text-slate-600">
              <span>Butuh bantuan pemesanan?</span>
              <a
                href={LANDING_CONFIG.whatsappConsultationUrl}
                target="_blank"
                rel="noopener noreferrer"
                onClick={() => trackWhatsAppClick('pricing')}
                className="inline-flex items-center gap-1 font-bold text-emerald-700 hover:text-emerald-800"
              >
                <MessageCircle className="w-4 h-4" />
                <span>Hubungi CS WhatsApp</span>
              </a>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
};
