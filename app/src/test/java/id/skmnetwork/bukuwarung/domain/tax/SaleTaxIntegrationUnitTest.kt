package id.skmnetwork.bukuwarung.domain.tax

import id.skmnetwork.bukuwarung.domain.tax.TaxPriceMode.EXCLUSIVE
import id.skmnetwork.bukuwarung.domain.tax.TaxPriceMode.INCLUSIVE
import java.math.RoundingMode
import org.junit.Assert.assertEquals
import org.junit.Test

class SaleTaxIntegrationUnitTest {

    @Test
    fun `PPN OFF returns zero tax for all items`() {
        val items = listOf(
            SaleItemTaxInput(lineSubtotal = 10000, taxable = false),
            SaleItemTaxInput(lineSubtotal = 5000, taxable = false)
        )
        val result = TaxCalculator.calculateSaleTax(items, discountAmount = 0, rate = 0.0, priceMode = EXCLUSIVE, roundingMode = RoundingMode.HALF_UP)
        assertEquals(0L, result.totalTaxableBase)
        assertEquals(0L, result.totalTaxAmount)
        assertEquals(15000L, result.grandTotal)
    }

    @Test
    fun `PPN ON taxable exclusive mode calculates tax correctly`() {
        val items = listOf(
            SaleItemTaxInput(lineSubtotal = 10000, taxable = true)
        )
        val result = TaxCalculator.calculateSaleTax(items, discountAmount = 0, rate = 11.0, priceMode = EXCLUSIVE, roundingMode = RoundingMode.HALF_UP)
        assertEquals(10000L, result.totalTaxableBase)
        assertEquals(1100L, result.totalTaxAmount)
        assertEquals(11100L, result.grandTotal)
    }

    @Test
    fun `non-taxable product gets zero tax`() {
        val items = listOf(
            SaleItemTaxInput(lineSubtotal = 10000, taxable = false)
        )
        val result = TaxCalculator.calculateSaleTax(items, discountAmount = 0, rate = 11.0, priceMode = EXCLUSIVE, roundingMode = RoundingMode.HALF_UP)
        assertEquals(0L, result.totalTaxableBase)
        assertEquals(0L, result.totalTaxAmount)
        assertEquals(10000L, result.grandTotal)
    }

    @Test
    fun `product tax override uses override rate`() {
        val items = listOf(
            SaleItemTaxInput(lineSubtotal = 10000, taxable = true, taxRateOverride = 5.0)
        )
        val result = TaxCalculator.calculateSaleTax(items, discountAmount = 0, rate = 11.0, priceMode = EXCLUSIVE, roundingMode = RoundingMode.HALF_UP)
        assertEquals(10000L, result.totalTaxableBase)
        assertEquals(500L, result.totalTaxAmount)
        assertEquals(10500L, result.grandTotal)
    }

    @Test
    fun `mixed taxable and non-taxable items calculate correctly`() {
        val items = listOf(
            SaleItemTaxInput(lineSubtotal = 10000, taxable = true),
            SaleItemTaxInput(lineSubtotal = 5000, taxable = false),
            SaleItemTaxInput(lineSubtotal = 3000, taxable = true)
        )
        val result = TaxCalculator.calculateSaleTax(items, discountAmount = 0, rate = 11.0, priceMode = EXCLUSIVE, roundingMode = RoundingMode.HALF_UP)
        assertEquals(13000L, result.totalTaxableBase)
        assertEquals(1430L, result.totalTaxAmount)
        assertEquals(19430L, result.grandTotal)
    }

    @Test
    fun `exclusive mode tax is added to subtotal`() {
        val items = listOf(
            SaleItemTaxInput(lineSubtotal = 10000, taxable = true)
        )
        val result = TaxCalculator.calculateSaleTax(items, discountAmount = 0, rate = 10.0, priceMode = EXCLUSIVE, roundingMode = RoundingMode.HALF_UP)
        assertEquals(10000L, result.totalTaxableBase)
        assertEquals(1000L, result.totalTaxAmount)
        assertEquals(11000L, result.grandTotal)
    }

