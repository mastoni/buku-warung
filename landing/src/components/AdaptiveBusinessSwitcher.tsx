import React, { useState } from 'react';
import {
  Store,
  Pill,
  Hammer,
  Wrench,
  CheckCircle2,
  Share2,
  Sparkles,
  ArrowRight,
  Package,
  Layers,
  SlidersHorizontal,
} from 'lucide-react';
import { ADAPTIVE_BUSINESS_PROFILES, AdaptiveBusinessProfile, LANDING_CONFIG } from '../data/landingData';
import { trackBuyClick } from '../tracking';

const iconMap: Record<string, React.ElementType> = {
  Store,
  Pill,
  Hammer,
  Wrench,
};

export const AdaptiveBusinessSwitcher: React.FC = () => {
  const [activeProfileId, setActiveProfileId] = useState<string>('WARUNG_SEMBAKO');

  const activeProfile: AdaptiveBusinessProfile =
    ADAPTIVE_BUSINESS_PROFILES.find((p) => p.id === activeProfileId) || ADAPTIVE_BUSINESS_PROFILES[0];

  const ProfileIcon = iconMap[activeProfile.iconName] || Store;

  return (
    <section id="adaptif" className="py-16 md:py-24 bg-slate-50 border-b border-slate-200/80 reveal-on-scroll">
      <div className="max-w-6xl mx-auto px-4 sm:px-6">
        {/* Section Header */}
        <div className="text-center max-w-3xl mx-auto space-y-3 mb-10 sm:mb-14">
          <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-lg bg-emerald-100 text-emerald-800 text-xs font-bold uppercase tracking-wider">
            <Sparkles className="w-3.5 h-3.5 text-emerald-600" />
            <span>Satu Aplikasi, Adaptif Semua Usaha</span>
          </div>
          <h2 className="text-2xl sm:text-4xl font-extrabold text-slate-900 tracking-tight">
            Terminologi & Katalog Menyesuaikan Jenis Toko Anda
          </h2>
          <p className="text-sm sm:text-base text-slate-600 leading-relaxed">
            Buku Warung tidak memaksakan istilah kaku. Menu master barang, nota kasir, hingga format katalog WhatsApp otomatis beradaptasi dengan karakter bisnis Anda.
          </p>
        </div>

        {/* Business Type Selector Tabs */}
        <div
          role="tablist"
          aria-label="Pilihan Tipe Usaha Adaptif"
          className="grid grid-cols-2 sm:grid-cols-4 gap-2.5 sm:gap-4 mb-8"
        >
          {ADAPTIVE_BUSINESS_PROFILES.map((profile) => {
            const Icon = iconMap[profile.iconName] || Store;
            const isActive = profile.id === activeProfileId;

            return (
              <button
                key={profile.id}
                role="tab"
                id={`tab-${profile.id}`}
                aria-selected={isActive}
                aria-controls={`panel-${profile.id}`}
                onClick={() => setActiveProfileId(profile.id)}
                className={`flex flex-col items-center justify-center p-4 rounded-2xl border text-center transition-all duration-200 cursor-pointer focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:ring-offset-2 ${
                  isActive
                    ? 'bg-emerald-600 text-white border-emerald-600 shadow-lg shadow-emerald-600/20 scale-[1.02]'
                    : 'bg-white text-slate-700 border-slate-200/80 hover:bg-slate-100 hover:border-slate-300'
                }`}
              >
                <div
                  className={`w-11 h-11 rounded-xl flex items-center justify-center mb-2.5 transition-colors ${
                    isActive ? 'bg-white/20 text-white' : 'bg-emerald-50 text-emerald-700'
                  }`}
                >
                  <Icon className="w-5 h-5" />
                </div>
                <span className="font-bold text-xs sm:text-sm leading-snug">
                  {profile.name}
                </span>
                <span
                  className={`text-[10px] font-medium mt-1 px-2 py-0.5 rounded-full ${
                    isActive
                      ? 'bg-emerald-700/80 text-emerald-100'
                      : 'bg-slate-100 text-slate-500'
                  }`}
                >
                  {profile.badge}
                </span>
              </button>
            );
          })}
        </div>

        {/* Interactive Adaptive Showcase Panel */}
        <div
          id={`panel-${activeProfile.id}`}
          role="tabpanel"
          aria-labelledby={`tab-${activeProfile.id}`}
          className="bg-white rounded-3xl border border-slate-200/80 shadow-xl shadow-slate-200/40 p-6 sm:p-8 lg:p-10 transition-all duration-300"
        >
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
            {/* Left Column: Business Profile Info & Terminology Map */}
            <div className="lg:col-span-6 space-y-6">
              <div className="flex items-start gap-4">
                <div className="w-14 h-14 rounded-2xl bg-emerald-100 text-emerald-700 flex items-center justify-center shrink-0">
                  <ProfileIcon className="w-7 h-7" />
                </div>
                <div>
                  <div className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full bg-emerald-50 text-emerald-700 text-xs font-semibold mb-1 border border-emerald-200/60">
                    <SlidersHorizontal className="w-3 h-3" />
                    <span>Mode Adaptif Aktif</span>
                  </div>
                  <h3 className="text-xl sm:text-2xl font-extrabold text-slate-900">
                    {activeProfile.name}
                  </h3>
                  <p className="text-xs sm:text-sm text-slate-600 mt-1">
                    {activeProfile.summary}
                  </p>
                </div>
              </div>

              {/* Terminology Comparison Table */}
              <div className="bg-slate-50 rounded-2xl p-4 sm:p-5 border border-slate-200/80 space-y-3">
                <div className="text-xs font-bold text-slate-500 uppercase tracking-wider flex items-center gap-1.5">
                  <Layers className="w-3.5 h-3.5 text-emerald-600" />
                  <span>Adaptasi Terminologi di Aplikasi</span>
                </div>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-1">
                  <div className="bg-white p-3 rounded-xl border border-slate-200/60 shadow-xs">
                    <div className="text-[11px] font-medium text-slate-500">Menu Master Barang</div>
                    <div className="text-sm font-extrabold text-emerald-700 mt-0.5 flex items-center gap-1.5">
                      <Package className="w-4 h-4 text-emerald-600" />
                      <span>{activeProfile.productTerm} & Stok</span>
                    </div>
                  </div>
                  <div className="bg-white p-3 rounded-xl border border-slate-200/60 shadow-xs">
                    <div className="text-[11px] font-medium text-slate-500">Katalog Promosi WhatsApp</div>
                    <div className="text-sm font-extrabold text-emerald-700 mt-0.5 flex items-center gap-1.5">
                      <Share2 className="w-4 h-4 text-emerald-600" />
                      <span>{activeProfile.catalogTerm}</span>
                    </div>
                  </div>
                </div>
              </div>

              {/* Key Feature Highlights */}
              <div className="space-y-2.5">
                <div className="text-xs font-bold text-slate-500 uppercase tracking-wider">
                  Keunggulan Khusus {activeProfile.name}
                </div>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 text-xs sm:text-sm text-slate-700">
                  {activeProfile.keyHighlights.map((highlight, idx) => (
                    <div key={idx} className="flex items-start gap-2">
                      <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0 mt-0.5" />
                      <span>{highlight}</span>
                    </div>
                  ))}
                </div>
              </div>
            </div>

            {/* Right Column: Live Data Simulation & WhatsApp Catalog Preview */}
            <div className="lg:col-span-6 space-y-4">
              {/* Product List Mockup */}
              <div className="bg-slate-900 text-white rounded-2xl p-4 sm:p-5 shadow-lg">
                <div className="flex items-center justify-between border-b border-slate-800 pb-3 mb-3">
                  <div className="flex items-center gap-2">
                    <span className="w-2.5 h-2.5 rounded-full bg-emerald-500 animate-pulse"></span>
                    <span className="text-xs font-bold uppercase tracking-wider text-slate-300">
                      Simulasi Master {activeProfile.productTerm}
                    </span>
                  </div>
                  <span className="text-[11px] font-semibold text-emerald-400 bg-emerald-950/80 px-2 py-0.5 rounded-md border border-emerald-800/60">
                    Mode Offline
                  </span>
                </div>

                <div className="space-y-2.5">
                  {activeProfile.sampleProducts.map((item, idx) => (
                    <div
                      key={idx}
                      className="flex items-center justify-between p-2.5 rounded-xl bg-slate-800/80 hover:bg-slate-800 border border-slate-700/60 text-xs sm:text-sm transition-colors"
                    >
                      <div className="space-y-0.5">
                        <div className="font-bold text-white flex items-center gap-1.5">
                          <span>{item.name}</span>
                          {item.type && (
                            <span className="text-[10px] font-semibold px-1.5 py-0.2 rounded bg-slate-700 text-slate-300">
                              {item.type}
                            </span>
                          )}
                        </div>
                        <div className="text-[11px] text-slate-400">
                          Satuan: <span className="text-slate-200">{item.unit}</span> | Stok:{' '}
                          <span className="text-emerald-400 font-semibold">{item.stock}</span>
                        </div>
                      </div>
                      <div className="font-extrabold text-emerald-400 text-right">
                        {item.price}
                      </div>
                    </div>
                  ))}
                </div>
              </div>

              {/* WhatsApp Share Text Simulation */}
              <div className="bg-emerald-50/90 rounded-2xl p-4 sm:p-5 border border-emerald-200/80 text-xs sm:text-sm">
                <div className="flex items-center gap-2 text-emerald-900 font-bold mb-2">
                  <Share2 className="w-4 h-4 text-emerald-700" />
                  <span>Preview Format WhatsApp Otomatis:</span>
                </div>
                <div className="bg-white p-3 rounded-xl border border-emerald-200 text-slate-700 font-mono text-[11px] sm:text-xs leading-relaxed space-y-1">
                  <p className="text-slate-600">Halo Kak! Berikut *{activeProfile.catalogTerm.toUpperCase()}* terupdate kami:</p>
                  {activeProfile.sampleProducts.map((item, idx) => (
                    <p key={idx} className="text-slate-800">
                      • *{item.name}*: {item.price} /{item.unit} (Tersedia {item.stock})
                    </p>
                  ))}
                  <p className="text-slate-500 pt-1">Bisa langsung pesan dan kami siapkan pesanannya! Terima kasih 🙏</p>
                </div>
              </div>
            </div>
          </div>

          {/* Section Bottom CTA */}
          <div className="mt-8 pt-6 border-t border-slate-100 flex flex-col sm:flex-row items-center justify-between gap-4">
            <div className="text-xs sm:text-sm text-slate-600 text-center sm:text-left">
              Cocok untuk <span className="font-bold text-slate-900">{activeProfile.name}</span> Anda.
              Tanpa setup rumit, langsung siap jualan dalam 2 menit.
            </div>
             <a
               href={LANDING_CONFIG.publicOrderUrl}
               onClick={() => trackBuyClick('adaptive_business')}
               className="inline-flex items-center justify-center gap-2 bg-emerald-600 hover:bg-emerald-700 text-white font-bold text-xs sm:text-sm px-5 py-2.5 rounded-xl shadow-xs hover:shadow-md transition-all active:scale-95 whitespace-nowrap"
             >
              <span>Mulai Pakai Buku Warung</span>
              <ArrowRight className="w-4 h-4" />
            </a>
          </div>
        </div>
      </div>
    </section>
  );
};
