import crypto from 'crypto';
import { config } from '../config/index.js';

const TOKEN_PREFIX = 'pot_'; // Public Order Token
const ALGORITHM = 'aes-256-gcm';

function getDerivedKey(): Buffer {
  return crypto.createHash('sha256').update(`${config.serverPepper}:public_order_token_v1`).digest();
}

/**
 * Generates a cryptographically secure, unpredictable capability token for an order.
 * Uses AES-256-GCM authenticated encryption with a fresh random 96-bit IV.
 * Token length is ~64 characters (safe for all HTTP clients and Fastify parameter limits).
 * Zero database migration required.
 */
export function generatePublicOrderToken(orderNumber: string): string {
  const key = getDerivedKey();
  const iv = crypto.randomBytes(12); // 96-bit random IV (guarantees unique ciphertext even for identical plaintexts)

  const cipher = crypto.createCipheriv(ALGORITHM, key, iv);
  const ciphertext = Buffer.concat([cipher.update(orderNumber, 'utf8'), cipher.final()]);
  const authTag = cipher.getAuthTag(); // 128-bit authentication tag

  const combined = Buffer.concat([iv, authTag, ciphertext]);
  return `${TOKEN_PREFIX}${combined.toString('base64url')}`;
}

/**
 * Verifies and decrypts a public order token.
 * Returns the authenticated order_number if valid, or an error if tampered.
 */
export function verifyPublicOrderToken(token: string): { valid: boolean; orderNumber?: string; error?: string } {
  if (!token || typeof token !== 'string' || !token.startsWith(TOKEN_PREFIX)) {
    return { valid: false, error: 'INVALID_TOKEN_FORMAT' };
  }

  const rawBase64 = token.slice(TOKEN_PREFIX.length);
  try {
    const combined = Buffer.from(rawBase64, 'base64url');
    // 12 bytes IV + 16 bytes AuthTag + minimum 1 byte ciphertext = 29 bytes minimum
    if (combined.length < 29) {
      return { valid: false, error: 'TOKEN_TOO_SHORT' };
    }

    const iv = combined.subarray(0, 12);
    const authTag = combined.subarray(12, 28);
    const ciphertext = combined.subarray(28);

    const key = getDerivedKey();
    const decipher = crypto.createDecipheriv(ALGORITHM, key, iv);
    decipher.setAuthTag(authTag);

    const decrypted = Buffer.concat([decipher.update(ciphertext), decipher.final()]).toString('utf8');

    if (!decrypted || !decrypted.startsWith('BW-ORD-')) {
      return { valid: false, error: 'MALFORMED_PAYLOAD' };
    }

    return { valid: true, orderNumber: decrypted };
  } catch {
    return { valid: false, error: 'TOKEN_VERIFICATION_FAILED' };
  }
}
