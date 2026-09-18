import React, { useState } from 'react';
import { useDocsRouter } from '../../hooks/useDocsRouter';
import { getDocArticleBySlug } from '../../data/docsData';
import { DocsHeader } from './DocsHeader';
import { DocsSidebar } from './DocsSidebar';
import { DocsSearchModal } from './DocsSearchModal';
import { DocsPortalHome } from './DocsPortalHome';
import { DocArticleView } from './DocArticleView';
import { DocsSeo } from './DocsSeo';

export const DocsLayout: React.FC = () => {
  const { activeDocSlug } = useDocsRouter();
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);

  const activeArticle = activeDocSlug ? getDocArticleBySlug(activeDocSlug) : undefined;

  return (
    <div className="min-h-screen flex flex-col bg-slate-50 text-slate-900 selection:bg-emerald-500 selection:text-white">
      {/* SEO & Structured Data Sync */}
      <DocsSeo article={activeArticle} />

      {/* Docs Header */}
      <DocsHeader
        onOpenSearch={() => setSearchOpen(true)}
        onToggleSidebar={() => setSidebarOpen(!sidebarOpen)}
        sidebarOpen={sidebarOpen}
      />

      {/* Search Modal */}
      <DocsSearchModal isOpen={searchOpen} onClose={() => setSearchOpen(false)} />

      {/* Content Area with Sidebar */}
      <div className="flex-1 max-w-7xl mx-auto w-full px-4 sm:px-6 lg:px-8 flex">
        {/* Sidebar */}
        <DocsSidebar isOpen={sidebarOpen} onClose={() => setSidebarOpen(false)} />

        {/* Main Content Area */}
        <main className="flex-1 min-w-0 py-6 lg:px-8">
          {activeArticle ? (
            <DocArticleView article={activeArticle} />
          ) : (
            <DocsPortalHome onOpenSearch={() => setSearchOpen(true)} />
          )}
        </main>
      </div>

      {/* Docs Footer */}
      <footer className="bg-white border-t border-slate-200 py-8 text-center text-xs text-slate-500">
        <div className="max-w-7xl mx-auto px-4">
          <p>© 2026 SKMNetwork — Dokumentasi Resmi Aplikasi Buku Warung v0.2.0.</p>
          <p className="mt-1 text-slate-400">
            Dibuat untuk membantu jutaan UMKM dan pemilik warung di seluruh Indonesia.
          </p>
        </div>
      </footer>
    </div>
  );
};
