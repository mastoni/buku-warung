// Buku Warung License Admin Dashboard Client Logic

let currentTab = 'dashboard';
let cachedLicenses = [];
let cachedOrders = [];
let cachedRecoveries = [];
let currentFilter = 'ALL';
let currentOrderFilter = 'ALL';
let activeCreatedCode = '';
let activeDeliveryOrder = null;

// DOM Elements
const loginSection = document.getElementById('loginSection');
const appSection = document.getElementById('appSection');
const loginForm = document.getElementById('loginForm');
const loginError = document.getElementById('loginError');
const logoutBtn = document.getElementById('logoutBtn');
const adminUsernameDisplay = document.getElementById('adminUsernameDisplay');

// Init
document.addEventListener('DOMContentLoaded', () => {
  checkAuth();
  setupEventListeners();
});

function setupEventListeners() {
  loginForm.addEventListener('submit', handleLogin);
  logoutBtn.addEventListener('click', handleLogout);

  // Tab navigation
  document.querySelectorAll('.nav-item').forEach((item) => {
    item.addEventListener('click', () => {
      const tab = item.getAttribute('data-tab');
      switchTab(tab);
    });
  });

  // License Filter buttons
  document.querySelectorAll('.filter-btn').forEach((btn) => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('.filter-btn').forEach((b) => b.classList.remove('active'));
      btn.classList.add('active');
      currentFilter = btn.getAttribute('data-filter');
      renderLicensesTable();
    });
  });

  // Order Filter buttons
  document.querySelectorAll('.order-filter-btn').forEach((btn) => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('.order-filter-btn').forEach((b) => b.classList.remove('active'));
      btn.classList.add('active');
      currentOrderFilter = btn.getAttribute('data-order-filter');
      renderOrdersTable();
    });
  });

  // License Search input
  const searchInput = document.getElementById('licenseSearchInput');
  if (searchInput) {
    searchInput.addEventListener('input', () => {
      renderLicensesTable();
    });
  }

  // Order Search input
  const orderSearchInput = document.getElementById('orderSearchInput');
  if (orderSearchInput) {
    orderSearchInput.addEventListener('input', () => {
      renderOrdersTable();
    });
  }

  // Create license direct modal
  document.getElementById('openCreateModalBtn').addEventListener('click', () => {
    document.getElementById('createLicenseForm').reset();
    document.getElementById('createLicenseForm').classList.remove('hidden');
    document.getElementById('createSuccessBox').classList.add('hidden');
    activeCreatedCode = '';
    openModal('createLicenseModal');
  });

  // Create order modal button
  const openCreateOrderBtn = document.getElementById('openCreateOrderBtn');
  if (openCreateOrderBtn) {
    openCreateOrderBtn.addEventListener('click', () => {
      document.getElementById('createOrderForm').reset();
      openModal('createOrderModal');
    });
  }

  document.getElementById('createLicenseForm').addEventListener('submit', handleCreateLicense);
  document.getElementById('createOrderForm').addEventListener('submit', handleCreateOrder);
  document.getElementById('verifyPaymentForm').addEventListener('submit', handleVerifyPayment);

  const markDeliveredBtn = document.getElementById('markDeliveredBtn');
  if (markDeliveredBtn) {
    markDeliveredBtn.addEventListener('click', () => {
      if (activeDeliveryOrder) {
        handleMarkDelivered(activeDeliveryOrder.id);
      }
    });
  }
}

// Authentication
async function checkAuth() {
  try {
    const res = await fetch('/api/auth/me');
    if (res.ok) {
      const data = await res.json();
      showApp(data.data.username);
    } else {
      showLogin();
    }
  } catch {
    showLogin();
  }
}

async function handleLogin(e) {
  e.preventDefault();
  loginError.classList.add('hidden');
  const username = document.getElementById('usernameInput').value.trim();
  const password = document.getElementById('passwordInput').value;

  try {
    const res = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password })
    });

    const data = await res.json();
    if (res.ok && data.success) {
      showApp(data.data.username);
    } else {
      loginError.textContent = data.error?.message || 'Login gagal. Periksa kembali username dan password.';
      loginError.classList.remove('hidden');
    }
  } catch {
    loginError.textContent = 'Gagal terhubung ke server admin.';
    loginError.classList.remove('hidden');
  }
}

async function handleLogout() {
  try {
    await fetch('/api/auth/logout', { method: 'POST' });
  } finally {
    showLogin();
    showToast('Berhasil logout.');
  }
}

function showLogin() {
  loginSection.classList.remove('hidden');
  appSection.classList.add('hidden');
}

function showApp(username) {
  adminUsernameDisplay.textContent = username || 'admin';
  loginSection.classList.add('hidden');
  appSection.classList.remove('hidden');
  switchTab(currentTab);
}

// Tab Switching
function switchTab(tab) {
  currentTab = tab;
  document.querySelectorAll('.nav-item').forEach((btn) => {
    btn.classList.toggle('active', btn.getAttribute('data-tab') === tab);
  });

  document.querySelectorAll('.tab-view').forEach((view) => {
    view.classList.add('hidden');
  });

  const activeView = document.getElementById(`tab-${tab}`);
  if (activeView) {
    activeView.classList.remove('hidden');
  }

  const titles = {
    dashboard: 'Dashboard Overview',
    sales: 'Penjualan Buku Warung (Order & Sales Management)',
    licenses: 'Daftar Lisensi Komersial',
    recovery: 'Permintaan Recovery & Rebind Perangkat',
    audit: 'Audit Trail Log'
  };
  document.getElementById('pageTitle').textContent = titles[tab] || 'Dashboard';

  if (tab === 'dashboard') loadDashboard();
  if (tab === 'sales') loadSales();
  if (tab === 'licenses') loadLicenses();
  if (tab === 'recovery') loadRecoveries();
  if (tab === 'audit') loadAuditLogs();
}

