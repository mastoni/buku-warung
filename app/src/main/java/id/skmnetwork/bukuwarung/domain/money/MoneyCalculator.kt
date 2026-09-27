package id.skmnetwork.bukuwarung.domain.money

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Gate H.2 - the single authoritative money calculation rule for quantity x unit price.
 *
 * WHY THIS EXISTS
 * The application supports fractional quantities (kg, liter, meter, sack) - `ProductEntity.stock`,
 * `SaleItemEntity.quantity`, `PurchaseItemEntity.quantity` and `StockMovementEntity.deltaQuantity`
 * are all `Double` backed by SQLite `REAL` columns, and FUEL items are explicitly sold by the
 * litre. The money path however used `unitPrice * quantity.toLong()`, which truncates the
 * quantity before multiplying:
 *
 *     15000L * 2.5.toLong()  == 15000L * 2  == 30000   (should be 37500)
 *     15000L * 0.5.toLong()  == 15000L * 0  ==     0   (should be  7500)
 *
 * That silently under-billed merchants and could even make a legitimate sale fail the
 * `grossSubtotal > 0` guard. Every sale, purchase, purchase-order, return, preview and ledger
 * amount now routes through this object, so one rule governs all of them.
 *
 * PRECISION CONTRACT
 * - Quantity is a decimal. It is never truncated and never silently rounded to a whole unit.
 * - The intermediate product is computed in exact decimal arithmetic, never binary floating point.
 * - `BigDecimal.valueOf` is used deliberately instead of `BigDecimal(double)`: the latter captures
 *   the exact binary expansion, so 0.1 would become 0.1000000000000000055511151231257827 and
 *   15000 x 0.1 would be 1500.0000000000000832... `valueOf` goes through the shortest decimal
 *   representation instead, giving exactly 0.1.
 * - [QUANTITY_SCALE] normalises residual float error introduced by upstream arithmetic
 *   (for example 2.5 + 0.1) without ever rounding a realistic retail quantity away.
 *
 * SUB-RUPIAH ROUNDING POLICY
 * Money is stored as `Long` rupiah and cannot hold a fraction of a rupiah, so the product is
 * rounded exactly once, at the rupiah boundary, using [MONEY_ROUNDING].
 *
 * `HALF_UP` is not a new policy invented here: it is already the application-wide default,
 * used by `TaxSettings.roundingMode ?: RoundingMode.HALF_UP` in the sale path and applied
 * unconditionally by the refund ledger. Half-up is also the only policy that keeps a normal
 * integer-quantity sale bit-for-bit identical, because for an integral quantity the exact product
 * is already a whole rupiah and no rounding ever occurs.
 *
 * Tax and discount rounding remain governed by the merchant's own `TaxSettings.roundingMode`;
 * that setting is deliberately NOT reused here, because reusing a tax preference to round the
 * pre-tax line subtotal would silently change merchandise pricing.
 */
object MoneyCalculator {

    /** Rounding applied once, at the rupiah boundary, for every quantity x unit-price product. */
    val MONEY_ROUNDING: RoundingMode = RoundingMode.HALF_UP

    /** Decimal places retained for a quantity before the money rounding is applied. */
    const val QUANTITY_SCALE: Int = 9

    /**
     * Line amount for [quantity] units at [unitPrice] rupiah, rounded once to whole rupiah.
     *
     *     lineSubtotal(15000L, 2.5)  == 37500
     *     lineSubtotal(15000L, 0.5)  ==  7500
     *     lineSubtotal(15000L, 1.25) == 18750
     *     lineSubtotal(15000L, 0.1)  ==  1500
     *     lineSubtotal(999L, 0.5)   ==   500   (499.5 rounded HALF_UP)
     */
    fun lineSubtotal(unitPrice: Long, quantity: Double): Long {
        if (!quantity.isFinite()) {
            throw IllegalArgumentException("Kuantitas tidak valid: $quantity")
        }
        if (unitPrice == 0L || quantity == 0.0) return 0L
        val product = BigDecimal(unitPrice).multiply(decimalQuantity(quantity))
        return product.setScale(0, MONEY_ROUNDING).toLong()
    }

    /**
     * The share of [originalAmount] that corresponds to [portionQuantity] out of
     * [originalQuantity] - the refund rule. Rounded once to whole rupiah, exactly like
     * [lineSubtotal], so a UI preview and the persisted ledger cannot disagree.
     *
     *     proportionalShare(75000L, 1.5, 5.0) == 22500
     */
    fun proportionalShare(
        originalAmount: Long,
        portionQuantity: Double,
        originalQuantity: Double
    ): Long {
        if (!portionQuantity.isFinite() || !originalQuantity.isFinite()) {
            throw IllegalArgumentException("Kuantitas tidak valid: $portionQuantity / $originalQuantity")
        }
        if (originalAmount == 0L || portionQuantity == 0.0) return 0L
        require(originalQuantity > 0.0) {
            "Kuantitas asli harus lebih besar dari 0"
        }
        val share = BigDecimal(originalAmount)
            .multiply(decimalQuantity(portionQuantity))
            .divide(decimalQuantity(originalQuantity), 0, MONEY_ROUNDING)
        return share.toLong()
    }

    /**
     * Normalises a Double quantity into an exact decimal scaled to [QUANTITY_SCALE].
     * Uses `valueOf` (shortest decimal representation) rather than the `BigDecimal(double)`
     * constructor (exact binary expansion) - see the class documentation.
     */
    private fun decimalQuantity(quantity: Double): BigDecimal =
        BigDecimal.valueOf(quantity).setScale(QUANTITY_SCALE, RoundingMode.HALF_UP)
}
