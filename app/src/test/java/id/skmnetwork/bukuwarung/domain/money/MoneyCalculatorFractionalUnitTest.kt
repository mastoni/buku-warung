package id.skmnetwork.bukuwarung.domain.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gate H.2 - the authoritative money rule for fractional quantities.
 *
 * These tests pin the exact contract the whole application now shares, and they fail against the
 * pre-Gate-H.2 implementation `unitPrice * quantity.toLong()`.
 */
class MoneyCalculatorFractionalUnitTest {

    // -----------------------------------------------------------------------
    // The five contract cases from the gate specification.
    // -----------------------------------------------------------------------

    @Test
    fun caseA_twoAndAHalfUnitsAt15000_is37500() {
        assertEquals(37500L, MoneyCalculator.lineSubtotal(15000L, 2.5))
    }

    @Test
    fun caseB_halfUnitAt15000_is7500() {
        // Pre-fix: 15000L * 0.5.toLong() == 15000L * 0 == 0, and the sale then failed
        // the `grossSubtotal > 0` guard outright.
        assertEquals(7500L, MoneyCalculator.lineSubtotal(15000L, 0.5))
    }

    @Test
    fun caseC_oneAndAQuarterUnitsAt15000_is18750() {
        assertEquals(18750L, MoneyCalculator.lineSubtotal(15000L, 1.25))
    }

    @Test
    fun caseD_oneTenthUnitAt15000_is1500() {
        // Guards the BigDecimal(double) trap: the exact binary expansion of 0.1 is
        // 0.1000000000000000055511151231257827..., which would give 1500.0000000000000832.
        assertEquals(1500L, MoneyCalculator.lineSubtotal(15000L, 0.1))
    }

    @Test
    fun caseE_subRupeiahProductIsRoundedHalfUp() {
        // 999 x 0.5 = 499.5 rupiah. Indonesian Rupiah cannot hold a half rupiah, so the
        // documented policy is a single HALF_UP rounding at the rupiah boundary: 500.
        assertEquals(500L, MoneyCalculator.lineSubtotal(999L, 0.5))
        assertEquals("Sub-rupiah policy must be HALF_UP", 500L, MoneyCalculator.lineSubtotal(999L, 0.5))
    }

    // -----------------------------------------------------------------------
    // The rounding policy itself, stated explicitly.
    // -----------------------------------------------------------------------

    @Test
    fun subRupeiahPolicyIsHalfUpAtTheRupeiahBoundary() {
        assertEquals(java.math.RoundingMode.HALF_UP, MoneyCalculator.MONEY_ROUNDING)

        // 0.5 up, 1.5 up, 2.5 up
        assertEquals(1L, MoneyCalculator.lineSubtotal(1L, 0.5))
        assertEquals(2L, MoneyCalculator.lineSubtotal(1L, 1.5))
        assertEquals(3L, MoneyCalculator.lineSubtotal(1L, 2.5))
        // 0.4 down, 0.6 up
        assertEquals(0L, MoneyCalculator.lineSubtotal(1L, 0.4))
        assertEquals(1L, MoneyCalculator.lineSubtotal(1L, 0.6))
    }

    @Test
    fun integralQuantitiesAreUnchangedByTheRounding() {
        // No rounding ever occurs for an integral quantity, so every pre-existing whole-unit
        // behaviour is bit-for-bit identical to the old implementation.
        listOf(1.0, 2.0, 3.0, 7.0, 20.0, 100.0).forEach { qty ->
            assertEquals(
                "Whole-unit quantity $qty must match the legacy long multiplication",
                (15000L * qty.toLong()),
                MoneyCalculator.lineSubtotal(15000L, qty)
            )
        }
    }

    // -----------------------------------------------------------------------
    // Old-code failure proof. These assertions document what the pre-fix code
    // produced and prove the new rule is genuinely different, not coincidentally equal.
    // -----------------------------------------------------------------------

    @Test
    fun oldTruncatingImplementationWasWrongAndTheFixDiffers() {
        // The old implementation, reproduced verbatim.
        fun oldLineSubtotal(unitPrice: Long, quantity: Double): Long = unitPrice * quantity.toLong()

        assertEquals(30000L, oldLineSubtotal(15000L, 2.5))
        assertEquals(37500L, MoneyCalculator.lineSubtotal(15000L, 2.5))
        assertNotEquals(oldLineSubtotal(15000L, 2.5), MoneyCalculator.lineSubtotal(15000L, 2.5))

        assertEquals(0L, oldLineSubtotal(15000L, 0.5))
        assertEquals(7500L, MoneyCalculator.lineSubtotal(15000L, 0.5))
        assertNotEquals(oldLineSubtotal(15000L, 0.5), MoneyCalculator.lineSubtotal(15000L, 0.5))

        assertEquals(15000L, oldLineSubtotal(15000L, 1.25))
        assertEquals(18750L, MoneyCalculator.lineSubtotal(15000L, 1.25))
        assertNotEquals(oldLineSubtotal(15000L, 1.25), MoneyCalculator.lineSubtotal(15000L, 1.25))

        assertEquals(0L, oldLineSubtotal(15000L, 0.1))
        assertEquals(1500L, MoneyCalculator.lineSubtotal(15000L, 0.1))
    }

