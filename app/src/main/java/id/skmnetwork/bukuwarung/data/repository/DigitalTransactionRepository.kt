package id.skmnetwork.bukuwarung.data.repository

import id.skmnetwork.bukuwarung.data.local.dao.DigitalTransactionDao
import id.skmnetwork.bukuwarung.data.local.entity.DigitalTransactionEntity
import id.skmnetwork.bukuwarung.data.local.entity.DigitalTransactionStatus
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DigitalTransactionRepository @Inject constructor(
    private val digitalTransactionDao: DigitalTransactionDao
) {
    suspend fun createDraft(
        saleItemId: Long,
        providerId: String,
        providerProductCode: String,
        destinationNumber: String,
        sellingPrice: Long,
        actualPurchasePrice: Long
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

    suspend fun transitionState(id: Long, newState: DigitalTransactionStatus, failureReason: String? = null, snToken: String? = null) {
        val current = digitalTransactionDao.getById(id) ?: return
        val currentState = DigitalTransactionStatus.valueOf(current.status)

        // Validate Transition
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
