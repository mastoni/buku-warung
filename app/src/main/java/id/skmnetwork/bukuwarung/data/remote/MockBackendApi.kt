package id.skmnetwork.bukuwarung.data.remote

class MockBackendApi : BackendApi {
    private val transactionStates = mutableMapOf<String, String>()
    private val providerReferences = mutableMapOf<String, String>()
    private val snTokens = mutableMapOf<String, String?>()
    private val failureReasons = mutableMapOf<String, String?>()

    override suspend fun submitDigitalTransaction(request: SubmitDigitalRequest): SubmitDigitalResponse {
        val refId = "REF-${request.idempotencyKey.takeLast(8)}"
        transactionStates[request.idempotencyKey] = "PENDING"
        providerReferences[request.idempotencyKey] = refId
        snTokens[request.idempotencyKey] = null
        failureReasons[request.idempotencyKey] = null
        return SubmitDigitalResponse(
            idempotencyKey = request.idempotencyKey,
            status = "PENDING",
            providerReferenceId = refId,
            snToken = null,
            failureReason = null
        )
    }

    override suspend fun checkDigitalTransactionStatus(request: CheckDigitalStatusRequest): CheckDigitalStatusResponse {
        val status = transactionStates[request.idempotencyKey] ?: "UNKNOWN"
        return CheckDigitalStatusResponse(
            idempotencyKey = request.idempotencyKey,
            status = status,
            providerReferenceId = providerReferences[request.idempotencyKey],
            snToken = snTokens[request.idempotencyKey],
            failureReason = failureReasons[request.idempotencyKey]
        )
    }

    override suspend fun processDigitalCallback(payload: DigitalCallbackPayload): DigitalCallbackResponse {
        transactionStates[payload.idempotencyKey] = payload.status
        if (payload.providerReferenceId != null) {
            providerReferences[payload.idempotencyKey] = payload.providerReferenceId
        }
        snTokens[payload.idempotencyKey] = payload.snToken
        failureReasons[payload.idempotencyKey] = payload.failureReason
        return DigitalCallbackResponse(processed = true, idempotencyKey = payload.idempotencyKey)
    }

    fun setStatus(idempotencyKey: String, status: String) {
        transactionStates[idempotencyKey] = status
    }

    fun setProviderReference(idempotencyKey: String, referenceId: String) {
        providerReferences[idempotencyKey] = referenceId
    }

    fun clear() {
        transactionStates.clear()
        providerReferences.clear()
        snTokens.clear()
        failureReasons.clear()
    }
}