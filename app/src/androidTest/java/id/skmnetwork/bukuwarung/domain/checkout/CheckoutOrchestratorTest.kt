package id.skmnetwork.bukuwarung

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.local.entity.CategoryEntity
import id.skmnetwork.bukuwarung.data.local.entity.DigitalTransactionStatus
import id.skmnetwork.bukuwarung.data.local.entity.FulfillmentMode
import id.skmnetwork.bukuwarung.data.local.entity.ItemType
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.data.repository.DigitalTransactionRepository
import id.skmnetwork.bukuwarung.data.repository.SaleRepository
import id.skmnetwork.bukuwarung.domain.checkout.CartLine
import id.skmnetwork.bukuwarung.domain.checkout.CheckoutOrchestrator
import id.skmnetwork.bukuwarung.domain.checkout.aggregateByCheckoutIdentity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CheckoutOrchestratorTest {

    private lateinit var db: AppDatabase
    private lateinit var saleRepository: SaleRepository
    private lateinit var digitalTransactionRepository: DigitalTransactionRepository
    private lateinit var orchestrator: CheckoutOrchestrator

    @Before
    fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        db.categoryDao().insertCategory(CategoryEntity(name = "Test"))
        saleRepository = SaleRepository(db)
        digitalTransactionRepository = DigitalTransactionRepository(db.digitalTransactionDao())
        orchestrator = CheckoutOrchestrator(db, saleRepository, digitalTransactionRepository)
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun cartIdentity_aggregatesByProductAndDestinationWithoutForcingQuantityToOne() = runBlocking {
        val physical = insertProduct("Physical", ItemType.PHYSICAL, FulfillmentMode.MANUAL, 10.0)
        val manual = insertProduct("Manual", ItemType.DIGITAL, FulfillmentMode.MANUAL, 0.0)
        val provider = insertProduct("Provider", ItemType.DIGITAL, FulfillmentMode.PROVIDER, 0.0)

        val aggregated = listOf(
            CartLine("p1", physical, 1.5),
            CartLine("p2", physical, 2.5),
            CartLine("m1", manual, 1.5),
            CartLine("m2", manual, 2.0),
            CartLine("d1", provider, 1.0, "0811"),
            CartLine("d2", provider, 2.0, "0811"),
            CartLine("d3", provider, 1.5, "0822")
        ).aggregateByCheckoutIdentity()

        assertEquals(listOf("p1", "m1", "d1", "d3"), aggregated.map { it.lineId })
        assertEquals(listOf(4.0, 3.5, 3.0, 1.5), aggregated.map { it.quantity })
    }

    @Test
    fun processCheckout_preservesLineOrderAndCreatesProviderDraftsForMixedCart() = runBlocking {
        val physical = insertProduct("Physical", ItemType.PHYSICAL, FulfillmentMode.MANUAL, 10.0)
        val manual = insertProduct("Manual", ItemType.DIGITAL, FulfillmentMode.MANUAL, 0.0)
        val provider = insertProduct("Provider", ItemType.DIGITAL, FulfillmentMode.PROVIDER, 0.0)

        val result = orchestrator.processCheckout(
            cartLines = listOf(
                CartLine("p", physical, 1.5),
                CartLine("m", manual, 2.5),
                CartLine("d1", provider, 1.0, "0811"),
                CartLine("d2", provider, 1.5, "0822")
            ),
            paymentMethod = "CASH"
        )

        assertTrue(result.toString(), result.isSuccess)
        val saleId = result.getOrThrow()
        val saleItems = db.saleDao().getItemsForTransaction(saleId)
        assertEquals(listOf(1.5, 2.5, 1.0, 1.5), saleItems.map { it.quantity })
        assertEquals(8.5, db.productDao().getProductById(physical.id)!!.stock, 0.001)

        val drafts = digitalTransactionRepository.getBySaleItemIds(saleItems.map { it.id })
        assertEquals(2, drafts.size)
        assertEquals(saleItems[2].id, drafts[0].saleItemId)
        assertEquals(saleItems[3].id, drafts[1].saleItemId)
        assertEquals("0811", drafts[0].destinationNumber)
        assertEquals("0822", drafts[1].destinationNumber)
        assertEquals(DigitalTransactionStatus.DRAFT.name, drafts[0].status)
        assertEquals(DigitalTransactionStatus.DRAFT.name, drafts[1].status)
    }

    @Test
    fun processCheckout_rollsBackEveryLocalWriteWhenDraftPhaseFails() = runBlocking {
        val provider = insertProduct("Provider", ItemType.DIGITAL, FulfillmentMode.PROVIDER, 0.0)
        val failingOrchestrator = CheckoutOrchestrator(
            appDatabase = db,
            saleRepository = saleRepository,
            digitalTransactionRepository = digitalTransactionRepository,
            beforeDigitalDrafts = { throw IllegalStateException("draft phase failure") }
        )

        val result = failingOrchestrator.processCheckout(
            cartLines = listOf(CartLine("d", provider, 1.0, "0811")),
            paymentMethod = "CASH"
        )

        assertFalse(result.toString(), result.isSuccess)
        assertTrue(db.saleDao().getAllTransactions().first().isEmpty())
        assertTrue(db.digitalTransactionDao().getBySaleItemIds(listOf(1L)).isEmpty())
        assertTrue(db.cashDao().getAllCashTransactions().first().isEmpty())
        assertTrue(db.stockMovementDao().getAllMovements().first().isEmpty())
        assertTrue(db.syncQueueDao().getAllItems().first().isEmpty())
        assertEquals(0.0, db.productDao().getProductById(provider.id)!!.stock, 0.001)
    }

    private suspend fun insertProduct(
        name: String,
        itemType: ItemType,
        fulfillmentMode: FulfillmentMode,
        stock: Double
    ): ProductEntity {
        val product = ProductEntity(
            categoryId = 1L,
            name = name,
            purchasePrice = 100L,
            sellingPrice = 150L,
            stock = stock,
            minimumStock = 0.0,
            unit = "pcs",
            itemType = itemType.name,
            fulfillmentMode = fulfillmentMode.name,
            digitalProviderId = if (fulfillmentMode == FulfillmentMode.PROVIDER) "DIGIFLAZZ" else null,
            digitalProductCode = if (fulfillmentMode == FulfillmentMode.PROVIDER) "PLN20" else null,
            createdAt = 0L,
            updatedAt = 0L
        )
        val id = db.productDao().insertProduct(product)
        return db.productDao().getProductById(id)!!
    }
}
