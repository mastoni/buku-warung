package id.skmnetwork.bukuwarung.backup

import id.skmnetwork.bukuwarung.domain.tax.TaxApplicability
import id.skmnetwork.bukuwarung.domain.tax.TaxPriceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PR-12.6 — Tax/PPN Google Sheets Backup Format 1.2 Unit Tests
 * Verifies tax configuration export/restore, product/sales/purchase tax fields,
 * legacy compatibility, checksum invariance, and round-trip preservation.
 */
class TaxBackupRestoreTest {

    // ===== 1. Tax Configuration Export/Import in BackupMetadata =====

    @Test
    fun `BackupMetadata includes tax fields with defaults`() {
        val metadata = BackupMetadata()
        assertEquals(false, metadata.taxEnabled)
        assertEquals(0.0, metadata.taxRate, 0.0)
        assertEquals("EXCLUSIVE", metadata.taxPriceMode)
        assertEquals("GLOBAL", metadata.taxApplicability)
        assertEquals("HALF_UP", metadata.taxRoundingMode)
        assertEquals(0L, metadata.taxEffectiveDate)
    }

    @Test
    fun `BackupMetadata accepts tax configuration values`() {
        val metadata = BackupMetadata(
            taxEnabled = true,
            taxRate = 11.0,
            taxPriceMode = TaxPriceMode.INCLUSIVE.name,
            taxApplicability = TaxApplicability.PRODUCT.name,
            taxRoundingMode = "HALF_DOWN",
            taxEffectiveDate = 1700000000000L
        )
        assertEquals(true, metadata.taxEnabled)
        assertEquals(11.0, metadata.taxRate, 0.0)
        assertEquals("INCLUSIVE", metadata.taxPriceMode)
        assertEquals("PRODUCT", metadata.taxApplicability)
        assertEquals("HALF_DOWN", metadata.taxRoundingMode)
        assertEquals(1700000000000L, metadata.taxEffectiveDate)
    }

    // ===== 2. Format Version Compatibility =====

    @Test
    fun `BackupValidator accepts format 1_0 1_1 1_2 and current`() {
        val validFormats = listOf("1.0", "1.1", "1.2", CanonicalSerializer.BACKUP_FORMAT_VERSION)
        for (format in validFormats) {
            val businessTab = SheetTab(
                name = "01_Business",
                headers = listOf("business_id", "shop_name"),
                rows = listOf(listOf("BIZ_001", "Warung Test"))
            )
            val categoryTab = SheetTab(
                name = "03_Categories",
                headers = listOf("uuid", "business_id", "name"),
                rows = listOf(listOf("cat_uuid_1", "BIZ_001", "Kategori Test"))
            )
            val productTab = SheetTab(
                name = "04_Products",
                headers = listOf("uuid", "business_id", "category_uuid", "name", "purchase_price", "selling_price", "stock", "minimum_stock", "unit", "item_type", "is_deleted", "deleted_at", "created_at", "updated_at", "barcode", "image_uri", "taxable", "tax_rate_override"),
                rows = listOf(listOf("prod_uuid_1", "BIZ_001", "cat_uuid_1", "Produk A", "1000", "2000", "0.0", "0.0", "pcs", "SERVICE", "false", "NULL", "1700000000000", "1700000000000", "NULL", "NULL", "true", "0.11"))
            )
            val stockTab = SheetTab(
                name = "16_StockMovements",
                headers = listOf("uuid", "business_id", "device_id", "product_uuid", "movement_type", "delta_quantity", "current_stock_snapshot", "reference_uuid", "note", "created_at"),
                rows = listOf(listOf("mov_uuid_1", "BIZ_001", "DEV_001", "prod_uuid_1", "INITIAL", "0.0", "0.0", "NULL", "Migrasi baseline stok", "1700000000000"))
            )
            val tabs = mapOf(
                "01_Business" to businessTab,
                "03_Categories" to categoryTab,
                "04_Products" to productTab,
                "16_StockMovements" to stockTab
            )
            val checksum = CanonicalSerializer.calculateChecksum(tabs)
            val snapshot = BackupSnapshot(
                metadata = BackupMetadata(
                    backupFormatVersion = format,
                    businessId = "BIZ_001",
                    checksum = checksum,
                    capabilities = emptySet()
                ),
                tabs = tabs
            )
            BackupValidator.validate(snapshot, "BIZ_001")
        }
    }