// Data Fetching: Sales (Penjualan Buku Warung)
async function loadSales() {
  try {
    const res = await fetch('/api/orders');
    if (res.ok) {
      const result = await res.json();
      cachedOrders = result.data || [];
      const metrics = result.metrics || {
        totalOrders: 0,
        pendingPayment: 0,
        paid: 0,
        pendingLicense: 0,
        delivered: 0,
        active: 0,
        paidRevenue: 0,
        actionRequiredCount: 0
      };

      // Summary cards
      document.getElementById('metricSalesTotal').textContent = metrics.totalOrders;
      document.getElementById('metricSalesPendingPayment').textContent = metrics.pendingPayment;
      document.getElementById('metricSalesPaid').textContent = metrics.paid;
      document.getElementById('metricSalesPendingLicense').textContent = metrics.pendingLicense;
      document.getElementById('metricSalesActive').textContent = metrics.active;
      const revEl = document.getElementById('metricSalesRevenue');
      if (revEl) {
        revEl.textContent = `Rp ${formatNumber(metrics.paidRevenue || 0)}`;
      }

      // Sidebar Action badge
      const actionBadge = document.getElementById('salesActionBadge');
      if (actionBadge) {
        if (metrics.actionRequiredCount > 0) {
          actionBadge.textContent = metrics.actionRequiredCount;
          actionBadge.classList.remove('hidden');
        } else {
          actionBadge.classList.add('hidden');
        }
      }

      // Action Queue Header Badge
      const queueBadge = document.getElementById('actionQueueSummaryBadge');
      if (queueBadge) {
        queueBadge.textContent = `${metrics.actionRequiredCount} Perlu Tindakan`;
      }

      // Render PERLU DIPROSES and Orders Table
      renderSalesActionQueue(cachedOrders, metrics);
      renderOrdersTable();
    }
  } catch {
    showToast('Gagal memuat data penjualan.');
  }
}

function renderSalesActionQueue(orders, metrics) {
  const container = document.getElementById('salesActionQueue');
  if (!container) return;

  const actionItems = [];

  orders.forEach((order) => {
    if (order.payment_status === 'UNPAID') {
      actionItems.push({
        type: 'VERIFY_PAY',
        order,
        priority: 2,
        title: `${escapeHtml(order.customer_name)} (${order.order_code || order.order_number})`,
        meta: `Rp ${formatNumber(order.amount)} • Menunggu Pembayaran`,
        btnLabel: '💳 Verifikasi Bayar',
        btnClass: 'btn btn-warning btn-sm',
        action: () => openVerifyPaymentModal(order.id)
      });
    } else if (order.payment_status === 'PAID' && !order.license_id) {
      actionItems.push({
        type: 'CREATE_LICENSE',
        order,
        priority: 1,
        title: `${escapeHtml(order.customer_name)} (${order.order_code || order.order_number})`,
        meta: `PAID • ${escapeHtml(order.owner_email)} • License Belum Dibuat`,
        btnLabel: '🔑 Buat License',
        btnClass: 'btn btn-primary btn-sm',
        action: () => handleGenerateLicenseForOrder(order.id, order.customer_name, order.owner_email)
      });
    } else if (order.license_id && (order.status === 'LICENSE_CREATED' || order.status === 'PAID')) {
      actionItems.push({
        type: 'SEND_WA',
        order,
        priority: 3,
        title: `${escapeHtml(order.customer_name)} (${order.order_code || order.order_number})`,
        meta: `License Siap • Belum Dikirim ke WA (${escapeHtml(order.customer_whatsapp || order.customer_contact || '-')})`,
        btnLabel: '📲 Siapkan WhatsApp',
        btnClass: 'btn btn-success btn-sm',
        action: () => openDeliveryPreparationModal(order.id)
      });
    }
  });

  // Sort by priority (1: create license, 2: verify pay, 3: send WA)
  actionItems.sort((a, b) => a.priority - b.priority);

  if (actionItems.length === 0) {
    container.innerHTML = `
      <div class="text-center text-muted p-4" style="grid-column: 1 / -1;">
        ✨ Semua pesanan telah diproses. Tidak ada tindakan mendesak saat ini.
      </div>
    `;
    return;
  }

  container.innerHTML = '';
  actionItems.forEach((item, index) => {
    const el = document.createElement('div');
    el.className = 'action-queue-item';
    el.innerHTML = `
      <div class="action-queue-info">
        <span class="action-queue-title">${item.title}</span>
        <span class="action-queue-meta">${item.meta}</span>
      </div>
      <div class="action-queue-btn">
        <button id="actionQueueBtn_${index}" class="${item.btnClass}">${item.btnLabel}</button>
      </div>
    `;
    container.appendChild(el);
    document.getElementById(`actionQueueBtn_${index}`).addEventListener('click', item.action);
  });
}

