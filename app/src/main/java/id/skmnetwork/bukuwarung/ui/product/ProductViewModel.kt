package id.skmnetwork.bukuwarung.ui.product

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import id.skmnetwork.bukuwarung.data.local.entity.CashTransactionEntity
import id.skmnetwork.bukuwarung.data.local.entity.CategoryEntity
import id.skmnetwork.bukuwarung.data.local.entity.DigitalTransactionEntity
import id.skmnetwork.bukuwarung.data.local.entity.FulfillmentMode
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.data.repository.ProductRepository
import id.skmnetwork.bukuwarung.domain.checkout.CartLine
import id.skmnetwork.bukuwarung.domain.checkout.CheckoutOrchestrator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import id.skmnetwork.bukuwarung.util.loadingFlag
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Step 11 - why a product could not be saved, as a semantic value rather than a bare sentence.
 *
 * The form previously received its failures as `onError(String)` and then worked out which field
 * was at fault by comparing that sentence against hardcoded Indonesian literals. That made the
 * user-facing copy load-bearing: reword a message and the field silently stopped being marked,
 * with no other signal. Carrying the reason as a typed variant lets the screen ask "was this the
 * name?" and keeps the text free to change in one place.
 *
 * This mirrors the shape the project already uses for the same job - `license.ValidationResult`,
 * `domain.stock.StockAdjustmentValidation` and `backup.BackupValidationResult` are all sealed
 * hierarchies whose variants carry their own message, and the UI renders `result.message`. Every
 * message below is the exact text the screen showed before, unchanged.
 */
sealed class ProductFormError(open val message: String) {
    /** The name field was empty. */
    data class NameRequired(override val message: String = "Tulis nama produk") : ProductFormError(message)

    /** The selling price was missing, unparseable, or not positive. */
    data class SellingPriceRequired(override val message: String = "Masukkan harga jual") :
        ProductFormError(message)

    /** A numeric field could not be parsed, or was negative. */
    data class InvalidNumber(override val message: String = "Tulis angka yang valid") :
        ProductFormError(message)

    /** The digital provider id is required for provider-fulfilsed digital items. */
    data class DigitalProviderIdRequired(
        override val message: String = "ID provider digital wajib diisi"
    ) : ProductFormError(message)

    /** The digital provider product code is required for provider-fulfilment. */
    data class DigitalProductCodeRequired(
        override val message: String = "Kode produk digital wajib diisi"
    ) : ProductFormError(message)

    /** A repository operation failed, carrying its existing explanation. */
    data class OperationFailed(val detail: String) : ProductFormError(detail)
}

