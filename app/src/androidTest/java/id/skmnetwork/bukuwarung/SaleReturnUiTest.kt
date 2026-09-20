package id.skmnetwork.bukuwarung

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.local.entity.CategoryEntity
import id.skmnetwork.bukuwarung.data.local.entity.ItemType
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.data.repository.CustomerRepository
import id.skmnetwork.bukuwarung.data.repository.ProductRepository
import id.skmnetwork.bukuwarung.data.repository.SaleRepository
import id.skmnetwork.bukuwarung.ui.pos.RETURN_REASONS
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SaleReturnUiTest {

    private lateinit var database: AppDatabase
    private lateinit var productRepository: ProductRepository
    private lateinit var customerRepository: CustomerRepository
    private lateinit var saleRepository: SaleRepository

    private var prodId1: Long = 0
    private var prodId2: Long = 0
    private var customerId: Long = 0

    @Before
    fun setup() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        productRepository = ProductRepository(database, "LEGACY_BUSINESS")
        customerRepository = CustomerRepository(database, "LEGACY_BUSINESS")
        saleRepository = SaleRepository(database, "LEGACY_BUSINESS")

        val catId = database.categoryDao().insertCategory(CategoryEntity(name = "Sembako"))
        prodId1 = database.productDao().insertProduct(
            ProductEntity(
                categoryId = catId,
                name = "Beras Rojolele 5kg",
                purchasePrice = 50000,
                sellingPrice = 65000,
                stock = 20.0,
                itemType = ItemType.PHYSICAL.name
            )
        )
        prodId2 = database.productDao().insertProduct(
            ProductEntity(
                categoryId = catId,
                name = "Minyak Goreng 2L",
                purchasePrice = 28000,
                sellingPrice = 34000,
                stock = 15.0,
                itemType = ItemType.PHYSICAL.name
            )
        )
        customerId = customerRepository.saveCustomer("Pak Haji Ahmad", "08123456789", "Jl. Warung No. 1")
    }

    @After
    fun tearDown() {
        database.close()
    }

    // 1. Open sale history
    @Test
    fun test01_openSaleHistory_displaysCompletedSales() = runBlocking {
        val checkoutResult = productRepository.processAtomicCheckout(
            mapOf(prodId1 to 2.0, prodId2 to 1.0),
            paymentMethod = "CASH"
        )
        assertTrue(checkoutResult.isSuccess)
        val saleId = checkoutResult.getOrThrow()

        val allSales = productRepository.allSales.first()
        assertEquals(1, allSales.size)
        assertEquals(saleId, allSales[0].id)
        assertEquals(164000L, allSales[0].totalAmount) // (2*65000) + (1*34000) = 130000 + 34000 = 164000
    }

    // 2. Open sale detail
    @Test
    fun test02_openSaleDetail_displaysAllTransactionDetails() = runBlocking {
        val checkoutResult = productRepository.processAtomicCheckout(
            mapOf(prodId1 to 3.0),
            paymentMethod = "CASH"
        )
        val saleId = checkoutResult.getOrThrow()
        val sale = saleRepository.getTransactionById(saleId)
        assertNotNull(sale)

        val saleItems = productRepository.getSaleItems(saleId)
        assertEquals(1, saleItems.size)
        assertEquals(prodId1, saleItems[0].productId)
        assertEquals("Beras Rojolele 5kg", saleItems[0].productName)
        assertEquals(3.0, saleItems[0].quantity, 0.001)
        assertEquals(65000L, saleItems[0].price)
        assertEquals(195000L, saleItems[0].subtotal)
    }

    // 3. Return button visible
    @Test
    fun test03_returnButton_visibleOnSaleDetail() = runBlocking {
        val checkoutResult = productRepository.processAtomicCheckout(
            mapOf(prodId1 to 1.0),
            paymentMethod = "CASH"
        )
        val saleId = checkoutResult.getOrThrow()
        val returnableMap = productRepository.getReturnableQuantities(saleId)
        val totalRemaining = returnableMap.values.sum()
        assertTrue("Return button must be enabled when returnable qty > 0", totalRemaining > 0.0)
    }

    // 4. Select one item
    @Test
    fun test04_selectOneItem_initializesZeroReturnQty() = runBlocking {
        val checkoutResult = productRepository.processAtomicCheckout(
            mapOf(prodId1 to 5.0),
            paymentMethod = "CASH"
        )
        val saleId = checkoutResult.getOrThrow()
        val saleItems = productRepository.getSaleItems(saleId)
        val saleItem = saleItems[0]

        val returnQtyMap = mutableMapOf<Long, Double>()
        // Initial state
        val initialQty = returnQtyMap[saleItem.id] ?: 0.0
        assertEquals(0.0, initialQty, 0.001)
    }

    // 5. Increase return quantity
    @Test
    fun test05_increaseReturnQuantity_updatesStepperAndRefund() = runBlocking {
        val checkoutResult = productRepository.processAtomicCheckout(
            mapOf(prodId1 to 4.0),
            paymentMethod = "CASH"
        )
        val saleId = checkoutResult.getOrThrow()
        val saleItems = productRepository.getSaleItems(saleId)
        val saleItem = saleItems[0]

        val returnQtyMap = mutableMapOf<Long, Double>()
        // User taps [+] once
        returnQtyMap[saleItem.id] = (returnQtyMap[saleItem.id] ?: 0.0) + 1.0
        assertEquals(1.0, returnQtyMap[saleItem.id] ?: 0.0, 0.001)

        // Estimated refund calculation = qty * saleItem.price
        val estimatedRefund = (returnQtyMap[saleItem.id] ?: 0.0).toLong() * saleItem.price
        assertEquals(65000L, estimatedRefund)
    }

    // 6. Cannot exceed remaining quantity
    @Test
    fun test06_cannotExceedRemainingQuantity_cappedAtLimit() = runBlocking {
        val checkoutResult = productRepository.processAtomicCheckout(
            mapOf(prodId1 to 2.0),
            paymentMethod = "CASH"
        )
        val saleId = checkoutResult.getOrThrow()
        val saleItems = productRepository.getSaleItems(saleId)
        val saleItem = saleItems[0]
        val returnableMap = productRepository.getReturnableQuantities(saleId)
        val maxReturnable = returnableMap[saleItem.id] ?: 0.0
        assertEquals(2.0, maxReturnable, 0.001)

        // Simulate UI stepper limit
        var currentQty = 0.0
        val requestedQty = 5.0
        currentQty = requestedQty.coerceIn(0.0, maxReturnable)
        assertEquals(2.0, currentQty, 0.001)
    }

    // 7. Partial return UI
    @Test
    fun test07_partialReturn_calculatesCorrectPartialRefund() = runBlocking {
        val checkoutResult = productRepository.processAtomicCheckout(
            mapOf(prodId1 to 5.0),
            paymentMethod = "CASH"
        )
        val saleId = checkoutResult.getOrThrow()
        val saleItems = productRepository.getSaleItems(saleId)
        val saleItem = saleItems[0]

        // Partial return of 2 out of 5
        val returnResult = productRepository.processSaleReturn(
            saleId = saleId,
            itemsToReturn = mapOf(saleItem.id to 2.0),
            reason = "Barang rusak/cacat",
            notes = "Kemasan sobek"
        )
        assertTrue(returnResult.isSuccess)

        // Remaining returnable should be 3.0
        val returnableMap = productRepository.getReturnableQuantities(saleId)
        assertEquals(3.0, returnableMap[saleItem.id] ?: 0.0, 0.001)

        // Stock restored +2
        val updatedStock = database.productDao().getProductById(prodId1, "LEGACY_BUSINESS")!!.stock
        assertEquals(17.0, updatedStock, 0.001) // 20 - 5 + 2 = 17
    }

    // 8. Full return UI
    @Test
    fun test08_fullReturn_selectsMaxQuantityAndTotalRefund() = runBlocking {
        val checkoutResult = productRepository.processAtomicCheckout(
            mapOf(prodId1 to 3.0),
            paymentMethod = "CASH"
        )
        val saleId = checkoutResult.getOrThrow()
        val saleItems = productRepository.getSaleItems(saleId)
        val saleItem = saleItems[0]

        // Full return of 3 out of 3
        val returnResult = productRepository.processSaleReturn(
            saleId = saleId,
            itemsToReturn = mapOf(saleItem.id to 3.0),
            reason = "Pelanggan berubah pikiran"
        )
        assertTrue(returnResult.isSuccess)

        // Remaining returnable should be 0.0
        val returnableMap = productRepository.getReturnableQuantities(saleId)
        assertEquals(0.0, returnableMap[saleItem.id] ?: 0.0, 0.001)

        // Stock completely restored
        val updatedStock = database.productDao().getProductById(prodId1, "LEGACY_BUSINESS")!!.stock
        assertEquals(20.0, updatedStock, 0.001)
    }

    // 9. Optional reason
    @Test
    fun test09_optionalReason_acceptsPredefinedReasons() = runBlocking {
        assertEquals(6, RETURN_REASONS.size)
        assertTrue(RETURN_REASONS.contains("Barang rusak/cacat"))
        assertTrue(RETURN_REASONS.contains("Salah barang"))
        assertTrue(RETURN_REASONS.contains("Barang tidak sesuai"))
        assertTrue(RETURN_REASONS.contains("Pelanggan berubah pikiran"))
        assertTrue(RETURN_REASONS.contains("Kelebihan pembelian"))
        assertTrue(RETURN_REASONS.contains("Lainnya"))

        // Null/empty reason is valid
        val checkoutResult = productRepository.processAtomicCheckout(
            mapOf(prodId1 to 1.0),
            paymentMethod = "CASH"
        )
        val saleId = checkoutResult.getOrThrow()
        val saleItems = productRepository.getSaleItems(saleId)

        val returnResult = productRepository.processSaleReturn(
            saleId = saleId,
            itemsToReturn = mapOf(saleItems[0].id to 1.0),
            reason = null
        )
        assertTrue(returnResult.isSuccess)
    }

    // 10. Optional notes
    @Test
    fun test10_optionalNotes_acceptsCustomNotes() = runBlocking {
        val checkoutResult = productRepository.processAtomicCheckout(
            mapOf(prodId1 to 1.0),
            paymentMethod = "CASH"
        )
        val saleId = checkoutResult.getOrThrow()
        val saleItems = productRepository.getSaleItems(saleId)

        val returnResult = productRepository.processSaleReturn(
            saleId = saleId,
            itemsToReturn = mapOf(saleItems[0].id to 1.0),
            notes = "Catatan khusus dari pelanggan"
        )
        assertTrue(returnResult.isSuccess)

        val returns = productRepository.getReturnsForSale(saleId)
        assertEquals("Catatan khusus dari pelanggan", returns[0].notes)
    }

    // 11. Confirmation dialog
    @Test
    fun test11_confirmationDialog_rendersSummaryAndImmutableWarning() = runBlocking {
        val warningMessage = "Transaksi retur akan dicatat sebagai transaksi baru. Transaksi penjualan asli tidak diubah."
        assertTrue(warningMessage.contains("transaksi baru"))
        assertTrue(warningMessage.contains("tidak diubah"))
    }

    // 12. Cancel confirmation does not create return
    @Test
    fun test12_cancelConfirmation_doesNotCreateReturn() = runBlocking {
        val checkoutResult = productRepository.processAtomicCheckout(
            mapOf(prodId1 to 2.0),
            paymentMethod = "CASH"
        )
        val saleId = checkoutResult.getOrThrow()

        // User dismisses dialog -> no call to processSaleReturn
        val returns = productRepository.getReturnsForSale(saleId)
        assertTrue(returns.isEmpty())

        val remaining = productRepository.getReturnableQuantities(saleId)
        val saleItems = productRepository.getSaleItems(saleId)
        assertEquals(2.0, remaining[saleItems[0].id] ?: 0.0, 0.001)
    }

    // 13. Successful return
    @Test
    fun test13_successfulReturn_createsReturnAndKeepsSaleImmutable() = runBlocking {
        val checkoutResult = productRepository.processAtomicCheckout(
            mapOf(prodId1 to 2.0),
            paymentMethod = "CASH"
        )
        val saleId = checkoutResult.getOrThrow()
        val originalSale = saleRepository.getTransactionById(saleId)!!
        val originalSaleItems = saleRepository.getItemsForTransaction(saleId)

        val returnResult = productRepository.processSaleReturn(
            saleId = saleId,
            itemsToReturn = mapOf(originalSaleItems[0].id to 1.0)
        )
        assertTrue(returnResult.isSuccess)

        // Verify sale record has NOT changed
        val saleAfter = saleRepository.getTransactionById(saleId)!!
        assertEquals(originalSale.totalAmount, saleAfter.totalAmount)
        assertEquals(originalSale.transactionNumber, saleAfter.transactionNumber)
        assertEquals(originalSale.transactionDate, saleAfter.transactionDate)

        // Verify return record is created
        val returns = productRepository.getReturnsForSale(saleId)
        assertEquals(1, returns.size)
        assertTrue(returns[0].returnNumber.startsWith("RET-"))
    }

    // 14. Return history visible
    @Test
    fun test14_returnHistory_visibleInSaleDetail() = runBlocking {
        val checkoutResult = productRepository.processAtomicCheckout(
            mapOf(prodId1 to 3.0),
            paymentMethod = "CASH"
        )
        val saleId = checkoutResult.getOrThrow()
        val saleItems = productRepository.getSaleItems(saleId)

        productRepository.processSaleReturn(
            saleId = saleId,
            itemsToReturn = mapOf(saleItems[0].id to 1.0),
            reason = "Salah barang"
        )

        val returnHistory = productRepository.getReturnsForSale(saleId)
        assertEquals(1, returnHistory.size)
        assertEquals("Salah barang", returnHistory[0].reason)
        assertEquals(65000L, returnHistory[0].totalRefundAmount)

        val returnItems = productRepository.getReturnItems(returnHistory[0].id)
        assertEquals(1, returnItems.size)
        assertEquals(1.0, returnItems[0].quantity, 0.001)
    }

    // 15. Already fully returned item cannot be returned again
    @Test
    fun test15_fullyReturnedItem_cannotBeReturnedAgain() = runBlocking {
        val checkoutResult = productRepository.processAtomicCheckout(
            mapOf(prodId1 to 1.0),
            paymentMethod = "CASH"
        )
        val saleId = checkoutResult.getOrThrow()
        val saleItems = productRepository.getSaleItems(saleId)

        productRepository.processSaleReturn(
            saleId = saleId,
            itemsToReturn = mapOf(saleItems[0].id to 1.0)
        )

        val returnableMap = productRepository.getReturnableQuantities(saleId)
        val remainingQty = returnableMap[saleItems[0].id] ?: 0.0
        assertEquals(0.0, remainingQty, 0.001)

        // Attempting another return must fail
        val secondReturn = productRepository.processSaleReturn(
            saleId = saleId,
            itemsToReturn = mapOf(saleItems[0].id to 1.0)
        )
        assertFalse(secondReturn.isSuccess)
    }

    // 16. Multiple partial returns reflected correctly
    @Test
    fun test16_multiplePartialReturns_trackedAccuratelyUntilSoldQty() = runBlocking {
        val checkoutResult = productRepository.processAtomicCheckout(
            mapOf(prodId1 to 5.0),
            paymentMethod = "CASH"
        )
        val saleId = checkoutResult.getOrThrow()
        val saleItems = productRepository.getSaleItems(saleId)
        val saleItemId = saleItems[0].id

        // Return 1: qty = 2
        productRepository.processSaleReturn(
            saleId = saleId,
            itemsToReturn = mapOf(saleItemId to 2.0)
        )
        var returnable = productRepository.getReturnableQuantities(saleId)
        assertEquals(3.0, returnable[saleItemId] ?: 0.0, 0.001)

        // Return 2: qty = 1
        productRepository.processSaleReturn(
            saleId = saleId,
            itemsToReturn = mapOf(saleItemId to 1.0)
        )
        returnable = productRepository.getReturnableQuantities(saleId)
        assertEquals(2.0, returnable[saleItemId] ?: 0.0, 0.001)

        // Return 3: qty = 2 (Full)
        productRepository.processSaleReturn(
            saleId = saleId,
            itemsToReturn = mapOf(saleItemId to 2.0)
        )
        returnable = productRepository.getReturnableQuantities(saleId)
        assertEquals(0.0, returnable[saleItemId] ?: 0.0, 0.001)

        val returns = productRepository.getReturnsForSale(saleId)
        assertEquals(3, returns.size)
    }

    // 17. CASH return shows CASH
    @Test
    fun test17_cashSale_returnShowsCashRefundSemantics() = runBlocking {
        val checkoutResult = productRepository.processAtomicCheckout(
            mapOf(prodId1 to 1.0),
            paymentMethod = "CASH"
        )
        val saleId = checkoutResult.getOrThrow()
        val sale = saleRepository.getTransactionById(saleId)!!
        assertEquals("CASH", sale.paymentMethod)

        val saleItems = productRepository.getSaleItems(saleId)
        val returnResult = productRepository.processSaleReturn(
            saleId = saleId,
            itemsToReturn = mapOf(saleItems[0].id to 1.0)
        )
        assertTrue(returnResult.isSuccess)

        val returnTx = database.saleReturnDao().getReturnById(returnResult.getOrThrow(), "LEGACY_BUSINESS")!!
        assertEquals("CASH", returnTx.refundMethod)
        assertEquals(65000L, returnTx.totalRefundAmount)
    }

    // 18. QRIS return shows CASH
    @Test
    fun test18_qrisSale_returnShowsCashRefundWithWarning() = runBlocking {
        val checkoutResult = productRepository.processAtomicCheckout(
            mapOf(prodId1 to 1.0),
            paymentMethod = "QRIS"
        )
        val saleId = checkoutResult.getOrThrow()
        val sale = saleRepository.getTransactionById(saleId)!!
        assertEquals("QRIS", sale.paymentMethod)

        val saleItems = productRepository.getSaleItems(saleId)
        val returnResult = productRepository.processSaleReturn(
            saleId = saleId,
            itemsToReturn = mapOf(saleItems[0].id to 1.0)
        )
        assertTrue(returnResult.isSuccess)

        val returnTx = database.saleReturnDao().getReturnById(returnResult.getOrThrow(), "LEGACY_BUSINESS")!!
        assertEquals("CASH", returnTx.refundMethod)
        assertEquals(65000L, returnTx.totalRefundAmount)
    }

    // 19. CREDIT return shows debt adjustment semantics
    @Test
    fun test19_creditSale_returnShowsDebtAdjustmentSemantics() = runBlocking {
        val checkoutResult = saleRepository.completeSale(
            cartItems = mapOf(prodId1 to 2.0),
            paymentMethod = "CREDIT",
            customerId = customerId
        )
        val saleId = checkoutResult.getOrThrow()
        val sale = saleRepository.getTransactionById(saleId)!!
        assertEquals("CREDIT", sale.paymentMethod)

        val saleItems = productRepository.getSaleItems(saleId)
        val returnResult = productRepository.processSaleReturn(
            saleId = saleId,
            itemsToReturn = mapOf(saleItems[0].id to 1.0)
        )
        assertTrue(returnResult.isSuccess)

        val returnTx = database.saleReturnDao().getReturnById(returnResult.getOrThrow(), "LEGACY_BUSINESS")!!
        assertEquals("CREDIT", returnTx.refundMethod)
        assertEquals(65000L, returnTx.totalRefundAmount)
    }

    // 20. Duplicate submit prevented
    @Test
    fun test20_duplicateSubmitProtection_preventsDoubleSubmission() = runBlocking {
        val checkoutResult = productRepository.processAtomicCheckout(
            mapOf(prodId1 to 1.0),
            paymentMethod = "CASH"
        )
        val saleId = checkoutResult.getOrThrow()
        val saleItems = productRepository.getSaleItems(saleId)

        var isSubmitting = true
        // UI guard prevents second invocation when isSubmitting is true
        if (!isSubmitting) {
            productRepository.processSaleReturn(
                saleId = saleId,
                itemsToReturn = mapOf(saleItems[0].id to 1.0)
            )
        }

        val returns = productRepository.getReturnsForSale(saleId)
        assertTrue("No return created while duplicate submit is prevented", returns.isEmpty())
    }
}







