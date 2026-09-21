import React from 'react';
import { ShoppingCart, FileText, CreditCard, Smartphone, CheckCircle2 } from 'lucide-react';
import { HOW_TO_BUY_STEPS } from '../data/landingData';
import { LANDING_CONFIG } from '../data/landingData';
import { trackBuyClick } from '../tracking';

const iconMap: Record<string, React.ElementType> = {
  1: FileText,
  2: CreditCard,
  3: Smartphone,
  4: CheckCircle2,
  5: ShoppingCart,
};

export const HowToBuy: React.FC = () => {
  return (
    <section id="cara-beli" className="py-16 md:py-24 bg-white border-t border-slate-200 reveal-on-scroll">
      <div className="max-w-6xl mx-auto px-4 sm:px-6">
        <div className="text-center max-w-2xl mx-auto space-y-3 mb-12 sm:mb-16">
          <div className="inline-block px-3 py-1 rounded-lg bg-emerald-100 text-emerald-800 text-xs font-bold uppercase tracking-wider">
            Cara Pembelian
          </div>
          <h2 className="text-2xl sm:text-4xl font-extrabold text-slate-900 tracking-tight">
            Cara Pembelian
          </h2>
          <p className="text-sm sm:text-base text-slate-600">
            Dapatkan Buku Warung dalam 5 langkah mudah.
          </p>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-4 sm:gap-6">
          {HOW_TO_BUY_STEPS.map((step) => {
            const Icon = iconMap[step.id] || FileText;
            return (
              <div
                key={step.id}
                className="bg-slate-50/80 p-5 sm:p-6 rounded-2xl border border-slate-200/80 hover:border-emerald-200 hover:shadow-md transition-all duration-200 flex flex-col gap-3"
              >
                <div className="flex items-center gap-2.5">
                  <div className="w-8 h-8 rounded-full bg-emerald-600 text-white font-extrabold text-sm flex items-center justify-center shrink-0">
                    {step.id}
                  </div>
                  <div className="w-9 h-9 rounded-lg bg-emerald-100 text-emerald-700 flex items-center justify-center">
                    <Icon className="w-4.5 h-4.5" />
                  </div>
                </div>
                <div>
                  <h3 className="font-extrabold text-sm sm:text-base text-slate-900 mb-1">{step.title}</h3>
                  <p className="text-xs sm:text-sm text-slate-600 leading-relaxed">{step.description}</p>
                </div>
              </div>
            );
          })}
        </div>

        <div className="mt-10 flex justify-center">
          <a
            href={LANDING_CONFIG.publicOrderUrl}
            onClick={() => trackBuyClick('howtobuy')}
            className="inline-flex items-center justify-center gap-2.5 bg-emerald-600 hover:bg-emerald-700 active:scale-98 text-white font-extrabold text-base px-7 py-4 rounded-2xl shadow-lg shadow-emerald-600/25 hover:shadow-xl hover:shadow-emerald-600/30 transition-all"
          >
            <ShoppingCart className="w-5 h-5" />
            <span>Beli Sekarang — Rp50.000</span>
          </a>
        </div>
      </div>
    </section>
  );
};
