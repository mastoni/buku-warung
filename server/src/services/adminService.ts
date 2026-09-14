import Database from 'better-sqlite3';
import { getDatabase } from '../db/database.js';
import {
  AdminCreateLicenseRequest,
  LicenseRecord,
  LicenseDeviceRecord,
  AuditLogRecord,
  RecoveryRequestRecord,
  OrderRecord,
  CreateOrderRequest,
  VerifyPaymentRequest,
  MarkDeliveredRequest,
  OrderSummaryMetrics
} from '../types/index.js';
import {
  generateLicenseCode,
  generateUuid,
  hashEmail,
  hashLicenseCode,
  normalizeEmail
} from '../utils/crypto.js';

export class AdminService {
  private db: Database.Database;

  constructor(customDb?: Database.Database) {
    this.db = customDb || getDatabase();
  }

  /**
   * Creates a new commercial license.
   * Returns the plaintext license code once for distribution to customer.
   */
  createLicense(payload: AdminCreateLicenseRequest, actor: string = 'ADMIN'): {
    success: boolean;
    data?: {
      licenseId: number;
      licenseUuid: string;
      licenseCode: string; // Plaintext code returned only upon creation
      ownerEmail: string;
      product: string;
      price: number;
      status: string;
      orderNumber?: string;
    };
    error?: { code: string; message: string };
  } {
    const { ownerEmail, product = 'BUKU_WARUNG', price = 50000, customerName, customerContact } = payload;

    if (!ownerEmail || !ownerEmail.includes('@')) {
      return {
        success: false,
        error: { code: 'INVALID_EMAIL', message: 'Valid owner email is required.' }
      };
    }

    const canonicalEmail = normalizeEmail(ownerEmail);
    const emailHash = hashEmail(canonicalEmail);
    const licenseCode = generateLicenseCode();
    const codeHash = hashLicenseCode(licenseCode);
    const licenseUuid = generateUuid();
    const now = Date.now();

    const createTx = this.db.transaction(() => {
      const stmt = this.db.prepare(`
        INSERT INTO licenses (
          license_uuid, license_code_hash, owner_email_canonical, owner_email_hash,
          product, price, status, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, 'PENDING', ?, ?)
      `);

      const result = stmt.run(
        licenseUuid,
        codeHash,
        canonicalEmail,
        emailHash,
        product,
        price,
        now,
        now
      );

      const licenseId = Number(result.lastInsertRowid);
      let orderNumber: string | undefined;

      if (customerName || customerContact) {
        orderNumber = `BW-ORD-${Date.now()}-${Math.floor(Math.random() * 1000)}`;
        this.db
          .prepare(`
            INSERT INTO orders (
              order_number, customer_name, customer_contact, owner_email, product, amount,
              status, payment_status, license_id, verified_at, verified_by, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, 'LICENSE_CREATED', 'PAID', ?, ?, ?, ?, ?)
          `)
          .run(
            orderNumber,
            customerName || canonicalEmail,
            customerContact || canonicalEmail,
            canonicalEmail,
            product,
            price,
            licenseId,
            now,
            actor,
            now,
            now
          );
      }

      this.db
        .prepare(`
          INSERT INTO audit_logs (action, license_id, old_state, new_state, actor, reason, created_at)
          VALUES ('CREATE_LICENSE', ?, NULL, 'PENDING', ?, 'New license generated', ?)
        `)
        .run(licenseId, actor, now);

      return {
        licenseId,
        orderNumber
      };
    });

    const txResult = createTx.immediate();

    return {
      success: true,
      data: {
        licenseId: txResult.licenseId,
        licenseUuid,
        licenseCode,
        ownerEmail: canonicalEmail,
        product,
        price,
        status: 'PENDING',
        orderNumber: txResult.orderNumber
      }
    };
  }

  /**
   * Lists all licenses with active device details.
   */
  listLicenses(): LicenseRecord[] {
    return this.db
      .prepare(`
        SELECT id, license_uuid, owner_email_canonical, product, price, status, created_at, activated_at, revoked_at, updated_at
        FROM licenses
        ORDER BY id DESC
      `)
      .all() as LicenseRecord[];
  }

