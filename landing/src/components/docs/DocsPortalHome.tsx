import React from 'react';
import {
  BookOpen,
  Search,
  Compass,
  Package,
  ShoppingCart,
  Truck,
  BarChart3,
  Printer,
  ShieldCheck,
  HelpCircle,
  ArrowRight,
  FileText,
} from 'lucide-react';
import {
  DOC_CATEGORIES,
  DOC_ARTICLES,
  DocCategoryKey,
  DOC_VERSION,
} from '../../data/docsData';
import { useDocsRouter } from '../../hooks/useDocsRouter';
import { DocsBreadcrumb } from './DocsBreadcrumb';

interface DocsPortalHomeProps {
  onOpenSearch: () => void;
}

const CATEGORY_ICONS: Record<DocCategoryKey, React.FC<{ className?: string }>> = {
  mulai: Compass,
  inventori: Package,
  pos: ShoppingCart,
  pembelian: Truck,
  keuangan: BarChart3,
  hardware: Printer,
  keamanan: ShieldCheck,
  bantuan: HelpCircle,
};

export const DocsPortalHome: React.FC<DocsPortalHomeProps> = ({ onOpenSearch }) => {
  const { navigateTo } = useDocsRouter();

  const quickLinks = [
    { title: 'Mulai Setup Toko', slug: 'mulai', icon: Compass },
    { title: 'Kasir POS & Barcode', slug: 'pos', icon: ShoppingCart },
    { title: 'Purchase Order (PO)', slug: 'purchase-order', icon: Truck },
    { title: 'Printer Thermal', slug: 'printer', icon: Printer },
    { title: 'Backup Google Sheets', slug: 'backup-restore', icon: ShieldCheck },
    { title: 'Tanya Jawab (FAQ)', slug: 'faq', icon: HelpCircle },
  ];

  return (
    <div className="max-w-5xl mx-auto py-6 sm:py-8 space-y-12">
      {/* Breadcrumb */}
      <DocsBreadcrumb />

      {/* Hero Welcome Banner */}
      <section className="relative overflow-hidden rounded-3xl bg-linear-to-br from-slate-900 via-slate-800 to-emerald-950 p-6 sm:p-10 text-white shadow-xl">
        <div className="relative z-10 max-w-2xl space-y-4">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-emerald-500/20 border border-emerald-400/30 text-emerald-300 text-xs font-bold">
            <ShieldCheck className="w-3.5 h-3.5" />
            <span>Dokumentasi Resmi — {DOC_VERSION}</span>
          </div>

          <h1 className="text-2xl sm:text-4xl font-extrabold tracking-tight leading-tight">
            Pusat Panduan & Bantuan Buku Warung
          </h1>

          <p className="text-slate-300 text-sm sm:text-base font-normal leading-relaxed">
            Pelajari langkah demi langkah cara mengelola kasir POS, stok barang fisik & desimal,
            buku hutang piutang, Purchase Order supplier, laporan laba rugi, dan pencadangan Google Sheets.
          </p>

          {/* Search Bar in Hero */}
          <div className="pt-2">
            <button
              type="button"
              onClick={onOpenSearch}
              className="w-full sm:w-auto flex items-center gap-3 px-5 py-3 rounded-xl bg-white text-slate-700 hover:bg-slate-50 font-medium text-sm shadow-md transition-all hover:scale-[1.01]"
            >
              <Search className="w-4 h-4 text-emerald-600" />
              <span>Cari panduan dari 24 bab resmi...</span>
              <kbd className="hidden sm:inline text-[10px] font-bold px-1.5 py-0.5 rounded-md bg-slate-100 border border-slate-300 text-slate-500 ml-4">
                Ctrl+K
              </kbd>
            </button>
          </div>
        </div>
      </section>

      {/* Quick Access Action Grid */}
      <section className="space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-bold text-slate-900 tracking-tight">Panduan Cepat & Populer</h2>
          <span className="text-xs text-slate-400">Paling sering diakses</span>
        </div>
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
          {quickLinks.map((item) => {
            const Icon = item.icon;
            return (
              <button
                key={item.slug}
                type="button"
                onClick={() => navigateTo(`/panduan/${item.slug}`)}
                className="p-3.5 rounded-2xl bg-white border border-slate-200/80 hover:border-emerald-300 hover:bg-emerald-50/50 hover:shadow-sm transition-all text-center group flex flex-col items-center justify-center gap-2"
              >
                <div className="w-9 h-9 rounded-xl bg-slate-100 group-hover:bg-emerald-100 text-slate-700 group-hover:text-emerald-800 flex items-center justify-center transition-colors">
                  <Icon className="w-4 h-4" />
                </div>
                <span className="text-xs font-bold text-slate-800 group-hover:text-emerald-900 leading-tight">
                  {item.title}
                </span>
              </button>
            );
          })}
        </div>
      </section>

      {/* Category Grid Section */}
      <section className="space-y-6">
        <div>
          <h2 className="text-xl font-extrabold text-slate-900 tracking-tight">Kategori Panduan</h2>
          <p className="text-xs sm:text-sm text-slate-500 mt-1">
            Telusuri 24 bab panduan berdasarkan kelompok fitur operasional usaha.
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {DOC_CATEGORIES.map((cat) => {
            const Icon = CATEGORY_ICONS[cat.key] || BookOpen;
            const articles = DOC_ARTICLES.filter((a) => a.category === cat.key);

            return (
              <div
                key={cat.key}
                className="p-5 rounded-2xl bg-white border border-slate-200/80 hover:border-slate-300 shadow-2xs hover:shadow-sm transition-all space-y-3"
              >
                <div className="flex items-start gap-3">
                  <div className="w-10 h-10 rounded-xl bg-emerald-50 text-emerald-700 flex items-center justify-center shrink-0 border border-emerald-100">
                    <Icon className="w-5 h-5" />
                  </div>
                  <div>
                    <h3 className="text-sm font-bold text-slate-900">{cat.name}</h3>
                    <p className="text-xs text-slate-500 mt-0.5 leading-relaxed">{cat.description}</p>
                  </div>
                </div>

                {/* Articles List */}
                <ul className="pt-2 border-t border-slate-100 space-y-1.5">
                  {articles.map((art) => (
                    <li key={art.slug}>
                      <button
                        type="button"
                        onClick={() => navigateTo(`/panduan/${art.slug}`)}
                        className="w-full text-left flex items-center justify-between text-xs font-medium text-slate-600 hover:text-emerald-700 hover:bg-slate-50 px-2 py-1 rounded-lg transition-colors group"
                      >
                        <span className="truncate pr-2">
                          <span className="text-slate-400 font-semibold mr-1.5">{art.order}.</span>
                          {art.title}
                        </span>
                        <ArrowRight className="w-3.5 h-3.5 text-slate-300 group-hover:text-emerald-600 group-hover:translate-x-0.5 transition-all shrink-0" />
                      </button>
                    </li>
                  ))}
                </ul>
              </div>
            );
          })}
        </div>
      </section>

      {/* Official PDF Manual Specification Banner */}
      <section className="p-6 rounded-3xl bg-slate-50 border border-slate-200 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div className="flex items-start gap-3">
          <div className="w-10 h-10 rounded-xl bg-slate-900 text-white flex items-center justify-center shrink-0">
            <FileText className="w-5 h-5" />
          </div>
          <div>
            <h3 className="font-bold text-sm text-slate-900">
              Buku Manual PDF Resmi — Buku Warung v0.2.0
            </h3>
            <p className="text-xs text-slate-500 mt-0.5 max-w-md">
              Versi cetak A4 dan offline manual lengkap sedang disiapkan sesuai struktur kanonikal 24 bab panduan di atas.
            </p>
          </div>
        </div>

        <button
          type="button"
          onClick={() => navigateTo('/panduan/mulai')}
          className="inline-flex items-center gap-2 bg-slate-900 hover:bg-slate-800 text-white text-xs font-bold px-4 py-2.5 rounded-xl shadow-xs transition-all shrink-0"
        >
          <BookOpen className="w-3.5 h-3.5" />
          <span>Baca Panduan Online</span>
        </button>
      </section>
    </div>
  );
};
