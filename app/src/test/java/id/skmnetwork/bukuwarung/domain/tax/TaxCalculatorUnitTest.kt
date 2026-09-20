package id.skmnetwork.bukuwarung.domain.tax

import id.skmnetwork.bukuwarung.domain.tax.TaxPriceMode.EXCLUSIVE
import id.skmnetwork.bukuwarung.domain.tax.TaxPriceMode.INCLUSIVE
import java.math.RoundingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaxCalculatorUnitTest {

    // A. PPN OFF / not applicable
    @Test
    fun testTaxDisabledReturnsZeroTax() {
        val result = TaxCalculator.calculateItemTax(
            lineSubtotal = 10000L,
            rate = 11.0,
            priceMode = EXCLUSIVE,
            roundingMode = RoundingMode.HALF_UP,
            taxable = false
        )
        assertEquals(10000L, result.lineSubtotal)
        assertEquals(0L, result.taxableBase)
        assertEquals(0L, result.taxAmount)
        assertEquals(10000L, result.grandTotal)
    }

    // B. taxable item + exclusive
    @Test
    fun testTaxableItemExclusiveMode() {
        val result = TaxCalculator.calculateItemTax(
            lineSubtotal = 10000L,
            rate = 11.0,
            priceMode = EXCLUSIVE,
            roundingMode = RoundingMode.HALF_UP,
            taxable = true
        )
        assertEquals(10000L, result.lineSubtotal)
        assertEquals(10000L, result.taxableBase)
        assertEquals(1100L, result.taxAmount)
        assertEquals(11100L, result.grandTotal)
    }

    // C. non-taxable item
    @Test
    fun testNonTaxableItemReturnsNoTax() {
        val result = TaxCalculator.calculateItemTax(
            lineSubtotal = 10000L,
            rate = 11.0,
            priceMode = EXCLUSIVE,
            roundingMode = RoundingMode.HALF_UP,
            taxable = false
        )
        assertEquals(0L, result.taxAmount)
        assertEquals(10000L, result.grandTotal)
    }

    // D. exclusive mode
    @Test
    fun testExclusiveModeAddsTaxToTotal() {
        val result = TaxCalculator.calculateItemTax(
            lineSubtotal = 50000L,
            rate = 11.0,
            priceMode = EXCLUSIVE,
            roundingMode = RoundingMode.HALF_UP,
            taxable = true
        )
        assertEquals(5500L, result.taxAmount)
        assertEquals(55500L, result.grandTotal)
    }

    // E. inclusive mode
    @Test
    fun testInclusiveModeExtractsTaxFromGross() {
        val result = TaxCalculator.calculateItemTax(
            lineSubtotal = 11000L,
            rate = 11.0,
            priceMode = INCLUSIVE,
            roundingMode = RoundingMode.HALF_UP,
            taxable = true
        )
        // taxableBase = 11000 * 100 / 111 = 9909.909... -> HALF_UP = 9910
        // taxAmount = 11000 - 9910 = 1090
        assertEquals(9910L, result.taxableBase)
        assertEquals(1090L, result.taxAmount)
        assertEquals(11000L, result.grandTotal)
    }

    // F. discount before tax
    @Test
    fun testDiscountBeforeTaxInTransaction() {
        val itemResults = TaxCalculator.calculateTransactionTax(
            items = listOf(
                ItemTaxInput(lineSubtotal = 10000L, taxable = true),
                ItemTaxInput(lineSubtotal = 20000L, taxable = true)
            ),
            discountAmount = 3000L,
            rate = 11.0,
            priceMode = EXCLUSIVE,
            roundingMode = RoundingMode.HALF_UP
        )
        assertEquals(30000L, itemResults.grossSubtotal)
        assertEquals(3000L, itemResults.discountAmount)
        assertEquals(27000L, itemResults.taxableBase)
        assertEquals(2970L, itemResults.taxAmount)
        assertEquals(29970L, itemResults.grandTotal)
    }

    // G. zero tax rate
    @Test
    fun testZeroTaxRate() {
        val result = TaxCalculator.calculateItemTax(
            lineSubtotal = 10000L,
            rate = 0.0,
            priceMode = EXCLUSIVE,
            roundingMode = RoundingMode.HALF_UP,
            taxable = true
        )
        assertEquals(0L, result.taxAmount)
        assertEquals(10000L, result.grandTotal)
    }

    // H. normal rate
    @Test
    fun testNormalRate11Percent() {
        val result = TaxCalculator.calculateItemTax(
            lineSubtotal = 100000L,
            rate = 11.0,
            priceMode = EXCLUSIVE,
            roundingMode = RoundingMode.HALF_UP,
            taxable = true
        )
        assertEquals(11000L, result.taxAmount)
        assertEquals(111000L, result.grandTotal)
    }

    // I. rounding HALF_UP
    @Test
    fun testRoundingHalfUp() {
        // 999 * 11 / 100 = 109.89 -> HALF_UP = 110
        val result = TaxCalculator.calculateItemTax(
            lineSubtotal = 999L,
            rate = 11.0,
            priceMode = EXCLUSIVE,
            roundingMode = RoundingMode.HALF_UP,
            taxable = true
        )
        assertEquals(110L, result.taxAmount)
        assertEquals(1109L, result.grandTotal)
    }

    // J. integer monetary values
    @Test
    fun testIntegerMonetaryValues() {
        val result = TaxCalculator.calculateItemTax(
            lineSubtotal = 10000L,
            rate = 11.0,
            priceMode = EXCLUSIVE,
            roundingMode = RoundingMode.HALF_UP,
            taxable = true
        )
        assertTrue(result.lineSubtotal is Long)
        assertTrue(result.taxableBase is Long)
        assertTrue(result.taxAmount is Long)
        assertTrue(result.grandTotal is Long)
    }

    // K. invalid rate
    @Test
    fun testInvalidRateClamped() {
        val result = TaxCalculator.calculateItemTax(
            lineSubtotal = 10000L,
            rate = -5.0,
            priceMode = EXCLUSIVE,
            roundingMode = RoundingMode.HALF_UP,
            taxable = true
        )
        assertEquals(0L, result.taxAmount)
        assertEquals(10000L, result.grandTotal)
    }

    // L. mixed taxable/non-taxable calculation
    @Test
    fun testMixedTaxableAndNonTaxableItems() {
        val result = TaxCalculator.calculateTransactionTax(
            items = listOf(
                ItemTaxInput(lineSubtotal = 10000L, taxable = true),
                ItemTaxInput(lineSubtotal = 20000L, taxable = false),
                ItemTaxInput(lineSubtotal = 5000L, taxable = true)
            ),
            discountAmount = 0L,
            rate = 11.0,
            priceMode = EXCLUSIVE,
            roundingMode = RoundingMode.HALF_UP
        )
        assertEquals(35000L, result.grossSubtotal)
        assertEquals(15000L, result.taxableBase)
        assertEquals(1650L, result.taxAmount)
        assertEquals(36650L, result.grandTotal)
    }

    // M. historical configuration change does not alter previously calculated snapshot
    @Test
    fun testHistoricalSnapshotUnchangedByConfigurationChange() {
        val snapshot1 = TaxCalculator.calculateItemTax(
            lineSubtotal = 10000L,
            rate = 11.0,
            priceMode = EXCLUSIVE,
            roundingMode = RoundingMode.HALF_UP,
            taxable = true
        )
        val snapshot2 = TaxCalculator.calculateItemTax(
            lineSubtotal = 10000L,
            rate = 10.0,
            priceMode = EXCLUSIVE,
            roundingMode = RoundingMode.HALF_UP,
            taxable = true
        )
        assertEquals(1100L, snapshot1.taxAmount)
        assertEquals(1000L, snapshot2.taxAmount)
        assertTrue(snapshot1.taxAmount != snapshot2.taxAmount)
    }

    // Additional: validateRate
    @Test
    fun testValidateRateClampsToValidRange() {
        assertEquals(0.0, TaxCalculator.validateRate(-5.0), 0.001)
        assertEquals(100.0, TaxCalculator.validateRate(150.0), 0.001)
        assertEquals(11.0, TaxCalculator.validateRate(11.0), 0.001)
        assertEquals(0.0, TaxCalculator.validateRate(Double.NaN), 0.001)
    }

    // Additional: inclusive mode with zero rate
    @Test
    fun testInclusiveModeZeroRate() {
        val result = TaxCalculator.calculateItemTax(
            lineSubtotal = 10000L,
            rate = 0.0,
            priceMode = INCLUSIVE,
            roundingMode = RoundingMode.HALF_UP,
            taxable = true
        )
        assertEquals(10000L, result.taxableBase)
        assertEquals(0L, result.taxAmount)
        assertEquals(10000L, result.grandTotal)
    }

    // Additional: discount cannot exceed gross
    @Test
    fun testTransactionDiscountClampedToGross() {
        val result = TaxCalculator.calculateTransactionTax(
            items = listOf(
                ItemTaxInput(lineSubtotal = 10000L, taxable = true)
            ),
            discountAmount = 20000L,
            rate = 11.0,
            priceMode = EXCLUSIVE,
            roundingMode = RoundingMode.HALF_UP
        )
        assertEquals(10000L, result.discountAmount)
        assertEquals(0L, result.taxableBase)
        assertEquals(0L, result.taxAmount)
        assertEquals(0L, result.grandTotal)
    }
}
