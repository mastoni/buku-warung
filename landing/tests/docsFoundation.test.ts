import { describe, it, expect } from 'vitest';
import {
  DOC_ARTICLES,
  DOC_CATEGORIES,
  DOC_VERSION,
  getDocArticleBySlug,
  getAdjacentDocArticles,
  searchDocArticles,
} from '../src/data/docsData';

describe('Documentation Content Foundation Tests', () => {
  describe('1. Canonical Structure & Order', () => {
    it('contains exactly 24 canonical chapters in consecutive order (1 to 24)', () => {
      expect(DOC_ARTICLES.length).toBe(24);
      DOC_ARTICLES.forEach((article, index) => {
        expect(article.order).toBe(index + 1);
        expect(article.slug.trim()).not.toBe('');
        expect(article.title.trim()).not.toBe('');
        expect(article.description.trim()).not.toBe('');
        expect(article.summary.trim()).not.toBe('');
        expect(article.sections.length).toBeGreaterThan(0);
      });
    });

    it('contains all 8 defined categories with valid metadata', () => {
      expect(DOC_CATEGORIES.length).toBe(8);
      const catKeys = DOC_CATEGORIES.map((c) => c.key);
      DOC_ARTICLES.forEach((art) => {
        expect(catKeys).toContain(art.category);
      });
    });

    it('enforces official documentation version metadata as v0.2.0 (Build 2)', () => {
      expect(DOC_VERSION).toBe('Buku Warung v0.2.0 (Build 2)');
      DOC_ARTICLES.forEach((art) => {
        expect(art.version).toBe('Buku Warung v0.2.0 (Build 2)');
      });
    });
  });

  describe('2. Slug Lookup & Navigation Helpers', () => {
    it('resolves article by slug case-insensitively', () => {
      const art = getDocArticleBySlug('pos');
      expect(art).toBeDefined();
      expect(art?.title).toContain('Kasir');

      const artUpper = getDocArticleBySlug('PURCHASE-ORDER');
      expect(artUpper).toBeDefined();
      expect(artUpper?.order).toBe(12);

      const invalid = getDocArticleBySlug('non-existent-slug');
      expect(invalid).toBeUndefined();
    });

    it('retrieves adjacent articles in strictly official sequence', () => {
      // First article (order 1) has no previous, next is order 2
      const first = getAdjacentDocArticles(1);
      expect(first.prev).toBeUndefined();
      expect(first.next?.order).toBe(2);
      expect(first.next?.slug).toBe('beranda');

      // Mid article (order 12 - PO) has prev (11) and next (13)
      const po = getAdjacentDocArticles(12);
      expect(po.prev?.order).toBe(11);
      expect(po.prev?.slug).toBe('pembelian');
      expect(po.next?.order).toBe(13);
      expect(po.next?.slug).toBe('kirim-po');

      // Last article (order 24) has prev (23) and no next
      const last = getAdjacentDocArticles(24);
      expect(last.prev?.order).toBe(23);
      expect(last.prev?.slug).toBe('faq');
      expect(last.next).toBeUndefined();
    });
  });

  describe('3. Client-Side Search Indexing', () => {
    it('returns empty array when search query is blank or whitespace', () => {
      expect(searchDocArticles('')).toEqual([]);
      expect(searchDocArticles('   ')).toEqual([]);
    });

    it('finds relevant articles by title match', () => {
      const res = searchDocArticles('barcode');
      expect(res.length).toBeGreaterThan(0);
      expect(res.some((r) => r.slug === 'pos' || r.slug === 'produk')).toBe(true);
    });

    it('finds relevant articles by keyword match', () => {
      const res = searchDocArticles('google drive');
      expect(res.length).toBeGreaterThan(0);
      expect(res.some((r) => r.slug === 'backup-restore')).toBe(true);
    });

    it('finds relevant articles by category name', () => {
      const res = searchDocArticles('Pembelian');
      expect(res.length).toBeGreaterThan(0);
      expect(res.some((r) => r.category === 'pembelian')).toBe(true);
    });
  });

  describe('4. Scope Boundary Validation (No Unsupported Features Claimed)', () => {
    it('does NOT claim partial PO receiving as active feature in v0.2.0', () => {
      const poArticle = getDocArticleBySlug('purchase-order');
      expect(poArticle).toBeDefined();
      expect(poArticle?.limitations).toBeDefined();
      expect(poArticle?.limitations?.some((lim) => lim.toLowerCase().includes('parsial'))).toBe(true);
    });

    it('does NOT claim realtime multi-device cloud sync without backup', () => {
      const mulaiArticle = getDocArticleBySlug('mulai');
      expect(mulaiArticle).toBeDefined();
      expect(mulaiArticle?.limitations?.some((lim) => lim.toLowerCase().includes('multi-kasir'))).toBe(true);
    });
  });
});