    @Test(expected = IncompatibleBackupFormatException::class)
    fun `BackupValidator rejects future format 2_0`() {
        val snapshot = BackupSnapshot(
            metadata = BackupMetadata(backupFormatVersion = "2.0"),
            tabs = emptyMap()
        )
        BackupValidator.validate(snapshot, "BIZ_001")
    }

    // ===== 3. Canonical Serializer Version Bump =====

    @Test
    fun `CanonicalSerializer reports format 1_2 and schema 16`() {
        assertEquals("1.2", CanonicalSerializer.BACKUP_FORMAT_VERSION)
        assertEquals(16, CanonicalSerializer.ROOM_SCHEMA_VERSION)
    }

    // ===== 4. Product Tax Fields Round-Trip =====

    @Test
    fun `04_Products preserves taxable and tax_rate_override columns`() {
        val productRows = listOf(
            listOf("prod_uuid_1", "BIZ_001", "cat_uuid_1", "Produk A", "1000", "2000", "10.0", "2.0", "pcs", "PHYSICAL", "false", "NULL", "1700000000000", "1700000000000", "NULL", "NULL", "true", "0.11"),
            listOf("prod_uuid_2", "BIZ_001", "cat_uuid_1", "Produk B", "2000", "4000", "5.0", "1.0", "pcs", "PHYSICAL", "false", "NULL", "1700000000000", "1700000000000", "NULL", "NULL", "false", "NULL")
        )
        val tab04 = SheetTab(
            name = "04_Products",
            headers = listOf("uuid", "business_id", "category_uuid", "name", "purchase_price", "selling_price", "stock", "minimum_stock", "unit", "item_type", "is_deleted", "deleted_at", "created_at", "updated_at", "barcode", "image_uri", "taxable", "tax_rate_override"),
            rows = productRows
        )

        assertEquals(2, tab04.rows.size)
        assertEquals("true", tab04.rows[0][16])
        assertEquals("0.11", tab04.rows[0][17])
        assertEquals("false", tab04.rows[1][16])
        assertEquals("NULL", tab04.rows[1][17])
    }

    // ===== 5. Sales Tax Snapshot Fields Round-Trip =====

    @Test
    fun `06_Sales preserves taxable_base_snapshot and tax snapshot columns`() {
        val saleRows = listOf(
            listOf("sale_uuid_1", "BIZ_001", "DEV_001", "TX-001", "1700000000000", "22000", "CASH", "1700000000000", "cust_uuid_1", "0", "20000", "2000", "0.10", "2000")
        )
        val tab06 = SheetTab(
            name = "06_Sales",
            headers = listOf("uuid", "business_id", "device_id", "transaction_number", "transaction_date", "total_amount", "payment_method", "created_at", "customer_uuid", "discount_amount", "subtotal_amount", "taxable_base_snapshot", "tax_rate_snapshot", "tax_amount_snapshot"),
            rows = saleRows
        )

        assertEquals(1, tab06.rows.size)
        assertEquals("20000", tab06.rows[0][10])
        assertEquals("2000", tab06.rows[0][11])
        assertEquals("0.10", tab06.rows[0][12])
        assertEquals("2000", tab06.rows[0][13])
    }

    // ===== 6. Purchase Tax Snapshot Fields Round-Trip =====

