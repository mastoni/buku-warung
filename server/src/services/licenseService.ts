import Database from 'better-sqlite3';
import { getDatabase } from '../db/database.js';
import {
  ActivateRequest,
  ValidateRequest,
  RecoverRequest,
  LicenseRecord,
  LicenseDeviceRecord,
  ValidationResultStatus
} from '../types/index.js';
import { hashEmail, hashLicenseCode, normalizeEmail } from '../utils/crypto.js';

export class LicenseService {
  private db: Database.Database;

  constructor(customDb?: Database.Database) {
    this.db = customDb || getDatabase();
  }

  /**
   * Activates a license on a device.
   * Atomic transaction guarantees strict concurrency protection (at most 1 active device per license).
   */
  activateLicense(payload: ActivateRequest): {
    success: boolean;
    status?: string;
    error?: { code: string; message: string };
  } {
    const { licenseCode, ownerEmail, deviceBinding } = payload;

    if (!licenseCode || !ownerEmail || !deviceBinding) {
      return {
        success: false,
        error: { code: 'INVALID_REQUEST', message: 'Missing required activation fields.' }
      };
    }

    const codeHash = hashLicenseCode(licenseCode);
    const emailHash = hashEmail(ownerEmail);
    const canonicalEmail = normalizeEmail(ownerEmail);
    const now = Date.now();

    const activateTx = this.db.transaction(() => {
      // Find license
      const license = this.db
        .prepare('SELECT * FROM licenses WHERE license_code_hash = ?')
        .get(codeHash) as LicenseRecord | undefined;

      if (!license) {
        return {
          success: false,
          error: { code: 'LICENSE_NOT_FOUND', message: 'License code does not exist.' }
        };
      }

      if (license.status === 'REVOKED') {
        return {
          success: false,
          error: { code: 'LICENSE_REVOKED', message: 'License has been revoked and cannot be activated.' }
        };
      }

      // Verify owner email (constant time / canonical comparison)
      if (license.owner_email_hash !== emailHash && license.owner_email_canonical !== canonicalEmail) {
        return {
          success: false,
          error: { code: 'EMAIL_MISMATCH', message: 'Owner email does not match license registration.' }
        };
      }

      // Check current active device
      const activeDevice = this.db
        .prepare('SELECT * FROM license_devices WHERE license_id = ? AND status = ?')
        .get(license.id, 'ACTIVE') as LicenseDeviceRecord | undefined;

      if (activeDevice) {
        if (activeDevice.device_binding === deviceBinding) {
          // Idempotent reactivation on same device
          this.db
            .prepare('UPDATE license_devices SET last_validated_at = ?, updated_at = ? WHERE id = ?')
            .run(now, now, activeDevice.id);

          return {
            success: true,
            status: 'ACTIVE'
          };
        } else {
          // Second device rejection
          return {
            success: false,
            error: {
              code: 'DEVICE_MISMATCH',
              message: 'License is already active on another device. Transfer requires recovery.'
            }
          };
        }
      }

      // Bind new active device (License was PENDING)
      this.db
        .prepare(`
          INSERT INTO license_devices (
            license_id, device_binding, status, first_activated_at, last_validated_at, created_at, updated_at
          ) VALUES (?, ?, 'ACTIVE', ?, ?, ?, ?)
        `)
        .run(license.id, deviceBinding, now, now, now, now);

      this.db
        .prepare(`
          UPDATE licenses 
          SET status = 'ACTIVE', activated_at = COALESCE(activated_at, ?), updated_at = ? 
          WHERE id = ?
        `)
        .run(now, now, license.id);

      this.db
        .prepare(`
          INSERT INTO audit_logs (action, license_id, old_state, new_state, actor, reason, created_at)
          VALUES ('ACTIVATE_LICENSE', ?, ?, 'ACTIVE', 'CLIENT', 'Initial device activation', ?)
        `)
        .run(license.id, license.status, now);

      return {
        success: true,
        status: 'ACTIVE'
      };
    });

    return activateTx.immediate();
  }

