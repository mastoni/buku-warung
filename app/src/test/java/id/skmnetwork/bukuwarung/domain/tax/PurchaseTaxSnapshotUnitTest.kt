package id.skmnetwork.bukuwarung.domain.tax

import id.skmnetwork.bukuwarung.data.local.entity.PurchaseItemEntity
import id.skmnetwork.bukuwarung.data.local.entity.PurchaseTransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PurchaseTaxSnapshotUnitTest {

    @Test
    fun `PurchaseItemEntity defaults to taxable true`() {
        val item = PurchaseItemEntity(
            transactionId = 1L,
            productId = 100L,
            productName = "Test",
            quantity = 1.0,
            purchasePrice = 10000L,
            subtotal = 10000L
        )
        assertTrue(item.taxable)
    }

    @Test
    fun `PurchaseItemEntity can be non-taxable`() {
        val item = PurchaseItemEntity(
            transactionId = 1L,
            productId = 100L,
            productName = "Test",
            quantity = 1.0,
            purchasePrice = 10000L,
            subtotal = 10000L,
            taxable = false
        )
        assertFalse(item.taxable)
    }

    @Test
    fun `PurchaseTransactionEntity tax snapshot defaults to zero`() {
        val tx = PurchaseTransactionEntity(
            transactionNumber = "PUR-001",
            transactionDate = 1000L,
            totalAmount = 10000L
        )
        assertEquals(0L, tx.subtotalAmount)
        assertEquals(0L, tx.taxableBaseSnapshot)
        assertEquals(0.0, tx.taxRateSnapshot, 0.0)
        assertEquals(0L, tx.taxAmountSnapshot)
    }

    @Test
    fun `PurchaseTransactionEntity stores tax snapshot`() {
        val tx = PurchaseTransactionEntity(
            transactionNumber = "PUR-001",
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
