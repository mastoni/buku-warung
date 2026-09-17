package id.skmnetwork.bukuwarung.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import id.skmnetwork.bukuwarung.data.local.dao.CashDao
import id.skmnetwork.bukuwarung.data.local.dao.CategoryDao
import id.skmnetwork.bukuwarung.data.local.dao.CustomerDao
import id.skmnetwork.bukuwarung.data.local.dao.DebtDao
import id.skmnetwork.bukuwarung.data.local.dao.ProductDao
import id.skmnetwork.bukuwarung.data.local.dao.PurchaseDao
import id.skmnetwork.bukuwarung.data.local.dao.SaleDao
import id.skmnetwork.bukuwarung.data.local.dao.StockMovementDao
import id.skmnetwork.bukuwarung.data.local.dao.SupplierDao
import id.skmnetwork.bukuwarung.data.local.dao.SupplierPayableDao
import id.skmnetwork.bukuwarung.data.local.dao.SyncQueueDao
import id.skmnetwork.bukuwarung.data.local.entity.CashTransactionEntity
import id.skmnetwork.bukuwarung.data.local.entity.CategoryEntity
import id.skmnetwork.bukuwarung.data.local.entity.CustomerEntity
import id.skmnetwork.bukuwarung.data.local.entity.DebtEntity
import id.skmnetwork.bukuwarung.data.local.entity.DebtPaymentEntity
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.data.local.entity.PurchaseItemEntity
import id.skmnetwork.bukuwarung.data.local.entity.PurchaseTransactionEntity
import id.skmnetwork.bukuwarung.data.local.entity.SaleItemEntity
import id.skmnetwork.bukuwarung.data.local.entity.SaleTransactionEntity
import id.skmnetwork.bukuwarung.data.local.entity.StockMovementEntity
import id.skmnetwork.bukuwarung.data.local.entity.SupplierEntity
import id.skmnetwork.bukuwarung.data.local.entity.SupplierPayableEntity
import id.skmnetwork.bukuwarung.data.local.entity.SupplierPaymentEntity
import id.skmnetwork.bukuwarung.data.local.dao.PurchaseOrderDao
import id.skmnetwork.bukuwarung.data.local.dao.SaleReturnDao
import id.skmnetwork.bukuwarung.data.local.entity.PurchaseOrderEntity
import id.skmnetwork.bukuwarung.data.local.entity.PurchaseOrderItemEntity
import id.skmnetwork.bukuwarung.data.local.entity.SaleReturnItemEntity
import id.skmnetwork.bukuwarung.data.local.entity.SaleReturnTransactionEntity
import id.skmnetwork.bukuwarung.data.local.entity.SyncQueueEntity

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `sales_transactions` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `transaction_number` TEXT NOT NULL,
                `transaction_date` INTEGER NOT NULL,
                `total_amount` INTEGER NOT NULL,
                `payment_method` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `sale_items` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `transaction_id` INTEGER NOT NULL,
                `product_id` INTEGER NOT NULL,
                `product_name` TEXT NOT NULL,
                `quantity` REAL NOT NULL,
                `price` INTEGER NOT NULL,
                `subtotal` INTEGER NOT NULL,
                FOREIGN KEY(`transaction_id`) REFERENCES `sales_transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`product_id`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sale_items_transaction_id` ON `sale_items` (`transaction_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sale_items_product_id` ON `sale_items` (`product_id`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `cash_transactions` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `type` TEXT NOT NULL,
                `amount` INTEGER NOT NULL,
                `description` TEXT NOT NULL,
                `ref_id` INTEGER,
                `created_at` INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `purchase_transactions` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `transaction_number` TEXT NOT NULL,
                `transaction_date` INTEGER NOT NULL,
                `total_amount` INTEGER NOT NULL,
                `created_at` INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `purchase_items` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `transaction_id` INTEGER NOT NULL,
                `product_id` INTEGER NOT NULL,
                `product_name` TEXT NOT NULL,
                `quantity` REAL NOT NULL,
                `purchase_price` INTEGER NOT NULL,
                `subtotal` INTEGER NOT NULL,
                FOREIGN KEY(`transaction_id`) REFERENCES `purchase_transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`product_id`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_items_transaction_id` ON `purchase_items` (`transaction_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_items_product_id` ON `purchase_items` (`product_id`)")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `customers` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `phone` TEXT,
                `address` TEXT,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            ALTER TABLE `sales_transactions`
            ADD COLUMN `customer_id` INTEGER
            REFERENCES `customers`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
            """.trimIndent()
        )

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_transactions_customer_id` ON `sales_transactions` (`customer_id`)")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `debts` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `customer_id` INTEGER NOT NULL,
                `sale_transaction_id` INTEGER,
                `total_debt` INTEGER NOT NULL,
                `paid_amount` INTEGER NOT NULL,
                `status` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                FOREIGN KEY(`customer_id`) REFERENCES `customers`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`sale_transaction_id`) REFERENCES `sales_transactions`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_debts_customer_id` ON `debts` (`customer_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_debts_sale_transaction_id` ON `debts` (`sale_transaction_id`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `debt_payments` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `debt_id` INTEGER NOT NULL,
                `amount` INTEGER NOT NULL,
                `payment_date` INTEGER NOT NULL,
                `note` TEXT,
                `created_at` INTEGER NOT NULL,
                FOREIGN KEY(`debt_id`) REFERENCES `debts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_debt_payments_debt_id` ON `debt_payments` (`debt_id`)")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `suppliers` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `phone` TEXT,
                `address` TEXT,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            ALTER TABLE `purchase_transactions`
            ADD COLUMN `supplier_id` INTEGER
            REFERENCES `suppliers`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_transactions_supplier_id` ON `purchase_transactions` (`supplier_id`)")

        db.execSQL(
            """
            ALTER TABLE `purchase_transactions`
            ADD COLUMN `payment_method` TEXT NOT NULL DEFAULT 'CASH'
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `supplier_payables` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `supplier_id` INTEGER NOT NULL,
                `purchase_transaction_id` INTEGER,
                `total_debt` INTEGER NOT NULL,
                `paid_amount` INTEGER NOT NULL,
                `status` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                FOREIGN KEY(`supplier_id`) REFERENCES `suppliers`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`purchase_transaction_id`) REFERENCES `purchase_transactions`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_supplier_payables_supplier_id` ON `supplier_payables` (`supplier_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_supplier_payables_purchase_transaction_id` ON `supplier_payables` (`purchase_transaction_id`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `supplier_payments` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `payable_id` INTEGER NOT NULL,
                `amount` INTEGER NOT NULL,
                `payment_date` INTEGER NOT NULL,
                `note` TEXT,
                `created_at` INTEGER NOT NULL,
                FOREIGN KEY(`payable_id`) REFERENCES `supplier_payables`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_supplier_payments_payable_id` ON `supplier_payments` (`payable_id`)")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `products` ADD COLUMN `barcode` TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_barcode` ON `products` (`barcode`)")
        db.execSQL("ALTER TABLE `products` ADD COLUMN `image_uri` TEXT")
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Categories
        db.execSQL("ALTER TABLE `categories` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `categories` ADD COLUMN `business_id` TEXT NOT NULL DEFAULT 'LEGACY_BUSINESS'")
        db.execSQL("ALTER TABLE `categories` ADD COLUMN `is_deleted` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `categories` ADD COLUMN `deleted_at` INTEGER")
        backfillUuids(db, "categories")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_uuid` ON `categories` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_categories_business_id` ON `categories` (`business_id`)")

        // 2. Products
        db.execSQL("ALTER TABLE `products` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `products` ADD COLUMN `business_id` TEXT NOT NULL DEFAULT 'LEGACY_BUSINESS'")
        db.execSQL("ALTER TABLE `products` ADD COLUMN `item_type` TEXT NOT NULL DEFAULT 'PHYSICAL'")
        db.execSQL("ALTER TABLE `products` ADD COLUMN `is_deleted` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `products` ADD COLUMN `deleted_at` INTEGER")
        backfillUuids(db, "products")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_products_uuid` ON `products` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_business_id` ON `products` (`business_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_is_deleted` ON `products` (`is_deleted`)")

        // 3. Customers
        db.execSQL("ALTER TABLE `customers` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `customers` ADD COLUMN `business_id` TEXT NOT NULL DEFAULT 'LEGACY_BUSINESS'")
        db.execSQL("ALTER TABLE `customers` ADD COLUMN `is_deleted` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `customers` ADD COLUMN `deleted_at` INTEGER")
        backfillUuids(db, "customers")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_customers_uuid` ON `customers` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_customers_business_id` ON `customers` (`business_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_customers_is_deleted` ON `customers` (`is_deleted`)")

        // 4. Suppliers
        db.execSQL("ALTER TABLE `suppliers` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `suppliers` ADD COLUMN `business_id` TEXT NOT NULL DEFAULT 'LEGACY_BUSINESS'")
        db.execSQL("ALTER TABLE `suppliers` ADD COLUMN `is_deleted` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `suppliers` ADD COLUMN `deleted_at` INTEGER")
        backfillUuids(db, "suppliers")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_suppliers_uuid` ON `suppliers` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_suppliers_business_id` ON `suppliers` (`business_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_suppliers_is_deleted` ON `suppliers` (`is_deleted`)")

        // 5. Sales Transactions
        db.execSQL("ALTER TABLE `sales_transactions` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `sales_transactions` ADD COLUMN `business_id` TEXT NOT NULL DEFAULT 'LEGACY_BUSINESS'")
        db.execSQL("ALTER TABLE `sales_transactions` ADD COLUMN `device_id` TEXT NOT NULL DEFAULT 'LEGACY_DEVICE'")
        backfillUuids(db, "sales_transactions")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sales_transactions_uuid` ON `sales_transactions` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_transactions_business_id` ON `sales_transactions` (`business_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_transactions_transaction_date` ON `sales_transactions` (`transaction_date`)")

        // 6. Sale Items
        db.execSQL("ALTER TABLE `sale_items` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `sale_items` ADD COLUMN `business_id` TEXT NOT NULL DEFAULT 'LEGACY_BUSINESS'")
        db.execSQL("ALTER TABLE `sale_items` ADD COLUMN `sale_uuid` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `sale_items` ADD COLUMN `product_uuid` TEXT NOT NULL DEFAULT ''")
        backfillUuids(db, "sale_items")
        db.execSQL("UPDATE `sale_items` SET `sale_uuid` = COALESCE((SELECT `uuid` FROM `sales_transactions` WHERE `sales_transactions`.`id` = `sale_items`.`transaction_id`), '')")
        db.execSQL("UPDATE `sale_items` SET `product_uuid` = COALESCE((SELECT `uuid` FROM `products` WHERE `products`.`id` = `sale_items`.`product_id`), '')")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sale_items_uuid` ON `sale_items` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sale_items_sale_uuid` ON `sale_items` (`sale_uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sale_items_product_uuid` ON `sale_items` (`product_uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sale_items_business_id` ON `sale_items` (`business_id`)")

        // 7. Purchase Transactions
        db.execSQL("ALTER TABLE `purchase_transactions` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `purchase_transactions` ADD COLUMN `business_id` TEXT NOT NULL DEFAULT 'LEGACY_BUSINESS'")
        db.execSQL("ALTER TABLE `purchase_transactions` ADD COLUMN `device_id` TEXT NOT NULL DEFAULT 'LEGACY_DEVICE'")
        backfillUuids(db, "purchase_transactions")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_purchase_transactions_uuid` ON `purchase_transactions` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_transactions_business_id` ON `purchase_transactions` (`business_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_transactions_transaction_date` ON `purchase_transactions` (`transaction_date`)")

        // 8. Purchase Items
        db.execSQL("ALTER TABLE `purchase_items` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `purchase_items` ADD COLUMN `business_id` TEXT NOT NULL DEFAULT 'LEGACY_BUSINESS'")
        db.execSQL("ALTER TABLE `purchase_items` ADD COLUMN `purchase_uuid` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `purchase_items` ADD COLUMN `product_uuid` TEXT NOT NULL DEFAULT ''")
        backfillUuids(db, "purchase_items")
        db.execSQL("UPDATE `purchase_items` SET `purchase_uuid` = COALESCE((SELECT `uuid` FROM `purchase_transactions` WHERE `purchase_transactions`.`id` = `purchase_items`.`transaction_id`), '')")
        db.execSQL("UPDATE `purchase_items` SET `product_uuid` = COALESCE((SELECT `uuid` FROM `products` WHERE `products`.`id` = `purchase_items`.`product_id`), '')")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_purchase_items_uuid` ON `purchase_items` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_items_purchase_uuid` ON `purchase_items` (`purchase_uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_items_product_uuid` ON `purchase_items` (`product_uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_items_business_id` ON `purchase_items` (`business_id`)")

        // 9. Cash Transactions
        db.execSQL("ALTER TABLE `cash_transactions` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `cash_transactions` ADD COLUMN `business_id` TEXT NOT NULL DEFAULT 'LEGACY_BUSINESS'")
        db.execSQL("ALTER TABLE `cash_transactions` ADD COLUMN `device_id` TEXT NOT NULL DEFAULT 'LEGACY_DEVICE'")
        db.execSQL("ALTER TABLE `cash_transactions` ADD COLUMN `ref_uuid` TEXT")
        backfillUuids(db, "cash_transactions")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_cash_transactions_uuid` ON `cash_transactions` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_cash_transactions_business_id` ON `cash_transactions` (`business_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_cash_transactions_ref_id` ON `cash_transactions` (`ref_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_cash_transactions_ref_uuid` ON `cash_transactions` (`ref_uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_cash_transactions_created_at` ON `cash_transactions` (`created_at`)")

        // 10. Debts
        db.execSQL("ALTER TABLE `debts` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `debts` ADD COLUMN `business_id` TEXT NOT NULL DEFAULT 'LEGACY_BUSINESS'")
        db.execSQL("ALTER TABLE `debts` ADD COLUMN `customer_uuid` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `debts` ADD COLUMN `sale_uuid` TEXT NOT NULL DEFAULT ''")
        backfillUuids(db, "debts")
        db.execSQL("UPDATE `debts` SET `customer_uuid` = COALESCE((SELECT `uuid` FROM `customers` WHERE `customers`.`id` = `debts`.`customer_id`), '')")
        db.execSQL("UPDATE `debts` SET `sale_uuid` = COALESCE((SELECT `uuid` FROM `sales_transactions` WHERE `sales_transactions`.`id` = `debts`.`sale_transaction_id`), '')")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_debts_uuid` ON `debts` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_debts_customer_uuid` ON `debts` (`customer_uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_debts_sale_uuid` ON `debts` (`sale_uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_debts_business_id` ON `debts` (`business_id`)")

        // 11. Debt Payments
        db.execSQL("ALTER TABLE `debt_payments` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `debt_payments` ADD COLUMN `business_id` TEXT NOT NULL DEFAULT 'LEGACY_BUSINESS'")
        db.execSQL("ALTER TABLE `debt_payments` ADD COLUMN `device_id` TEXT NOT NULL DEFAULT 'LEGACY_DEVICE'")
        db.execSQL("ALTER TABLE `debt_payments` ADD COLUMN `debt_uuid` TEXT NOT NULL DEFAULT ''")
        backfillUuids(db, "debt_payments")
        db.execSQL("UPDATE `debt_payments` SET `debt_uuid` = COALESCE((SELECT `uuid` FROM `debts` WHERE `debts`.`id` = `debt_payments`.`debt_id`), '')")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_debt_payments_uuid` ON `debt_payments` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_debt_payments_debt_uuid` ON `debt_payments` (`debt_uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_debt_payments_business_id` ON `debt_payments` (`business_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_debt_payments_payment_date` ON `debt_payments` (`payment_date`)")

        // 12. Supplier Payables
        db.execSQL("ALTER TABLE `supplier_payables` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `supplier_payables` ADD COLUMN `business_id` TEXT NOT NULL DEFAULT 'LEGACY_BUSINESS'")
        db.execSQL("ALTER TABLE `supplier_payables` ADD COLUMN `supplier_uuid` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `supplier_payables` ADD COLUMN `purchase_uuid` TEXT NOT NULL DEFAULT ''")
        backfillUuids(db, "supplier_payables")
        db.execSQL("UPDATE `supplier_payables` SET `supplier_uuid` = COALESCE((SELECT `uuid` FROM `suppliers` WHERE `suppliers`.`id` = `supplier_payables`.`supplier_id`), '')")
        db.execSQL("UPDATE `supplier_payables` SET `purchase_uuid` = COALESCE((SELECT `uuid` FROM `purchase_transactions` WHERE `purchase_transactions`.`id` = `supplier_payables`.`purchase_transaction_id`), '')")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_supplier_payables_uuid` ON `supplier_payables` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_supplier_payables_supplier_uuid` ON `supplier_payables` (`supplier_uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_supplier_payables_purchase_uuid` ON `supplier_payables` (`purchase_uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_supplier_payables_business_id` ON `supplier_payables` (`business_id`)")

        // 13. Supplier Payments
        db.execSQL("ALTER TABLE `supplier_payments` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `supplier_payments` ADD COLUMN `business_id` TEXT NOT NULL DEFAULT 'LEGACY_BUSINESS'")
        db.execSQL("ALTER TABLE `supplier_payments` ADD COLUMN `device_id` TEXT NOT NULL DEFAULT 'LEGACY_DEVICE'")
        db.execSQL("ALTER TABLE `supplier_payments` ADD COLUMN `payable_uuid` TEXT NOT NULL DEFAULT ''")
        backfillUuids(db, "supplier_payments")
        db.execSQL("UPDATE `supplier_payments` SET `payable_uuid` = COALESCE((SELECT `uuid` FROM `supplier_payables` WHERE `supplier_payables`.`id` = `supplier_payments`.`payable_id`), '')")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_supplier_payments_uuid` ON `supplier_payments` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_supplier_payments_payable_uuid` ON `supplier_payments` (`payable_uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_supplier_payments_business_id` ON `supplier_payments` (`business_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_supplier_payments_payment_date` ON `supplier_payments` (`payment_date`)")

        // 14. Create Stock Movements Table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `stock_movements` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `business_id` TEXT NOT NULL,
                `device_id` TEXT NOT NULL,
                `product_uuid` TEXT NOT NULL,
                `movement_type` TEXT NOT NULL,
                `delta_quantity` REAL NOT NULL,
                `current_stock_snapshot` REAL NOT NULL,
                `reference_uuid` TEXT,
                `note` TEXT,
                `created_at` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_stock_movements_uuid` ON `stock_movements` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_movements_product_uuid` ON `stock_movements` (`product_uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_movements_business_id` ON `stock_movements` (`business_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_movements_created_at` ON `stock_movements` (`created_at`)")

        // 15. Backfill INITIAL StockMovements for ALL products
        val productCursor = db.query("SELECT `uuid`, `stock` FROM `products`")
        val now = System.currentTimeMillis()
        val insertMovementStmt = db.compileStatement(
            "INSERT INTO `stock_movements` (`uuid`, `business_id`, `device_id`, `product_uuid`, `movement_type`, `delta_quantity`, `current_stock_snapshot`, `reference_uuid`, `note`, `created_at`) VALUES (?, 'LEGACY_BUSINESS', 'LEGACY_DEVICE', ?, 'INITIAL', ?, ?, NULL, 'Migrasi baseline stok Room v7->v8', ?)"
        )
        productCursor.use { cursor ->
            val uuidIdx = cursor.getColumnIndex("uuid")
            val stockIdx = cursor.getColumnIndex("stock")
            while (cursor.moveToNext()) {
                val prodUuid = cursor.getString(uuidIdx)
                val prodStock = cursor.getDouble(stockIdx)
                val movUuid = java.util.UUID.randomUUID().toString()

                insertMovementStmt.bindString(1, movUuid)
                insertMovementStmt.bindString(2, prodUuid)
                insertMovementStmt.bindDouble(3, prodStock)
                insertMovementStmt.bindDouble(4, prodStock)
                insertMovementStmt.bindLong(5, now)
                insertMovementStmt.executeInsert()
                insertMovementStmt.clearBindings()
            }
        }
    }

    private fun backfillUuids(db: SupportSQLiteDatabase, tableName: String) {
        val cursor = db.query("SELECT `id` FROM `$tableName` WHERE `uuid` = '' OR `uuid` IS NULL")
        val updateStmt = db.compileStatement("UPDATE `$tableName` SET `uuid` = ? WHERE `id` = ?")
        cursor.use { c ->
            val idIdx = c.getColumnIndex("id")
            while (c.moveToNext()) {
                val rowId = c.getLong(idIdx)
                val newUuid = java.util.UUID.randomUUID().toString()
                updateStmt.bindString(1, newUuid)
                updateStmt.bindLong(2, rowId)
                updateStmt.executeUpdateDelete()
                updateStmt.clearBindings()
            }
        }
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `sync_queue` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `sync_id` TEXT NOT NULL,
                `business_id` TEXT NOT NULL,
                `device_id` TEXT NOT NULL,
                `entity_type` TEXT NOT NULL,
                `entity_uuid` TEXT NOT NULL,
                `operation` TEXT NOT NULL,
                `payload_json` TEXT,
                `status` TEXT NOT NULL DEFAULT 'PENDING',
                `attempt_count` INTEGER NOT NULL DEFAULT 0,
                `last_error` TEXT,
                `next_attempt_at` INTEGER NOT NULL,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sync_queue_sync_id` ON `sync_queue` (`sync_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_queue_status` ON `sync_queue` (`status`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_queue_business_id` ON `sync_queue` (`business_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_queue_created_at` ON `sync_queue` (`created_at`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_queue_entity_type_entity_uuid` ON `sync_queue` (`entity_type`, `entity_uuid`)")
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `sale_return_transactions` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `business_id` TEXT NOT NULL,
                `device_id` TEXT NOT NULL,
                `return_number` TEXT NOT NULL,
                `return_date` INTEGER NOT NULL,
                `sale_transaction_id` INTEGER NOT NULL,
                `sale_uuid` TEXT NOT NULL,
                `customer_id` INTEGER,
                `customer_uuid` TEXT,
                `total_refund_amount` INTEGER NOT NULL,
                `refund_method` TEXT NOT NULL,
                `reason` TEXT,
                `notes` TEXT,
                `created_at` INTEGER NOT NULL,
                FOREIGN KEY(`sale_transaction_id`) REFERENCES `sales_transactions`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`customer_id`) REFERENCES `customers`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sale_return_transactions_uuid` ON `sale_return_transactions` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sale_return_transactions_sale_transaction_id` ON `sale_return_transactions` (`sale_transaction_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sale_return_transactions_sale_uuid` ON `sale_return_transactions` (`sale_uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sale_return_transactions_customer_id` ON `sale_return_transactions` (`customer_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sale_return_transactions_customer_uuid` ON `sale_return_transactions` (`customer_uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sale_return_transactions_business_id` ON `sale_return_transactions` (`business_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sale_return_transactions_return_date` ON `sale_return_transactions` (`return_date`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `sale_return_items` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `business_id` TEXT NOT NULL,
                `return_uuid` TEXT NOT NULL,
                `sale_item_uuid` TEXT NOT NULL,
                `product_uuid` TEXT NOT NULL,
                `return_transaction_id` INTEGER NOT NULL,
                `sale_item_id` INTEGER NOT NULL,
                `product_id` INTEGER NOT NULL,
                `product_name` TEXT NOT NULL,
                `quantity` REAL NOT NULL,
                `price` INTEGER NOT NULL,
                `subtotal` INTEGER NOT NULL,
                `created_at` INTEGER NOT NULL,
                FOREIGN KEY(`return_transaction_id`) REFERENCES `sale_return_transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`sale_item_id`) REFERENCES `sale_items`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`product_id`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sale_return_items_uuid` ON `sale_return_items` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sale_return_items_return_transaction_id` ON `sale_return_items` (`return_transaction_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sale_return_items_sale_item_id` ON `sale_return_items` (`sale_item_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sale_return_items_product_id` ON `sale_return_items` (`product_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sale_return_items_return_uuid` ON `sale_return_items` (`return_uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sale_return_items_sale_item_uuid` ON `sale_return_items` (`sale_item_uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sale_return_items_product_uuid` ON `sale_return_items` (`product_uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sale_return_items_business_id` ON `sale_return_items` (`business_id`)")
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Add purchase_price column to sale_items
        db.execSQL("ALTER TABLE `sale_items` ADD COLUMN `purchase_price` INTEGER NOT NULL DEFAULT 0")

        // 2. Populate legacy purchase_price from products table if available
        db.execSQL(
            """
            UPDATE `sale_items` 
            SET `purchase_price` = (
                SELECT `purchase_price` FROM `products` WHERE `products`.`id` = `sale_items`.`product_id`
            ) 
            WHERE `purchase_price` = 0 AND `product_id` IN (SELECT `id` FROM `products`)
            """.trimIndent()
        )

        // 3. Add purchase_price column to sale_return_items
        db.execSQL("ALTER TABLE `sale_return_items` ADD COLUMN `purchase_price` INTEGER NOT NULL DEFAULT 0")

        // 4. Populate legacy purchase_price from sale_items if available
        db.execSQL(
            """
            UPDATE `sale_return_items` 
            SET `purchase_price` = (
                SELECT `purchase_price` FROM `sale_items` WHERE `sale_items`.`id` = `sale_return_items`.`sale_item_id`
            ) 
            WHERE `purchase_price` = 0 AND `sale_item_id` IN (SELECT `id` FROM `sale_items`)
            """.trimIndent()
        )
    }
}

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            ALTER TABLE `sales_transactions`
            ADD COLUMN `discount_amount` INTEGER NOT NULL DEFAULT 0
            """.trimIndent()
        )
    }
}

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `purchase_orders` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `business_id` TEXT NOT NULL,
                `device_id` TEXT NOT NULL,
                `order_number` TEXT NOT NULL,
                `supplier_id` INTEGER NOT NULL,
                `supplier_name_snapshot` TEXT NOT NULL,
                `supplier_phone_snapshot` TEXT,
                `status` TEXT NOT NULL,
                `total_estimated_amount` INTEGER NOT NULL,
                `notes` TEXT,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                `sent_at` INTEGER,
                `received_at` INTEGER,
                `final_purchase_id` INTEGER,
                FOREIGN KEY(`supplier_id`) REFERENCES `suppliers`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_purchase_orders_uuid` ON `purchase_orders` (`uuid`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_purchase_orders_order_number` ON `purchase_orders` (`order_number`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_orders_supplier_id` ON `purchase_orders` (`supplier_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_orders_status` ON `purchase_orders` (`status`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_orders_business_id` ON `purchase_orders` (`business_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_orders_created_at` ON `purchase_orders` (`created_at`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `purchase_order_items` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `business_id` TEXT NOT NULL,
                `purchase_order_id` INTEGER NOT NULL,
                `po_uuid` TEXT NOT NULL,
                `product_id` INTEGER NOT NULL,
                `product_uuid` TEXT NOT NULL,
                `product_name` TEXT NOT NULL,
                `ordered_quantity` REAL NOT NULL,
                `unit` TEXT NOT NULL,
                `estimated_price` INTEGER NOT NULL,
                `estimated_subtotal` INTEGER NOT NULL,
                `received_quantity` REAL NOT NULL,
                `notes` TEXT,
                FOREIGN KEY(`purchase_order_id`) REFERENCES `purchase_orders`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`product_id`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_purchase_order_items_uuid` ON `purchase_order_items` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_order_items_purchase_order_id` ON `purchase_order_items` (`purchase_order_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_order_items_po_uuid` ON `purchase_order_items` (`po_uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_order_items_product_id` ON `purchase_order_items` (`product_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_order_items_product_uuid` ON `purchase_order_items` (`product_uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_order_items_business_id` ON `purchase_order_items` (`business_id`)")
    }
}

@Database(
    entities = [
        CategoryEntity::class,
        ProductEntity::class,
        SaleTransactionEntity::class,
        SaleItemEntity::class,
        CashTransactionEntity::class,
        PurchaseTransactionEntity::class,
        PurchaseItemEntity::class,
        CustomerEntity::class,
        DebtEntity::class,
        DebtPaymentEntity::class,
        SupplierEntity::class,
        SupplierPayableEntity::class,
        SupplierPaymentEntity::class,
        StockMovementEntity::class,
        SyncQueueEntity::class,
        SaleReturnTransactionEntity::class,
        SaleReturnItemEntity::class,
        PurchaseOrderEntity::class,
        PurchaseOrderItemEntity::class
    ],
    version = 13,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun categoryDao(): CategoryDao
    abstract fun productDao(): ProductDao
    abstract fun saleDao(): SaleDao
    abstract fun cashDao(): CashDao
    abstract fun purchaseDao(): PurchaseDao
    abstract fun customerDao(): CustomerDao
    abstract fun debtDao(): DebtDao
    abstract fun supplierDao(): SupplierDao
    abstract fun supplierPayableDao(): SupplierPayableDao
    abstract fun stockMovementDao(): StockMovementDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun saleReturnDao(): SaleReturnDao
    abstract fun purchaseOrderDao(): PurchaseOrderDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "buku_warung_db"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10,
                        MIGRATION_10_11,
                        MIGRATION_11_12,
                        MIGRATION_12_13
                    )
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
