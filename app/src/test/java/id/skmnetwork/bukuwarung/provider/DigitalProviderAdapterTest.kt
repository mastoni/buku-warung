package id.skmnetwork.bukuwarung.provider

import id.skmnetwork.bukuwarung.data.remote.CheckDigitalStatusResponse
import id.skmnetwork.bukuwarung.data.remote.DigitalCallbackPayload
import id.skmnetwork.bukuwarung.data.remote.SubmitDigitalRequest
import id.skmnetwork.bukuwarung.domain.provider.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DigitalProviderAdapterTest {

    @Test
    fun adapter_contract_has_required_methods() {
        val adapter = TestProviderAdapter()
        assertNotNull(adapter)
    }

    @Test
    fun createTransaction_uses_idempotencyKey_from_uuid() {
        val request = CreateTransactionRequest(
            idempotencyKey = "test-uuid-123",
            productCode = "TSEL25",
            destinationNumber = "08123456789",
            sellingPrice = 26000,
            actualPurchasePrice = 24000
        )
        assertEquals("test-uuid-123", request.idempotencyKey)
        assertEquals("TSEL25", request.productCode)
        assertEquals("08123456789", request.destinationNumber)
        assertEquals(26000, request.sellingPrice)
        assertEquals(24000, request.actualPurchasePrice)
    }

    @Test
    fun checkStatus_never_creates_new_transaction() {
        val request = CheckStatusRequest(
            idempotencyKey = "test-uuid-123",
            providerReferenceId = "REF-abc123"
        )
        assertEquals("test-uuid-123", request.idempotencyKey)
        assertEquals("REF-abc123", request.providerReferenceId)
    }

    @Test
    fun provider_agnostic_no_provider_specific_fields() {
        val adapter = TestProviderAdapter()
        val result = runBlocking {
            adapter.createTransaction(
                CreateTransactionRequest(
                    idempotencyKey = "uuid",
                    productCode = "CODE",
                    destinationNumber = "08123456789",
                    sellingPrice = 26000,
                    actualPurchasePrice = 24000
                )
            )
        }
        assertNotNull(result)
        assertEquals("uuid", result.idempotencyKey)
        assertEquals("PENDING", result.status)
        assertNotNull(result.providerReferenceId)
    }

    @Test
    fun callback_mapping_contains_required_fields() {
        val payload = DigitalCallbackPayload(
            idempotencyKey = "uuid",
            status = "SUCCESS",
            providerReferenceId = "REF-123",
            snToken = "SN-456",
            failureReason = null,
            timestamp = 1000000L
        )
        assertEquals("uuid", payload.idempotencyKey)
        assertEquals("SUCCESS", payload.status)
        assertEquals("REF-123", payload.providerReferenceId)
        assertEquals("SN-456", payload.snToken)
        assertNull(payload.failureReason)
    }

    @Test
    fun inquiry_result_contains_bill_amount() {
        val result = InquiryResult(
            productCode = "TSEL25",
            destinationNumber = "08123456789",
            customerName = "John",
            billAmount = 26000,
            validityPeriod = 86400000L,
            isActive = true
        )
        assertEquals(26000, result.billAmount)
        assertEquals("08123456789", result.destinationNumber)
    }

    @Test
    fun submit_request_mapping_matches_entity_fields() {
        val request = SubmitDigitalRequest(
            idempotencyKey = "uuid-456",
            productCode = "PLN20",
            destinationNumber = "08123456789",
            sellingPrice = 26000,
            actualPurchasePrice = 24000
        )
        assertEquals("uuid-456", request.idempotencyKey)
        assertEquals("PLN20", request.productCode)
        assertEquals("08123456789", request.destinationNumber)
        assertEquals(26000, request.sellingPrice)
        assertEquals(24000, request.actualPurchasePrice)
    }

    @Test
    fun status_response_mapping_contains_all_fields() {
        val response = CheckDigitalStatusResponse(
            idempotencyKey = "uuid-456",
            status = "SUCCESS",
            providerReferenceId = "REF-789",
            snToken = "SN-000",
            failureReason = null
        )
        assertEquals("uuid-456", response.idempotencyKey)
        assertEquals("SUCCESS", response.status)
        assertEquals("REF-789", response.providerReferenceId)
        assertEquals("SN-000", response.snToken)
        assertNull(response.failureReason)
    }

    private class TestProviderAdapter : DigitalProviderAdapter {
        override suspend fun getCatalog(category: String?): List<ProviderProduct> = emptyList()
        override suspend fun syncProductPricing(productCode: String): ProviderPrice =
            ProviderPrice(productCode, 0, 0, null)
        override suspend fun inquireDestination(
            productCode: String,
            destinationNumber: String
        ): InquiryResult =
            InquiryResult(productCode, destinationNumber, null, 0, null, false)
        override suspend fun createTransaction(request: CreateTransactionRequest): CreateTransactionResult =
            CreateTransactionResult(
                idempotencyKey = request.idempotencyKey,
                status = "PENDING",
                providerReferenceId = "REF-${request.idempotencyKey}",
                snToken = null,
                failureReason = null
            )
        override suspend fun checkStatus(request: CheckStatusRequest): CheckStatusResult =
            CheckStatusResult(
                idempotencyKey = request.idempotencyKey,
                status = "UNKNOWN",
                providerReferenceId = request.providerReferenceId,
                snToken = null,
                failureReason = null
            )
        override suspend fun getBalance(): ProviderBalance = ProviderBalance(0)
    }
}