function renderOrdersTable() {
  const tbody = document.getElementById('ordersTbody');
  if (!tbody) return;

  if (cachedOrders.length === 0) {
    tbody.innerHTML = '<tr><td colspan="10" class="text-center text-muted p-4">Belum ada order. Klik "+ Order Baru" untuk membuat pesanan.</td></tr>';
    return;
  }

  const search = (document.getElementById('orderSearchInput')?.value || '').toLowerCase().trim();

  let filtered = cachedOrders.filter((o) => {
    let matchFilter = true;
    if (currentOrderFilter === 'PENDING_PAYMENT') {
      matchFilter = o.payment_status === 'UNPAID';
    } else if (currentOrderFilter === 'PAID') {
      matchFilter = o.payment_status === 'PAID';
    } else if (currentOrderFilter === 'PENDING_LICENSE') {
      matchFilter = o.payment_status === 'PAID' && !o.license_id;
    } else if (currentOrderFilter === 'DELIVERED') {
      matchFilter = o.status === 'DELIVERED';
    } else if (currentOrderFilter === 'ACTIVE') {
      matchFilter = o.status === 'ACTIVE' || o.effective_status === 'ACTIVE';
    }

    const matchSearch =
      !search ||
      (o.order_code || '').toLowerCase().includes(search) ||
      (o.order_number || '').toLowerCase().includes(search) ||
      (o.customer_name || '').toLowerCase().includes(search) ||
      (o.customer_whatsapp || '').toLowerCase().includes(search) ||
      (o.customer_contact || '').toLowerCase().includes(search) ||
      (o.owner_email || '').toLowerCase().includes(search) ||
      (o.notes || '').toLowerCase().includes(search);

    return matchFilter && matchSearch;
  });

  if (filtered.length === 0) {
    tbody.innerHTML = '<tr><td colspan="10" class="text-center text-muted p-4">Order tidak ditemukan dengan filter saat ini.</td></tr>';
    return;
  }

  tbody.innerHTML = filtered
    .map(
      (o) => `
    <tr>
      <td><strong class="mono-text">${escapeHtml(o.order_code || o.order_number)}</strong></td>
      <td><strong>${escapeHtml(o.customer_name)}</strong></td>
      <td>${escapeHtml(o.customer_whatsapp || o.customer_contact || '-')}</td>
      <td>${escapeHtml(o.owner_email)}</td>
      <td>Rp ${formatNumber(o.amount)}</td>
      <td>${renderPaymentStatusBadge(o.payment_status)}</td>
      <td>${renderOrderStatusBadge(o.status || o.effective_status)}</td>
      <td>${o.license_code ? `<span class="mono-text text-success">${escapeHtml(o.license_code)}</span>` : (o.license_id ? `<span class="badge badge-info">ID #${o.license_id}</span>` : '<span class="text-muted">-</span>')}</td>
      <td>${formatDate(o.created_at)}</td>
      <td>
        <div style="display: flex; gap: 4px; flex-wrap: wrap;">
          <button class="btn btn-secondary btn-sm" onclick="openOrderDetail(${o.id})">Detail</button>
          ${
            o.payment_status === 'UNPAID'
              ? `<button class="btn btn-warning btn-sm" onclick="openVerifyPaymentModal(${o.id})">Verifikasi Bayar</button>`
              : ''
          }
          ${
            o.payment_status === 'PAID' && !o.license_id
              ? `<button class="btn btn-primary btn-sm" onclick="handleGenerateLicenseForOrder(${o.id}, '${escapeHtml(o.customer_name)}', '${escapeHtml(o.owner_email)}')">Buat License</button>`
              : ''
          }
          ${
            o.license_id && (o.status === 'LICENSE_CREATED' || o.status === 'PAID')
              ? `<button class="btn btn-success btn-sm" onclick="openDeliveryPreparationModal(${o.id})">Kirim WA</button>`
              : ''
          }
        </div>
      </td>
    </tr>
  `
    )
    .join('');
}

// Order Form & Actions
async function handleCreateOrder(e) {
  e.preventDefault();
  const customerName = document.getElementById('orderCustomerName').value.trim();
  const customerWhatsapp = document.getElementById('orderCustomerWhatsapp').value.trim();
  const ownerEmail = document.getElementById('orderOwnerEmail').value.trim();
  const notes = document.getElementById('orderNotes').value.trim();

  const submitBtn = document.getElementById('submitCreateOrderBtn');
  submitBtn.disabled = true;
  submitBtn.textContent = 'Menyimpan...';

  try {
    const res = await fetch('/api/orders', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        customerName,
        customerWhatsapp,
        ownerEmail,
        notes
      })
    });

    const data = await res.json();
    if (res.ok && data.success) {
      showToast(`Order ${data.data.orderCode} berhasil dibuat!`);
      closeModal('createOrderModal');
      loadSales();
      loadDashboard();
    } else {
      showToast(data.error?.message || 'Gagal membuat order.');
    }
  } catch {
    showToast('Gagal menghubungi server admin.');
  } finally {
    submitBtn.disabled = false;
    submitBtn.textContent = 'Simpan Order';
  }
}

async function openOrderDetail(id) {
  try {
    const res = await fetch(`/api/orders/${id}`);
    if (!res.ok) {
      showToast('Gagal memuat detail order.');
      return;
    }
    const { data } = await res.json();
    const order = data.order;
    const license = data.license;
    const devices = data.devices || [];

    document.getElementById('detailOrderCodeHeader').textContent = `(${order.order_code})`;
    document.getElementById('detailCustomerName').textContent = order.customer_name;
    document.getElementById('detailCustomerWhatsapp').textContent = order.customer_whatsapp;
    document.getElementById('detailOwnerEmail').textContent = order.owner_email;
    document.getElementById('detailOrderNotes').textContent = order.notes || '-';

    document.getElementById('detailOrderCode').textContent = order.order_code;
    document.getElementById('detailOrderProduct').textContent = order.product;
    document.getElementById('detailOrderAmount').textContent = `Rp ${formatNumber(order.amount)}`;
    document.getElementById('detailOrderStatusBadge').innerHTML = renderOrderStatusBadge(order.status);
    document.getElementById('detailOrderCreatedAt').textContent = formatDate(order.created_at);
    document.getElementById('detailOrderDeliveredAt').textContent = order.delivered_at ? formatDate(order.delivered_at) : '-';

    document.getElementById('detailPaymentStatusBadge').innerHTML = renderPaymentStatusBadge(order.payment_status);
    document.getElementById('detailPaymentAmount').textContent = `Rp ${formatNumber(order.amount)}`;
    document.getElementById('detailPaymentMethodRef').textContent = `${order.payment_method || '-'} / ${order.payment_reference || '-'}`;
    document.getElementById('detailPaymentVerifiedAt').textContent = order.verified_at ? formatDate(order.verified_at) : '-';
    document.getElementById('detailPaymentVerifiedBy').textContent = order.verified_by || '-';
    document.getElementById('detailPaymentNotes').textContent = order.notes || '-';

    if (license) {
      document.getElementById('detailOrderLicenseCode').textContent = order.license_code || license.license_uuid;
      document.getElementById('detailOrderLicenseStatus').innerHTML = renderStatusBadge(license.status);
      const activeDevice = devices.find((d) => d.status === 'ACTIVE');
      document.getElementById('detailOrderDeviceStatus').textContent = activeDevice ? `Active (${maskString(activeDevice.device_binding)})` : 'Belum Ada / Pending';
      document.getElementById('detailOrderActivatedAt').textContent = license.activated_at ? formatDate(license.activated_at) : '-';
    } else {
      document.getElementById('detailOrderLicenseCode').textContent = '-';
      document.getElementById('detailOrderLicenseStatus').textContent = 'Belum Dibuat';
      document.getElementById('detailOrderDeviceStatus').textContent = '-';
      document.getElementById('detailOrderActivatedAt').textContent = '-';
    }

    // Render Order Lifecycle Timeline
    const timelineEl = document.getElementById('orderLifecycleTimeline');
    if (timelineEl) {
      const isPaid = order.payment_status === 'PAID';
      const isLicenseCreated = Boolean(order.license_id);
      const isDelivered = order.status === 'DELIVERED' || Boolean(order.delivered_at);
      const activeDevice = devices.find((d) => d.status === 'ACTIVE');
      const isActive = order.status === 'ACTIVE' || Boolean(activeDevice);

      timelineEl.innerHTML = `
        <div class="timeline-step completed">
          <div class="timeline-dot">✓</div>
          <div class="timeline-content">
            <div class="timeline-title">
              <span>1. Order Dibuat</span>
              <span class="timeline-time">${formatDate(order.created_at)}</span>
            </div>
            <div class="timeline-desc">Order #${escapeHtml(order.order_code || order.order_number)} dibuat untuk ${escapeHtml(order.customer_name)} (Rp ${formatNumber(order.amount)})</div>
          </div>
        </div>

        <div class="timeline-step ${isPaid ? 'completed' : 'active'}">
          <div class="timeline-dot">${isPaid ? '✓' : '2'}</div>
          <div class="timeline-content">
            <div class="timeline-title">
              <span>2. Pembayaran (Verifikasi Admin)</span>
              <span class="timeline-time">${isPaid ? formatDate(order.verified_at) : 'Menunggu'}</span>
            </div>
            <div class="timeline-desc">${isPaid ? `Diverifikasi oleh ${escapeHtml(order.verified_by || 'ADMIN')} (${escapeHtml(order.payment_method || 'Transfer')})` : 'Pelanggan belum bayar / pembayaran belum diverifikasi admin.'}</div>
          </div>
        </div>

        <div class="timeline-step ${isLicenseCreated ? 'completed' : (isPaid ? 'active' : '')}">
          <div class="timeline-dot">${isLicenseCreated ? '✓' : '3'}</div>
          <div class="timeline-content">
            <div class="timeline-title">
              <span>3. Pembuatan Lisensi</span>
              <span class="timeline-time">${isLicenseCreated ? (license ? formatDate(license.created_at) : '-') : 'Menunggu'}</span>
            </div>
            <div class="timeline-desc">${isLicenseCreated ? `Kode Lisensi resmi dikaitkan: ${escapeHtml(order.license_code || license?.license_uuid || 'Tersedia')}` : (isPaid ? 'Pembayaran lunas. Siap digenerate oleh admin.' : 'Menunggu pembayaran selesai.')}</div>
          </div>
        </div>

        <div class="timeline-step ${isDelivered ? 'completed' : (isLicenseCreated ? 'active' : '')}">
          <div class="timeline-dot">${isDelivered ? '✓' : '4'}</div>
          <div class="timeline-content">
            <div class="timeline-title">
              <span>4. Pengiriman Paket ke Pelanggan</span>
              <span class="timeline-time">${isDelivered ? formatDate(order.delivered_at) : 'Menunggu'}</span>
            </div>
            <div class="timeline-desc">${isDelivered ? `Paket APK & Lisensi telah dikirim via WhatsApp (${escapeHtml(order.customer_whatsapp || order.customer_contact || '-')})` : (isLicenseCreated ? 'Lisensi siap dikirimkan ke nomor WhatsApp pelanggan.' : 'Menunggu lisensi dibuat.')}</div>
          </div>
        </div>

        <div class="timeline-step ${isActive ? 'completed' : (isDelivered ? 'active' : '')}">
          <div class="timeline-dot">${isActive ? '✓' : '5'}</div>
          <div class="timeline-content">
            <div class="timeline-title">
              <span>5. Aktivasi di HP Android Pelanggan</span>
              <span class="timeline-time">${isActive ? formatDate(license?.activated_at) : 'Menunggu'}</span>
            </div>
            <div class="timeline-desc">${isActive ? `Aplikasi telah aktif (${activeDevice ? maskString(activeDevice.device_binding) : 'Perangkat Terikat'}). 100% Siap Digunakan Offline.` : 'Pelanggan belum melakukan aktivasi pada aplikasi Buku Warung di HP.'}</div>
          </div>
        </div>
      `;
    }

    // Modal footer dynamic actions
    const footer = document.getElementById('orderDetailModalFooter');
    let actionButtons = `<button type="button" class="btn btn-secondary" onclick="closeModal('orderDetailModal')">Tutup</button>`;

    if (order.payment_status === 'UNPAID') {
      actionButtons = `
        <button type="button" class="btn btn-warning" onclick="closeModal('orderDetailModal'); openVerifyPaymentModal(${order.id});">Verifikasi Pembayaran</button>
        ${actionButtons}
      `;
    } else if (order.payment_status === 'PAID' && !order.license_id) {
      actionButtons = `
        <button type="button" class="btn btn-primary" onclick="closeModal('orderDetailModal'); handleGenerateLicenseForOrder(${order.id}, '${escapeHtml(order.customer_name)}', '${escapeHtml(order.owner_email)}');">🔑 Generate License Sekarang</button>
        ${actionButtons}
      `;
    } else if (order.license_id) {
      actionButtons = `
        <button type="button" class="btn btn-success" onclick="closeModal('orderDetailModal'); openDeliveryPreparationModal(${order.id});">📲 Siapkan Pesan WhatsApp</button>
        ${actionButtons}
      `;
    }
    footer.innerHTML = actionButtons;

    openModal('orderDetailModal');
  } catch {
    showToast('Terjadi kesalahan saat memuat detail order.');
  }
}

function openVerifyPaymentModal(orderId) {
  const order = cachedOrders.find((o) => o.id === orderId);
  if (!order) return;

  document.getElementById('verifyPayOrderId').value = order.id;
  document.getElementById('verifyPayCustName').textContent = order.customer_name;
  document.getElementById('verifyPayOrderCode').textContent = order.order_code;
  document.getElementById('verifyPayEmail').textContent = order.owner_email;
  document.getElementById('verifyPayAmount').textContent = `Rp ${formatNumber(order.amount)}`;
  document.getElementById('verifyPaymentMethod').value = 'Transfer Bank';
  document.getElementById('verifyPaymentReference').value = '';
  document.getElementById('verifyPaymentNotes').value = '';

  openModal('verifyPaymentModal');
}

async function handleVerifyPayment(e) {
  e.preventDefault();
  const orderId = document.getElementById('verifyPayOrderId').value;
  const paymentMethod = document.getElementById('verifyPaymentMethod').value.trim();
  const paymentReference = document.getElementById('verifyPaymentReference').value.trim();
  const notes = document.getElementById('verifyPaymentNotes').value.trim();

  const submitBtn = document.getElementById('submitVerifyPaymentBtn');
  submitBtn.disabled = true;
  submitBtn.textContent = 'Memproses...';

  try {
    const res = await fetch(`/api/orders/${orderId}/verify-payment`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        paymentMethod,
        paymentReference,
        notes
      })
    });

    const data = await res.json();
    if (res.ok && data.success) {
      showToast('Pembayaran berhasil diverifikasi (PAID)!');
      closeModal('verifyPaymentModal');
      loadSales();
      loadDashboard();
    } else {
      showToast(data.error?.message || 'Gagal memverifikasi pembayaran.');
    }
  } catch {
    showToast('Terjadi kesalahan jaringan.');
  } finally {
    submitBtn.disabled = false;
    submitBtn.textContent = 'Konfirmasi Pembayaran (PAID)';
  }
}

function handleGenerateLicenseForOrder(orderId, customerName, ownerEmail) {
  const modal = document.getElementById('confirmModal');
  document.getElementById('confirmTitle').textContent = `Generate License untuk Order #${orderId}`;
  document.getElementById('confirmMessage').innerHTML = `
    Konfirmasi generate lisensi produksi Buku Warung v0.1.0 untuk:<br><br>
    <strong>Customer:</strong> ${escapeHtml(customerName)}<br>
    <strong>Owner Email:</strong> ${escapeHtml(ownerEmail)}<br>
    <strong>Nominal:</strong> Rp50.000 (PAID)<br><br>
    <small class="text-muted">License akan dibuat menggunakan License Server resmi dan ditautkan ke pesanan ini.</small>
  `;
  document.getElementById('confirmReasonGroup').classList.add('hidden');

  document.getElementById('confirmActionBtn').onclick = async () => {
    try {
      const res = await fetch(`/api/orders/${orderId}/generate-license`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' }
      });

      const result = await res.json();
      if (res.ok && result.success) {
        closeModal('confirmModal');
        showToast('Lisensi berhasil dibuat untuk pesanan!');
        loadSales();
        loadLicenses();
        loadDashboard();

        // Open WhatsApp delivery preparation modal immediately
        openDeliveryPreparationModal(orderId, result.data?.licenseCode);
      } else {
        showToast(result.error?.message || 'Gagal generate lisensi.');
      }
    } catch {
      showToast('Terjadi kesalahan saat memproses lisensi.');
    }
  };

  openModal('confirmModal');
}