  /**
   * Retrieves full details for a specific license.
   */
  getLicenseDetail(id: number): {
    license: LicenseRecord | null;
    devices: LicenseDeviceRecord[];
    recoveryRequests: RecoveryRequestRecord[];
  } {
    const license = this.db
      .prepare(`
        SELECT id, license_uuid, owner_email_canonical, product, price, status, created_at, activated_at, revoked_at, updated_at
        FROM licenses
        WHERE id = ?
      `)
      .get(id) as LicenseRecord | undefined;

    if (!license) {
      return { license: null, devices: [], recoveryRequests: [] };
    }

    const devices = this.db
      .prepare('SELECT * FROM license_devices WHERE license_id = ? ORDER BY id DESC')
      .all(id) as LicenseDeviceRecord[];

    const recoveryRequests = this.db
      .prepare('SELECT * FROM recovery_requests WHERE license_id = ? ORDER BY id DESC')
      .all(id) as RecoveryRequestRecord[];

    return {
      license,
      devices,
      recoveryRequests
    };
  }

  /**
   * Revokes a license and all its active devices.
   */
  revokeLicense(licenseId: number, actor: string = 'ADMIN', reason: string = 'Admin revocation'): {
    success: boolean;
    error?: { code: string; message: string };
  } {
    const now = Date.now();

    const revokeTx = this.db.transaction(() => {
      const license = this.db
        .prepare('SELECT * FROM licenses WHERE id = ?')
        .get(licenseId) as LicenseRecord | undefined;

      if (!license) {
        return { success: false, error: { code: 'NOT_FOUND', message: 'License not found.' } };
      }

      if (license.status === 'REVOKED') {
        return { success: true };
      }

      this.db
        .prepare('UPDATE licenses SET status = \'REVOKED\', revoked_at = ?, updated_at = ? WHERE id = ?')
        .run(now, now, licenseId);

      this.db
        .prepare('UPDATE license_devices SET status = \'REVOKED\', revoked_at = ?, updated_at = ? WHERE license_id = ? AND status = \'ACTIVE\'')
        .run(now, now, licenseId);

      this.db
        .prepare(`
          INSERT INTO audit_logs (action, license_id, old_state, new_state, actor, reason, created_at)
          VALUES ('REVOKE_LICENSE', ?, ?, 'REVOKED', ?, ?, ?)
        `)
        .run(licenseId, license.status, actor, reason, now);

      return { success: true };
    });

    return revokeTx.immediate();
  }

  /**
   * Revokes a specific device binding.
   */
  revokeDevice(deviceId: number, actor: string = 'ADMIN', reason: string = 'Admin device revocation'): {
    success: boolean;
    error?: { code: string; message: string };
  } {
    const now = Date.now();

    const revokeTx = this.db.transaction(() => {
      const device = this.db
        .prepare('SELECT * FROM license_devices WHERE id = ?')
        .get(deviceId) as LicenseDeviceRecord | undefined;

      if (!device) {
        return { success: false, error: { code: 'NOT_FOUND', message: 'Device binding not found.' } };
      }

      this.db
        .prepare('UPDATE license_devices SET status = \'REVOKED\', revoked_at = ?, updated_at = ? WHERE id = ?')
        .run(now, now, deviceId);

      // Check if any other active device exists
      const activeCount = this.db
        .prepare('SELECT COUNT(*) as count FROM license_devices WHERE license_id = ? AND status = \'ACTIVE\'')
        .get(device.license_id) as { count: number };

      if (activeCount.count === 0) {
        this.db
          .prepare('UPDATE licenses SET status = \'PENDING\', updated_at = ? WHERE id = ?')
          .run(now, device.license_id);
      }

      this.db
        .prepare(`
          INSERT INTO audit_logs (action, license_id, old_state, new_state, actor, reason, created_at)
          VALUES ('REVOKE_DEVICE', ?, 'ACTIVE', 'REVOKED', ?, ?, ?)
        `)
        .run(device.license_id, actor, reason, now);

      return { success: true };
    });

    return revokeTx.immediate();
  }

