package id.skmnetwork.bukuwarung.domain.discount

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscountCalculatorUnitTest {

    /**
     * Test 1: Fixed Discount
     * 100.000 - 5.000 = 95.000
     */
    @Test
    fun testFixedDiscount() {
        val gross = 100000L
        val fixedDiscount = 5000L
        val result = DiscountCalculator.calculateFixed(gross, fixedDiscount)

        assertEquals(100000L, result.grossSubtotal)
        assertEquals(5000L, result.discountAmount)
        assertEquals(95000L, result.netTotal)
    }

    /**
     * Test 2: Percentage Discount
     * 100.000 - 10% = 90.000
     */
    @Test
    fun testPercentageDiscount() {
        val gross = 100000L
        val percentage = 10.0
        val result = DiscountCalculator.calculatePercentage(gross, percentage)

        assertEquals(100000L, result.grossSubtotal)
        assertEquals(10000L, result.discountAmount)
        assertEquals(90000L, result.netTotal)
        assertEquals(10.0, result.discountPercentage ?: 0.0, 0.001)
    }

    /**
     * Test 3: Zero Discount
     * 100.000 - 0 = 100.000
     */
    @Test
    fun testZeroDiscount() {
        val gross = 100000L
        val resultFixed = DiscountCalculator.calculateFixed(gross, 0L)
        val resultPercent = DiscountCalculator.calculatePercentage(gross, 0.0)

        assertEquals(100000L, resultFixed.grossSubtotal)
        assertEquals(0L, resultFixed.discountAmount)
        assertEquals(100000L, resultFixed.netTotal)

        assertEquals(100000L, resultPercent.grossSubtotal)
        assertEquals(0L, resultPercent.discountAmount)
        assertEquals(100000L, resultPercent.netTotal)
    }

    /**
     * Test 4: Full Discount (100% or equal fixed amount)
     * 100.000 - 100% = 0
     */
    @Test
    fun testFullDiscount() {
        val gross = 100000L
        val resultFixed = DiscountCalculator.calculateFixed(gross, 100000L)
        val resultPercent = DiscountCalculator.calculatePercentage(gross, 100.0)

        assertEquals(100000L, resultFixed.discountAmount)
        assertEquals(0L, resultFixed.netTotal)

        assertEquals(100000L, resultPercent.discountAmount)
        assertEquals(0L, resultPercent.netTotal)
    }

    /**
     * Test 5: Over Discount (Clamping)
     * Fixed discount > grossSubtotal -> clamped to grossSubtotal, netTotal = 0
     * Percentage > 100% -> clamped to 100%, netTotal = 0
     */
    @Test
    fun testOverDiscountClamping() {
        val gross = 100000L
        val resultFixed = DiscountCalculator.calculateFixed(gross, 150000L)
        val resultPercent = DiscountCalculator.calculatePercentage(gross, 150.0)

        assertEquals(100000L, resultFixed.discountAmount)
        assertEquals(0L, resultFixed.netTotal)

        assertEquals(100000L, resultPercent.discountAmount)
        assertEquals(0L, resultPercent.netTotal)
    }

    /**
     * Test 6: Negative Discount Input
     * Negative fixed or percentage -> clamped to 0
     */
    @Test
    fun testNegativeDiscountInput() {
        val gross = 50000L
        val resultFixed = DiscountCalculator.calculateFixed(gross, -5000L)
        val resultPercent = DiscountCalculator.calculatePercentage(gross, -10.0)

        assertEquals(0L, resultFixed.discountAmount)
        assertEquals(50000L, resultFixed.netTotal)

        assertEquals(0L, resultPercent.discountAmount)
        assertEquals(50000L, resultPercent.netTotal)
    }

    /**
     * Test 7: Percentage Floor Rounding for Integer Rupiah
     * Gross = 15.750, 15% discount:
     * 15750 * 0.15 = 2362.5 -> floor = 2362
     * Net = 15750 - 2362 = 13388
     */
    @Test
    fun testPercentageFloorRounding() {
        val gross = 15750L
        val percentage = 15.0
        val result = DiscountCalculator.calculatePercentage(gross, percentage)

        assertEquals(2362L, result.discountAmount)
        assertEquals(13388L, result.netTotal)
        assertTrue(result.netTotal >= 0L)
    }

    /**
     * Test 8: Zero Gross Subtotal
     */
    @Test
    fun testZeroGrossSubtotal() {
        val result = DiscountCalculator.calculateFixed(0L, 5000L)
        assertEquals(0L, result.grossSubtotal)
        assertEquals(0L, result.discountAmount)
        assertEquals(0L, result.netTotal)
    }
}