async function openDeliveryPreparationModal(orderId, directLicenseCode) {
  try {
    let order = cachedOrders.find((o) => o.id === orderId);
    let licenseCode = directLicenseCode || (order ? order.license_code : '');

    if (!order || !licenseCode) {
      const res = await fetch(`/api/orders/${orderId}`);
      if (res.ok) {
        const d = await res.json();
        order = d.data.order;
        licenseCode = licenseCode || order.license_code;
      }
    }

    if (!order) {
      showToast('Data order tidak ditemukan.');
      return;
    }

    activeDeliveryOrder = order;

    // Display info
    document.getElementById('waRecipientInfo').textContent = `${order.customer_name} (${order.customer_whatsapp}) — ${order.owner_email}`;

    const draftText = `Halo ${order.customer_name} 👋

Pembayaran Buku Warung Anda sudah kami konfirmasi.

Order Code:
${order.order_code}

License Code:
${licenseCode || order.license_code || 'BW-XXXX-XXXX-XXXX'}

Harga:
Rp50.000

Silakan install Buku Warung v0.1.0 lalu lakukan aktivasi menggunakan Owner Email (${order.owner_email}) dan License Code tersebut.

Terima kasih.`;

    document.getElementById('waMessageDraft').value = draftText;

    // Build WA URL
    let waPhone = (order.customer_whatsapp || '').replace(/\D/g, '');
    if (waPhone.startsWith('0')) {
      waPhone = '62' + waPhone.substring(1);
    } else if (waPhone.startsWith('8')) {
      waPhone = '62' + waPhone;
    }

    const waUrl = `https://wa.me/${waPhone}?text=${encodeURIComponent(draftText)}`;
    const openWaBtn = document.getElementById('openWaLinkBtn');
    openWaBtn.href = waUrl;

    const markBtn = document.getElementById('markDeliveredBtn');
    if (order.status === 'DELIVERED' || order.status === 'ACTIVE' || order.status === 'COMPLETED') {
      markBtn.disabled = true;
      markBtn.textContent = `✓ Sudah Dikirim (${formatDate(order.delivered_at)})`;
    } else {
      markBtn.disabled = false;
      markBtn.textContent = '✓ Tandai Sudah Dikirim (DELIVERED)';
    }

    openModal('deliveryPreparationModal');
  } catch {
    showToast('Gagal menyiapkan pesan WhatsApp.');
  }
}

