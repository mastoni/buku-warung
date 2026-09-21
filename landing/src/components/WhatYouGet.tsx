import React from 'react';
import { ShoppingCart, Package, BookOpen, Wallet, BarChart3, Printer, Share2 } from 'lucide-react';
import { BENEFITS, BenefitItem } from '../data/landingData';

const iconMap: Record<string, React.ElementType> = {
  ShoppingCart,
  Package,
  BookOpen,
  Wallet,
  BarChart3,
  Printer,
  Share2,
};

export const WhatYouGet: React.FC = () => {
  return (
    <section id="fitur" className="py-16 md:py-24 bg-slate-50 border-t border-slate-200 reveal-on-scroll">
      <div className="max-w-6xl mx-auto px-4 sm:px-6">
        <div className="text-center max-w-2xl mx-auto space-y-3 mb-12 sm:mb-16">
          <div className="inline-block px-3 py-1 rounded-lg bg-emerald-100 text-emerald-800 text-xs font-bold uppercase tracking-wider">
            Apa yang Anda Dapatkan
          </div>
          <h2 className="text-2xl sm:text-4xl font-extrabold text-slate-900 tracking-tight">
            Dengan Rp50.000, Anda Mendapatkan:
          </h2>
          <p className="text-sm sm:text-base text-slate-600">
            Semua modul yang dibutuhkan untuk pencatatan warung dan UMKM dalam satu aplikasi.
          </p>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 sm:gap-6">
          {BENEFITS.map((benefit: BenefitItem) => {
            const Icon = iconMap[benefit.icon] || Package;
            return (
              <div
                key={benefit.id}
                className="bg-white p-5 sm:p-6 rounded-2xl border border-slate-200/80 hover:border-emerald-200 hover:shadow-md transition-all duration-200 flex flex-col gap-3"
              >
                <div className="w-10 h-10 rounded-xl bg-emerald-100 text-emerald-700 flex items-center justify-center">
                  <Icon className="w-5 h-5" />
                </div>
                <div>
                  <h3 className="font-extrabold text-sm sm:text-base text-slate-900 mb-1">{benefit.title}</h3>
                  <p className="text-xs sm:text-sm text-slate-600 leading-relaxed">{benefit.description}</p>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
};
