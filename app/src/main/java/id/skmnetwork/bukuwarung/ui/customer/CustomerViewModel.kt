package id.skmnetwork.bukuwarung.ui.customer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import id.skmnetwork.bukuwarung.data.local.entity.CustomerEntity
import id.skmnetwork.bukuwarung.data.local.entity.DebtEntity
import id.skmnetwork.bukuwarung.data.local.entity.DebtPaymentEntity
import id.skmnetwork.bukuwarung.data.repository.CustomerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class CustomerViewModel(
    private val repository: CustomerRepository
) : ViewModel() {

    val searchQuery = MutableStateFlow("")

    val customers: StateFlow<List<CustomerEntity>> = searchQuery
        .flatMapLatest { query ->
            repository.searchCustomers(query)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun search(query: String) {
        searchQuery.value = query
    }

    fun getDebtsForCustomer(customerId: Long): StateFlow<List<DebtEntity>> {
        return repository.getDebtsForCustomer(customerId)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )
    }

    fun getPaymentsForDebt(debtId: Long): StateFlow<List<DebtPaymentEntity>> {
        return repository.getPaymentsForDebt(debtId)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )
    }

    fun getTotalOutstandingForCustomer(customerId: Long): StateFlow<Long?> {
        return repository.getTotalOutstandingForCustomer(customerId)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = 0L
            )
    }

    fun saveCustomer(
        name: String,
        phone: String?,
        address: String?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (name.trim().isEmpty()) {
            onError("Nama pelanggan wajib diisi")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.saveCustomer(name, phone, address)
                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e.localizedMessage ?: "Gagal menyimpan pelanggan")
                }
            }
        }
    }

    fun updateCustomer(
        id: Long,
        name: String,
        phone: String?,
        address: String?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (name.trim().isEmpty()) {
            onError("Nama pelanggan wajib diisi")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.updateCustomer(id, name, phone, address)
                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e.localizedMessage ?: "Gagal memperbarui pelanggan")
                }
            }
        }
    }

    fun deleteCustomer(
        id: Long,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.deleteCustomer(id)
                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError("Pelanggan tidak dapat dihapus karena memiliki histori transaksi.")
                }
            }
        }
    }

    fun checkoutCreditSale(
        cartItems: Map<Long, Double>,
        customerId: Long,
        discountAmount: Long = 0L,
        onSuccess: (Long) -> Unit,
        onError: (String) -> Unit
    ) {
        if (cartItems.isEmpty()) {
            onError("Keranjang kosong")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.processAtomicCreditCheckout(cartItems, customerId, discountAmount)
            withContext(Dispatchers.Main) {
                result.fold(
                    onSuccess = { saleId -> onSuccess(saleId) },
                    onFailure = { e -> onError(e.localizedMessage ?: "Gagal memproses transaksi kredit") }
                )
            }
        }
    }

    suspend fun getReceiptData(
        saleId: Long,
        userSettings: id.skmnetwork.bukuwarung.data.preferences.UserSettings? = null
    ): id.skmnetwork.bukuwarung.domain.receipt.ReceiptData? = repository.getReceiptData(saleId, userSettings)

    fun payDebt(
        debtId: Long,
        amountStr: String,
        note: String?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val amount = amountStr.trim().toLongOrNull()
        if (amount == null || amount <= 0) {
            onError("Jumlah pembayaran harus lebih dari 0")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.processAtomicDebtPayment(debtId, amount, note)
            withContext(Dispatchers.Main) {
                result.fold(
                    onSuccess = { onSuccess() },
                    onFailure = { e -> onError(e.localizedMessage ?: "Gagal memproses pembayaran hutang") }
                )
            }
        }
    }
}

class CustomerViewModelFactory(
    private val repository: CustomerRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CustomerViewModel::class.java)) {
            return CustomerViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
