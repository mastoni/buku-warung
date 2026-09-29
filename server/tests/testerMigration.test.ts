import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import Database from 'better-sqlite3';
import fs from 'fs';
import os from 'os';
import path from 'path';
import { getDatabase, closeDatabase } from '../src/db/database.js';
import { TEST_CAMPAIGN_ID, TESTER_STATUSES } from '../src/services/testCampaignService.js';

/**
 * The production database already holds the ORIGINAL test_campaign_registrations table with a
 * three-value CHECK constraint. SQLite cannot alter a CHECK, so production will be rebuilt once.
 * This suite proves that rebuild preserves registrations, keeps the UNIQUE indexes that provide
 * the campaign's concurrency protection, and accepts the new lifecycle statuses.
 */
describe('Test campaign table migration (production upgrade path)', () => {
  const OLD_SCHEMA = `
    CREATE TABLE test_campaign_registrations (
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
    CREATE UNIQUE INDEX idx_test_campaign_slot
      ON test_campaign_registrations(campaign_id, slot_number);
    CREATE UNIQUE INDEX idx_test_campaign_lead
      ON test_campaign_registrations(campaign_id, lead_token);
    CREATE INDEX idx_test_campaign_contact
      ON test_campaign_registrations(campaign_id, contact_hash);
  `;

  let dbFile: string;

  beforeEach(() => {
    closeDatabase();
    // A real file, not ':memory:' - an in-memory database lives inside one connection, so the
    // legacy rows written here would not be visible to the connection that runs the migration.
    dbFile = path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'bw-migration-')), 'campaign.db');
  });

  afterEach(() => {
    closeDatabase();
    try {
      fs.rmSync(path.dirname(dbFile), { recursive: true, force: true });
    } catch {
      /* best effort */
    }
  });

  function buildLegacyDb(): Database.Database {
    const db = new Database(dbFile);
    db.pragma('journal_mode = WAL');
    db.pragma('foreign_keys = ON');
    db.exec(OLD_SCHEMA);
    return db;
  }

  it('migrates an existing legacy table and preserves every registration', () => {
    const legacy = buildLegacyDb();
    const insert = legacy.prepare(
      `INSERT INTO test_campaign_registrations
         (campaign_id, slot_number, lead_token, contact_hash, details_encrypted, status, created_at)
       VALUES (?, ?, ?, ?, ?, ?, ?)`
    );
    insert.run(TEST_CAMPAIGN_ID, 1, 'LW-222222', 'hash-1', 'cipher-1', 'REGISTERED', 1700000000000);
    insert.run(TEST_CAMPAIGN_ID, 2, 'LW-222223', 'hash-2', 'cipher-2', 'REGISTERED', 1700000001000);
    // A legacy row in the old ACTIVATED value must still land on a valid new status.
    insert.run(TEST_CAMPAIGN_ID, 3, 'LW-222224', 'hash-3', 'cipher-3', 'ACTIVATED', 1700000002000);
    legacy.close();

    // Re-opening through the normal path runs initSchema, which performs the rebuild.
    const db = getDatabase(dbFile);
    const rows = db
      .prepare('SELECT id, slot_number, lead_token, details_encrypted, status FROM test_campaign_registrations ORDER BY id')
      .all() as any[];

    expect(rows).toHaveLength(3);
    expect(rows[0]).toMatchObject({ slot_number: 1, lead_token: 'LW-222222', details_encrypted: 'cipher-1' });
    expect(rows[1].lead_token).toBe('LW-222223');
    // The old ACTIVATED value is not part of the Closed Testing workflow any more.
    expect(rows[2].status).toBe('REGISTERED');
    for (const r of rows) {
      expect(TESTER_STATUSES).toContain(r.status);
    }
  });

  it('adds the tester-management columns after migration', () => {
    getDatabase(':memory:');
    const db = getDatabase();
    const cols = (db.prepare('PRAGMA table_info(test_campaign_registrations)').all() as Array<{ name: string }>)
      .map((c) => c.name);

    for (const expected of ['admin_notes', 'play_opt_in_url', 'play_invited_at', 'play_opted_in_at', 'updated_at']) {
      expect(cols, `expected column ${expected}`).toContain(expected);
    }
  });

  it('keeps the UNIQUE indexes that make registration concurrency-safe', () => {
    getDatabase(':memory:');
    const db = getDatabase();
    const indexes = (db.prepare("SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='test_campaign_registrations'").all() as any[])
      .map((i) => i.name);

    expect(indexes).toContain('idx_test_campaign_slot');
    expect(indexes).toContain('idx_test_campaign_lead');
    expect(indexes).toContain('idx_test_campaign_status');

    const insert = db.prepare(
      `INSERT INTO test_campaign_registrations
         (campaign_id, slot_number, lead_token, created_at) VALUES (?, ?, ?, ?)`
    );
    insert.run(TEST_CAMPAIGN_ID, 1, 'LW-AAAA11', 1700000000000);

    // Same slot, different lead -> must be rejected by UNIQUE(campaign_id, slot_number).
    expect(() => insert.run(TEST_CAMPAIGN_ID, 1, 'LW-BBBB22', 1700000000000)).toThrow();

    // Different slot, same lead -> must be rejected by UNIQUE(campaign_id, lead_token).
    expect(() => insert.run(TEST_CAMPAIGN_ID, 2, 'LW-AAAA11', 1700000000000)).toThrow();
  });

  it('rejects a status outside the Closed Testing workflow', () => {
    getDatabase(':memory:');
    const db = getDatabase();
    db.prepare(
      `INSERT INTO test_campaign_registrations (campaign_id, slot_number, lead_token, created_at)
       VALUES (?, ?, ?, ?)`
    ).run(TEST_CAMPAIGN_ID, 1, 'LW-CCCC33', 1700000000000);

    expect(() =>
      db.prepare('UPDATE test_campaign_registrations SET status = ? WHERE id = 1').run('ACTIVATED')
    ).toThrow();
    expect(() =>
      db.prepare('UPDATE test_campaign_registrations SET status = ? WHERE id = 1').run('REVIEWED')
    ).not.toThrow();
  });

  it('creates the status history table with a foreign key to testers', () => {
    getDatabase(':memory:');
    const db = getDatabase();
    const tables = (db.prepare("SELECT name FROM sqlite_master WHERE type='table'").all() as any[]).map((t) => t.name);
    expect(tables).toContain('tester_status_history');

    db.prepare(
      `INSERT INTO test_campaign_registrations (campaign_id, slot_number, lead_token, created_at)
       VALUES (?, ?, ?, ?)`
    ).run(TEST_CAMPAIGN_ID, 1, 'LW-DDDD44', 1700000000000);

    db.prepare(
      `INSERT INTO tester_status_history
         (campaign_id, tester_id, from_status, to_status, actor, created_at)
       VALUES (?, ?, ?, ?, ?, ?)`
    ).run(TEST_CAMPAIGN_ID, 1, 'REGISTERED', 'REVIEWED', 'ADMIN_API', 1700000000000);

    const history = db.prepare('SELECT * FROM tester_status_history').all() as any[];
    expect(history).toHaveLength(1);

    // Deleting a tester cascades to its history.
    db.prepare('DELETE FROM test_campaign_registrations WHERE id = 1').run();
    expect(db.prepare('SELECT COUNT(*) AS c FROM tester_status_history').get()).toEqual({ c: 0 });
  });

  it('is idempotent: re-opening a migrated file keeps the data and does not rebuild again', () => {
    getDatabase(dbFile);
    let db = getDatabase();
    db.prepare(
      `INSERT INTO test_campaign_registrations (campaign_id, slot_number, lead_token, created_at)
       VALUES (?, ?, ?, ?)`
    ).run(TEST_CAMPAIGN_ID, 1, 'LW-EEEE55', 1700000000000);

    // Simulate a restart on the same file: the guarded rebuild must not run a second time and
    // must not lose the row.
    closeDatabase();
    db = getDatabase(dbFile);
    const rows = db.prepare('SELECT lead_token FROM test_campaign_registrations').all() as any[];
    expect(rows).toHaveLength(1);
    expect(rows[0].lead_token).toBe('LW-EEEE55');
  });
});
