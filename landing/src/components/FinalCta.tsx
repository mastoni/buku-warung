import React from 'react';
import { Download, MessageCircle, ShoppingCart } from 'lucide-react';
import { LANDING_CONFIG } from '../data/landingData';
import { usePricing } from '../hooks/usePricingPromo';
import { trackDownloadClick, trackWhatsAppClick, trackBuyClick, getOrderUrl } from '../tracking';

export const FinalCta: React.FC = () => {
  const { isPromoActive, effectivePriceFormatted } = usePricing();

  return (
    <section id="cta" className="py-16 md:py-24 bg-slate-900 border-t border-slate-800 reveal-on-scroll">
      <div className="max-w-4xl mx-auto px-4 sm:px-6 text-center space-y-6">
        <h2 className="text-2xl sm:text-4xl font-extrabold text-white tracking-tight">
          Kelola Jualan &amp; Keuangan Warung Lebih Rapi Hari Ini
        </h2>
        <p className="text-base sm:text-lg text-slate-300 max-w-2xl mx-auto">
          {isPromoActive
            ? `Tanpa langganan bulanan. Promo peluncuran hanya ${effectivePriceFormatted} sekali beli seumur hidup.`
            : `Tanpa langganan bulanan. Cukup ${effectivePriceFormatted} sekali beli seumur hidup.`}
        </p>

        <div className="flex flex-col sm:flex-row items-center justify-center gap-3.5 pt-2">
          <a
            href={LANDING_CONFIG.downloadApkUrl}
            onClick={() => trackDownloadClick('final')}
            className="w-full sm:w-auto inline-flex items-center justify-center gap-2.5 bg-emerald-600 hover:bg-emerald-700 active:scale-98 text-white font-extrabold text-base px-7 py-4 rounded-2xl shadow-lg shadow-emerald-600/25 hover:shadow-xl hover:shadow-emerald-600/30 transition-all"
          >
            <Download className="w-5 h-5" />
            <span>Download Buku Warung (APK)</span>
          </a>
          <a
            href={LANDING_CONFIG.whatsappConsultationUrl}
            target="_blank"
            rel="noopener noreferrer"
            onClick={() => trackWhatsAppClick('final')}
            className="w-full sm:w-auto inline-flex items-center justify-center gap-2 bg-white hover:bg-slate-50 text-slate-700 border border-slate-300/80 font-bold text-sm px-6 py-4 rounded-2xl shadow-xs transition-colors"
          >
            <MessageCircle className="w-4 h-4 text-emerald-600" />
            <span>Tanya Paket via WhatsApp</span>
          </a>
        </div>

        <div className="pt-2">
          <a
            href={getOrderUrl(LANDING_CONFIG.publicOrderUrl)}
            onClick={() => trackBuyClick('final_secondary')}
            className="inline-flex items-center gap-1.5 text-xs font-semibold text-emerald-400 hover:text-emerald-300 underline underline-offset-4"
          >
            <ShoppingCart className="w-3.5 h-3.5" />
            <span>Langsung Pesan Lisensi Resmi ({effectivePriceFormatted}) &rarr;</span>
          </a>
        </div>
      </div>
    </section>
  );
};
