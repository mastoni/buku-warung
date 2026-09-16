import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import path from 'path';
import fs from 'fs';
import { AttributionService } from '../services/attributionService.js';

/* =========================================================================
 * Product metadata — single source of truth for download page
 * ========================================================================= */
const PRODUCT = {
  name: 'Buku Warung',
  tagline: 'Kasir & pembukuan sederhana untuk warung dan toko kecil.',
  version: '0.1.0',
  platform: 'Android',
  packageId: 'id.skmnetwork.bukuwarung',
  sha256: '1E0221EF344413EA9954A21939E023C59B6374CEE8E56FF004084F92023DB3A8',
  apkFilename: 'bukuwarung-0.1.0-release.apk',
  pdfFilename: 'panduan-buku-warung-v0.1.0.pdf',
} as const;

/**
 * Resolves the public/releases directory.
 * Works both from dist/ (production) and src/ (development).
 */
function getReleasesDir(): string {
  const candidates = [
    path.resolve(__dirname, '../public/releases'),
    path.resolve(__dirname, '../../public/releases'),
  ];
  for (const dir of candidates) {
    if (fs.existsSync(dir)) return dir;
  }
  return candidates[0]; // fallback
}

export async function registerDownloadRoutes(fastify: FastifyInstance) {
  const attributionService = new AttributionService();
  const releasesDir = getReleasesDir();

  /* -----------------------------------------------------------------------
   * GET /download/buku-warung — Public download page (HTML)
   * ----------------------------------------------------------------------- */
  fastify.get(
    '/download/buku-warung',
    async (_request: FastifyRequest, reply: FastifyReply) => {
      const apkPath = path.join(releasesDir, PRODUCT.apkFilename);
      let apkSizeBytes = 0;
      let apkSizeMB = '0';
      try {
        const stat = fs.statSync(apkPath);
        apkSizeBytes = stat.size;
        apkSizeMB = (apkSizeBytes / (1024 * 1024)).toFixed(1);
      } catch {
        // APK not found — page still renders, download will 404
      }

      const pdfPath = path.join(releasesDir, PRODUCT.pdfFilename);
      const pdfExists = fs.existsSync(pdfPath);

      const html = renderDownloadPage({
        ...PRODUCT,
        apkSizeBytes,
        apkSizeMB,
        pdfExists,
      });

      return reply
        .type('text/html; charset=utf-8')
        .header('Cache-Control', 'public, max-age=300')
        .send(html);
    }
  );

  /* -----------------------------------------------------------------------
   * GET /download/buku-warung/apk — Serve official APK (streaming)
   * ----------------------------------------------------------------------- */
  fastify.get(
    '/download/buku-warung/apk',
    async (request: FastifyRequest, reply: FastifyReply) => {
      const apkPath = path.join(releasesDir, PRODUCT.apkFilename);

      if (!fs.existsSync(apkPath)) {
        return reply.status(404).send({
          success: false,
          error: { code: 'NOT_FOUND', message: 'APK file not available.' }
        });
      }

      // Fire-and-forget APK_DOWNLOADED tracking (non-blocking)
      try {
        const query = request.query as { leadToken?: string };
        const leadToken = query.leadToken || undefined;
        if (leadToken || true) {
          const ipAddress = request.ip || (request.headers['x-forwarded-for'] as string | undefined)?.split(',')[0]?.trim();
          const userAgent = request.headers['user-agent'] as string | undefined;
          attributionService.processLandingTrack(
            { eventType: 'APK_DOWNLOADED', leadToken },
            ipAddress,
            userAgent
          );
        }
      } catch {
        // Tracking failure must NOT block APK download
      }

      const stat = fs.statSync(apkPath);
      const stream = fs.createReadStream(apkPath);

      return reply
        .type('application/vnd.android.package-archive')
        .header('Content-Disposition', `attachment; filename="${PRODUCT.apkFilename}"`)
        .header('Content-Length', stat.size)
        .header('Cache-Control', 'public, max-age=86400')
        .send(stream);
    }
  );

  /* -----------------------------------------------------------------------
   * GET /download/buku-warung/panduan — Serve customer PDF guide
   * ----------------------------------------------------------------------- */
  fastify.get(
    '/download/buku-warung/panduan',
    async (_request: FastifyRequest, reply: FastifyReply) => {
      const pdfPath = path.join(releasesDir, PRODUCT.pdfFilename);

      if (!fs.existsSync(pdfPath)) {
        return reply.status(404).send({
          success: false,
          error: { code: 'NOT_FOUND', message: 'PDF guide not available yet. Please contact admin.' }
        });
      }

      const stat = fs.statSync(pdfPath);
      const stream = fs.createReadStream(pdfPath);

      return reply
        .type('application/pdf')
        .header('Content-Disposition', `inline; filename="${PRODUCT.pdfFilename}"`)
        .header('Content-Length', stat.size)
        .header('Cache-Control', 'public, max-age=86400')
        .send(stream);
    }
  );

  /* -----------------------------------------------------------------------
   * GET /download/buku-warung/info — JSON product metadata (public, no secrets)
   * ----------------------------------------------------------------------- */
  fastify.get(
    '/download/buku-warung/info',
    async (_request: FastifyRequest, reply: FastifyReply) => {
      const apkPath = path.join(releasesDir, PRODUCT.apkFilename);
      let apkSizeBytes = 0;
      try {
        apkSizeBytes = fs.statSync(apkPath).size;
      } catch { /* no-op */ }

      const pdfPath = path.join(releasesDir, PRODUCT.pdfFilename);
      const pdfAvailable = fs.existsSync(pdfPath);

      return reply.status(200).send({
        success: true,
        data: {
          product: PRODUCT.name,
          version: PRODUCT.version,
          platform: PRODUCT.platform,
          packageId: PRODUCT.packageId,
          sha256: PRODUCT.sha256,
          apkSizeBytes,
          apkUrl: '/download/buku-warung/apk',
          pdfUrl: pdfAvailable ? '/download/buku-warung/panduan' : null,
          pdfAvailable,
        }
      });
    }
  );
}

