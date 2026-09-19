package id.skmnetwork.bukuwarung.domain.provider

interface DigitalProviderAdapter {
    suspend fun getCatalog(category: String?): List<ProviderProduct>
    suspend fun syncProductPricing(productCode: String): ProviderPrice
    suspend fun inquireDestination(
        productCode: String,
        destinationNumber: String
    ): InquiryResult
    suspend fun createTransaction(request: CreateTransactionRequest): CreateTransactionResult
    suspend fun checkStatus(request: CheckStatusRequest): CheckStatusResult
    suspend fun getBalance(): ProviderBalance
}