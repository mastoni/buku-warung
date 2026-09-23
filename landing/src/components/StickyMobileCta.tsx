import React, { useState, useEffect } from 'react';
import { ShoppingCart, Download, X } from 'lucide-react';
import { LANDING_CONFIG } from '../data/landingData';
import { usePricing } from '../hooks/usePricingPromo';
import { trackBuyClick, trackDownloadClick, getOrderUrl } from '../tracking';

export const StickyMobileCta: React.FC = () => {
  const { effectivePriceFormatted } = usePricing();
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
    <div className="md:hidden fixed bottom-0 left-0 right-0 z-50 bg-white/95 backdrop-blur-md border-t border-slate-200/90 px-3 py-2.5 shadow-2xl flex items-center justify-between gap-2">
      <a
        href={LANDING_CONFIG.downloadApkUrl}
        onClick={() => trackDownloadClick('sticky_mobile')}
        className="flex-1 flex items-center justify-center gap-1.5 bg-emerald-50 active:bg-emerald-100 text-emerald-900 border border-emerald-300 font-bold text-xs py-2.5 px-2 rounded-xl transition-all"
      >
        <Download className="w-3.5 h-3.5 shrink-0" />
        <span>Download APK</span>
      </a>

      <a
        href={getOrderUrl(LANDING_CONFIG.publicOrderStickyUrl)}
        onClick={() => trackBuyClick('sticky')}
        className="flex-1 flex items-center justify-center gap-1.5 bg-emerald-600 active:bg-emerald-700 text-white font-extrabold text-xs py-2.5 px-2 rounded-xl shadow-md transition-all"
      >
        <ShoppingCart className="w-3.5 h-3.5 shrink-0" />
        <span>Beli ({effectivePriceFormatted})</span>
      </a>

      <button
        type="button"
        onClick={handleDismiss}
        className="p-1.5 rounded-lg text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition-colors"
        aria-label="Tutup"
      >
        <X className="w-4 h-4" />
      </button>
    </div>
  );
};
