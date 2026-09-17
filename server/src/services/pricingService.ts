import Database from 'better-sqlite3';
import { getDatabase } from '../db/database.js';
import { PromotionRecord, PricingResult, UpdatePromotionRequest } from '../types/index.js';

export class PricingService {
  private db: Database.Database;

  constructor(customDb?: Database.Database) {
    this.db = customDb || getDatabase();
  }

  /**
   * Retrieves a single promotion record by product identifier.
   */
  getPromotion(product: string): PromotionRecord | null {
    if (!product || typeof product !== 'string') return null;
    const row = this.db
      .prepare('SELECT * FROM promotions WHERE product = ?')
      .get(product.trim()) as PromotionRecord | undefined;
    return row || null;
  }

  /**
   * Lists all promotion configurations.
   */
  listPromotions(): PromotionRecord[] {
    return this.db
      .prepare('SELECT * FROM promotions ORDER BY id ASC')
      .all() as PromotionRecord[];
  }

  /**
   * Resolves the authoritative commercial price for a product at a specific timestamp.
   * If timestamp is omitted, current server time (Date.now()) is used.
   */
  resolvePrice(product: string, timestamp?: number): PricingResult {
    const serverTime = typeof timestamp === 'number' && !isNaN(timestamp) ? timestamp : Date.now();
    const promo = this.getPromotion(product);

    if (!promo) {
      // Default fallback for unknown products if not found in db
      const fallbackPrice = 100000;
      return {
        product,
        isPromoActive: false,
        effectivePrice: fallbackPrice,
        normalPrice: fallbackPrice,
        promoPrice: fallbackPrice,
        promoName: '',
        startsAt: 0,
        expiresAt: 0,
        timezone: 'Asia/Jakarta',
        showCountdown: false,
        serverTime
      };
    }

    const isPromoActive =
      promo.enabled === 1 &&
      serverTime >= promo.starts_at &&
      serverTime < promo.expires_at;

    const effectivePrice = isPromoActive ? promo.promo_price : promo.normal_price;

    return {
      product: promo.product,
      isPromoActive,
      effectivePrice,
      normalPrice: promo.normal_price,
      promoPrice: promo.promo_price,
      promoName: promo.name,
      startsAt: promo.starts_at,
      expiresAt: promo.expires_at,
      timezone: promo.timezone,
      showCountdown: promo.show_countdown === 1,
      serverTime
    };
  }

