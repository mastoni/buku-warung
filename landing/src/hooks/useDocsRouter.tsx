import React, { useState, useEffect, useCallback, createContext, useContext } from 'react';

export interface DocsRouterState {
  currentPath: string;
  isDocsPortal: boolean;
  activeDocSlug: string | null;
  navigateTo: (path: string) => void;
}

const DocsRouterContext = createContext<DocsRouterState | null>(null);

function normalizePath(rawPath: string): { path: string; slug: string | null; isDocs: boolean } {
  let p = rawPath.trim();
  
  if (p.startsWith('#/')) {
    p = p.substring(1);
  } else if (p.startsWith('#panduan')) {
    p = '/' + p.substring(1);
  }

  if (p.length > 1 && p.endsWith('/')) {
    p = p.substring(0, p.length - 1);
  }

  const isDocs = p === '/panduan' || p.startsWith('/panduan/');
  let slug: string | null = null;

  if (p.startsWith('/panduan/')) {
    slug = p.replace('/panduan/', '').split('/')[0] || null;
  }

  return { path: p, slug, isDocs };
}

export const DocsRouterProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [currentPath, setCurrentPath] = useState<string>(() => {
    if (typeof window === 'undefined') return '/';
    if (window.location.hash.startsWith('#/panduan') || window.location.hash.startsWith('#panduan')) {
      return normalizePath(window.location.hash).path;
    }
    return normalizePath(window.location.pathname).path;
  });

  const { isDocs, slug } = normalizePath(currentPath);

  const navigateTo = useCallback((targetPath: string) => {
    if (typeof window === 'undefined') return;

    const normalized = normalizePath(targetPath);
    setCurrentPath(normalized.path);

    if (targetPath.startsWith('#') && !targetPath.startsWith('#/panduan')) {
      if (normalized.path !== '/') {
        window.history.pushState({}, '', '/' + targetPath);
      }
      const el = document.querySelector(targetPath);
      if (el) {
        el.scrollIntoView({ behavior: 'smooth' });
      }
      return;
    }

    try {
      window.history.pushState({}, '', normalized.path);
    } catch {
      window.location.hash = normalized.path;
    }

    window.scrollTo({ top: 0, behavior: 'smooth' });
  }, []);

  useEffect(() => {
    const handlePopState = () => {
      const hash = window.location.hash;
      if (hash.startsWith('#/panduan') || hash.startsWith('#panduan')) {
        setCurrentPath(normalizePath(hash).path);
      } else {
        setCurrentPath(normalizePath(window.location.pathname).path);
      }
    };

    window.addEventListener('popstate', handlePopState);
    return () => window.removeEventListener('popstate', handlePopState);
  }, []);

  const value: DocsRouterState = {
    currentPath,
    isDocsPortal: isDocs,
    activeDocSlug: slug,
    navigateTo,
  };

  return <DocsRouterContext.Provider value={value}>{children}</DocsRouterContext.Provider>;
};

export function useDocsRouter(): DocsRouterState {
  const context = useContext(DocsRouterContext);
  if (!context) {
    throw new Error('useDocsRouter must be used within a DocsRouterProvider');
  }
  return context;
}
