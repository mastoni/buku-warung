import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, it, expect } from 'vitest';
import { DOC_ARTICLES, DOC_VERSION, OFFICIAL_APK_SHA256 } from '../src/data/docsData';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const ROOT_DIR = path.resolve(__dirname, '..');
const PDF_PATH = path.resolve(ROOT_DIR, 'public', 'downloads', 'Buku-Warung-v0.2.0-Panduan-Pengguna.pdf');

describe('Official PDF User Manual (G3) Tests', () => {
  it('verifies the official PDF manual exists at canonical static path', () => {
    expect(fs.existsSync(PDF_PATH)).toBe(true);
  });

  it('verifies the PDF manual has valid non-zero size and is formatted as a valid PDF', () => {
    const stat = fs.statSync(PDF_PATH);
    expect(stat.size).toBeGreaterThan(100 * 1024); // Greater than 100 KB

    const buffer = fs.readFileSync(PDF_PATH);
    const header = buffer.subarray(0, 8).toString('ascii');
    expect(header.startsWith('%PDF-')).toBe(true);
  });

  it('ensures all 25 canonical articles (Bab 0 to 24) are present in the documentation baseline', () => {
    expect(DOC_ARTICLES.length).toBe(25);
    const orders = DOC_ARTICLES.map((a) => a.order);
    for (let i = 0; i <= 24; i++) {
      expect(orders).toContain(i);
    }
  });

  it('verifies Bab 0 metadata matches official APK distribution constraints', () => {
    const bab0 = DOC_ARTICLES.find((a) => a.order === 0);
    expect(bab0).toBeDefined();
    expect(bab0?.slug).toBe('instalasi');
    expect(
      bab0?.sections.some((s) =>
        s.paragraphs.some((p) => p.includes(OFFICIAL_APK_SHA256))
      )
    ).toBe(true);
  });

  it('verifies scope boundaries in the manual source are strictly preserved', () => {
    const po = DOC_ARTICLES.find((a) => a.slug === 'purchase-order');
    expect(po?.limitations?.some((lim) => lim.toLowerCase().includes('parsial'))).toBe(true);

    const gr = DOC_ARTICLES.find((a) => a.slug === 'goods-receipt');
    expect(gr?.limitations?.some((lim) => lim.toLowerCase().includes('penyesuaian harga'))).toBe(true);

    const backup = DOC_ARTICLES.find((a) => a.slug === 'backup-restore');
    expect(backup?.limitations?.some((lim) => lim.toLowerCase().includes('realtime'))).toBe(true);
  });
});
