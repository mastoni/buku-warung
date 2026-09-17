import React from 'react';
import { ShoppingCart } from 'lucide-react';
import { LANDING_CONFIG } from '../data/landingData';
import { usePricing } from '../hooks/usePricingPromo';

export const StickyMobileCta: React.FC = () => {
  const { isPromoActive, effectivePriceFormatted, normalPriceFormatted, normalPrice, effectivePrice } = usePricing();

  return (
    <div className="md:hidden fixed bottom-0 left-0 right-0 z-50 bg-white/95 backdrop-blur-md border-t border-slate-200/90 px-4 py-3 shadow-2xl flex items-center justify-between gap-3">
      <div>
        <div className="text-[11px] text-slate-500 font-semibold uppercase tracking-wider">Sekali Beli Seumur Hidup</div>
        <div className="flex items-baseline gap-1.5">
          <span className="font-extrabold text-lg text-slate-900">{effectivePriceFormatted}</span>
          {isPromoActive && normalPrice > effectivePrice && (
            <span className="text-[10px] text-slate-400 line-through">{normalPriceFormatted}</span>
          )}
        </div>
      </div>

      <a
        href={LANDING_CONFIG.publicOrderStickyUrl}
        className="flex-1 max-w-[200px] flex items-center justify-center gap-1.5 bg-emerald-600 active:bg-emerald-700 text-white font-extrabold text-sm py-3 px-4 rounded-xl shadow-md active:scale-98 transition-all"
      >
        <ShoppingCart className="w-4 h-4 shrink-0" />
        <span>Beli Sekarang</span>
      </a>
    </div>
  );
};
