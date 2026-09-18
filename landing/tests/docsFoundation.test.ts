import { describe, it, expect } from 'vitest';
import {
  DOC_ARTICLES,
  DOC_CATEGORIES,
  DOC_VERSION,
  getDocArticleBySlug,
  getAdjacentDocArticles,
  searchDocArticles,
} from '../src/data/docsData';

describe('Documentation Content Full Implementation (G2) Tests', () => {
  describe('1. Canonical Structure & Order (Bab 0 + 24 Chapters)', () => {
    it('contains exactly 25 canonical articles in consecutive order (0 to 24)', () => {
      expect(DOC_ARTICLES.length).toBe(25);
      DOC_ARTICLES.forEach((article, index) => {
        expect(article.order).toBe(index);
        expect(article.slug.trim()).not.toBe('');
        expect(article.title.trim()).not.toBe('');
        expect(article.description.trim()).not.toBe('');
        expect(article.summary.trim()).not.toBe('');
        expect(article.targetAudience).toBeDefined();
        expect(article.features).toBeDefined();
        expect(article.features?.length).toBeGreaterThan(0);
        expect(article.sections.length).toBeGreaterThan(0);
        expect(article.keywords.length).toBeGreaterThan(0);
      });
    });

    it('contains Bab 0 (slug: instalasi) as the first article (order: 0)', () => {
      const bab0 = DOC_ARTICLES[0];
      expect(bab0.order).toBe(0);
      expect(bab0.slug).toBe('instalasi');
      expect(bab0.title).toContain('BAB 0');
      expect(bab0.category).toBe('instalasi');
      expect(
        bab0.sections.some((s) =>
          s.paragraphs.some((p) => p.includes('9f2b282f924cd2c5388759c4c0f6feef5ec52a5bd55cd434f128b28b15a05d2b'))
        )
      ).toBe(true);
      expect(
        bab0.sections.some((s) =>
          s.paragraphs.some((p) => p.includes('Nama menu dapat berbeda tergantung merek dan versi Android'))
        )
      ).toBe(true);
    });

    it('contains all 9 defined categories with valid metadata', () => {
      expect(DOC_CATEGORIES.length).toBe(9);
      const catKeys = DOC_CATEGORIES.map((c) => c.key);
      expect(catKeys).toContain('instalasi');
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
    it('resolves Bab 0 and product chapters by slug case-insensitively', () => {
      const bab0 = getDocArticleBySlug('instalasi');
      expect(bab0).toBeDefined();
      expect(bab0?.order).toBe(0);

      const pos = getDocArticleBySlug('pos');
      expect(pos).toBeDefined();
      expect(pos?.order).toBe(7);

      const poUpper = getDocArticleBySlug('PURCHASE-ORDER');
      expect(poUpper).toBeDefined();
      expect(poUpper?.order).toBe(12);

      const invalid = getDocArticleBySlug('non-existent-slug');
      expect(invalid).toBeUndefined();
    });

    it('retrieves adjacent articles in strictly consecutive order from Bab 0 (0) to Bab 24 (24)', () => {
      // Bab 0 (order 0): prev is undefined, next is order 1 (mulai)
      const bab0Nav = getAdjacentDocArticles(0);
      expect(bab0Nav.prev).toBeUndefined();
      expect(bab0Nav.next?.order).toBe(1);
      expect(bab0Nav.next?.slug).toBe('mulai');

      // Bab 1 (order 1): prev is Bab 0 (instalasi), next is order 2 (beranda)
      const bab1Nav = getAdjacentDocArticles(1);
      expect(bab1Nav.prev?.order).toBe(0);
      expect(bab1Nav.prev?.slug).toBe('instalasi');
      expect(bab1Nav.next?.order).toBe(2);
      expect(bab1Nav.next?.slug).toBe('beranda');

      // Mid article (order 12 - PO): prev is 11 (pembelian), next is 13 (kirim-po)
      const poNav = getAdjacentDocArticles(12);
      expect(poNav.prev?.order).toBe(11);
      expect(poNav.prev?.slug).toBe('pembelian');
      expect(poNav.next?.order).toBe(13);
      expect(poNav.next?.slug).toBe('kirim-po');

      // Last article (order 24): prev is 23 (faq), next is undefined
      const lastNav = getAdjacentDocArticles(24);
      expect(lastNav.prev?.order).toBe(23);
      expect(lastNav.prev?.slug).toBe('faq');
      expect(lastNav.next).toBeUndefined();
    });

    it('ensures all relatedSlugs exist in DOC_ARTICLES', () => {
      const allSlugs = new Set(DOC_ARTICLES.map((a) => a.slug));
      DOC_ARTICLES.forEach((art) => {
        if (art.relatedSlugs) {
          art.relatedSlugs.forEach((relSlug) => {
            expect(allSlugs.has(relSlug)).toBe(true);
          });
        }
      });
    });
  });

  describe('3. Indonesian UMKM Search Indexing', () => {
    it('returns empty array when search query is blank or whitespace', () => {
      expect(searchDocArticles('')).toEqual([]);
      expect(searchDocArticles('   ')).toEqual([]);
    });

    it('finds installation and APK topics', () => {
      const res = searchDocArticles('install apk');
      expect(res.length).toBeGreaterThan(0);
      expect(res.some((r) => r.slug === 'instalasi')).toBe(true);

      const resAktivasi = searchDocArticles('aktivasi lisensi');
      expect(resAktivasi.some((r) => r.slug === 'instalasi')).toBe(true);
    });

    it('finds POS and sales queries', () => {
      const res = searchDocArticles('cara jual barang');
      expect(res.length).toBeGreaterThan(0);
      expect(res.some((r) => r.slug === 'pos')).toBe(true);
    });

    it('finds inventory & stock queries', () => {
      const res = searchDocArticles('stok habis');
      expect(res.length).toBeGreaterThan(0);
      expect(res.some((r) => r.slug === 'stok' || r.slug === 'produk')).toBe(true);
    });

    it('finds financial and debt queries', () => {
      const resPiutang = searchDocArticles('piutang');
      expect(resPiutang.some((r) => r.slug === 'pelanggan')).toBe(true);

      const resHutang = searchDocArticles('hutang supplier');
      expect(resHutang.some((r) => r.slug === 'supplier')).toBe(true);
    });

    it('finds hardware and backup queries', () => {
      const resPrinter = searchDocArticles('printer thermal');
      expect(resPrinter.some((r) => r.slug === 'printer')).toBe(true);

      const resBackup = searchDocArticles('backup google sheets');
      expect(resBackup.some((r) => r.slug === 'backup-restore')).toBe(true);
    });
  });

  describe('4. Scope Boundary & Realistic v0.2.0 Claims', () => {
    it('does NOT claim partial PO receiving as active feature in v0.2.0', () => {
      const poArticle = getDocArticleBySlug('purchase-order');
      expect(poArticle).toBeDefined();
      expect(poArticle?.limitations).toBeDefined();
      expect(poArticle?.limitations?.some((lim) => lim.toLowerCase().includes('parsial'))).toBe(true);
    });

    it('does NOT claim automatic invoice variance adjustment in v0.2.0', () => {
      const terimaArticle = getDocArticleBySlug('goods-receipt');
      expect(terimaArticle).toBeDefined();
      expect(terimaArticle?.limitations?.some((lim) => lim.toLowerCase().includes('penyesuaian harga'))).toBe(true);
    });

    it('does NOT claim realtime multi-device cloud synchronization', () => {
      const mulaiArticle = getDocArticleBySlug('mulai');
      expect(mulaiArticle).toBeDefined();
      expect(mulaiArticle?.limitations?.some((lim) => lim.toLowerCase().includes('multi-kasir'))).toBe(true);

      const backupArticle = getDocArticleBySlug('backup-restore');
      expect(backupArticle).toBeDefined();
      expect(backupArticle?.limitations?.some((lim) => lim.toLowerCase().includes('realtime'))).toBe(true);
    });
  });
});


