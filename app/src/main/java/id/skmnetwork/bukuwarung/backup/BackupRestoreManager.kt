package id.skmnetwork.bukuwarung.backup

import android.content.ContentValues
import android.database.Cursor
import androidx.room.withTransaction
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Foundation Step 5 — Backup & Restore Manager
 * Coordinates consistent snapshot export, decoupled transport SPI,
 * pre-validation, atomic Room restore, and DataStore reconciliation.
 */
class BackupRestoreManager(
    private val database: AppDatabase,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val transport: SheetsBackupTransport = MockSheetsTransport()
) {

    /**
     * Captures DataStore + Room snapshot and returns a deterministic BackupSnapshot.
     */
    suspend fun exportSnapshot(): BackupSnapshot = withContext(Dispatchers.IO) {
        val userSettings = userPreferencesRepository.userSettings.first()
        val businessId = userSettings.businessId.ifBlank { "LEGACY_BUSINESS" }
        val deviceId = userSettings.deviceId.ifBlank { "LEGACY_DEVICE" }

        val db = database.openHelper.readableDatabase

        // 1. Tab 01_Business
        val businessRows = listOf(
            listOf(
                businessId,
                CanonicalSerializer.sanitize(userSettings.shopName),
                CanonicalSerializer.sanitize(userSettings.ownerName),
                CanonicalSerializer.sanitize(userSettings.phone),
                CanonicalSerializer.sanitize(userSettings.address),
                "0"
            )
        )
        val tab01 = SheetTab(
            name = "01_Business",
            headers = listOf("business_id", "shop_name", "owner_name", "phone", "address", "created_at"),
            rows = CanonicalSerializer.sortTabRows("01_Business", businessRows)
        )

        // 2. Tab 02_Device
        val deviceRows = listOf(
            listOf(
                deviceId,
                businessId,
                CanonicalSerializer.sanitize(userSettings.deviceName),
                CanonicalSerializer.sanitize(userSettings.deviceRole),
                "1.0.0"
            )
        )
        val tab02 = SheetTab(
            name = "02_Device",
            headers = listOf("device_id", "business_id", "device_name", "device_role", "app_version"),
            rows = CanonicalSerializer.sortTabRows("02_Device", deviceRows)
        )

        // Map Category ID -> Category UUID for product export
        val categoryIdToUuid = mutableMapOf<Long, String>()
        val categoryRows = mutableListOf<List<String>>()
        db.query("SELECT id, uuid, business_id, name, is_deleted, deleted_at, created_at FROM categories").use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val uuid = cursor.getString(1)
                val bId = cursor.getString(2)
                val name = cursor.getString(3)
                val isDeleted = cursor.getInt(4) == 1
                val deletedAt = if (cursor.isNull(5)) null else cursor.getLong(5)
                val createdAt = cursor.getLong(6)

                categoryIdToUuid[id] = uuid
                categoryRows.add(
                    listOf(
                        uuid,
                        bId,
                        CanonicalSerializer.sanitize(name),
                        isDeleted.toString(),
                        CanonicalSerializer.formatLong(deletedAt),
                        createdAt.toString()
                    )
                )
            }
        }
        val tab03 = SheetTab(
            name = "03_Categories",
            headers = listOf("uuid", "business_id", "name", "is_deleted", "deleted_at", "created_at"),
            rows = CanonicalSerializer.sortTabRows("03_Categories", categoryRows)
        )

        // Map Customer ID -> Customer UUID
        val customerIdToUuid = mutableMapOf<Long, String>()
        val customerRows = mutableListOf<List<String>>()
        db.query("SELECT id, uuid, business_id, name, phone, address, is_deleted, deleted_at, created_at, updated_at FROM customers").use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val uuid = cursor.getString(1)
                val bId = cursor.getString(2)
                val name = cursor.getString(3)
                val phone = cursor.getString(4)
                val address = cursor.getString(5)
                val isDeleted = cursor.getInt(6) == 1
                val deletedAt = if (cursor.isNull(7)) null else cursor.getLong(7)
                val createdAt = cursor.getLong(8)
                val updatedAt = cursor.getLong(9)

                customerIdToUuid[id] = uuid
                customerRows.add(
                    listOf(
                        uuid,
                        bId,
                        CanonicalSerializer.sanitize(name),
                        CanonicalSerializer.sanitize(phone),
                        CanonicalSerializer.sanitize(address),
                        isDeleted.toString(),
                        CanonicalSerializer.formatLong(deletedAt),
                        createdAt.toString(),
                        updatedAt.toString()
                    )
                )
            }
        }
        val tab05 = SheetTab(
            name = "05_Customers",
            headers = listOf("uuid", "business_id", "name", "phone", "address", "is_deleted", "deleted_at", "created_at", "updated_at"),
            rows = CanonicalSerializer.sortTabRows("05_Customers", customerRows)
        )

        // Map Supplier ID -> Supplier UUID
        val supplierIdToUuid = mutableMapOf<Long, String>()
        val supplierRows = mutableListOf<List<String>>()
        db.query("SELECT id, uuid, business_id, name, phone, address, is_deleted, deleted_at, created_at, updated_at FROM suppliers").use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val uuid = cursor.getString(1)
                val bId = cursor.getString(2)
                val name = cursor.getString(3)
                val phone = cursor.getString(4)
                val address = cursor.getString(5)
                val isDeleted = cursor.getInt(6) == 1
                val deletedAt = if (cursor.isNull(7)) null else cursor.getLong(7)
                val createdAt = cursor.getLong(8)
                val updatedAt = cursor.getLong(9)

                supplierIdToUuid[id] = uuid
                supplierRows.add(
                    listOf(
                        uuid,
                        bId,
                        CanonicalSerializer.sanitize(name),
                        CanonicalSerializer.sanitize(phone),
                        CanonicalSerializer.sanitize(address),
                        isDeleted.toString(),
                        CanonicalSerializer.formatLong(deletedAt),
                        createdAt.toString(),
                        updatedAt.toString()
                    )
                )
            }
        }
        val tab12 = SheetTab(
            name = "12_Suppliers",
            headers = listOf("uuid", "business_id", "name", "phone", "address", "is_deleted", "deleted_at", "created_at", "updated_at"),
            rows = CanonicalSerializer.sortTabRows("12_Suppliers", supplierRows)
        )

        // 3. Tab 04_Products
        val productRows = mutableListOf<List<String>>()
        db.query("SELECT id, uuid, business_id, category_id, name, purchase_price, selling_price, stock, minimum_stock, unit, item_type, is_deleted, deleted_at, created_at, updated_at, barcode, image_uri FROM products").use { cursor ->
            while (cursor.moveToNext()) {
                val uuid = cursor.getString(1)
                val bId = cursor.getString(2)
                val categoryId = cursor.getLong(3)
                val categoryUuid = categoryIdToUuid[categoryId] ?: "NULL"
                val name = cursor.getString(4)
                val purchasePrice = cursor.getLong(5)
                val sellingPrice = cursor.getLong(6)
                val stock = cursor.getDouble(7)
                val minStock = cursor.getDouble(8)
                val unit = cursor.getString(9)
                val itemType = cursor.getString(10)
                val isDeleted = cursor.getInt(11) == 1
                val deletedAt = if (cursor.isNull(12)) null else cursor.getLong(12)
                val createdAt = cursor.getLong(13)
                val updatedAt = cursor.getLong(14)
                val barcode = cursor.getString(15)
                val imageUri = cursor.getString(16)

                productRows.add(
                    listOf(
                        uuid,
                        bId,
                        categoryUuid,
                        CanonicalSerializer.sanitize(name),
                        purchasePrice.toString(),
                        sellingPrice.toString(),
                        CanonicalSerializer.formatDouble(stock),
                        CanonicalSerializer.formatDouble(minStock),
                        CanonicalSerializer.sanitize(unit),
                        CanonicalSerializer.sanitize(itemType),
                        isDeleted.toString(),
                        CanonicalSerializer.formatLong(deletedAt),
                        createdAt.toString(),
                        updatedAt.toString(),
                        CanonicalSerializer.sanitize(barcode),
                        CanonicalSerializer.sanitize(imageUri)
                    )
                )
            }
        }
        val tab04 = SheetTab(
            name = "04_Products",
            headers = listOf("uuid", "business_id", "category_uuid", "name", "purchase_price", "selling_price", "stock", "minimum_stock", "unit", "item_type", "is_deleted", "deleted_at", "created_at", "updated_at", "barcode", "image_uri"),
            rows = CanonicalSerializer.sortTabRows("04_Products", productRows)
        )

        // 4. Tab 06_Sales
        val saleRows = mutableListOf<List<String>>()
        db.query("SELECT id, uuid, business_id, device_id, transaction_number, transaction_date, total_amount, payment_method, created_at, customer_id FROM sales_transactions").use { cursor ->
            while (cursor.moveToNext()) {
                val uuid = cursor.getString(1)
                val bId = cursor.getString(2)
                val dId = cursor.getString(3)
                val txNumber = cursor.getString(4)
                val txDate = cursor.getLong(5)
                val totalAmount = cursor.getLong(6)
                val paymentMethod = cursor.getString(7)
                val createdAt = cursor.getLong(8)
                val customerId = if (cursor.isNull(9)) null else cursor.getLong(9)
                val customerUuid = if (customerId != null) customerIdToUuid[customerId] ?: "NULL" else "NULL"

                saleRows.add(
                    listOf(
                        uuid,
                        bId,
                        dId,
                        CanonicalSerializer.sanitize(txNumber),
                        txDate.toString(),
                        totalAmount.toString(),
                        CanonicalSerializer.sanitize(paymentMethod),
                        createdAt.toString(),
                        customerUuid
                    )
                )
            }
        }
        val tab06 = SheetTab(
            name = "06_Sales",
            headers = listOf("uuid", "business_id", "device_id", "transaction_number", "transaction_date", "total_amount", "payment_method", "created_at", "customer_uuid"),
            rows = CanonicalSerializer.sortTabRows("06_Sales", saleRows)
        )

        // 5. Tab 07_SaleItems
        val saleItemRows = mutableListOf<List<String>>()
        db.query("SELECT id, uuid, business_id, sale_uuid, product_uuid, product_name, quantity, price, purchase_price, subtotal FROM sale_items").use { cursor ->
            while (cursor.moveToNext()) {
                val uuid = cursor.getString(1)
                val bId = cursor.getString(2)
                val saleUuid = cursor.getString(3)
                val productUuid = cursor.getString(4)
                val productName = cursor.getString(5)
                val qty = cursor.getDouble(6)
                val price = cursor.getLong(7)
                val purchasePrice = cursor.getLong(8)
                val subtotal = cursor.getLong(9)

                saleItemRows.add(
                    listOf(
                        uuid,
                        bId,
                        saleUuid,
                        productUuid,
                        CanonicalSerializer.sanitize(productName),
                        CanonicalSerializer.formatDouble(qty),
                        price.toString(),
                        purchasePrice.toString(),
                        subtotal.toString()
                    )
                )
            }
        }
        val tab07 = SheetTab(
            name = "07_SaleItems",
            headers = listOf("uuid", "business_id", "sale_uuid", "product_uuid", "product_name", "quantity", "price", "purchase_price", "subtotal"),
            rows = CanonicalSerializer.sortTabRows("07_SaleItems", saleItemRows)
        )

        // 6. Tab 08_Purchases
        val purchaseRows = mutableListOf<List<String>>()
        db.query("SELECT id, uuid, business_id, device_id, transaction_number, transaction_date, total_amount, payment_method, created_at, supplier_id FROM purchase_transactions").use { cursor ->
            while (cursor.moveToNext()) {
                val uuid = cursor.getString(1)
                val bId = cursor.getString(2)
                val dId = cursor.getString(3)
                val txNumber = cursor.getString(4)
                val txDate = cursor.getLong(5)
                val totalAmount = cursor.getLong(6)
                val paymentMethod = cursor.getString(7)
                val createdAt = cursor.getLong(8)
                val supplierId = if (cursor.isNull(9)) null else cursor.getLong(9)
                val supplierUuid = if (supplierId != null) supplierIdToUuid[supplierId] ?: "NULL" else "NULL"

                purchaseRows.add(
                    listOf(
                        uuid,
                        bId,
                        dId,
                        CanonicalSerializer.sanitize(txNumber),
                        txDate.toString(),
                        totalAmount.toString(),
                        CanonicalSerializer.sanitize(paymentMethod),
                        createdAt.toString(),
                        supplierUuid
                    )
                )
            }
        }
        val tab08 = SheetTab(
            name = "08_Purchases",
            headers = listOf("uuid", "business_id", "device_id", "transaction_number", "transaction_date", "total_amount", "payment_method", "created_at", "supplier_uuid"),
            rows = CanonicalSerializer.sortTabRows("08_Purchases", purchaseRows)
        )

        // 7. Tab 09_PurchaseItems
        val purchaseItemRows = mutableListOf<List<String>>()
        db.query("SELECT id, uuid, business_id, purchase_uuid, product_uuid, product_name, quantity, purchase_price, subtotal FROM purchase_items").use { cursor ->
            while (cursor.moveToNext()) {
                val uuid = cursor.getString(1)
                val bId = cursor.getString(2)
                val purchaseUuid = cursor.getString(3)
                val productUuid = cursor.getString(4)
                val productName = cursor.getString(5)
                val qty = cursor.getDouble(6)
                val price = cursor.getLong(7)
                val subtotal = cursor.getLong(8)

                purchaseItemRows.add(
                    listOf(
                        uuid,
                        bId,
                        purchaseUuid,
                        productUuid,
                        CanonicalSerializer.sanitize(productName),
                        CanonicalSerializer.formatDouble(qty),
                        price.toString(),
                        subtotal.toString()
                    )
                )
            }
        }
        val tab09 = SheetTab(
            name = "09_PurchaseItems",
            headers = listOf("uuid", "business_id", "purchase_uuid", "product_uuid", "product_name", "quantity", "purchase_price", "subtotal"),
            rows = CanonicalSerializer.sortTabRows("09_PurchaseItems", purchaseItemRows)
        )

        // 8. Tab 10_Debts
        val debtRows = mutableListOf<List<String>>()
        db.query("SELECT id, uuid, business_id, customer_uuid, sale_uuid, total_debt, paid_amount, status, created_at, updated_at FROM debts").use { cursor ->
            while (cursor.moveToNext()) {
                val uuid = cursor.getString(1)
                val bId = cursor.getString(2)
                val customerUuid = cursor.getString(3)
                val saleUuid = cursor.getString(4)
                val totalDebt = cursor.getLong(5)
                val paidAmount = cursor.getLong(6)
                val status = cursor.getString(7)
                val createdAt = cursor.getLong(8)
                val updatedAt = cursor.getLong(9)

                debtRows.add(
                    listOf(
                        uuid,
                        bId,
                        customerUuid,
                        saleUuid.ifBlank { "NULL" },
                        totalDebt.toString(),
                        paidAmount.toString(),
                        status,
                        createdAt.toString(),
                        updatedAt.toString()
                    )
                )
            }
        }
        val tab10 = SheetTab(
            name = "10_Debts",
            headers = listOf("uuid", "business_id", "customer_uuid", "sale_uuid", "total_debt", "paid_amount", "status", "created_at", "updated_at"),
            rows = CanonicalSerializer.sortTabRows("10_Debts", debtRows)
        )

        // 9. Tab 11_DebtPayments
        val debtPaymentRows = mutableListOf<List<String>>()
        db.query("SELECT id, uuid, business_id, device_id, debt_uuid, amount, payment_date, note, created_at FROM debt_payments").use { cursor ->
            while (cursor.moveToNext()) {
                val uuid = cursor.getString(1)
                val bId = cursor.getString(2)
                val dId = cursor.getString(3)
                val debtUuid = cursor.getString(4)
                val amount = cursor.getLong(5)
                val paymentDate = cursor.getLong(6)
                val note = cursor.getString(7)
                val createdAt = cursor.getLong(8)

                debtPaymentRows.add(
                    listOf(
                        uuid,
                        bId,
                        dId,
                        debtUuid,
                        amount.toString(),
                        paymentDate.toString(),
                        CanonicalSerializer.sanitize(note),
                        createdAt.toString()
                    )
                )
            }
        }
        val tab11 = SheetTab(
            name = "11_DebtPayments",
            headers = listOf("uuid", "business_id", "device_id", "debt_uuid", "amount", "payment_date", "note", "created_at"),
            rows = CanonicalSerializer.sortTabRows("11_DebtPayments", debtPaymentRows)
        )

        // 10. Tab 13_SupplierPayables
        val payableRows = mutableListOf<List<String>>()
        db.query("SELECT id, uuid, business_id, supplier_uuid, purchase_uuid, total_debt, paid_amount, status, created_at, updated_at FROM supplier_payables").use { cursor ->
            while (cursor.moveToNext()) {
                val uuid = cursor.getString(1)
                val bId = cursor.getString(2)
                val supplierUuid = cursor.getString(3)
                val purchaseUuid = cursor.getString(4)
                val totalDebt = cursor.getLong(5)
                val paidAmount = cursor.getLong(6)
                val status = cursor.getString(7)
                val createdAt = cursor.getLong(8)
                val updatedAt = cursor.getLong(9)

                payableRows.add(
                    listOf(
                        uuid,
                        bId,
                        supplierUuid,
                        purchaseUuid.ifBlank { "NULL" },
                        totalDebt.toString(),
                        paidAmount.toString(),
                        status,
                        createdAt.toString(),
                        updatedAt.toString()
                    )
                )
            }
        }
        val tab13 = SheetTab(
            name = "13_SupplierPayables",
            headers = listOf("uuid", "business_id", "supplier_uuid", "purchase_uuid", "total_debt", "paid_amount", "status", "created_at", "updated_at"),
            rows = CanonicalSerializer.sortTabRows("13_SupplierPayables", payableRows)
        )

        // 11. Tab 14_SupplierPayments
        val supplierPaymentRows = mutableListOf<List<String>>()
        db.query("SELECT id, uuid, business_id, device_id, payable_uuid, amount, payment_date, note, created_at FROM supplier_payments").use { cursor ->
            while (cursor.moveToNext()) {
                val uuid = cursor.getString(1)
                val bId = cursor.getString(2)
                val dId = cursor.getString(3)
                val payableUuid = cursor.getString(4)
                val amount = cursor.getLong(5)
                val paymentDate = cursor.getLong(6)
                val note = cursor.getString(7)
                val createdAt = cursor.getLong(8)

                supplierPaymentRows.add(
                    listOf(
                        uuid,
                        bId,
                        dId,
                        payableUuid,
                        amount.toString(),
                        paymentDate.toString(),
                        CanonicalSerializer.sanitize(note),
                        createdAt.toString()
                    )
                )
            }
        }
        val tab14 = SheetTab(
            name = "14_SupplierPayments",
            headers = listOf("uuid", "business_id", "device_id", "payable_uuid", "amount", "payment_date", "note", "created_at"),
            rows = CanonicalSerializer.sortTabRows("14_SupplierPayments", supplierPaymentRows)
        )

        // 12. Tab 15_CashTransactions
        val cashRows = mutableListOf<List<String>>()
        db.query("SELECT id, uuid, business_id, device_id, type, amount, description, ref_uuid, created_at FROM cash_transactions").use { cursor ->
            while (cursor.moveToNext()) {
                val uuid = cursor.getString(1)
                val bId = cursor.getString(2)
                val dId = cursor.getString(3)
                val type = cursor.getString(4)
                val amount = cursor.getLong(5)
                val desc = cursor.getString(6)
                val refUuid = cursor.getString(7)
                val createdAt = cursor.getLong(8)

                cashRows.add(
                    listOf(
                        uuid,
                        bId,
                        dId,
                        type,
                        amount.toString(),
                        CanonicalSerializer.sanitize(desc),
                        CanonicalSerializer.sanitize(refUuid),
                        createdAt.toString()
                    )
                )
            }
        }
        val tab15 = SheetTab(
            name = "15_CashTransactions",
            headers = listOf("uuid", "business_id", "device_id", "type", "amount", "description", "ref_uuid", "created_at"),
            rows = CanonicalSerializer.sortTabRows("15_CashTransactions", cashRows)
        )

        // 13. Tab 16_StockMovements
        val movementRows = mutableListOf<List<String>>()
        db.query("SELECT id, uuid, business_id, device_id, product_uuid, movement_type, delta_quantity, current_stock_snapshot, reference_uuid, note, created_at FROM stock_movements").use { cursor ->
            while (cursor.moveToNext()) {
                val uuid = cursor.getString(1)
                val bId = cursor.getString(2)
                val dId = cursor.getString(3)
                val productUuid = cursor.getString(4)
                val movementType = cursor.getString(5)
                val deltaQty = cursor.getDouble(6)
                val currentStock = cursor.getDouble(7)
                val refUuid = cursor.getString(8)
                val note = cursor.getString(9)
                val createdAt = cursor.getLong(10)

                movementRows.add(
                    listOf(
                        uuid,
                        bId,
                        dId,
                        productUuid,
                        movementType,
                        CanonicalSerializer.formatDouble(deltaQty),
                        CanonicalSerializer.formatDouble(currentStock),
                        CanonicalSerializer.sanitize(refUuid),
                        CanonicalSerializer.sanitize(note),
                        createdAt.toString()
                    )
                )
            }
        }
        val tab16 = SheetTab(
            name = "16_StockMovements",
            headers = listOf("uuid", "business_id", "device_id", "product_uuid", "movement_type", "delta_quantity", "current_stock_snapshot", "reference_uuid", "note", "created_at"),
            rows = CanonicalSerializer.sortTabRows("16_StockMovements", movementRows)
        )

        // 14. Tab 17_SaleReturns
        val returnRows = mutableListOf<List<String>>()
        db.query("SELECT id, uuid, business_id, device_id, return_number, return_date, sale_uuid, customer_uuid, total_refund_amount, refund_method, reason, notes, created_at FROM sale_return_transactions").use { cursor ->
            while (cursor.moveToNext()) {
                val uuid = cursor.getString(1)
                val bId = cursor.getString(2)
                val dId = cursor.getString(3)
                val retNumber = cursor.getString(4)
                val retDate = cursor.getLong(5)
                val saleUuid = cursor.getString(6)
                val custUuid = if (cursor.isNull(7)) null else cursor.getString(7)
                val totalRefund = cursor.getLong(8)
                val refundMethod = cursor.getString(9)
                val reason = if (cursor.isNull(10)) null else cursor.getString(10)
                val notes = if (cursor.isNull(11)) null else cursor.getString(11)
                val createdAt = cursor.getLong(12)

                returnRows.add(
                    listOf(
                        uuid,
                        bId,
                        dId,
                        CanonicalSerializer.sanitize(retNumber),
                        retDate.toString(),
                        saleUuid,
                        custUuid ?: "NULL",
                        totalRefund.toString(),
                        CanonicalSerializer.sanitize(refundMethod),
                        CanonicalSerializer.sanitize(reason),
                        CanonicalSerializer.sanitize(notes),
                        createdAt.toString()
                    )
                )
            }
        }
        val tab17 = SheetTab(
            name = "17_SaleReturns",
            headers = listOf("uuid", "business_id", "device_id", "return_number", "return_date", "sale_uuid", "customer_uuid", "total_refund_amount", "refund_method", "reason", "notes", "created_at"),
            rows = CanonicalSerializer.sortTabRows("17_SaleReturns", returnRows)
        )

        // 15. Tab 18_SaleReturnItems
        val returnItemRows = mutableListOf<List<String>>()
        db.query("SELECT id, uuid, business_id, return_uuid, sale_item_uuid, product_uuid, product_name, quantity, price, purchase_price, subtotal, created_at FROM sale_return_items").use { cursor ->
            while (cursor.moveToNext()) {
                val uuid = cursor.getString(1)
                val bId = cursor.getString(2)
                val returnUuid = cursor.getString(3)
                val saleItemUuid = cursor.getString(4)
                val productUuid = cursor.getString(5)
                val prodName = cursor.getString(6)
                val qty = cursor.getDouble(7)
                val price = cursor.getLong(8)
                val purchasePrice = cursor.getLong(9)
                val subtotal = cursor.getLong(10)
                val createdAt = cursor.getLong(11)

                returnItemRows.add(
                    listOf(
                        uuid,
                        bId,
                        returnUuid,
                        saleItemUuid,
                        productUuid,
                        CanonicalSerializer.sanitize(prodName),
                        CanonicalSerializer.formatDouble(qty),
                        price.toString(),
                        purchasePrice.toString(),
                        subtotal.toString(),
                        createdAt.toString()
                    )
                )
            }
        }
        val tab18 = SheetTab(
            name = "18_SaleReturnItems",
            headers = listOf("uuid", "business_id", "return_uuid", "sale_item_uuid", "product_uuid", "product_name", "quantity", "price", "purchase_price", "subtotal", "created_at"),
            rows = CanonicalSerializer.sortTabRows("18_SaleReturnItems", returnItemRows)
        )

        val dataTabs = mapOf(
            "01_Business" to tab01,
            "02_Device" to tab02,
            "03_Categories" to tab03,
            "04_Products" to tab04,
            "05_Customers" to tab05,
            "06_Sales" to tab06,
            "07_SaleItems" to tab07,
            "08_Purchases" to tab08,
            "09_PurchaseItems" to tab09,
            "10_Debts" to tab10,
            "11_DebtPayments" to tab11,
            "12_Suppliers" to tab12,
            "13_SupplierPayables" to tab13,
            "14_SupplierPayments" to tab14,
            "15_CashTransactions" to tab15,
            "16_StockMovements" to tab16,
            "17_SaleReturns" to tab17,
            "18_SaleReturnItems" to tab18
        )

        val totalRecords = dataTabs.values.sumOf { it.rows.size }
        val checksum = CanonicalSerializer.calculateChecksum(dataTabs)
        val exportedAt = System.currentTimeMillis()

        val metadata = BackupMetadata(
            backupFormatVersion = CanonicalSerializer.BACKUP_FORMAT_VERSION,
            roomSchemaVersion = CanonicalSerializer.ROOM_SCHEMA_VERSION,
            exportedAt = exportedAt,
            businessId = businessId,
            deviceId = deviceId,
            appVersion = "1.0.0",
            totalRecords = totalRecords,
            checksum = checksum
        )

        val metadataRows = listOf(
            listOf("backup_format_version", metadata.backupFormatVersion),
            listOf("room_schema_version", metadata.roomSchemaVersion.toString()),
            listOf("exported_at", metadata.exportedAt.toString()),
            listOf("business_id", metadata.businessId),
            listOf("device_id", metadata.deviceId),
            listOf("app_version", metadata.appVersion),
            listOf("total_records", metadata.totalRecords.toString()),
            listOf("checksum", metadata.checksum)
        )
        val tab00 = SheetTab(
            name = "00_Metadata",
            headers = listOf("key", "value"),
            rows = metadataRows
        )

        val allTabs = mutableMapOf<String, SheetTab>()
        allTabs["00_Metadata"] = tab00
        allTabs.putAll(dataTabs)

        BackupSnapshot(metadata = metadata, tabs = allTabs)
    }

    /**
     * Executes the full atomic restore pipeline.
     */
    suspend fun restoreSnapshot(snapshot: BackupSnapshot): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Phase 1: Pre-validation (Zero Room DB Mutation on failure)
            BackupValidator.validate(snapshot)

            // Phase 2: Room Atomic Transaction (Authoritative persistence boundary)
            database.withTransaction {
                val db = database.openHelper.writableDatabase

                // 1. Clear all domain tables in reverse dependency order
                db.execSQL("DELETE FROM sale_return_items")
                db.execSQL("DELETE FROM sale_return_transactions")
                db.execSQL("DELETE FROM stock_movements")
                db.execSQL("DELETE FROM cash_transactions")
                db.execSQL("DELETE FROM supplier_payments")
                db.execSQL("DELETE FROM supplier_payables")
                db.execSQL("DELETE FROM debt_payments")
                db.execSQL("DELETE FROM debts")
                db.execSQL("DELETE FROM purchase_items")
                db.execSQL("DELETE FROM purchase_transactions")
                db.execSQL("DELETE FROM sale_items")
                db.execSQL("DELETE FROM sales_transactions")
                db.execSQL("DELETE FROM products")
                db.execSQL("DELETE FROM categories")
                db.execSQL("DELETE FROM customers")
                db.execSQL("DELETE FROM suppliers")

                // 2. Truncate local sync_queue to avoid replaying obsolete actions
                db.execSQL("DELETE FROM sync_queue")

                // 3. Restore Categories
                val categoryUuidToId = mutableMapOf<String, Long>()
                snapshot.getTab("03_Categories")?.rows?.forEach { row ->
                    val uuid = row[0]
                    val bId = row[1]
                    val name = row[2]
                    val isDeleted = row[3].toBoolean()
                    val deletedAt = if (row[4] == "NULL") null else row[4].toLongOrNull()
                    val createdAt = row[5].toLongOrNull() ?: System.currentTimeMillis()

                    val cv = ContentValues().apply {
                        put("uuid", uuid)
                        put("business_id", bId)
                        put("name", name)
                        put("is_deleted", if (isDeleted) 1 else 0)
                        if (deletedAt != null) put("deleted_at", deletedAt) else putNull("deleted_at")
                        put("created_at", createdAt)
                    }
                    val newId = db.insert("categories", 0, cv)
                    categoryUuidToId[uuid] = newId
                }

                // 4. Restore Customers
                val customerUuidToId = mutableMapOf<String, Long>()
                snapshot.getTab("05_Customers")?.rows?.forEach { row ->
                    val uuid = row[0]
                    val bId = row[1]
                    val name = row[2]
                    val phone = if (row[3] == "NULL") null else row[3]
                    val address = if (row[4] == "NULL") null else row[4]
                    val isDeleted = row[5].toBoolean()
                    val deletedAt = if (row[6] == "NULL") null else row[6].toLongOrNull()
                    val createdAt = row[7].toLongOrNull() ?: System.currentTimeMillis()
                    val updatedAt = row[8].toLongOrNull() ?: System.currentTimeMillis()

                    val cv = ContentValues().apply {
                        put("uuid", uuid)
                        put("business_id", bId)
                        put("name", name)
                        if (phone != null) put("phone", phone) else putNull("phone")
                        if (address != null) put("address", address) else putNull("address")
                        put("is_deleted", if (isDeleted) 1 else 0)
                        if (deletedAt != null) put("deleted_at", deletedAt) else putNull("deleted_at")
                        put("created_at", createdAt)
                        put("updated_at", updatedAt)
                    }
                    val newId = db.insert("customers", 0, cv)
                    customerUuidToId[uuid] = newId
                }

                // 5. Restore Suppliers
                val supplierUuidToId = mutableMapOf<String, Long>()
                snapshot.getTab("12_Suppliers")?.rows?.forEach { row ->
                    val uuid = row[0]
                    val bId = row[1]
                    val name = row[2]
                    val phone = if (row[3] == "NULL") null else row[3]
                    val address = if (row[4] == "NULL") null else row[4]
                    val isDeleted = row[5].toBoolean()
                    val deletedAt = if (row[6] == "NULL") null else row[6].toLongOrNull()
                    val createdAt = row[7].toLongOrNull() ?: System.currentTimeMillis()
                    val updatedAt = row[8].toLongOrNull() ?: System.currentTimeMillis()

                    val cv = ContentValues().apply {
                        put("uuid", uuid)
                        put("business_id", bId)
                        put("name", name)
                        if (phone != null) put("phone", phone) else putNull("phone")
                        if (address != null) put("address", address) else putNull("address")
                        put("is_deleted", if (isDeleted) 1 else 0)
                        if (deletedAt != null) put("deleted_at", deletedAt) else putNull("deleted_at")
                        put("created_at", createdAt)
                        put("updated_at", updatedAt)
                    }
                    val newId = db.insert("suppliers", 0, cv)
                    supplierUuidToId[uuid] = newId
                }

                // 6. Restore Products
                val productUuidToId = mutableMapOf<String, Long>()
                snapshot.getTab("04_Products")?.rows?.forEach { row ->
                    val uuid = row[0]
                    val bId = row[1]
                    val categoryUuid = row[2]
                    val categoryId = categoryUuidToId[categoryUuid]
                        ?: throw CorruptedBackupException("Unknown category_uuid '$categoryUuid' for product '$uuid'")
                    val name = row[3]
                    val purchasePrice = row[4].toLongOrNull() ?: 0L
                    val sellingPrice = row[5].toLongOrNull() ?: 0L
                    val stock = row[6].toDoubleOrNull() ?: 0.0
                    val minStock = row[7].toDoubleOrNull() ?: 0.0
                    val unit = row[8]
                    val itemType = row[9]
                    val isDeleted = row[10].toBoolean()
                    val deletedAt = if (row[11] == "NULL") null else row[11].toLongOrNull()
                    val createdAt = row[12].toLongOrNull() ?: System.currentTimeMillis()
                    val updatedAt = row[13].toLongOrNull() ?: System.currentTimeMillis()
                    val barcode = if (row[14] == "NULL") null else row[14]
                    val imageUri = if (row[15] == "NULL") null else row[15]

                    val cv = ContentValues().apply {
                        put("uuid", uuid)
                        put("business_id", bId)
                        put("category_id", categoryId)
                        put("name", name)
                        put("purchase_price", purchasePrice)
                        put("selling_price", sellingPrice)
                        put("stock", stock)
                        put("minimum_stock", minStock)
                        put("unit", unit)
                        put("item_type", itemType)
                        put("is_deleted", if (isDeleted) 1 else 0)
                        if (deletedAt != null) put("deleted_at", deletedAt) else putNull("deleted_at")
                        put("created_at", createdAt)
                        put("updated_at", updatedAt)
                        if (barcode != null) put("barcode", barcode) else putNull("barcode")
                        if (imageUri != null) put("image_uri", imageUri) else putNull("image_uri")
                    }
                    val newId = db.insert("products", 0, cv)
                    productUuidToId[uuid] = newId
                }

                // 7. Restore Sales
                val saleUuidToId = mutableMapOf<String, Long>()
                snapshot.getTab("06_Sales")?.rows?.forEach { row ->
                    val uuid = row[0]
                    val bId = row[1]
                    val dId = row[2]
                    val txNumber = row[3]
                    val txDate = row[4].toLongOrNull() ?: System.currentTimeMillis()
                    val totalAmount = row[5].toLongOrNull() ?: 0L
                    val paymentMethod = row[6]
                    val createdAt = row[7].toLongOrNull() ?: System.currentTimeMillis()
                    val customerUuid = if (row[8] == "NULL") null else row[8]
                    val customerId = if (customerUuid != null) customerUuidToId[customerUuid] else null

                    val cv = ContentValues().apply {
                        put("uuid", uuid)
                        put("business_id", bId)
                        put("device_id", dId)
                        put("transaction_number", txNumber)
                        put("transaction_date", txDate)
                        put("total_amount", totalAmount)
                        put("payment_method", paymentMethod)
                        put("created_at", createdAt)
                        if (customerId != null) put("customer_id", customerId) else putNull("customer_id")
                    }
                    val newId = db.insert("sales_transactions", 0, cv)
                    saleUuidToId[uuid] = newId
                }

                // 8. Restore SaleItems
                val saleItemUuidToId = mutableMapOf<String, Long>()
                snapshot.getTab("07_SaleItems")?.rows?.forEach { row ->
                    val uuid = row[0]
                    val bId = row[1]
                    val saleUuid = row[2]
                    val productUuid = row[3]
                    val productName = row[4]
                    val qty = row[5].toDoubleOrNull() ?: 0.0
                    val price = row[6].toLongOrNull() ?: 0L
                    val purchasePrice = if (row.size > 8) row[7].toLongOrNull() ?: 0L else 0L
                    val subtotal = if (row.size > 8) row[8].toLongOrNull() ?: 0L else (row.getOrNull(7)?.toLongOrNull() ?: 0L)

                    val txId = saleUuidToId[saleUuid]
                        ?: throw CorruptedBackupException("Unknown sale_uuid '$saleUuid' for sale item '$uuid'")
                    val prodId = productUuidToId[productUuid]
                        ?: throw CorruptedBackupException("Unknown product_uuid '$productUuid' for sale item '$uuid'")

                    val cv = ContentValues().apply {
                        put("uuid", uuid)
                        put("business_id", bId)
                        put("sale_uuid", saleUuid)
                        put("product_uuid", productUuid)
                        put("transaction_id", txId)
                        put("product_id", prodId)
                        put("product_name", productName)
                        put("quantity", qty)
                        put("price", price)
                        put("purchase_price", purchasePrice)
                        put("subtotal", subtotal)
                    }
                    val newId = db.insert("sale_items", 0, cv)
                    saleItemUuidToId[uuid] = newId
                }

                // 9. Restore Purchases
                val purchaseUuidToId = mutableMapOf<String, Long>()
                snapshot.getTab("08_Purchases")?.rows?.forEach { row ->
                    val uuid = row[0]
                    val bId = row[1]
                    val dId = row[2]
                    val txNumber = row[3]
                    val txDate = row[4].toLongOrNull() ?: System.currentTimeMillis()
                    val totalAmount = row[5].toLongOrNull() ?: 0L
                    val paymentMethod = row[6]
                    val createdAt = row[7].toLongOrNull() ?: System.currentTimeMillis()
                    val supplierUuid = if (row[8] == "NULL") null else row[8]
                    val supplierId = if (supplierUuid != null) supplierUuidToId[supplierUuid] else null

                    val cv = ContentValues().apply {
                        put("uuid", uuid)
                        put("business_id", bId)
                        put("device_id", dId)
                        put("transaction_number", txNumber)
                        put("transaction_date", txDate)
                        put("total_amount", totalAmount)
                        put("payment_method", paymentMethod)
                        put("created_at", createdAt)
                        if (supplierId != null) put("supplier_id", supplierId) else putNull("supplier_id")
                    }
                    val newId = db.insert("purchase_transactions", 0, cv)
                    purchaseUuidToId[uuid] = newId
                }

                // 10. Restore PurchaseItems
                snapshot.getTab("09_PurchaseItems")?.rows?.forEach { row ->
                    val uuid = row[0]
                    val bId = row[1]
                    val purchaseUuid = row[2]
                    val productUuid = row[3]
                    val productName = row[4]
                    val qty = row[5].toDoubleOrNull() ?: 0.0
                    val price = row[6].toLongOrNull() ?: 0L
                    val subtotal = row[7].toLongOrNull() ?: 0L

                    val txId = purchaseUuidToId[purchaseUuid]
                        ?: throw CorruptedBackupException("Unknown purchase_uuid '$purchaseUuid' for purchase item '$uuid'")
                    val prodId = productUuidToId[productUuid]
                        ?: throw CorruptedBackupException("Unknown product_uuid '$productUuid' for purchase item '$uuid'")

                    val cv = ContentValues().apply {
                        put("uuid", uuid)
                        put("business_id", bId)
                        put("purchase_uuid", purchaseUuid)
                        put("product_uuid", productUuid)
                        put("transaction_id", txId)
                        put("product_id", prodId)
                        put("product_name", productName)
                        put("quantity", qty)
                        put("purchase_price", price)
                        put("subtotal", subtotal)
                    }
                    db.insert("purchase_items", 0, cv)
                }

                // 11. Restore Debts
                val debtUuidToId = mutableMapOf<String, Long>()
                snapshot.getTab("10_Debts")?.rows?.forEach { row ->
                    val uuid = row[0]
                    val bId = row[1]
                    val customerUuid = row[2]
                    val saleUuid = if (row[3] == "NULL") null else row[3]
                    val totalDebt = row[4].toLongOrNull() ?: 0L
                    val paidAmount = row[5].toLongOrNull() ?: 0L
                    val status = row[6]
                    val createdAt = row[7].toLongOrNull() ?: System.currentTimeMillis()
                    val updatedAt = row[8].toLongOrNull() ?: System.currentTimeMillis()

                    val customerId = customerUuidToId[customerUuid]
                        ?: throw CorruptedBackupException("Unknown customer_uuid '$customerUuid' for debt '$uuid'")
                    val saleTxId = if (saleUuid != null) saleUuidToId[saleUuid] else null

                    val cv = ContentValues().apply {
                        put("uuid", uuid)
                        put("business_id", bId)
                        put("customer_id", customerId)
                        if (saleTxId != null) put("sale_transaction_id", saleTxId) else putNull("sale_transaction_id")
                        put("customer_uuid", customerUuid)
                        put("sale_uuid", saleUuid ?: "")
                        put("total_debt", totalDebt)
                        put("paid_amount", paidAmount)
                        put("status", status)
                        put("created_at", createdAt)
                        put("updated_at", updatedAt)
                    }
                    val newId = db.insert("debts", 0, cv)
                    debtUuidToId[uuid] = newId
                }

                // 12. Restore DebtPayments
                snapshot.getTab("11_DebtPayments")?.rows?.forEach { row ->
                    val uuid = row[0]
                    val bId = row[1]
                    val dId = row[2]
                    val debtUuid = row[3]
                    val amount = row[4].toLongOrNull() ?: 0L
                    val paymentDate = row[5].toLongOrNull() ?: System.currentTimeMillis()
                    val note = if (row[6] == "NULL") null else row[6]
                    val createdAt = row[7].toLongOrNull() ?: System.currentTimeMillis()

                    val debtId = debtUuidToId[debtUuid]
                        ?: throw CorruptedBackupException("Unknown debt_uuid '$debtUuid' for debt payment '$uuid'")

                    val cv = ContentValues().apply {
                        put("uuid", uuid)
                        put("business_id", bId)
                        put("device_id", dId)
                        put("debt_id", debtId)
                        put("debt_uuid", debtUuid)
                        put("amount", amount)
                        put("payment_date", paymentDate)
                        if (note != null) put("note", note) else putNull("note")
                        put("created_at", createdAt)
                    }
                    db.insert("debt_payments", 0, cv)
                }

                // 13. Restore SupplierPayables
                val payableUuidToId = mutableMapOf<String, Long>()
                snapshot.getTab("13_SupplierPayables")?.rows?.forEach { row ->
                    val uuid = row[0]
                    val bId = row[1]
                    val supplierUuid = row[2]
                    val purchaseUuid = if (row[3] == "NULL") null else row[3]
                    val totalDebt = row[4].toLongOrNull() ?: 0L
                    val paidAmount = row[5].toLongOrNull() ?: 0L
                    val status = row[6]
                    val createdAt = row[7].toLongOrNull() ?: System.currentTimeMillis()
                    val updatedAt = row[8].toLongOrNull() ?: System.currentTimeMillis()

                    val supplierId = supplierUuidToId[supplierUuid]
                        ?: throw CorruptedBackupException("Unknown supplier_uuid '$supplierUuid' for supplier payable '$uuid'")
                    val purchaseTxId = if (purchaseUuid != null) purchaseUuidToId[purchaseUuid] else null

                    val cv = ContentValues().apply {
                        put("uuid", uuid)
                        put("business_id", bId)
                        put("supplier_id", supplierId)
                        if (purchaseTxId != null) put("purchase_transaction_id", purchaseTxId) else putNull("purchase_transaction_id")
                        put("supplier_uuid", supplierUuid)
                        put("purchase_uuid", purchaseUuid ?: "")
                        put("total_debt", totalDebt)
                        put("paid_amount", paidAmount)
                        put("status", status)
                        put("created_at", createdAt)
                        put("updated_at", updatedAt)
                    }
                    val newId = db.insert("supplier_payables", 0, cv)
                    payableUuidToId[uuid] = newId
                }

                // 14. Restore SupplierPayments
                snapshot.getTab("14_SupplierPayments")?.rows?.forEach { row ->
                    val uuid = row[0]
                    val bId = row[1]
                    val dId = row[2]
                    val payableUuid = row[3]
                    val amount = row[4].toLongOrNull() ?: 0L
                    val paymentDate = row[5].toLongOrNull() ?: System.currentTimeMillis()
                    val note = if (row[6] == "NULL") null else row[6]
                    val createdAt = row[7].toLongOrNull() ?: System.currentTimeMillis()

                    val payableId = payableUuidToId[payableUuid]
                        ?: throw CorruptedBackupException("Unknown payable_uuid '$payableUuid' for supplier payment '$uuid'")

                    val cv = ContentValues().apply {
                        put("uuid", uuid)
                        put("business_id", bId)
                        put("device_id", dId)
                        put("payable_id", payableId)
                        put("payable_uuid", payableUuid)
                        put("amount", amount)
                        put("payment_date", paymentDate)
                        if (note != null) put("note", note) else putNull("note")
                        put("created_at", createdAt)
                    }
                    db.insert("supplier_payments", 0, cv)
                }

                // 15. Restore CashTransactions
                snapshot.getTab("15_CashTransactions")?.rows?.forEach { row ->
                    val uuid = row[0]
                    val bId = row[1]
                    val dId = row[2]
                    val type = row[3]
                    val amount = row[4].toLongOrNull() ?: 0L
                    val description = row[5]
                    val refUuid = if (row[6] == "NULL") null else row[6]
                    val createdAt = row[7].toLongOrNull() ?: System.currentTimeMillis()

                    val cv = ContentValues().apply {
                        put("uuid", uuid)
                        put("business_id", bId)
                        put("device_id", dId)
                        put("type", type)
                        put("amount", amount)
                        put("description", description)
                        putNull("ref_id")
                        if (refUuid != null) put("ref_uuid", refUuid) else putNull("ref_uuid")
                        put("created_at", createdAt)
                    }
                    db.insert("cash_transactions", 0, cv)
                }

                // 16. Restore StockMovements
                snapshot.getTab("16_StockMovements")?.rows?.forEach { row ->
                    val uuid = row[0]
                    val bId = row[1]
                    val dId = row[2]
                    val productUuid = row[3]
                    val movementType = row[4]
                    val deltaQty = row[5].toDoubleOrNull() ?: 0.0
                    val currentStock = row[6].toDoubleOrNull() ?: 0.0
                    val refUuid = if (row[7] == "NULL") null else row[7]
                    val note = if (row[8] == "NULL") null else row[8]
                    val createdAt = row[9].toLongOrNull() ?: System.currentTimeMillis()

                    val cv = ContentValues().apply {
                        put("uuid", uuid)
                        put("business_id", bId)
                        put("device_id", dId)
                        put("product_uuid", productUuid)
                        put("movement_type", movementType)
                        put("delta_quantity", deltaQty)
                        put("current_stock_snapshot", currentStock)
                        if (refUuid != null) put("reference_uuid", refUuid) else putNull("reference_uuid")
                        if (note != null) put("note", note) else putNull("note")
                        put("created_at", createdAt)
                    }
                    db.insert("stock_movements", 0, cv)
                }

                // 17. Restore SaleReturnTransactions
                val returnUuidToId = mutableMapOf<String, Long>()
                snapshot.getTab("17_SaleReturns")?.rows?.forEach { row ->
                    val uuid = row[0]
                    val bId = row[1]
                    val dId = row[2]
                    val retNumber = row[3]
                    val retDate = row[4].toLongOrNull() ?: System.currentTimeMillis()
                    val saleUuid = row[5]
                    val customerUuid = if (row[6] == "NULL") null else row[6]
                    val totalRefund = row[7].toLongOrNull() ?: 0L
                    val refundMethod = row[8]
                    val reason = if (row[9] == "NULL") null else row[9]
                    val notes = if (row[10] == "NULL") null else row[10]
                    val createdAt = row[11].toLongOrNull() ?: System.currentTimeMillis()

                    val saleTxId = saleUuidToId[saleUuid]
                        ?: throw CorruptedBackupException("Unknown sale_uuid '$saleUuid' for return '$uuid'")
                    val customerId = if (customerUuid != null) customerUuidToId[customerUuid] else null

                    val cv = ContentValues().apply {
                        put("uuid", uuid)
                        put("business_id", bId)
                        put("device_id", dId)
                        put("return_number", retNumber)
                        put("return_date", retDate)
                        put("sale_transaction_id", saleTxId)
                        put("sale_uuid", saleUuid)
                        if (customerId != null) put("customer_id", customerId) else putNull("customer_id")
                        if (customerUuid != null) put("customer_uuid", customerUuid) else putNull("customer_uuid")
                        put("total_refund_amount", totalRefund)
                        put("refund_method", refundMethod)
                        if (reason != null) put("reason", reason) else putNull("reason")
                        if (notes != null) put("notes", notes) else putNull("notes")
                        put("created_at", createdAt)
                    }
                    val newId = db.insert("sale_return_transactions", 0, cv)
                    returnUuidToId[uuid] = newId
                }

                // 18. Restore SaleReturnItems
                snapshot.getTab("18_SaleReturnItems")?.rows?.forEach { row ->
                    val uuid = row[0]
                    val bId = row[1]
                    val returnUuid = row[2]
                    val saleItemUuid = row[3]
                    val productUuid = row[4]
                    val productName = row[5]
                    val qty = row[6].toDoubleOrNull() ?: 0.0
                    val price = row[7].toLongOrNull() ?: 0L
                    val purchasePrice = if (row.size > 10) row[8].toLongOrNull() ?: 0L else 0L
                    val subtotal = if (row.size > 10) row[9].toLongOrNull() ?: 0L else (row.getOrNull(8)?.toLongOrNull() ?: 0L)
                    val createdAt = if (row.size > 10) row[10].toLongOrNull() ?: System.currentTimeMillis() else (row.getOrNull(9)?.toLongOrNull() ?: System.currentTimeMillis())

                    val returnTxId = returnUuidToId[returnUuid]
                        ?: throw CorruptedBackupException("Unknown return_uuid '$returnUuid' for return item '$uuid'")
                    val saleItemId = saleItemUuidToId[saleItemUuid]
                        ?: throw CorruptedBackupException("Unknown sale_item_uuid '$saleItemUuid' for return item '$uuid'")
                    val prodId = productUuidToId[productUuid]
                        ?: throw CorruptedBackupException("Unknown product_uuid '$productUuid' for return item '$uuid'")

                    val cv = ContentValues().apply {
                        put("uuid", uuid)
                        put("business_id", bId)
                        put("return_uuid", returnUuid)
                        put("sale_item_uuid", saleItemUuid)
                        put("product_uuid", productUuid)
                        put("return_transaction_id", returnTxId)
                        put("sale_item_id", saleItemId)
                        put("product_id", prodId)
                        put("product_name", productName)
                        put("quantity", qty)
                        put("price", price)
                        put("purchase_price", purchasePrice)
                        put("subtotal", subtotal)
                        put("created_at", createdAt)
                    }
                    db.insert("sale_return_items", 0, cv)
                }

                // Verify Foreign Key constraints
                db.query("PRAGMA foreign_key_check").use { cursor ->
                    if (cursor.count > 0) {
                        throw CorruptedBackupException("PRAGMA foreign_key_check failed with ${cursor.count} violations")
                    }
                }
            }
            // Room COMMIT succeeded! Room state is now authoritative.

            // Phase 3: Update DataStore (Separate persistence boundary)
            try {
                val businessTab = snapshot.getTab("01_Business")
                if (businessTab != null && businessTab.rows.isNotEmpty()) {
                    val row = businessTab.rows[0]
                    val businessId = row.getOrElse(0) { "" }
                    val shopName = row.getOrElse(1) { "Warung Saya" }
                    val ownerName = row.getOrElse(2) { "" }
                    val phone = row.getOrElse(3) { "" }
                    val address = row.getOrElse(4) { "" }

                    if (businessId.isNotBlank()) {
                        userPreferencesRepository.saveShopProfile(
                            shopName = if (shopName == "NULL") "Warung Saya" else shopName,
                            ownerName = if (ownerName == "NULL") "" else ownerName,
                            phone = if (phone == "NULL") "" else phone,
                            address = if (address == "NULL") "" else address
                        )
                    }
                }
            } catch (e: Exception) {
                // IMPORTANT: Do NOT attempt to rollback Room. Room is already committed and authoritative.
                // Log exception and allow deterministic startup reconciliation to resolve DataStore later.
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Reconciles DataStore from authoritative Room database rows if out-of-sync or missing.
     */
    suspend fun reconcileDataStoreFromRoom(): Unit = withContext(Dispatchers.IO) {
        val sdb = database.openHelper.readableDatabase
        val tables = listOf("categories", "products", "sales_transactions", "purchase_transactions", "customers", "suppliers", "cash_transactions")
        var authoritativeBusinessId: String? = null

        for (table in tables) {
            try {
                val cursor = sdb.query("SELECT business_id FROM `$table` WHERE business_id != '' AND business_id != 'LEGACY_BUSINESS' LIMIT 1")
                cursor.use {
                    if (it.moveToFirst()) {
                        val bId = it.getString(0)
                        if (!bId.isNullOrBlank()) {
                            authoritativeBusinessId = bId
                        }
                    }
                }
                if (authoritativeBusinessId != null) break
            } catch (ignored: Exception) {}
        }

        if (authoritativeBusinessId != null) {
            val userSettings = userPreferencesRepository.userSettings.first()
            if (userSettings.businessId.isBlank() || userSettings.businessId == "LEGACY_BUSINESS") {
                userPreferencesRepository.saveShopProfile(
                    shopName = userSettings.shopName,
                    ownerName = userSettings.ownerName,
                    phone = userSettings.phone,
                    address = userSettings.address
                )
            }
        }
    }

    /**
     * Convenient full backup to transport SPI.
     */
    suspend fun performBackup(spreadsheetId: String): Result<Unit> {
        return try {
            val snapshot = exportSnapshot()
            transport.writeBackup(spreadsheetId, snapshot)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Convenient full restore from transport SPI.
     */
    suspend fun performRestore(spreadsheetId: String): Result<Unit> {
        return try {
            val readResult = transport.readBackup(spreadsheetId)
            if (readResult.isFailure) {
                return Result.failure(readResult.exceptionOrNull() ?: Exception("Failed to read from transport"))
            }
            val snapshot = readResult.getOrThrow()
            restoreSnapshot(snapshot)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
