import React from 'react';
import { Search, Home, Menu, ShieldCheck } from 'lucide-react';
import { useDocsRouter } from '../../hooks/useDocsRouter';
import { DOC_VERSION } from '../../data/docsData';

interface DocsHeaderProps {
  onOpenSearch: () => void;
  onToggleSidebar: () => void;
  sidebarOpen: boolean;
}

export const DocsHeader: React.FC<DocsHeaderProps> = ({
  onOpenSearch,
  onToggleSidebar,
  sidebarOpen,
}) => {
  const { navigateTo } = useDocsRouter();

  return (
    <header className="sticky top-0 z-30 bg-white/95 backdrop-blur-md border-b border-slate-200/80 shadow-2xs">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between gap-4">
        {/* Left: Mobile sidebar toggle + Brand */}
        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={onToggleSidebar}
            className="lg:hidden p-2 rounded-lg text-slate-600 hover:bg-slate-100 hover:text-slate-900 transition-colors focus:outline-hidden focus:ring-2 focus:ring-emerald-500"
            aria-label={sidebarOpen ? 'Tutup navigasi panduan' : 'Buka navigasi panduan'}
          >
            <Menu className="w-5 h-5" />
          </button>

          <button
            type="button"
            onClick={() => navigateTo('/panduan')}
            className="flex items-center gap-2.5 text-left group"
          >
            <img
              src="/img/bukuwarung-icon.png"
              alt="Buku Warung Logo"
              className="w-8 h-8 rounded-lg border border-emerald-100 shadow-2xs group-hover:scale-105 transition-transform"
            />
            <div>
              <div className="flex items-center gap-1.5">
                <span className="font-extrabold text-base tracking-tight text-slate-900">Buku Warung</span>
                <span className="text-[10px] font-bold uppercase tracking-wider px-1.5 py-0.5 rounded-md bg-emerald-100 text-emerald-800 border border-emerald-200">
                  Panduan
                </span>
              </div>
              <p className="text-[10px] text-slate-500 hidden sm:block">Pusat Bantuan & Petunjuk Penggunaan</p>
            </div>
          </button>
        </div>

        {/* Center: Search input button */}
        <div className="flex-1 max-w-md hidden sm:block">
          <button
            type="button"
            onClick={onOpenSearch}
            className="w-full flex items-center justify-between px-3.5 py-2 text-xs font-medium text-slate-400 bg-slate-50 hover:bg-slate-100 border border-slate-200 rounded-xl transition-all hover:border-slate-300 focus:outline-hidden focus:ring-2 focus:ring-emerald-500"
          >
            <span className="flex items-center gap-2">
              <Search className="w-4 h-4 text-slate-400" />
              <span>Cari panduan, fitur, kasir, atau printer...</span>
            </span>
            <kbd className="text-[10px] font-bold px-1.5 py-0.5 rounded-md bg-white border border-slate-200 text-slate-500 shadow-2xs">
              Ctrl+K
            </kbd>
          </button>
        </div>

        {/* Right: Actions */}
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={onOpenSearch}
            className="sm:hidden p-2 rounded-lg text-slate-600 hover:bg-slate-100"
            aria-label="Cari panduan"
          >
            <Search className="w-5 h-5" />
          </button>

          <div className="hidden xl:flex items-center gap-1.5 text-[11px] font-medium text-emerald-700 bg-emerald-50 border border-emerald-200/80 px-2.5 py-1 rounded-lg">
            <ShieldCheck className="w-3.5 h-3.5 text-emerald-600" />
            <span>{DOC_VERSION}</span>
          </div>

          <button
            type="button"
            onClick={() => navigateTo('/')}
            className="inline-flex items-center gap-1.5 text-xs font-semibold text-slate-700 hover:text-emerald-700 bg-white hover:bg-slate-50 border border-slate-200 px-3 py-2 rounded-xl transition-all shadow-2xs hover:border-slate-300"
          >
            <Home className="w-3.5 h-3.5" />
            <span className="hidden md:inline">Kembali ke Web</span>
          </button>
        </div>
      </div>
    </header>
  );
};