  /**
   * Rebinds a license to a new device in an atomic transaction:
   * 1. Revokes old active device
   * 2. Binds new device as ACTIVE
   * 3. Sets license to ACTIVE
   * 4. Approves pending recovery requests
   */
  rebindLicense(licenseId: number, newDeviceBinding: string, actor: string = 'ADMIN', reason: string = 'Admin approved rebind'): {
    success: boolean;
    error?: { code: string; message: string };
  } {
    const now = Date.now();

    const rebindTx = this.db.transaction(() => {
      const license = this.db
        .prepare('SELECT * FROM licenses WHERE id = ?')
        .get(licenseId) as LicenseRecord | undefined;

      if (!license) {
        return { success: false, error: { code: 'NOT_FOUND', message: 'License not found.' } };
      }

      if (license.status === 'REVOKED') {
        return { success: false, error: { code: 'LICENSE_REVOKED', message: 'Cannot rebind a revoked license.' } };
      }

      // Revoke any existing active devices
      this.db
        .prepare('UPDATE license_devices SET status = \'REVOKED\', revoked_at = ?, updated_at = ? WHERE license_id = ? AND status = \'ACTIVE\'')
        .run(now, now, licenseId);

      // Insert new active device
      this.db
        .prepare(`
          INSERT INTO license_devices (
            license_id, device_binding, status, first_activated_at, last_validated_at, created_at, updated_at
          ) VALUES (?, ?, 'ACTIVE', ?, ?, ?, ?)
        `)
        .run(licenseId, newDeviceBinding, now, now, now, now);

      // Ensure license is ACTIVE
      this.db
        .prepare('UPDATE licenses SET status = \'ACTIVE\', updated_at = ? WHERE id = ?')
        .run(now, licenseId);

      // Approve any pending recovery requests
      this.db
        .prepare('UPDATE recovery_requests SET status = \'APPROVED\', resolved_at = ? WHERE license_id = ? AND status = \'PENDING\'')
        .run(now, licenseId);

      this.db
        .prepare(`
          INSERT INTO audit_logs (action, license_id, old_state, new_state, actor, reason, created_at)
          VALUES ('REBIND_DEVICE', ?, 'REBIND_OLD', 'ACTIVE', ?, ?, ?)
        `)
        .run(licenseId, actor, reason, now);

      return { success: true };
    });

    return rebindTx.immediate();
  }

  /**
   * Retrieves audit logs.
   */
  getAuditLogs(licenseId?: number): AuditLogRecord[] {
    if (licenseId) {
      return this.db
        .prepare('SELECT * FROM audit_logs WHERE license_id = ? ORDER BY id DESC')
        .all(licenseId) as AuditLogRecord[];
    }
    return this.db
      .prepare('SELECT * FROM audit_logs ORDER BY id DESC LIMIT 200')
      .all() as AuditLogRecord[];
  }

  /* =========================================================================
   * SALES & ORDER MANAGEMENT METHODS (C.10.1)
   * ========================================================================= */

