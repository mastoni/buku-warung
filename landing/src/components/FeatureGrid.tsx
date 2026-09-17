import React from 'react';
import {
  ShoppingCart,
  Package,
  Truck,
  BookOpen,
  Wallet,
  BarChart3,
  Share2,
  Printer,
  Store,
} from 'lucide-react';
import { CORE_FEATURES, FeatureItem } from '../data/landingData';

const iconMap: Record<string, React.ElementType> = {
  ShoppingCart,
  Package,
  Truck,
  BookOpen,
  Wallet,
  BarChart3,
  Share2,
  Printer,
  Store,
};

export const FeatureGrid: React.FC = () => {
  return (
    <section id="fitur" className="py-16 md:py-24 bg-white border-y border-slate-200/80 reveal-on-scroll">
      <div className="max-w-6xl mx-auto px-4 sm:px-6">
        {/* Section Header */}
        <div className="text-center max-w-2xl mx-auto space-y-3 mb-12 sm:mb-16">
          <div className="inline-block px-3 py-1 rounded-lg bg-emerald-50 text-emerald-700 text-xs font-bold uppercase tracking-wider">
            9 Fitur Lengkap Warung & UMKM
          </div>
          <h2 className="text-2xl sm:text-4xl font-extrabold text-slate-900 tracking-tight">
            Semua yang Dibutuhkan untuk Pembukuan Rapi & Anti-Pusing
          </h2>
          <p className="text-sm sm:text-base text-slate-600">
            Didesain intuitif agar siapa saja bisa langsung menggunakannya tanpa perlu keahlian akuntansi rumit.
          </p>
        </div>

        {/* 9 Features Grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 sm:gap-8">
          {CORE_FEATURES.map((feature: FeatureItem) => {
            const IconComponent = iconMap[feature.iconName] || Store;
            return (
              <div
                key={feature.id}
                className="bg-slate-50/80 hover:bg-white p-6 rounded-2xl border border-slate-200/80 hover:border-emerald-200 hover:shadow-xl hover:shadow-emerald-600/5 transition-all duration-200 flex flex-col justify-between group"
              >
                <div>
                  <div className="flex items-center justify-between mb-4">
                    <div className="w-12 h-12 rounded-xl bg-emerald-100 text-emerald-700 flex items-center justify-center group-hover:scale-105 group-hover:bg-emerald-600 group-hover:text-white transition-all">
                      <IconComponent className="w-6 h-6" />
                    </div>
                    {feature.badge && (
                      <span className="text-[11px] font-bold px-2.5 py-0.5 rounded-full bg-emerald-100/80 text-emerald-800 border border-emerald-200">
                        {feature.badge}
                      </span>
                    )}
                  </div>
                  <h3 className="font-extrabold text-lg text-slate-900 mb-1 group-hover:text-emerald-700 transition-colors">
                    {feature.title}
                  </h3>
                  <p className="text-xs font-semibold text-emerald-600 uppercase tracking-wider mb-2.5">
                    {feature.subtitle}
                  </p>
                  <p className="text-sm text-slate-600 leading-relaxed font-normal">{feature.description}</p>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
};
