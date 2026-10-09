package id.skmnetwork.bukuwarung.data.migration

import id.skmnetwork.bukuwarung.data.local.dao.DigitalTransactionDao
import id.skmnetwork.bukuwarung.data.local.entity.DigitalTransactionEntity
import id.skmnetwork.bukuwarung.data.local.entity.DigitalTransactionStatus
import id.skmnetwork.bukuwarung.data.repository.DigitalTransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Release 3 — Gate 1C: Standalone PPOB Data Foundation & Repository Test Suite
 * Validates standalone PPOB draft creation, legacy compatibility, transaction number generation,
 * and DAO interaction for Room v17 schema.
 */
class DigitalTransactionMigrationTest {

    private lateinit var fakeDao: MemoryDigitalTransactionDao
    private lateinit var repository: DigitalTransactionRepository

    @Before
    fun setup() {
        fakeDao = MemoryDigitalTransactionDao()
        repository = DigitalTransactionRepository(
            digitalTransactionDao = fakeDao,
            businessId = "TEST_BIZ_01"
        )
    }

    @Test
    fun test01_transactionNumberGenerator_uniquenessAndFormat() {
        val tx1 = repository.generateTransactionNumber()
        val tx2 = repository.generateTransactionNumber()

        assertNotNull(tx1)
        assertNotNull(tx2)
        assertTrue("Must start with DIG- prefix", tx1.startsWith("DIG-"))
        assertTrue("Must start with DIG- prefix", tx2.startsWith("DIG-"))
        assertTrue("Consecutive generated transaction numbers must be unique", tx1 != tx2)
    }

    @Test
    fun test02_createStandaloneDraft_createsValidV17EntityWithoutSaleItemId() = runBlocking {
        val id = repository.createStandaloneDraft(
            providerId = "DIGIFLAZZ",
            providerProductCode = "pln20",
            destinationNumber = "541234567890",
            sellingPrice = 22500L,
            actualPurchasePrice = 20150L,
            adminFee = 2500L,
            customerId = 7L,
            paymentMethod = "QRIS"
        )

        val inserted = repository.getById(id)
        assertNotNull(inserted)
        assertEquals("TEST_BIZ_01", inserted!!.businessId)
        assertNull("Standalone PPOB must have null saleItemId", inserted.saleItemId)
        assertNotNull("Standalone PPOB must have generated transactionNumber", inserted.transactionNumber)
        assertTrue(inserted.transactionNumber!!.startsWith("DIG-"))
        assertEquals(7L, inserted.customerId)
        assertEquals("QRIS", inserted.paymentMethod)
        assertEquals(22500L, inserted.sellingPrice)
        assertEquals(20150L, inserted.actualPurchasePrice)
        assertEquals(2500L, inserted.adminFee)
        assertEquals(DigitalTransactionStatus.DRAFT.name, inserted.status)
    }

    @Test
    fun test03_createDraft_preservesLegacyHybridCartContract() = runBlocking {
        val id = repository.createDraft(
            saleItemId = 555L,
            providerId = "DIGIFLAZZ",
            providerProductCode = "xld50",
            destinationNumber = "081234567890",
            sellingPrice = 52000L,
            actualPurchasePrice = 50150L,
            adminFee = 0L,
            paymentMethod = "CASH"
        )

        val inserted = repository.getById(id)
        assertNotNull(inserted)
        assertEquals("TEST_BIZ_01", inserted!!.businessId)
        assertEquals(555L, inserted.saleItemId)
        assertNull("Hybrid cart item has null transactionNumber on digital entity (inherits from sales_transactions)", inserted.transactionNumber)
        assertNull(inserted.customerId)
        assertEquals("CASH", inserted.paymentMethod)
        assertEquals(52000L, inserted.sellingPrice)
        assertEquals(50150L, inserted.actualPurchasePrice)
        assertEquals(0L, inserted.adminFee)
    }