function copyWaMessage() {
  const textarea = document.getElementById('waMessageDraft');
  if (!textarea) return;
  navigator.clipboard.writeText(textarea.value).then(() => {
    const btn = document.getElementById('copyWaMsgBtn');
    btn.textContent = '✅ Disalin!';
    setTimeout(() => {
      btn.textContent = '📋 Salin Pesan';
    }, 2000);
    showToast('Pesan WhatsApp berhasil disalin.');
  });
}

async function handleMarkDelivered(orderId) {
  try {
    const res = await fetch(`/api/orders/${orderId}/mark-delivered`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' }
    });

    const data = await res.json();
    if (res.ok && data.success) {
      showToast('Order berhasil ditandai sebagai DELIVERED.');
      closeModal('deliveryPreparationModal');
      loadSales();
      loadDashboard();
    } else {
      showToast(data.error?.message || 'Gagal menandai status pengiriman.');
    }
  } catch {
    showToast('Terjadi kesalahan jaringan.');
  }
}

// Data Fetching: Dashboard
async function loadDashboard() {
  try {
    const res = await fetch('/api/dashboard/summary');
    if (res.ok) {
      const { data } = await res.json();
      document.getElementById('metricTotal').textContent = data.totalLicenses;
      document.getElementById('metricActive').textContent = data.activeLicenses;
      document.getElementById('metricPending').textContent = data.pendingLicenses;
      document.getElementById('metricRevoked').textContent = data.revokedLicenses;
      document.getElementById('metricDevices').textContent = data.activeDevices;
      document.getElementById('metricRecovery').textContent = data.pendingRecoveryRequests;

      const badge = document.getElementById('recoveryBadge');
      if (data.pendingRecoveryRequests > 0) {
        badge.textContent = data.pendingRecoveryRequests;
        badge.classList.remove('hidden');
      } else {
        badge.classList.add('hidden');
      }
    }

    // Load recent licenses
    const licRes = await fetch('/api/licenses');
    if (licRes.ok) {
      const licData = await licRes.json();
      cachedLicenses = licData.data || [];
      const recents = cachedLicenses.slice(0, 5);
      const tbody = document.getElementById('recentLicensesTbody');
      if (recents.length === 0) {
        tbody.innerHTML = '<tr><td colspan="4" class="text-center text-muted">Belum ada lisensi dibuat.</td></tr>';
      } else {
        tbody.innerHTML = recents
          .map(
            (l) => `
          <tr>
            <td><strong>${escapeHtml(l.owner_email_canonical)}</strong></td>
            <td>${escapeHtml(l.product)}</td>
            <td>${renderStatusBadge(l.status)}</td>
            <td>${formatDate(l.created_at)}</td>
          </tr>
        `
          )
          .join('');
      }
    }

    // Load recent audits
    const audRes = await fetch('/api/audit-logs');
    if (audRes.ok) {
      const audData = await audRes.json();
      const recents = (audData.data || []).slice(0, 5);
      const tbody = document.getElementById('recentAuditTbody');
      if (recents.length === 0) {
        tbody.innerHTML = '<tr><td colspan="4" class="text-center text-muted">Belum ada aktivitas audit.</td></tr>';
      } else {
        tbody.innerHTML = recents
          .map(
            (a) => `
          <tr>
            <td>${formatDate(a.created_at)}</td>
            <td><span class="badge badge-info">${escapeHtml(a.action)}</span></td>
            <td>${escapeHtml(a.actor)}</td>
            <td>${escapeHtml(a.reason || '-')}</td>
          </tr>
        `
          )
          .join('');
      }
    }
  } catch {
    showToast('Gagal memuat ringkasan dashboard.');
  }
}

