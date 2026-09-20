package id.skmnetwork.bukuwarung.backup

import android.content.ContentValues
import android.database.Cursor
import androidx.room.withTransaction
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.preferences.UserPreferencesRepository
import id.skmnetwork.bukuwarung.domain.business.BusinessActivity
import id.skmnetwork.bukuwarung.domain.business.BusinessCapability
import id.skmnetwork.bukuwarung.domain.business.BusinessTaxonomyRegistry
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
    val transport: SheetsBackupTransport = MockSheetsTransport()
) {

    /**
     * Creates a new spreadsheet if transport supports creation, or returns a deterministic identifier.
     */
    suspend fun ensureSpreadsheetCreated(title: String): Result<String> {
        return if (transport is id.skmnetwork.bukuwarung.backup.transport.GoogleSheetsApiTransport) {
            transport.createSpreadsheet(title)
        } else {
            Result.success("SPREADSHEET_${System.currentTimeMillis()}")
        }
    }

    /**
     * Captures DataStore + Room snapshot and returns a deterministic BackupSnapshot.
     */
    suspend fun exportSnapshot(): BackupSnapshot = withContext(Dispatchers.IO) {
        val userSettings = userPreferencesRepository.userSettings.first()
        val businessId = userSettings.businessId.ifBlank { "LEGACY_BUSINESS" }
        val deviceId = userSettings.deviceId.ifBlank { "LEGACY_DEVICE" }

        val resolvedProfile = BusinessTaxonomyRegistry.resolve(
            userSettings.primaryBusinessType,
            userSettings.secondaryActivities
        )
        val hasDigitalItems = resolvedProfile.hasCapability(BusinessCapability.CAP_DIGITAL_ITEMS)
        val hasWholesalePurchase = resolvedProfile.hasActivity(BusinessActivity.ACTIVITY_WHOLESALE_PURCHASE)

        val db = database.openHelper.readableDatabase

        // 1. Tab 01_Business
        val secondaryActivitiesStr = userSettings.secondaryActivities.sorted().joinToString(",")
        val businessRows = listOf(
            listOf(
                businessId,
                CanonicalSerializer.sanitize(userSettings.shopName),
                CanonicalSerializer.sanitize(userSettings.ownerName),
                CanonicalSerializer.sanitize(userSettings.phone),
                CanonicalSerializer.sanitize(userSettings.address),
                "0",
                CanonicalSerializer.sanitize(userSettings.primaryBusinessType),
                CanonicalSerializer.sanitize(secondaryActivitiesStr),
                userSettings.profileVersion.toString()
            )
        )
        val tab01 = SheetTab(
            name = "01_Business",
            headers = listOf(
                "business_id",
                "shop_name",
                "owner_name",
                "phone",
                "address",
                "created_at",
                "primary_business_type",
                "secondary_activities",
                "profile_version"
            ),
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
        db.query("SELECT id, uuid, business_id, name, is_deleted, deleted_at, created_at FROM categories WHERE business_id = ?", arrayOf(businessId)).use { cursor ->
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
        db.query("SELECT id, uuid, business_id, name, phone, address, is_deleted, deleted_at, created_at, updated_at FROM customers WHERE business_id = ?", arrayOf(businessId)).use { cursor ->
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
        db.query("SELECT id, uuid, business_id, name, phone, address, is_deleted, deleted_at, created_at, updated_at FROM suppliers WHERE business_id = ?", arrayOf(businessId)).use { cursor ->
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
        db.query("SELECT id, uuid, business_id, category_id, name, purchase_price, selling_price, stock, minimum_stock, unit, item_type, is_deleted, deleted_at, created_at, updated_at, barcode, image_uri, taxable, tax_rate_override FROM products WHERE business_id = ?", arrayOf(businessId)).use { cursor ->
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
                val taxable = cursor.getInt(17) == 1
                val taxRateOverride = if (cursor.isNull(18)) null else cursor.getDouble(18)

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
                        CanonicalSerializer.sanitize(imageUri),
                        taxable.toString(),
                        CanonicalSerializer.formatDouble(taxRateOverride)
                    )
                )
            }
        }
        val tab04 = SheetTab(
            name = "04_Products",
            headers = listOf("uuid", "business_id", "category_uuid", "name", "purchase_price", "selling_price", "stock", "minimum_stock", "unit", "item_type", "is_deleted", "deleted_at", "created_at", "updated_at", "barcode", "image_uri", "taxable", "tax_rate_override"),
            rows = CanonicalSerializer.sortTabRows("04_Products", productRows)
        )

        // 4. Tab 06_Sales
        val saleRows = mutableListOf<List<String>>()
        db.query("SELECT id, uuid, business_id, device_id, transaction_number, transaction_date, total_amount, payment_method, created_at, customer_id, discount_amount, subtotal_amount, taxable_base_snapshot, tax_rate_snapshot, tax_amount_snapshot FROM sales_transactions WHERE business_id = ?", arrayOf(businessId)).use { cursor ->
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
                val discountAmount = if (cursor.columnCount > 10 && !cursor.isNull(10)) cursor.getLong(10) else 0L
                val subtotalAmount = if (cursor.columnCount > 11 && !cursor.isNull(11)) cursor.getLong(11) else 0L
                val taxableBaseSnapshot = if (cursor.columnCount > 12 && !cursor.isNull(12)) cursor.getLong(12) else 0L
                val taxRateSnapshot = if (cursor.columnCount > 13 && !cursor.isNull(13)) cursor.getDouble(13) else 0.0
                val taxAmountSnapshot = if (cursor.columnCount > 14 && !cursor.isNull(14)) cursor.getLong(14) else 0L

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
                        customerUuid,
                        discountAmount.toString(),
                        subtotalAmount.toString(),
                        taxableBaseSnapshot.toString(),
                        taxRateSnapshot.toString(),
                        taxAmountSnapshot.toString()
                    )
                )
            }
        }
        val tab06 = SheetTab(
            name = "06_Sales",
            headers = listOf("uuid", "business_id", "device_id", "transaction_number", "transaction_date", "total_amount", "payment_method", "created_at", "customer_uuid", "discount_amount", "subtotal_amount", "taxable_base_snapshot", "tax_rate_snapshot", "tax_amount_snapshot"),
            rows = CanonicalSerializer.sortTabRows("06_Sales", saleRows)
        )

        // 5. Tab 07_SaleItems
        val saleItemRows = mutableListOf<List<String>>()
        db.query("SELECT id, uuid, business_id, sale_uuid, product_uuid, product_name, quantity, price, purchase_price, subtotal, taxable, tax_rate_snapshot, tax_amount_snapshot FROM sale_items WHERE business_id = ?", arrayOf(businessId)).use { cursor ->
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
                val taxable = cursor.getInt(10) == 1
                val taxRateSnapshot = if (cursor.isNull(11)) null else cursor.getDouble(11)
                val taxAmountSnapshot = if (cursor.isNull(12)) null else cursor.getLong(12)

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
                        subtotal.toString(),
                        taxable.toString(),
                        CanonicalSerializer.formatDouble(taxRateSnapshot),
                        CanonicalSerializer.formatLong(taxAmountSnapshot)
                    )
                )
            }
        }
        val tab07 = SheetTab(
            name = "07_SaleItems",
            headers = listOf("uuid", "business_id", "sale_uuid", "product_uuid", "product_name", "quantity", "price", "purchase_price", "subtotal", "taxable", "tax_rate_snapshot", "tax_amount_snapshot"),
            rows = CanonicalSerializer.sortTabRows("07_SaleItems", saleItemRows)
        )

        // 6. Tab 08_Purchases
        val purchaseRows = mutableListOf<List<String>>()
        db.query("SELECT id, uuid, business_id, device_id, transaction_number, transaction_date, total_amount, payment_method, created_at, supplier_id, subtotal_amount, taxable_base_snapshot, tax_rate_snapshot, tax_amount_snapshot FROM purchase_transactions WHERE business_id = ?", arrayOf(businessId)).use { cursor ->
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
                val subtotalAmount = if (cursor.columnCount > 10 && !cursor.isNull(10)) cursor.getLong(10) else 0L
                val taxableBaseSnapshot = if (cursor.columnCount > 11 && !cursor.isNull(11)) cursor.getLong(11) else 0L
                val taxRateSnapshot = if (cursor.columnCount > 12 && !cursor.isNull(12)) cursor.getDouble(12) else 0.0
                val taxAmountSnapshot = if (cursor.columnCount > 13 && !cursor.isNull(13)) cursor.getLong(13) else 0L

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
                        supplierUuid,
                        subtotalAmount.toString(),
                        taxableBaseSnapshot.toString(),
                        taxRateSnapshot.toString(),
                        taxAmountSnapshot.toString()
                    )
                )
            }
        }
        val tab08 = SheetTab(
            name = "08_Purchases",
            headers = listOf("uuid", "business_id", "device_id", "transaction_number", "transaction_date", "total_amount", "payment_method", "created_at", "supplier_uuid", "subtotal_amount", "taxable_base_snapshot", "tax_rate_snapshot", "tax_amount_snapshot"),
            rows = CanonicalSerializer.sortTabRows("08_Purchases", purchaseRows)
        )

        // 7. Tab 09_PurchaseItems
        val purchaseItemRows = mutableListOf<List<String>>()
        db.query("SELECT id, uuid, business_id, purchase_uuid, product_uuid, product_name, quantity, purchase_price, subtotal, taxable, tax_rate_snapshot, tax_amount_snapshot FROM purchase_items WHERE business_id = ?", arrayOf(businessId)).use { cursor ->
            while (cursor.moveToNext()) {
                val uuid = cursor.getString(1)
                val bId = cursor.getString(2)
                val purchaseUuid = cursor.getString(3)
                val productUuid = cursor.getString(4)
                val productName = cursor.getString(5)
                val qty = cursor.getDouble(6)
                val price = cursor.getLong(7)
                val subtotal = cursor.getLong(8)
                val taxable = cursor.getInt(9) == 1
                val taxRateSnapshot = if (cursor.isNull(10)) null else cursor.getDouble(10)
                val taxAmountSnapshot = if (cursor.isNull(11)) null else cursor.getLong(11)

                purchaseItemRows.add(
                    listOf(
                        uuid,
                        bId,
                        purchaseUuid,
                        productUuid,
                        CanonicalSerializer.sanitize(productName),
                        CanonicalSerializer.formatDouble(qty),
                        price.toString(),
                        subtotal.toString(),
                        taxable.toString(),
                        CanonicalSerializer.formatDouble(taxRateSnapshot),
                        CanonicalSerializer.formatLong(taxAmountSnapshot)
                    )
                )
            }
        }
        val tab09 = SheetTab(
            name = "09_PurchaseItems",
            headers = listOf("uuid", "business_id", "purchase_uuid", "product_uuid", "product_name", "quantity", "purchase_price", "subtotal", "taxable", "tax_rate_snapshot", "tax_amount_snapshot"),
            rows = CanonicalSerializer.sortTabRows("09_PurchaseItems", purchaseItemRows)
        )

        // 8. Tab 10_Debts
        val debtRows = mutableListOf<List<String>>()
        db.query("SELECT id, uuid, business_id, customer_uuid, sale_uuid, total_debt, paid_amount, status, created_at, updated_at FROM debts WHERE business_id = ?", arrayOf(businessId)).use { cursor ->
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
        db.query("SELECT id, uuid, business_id, device_id, debt_uuid, amount, payment_date, note, created_at FROM debt_payments WHERE business_id = ?", arrayOf(businessId)).use { cursor ->
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
        db.query("SELECT id, uuid, business_id, supplier_uuid, purchase_uuid, total_debt, paid_amount, status, created_at, updated_at FROM supplier_payables WHERE business_id = ?", arrayOf(businessId)).use { cursor ->
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
        db.query("SELECT id, uuid, business_id, device_id, payable_uuid, amount, payment_date, note, created_at FROM supplier_payments WHERE business_id = ?", arrayOf(businessId)).use { cursor ->
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
        db.query("SELECT id, uuid, business_id, device_id, type, amount, description, ref_uuid, created_at FROM cash_transactions WHERE business_id = ?", arrayOf(businessId)).use { cursor ->
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
        db.query("SELECT id, uuid, business_id, device_id, product_uuid, movement_type, delta_quantity, current_stock_snapshot, reference_uuid, note, created_at FROM stock_movements WHERE business_id = ?", arrayOf(businessId)).use { cursor ->
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
        db.query("SELECT id, uuid, business_id, device_id, return_number, return_date, sale_uuid, customer_uuid, total_refund_amount, refund_method, reason, notes, taxable_base_snapshot, tax_rate_snapshot, tax_amount_snapshot, created_at FROM sale_return_transactions WHERE business_id = ?", arrayOf(businessId)).use { cursor ->
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
                val taxableBaseSnapshot = if (cursor.isNull(12)) 0L else cursor.getLong(12)
                val taxRateSnapshot = if (cursor.isNull(13)) 0.0 else cursor.getDouble(13)
                val taxAmountSnapshot = if (cursor.isNull(14)) 0L else cursor.getLong(14)
                val createdAt = cursor.getLong(15)

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
                        taxableBaseSnapshot.toString(),
                        taxRateSnapshot.toString(),
                        taxAmountSnapshot.toString(),
                        createdAt.toString()
                    )
                )
            }
        }
        val tab17 = SheetTab(
            name = "17_SaleReturns",
            headers = listOf("uuid", "business_id", "device_id", "return_number", "return_date", "sale_uuid", "customer_uuid", "total_refund_amount", "refund_method", "reason", "notes", "taxable_base_snapshot", "tax_rate_snapshot", "tax_amount_snapshot", "created_at"),
            rows = CanonicalSerializer.sortTabRows("17_SaleReturns", returnRows)
        )

        // 15. Tab 18_SaleReturnItems
        val returnItemRows = mutableListOf<List<String>>()
        db.query("SELECT id, uuid, business_id, return_uuid, sale_item_uuid, product_uuid, product_name, quantity, price, purchase_price, subtotal, taxable, tax_rate_snapshot, tax_amount_snapshot, created_at FROM sale_return_items WHERE business_id = ?", arrayOf(businessId)).use { cursor ->
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
                val taxable = if (cursor.isNull(11)) "true" else cursor.getInt(11).toString()
                val taxRateSnapshot = if (cursor.isNull(12)) null else cursor.getDouble(12)
                val taxAmountSnapshot = if (cursor.isNull(13)) null else cursor.getLong(13)
                val createdAt = cursor.getLong(14)

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
                        taxable,
                        CanonicalSerializer.formatDouble(taxRateSnapshot),
                        CanonicalSerializer.formatLong(taxAmountSnapshot),
                        createdAt.toString()
                    )
                )
            }
        }
        val tab18 = SheetTab(
            name = "18_SaleReturnItems",
            headers = listOf("uuid", "business_id", "return_uuid", "sale_item_uuid", "product_uuid", "product_name", "quantity", "price", "purchase_price", "subtotal", "taxable", "tax_rate_snapshot", "tax_amount_snapshot", "created_at"),
            rows = CanonicalSerializer.sortTabRows("18_SaleReturnItems", returnItemRows)
        )

        // 16. Tab 19_DigitalTransactions (conditional on CAP_DIGITAL_ITEMS)
        val tab19 = if (hasDigitalItems) {
            val digitalRows = mutableListOf<List<String>>()
            db.query("SELECT dt.uuid, dt.business_id, si.uuid as sale_item_uuid, dt.provider_id, dt.provider_product_code, dt.destination_number, dt.selling_price, dt.actual_purchase_price, dt.status, dt.provider_reference_id, dt.sn_token, dt.failure_reason, dt.created_at, dt.updated_at FROM digital_transactions dt LEFT JOIN sale_items si ON dt.sale_item_id = si.id WHERE dt.business_id = ?", arrayOf(businessId)).use { cursor ->
                while (cursor.moveToNext()) {
                    val uuid = cursor.getString(0)
                    val bId = cursor.getString(1)
                    val saleItemUuid = cursor.getString(2) ?: "NULL"
                    val providerId = cursor.getString(3)
                    val providerProductCode = cursor.getString(4)
                    val destinationNumber = cursor.getString(5)
                    val sellingPrice = cursor.getLong(6)
                    val actualPurchasePrice = cursor.getLong(7)
                    val status = cursor.getString(8)
                    val providerReferenceId = if (cursor.isNull(9)) null else cursor.getString(9)
                    val snToken = if (cursor.isNull(10)) null else cursor.getString(10)
                    val failureReason = if (cursor.isNull(11)) null else cursor.getString(11)
                    val createdAt = cursor.getLong(12)
                    val updatedAt = cursor.getLong(13)

                    digitalRows.add(
                        listOf(
                            uuid,
                            bId,
                            saleItemUuid,
                            CanonicalSerializer.sanitize(providerId),
                            CanonicalSerializer.sanitize(providerProductCode),
                            CanonicalSerializer.sanitize(destinationNumber),
                            sellingPrice.toString(),
                            actualPurchasePrice.toString(),
                            status,
                            CanonicalSerializer.sanitize(providerReferenceId),
                            CanonicalSerializer.sanitize(snToken),
                            CanonicalSerializer.sanitize(failureReason),
                            createdAt.toString(),
                            updatedAt.toString()
                        )
                    )
                }
            }
            SheetTab(
                name = "19_DigitalTransactions",
                headers = listOf("uuid", "business_id", "sale_item_uuid", "provider_id", "provider_product_code", "destination_number", "selling_price", "actual_purchase_price", "status", "provider_reference_id", "sn_token", "failure_reason", "created_at", "updated_at"),
                rows = CanonicalSerializer.sortTabRows("19_DigitalTransactions", digitalRows)
            )
        } else null

        // 17. Tab 20_PurchaseOrders + 21_PurchaseOrderItems (conditional on ACTIVITY_WHOLESALE_PURCHASE)
        val tab20 = if (hasWholesalePurchase) {
            val poRows = mutableListOf<List<String>>()
            db.query("SELECT po.id, po.uuid, po.business_id, po.device_id, po.order_number, s.uuid as supplier_uuid, po.supplier_name_snapshot, po.supplier_phone_snapshot, po.status, po.total_estimated_amount, po.notes, po.created_at, po.updated_at, po.sent_at, po.received_at, po.final_purchase_id FROM purchase_orders po LEFT JOIN suppliers s ON po.supplier_id = s.id WHERE po.business_id = ?", arrayOf(businessId)).use { cursor ->
                while (cursor.moveToNext()) {
                    val uuid = cursor.getString(1)
                    val bId = cursor.getString(2)
                    val dId = cursor.getString(3)
                    val orderNumber = cursor.getString(4)
                    val supplierUuid = cursor.getString(5) ?: "NULL"
                    val supplierNameSnapshot = cursor.getString(6)
                    val supplierPhoneSnapshot = if (cursor.isNull(7)) null else cursor.getString(7)
                    val status = cursor.getString(8)
                    val totalEstimatedAmount = cursor.getLong(9)
                    val notes = if (cursor.isNull(10)) null else cursor.getString(10)
                    val createdAt = cursor.getLong(11)
                    val updatedAt = cursor.getLong(12)
                    val sentAt = if (cursor.isNull(13)) null else cursor.getLong(13)
                    val receivedAt = if (cursor.isNull(14)) null else cursor.getLong(14)
                    val finalPurchaseId = if (cursor.isNull(15)) null else cursor.getLong(15)

                    poRows.add(
                        listOf(
                            uuid,
                            bId,
                            dId,
                            CanonicalSerializer.sanitize(orderNumber),
                            supplierUuid,
                            CanonicalSerializer.sanitize(supplierNameSnapshot),
                            CanonicalSerializer.sanitize(supplierPhoneSnapshot),
                            status,
                            totalEstimatedAmount.toString(),
                            CanonicalSerializer.sanitize(notes),
                            createdAt.toString(),
                            updatedAt.toString(),
                            CanonicalSerializer.formatLong(sentAt),
                            CanonicalSerializer.formatLong(receivedAt),
                            CanonicalSerializer.formatLong(finalPurchaseId)
                        )
                    )
                }
            }
            SheetTab(
                name = "20_PurchaseOrders",
                headers = listOf("uuid", "business_id", "device_id", "order_number", "supplier_uuid", "supplier_name_snapshot", "supplier_phone_snapshot", "status", "total_estimated_amount", "notes", "created_at", "updated_at", "sent_at", "received_at", "final_purchase_id"),
                rows = CanonicalSerializer.sortTabRows("20_PurchaseOrders", poRows)
            )
        } else null

        val tab21 = if (hasWholesalePurchase) {
            val poiRows = mutableListOf<List<String>>()
            db.query("SELECT id, uuid, business_id, po_uuid, product_uuid, product_name, ordered_quantity, unit, estimated_price, estimated_subtotal, received_quantity, notes FROM purchase_order_items WHERE business_id = ?", arrayOf(businessId)).use { cursor ->
                while (cursor.moveToNext()) {
                    val uuid = cursor.getString(1)
                    val bId = cursor.getString(2)
                    val poUuid = cursor.getString(3)
                    val productUuid = cursor.getString(4)
                    val productName = cursor.getString(5)
                    val orderedQty = cursor.getDouble(6)
                    val unit = cursor.getString(7)
                    val estimatedPrice = cursor.getLong(8)
                    val estimatedSubtotal = cursor.getLong(9)
                    val receivedQty = cursor.getDouble(10)
                    val notes = if (cursor.isNull(11)) null else cursor.getString(11)

                    poiRows.add(
                        listOf(
                            uuid,
                            bId,
                            poUuid,
                            productUuid,
                            CanonicalSerializer.sanitize(productName),
                            CanonicalSerializer.formatDouble(orderedQty),
                            CanonicalSerializer.sanitize(unit),
                            estimatedPrice.toString(),
                            estimatedSubtotal.toString(),
                            CanonicalSerializer.formatDouble(receivedQty),
                            CanonicalSerializer.sanitize(notes)
                        )
                    )
                }
            }
            SheetTab(
                name = "21_PurchaseOrderItems",
                headers = listOf("uuid", "business_id", "po_uuid", "product_uuid", "product_name", "ordered_quantity", "unit", "estimated_price", "estimated_subtotal", "received_quantity", "notes"),
                rows = CanonicalSerializer.sortTabRows("21_PurchaseOrderItems", poiRows)
            )
        } else null

        val dataTabs = mutableMapOf(
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
        tab19?.let { dataTabs["19_DigitalTransactions"] = it }
        tab20?.let { dataTabs["20_PurchaseOrders"] = it }
        tab21?.let { dataTabs["21_PurchaseOrderItems"] = it }

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
            checksum = checksum,
            capabilities = resolvedProfile.capabilities.map { it.id }.toSet(),
            taxEnabled = userSettings.taxEnabled,
            taxRate = userSettings.taxRate,
            taxPriceMode = userSettings.taxPriceMode.name,
            taxApplicability = userSettings.taxApplicability.name,
            taxRoundingMode = userSettings.taxRoundingMode,
            taxEffectiveDate = userSettings.taxEffectiveDate
        )

        val metadataRows = listOf(
            listOf("backup_format_version", metadata.backupFormatVersion),
            listOf("room_schema_version", metadata.roomSchemaVersion.toString()),
            listOf("exported_at", metadata.exportedAt.toString()),
            listOf("business_id", metadata.businessId),
            listOf("device_id", metadata.deviceId),
            listOf("app_version", metadata.appVersion),
            listOf("capabilities", metadata.capabilities.sorted().joinToString(",")),
            listOf("total_records", metadata.totalRecords.toString()),
            listOf("checksum", metadata.checksum),
            listOf("tax_enabled", metadata.taxEnabled.toString()),
            listOf("tax_rate", metadata.taxRate.toString()),
            listOf("tax_price_mode", metadata.taxPriceMode),
            listOf("tax_applicability", metadata.taxApplicability),
            listOf("tax_rounding_mode", metadata.taxRoundingMode),
            listOf("tax_effective_date", metadata.taxEffectiveDate.toString())
        )
        val tab00 = SheetTab(
            name = CanonicalSerializer.METADATA_TAB_NAME,
            headers = listOf("key", "value"),
            rows = metadataRows
        )

        val tab00Readme = CanonicalSerializer.generateReadmeTab(
            metadata = metadata,
            shopName = userSettings.shopName,
            ownerName = userSettings.ownerName,
            primaryBusinessType = userSettings.primaryBusinessType,
            totalRecords = totalRecords
        )

        val allTabs = mutableMapOf<String, SheetTab>()
        allTabs[CanonicalSerializer.README_TAB_NAME] = tab00Readme
        allTabs[CanonicalSerializer.METADATA_TAB_NAME] = tab00
        allTabs.putAll(dataTabs)

        BackupSnapshot(metadata = metadata, tabs = allTabs)
    }

    /**
     * Executes the full atomic restore pipeline.
     */
    suspend fun restoreSnapshot(snapshot: BackupSnapshot, expectedBusinessId: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Phase 1: Pre-validation (Zero Room DB Mutation on failure)
            BackupValidator.validate(snapshot, expectedBusinessId)

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
                db.execSQL("DELETE FROM purchase_order_items")
                db.execSQL("DELETE FROM purchase_orders")
                db.execSQL("DELETE FROM digital_transactions")
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
                    val taxable = if (row.size > 16) row[16].toBoolean() else true
                    val taxRateOverride = if (row.size > 17 && row[17] != "NULL") row[17].toDoubleOrNull() else null

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
                        put("taxable", if (taxable) 1 else 0)
                        if (taxRateOverride != null) put("tax_rate_override", taxRateOverride) else putNull("tax_rate_override")
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
                    val discountAmount = if (row.size > 9) row[9].toLongOrNull() ?: 0L else 0L
                    val subtotalAmount = if (row.size > 10) row[10].toLongOrNull() ?: 0L else 0L
                    val taxableBaseSnapshot = if (row.size > 11) row[11].toLongOrNull() ?: 0L else 0L
                    val taxRateSnapshot = if (row.size > 12) row[12].toDoubleOrNull() ?: 0.0 else 0.0
                    val taxAmountSnapshot = if (row.size > 13) row[13].toLongOrNull() ?: 0L else 0L

                    val cv = ContentValues().apply {
                        put("uuid", uuid)
                        put("business_id", bId)
                        put("device_id", dId)
                        put("transaction_number", txNumber)
                        put("transaction_date", txDate)
                        put("total_amount", totalAmount)
                        put("payment_method", paymentMethod)
                        put("discount_amount", discountAmount)
                        put("subtotal_amount", subtotalAmount)
                        put("taxable_base_snapshot", taxableBaseSnapshot)
                        put("tax_rate_snapshot", taxRateSnapshot)
                        put("tax_amount_snapshot", taxAmountSnapshot)
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
                    val taxable = if (row.size > 9) row[9].toBoolean() else true
                    val taxRateSnapshot = if (row.size > 10 && row[10] != "NULL") row[10].toDoubleOrNull() else null
                    val taxAmountSnapshot = if (row.size > 11 && row[11] != "NULL") row[11].toLongOrNull() else null

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
                        put("taxable", if (taxable) 1 else 0)
                        if (taxRateSnapshot != null) put("tax_rate_snapshot", taxRateSnapshot) else putNull("tax_rate_snapshot")
                        if (taxAmountSnapshot != null) put("tax_amount_snapshot", taxAmountSnapshot) else putNull("tax_amount_snapshot")
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
                    val subtotalAmount = if (row.size > 9) row[9].toLongOrNull() ?: 0L else 0L
                    val taxableBaseSnapshot = if (row.size > 10) row[10].toLongOrNull() ?: 0L else 0L
                    val taxRateSnapshot = if (row.size > 11) row[11].toDoubleOrNull() ?: 0.0 else 0.0
                    val taxAmountSnapshot = if (row.size > 12) row[12].toLongOrNull() ?: 0L else 0L

                    val cv = ContentValues().apply {
                        put("uuid", uuid)
                        put("business_id", bId)
                        put("device_id", dId)
                        put("transaction_number", txNumber)
                        put("transaction_date", txDate)
                        put("total_amount", totalAmount)
                        put("payment_method", paymentMethod)
                        put("created_at", createdAt)
                        put("subtotal_amount", subtotalAmount)
                        put("taxable_base_snapshot", taxableBaseSnapshot)
                        put("tax_rate_snapshot", taxRateSnapshot)
                        put("tax_amount_snapshot", taxAmountSnapshot)
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
                    val taxable = if (row.size > 8) row[8].toBoolean() else true
                    val taxRateSnapshot = if (row.size > 9 && row[9] != "NULL") row[9].toDoubleOrNull() else null
                    val taxAmountSnapshot = if (row.size > 10 && row[10] != "NULL") row[10].toLongOrNull() else null

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
                        put("taxable", if (taxable) 1 else 0)
                        if (taxRateSnapshot != null) put("tax_rate_snapshot", taxRateSnapshot) else putNull("tax_rate_snapshot")
                        if (taxAmountSnapshot != null) put("tax_amount_snapshot", taxAmountSnapshot) else putNull("tax_amount_snapshot")
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
                    val taxableBaseSnapshot = if (row.size > 12) row[12].toLongOrNull() ?: 0L else 0L
                    val taxRateSnapshot = if (row.size > 13) row[13].toDoubleOrNull() ?: 0.0 else 0.0
                    val taxAmountSnapshot = if (row.size > 14) row[14].toLongOrNull() ?: 0L else 0L
                    val createdAt = if (row.size > 14) row[15].toLongOrNull() ?: System.currentTimeMillis() else row[11].toLongOrNull() ?: System.currentTimeMillis()

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
                        put("taxable_base_snapshot", taxableBaseSnapshot)
                        put("tax_rate_snapshot", taxRateSnapshot)
                        put("tax_amount_snapshot", taxAmountSnapshot)
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
                    val taxable = if (row.size > 11) row[10].toBooleanStrictOrNull() ?: true else true
                    val taxRateSnapshot = if (row.size > 12 && row[11] != "NULL") row[11].toDoubleOrNull() else null
                    val taxAmountSnapshot = if (row.size > 13 && row[12] != "NULL") row[12].toLongOrNull() else null
                    val createdAt = if (row.size > 13) row[13].toLongOrNull() ?: System.currentTimeMillis() else if (row.size > 10) row[10].toLongOrNull() ?: System.currentTimeMillis() else (row.getOrNull(9)?.toLongOrNull() ?: System.currentTimeMillis())

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
                        put("taxable", if (taxable) 1 else 0)
                        if (taxRateSnapshot != null) put("tax_rate_snapshot", taxRateSnapshot) else putNull("tax_rate_snapshot")
                        if (taxAmountSnapshot != null) put("tax_amount_snapshot", taxAmountSnapshot) else putNull("tax_amount_snapshot")
                        put("created_at", createdAt)
                    }
                    db.insert("sale_return_items", 0, cv)
                }

                // 19. Restore DigitalTransactions (conditional on CAP_DIGITAL_ITEMS)
                snapshot.getTab("19_DigitalTransactions")?.rows?.forEach { row ->
                    val uuid = row[0]
                    val bId = row[1]
                    val saleItemUuid = row[2]
                    val providerId = row[3]
                    val providerProductCode = row[4]
                    val destinationNumber = row[5]
                    val sellingPrice = row[6].toLongOrNull() ?: 0L
                    val actualPurchasePrice = row[7].toLongOrNull() ?: 0L
                    val status = row[8]
                    val providerReferenceId = if (row[9] == "NULL") null else row[9]
                    val snToken = if (row[10] == "NULL") null else row[10]
                    val failureReason = if (row[11] == "NULL") null else row[11]
                    val createdAt = row[12].toLongOrNull() ?: System.currentTimeMillis()
                    val updatedAt = row[13].toLongOrNull() ?: System.currentTimeMillis()

                    val saleItemId = saleItemUuidToId[saleItemUuid]
                        ?: throw CorruptedBackupException("Unknown sale_item_uuid '$saleItemUuid' for digital transaction '$uuid'")

                    val cv = ContentValues().apply {
                        put("uuid", uuid)
                        put("business_id", bId)
                        put("sale_item_id", saleItemId)
                        put("provider_id", providerId)
                        put("provider_product_code", providerProductCode)
                        put("destination_number", destinationNumber)
                        put("selling_price", sellingPrice)
                        put("actual_purchase_price", actualPurchasePrice)
                        put("status", status)
                        if (providerReferenceId != null) put("provider_reference_id", providerReferenceId) else putNull("provider_reference_id")
                        if (snToken != null) put("sn_token", snToken) else putNull("sn_token")
                        if (failureReason != null) put("failure_reason", failureReason) else putNull("failure_reason")
                        put("created_at", createdAt)
                        put("updated_at", updatedAt)
                    }
                    db.insert("digital_transactions", 0, cv)
                }

                // 20. Restore PurchaseOrders (conditional on ACTIVITY_WHOLESALE_PURCHASE)
                val purchaseOrderUuidToId = mutableMapOf<String, Long>()
                snapshot.getTab("20_PurchaseOrders")?.rows?.forEach { row ->
                    val uuid = row[0]
                    val bId = row[1]
                    val dId = row[2]
                    val orderNumber = row[3]
                    val supplierUuid = row[4]
                    val supplierNameSnapshot = row[5]
                    val supplierPhoneSnapshot = if (row[6] == "NULL") null else row[6]
                    val status = row[7]
                    val totalEstimatedAmount = row[8].toLongOrNull() ?: 0L
                    val notes = if (row[9] == "NULL") null else row[9]
                    val createdAt = row[10].toLongOrNull() ?: System.currentTimeMillis()
                    val updatedAt = row[11].toLongOrNull() ?: System.currentTimeMillis()
                    val sentAt = if (row[12] == "NULL") null else row[12].toLongOrNull()
                    val receivedAt = if (row[13] == "NULL") null else row[13].toLongOrNull()
                    val finalPurchaseId = if (row[14] == "NULL") null else row[14].toLongOrNull()

                    val supplierId = supplierUuidToId[supplierUuid]
                        ?: throw CorruptedBackupException("Unknown supplier_uuid '$supplierUuid' for purchase order '$uuid'")

                    val cv = ContentValues().apply {
                        put("uuid", uuid)
                        put("business_id", bId)
                        put("device_id", dId)
                        put("order_number", orderNumber)
                        put("supplier_id", supplierId)
                        put("supplier_name_snapshot", supplierNameSnapshot)
                        if (supplierPhoneSnapshot != null) put("supplier_phone_snapshot", supplierPhoneSnapshot) else putNull("supplier_phone_snapshot")
                        put("status", status)
                        put("total_estimated_amount", totalEstimatedAmount)
                        if (notes != null) put("notes", notes) else putNull("notes")
                        put("created_at", createdAt)
                        put("updated_at", updatedAt)
                        if (sentAt != null) put("sent_at", sentAt) else putNull("sent_at")
                        if (receivedAt != null) put("received_at", receivedAt) else putNull("received_at")
                        if (finalPurchaseId != null) put("final_purchase_id", finalPurchaseId) else putNull("final_purchase_id")
                    }
                    val newId = db.insert("purchase_orders", 0, cv)
                    purchaseOrderUuidToId[uuid] = newId
                }

                // 21. Restore PurchaseOrderItems (conditional on ACTIVITY_WHOLESALE_PURCHASE)
                snapshot.getTab("21_PurchaseOrderItems")?.rows?.forEach { row ->
                    val uuid = row[0]
                    val bId = row[1]
                    val poUuid = row[2]
                    val productUuid = row[3]
                    val productName = row[4]
                    val orderedQty = row[5].toDoubleOrNull() ?: 0.0
                    val unit = row[6]
                    val estimatedPrice = row[7].toLongOrNull() ?: 0L
                    val estimatedSubtotal = row[8].toLongOrNull() ?: 0L
                    val receivedQty = row[9].toDoubleOrNull() ?: 0.0
                    val notes = if (row[10] == "NULL") null else row[10]

                    val poId = purchaseOrderUuidToId[poUuid]
                        ?: throw CorruptedBackupException("Unknown po_uuid '$poUuid' for purchase order item '$uuid'")
                    val prodId = productUuidToId[productUuid]
                        ?: throw CorruptedBackupException("Unknown product_uuid '$productUuid' for purchase order item '$uuid'")

                    val cv = ContentValues().apply {
                        put("uuid", uuid)
                        put("business_id", bId)
                        put("purchase_order_id", poId)
                        put("po_uuid", poUuid)
                        put("product_id", prodId)
                        put("product_uuid", productUuid)
                        put("product_name", productName)
                        put("ordered_quantity", orderedQty)
                        put("unit", unit)
                        put("estimated_price", estimatedPrice)
                        put("estimated_subtotal", estimatedSubtotal)
                        put("received_quantity", receivedQty)
                        if (notes != null) put("notes", notes) else putNull("notes")
                    }
                    db.insert("purchase_order_items", 0, cv)
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

                    // v0.2.0 Business Profile extension (with backward-compatible fallbacks)
                    val primaryBusinessType = row.getOrElse(6) { "WARUNG_SEMBAKO" }.ifBlank { "WARUNG_SEMBAKO" }
                    val secondaryActivitiesRaw = row.getOrElse(7) { "ACTIVITY_GOODS_SELLING" }.ifBlank { "ACTIVITY_GOODS_SELLING" }
                    val secondaryActivities = if (secondaryActivitiesRaw == "NULL" || secondaryActivitiesRaw.isBlank()) {
                        setOf("ACTIVITY_GOODS_SELLING")
                    } else {
                        secondaryActivitiesRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet().ifEmpty {
                            setOf("ACTIVITY_GOODS_SELLING")
                        }
                    }
                    val profileVersion = row.getOrElse(8) { "1" }.toIntOrNull() ?: 1

                    if (businessId.isNotBlank()) {
                        userPreferencesRepository.saveShopProfile(
                            shopName = if (shopName == "NULL") "Warung Saya" else shopName,
                            ownerName = if (ownerName == "NULL") "" else ownerName,
                            phone = if (phone == "NULL") "" else phone,
                            address = if (address == "NULL") "" else address
                        )
                        userPreferencesRepository.updateBusinessProfile(
                            primaryType = if (primaryBusinessType == "NULL") "WARUNG_SEMBAKO" else primaryBusinessType,
                            secondaryActivities = secondaryActivities,
                            version = profileVersion
                        )
                    }
                }

                // PR-12.6 Tax/PPN configuration restore from BackupMetadata
                val metadata = snapshot.metadata
                userPreferencesRepository.updateTaxSettings(
                    enabled = metadata.taxEnabled,
                    rate = metadata.taxRate,
                    priceMode = when (metadata.taxPriceMode) {
                        "INCLUSIVE" -> id.skmnetwork.bukuwarung.domain.tax.TaxPriceMode.INCLUSIVE
                        else -> id.skmnetwork.bukuwarung.domain.tax.TaxPriceMode.EXCLUSIVE
                    },
                    applicability = when (metadata.taxApplicability) {
                        "GLOBAL" -> id.skmnetwork.bukuwarung.domain.tax.TaxApplicability.GLOBAL
                        "PRODUCT" -> id.skmnetwork.bukuwarung.domain.tax.TaxApplicability.PRODUCT
                        else -> id.skmnetwork.bukuwarung.domain.tax.TaxApplicability.NONE
                    },
                    roundingMode = metadata.taxRoundingMode
                )
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
        val tables = listOf("categories", "products", "sales_transactions", "purchase_transactions", "customers", "suppliers", "cash_transactions", "digital_transactions", "purchase_orders")
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
    suspend fun performRestore(spreadsheetId: String, expectedBusinessId: String? = null): Result<Unit> {
        return try {
            val readResult = transport.readBackup(spreadsheetId)
            if (readResult.isFailure) {
                return Result.failure(readResult.exceptionOrNull() ?: Exception("Failed to read from transport"))
            }
            val snapshot = readResult.getOrThrow()
            restoreSnapshot(snapshot, expectedBusinessId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
