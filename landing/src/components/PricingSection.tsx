import React, { useEffect, useRef } from 'react';
import { ShoppingCart, Check, MessageCircle, Sparkles, Download } from 'lucide-react';
import { LANDING_CONFIG } from '../data/landingData';
import { usePricing } from '../hooks/usePricingPromo';
import { PromoCountdown } from './PromoCountdown';
import { trackViewPrice, trackBuyClick, trackWhatsAppClick, trackDownloadClick, getOrderUrl } from '../tracking';

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
            Paket Lisensi Resmi
          </div>
          <h2 className="text-2xl sm:text-4xl font-extrabold text-slate-900 tracking-tight">
            Harga Lisensi Resmi Buku Warung
          </h2>
          <p className="text-sm sm:text-base text-slate-600">
            Pembelian sekali beli, tanpa biaya bulanan, tahunan, atau potongan per transaksi.
          </p>
        </div>

        {/* Pricing Card */}
        <div className="max-w-2xl mx-auto">
          <div className="bg-gradient-to-b from-emerald-900 via-emerald-900 to-slate-900 rounded-3xl p-6 sm:p-8 text-white shadow-2xl relative flex flex-col justify-between overflow-hidden border border-emerald-700">
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
              <h3 className="text-2xl font-extrabold text-white">Lisensi Seumur Hidup (1 Perangkat)</h3>
              <p className="text-xs text-emerald-200/80 mt-1">1 Lisensi = 1 Email Pemilik = 1 HP Android</p>

              <div className="mt-6 mb-4 flex items-baseline gap-2">
                <span className="text-4xl sm:text-5xl font-extrabold text-white tracking-tight">
                  {effectivePriceFormatted}
                </span>
                <span className="text-xs sm:text-sm font-semibold text-emerald-300">/ sekali beli</span>
              </div>

              {/* Dynamic Countdown Display */}
              {isPromoActive && showCountdown && <PromoCountdown variant="card" />}

              {/* Feature Highlights */}
              <ul className="space-y-3 text-sm text-emerald-100/90 font-medium mt-6">
                <li className="flex items-start gap-2.5">
                  <Check className="w-5 h-5 text-emerald-400 shrink-0 mt-0.5" />
                  <span>Offline-First (Jualan kasir tanpa perlu paket data internet)</span>
                </li>
                <li className="flex items-start gap-2.5">
                  <Check className="w-5 h-5 text-emerald-400 shrink-0 mt-0.5" />
                  <span>Tanpa iuran bulanan maupun tahunan</span>
                </li>
                <li className="flex items-start gap-2.5">
                  <Check className="w-5 h-5 text-emerald-400 shrink-0 mt-0.5" />
                  <span>Kasir POS, Stok Barang, Hutang Piutang, Laporan &amp; Ekspor PDF</span>
                </li>
                <li className="flex items-start gap-2.5">
                  <Check className="w-5 h-5 text-emerald-400 shrink-0 mt-0.5" />
                  <span>Cetak Struk Thermal Bluetooth 58mm/80mm &amp; Katalog WhatsApp</span>
                </li>
                <li className="flex items-start gap-2.5">
                  <Check className="w-5 h-5 text-emerald-400 shrink-0 mt-0.5" />
                  <span>Backup manual opsional ke Google Sheets pribadi</span>
                </li>
                <li className="flex items-start gap-2.5">
                  <Check className="w-5 h-5 text-emerald-400 shrink-0 mt-0.5" />
                  <span>Pembaruan aplikasi resmi &amp; bantuan teknis admin SKMNetwork</span>
                </li>
              </ul>
            </div>

            {/* Card CTA */}
            <div className="mt-8 pt-6 border-t border-emerald-800/80 space-y-3">
              <a
                href={getOrderUrl(LANDING_CONFIG.publicOrderPricingUrl)}
                onClick={() => trackBuyClick('pricing')}
                className="w-full flex items-center justify-center gap-2 bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-extrabold text-base py-4 rounded-xl shadow-lg hover:shadow-emerald-500/25 transition-all active:scale-98"
              >
                <ShoppingCart className="w-5 h-5" />
                <span>Beli Lisensi Resmi — {effectivePriceFormatted}</span>
              </a>
              <p className="text-[11px] text-center text-emerald-300/70 font-medium">
                Aktivasi instan via kode lisensi resmi SKMNetwork setelah pembayaran
              </p>
            </div>
          </div>
        </div>

        {/* Alternative CTAs */}
        <div className="mt-8 flex flex-col sm:flex-row items-center justify-center gap-4 text-center">
          <a
            href={LANDING_CONFIG.downloadApkUrl}
            onClick={() => trackDownloadClick('pricing_download')}
            className="inline-flex items-center gap-1.5 font-bold text-slate-700 hover:text-emerald-700 text-sm bg-slate-100 hover:bg-slate-200 px-4 py-2.5 rounded-xl transition-colors"
          >
            <Download className="w-4 h-4 text-emerald-600" />
            <span>Download APK Buku Warung</span>
          </a>
          <a
            href={LANDING_CONFIG.whatsappConsultationUrl}
            target="_blank"
            rel="noopener noreferrer"
            onClick={() => trackWhatsAppClick('pricing')}
            className="inline-flex items-center gap-1.5 font-bold text-emerald-700 hover:text-emerald-800 text-sm px-4 py-2.5"
          >
            <MessageCircle className="w-4 h-4 text-emerald-600" />
            <span>Tanya Paket via WhatsApp</span>
          </a>
        </div>
      </div>
    </section>
  );
};
