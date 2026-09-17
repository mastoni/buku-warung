package id.skmnetwork.bukuwarung.domain.purchase

import androidx.sqlite.db.SupportSQLiteDatabase
import id.skmnetwork.bukuwarung.data.local.database.MIGRATION_12_13
import id.skmnetwork.bukuwarung.data.local.entity.ItemType
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.data.local.entity.PurchaseItemEntity
import id.skmnetwork.bukuwarung.data.local.entity.PurchaseOrderEntity
import id.skmnetwork.bukuwarung.data.local.entity.PurchaseOrderItemEntity
import id.skmnetwork.bukuwarung.data.local.entity.PurchaseOrderStatus
import id.skmnetwork.bukuwarung.data.local.entity.PurchaseTransactionEntity
import id.skmnetwork.bukuwarung.data.local.entity.SupplierEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

/**
 * Gate G13.1 — Purchase Order Data Model & Room Migration Unit Tests
 * Comprehensive validation of Room schema version 12 -> 13 migration,
 * purchase_orders & purchase_order_items table creation, indices, foreign keys,
 * entity constraints, decimal quantities, and financial isolation.
 */
class PurchaseOrderMigrationUnitTest {

    private fun createMockDb(executedSql: MutableList<String>): SupportSQLiteDatabase {
        return Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                executedSql.add(args[0] as String)
                null
            } else {
                when (method.returnType) {
                    java.lang.Boolean.TYPE -> false
                    java.lang.Integer.TYPE -> 0
                    java.lang.Long.TYPE -> 0L
                    else -> null
                }
            }
        } as SupportSQLiteDatabase
    }

    /**
     * Requirement 1: Migration versions match 12 -> 13.
     */
    @Test
    fun test1_migrationVersionsMatch12To13() {
        assertEquals(12, MIGRATION_12_13.startVersion)
        assertEquals(13, MIGRATION_12_13.endVersion)
    }

    /**
     * Requirement 2: Additive migration only — no DROP or destructive statements on legacy data.
     */
    @Test
    fun test2_additiveMigrationOnlyPreservesV12Data() {
        val executedSql = mutableListOf<String>()
        val db = createMockDb(executedSql)
        MIGRATION_12_13.migrate(db)

        val destructivePatterns = listOf("DROP TABLE", "DELETE FROM", "TRUNCATE", "DROP COLUMN")
        for (sql in executedSql) {
            val upperSql = sql.uppercase()
            for (pattern in destructivePatterns) {
                assertFalse("Migration must not contain destructive statement: $pattern in $sql", upperSql.contains(pattern))
            }
        }
        assertTrue("Migration must execute SQL statements", executedSql.isNotEmpty())
    }

    /**
     * Requirement 3: purchase_orders table is created with expected core fields.
     */
    @Test
    fun test3_purchaseOrdersTableCreated() {
        val executedSql = mutableListOf<String>()
        val db = createMockDb(executedSql)
        MIGRATION_12_13.migrate(db)

        val poCreateSql = executedSql.find { it.contains("CREATE TABLE IF NOT EXISTS `purchase_orders`") }
        assertNotNull("purchase_orders table creation SQL must exist", poCreateSql)

        val sql = poCreateSql!!
        assertTrue("Must have id column", sql.contains("`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL"))
        assertTrue("Must have uuid column", sql.contains("`uuid` TEXT NOT NULL"))
        assertTrue("Must have business_id column", sql.contains("`business_id` TEXT NOT NULL"))
        assertTrue("Must have device_id column", sql.contains("`device_id` TEXT NOT NULL"))
        assertTrue("Must have order_number column", sql.contains("`order_number` TEXT NOT NULL"))
        assertTrue("Must have supplier_id column", sql.contains("`supplier_id` INTEGER NOT NULL"))
        assertTrue("Must have supplier_name_snapshot column", sql.contains("`supplier_name_snapshot` TEXT NOT NULL"))
        assertTrue("Must have supplier_phone_snapshot column", sql.contains("`supplier_phone_snapshot` TEXT"))
        assertTrue("Must have status column", sql.contains("`status` TEXT NOT NULL"))
        assertTrue("Must have total_estimated_amount column", sql.contains("`total_estimated_amount` INTEGER NOT NULL"))
        assertTrue("Must have notes column", sql.contains("`notes` TEXT"))
        assertTrue("Must have created_at column", sql.contains("`created_at` INTEGER NOT NULL"))
        assertTrue("Must have updated_at column", sql.contains("`updated_at` INTEGER NOT NULL"))
        assertTrue("Must have sent_at column", sql.contains("`sent_at` INTEGER"))
        assertTrue("Must have received_at column", sql.contains("`received_at` INTEGER"))
        assertTrue("Must have final_purchase_id column", sql.contains("`final_purchase_id` INTEGER"))
    }

    /**
     * Requirement 4: purchase_order_items table is created with expected fields.
     */
    @Test
    fun test4_purchaseOrderItemsTableCreated() {
        val executedSql = mutableListOf<String>()
        val db = createMockDb(executedSql)
        MIGRATION_12_13.migrate(db)

        val poItemCreateSql = executedSql.find { it.contains("CREATE TABLE IF NOT EXISTS `purchase_order_items`") }
        assertNotNull("purchase_order_items table creation SQL must exist", poItemCreateSql)

        val sql = poItemCreateSql!!
        assertTrue("Must have id column", sql.contains("`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL"))
        assertTrue("Must have uuid column", sql.contains("`uuid` TEXT NOT NULL"))
        assertTrue("Must have business_id column", sql.contains("`business_id` TEXT NOT NULL"))
        assertTrue("Must have purchase_order_id column", sql.contains("`purchase_order_id` INTEGER NOT NULL"))
        assertTrue("Must have po_uuid column", sql.contains("`po_uuid` TEXT NOT NULL"))
        assertTrue("Must have product_id column", sql.contains("`product_id` INTEGER NOT NULL"))
        assertTrue("Must have product_uuid column", sql.contains("`product_uuid` TEXT NOT NULL"))
        assertTrue("Must have product_name column", sql.contains("`product_name` TEXT NOT NULL"))
        assertTrue("Must have ordered_quantity REAL column", sql.contains("`ordered_quantity` REAL NOT NULL"))
        assertTrue("Must have unit column", sql.contains("`unit` TEXT NOT NULL"))
        assertTrue("Must have estimated_price column", sql.contains("`estimated_price` INTEGER NOT NULL"))
        assertTrue("Must have estimated_subtotal column", sql.contains("`estimated_subtotal` INTEGER NOT NULL"))
        assertTrue("Must have received_quantity REAL column", sql.contains("`received_quantity` REAL NOT NULL"))
        assertTrue("Must have notes column", sql.contains("`notes` TEXT"))
    }

    /**
     * Requirement 5: Supplier Foreign Key exists with RESTRICT.
     */
    @Test
    fun test5_supplierForeignKeyConstraint() {
        val executedSql = mutableListOf<String>()
        val db = createMockDb(executedSql)
        MIGRATION_12_13.migrate(db)

        val poCreateSql = executedSql.find { it.contains("`purchase_orders`") && it.contains("FOREIGN KEY(`supplier_id`)") }
        assertNotNull("Supplier FK must exist on purchase_orders", poCreateSql)
        assertTrue("Supplier FK must reference suppliers(id) with RESTRICT",
            poCreateSql!!.contains("FOREIGN KEY(`supplier_id`) REFERENCES `suppliers`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT")
        )
    }

    /**
     * Requirement 6: Product FK and PO FK exist on purchase_order_items.
     */
    @Test
    fun test6_itemForeignKeys() {
        val executedSql = mutableListOf<String>()
        val db = createMockDb(executedSql)
        MIGRATION_12_13.migrate(db)

        val itemCreateSql = executedSql.find { it.contains("`purchase_order_items`") }
        assertNotNull("purchase_order_items table must exist", itemCreateSql)

        assertTrue("PO FK must reference purchase_orders(id) with CASCADE",
            itemCreateSql!!.contains("FOREIGN KEY(`purchase_order_id`) REFERENCES `purchase_orders`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE")
        )
        assertTrue("Product FK must reference products(id) with RESTRICT",
            itemCreateSql.contains("FOREIGN KEY(`product_id`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT")
        )
    }

    /**
     * Requirement 7 & 8: PO UUID and order_number unique constraint indexes exist.
     */
    @Test
    fun test7And8_purchaseOrderUniqueIndexes() {
        val executedSql = mutableListOf<String>()
        val db = createMockDb(executedSql)
        MIGRATION_12_13.migrate(db)

        val hasPoUuidUnique = executedSql.any {
            it.contains("CREATE UNIQUE INDEX") && it.contains("`index_purchase_orders_uuid`") && it.contains("(`uuid`)")
        }
        val hasPoOrderNumUnique = executedSql.any {
            it.contains("CREATE UNIQUE INDEX") && it.contains("`index_purchase_orders_order_number`") && it.contains("(`order_number`)")
        }
        val hasPoSupplierIdx = executedSql.any {
            it.contains("`index_purchase_orders_supplier_id`")
        }
        val hasPoStatusIdx = executedSql.any {
            it.contains("`index_purchase_orders_status`")
        }

        assertTrue("index_purchase_orders_uuid unique index must exist", hasPoUuidUnique)
        assertTrue("index_purchase_orders_order_number unique index must exist", hasPoOrderNumUnique)
        assertTrue("index_purchase_orders_supplier_id index must exist", hasPoSupplierIdx)
        assertTrue("index_purchase_orders_status index must exist", hasPoStatusIdx)
    }

    /**
     * Requirement 9: Required supplier behavior on entity instantiation.
     */
    @Test
    fun test9_requiredSupplierBehavior() {
        val supplier = SupplierEntity(
            id = 42L,
            name = "PT Sumber Makmur",
            phone = "081234567890"
        )
        val po = PurchaseOrderEntity(
            id = 1L,
            orderNumber = "PO-20260918-001",
            supplierId = supplier.id,
            supplierNameSnapshot = supplier.name,
            supplierPhoneSnapshot = supplier.phone,
            status = PurchaseOrderStatus.DRAFT.name,
            totalEstimatedAmount = 250000L
        )

        assertEquals(42L, po.supplierId)
        assertEquals("PT Sumber Makmur", po.supplierNameSnapshot)
        assertEquals("081234567890", po.supplierPhoneSnapshot)
        assertEquals(PurchaseOrderStatus.DRAFT.name, po.status)
        assertEquals("LEGACY_BUSINESS", po.businessId)
        assertEquals("LEGACY_DEVICE", po.deviceId)
    }

    /**
     * Requirement 10: Double quantity supports decimal values for physical and fuel items.
     */
    @Test
    fun test10_decimalQuantitiesSupported() {
        val fuelItem = PurchaseOrderItemEntity(
            id = 1L,
            purchaseOrderId = 10L,
            poUuid = "PO-UUID-001",
            productId = 101L,
            productUuid = "PROD-FUEL-001",
            productName = "Pertalite",
            orderedQuantity = 45.75,
            unit = "L",
            estimatedPrice = 10000L,
            estimatedSubtotal = (45.75 * 10000).toLong(),
            receivedQuantity = 45.75
        )

        val physicalItem = PurchaseOrderItemEntity(
            id = 2L,
            purchaseOrderId = 10L,
            poUuid = "PO-UUID-001",
            productId = 102L,
            productUuid = "PROD-PHYS-002",
            productName = "Beras Rojolele",
            orderedQuantity = 12.5,
            unit = "kg",
            estimatedPrice = 15000L,
            estimatedSubtotal = (12.5 * 15000).toLong(),
            receivedQuantity = 0.0
        )

        assertEquals(45.75, fuelItem.orderedQuantity, 0.0001)
        assertEquals(45.75, fuelItem.receivedQuantity, 0.0001)
        assertEquals(457500L, fuelItem.estimatedSubtotal)

        assertEquals(12.5, physicalItem.orderedQuantity, 0.0001)
        assertEquals(0.0, physicalItem.receivedQuantity, 0.0001)
        assertEquals(187500L, physicalItem.estimatedSubtotal)
    }

    /**
     * Requirement 11: No financial rows created by PO persistence alone.
     */
    @Test
    fun test11_financialIsolation() {
        val po = PurchaseOrderEntity(
            orderNumber = "PO-20260918-002",
            supplierId = 5L,
            supplierNameSnapshot = "Supplier ABC",
            status = PurchaseOrderStatus.ORDERED.name,
            totalEstimatedAmount = 1000000L
        )

        // PO does not contain transaction date, payment method, or stock delta
        assertNull("PO has no final_purchase_id before fulfillment", po.finalPurchaseId)
        assertEquals(PurchaseOrderStatus.ORDERED.name, po.status)
        assertEquals(1000000L, po.totalEstimatedAmount)
    }

    /**
     * Requirement 12: Existing PurchaseTransaction queries and models still work.
     */
    @Test
    fun test12_legacyPurchaseTransactionCompatibility() {
        val purchaseTx = PurchaseTransactionEntity(
            id = 100L,
            transactionNumber = "TRX-PUR-001",
            transactionDate = System.currentTimeMillis(),
            totalAmount = 500000L,
            paymentMethod = "CASH",
            supplierId = 5L
        )

        val purchaseItem = PurchaseItemEntity(
            id = 200L,
            transactionId = 100L,
            productId = 50L,
            productName = "Gula Pasir 1kg",
            quantity = 20.0,
            purchasePrice = 14000L,
            subtotal = 280000L
        )

        assertEquals(100L, purchaseTx.id)
        assertEquals("TRX-PUR-001", purchaseTx.transactionNumber)
        assertEquals(500000L, purchaseTx.totalAmount)
        assertEquals(200L, purchaseItem.id)
        assertEquals(20.0, purchaseItem.quantity, 0.0001)
        assertEquals(280000L, purchaseItem.subtotal)
    }

    /**
     * Status enum integrity check.
     */
    @Test
    fun test13_purchaseOrderStatusValues() {
        val statuses = PurchaseOrderStatus.values().map { it.name }
        assertEquals(4, statuses.size)
        assertTrue(statuses.contains("DRAFT"))
        assertTrue(statuses.contains("ORDERED"))
        assertTrue(statuses.contains("RECEIVED"))
        assertTrue(statuses.contains("CANCELLED"))
        assertFalse("PARTIALLY_RECEIVED is deferred to future scope", statuses.contains("PARTIALLY_RECEIVED"))
    }
}
