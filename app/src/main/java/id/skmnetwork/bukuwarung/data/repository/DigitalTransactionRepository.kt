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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DigitalTransactionRepository @Inject constructor(
    private val digitalTransactionDao: DigitalTransactionDao,
    private val backendApi: BackendApi? = null
) {
    suspend fun createDraft(
        saleItemId: Long,
        providerId: String,
        providerProductCode: String,
        destinationNumber: String,
        sellingPrice: Long,
        actualPurchasePrice: Long = sellingPrice
    ): Long {
        val transaction = DigitalTransactionEntity(
            saleItemId = saleItemId,
            providerId = providerId,
            providerProductCode = providerProductCode,
            destinationNumber = destinationNumber,
            sellingPrice = sellingPrice,
            actualPurchasePrice = actualPurchasePrice,
            status = DigitalTransactionStatus.DRAFT.name
        )
        return digitalTransactionDao.insert(transaction)
    }

    suspend fun submitDraft(id: Long): DigitalTransactionEntity {
        val current = digitalTransactionDao.getById(id)
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
        val current = digitalTransactionDao.getById(id)
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
        val current = digitalTransactionDao.getByUuid(idempotencyKey) ?: return
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
        val unknown = digitalTransactionDao.getByStatus(DigitalTransactionStatus.UNKNOWN.name).first()
        for (tx in unknown) {
            try {
                checkStatus(tx.id)
            } catch (_: Exception) {
            }
        }
    }

    suspend fun getBySaleItemIds(saleItemIds: List<Long>): List<DigitalTransactionEntity> {
        if (saleItemIds.isEmpty()) return emptyList()
        return digitalTransactionDao.getBySaleItemIds(saleItemIds.distinct())
    }

    suspend fun transitionState(id: Long, newState: DigitalTransactionStatus, failureReason: String? = null, snToken: String? = null) {
        val current = digitalTransactionDao.getById(id) ?: return
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
        val current = digitalTransactionDao.getById(id) ?: return
        digitalTransactionDao.update(
            current.copy(
                providerReferenceId = providerRefId,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    fun getPendingTransactions(): Flow<List<DigitalTransactionEntity>> {
        return digitalTransactionDao.getByStatus(DigitalTransactionStatus.PENDING.name)
    }

    fun getUnknownTransactions(): Flow<List<DigitalTransactionEntity>> {
        return digitalTransactionDao.getByStatus(DigitalTransactionStatus.UNKNOWN.name)
    }
}
