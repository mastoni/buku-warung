import React from 'react';
import { Store, Pill, Hammer, Wrench, ArrowRight } from 'lucide-react';
import { ADAPTIVE_BUSINESS_PROFILES, AdaptiveBusinessProfile } from '../data/landingData';
import { trackBuyClick } from '../tracking';
import { LANDING_CONFIG } from '../data/landingData';

const iconMap: Record<string, React.ElementType> = {
  Store,
  Pill,
  Hammer,
  Wrench,
};

export const AdaptiveBusinessSwitcher: React.FC = () => {
  return (
    <section id="adaptif" className="py-16 md:py-24 bg-slate-50 border-b border-slate-200/80 reveal-on-scroll">
      <div className="max-w-6xl mx-auto px-4 sm:px-6">
        <div className="text-center max-w-3xl mx-auto space-y-3 mb-10 sm:mb-14">
          <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-lg bg-emerald-100 text-emerald-800 text-xs font-bold uppercase tracking-wider">
            <span>Cocok untuk Berbagai Jenis Usaha</span>
          </div>
          <h2 className="text-2xl sm:text-4xl font-extrabold text-slate-900 tracking-tight">
            Cocok untuk Warung, Toko, Apotek, dan Bengkel
          </h2>
          <p className="text-sm sm:text-base text-slate-600 leading-relaxed">
            Terminologi dan tampilan otomatis menyesuaikan jenis usaha Anda.
          </p>
        </div>

        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 sm:gap-6">
          {ADAPTIVE_BUSINESS_PROFILES.map((profile: AdaptiveBusinessProfile) => {
            const Icon = iconMap[profile.iconName] || Store;
            return (
              <div
                key={profile.id}
                className="bg-white p-5 sm:p-6 rounded-2xl border border-slate-200/80 hover:border-emerald-200 hover:shadow-md transition-all duration-200 flex flex-col items-center text-center gap-3"
              >
                <div className="w-12 h-12 rounded-xl bg-emerald-100 text-emerald-700 flex items-center justify-center">
                  <Icon className="w-6 h-6" />
                </div>
                <div className="space-y-1">
                  <h3 className="font-extrabold text-sm sm:text-base text-slate-900">{profile.name}</h3>
                  <p className="text-xs text-slate-500 leading-relaxed line-clamp-3">{profile.summary}</p>
                </div>
              </div>
            );
          })}
        </div>

        <div className="mt-8 text-center">
          <a
            href={LANDING_CONFIG.publicOrderUrl}
            onClick={() => trackBuyClick('adaptif')}
            className="inline-flex items-center gap-2 bg-emerald-600 hover:bg-emerald-700 text-white font-bold text-sm px-6 py-3 rounded-xl shadow-xs hover:shadow-md transition-all"
          >
            <span>Coba untuk usaha Anda</span>
            <ArrowRight className="w-4 h-4" />
          </a>
        </div>
      </div>
    </section>
  );
};