    @Test
    fun zeroPointFiveUnitsNeverCollapseToZero() {
        // The old code produced 0 for any quantity below 1.0, which made the sale fail the
        // `grossSubtotal > 0` guard and made stock move without any money being recorded.
        listOf(0.1, 0.25, 0.5, 0.75, 0.9).forEach { qty ->
            assertTrue(
                "Quantity $qty must not produce a zero line amount",
                MoneyCalculator.lineSubtotal(15000L, qty) > 0L
            )
        }
    }

    // -----------------------------------------------------------------------
    // Binary floating point must never leak into the money result.
    // -----------------------------------------------------------------------

    @Test
    fun binaryFloatingPointArtifactsAreNotLeaked() {
        // 0.1 + 0.2 == 0.30000000000000004 in binary floating point.
        val accumulated = 0.1 + 0.2
        assertEquals(4500L, MoneyCalculator.lineSubtotal(15000L, accumulated))

        // 0.1 * 3 == 0.30000000000000004 as well.
        assertEquals(4500L, MoneyCalculator.lineSubtotal(15000L, 0.1 * 3))

        // 1.1 as a literal is not exactly 1.1 in binary.
        assertEquals(16500L, MoneyCalculator.lineSubtotal(15000L, 1.1))
        assertEquals(27500L, MoneyCalculator.lineSubtotal(25000L, 1.1))
    }

    @Test
    fun realisticFuelQuantitiesAreExact() {
        // 8.64 liter is used by the FUEL purchase-order fixtures.
        assertEquals(108000L, MoneyCalculator.lineSubtotal(12500L, 8.64))
        assertEquals(250750L, MoneyCalculator.lineSubtotal(10000L, 25.075))
    }

    // -----------------------------------------------------------------------
    // The proportional refund rule.
    // -----------------------------------------------------------------------

    @Test
    fun proportionalShareMatchesTheGateRefundExample() {
        // 5 kg at Rp 15.000 = Rp 75.000; returning 1.5 kg refunds Rp 22.500.
        assertEquals(75000L, MoneyCalculator.lineSubtotal(15000L, 5.0))
        assertEquals(22500L, MoneyCalculator.proportionalShare(75000L, 1.5, 5.0))
    }

    @Test
    fun proportionalShareOldPreviewFormulaWasWrong() {
        // The old preview used `item.price * quantity.toLong()` == 15000L * 1 == 15.000,
        // while the ledger moved 22.500 - a 7.500 discrepancy the merchant never saw.
        fun oldPreviewAmount(price: Long, returnQuantity: Double): Long = price * returnQuantity.toLong()

        assertEquals(15000L, oldPreviewAmount(15000L, 1.5))
        assertEquals(22500L, MoneyCalculator.proportionalShare(75000L, 1.5, 5.0))
        assertNotEquals(oldPreviewAmount(15000L, 1.5), MoneyCalculator.proportionalShare(75000L, 1.5, 5.0))
    }

    @Test
    fun proportionalShareRoundsHalfUpAndHandlesEdges() {
        // 1/3 of 10.000 = 3.333.. -> 3333
        assertEquals(3333L, MoneyCalculator.proportionalShare(10000L, 1.0, 3.0))
        // A full return returns everything, unrounded.
        assertEquals(75000L, MoneyCalculator.proportionalShare(75000L, 5.0, 5.0))
        // Nothing returned returns nothing.
        assertEquals(0L, MoneyCalculator.proportionalShare(75000L, 0.0, 5.0))
    }

    @Test
    fun proportionalShareIsAdditiveUpToTheRoundingPolicy() {
        // Returning 2 kg then 3 kg of a 5 kg sale must refund the full 75.000 in total,
        // with any rounding remainder absorbed on the final slice.
        val first = MoneyCalculator.proportionalShare(75000L, 2.0, 5.0)
        val second = MoneyCalculator.proportionalShare(75000L, 3.0, 5.0)
        assertEquals(75000L, first + second)
    }

    // -----------------------------------------------------------------------
    // Guards and trivial inputs.
    // -----------------------------------------------------------------------

    @Test
    fun zeroPriceOrZeroQuantityYieldsZero() {
        assertEquals(0L, MoneyCalculator.lineSubtotal(0L, 2.5))
        assertEquals(0L, MoneyCalculator.lineSubtotal(15000L, 0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonFiniteQuantityIsRejected() {
        MoneyCalculator.lineSubtotal(15000L, Double.NaN)
    }

    @Test(expected = IllegalArgumentException::class)
    fun infiniteQuantityIsRejected() {
        MoneyCalculator.lineSubtotal(15000L, Double.POSITIVE_INFINITY)
    }

    @Test(expected = IllegalArgumentException::class)
    fun proportionalShareRejectsZeroOriginalQuantity() {
        MoneyCalculator.proportionalShare(75000L, 1.0, 0.0)
    }
}