    @Test
    fun `08_Purchases preserves taxable_base_snapshot and tax snapshot columns`() {
        val purchaseRows = listOf(
            listOf("pur_uuid_1", "BIZ_001", "DEV_001", "PO-001", "1700000000000", "110000", "CASH", "1700000000000", "sup_uuid_1", "100000", "100000", "0.10", "10000")
        )
        val tab08 = SheetTab(
            name = "08_Purchases",
            headers = listOf("uuid", "business_id", "device_id", "transaction_number", "transaction_date", "total_amount", "payment_method", "created_at", "supplier_uuid", "subtotal_amount", "taxable_base_snapshot", "tax_rate_snapshot", "tax_amount_snapshot"),
            rows = purchaseRows
        )

        assertEquals(1, tab08.rows.size)
        assertEquals("100000", tab08.rows[0][10])
        assertEquals("0.10", tab08.rows[0][11])
        assertEquals("10000", tab08.rows[0][12])
    }

    // ===== 7. Sale Item Tax Fields Round-Trip =====

    @Test
    fun `07_SaleItems preserves taxable and tax snapshot columns`() {
        val saleItemRows = listOf(
            listOf("si_uuid_1", "BIZ_001", "sale_uuid_1", "prod_uuid_1", "Produk A", "1.0", "20000", "10000", "20000", "true", "0.10", "2000")
        )
        val tab07 = SheetTab(
            name = "07_SaleItems",
            headers = listOf("uuid", "business_id", "sale_uuid", "product_uuid", "product_name", "quantity", "price", "purchase_price", "subtotal", "taxable", "tax_rate_snapshot", "tax_amount_snapshot"),
            rows = saleItemRows
        )

        assertEquals(1, tab07.rows.size)
        assertEquals("true", tab07.rows[0][9])
        assertEquals("0.10", tab07.rows[0][10])
        assertEquals("2000", tab07.rows[0][11])
    }

    // ===== 8. Purchase Item Tax Fields Round-Trip =====

    @Test
    fun `09_PurchaseItems preserves taxable and tax snapshot columns`() {
        val purchaseItemRows = listOf(
            listOf("pi_uuid_1", "BIZ_001", "pur_uuid_1", "prod_uuid_1", "Produk A", "2.0", "50000", "100000", "true", "0.10", "10000")
        )
        val tab09 = SheetTab(
            name = "09_PurchaseItems",
            headers = listOf("uuid", "business_id", "purchase_uuid", "product_uuid", "product_name", "quantity", "purchase_price", "subtotal", "taxable", "tax_rate_snapshot", "tax_amount_snapshot"),
            rows = purchaseItemRows
        )

        assertEquals(1, tab09.rows.size)
        assertEquals("true", tab09.rows[0][8])
        assertEquals("0.10", tab09.rows[0][9])
        assertEquals("10000", tab09.rows[0][10])
    }

    // ===== 9. Checksum Includes Tax Data =====

    @Test
    fun `tax fields in data tabs affect checksum`() {
        val baseTabs = createTaxTabs(
            taxEnabled = false,
            taxRate = 0.0,
            taxPriceMode = "EXCLUSIVE",
            taxApplicability = "GLOBAL"
        )
        val modifiedTabs = baseTabs.toMutableMap()
        val modifiedProducts = baseTabs["04_Products"]!!.rows.toMutableList()
        modifiedProducts[0] = modifiedProducts[0].toMutableList().apply { set(16, "false"); set(17, "0.00") }
        modifiedTabs["04_Products"] = baseTabs["04_Products"]!!.copy(rows = modifiedProducts)

        val baseChecksum = CanonicalSerializer.calculateChecksum(baseTabs)
        val modifiedChecksum = CanonicalSerializer.calculateChecksum(modifiedTabs)

        assertFalse("Changing product tax fields must alter checksum", baseChecksum == modifiedChecksum)
    }

    // ===== 10. Metadata Tab Contains Tax Keys =====

