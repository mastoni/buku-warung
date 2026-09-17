import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import { AdminService } from '../services/adminService.js';
import { getDatabase } from '../db/database.js';
import { normalizeIndonesianPhone } from '../utils/phone.js';
import { normalizeEmail } from '../utils/crypto.js';
import { maskCustomerName, maskPhone, maskEmail } from '../utils/masking.js';
import { generatePublicOrderToken, verifyPublicOrderToken } from '../utils/token.js';
import { OrderRecord } from '../types/index.js';

export const OFFICIAL_QRIS = {
  merchant: 'KIOS KIARA',
  nmid: 'ID1026512762125',
  terminal: 'A01',
  nominal: 50000,
  imageUrl: 'https://license.skmnetwork.com/img/qris-kios-kiara.png'
} as const;

export interface PublicCreateOrderBody {
  customerName: string;
  customerContact: string;
  customerWhatsapp?: string;
  ownerEmail: string;
  leadToken?: string;
  utm_source?: string;
  utm_medium?: string;
  utm_campaign?: string;
  utm_content?: string;
  website?: string; // Honeypot field
  _hp?: string; // Honeypot field
  [key: string]: unknown;
}

export async function registerPublicOrderRoutes(fastify: FastifyInstance) {
  const adminService = new AdminService();
  const db = getDatabase();

  /**
   * POST /v1/public/orders
   * Customer creates a new order.
   * Rate limited: 5 attempts per 10 minutes per IP.
   */
  fastify.post(
    '/v1/public/orders',
    {
      config: {
        rateLimit: {
          max: 5,
          timeWindow: 10 * 60 * 1000
        }
      },
      schema: {
        body: {
          type: 'object',
          required: ['customerName', 'ownerEmail'],
          properties: {
            customerName: { type: 'string', minLength: 1, maxLength: 100 },
            customerContact: { type: 'string', maxLength: 30 },
            customerWhatsapp: { type: 'string', maxLength: 30 },
            ownerEmail: { type: 'string', minLength: 3, maxLength: 120 },
            leadToken: { type: 'string', maxLength: 50 },
            utm_source: { type: 'string', maxLength: 100 },
            utm_medium: { type: 'string', maxLength: 100 },
            utm_campaign: { type: 'string', maxLength: 100 },
            utm_content: { type: 'string', maxLength: 100 },
            website: { type: 'string' },
            _hp: { type: 'string' }
          }
        }
      }
    },
    async (request: FastifyRequest<{ Body: PublicCreateOrderBody }>, reply: FastifyReply) => {
      const body = request.body || ({} as PublicCreateOrderBody);

      // 1. Anti-Spam: Honeypot verification
      if ((body.website && body.website.trim() !== '') || (body._hp && body._hp.trim() !== '')) {
        return reply.status(400).send({
          success: false,
          error: { code: 'SPAM_DETECTED', message: 'Permintaan tidak dapat diproses.' }
        });
      }

      // 2. Validate & Trim Customer Name
      const customerName = (body.customerName || '').trim();
      if (!customerName || customerName.length < 2) {
        return reply.status(400).send({
          success: false,
          error: { code: 'INVALID_NAME', message: 'Nama pelanggan wajib diisi (minimal 2 karakter).' }
        });
      }

      // 3. Validate & Normalize Indonesian WhatsApp Phone
      const rawContact = body.customerContact || body.customerWhatsapp || '';
      const phoneCheck = normalizeIndonesianPhone(rawContact);
      if (!phoneCheck.valid) {
        return reply.status(400).send({
          success: false,
          error: { code: 'INVALID_PHONE', message: phoneCheck.error || 'Nomor WhatsApp tidak valid.' }
        });
      }
      const normalizedContact = phoneCheck.normalized;

      // 4. Validate & Normalize Owner Email
      const rawEmail = (body.ownerEmail || '').trim();
      const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
      if (!rawEmail || !rawEmail.includes('@') || !emailRegex.test(rawEmail)) {
        return reply.status(400).send({
          success: false,
          error: { code: 'INVALID_EMAIL', message: 'Email aktivasi tidak valid.' }
        });
      }
      const canonicalEmail = normalizeEmail(rawEmail);

      // 5. Anti-Spam / Idempotency: Duplicate pending order detection (5-minute window)
      const recentWindow = Date.now() - 5 * 60 * 1000;
      const existingPending = db
        .prepare(`
          SELECT * FROM orders
          WHERE customer_contact = ?
            AND product = 'BUKU_WARUNG'
            AND status = 'PENDING_PAYMENT'
            AND payment_status = 'UNPAID'
            AND created_at >= ?
          ORDER BY id DESC LIMIT 1
        `)
        .get(normalizedContact, recentWindow) as OrderRecord | undefined;

      if (existingPending) {
        const publicToken = generatePublicOrderToken(existingPending.order_number);
        return reply.status(200).send({
          success: true,
          data: {
            orderNumber: existingPending.order_number,
            status: existingPending.status,
            paymentStatus: existingPending.payment_status,
            product: 'Buku Warung v0.1.0',
            amount: existingPending.amount,
            paymentMethod: existingPending.payment_method || 'QRIS — KIOS KIARA',
            publicToken,
            qrisImageUrl: OFFICIAL_QRIS.imageUrl,
            qris: OFFICIAL_QRIS,
            idempotent: true
          }
        });
      }

      // 6. Security Enforcement: Hardcode authoritative commercial price and product
      // Never trust client-supplied product or amount
      const AUTHORITATIVE_PRODUCT = 'BUKU_WARUNG';
      const AUTHORITATIVE_AMOUNT = 50000;
      const DEFAULT_PAYMENT_METHOD = 'QRIS — KIOS KIARA';

      // 7. Reuse existing AdminService.createOrder()
      const createResult = adminService.createOrder(
        {
          customerName,
          customerContact: normalizedContact,
          ownerEmail: canonicalEmail,
          product: AUTHORITATIVE_PRODUCT,
          amount: AUTHORITATIVE_AMOUNT,
          notes: `Public Order via Website (${DEFAULT_PAYMENT_METHOD})`,
          leadToken: body.leadToken || undefined,
          utm_source: body.utm_source || undefined,
          utm_medium: body.utm_medium || undefined,
          utm_campaign: body.utm_campaign || undefined,
          utm_content: body.utm_content || undefined
        },
        'PUBLIC_WEB'
      );

      if (!createResult.success || !createResult.data) {
        return reply.status(400).send({
          success: false,
          error: createResult.error || { code: 'ORDER_CREATION_FAILED', message: 'Gagal membuat pesanan.' }
        });
      }

      const order = createResult.data;

      // Update payment_method to default QRIS in database
      db.prepare('UPDATE orders SET payment_method = ? WHERE id = ?').run(DEFAULT_PAYMENT_METHOD, order.id);

      // 8. Generate Cryptographically Secure Capability Token
      const publicToken = generatePublicOrderToken(order.order_number);

      return reply.status(201).send({
        success: true,
        data: {
          orderNumber: order.order_number,
          status: order.status,
          paymentStatus: order.payment_status,
          product: 'Buku Warung v0.1.0',
          amount: AUTHORITATIVE_AMOUNT,
          paymentMethod: DEFAULT_PAYMENT_METHOD,
          publicToken,
          qrisImageUrl: OFFICIAL_QRIS.imageUrl,
          qris: OFFICIAL_QRIS
        }
      });
    }
  );

  /**
   * GET /v1/public/orders/:publicToken
   * Customer views safe order status and payment instructions.
   * Rate limited: 30 requests per minute per IP.
   */
  fastify.get(
    '/v1/public/orders/:publicToken',
    {
      config: {
        rateLimit: {
          max: 30,
          timeWindow: 60 * 1000
        }
      }
    },
    async (request: FastifyRequest<{ Params: { publicToken: string } }>, reply: FastifyReply) => {
      const { publicToken } = request.params;

      // 1. Verify and decrypt capability token
      const tokenVerification = verifyPublicOrderToken(publicToken);
      if (!tokenVerification.valid || !tokenVerification.orderNumber) {
        return reply.status(404).send({
          success: false,
          error: { code: 'ORDER_NOT_FOUND', message: 'Pesanan tidak ditemukan atau tautan tidak valid.' }
        });
      }

      // 2. Query order from database using verified order_number
      const order = db
        .prepare('SELECT * FROM orders WHERE order_number = ?')
        .get(tokenVerification.orderNumber) as OrderRecord | undefined;

      if (!order) {
        return reply.status(404).send({
          success: false,
          error: { code: 'ORDER_NOT_FOUND', message: 'Pesanan tidak ditemukan.' }
        });
      }

      // 3. Return safe masked representation (Zero sensitive data exposure)
      return reply.status(200).send({
        success: true,
        data: {
          orderNumber: order.order_number,
          status: order.status,
          paymentStatus: order.payment_status,
          product: 'Buku Warung v0.1.0',
          amount: order.amount,
          paymentMethod: order.payment_method || 'QRIS — KIOS KIARA',
          customerNameMasked: maskCustomerName(order.customer_name),
          customerContactMasked: maskPhone(order.customer_contact),
          ownerEmailMasked: maskEmail(order.owner_email),
          createdAt: order.created_at,
          verifiedAt: order.verified_at,
          qrisImageUrl: OFFICIAL_QRIS.imageUrl,
          qris: OFFICIAL_QRIS
        }
      });
    }
  );

  /**
   * GET /beli/buku-warung
   * Customer-Facing Public Order Page (Mobile-first HTML UI)
   */
  fastify.get(
    '/beli/buku-warung',
    async (_request: FastifyRequest, reply: FastifyReply) => {
      const html = renderPublicOrderPage();
      return reply
        .type('text/html; charset=utf-8')
        .header('Cache-Control', 'public, max-age=120')
        .send(html);
    }
  );
}