  /**
   * Validates whether a device binding is currently authorized for a license.
   */
  validateLicense(payload: ValidateRequest): {
    valid: boolean;
    status: ValidationResultStatus;
    message?: string;
  } {
    const { licenseCode, ownerEmail, deviceBinding } = payload;

    if (!licenseCode || !ownerEmail || !deviceBinding) {
      return { valid: false, status: 'INVALID', message: 'Missing validation fields.' };
    }

    const codeHash = hashLicenseCode(licenseCode);
    const emailHash = hashEmail(ownerEmail);
    const canonicalEmail = normalizeEmail(ownerEmail);
    const now = Date.now();

    const license = this.db
      .prepare('SELECT * FROM licenses WHERE license_code_hash = ?')
      .get(codeHash) as LicenseRecord | undefined;

    if (!license) {
      return { valid: false, status: 'INVALID', message: 'License not found.' };
    }

    if (license.status === 'REVOKED') {
      return { valid: false, status: 'REVOKED', message: 'License is revoked.' };
    }

    if (license.owner_email_hash !== emailHash && license.owner_email_canonical !== canonicalEmail) {
      return { valid: false, status: 'EMAIL_MISMATCH', message: 'Email does not match license.' };
    }

    const activeDevice = this.db
      .prepare('SELECT * FROM license_devices WHERE license_id = ? AND status = ?')
      .get(license.id, 'ACTIVE') as LicenseDeviceRecord | undefined;

    if (!activeDevice) {
      return { valid: false, status: 'INVALID', message: 'License has not been activated on any device.' };
    }

    if (activeDevice.device_binding !== deviceBinding) {
      return {
        valid: false,
        status: 'DEVICE_MISMATCH',
        message: 'Device binding does not match active license binding.'
      };
    }

    // Update last validated timestamp
    this.db
      .prepare('UPDATE license_devices SET last_validated_at = ?, updated_at = ? WHERE id = ?')
      .run(now, now, activeDevice.id);

    return {
      valid: true,
      status: 'VALID',
      message: 'License is active and valid.'
    };
  }

  /**
   * Registers a recovery request for a license.
   */
  requestRecovery(payload: RecoverRequest): {
    success: boolean;
    status?: string;
    error?: { code: string; message: string };
  } {
    const { licenseCode, ownerEmail, newDeviceBinding, reason } = payload;

    if (!licenseCode || !ownerEmail || !newDeviceBinding) {
      return {
        success: false,
        error: { code: 'INVALID_REQUEST', message: 'Missing required recovery fields.' }
      };
    }

    const codeHash = hashLicenseCode(licenseCode);
    const emailHash = hashEmail(ownerEmail);
    const canonicalEmail = normalizeEmail(ownerEmail);
    const now = Date.now();

    const license = this.db
      .prepare('SELECT * FROM licenses WHERE license_code_hash = ?')
      .get(codeHash) as LicenseRecord | undefined;

    if (!license) {
      return {
        success: false,
        error: { code: 'LICENSE_NOT_FOUND', message: 'License code does not exist.' }
      };
    }

    if (license.status === 'REVOKED') {
      return {
        success: false,
        error: { code: 'LICENSE_REVOKED', message: 'Cannot recover a revoked license.' }
      };
    }

    if (license.owner_email_hash !== emailHash && license.owner_email_canonical !== canonicalEmail) {
      return {
        success: false,
        error: { code: 'EMAIL_MISMATCH', message: 'Owner email does not match license registration.' }
      };
    }

    const activeDevice = this.db
      .prepare('SELECT * FROM license_devices WHERE license_id = ? AND status = ?')
      .get(license.id, 'ACTIVE') as LicenseDeviceRecord | undefined;

    this.db
      .prepare(`
        INSERT INTO recovery_requests (
          license_id, old_device_binding, new_device_binding, status, reason, created_at
        ) VALUES (?, ?, ?, 'PENDING', ?, ?)
      `)
      .run(license.id, activeDevice ? activeDevice.device_binding : null, newDeviceBinding, reason || 'Device transfer/recovery requested', now);

    this.db
      .prepare(`
        INSERT INTO audit_logs (action, license_id, old_state, new_state, actor, reason, created_at)
        VALUES ('REQUEST_RECOVERY', ?, ?, 'RECOVERY_PENDING', 'CLIENT', ?, ?)
      `)
      .run(license.id, license.status, reason || 'User requested device recovery', now);

    return {
      success: true,
      status: 'RECOVERY_PENDING'
    };
  }

  /**
   * Returns non-sensitive status information for client queries.
   */
  getLicenseStatus(licenseCode: string, ownerEmail: string): {
    success: boolean;
    data?: {
      status: string;
      product: string;
      hasActiveDevice: boolean;
    };
    error?: { code: string; message: string };
  } {
    const codeHash = hashLicenseCode(licenseCode);
    const emailHash = hashEmail(ownerEmail);
    const canonicalEmail = normalizeEmail(ownerEmail);

    const license = this.db
      .prepare('SELECT * FROM licenses WHERE license_code_hash = ?')
      .get(codeHash) as LicenseRecord | undefined;

    if (!license) {
      return {
        success: false,
        error: { code: 'LICENSE_NOT_FOUND', message: 'License not found.' }
      };
    }

    if (license.owner_email_hash !== emailHash && license.owner_email_canonical !== canonicalEmail) {
      return {
        success: false,
        error: { code: 'EMAIL_MISMATCH', message: 'Owner email does not match license.' }
      };
    }

    const activeDevice = this.db
      .prepare('SELECT * FROM license_devices WHERE license_id = ? AND status = ?')
      .get(license.id, 'ACTIVE') as LicenseDeviceRecord | undefined;

    return {
      success: true,
      data: {
        status: license.status,
        product: license.product,
        hasActiveDevice: !!activeDevice
      }
    };
  }
}
