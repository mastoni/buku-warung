import React from 'react';
import { ChevronRight, Home } from 'lucide-react';
import { useDocsRouter } from '../../hooks/useDocsRouter';

interface DocsBreadcrumbProps {
  categoryName?: string;
  articleTitle?: string;
}

export const DocsBreadcrumb: React.FC<DocsBreadcrumbProps> = ({
  categoryName,
  articleTitle,
}) => {
  const { navigateTo } = useDocsRouter();

  return (
    <nav
      aria-label="Breadcrumb"
      className="flex items-center flex-wrap gap-1.5 text-xs text-slate-500 mb-4"
    >
      <button
        type="button"
        onClick={() => navigateTo('/')}
        className="flex items-center gap-1 hover:text-emerald-700 transition-colors"
      >
        <Home className="w-3.5 h-3.5" />
        <span>Beranda</span>
      </button>

      <ChevronRight className="w-3 h-3 text-slate-300 shrink-0" />

      <button
        type="button"
        onClick={() => navigateTo('/panduan')}
        className={`hover:text-emerald-700 transition-colors ${
          !articleTitle ? 'font-bold text-slate-800' : ''
        }`}
      >
        Panduan
      </button>

      {categoryName && (
        <>
          <ChevronRight className="w-3 h-3 text-slate-300 shrink-0" />
          <span className="text-slate-500">{categoryName}</span>
        </>
      )}

      {articleTitle && (
        <>
          <ChevronRight className="w-3 h-3 text-slate-300 shrink-0" />
          <span className="font-bold text-slate-900 truncate max-w-xs sm:max-w-md">
            {articleTitle}
          </span>
        </>
      )}
    </nav>
  );
};
