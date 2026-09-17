package id.skmnetwork.bukuwarung.domain.discount

import kotlin.math.floor

enum class DiscountType {
    FIXED,
    PERCENTAGE
}

data class DiscountInput(
    val type: DiscountType = DiscountType.FIXED,
    val value: Double = 0.0
)

data class DiscountCalculationResult(
    val grossSubtotal: Long,
    val discountAmount: Long,
    val netTotal: Long,
    val discountPercentage: Double? = null
)

object DiscountCalculator {

    /**
     * Calculates validated discount amount and net total from gross subtotal.
     * Guaranteed Invariants:
     * - discountAmount >= 0
     * - discountAmount <= grossSubtotal
     * - netTotal = grossSubtotal - discountAmount >= 0
     * - Percentage is calculated using integer floor: floor(grossSubtotal * percentage / 100)
     */
    fun calculate(grossSubtotal: Long, discountInput: DiscountInput): DiscountCalculationResult {
        if (grossSubtotal <= 0L) {
            return DiscountCalculationResult(
                grossSubtotal = 0L,
                discountAmount = 0L,
                netTotal = 0L,
                discountPercentage = if (discountInput.type == DiscountType.PERCENTAGE) discountInput.value else null
            )
        }

        val calculatedDiscount = when (discountInput.type) {
            DiscountType.FIXED -> {
                discountInput.value.toLong().coerceAtLeast(0L)
            }
            DiscountType.PERCENTAGE -> {
                val clampedPercent = discountInput.value.coerceIn(0.0, 100.0)
                floor((grossSubtotal.toDouble() * clampedPercent) / 100.0).toLong()
            }
        }

        val safeDiscount = calculatedDiscount.coerceIn(0L, grossSubtotal)
        val netTotal = (grossSubtotal - safeDiscount).coerceAtLeast(0L)

        return DiscountCalculationResult(
            grossSubtotal = grossSubtotal,
            discountAmount = safeDiscount,
            netTotal = netTotal,
            discountPercentage = if (discountInput.type == DiscountType.PERCENTAGE) discountInput.value.coerceIn(0.0, 100.0) else null
        )
    }

    fun calculateFixed(grossSubtotal: Long, fixedAmount: Long): DiscountCalculationResult {
        return calculate(grossSubtotal, DiscountInput(DiscountType.FIXED, fixedAmount.toDouble()))
    }

    fun calculatePercentage(grossSubtotal: Long, percentage: Double): DiscountCalculationResult {
        return calculate(grossSubtotal, DiscountInput(DiscountType.PERCENTAGE, percentage))
    }
}
