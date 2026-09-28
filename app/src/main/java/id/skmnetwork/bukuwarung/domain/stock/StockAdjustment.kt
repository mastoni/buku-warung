package id.skmnetwork.bukuwarung.domain.stock

import id.skmnetwork.bukuwarung.data.local.entity.ItemType

/**
 * Step 2 (D) - the rules for correcting a product's stock, kept pure so they can be unit tested
 * without a database.
 *
 * Stock is changed through exactly this path, and the result is always written as a stock movement
 * so the ledger and the product row cannot disagree.
 */
sealed class StockAdjustmentValidation {
    data class Valid(
        val previousStock: Double,
        val newStock: Double,
        val delta: Double,
        /** Movement label, so the history says whether this was a count or a correction. */
        val suggestedMovementType: String
    ) : StockAdjustmentValidation()

    data class Rejected(val reason: String) : StockAdjustmentValidation()
}

object StockAdjustment {

    /**
     * @param currentStock the stock the app currently believes is on the shelf
     * @param proposedStock the number the merchant actually counted
     * @param itemTypeName the product's [ItemType] name
     * @param isStockCount true for a physical stock count (OPNAME), false for a correction
     */
    fun validate(
        currentStock: Double,
        proposedStock: Double,
        itemTypeName: String,
        isStockCount: Boolean = false
    ): StockAdjustmentValidation {
        if (!ItemType.isStockable(itemTypeName)) {
            return StockAdjustmentValidation.Rejected(
                "Produk ini bukan produk berbobot, jadi tidak ada stok yang bisa disesuaikan"
            )
        }
        if (proposedStock.isNaN() || proposedStock.isInfinite()) {
            return StockAdjustmentValidation.Rejected("Stok aktual tidak valid")
        }
        if (proposedStock < 0) {
            return StockAdjustmentValidation.Rejected("Stok tidak boleh kurang dari 0")
        }
        val delta = proposedStock - currentStock
        if (delta == 0.0) {
            return StockAdjustmentValidation.Rejected(
                "Stok aktual sama dengan stok saat ini, tidak ada yang perlu disesuaikan"
            )
        }
        return StockAdjustmentValidation.Valid(
            previousStock = currentStock,
            newStock = proposedStock,
            delta = delta,
            suggestedMovementType = if (isStockCount) "OPNAME" else "ADJUSTMENT"
        )
    }
}
