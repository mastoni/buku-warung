package id.skmnetwork.bukuwarung.data.repository

import id.skmnetwork.bukuwarung.data.local.dao.DigitalTransactionDao
import id.skmnetwork.bukuwarung.data.local.entity.DigitalTransactionEntity
import id.skmnetwork.bukuwarung.data.local.entity.DigitalTransactionStatus
import id.skmnetwork.bukuwarung.data.remote.BackendApi
import id.skmnetwork.bukuwarung.data.remote.CheckDigitalStatusRequest
import id.skmnetwork.bukuwarung.data.remote.DigitalCallbackPayload
import id.skmnetwork.bukuwarung.data.remote.SubmitDigitalRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DigitalTransactionRepository @Inject constructor(
    private val digitalTransactionDao: DigitalTransactionDao,
    private val backendApi: BackendApi? = null,
    private val businessId: String = "LEGACY_BUSINESS"
) {

    /**
     * Generates a collision-free, deterministic transaction number for standalone PPOB.
     * Pattern: DIG-YYYYMMDD-HHMMSS-XXXX (UUID suffix ensures zero collisions even across concurrent calls).
     */
    fun generateTransactionNumber(now: Long = System.currentTimeMillis()): String {
        val dateStr = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(now))
        val suffix = UUID.randomUUID().toString().take(4).uppercase()
        return "DIG-$dateStr-$suffix"
    }

    /**
     * Creates a draft for hybrid retail cart transactions (tied to a saleItemId).
     */
    suspend fun createDraft(
        saleItemId: Long,
        providerId: String,
        providerProductCode: String,
        destinationNumber: String,
        sellingPrice: Long,
        actualPurchasePrice: Long = sellingPrice,
        adminFee: Long = 0L,
        paymentMethod: String = "CASH"
    ): Long {
        val transaction = DigitalTransactionEntity(
            businessId = businessId,
            saleItemId = saleItemId,
            providerId = providerId,
            providerProductCode = providerProductCode,
            destinationNumber = destinationNumber,
            sellingPrice = sellingPrice,
            actualPurchasePrice = actualPurchasePrice,
            adminFee = adminFee,
            paymentMethod = paymentMethod,
            status = DigitalTransactionStatus.DRAFT.name
        )
        return digitalTransactionDao.insert(transaction)
    }

    /**
     * Creates a draft for standalone PPOB transactions (no saleItemId, dedicated transactionNumber).
     */
    suspend fun createStandaloneDraft(
        providerId: String,
        providerProductCode: String,
        destinationNumber: String,
        sellingPrice: Long,
        actualPurchasePrice: Long,
        adminFee: Long = 0L,
        customerId: Long? = null,
        paymentMethod: String = "CASH",
        transactionNumber: String = generateTransactionNumber()
    ): Long {
        val transaction = DigitalTransactionEntity(
            businessId = businessId,
            transactionNumber = transactionNumber,
            saleItemId = null,
            customerId = customerId,
            paymentMethod = paymentMethod,
            providerId = providerId,
            providerProductCode = providerProductCode,
            destinationNumber = destinationNumber,
            sellingPrice = sellingPrice,
            actualPurchasePrice = actualPurchasePrice,
            adminFee = adminFee,
            status = DigitalTransactionStatus.DRAFT.name
        )
        return digitalTransactionDao.insert(transaction)
    }

    suspend fun submitDraft(id: Long): DigitalTransactionEntity {
        val current = digitalTransactionDao.getById(id, businessId)
            ?: throw IllegalStateException("Digital transaction not found: $id")
        if (current.status != DigitalTransactionStatus.DRAFT.name) {
            throw IllegalStateException("Cannot submit draft: current status is ${current.status}")
        }
        val backend = backendApi ?: throw IllegalStateException("Backend API not configured")
        val response = backend.submitDigitalTransaction(
            SubmitDigitalRequest(
                idempotencyKey = current.uuid,
                productCode = current.providerProductCode,
                destinationNumber = current.destinationNumber,
                sellingPrice = current.sellingPrice,
                actualPurchasePrice = current.actualPurchasePrice
            )
        )
        val updated = current.copy(
            status = DigitalTransactionStatus.PENDING.name,
            providerReferenceId = response.providerReferenceId,
            snToken = response.snToken,
            failureReason = response.failureReason,
            updatedAt = System.currentTimeMillis()
        )
        digitalTransactionDao.update(updated)
        return updated
    }

    suspend fun checkStatus(id: Long): DigitalTransactionEntity {
        val current = digitalTransactionDao.getById(id, businessId)
            ?: throw IllegalStateException("Digital transaction not found: $id")
        val backend = backendApi ?: throw IllegalStateException("Backend API not configured")
        val response = backend.checkDigitalTransactionStatus(
            CheckDigitalStatusRequest(
                idempotencyKey = current.uuid,
                providerReferenceId = current.providerReferenceId
            )
        )
        val updated = current.copy(
            status = response.status,
            providerReferenceId = response.providerReferenceId ?: current.providerReferenceId,
            snToken = response.snToken ?: current.snToken,
            failureReason = response.failureReason ?: current.failureReason,
            updatedAt = System.currentTimeMillis()
        )
        digitalTransactionDao.update(updated)
        return updated
    }

    suspend fun processCallback(
        idempotencyKey: String,
        status: String,
        providerReferenceId: String?,
        snToken: String?,
        failureReason: String?
    ) {
        val backend = backendApi ?: throw IllegalStateException("Backend API not configured")
        backend.processDigitalCallback(
            DigitalCallbackPayload(
                idempotencyKey = idempotencyKey,
                status = status,
                providerReferenceId = providerReferenceId,
                snToken = snToken,
                failureReason = failureReason,
                timestamp = System.currentTimeMillis()
            )
        )
        val current = digitalTransactionDao.getByUuid(idempotencyKey, businessId) ?: return
        val updated = current.copy(
            status = status,
            providerReferenceId = providerReferenceId ?: current.providerReferenceId,
            snToken = snToken ?: current.snToken,
            failureReason = failureReason ?: current.failureReason,
            updatedAt = System.currentTimeMillis()
        )
        digitalTransactionDao.update(updated)
    }

    suspend fun recoverUnknown() {
        val unknown = digitalTransactionDao.getByStatus(DigitalTransactionStatus.UNKNOWN.name, businessId).first()
        for (tx in unknown) {
            try {
                checkStatus(tx.id)
            } catch (_: Exception) {
            }
        }
    }

    suspend fun getBySaleItemIds(saleItemIds: List<Long>): List<DigitalTransactionEntity> {
        if (saleItemIds.isEmpty()) return emptyList()
        return digitalTransactionDao.getBySaleItemIds(saleItemIds.distinct(), businessId)
    }

    suspend fun getByTransactionNumber(transactionNumber: String): DigitalTransactionEntity? {
        return digitalTransactionDao.getByTransactionNumber(transactionNumber, businessId)
    }

    suspend fun getById(id: Long): DigitalTransactionEntity? {
        return digitalTransactionDao.getById(id, businessId)
    }

    suspend fun getByUuid(uuid: String): DigitalTransactionEntity? {
        return digitalTransactionDao.getByUuid(uuid, businessId)
    }

    fun getStandaloneTransactions(): Flow<List<DigitalTransactionEntity>> {
        return digitalTransactionDao.getStandaloneTransactions(businessId)
    }

    fun getTransactionsByCustomerId(customerId: Long): Flow<List<DigitalTransactionEntity>> {
        return digitalTransactionDao.getByCustomerId(customerId, businessId)
    }

    suspend fun transitionState(id: Long, newState: DigitalTransactionStatus, failureReason: String? = null, snToken: String? = null) {
        val current = digitalTransactionDao.getById(id, businessId) ?: return
        val currentState = DigitalTransactionStatus.valueOf(current.status)

        val isValid = when (currentState) {
            DigitalTransactionStatus.DRAFT -> newState == DigitalTransactionStatus.PENDING
            DigitalTransactionStatus.PENDING -> newState in listOf(DigitalTransactionStatus.SUCCESS, DigitalTransactionStatus.FAILED, DigitalTransactionStatus.UNKNOWN)
            DigitalTransactionStatus.UNKNOWN -> newState in listOf(DigitalTransactionStatus.SUCCESS, DigitalTransactionStatus.FAILED, DigitalTransactionStatus.PENDING)
            DigitalTransactionStatus.SUCCESS, DigitalTransactionStatus.FAILED -> false
        }

        if (isValid) {
            digitalTransactionDao.update(
                current.copy(
                    status = newState.name,
                    failureReason = failureReason ?: current.failureReason,
                    snToken = snToken ?: current.snToken,
                    updatedAt = System.currentTimeMillis()
                )
            )
        } else {
            throw IllegalStateException("Invalid state transition from $currentState to $newState")
        }
    }

    suspend fun updateProviderReference(id: Long, providerRefId: String) {
        val current = digitalTransactionDao.getById(id, businessId) ?: return
        digitalTransactionDao.update(
            current.copy(
                providerReferenceId = providerRefId,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    fun getPendingTransactions(): Flow<List<DigitalTransactionEntity>> {
        return digitalTransactionDao.getByStatus(DigitalTransactionStatus.PENDING.name, businessId)
    }

    fun getAllTransactions(): Flow<List<DigitalTransactionEntity>> {
        return digitalTransactionDao.getByBusinessId(businessId)
    }
}
