import React from 'react';
import { FileText, Package, Wallet, BarChart3 } from 'lucide-react';
import { PROBLEMS, ProblemItem } from '../data/landingData';

const iconMap: Record<string, React.ElementType> = {
  FileText,
  Package,
  Wallet,
  BarChart3,
};

export const ProblemSolution: React.FC = () => {
  return (
    <section id="masalah" className="py-16 md:py-24 bg-white border-t border-slate-200 reveal-on-scroll">
      <div className="max-w-6xl mx-auto px-4 sm:px-6">
        <div className="text-center max-w-2xl mx-auto space-y-3 mb-12 sm:mb-16">
          <div className="inline-block px-3 py-1 rounded-lg bg-emerald-100 text-emerald-800 text-xs font-bold uppercase tracking-wider">
            Masalah & Solusi
          </div>
          <h2 className="text-2xl sm:text-4xl font-extrabold text-slate-900 tracking-tight">
            Warung Anda Masih Dicatat Manual?
          </h2>
          <p className="text-sm sm:text-base text-slate-600">
            Buku Warung dirancang untuk pemilik usaha kecil yang ingin pencatatan lebih rapi tanpa ribet.
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 sm:gap-6">
          {PROBLEMS.map((problem: ProblemItem) => {
            const Icon = iconMap[problem.icon] || FileText;
            return (
              <div
                key={problem.id}
                className="bg-slate-50/80 hover:bg-white p-5 sm:p-6 rounded-2xl border border-slate-200/80 hover:border-emerald-200 hover:shadow-lg transition-all duration-200"
              >
                <div className="flex items-start gap-3.5">
                  <div className="w-11 h-11 rounded-xl bg-red-50 text-red-600 flex items-center justify-center shrink-0">
                    <Icon className="w-5 h-5" />
                  </div>
                  <div className="space-y-2">
                    <h3 className="font-extrabold text-base sm:text-lg text-slate-900">{problem.title}</h3>
                    <p className="text-sm text-slate-600 leading-relaxed">{problem.description}</p>
                    <div className="pt-1 flex items-start gap-2 text-sm text-emerald-700 font-semibold">
                      <span className="mt-0.5">✓</span>
                      <span>{problem.solution}</span>
                    </div>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
};