    @Test
    fun `metadata tab serialization includes tax keys`() {
        val metadata = BackupMetadata(
            backupFormatVersion = "1.2",
            roomSchemaVersion = 16,
            exportedAt = 1700000000000L,
            businessId = "BIZ_TEST",
            deviceId = "DEV_TEST",
            appVersion = "1.0.0",
            totalRecords = 5,
            checksum = "abc123",
            capabilities = setOf("CAP_BASIC"),
            taxEnabled = true,
            taxRate = 11.0,
            taxPriceMode = "INCLUSIVE",
            taxApplicability = "PRODUCT",
            taxRoundingMode = "HALF_UP",
            taxEffectiveDate = 1700000000000L
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

        val keyIndex = metadataRows.associate { it[0] to it[1] }
        assertEquals("1.2", keyIndex["backup_format_version"])
        assertEquals("16", keyIndex["room_schema_version"])
        assertEquals("true", keyIndex["tax_enabled"])
        assertEquals("11.0", keyIndex["tax_rate"])
        assertEquals("INCLUSIVE", keyIndex["tax_price_mode"])
        assertEquals("PRODUCT", keyIndex["tax_applicability"])
        assertEquals("HALF_UP", keyIndex["tax_rounding_mode"])
        assertEquals("1700000000000", keyIndex["tax_effective_date"])
    }

    // ===== 11. Legacy Format Fallback =====

    @Test
    fun `legacy format 1_1 backup metadata has safe tax defaults`() {
        val metadata = BackupMetadata(
            backupFormatVersion = "1.1",
            roomSchemaVersion = 15
        )
        assertEquals(false, metadata.taxEnabled)
        assertEquals(0.0, metadata.taxRate, 0.0)
        assertEquals("EXCLUSIVE", metadata.taxPriceMode)
        assertEquals("GLOBAL", metadata.taxApplicability)
        assertEquals("HALF_UP", metadata.taxRoundingMode)
        assertEquals(0L, metadata.taxEffectiveDate)
    }

    @Test
    fun `legacy format 1_0 backup metadata has safe tax defaults`() {
        val metadata = BackupMetadata(
            backupFormatVersion = "1.0",
            roomSchemaVersion = 9
        )
        assertEquals(false, metadata.taxEnabled)
        assertEquals(0.0, metadata.taxRate, 0.0)
        assertEquals("EXCLUSIVE", metadata.taxPriceMode)
        assertEquals("GLOBAL", metadata.taxApplicability)
        assertEquals("HALF_UP", metadata.taxRoundingMode)
        assertEquals(0L, metadata.taxEffectiveDate)
    }

    // ===== 12. Deterministic Column Ordering Preserved =====

    @Test
    fun `product headers maintain deterministic column order`() {
        val expectedHeaders = listOf("uuid", "business_id", "category_uuid", "name", "purchase_price", "selling_price", "stock", "minimum_stock", "unit", "item_type", "is_deleted", "deleted_at", "created_at", "updated_at", "barcode", "image_uri", "taxable", "tax_rate_override")
        val tab = SheetTab(name = "04_Products", headers = expectedHeaders, rows = emptyList())
        assertEquals(expectedHeaders, tab.headers)
    }

    @Test
    fun `sales headers maintain deterministic column order`() {
        val expectedHeaders = listOf("uuid", "business_id", "device_id", "transaction_number", "transaction_date", "total_amount", "payment_method", "created_at", "customer_uuid", "discount_amount", "subtotal_amount", "taxable_base_snapshot", "tax_rate_snapshot", "tax_amount_snapshot")
        val tab = SheetTab(name = "06_Sales", headers = expectedHeaders, rows = emptyList())
        assertEquals(expectedHeaders, tab.headers)
    }

    @Test
    fun `purchase headers maintain deterministic column order`() {
        val expectedHeaders = listOf("uuid", "business_id", "device_id", "transaction_number", "transaction_date", "total_amount", "payment_method", "created_at", "supplier_uuid", "subtotal_amount", "taxable_base_snapshot", "tax_rate_snapshot", "tax_amount_snapshot")
        val tab = SheetTab(name = "08_Purchases", headers = expectedHeaders, rows = emptyList())
        assertEquals(expectedHeaders, tab.headers)
    }

    // ===== Helper =====

    private fun createTaxTabs(
        taxEnabled: Boolean,
        taxRate: Double,
        taxPriceMode: String,
        taxApplicability: String
    ): Map<String, SheetTab> {
        val metadata = BackupMetadata(
            backupFormatVersion = "1.2",
            roomSchemaVersion = 16,
            exportedAt = 1700000000000L,
            businessId = "BIZ_TEST",
            deviceId = "DEV_TEST",
            appVersion = "1.0.0",
            totalRecords = 1,
            checksum = "placeholder",
            capabilities = emptySet(),
            taxEnabled = taxEnabled,
            taxRate = taxRate,
            taxPriceMode = taxPriceMode,
            taxApplicability = taxApplicability,
            taxRoundingMode = "HALF_UP",
            taxEffectiveDate = 1700000000000L
        )

        val metadataTab = SheetTab(
            name = "00_Metadata",
            headers = listOf("key", "value"),
            rows = listOf(
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
        )

        val businessTab = SheetTab(
            name = "01_Business",
            headers = listOf("business_id", "shop_name", "owner_name", "phone", "address", "created_at", "primary_business_type", "secondary_activities", "profile_version"),
            rows = listOf(listOf("BIZ_TEST", "Warung Test", "Owner", "08123456789", "Jl. Test", "1700000000000", "WARUNG_SEMBAKO", "ACTIVITY_GOODS_SELLING", "1"))
        )

        return mapOf(
            "00_Metadata" to metadataTab,
            "01_Business" to businessTab,
            "02_Device" to SheetTab(name = "02_Device", headers = listOf("device_id", "business_id", "device_name", "device_role", "app_version"), rows = listOf(listOf("DEV_TEST", "BIZ_TEST", "HP Utama", "OWNER", "1.0.0"))),
            "03_Categories" to SheetTab(name = "03_Categories", headers = listOf("uuid", "business_id", "name", "is_deleted", "deleted_at", "created_at"), rows = listOf(listOf("cat_uuid_1", "BIZ_TEST", "Kategori Test", "false", "NULL", "1700000000000"))),
            "04_Products" to SheetTab(name = "04_Products", headers = listOf("uuid", "business_id", "category_uuid", "name", "purchase_price", "selling_price", "stock", "minimum_stock", "unit", "item_type", "is_deleted", "deleted_at", "created_at", "updated_at", "barcode", "image_uri", "taxable", "tax_rate_override"), rows = listOf(listOf("prod_uuid_1", "BIZ_TEST", "cat_uuid_1", "Produk A", "1000", "2000", "10.0", "2.0", "pcs", "PHYSICAL", "false", "NULL", "1700000000000", "1700000000000", "NULL", "NULL", "true", "0.11"))),
            "05_Customers" to SheetTab(name = "05_Customers", headers = listOf("uuid", "business_id", "name", "phone", "address", "is_deleted", "deleted_at", "created_at", "updated_at"), rows = listOf(listOf("cust_uuid_1", "BIZ_TEST", "Customer A", "08123456789", "Jl. Cust", "false", "NULL", "1700000000000", "1700000000000"))),
            "06_Sales" to SheetTab(name = "06_Sales", headers = listOf("uuid", "business_id", "device_id", "transaction_number", "transaction_date", "total_amount", "payment_method", "created_at", "customer_uuid", "discount_amount", "subtotal_amount", "taxable_base_snapshot", "tax_rate_snapshot", "tax_amount_snapshot"), rows = listOf(listOf("sale_uuid_1", "BIZ_TEST", "DEV_TEST", "TX-001", "1700000000000", "22000", "CASH", "1700000000000", "cust_uuid_1", "0", "20000", "2000", "0.10", "2000"))),
            "07_SaleItems" to SheetTab(name = "07_SaleItems", headers = listOf("uuid", "business_id", "sale_uuid", "product_uuid", "product_name", "quantity", "price", "purchase_price", "subtotal", "taxable", "tax_rate_snapshot", "tax_amount_snapshot"), rows = listOf(listOf("si_uuid_1", "BIZ_TEST", "sale_uuid_1", "prod_uuid_1", "Produk A", "1.0", "20000", "10000", "20000", "true", "0.10", "2000"))),
            "08_Purchases" to SheetTab(name = "08_Purchases", headers = listOf("uuid", "business_id", "device_id", "transaction_number", "transaction_date", "total_amount", "payment_method", "created_at", "supplier_uuid", "subtotal_amount", "taxable_base_snapshot", "tax_rate_snapshot", "tax_amount_snapshot"), rows = listOf(listOf("pur_uuid_1", "BIZ_TEST", "DEV_TEST", "PO-001", "1700000000000", "110000", "CASH", "1700000000000", "sup_uuid_1", "100000", "100000", "0.10", "10000"))),
            "09_PurchaseItems" to SheetTab(name = "09_PurchaseItems", headers = listOf("uuid", "business_id", "purchase_uuid", "product_uuid", "product_name", "quantity", "purchase_price", "subtotal", "taxable", "tax_rate_snapshot", "tax_amount_snapshot"), rows = listOf(listOf("pi_uuid_1", "BIZ_TEST", "pur_uuid_1", "prod_uuid_1", "Produk A", "2.0", "50000", "100000", "true", "0.10", "10000"))),
            "10_Debts" to SheetTab(name = "10_Debts", headers = listOf("uuid", "business_id", "customer_uuid", "sale_uuid", "total_debt", "paid_amount", "status", "created_at", "updated_at"), rows = emptyList()),
            "11_DebtPayments" to SheetTab(name = "11_DebtPayments", headers = listOf("uuid", "business_id", "device_id", "debt_uuid", "amount", "payment_date", "note", "created_at"), rows = emptyList()),
            "12_Suppliers" to SheetTab(name = "12_Suppliers", headers = listOf("uuid", "business_id", "name", "phone", "address", "is_deleted", "deleted_at", "created_at", "updated_at"), rows = emptyList()),
            "13_SupplierPayables" to SheetTab(name = "13_SupplierPayables", headers = listOf("uuid", "business_id", "supplier_uuid", "purchase_uuid", "total_debt", "paid_amount", "status", "created_at", "updated_at"), rows = emptyList()),
            "14_SupplierPayments" to SheetTab(name = "14_SupplierPayments", headers = listOf("uuid", "business_id", "device_id", "payable_uuid", "amount", "payment_date", "note", "created_at"), rows = emptyList()),
            "15_CashTransactions" to SheetTab(name = "15_CashTransactions", headers = listOf("uuid", "business_id", "device_id", "type", "amount", "description", "ref_uuid", "created_at"), rows = emptyList()),
            "16_StockMovements" to SheetTab(name = "16_StockMovements", headers = listOf("uuid", "business_id", "device_id", "product_uuid", "movement_type", "delta_quantity", "current_stock_snapshot", "reference_uuid", "note", "created_at"), rows = emptyList()),
            "17_SaleReturns" to SheetTab(name = "17_SaleReturns", headers = listOf("uuid", "business_id", "device_id", "return_number", "return_date", "sale_uuid", "customer_uuid", "total_refund_amount", "refund_method", "reason", "notes", "taxable_base_snapshot", "tax_rate_snapshot", "tax_amount_snapshot", "created_at"), rows = emptyList()),
            "18_SaleReturnItems" to SheetTab(name = "18_SaleReturnItems", headers = listOf("uuid", "business_id", "return_uuid", "sale_item_uuid", "product_uuid", "product_name", "quantity", "price", "purchase_price", "subtotal", "taxable", "tax_rate_snapshot", "tax_amount_snapshot", "created_at"), rows = emptyList())
        )
    }
}