class ProductViewModel(
    private val repository: ProductRepository,
    private val checkoutOrchestrator: CheckoutOrchestrator? = null
) : ViewModel() {

    // Step 2 (H): attached to the RAW source flows, before stateIn, so the initial value of a
    // StateFlow is never mistaken for a real answer. Shared by Home, Products and Cash.
    val isLoading: StateFlow<Boolean> = repository.allProducts.loadingFlag(viewModelScope)

    val products: StateFlow<List<ProductEntity>> = repository.allProducts
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = emptyList()
        )

    val categories: StateFlow<List<CategoryEntity>> = repository.allCategories
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = emptyList()
        )

    val cashBalance: StateFlow<Long?> = repository.totalCashBalance
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = 0L
        )

    val todaySalesTotal: StateFlow<Long?> = repository.getTodaySalesTotalFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = 0L
        )

    val todayExpenseTotal: StateFlow<Long?> = repository.getTodayExpenseTotalFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = 0L
        )

    val cashTransactions: StateFlow<List<CashTransactionEntity>> = repository.allCashTransactions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val purchases: StateFlow<List<id.skmnetwork.bukuwarung.data.local.entity.PurchaseTransactionEntity>> = repository.allPurchases
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val sales: StateFlow<List<id.skmnetwork.bukuwarung.data.local.entity.SaleTransactionEntity>> = repository.allSales
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val returns: StateFlow<List<id.skmnetwork.bukuwarung.data.local.entity.SaleReturnTransactionEntity>> = repository.allReturns
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    suspend fun getSaleItems(transactionId: Long): List<id.skmnetwork.bukuwarung.data.local.entity.SaleItemEntity> {
        return repository.getSaleItems(transactionId)
    }

    suspend fun getDigitalTransactionsBySaleItemIds(saleItemIds: List<Long>): List<DigitalTransactionEntity> {
        return repository.getDigitalTransactionsBySaleItemIds(saleItemIds)
    }

    fun checkDigitalStatus(saleItemId: Long, onComplete: (DigitalTransactionEntity?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.checkDigitalTransactionStatus(saleItemId)
            withContext(Dispatchers.Main) {
                onComplete(result)
            }
        }
    }

    suspend fun getReturnsForSale(saleId: Long): List<id.skmnetwork.bukuwarung.data.local.entity.SaleReturnTransactionEntity> {
        return repository.getReturnsForSale(saleId)
    }

    suspend fun getReturnItems(returnId: Long): List<id.skmnetwork.bukuwarung.data.local.entity.SaleReturnItemEntity> {
        return repository.getReturnItems(returnId)
    }

    suspend fun getReturnableQuantities(saleId: Long): Map<Long, Double> {
        return repository.getReturnableQuantities(saleId)
    }

    suspend fun getReturnedQuantityForSaleItem(saleItemId: Long): Double {
        return repository.getReturnedQuantityForSaleItem(saleItemId)
    }

    fun processSaleReturn(
        saleId: Long,
        itemsToReturn: Map<Long, Double>,
        reason: String? = null,
        notes: String? = null,
        onSuccess: (Long) -> Unit,
        onError: (String) -> Unit
    ) {
        if (itemsToReturn.isEmpty()) {
            onError("Pilih minimal 1 barang untuk diretur")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.processSaleReturn(
                saleId = saleId,
                itemsToReturn = itemsToReturn,
                reason = reason?.takeIf { it.isNotBlank() },
                notes = notes?.takeIf { it.isNotBlank() }
            )
            withContext(Dispatchers.Main) {
                result.fold(
                    onSuccess = { returnId -> onSuccess(returnId) },
                    onFailure = { err ->
                        val msg = err.localizedMessage ?: "Gagal memproses retur"
                        onError(msg)
                    }
                )
            }
        }
    }

    suspend fun getPurchaseItems(transactionId: Long): List<id.skmnetwork.bukuwarung.data.local.entity.PurchaseItemEntity> {
        return repository.getPurchaseItems(transactionId)
    }

    suspend fun getProductById(id: Long): ProductEntity? {
        return repository.getProductById(id)
    }

    suspend fun getProductByBarcode(barcode: String): ProductEntity? {
        return repository.getProductByBarcode(barcode)
    }

    suspend fun getCategoryNameById(id: Long): String {
        return repository.getCategoryById(id)?.name ?: ""
    }

    fun createCategory(
        name: String,
        onSuccess: (CategoryEntity) -> Unit,
        onError: (String) -> Unit
    ) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            onError("Nama kategori tidak boleh kosong")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.createCategory(trimmed)
            withContext(Dispatchers.Main) {
                result.fold(
                    onSuccess = { cat -> onSuccess(cat) },
                    onFailure = { err -> onError(err.localizedMessage ?: "Gagal membuat kategori") }
                )
            }
        }
    }

    fun saveProduct(
        name: String,
        categoryName: String,
        purchasePriceStr: String,
        sellingPriceStr: String,
        stockStr: String,
        minimumStockStr: String,
        unitStr: String,
        barcodeStr: String? = null,
        imageUriStr: String? = null,
        categoryId: Long? = null,
        itemType: id.skmnetwork.bukuwarung.data.local.entity.ItemType = id.skmnetwork.bukuwarung.data.local.entity.ItemType.PHYSICAL,
        fulfillmentMode: FulfillmentMode = FulfillmentMode.MANUAL,
        digitalProviderId: String? = null,
        digitalProductCode: String? = null,
        onSuccess: () -> Unit,
        onError: (ProductFormError) -> Unit
    ) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            onError(ProductFormError.NameRequired())
            return
        }

        val sellingPrice = sellingPriceStr.trim().toLongOrNull()
        if (sellingPrice == null || sellingPrice <= 0) {
            onError(ProductFormError.SellingPriceRequired())
            return
        }

        val purchasePrice = purchasePriceStr.trim().ifEmpty { "0" }.toLongOrNull()
        if (purchasePrice == null || purchasePrice < 0) {
            onError(ProductFormError.InvalidNumber())
            return
        }

        val stock = stockStr.trim().ifEmpty { "0" }.toDoubleOrNull()
        if (stock == null || stock < 0) {
            onError(ProductFormError.InvalidNumber())
            return
        }

        val minimumStock = minimumStockStr.trim().ifEmpty { "0" }.toDoubleOrNull()
        if (minimumStock == null || minimumStock < 0) {
            onError(ProductFormError.InvalidNumber())
            return
        }

        val unit = unitStr.trim().ifEmpty { "pcs" }

        if (fulfillmentMode == FulfillmentMode.PROVIDER) {
            if (digitalProviderId.isNullOrBlank()) {
                onError(ProductFormError.DigitalProviderIdRequired())
                return
            }
            if (digitalProductCode.isNullOrBlank()) {
                onError(ProductFormError.DigitalProductCodeRequired())
                return
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.insertProductWithCategory(
                    name = trimmedName,
                    categoryName = categoryName,
                    purchasePrice = purchasePrice,
                    sellingPrice = sellingPrice,
                    stock = stock,
                    minimumStock = minimumStock,
                    unit = unit,
                    barcode = barcodeStr,
                    imageUri = imageUriStr,
                    itemType = itemType,
                    categoryId = categoryId,
                    fulfillmentMode = fulfillmentMode,
                    digitalProviderId = digitalProviderId,
                    digitalProductCode = digitalProductCode
                )
                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(ProductFormError.OperationFailed("Gagal menyimpan produk: ${e.localizedMessage ?: "Terjadi kesalahan"}"))
                }
            }
        }
    }

    fun updateProduct(
        productId: Long,
        name: String,
        categoryName: String,
        purchasePriceStr: String,
        sellingPriceStr: String,
        minimumStockStr: String,
        unitStr: String,
        barcodeStr: String? = null,
        imageUriStr: String? = null,
        categoryId: Long? = null,
        itemType: id.skmnetwork.bukuwarung.data.local.entity.ItemType? = null,
        fulfillmentMode: FulfillmentMode? = null,
        digitalProviderId: String? = null,
        digitalProductCode: String? = null,
        onSuccess: () -> Unit,
        onError: (ProductFormError) -> Unit
    ) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            onError(ProductFormError.NameRequired())
            return
        }

        val sellingPrice = sellingPriceStr.trim().toLongOrNull()
        if (sellingPrice == null || sellingPrice <= 0) {
            onError(ProductFormError.SellingPriceRequired())
            return
        }

        val purchasePrice = purchasePriceStr.trim().ifEmpty { "0" }.toLongOrNull()
        if (purchasePrice == null || purchasePrice < 0) {
            onError(ProductFormError.InvalidNumber())
            return
        }

        // Step 2 (A): stock is intentionally NOT a parameter here. Editing a product must never be
        // able to rewrite live stock - see ProductRepository.adjustStock.
        val minimumStock = minimumStockStr.trim().ifEmpty { "0" }.toDoubleOrNull()
        if (minimumStock == null || minimumStock < 0) {
            onError(ProductFormError.InvalidNumber())
            return
        }

        val unit = unitStr.trim().ifEmpty { "pcs" }

        if (fulfillmentMode == FulfillmentMode.PROVIDER) {
            if (digitalProviderId.isNullOrBlank()) {
                onError(ProductFormError.DigitalProviderIdRequired())
                return
            }
            if (digitalProductCode.isNullOrBlank()) {
                onError(ProductFormError.DigitalProductCodeRequired())
                return
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.updateProductWithCategory(
                    productId = productId,
                    name = trimmedName,
                    categoryName = categoryName,
                    purchasePrice = purchasePrice,
                    sellingPrice = sellingPrice,
                    minimumStock = minimumStock,
                    unit = unit,
                    barcode = barcodeStr,
                    imageUri = imageUriStr,
                    categoryId = categoryId,
                    itemType = itemType,
                    fulfillmentMode = fulfillmentMode,
                    digitalProviderId = digitalProviderId,
                    digitalProductCode = digitalProductCode
                )
                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(ProductFormError.OperationFailed("Gagal memperbarui produk: ${e.localizedMessage ?: "Terjadi kesalahan"}"))
                }
            }
        }
    }

    /**
     * Step 2 (D) - correct a product's stock, recording it as a stock movement.
     *
     * @param isStockCount true for a physical stock count (OPNAME), false for a correction.
     */
    fun adjustStock(
        productId: Long,
        newStock: Double,
        note: String?,
        isStockCount: Boolean = false,
        onSuccess: (id.skmnetwork.bukuwarung.data.repository.ProductRepository.StockAdjustmentResult) -> Unit,
        onError: (String) -> Unit
    ) {
        if (newStock < 0) {
            onError("Stok tidak boleh kurang dari 0")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = repository.adjustStock(
                    productId = productId,
                    newStock = newStock,
                    note = note,
                    movementType = if (isStockCount) "OPNAME" else "ADJUSTMENT"
                )
                withContext(Dispatchers.Main) { onSuccess(result) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e.localizedMessage ?: "Gagal menyimpan penyesuaian stok")
                }
            }
        }
    }

    fun deleteProduct(
        productId: Long,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.deleteProductById(productId)
                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError("Gagal menghapus produk: ${e.localizedMessage ?: "Terjadi kesalahan"}")
                }
            }
        }
    }

    fun checkoutCart(
        cartItems: Map<Long, Double>,
        paymentMethod: String = "CASH",
        discountAmount: Long = 0L,
        taxSettings: id.skmnetwork.bukuwarung.domain.tax.TaxSettings? = null,
        onSuccess: (Long) -> Unit,
        onError: (String) -> Unit
    ) {
        if (cartItems.isEmpty()) {
            onError("Keranjang kosong")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.processAtomicCheckout(
                cartItems = cartItems,
                paymentMethod = paymentMethod,
                discountAmount = discountAmount,
                taxSettings = taxSettings
            )
            withContext(Dispatchers.Main) {
                result.fold(
                    onSuccess = { saleId -> onSuccess(saleId) },
                    onFailure = { e -> onError(e.localizedMessage ?: "Gagal memproses transaksi") }
                )
            }
        }
    }

    fun checkoutCart(
        cartLines: List<CartLine>,
        paymentMethod: String = "CASH",
        discountAmount: Long = 0L,
        taxSettings: id.skmnetwork.bukuwarung.domain.tax.TaxSettings? = null,
        onSuccess: (Long) -> Unit,
        onError: (String) -> Unit
    ) {
        if (cartLines.isEmpty()) {
            onError("Keranjang kosong")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val result = checkoutOrchestrator?.processCheckout(
                cartLines = cartLines,
                paymentMethod = paymentMethod,
                discountAmount = discountAmount,
                taxSettings = taxSettings
            ) ?: repository.processAtomicCheckout(
                cartLines = cartLines,
                paymentMethod = paymentMethod,
                discountAmount = discountAmount,
                taxSettings = taxSettings
            )
            withContext(Dispatchers.Main) {
                result.fold(
                    onSuccess = { saleId -> onSuccess(saleId) },
                    onFailure = { e -> onError(e.localizedMessage ?: "Gagal memproses transaksi") }
                )
            }
        }
    }

    suspend fun getReceiptData(
        saleId: Long,
        userSettings: id.skmnetwork.bukuwarung.data.preferences.UserSettings? = null,
        cashGiven: Long? = null
    ): id.skmnetwork.bukuwarung.domain.receipt.ReceiptData? = repository.getReceiptData(saleId, userSettings, cashGiven)

    suspend fun getReturnReceiptData(
        returnId: Long,
        userSettings: id.skmnetwork.bukuwarung.data.preferences.UserSettings? = null,
        cashGiven: Long? = null
    ): id.skmnetwork.bukuwarung.domain.receipt.ReceiptData? = repository.getReturnReceiptData(returnId, userSettings, cashGiven)

    fun checkoutPurchase(
        purchaseItems: Map<Long, Double>,
        supplierId: Long? = null,
        taxSettings: id.skmnetwork.bukuwarung.domain.tax.TaxSettings? = null,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (purchaseItems.isEmpty()) {
            onError("Keranjang belanja kosong")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.processAtomicPurchase(
                purchaseItems = purchaseItems,
                supplierId = supplierId,
                taxSettings = taxSettings
            )
            withContext(Dispatchers.Main) {
                result.fold(
                    onSuccess = { onSuccess() },
                    onFailure = { e -> onError(e.localizedMessage ?: "Gagal memproses belanja") }
                )
            }
        }
    }

    fun addManualCash(
        type: String,
        amountStr: String,
        description: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val amount = amountStr.trim().toLongOrNull()
        if (amount == null || amount <= 0) {
            onError("Jumlah harus lebih dari 0")
            return
        }
        if (description.trim().isEmpty()) {
            onError("Deskripsi wajib diisi")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.addManualCashTransaction(type, amount, description)
            withContext(Dispatchers.Main) {
                result.fold(
                    onSuccess = { onSuccess() },
                    onFailure = { e -> onError(e.localizedMessage ?: "Gagal menyimpan transaksi kas") }
                )
            }
        }
    }
}

class ProductViewModelFactory(
    private val repository: ProductRepository,
    private val checkoutOrchestrator: CheckoutOrchestrator? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProductViewModel::class.java)) {
            return ProductViewModel(repository, checkoutOrchestrator) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
