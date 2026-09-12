package id.skmnetwork.bukuwarung

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.local.database.MIGRATION_1_2
import id.skmnetwork.bukuwarung.data.local.database.MIGRATION_2_3
import id.skmnetwork.bukuwarung.data.local.database.MIGRATION_3_4
import id.skmnetwork.bukuwarung.data.local.database.MIGRATION_4_5
import id.skmnetwork.bukuwarung.data.local.database.MIGRATION_5_6
import id.skmnetwork.bukuwarung.data.local.database.MIGRATION_6_7
import id.skmnetwork.bukuwarung.data.local.database.MIGRATION_7_8
import id.skmnetwork.bukuwarung.data.local.database.MIGRATION_8_9
import id.skmnetwork.bukuwarung.data.local.database.MIGRATION_9_10
import id.skmnetwork.bukuwarung.data.local.database.MIGRATION_10_11
import id.skmnetwork.bukuwarung.data.local.entity.SyncQueueEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomMigrationTest {

    private val dbName = "real_v7_v8_migration_test_db"
    private val dbNameV8V9 = "real_v8_v9_migration_test_db"
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
        context.deleteDatabase(dbNameV8V9)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
        context.deleteDatabase(dbNameV8V9)
    }

    @Test
    fun testRealMigrationV7ToV8PreservesAllDataAndCreatesStockLedger() : Unit = runBlocking {
        // Step 1: Create a REAL File-Backed Version 7 Database
        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
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

        // Step 2: Insert Historical Data into V7 Database
        v7Db.execSQL("INSERT INTO categories (id, name, created_at) VALUES (1, 'Sembako', 1700000000000)")
        v7Db.execSQL("INSERT INTO products (id, category_id, name, purchase_price, selling_price, stock, minimum_stock, unit, created_at, updated_at, barcode, image_uri) VALUES (1, 1, 'Beras 5kg', 50000, 65000, 15.0, 2.0, 'karung', 1700000000000, 1700000000000, '8991234567890', null)")
        v7Db.execSQL("INSERT INTO products (id, category_id, name, purchase_price, selling_price, stock, minimum_stock, unit, created_at, updated_at, barcode, image_uri) VALUES (2, 1, 'Minyak Goreng', 14000, 16000, 0.0, 5.0, 'liter', 1700000000000, 1700000000000, null, null)")
        v7Db.execSQL("INSERT INTO customers (id, name, phone, address, created_at, updated_at) VALUES (1, 'Ibu Siti', '08123456789', 'Jl. Kenanga 12', 1700000000000, 1700000000000)")
        v7Db.execSQL("INSERT INTO suppliers (id, name, phone, address, created_at, updated_at) VALUES (1, 'Grosir Beras Jaya', '08567890123', 'Pasar Induk', 1700000000000, 1700000000000)")
        v7Db.execSQL("INSERT INTO sales_transactions (id, transaction_number, transaction_date, total_amount, payment_method, created_at, customer_id) VALUES (1, 'TRX-V7-001', 1700000000000, 65000, 'CASH', 1700000000000, NULL)")
        v7Db.execSQL("INSERT INTO sale_items (id, transaction_id, product_id, product_name, quantity, price, subtotal) VALUES (1, 1, 1, 'Beras 5kg', 1.0, 65000, 65000)")
        v7Db.execSQL("INSERT INTO cash_transactions (id, type, amount, description, ref_id, created_at) VALUES (1, 'INCOME', 65000, 'Penjualan TRX-V7-001', 1, 1700000000000)")

        // Close V7 Database
        v7Db.close()
        helper.close()

        // Step 3: Upgrade Database using AppDatabase Version 11 + Migrations
        val v11Database = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            dbName
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
            .allowMainThreadQueries()
            .build()

        // Step 4: Verify ALL Historical Data was Preserved and Augmented with UUIDs
        val category = v11Database.categoryDao().getCategoryById(1)
        assertNotNull("Category v7 must exist in v11", category)
        assertEquals("Sembako", category?.name)
        assertTrue("Category uuid must not be empty", category?.uuid?.isNotEmpty() == true)
        assertFalse("Category is_deleted must be false", category?.isDeleted ?: true)

        val product1 = v11Database.productDao().getProductById(1)
        assertNotNull("Product 1 must exist in v11", product1)
        assertEquals("Beras 5kg", product1?.name)
        assertEquals(15.0, product1?.stock ?: 0.0, 0.001)
        assertEquals("PHYSICAL", product1?.itemType)
        assertTrue("Product uuid must be valid", product1?.uuid?.isNotEmpty() == true)
        assertFalse("Product is_deleted must be false", product1?.isDeleted ?: true)

        val product2 = v11Database.productDao().getProductById(2)
        assertNotNull("Product 2 must exist in v11", product2)
        assertEquals(0.0, product2?.stock ?: 0.0, 0.001)

        val customer = v11Database.customerDao().getCustomerById(1)
        assertNotNull("Customer must exist in v11", customer)
        assertEquals("Ibu Siti", customer?.name)
        assertTrue("Customer uuid must not be empty", customer?.uuid?.isNotEmpty() == true)

        val supplier = v11Database.supplierDao().getSupplierById(1)
        assertNotNull("Supplier must exist in v11", supplier)
        assertEquals("Grosir Beras Jaya", supplier?.name)

        val salesList = v11Database.saleDao().getAllTransactions().first()
        assertEquals(1, salesList.size)
        assertEquals("TRX-V7-001", salesList[0].transactionNumber)
        assertTrue("Sale uuid must not be empty", salesList[0].uuid.isNotEmpty())

        // Step 5: Verify StockMovement INITIAL Records were Created for Every Product
        val movementsCount = v11Database.stockMovementDao().getMovementCount()
        assertEquals("Must have 2 initial stock movements for 2 products", 2, movementsCount)

        val p1Movements = v11Database.stockMovementDao().getMovementsListForProduct(product1!!.uuid)
        assertEquals(1, p1Movements.size)
        assertEquals("INITIAL", p1Movements[0].movementType)
        assertEquals(15.0, p1Movements[0].deltaQuantity, 0.001)

        // Step 6: Verify Stock Invariant: SUM(deltaQuantity) == Product.stock
        val p1Calculated = v11Database.stockMovementDao().getCalculatedStockForProduct(product1.uuid)
        assertEquals(product1.stock, p1Calculated, 0.001)

        val p2Calculated = v11Database.stockMovementDao().getCalculatedStockForProduct(product2!!.uuid)
        assertEquals(product2.stock, p2Calculated, 0.001)

        // Step 7: PRAGMA Foreign Key Check Result
        val cursorFk = v11Database.openHelper.writableDatabase.query("PRAGMA foreign_key_check;")
        val fkViolationCount = cursorFk.count
        cursorFk.close()
        assertEquals("Foreign key violations count must be 0", 0, fkViolationCount)

        v11Database.close()
    }

    @Test
    fun testRealMigrationV8ToV9CreatesSyncQueueAndPreservesData() : Unit = runBlocking {
        // Step 1: Create a REAL File-Backed Database at Version 7
        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbNameV8V9)
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

        // Step 2: Insert historical v7 data
        v7Db.execSQL("INSERT INTO categories (id, name, created_at) VALUES (1, 'Minuman', 1700000000000)")
        v7Db.execSQL("INSERT INTO products (id, category_id, name, purchase_price, selling_price, stock, minimum_stock, unit, created_at, updated_at, barcode, image_uri) VALUES (1, 1, 'Teh Botol', 3000, 5000, 24.0, 5.0, 'botol', 1700000000000, 1700000000000, null, null)")

        // Step 3: Run MIGRATION_7_8 to reach authentic Version 8
        MIGRATION_7_8.migrate(v7Db)
        v7Db.version = 8

        // Close Database
        v7Db.close()
        helper.close()

        // Step 4: Upgrade from Version 8 to Version 11 using Migrations
        val v11Database = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            dbNameV8V9
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
            .allowMainThreadQueries()
            .build()

        // Step 5: Verify Product & Category data intact
        val prod = v11Database.productDao().getProductById(1)
        assertNotNull("Product must exist after v11 upgrade", prod)
        assertEquals("Teh Botol", prod?.name)
        assertEquals(24.0, prod?.stock ?: 0.0, 0.001)

        // Step 6: Verify sync_queue operations work
        val syncQueueDao = v11Database.syncQueueDao()
        val queueItem = SyncQueueEntity(
            syncId = "test-sync-uuid-1",
            businessId = "LEGACY_BUSINESS",
            deviceId = "LEGACY_DEVICE",
            entityType = "PRODUCT",
            entityUuid = prod!!.uuid,
            operation = "INSERT",
            createdAt = 1700000000000,
            updatedAt = 1700000000000
        )
        val insertedId = syncQueueDao.insert(queueItem)
        assertTrue("SyncQueue insert should succeed and return positive id", insertedId > 0)

        val fetched = syncQueueDao.getItemById(insertedId)
        assertNotNull("Fetched sync queue item must exist", fetched)
        assertEquals("test-sync-uuid-1", fetched?.syncId)
        assertEquals("PENDING", fetched?.status)

        // Step 7: Verify PRAGMA foreign key check
        val cursorFk = v11Database.openHelper.writableDatabase.query("PRAGMA foreign_key_check;")
        val fkViolationCount = cursorFk.count
        cursorFk.close()
        assertEquals("Foreign key violations count must be 0", 0, fkViolationCount)

        v11Database.close()
    }

    @Test
    fun testRealMigrationV9ToV10CreatesSaleReturnTablesAndPreservesData() : Unit = runBlocking {
        val dbNameV9V10 = "real_v9_v10_migration_test_db"
        context.deleteDatabase(dbNameV9V10)

        // Step 1: Create a REAL File-Backed Database at Version 7
        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbNameV9V10)
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

        // Step 2: Insert historical v7 data
        v7Db.execSQL("INSERT INTO categories (id, name, created_at) VALUES (1, 'Makanan', 1700000000000)")
        v7Db.execSQL("INSERT INTO products (id, category_id, name, purchase_price, selling_price, stock, minimum_stock, unit, created_at, updated_at, barcode, image_uri) VALUES (1, 1, 'Biskuit', 5000, 7000, 10.0, 2.0, 'pcs', 1700000000000, 1700000000000, null, null)")

        // Step 3: Run MIGRATION_7_8 and MIGRATION_8_9 to reach Version 9
        MIGRATION_7_8.migrate(v7Db)
        MIGRATION_8_9.migrate(v7Db)
        v7Db.version = 9

        // Close Database
        v7Db.close()
        helper.close()

        // Step 4: Upgrade from Version 9 to Version 11 using MIGRATION_9_10 and MIGRATION_10_11
        val v11Database = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            dbNameV9V10
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
            .allowMainThreadQueries()
            .build()

        // Step 5: Verify Product & Category data intact
        val prod = v11Database.productDao().getProductById(1)
        assertNotNull("Product must exist after v11 upgrade", prod)
        assertEquals("Biskuit", prod?.name)
        assertEquals(10.0, prod?.stock ?: 0.0, 0.001)

        // Step 6: Verify sale_return_transactions and sale_return_items tables exist and work
        val returnDao = v11Database.saleReturnDao()
        val allReturns = returnDao.getAllReturnsList()
        assertEquals(0, allReturns.size)

        // Step 7: Verify PRAGMA foreign key check
        val cursorFk = v11Database.openHelper.writableDatabase.query("PRAGMA foreign_key_check;")
        val fkViolationCount = cursorFk.count
        cursorFk.close()
        assertEquals("Foreign key violations count must be 0", 0, fkViolationCount)

        v11Database.close()
        context.deleteDatabase(dbNameV9V10)
    }

    @Test
    fun testRealMigrationV10ToV11PreservesDataAndPopulatesPurchasePrice() : Unit = runBlocking {
        val dbNameV10V11 = "real_v10_v11_migration_test_db"
        context.deleteDatabase(dbNameV10V11)

        // Step 1: Create a REAL File-Backed Database at Version 7
        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbNameV10V11)
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

        // Step 2: Insert product and legacy sale item
        v7Db.execSQL("INSERT INTO categories (id, name, created_at) VALUES (1, 'Sembako', 1700000000000)")
        v7Db.execSQL("INSERT INTO products (id, category_id, name, purchase_price, selling_price, stock, minimum_stock, unit, created_at, updated_at) VALUES (1, 1, 'Gula 1kg', 12000, 15000, 10.0, 2.0, 'kg', 1700000000000, 1700000000000)")
        v7Db.execSQL("INSERT INTO sales_transactions (id, transaction_number, transaction_date, total_amount, payment_method, created_at) VALUES (1, 'TRX-V7-002', 1700000000000, 15000, 'CASH', 1700000000000)")
        v7Db.execSQL("INSERT INTO sale_items (id, transaction_id, product_id, product_name, quantity, price, subtotal) VALUES (1, 1, 1, 'Gula 1kg', 1.0, 15000, 15000)")

        // Step 3: Run migrations up to v10
        MIGRATION_7_8.migrate(v7Db)
        MIGRATION_8_9.migrate(v7Db)
        MIGRATION_9_10.migrate(v7Db)
        v7Db.version = 10

        // Close Database
        v7Db.close()
        helper.close()

        // Step 4: Upgrade from Version 10 to Version 11 using MIGRATION_10_11
        val v11Database = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            dbNameV10V11
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
            .allowMainThreadQueries()
            .build()

        // Step 5: Verify legacy sale item purchase_price was populated from products table (12000)
        val items = v11Database.saleDao().getItemsForTransaction(1)
        assertEquals(1, items.size)
        assertEquals("Gula 1kg", items[0].productName)
        assertEquals(15000L, items[0].price)
        assertEquals(12000L, items[0].purchasePrice)

        // Step 6: Verify PRAGMA foreign key check
        val cursorFk = v11Database.openHelper.writableDatabase.query("PRAGMA foreign_key_check;")
        val fkViolationCount = cursorFk.count
        cursorFk.close()
        assertEquals("Foreign key violations count must be 0", 0, fkViolationCount)

        v11Database.close()
        context.deleteDatabase(dbNameV10V11)
    }

    @Test
    fun testFullMigrationChainV7ThroughV11() : Unit = runBlocking {
        val dbNameChain = "real_v7_v11_full_chain_migration_test_db"
        context.deleteDatabase(dbNameChain)

        // Step 1: Create a REAL File-Backed Database at Version 7
        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbNameChain)
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

        v7Db.execSQL("INSERT INTO categories (id, name, created_at) VALUES (1, 'Kategori 1', 1700000000000)")
        v7Db.execSQL("INSERT INTO products (id, category_id, name, purchase_price, selling_price, stock, minimum_stock, unit, created_at, updated_at) VALUES (1, 1, 'Produk Chain', 20000, 25000, 50.0, 5.0, 'pcs', 1700000000000, 1700000000000)")
        v7Db.execSQL("INSERT INTO sales_transactions (id, transaction_number, transaction_date, total_amount, payment_method, created_at) VALUES (1, 'TRX-CHAIN-001', 1700000000000, 25000, 'CASH', 1700000000000)")
        v7Db.execSQL("INSERT INTO sale_items (id, transaction_id, product_id, product_name, quantity, price, subtotal) VALUES (1, 1, 1, 'Produk Chain', 1.0, 25000, 25000)")

        // Close Database
        v7Db.close()
        helper.close()

        // Apply complete migration chain 7 -> 8 -> 9 -> 10 -> 11 directly through Room
        val v11Database = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            dbNameChain
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
            .allowMainThreadQueries()
            .build()

        val item = v11Database.saleDao().getItemsForTransaction(1)[0]
        assertEquals(20000L, item.purchasePrice)
        assertEquals(25000L, item.price)

        val cursorFk = v11Database.openHelper.writableDatabase.query("PRAGMA foreign_key_check;")
        assertEquals(0, cursorFk.count)
        cursorFk.close()

        v11Database.close()
        context.deleteDatabase(dbNameChain)
    }
}
