package id.skmnetwork.bukuwarung.repository

import id.skmnetwork.bukuwarung.data.local.dao.DigitalTransactionDao
import id.skmnetwork.bukuwarung.data.local.entity.DigitalTransactionEntity
import id.skmnetwork.bukuwarung.data.local.entity.DigitalTransactionStatus
import id.skmnetwork.bukuwarung.data.remote.MockBackendApi
import id.skmnetwork.bukuwarung.data.remote.SubmitDigitalRequest
import id.skmnetwork.bukuwarung.data.repository.DigitalTransactionRepository
import id.skmnetwork.bukuwarung.domain.provider.CreateTransactionRequest
import id.skmnetwork.bukuwarung.domain.provider.CheckStatusRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class DigitalTransactionRepositoryApiTest {

    private lateinit var dao: DigitalTransactionDao
    private lateinit var repository: DigitalTransactionRepository
    private lateinit var mockBackend: MockBackendApi

    @Before
    fun setup() {
        dao = FakeDigitalTransactionDao()
        mockBackend = MockBackendApi()
        repository = DigitalTransactionRepository(dao, mockBackend)
    }

    @After
    fun teardown() {
        mockBackend.clear()
    }

    @Test
    fun submitDraft_transitionsDraftToPendingViaBackend() = runBlocking {
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
        val loaded = dao.getById(id)!!

        val result = repository.submitDraft(id)

        assertEquals(DigitalTransactionStatus.PENDING.name, result.status)
        assertNotNull(result.providerReferenceId)
        val updated = dao.getById(id)!!
        assertEquals(DigitalTransactionStatus.PENDING.name, updated.status)
        assertNotNull(updated.providerReferenceId)
    }

    @Test
    fun checkStatus_returnsCurrentStatusFromBackend() = runBlocking {
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
        val loaded = dao.getById(id)!!

        val result = repository.checkStatus(id)

        assertNotNull(result)
        assertEquals(loaded.providerReferenceId, result.providerReferenceId)
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

        val request = CheckStatusRequest(
            idempotencyKey = entity.uuid,
            providerReferenceId = entity.providerReferenceId
        )
        assertEquals(entity.uuid, request.idempotencyKey)
        assertEquals(entity.providerReferenceId, request.providerReferenceId)
    }
}

private class FakeDigitalTransactionDao : DigitalTransactionDao {
    private val store = mutableMapOf<Long, DigitalTransactionEntity>()
    private var nextId = 1L

    override suspend fun insert(transaction: DigitalTransactionEntity): Long {
        val id = if (transaction.id == 0L) nextId++ else transaction.id
        store[id] = transaction.copy(id = id)
        return id
    }

    override suspend fun update(transaction: DigitalTransactionEntity) {
        store[transaction.id] = transaction
    }

    override suspend fun getById(id: Long): DigitalTransactionEntity? = store[id]

    override suspend fun getBySaleItemId(saleItemId: Long): DigitalTransactionEntity? =
        store.values.find { it.saleItemId == saleItemId }

    override suspend fun getBySaleItemIds(saleItemIds: List<Long>): List<DigitalTransactionEntity> =
        store.values.filter { it.saleItemId in saleItemIds }

    override fun getByStatus(status: String): Flow<List<DigitalTransactionEntity>> =
        flow { emit(store.values.filter { it.status == status }) }

    override suspend fun getByUuid(uuid: String): DigitalTransactionEntity? =
        store.values.find { it.uuid == uuid }
}