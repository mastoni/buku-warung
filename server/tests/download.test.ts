import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import { FastifyInstance } from 'fastify';
import { buildApp } from '../src/app.js';
import { getDatabase, closeDatabase } from '../src/db/database.js';
import { config } from '../src/config/index.js';
import fs from 'fs';
import path from 'path';
import crypto from 'crypto';

const OFFICIAL_SHA256 = '1E0221EF344413EA9954A21939E023C59B6374CEE8E56FF004084F92023DB3A8';
const APK_FILENAME = 'bukuwarung-0.1.0-release.apk';
const PDF_FILENAME = 'panduan-buku-warung-v0.1.0.pdf';

describe('Product Distribution & Download (M.2.0)', () => {
  let app: FastifyInstance;
  const adminApiKey = config.adminApiKey;

  // Determine if APK and PDF files exist for testing
  const releasesDir = path.resolve(process.cwd(), 'public/releases');
  const apkPath = path.join(releasesDir, APK_FILENAME);
  const pdfPath = path.join(releasesDir, PDF_FILENAME);
  const apkExists = fs.existsSync(apkPath);
  const pdfExists = fs.existsSync(pdfPath);

  beforeAll(async () => {
    getDatabase(':memory:');
    app = buildApp();
    await app.ready();
  });

  afterAll(async () => {
    await app.close();
    closeDatabase();
  });

  /* =========================================================================
   * 1. Download Page
   * ========================================================================= */
  describe('GET /download/buku-warung (Download Page)', () => {
    it('returns HTTP 200 with HTML content', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/download/buku-warung'
      });

      expect(res.statusCode).toBe(200);
      expect(res.headers['content-type']).toContain('text/html');
    });

    it('displays product name and version', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/download/buku-warung'
      });

      const html = res.body;
      expect(html).toContain('Buku Warung');
      expect(html).toContain('0.1.0');
    });

    it('displays SHA-256 checksum', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/download/buku-warung'
      });

      expect(res.body).toContain(OFFICIAL_SHA256);
    });

    it('displays platform info', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/download/buku-warung'
      });

      expect(res.body).toContain('Android');
      expect(res.body).toContain('id.skmnetwork.bukuwarung');
    });

    it('contains APK download link', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/download/buku-warung'
      });

      expect(res.body).toContain('/download/buku-warung/apk');
    });

    it('contains PDF guide link reference', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/download/buku-warung'
      });

      expect(res.body).toContain('/download/buku-warung/panduan');
    });

    it('does NOT require authentication', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/download/buku-warung'
        // No auth header
      });

      expect(res.statusCode).toBe(200);
    });

    it('does NOT expose API keys', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/download/buku-warung'
      });

      const html = res.body;
      expect(html).not.toContain(adminApiKey);
      expect(html).not.toContain('ADMIN_API_KEY');
      expect(html).not.toContain('SERVER_PEPPER');
    });

    it('does NOT expose customer data', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/download/buku-warung'
      });

      const html = res.body;
      expect(html).not.toContain('license_code');
      expect(html).not.toContain('customer_contact');
      expect(html).not.toContain('owner_email');
      expect(html).not.toContain('device_binding');
    });

    it('does NOT expose database paths', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/download/buku-warung'
      });

      const html = res.body;
      expect(html).not.toContain('DATABASE_PATH');
      expect(html).not.toContain('/var/data/');
      expect(html).not.toContain('.db');
    });
  });

  /* =========================================================================
   * 2. APK Serving
   * ========================================================================= */
  describe('GET /download/buku-warung/apk (APK Download)', () => {
    it.skipIf(!apkExists)('returns HTTP 200 with correct Content-Type', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/download/buku-warung/apk'
      });

      expect(res.statusCode).toBe(200);
      expect(res.headers['content-type']).toContain('application/vnd.android.package-archive');
    });

    it.skipIf(!apkExists)('includes Content-Disposition for download', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/download/buku-warung/apk'
      });

      expect(res.headers['content-disposition']).toContain('attachment');
      expect(res.headers['content-disposition']).toContain(APK_FILENAME);
    });

    it.skipIf(!apkExists)('serves the correct APK artifact (SHA-256 match)', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/download/buku-warung/apk'
      });

      const hash = crypto.createHash('sha256')
        .update(Buffer.from(res.rawPayload))
        .digest('hex')
        .toUpperCase();

      expect(hash).toBe(OFFICIAL_SHA256);
    });

    it.skipIf(!apkExists)('does NOT require authentication', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/download/buku-warung/apk'
      });

      expect(res.statusCode).toBe(200);
    });

    it.skipIf(!apkExists)('accepts optional leadToken query parameter', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/download/buku-warung/apk?leadToken=LW-ABC123'
      });

      // Should still serve APK regardless of tracking
      expect(res.statusCode).toBe(200);
    });
  });

  /* =========================================================================
   * 3. PDF Serving
   * ========================================================================= */
  describe('GET /download/buku-warung/panduan (PDF Guide)', () => {
    it.skipIf(!pdfExists)('returns HTTP 200 with application/pdf Content-Type', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/download/buku-warung/panduan'
      });

      expect(res.statusCode).toBe(200);
      expect(res.headers['content-type']).toContain('application/pdf');
    });

    it.skipIf(!pdfExists)('includes Content-Disposition with PDF filename', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/download/buku-warung/panduan'
      });

      expect(res.headers['content-disposition']).toContain(PDF_FILENAME);
    });

    it('returns 404 if PDF not yet available', async () => {
      if (pdfExists) return; // Skip if file exists
      const res = await app.inject({
        method: 'GET',
        url: '/download/buku-warung/panduan'
      });

      expect(res.statusCode).toBe(404);
    });
  });

  /* =========================================================================
   * 4. Product Info JSON
   * ========================================================================= */
  describe('GET /download/buku-warung/info (Product Metadata JSON)', () => {
    it('returns product metadata without secrets', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/download/buku-warung/info'
      });

      expect(res.statusCode).toBe(200);
      const body = JSON.parse(res.body);
      expect(body.success).toBe(true);
      expect(body.data.product).toBe('Buku Warung');
      expect(body.data.version).toBe('0.1.0');
      expect(body.data.platform).toBe('Android');
      expect(body.data.packageId).toBe('id.skmnetwork.bukuwarung');
      expect(body.data.sha256).toBe(OFFICIAL_SHA256);
      expect(body.data.apkUrl).toBe('/download/buku-warung/apk');
    });

    it('does NOT expose any internal data', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/download/buku-warung/info'
      });

      const raw = res.body;
      expect(raw).not.toContain(adminApiKey);
      expect(raw).not.toContain('ADMIN');
      expect(raw).not.toContain('pepper');
      expect(raw).not.toContain('database');
    });
  });

  /* =========================================================================
   * 5. Security — No IDOR, No Auth Bypass
   * ========================================================================= */
  describe('Security', () => {
    it('download page does not expose admin routes', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/download/buku-warung'
      });

      expect(res.body).not.toContain('/v1/admin/');
      expect(res.body).not.toContain('/api/orders');
      expect(res.body).not.toContain('/api/licenses');
    });

    it('admin endpoints still require authentication', async () => {
      // Attempt to access admin orders without API key
      const res = await app.inject({
        method: 'GET',
        url: '/v1/admin/orders'
        // No auth header
      });

      expect(res.statusCode).toBe(401);
    });

    it('admin license list still requires authentication', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/v1/admin/licenses'
      });

      expect(res.statusCode).toBe(401);
    });
  });

  /* =========================================================================
   * 6. Existing endpoints regression
   * ========================================================================= */
  describe('Regression — Existing Endpoints', () => {
    it('GET /health returns 200', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/health'
      });

      expect(res.statusCode).toBe(200);
    });

    it('GET /v1/admin/orders requires auth', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/v1/admin/orders'
      });

      expect(res.statusCode).toBe(401);
    });

    it('GET /v1/admin/orders works with auth', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/v1/admin/orders',
        headers: { authorization: `Bearer ${adminApiKey}` }
      });

      expect(res.statusCode).toBe(200);
      const body = JSON.parse(res.body);
      expect(body.success).toBe(true);
    });

    it('GET /v1/admin/licenses works with auth', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/v1/admin/licenses',
        headers: { authorization: `Bearer ${adminApiKey}` }
      });

      expect(res.statusCode).toBe(200);
      const body = JSON.parse(res.body);
      expect(body.success).toBe(true);
    });
  });
});