  /**
   * Creates a new customer order (initially PENDING_PAYMENT / UNPAID).
   */
  createOrder(payload: CreateOrderRequest, actor: string = 'ADMIN'): {
    success: boolean;
    data?: OrderRecord;
    error?: { code: string; message: string };
  } {
    const { customerName, ownerEmail, product = 'BUKU_WARUNG', amount = 50000, notes } = payload;
    const contact = (payload.customerContact || (payload as any).customerWhatsapp || '').trim();

    if (!customerName || !customerName.trim()) {
      return { success: false, error: { code: 'INVALID_NAME', message: 'Customer name is required.' } };
    }
    if (!contact) {
      return { success: false, error: { code: 'INVALID_CONTACT', message: 'Customer WhatsApp/contact is required.' } };
    }
    if (!ownerEmail || !ownerEmail.includes('@')) {
      return { success: false, error: { code: 'INVALID_EMAIL', message: 'Valid owner email is required.' } };
    }
    if (amount <= 0 || isNaN(amount)) {
      return { success: false, error: { code: 'INVALID_AMOUNT', message: 'Amount must be greater than 0.' } };
    }

    const canonicalEmail = normalizeEmail(ownerEmail);
    const now = Date.now();
    const orderNumber = `BW-ORD-${Date.now().toString().slice(-6)}-${Math.floor(1000 + Math.random() * 9000)}`;

    const createTx = this.db.transaction(() => {
      const stmt = this.db.prepare(`
        INSERT INTO orders (
          order_number, customer_name, customer_contact, owner_email, product, amount,
          status, payment_status, license_id, notes, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, 'PENDING_PAYMENT', 'UNPAID', NULL, ?, ?, ?)
      `);

      const result = stmt.run(
        orderNumber,
        customerName.trim(),
        contact,
        canonicalEmail,
        product,
        amount,
        notes || null,
        now,
        now
      );

      const orderId = Number(result.lastInsertRowid);

      this.db
        .prepare(`
          INSERT INTO audit_logs (action, license_id, old_state, new_state, actor, reason, created_at)
          VALUES ('CREATE_ORDER', NULL, NULL, 'PENDING_PAYMENT', ?, ?, ?)
        `)
        .run(actor, `Order #${orderId} (${orderNumber}) created for ${customerName}`, now);

      const order = this.db.prepare('SELECT * FROM orders WHERE id = ?').get(orderId) as OrderRecord;
      return order;
    });

    const createdOrder = createTx.immediate();
    return {
      success: true,
      data: createdOrder
    };
  }

  /**
   * Lists all orders with summary metrics and linked license/device info.
   */
  listOrders(filter?: string, search?: string): {
    orders: Array<OrderRecord & {
      license_code_masked?: string | null;
      license_status?: string | null;
      license_uuid?: string | null;
      active_device_count: number;
      effective_status: string;
    }>;
    metrics: OrderSummaryMetrics;
  } {
    const allOrders = this.db
      .prepare(`
        SELECT 
          o.*,
          l.status as license_status,
          l.license_uuid as license_uuid,
          (SELECT COUNT(*) FROM license_devices ld WHERE ld.license_id = o.license_id AND ld.status = 'ACTIVE') as active_device_count
        FROM orders o
        LEFT JOIN licenses l ON o.license_id = l.id
        ORDER BY o.id DESC
      `)
      .all() as Array<OrderRecord & {
        license_status?: string | null;
        license_uuid?: string | null;
        active_device_count: number;
        effective_status: string;
      }>;

    // Calculate effective statuses and metrics
    let totalOrders = allOrders.length;
    let pendingPayment = 0;
    let paid = 0;
    let pendingLicense = 0;
    let active = 0;

    const processedOrders = allOrders.map((o) => {
      let effectiveStatus = o.status;
      if (o.license_id && o.active_device_count > 0) {
        effectiveStatus = 'ACTIVE';
      }

      if (o.payment_status === 'UNPAID') {
        pendingPayment++;
      }
      if (o.payment_status === 'PAID') {
        paid++;
        if (!o.license_id) {
          pendingLicense++;
        }
      }
      if (o.active_device_count > 0) {
        active++;
      }

      return {
        ...o,
        effective_status: effectiveStatus
      };
    });

    let filtered = processedOrders;

    if (filter) {
      if (filter === 'PENDING_PAYMENT') {
        filtered = filtered.filter((o) => o.payment_status === 'UNPAID');
      } else if (filter === 'PAID') {
        filtered = filtered.filter((o) => o.payment_status === 'PAID');
      } else if (filter === 'PENDING_LICENSE') {
        filtered = filtered.filter((o) => o.payment_status === 'PAID' && !o.license_id);
      } else if (filter === 'ACTIVE') {
        filtered = filtered.filter((o) => o.active_device_count > 0);
      }
    }

    if (search) {
      const q = search.toLowerCase().trim();
      filtered = filtered.filter((o) =>
        o.order_number.toLowerCase().includes(q) ||
        o.customer_name.toLowerCase().includes(q) ||
        o.customer_contact.toLowerCase().includes(q) ||
        o.owner_email.toLowerCase().includes(q)
      );
    }

    return {
      orders: filtered,
      metrics: {
        totalOrders,
        pendingPayment,
        paid,
        pendingLicense,
        active
      }
    };
  }

