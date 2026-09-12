package id.skmnetwork.bukuwarung.ui.navigation

enum class AppScreen(val label: String) {
    HOME("Beranda"),
    POS("Jualan"),
    PRODUCTS("Produk & Stok"),
    PURCHASE("Pembelian"),
    CASH("Uang Kas"),
    REPORTS("Laporan"),
    CUSTOMERS("Pelanggan & Piutang"),
    SUPPLIERS("Supplier & Hutang"),
    SETTINGS("Pengaturan"),
    ADD_PRODUCT("Tambah Produk"),
}

data class CheckoutSuccessData(
    val items: List<Pair<String, Double>>,
    val totalAmount: Long,
    val paymentMethodLabel: String,
    val customerName: String? = null,
    val cashReceivedAmount: Long? = null,
    val changeAmount: Long? = null,
    val saleId: Long? = null,
    val receiptData: id.skmnetwork.bukuwarung.domain.receipt.ReceiptData? = null
)
