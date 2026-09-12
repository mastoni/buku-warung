package id.skmnetwork.bukuwarung.backup

import java.util.Locale

/**
 * Foundation Step 5 — Backup Validator
 * Executes strict pre-validation (Zero Room mutation on failure).
 */
object BackupValidator {

    fun validate(snapshot: BackupSnapshot) {
        val metadata = snapshot.metadata

        // 1. Exact Backup Format Version check
        if (metadata.backupFormatVersion != CanonicalSerializer.BACKUP_FORMAT_VERSION) {
            throw IncompatibleBackupFormatException(
                "Unsupported backup format version: expected ${CanonicalSerializer.BACKUP_FORMAT_VERSION}, got '${metadata.backupFormatVersion}'"
            )
        }

        // 2. Exact Room Schema Version check
        if (metadata.roomSchemaVersion != CanonicalSerializer.ROOM_SCHEMA_VERSION) {
            throw IncompatibleSchemaVersionException(
                "Unsupported Room schema version: expected ${CanonicalSerializer.ROOM_SCHEMA_VERSION}, got '${metadata.roomSchemaVersion}'"
            )
        }

        // 3. Cryptographic SHA-256 Checksum Validation
        if (!CanonicalSerializer.verifyChecksum(metadata.checksum, snapshot.tabs)) {
            val calculated = CanonicalSerializer.calculateChecksum(snapshot.tabs)
            throw ChecksumMismatchException(
                "Checksum mismatch: expected '${metadata.checksum}', calculated '$calculated'"
            )
        }

        // 4. UUID Uniqueness Validation
        val categoryUuids = mutableSetOf<String>()
        val productUuids = mutableSetOf<String>()
        val customerUuids = mutableSetOf<String>()
        val saleUuids = mutableSetOf<String>()
        val purchaseUuids = mutableSetOf<String>()
        val debtUuids = mutableSetOf<String>()
        val supplierUuids = mutableSetOf<String>()
        val payableUuids = mutableSetOf<String>()
        val movementUuids = mutableSetOf<String>()
        val cashUuids = mutableSetOf<String>()
        val returnUuids = mutableSetOf<String>()
        val returnItemUuids = mutableSetOf<String>()

        fun checkUniqueUuid(tabName: String, uuid: String, set: MutableSet<String>) {
            if (uuid.isBlank() || uuid == "NULL") {
                throw CorruptedBackupException("Invalid blank or NULL primary UUID in tab '$tabName'")
            }
            if (!set.add(uuid)) {
                throw CorruptedBackupException("Duplicate primary UUID detected in tab '$tabName': '$uuid'")
            }
        }

        snapshot.getTab("03_Categories")?.rows?.forEach { row ->
            val uuid = row.getOrElse(0) { "" }
            checkUniqueUuid("03_Categories", uuid, categoryUuids)
        }

        // Product stock map for ledger check: productUuid -> (stock, itemType, name)
        val productStockMap = mutableMapOf<String, Triple<Double, String, String>>()

        snapshot.getTab("04_Products")?.rows?.forEach { row ->
            val uuid = row.getOrElse(0) { "" }
            checkUniqueUuid("04_Products", uuid, productUuids)
            val name = row.getOrElse(3) { "" }
            val stock = row.getOrElse(6) { "0" }.toDoubleOrNull() ?: 0.0
            val itemType = row.getOrElse(9) { "PHYSICAL" }
            productStockMap[uuid] = Triple(stock, itemType, name)
        }

        val saleItemUuids = mutableSetOf<String>()
        snapshot.getTab("07_SaleItems")?.rows?.forEach { row ->
            val uuid = row.getOrElse(0) { "" }
            checkUniqueUuid("07_SaleItems", uuid, saleItemUuids)
        }

        snapshot.getTab("05_Customers")?.rows?.forEach { row ->
            val uuid = row.getOrElse(0) { "" }
            checkUniqueUuid("05_Customers", uuid, customerUuids)
        }

        snapshot.getTab("06_Sales")?.rows?.forEach { row ->
            val uuid = row.getOrElse(0) { "" }
            checkUniqueUuid("06_Sales", uuid, saleUuids)
        }

        snapshot.getTab("08_Purchases")?.rows?.forEach { row ->
            val uuid = row.getOrElse(0) { "" }
            checkUniqueUuid("08_Purchases", uuid, purchaseUuids)
        }

        snapshot.getTab("10_Debts")?.rows?.forEach { row ->
            val uuid = row.getOrElse(0) { "" }
            checkUniqueUuid("10_Debts", uuid, debtUuids)
        }

        snapshot.getTab("12_Suppliers")?.rows?.forEach { row ->
            val uuid = row.getOrElse(0) { "" }
            checkUniqueUuid("12_Suppliers", uuid, supplierUuids)
        }

        snapshot.getTab("13_SupplierPayables")?.rows?.forEach { row ->
            val uuid = row.getOrElse(0) { "" }
            checkUniqueUuid("13_SupplierPayables", uuid, payableUuids)
        }

        snapshot.getTab("15_CashTransactions")?.rows?.forEach { row ->
            val uuid = row.getOrElse(0) { "" }
            checkUniqueUuid("15_CashTransactions", uuid, cashUuids)
        }

        snapshot.getTab("16_StockMovements")?.rows?.forEach { row ->
            val uuid = row.getOrElse(0) { "" }
            checkUniqueUuid("16_StockMovements", uuid, movementUuids)
        }

        snapshot.getTab("17_SaleReturns")?.rows?.forEach { row ->
            val uuid = row.getOrElse(0) { "" }
            checkUniqueUuid("17_SaleReturns", uuid, returnUuids)
        }

        snapshot.getTab("18_SaleReturnItems")?.rows?.forEach { row ->
            val uuid = row.getOrElse(0) { "" }
            checkUniqueUuid("18_SaleReturnItems", uuid, returnItemUuids)
        }

        // 5. Relational Graph Validation
        // 5a. Products -> Category
        snapshot.getTab("04_Products")?.rows?.forEach { row ->
            val catUuid = row.getOrElse(2) { "" }
            if (catUuid.isNotBlank() && catUuid != "NULL" && !categoryUuids.contains(catUuid)) {
                throw CorruptedBackupException("Product '${row.getOrElse(0) { "" }}' references non-existent category_uuid '$catUuid'")
            }
        }

        // 5b. SaleItems -> Sale & Product
        snapshot.getTab("07_SaleItems")?.rows?.forEach { row ->
            val saleUuid = row.getOrElse(2) { "" }
            val productUuid = row.getOrElse(3) { "" }
            if (saleUuid.isBlank() || saleUuid == "NULL" || !saleUuids.contains(saleUuid)) {
                throw CorruptedBackupException("SaleItem '${row.getOrElse(0) { "" }}' references non-existent sale_uuid '$saleUuid'")
            }
            if (productUuid.isBlank() || productUuid == "NULL" || !productUuids.contains(productUuid)) {
                throw CorruptedBackupException("SaleItem '${row.getOrElse(0) { "" }}' references non-existent product_uuid '$productUuid'")
            }
        }

        // 5b-2. SaleReturns -> Sale & Customer
        snapshot.getTab("17_SaleReturns")?.rows?.forEach { row ->
            val saleUuid = row.getOrElse(5) { "" }
            val custUuid = row.getOrElse(6) { "" }
            if (saleUuid.isBlank() || saleUuid == "NULL" || !saleUuids.contains(saleUuid)) {
                throw CorruptedBackupException("SaleReturn '${row.getOrElse(0) { "" }}' references non-existent sale_uuid '$saleUuid'")
            }
            if (custUuid.isNotBlank() && custUuid != "NULL" && !customerUuids.contains(custUuid)) {
                throw CorruptedBackupException("SaleReturn '${row.getOrElse(0) { "" }}' references non-existent customer_uuid '$custUuid'")
            }
        }

        // 5b-3. SaleReturnItems -> Return & SaleItem & Product
        snapshot.getTab("18_SaleReturnItems")?.rows?.forEach { row ->
            val returnUuid = row.getOrElse(2) { "" }
            val saleItemUuid = row.getOrElse(3) { "" }
            val productUuid = row.getOrElse(4) { "" }
            if (returnUuid.isBlank() || returnUuid == "NULL" || !returnUuids.contains(returnUuid)) {
                throw CorruptedBackupException("SaleReturnItem '${row.getOrElse(0) { "" }}' references non-existent return_uuid '$returnUuid'")
            }
            if (saleItemUuid.isBlank() || saleItemUuid == "NULL" || !saleItemUuids.contains(saleItemUuid)) {
                throw CorruptedBackupException("SaleReturnItem '${row.getOrElse(0) { "" }}' references non-existent sale_item_uuid '$saleItemUuid'")
            }
            if (productUuid.isBlank() || productUuid == "NULL" || !productUuids.contains(productUuid)) {
                throw CorruptedBackupException("SaleReturnItem '${row.getOrElse(0) { "" }}' references non-existent product_uuid '$productUuid'")
            }
        }

        // 5c. PurchaseItems -> Purchase & Product
        snapshot.getTab("09_PurchaseItems")?.rows?.forEach { row ->
            val purchaseUuid = row.getOrElse(2) { "" }
            val productUuid = row.getOrElse(3) { "" }
            if (purchaseUuid.isBlank() || purchaseUuid == "NULL" || !purchaseUuids.contains(purchaseUuid)) {
                throw CorruptedBackupException("PurchaseItem '${row.getOrElse(0) { "" }}' references non-existent purchase_uuid '$purchaseUuid'")
            }
            if (productUuid.isBlank() || productUuid == "NULL" || !productUuids.contains(productUuid)) {
                throw CorruptedBackupException("PurchaseItem '${row.getOrElse(0) { "" }}' references non-existent product_uuid '$productUuid'")
            }
        }

        // 5d. Debts -> Customer & Sale
        snapshot.getTab("10_Debts")?.rows?.forEach { row ->
            val custUuid = row.getOrElse(2) { "" }
            val saleUuid = row.getOrElse(3) { "" }
            if (custUuid.isBlank() || custUuid == "NULL" || !customerUuids.contains(custUuid)) {
                throw CorruptedBackupException("Debt '${row.getOrElse(0) { "" }}' references non-existent customer_uuid '$custUuid'")
            }
            if (saleUuid.isNotBlank() && saleUuid != "NULL" && !saleUuids.contains(saleUuid)) {
                throw CorruptedBackupException("Debt '${row.getOrElse(0) { "" }}' references non-existent sale_uuid '$saleUuid'")
            }
        }

        // 5e. DebtPayments -> Debt
        snapshot.getTab("11_DebtPayments")?.rows?.forEach { row ->
            val debtUuid = row.getOrElse(3) { "" }
            if (debtUuid.isBlank() || debtUuid == "NULL" || !debtUuids.contains(debtUuid)) {
                throw CorruptedBackupException("DebtPayment '${row.getOrElse(0) { "" }}' references non-existent debt_uuid '$debtUuid'")
            }
        }

        // 5f. SupplierPayables -> Supplier & Purchase
        snapshot.getTab("13_SupplierPayables")?.rows?.forEach { row ->
            val supUuid = row.getOrElse(2) { "" }
            val purUuid = row.getOrElse(3) { "" }
            if (supUuid.isBlank() || supUuid == "NULL" || !supplierUuids.contains(supUuid)) {
                throw CorruptedBackupException("SupplierPayable '${row.getOrElse(0) { "" }}' references non-existent supplier_uuid '$supUuid'")
            }
            if (purUuid.isNotBlank() && purUuid != "NULL" && !purchaseUuids.contains(purUuid)) {
                throw CorruptedBackupException("SupplierPayable '${row.getOrElse(0) { "" }}' references non-existent purchase_uuid '$purUuid'")
            }
        }

        // 5g. SupplierPayments -> SupplierPayable
        snapshot.getTab("14_SupplierPayments")?.rows?.forEach { row ->
            val payableUuid = row.getOrElse(3) { "" }
            if (payableUuid.isBlank() || payableUuid == "NULL" || !payableUuids.contains(payableUuid)) {
                throw CorruptedBackupException("SupplierPayment '${row.getOrElse(0) { "" }}' references non-existent payable_uuid '$payableUuid'")
            }
        }

        // 5h. StockMovements -> Product
        val productDeltas = mutableMapOf<String, Double>()
        snapshot.getTab("16_StockMovements")?.rows?.forEach { row ->
            val prodUuid = row.getOrElse(3) { "" }
            val delta = row.getOrElse(5) { "0" }.toDoubleOrNull() ?: 0.0
            if (prodUuid.isBlank() || prodUuid == "NULL" || !productUuids.contains(prodUuid)) {
                throw CorruptedBackupException("StockMovement '${row.getOrElse(0) { "" }}' references non-existent product_uuid '$prodUuid'")
            }
            productDeltas[prodUuid] = (productDeltas[prodUuid] ?: 0.0) + delta
        }

        // 6. Stock Ledger Invariant Check: SUM(deltaQuantity) == Product.stock
        for ((prodUuid, info) in productStockMap) {
            val (expectedStock, itemType, name) = info
            if (itemType != "SERVICE") {
                val totalDelta = productDeltas[prodUuid] ?: 0.0
                if (Math.abs(totalDelta - expectedStock) > 0.0001) {
                    throw CorruptedBackupException(
                        "Stock ledger invariant violation for product '$prodUuid' ('$name'): Product.stock is $expectedStock, but SUM(delta_quantity) is $totalDelta"
                    )
                }
            }
        }
    }
}
