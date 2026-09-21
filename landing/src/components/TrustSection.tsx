import React from 'react';
import { ShieldCheck, Smartphone, Cloud, MessageCircle, CheckCircle2 } from 'lucide-react';
import { TRUST_ITEMS } from '../data/landingData';

const iconMap: Record<string, React.ElementType> = {
  ShieldCheck,
  Smartphone,
  Cloud,
  MessageCircle,
  CheckCircle2,
};

export const TrustSection: React.FC = () => {
  return (
    <section id="kepercayaan" className="py-16 md:py-24 bg-slate-50 border-t border-slate-200 reveal-on-scroll">
      <div className="max-w-6xl mx-auto px-4 sm:px-6">
        <div className="text-center max-w-2xl mx-auto space-y-3 mb-12 sm:mb-16">
          <div className="inline-block px-3 py-1 rounded-lg bg-emerald-100 text-emerald-800 text-xs font-bold uppercase tracking-wider">
            Mengapa Memilih Buku Warung
          </div>
          <h2 className="text-2xl sm:text-4xl font-extrabold text-slate-900 tracking-tight">
            Mengapa Memilih Buku Warung
          </h2>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-4 sm:gap-6">
          {TRUST_ITEMS.map((item) => {
            const Icon = iconMap[item.icon] || ShieldCheck;
            return (
              <div
                key={item.id}
                className="bg-white p-5 sm:p-6 rounded-2xl border border-slate-200/80 hover:border-emerald-200 hover:shadow-md transition-all duration-200 flex flex-col gap-3 text-center"
              >
                <div className="w-12 h-12 rounded-xl bg-emerald-100 text-emerald-700 flex items-center justify-center mx-auto">
                  <Icon className="w-6 h-6" />
                </div>
                <div>
                  <h3 className="font-extrabold text-sm sm:text-base text-slate-900 mb-1">{item.title}</h3>
                  <p className="text-xs sm:text-sm text-slate-600 leading-relaxed">{item.description}</p>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
};
