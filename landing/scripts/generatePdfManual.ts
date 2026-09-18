import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import PDFDocument from 'pdfkit';
import {
  DOC_ARTICLES,
  DOC_CATEGORIES,
  DOC_VERSION,
  DOC_LAST_UPDATED,
  OFFICIAL_APK_SHA256,
  DocArticle,
} from '../src/data/docsData.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const ROOT_DIR = path.resolve(__dirname, '..');
const PUBLIC_DIR = path.resolve(ROOT_DIR, 'public');
const DOWNLOADS_DIR = path.resolve(PUBLIC_DIR, 'downloads');
const OUTPUT_PDF_PATH = path.resolve(DOWNLOADS_DIR, 'Buku-Warung-v0.2.0-Panduan-Pengguna.pdf');

// PDF Colors
const COLORS = {
  primary: '#0F172A',      // Slate 900
  secondary: '#334155',    // Slate 700
  muted: '#64748B',        // Slate 500
  accent: '#2563EB',       // Blue 600
  accentDark: '#1D4ED8',   // Blue 700
  accentLight: '#EFF6FF',  // Blue 50
  accentBorder: '#BFDBFE', // Blue 200
  cardBg: '#F8FAFC',       // Slate 50
  cardBorder: '#E2E8F0',   // Slate 200
  infoBg: '#F0F9FF',       // Sky 50
  infoBorder: '#0EA5E9',   // Sky 500
  warningBg: '#FFFBEB',    // Amber 50
  warningBorder: '#F59E0B',// Amber 500
  tipBg: '#F0FDF4',        // Emerald 50
  tipBorder: '#10B981',    // Emerald 500
  dangerBg: '#FEF2F2',     // Red 50
  dangerBorder: '#EF4444', // Red 500
  white: '#FFFFFF',
};

// Ensure downloads directory exists
if (!fs.existsSync(DOWNLOADS_DIR)) {
  fs.mkdirSync(DOWNLOADS_DIR, { recursive: true });
}

interface ChapterPageMap {
  [order: number]: number;
}

