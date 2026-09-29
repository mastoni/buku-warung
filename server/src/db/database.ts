import Database from 'better-sqlite3';
import fs from 'fs';
import path from 'path';
import { config } from '../config/index.js';

let dbInstance: Database.Database | null = null;

export function getDatabase(customPath?: string): Database.Database {
  if (!dbInstance || customPath) {
    if (dbInstance && customPath) {
      dbInstance.close();
    }
    const dbPath = customPath || config.databasePath;
    const dbDir = path.dirname(dbPath);
    if (dbDir && !fs.existsSync(dbDir)) {
      fs.mkdirSync(dbDir, { recursive: true });
    }
    const db = new Database(dbPath);
    db.pragma('journal_mode = WAL');
    db.pragma('foreign_keys = ON');
    initSchema(db);

    dbInstance = db;
    return db;
  }
  return dbInstance;
}

export function closeDatabase(): void {
  if (dbInstance) {
    dbInstance.close();
    dbInstance = null;
  }
}

function initSchema(db: Database.Database): void {
  // Migrate existing orders table if columns are missing
  try {
    const existingCols = db.pragma('table_info(orders)') as Array<{ name: string }>;
    const colNames = new Set(existingCols.map((c) => c.name));

    if (!colNames.has('owner_email')) db.exec("ALTER TABLE orders ADD COLUMN owner_email TEXT NOT NULL DEFAULT ''");
    if (!colNames.has('product')) db.exec("ALTER TABLE orders ADD COLUMN product TEXT NOT NULL DEFAULT 'BUKU_WARUNG'");
    if (!colNames.has('status')) db.exec("ALTER TABLE orders ADD COLUMN status TEXT NOT NULL DEFAULT 'PENDING_PAYMENT'");
    if (!colNames.has('payment_method')) db.exec("ALTER TABLE orders ADD COLUMN payment_method TEXT");
    if (!colNames.has('payment_reference')) db.exec("ALTER TABLE orders ADD COLUMN payment_reference TEXT");
    if (!colNames.has('verified_at')) db.exec("ALTER TABLE orders ADD COLUMN verified_at INTEGER");
    if (!colNames.has('verified_by')) db.exec("ALTER TABLE orders ADD COLUMN verified_by TEXT");
    if (!colNames.has('delivered_at')) db.exec("ALTER TABLE orders ADD COLUMN delivered_at INTEGER");
    if (!colNames.has('delivered_by')) db.exec("ALTER TABLE orders ADD COLUMN delivered_by TEXT");
    if (!colNames.has('notes')) db.exec("ALTER TABLE orders ADD COLUMN notes TEXT");
    if (!colNames.has('lead_token')) db.exec("ALTER TABLE orders ADD COLUMN lead_token TEXT");
    if (!colNames.has('utm_source')) db.exec("ALTER TABLE orders ADD COLUMN utm_source TEXT");
    if (!colNames.has('utm_medium')) db.exec("ALTER TABLE orders ADD COLUMN utm_medium TEXT");
    if (!colNames.has('utm_campaign')) db.exec("ALTER TABLE orders ADD COLUMN utm_campaign TEXT");
    if (!colNames.has('utm_content')) db.exec("ALTER TABLE orders ADD COLUMN utm_content TEXT");
    if (!colNames.has('encrypted_delivery_license_code')) db.exec("ALTER TABLE orders ADD COLUMN encrypted_delivery_license_code TEXT");
  } catch {
    // Ignore migration error if table just created
  }

  db.exec(`
    CREATE TABLE IF NOT EXISTS licenses (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      license_uuid TEXT NOT NULL UNIQUE,
      license_code_hash TEXT NOT NULL UNIQUE,
      owner_email_canonical TEXT NOT NULL,
      owner_email_hash TEXT NOT NULL,
      product TEXT NOT NULL DEFAULT 'BUKU_WARUNG',
      price INTEGER NOT NULL DEFAULT 50000,
      status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'ACTIVE', 'REVOKED')),
      created_at INTEGER NOT NULL,
      activated_at INTEGER,
      revoked_at INTEGER,
      updated_at INTEGER NOT NULL
    );

    CREATE INDEX IF NOT EXISTS idx_licenses_code_hash ON licenses(license_code_hash);
    CREATE INDEX IF NOT EXISTS idx_licenses_email_hash ON licenses(owner_email_hash);
    CREATE INDEX IF NOT EXISTS idx_licenses_status ON licenses(status);

    CREATE TABLE IF NOT EXISTS license_devices (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      license_id INTEGER NOT NULL REFERENCES licenses(id) ON DELETE CASCADE,
      device_binding TEXT NOT NULL,
      status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'REVOKED')),
      first_activated_at INTEGER NOT NULL,
      last_validated_at INTEGER NOT NULL,
      revoked_at INTEGER,
      created_at INTEGER NOT NULL,
      updated_at INTEGER NOT NULL
    );

    CREATE INDEX IF NOT EXISTS idx_devices_license_id ON license_devices(license_id);
    CREATE INDEX IF NOT EXISTS idx_devices_binding ON license_devices(device_binding);
    CREATE UNIQUE INDEX IF NOT EXISTS idx_active_license_device ON license_devices(license_id) WHERE status = 'ACTIVE';

    CREATE TABLE IF NOT EXISTS orders (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      order_number TEXT NOT NULL UNIQUE,
      customer_name TEXT NOT NULL,
      customer_contact TEXT NOT NULL,
      owner_email TEXT NOT NULL DEFAULT '',
      product TEXT NOT NULL DEFAULT 'BUKU_WARUNG',
      amount INTEGER NOT NULL DEFAULT 50000,
      status TEXT NOT NULL DEFAULT 'PENDING_PAYMENT',
      payment_status TEXT NOT NULL DEFAULT 'UNPAID',
      license_id INTEGER REFERENCES licenses(id),
      payment_method TEXT,
      payment_reference TEXT,
      verified_at INTEGER,
      verified_by TEXT,
      delivered_at INTEGER,
      delivered_by TEXT,
      notes TEXT,
      lead_token TEXT,
      utm_source TEXT,
      utm_medium TEXT,
      utm_campaign TEXT,
      utm_content TEXT,
      encrypted_delivery_license_code TEXT,
      created_at INTEGER NOT NULL,
      updated_at INTEGER NOT NULL
    );

    CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);
    CREATE INDEX IF NOT EXISTS idx_orders_payment_status ON orders(payment_status);
    CREATE INDEX IF NOT EXISTS idx_orders_license_id ON orders(license_id);
    CREATE INDEX IF NOT EXISTS idx_orders_email ON orders(owner_email);
    CREATE INDEX IF NOT EXISTS idx_orders_lead_token ON orders(lead_token) WHERE lead_token IS NOT NULL;

    CREATE TABLE IF NOT EXISTS funnel_events (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      lead_token TEXT NOT NULL,
      event_type TEXT NOT NULL,
      event_data TEXT,
      ip_hash TEXT,
      user_agent TEXT,
      created_at INTEGER NOT NULL,
      installation_id TEXT
    );

    CREATE INDEX IF NOT EXISTS idx_funnel_events_token ON funnel_events(lead_token);
    CREATE INDEX IF NOT EXISTS idx_funnel_events_type ON funnel_events(event_type);
    CREATE INDEX IF NOT EXISTS idx_funnel_events_created ON funnel_events(created_at);

    CREATE TABLE IF NOT EXISTS audit_logs (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      action TEXT NOT NULL,
      license_id INTEGER REFERENCES licenses(id),
      old_state TEXT,
      new_state TEXT,
      actor TEXT NOT NULL,
      reason TEXT,
      created_at INTEGER NOT NULL
    );

    CREATE INDEX IF NOT EXISTS idx_audit_license_id ON audit_logs(license_id);

    CREATE TABLE IF NOT EXISTS recovery_requests (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      license_id INTEGER NOT NULL REFERENCES licenses(id) ON DELETE CASCADE,
      old_device_binding TEXT,
      new_device_binding TEXT NOT NULL,
      status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
      reason TEXT,
      created_at INTEGER NOT NULL,
      resolved_at INTEGER
    );

    CREATE INDEX IF NOT EXISTS idx_recovery_license_id ON recovery_requests(license_id);

    CREATE TABLE IF NOT EXISTS promotions (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      product TEXT NOT NULL UNIQUE,
      name TEXT NOT NULL,
      enabled INTEGER NOT NULL DEFAULT 1,
      normal_price INTEGER NOT NULL,
      promo_price INTEGER NOT NULL,
      starts_at INTEGER NOT NULL,
      expires_at INTEGER NOT NULL,
      timezone TEXT NOT NULL DEFAULT 'Asia/Jakarta',
      show_countdown INTEGER NOT NULL DEFAULT 1,
      created_at INTEGER NOT NULL,
      updated_at INTEGER NOT NULL
    );

    CREATE UNIQUE INDEX IF NOT EXISTS idx_promotions_product ON promotions(product);
  `);

  // CAMPAIGN-FIX: additive, idempotent, non-destructive.
  // Closed-testing campaign registrations. Unlike funnel_events (an append-only analytics
  // log with no uniqueness), this table is a counted, constrained resource ledger, so the
  // database itself - not the client and not an analytics count - is the authority for
  // "how many of the 50 slots are taken".
  //
  //   UNIQUE(campaign_id, slot_number) -> a slot can be taken at most once
  //   UNIQUE(campaign_id, lead_token)  -> one lead can never consume a second slot
  //
  // Both constraints are enforced by SQLite inside the registration transaction, which is
  // what makes concurrent registration unable to oversubscribe capacity.
  //
  // details_encrypted holds AES-256-GCM ciphertext (see utils/crypto.ts, domain-separated
  // key). Raw name/WhatsApp/email are never written in plaintext and never enter
  // funnel_events. contact_hash is a peppered HMAC used only for exact-match lookups.
  db.exec(`
    CREATE TABLE IF NOT EXISTS test_campaign_registrations (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      campaign_id TEXT NOT NULL,
      slot_number INTEGER NOT NULL,
      lead_token TEXT NOT NULL,
      contact_hash TEXT,
      details_encrypted TEXT,
      status TEXT NOT NULL DEFAULT 'REGISTERED' CHECK (status IN ('REGISTERED', 'ACTIVATED', 'REVOKED')),
      created_at INTEGER NOT NULL,
      activated_at INTEGER
    );

    CREATE UNIQUE INDEX IF NOT EXISTS idx_test_campaign_slot
      ON test_campaign_registrations(campaign_id, slot_number);
    CREATE UNIQUE INDEX IF NOT EXISTS idx_test_campaign_lead
      ON test_campaign_registrations(campaign_id, lead_token);
    CREATE INDEX IF NOT EXISTS idx_test_campaign_contact
      ON test_campaign_registrations(campaign_id, contact_hash);
  `);

  // FUNNEL-FIX: additive, idempotent, non-destructive migration.
  // Adds installation_id so app-originated events can be counted per installation.
  // Historical rows keep installation_id = NULL and are never backfilled or rewritten.
  // No UNIQUE constraint is added on (event_type, installation_id) on purpose:
  // LICENSE_PURCHASE_CLICKED and LICENSE_WHATSAPP_CLICKED are genuine repeatable click
  // events and must stay countable per occurrence. Deduplication is enforced by the client
  // according to each event's own semantics, not by a storage constraint.
  try {
    const funnelCols = db.pragma('table_info(funnel_events)') as Array<{ name: string }>;
    const funnelColNames = new Set(funnelCols.map((c) => c.name));
    if (!funnelColNames.has('installation_id')) {
      db.exec('ALTER TABLE funnel_events ADD COLUMN installation_id TEXT');
    }
    db.exec(
      'CREATE INDEX IF NOT EXISTS idx_funnel_events_installation ON funnel_events(installation_id) WHERE installation_id IS NOT NULL'
    );
  } catch {
    // Ignore migration error if table was just created with the column already present
  }

  // Seed default BUKU_WARUNG promotion if not exists
  try {
    const existingPromo = db.prepare('SELECT id FROM promotions WHERE product = ?').get('BUKU_WARUNG');
    if (!existingPromo) {
      const now = Date.now();
      const defaultExpiry = new Date('2027-12-31T23:59:59+07:00').getTime();
      db.prepare(`
        INSERT INTO promotions (
          product, name, enabled, normal_price, promo_price, starts_at, expires_at,
          timezone, show_countdown, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      `).run(
        'BUKU_WARUNG',
        'Promo Peluncuran',
        1,
        100000,
        50000,
        now,
        defaultExpiry,
        'Asia/Jakarta',
        1,
        now,
        now
      );
    }
  } catch {
    // Ignore seed error if during concurrent init
  }
}