// Data Fetching: Licenses
async function loadLicenses() {
  try {
    const res = await fetch('/api/licenses');
    if (res.ok) {
      const data = await res.json();
      cachedLicenses = data.data || [];
      renderLicensesTable();
    }
  } catch {
    showToast('Gagal memuat daftar lisensi.');
  }
}

function renderLicensesTable() {
  const tbody = document.getElementById('licensesTbody');
  const search = (document.getElementById('licenseSearchInput')?.value || '').toLowerCase().trim();

  let filtered = cachedLicenses.filter((l) => {
    const matchFilter = currentFilter === 'ALL' || l.status === currentFilter;
    const matchSearch =
      !search ||
      l.owner_email_canonical.toLowerCase().includes(search) ||
      l.license_uuid.toLowerCase().includes(search);
    return matchFilter && matchSearch;
  });

  if (filtered.length === 0) {
    tbody.innerHTML = '<tr><td colspan="7" class="text-center text-muted">Tidak ada lisensi yang cocok.</td></tr>';
    return;
  }

  tbody.innerHTML = filtered
    .map(
      (l) => `
    <tr>
      <td>#${l.id}</td>
      <td><strong>${escapeHtml(l.owner_email_canonical)}</strong></td>
      <td>${escapeHtml(l.product)}</td>
      <td>Rp ${formatNumber(l.price)}</td>
      <td>${renderStatusBadge(l.status)}</td>
      <td>${formatDate(l.created_at)}</td>
      <td>
        <button class="btn btn-secondary btn-sm" onclick="openLicenseDetail(${l.id})">Detail</button>
        ${
          l.status !== 'REVOKED'
            ? `<button class="btn btn-danger btn-sm" onclick="confirmRevokeLicense(${l.id}, '${escapeHtml(l.owner_email_canonical)}')">Revoke</button>`
            : ''
        }
      </td>
    </tr>
  `
    )
    .join('');
}

// License Detail View
async function openLicenseDetail(id) {
  try {
    const res = await fetch(`/api/licenses/${id}`);
    if (!res.ok) {
      showToast('Gagal memuat detail lisensi.');
      return;
    }
    const data = await res.json();
    const { license, devices, recoveryRequests } = data.data;

    document.getElementById('detailLicenseId').textContent = license.id;
    document.getElementById('detailUuid').textContent = license.license_uuid;
    document.getElementById('detailEmail').textContent = license.owner_email_canonical;
    document.getElementById('detailProduct').textContent = license.product;
    document.getElementById('detailPrice').textContent = `Rp ${formatNumber(license.price)}`;
    document.getElementById('detailStatusBadge').innerHTML = renderStatusBadge(license.status);
    document.getElementById('detailCreatedAt').textContent = formatDate(license.created_at);
    document.getElementById('detailActivatedAt').textContent = license.activated_at ? formatDate(license.activated_at) : '-';
    document.getElementById('detailRevokedAt').textContent = license.revoked_at ? formatDate(license.revoked_at) : '-';

    // Revoke license button visibility
    const revokeBtn = document.getElementById('detailRevokeLicenseBtn');
    if (license.status === 'REVOKED') {
      revokeBtn.classList.add('hidden');
    } else {
      revokeBtn.classList.remove('hidden');
      revokeBtn.onclick = () => {
        closeModal('licenseDetailModal');
        confirmRevokeLicense(license.id, license.owner_email_canonical);
      };
    }

    // Devices table
    const devTbody = document.getElementById('detailDevicesTbody');
    if (devices.length === 0) {
      devTbody.innerHTML = '<tr><td colspan="5" class="text-center text-muted">Belum ada perangkat terhubung.</td></tr>';
    } else {
      devTbody.innerHTML = devices
        .map(
          (d) => `
        <tr>
          <td><span class="mono-text">${maskString(d.device_binding)}</span></td>
          <td>${renderStatusBadge(d.status)}</td>
          <td>${formatDate(d.first_activated_at)}</td>
          <td>${formatDate(d.last_validated_at)}</td>
          <td>
            ${
              d.status === 'ACTIVE'
                ? `<button class="btn btn-danger btn-sm" onclick="confirmRevokeDevice(${d.id})">Revoke Device</button>`
                : '-'
            }
          </td>
        </tr>
      `
        )
        .join('');
    }

    // Recoveries table
    const recTbody = document.getElementById('detailRecoveriesTbody');
    if (recoveryRequests.length === 0) {
      recTbody.innerHTML = '<tr><td colspan="6" class="text-center text-muted">Tidak ada permintaan recovery.</td></tr>';
    } else {
      recTbody.innerHTML = recoveryRequests
        .map(
          (r) => `
        <tr>
          <td>#${r.id}</td>
          <td><span class="mono-text">${r.old_device_binding ? maskString(r.old_device_binding) : '-'}</span></td>
          <td><span class="mono-text">${maskString(r.new_device_binding)}</span></td>
          <td>${renderStatusBadge(r.status)}</td>
          <td>${escapeHtml(r.reason || '-')}</td>
          <td>${formatDate(r.created_at)}</td>
        </tr>
      `
        )
        .join('');
    }

    openModal('licenseDetailModal');
  } catch {
    showToast('Terjadi kesalahan saat memuat detail.');
  }
}