/* =========================================================================
 * Self-contained download page HTML — no external dependencies
 * ========================================================================= */
function renderDownloadPage(product: {
  name: string;
  tagline: string;
  version: string;
  platform: string;
  packageId: string;
  sha256: string;
  apkSizeBytes: number;
  apkSizeMB: string;
  pdfExists: boolean;
}): string {
  return `<!DOCTYPE html>
<html lang="id">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>${product.name} v${product.version} — Download</title>
<style>
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body {
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
    background: #f0f4f8;
    color: #1a202c;
    line-height: 1.6;
    min-height: 100vh;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: 24px;
  }
  .card {
    background: #fff;
    border-radius: 16px;
    box-shadow: 0 4px 24px rgba(0,0,0,0.08);
    max-width: 480px;
    width: 100%;
    padding: 40px 32px;
    text-align: center;
  }
  .logo {
    width: 80px;
    height: 80px;
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    border-radius: 20px;
    display: flex;
    align-items: center;
    justify-content: center;
    margin: 0 auto 20px;
    font-size: 36px;
    color: white;
  }
  h1 {
    font-size: 24px;
    font-weight: 700;
    margin-bottom: 4px;
    color: #1a202c;
  }
  .version {
    font-size: 14px;
    color: #718096;
    margin-bottom: 8px;
  }
  .tagline {
    font-size: 15px;
    color: #4a5568;
    margin-bottom: 28px;
  }
  .info-table {
    width: 100%;
    border-collapse: collapse;
    margin-bottom: 28px;
    text-align: left;
  }
  .info-table td {
    padding: 8px 0;
    font-size: 13px;
    border-bottom: 1px solid #edf2f7;
  }
  .info-table td:first-child {
    color: #718096;
    width: 100px;
    font-weight: 500;
  }
  .info-table td:last-child {
    color: #2d3748;
    word-break: break-all;
    font-family: 'SF Mono', 'Fira Code', monospace;
    font-size: 12px;
  }
  .btn {
    display: block;
    width: 100%;
    padding: 14px 24px;
    border: none;
    border-radius: 10px;
    font-size: 16px;
    font-weight: 600;
    cursor: pointer;
    text-decoration: none;
    margin-bottom: 12px;
    transition: transform 0.1s, box-shadow 0.2s;
  }
  .btn:active { transform: scale(0.98); }
  .btn-primary {
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    color: #fff;
    box-shadow: 0 4px 14px rgba(102, 126, 234, 0.4);
  }
  .btn-primary:hover { box-shadow: 0 6px 20px rgba(102, 126, 234, 0.5); }
  .btn-secondary {
    background: #edf2f7;
    color: #4a5568;
  }
  .btn-secondary:hover { background: #e2e8f0; }
  .btn-disabled {
    background: #e2e8f0;
    color: #a0aec0;
    cursor: not-allowed;
  }
  .footer {
    margin-top: 24px;
    font-size: 12px;
    color: #a0aec0;
  }
  .footer a { color: #718096; text-decoration: none; }
  @media (max-width: 520px) {
    .card { padding: 28px 20px; }
    body { padding: 16px; }
  }
</style>
</head>
<body>
<div class="card">
  <div class="logo">📱</div>
  <h1>${escHtml(product.name)}</h1>
  <div class="version">Versi ${escHtml(product.version)} · ${escHtml(product.platform)}</div>
  <div class="tagline">${escHtml(product.tagline)}</div>

  <table class="info-table">
    <tr><td>Package</td><td>${escHtml(product.packageId)}</td></tr>
    <tr><td>Versi</td><td>${escHtml(product.version)}</td></tr>
    <tr><td>Platform</td><td>${escHtml(product.platform)}</td></tr>
    <tr><td>Ukuran</td><td>${escHtml(product.apkSizeMB)} MB</td></tr>
    <tr><td>SHA-256</td><td>${escHtml(product.sha256)}</td></tr>
  </table>

  <a href="/download/buku-warung/apk" class="btn btn-primary" id="downloadApkBtn">
    📥 Download Buku Warung v${escHtml(product.version)}
  </a>

  ${product.pdfExists
    ? `<a href="/download/buku-warung/panduan" class="btn btn-secondary" target="_blank">
        📄 Panduan Instalasi &amp; Penggunaan
      </a>`
    : `<a href="/download/buku-warung/panduan" class="btn btn-secondary" target="_blank">
        📄 Panduan Instalasi &amp; Penggunaan
      </a>`
  }
</div>

<div class="footer">
  &copy; ${new Date().getFullYear()} <a href="https://skmnetwork.com">SKMNetwork</a>
</div>
</body>
</html>`;
}

function escHtml(str: string): string {
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}
