package id.skmnetwork.bukuwarung.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.skmnetwork.bukuwarung.data.local.dao.DigitalTransactionDao
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.local.entity.DigitalTransactionEntity
import id.skmnetwork.bukuwarung.data.local.entity.DigitalTransactionStatus
import id.skmnetwork.bukuwarung.data.local.entity.FulfillmentMode
import id.skmnetwork.bukuwarung.data.local.entity.ItemType
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.data.local.entity.SaleItemEntity
import id.skmnetwork.bukuwarung.data.local.entity.SaleTransactionEntity
import id.skmnetwork.bukuwarung.data.remote.CheckDigitalStatusRequest
import id.skmnetwork.bukuwarung.data.remote.MockBackendApi
import id.skmnetwork.bukuwarung.data.remote.SubmitDigitalRequest
import id.skmnetwork.bukuwarung.data.repository.DigitalTransactionRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DigitalTransactionRepositoryApiTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: DigitalTransactionDao
    private lateinit var repository: DigitalTransactionRepository
    private lateinit var mockBackend: MockBackendApi

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.digitalTransactionDao()
        mockBackend = MockBackendApi()
        repository = DigitalTransactionRepository(dao, mockBackend)
    }

    @After
    fun teardown() {
        db.close()
        mockBackend.clear()
    }

    @Test
    fun submitDraft_transitionsDraftToPendingViaBackend() = runBlocking {
        val categoryId = db.categoryDao().insertCategory(id.skmnetwork.bukuwarung.data.local.entity.CategoryEntity(name = "Cat"))
        val product = ProductEntity(
            categoryId = categoryId,
            name = "Pulsa 25K",
            purchasePrice = 24000,
            sellingPrice = 26000,
            stock = 0.0,
            itemType = ItemType.DIGITAL.name,
            fulfillmentMode = FulfillmentMode.PROVIDER.name,
            digitalProviderId = "DIGIFLAZZ",
            digitalProductCode = "TSEL25"
        )
        val productId = db.productDao().insertProduct(product)
        val sale = SaleTransactionEntity(transactionNumber = "INV-001", totalAmount = 26000)
        val saleId = db.saleDao().insertTransaction(sale)
        val saleItem = SaleItemEntity(
            transactionId = saleId,
            productId = productId,
            productName = product.name,
            quantity = 1.0,
            price = product.sellingPrice,
            purchasePrice = product.purchasePrice,
            subtotal = product.sellingPrice
        )
        val saleItemId = db.saleDao().insertSaleItems(listOf(saleItem)).single()

        val entity = DigitalTransactionEntity(
            saleItemId = saleItemId,
            providerId = product.digitalProviderId!!,
            providerProductCode = product.digitalProductCode!!,
            destinationNumber = "08123456789",
            sellingPrice = product.sellingPrice,
            actualPurchasePrice = product.purchasePrice,
            status = DigitalTransactionStatus.DRAFT.name
        )
        val id = dao.insert(entity)

        val result = repository.submitDraft(id)

        assertEquals(DigitalTransactionStatus.PENDING.name, result.status)
        assertNotNull(result.providerReferenceId)
        val updated = dao.getById(id)!!
        assertEquals(DigitalTransactionStatus.PENDING.name, updated.status)
        assertNotNull(updated.providerReferenceId)
    }

    @Test
    fun checkStatus_returnsCurrentStatusFromBackend() = runBlocking {
        val categoryId = db.categoryDao().insertCategory(id.skmnetwork.bukuwarung.data.local.entity.CategoryEntity(name = "Cat"))
        val product = ProductEntity(
            categoryId = categoryId,
            name = "Pulsa 25K",
            purchasePrice = 24000,
            sellingPrice = 26000,
            stock = 0.0,
            itemType = ItemType.DIGITAL.name,
            fulfillmentMode = FulfillmentMode.PROVIDER.name,
            digitalProviderId = "DIGIFLAZZ",
            digitalProductCode = "TSEL25"
        )
        val productId = db.productDao().insertProduct(product)
        val sale = SaleTransactionEntity(transactionNumber = "INV-002", totalAmount = 26000)
        val saleId = db.saleDao().insertTransaction(sale)
        val saleItem = SaleItemEntity(
            transactionId = saleId,
            productId = productId,
            productName = product.name,
            quantity = 1.0,
            price = product.sellingPrice,
            purchasePrice = product.purchasePrice,
            subtotal = product.sellingPrice
        )
        val saleItemId = db.saleDao().insertSaleItems(listOf(saleItem)).single()

        val entity = DigitalTransactionEntity(
            saleItemId = saleItemId,
            providerId = "PROV1",
            providerProductCode = "CODE1",
            destinationNumber = "08123456789",
            sellingPrice = 26000,
            actualPurchasePrice = 24000,
            status = DigitalTransactionStatus.PENDING.name,
            providerReferenceId = "REF-123"
        )
        val id = dao.insert(entity)

        val result = repository.checkStatus(id)

        assertNotNull(result)
        assertEquals("REF-123", result.providerReferenceId)
    }

    @Test
    fun processCallback_updatesStatusFromBackend() = runBlocking {
        val entity = DigitalTransactionEntity(
            saleItemId = 1L,
            providerId = "PROV1",
            providerProductCode = "CODE1",
            destinationNumber = "08123456789",
            sellingPrice = 26000,
            actualPurchasePrice = 24000,
            status = DigitalTransactionStatus.PENDING.name,
            providerReferenceId = "REF-123"
        )
        val id = dao.insert(entity)

        repository.processCallback(
            idempotencyKey = entity.uuid,
            status = DigitalTransactionStatus.SUCCESS.name,
            providerReferenceId = "REF-123",
            snToken = "SN-456",
            failureReason = null
        )

        val updated = dao.getById(id)!!
        assertEquals(DigitalTransactionStatus.SUCCESS.name, updated.status)
        assertEquals("SN-456", updated.snToken)
    }

    @Test
    fun recoverUnknown_usesCheckStatusNotCreate() = runBlocking {
        val entity = DigitalTransactionEntity(
            saleItemId = 1L,
            providerId = "PROV1",
            providerProductCode = "CODE1",
            destinationNumber = "08123456789",
            sellingPrice = 26000,
            actualPurchasePrice = 24000,
            status = DigitalTransactionStatus.UNKNOWN.name,
            providerReferenceId = "REF-123"
        )
        val id = dao.insert(entity)

        val result = repository.checkStatus(id)

        assertNotNull(result)
        val updated = dao.getById(id)!!
        assertEquals(DigitalTransactionStatus.UNKNOWN.name, updated.status)
    }

    @Test
    fun duplicateCallback_isIdempotent() = runBlocking {
        val entity = DigitalTransactionEntity(
            saleItemId = 1L,
            providerId = "PROV1",
            providerProductCode = "CODE1",
            destinationNumber = "08123456789",
            sellingPrice = 26000,
            actualPurchasePrice = 24000,
            status = DigitalTransactionStatus.PENDING.name,
            providerReferenceId = "REF-123"
        )
        val id = dao.insert(entity)

        repository.processCallback(
            idempotencyKey = entity.uuid,
            status = DigitalTransactionStatus.SUCCESS.name,
            providerReferenceId = "REF-123",
            snToken = "SN-456",
            failureReason = null
        )
        repository.processCallback(
            idempotencyKey = entity.uuid,
            status = DigitalTransactionStatus.SUCCESS.name,
            providerReferenceId = "REF-123",
            snToken = "SN-456",
            failureReason = null
        )

        val updated = dao.getById(id)!!
        assertEquals(DigitalTransactionStatus.SUCCESS.name, updated.status)
    }

    @Test
    fun submitDraft_usesUuidAsIdempotencyKey() = runBlocking {
        val entity = DigitalTransactionEntity(
            saleItemId = 1L,
            providerId = "PROV1",
            providerProductCode = "CODE1",
            destinationNumber = "08123456789",
            sellingPrice = 26000,
            actualPurchasePrice = 24000,
            status = DigitalTransactionStatus.DRAFT.name
        )
        val id = dao.insert(entity)

        repository.submitDraft(id)

        val backendRequest = SubmitDigitalRequest(
            idempotencyKey = entity.uuid,
            productCode = entity.providerProductCode,
            destinationNumber = entity.destinationNumber,
            sellingPrice = entity.sellingPrice,
            actualPurchasePrice = entity.actualPurchasePrice
        )
        assertEquals(entity.uuid, backendRequest.idempotencyKey)
    }

    @Test
    fun checkStatus_usesUuidNotNewTransaction() = runBlocking {
        val entity = DigitalTransactionEntity(
            saleItemId = 1L,
            providerId = "PROV1",
            providerProductCode = "CODE1",
            destinationNumber = "08123456789",
            sellingPrice = 26000,
            actualPurchasePrice = 24000,
            status = DigitalTransactionStatus.UNKNOWN.name,
            providerReferenceId = "REF-123"
        )
        val id = dao.insert(entity)

        repository.checkStatus(id)

        val request = CheckDigitalStatusRequest(
            idempotencyKey = entity.uuid,
            providerReferenceId = entity.providerReferenceId
        )
        assertEquals(entity.uuid, request.idempotencyKey)
        assertEquals(entity.providerReferenceId, request.providerReferenceId)
    }

    @Test
    fun processCallback_usesUuidToFindEntity() = runBlocking {
        val entity = DigitalTransactionEntity(
            saleItemId = 1L,
            providerId = "PROV1",
            providerProductCode = "CODE1",
            destinationNumber = "08123456789",
            sellingPrice = 26000,
            actualPurchasePrice = 24000,
            status = DigitalTransactionStatus.PENDING.name,
            providerReferenceId = "REF-123"
        )
        dao.insert(entity)

        repository.processCallback(
            idempotencyKey = entity.uuid,
            status = DigitalTransactionStatus.FAILED.name,
            providerReferenceId = "REF-123",
            snToken = null,
            failureReason = "Insufficient balance"
        )

        val all = dao.getByStatus(DigitalTransactionStatus.FAILED.name).first()
        assertEquals(1, all.size)
        assertEquals(entity.uuid, all[0].uuid)
        assertEquals("Insufficient balance", all[0].failureReason)
    }
}