    @Test
    fun `inclusive mode tax is extracted from price`() {
        val items = listOf(
            SaleItemTaxInput(lineSubtotal = 11000, taxable = true)
        )
        val result = TaxCalculator.calculateSaleTax(items, discountAmount = 0, rate = 10.0, priceMode = INCLUSIVE, roundingMode = RoundingMode.HALF_UP)
        assertEquals(10000L, result.totalTaxableBase)
        assertEquals(1000L, result.totalTaxAmount)
        assertEquals(11000L, result.grandTotal)
    }

    @Test
    fun `discount before tax reduces taxable base`() {
        val items = listOf(
            SaleItemTaxInput(lineSubtotal = 10000, taxable = true),
            SaleItemTaxInput(lineSubtotal = 5000, taxable = true)
        )
        val result = TaxCalculator.calculateSaleTax(items, discountAmount = 3000, rate = 10.0, priceMode = EXCLUSIVE, roundingMode = RoundingMode.HALF_UP)
        assertEquals(12000L, result.totalTaxableBase)
        assertEquals(1200L, result.totalTaxAmount)
        assertEquals(13200L, result.grandTotal)
    }

    @Test
    fun `historical sale unchanged after PPN settings change`() {
        val items = listOf(
            SaleItemTaxInput(lineSubtotal = 10000, taxable = true)
        )
        val oldResult = TaxCalculator.calculateSaleTax(items, discountAmount = 0, rate = 0.0, priceMode = EXCLUSIVE, roundingMode = RoundingMode.HALF_UP)
        assertEquals(0L, oldResult.totalTaxAmount)
        assertEquals(10000L, oldResult.grandTotal)

        val newResult = TaxCalculator.calculateSaleTax(items, discountAmount = 0, rate = 11.0, priceMode = EXCLUSIVE, roundingMode = RoundingMode.HALF_UP)
        assertEquals(1100L, newResult.totalTaxAmount)
        assertEquals(11100L, newResult.grandTotal)
    }

    @Test
    fun `rounding HALF_UP is consistent across items and transaction`() {
        val items = listOf(
            SaleItemTaxInput(lineSubtotal = 3333, taxable = true),
            SaleItemTaxInput(lineSubtotal = 3333, taxable = true),
            SaleItemTaxInput(lineSubtotal = 3334, taxable = true)
        )
        val result = TaxCalculator.calculateSaleTax(items, discountAmount = 0, rate = 10.0, priceMode = EXCLUSIVE, roundingMode = RoundingMode.HALF_UP)
        assertEquals(10000L, result.totalTaxableBase)
        assertEquals(999L, result.totalTaxAmount)
        assertEquals(10999L, result.grandTotal)
    }

    @Test
    fun `zero rate produces zero tax`() {
        val items = listOf(
            SaleItemTaxInput(lineSubtotal = 10000, taxable = true)
        )
        val result = TaxCalculator.calculateSaleTax(items, discountAmount = 0, rate = 0.0, priceMode = EXCLUSIVE, roundingMode = RoundingMode.HALF_UP)
        assertEquals(0L, result.totalTaxableBase)
        assertEquals(0L, result.totalTaxAmount)
        assertEquals(10000L, result.grandTotal)
    }

    @Test
    fun `multiple overrides in same transaction`() {
        val items = listOf(
            SaleItemTaxInput(lineSubtotal = 10000, taxable = true, taxRateOverride = null),
            SaleItemTaxInput(lineSubtotal = 5000, taxable = true, taxRateOverride = 5.0),
            SaleItemTaxInput(lineSubtotal = 3000, taxable = false)
        )
        val result = TaxCalculator.calculateSaleTax(items, discountAmount = 0, rate = 11.0, priceMode = EXCLUSIVE, roundingMode = RoundingMode.HALF_UP)
        assertEquals(15000L, result.totalTaxableBase)
        assertEquals(1350L, result.totalTaxAmount)
        assertEquals(19350L, result.grandTotal)
    }
}