/**
 * Self-contained HTML renderer for /beli/buku-warung
 * Clean, mobile-first, zero framework, CSP-compatible.
 */
function renderPublicOrderPage(): string {
  return `<!DOCTYPE html>
<html lang="id">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Buku Warung v0.1.0 — Pesan Aplikasi Kasir Warung</title>
<meta name="description" content="Pesan aplikasi kasir Buku Warung Rp50.000 sekali beli tanpa langganan. 100% offline, cetak struk thermal, catat hutang piutang.">
<style>
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body {
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
    background: #f1f5f9;
    color: #0f172a;
    line-height: 1.5;
    min-height: 100vh;
    padding: 16px;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: flex-start;
  }
  .container {
    width: 100%;
    max-width: 480px;
    margin: 0 auto;
  }
  .header {
    text-align: center;
    margin-bottom: 20px;
    padding-top: 8px;
  }
  .logo-badge {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 56px;
    height: 56px;
    background: linear-gradient(135deg, #10b981 0%, #059669 100%);
    color: white;
    font-size: 26px;
    border-radius: 14px;
    margin-bottom: 10px;
    box-shadow: 0 4px 12px rgba(16, 185, 129, 0.3);
  }
  h1 {
    font-size: 22px;
    font-weight: 700;
    color: #0f172a;
    margin-bottom: 4px;
  }
  .subtitle {
    font-size: 14px;
    color: #64748b;
  }
  .card {
    background: #ffffff;
    border-radius: 16px;
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.06);
    border: 1px solid #e2e8f0;
    overflow: hidden;
    margin-bottom: 16px;
  }
  .product-summary {
    background: linear-gradient(135deg, #ecfdf5 0%, #f0fdf4 100%);
    border-bottom: 1px solid #d1fae5;
    padding: 16px 20px;
    display: flex;
    justify-content: space-between;
    align-items: center;
  }
  .product-name {
    font-weight: 700;
    font-size: 15px;
    color: #065f46;
  }
  .product-tag {
    font-size: 12px;
    color: #047857;
    margin-top: 2px;
  }
  .product-price {
    text-align: right;
  }
  .price-amount {
    font-size: 20px;
    font-weight: 800;
    color: #059669;
  }
  .price-type {
    font-size: 11px;
    color: #047857;
    text-transform: uppercase;
    font-weight: 600;
  }
  .card-body {
    padding: 24px 20px;
  }
  .form-group {
    margin-bottom: 18px;
  }
  label {
    display: block;
    font-size: 13px;
    font-weight: 600;
    color: #334155;
    margin-bottom: 6px;
  }
  .required {
    color: #ef4444;
  }
  input[type="text"],
  input[type="tel"],
  input[type="email"] {
    width: 100%;
    padding: 12px 14px;
    border: 1.5px solid #cbd5e1;
    border-radius: 10px;
    font-size: 15px;
    color: #0f172a;
    background: #ffffff;
    transition: border-color 0.2s, box-shadow 0.2s;
    outline: none;
    -webkit-appearance: none;
  }
  input[type="text"]:focus,
  input[type="tel"]:focus,
  input[type="email"]:focus {
    border-color: #10b981;
    box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.15);
  }
  .help-text {
    font-size: 12px;
    color: #64748b;
    margin-top: 5px;
  }
  .field-error {
    font-size: 12px;
    color: #ef4444;
    margin-top: 4px;
    display: none;
    font-weight: 500;
  }
  .btn {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 100%;
    padding: 14px 20px;
    border: none;
    border-radius: 10px;
    font-size: 16px;
    font-weight: 600;
    cursor: pointer;
    text-decoration: none;
    transition: all 0.2s;
    min-height: 48px;
  }
  .btn-primary {
    background: #10b981;
    color: #ffffff;
    box-shadow: 0 4px 12px rgba(16, 185, 129, 0.35);
  }
  .btn-primary:hover {
    background: #059669;
  }
  .btn-primary:active {
    transform: scale(0.98);
  }
  .btn-primary:disabled {
    background: #94a3b8;
    cursor: not-allowed;
    transform: none;
    box-shadow: none;
  }
  .btn-whatsapp {
    background: #25d366;
    color: #ffffff;
    box-shadow: 0 4px 12px rgba(37, 211, 102, 0.35);
    margin-top: 14px;
    font-size: 15px;
  }
  .btn-whatsapp:hover {
    background: #1eb854;
  }
  .btn-secondary {
    background: #f1f5f9;
    color: #475569;
    margin-top: 10px;
    font-size: 14px;
  }
  .btn-secondary:hover {
    background: #e2e8f0;
  }
  .alert-banner {
    padding: 12px 14px;
    border-radius: 8px;
    font-size: 13px;
    margin-bottom: 18px;
    display: none;
    line-height: 1.4;
  }
  .alert-danger {
    background: #fef2f2;
    border: 1px solid #fecaca;
    color: #b91c1c;
  }
  .alert-info {
    background: #f0fdf4;
    border: 1px solid #bbf7d0;
    color: #166534;
  }
  /* Payment Section Styles */
  .order-meta {
    background: #f8fafc;
    border: 1px solid #e2e8f0;
    border-radius: 12px;
    padding: 14px 16px;
    margin-bottom: 18px;
  }
  .meta-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 6px 0;
    font-size: 13px;
  }
  .meta-row:not(:last-child) {
    border-bottom: 1px dashed #e2e8f0;
  }
  .meta-label {
    color: #64748b;
  }
  .meta-value {
    font-weight: 600;
    color: #0f172a;
    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  }
  .status-badge {
    display: inline-block;
    padding: 4px 10px;
    border-radius: 20px;
    font-size: 12px;
    font-weight: 700;
    text-transform: uppercase;
  }
  .status-pending {
    background: #fef3c7;
    color: #b45309;
  }
  .status-paid {
    background: #dcfce7;
    color: #15803d;
  }
  .qris-box {
    text-align: center;
    background: #ffffff;
    border: 2px dashed #cbd5e1;
    border-radius: 14px;
    padding: 20px 16px;
    margin-bottom: 18px;
  }
  .qris-image {
    max-width: 240px;
    width: 100%;
    height: auto;
    border-radius: 8px;
    margin: 0 auto 12px;
    display: block;
    background: #ffffff;
    border: 1px solid #e2e8f0;
    padding: 8px;
  }
  .qris-merchant {
    font-size: 15px;
    font-weight: 700;
    color: #0f172a;
  }
  .qris-nmid {
    font-size: 12px;
    color: #64748b;
    font-family: monospace;
    margin-top: 2px;
  }
  .qris-instruction {
    font-size: 13px;
    color: #475569;
    margin-top: 8px;
  }
  .trust-list {
    margin-top: 14px;
    padding-top: 14px;
    border-top: 1px solid #e2e8f0;
    font-size: 12px;
    color: #64748b;
  }
  .trust-item {
    display: flex;
    align-items: center;
    margin-bottom: 6px;
  }
  .trust-icon {
    color: #10b981;
    margin-right: 8px;
    font-weight: bold;
  }
  .footer {
    text-align: center;
    font-size: 12px;
    color: #94a3b8;
    margin-top: 12px;
    padding-bottom: 24px;
  }
  .footer a {
    color: #64748b;
    text-decoration: none;
  }
  /* Polling indicator */
  .poll-indicator {
    font-size: 12px;
    color: #64748b;
    text-align: center;
    margin-top: 10px;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 6px;
  }
  .spinner {
    width: 12px;
    height: 12px;
    border: 2px solid #cbd5e1;
    border-top-color: #10b981;
    border-radius: 50%;
    animation: spin 1s infinite linear;
  }
  @keyframes spin {
    0% { transform: rotate(0deg); }
    100% { transform: rotate(360deg); }
  }
  /* Anti-spam honeypot: visually hidden */
  .hp-wrap {
    display: none !important;
    visibility: hidden !important;
    opacity: 0 !important;
    position: absolute !important;
    left: -9999px !important;
  }
</style>
</head>
<body>
<div class="container">
  <div class="header">
    <div class="logo-badge">BW</div>
    <h1>Buku Warung v0.1.0</h1>
    <div class="subtitle">Aplikasi Kasir &amp; Pembukuan Warung Offline-First</div>
  </div>

  <div class="card">
    <div class="product-summary">
      <div>
        <div class="product-name">Lisensi Resmi Buku Warung</div>
        <div class="product-tag">1 Lisensi = 1 Email = 1 HP Android</div>
      </div>
      <div class="product-price">
        <div class="price-amount" id="displayAmount">Rp 50.000</div>
        <div class="price-type">Sekali Beli</div>
      </div>
    </div>

    <!-- STEP 1: FORM PEMESANAN -->
    <div class="card-body" id="stepOrderForm">
      <div id="formAlert" class="alert-banner alert-danger"></div>

      <form id="orderForm" novalidate>
        <!-- Honeypot anti-spam (harus tetap kosong) -->
        <div class="hp-wrap">
          <label for="website">Website</label>
          <input type="text" id="website" name="website" tabindex="-1" autocomplete="off">
        </div>

        <div class="form-group">
          <label for="customerName">Nama Pemilik Warung <span class="required">*</span></label>
          <input type="text" id="customerName" name="customerName" placeholder="Contoh: Budi Santoso" autocomplete="name" required>
          <div class="field-error" id="nameError">Nama wajib diisi (minimal 2 huruf).</div>
        </div>

        <div class="form-group">
          <label for="customerContact">Nomor WhatsApp Aktif <span class="required">*</span></label>
          <input type="tel" id="customerContact" name="customerContact" placeholder="Contoh: 081234567890" autocomplete="tel" required>
          <div class="help-text">Nomor WhatsApp untuk pengiriman instruksi dan bantuan teknis.</div>
          <div class="field-error" id="contactError">Nomor WhatsApp Indonesia tidak valid (harus 08xx / 628xx).</div>
        </div>

        <div class="form-group">
          <label for="ownerEmail">Email Aktivasi Pemilik <span class="required">*</span></label>
          <input type="email" id="ownerEmail" name="ownerEmail" placeholder="Contoh: budi@warung.com" autocomplete="email" required>
          <div class="help-text">Email ini akan digunakan untuk aktivasi perangkat di aplikasi.</div>
          <div class="field-error" id="emailError">Format alamat email tidak valid.</div>
        </div>

        <button type="submit" class="btn btn-primary" id="submitBtn">
          <span>Lanjutkan Pesanan (Rp 50.000)</span>
        </button>
      </form>

      <div class="trust-list">
        <div class="trust-item"><span class="trust-icon">✓</span> Sekali bayar untuk selamanya tanpa biaya langganan</div>
        <div class="trust-item"><span class="trust-icon">✓</span> 100% Berjalan offline tanpa perlu paket internet kasir</div>
        <div class="trust-item"><span class="trust-icon">✓</span> Cetak struk printer thermal bluetooth 58mm / 80mm</div>
      </div>
    </div>

    <!-- STEP 2: PEMBAYARAN QRIS & STATUS -->
    <div class="card-body" id="stepPayment" style="display: none;">
      <div id="paymentAlert" class="alert-banner alert-info" style="display: block;">
        Pesanan berhasil dibuat! Silakan scan QRIS di bawah ini untuk menyelesaikan pembayaran.
      </div>

      <div class="order-meta">
        <div class="meta-row">
          <span class="meta-label">Nomor Pesanan</span>
          <span class="meta-value" id="orderNumberVal">-</span>
        </div>
        <div class="meta-row">
          <span class="meta-label">Total Pembayaran</span>
          <span class="meta-value" style="color: #059669;" id="amountVal">Rp 50.000</span>
        </div>
        <div class="meta-row">
          <span class="meta-label">Status Pesanan</span>
          <span class="status-badge status-pending" id="statusVal">Menunggu Pembayaran</span>
        </div>
        <div class="meta-row">
          <span class="meta-label">Metode Bayar</span>
          <span class="meta-value" id="paymentMethodVal">QRIS — KIOS KIARA</span>
        </div>
        <div class="meta-row">
          <span class="meta-label">Nama Pemesan</span>
          <span class="meta-value" id="customerNameVal">-</span>
        </div>
      </div>

      <div class="qris-box" id="qrisContainer">
        <div class="qris-merchant">KIOS KIARA</div>
        <div class="qris-nmid">NMID: ID1026512762125</div>
        <img src="https://license.skmnetwork.com/img/qris-kios-kiara.png" alt="QRIS Kios Kiara" class="qris-image" id="qrisImg">
        <div class="qris-instruction">
          Buka aplikasi BCA, Mandiri, BRI, GoPay, OVO, Dana, atau ShopeePay lalu scan QRIS di atas sebesar <strong>Rp 50.000</strong>.
        </div>
      </div>

      <a href="#" target="_blank" class="btn btn-whatsapp" id="btnConfirmWhatsApp">
        <span>Saya Sudah Membayar</span>
      </a>

      <button type="button" class="btn btn-secondary" id="btnRefreshStatus">
        <span>Periksa Status Pembayaran</span>
      </button>

      <div class="poll-indicator" id="pollIndicator">
        <div class="spinner"></div>
        <span>Memeriksa status pembayaran otomatis...</span>
      </div>

      <div class="trust-list">
        <div class="trust-item"><span class="trust-icon">ℹ</span> Setelah konfirmasi WhatsApp, admin akan memverifikasi dan lisensi akan diaktifkan.</div>
        <div class="trust-item"><span class="trust-icon">ℹ</span> Simpan nomor pesanan Anda untuk keperluan konfirmasi dan garansi perangkat.</div>
      </div>
    </div>
  </div>

  <div class="footer">
    &copy; ${new Date().getFullYear()} <a href="https://skmnetwork.com" target="_blank" rel="noopener">SKMNetwork</a> · Dukungan Teknis: 085157056604
  </div>
</div>

<script>
(function() {
  var ADMIN_WA = '6285157056604';
  var currentPublicToken = '';
  var currentOrderData = null;
  var pollIntervalId = null;

  // DOM Elements
  var stepOrderForm = document.getElementById('stepOrderForm');
  var stepPayment = document.getElementById('stepPayment');
  var orderForm = document.getElementById('orderForm');
  var submitBtn = document.getElementById('submitBtn');
  var formAlert = document.getElementById('formAlert');
  var nameInput = document.getElementById('customerName');
  var contactInput = document.getElementById('customerContact');
  var emailInput = document.getElementById('ownerEmail');
  var websiteInput = document.getElementById('website');

  var nameError = document.getElementById('nameError');
  var contactError = document.getElementById('contactError');
  var emailError = document.getElementById('emailError');

  var orderNumberVal = document.getElementById('orderNumberVal');
  var amountVal = document.getElementById('amountVal');
  var statusVal = document.getElementById('statusVal');
  var paymentMethodVal = document.getElementById('paymentMethodVal');
  var customerNameVal = document.getElementById('customerNameVal');
  var btnConfirmWhatsApp = document.getElementById('btnConfirmWhatsApp');
  var btnRefreshStatus = document.getElementById('btnRefreshStatus');
  var pollIndicator = document.getElementById('pollIndicator');

  // Helper formatting
  function formatRupiah(num) {
    return 'Rp ' + (num || 50000).toLocaleString('id-ID');
  }

  // Token recovery from URL Hash (#token=...) or Session
  function getFragmentToken() {
    var hash = window.location.hash || '';
    if (hash.indexOf('token=') !== -1) {
      var parts = hash.substring(1).split('&');
      for (var i = 0; i < parts.length; i++) {
        var pair = parts[i].split('=');
        if (pair[0] === 'token' && pair[1]) {
          return decodeURIComponent(pair[1]);
        }
      }
    }
    return '';
  }

  function setFragmentToken(token) {
    if (token) {
      window.location.hash = 'token=' + encodeURIComponent(token);
    }
  }

  // Phone Normalization Check (Indonesian Mobile 08xx/628xx)
  function validatePhone(phone) {
    var cleaned = (phone || '').replace(/[^0-9+]/g, '');
    if (cleaned.indexOf('+62') === 0) cleaned = '62' + cleaned.substring(3);
    else if (cleaned.indexOf('0') === 0) cleaned = '62' + cleaned.substring(1);
    else if (cleaned.indexOf('8') === 0) cleaned = '628' + cleaned.substring(1);

    var valid = /^628[0-9]{8,12}$/.test(cleaned);
    return { valid: valid, normalized: cleaned };
  }

  function validateEmail(email) {
    var re = /^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$/;
    return re.test((email || '').trim());
  }

  // Render Order Details in Step 2
  function displayPaymentView(order, token) {
    currentPublicToken = token || currentPublicToken;
    currentOrderData = order;

    stepOrderForm.style.display = 'none';
    stepPayment.style.display = 'block';

    orderNumberVal.textContent = order.orderNumber || '-';
    amountVal.textContent = formatRupiah(order.amount);
    paymentMethodVal.textContent = order.paymentMethod || 'QRIS — KIOS KIARA';
    customerNameVal.textContent = order.customerNameMasked || nameInput.value.trim() || 'Pelanggan';

    // Status mapping
    var status = order.status || 'PENDING_PAYMENT';
    var paymentStatus = order.paymentStatus || 'UNPAID';

    if (paymentStatus === 'PAID' || status === 'PAID' || status === 'COMPLETED' || status === 'DELIVERED') {
      statusVal.textContent = 'Pembayaran Diterima';
      statusVal.className = 'status-badge status-paid';
      stopPolling();
      pollIndicator.innerHTML = '<span style="color: #15803d; font-weight: 600;">✓ Pembayaran Telah Terverifikasi</span>';
    } else {
      statusVal.textContent = 'Menunggu Pembayaran';
      statusVal.className = 'status-badge status-pending';
    }

    // Prepare WhatsApp Confirmation Link
    var custName = order.customerNameMasked || nameInput.value.trim() || 'Customer';
    var waMessage = 'Halo Admin SKMNetwork,\\n' +
      'saya sudah melakukan pembayaran Buku Warung.\\n\\n' +
      'Order: ' + (order.orderNumber || '') + '\\n' +
      'Nama: ' + custName + '\\n' +
      'Nominal: Rp 50.000\\n\\n' +
      'Mohon dibantu verifikasi pembayaran.';

    var waUrl = 'https://wa.me/' + ADMIN_WA + '?text=' + encodeURIComponent(waMessage);
    btnConfirmWhatsApp.href = waUrl;

    // Start status polling if unpaid
    if (paymentStatus !== 'PAID') {
      startPolling();
    }
  }

  // Lookup Order by Token
  function fetchOrderStatus(token, isManual) {
    if (!token) return;
    if (isManual) {
      btnRefreshStatus.disabled = true;
      btnRefreshStatus.textContent = 'Memeriksa...';
    }

    fetch('/v1/public/orders/' + encodeURIComponent(token))
      .then(function(res) {
        if (!res.ok) {
          throw new Error('STATUS_' + res.status);
        }
        return res.json();
      })
      .then(function(json) {
        if (json.success && json.data) {
          displayPaymentView(json.data, token);
        }
      })
      .catch(function(err) {
        // Safe error handling — never crash UI
        if (err.message === 'STATUS_404') {
          stopPolling();
          pollIndicator.textContent = 'Pesanan tidak ditemukan atau tautan sudah kedaluwarsa.';
        }
      })
      .finally(function() {
        if (isManual) {
          btnRefreshStatus.disabled = false;
          btnRefreshStatus.textContent = 'Periksa Status Pembayaran';
        }
      });
  }

  // Safe Polling (12 seconds, stops on hidden tab/unmount)
  function startPolling() {
    stopPolling();
    pollIndicator.style.display = 'flex';
    pollIntervalId = setInterval(function() {
      if (document.hidden) return; // Pauses when tab is hidden
      if (currentPublicToken) {
        fetchOrderStatus(currentPublicToken, false);
      }
    }, 12000);
  }

  function stopPolling() {
    if (pollIntervalId) {
      clearInterval(pollIntervalId);
      pollIntervalId = null;
    }
  }

  // Handle visibility change
  document.addEventListener('visibilitychange', function() {
    if (document.hidden) {
      stopPolling();
    } else if (currentPublicToken && currentOrderData && currentOrderData.paymentStatus !== 'PAID') {
      fetchOrderStatus(currentPublicToken, false);
      startPolling();
    }
  });

  // Manual status refresh click
  btnRefreshStatus.addEventListener('click', function() {
    if (currentPublicToken) {
      fetchOrderStatus(currentPublicToken, true);
    }
  });

  // Form Submission
  orderForm.addEventListener('submit', function(e) {
    e.preventDefault();

    formAlert.style.display = 'none';
    nameError.style.display = 'none';
    contactError.style.display = 'none';
    emailError.style.display = 'none';

    var nameVal = nameInput.value.trim();
    var contactVal = contactInput.value.trim();
    var emailVal = emailInput.value.trim();
    var hpVal = websiteInput.value.trim();

    // Anti-spam honeypot
    if (hpVal !== '') {
      formAlert.textContent = 'Permintaan tidak dapat diproses.';
      formAlert.style.display = 'block';
      return;
    }

    var hasError = false;

    if (!nameVal || nameVal.length < 2) {
      nameError.style.display = 'block';
      hasError = true;
    }

    var phoneResult = validatePhone(contactVal);
    if (!phoneResult.valid) {
      contactError.style.display = 'block';
      hasError = true;
    }

    if (!validateEmail(emailVal)) {
      emailError.style.display = 'block';
      hasError = true;
    }

    if (hasError) return;

    // Submit payload
    submitBtn.disabled = true;
    submitBtn.textContent = 'Memproses Pesanan...';

    // Parse URL params for UTM/attribution
    var searchParams = new URLSearchParams(window.location.search);

    var payload = {
      customerName: nameVal,
      customerContact: phoneResult.normalized,
      ownerEmail: emailVal.toLowerCase(),
      leadToken: searchParams.get('leadToken') || undefined,
      utm_source: searchParams.get('utm_source') || undefined,
      utm_medium: searchParams.get('utm_medium') || undefined,
      utm_campaign: searchParams.get('utm_campaign') || undefined,
      utm_content: searchParams.get('utm_content') || undefined
    };

    fetch('/v1/public/orders', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(payload)
    })
    .then(function(res) {
      return res.json().then(function(body) {
        return { status: res.status, body: body };
      });
    })
    .then(function(result) {
      if ((result.status === 201 || result.status === 200) && result.body.success && result.body.data) {
        var data = result.body.data;
        var token = data.publicToken;
        setFragmentToken(token);
        displayPaymentView(data, token);
      } else {
        var errMsg = (result.body && result.body.error && result.body.error.message) || 'Gagal memproses pesanan. Silakan coba beberapa saat lagi.';
        if (result.status === 429) {
          errMsg = 'Terlalu banyak percobaan. Harap tunggu beberapa menit sebelum mencoba kembali.';
        }
        formAlert.textContent = errMsg;
        formAlert.style.display = 'block';
      }
    })
    .catch(function() {
      formAlert.textContent = 'Terjadi gangguan koneksi internet. Pastikan perangkat Anda terhubung.';
      formAlert.style.display = 'block';
    })
    .finally(function() {
      submitBtn.disabled = false;
      submitBtn.textContent = 'Lanjutkan Pesanan (Rp 50.000)';
    });
  });

  // Check URL on load (Recovery / Direct Lookup)
  var initialToken = getFragmentToken();
  if (initialToken) {
    fetchOrderStatus(initialToken, false);
  }
})();
</script>
</body>
</html>`;
}