// Data Fetching: Recovery Requests
async function loadRecoveries() {
  try {
    const licRes = await fetch('/api/licenses');
    if (!licRes.ok) return;
    const licData = await licRes.json();
    const licenses = licData.data || [];

    const allRecoveries = [];
    await Promise.all(
      licenses.map(async (lic) => {
        const detailRes = await fetch(`/api/licenses/${lic.id}`);
        if (detailRes.ok) {
          const det = await detailRes.json();
          const recs = det.data?.recoveryRequests || [];
          recs.forEach((r) => {
            allRecoveries.push({
              ...r,
              ownerEmail: lic.owner_email_canonical
            });
          });
        }
      })
    );

    allRecoveries.sort((a, b) => b.created_at - a.created_at);
    cachedRecoveries = allRecoveries;

    const tbody = document.getElementById('recoveryTbody');
    if (allRecoveries.length === 0) {
      tbody.innerHTML = '<tr><td colspan="9" class="text-center text-muted">Tidak ada permintaan recovery.</td></tr>';
      return;
    }

    tbody.innerHTML = allRecoveries
      .map(
        (r) => `
      <tr>
        <td>#${r.id}</td>
        <td>#${r.license_id}</td>
        <td><strong>${escapeHtml(r.ownerEmail)}</strong></td>
        <td><span class="mono-text">${r.old_device_binding ? maskString(r.old_device_binding) : '-'}</span></td>
        <td><span class="mono-text">${maskString(r.new_device_binding)}</span></td>
        <td>${renderStatusBadge(r.status)}</td>
        <td>${escapeHtml(r.reason || '-')}</td>
        <td>${formatDate(r.created_at)}</td>
        <td>
          ${
            r.status === 'PENDING'
              ? `<button class="btn btn-primary btn-sm" onclick="confirmRebind(${r.license_id}, '${escapeHtml(r.new_device_binding)}')">Setujui & Rebind</button>`
              : '<span class="text-muted">Selesai</span>'
          }
        </td>
      </tr>
    `
      )
      .join('');
  } catch {
    showToast('Gagal memuat permintaan recovery.');
  }
}

// Data Fetching: Audit Logs
async function loadAuditLogs() {
  try {
    const res = await fetch('/api/audit-logs');
    if (res.ok) {
      const data = await res.json();
      const logs = data.data || [];
      const tbody = document.getElementById('auditTbody');
      if (logs.length === 0) {
        tbody.innerHTML = '<tr><td colspan="6" class="text-center text-muted">Belum ada log audit tercatat.</td></tr>';
        return;
      }

      tbody.innerHTML = logs
        .map(
          (a) => `
        <tr>
          <td>${formatDate(a.created_at)}</td>
          <td><span class="badge badge-info">${escapeHtml(a.action)}</span></td>
          <td>#${a.license_id || '-'}</td>
          <td>${escapeHtml(a.old_state || '-')} → ${escapeHtml(a.new_state || '-')}</td>
          <td><strong>${escapeHtml(a.actor)}</strong></td>
          <td>${escapeHtml(a.reason || '-')}</td>
        </tr>
      `
        )
        .join('');
    }
  } catch {
    showToast('Gagal memuat audit log.');
  }
}

// Create License Form Handler
async function handleCreateLicense(e) {
  e.preventDefault();
  const ownerEmail = document.getElementById('createEmail').value.trim();
  const customerName = document.getElementById('createCustName').value.trim();
  const customerContact = document.getElementById('createCustContact').value.trim();
  const product = document.getElementById('createProduct').value;
  const price = parseInt(document.getElementById('createPrice').value, 10);

  const submitBtn = document.getElementById('submitCreateLicenseBtn');
  submitBtn.disabled = true;
  submitBtn.textContent = 'Membuat...';

  try {
    const res = await fetch('/api/licenses', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        ownerEmail,
        customerName,
        customerContact,
        product,
        price
      })
    });

    const data = await res.json();
    if (res.ok && data.success) {
      activeCreatedCode = data.data.licenseCode;
      document.getElementById('createdLicenseCodeText').textContent = activeCreatedCode;
      document.getElementById('createdOwnerEmail').textContent = data.data.ownerEmail;

      document.getElementById('createLicenseForm').classList.add('hidden');
      document.getElementById('createSuccessBox').classList.remove('hidden');

      showToast('Lisensi baru berhasil dibuat!');
      loadLicenses();
      loadDashboard();
    } else {
      showToast(data.error?.message || 'Gagal membuat lisensi.');
    }
  } catch {
    showToast('Gagal menghubungi server.');
  } finally {
    submitBtn.disabled = false;
    submitBtn.textContent = 'Generate Lisensi';
  }
}

function copyCreatedLicenseCode() {
  if (!activeCreatedCode) return;
  navigator.clipboard.writeText(activeCreatedCode).then(() => {
    const btn = document.getElementById('copyLicenseCodeBtn');
    btn.textContent = '✅ Disalin!';
    setTimeout(() => {
      btn.textContent = '📋 Salin Kode';
    }, 2000);
    showToast('Kode lisensi berhasil disalin ke clipboard.');
  });
}

