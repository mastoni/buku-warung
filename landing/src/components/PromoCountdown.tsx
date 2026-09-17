import React from 'react';
import { Timer, Sparkles } from 'lucide-react';
import { usePricing } from '../hooks/usePricingPromo';

export interface PromoCountdownProps {
  variant?: 'card' | 'hero';
}

export const PromoCountdown: React.FC<PromoCountdownProps> = ({ variant = 'card' }) => {
  const { isPromoActive, showCountdown, countdown, promoName } = usePricing();

  if (!isPromoActive || !showCountdown || countdown.isExpired) {
    return null;
  }

  const { days, hours, minutes, seconds } = countdown;

  const pad = (n: number) => String(n).padStart(2, '0');

  if (variant === 'hero') {
    return (
      <div
        className="inline-flex flex-wrap items-center justify-center gap-2 px-4 py-2 rounded-2xl bg-emerald-950/80 border border-emerald-500/40 text-white text-xs sm:text-sm shadow-xl backdrop-blur-md"
        role="timer"
        aria-live="polite"
      >
        <div className="flex items-center gap-1.5 text-emerald-300 font-bold">
          <Timer className="w-4 h-4 animate-pulse text-emerald-400" />
          <span>{promoName || 'Promo'} Berakhir Dalam:</span>
        </div>
        <div className="flex items-center gap-1 font-mono font-extrabold text-white">
          {days > 0 && (
            <span className="px-2 py-0.5 rounded-md bg-emerald-800/80 border border-emerald-400/30">
              {days}h
            </span>
          )}
          <span className="px-2 py-0.5 rounded-md bg-emerald-800/80 border border-emerald-400/30">
            {pad(hours)}
          </span>
          <span className="text-emerald-400 font-bold">:</span>
          <span className="px-2 py-0.5 rounded-md bg-emerald-800/80 border border-emerald-400/30">
            {pad(minutes)}
          </span>
          <span className="text-emerald-400 font-bold">:</span>
          <span className="px-2 py-0.5 rounded-md bg-emerald-800/80 border border-emerald-400/30 text-emerald-200">
            {pad(seconds)}
          </span>
        </div>
        <span className="sr-only">
          Promo berakhir dalam {days > 0 ? `${days} hari ` : ''}{hours} jam {minutes} menit {seconds} detik
        </span>
      </div>
    );
  }

  // Default 'card' variant for Pricing Section
  return (
    <div
      className="mt-6 mb-4 p-4 rounded-2xl bg-emerald-950/70 border border-emerald-500/40 text-white backdrop-blur-xs"
      role="timer"
      aria-live="polite"
    >
      <div className="flex items-center justify-between gap-2 mb-3">
        <div className="flex items-center gap-1.5 text-xs font-extrabold text-emerald-300 uppercase tracking-wider">
          <Sparkles className="w-3.5 h-3.5 text-emerald-400" />
          <span>Sisa Waktu {promoName || 'Promo Spesial'}</span>
        </div>
        <div className="flex items-center gap-1 text-[11px] text-emerald-400 font-semibold">
          <Timer className="w-3.5 h-3.5" />
          <span>Terbatas</span>
        </div>
      </div>

      <div className="grid grid-cols-4 gap-2 text-center font-mono">
        <div className="bg-emerald-900/90 border border-emerald-400/30 rounded-xl p-2">
          <div className="text-lg sm:text-xl font-extrabold text-white leading-tight">{days}</div>
          <div className="text-[9px] sm:text-[10px] uppercase font-sans text-emerald-300 font-bold mt-0.5">Hari</div>
        </div>
        <div className="bg-emerald-900/90 border border-emerald-400/30 rounded-xl p-2">
          <div className="text-lg sm:text-xl font-extrabold text-white leading-tight">{pad(hours)}</div>
          <div className="text-[9px] sm:text-[10px] uppercase font-sans text-emerald-300 font-bold mt-0.5">Jam</div>
        </div>
        <div className="bg-emerald-900/90 border border-emerald-400/30 rounded-xl p-2">
          <div className="text-lg sm:text-xl font-extrabold text-white leading-tight">{pad(minutes)}</div>
          <div className="text-[9px] sm:text-[10px] uppercase font-sans text-emerald-300 font-bold mt-0.5">Menit</div>
        </div>
        <div className="bg-emerald-900/90 border border-emerald-400/30 rounded-xl p-2">
          <div className="text-lg sm:text-xl font-extrabold text-emerald-200 leading-tight">{pad(seconds)}</div>
          <div className="text-[9px] sm:text-[10px] uppercase font-sans text-emerald-300 font-bold mt-0.5">Detik</div>
        </div>
      </div>

      <p className="text-[11px] text-center text-emerald-200/80 font-sans mt-3">
        Setelah batas waktu habis, harga akan otomatis kembali ke harga normal.
      </p>
      <span className="sr-only">
        Sisa waktu promosi: {days > 0 ? `${days} hari ` : ''}{hours} jam {minutes} menit {seconds} detik
      </span>
    </div>
  );
};
