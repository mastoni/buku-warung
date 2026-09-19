package id.skmnetwork.bukuwarung.data.remote

data class SubmitDigitalRequest(
    val idempotencyKey: String,
    val productCode: String,
    val destinationNumber: String,
    val sellingPrice: Long,
    val actualPurchasePrice: Long
)

data class SubmitDigitalResponse(
    val idempotencyKey: String,
    val status: String,
    val providerReferenceId: String,
    val snToken: String?,
    val failureReason: String?
)

data class CheckDigitalStatusRequest(
    val idempotencyKey: String,
    val providerReferenceId: String? = null
)

data class CheckDigitalStatusResponse(
    val idempotencyKey: String,
    val status: String,
    val providerReferenceId: String?,
    val snToken: String?,
    val failureReason: String?
)

data class DigitalCallbackPayload(
    val idempotencyKey: String,
    val status: String,
    val providerReferenceId: String?,
    val snToken: String?,
    val failureReason: String?,
    val timestamp: Long
)

data class DigitalCallbackResponse(
    val processed: Boolean,
    val idempotencyKey: String
)

interface BackendApi {
    suspend fun submitDigitalTransaction(request: SubmitDigitalRequest): SubmitDigitalResponse
    suspend fun checkDigitalTransactionStatus(request: CheckDigitalStatusRequest): CheckDigitalStatusResponse
    suspend fun processDigitalCallback(payload: DigitalCallbackPayload): DigitalCallbackResponse
}