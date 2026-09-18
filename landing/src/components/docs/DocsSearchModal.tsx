import React, { useState, useEffect, useRef } from 'react';
import { Search, X, BookOpen, ArrowRight } from 'lucide-react';
import { searchDocArticles, DocArticle } from '../../data/docsData';
import { useDocsRouter } from '../../hooks/useDocsRouter';

interface DocsSearchModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const DocsSearchModal: React.FC<DocsSearchModalProps> = ({ isOpen, onClose }) => {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<DocArticle[]>([]);
  const inputRef = useRef<HTMLInputElement>(null);
  const { navigateTo } = useDocsRouter();

  useEffect(() => {
    if (isOpen) {
      setTimeout(() => inputRef.current?.focus(), 50);
    } else {
      setQuery('');
      setResults([]);
    }
  }, [isOpen]);

  // Handle Ctrl+K shortcut
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
        e.preventDefault();
        if (isOpen) {
          onClose();
        } else {
          // Trigger open via custom event or parent
          const searchBtn = document.querySelector('[aria-label="Cari panduan"]') as HTMLButtonElement;
          searchBtn?.click();
        }
      } else if (e.key === 'Escape' && isOpen) {
        onClose();
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, onClose]);

  const handleSearchChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const q = e.target.value;
    setQuery(q);
    setResults(searchDocArticles(q));
  };

  const handleSelectArticle = (slug: string) => {
    navigateTo(`/panduan/${slug}`);
    onClose();
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center pt-16 sm:pt-24 px-4 bg-slate-900/50 backdrop-blur-xs animate-in fade-in duration-150">
      <div
        className="w-full max-w-xl bg-white rounded-2xl shadow-2xl border border-slate-200 overflow-hidden animate-in zoom-in-95 duration-150"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Search Input Bar */}
        <div className="relative flex items-center border-b border-slate-200 px-4">
          <Search className="w-5 h-5 text-slate-400 shrink-0" />
          <input
            ref={inputRef}
            type="text"
            value={query}
            onChange={handleSearchChange}
            placeholder="Cari fitur, kasir, barcode, PO, printer, backup..."
            className="w-full px-3 py-4 text-sm font-medium text-slate-800 placeholder-slate-400 bg-transparent focus:outline-hidden"
          />
          {query && (
            <button
              type="button"
              onClick={() => {
                setQuery('');
                setResults([]);
              }}
              className="p-1 rounded-md text-slate-400 hover:text-slate-600 mr-2"
            >
              <X className="w-4 h-4" />
            </button>
          )}
          <kbd className="text-[10px] font-bold px-1.5 py-0.5 rounded-md bg-slate-100 border border-slate-200 text-slate-500">
            ESC
          </kbd>
        </div>

        {/* Results List */}
        <div className="max-h-80 overflow-y-auto p-2">
          {query.trim() === '' ? (
            <div className="p-6 text-center text-xs text-slate-400">
              <BookOpen className="w-8 h-8 text-slate-300 mx-auto mb-2" />
              <p>Ketik kata kunci untuk mencari di 24 bab panduan resmi Buku Warung.</p>
              <div className="flex flex-wrap items-center justify-center gap-1.5 mt-3">
                {['kasir', 'stok', 'purchase order', 'printer', 'google sheets', 'qris'].map((tag) => (
                  <button
                    key={tag}
                    type="button"
                    onClick={() => {
                      setQuery(tag);
                      setResults(searchDocArticles(tag));
                    }}
                    className="text-[11px] font-medium text-slate-600 bg-slate-100 hover:bg-emerald-50 hover:text-emerald-700 px-2 py-0.5 rounded-md transition-colors"
                  >
                    #{tag}
                  </button>
                ))}
              </div>
            </div>
          ) : results.length === 0 ? (
            <div className="p-8 text-center text-xs text-slate-500">
              <p className="font-semibold text-slate-700">Tidak ada artikel yang cocok dengan "{query}"</p>
              <p className="mt-1 text-slate-400">Coba gunakan kata kunci lain seperti kasir, stok, PO, atau printer.</p>
            </div>
          ) : (
            <ul className="space-y-1">
              {results.map((art) => (
                <li key={art.slug}>
                  <button
                    type="button"
                    onClick={() => handleSelectArticle(art.slug)}
                    className="w-full text-left p-3 rounded-xl hover:bg-slate-50 transition-colors group flex items-center justify-between gap-3 border border-transparent hover:border-slate-200"
                  >
                    <div>
                      <div className="flex items-center gap-2">
                        <span className="text-[10px] font-bold uppercase tracking-wider px-1.5 py-0.5 rounded-md bg-emerald-50 text-emerald-700 border border-emerald-200/60">
                          {art.categoryName}
                        </span>
                        <span className="text-xs font-bold text-slate-800 group-hover:text-emerald-700 transition-colors">
                          {art.title}
                        </span>
                      </div>
                      <p className="text-[11px] text-slate-500 mt-1 line-clamp-1">{art.description}</p>
                    </div>
                    <ArrowRight className="w-4 h-4 text-slate-300 group-hover:text-emerald-600 group-hover:translate-x-0.5 transition-all shrink-0" />
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </div>
  );
};