function buildPdfDocument(
  outputPath: string,
  tocPageNumbers: ChapterPageMap | null = null,
  knownTotalPages: number = 0
): Promise<{ pageCount: number; chapterPages: ChapterPageMap }> {
  return new Promise((resolve, reject) => {
    const doc = new PDFDocument({
      size: 'A4',
      margins: { top: 50, bottom: 50, left: 45, right: 45 },
      autoFirstPage: false,
      bufferPages: true,
    });

    const writeStream = fs.createWriteStream(outputPath);
    doc.pipe(writeStream);

    const chapterPages: ChapterPageMap = {};
    let helpSectionPage = 0;

    // Helper: Check space and add page if needed
    function ensureSpace(neededHeight: number) {
      if (doc.y + neededHeight > doc.page.height - doc.page.margins.bottom) {
        doc.addPage();
      }
    }

    // =========================================================================
    // 1. COVER PAGE
    // =========================================================================
    doc.addPage({ margins: { top: 0, bottom: 0, left: 0, right: 0 } });
    const pageWidth = doc.page.width;
    const pageHeight = doc.page.height;

    // Dark professional background header
    doc.rect(0, 0, pageWidth, 280).fill(COLORS.primary);

    // Accent line
    doc.rect(0, 275, pageWidth, 5).fill(COLORS.accent);

    // Brand icon box
    doc.roundedRect(50, 45, 54, 54, 12).fill(COLORS.accent);
    doc.fillColor(COLORS.white).font('Helvetica-Bold').fontSize(26).text('BW', 58, 58);

    // Cover Titles
    doc.fillColor(COLORS.white).font('Helvetica-Bold').fontSize(32).text('Buku Warung', 120, 50);
    doc.fillColor('#94A3B8').font('Helvetica').fontSize(16).text('Aplikasi Kasir & Pembukuan UMKM 100% Offline', 120, 90);

    // Document Type Banner
    doc.fillColor('#38BDF8').font('Helvetica-Bold').fontSize(14).text('PANDUAN PENGGUNA RESMI (OFFICIAL USER MANUAL)', 50, 150);
    doc.fillColor(COLORS.white).font('Helvetica-Bold').fontSize(22).text('Buku Warung v0.2.0 — Build 2', 50, 175);
    doc.fillColor('#CBD5E1').font('Helvetica').fontSize(11).text(
      'Panduan lengkap operasional penjualan kasir POS, stok barang, bahan bakar (FUEL), produk digital & jasa, pembelian & Purchase Order, buku kas, laporan keuangan, printer thermal, dan backup Google Sheets.',
      50,
      205,
      { width: pageWidth - 100, lineGap: 3 }
    );

    // White body section on cover
    doc.y = 310;

    // Feature highlights cards grid on cover
    const highlights = [
      { title: '100% Offline-First', desc: 'Semua data tersimpan di HP Anda tanpa ketergantungan cloud realtime.' },
      { title: 'Siklus Kasir & PO Lengkap', desc: 'Transaksi kasir, barcode, piutang, hutang, kulakan, dan status PO resmi.' },
      { title: 'Adaptif 19 Jenis Usaha', desc: 'Warung sembako, apotek, bengkel, toko bangunan, jasa, dan pulsa.' },
      { title: 'Cetak Thermal & PDF', desc: 'Dukungan printer thermal 58mm/80mm ESC/POS dan ekspor laporan PDF.' },
    ];

    let cardY = 305;
    highlights.forEach((h, i) => {
      const col = i % 2;
      const row = Math.floor(i / 2);
      const cx = 50 + col * 255;
      const cy = cardY + row * 85;

      doc.roundedRect(cx, cy, 240, 75, 8).fillAndStroke(COLORS.cardBg, COLORS.cardBorder);
      doc.fillColor(COLORS.accent).font('Helvetica-Bold').fontSize(12).text(h.title, cx + 14, cy + 12);
      doc.fillColor(COLORS.secondary).font('Helvetica').fontSize(9.5).text(h.desc, cx + 14, cy + 30, {
        width: 212,
        lineGap: 2,
      });
    });

    // Target Audience banner
    const audY = 490;
    doc.roundedRect(50, audY, pageWidth - 100, 70, 8).fillAndStroke(COLORS.accentLight, COLORS.accentBorder);
    doc.fillColor(COLORS.accentDark).font('Helvetica-Bold').fontSize(11).text('UNTUK SIAPA PANDUAN INI?', 66, audY + 12);
    doc.fillColor(COLORS.secondary).font('Helvetica').fontSize(9.5).text(
      'Panduan ini disusun dalam Bahasa Indonesia yang praktis dan ramah pengguna untuk pemilik warung, pengelola toko kelontong, kasir, apoteker, pemilik bengkel, teknisi, dan pelaku UMKM di seluruh Indonesia.',
      66,
      audY + 30,
      { width: pageWidth - 132, lineGap: 2 }
    );

    // Bottom official metadata footer on cover
    const footY = pageHeight - 110;
    doc.rect(0, footY, pageWidth, 110).fill(COLORS.cardBg);
    doc.rect(0, footY, pageWidth, 1).fill(COLORS.cardBorder);

    doc.fillColor(COLORS.primary).font('Helvetica-Bold').fontSize(10).text('INFORMASI DISTRIBUSI RESMI', 50, footY + 15);
    doc.fillColor(COLORS.secondary).font('Helvetica').fontSize(8.5).text('Website Resmi: https://bukuwarung.skmnetwork.com', 50, footY + 32);
    doc.fillColor(COLORS.secondary).font('Helvetica').fontSize(8.5).text('Lisensi: Sekali Beli Seumur Hidup (Lifetime 1 Perangkat HP)', 50, footY + 46);
    doc.fillColor(COLORS.secondary).font('Helvetica').fontSize(8.5).text(`Versi Baseline: ${DOC_VERSION} • Pembaruan: ${DOC_LAST_UPDATED}`, 50, footY + 60);

    doc.fillColor(COLORS.muted).font('Helvetica').fontSize(8).text(
      'Hak Cipta © 2026 SKMNetwork. Seluruh hak cipta dilindungi undang-undang.',
      50,
      footY + 82
    );

    // =========================================================================
    // 2. INFORMASI VERSI & ARSITEKTUR PRODUK
    // =========================================================================
    doc.addPage();
    doc.fillColor(COLORS.primary).font('Helvetica-Bold').fontSize(20).text('Informasi Versi & Ketentuan Produk', 45, 50);
    doc.rect(45, 75, doc.page.width - 90, 2).fill(COLORS.accent);

    doc.y = 90;
    doc.fillColor(COLORS.secondary).font('Helvetica').fontSize(10).text(
      'Dokumen ini adalah buku panduan pengguna resmi (Official User Manual) untuk aplikasi Buku Warung Android. Panduan ini menjelaskan seluruh modul operasional, alur transaksi kasir, manajemen stok barang, penerimaan Purchase Order, buku kas, pelaporan keuangan, serta pencadangan data.',
      { lineGap: 3 }
    );

    doc.moveDown(1);
    doc.roundedRect(45, doc.y, doc.page.width - 90, 115, 6).fillAndStroke(COLORS.cardBg, COLORS.cardBorder);
    const vY = doc.y + 12;
    doc.fillColor(COLORS.primary).font('Helvetica-Bold').fontSize(11).text('SPESIFIKASI RILIS PRODUK RESMI', 60, vY);
    doc.fillColor(COLORS.secondary).font('Helvetica').fontSize(9.5);
    doc.text(`• Nama Aplikasi: Buku Warung Android`, 60, vY + 20);
    doc.text(`• Versi Resmi: v0.2.0 (Build / versionCode: 2)`, 60, vY + 35);
    doc.text(`• Paket Distribusi: app-release.apk resmi via https://bukuwarung.skmnetwork.com`, 60, vY + 50);
    doc.text(`• SHA-256 Checksum: ${OFFICIAL_APK_SHA256}`, 60, vY + 65);
    doc.text(`• Model Lisensi: 1 Pembelian = 1 Email Pemilik = 1 Perangkat HP Aktif Seumur Hidup`, 60, vY + 80);

    doc.y = vY + 115;
    doc.moveDown(1);

    doc.fillColor(COLORS.primary).font('Helvetica-Bold').fontSize(13).text('Arsitektur & Batasan Lingkup v0.2.0');
    doc.moveDown(0.5);

    const archItems = [
      {
        title: '1. 100% Offline-First Architecture',
        desc: 'Semua database (SQLite Room) tersimpan aman di HP Anda. Transaksi kasir, cetak struk Bluetooth, hitung laba, dan pencatatan hutang berjalan normal tanpa memerlukan kuota atau sinyal internet.',
      },
      {
        title: '2. Backup Google Sheets Mandiri',
        desc: 'Pencadangan dilakukan ke Google Spreadsheet di akun Google Drive pribadi Anda dalam 18 tab tabel standar. Aplikasi tidak bergantung pada server cloud realtime multi-perangkat pihak ketiga.',
      },
      {
        title: '3. Single Engine & Adaptive Layer',
        desc: 'Satu mesin aplikasi terpadu melayani 19 jenis usaha UMKM (sembako, bengkel, apotek, toko bangunan, pulsa, salon, laundry, dsb) melalui adaptasi istilah antarmuka tanpa merombak skema database.',
      },
      {
        title: '4. Batasan Purchase Order v0.2.0',
        desc: 'Penerimaan barang (Goods Receipt) memproses seluruh item PO sekaligus (full receiving). Fitur penerimaan parsial dan penyesuaian selisih harga dialokasikan untuk pembaruan berikutnya (G13.7).',
      },
    ];

    archItems.forEach((item) => {
      doc.fillColor(COLORS.accentDark).font('Helvetica-Bold').fontSize(10.5).text(item.title);
      doc.fillColor(COLORS.secondary).font('Helvetica').fontSize(9.5).text(item.desc, { lineGap: 2.5 });
      doc.moveDown(0.8);
    });

    // =========================================================================
    // 3. DAFTAR ISI (TABLE OF CONTENTS)
    // =========================================================================
    doc.addPage();
    doc.fillColor(COLORS.primary).font('Helvetica-Bold').fontSize(20).text('Daftar Isi Panduan Pengguna', 45, 50);
    doc.rect(45, 75, doc.page.width - 90, 2).fill(COLORS.accent);

    doc.y = 90;
    doc.fillColor(COLORS.muted).font('Helvetica').fontSize(9).text(
      'Daftar bab dan nomor halaman kanonikal panduan pengguna Buku Warung v0.2.0:',
      { lineGap: 2 }
    );
    doc.moveDown(0.8);

    // Render TOC items
    const tocItems: { label: string; title: string; page: number }[] = [];

    // Bab 0
    const bab0Page = tocPageNumbers ? tocPageNumbers[0] || 4 : 4;
    tocItems.push({
      label: 'BAB 0',
      title: 'Instalasi & Aktivasi APK Resmi Android',
      page: bab0Page,
    });

    // Bab 1..24
    for (let i = 1; i <= 24; i++) {
      const art = DOC_ARTICLES.find((a) => a.order === i);
      if (art) {
        const p = tocPageNumbers ? tocPageNumbers[i] || (4 + i) : 4 + i;
        tocItems.push({
          label: `BAB ${i}`,
          title: art.title.replace(/^BAB \d+\s*—\s*/i, ''),
          page: p,
        });
      }
    }

    // Help section
    const helpPage = tocPageNumbers ? tocPageNumbers[999] || 28 : 28;
    tocItems.push({
      label: 'PENUTUP',
      title: 'Informasi Bantuan & Layanan Pelanggan Resmi',
      page: helpPage,
    });

    const tocColW = (doc.page.width - 90);
    tocItems.forEach((item) => {
      ensureSpace(18);
      const currY = doc.y;

      doc.fillColor(COLORS.accentDark).font('Helvetica-Bold').fontSize(9.5).text(item.label, 45, currY, {
        width: 60,
      });

      doc.fillColor(COLORS.primary).font('Helvetica').fontSize(9.5).text(item.title, 110, currY, {
        width: tocColW - 100,
      });

      // Dots line
      const pageStr = `${item.page}`;
      doc.fillColor(COLORS.accentDark).font('Helvetica-Bold').fontSize(9.5).text(pageStr, 45 + tocColW - 30, currY, {
        align: 'right',
        width: 30,
      });

      doc.y = currY + 16;
    });

    // =========================================================================
    // 4. BAB 0 THROUGH BAB 24 ARTICLES
    // =========================================================================
    DOC_ARTICLES.forEach((article) => {
      doc.addPage();
      chapterPages[article.order] = doc.bufferedPageRange().count;

      const contentWidth = doc.page.width - 90;

      // Category Pill
      doc.roundedRect(45, 50, 130, 18, 4).fill(COLORS.accentLight);
      doc.fillColor(COLORS.accentDark).font('Helvetica-Bold').fontSize(8.5).text(
        article.categoryName.toUpperCase(),
        52,
        54
      );

      // Read time badge
      doc.fillColor(COLORS.muted).font('Helvetica').fontSize(8.5).text(
        `Waktu baca: ${article.readTime}`,
        doc.page.width - 45 - 130,
        54,
        { align: 'right', width: 130 }
      );

      // Article Title
      doc.y = 75;
      doc.fillColor(COLORS.primary).font('Helvetica-Bold').fontSize(18).text(article.title, 45, doc.y, {
        width: contentWidth,
        lineGap: 3,
      });

      doc.moveDown(0.3);
      doc.rect(45, doc.y, contentWidth, 1.5).fill(COLORS.accent);
      doc.moveDown(0.6);

      // Summary Box
      const sumY = doc.y;
      doc.roundedRect(45, sumY, contentWidth, 48, 6).fillAndStroke(COLORS.cardBg, COLORS.cardBorder);
      doc.fillColor(COLORS.secondary).font('Helvetica-Oblique').fontSize(9.5).text(
        article.summary,
        55,
        sumY + 8,
        { width: contentWidth - 20, lineGap: 2.5 }
      );
      doc.y = sumY + 54;
      doc.moveDown(0.5);

      // Target Audience & Features Box
      if (article.targetAudience || (article.features && article.features.length > 0)) {
        ensureSpace(70);
        const featY = doc.y;
        doc.fillColor(COLORS.primary).font('Helvetica-Bold').fontSize(11).text('Sasaran Pengguna & Kemampuan Utama', 45, featY);
        doc.moveDown(0.3);

        if (article.targetAudience) {
          doc.fillColor(COLORS.secondary).font('Helvetica').fontSize(9).text(
            `Untuk Siapa: ${article.targetAudience}`,
            { lineGap: 2 }
          );
          doc.moveDown(0.3);
        }

        if (article.features) {
          article.features.forEach((feat) => {
            doc.fillColor(COLORS.accentDark).font('Helvetica-Bold').fontSize(8.5).text('✔ ', { continued: true });
            doc.fillColor(COLORS.secondary).font('Helvetica').fontSize(9).text(feat, { lineGap: 2 });
          });
          doc.moveDown(0.5);
        }
      }

      // Sections (H2 Paragraphs and Lists)
      if (article.sections && article.sections.length > 0) {
        article.sections.forEach((sec) => {
          ensureSpace(50);
          doc.fillColor(COLORS.primary).font('Helvetica-Bold').fontSize(12).text(sec.title);
          doc.moveDown(0.3);

          if (sec.paragraphs) {
            sec.paragraphs.forEach((p) => {
              ensureSpace(25);
              doc.fillColor(COLORS.secondary).font('Helvetica').fontSize(9.5).text(p, {
                lineGap: 2.5,
              });
              doc.moveDown(0.3);
            });
          }

          if (sec.listItems) {
            sec.listItems.forEach((item) => {
              ensureSpace(20);
              doc.fillColor(COLORS.accentDark).font('Helvetica-Bold').fontSize(9).text('• ', { continued: true });
              doc.fillColor(COLORS.secondary).font('Helvetica').fontSize(9).text(item, {
                lineGap: 2,
              });
            });
            doc.moveDown(0.4);
          }
        });
      }

      // Step by Step Guide
      if (article.steps && article.steps.length > 0) {
        ensureSpace(80);
        doc.fillColor(COLORS.primary).font('Helvetica-Bold').fontSize(12).text('Petunjuk Langkah Demi Langkah');
        doc.moveDown(0.4);

        article.steps.forEach((st, sIdx) => {
          ensureSpace(40);
          const sY = doc.y;
          doc.circle(55, sY + 6, 8).fill(COLORS.accent);
          doc.fillColor(COLORS.white).font('Helvetica-Bold').fontSize(8.5).text(`${sIdx + 1}`, 52, sY + 2);

          doc.fillColor(COLORS.primary).font('Helvetica-Bold').fontSize(10).text(st.title, 72, sY);
          doc.fillColor(COLORS.secondary).font('Helvetica').fontSize(9).text(st.description, 72, sY + 13, {
            width: contentWidth - 30,
            lineGap: 2,
          });
          doc.y = sY + 36;
        });
        doc.moveDown(0.5);
      }

      // Real World Example Card
      if (article.example) {
        ensureSpace(70);
        const exY = doc.y;
        doc.roundedRect(45, exY, contentWidth, 75, 6).fillAndStroke('#F1F5F9', '#CBD5E1');
        doc.fillColor(COLORS.primary).font('Helvetica-Bold').fontSize(10).text(
          `Contoh Nyata: ${article.example.title}`,
          55,
          exY + 8
        );
        doc.fillColor(COLORS.secondary).font('Helvetica-Oblique').fontSize(8.5).text(
          article.example.scenario,
          55,
          exY + 22,
          { width: contentWidth - 20 }
        );

        let detY = exY + 36;
        article.example.details.forEach((det) => {
          doc.fillColor(COLORS.secondary).font('Helvetica').fontSize(8).text(`• ${det}`, 55, detY, {
            width: contentWidth - 20,
          });
          detY += 11;
        });

        doc.y = exY + 82;
        doc.moveDown(0.4);
      }

      // Callouts (Info / Tip / Warning)
      if (article.callouts && article.callouts.length > 0) {
        article.callouts.forEach((cal) => {
          ensureSpace(50);
          const cY = doc.y;
          let bg = COLORS.infoBg;
          let border = COLORS.infoBorder;
          if (cal.type === 'warning') {
            bg = COLORS.warningBg;
            border = COLORS.warningBorder;
          } else if (cal.type === 'tip') {
            bg = COLORS.tipBg;
            border = COLORS.tipBorder;
          }

          doc.roundedRect(45, cY, contentWidth, 44, 4).fill(bg);
          doc.rect(45, cY, 4, 44).fill(border);

          doc.fillColor(border).font('Helvetica-Bold').fontSize(9).text(cal.title, 56, cY + 6);
          doc.fillColor(COLORS.secondary).font('Helvetica').fontSize(8.5).text(cal.text, 56, cY + 18, {
            width: contentWidth - 20,
            lineGap: 1.5,
          });

          doc.y = cY + 48;
          doc.moveDown(0.3);
        });
      }

      // Screenshot Rendering (Only Real UI Screenshots)
      if (article.screenshot) {
        const imgPath = path.resolve(PUBLIC_DIR, article.screenshot.src.replace(/^\//, ''));
        if (fs.existsSync(imgPath)) {
          ensureSpace(190);
          const imgY = doc.y;
          const maxImgW = 200;
          const imgX = 45 + (contentWidth - maxImgW) / 2;

          try {
            doc.image(imgPath, imgX, imgY, { width: maxImgW });
            doc.roundedRect(imgX - 2, imgY - 2, maxImgW + 4, 150 + 4, 4).stroke(COLORS.cardBorder);
            doc.y = imgY + 156;
            doc.fillColor(COLORS.muted).font('Helvetica-Oblique').fontSize(8).text(
              article.screenshot.caption,
              45,
              doc.y,
              { align: 'center', width: contentWidth }
            );
            doc.moveDown(0.5);
          } catch (e) {
            // Ignore image draw error gracefully
          }
        }
      }

      // Scope Limitations Callout
      if (article.limitations && article.limitations.length > 0) {
        ensureSpace(45);
        const lY = doc.y;
        doc.roundedRect(45, lY, contentWidth, 38 + article.limitations.length * 12, 4).fill(COLORS.dangerBg);
        doc.rect(45, lY, 4, 38 + article.limitations.length * 12).fill(COLORS.dangerBorder);

        doc.fillColor(COLORS.dangerBorder).font('Helvetica-Bold').fontSize(9).text('BATASAN & KETENTUAN VERSI v0.2.0', 56, lY + 6);
        let limTextY = lY + 20;
        article.limitations.forEach((lim) => {
          doc.fillColor(COLORS.secondary).font('Helvetica').fontSize(8.5).text(`• ${lim}`, 56, limTextY, {
            width: contentWidth - 20,
          });
          limTextY += 13;
        });

        doc.y = lY + 42 + article.limitations.length * 12;
        doc.moveDown(0.4);
      }
    });

    // =========================================================================
    // 5. INFORMASI BANTUAN & CATATAN AKHIR
    // =========================================================================
    doc.addPage();
    helpSectionPage = doc.bufferedPageRange().count;
    chapterPages[999] = helpSectionPage;

    doc.fillColor(COLORS.primary).font('Helvetica-Bold').fontSize(20).text('Layanan Bantuan & Dukungan Pelanggan', 45, 50);
    doc.rect(45, 75, doc.page.width - 90, 2).fill(COLORS.accent);

    doc.y = 95;
    doc.fillColor(COLORS.secondary).font('Helvetica').fontSize(10).text(
      'Jika Anda memiliki pertanyaan seputar cara penggunaan, aktivasi lisensi, koneksi printer thermal, atau pemulihan data cadangan Google Sheets, tim Customer Service resmi SKMNetwork siap membantu Anda.',
      { lineGap: 3 }
    );

    doc.moveDown(1);
    const helpBoxY = doc.y;
    doc.roundedRect(45, helpBoxY, doc.page.width - 90, 130, 8).fillAndStroke(COLORS.accentLight, COLORS.accentBorder);

    doc.fillColor(COLORS.accentDark).font('Helvetica-Bold').fontSize(12).text('KONTAK RESMI SKMNETWORK', 60, helpBoxY + 14);
    doc.fillColor(COLORS.secondary).font('Helvetica').fontSize(9.5);
    doc.text('• Website Resmi: https://bukuwarung.skmnetwork.com', 60, helpBoxY + 34);
    doc.text('• Formulir Pemesanan Resmi: https://bukuwarung.skmnetwork.com/#beli', 60, helpBoxY + 50);
    doc.text('• Portal Panduan Online: https://bukuwarung.skmnetwork.com/panduan', 60, helpBoxY + 66);
    doc.text('• Layanan WhatsApp CS: Hubungi nomor CS resmi yang tertera pada website', 60, helpBoxY + 82);
    doc.text('• Jam Operasional Layanan: Senin – Sabtu (08.00 – 17.00 WIB)', 60, helpBoxY + 98);

    doc.y = helpBoxY + 145;
    doc.moveDown(1);

    doc.fillColor(COLORS.primary).font('Helvetica-Bold').fontSize(12).text('Ketentuan Garansi & Pemindahan Perangkat');
    doc.moveDown(0.4);
    doc.fillColor(COLORS.secondary).font('Helvetica').fontSize(9.5).text(
      'Lisensi Buku Warung berlaku seumur hidup (Lifetime) untuk 1 perangkat HP aktif. Jika Anda berganti smartphone atau HP lama rusak, lisensi dapat dipindahkan ke perangkat baru dengan memverifikasi Email Pemilik terdaftar melalui bantuan Customer Service SKMNetwork.',
      { lineGap: 2.5 }
    );

    // =========================================================================
    // 6. RUNNING HEADERS & FOOTERS (All Pages except Cover)
    // =========================================================================
    const totalPages = knownTotalPages || doc.bufferedPageRange().count;
    const range = doc.bufferedPageRange();

    for (let pIdx = 1; pIdx < range.count; pIdx++) {
      doc.switchToPage(pIdx);
      const pgNum = pIdx + 1;

      // Header
      doc.fillColor(COLORS.muted).font('Helvetica').fontSize(7.5).text(
        'Buku Warung v0.2.0 — Panduan Pengguna Resmi',
        45,
        25,
        { width: doc.page.width - 90 }
      );
      doc.rect(45, 36, doc.page.width - 90, 0.5).fill(COLORS.cardBorder);

      // Footer
      const fY = doc.page.height - 35;
      doc.rect(45, fY - 6, doc.page.width - 90, 0.5).fill(COLORS.cardBorder);
      doc.fillColor(COLORS.muted).font('Helvetica').fontSize(7.5).text(
        'Hak Cipta © 2026 SKMNetwork • https://bukuwarung.skmnetwork.com',
        45,
        fY,
        { width: 300 }
      );
      doc.fillColor(COLORS.muted).font('Helvetica-Bold').fontSize(7.5).text(
        `Halaman ${pgNum} dari ${totalPages}`,
        doc.page.width - 45 - 120,
        fY,
        { align: 'right', width: 120 }
      );
    }

    doc.end();

    writeStream.on('finish', () => {
      resolve({ pageCount: range.count, chapterPages });
    });

    writeStream.on('error', (err) => {
      reject(err);
    });
  });
}

// 2-pass execution to calculate dynamic page count and accurate Table of Contents
async function run() {
  console.log('--- Generating Buku Warung Official PDF Manual (Pass 1: Layout & Page Calculation) ---');
  const tempPdf = path.resolve(DOWNLOADS_DIR, 'temp_pass1.pdf');
  const pass1 = await buildPdfDocument(tempPdf, null, 0);

  console.log(`Pass 1 completed: ${pass1.pageCount} pages calculated.`);
  console.log('Chapter Starting Pages Map:', pass1.chapterPages);

  console.log('--- Generating Buku Warung Official PDF Manual (Pass 2: Final Resolution with TOC & Total Page Count) ---');
  const pass2 = await buildPdfDocument(OUTPUT_PDF_PATH, pass1.chapterPages, pass1.pageCount);

  // Clean up temp file
  if (fs.existsSync(tempPdf)) {
    fs.unlinkSync(tempPdf);
  }

  const stat = fs.statSync(OUTPUT_PDF_PATH);
  console.log(`\n======================================================`);
  console.log(`SUCCESS: Official PDF Manual Generated Successfully!`);
  console.log(`File Path: ${OUTPUT_PDF_PATH}`);
  console.log(`File Size: ${(stat.size / 1024).toFixed(2)} KB`);
  console.log(`Total Pages: ${pass2.pageCount}`);
  console.log(`======================================================\n`);
}

run().catch((err) => {
  console.error('ERROR generating PDF:', err);
  process.exit(1);
});
