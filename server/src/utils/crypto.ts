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
export function hashEmail(email: string, pepper: string = config.serverPepper): string {
  const canonical = normalizeEmail(email);
  return crypto.createHmac('sha256', pepper).update(canonical).digest('hex');
}

/**
 * Normalizes license code (uppercase, trim) and hashes using HMAC-SHA256 with server pepper.
 */
export function hashLicenseCode(code: string, pepper: string = config.serverPepper): string {
  const normalized = code.trim().toUpperCase();
  return crypto.createHmac('sha256', pepper).update(normalized).digest('hex');
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
