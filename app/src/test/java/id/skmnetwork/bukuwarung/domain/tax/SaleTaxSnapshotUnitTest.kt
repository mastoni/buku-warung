package id.skmnetwork.bukuwarung.domain.tax

import id.skmnetwork.bukuwarung.data.local.entity.SaleItemEntity
import id.skmnetwork.bukuwarung.data.local.entity.SaleTransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SaleTaxSnapshotUnitTest {

    @Test
    fun `SaleItemEntity defaults to taxable true`() {
        val item = SaleItemEntity(
            transactionId = 1L,
            productId = 100L,
            productName = "Test",
            quantity = 1.0,
            price = 10000L,
            subtotal = 10000L
        )
        assertTrue(item.taxable)
    }

    @Test
    fun `SaleItemEntity can be non-taxable`() {
        val item = SaleItemEntity(
            transactionId = 1L,
            productId = 100L,
            productName = "Test",
            quantity = 1.0,
            price = 10000L,
            subtotal = 10000L,
            taxable = false
        )
        assertFalse(item.taxable)
    }

    @Test
    fun `SaleTransactionEntity tax snapshot defaults to zero`() {
        val tx = SaleTransactionEntity(
            transactionNumber = "TRX-001",
            transactionDate = 1000L,
            totalAmount = 10000L
        )
        assertEquals(0L, tx.subtotalAmount)
        assertEquals(0L, tx.taxableBaseSnapshot)
        assertEquals(0.0, tx.taxRateSnapshot, 0.0)
        assertEquals(0L, tx.taxAmountSnapshot)
    }

    @Test
    fun `SaleTransactionEntity stores tax snapshot`() {
        val tx = SaleTransactionEntity(
            transactionNumber = "TRX-001",
            transactionDate = 1000L,
            totalAmount = 11100L,
            subtotalAmount = 10000L,
            taxableBaseSnapshot = 10000L,
            taxRateSnapshot = 11.0,
            taxAmountSnapshot = 1100L
        )
        assertEquals(10000L, tx.subtotalAmount)
        assertEquals(10000L, tx.taxableBaseSnapshot)
        assertEquals(11.0, tx.taxRateSnapshot, 0.0)
        assertEquals(1100L, tx.taxAmountSnapshot)
    }
}
