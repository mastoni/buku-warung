package id.skmnetwork.bukuwarung.domain.tax

import id.skmnetwork.bukuwarung.domain.tax.TaxPriceMode.EXCLUSIVE
import id.skmnetwork.bukuwarung.domain.tax.TaxPriceMode.INCLUSIVE
import java.math.RoundingMode
import org.junit.Assert.assertEquals
import org.junit.Test

class PurchaseTaxIntegrationUnitTest {

    @Test
    fun `PPN OFF returns zero tax for all purchase items`() {
        val items = listOf(
            PurchaseItemTaxInput(lineSubtotal = 10000, taxable = false),
            PurchaseItemTaxInput(lineSubtotal = 5000, taxable = false)
        )
        val result = TaxCalculator.calculatePurchaseTax(items, discountAmount = 0, rate = 0.0, priceMode = EXCLUSIVE, roundingMode = RoundingMode.HALF_UP)
        assertEquals(0L, result.totalTaxableBase)
        assertEquals(0L, result.totalTaxAmount)
        assertEquals(15000L, result.grandTotal)
    }

    @Test
    fun `PPN ON taxable exclusive mode calculates purchase tax correctly`() {
        val items = listOf(
            PurchaseItemTaxInput(lineSubtotal = 10000, taxable = true)
        )
        val result = TaxCalculator.calculatePurchaseTax(items, discountAmount = 0, rate = 11.0, priceMode = EXCLUSIVE, roundingMode = RoundingMode.HALF_UP)
        assertEquals(10000L, result.totalTaxableBase)
        assertEquals(1100L, result.totalTaxAmount)
        assertEquals(11100L, result.grandTotal)
    }

    @Test
    fun `non-taxable purchase product gets zero tax`() {
        val items = listOf(
            PurchaseItemTaxInput(lineSubtotal = 10000, taxable = false)
        )
        val result = TaxCalculator.calculatePurchaseTax(items, discountAmount = 0, rate = 11.0, priceMode = EXCLUSIVE, roundingMode = RoundingMode.HALF_UP)
        assertEquals(0L, result.totalTaxableBase)
        assertEquals(0L, result.totalTaxAmount)
        assertEquals(10000L, result.grandTotal)
    }

    @Test
    fun `product tax override uses override rate for purchase`() {
        val items = listOf(
            PurchaseItemTaxInput(lineSubtotal = 10000, taxable = true, taxRateOverride = 5.0)
        )
        val result = TaxCalculator.calculatePurchaseTax(items, discountAmount = 0, rate = 11.0, priceMode = EXCLUSIVE, roundingMode = RoundingMode.HALF_UP)
        assertEquals(10000L, result.totalTaxableBase)
        assertEquals(500L, result.totalTaxAmount)
        assertEquals(10500L, result.grandTotal)
    }

    @Test
    fun `mixed taxable and non-taxable purchase items calculate correctly`() {
        val items = listOf(
            PurchaseItemTaxInput(lineSubtotal = 10000, taxable = true),
            PurchaseItemTaxInput(lineSubtotal = 5000, taxable = false),
            PurchaseItemTaxInput(lineSubtotal = 3000, taxable = true)
        )
        val result = TaxCalculator.calculatePurchaseTax(items, discountAmount = 0, rate = 11.0, priceMode = EXCLUSIVE, roundingMode = RoundingMode.HALF_UP)
        assertEquals(13000L, result.totalTaxableBase)
        assertEquals(1430L, result.totalTaxAmount)
        assertEquals(19430L, result.grandTotal)
    }

    @Test
    fun `exclusive mode tax is added to purchase subtotal`() {
        val items = listOf(
            PurchaseItemTaxInput(lineSubtotal = 10000, taxable = true)
        )
        val result = TaxCalculator.calculatePurchaseTax(items, discountAmount = 0, rate = 10.0, priceMode = EXCLUSIVE, roundingMode = RoundingMode.HALF_UP)
        assertEquals(10000L, result.totalTaxableBase)
        assertEquals(1000L, result.totalTaxAmount)
        assertEquals(11000L, result.grandTotal)
    }

    @Test
    fun `inclusive mode tax is extracted from purchase price`() {
        val items = listOf(
            PurchaseItemTaxInput(lineSubtotal = 11000, taxable = true)
        )
        val result = TaxCalculator.calculatePurchaseTax(items, discountAmount = 0, rate = 10.0, priceMode = INCLUSIVE, roundingMode = RoundingMode.HALF_UP)
        assertEquals(10000L, result.totalTaxableBase)
        assertEquals(1000L, result.totalTaxAmount)
        assertEquals(11000L, result.grandTotal)
    }

    @Test
    fun `discount before tax reduces purchase taxable base`() {
        val items = listOf(
            PurchaseItemTaxInput(lineSubtotal = 10000, taxable = true),
            PurchaseItemTaxInput(lineSubtotal = 5000, taxable = true)
        )
        val result = TaxCalculator.calculatePurchaseTax(items, discountAmount = 3000, rate = 10.0, priceMode = EXCLUSIVE, roundingMode = RoundingMode.HALF_UP)
        assertEquals(12000L, result.totalTaxableBase)
        assertEquals(1200L, result.totalTaxAmount)
        assertEquals(13200L, result.grandTotal)
    }

    @Test
    fun `historical purchase unchanged after PPN settings change`() {
        val items = listOf(
            PurchaseItemTaxInput(lineSubtotal = 10000, taxable = true)
        )
        val oldResult = TaxCalculator.calculatePurchaseTax(items, discountAmount = 0, rate = 0.0, priceMode = EXCLUSIVE, roundingMode = RoundingMode.HALF_UP)
        assertEquals(0L, oldResult.totalTaxAmount)
        assertEquals(10000L, oldResult.grandTotal)

        val newResult = TaxCalculator.calculatePurchaseTax(items, discountAmount = 0, rate = 11.0, priceMode = EXCLUSIVE, roundingMode = RoundingMode.HALF_UP)
        assertEquals(1100L, newResult.totalTaxAmount)
        assertEquals(11100L, newResult.grandTotal)
    }

    @Test
    fun `zero rate produces zero tax for purchase`() {
        val items = listOf(
            PurchaseItemTaxInput(lineSubtotal = 10000, taxable = true)
        )
        val result = TaxCalculator.calculatePurchaseTax(items, discountAmount = 0, rate = 0.0, priceMode = EXCLUSIVE, roundingMode = RoundingMode.HALF_UP)
        assertEquals(0L, result.totalTaxableBase)
        assertEquals(0L, result.totalTaxAmount)
        assertEquals(10000L, result.grandTotal)
    }

    @Test
    fun `multiple overrides in same purchase transaction`() {
        val items = listOf(
            PurchaseItemTaxInput(lineSubtotal = 10000, taxable = true, taxRateOverride = null),
            PurchaseItemTaxInput(lineSubtotal = 5000, taxable = true, taxRateOverride = 5.0),
            PurchaseItemTaxInput(lineSubtotal = 3000, taxable = false)
        )
        val result = TaxCalculator.calculatePurchaseTax(items, discountAmount = 0, rate = 11.0, priceMode = EXCLUSIVE, roundingMode = RoundingMode.HALF_UP)
        assertEquals(15000L, result.totalTaxableBase)
        assertEquals(1350L, result.totalTaxAmount)
        assertEquals(19350L, result.grandTotal)
    }
}
