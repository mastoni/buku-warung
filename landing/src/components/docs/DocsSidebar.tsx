import React from 'react';
import {
  BookOpen,
  Compass,
  Package,
  ShoppingCart,
  Truck,
  BarChart3,
  Printer,
  ShieldCheck,
  HelpCircle,
  ChevronRight,
  X,
} from 'lucide-react';
import {
  DOC_CATEGORIES,
  DOC_ARTICLES,
  DocCategoryKey,
  DOC_VERSION,
} from '../../data/docsData';
import { useDocsRouter } from '../../hooks/useDocsRouter';

interface DocsSidebarProps {
  isOpen: boolean;
  onClose: () => void;
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

export const DocsSidebar: React.FC<DocsSidebarProps> = ({ isOpen, onClose }) => {
  const { activeDocSlug, currentPath, navigateTo } = useDocsRouter();

  const handleSelectArticle = (slug: string) => {
    navigateTo(`/panduan/${slug}`);
    onClose();
  };

  const handleSelectPortalHome = () => {
    navigateTo('/panduan');
    onClose();
  };

  return (
    <>
      {/* Mobile Backdrop */}
      {isOpen && (
        <div
          className="fixed inset-0 z-40 bg-slate-900/40 backdrop-blur-xs lg:hidden animate-in fade-in duration-200"
          onClick={onClose}
          aria-hidden="true"
        />
      )}

      {/* Sidebar Container */}
      <aside
        className={`fixed top-0 bottom-0 left-0 z-40 w-72 sm:w-80 bg-white border-r border-slate-200 p-4 overflow-y-auto transition-transform duration-200 ease-in-out lg:static lg:translate-x-0 lg:z-0 lg:h-[calc(100vh-4rem)] lg:sticky lg:top-16 ${
          isOpen ? 'translate-x-0 shadow-2xl' : '-translate-x-full lg:shadow-none'
        }`}
      >
        {/* Mobile Header in Sidebar */}
        <div className="flex items-center justify-between pb-3 mb-3 border-b border-slate-100 lg:hidden">
          <div className="flex items-center gap-2">
            <BookOpen className="w-5 h-5 text-emerald-600" />
            <span className="font-bold text-sm text-slate-900">Daftar Panduan</span>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-1.5 rounded-lg text-slate-500 hover:bg-slate-100"
            aria-label="Tutup navigasi"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Portal Home Button */}
        <button
          type="button"
          onClick={handleSelectPortalHome}
          className={`w-full flex items-center justify-between px-3 py-2.5 rounded-xl text-xs font-bold transition-all mb-4 ${
            currentPath === '/panduan'
              ? 'bg-emerald-600 text-white shadow-xs'
              : 'text-slate-700 hover:bg-slate-100 hover:text-emerald-700'
          }`}
        >
          <span className="flex items-center gap-2">
            <BookOpen className="w-4 h-4" />
            <span>Pusat Panduan v0.2.0</span>
          </span>
          <ChevronRight className={`w-3.5 h-3.5 ${currentPath === '/panduan' ? 'text-white' : 'text-slate-400'}`} />
        </button>

        {/* Categories & Articles Tree */}
        <nav className="space-y-5">
          {DOC_CATEGORIES.map((cat) => {
            const Icon = CATEGORY_ICONS[cat.key] || BookOpen;
            const articles = DOC_ARTICLES.filter((a) => a.category === cat.key);

            return (
              <div key={cat.key} className="space-y-1">
                {/* Category Header */}
                <div className="flex items-center gap-2 px-2 py-1 text-[11px] font-bold uppercase tracking-wider text-slate-400">
                  <Icon className="w-3.5 h-3.5 text-slate-400" />
                  <span>{cat.name}</span>
                </div>

                {/* Articles in Category */}
                <ul className="space-y-0.5 pl-2">
                  {articles.map((art) => {
                    const isActive = activeDocSlug === art.slug;
                    return (
                      <li key={art.slug}>
                        <button
                          type="button"
                          onClick={() => handleSelectArticle(art.slug)}
                          className={`w-full text-left flex items-start gap-2 px-2.5 py-1.5 rounded-lg text-xs transition-all ${
                            isActive
                              ? 'bg-emerald-50 text-emerald-800 font-bold border-l-2 border-emerald-600 pl-2 shadow-2xs'
                              : 'text-slate-600 hover:text-slate-900 hover:bg-slate-50 font-medium'
                          }`}
                        >
                          <span className="text-[10px] font-semibold text-slate-400 mt-0.5 w-4 shrink-0">
                            {art.order}.
                          </span>
                          <span className="leading-snug">{art.title}</span>
                        </button>
                      </li>
                    );
                  })}
                </ul>
              </div>
            );
          })}
        </nav>

        {/* Footer Info in Sidebar */}
        <div className="mt-8 pt-4 border-t border-slate-100 px-2 text-[11px] text-slate-400 space-y-1">
          <p className="font-semibold text-slate-600">{DOC_VERSION}</p>
          <p>© 2026 SKMNetwork</p>
        </div>
      </aside>
    </>
  );
};