// Confirm Actions
function confirmRevokeLicense(id, email) {
  const modal = document.getElementById('confirmModal');
  document.getElementById('confirmTitle').textContent = `Revoke Lisensi #${id}`;
  document.getElementById('confirmMessage').innerHTML = `Apakah Anda yakin ingin me-revoke lisensi untuk <strong>${escapeHtml(email)}</strong>?<br><br><small class="text-danger">Perangkat yang sedang aktif akan otomatis dinonaktifkan.</small>`;
  document.getElementById('confirmReasonInput').value = '';

  document.getElementById('confirmActionBtn').onclick = async () => {
    const reason = document.getElementById('confirmReasonInput').value.trim() || 'Revocation by admin';
    try {
      const res = await fetch(`/api/licenses/${id}/revoke`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ reason })
      });
      if (res.ok) {
        showToast(`Lisensi #${id} berhasil di-revoke.`);
        closeModal('confirmModal');
        loadLicenses();
        loadDashboard();
      } else {
        const err = await res.json();
        showToast(err.error?.message || 'Gagal me-revoke lisensi.');
      }
    } catch {
      showToast('Terjadi kesalahan jaringan.');
    }
  };

  openModal('confirmModal');
}

function confirmRevokeDevice(deviceId) {
  const modal = document.getElementById('confirmModal');
  document.getElementById('confirmTitle').textContent = `Revoke Perangkat #${deviceId}`;
  document.getElementById('confirmMessage').textContent = 'Apakah Anda yakin ingin me-revoke perangkat ini? Lisensi akan kembali ke status PENDING hingga perangkat baru diaktivasi.';
  document.getElementById('confirmReasonInput').value = '';

  document.getElementById('confirmActionBtn').onclick = async () => {
    const reason = document.getElementById('confirmReasonInput').value.trim() || 'Device binding revoked by admin';
    try {
      const res = await fetch(`/api/devices/${deviceId}/revoke`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ reason })
      });
      if (res.ok) {
        showToast(`Perangkat #${deviceId} berhasil di-revoke.`);
        closeModal('confirmModal');
        closeModal('licenseDetailModal');
        loadLicenses();
        loadDashboard();
      } else {
        const err = await res.json();
        showToast(err.error?.message || 'Gagal me-revoke perangkat.');
      }
    } catch {
      showToast('Terjadi kesalahan jaringan.');
    }
  };

  openModal('confirmModal');
}

function confirmRebind(licenseId, newDeviceBinding) {
  const modal = document.getElementById('confirmModal');
  document.getElementById('confirmTitle').textContent = `Setujui & Rebind Lisensi #${licenseId}`;
  document.getElementById('confirmMessage').innerHTML = `Setujui pemindahan lisensi ke perangkat baru (<strong>${maskString(newDeviceBinding)}</strong>)?<br><br><small class="text-warning">Perangkat lama akan otomatis di-REVOKE dan perangkat baru menjadi ACTIVE.</small>`;
  document.getElementById('confirmReasonInput').value = '';

  document.getElementById('confirmActionBtn').onclick = async () => {
    const reason = document.getElementById('confirmReasonInput').value.trim() || 'Admin approved device rebind';
    try {
      const res = await fetch('/api/license/rebind', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          licenseId,
          newDeviceBinding,
          reason
        })
      });
      if (res.ok) {
        showToast(`Lisensi #${licenseId} berhasil dipindahkan ke perangkat baru.`);
        closeModal('confirmModal');
        loadRecoveries();
        loadLicenses();
        loadDashboard();
      } else {
        const err = await res.json();
        showToast(err.error?.message || 'Gagal me-rebind lisensi.');
      }
    } catch {
      showToast('Terjadi kesalahan jaringan.');
    }
  };

  openModal('confirmModal');
}

// Modal Helpers
function openModal(id) {
  document.getElementById(id).classList.remove('hidden');
}

function closeModal(id) {
  document.getElementById(id).classList.add('hidden');
}

// Formatters
function renderStatusBadge(status) {
  const map = {
    ACTIVE: '<span class="badge badge-success">Aktif</span>',
    PENDING: '<span class="badge badge-warning">Pending</span>',
    REVOKED: '<span class="badge badge-danger">Revoked</span>',
    APPROVED: '<span class="badge badge-success">Disetujui</span>',
    REJECTED: '<span class="badge badge-danger">Ditolak</span>'
  };
  return map[status] || `<span class="badge badge-subtle">${escapeHtml(status)}</span>`;
}

function renderPaymentStatusBadge(status) {
  const map = {
    PAID: '<span class="badge badge-success">🟢 Sudah Bayar</span>',
    UNPAID: '<span class="badge badge-warning">🟡 Menunggu Pembayaran</span>',
    REJECTED: '<span class="badge badge-danger">🔴 Ditolak</span>'
  };
  return map[status] || `<span class="badge badge-subtle">${escapeHtml(status)}</span>`;
}

function renderOrderStatusBadge(status) {
  const map = {
    PENDING_PAYMENT: '<span class="badge badge-warning">🟡 Menunggu Bayar</span>',
    PAID: '<span class="badge badge-success">🟢 Sudah Bayar</span>',
    LICENSE_CREATED: '<span class="badge badge-info">🔵 License Dibuat</span>',
    DELIVERED: '<span class="badge badge-purple">🟣 Terkirim</span>',
    ACTIVE: '<span class="badge badge-success">🟢 Aktif</span>',
    COMPLETED: '<span class="badge badge-success">Selesai</span>'
  };
  return map[status] || `<span class="badge badge-subtle">${escapeHtml(status)}</span>`;
}

function formatDate(timestamp) {
  if (!timestamp) return '-';
  const d = new Date(timestamp);
  return d.toLocaleDateString('id-ID', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  });
}

function formatNumber(num) {
  return new Intl.NumberFormat('id-ID').format(num || 0);
}

function maskString(str) {
  if (!str) return '-';
  if (str.length <= 8) return str;
  return str.substring(0, 4) + '••••' + str.substring(str.length - 4);
}

function escapeHtml(str) {
  if (!str) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

function showToast(msg) {
  const toast = document.getElementById('toast');
  toast.textContent = msg;
  toast.classList.remove('hidden');
  setTimeout(() => {
    toast.classList.add('hidden');
  }, 3500);
}