  /**
   * Retrieves single order details including linked license and devices.
   */
  getOrderDetail(id: number): {
    order: (OrderRecord & { effective_status: string }) | null;
    license: LicenseRecord | null;
    devices: LicenseDeviceRecord[];
    auditLogs: AuditLogRecord[];
  } {
    const order = this.db.prepare('SELECT * FROM orders WHERE id = ?').get(id) as OrderRecord | undefined;

    if (!order) {
      return { order: null, license: null, devices: [], auditLogs: [] };
    }

    let license: LicenseRecord | null = null;
    let devices: LicenseDeviceRecord[] = [];
    let auditLogs: AuditLogRecord[] = [];

    if (order.license_id) {
      const licDetail = this.getLicenseDetail(order.license_id);
      license = licDetail.license;
      devices = licDetail.devices;
      auditLogs = this.getAuditLogs(order.license_id);
    }

    let effectiveStatus = order.status;
    const activeCount = devices.filter((d) => d.status === 'ACTIVE').length;
    if (order.license_id && activeCount > 0) {
      effectiveStatus = 'ACTIVE';
    }

    return {
      order: { ...order, status: effectiveStatus, effective_status: effectiveStatus },
      license,
      devices,
      auditLogs
    };
  }

  /**
   * Verifies payment for an order and sets status to PAID.
   */
  verifyOrderPayment(
    orderId: number,
    payload: VerifyPaymentRequest,
    actor: string = 'ADMIN'
  ): {
    success: boolean;
    data?: OrderRecord;
    error?: { code: string; message: string };
  } {
    const now = Date.now();

    const verifyTx = this.db.transaction(() => {
      const order = this.db.prepare('SELECT * FROM orders WHERE id = ?').get(orderId) as OrderRecord | undefined;

      if (!order) {
        return { success: false, error: { code: 'NOT_FOUND', message: 'Order not found.' } };
      }

      if (order.payment_status === 'PAID') {
        return { success: false, error: { code: 'ALREADY_PAID', message: 'Order is already marked as PAID.' } };
      }

      this.db
        .prepare(`
          UPDATE orders
          SET 
            payment_status = 'PAID',
            status = CASE WHEN status = 'PENDING_PAYMENT' THEN 'PAID' ELSE status END,
            payment_method = ?,
            payment_reference = ?,
            verified_at = ?,
            verified_by = ?,
            notes = COALESCE(?, notes),
            updated_at = ?
          WHERE id = ?
        `)
        .run(
          payload.paymentMethod || 'MANUAL_TRANSFER',
          payload.paymentReference || null,
          now,
          actor,
          payload.notes || null,
          now,
          orderId
        );

      this.db
        .prepare(`
          INSERT INTO audit_logs (action, license_id, old_state, new_state, actor, reason, created_at)
          VALUES ('VERIFY_PAYMENT', ?, 'UNPAID', 'PAID', ?, ?, ?)
        `)
        .run(order.license_id, actor, `Payment verified for Order #${orderId} (${order.order_number})`, now);

      const updated = this.db.prepare('SELECT * FROM orders WHERE id = ?').get(orderId) as OrderRecord;
      return { success: true, data: updated };
    });

    return verifyTx.immediate();
  }

