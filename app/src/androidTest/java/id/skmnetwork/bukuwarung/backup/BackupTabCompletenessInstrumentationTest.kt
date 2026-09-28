package id.skmnetwork.bukuwarung.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.local.entity.DigitalTransactionEntity
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.data.local.entity.PurchaseOrderEntity
import id.skmnetwork.bukuwarung.data.local.entity.PurchaseOrderItemEntity
import id.skmnetwork.bukuwarung.data.preferences.UserPreferencesRepository
import id.skmnetwork.bukuwarung.data.repository.CustomerRepository
import id.skmnetwork.bukuwarung.data.repository.ProductRepository
import id.skmnetwork.bukuwarung.data.repository.SaleRepository
import id.skmnetwork.bukuwarung.data.repository.SupplierRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Step 14B - real Room proof that backup completeness no longer depends on business type.
 *
 * The defect this closes: `exportSnapshot` omitted `19_DigitalTransactions`, `20_PurchaseOrders` and
 * `21_PurchaseOrderItems` unless the merchant's profile carried `CAP_DIGITAL_ITEMS` or
 * `ACTIVITY_WHOLESALE_PURCHASE`, while nothing gates the digital or purchase-order write paths. A
 * laundry shop could sell pulsa and raise a purchase order, archive without those tabs, and then
 * lose the rows on restore - because the restore deletes those three tables unconditionally and had
 * nothing to reinsert.
 *
 * Every fixture here runs on the `LAUNDRY` profile with no secondary activities, which carries
 * neither capability nor the wholesale activity. Nothing here adds them: if backup completeness
 * regressed to capability gating, these tests would fail rather than pass.
 *
 * The counterpart JVM coverage lives in `BackupTabCompletenessTest`; the existing round-trip suites
 * (`DigitalTransactionBackupTest`, `PurchaseOrderBackupTest`) are left untouched.
 */
@RunWith(AndroidJUnit4::class)
class BackupTabCompletenessInstrumentationTest {

    private companion object {
        const val BUSINESS = "STEP14A_BUSINESS"
        const val OTHER_BUSINESS = "STEP14A_OTHER_BUSINESS"
        const val SHEET = "STEP14A_SHEET"
    }

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var backupRestoreManager: BackupRestoreManager

    private lateinit var productRepository: ProductRepository
    private lateinit var saleRepository: SaleRepository
    private lateinit var customerRepository: CustomerRepository
    private lateinit var supplierRepository: SupplierRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        userPreferencesRepository = UserPreferencesRepository(context)
        backupRestoreManager = BackupRestoreManager(
            database = database,
            userPreferencesRepository = userPreferencesRepository,
            transport = MockSheetsTransport()
        )

