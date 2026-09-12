package id.skmnetwork.bukuwarung.domain.receipt

enum class ReceiptPaperWidth(val columns: Int) {
    WIDTH_58MM(32),
    WIDTH_80MM(48)
}

data class ReceiptItem(
    val name: String,
    val quantity: Double,
    val unit: String = "pcs",
    val price: Long,
    val subtotal: Long
)

data class ShopProfile(
    val shopName: String = "Warung Saya",
    val ownerName: String = "",
    val phone: String = "",
    val address: String = "",
    val footerMessage: String = "Terima Kasih Atas Kunjungan Anda!",
    val showShopName: Boolean = true,
    val showAddress: Boolean = true,
    val showPhone: Boolean = true,
    val showPaymentMethod: Boolean = true,
    val showChange: Boolean = true
)

data class ReceiptPaymentInfo(
    val method: String, // CASH, QRIS, CREDIT
    val totalAmount: Long,
    val payAmount: Long? = null,
    val changeAmount: Long? = null,
    val customerName: String? = null,
    val remainingDebt: Long? = null
)

data class ReceiptData(
    val receiptNumber: String,
    val transactionUuid: String,
    val dateTimeMillis: Long,
    val shopProfile: ShopProfile,
    val cashierName: String,
    val items: List<ReceiptItem>,
    val paymentInfo: ReceiptPaymentInfo
)
