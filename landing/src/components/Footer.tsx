import React from 'react';
import { ShoppingCart, MessageCircle, ShieldCheck } from 'lucide-react';
import { LANDING_CONFIG } from '../data/landingData';
import { usePricing } from '../hooks/usePricingPromo';
import { useDocsRouter } from '../hooks/useDocsRouter';

export const Footer: React.FC = () => {
  const { effectivePriceFormatted } = usePricing();
  const { navigateTo } = useDocsRouter();

  return (
    <footer className="bg-slate-900 text-slate-300 py-12 md:py-16 border-t border-slate-800">
      <div className="max-w-6xl mx-auto px-4 sm:px-6">
        <div className="grid grid-cols-1 md:grid-cols-12 gap-8 pb-12 border-b border-slate-800">
          {/* Brand Col */}
          <div className="md:col-span-6 space-y-4">
            <div className="flex items-center gap-3">
              <img
                src="/img/bukuwarung-icon.png"
                alt="Buku Warung Logo"
                className="w-10 h-10 rounded-xl bg-white p-0.5"
              />
              <div>
                <span className="font-extrabold text-xl text-white tracking-tight">Buku Warung</span>
                <p className="text-xs text-slate-400">Aplikasi Kasir POS & Pembukuan UMKM</p>
              </div>
            </div>
            <p className="text-sm text-slate-400 max-w-sm font-normal leading-relaxed">
              Membantu jutaan pemilik warung dan pelaku UMKM di seluruh Indonesia mencatat kasir, stok, hutang piutang,
              dan laporan keuangan secara mandiri tanpa biaya bulanan.
            </p>
            <div className="flex items-center gap-2 text-xs text-emerald-400 font-semibold">
              <ShieldCheck className="w-4 h-4" />
              <span>Lisensi Resmi & Layanan Resmi by SKMNetwork</span>
            </div>
          </div>

          {/* Quick Links */}
          <div className="md:col-span-3 space-y-3">
            <h4 className="font-bold text-sm text-white uppercase tracking-wider">Navigasi</h4>
            <ul className="space-y-2 text-xs sm:text-sm text-slate-400">
              <li>
                <a href="#fitur" className="hover:text-emerald-400 transition-colors">
                  Fitur Utama
                </a>
              </li>
              <li>
                <a href="#tampilan" className="hover:text-emerald-400 transition-colors">
                  Tampilan Layar
                </a>
              </li>
              <li>
                <a href="#harga" className="hover:text-emerald-400 transition-colors">
                  Harga & Paket
                </a>
              </li>
              <li>
                <button
                  type="button"
                  onClick={() => navigateTo('/panduan')}
                  className="hover:text-emerald-400 transition-colors text-left font-medium cursor-pointer"
                >
                  Pusat Panduan Pengguna
                </button>
              </li>
              <li>
                <a href="#faq" className="hover:text-emerald-400 transition-colors">
                  Tanya Jawab
                </a>
              </li>
            </ul>
          </div>

          {/* Direct Actions */}
          <div className="md:col-span-3 space-y-3">
            <h4 className="font-bold text-sm text-white uppercase tracking-wider">Pemesanan & CS</h4>
            <div className="space-y-2">
              <a
                href={LANDING_CONFIG.publicOrderUrl}
                className="inline-flex items-center gap-2 text-xs font-bold text-emerald-400 hover:text-emerald-300 transition-colors"
              >
                <ShoppingCart className="w-3.5 h-3.5" />
                <span>Beli Lisensi ({effectivePriceFormatted})</span>
              </a>
              <div>
                <a
                  href={LANDING_CONFIG.whatsappConsultationUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex items-center gap-2 text-xs font-semibold text-slate-400 hover:text-emerald-400 transition-colors"
                >
                  <MessageCircle className="w-3.5 h-3.5 text-emerald-500" />
                  <span>CS WhatsApp SKMNetwork</span>
                </a>
              </div>
            </div>
          </div>
        </div>

        {/* Copyright */}
        <div className="pt-8 flex flex-col sm:flex-row items-center justify-between gap-4 text-xs text-slate-500">
          <p>© 2026 SKMNetwork. Seluruh hak cipta dilindungi undang-undang.</p>
          <p className="flex items-center gap-1">
            <span>Produk Resmi</span>
            <span className="text-slate-400 font-semibold">SKMNetwork Software & Solutions</span>
          </p>
        </div>
      </div>
    </footer>
  );
};