  /**
   * Updates or creates a promotion/pricing configuration for a product.
   * Enforces all pricing, time range, and data type validation rules.
   * Logs mutation atomically to audit_logs.
   */
  updatePromotion(
    product: string,
    payload: UpdatePromotionRequest,
    actor: string = 'ADMIN'
  ): {
    success: boolean;
    data?: PricingResult;
    error?: { code: string; message: string };
  } {
    if (!product || typeof product !== 'string' || !product.trim()) {
      return {
        success: false,
        error: { code: 'INVALID_PRODUCT', message: 'Product code is required and must be non-empty.' }
      };
    }

    const canonicalProduct = product.trim();
    const existing = this.getPromotion(canonicalProduct);

    // Merge existing or defaults
    const currentName = existing ? existing.name : 'Promo ' + canonicalProduct;
    const currentEnabled = existing ? existing.enabled : 1;
    const currentNormal = existing ? existing.normal_price : 100000;
    const currentPromo = existing ? existing.promo_price : 50000;
    const currentStarts = existing ? existing.starts_at : Date.now();
    const currentExpires = existing ? existing.expires_at : currentStarts + 365 * 24 * 60 * 60 * 1000;
    const currentTimezone = existing ? existing.timezone : 'Asia/Jakarta';
    const currentShowCountdown = existing ? existing.show_countdown : 1;

    // Validate and parse payload fields
    const name = payload.name !== undefined ? String(payload.name).trim() : currentName;
    if (!name) {
      return {
        success: false,
        error: { code: 'INVALID_NAME', message: 'Promotion name cannot be empty.' }
      };
    }

    let enabled = currentEnabled;
    if (payload.enabled !== undefined) {
      if (typeof payload.enabled === 'boolean') {
        enabled = payload.enabled ? 1 : 0;
      } else if (payload.enabled === 1 || payload.enabled === 0) {
        enabled = payload.enabled;
      } else {
        return {
          success: false,
          error: { code: 'INVALID_ENABLED', message: 'Enabled field must be boolean or 0/1.' }
        };
      }
    }

    const normalPrice = payload.normalPrice !== undefined ? Number(payload.normalPrice) : currentNormal;
    if (isNaN(normalPrice) || normalPrice <= 0 || !Number.isInteger(normalPrice)) {
      return {
        success: false,
        error: { code: 'INVALID_NORMAL_PRICE', message: 'Normal price must be a positive integer greater than 0.' }
      };
    }

    const promoPrice = payload.promoPrice !== undefined ? Number(payload.promoPrice) : currentPromo;
    if (isNaN(promoPrice) || promoPrice <= 0 || !Number.isInteger(promoPrice)) {
      return {
        success: false,
        error: { code: 'INVALID_PROMO_PRICE', message: 'Promo price must be a positive integer greater than 0.' }
      };
    }

    if (promoPrice > normalPrice) {
      return {
        success: false,
        error: {
          code: 'INVALID_PRICE_RELATION',
          message: 'Promo price cannot exceed normal price.'
        }
      };
    }

    // Parse startsAt
    let startsAt = currentStarts;
    if (payload.startsAt !== undefined) {
      if (typeof payload.startsAt === 'number') {
        startsAt = payload.startsAt;
      } else if (typeof payload.startsAt === 'string') {
        const parsed = new Date(payload.startsAt).getTime();
        if (isNaN(parsed)) {
          return {
            success: false,
            error: { code: 'INVALID_STARTS_AT', message: 'startsAt is not a valid date/timestamp.' }
          };
        }
        startsAt = parsed;
      }
    }

    // Parse expiresAt
    let expiresAt = currentExpires;
    if (payload.expiresAt !== undefined) {
      if (typeof payload.expiresAt === 'number') {
        expiresAt = payload.expiresAt;
      } else if (typeof payload.expiresAt === 'string') {
        const parsed = new Date(payload.expiresAt).getTime();
        if (isNaN(parsed)) {
          return {
            success: false,
            error: { code: 'INVALID_EXPIRES_AT', message: 'expiresAt is not a valid date/timestamp.' }
          };
        }
        expiresAt = parsed;
      }
    }

    if (startsAt >= expiresAt) {
      return {
        success: false,
        error: {
          code: 'INVALID_DATE_RANGE',
          message: 'Promotion start time must be strictly earlier than expiration time.'
        }
      };
    }

    const timezone = payload.timezone !== undefined ? String(payload.timezone).trim() : currentTimezone;
    if (!timezone) {
      return {
        success: false,
        error: { code: 'INVALID_TIMEZONE', message: 'Timezone cannot be empty.' }
      };
    }

    let showCountdown = currentShowCountdown;
    if (payload.showCountdown !== undefined) {
      if (typeof payload.showCountdown === 'boolean') {
        showCountdown = payload.showCountdown ? 1 : 0;
      } else if (payload.showCountdown === 1 || payload.showCountdown === 0) {
        showCountdown = payload.showCountdown;
      } else {
        return {
          success: false,
          error: { code: 'INVALID_SHOW_COUNTDOWN', message: 'showCountdown must be boolean or 0/1.' }
        };
      }
    }

    const now = Date.now();

    // Atomic transaction for promotion update & audit logging
    const updateTx = this.db.transaction(() => {
      let recordId = existing ? existing.id : 0;

      if (existing) {
        this.db
          .prepare(`
            UPDATE promotions
            SET name = ?, enabled = ?, normal_price = ?, promo_price = ?,
                starts_at = ?, expires_at = ?, timezone = ?, show_countdown = ?,
                updated_at = ?
            WHERE product = ?
          `)
          .run(
            name,
            enabled,
            normalPrice,
            promoPrice,
            startsAt,
            expiresAt,
            timezone,
            showCountdown,
            now,
            canonicalProduct
          );
      } else {
        const result = this.db
          .prepare(`
            INSERT INTO promotions (
              product, name, enabled, normal_price, promo_price, starts_at, expires_at,
              timezone, show_countdown, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          `)
          .run(
            canonicalProduct,
            name,
            enabled,
            normalPrice,
            promoPrice,
            startsAt,
            expiresAt,
            timezone,
            showCountdown,
            now,
            now
          );
        recordId = Number(result.lastInsertRowid);
      }

      const updatedRecord = this.getPromotion(canonicalProduct)!;

      // Audit Log
      this.db
        .prepare(`
          INSERT INTO audit_logs (action, license_id, old_state, new_state, actor, reason, created_at)
          VALUES ('UPDATE_PROMOTION', NULL, ?, ?, ?, ?, ?)
        `)
        .run(
          existing ? JSON.stringify(existing) : null,
          JSON.stringify(updatedRecord),
          actor,
          `Updated pricing/promotion config for product '${canonicalProduct}'`,
          now
        );

      return updatedRecord;
    });

    const updated = updateTx.immediate();
    const resolved = this.resolvePrice(canonicalProduct, now);

    return {
      success: true,
      data: resolved
    };
  }
}
