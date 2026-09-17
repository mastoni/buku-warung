package id.skmnetwork.bukuwarung.domain.business

/**
 * Gate G3 — Business Terminology Model
 * Localized semantic labels adapting to specific business types.
 */
data class BusinessTerminology(
    val productLabel: String = "Produk",
    val serviceLabel: String = "Layanan",
    val customerLabel: String = "Pelanggan",
    val supplierLabel: String = "Supplier",
    val transactionLabel: String = "Penjualan",
    val purchaseLabel: String = "Pembelian",
    val stockLabel: String = "Stok",
    val debtLabel: String = "Hutang"
)
