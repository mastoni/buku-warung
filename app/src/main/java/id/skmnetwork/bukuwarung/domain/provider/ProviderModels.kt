package id.skmnetwork.bukuwarung.domain.provider

data class ProviderProduct(
    val productCode: String,
    val name: String,
    val category: String?,
    val sellingPrice: Long,
    val actualPurchasePrice: Long
)

data class ProviderPrice(
    val productCode: String,
    val sellingPrice: Long,
    val actualPurchasePrice: Long,
    val validUntil: Long?
)

data class InquiryResult(
    val productCode: String,
    val destinationNumber: String,
    val customerName: String?,
    val billAmount: Long,
    val validityPeriod: Long?,
    val isActive: Boolean
)

data class CreateTransactionRequest(
    val idempotencyKey: String,
    val productCode: String,
    val destinationNumber: String,
    val sellingPrice: Long,
    val actualPurchasePrice: Long
)

data class CreateTransactionResult(
    val idempotencyKey: String,
    val status: String,
    val providerReferenceId: String,
    val snToken: String?,
    val failureReason: String?
)

data class CheckStatusRequest(
    val idempotencyKey: String,
    val providerReferenceId: String? = null
)

data class CheckStatusResult(
    val idempotencyKey: String,
    val status: String,
    val providerReferenceId: String?,
    val snToken: String?,
    val failureReason: String?
)

data class ProviderBalance(
    val balance: Long,
    val currency: String = "IDR"
)