  /**
   * Generates a commercial license for a PAID order.
   * Protects against duplicate generation (idempotent).
   */
  generateLicenseForOrder(
    orderId: number,
    actor: string = 'ADMIN'
  ): {
    success: boolean;
    data?: {
      licenseId: number;
      licenseCode: string;
      licenseUuid: string;
      ownerEmail: string;
      order: OrderRecord;
    };
    error?: { code: string; message: string };
  } {
    const now = Date.now();

    const genTx = this.db.transaction(() => {
      const order = this.db.prepare('SELECT * FROM orders WHERE id = ?').get(orderId) as OrderRecord | undefined;

      if (!order) {
        return { success: false, error: { code: 'NOT_FOUND', message: 'Order not found.' } };
      }

      if (order.payment_status !== 'PAID') {
        return {
          success: false,
          error: { code: 'ORDER_UNPAID', message: 'Cannot generate license for unpaid order.' }
        };
      }

      // Duplicate prevention: if order already has license, return it
      if (order.license_id) {
        const lic = this.db.prepare('SELECT * FROM licenses WHERE id = ?').get(order.license_id) as LicenseRecord | undefined;
        if (lic) {
          return {
            success: true,
            data: {
              licenseId: lic.id,
              licenseCode: '(Already Generated)',
              licenseUuid: lic.license_uuid,
              ownerEmail: lic.owner_email_canonical,
              order
            }
          };
        }
      }

      const canonicalEmail = normalizeEmail(order.owner_email || order.customer_contact);
      const emailHash = hashEmail(canonicalEmail);
      const licenseCode = generateLicenseCode();
      const codeHash = hashLicenseCode(licenseCode);
      const licenseUuid = generateUuid();

      const stmt = this.db.prepare(`
        INSERT INTO licenses (
          license_uuid, license_code_hash, owner_email_canonical, owner_email_hash,
          product, price, status, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, 'PENDING', ?, ?)
      `);

      const result = stmt.run(
        licenseUuid,
        codeHash,
        canonicalEmail,
        emailHash,
        order.product,
        order.amount,
        now,
        now
      );

      const licenseId = Number(result.lastInsertRowid);

      this.db
        .prepare(`
          UPDATE orders
          SET license_id = ?, status = 'LICENSE_CREATED', updated_at = ?
          WHERE id = ?
        `)
        .run(licenseId, now, orderId);

      this.db
        .prepare(`
          INSERT INTO audit_logs (action, license_id, old_state, new_state, actor, reason, created_at)
          VALUES ('GENERATE_ORDER_LICENSE', ?, 'PAID', 'LICENSE_CREATED', ?, ?, ?)
        `)
        .run(licenseId, actor, `License generated for Order #${orderId} (${order.order_number})`, now);

      const updatedOrder = this.db.prepare('SELECT * FROM orders WHERE id = ?').get(orderId) as OrderRecord;

      return {
        success: true,
        data: {
          licenseId,
          licenseCode,
          licenseUuid,
          ownerEmail: canonicalEmail,
          order: updatedOrder
        }
      };
    });

    return genTx.immediate();
  }

  /**
   * Marks an order as DELIVERED to customer.
   */
  markOrderDelivered(
    orderId: number,
    payload: MarkDeliveredRequest,
    actor: string = 'ADMIN'
  ): {
    success: boolean;
    data?: OrderRecord;
    error?: { code: string; message: string };
  } {
    const now = Date.now();

    const deliverTx = this.db.transaction(() => {
      const order = this.db.prepare('SELECT * FROM orders WHERE id = ?').get(orderId) as OrderRecord | undefined;

      if (!order) {
        return { success: false, error: { code: 'NOT_FOUND', message: 'Order not found.' } };
      }

      if (!order.license_id) {
        return {
          success: false,
          error: { code: 'LICENSE_NOT_CREATED', message: 'Cannot mark delivered before license is generated.' }
        };
      }

      this.db
        .prepare(`
          UPDATE orders
          SET 
            status = 'DELIVERED',
            delivered_at = ?,
            delivered_by = ?,
            notes = COALESCE(?, notes),
            updated_at = ?
          WHERE id = ?
        `)
        .run(now, actor, payload.notes || null, now, orderId);

      this.db
        .prepare(`
          INSERT INTO audit_logs (action, license_id, old_state, new_state, actor, reason, created_at)
          VALUES ('MARK_ORDER_DELIVERED', ?, 'LICENSE_CREATED', 'DELIVERED', ?, ?, ?)
        `)
        .run(order.license_id, actor, `Order #${orderId} marked as delivered to customer`, now);

      const updated = this.db.prepare('SELECT * FROM orders WHERE id = ?').get(orderId) as OrderRecord;
      return { success: true, data: updated };
    });

    return deliverTx.immediate();
  }
}