    @Test
    fun test04_getStandaloneTransactions_filtersOnlyNullSaleItemId() = runBlocking {
        // Hybrid transaction
        repository.createDraft(
            saleItemId = 101L,
            providerId = "TELKOMSEL",
            providerProductCode = "tsel10",
            destinationNumber = "08111111",
            sellingPrice = 12000L
        )

        // Standalone transaction
        repository.createStandaloneDraft(
            providerId = "PLN",
            providerProductCode = "pln50",
            destinationNumber = "123456789",
            sellingPrice = 52500L,
            actualPurchasePrice = 50000L
        )

        val all = repository.getAllTransactions().first()
        val standalone = repository.getStandaloneTransactions().first()

        assertEquals(2, all.size)
        assertEquals(1, standalone.size)
        assertNull(standalone[0].saleItemId)
        assertEquals("PLN", standalone[0].providerId)
    }

    @Test
    fun test05_getByTransactionNumber_retrievesExactRecord() = runBlocking {
        val customTrxNumber = "DIG-20261009-TEST-0001"
        val id = repository.createStandaloneDraft(
            providerId = "INDOSAT",
            providerProductCode = "isat25",
            destinationNumber = "085711112222",
            sellingPrice = 27000L,
            actualPurchasePrice = 25100L,
            transactionNumber = customTrxNumber
        )

        val retrieved = repository.getByTransactionNumber(customTrxNumber)
        assertNotNull(retrieved)
        assertEquals(id, retrieved!!.id)
        assertEquals(customTrxNumber, retrieved.transactionNumber)
        assertEquals("085711112222", retrieved.destinationNumber)
    }

    @Test
    fun test06_getTransactionsByCustomerId_filtersAccurately() = runBlocking {
        repository.createStandaloneDraft(
            providerId = "TELKOMSEL",
            providerProductCode = "tsel25",
            destinationNumber = "0812345",
            sellingPrice = 27000L,
            actualPurchasePrice = 25000L,
            customerId = 99L
        )

        repository.createStandaloneDraft(
            providerId = "XL",
            providerProductCode = "xl25",
            destinationNumber = "0818345",
            sellingPrice = 27000L,
            actualPurchasePrice = 25000L,
            customerId = null
        )

        val customer99Txs = repository.getTransactionsByCustomerId(99L).first()
        assertEquals(1, customer99Txs.size)
        assertEquals(99L, customer99Txs[0].customerId)
    }
}

/**
 * In-memory test implementation of DigitalTransactionDao for Room v17 verification.
 */
private class MemoryDigitalTransactionDao : DigitalTransactionDao {
    private val store = mutableMapOf<Long, DigitalTransactionEntity>()
    private var autoId = 1L

    override suspend fun insert(transaction: DigitalTransactionEntity): Long {
        val id = if (transaction.id == 0L) autoId++ else transaction.id
        store[id] = transaction.copy(id = id)
        return id
    }

    override suspend fun update(transaction: DigitalTransactionEntity) {
        store[transaction.id] = transaction
    }

    override suspend fun getById(id: Long, businessId: String): DigitalTransactionEntity? =
        store[id]?.takeIf { it.businessId == businessId }

    override suspend fun getBySaleItemId(saleItemId: Long, businessId: String): DigitalTransactionEntity? =
        store.values.find { it.saleItemId == saleItemId && it.businessId == businessId }

    override suspend fun getBySaleItemIds(saleItemIds: List<Long>, businessId: String): List<DigitalTransactionEntity> =
        store.values.filter { it.saleItemId in saleItemIds && it.businessId == businessId }

    override fun getByBusinessId(businessId: String): Flow<List<DigitalTransactionEntity>> =
        flow { emit(store.values.filter { it.businessId == businessId }) }

    override fun getStandaloneTransactions(businessId: String): Flow<List<DigitalTransactionEntity>> =
        flow { emit(store.values.filter { it.saleItemId == null && it.businessId == businessId }) }

    override suspend fun getByTransactionNumber(transactionNumber: String, businessId: String): DigitalTransactionEntity? =
        store.values.find { it.transactionNumber == transactionNumber && it.businessId == businessId }

    override fun getByCustomerId(customerId: Long, businessId: String): Flow<List<DigitalTransactionEntity>> =
        flow { emit(store.values.filter { it.customerId == customerId && it.businessId == businessId }) }

    override fun getByStatus(status: String, businessId: String): Flow<List<DigitalTransactionEntity>> =
        flow { emit(store.values.filter { it.status == status && it.businessId == businessId }) }

    override suspend fun getByUuid(uuid: String, businessId: String): DigitalTransactionEntity? =
        store.values.find { it.uuid == uuid && it.businessId == businessId }
}
