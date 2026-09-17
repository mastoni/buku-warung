package id.skmnetwork.bukuwarung.ui.purchase

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.data.local.entity.PurchaseOrderEntity
import id.skmnetwork.bukuwarung.data.local.entity.PurchaseOrderItemEntity
import id.skmnetwork.bukuwarung.data.local.entity.PurchaseOrderStatus
import id.skmnetwork.bukuwarung.data.local.entity.SupplierEntity
import id.skmnetwork.bukuwarung.data.preferences.UserPreferencesRepository
import id.skmnetwork.bukuwarung.data.repository.PurchaseOrderItemInput
import id.skmnetwork.bukuwarung.data.repository.PurchaseOrderRepository
import id.skmnetwork.bukuwarung.domain.receipt.ReceiptPaperWidth
import id.skmnetwork.bukuwarung.pdf.PdfReportGenerator
import id.skmnetwork.bukuwarung.pdf.PdfShareManager
import id.skmnetwork.bukuwarung.printer.PrinterService
import id.skmnetwork.bukuwarung.purchase.PurchaseOrderPdfBuilder
import id.skmnetwork.bukuwarung.purchase.WhatsAppPurchaseOrderFormatter
import id.skmnetwork.bukuwarung.purchase.WhatsAppPurchaseOrderShareHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Gate G13.5 — ViewModel managing Purchase Order creation, editing, status transitions,
 * WhatsApp sharing, thermal receipt printing, and PDF document generation.
 */
class PurchaseOrderViewModel(
    private val purchaseOrderRepository: PurchaseOrderRepository,
    private val userPreferencesRepository: UserPreferencesRepository? = null
) : ViewModel() {

    val allOrders: StateFlow<List<PurchaseOrderEntity>> = purchaseOrderRepository.allOrders
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    val filterStatus = MutableStateFlow("ALL")

    val filteredOrders: StateFlow<List<PurchaseOrderEntity>> = combine(
        allOrders,
        filterStatus
    ) { orders, status ->
        if (status == "ALL") {
            orders
        } else {
            orders.filter { it.status.equals(status, ignoreCase = true) }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    val selectedOrderDetails = MutableStateFlow<Pair<PurchaseOrderEntity, List<PurchaseOrderItemEntity>>?>(null)
    val isLoading = MutableStateFlow(false)
    val errorMessage = MutableStateFlow<String?>(null)

    fun setFilter(status: String) {
        filterStatus.value = status
    }

    fun clearError() {
        errorMessage.value = null
    }

    suspend fun loadOrderDetails(orderId: Long): Pair<PurchaseOrderEntity, List<PurchaseOrderItemEntity>>? {
        return withContext(Dispatchers.IO) {
            val order = purchaseOrderRepository.getOrderById(orderId) ?: return@withContext null
            val items = purchaseOrderRepository.getItemsForOrder(orderId)
            val pair = Pair(order, items)
            selectedOrderDetails.value = pair
            pair
        }
    }

    suspend fun createDraftOrder(
        supplierId: Long,
        items: List<PurchaseOrderItemInput>,
        notes: String? = null
    ): Result<Long> {
        isLoading.value = true
        return try {
            val result = purchaseOrderRepository.createOrder(
                supplierId = supplierId,
                items = items,
                notes = notes
            )
            if (result.isFailure) {
                errorMessage.value = result.exceptionOrNull()?.message ?: "Gagal membuat pesanan"
            }
            result
        } finally {
            isLoading.value = false
        }
    }

    suspend fun updateDraftOrder(
        orderId: Long,
        supplierId: Long,
        items: List<PurchaseOrderItemInput>,
        notes: String? = null
    ): Result<Unit> {
        isLoading.value = true
        return try {
            val result = purchaseOrderRepository.updateOrder(
                orderId = orderId,
                supplierId = supplierId,
                items = items,
                notes = notes
            )
            if (result.isSuccess) {
                loadOrderDetails(orderId)
            } else {
                errorMessage.value = result.exceptionOrNull()?.message ?: "Gagal memperbarui pesanan"
            }
            result
        } finally {
            isLoading.value = false
        }
    }

    suspend fun markOrderOrdered(orderId: Long): Result<Unit> {
        isLoading.value = true
        return try {
            val result = purchaseOrderRepository.markOrderOrdered(orderId)
            if (result.isSuccess) {
                loadOrderDetails(orderId)
            } else {
                errorMessage.value = result.exceptionOrNull()?.message ?: "Gagal menandai pesanan sebagai dipesan"
            }
            result
        } finally {
            isLoading.value = false
        }
    }

    suspend fun cancelOrder(orderId: Long): Result<Unit> {
        isLoading.value = true
        return try {
            val result = purchaseOrderRepository.cancelOrder(orderId)
            if (result.isSuccess) {
                loadOrderDetails(orderId)
            } else {
                errorMessage.value = result.exceptionOrNull()?.message ?: "Gagal membatalkan pesanan"
            }
            result
        } finally {
            isLoading.value = false
        }
    }

    fun shareWhatsApp(
        context: Context,
        order: PurchaseOrderEntity,
        items: List<PurchaseOrderItemEntity>,
        shopName: String = "Usaha Kami",
        poTitle: String = "PURCHASE ORDER"
    ) {
        val text = WhatsAppPurchaseOrderFormatter.formatOrderText(
            shopName = shopName,
            order = order,
            items = items,
            poTitle = poTitle
        )
        WhatsAppPurchaseOrderShareHelper.sharePurchaseOrder(
            context = context,
            orderText = text,
            supplierPhone = order.supplierPhoneSnapshot
        )
    }

    suspend fun printOrder(
        printerService: PrinterService,
        order: PurchaseOrderEntity,
        items: List<PurchaseOrderItemEntity>,
        shopName: String = "Usaha Kami",
        shopAddress: String = "",
        shopPhone: String = "",
        paperWidth: ReceiptPaperWidth = ReceiptPaperWidth.WIDTH_58MM,
        poTitle: String = "PURCHASE ORDER"
    ): Result<Unit> {
        return printerService.printPurchaseOrder(
            order = order,
            items = items,
            shopName = shopName,
            shopAddress = shopAddress,
            shopPhone = shopPhone,
            width = paperWidth,
            poTitle = poTitle,
            autoConnect = true
        )
    }

    suspend fun exportAndSharePdf(
        context: Context,
        order: PurchaseOrderEntity,
        items: List<PurchaseOrderItemEntity>,
        shopName: String = "Usaha Kami",
        address: String = "",
        phone: String = "",
        poTitle: String = "PURCHASE ORDER"
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val doc = PurchaseOrderPdfBuilder.build(
                order = order,
                items = items,
                shopName = shopName,
                address = address,
                phone = phone,
                poTitle = poTitle
            )
            val generator = PdfReportGenerator(context)
            val fileName = "PO_${order.orderNumber.replace("[^a-zA-Z0-9]".toRegex(), "_")}.pdf"
            val genResult = generator.generatePdf(doc, fileName)

            if (genResult.isSuccess) {
                val file = genResult.getOrThrow()
                withContext(Dispatchers.Main) {
                    val shareIntent = PdfShareManager.createSharePdfIntent(context, file, "Bagikan Dokumen Purchase Order")
                    context.startActivity(shareIntent)
                }
            }
            genResult
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class PurchaseOrderViewModelFactory(
    private val purchaseOrderRepository: PurchaseOrderRepository,
    private val userPreferencesRepository: UserPreferencesRepository? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PurchaseOrderViewModel::class.java)) {
            return PurchaseOrderViewModel(purchaseOrderRepository, userPreferencesRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
