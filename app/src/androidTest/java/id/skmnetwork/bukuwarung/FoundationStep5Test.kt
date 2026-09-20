package id.skmnetwork.bukuwarung

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.skmnetwork.bukuwarung.backup.BackupException
import id.skmnetwork.bukuwarung.backup.BackupMetadata
import id.skmnetwork.bukuwarung.backup.BackupRestoreManager
import id.skmnetwork.bukuwarung.backup.BackupSnapshot
import id.skmnetwork.bukuwarung.backup.BackupValidator
import id.skmnetwork.bukuwarung.backup.CanonicalSerializer
import id.skmnetwork.bukuwarung.backup.ChecksumMismatchException
import id.skmnetwork.bukuwarung.backup.CorruptedBackupException
import id.skmnetwork.bukuwarung.backup.IncompatibleBackupFormatException
import id.skmnetwork.bukuwarung.backup.IncompatibleSchemaVersionException
import id.skmnetwork.bukuwarung.backup.MockSheetsTransport
import id.skmnetwork.bukuwarung.backup.SheetTab
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.local.entity.CategoryEntity
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.data.local.entity.SyncQueueEntity
import id.skmnetwork.bukuwarung.data.preferences.UserPreferencesRepository
import id.skmnetwork.bukuwarung.data.repository.CashRepository
import id.skmnetwork.bukuwarung.data.repository.CustomerRepository
import id.skmnetwork.bukuwarung.data.repository.ProductRepository
import id.skmnetwork.bukuwarung.data.repository.PurchaseRepository
import id.skmnetwork.bukuwarung.data.repository.SaleRepository
import id.skmnetwork.bukuwarung.data.repository.StockRepository
import id.skmnetwork.bukuwarung.data.repository.SupplierRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class FoundationStep5Test {

    private lateinit var database: AppDatabase
    private lateinit var context: Context
    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var mockTransport: MockSheetsTransport
    private lateinit var backupRestoreManager: BackupRestoreManager

    private lateinit var productRepository: ProductRepository
    private lateinit var saleRepository: SaleRepository
    private lateinit var purchaseRepository: PurchaseRepository
    private lateinit var customerRepository: CustomerRepository
    private lateinit var supplierRepository: SupplierRepository
    private lateinit var cashRepository: CashRepository
    private lateinit var stockRepository: StockRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        userPreferencesRepository = UserPreferencesRepository(context)
        mockTransport = MockSheetsTransport()
        backupRestoreManager = BackupRestoreManager(
            database = database,
            userPreferencesRepository = userPreferencesRepository,
            transport = mockTransport
        )

        productRepository = ProductRepository(database, "LEGACY_BUSINESS")
        saleRepository = SaleRepository(database, "LEGACY_BUSINESS")
        purchaseRepository = PurchaseRepository(database, "LEGACY_BUSINESS")
        customerRepository = CustomerRepository(database, "LEGACY_BUSINESS")
        supplierRepository = SupplierRepository(database, "LEGACY_BUSINESS")
        cashRepository = CashRepository(database, "LEGACY_BUSINESS")
        stockRepository = StockRepository(database, "LEGACY_BUSINESS")
    }

    @After
    fun tearDown() {
        database.close()
    }

    /**
     * Helper to populate standard realistic seed data.
     */
    private suspend fun seedStandardData() {
        userPreferencesRepository.setBusinessId("LEGACY_BUSINESS")
        val p1Id = productRepository.insertProductWithCategory(
            name = "Beras Rojolele 5kg",
            categoryName = "Sembako",
            purchasePrice = 60000,
            sellingPrice = 72000,
            stock = 20.0,
            minimumStock = 5.0,
            unit = "karung",
            barcode = "BRS001"
        )
        val p1 = productRepository.getProductById(p1Id)!!

        val p2Id = productRepository.insertProductWithCategory(
            name = "Minyak Goreng 2L",
            categoryName = "Sembako",
            purchasePrice = 28000,
            sellingPrice = 34000,
            stock = 15.0,
            minimumStock = 3.0,
            unit = "pouch",
            barcode = "MYK002"
        )
        val p2 = productRepository.getProductById(p2Id)!!

        val custId = customerRepository.saveCustomer("Pak Haji", "08123456789", "Jl. Melati 12")
        val supId = supplierRepository.saveSupplier("Distributor Sembako Jaya", "08987654321", "Jl. Industri 4")

        // 1. Cash Sale
        val saleRes1 = saleRepository.completeSale(
            cartItems = mapOf(p1Id to 2.0),
            paymentMethod = "CASH"
        )
        assertTrue(saleRes1.isSuccess)

        // 2. Credit Sale
        val saleRes2 = saleRepository.completeSale(
            cartItems = mapOf(p2Id to 3.0),
            paymentMethod = "CREDIT",
            customerId = custId
        )
        assertTrue(saleRes2.isSuccess)

        // 3. Purchase
        val purRes = purchaseRepository.completePurchase(
            purchaseItems = mapOf(p1Id to 10.0),
            paymentMethod = "CREDIT",
            supplierId = supId
        )
        assertTrue(purRes.isSuccess)

        // 4. Debt Payment
        val debts = customerRepository.getDebtsForCustomer(custId).first()
        if (debts.isNotEmpty()) {
            customerRepository.processAtomicDebtPayment(debts[0].id, 50000L, "Cicilan 1")
        }

        // 5. Supplier Payment
        val payables = supplierRepository.getPayablesForSupplier(supId).first()
        if (payables.isNotEmpty()) {
            supplierRepository.processAtomicSupplierPayment(payables[0].id, 200000L, "DP Pembelian")
        }

        // 6. Manual Cash Transaction
        cashRepository.recordManualExpense(
            amount = 15000,
            description = "Beli token listrik warung"
        )

        // 7. Stock Opname / Adjustment
        val p2After = productRepository.getProductById(p2Id)!!
        val newStock = p2After.stock + 2.0
        stockRepository.recordAdjustment(
            productUuid = p2.uuid,
            deltaQuantity = 2.0,
            currentStockSnapshot = newStock,
            note = "Stok tambahan ditemukan saat opname"
        )
        database.openHelper.writableDatabase.execSQL("UPDATE products SET stock = ? WHERE id = ?", arrayOf(newStock, p2Id))
    }

    @Test
    fun test01_deterministicCanonicalExport() = runBlocking {
        seedStandardData()

        val snapshot1 = backupRestoreManager.exportSnapshot()
        val snapshot2 = backupRestoreManager.exportSnapshot()

        // Checksum must be identical for the same dataset
        assertEquals(snapshot1.metadata.checksum, snapshot2.metadata.checksum)
        assertEquals(snapshot1.tabs.keys, snapshot2.tabs.keys)

        for (tabName in CanonicalSerializer.DATA_TAB_NAMES) {
            val tab1 = snapshot1.getTab(tabName)
            val tab2 = snapshot2.getTab(tabName)
            if (tab1 != null && tab2 != null) {
                assertEquals("Tab headers mismatch for $tabName", tab1.headers, tab2.headers)
                assertEquals("Tab rows count mismatch for $tabName", tab1.rows.size, tab2.rows.size)
                for (i in tab1.rows.indices) {
                    assertEquals("Row $i mismatch in tab $tabName", tab1.rows[i], tab2.rows[i])
                }
            } else {
                assertEquals("Tab presence mismatch for $tabName", tab1 != null, tab2 != null)
            }
        }
    }

    @Test
    fun test02_sha256ChecksumGenerationAndVerification() = runBlocking {
        seedStandardData()

        val snapshot = backupRestoreManager.exportSnapshot()
        val metadata = snapshot.metadata

        assertNotNull(metadata.checksum)
        assertTrue(metadata.checksum.length == 64) // SHA-256 is 64 hex characters

        val recomputed = CanonicalSerializer.calculateChecksum(snapshot.tabs)
        assertEquals(metadata.checksum, recomputed)
        assertTrue(CanonicalSerializer.verifyChecksum(metadata.checksum, snapshot.tabs))

        // Pre-validation should pass smoothly
        BackupValidator.validate(snapshot)
    }

    @Test
    fun test03_checksumTamperRejection() = runBlocking {
        seedStandardData()

        val snapshot = backupRestoreManager.exportSnapshot()

        // Tamper with a single cell in Products tab
        val productsTab = snapshot.getTab("04_Products")!!
        val modifiedRows = productsTab.rows.mapIndexed { idx, row ->
            if (idx == 0) {
                val modRow = row.toMutableList()
                modRow[3] = modRow[3] + " TAMPERED"
                modRow
            } else row
        }
        val tamperedProductsTab = productsTab.copy(rows = modifiedRows)
        val tamperedTabs = snapshot.tabs.toMutableMap()
        tamperedTabs["04_Products"] = tamperedProductsTab
        val tamperedSnapshot = snapshot.copy(tabs = tamperedTabs)

        try {
            BackupValidator.validate(tamperedSnapshot)
            fail("Expected ChecksumMismatchException was not thrown")
        } catch (e: ChecksumMismatchException) {
            assertTrue(e.message?.contains("Checksum mismatch") == true)
        }
    }

    @Test
    fun test04_formatVersionCompatibility() = runBlocking {
        seedStandardData()

        val snapshot = backupRestoreManager.exportSnapshot()
        val invalidFormatSnapshot = snapshot.copy(
            metadata = snapshot.metadata.copy(backupFormatVersion = "2.0")
        )

        try {
            BackupValidator.validate(invalidFormatSnapshot)
            fail("Expected IncompatibleBackupFormatException was not thrown")
        } catch (e: IncompatibleBackupFormatException) {
            assertTrue(e.message?.contains("Unsupported backup format version") == true)
        }
    }

    @Test
    fun test05_roomSchemaVersionCompatibility() = runBlocking {
        seedStandardData()

        val snapshot = backupRestoreManager.exportSnapshot()

        // Test older schema (e.g. 8)
        val olderSchemaSnapshot = snapshot.copy(
            metadata = snapshot.metadata.copy(roomSchemaVersion = 8)
        )
        try {
            BackupValidator.validate(olderSchemaSnapshot)
            fail("Expected IncompatibleSchemaVersionException for schema 8")
        } catch (e: IncompatibleSchemaVersionException) {
            assertTrue(e.message?.contains("Unsupported Room schema version") == true)
        }

        // Test future schema (e.g. 16)
        val futureSchemaSnapshot = snapshot.copy(
            metadata = snapshot.metadata.copy(roomSchemaVersion = 16)
        )
        try {
            BackupValidator.validate(futureSchemaSnapshot)
            fail("Expected IncompatibleSchemaVersionException for schema 16")
        } catch (e: IncompatibleSchemaVersionException) {
            assertTrue(e.message?.contains("Unsupported Room schema version") == true)
        }
    }

    @Test
    fun test06_immutableUuidPreservation() = runBlocking {
        seedStandardData()

        val originalProducts = database.productDao().getAllProducts("LEGACY_BUSINESS").first()
        val originalSales = database.saleDao().getAllTransactions("LEGACY_BUSINESS").first()
        val originalCustomers = database.customerDao().getAllCustomers("LEGACY_BUSINESS").first()
        val originalSuppliers = database.supplierDao().getAllSuppliers("LEGACY_BUSINESS").first()

        val snapshot = backupRestoreManager.exportSnapshot()

        // Clear local database in reverse foreign key order
        database.openHelper.writableDatabase.execSQL("DELETE FROM stock_movements")
        database.openHelper.writableDatabase.execSQL("DELETE FROM cash_transactions")
        database.openHelper.writableDatabase.execSQL("DELETE FROM supplier_payments")
        database.openHelper.writableDatabase.execSQL("DELETE FROM supplier_payables")
        database.openHelper.writableDatabase.execSQL("DELETE FROM debt_payments")
        database.openHelper.writableDatabase.execSQL("DELETE FROM debts")
        database.openHelper.writableDatabase.execSQL("DELETE FROM purchase_items")
        database.openHelper.writableDatabase.execSQL("DELETE FROM purchase_transactions")
        database.openHelper.writableDatabase.execSQL("DELETE FROM purchase_order_items")
        database.openHelper.writableDatabase.execSQL("DELETE FROM purchase_orders")
        database.openHelper.writableDatabase.execSQL("DELETE FROM digital_transactions")
        database.openHelper.writableDatabase.execSQL("DELETE FROM sale_items")
        database.openHelper.writableDatabase.execSQL("DELETE FROM sales_transactions")
        database.openHelper.writableDatabase.execSQL("DELETE FROM products")
        database.openHelper.writableDatabase.execSQL("DELETE FROM categories")
        database.openHelper.writableDatabase.execSQL("DELETE FROM customers")
        database.openHelper.writableDatabase.execSQL("DELETE FROM suppliers")

        val restoreResult = backupRestoreManager.restoreSnapshot(snapshot)
        assertTrue("Restore must succeed", restoreResult.isSuccess)

        val restoredProducts = database.productDao().getAllProducts("LEGACY_BUSINESS").first()
        val restoredSales = database.saleDao().getAllTransactions("LEGACY_BUSINESS").first()
        val restoredCustomers = database.customerDao().getAllCustomers("LEGACY_BUSINESS").first()
        val restoredSuppliers = database.supplierDao().getAllSuppliers("LEGACY_BUSINESS").first()

        assertEquals(originalProducts.map { it.uuid }.sorted(), restoredProducts.map { it.uuid }.sorted())
        assertEquals(originalSales.map { it.uuid }.sorted(), restoredSales.map { it.uuid }.sorted())
        assertEquals(originalCustomers.map { it.uuid }.sorted(), restoredCustomers.map { it.uuid }.sorted())
        assertEquals(originalSuppliers.map { it.uuid }.sorted(), restoredSuppliers.map { it.uuid }.sorted())
    }

    @Test
    fun test07_businessAndDeviceIdentityPreservation() = runBlocking {
        userPreferencesRepository.saveShopProfile(
            shopName = "Warung Berkah Jaya",
            ownerName = "Haji Subur",
            phone = "0811223344",
            address = "Pasar Induk No. 5"
        )

        seedStandardData()

        val snapshot = backupRestoreManager.exportSnapshot()
        val businessTab = snapshot.getTab("01_Business")!!
        val deviceTab = snapshot.getTab("02_Device")!!

        assertTrue(businessTab.rows.isNotEmpty())
        assertEquals("Warung Berkah Jaya", businessTab.rows[0][1])
        assertEquals("Haji Subur", businessTab.rows[0][2])
        assertEquals("0811223344", businessTab.rows[0][3])
        assertEquals("Pasar Induk No. 5", businessTab.rows[0][4])

        assertTrue(deviceTab.rows.isNotEmpty())
    }

    @Test
    fun test08_relationalGraphIntegrity() = runBlocking {
        seedStandardData()
        val snapshot = backupRestoreManager.exportSnapshot()

        // Tamper with a foreign key reference in sale items
        val saleItemsTab = snapshot.getTab("07_SaleItems")!!
        val brokenRows = saleItemsTab.rows.mapIndexed { idx, row ->
            if (idx == 0) {
                val mod = row.toMutableList()
                mod[3] = "NON_EXISTENT_PRODUCT_UUID" // product_uuid
                mod
            } else row
        }
        val brokenTab = saleItemsTab.copy(rows = brokenRows)
        val brokenTabs = snapshot.tabs.toMutableMap()
        brokenTabs["07_SaleItems"] = brokenTab
        val newChecksum = CanonicalSerializer.calculateChecksum(brokenTabs)
        val brokenSnapshot = snapshot.copy(
            metadata = snapshot.metadata.copy(checksum = newChecksum),
            tabs = brokenTabs
        )

        try {
            BackupValidator.validate(brokenSnapshot)
            fail("Expected CorruptedBackupException for broken relational graph")
        } catch (e: CorruptedBackupException) {
            assertTrue(e.message?.contains("references non-existent product_uuid") == true)
        }
    }

    @Test
    fun test09_duplicateUuidRejection() = runBlocking {
        seedStandardData()
        val snapshot = backupRestoreManager.exportSnapshot()

        val productsTab = snapshot.getTab("04_Products")!!
        if (productsTab.rows.isNotEmpty()) {
            val dupRows = productsTab.rows.toMutableList()
            dupRows.add(dupRows[0]) // Add exact duplicate row with same UUID
            val dupTab = productsTab.copy(rows = dupRows)
            val dupTabs = snapshot.tabs.toMutableMap()
            dupTabs["04_Products"] = dupTab
            val newChecksum = CanonicalSerializer.calculateChecksum(dupTabs)
            val dupSnapshot = snapshot.copy(
                metadata = snapshot.metadata.copy(checksum = newChecksum),
                tabs = dupTabs
            )

            try {
                BackupValidator.validate(dupSnapshot)
                fail("Expected CorruptedBackupException for duplicate UUID")
            } catch (e: CorruptedBackupException) {
                assertTrue(e.message?.contains("Duplicate primary UUID detected") == true)
            }
        }
    }

    @Test
    fun test10_tombstonePreservation() = runBlocking {
        val prodId = productRepository.insertProductWithCategory("Chiki Balls", "Makanan Ringan", 2000, 3000, 10.0, 2.0, "bungkus")
        val product = productRepository.getProductById(prodId)!!

        // Soft delete the product
        productRepository.deleteProductById(product.id)

        val snapshot = backupRestoreManager.exportSnapshot()
        val productsTab = snapshot.getTab("04_Products")!!
        val chikiRow = productsTab.rows.find { it[0] == product.uuid }
        assertNotNull(chikiRow)
        assertEquals("true", chikiRow!![10]) // is_deleted == true
        assertTrue(chikiRow[11] != "NULL") // deleted_at != NULL

        // Wipe and restore
        database.openHelper.writableDatabase.execSQL("DELETE FROM products")
        val restoreResult = backupRestoreManager.restoreSnapshot(snapshot)
        assertTrue(restoreResult.isSuccess)

        // Raw query to check tombstone persisted
        database.openHelper.readableDatabase.query("SELECT is_deleted, deleted_at FROM products WHERE uuid = ?", arrayOf(product.uuid)).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
            assertFalse(cursor.isNull(1))
        }
    }

    @Test
    fun test11_stockLedgerReconciliation() = runBlocking {
        seedStandardData()
        val snapshot = backupRestoreManager.exportSnapshot()

        // Corrupt stock movement delta sum so it does not match product stock
        val movementsTab = snapshot.getTab("16_StockMovements")!!
        val brokenRows = movementsTab.rows.mapIndexed { idx, row ->
            if (idx == 0) {
                val mod = row.toMutableList()
                mod[5] = "999.0" // Alter delta quantity
                mod
            } else row
        }
        val brokenTab = movementsTab.copy(rows = brokenRows)
        val brokenTabs = snapshot.tabs.toMutableMap()
        brokenTabs["16_StockMovements"] = brokenTab
        val newChecksum = CanonicalSerializer.calculateChecksum(brokenTabs)
        val brokenSnapshot = snapshot.copy(
            metadata = snapshot.metadata.copy(checksum = newChecksum),
            tabs = brokenTabs
        )

        try {
            BackupValidator.validate(brokenSnapshot)
            fail("Expected CorruptedBackupException for stock ledger mismatch")
        } catch (e: CorruptedBackupException) {
            assertTrue(e.message?.contains("Stock ledger invariant violation") == true)
        }
    }

    @Test
    fun test12_atomicRollbackOnFailure() = runBlocking {
        seedStandardData()
        val originalProductCount = database.productDao().getAllProducts("LEGACY_BUSINESS").first().size
        assertTrue(originalProductCount > 0)

        val snapshot = backupRestoreManager.exportSnapshot()

        // Create a snapshot with missing category UUID in product to trigger failure during insert
        val productsTab = snapshot.getTab("04_Products")!!
        val corruptedProducts = productsTab.rows.mapIndexed { idx, row ->
            if (idx == 0) {
                val mod = row.toMutableList()
                mod[2] = "CORRUPTED_CATEGORY_UUID"
                mod
            } else row
        }
        val brokenTab = productsTab.copy(rows = corruptedProducts)
        val brokenTabs = snapshot.tabs.toMutableMap()
        brokenTabs["04_Products"] = brokenTab
        val brokenSnapshot = snapshot.copy(
            metadata = snapshot.metadata.copy(checksum = CanonicalSerializer.calculateChecksum(brokenTabs)),
            tabs = brokenTabs
        )

        // Attempt restore
        val restoreResult = backupRestoreManager.restoreSnapshot(brokenSnapshot)
        assertTrue(restoreResult.isFailure)

        // Original database must remain intact (rollback occurred)
        val postProductCount = database.productDao().getAllProducts("LEGACY_BUSINESS").first().size
        assertEquals("Database should roll back to original state", originalProductCount, postProductCount)
    }

    @Test
    fun test13_syncQueueClearOnRestore() = runBlocking {
        seedStandardData()

        // Insert some pending and failed sync queue items
        val syncDao = database.syncQueueDao()
        syncDao.insert(
            SyncQueueEntity(
                syncId = "SYNC_TEST_1",
                businessId = "BIZ_1",
                deviceId = "DEV_1",
                entityType = "PRODUCT",
                entityUuid = "PROD_UUID_1",
                operation = "INSERT",
                status = "PENDING"
            )
        )
        syncDao.insert(
            SyncQueueEntity(
                syncId = "SYNC_TEST_2",
                businessId = "BIZ_1",
                deviceId = "DEV_1",
                entityType = "SALE",
                entityUuid = "SALE_UUID_2",
                operation = "INSERT",
                status = "FAILED"
            )
        )

        val pendingBefore = syncDao.getAllItems("LEGACY_BUSINESS").first().size
        assertTrue("Sync queue should have items before restore", pendingBefore > 0)

        val snapshot = backupRestoreManager.exportSnapshot()
        val restoreResult = backupRestoreManager.restoreSnapshot(snapshot)
        assertTrue(restoreResult.isSuccess)

        // sync_queue must be cleanly truncated
        val pendingAfter = syncDao.getAllItems("LEGACY_BUSINESS").first().size
        assertEquals(0, pendingAfter)
    }

    @Test
    fun test14_transportFailureIsolation() = runBlocking {
        seedStandardData()
        val originalProducts = database.productDao().getAllProducts("LEGACY_BUSINESS").first()

        mockTransport.simulateNetworkFailure = true
        val backupResult = backupRestoreManager.performBackup("SHEET_123")
        assertTrue(backupResult.isFailure)

        // Database remains unchanged
        val afterProducts = database.productDao().getAllProducts("LEGACY_BUSINESS").first()
        assertEquals(originalProducts.size, afterProducts.size)
    }

    @Test
    fun test15_crossStoreReconciliation() = runBlocking {
        seedStandardData()
        val snapshot = backupRestoreManager.exportSnapshot()

        val restoreResult = backupRestoreManager.restoreSnapshot(snapshot)
        assertTrue(restoreResult.isSuccess)

        // Simulate DataStore business_id becoming missing
        userPreferencesRepository.autoMigrateExistingUserIfNeeded(database)

        val settings = userPreferencesRepository.userSettings.first()
        assertTrue(settings.isSetupCompleted)
        assertTrue(settings.businessId.isNotBlank())
    }

    @Test
    fun test16_fullRoundtripBackupAndRestore() = runBlocking {
        seedStandardData()

        val pCount = database.productDao().getAllProducts("LEGACY_BUSINESS").first().size
        val sCount = database.saleDao().getAllTransactions("LEGACY_BUSINESS").first().size
        val purCount = database.purchaseDao().getAllPurchaseTransactions("LEGACY_BUSINESS").first().size
        val cCount = database.customerDao().getAllCustomers("LEGACY_BUSINESS").first().size
        val supCount = database.supplierDao().getAllSuppliers("LEGACY_BUSINESS").first().size

        val spreadsheetId = "SHEET_ROUNDTRIP_1001"
        val backupRes = backupRestoreManager.performBackup(spreadsheetId)
        assertTrue(backupRes.isSuccess)

        // Wipe database completely
        database.openHelper.writableDatabase.execSQL("DELETE FROM stock_movements")
        database.openHelper.writableDatabase.execSQL("DELETE FROM cash_transactions")
        database.openHelper.writableDatabase.execSQL("DELETE FROM supplier_payments")
        database.openHelper.writableDatabase.execSQL("DELETE FROM supplier_payables")
        database.openHelper.writableDatabase.execSQL("DELETE FROM debt_payments")
        database.openHelper.writableDatabase.execSQL("DELETE FROM debts")
        database.openHelper.writableDatabase.execSQL("DELETE FROM purchase_items")
        database.openHelper.writableDatabase.execSQL("DELETE FROM purchase_transactions")
        database.openHelper.writableDatabase.execSQL("DELETE FROM purchase_order_items")
        database.openHelper.writableDatabase.execSQL("DELETE FROM purchase_orders")
        database.openHelper.writableDatabase.execSQL("DELETE FROM digital_transactions")
        database.openHelper.writableDatabase.execSQL("DELETE FROM sale_items")
        database.openHelper.writableDatabase.execSQL("DELETE FROM sales_transactions")
        database.openHelper.writableDatabase.execSQL("DELETE FROM products")
        database.openHelper.writableDatabase.execSQL("DELETE FROM categories")
        database.openHelper.writableDatabase.execSQL("DELETE FROM customers")
        database.openHelper.writableDatabase.execSQL("DELETE FROM suppliers")

        assertEquals(0, database.productDao().getAllProducts("LEGACY_BUSINESS").first().size)

        // Restore from transport
        val restoreRes = backupRestoreManager.performRestore(spreadsheetId)
        assertTrue(restoreRes.isSuccess)

        // Assert 100% matched counts and state
        assertEquals(pCount, database.productDao().getAllProducts("LEGACY_BUSINESS").first().size)
        assertEquals(sCount, database.saleDao().getAllTransactions("LEGACY_BUSINESS").first().size)
        assertEquals(purCount, database.purchaseDao().getAllPurchaseTransactions("LEGACY_BUSINESS").first().size)
        assertEquals(cCount, database.customerDao().getAllCustomers("LEGACY_BUSINESS").first().size)
        assertEquals(supCount, database.supplierDao().getAllSuppliers("LEGACY_BUSINESS").first().size)
    }

    @Test
    fun test17_regressionSuite() = runBlocking {
        // Multi-domain transaction test ensuring all foundation domains remain fully operational
        val prodId = productRepository.insertProductWithCategory(
            name = "Produk Regression",
            categoryName = "Kategori Regression",
            purchasePrice = 10000,
            sellingPrice = 15000,
            stock = 50.0,
            minimumStock = 5.0,
            unit = "pcs"
        )
        val customerId = customerRepository.saveCustomer("Customer Regression", "0812345", "Alamat")

        // Sale
        val saleRes = saleRepository.completeSale(
            cartItems = mapOf(prodId to 5.0),
            paymentMethod = "CASH"
        )
        assertTrue(saleRes.isSuccess)

        // Verify stock ledger updated
        val updatedProduct = database.productDao().getProductById(prodId, "LEGACY_BUSINESS")!!
        assertEquals(45.0, updatedProduct.stock, 0.001)

        // Verify snapshot export works with newly created transactions
        val snapshot = backupRestoreManager.exportSnapshot()
        assertNotNull(snapshot.metadata.checksum)
        BackupValidator.validate(snapshot)
    }
}






