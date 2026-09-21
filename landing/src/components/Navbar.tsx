import React, { useState, useEffect } from 'react';
import { ShoppingCart, Menu, X, ArrowRight } from 'lucide-react';
import { LANDING_CONFIG } from '../data/landingData';
import { usePricing } from '../hooks/usePricingPromo';
import { useDocsRouter } from '../hooks/useDocsRouter';
import { trackBuyClick, trackWhatsAppClick } from '../tracking';

export const Navbar: React.FC = () => {
  const [isScrolled, setIsScrolled] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const { effectivePriceFormatted } = usePricing();
  const { navigateTo } = useDocsRouter();

  useEffect(() => {
    const handleScroll = () => {
      setIsScrolled(window.scrollY > 20);
    };
    window.addEventListener('scroll', handleScroll);
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  return (
    <header
      className={`fixed top-0 left-0 right-0 z-40 transition-all duration-200 ${
        isScrolled
          ? 'bg-white/95 backdrop-blur-md shadow-xs border-b border-slate-200/80 py-3'
          : 'bg-transparent py-4 md:py-6'
      }`}
    >
      <div className="max-w-6xl mx-auto px-4 sm:px-6 flex items-center justify-between">
        {/* Brand */}
        <a href="#beranda" className="flex items-center gap-3 group">
          <img
            src="/img/bukuwarung-icon.png"
            alt="Buku Warung Logo"
            className="w-9 h-9 sm:w-10 sm:h-10 rounded-xl shadow-xs border border-emerald-100 group-hover:scale-105 transition-transform"
          />
          <div>
            <div className="font-extrabold text-lg sm:text-xl tracking-tight text-slate-900 flex items-center gap-1.5">
              <span>Buku Warung</span>
              <span className="text-[10px] uppercase font-bold tracking-wider px-1.5 py-0.5 rounded-md bg-emerald-100 text-emerald-800 border border-emerald-200">
                POS
              </span>
            </div>
            <p className="text-[11px] text-slate-500 hidden sm:block">Aplikasi Kasir & Pembukuan UMKM</p>
          </div>
        </a>

        {/* Desktop Nav */}
        <nav className="hidden md:flex items-center gap-6 text-sm font-semibold text-slate-600">
          <a href="#fitur" className="hover:text-emerald-600 transition-colors">
            Fitur Utama
          </a>
          <a href="#adaptif" className="hover:text-emerald-600 transition-colors">
            Tipe Usaha
          </a>
          <a href="#tampilan" className="hover:text-emerald-600 transition-colors">
            Tampilan Layar
          </a>
          <a href="#harga" className="hover:text-emerald-600 transition-colors">
            Harga & Paket
          </a>
          <button
            type="button"
            onClick={() => navigateTo('/panduan')}
            className="hover:text-emerald-600 transition-colors font-semibold cursor-pointer"
          >
            Panduan
          </button>
          <a href="#faq" className="hover:text-emerald-600 transition-colors">
            Tanya Jawab
          </a>
        </nav>

        {/* Right CTA */}
        <div className="hidden md:flex items-center gap-3">
          <a
            href={LANDING_CONFIG.whatsappConsultationUrl}
            target="_blank"
            rel="noopener noreferrer"
            onClick={() => trackWhatsAppClick('navbar')}
            className="text-xs font-semibold text-slate-700 hover:text-emerald-700 px-3 py-2 rounded-lg hover:bg-slate-100 transition-colors"
          >
            Tanya Admin
          </a>
          <a
            href={LANDING_CONFIG.publicOrderUrl}
            onClick={() => trackBuyClick('navbar')}
            className="inline-flex items-center gap-2 bg-emerald-600 hover:bg-emerald-700 text-white font-bold text-sm px-4 py-2.5 rounded-xl shadow-xs hover:shadow-md transition-all active:scale-95"
          >
            <ShoppingCart className="w-4 h-4" />
            <span>Beli Sekarang — {effectivePriceFormatted}</span>
          </a>
        </div>

        {/* Mobile menu trigger */}
        <button
          type="button"
          onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
          className="md:hidden p-2 rounded-lg text-slate-700 hover:bg-slate-100 transition-colors focus:outline-hidden"
          aria-label={mobileMenuOpen ? 'Tutup menu' : 'Buka menu'}
        >
          {mobileMenuOpen ? <X className="w-6 h-6" /> : <Menu className="w-6 h-6" />}
        </button>
      </div>

      {/* Mobile dropdown */}
      {mobileMenuOpen && (
        <div className="md:hidden bg-white border-b border-slate-200 px-4 pt-3 pb-6 space-y-4 shadow-xl animate-in slide-in-from-top duration-200">
          <nav className="flex flex-col space-y-3 font-semibold text-slate-700">
            <a
              href="#fitur"
              onClick={() => setMobileMenuOpen(false)}
              className="px-3 py-2 rounded-lg hover:bg-slate-50"
            >
              Fitur Utama
            </a>
            <a
              href="#adaptif"
              onClick={() => setMobileMenuOpen(false)}
              className="px-3 py-2 rounded-lg hover:bg-slate-50"
            >
              Tipe Usaha
            </a>
            <a
              href="#tampilan"
              onClick={() => setMobileMenuOpen(false)}
              className="px-3 py-2 rounded-lg hover:bg-slate-50"
            >
              Tampilan Layar
            </a>
            <a
              href="#harga"
              onClick={() => setMobileMenuOpen(false)}
              className="px-3 py-2 rounded-lg hover:bg-slate-50"
            >
              Harga & Paket
            </a>
            <button
              type="button"
              onClick={() => {
                setMobileMenuOpen(false);
                navigateTo('/panduan');
              }}
              className="text-left px-3 py-2 rounded-lg hover:bg-slate-50 text-emerald-700 font-bold"
            >
              Buku Panduan v0.2.0
            </button>
            <a
              href="#faq"
              onClick={() => setMobileMenuOpen(false)}
              className="px-3 py-2 rounded-lg hover:bg-slate-50"
            >
              Tanya Jawab
            </a>
          </nav>
          <div className="pt-2 border-t border-slate-100 flex flex-col gap-2">
             <a
               href={LANDING_CONFIG.publicOrderUrl}
               onClick={() => trackBuyClick('navbar_mobile')}
               className="flex items-center justify-center gap-2 bg-emerald-600 text-white font-bold py-3 rounded-xl shadow-xs text-sm"
             >
              <span>Beli Sekarang — {effectivePriceFormatted}</span>
              <ArrowRight className="w-4 h-4" />
            </a>
             <a
               href={LANDING_CONFIG.whatsappConsultationUrl}
               target="_blank"
               rel="noopener noreferrer"
               onClick={() => trackWhatsAppClick('navbar_mobile')}
               className="text-center text-xs font-semibold text-slate-600 py-2"
             >
              Tanya Admin via WhatsApp
            </a>
          </div>
        </div>
      )}
    </header>
  );
};
