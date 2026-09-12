package id.skmnetwork.bukuwarung.ui.supplier

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import id.skmnetwork.bukuwarung.data.local.entity.SupplierEntity
import id.skmnetwork.bukuwarung.data.local.entity.SupplierPayableEntity
import id.skmnetwork.bukuwarung.data.local.entity.SupplierPaymentEntity
import id.skmnetwork.bukuwarung.data.repository.SupplierRepository
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
class SupplierViewModel(
    private val repository: SupplierRepository
) : ViewModel() {

    val searchQuery = MutableStateFlow("")

    val suppliers: StateFlow<List<SupplierEntity>> = searchQuery
        .flatMapLatest { query ->
            repository.searchSuppliers(query)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun search(query: String) {
        searchQuery.value = query
    }

    fun getPayablesForSupplier(supplierId: Long): StateFlow<List<SupplierPayableEntity>> {
        return repository.getPayablesForSupplier(supplierId)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )
    }

    fun getPaymentsForPayable(payableId: Long): StateFlow<List<SupplierPaymentEntity>> {
        return repository.getPaymentsForPayable(payableId)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )
    }

    fun getTotalOutstandingForSupplier(supplierId: Long): StateFlow<Long?> {
        return repository.getTotalOutstandingForSupplier(supplierId)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = 0L
            )
    }

    fun saveSupplier(
        name: String,
        phone: String?,
        address: String?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (name.trim().isEmpty()) {
            onError("Nama supplier wajib diisi")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.saveSupplier(name, phone, address)
                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e.localizedMessage ?: "Gagal menyimpan supplier")
                }
            }
        }
    }

    fun updateSupplier(
        id: Long,
        name: String,
        phone: String?,
        address: String?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (name.trim().isEmpty()) {
            onError("Nama supplier wajib diisi")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.updateSupplier(id, name, phone, address)
                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e.localizedMessage ?: "Gagal memperbarui supplier")
                }
            }
        }
    }

    fun deleteSupplier(
        id: Long,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.deleteSupplier(id)
                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError("Supplier tidak dapat dihapus karena memiliki histori transaksi.")
                }
            }
        }
    }

    fun checkoutCreditPurchase(
        purchaseItems: Map<Long, Double>,
        supplierId: Long,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (purchaseItems.isEmpty()) {
            onError("Keranjang belanja kosong")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.processAtomicCreditPurchase(purchaseItems, supplierId)
            withContext(Dispatchers.Main) {
                result.fold(
                    onSuccess = { onSuccess() },
                    onFailure = { e -> onError(e.localizedMessage ?: "Gagal memproses pembelian kredit") }
                )
            }
        }
    }

    fun paySupplier(
        payableId: Long,
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
            val result = repository.processAtomicSupplierPayment(payableId, amount, note)
            withContext(Dispatchers.Main) {
                result.fold(
                    onSuccess = { onSuccess() },
                    onFailure = { e -> onError(e.localizedMessage ?: "Gagal memproses pembayaran supplier") }
                )
            }
        }
    }
}

class SupplierViewModelFactory(
    private val repository: SupplierRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SupplierViewModel::class.java)) {
            return SupplierViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
