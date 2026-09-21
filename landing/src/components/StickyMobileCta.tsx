import React, { useState, useEffect } from 'react';
import { ShoppingCart, X } from 'lucide-react';
import { LANDING_CONFIG } from '../data/landingData';
import { usePricing } from '../hooks/usePricingPromo';
import { trackBuyClick } from '../tracking';

export const StickyMobileCta: React.FC = () => {
  const { isPromoActive, effectivePriceFormatted, normalPriceFormatted, normalPrice, effectivePrice } = usePricing();
  const [isDismissed, setIsDismissed] = useState(false);

  useEffect(() => {
    const dismissed = sessionStorage.getItem('sticky_cta_dismissed');
    if (dismissed) {
      setIsDismissed(true);
    }
  }, []);

  const handleDismiss = () => {
    setIsDismissed(true);
    sessionStorage.setItem('sticky_cta_dismissed', 'true');
  };

  if (isDismissed) {
    return null;
  }

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

      <div className="flex items-center gap-2">
        <a
          href={LANDING_CONFIG.publicOrderStickyUrl}
          onClick={() => trackBuyClick('sticky')}
          className="flex-1 max-w-[200px] flex items-center justify-center gap-1.5 bg-emerald-600 active:bg-emerald-700 text-white font-extrabold text-sm py-3 px-4 rounded-xl shadow-md active:scale-98 transition-all"
        >
          <ShoppingCart className="w-4 h-4 shrink-0" />
          <span>Beli Sekarang</span>
        </a>
        <button
          type="button"
          onClick={handleDismiss}
          className="p-2 rounded-lg text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition-colors"
          aria-label="Tutup"
        >
          <X className="w-4 h-4" />
        </button>
      </div>
    </div>
  );
};
