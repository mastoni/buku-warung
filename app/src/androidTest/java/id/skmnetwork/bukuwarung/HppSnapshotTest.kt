package id.skmnetwork.bukuwarung

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.skmnetwork.bukuwarung.backup.BackupRestoreManager
import id.skmnetwork.bukuwarung.backup.MockSheetsTransport
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.local.database.MIGRATION_10_11
import id.skmnetwork.bukuwarung.data.local.database.MIGRATION_1_2
import id.skmnetwork.bukuwarung.data.local.database.MIGRATION_2_3
import id.skmnetwork.bukuwarung.data.local.database.MIGRATION_3_4
import id.skmnetwork.bukuwarung.data.local.database.MIGRATION_4_5
import id.skmnetwork.bukuwarung.data.local.database.MIGRATION_5_6
import id.skmnetwork.bukuwarung.data.local.database.MIGRATION_6_7
import id.skmnetwork.bukuwarung.data.local.database.MIGRATION_7_8
import id.skmnetwork.bukuwarung.data.local.database.MIGRATION_8_9
import id.skmnetwork.bukuwarung.data.local.database.MIGRATION_9_10
import id.skmnetwork.bukuwarung.data.local.entity.ItemType
import id.skmnetwork.bukuwarung.data.local.entity.SaleReturnItemEntity
import id.skmnetwork.bukuwarung.data.local.entity.SaleReturnTransactionEntity
import id.skmnetwork.bukuwarung.data.preferences.UserPreferencesRepository
import id.skmnetwork.bukuwarung.data.repository.ProductRepository
import id.skmnetwork.bukuwarung.data.repository.ReportRepository
import id.skmnetwork.bukuwarung.data.repository.SaleRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HppSnapshotTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var productRepository: ProductRepository
    private lateinit var saleRepository: SaleRepository
    private lateinit var reportRepository: ReportRepository
    private lateinit var userPreferencesRepository: UserPreferencesRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        productRepository = ProductRepository(database)
        saleRepository = SaleRepository(database)
        reportRepository = ReportRepository(database)
        userPreferencesRepository = UserPreferencesRepository(context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun test01_productHppSnapshotOnSale() : Unit = runBlocking {
        val prodId = productRepository.insertProductWithCategory(
            name = "Kopi Sachet",
            categoryName = "Minuman",
            purchasePrice = 6000L,
            sellingPrice = 10000L,
            stock = 20.0,
            minimumStock = 2.0,
            unit = "renceng"
        )

        val saleRes = saleRepository.completeSale(
            cartItems = mapOf(prodId to 1.0),
            paymentMethod = "CASH"
        )
        assertTrue(saleRes.isSuccess)
        val saleId = saleRes.getOrThrow()

        val items = database.saleDao().getItemsForTransaction(saleId)
        assertEquals(1, items.size)
        assertEquals(10000L, items[0].price)
        assertEquals(6000L, items[0].purchasePrice)
    }

    @Test
    fun test02_and_test03_historicalHppRemainsUnchangedAfterMasterProductPriceEdit() : Unit = runBlocking {
        // 1. Initial product with HPP 6.000
        val prodId = productRepository.insertProductWithCategory(
            name = "Mie Instan",
            categoryName = "Makanan",
            purchasePrice = 6000L,
            sellingPrice = 10000L,
            stock = 50.0,
            minimumStock = 5.0,
            unit = "bungkus"
        )

        // 2. Sale 1 item at HPP 6.000
        val sale1Res = saleRepository.completeSale(
            cartItems = mapOf(prodId to 1.0),
            paymentMethod = "CASH"
        )
        val sale1Id = sale1Res.getOrThrow()

        // 3. Update master Product purchasePrice to 7.000
        val prod = database.productDao().getProductById(prodId)!!
        productRepository.updateProductWithCategory(
            productId = prodId,
            name = prod.name,
            categoryName = "Makanan",
            purchasePrice = 7000L,
            sellingPrice = 12000L,
            stock = prod.stock,
            minimumStock = prod.minimumStock,
            unit = prod.unit
        )

        // 4. TEST 2: Old sale item HPP must REMAIN 6.000
        val oldSaleItems = database.saleDao().getItemsForTransaction(sale1Id)
        assertEquals(1, oldSaleItems.size)
        assertEquals(6000L, oldSaleItems[0].purchasePrice)
        assertEquals(10000L, oldSaleItems[0].price)

        // 5. TEST 3: New sale after master price update gets new HPP 7.000
        val sale2Res = saleRepository.completeSale(
            cartItems = mapOf(prodId to 2.0),
            paymentMethod = "CASH"
        )
        val sale2Id = sale2Res.getOrThrow()

        val newSaleItems = database.saleDao().getItemsForTransaction(sale2Id)
        assertEquals(1, newSaleItems.size)
        assertEquals(7000L, newSaleItems[0].purchasePrice)
        assertEquals(12000L, newSaleItems[0].price)
    }

    @Test
    fun test04_and_test05_reportCogsUsesHistoricalSnapshot() : Unit = runBlocking {
        val now = System.currentTimeMillis()
        val prodId = productRepository.insertProductWithCategory(
            name = "Susu UHT",
            categoryName = "Minuman",
            purchasePrice = 6000L,
            sellingPrice = 10000L,
            stock = 30.0,
            minimumStock = 5.0,
            unit = "kotak"
        )

        // Sale 1: 2 pcs @ HPP 6.000 = 12.000
        saleRepository.completeSale(
            cartItems = mapOf(prodId to 2.0),
            paymentMethod = "CASH",
            now = now
        )

        // Check report COGS for sale 1 (TEST 4)
        val cogs1 = reportRepository.getSaleCogsTotal(now - 1000, now + 1000).first()
        assertEquals(12000L, cogs1)

        // Update product HPP to 7.000
        val prod = database.productDao().getProductById(prodId)!!
        productRepository.updateProductWithCategory(
            productId = prodId,
            name = prod.name,
            categoryName = "Minuman",
            purchasePrice = 7000L,
            sellingPrice = 11000L,
            stock = prod.stock,
            minimumStock = prod.minimumStock,
            unit = prod.unit
        )

        // Sale 2: 3 pcs @ HPP 7.000 = 21.000
        saleRepository.completeSale(
            cartItems = mapOf(prodId to 3.0),
            paymentMethod = "CASH",
            now = now + 500
        )

        // Combined COGS (TEST 5): 12.000 (old sale) + 21.000 (new sale) = 33.000
        val totalCogs = reportRepository.getSaleCogsTotal(now - 1000, now + 1000).first()
        assertEquals(33000L, totalCogs)
    }

    @Test
    fun test06_returnUsesOriginalSaleSnapshotHppEvenIfProductHppChanged() : Unit = runBlocking {
        val now = System.currentTimeMillis()
        val prodId = productRepository.insertProductWithCategory(
            name = "Teh Celup",
            categoryName = "Minuman",
            purchasePrice = 6000L,
            sellingPrice = 10000L,
            stock = 20.0,
            minimumStock = 2.0,
            unit = "kotak"
        )

        val saleRes = saleRepository.completeSale(
            cartItems = mapOf(prodId to 2.0),
            paymentMethod = "CASH",
            now = now
        )
        val saleId = saleRes.getOrThrow()
        val saleItem = database.saleDao().getItemsForTransaction(saleId)[0]

        // Update master product HPP to 7.000
        val prod = database.productDao().getProductById(prodId)!!
        productRepository.updateProductWithCategory(
            productId = prodId,
            name = prod.name,
            categoryName = "Minuman",
            purchasePrice = 7000L,
            sellingPrice = 11000L,
            stock = prod.stock,
            minimumStock = prod.minimumStock,
            unit = prod.unit
        )

        // Execute return of 1 item
        val retRes = saleRepository.processSaleReturn(
            saleId = saleId,
            itemsToReturn = mapOf(saleItem.id to 1.0)
        )
        assertTrue(retRes.isSuccess)
        val retId = retRes.getOrThrow()

        val returnItems = database.saleReturnDao().getItemsForReturn(retId)
        assertEquals(1, returnItems.size)
        assertEquals("Return item purchasePrice must equal original sale item purchasePrice (6.000)", 6000L, returnItems[0].purchasePrice)

        // Return COGS = 1 * 6000 = 6000
        val returnCogs = reportRepository.getReturnCogsTotal(now - 10000, now + 10000).first()
        assertEquals(6000L, returnCogs)

        // Net COGS = (2 * 6000) - (1 * 6000) = 6000
        val netCogs = reportRepository.getNetCogsTotal(now - 10000, now + 10000).first()
        assertEquals(6000L, netCogs)
    }

    @Test
    fun test07_partialReturnCogsCalculation() : Unit = runBlocking {
        val now = System.currentTimeMillis()
        val prodId = productRepository.insertProductWithCategory(
            name = "Biskuit Gandum",
            categoryName = "Makanan",
            purchasePrice = 5000L,
            sellingPrice = 8000L,
            stock = 20.0,
            minimumStock = 2.0,
            unit = "bungkus"
        )

        // Sale qty 5 @ HPP 5.000 = COGS 25.000
        val saleRes = saleRepository.completeSale(
            cartItems = mapOf(prodId to 5.0),
            paymentMethod = "CASH",
            now = now
        )
        val saleId = saleRes.getOrThrow()
        val saleItem = database.saleDao().getItemsForTransaction(saleId)[0]

        // Return qty 2 -> Return COGS = 2 * 5.000 = 10.000
        val retRes = saleRepository.processSaleReturn(
            saleId = saleId,
            itemsToReturn = mapOf(saleItem.id to 2.0)
        )
        assertTrue(retRes.isSuccess)

        val returnCogs = reportRepository.getReturnCogsTotal(now - 10000, now + 10000).first()
        assertEquals(10000L, returnCogs)

        val netCogs = reportRepository.getNetCogsTotal(now - 10000, now + 10000).first()
        assertEquals(15000L, netCogs) // 25.000 - 10.000 = 15.000
    }

    @Test
    fun test08_fullReturnCogsNetBecomesZero() : Unit = runBlocking {
        val now = System.currentTimeMillis()
        val prodId = productRepository.insertProductWithCategory(
            name = "Kacang Kulit",
            categoryName = "Makanan",
            purchasePrice = 4000L,
            sellingPrice = 6000L,
            stock = 10.0,
            minimumStock = 2.0,
            unit = "bungkus"
        )

        // Sale 3 pcs
        val saleRes = saleRepository.completeSale(
            cartItems = mapOf(prodId to 3.0),
            paymentMethod = "CASH",
            now = now
        )
        val saleId = saleRes.getOrThrow()
        val saleItem = database.saleDao().getItemsForTransaction(saleId)[0]

        // Full return 3 pcs
        val retRes = saleRepository.processSaleReturn(
            saleId = saleId,
            itemsToReturn = mapOf(saleItem.id to 3.0)
        )
        assertTrue(retRes.isSuccess)

        val netCogs = reportRepository.getNetCogsTotal(now - 10000, now + 10000).first()
        assertEquals(0L, netCogs)

        val netSales = reportRepository.getNetSalesTotal(now - 10000, now + 10000).first()
        assertEquals(0L, netSales)

        val grossProfit = reportRepository.getGrossProfitTotal(now - 10000, now + 10000).first()
        assertEquals(0L, grossProfit)
    }

    @Test
    fun test09_historicalSaleRemainsUnchangedAfterProductPriceEdit() : Unit = runBlocking {
        val prodId = productRepository.insertProductWithCategory(
            name = "Minyak Kelapa",
            categoryName = "Sembako",
            purchasePrice = 15000L,
            sellingPrice = 20000L,
            stock = 10.0,
            minimumStock = 2.0,
            unit = "botol"
        )

        val saleRes = saleRepository.completeSale(
            cartItems = mapOf(prodId to 1.0),
            paymentMethod = "CASH"
        )
        val saleId = saleRes.getOrThrow()

        // Edit product price multiple times
        productRepository.updateProductWithCategory(prodId, "Minyak Kelapa", "Sembako", 18000L, 23000L, 9.0, 2.0, "botol")
        productRepository.updateProductWithCategory(prodId, "Minyak Kelapa", "Sembako", 22000L, 28000L, 9.0, 2.0, "botol")

        val items = database.saleDao().getItemsForTransaction(saleId)
        assertEquals(15000L, items[0].purchasePrice)
        assertEquals(20000L, items[0].price)
    }

    @Test
    fun test10_and_test11_backupRestorePreservesSaleAndReturnPurchasePrice() : Unit = runBlocking {
        val prodId = productRepository.insertProductWithCategory(
            name = "Kecap Asin",
            categoryName = "Sembako",
            purchasePrice = 8000L,
            sellingPrice = 12000L,
            stock = 15.0,
            minimumStock = 2.0,
            unit = "botol"
        )

        val saleRes = saleRepository.completeSale(
            cartItems = mapOf(prodId to 2.0),
            paymentMethod = "CASH"
        )
        val saleId = saleRes.getOrThrow()
        val saleItem = database.saleDao().getItemsForTransaction(saleId)[0]

        val retRes = saleRepository.processSaleReturn(
            saleId = saleId,
            itemsToReturn = mapOf(saleItem.id to 1.0)
        )
        assertTrue(retRes.isSuccess)

        val mockTransport = MockSheetsTransport()
        val backupManager = BackupRestoreManager(
            database = database,
            userPreferencesRepository = userPreferencesRepository,
            transport = mockTransport
        )

        // Export Snapshot
        val snapshot = backupManager.exportSnapshot()

        // Create fresh database and restore
        val restoredDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val restoreManager = BackupRestoreManager(
            database = restoredDb,
            userPreferencesRepository = userPreferencesRepository,
            transport = mockTransport
        )

        val restoreRes = restoreManager.restoreSnapshot(snapshot)
        assertTrue("Restore must succeed", restoreRes.isSuccess)

        // TEST 10: Assert restored sale item preserves purchase_price
        val restoredSaleItems = restoredDb.saleDao().getItemsForTransaction(saleId)
        assertEquals(1, restoredSaleItems.size)
        assertEquals(8000L, restoredSaleItems[0].purchasePrice)
        assertEquals(12000L, restoredSaleItems[0].price)

        // TEST 11: Assert restored return item preserves purchase_price
        val restoredReturns = restoredDb.saleReturnDao().getAllReturnsList()
        assertEquals(1, restoredReturns.size)
        val restoredReturnItems = restoredDb.saleReturnDao().getItemsForReturn(restoredReturns[0].id)
        assertEquals(1, restoredReturnItems.size)
        assertEquals(8000L, restoredReturnItems[0].purchasePrice)
        assertEquals(12000L, restoredReturnItems[0].price)

        restoredDb.close()
    }

    @Test
    fun test12_and_test13_migrationChainV7ThroughV11() : Unit = runBlocking {
        val testDbName = "hpp_snapshot_chain_test_db"
        context.deleteDatabase(testDbName)

        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(testDbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(7) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS `categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `created_at` INTEGER NOT NULL)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `products` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `category_id` INTEGER NOT NULL, `name` TEXT NOT NULL, `purchase_price` INTEGER NOT NULL, `selling_price` INTEGER NOT NULL, `stock` REAL NOT NULL, `minimum_stock` REAL NOT NULL, `unit` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `barcode` TEXT, `image_uri` TEXT, FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_category_id` ON `products` (`category_id`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_barcode` ON `products` (`barcode`)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `customers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `phone` TEXT, `address` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `sales_transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `transaction_number` TEXT NOT NULL, `transaction_date` INTEGER NOT NULL, `total_amount` INTEGER NOT NULL, `payment_method` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `customer_id` INTEGER, FOREIGN KEY(`customer_id`) REFERENCES `customers`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_transactions_customer_id` ON `sales_transactions` (`customer_id`)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `sale_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `transaction_id` INTEGER NOT NULL, `product_id` INTEGER NOT NULL, `product_name` TEXT NOT NULL, `quantity` REAL NOT NULL, `price` INTEGER NOT NULL, `subtotal` INTEGER NOT NULL, FOREIGN KEY(`transaction_id`) REFERENCES `sales_transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`product_id`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_sale_items_transaction_id` ON `sale_items` (`transaction_id`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_sale_items_product_id` ON `sale_items` (`product_id`)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `cash_transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL, `amount` INTEGER NOT NULL, `description` TEXT NOT NULL, `ref_id` INTEGER, `created_at` INTEGER NOT NULL)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `suppliers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `phone` TEXT, `address` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `purchase_transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `transaction_number` TEXT NOT NULL, `transaction_date` INTEGER NOT NULL, `total_amount` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `supplier_id` INTEGER, `payment_method` TEXT NOT NULL DEFAULT 'CASH', FOREIGN KEY(`supplier_id`) REFERENCES `suppliers`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_transactions_supplier_id` ON `purchase_transactions` (`supplier_id`)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `purchase_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `transaction_id` INTEGER NOT NULL, `product_id` INTEGER NOT NULL, `product_name` TEXT NOT NULL, `quantity` REAL NOT NULL, `purchase_price` INTEGER NOT NULL, `subtotal` INTEGER NOT NULL, FOREIGN KEY(`transaction_id`) REFERENCES `purchase_transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`product_id`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_items_transaction_id` ON `purchase_items` (`transaction_id`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_items_product_id` ON `purchase_items` (`product_id`)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `debts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `customer_id` INTEGER NOT NULL, `sale_transaction_id` INTEGER, `total_debt` INTEGER NOT NULL, `paid_amount` INTEGER NOT NULL, `status` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, FOREIGN KEY(`customer_id`) REFERENCES `customers`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`sale_transaction_id`) REFERENCES `sales_transactions`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_debts_customer_id` ON `debts` (`customer_id`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_debts_sale_transaction_id` ON `debts` (`sale_transaction_id`)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `debt_payments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `debt_id` INTEGER NOT NULL, `amount` INTEGER NOT NULL, `payment_date` INTEGER NOT NULL, `note` TEXT, `created_at` INTEGER NOT NULL, FOREIGN KEY(`debt_id`) REFERENCES `debts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_debt_payments_debt_id` ON `debt_payments` (`debt_id`)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `supplier_payables` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `supplier_id` INTEGER NOT NULL, `purchase_transaction_id` INTEGER, `total_debt` INTEGER NOT NULL, `paid_amount` INTEGER NOT NULL, `status` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, FOREIGN KEY(`supplier_id`) REFERENCES `suppliers`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`purchase_transaction_id`) REFERENCES `purchase_transactions`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_supplier_payables_supplier_id` ON `supplier_payables` (`supplier_id`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_supplier_payables_purchase_transaction_id` ON `supplier_payables` (`purchase_transaction_id`)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `supplier_payments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `payable_id` INTEGER NOT NULL, `amount` INTEGER NOT NULL, `payment_date` INTEGER NOT NULL, `note` TEXT, `created_at` INTEGER NOT NULL, FOREIGN KEY(`payable_id`) REFERENCES `supplier_payables`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_supplier_payments_payable_id` ON `supplier_payments` (`payable_id`)")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = factory.create(config)
        val v7Db = helper.writableDatabase

        v7Db.execSQL("INSERT INTO categories (id, name, created_at) VALUES (1, 'Kategori Test', 1700000000000)")
        v7Db.execSQL("INSERT INTO products (id, category_id, name, purchase_price, selling_price, stock, minimum_stock, unit, created_at, updated_at) VALUES (1, 1, 'Produk Migrasi', 11000, 15000, 20.0, 2.0, 'pcs', 1700000000000, 1700000000000)")
        v7Db.execSQL("INSERT INTO sales_transactions (id, transaction_number, transaction_date, total_amount, payment_method, created_at) VALUES (1, 'TRX-V7-999', 1700000000000, 15000, 'CASH', 1700000000000)")
        v7Db.execSQL("INSERT INTO sale_items (id, transaction_id, product_id, product_name, quantity, price, subtotal) VALUES (1, 1, 1, 'Produk Migrasi', 1.0, 15000, 15000)")

        v7Db.close()
        helper.close()

        val v11Db = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            testDbName
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
            .allowMainThreadQueries()
            .build()

        val items = v11Db.saleDao().getItemsForTransaction(1)
        assertEquals(1, items.size)
        assertEquals(11000L, items[0].purchasePrice)
        assertEquals(15000L, items[0].price)

        val cursorFk = v11Db.openHelper.writableDatabase.query("PRAGMA foreign_key_check;")
        assertEquals(0, cursorFk.count)
        cursorFk.close()

        v11Db.close()
        context.deleteDatabase(testDbName)
    }
}