        productRepository = ProductRepository(database, BUSINESS)
        saleRepository = SaleRepository(database, BUSINESS)
        customerRepository = CustomerRepository(database, BUSINESS)
        supplierRepository = SupplierRepository(database, BUSINESS)
    }

    @After
    fun tearDown() {
        database.close()
    }

    /**
     * A profile with neither CAP_DIGITAL_ITEMS nor ACTIVITY_WHOLESALE_PURCHASE, so every assertion
     * below runs under a merchant who could not have those modules "enabled" under the old rule.
     *
     * The active business id is written to the shared DataStore, which the exporter reads, so this
     * waits for that write to land. Without it a test can export under a previous test's tenant.
     */
    private suspend fun useProfileWithoutThoseCapabilities() {
        userPreferencesRepository.setBusinessId(BUSINESS)
        userPreferencesRepository.updateBusinessProfile(
            primaryType = "LAUNDRY",
            secondaryActivities = emptySet()
        )
        var settled = false
        repeat(50) {
            if (userPreferencesRepository.userSettings.first().businessId == BUSINESS) {
                settled = true
                return@repeat
            }
            kotlinx.coroutines.delay(20)
        }
        assertTrue("The active business id must be the one the exporter reads", settled)
    }

    private suspend fun assertProfileReallyLacksThem() {
        val profile = id.skmnetwork.bukuwarung.domain.business.BusinessTaxonomyRegistry.resolve(
            userPreferencesRepository.userSettings.first().primaryBusinessType,
            userPreferencesRepository.userSettings.first().secondaryActivities
        )
        assertTrue(
            "Fixture must not carry CAP_DIGITAL_ITEMS",
            !profile.hasCapability(id.skmnetwork.bukuwarung.domain.business.BusinessCapability.CAP_DIGITAL_ITEMS)
        )
        assertTrue(
            "Fixture must not carry ACTIVITY_WHOLESALE_PURCHASE",
            !profile.hasActivity(id.skmnetwork.bukuwarung.domain.business.BusinessActivity.ACTIVITY_WHOLESALE_PURCHASE)
        )
    }

    private suspend fun seedDigitalTransaction(): Pair<DigitalTransactionEntity, String> {
        val cat = productRepository.createCategory("Pulsa").getOrThrow()
        val productId = productRepository.insertProductWithCategory(
            name = "Pulsa 50K",
            categoryName = "Pulsa",
            purchasePrice = 48000,
            sellingPrice = 50000,
            stock = 100.0,
            minimumStock = 10.0,
            unit = "pcs",
            categoryId = cat.id
        )
        val customerId = customerRepository.saveCustomer("Budi", "0812345", "Jl. Test")
        assertTrue(
            saleRepository.completeSale(
                cartItems = mapOf(productId to 1.0),
                paymentMethod = "CASH",
                customerId = customerId
            ).isSuccess
        )
        val sale = database.saleDao().getAllTransactions(BUSINESS).first().first()
        val saleItem = database.saleDao().getItemsForTransaction(sale.id, BUSINESS).first()

        val entity = DigitalTransactionEntity(
            businessId = BUSINESS,
            saleItemId = saleItem.id,
            providerId = "PROVIDER_A",
            providerProductCode = "PULSA_50K",
            destinationNumber = "081234567890",
            sellingPrice = 50000,
            actualPurchasePrice = 48000,
            status = "SUCCESS",
            providerReferenceId = "REF_123",
            snToken = "TOKEN_ABC",
            failureReason = null,
            createdAt = 1000000L,
            updatedAt = 1000100L
        )
        database.digitalTransactionDao().insert(entity)
        // The sale item's uuid travels in the archive; its numeric id is reallocated by the restore.
        return database.digitalTransactionDao().getBySaleItemId(saleItem.id, BUSINESS)!! to saleItem.uuid
    }

    private suspend fun seedPurchaseOrder(): PurchaseOrderEntity {
        val supplierId = supplierRepository.saveSupplier("PT. Bumbu Sejahtera", "0215550001", "Bandung")
        if (database.productDao().getAllProducts(BUSINESS).first().isEmpty()) {
            val cat = productRepository.createCategory("Umum").getOrThrow()
            productRepository.insertProductWithCategory(
                name = "Produk A",
                categoryName = "Umum",
                purchasePrice = 4000,
                sellingPrice = 5000,
                stock = 20.0,
                minimumStock = 5.0,
                unit = "pcs",
                categoryId = cat.id
            )
        }
        val product = database.productDao().getAllProducts(BUSINESS).first().first()
        val now = 2000000L
        val po = PurchaseOrderEntity(
            businessId = BUSINESS,
            deviceId = "DEV_1",
            orderNumber = "PO-STEP14A",
            supplierId = supplierId,
            supplierNameSnapshot = "PT. Bumbu Sejahtera",
            supplierPhoneSnapshot = "0215550001",
            status = "ORDERED",
            totalEstimatedAmount = 50000,
            notes = "Penting",
            createdAt = now,
            updatedAt = now + 100L,
            sentAt = now + 50L,
            receivedAt = null,
            finalPurchaseId = null
        )
        val poId = database.purchaseOrderDao().insertPurchaseOrder(po)
        database.purchaseOrderDao().insertPurchaseOrderItems(
            listOf(
                PurchaseOrderItemEntity(
                    purchaseOrderId = poId,
                    // The item must carry the order's real uuid: that is the link the archive keeps.
                    poUuid = po.uuid,
                    // PurchaseOrderRepository stamps the active tenant on its items; the entity
                    // would otherwise default to LEGACY_BUSINESS and the tenant-scoped export
                    // would correctly return nothing.
                    businessId = BUSINESS,
                    productId = product.id,
                    productUuid = product.uuid,
                    productName = product.name,
                    orderedQuantity = 10.0,
                    unit = product.unit,
                    estimatedPrice = 5000,
                    estimatedSubtotal = 50000,
                    receivedQuantity = 0.0
                )
            )
        )
        assertEquals(
            "The fixture must actually have a purchase order to archive",
            1,
            database.purchaseOrderDao().getAllPurchaseOrdersList(BUSINESS).size
        )
        return po
    }

    // =======================================================================
    // A - a tenant with no digital / purchase-order data still archives all tabs
    // =======================================================================

    @Test
    fun emptyTabsArePresentWithZeroRows() = runBlocking<Unit> {
        useProfileWithoutThoseCapabilities()
        assertProfileReallyLacksThem()

        val snapshot = backupRestoreManager.exportSnapshot()
        for (tab in CanonicalSerializer.DATA_TAB_NAMES) {
            assertNotNull("Data tab $tab must always be exported", snapshot.getTab(tab))
        }
        assertEquals(0, snapshot.getTab("19_DigitalTransactions")!!.rows.size)
        assertEquals(0, snapshot.getTab("20_PurchaseOrders")!!.rows.size)
        assertEquals(0, snapshot.getTab("21_PurchaseOrderItems")!!.rows.size)

        // An empty-but-complete archive is valid.
        runCatching { BackupValidator.validate(snapshot, BUSINESS) }
            .onFailure { throw AssertionError("An empty but complete archive must validate, got $it") }
    }

    // =======================================================================
    // B - digital data survives without CAP_DIGITAL_ITEMS
    // =======================================================================

    @Test
    fun digitalTransactionsAreExportedWithoutTheCapability() = runBlocking<Unit> {
        useProfileWithoutThoseCapabilities()
        assertProfileReallyLacksThem()
        val (digital) = seedDigitalTransaction()

        val snapshot = backupRestoreManager.exportSnapshot()
        val tab = snapshot.getTab("19_DigitalTransactions")
        assertNotNull("19_DigitalTransactions must exist without CAP_DIGITAL_ITEMS", tab)
        assertEquals(1, tab!!.rows.size)
        val row = tab.rows.first()
        assertEquals(digital.uuid, row[0])
        assertEquals(BUSINESS, row[1])
        assertEquals("PROVIDER_A", row[3])
        assertEquals("PULSA_50K", row[4])
        assertEquals("081234567890", row[5])
        assertEquals("50000", row[6])
        assertEquals("SUCCESS", row[8])
        assertEquals("REF_123", row[9])
    }

    // =======================================================================
    // C - purchase-order data survives without ACTIVITY_WHOLESALE_PURCHASE
    // =======================================================================

    @Test
    fun purchaseOrdersAreExportedWithoutTheActivity() = runBlocking<Unit> {
        useProfileWithoutThoseCapabilities()
        assertProfileReallyLacksThem()
        val po = seedPurchaseOrder()

        val snapshot = backupRestoreManager.exportSnapshot()
        val poTab = snapshot.getTab("20_PurchaseOrders")
        val itemTab = snapshot.getTab("21_PurchaseOrderItems")
        assertNotNull("20_PurchaseOrders must exist without ACTIVITY_WHOLESALE_PURCHASE", poTab)
        assertNotNull("21_PurchaseOrderItems must exist without ACTIVITY_WHOLESALE_PURCHASE", itemTab)
        assertEquals(1, poTab!!.rows.size)
        assertEquals(1, itemTab!!.rows.size)
        assertEquals(po.uuid, poTab.rows.first()[0])
        assertEquals(BUSINESS, poTab.rows.first()[1])
        assertEquals("DEV_1", poTab.rows.first()[2])
        assertEquals("PO-STEP14A", poTab.rows.first()[3])
        assertEquals("Penting", poTab.rows.first()[9])
        assertEquals("2000000", poTab.rows.first()[10])
    }

    // =======================================================================
    // D - seed -> export -> restore, for a merchant with neither capability
    // =======================================================================

    @Test
    fun crossCategoryDataSurvivesTheFullRoundTrip() = runBlocking<Unit> {
        useProfileWithoutThoseCapabilities()
        assertProfileReallyLacksThem()
        val (digital, originalSaleItemUuid) = seedDigitalTransaction()
        val po = seedPurchaseOrder()

        val backupResult = backupRestoreManager.performBackup(SHEET)
        assertTrue("Backup must succeed, got $backupResult", backupResult.isSuccess)

        // Exactly what a destructive restore does: the rows are gone.
        database.openHelper.writableDatabase.execSQL("DELETE FROM digital_transactions")
        database.openHelper.writableDatabase.execSQL("DELETE FROM purchase_order_items")
        database.openHelper.writableDatabase.execSQL("DELETE FROM purchase_orders")
        assertEquals(0, database.digitalTransactionDao().getBySaleItemId(digital.saleItemId, BUSINESS)?.let { 1 } ?: 0)

        val restoreResult = backupRestoreManager.performRestore(SHEET, BUSINESS)
        assertTrue("Restore must succeed, got $restoreResult", restoreResult.isSuccess)

        val restoredDigital = database.digitalTransactionDao().getByUuid(digital.uuid, BUSINESS)
        assertNotNull("The digital transaction must come back", restoredDigital)
        assertEquals(BUSINESS, restoredDigital!!.businessId)
        // The restore reallocates primary keys on purpose, so the link is proven through the uuid the
        // archive actually carries - not through the pre-restore row id.
        val restoredSaleItem = database.saleDao().getItemsForTransaction(
            database.saleDao().getAllTransactions(BUSINESS).first().first().id,
            BUSINESS
        ).firstOrNull { it.id == restoredDigital.saleItemId }
        assertNotNull("The restored digital transaction must still point at its sale item", restoredSaleItem)
        assertEquals(
            "The sale item link must survive the restore by uuid",
            originalSaleItemUuid,
            restoredSaleItem!!.uuid
        )
        assertEquals("PROVIDER_A", restoredDigital.providerId)
        assertEquals("PULSA_50K", restoredDigital.providerProductCode)
        assertEquals("081234567890", restoredDigital.destinationNumber)
        assertEquals(50000, restoredDigital.sellingPrice)
        assertEquals("SUCCESS", restoredDigital.status)
        assertEquals("REF_123", restoredDigital.providerReferenceId)

        val restoredPo = database.purchaseOrderDao().getAllPurchaseOrders(BUSINESS).first()
        assertEquals("The purchase order must come back", 1, restoredPo.size)
        assertEquals(po.uuid, restoredPo.first().uuid)
        assertEquals(BUSINESS, restoredPo.first().businessId)
        assertEquals("PO-STEP14A", restoredPo.first().orderNumber)
        assertEquals("Penting", restoredPo.first().notes)

        val restoredItems = database.purchaseOrderDao().getItemsForPurchaseOrder(BUSINESS, restoredPo.first().id)
        assertEquals("The purchase order item must come back", 1, restoredItems.size)
        assertEquals(5000, restoredItems.first().estimatedPrice)
        assertEquals(50000, restoredItems.first().estimatedSubtotal)
    }

    // =======================================================================
    // E - a missing tab is still a hard validation failure
    // =======================================================================

    @Test
    fun anArchiveMissingOneOfThoseTabsIsRejected() = runBlocking<Unit> {
        useProfileWithoutThoseCapabilities()
        seedDigitalTransaction()
        seedPurchaseOrder()

        val snapshot = backupRestoreManager.exportSnapshot()
        for (missing in listOf("19_DigitalTransactions", "20_PurchaseOrders", "21_PurchaseOrderItems")) {
            val trimmed = BackupSnapshot(
                snapshot.metadata,
                snapshot.tabs.filterKeys { it != missing }
            )
            val thrown = runCatching { BackupValidator.validate(trimmed, BUSINESS) }.exceptionOrNull()
            assertTrue(
                "Removing $missing must make the archive invalid, got $thrown",
                thrown is CorruptedBackupException
            )
        }
    }

    // =======================================================================
    // F - another tenant's data neither leaks in nor changes inclusion
    // =======================================================================

    @Test
    fun anotherTenantsDataDoesNotAffectThisArchive() = runBlocking<Unit> {
        useProfileWithoutThoseCapabilities()
        val (own) = seedDigitalTransaction()

        // A second tenant rows that must stay out of the export.
        val foreignSaleItem = database.saleDao().getItemsForTransaction(
            database.saleDao().getAllTransactions(BUSINESS).first().first().id,
            BUSINESS
        ).first()
        database.digitalTransactionDao().insert(
            DigitalTransactionEntity(
                businessId = OTHER_BUSINESS,
                saleItemId = foreignSaleItem.id,
                providerId = "PROVIDER_B",
                providerProductCode = "PULSA_B",
                destinationNumber = "089999999999",
                sellingPrice = 10000,
                actualPurchasePrice = 9000,
                status = "SUCCESS",
                createdAt = 1000000L,
                updatedAt = 1000100L
            )
        )

        val snapshot = backupRestoreManager.exportSnapshot()
        val tab = snapshot.getTab("19_DigitalTransactions")
        assertNotNull(tab)
        assertEquals("Only this tenant's row belongs in the archive", 1, tab!!.rows.size)
        assertEquals(own.uuid, tab.rows.first()[0])
        assertEquals(BUSINESS, tab.rows.first()[1])
        for (row in tab.rows) {
            assertTrue("No foreign row may appear", !row.contains(OTHER_BUSINESS))
        }

        // The same archive is valid, so the other tenant's presence changed nothing.
        runCatching { BackupValidator.validate(snapshot, BUSINESS) }
            .onFailure { throw AssertionError("This tenant's archive must validate, got $it") }
    }
}
