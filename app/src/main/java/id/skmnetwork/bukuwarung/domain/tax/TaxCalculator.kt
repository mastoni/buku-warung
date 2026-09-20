package id.skmnetwork.bukuwarung.domain.tax

import id.skmnetwork.bukuwarung.domain.tax.TaxPriceMode.EXCLUSIVE
import id.skmnetwork.bukuwarung.domain.tax.TaxPriceMode.INCLUSIVE
import java.math.BigDecimal
import java.math.RoundingMode

data class TaxCalculationResult(
    val lineSubtotal: Long,
    val taxableBase: Long,
    val taxAmount: Long,
    val grandTotal: Long
)

data class TransactionTaxResult(
    val grossSubtotal: Long,
    val discountAmount: Long,
    val taxableBase: Long,
    val taxAmount: Long,
    val grandTotal: Long,
    val itemResults: List<TaxCalculationResult>
)

data class ItemTaxInput(
    val lineSubtotal: Long,
    val taxable: Boolean
)

object TaxCalculator {

    fun calculateItemTax(
        lineSubtotal: Long,
        rate: Double,
        priceMode: TaxPriceMode,
        roundingMode: RoundingMode,
        taxable: Boolean
    ): TaxCalculationResult {
        if (!taxable) {
            return TaxCalculationResult(
                lineSubtotal = lineSubtotal,
                taxableBase = 0L,
                taxAmount = 0L,
                grandTotal = lineSubtotal
            )
        }

        val safeRate = validateRate(rate)
        return when (priceMode) {
            EXCLUSIVE -> calculateExclusiveItem(lineSubtotal, safeRate, roundingMode)
            INCLUSIVE -> calculateInclusiveItem(lineSubtotal, safeRate, roundingMode)
        }
    }

    fun calculateTransactionTax(
        items: List<ItemTaxInput>,
        discountAmount: Long,
        rate: Double,
        priceMode: TaxPriceMode,
        roundingMode: RoundingMode
    ): TransactionTaxResult {
        val grossSubtotal = items.sumOf { it.lineSubtotal }
        val safeDiscount = discountAmount.coerceAtLeast(0L).coerceAtMost(grossSubtotal)
        val safeRate = validateRate(rate)
        
        val itemResults = items.map { item ->
            if (!item.taxable || safeRate <= 0.0) {
                TaxCalculationResult(
                    lineSubtotal = item.lineSubtotal,
                    taxableBase = 0L,
                    taxAmount = 0L,
                    grandTotal = item.lineSubtotal
                )
            } else {
                val itemDiscount = if (grossSubtotal > 0L) {
                    roundToLong(BigDecimal(safeDiscount) * BigDecimal(item.lineSubtotal) / BigDecimal(grossSubtotal), roundingMode)
                } else {
                    0L
                }
                val discountedSubtotal = item.lineSubtotal - itemDiscount
                
                when (priceMode) {
                    EXCLUSIVE -> {
                        val taxAmount = roundToLong(BigDecimal(discountedSubtotal) * BigDecimal(rate) / BigDecimal(100), roundingMode)
                        TaxCalculationResult(
                            lineSubtotal = item.lineSubtotal,
                            taxableBase = discountedSubtotal,
                            taxAmount = taxAmount,
                            grandTotal = discountedSubtotal + taxAmount
                        )
                    }
                    INCLUSIVE -> {
                        val divisor = BigDecimal(100) + BigDecimal(rate)
                        val taxableBase = roundToLong(BigDecimal(discountedSubtotal) * BigDecimal(100) / divisor, roundingMode)
                        val taxAmount = discountedSubtotal - taxableBase
                        TaxCalculationResult(
                            lineSubtotal = item.lineSubtotal,
                            taxableBase = taxableBase,
                            taxAmount = taxAmount,
                            grandTotal = discountedSubtotal
                        )
                    }
                }
            }
        }
        
        val totalTaxableBase = itemResults.sumOf { it.taxableBase }
        val totalTaxAmount = itemResults.sumOf { it.taxAmount }
        val netSubtotal = grossSubtotal - safeDiscount
        val grandTotal = netSubtotal + totalTaxAmount
        
        return TransactionTaxResult(
            grossSubtotal = grossSubtotal,
            discountAmount = safeDiscount,
            taxableBase = totalTaxableBase,
            taxAmount = totalTaxAmount,
            grandTotal = grandTotal,
            itemResults = itemResults
        )
    }

    fun validateRate(rate: Double): Double {
        val clamped = rate.coerceIn(0.0, 100.0)
        return if (clamped.isNaN() || clamped.isInfinite()) 0.0 else clamped
    }

    private fun calculateExclusiveItem(
        lineSubtotal: Long,
        rate: Double,
        roundingMode: RoundingMode
    ): TaxCalculationResult {
        val taxableBase = lineSubtotal
        val taxAmount = roundToLong(BigDecimal(taxableBase) * BigDecimal(rate) / BigDecimal(100), roundingMode)
        val grandTotal = taxableBase + taxAmount

        return TaxCalculationResult(
            lineSubtotal = lineSubtotal,
            taxableBase = taxableBase,
            taxAmount = taxAmount,
            grandTotal = grandTotal
        )
    }

    private fun calculateInclusiveItem(
        grossPrice: Long,
        rate: Double,
        roundingMode: RoundingMode
    ): TaxCalculationResult {
        val divisor = BigDecimal(100) + BigDecimal(rate)
        val taxableBase = roundToLong(BigDecimal(grossPrice) * BigDecimal(100) / divisor, roundingMode)
        val taxAmount = grossPrice - taxableBase

        return TaxCalculationResult(
            lineSubtotal = grossPrice,
            taxableBase = taxableBase,
            taxAmount = taxAmount,
            grandTotal = grossPrice
        )
    }

    private fun roundToLong(value: BigDecimal, roundingMode: RoundingMode): Long {
        return value.setScale(0, roundingMode).toLong()
    }
}
