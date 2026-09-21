import React from 'react';
import { ShoppingCart, MessageCircle } from 'lucide-react';
import { LANDING_CONFIG } from '../data/landingData';
import { trackBuyClick, trackWhatsAppClick } from '../tracking';

export const FinalCta: React.FC = () => {
  return (
    <section id="cta" className="py-16 md:py-24 bg-slate-900 border-t border-slate-800 reveal-on-scroll">
      <div className="max-w-4xl mx-auto px-4 sm:px-6 text-center space-y-6">
        <h2 className="text-2xl sm:text-4xl font-extrabold text-white tracking-tight">
          Mulai Warung Anda Lebih Rapi
        </h2>
        <p className="text-base sm:text-lg text-slate-300 max-w-2xl mx-auto">
          Dapatkan aplikasi kasir & pembukuan Buku Warung seharga Rp50.000 sekali beli. Tanpa langganan.
        </p>

        <div className="flex flex-col sm:flex-row items-center justify-center gap-3.5 pt-2">
          <a
            href={LANDING_CONFIG.publicOrderUrl}
            onClick={() => trackBuyClick('final')}
            className="w-full sm:w-auto inline-flex items-center justify-center gap-2.5 bg-emerald-600 hover:bg-emerald-700 active:scale-98 text-white font-extrabold text-base px-7 py-4 rounded-2xl shadow-lg shadow-emerald-600/25 hover:shadow-xl hover:shadow-emerald-600/30 transition-all"
          >
            <ShoppingCart className="w-5 h-5" />
            <span>Beli Sekarang — Rp50.000</span>
          </a>
          <a
            href={LANDING_CONFIG.whatsappConsultationUrl}
            target="_blank"
            rel="noopener noreferrer"
            onClick={() => trackWhatsAppClick('final')}
            className="w-full sm:w-auto inline-flex items-center justify-center gap-2 bg-white hover:bg-slate-50 text-slate-700 border border-slate-300/80 font-bold text-sm px-6 py-4 rounded-2xl shadow-xs transition-colors"
          >
            <MessageCircle className="w-4 h-4 text-emerald-600" />
            <span>Tanya Admin via WhatsApp</span>
          </a>
        </div>
      </div>
    </section>
  );
};
