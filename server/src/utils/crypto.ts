import crypto from 'crypto';
import { config } from '../config/index.js';

/**
 * Normalizes email by trimming whitespace and converting to lowercase.
 */
export function normalizeEmail(email: string): string {
  return email.trim().toLowerCase();
}

/**
 * Computes deterministic SHA-256 hash of canonicalized email with server pepper.
 */
export function hashEmail(email: string, pepper: string = getServerPepper()): string {
  const canonical = normalizeEmail(email);
  return crypto.createHmac('sha256', pepper).update(canonical).digest('hex');
}

/**
 * Normalizes license code (uppercase, trim) and hashes using HMAC-SHA256 with server pepper.
 */
export function hashLicenseCode(code: string, pepper: string = getServerPepper()): string {
  const normalized = code.trim().toUpperCase();
  return crypto.createHmac('sha256', pepper).update(normalized).digest('hex');
}

function getServerPepper(): string {
  const pepper = config.serverPepper;
  if (!pepper) {
    throw new Error('SERVER_PEPPER is required. Set it in the environment before starting the server.');
  }
  return pepper;
}

/**
 * Generates a cryptographically secure random license code.
 * Format: BW-XXXX-XXXX-XXXX (Base32 Crockford uppercase alphanumeric without confusing chars: 0/O, 1/I/L)
 */
export function generateLicenseCode(): string {
  const chars = '23456789ABCDEFGHJKMNPQRSTVWXYZ';
  const generateSegment = (length: number): string => {
    const bytes = crypto.randomBytes(length);
    let result = '';
    for (let i = 0; i < length; i++) {
      result += chars[bytes[i] % chars.length];
    }
    return result;
  };

  return `BW-${generateSegment(4)}-${generateSegment(4)}-${generateSegment(4)}`;
}

/**
 * Generates a standard UUID v4.
 */
export function generateUuid(): string {
  return crypto.randomUUID();
}

/**
 * Timing-safe string comparison to prevent timing attacks on API keys or secrets.
 */
export function timingSafeCompare(a: string, b: string): boolean {
  const bufA = Buffer.from(a);
  const bufB = Buffer.from(b);
  if (bufA.length !== bufB.length) {
    return false;
  }
  return crypto.timingSafeEqual(bufA, bufB);
}

const DELIVERY_CIPHER_ALGORITHM = 'aes-256-gcm';
const DELIVERY_KEY_DOMAIN = 'license_delivery_v1';

/**
 * Derives a 32-byte AES-256 key from SERVER_PEPPER with domain separation.
 * NOTE: Changing SERVER_PEPPER in the environment invalidates existing encrypted delivery values.
 */
function getDeliveryDerivedKey(): Buffer {
  const pepper = getServerPepper();
  return crypto.createHash('sha256').update(`${pepper}:${DELIVERY_KEY_DOMAIN}`).digest();
}

/**
 * Encrypts plaintext license code for secure storage at rest in orders table.
 * Uses AES-256-GCM with a fresh random 96-bit IV per encryption.
 * Output format: base64url(IV [12 bytes] + AuthTag [16 bytes] + Ciphertext).
 * Plaintext license code is never logged.
 */
export function encryptDeliveryLicenseCode(licenseCode: string): string {
  if (!licenseCode || typeof licenseCode !== 'string') {
    throw new Error('Valid licenseCode string is required for delivery encryption.');
  }

  const key = getDeliveryDerivedKey();
  const iv = crypto.randomBytes(12); // 96-bit random IV
  const cipher = crypto.createCipheriv(DELIVERY_CIPHER_ALGORITHM, key, iv);
  const ciphertext = Buffer.concat([cipher.update(licenseCode.trim().toUpperCase(), 'utf8'), cipher.final()]);
  const authTag = cipher.getAuthTag(); // 128-bit authentication tag

  const combined = Buffer.concat([iv, authTag, ciphertext]);
  return combined.toString('base64url');
}

/**
 * Decrypts an encrypted delivery license code with authentication tag verification.
 * Returns the plaintext license code if valid, or null if missing, malformed, or tampered.
 */
export function decryptDeliveryLicenseCode(encryptedData: string | null | undefined): string | null {
  if (!encryptedData || typeof encryptedData !== 'string') {
    return null;
  }

  try {
    const combined = Buffer.from(encryptedData, 'base64url');
    // Minimum 12 bytes IV + 16 bytes AuthTag + 1 byte ciphertext = 29 bytes
    if (combined.length < 29) {
      return null;
    }

    const iv = combined.subarray(0, 12);
    const authTag = combined.subarray(12, 28);
    const ciphertext = combined.subarray(28);

    const key = getDeliveryDerivedKey();
    const decipher = crypto.createDecipheriv(DELIVERY_CIPHER_ALGORITHM, key, iv);
    decipher.setAuthTag(authTag);

    const decrypted = Buffer.concat([decipher.update(ciphertext), decipher.final()]).toString('utf8');
    return decrypted || null;
  } catch {
    return null;
  }
}

