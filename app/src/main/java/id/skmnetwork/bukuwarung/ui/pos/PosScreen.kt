package id.skmnetwork.bukuwarung.ui.pos

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.skmnetwork.bukuwarung.data.local.entity.CustomerEntity
import id.skmnetwork.bukuwarung.data.local.entity.FulfillmentMode
import id.skmnetwork.bukuwarung.data.local.entity.ItemType
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.data.local.entity.SaleTransactionEntity
import id.skmnetwork.bukuwarung.domain.checkout.CartLine
import id.skmnetwork.bukuwarung.data.preferences.UserSettings
import id.skmnetwork.bukuwarung.domain.business.BusinessCapability
import id.skmnetwork.bukuwarung.domain.business.BusinessTaxonomyRegistry
import id.skmnetwork.bukuwarung.domain.business.PosContextFamily
import id.skmnetwork.bukuwarung.domain.business.ResolvedBusinessProfile
import id.skmnetwork.bukuwarung.domain.discount.DiscountCalculator
import id.skmnetwork.bukuwarung.domain.discount.DiscountInput
import id.skmnetwork.bukuwarung.domain.discount.DiscountType
import id.skmnetwork.bukuwarung.ui.components.AppCard
import id.skmnetwork.bukuwarung.ui.components.AppEmptyState
import id.skmnetwork.bukuwarung.ui.components.AppTextField
import id.skmnetwork.bukuwarung.ui.components.CameraBarcodeScannerDialog
import id.skmnetwork.bukuwarung.ui.components.ProductImageThumbnail
import id.skmnetwork.bukuwarung.ui.components.SegmentedControl
import id.skmnetwork.bukuwarung.ui.customer.CustomerViewModel
import id.skmnetwork.bukuwarung.ui.navigation.CheckoutSuccessData
import id.skmnetwork.bukuwarung.ui.product.ProductViewModel
import id.skmnetwork.bukuwarung.ui.report.ReportPeriod
import id.skmnetwork.bukuwarung.ui.theme.AppColors
import id.skmnetwork.bukuwarung.ui.theme.AppShapes
import id.skmnetwork.bukuwarung.ui.theme.AppSpacing
import id.skmnetwork.bukuwarung.ui.theme.rememberAppWindowSize
import id.skmnetwork.bukuwarung.util.formatQuantityValue
import id.skmnetwork.bukuwarung.util.formatRupiah
import id.skmnetwork.bukuwarung.data.local.entity.DigitalTransactionEntity
import id.skmnetwork.bukuwarung.data.local.entity.DigitalTransactionStatus
import id.skmnetwork.bukuwarung.data.repository.DigitalTransactionRepository
import id.skmnetwork.bukuwarung.domain.business.DigitalProductCatalog
import id.skmnetwork.bukuwarung.domain.business.DigitalProductItem
import id.skmnetwork.bukuwarung.domain.money.MoneyCalculator
import id.skmnetwork.bukuwarung.domain.tax.TaxSettings
import id.skmnetwork.bukuwarung.domain.tax.TaxPriceMode
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosScreen(
    viewModel: ProductViewModel,
    customerViewModel: CustomerViewModel,
    userSettings: UserSettings = UserSettings(),
    printerService: id.skmnetwork.bukuwarung.printer.PrinterService? = null,
    digitalTransactionRepository: DigitalTransactionRepository? = null,
    onNavigateToAddProduct: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onOpenNavigation: () -> Unit = {}
) {
    val resolvedProfile = remember(userSettings.primaryBusinessType, userSettings.secondaryActivities) {
        BusinessTaxonomyRegistry.resolve(
            primaryType = userSettings.primaryBusinessType,
            secondaryActivities = userSettings.secondaryActivities
        )
    }
    val terminology = resolvedProfile.terminology
    val posContextFamily = remember(resolvedProfile.businessType, resolvedProfile.operatingMode) {
        PosContextFamily.fromBusinessType(
            businessType = resolvedProfile.businessType,
            operatingMode = resolvedProfile.operatingMode
        )
    }

    val dbProducts by viewModel.products.collectAsStateWithLifecycle()
    val dbCategories by viewModel.categories.collectAsStateWithLifecycle()
    val customers by customerViewModel.customers.collectAsStateWithLifecycle()
    val sales by viewModel.sales.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(0) } // 0: Kasir, 1: Riwayat Penjualan

    var query by remember { mutableStateOf("") }
    var salesHistoryQuery by remember { mutableStateOf("") }
    // Step 9 - sale history discoverability. The period chips reuse the reports screen's
    // ReportPeriod vocabulary and its date range, and the payment chips reuse the codes the POS
    // itself writes, so neither filter invents a value the sale model does not already carry.
    // null period and SalesPaymentFilter.ALL are the clear state.
    var salesHistoryPeriod by remember { mutableStateOf<ReportPeriod?>(null) }
    var salesHistoryPayment by remember { mutableStateOf(SalesPaymentFilter.ALL) }
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) } // null = Semua

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cart = remember { mutableStateListOf<CartLine>() }

    val windowSize = rememberAppWindowSize()
    val productGridColumns = windowSize.gridColumns(compact = 2, medium = 3, expanded = 4)

    fun isProvider(product: ProductEntity): Boolean {
        return product.itemType == ItemType.DIGITAL.name &&
                product.fulfillmentMode == FulfillmentMode.PROVIDER.name
    }

    fun isInternetAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun normalizedDestination(destinationNumber: String?): String? {
        return destinationNumber?.trim()?.ifEmpty { null }
    }

    fun addProductToCart(product: ProductEntity) {
        val isServiceOrDigital = product.itemType == ItemType.SERVICE.name ||
                product.itemType == ItemType.DIGITAL.name
        val provider = isProvider(product)
        val existingIndex = if (provider) {
            cart.indexOfFirst { it.product.id == product.id && normalizedDestination(it.destinationNumber) == null }
        } else {
            cart.indexOfFirst { it.product.id == product.id }
        }

        if (isServiceOrDigital) {
            if (existingIndex >= 0) {
                val existing = cart[existingIndex]
                cart[existingIndex] = existing.copy(quantity = existing.quantity + 1.0)
            } else {
                cart.add(CartLine(product = product, quantity = 1.0, destinationNumber = null))
            }
            Toast.makeText(context, "+1 ${product.name}", Toast.LENGTH_SHORT).show()
        } else if (product.stock <= 0.0) {
            Toast.makeText(context, "${terminology.stockLabel} ${product.name} habis (0)", Toast.LENGTH_SHORT).show()
        } else {
            val currentQty = if (existingIndex >= 0) cart[existingIndex].quantity else 0.0
            if (currentQty + 1.0 <= product.stock) {
                if (existingIndex >= 0) {
                    val existing = cart[existingIndex]
                    cart[existingIndex] = existing.copy(quantity = existing.quantity + 1.0)
                } else {
                    cart.add(CartLine(product = product, quantity = 1.0, destinationNumber = null))
                }
                Toast.makeText(context, "+1 ${product.name}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "${terminology.stockLabel} ${product.name} hanya tersisa ${formatQuantityValue(product.stock)}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Step 5: only stockable goods get the precise-entry editor.
     *
     * A service or a digital item has no stock and the register has always billed it one unit at a
     * time, so inventing a fractional rule for those would be a product decision, not a UX one.
     * Those lines keep exactly the stepper behaviour they had.
     */
    fun isStockableCartLine(product: ProductEntity): Boolean =
        product.itemType != ItemType.SERVICE.name && product.itemType != ItemType.DIGITAL.name

    fun updateCartDestination(index: Int, destinationNumber: String) {
        val normalized = normalizedDestination(destinationNumber)
        val current = cart[index]
        val duplicateIndex = cart.indexOfFirst {
            it.lineId != current.lineId &&
                    it.product.id == current.product.id &&
                    normalizedDestination(it.destinationNumber) == normalized
        }
        if (duplicateIndex >= 0) {
            val duplicate = cart[duplicateIndex]
            cart[duplicateIndex] = duplicate.copy(quantity = duplicate.quantity + current.quantity)
            cart.removeAt(index)
        } else {
            cart[index] = current.copy(destinationNumber = normalized)
        }
    }

    var showPaymentSelectorDialog by remember { mutableStateOf(false) }

    // Step 2 (E): emptying the cart is destructive, so it is confirmed instead of immediate.
    var showClearCartDialog by remember { mutableStateOf(false) }
    var showCashPaymentDialog by remember { mutableStateOf(false) }
    var showQrisPaymentDialog by remember { mutableStateOf(false) }
    var showCheckoutSuccessDialog by remember { mutableStateOf(false) }
    // PHONE only: the cart body the tablet shows permanently, shown on demand instead.
    var showPhoneCartDialog by remember { mutableStateOf(false) }

    var selectedSaleForDetail by remember { mutableStateOf<SaleTransactionEntity?>(null) }
    var selectedSaleForReturn by remember { mutableStateOf<SaleTransactionEntity?>(null) }

    var lastCheckoutData by remember { mutableStateOf<CheckoutSuccessData?>(null) }
    var isCheckingOut by remember { mutableStateOf(false) }
    var printerStatusMessage by remember { mutableStateOf<String?>(null) }
    var isPrintingReceipt by remember { mutableStateOf(false) }

    var selectedPaymentMethod by remember { mutableStateOf("CASH") } // "CASH", "QRIS", "CREDIT"
    var selectedCustomerForCredit by remember { mutableStateOf<CustomerEntity?>(null) }
    var showCustomerPickerSheet by remember { mutableStateOf(false) }
    var showPosScannerDialog by remember { mutableStateOf(false) }

    var digitalDestinationNumber by remember { mutableStateOf("") }
    var selectedDigitalProduct by remember { mutableStateOf<DigitalProductItem?>(null) }
    var selectedDigitalCategory by remember { mutableStateOf("ALL") }
    var isSubmittingDigital by remember { mutableStateOf(false) }
    var lastDigitalTransaction by remember { mutableStateOf<DigitalTransactionEntity?>(null) }
    var showDigitalResultDialog by remember { mutableStateOf(false) }
    var isCheckingDigitalStatus by remember { mutableStateOf(false) }
    var serviceTypeFilter by remember { mutableStateOf("ALL") } // "ALL", "SERVICE", "PARTS"

    val detectedDigitalProvider = remember(digitalDestinationNumber) {
        DigitalProductCatalog.detectProvider(digitalDestinationNumber)
    }

    val filteredDigitalProducts = remember(detectedDigitalProvider, selectedDigitalCategory) {
        DigitalProductCatalog.PRESET_ITEMS.filter { item ->
            val matchesCategory = selectedDigitalCategory == "ALL" || item.category.equals(selectedDigitalCategory, ignoreCase = true)
            val matchesProvider = detectedDigitalProvider == null || item.providerId.equals(detectedDigitalProvider, ignoreCase = true)
            matchesCategory && matchesProvider
        }
    }

    val filteredProducts = dbProducts.filter { product ->
        val matchesQuery = product.name.contains(query, ignoreCase = true) ||
                (product.barcode != null && product.barcode.contains(query, ignoreCase = true))
        val matchesCategory = selectedCategoryId == null || product.categoryId == selectedCategoryId
        matchesQuery && matchesCategory
    }

    val serviceFilteredProducts = remember(filteredProducts, serviceTypeFilter) {
        when (serviceTypeFilter) {
            "SERVICE" -> filteredProducts.filter { it.itemType == ItemType.SERVICE.name }
            "PARTS" -> filteredProducts.filter { it.itemType != ItemType.SERVICE.name }
            else -> filteredProducts
        }
    }

    fun processStandaloneDigitalCheckout() {
        val dest = digitalDestinationNumber.trim()
        if (dest.isBlank()) {
            Toast.makeText(context, "Masukkan nomor tujuan / No Meter", Toast.LENGTH_SHORT).show()
            return
        }
        val product = selectedDigitalProduct
        if (product == null) {
            Toast.makeText(context, "Pilih nominal produk digital", Toast.LENGTH_SHORT).show()
            return
        }
        if (isSubmittingDigital) return
        isSubmittingDigital = true
        scope.launch {
            try {
                val repo = digitalTransactionRepository
                if (repo != null) {
                    val draftId = repo.createStandaloneDraft(
                        providerId = product.providerId,
                        providerProductCode = product.code,
                        destinationNumber = dest,
                        sellingPrice = product.sellingPrice,
                        actualPurchasePrice = product.purchasePrice,
                        adminFee = product.adminFee,
                        paymentMethod = "CASH"
                    )
                    val submitted = repo.submitDraft(draftId)
                    lastDigitalTransaction = submitted
                    showDigitalResultDialog = true
                    selectedDigitalProduct = null
                    digitalDestinationNumber = ""
                } else {
                    Toast.makeText(context, "Repository digital belum diinisialisasi", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal memproses: ${e.localizedMessage ?: "Terjadi kesalahan"}", Toast.LENGTH_SHORT).show()
            } finally {
                isSubmittingDigital = false
            }
        }
    }

    val customerNamesById = remember(customers) {
        customers.associate { it.id to it.name }
    }

    val filteredSales = filterSalesHistory(
        sales = sales,
        customerNamesById = customerNamesById,
        query = salesHistoryQuery,
        period = salesHistoryPeriod,
        paymentFilter = salesHistoryPayment
    )

    var discountType by remember { mutableStateOf(DiscountType.FIXED) }
    var discountInputText by remember { mutableStateOf("") }

    // Step 3 section 9: the item count is a real quantity, so it is never truncated to an Int.
    val totalItemCount = formatQuantityValue(cart.sumOf { it.quantity })
    val totalPrice = cart.sumOf { MoneyCalculator.lineSubtotal(it.product.sellingPrice, it.quantity) }

    val parsedDiscountValue = discountInputText.trim().toDoubleOrNull() ?: 0.0
    val discountCalcResult = remember(totalPrice, discountType, parsedDiscountValue) {
        DiscountCalculator.calculate(
            grossSubtotal = totalPrice,
            discountInput = DiscountInput(discountType, parsedDiscountValue)
        )
    }
    val grossSubtotal = discountCalcResult.grossSubtotal
    val discountAmount = discountCalcResult.discountAmount
    val netTotal = discountCalcResult.netTotal

    val taxSettings = remember(userSettings.taxEnabled, userSettings.taxRate, userSettings.taxPriceMode) {
        id.skmnetwork.bukuwarung.domain.tax.TaxSettings(
            enabled = userSettings.taxEnabled,
            rate = userSettings.taxRate,
            priceMode = userSettings.taxPriceMode,
            roundingMode = java.math.RoundingMode.HALF_UP
        )
    }
    val taxBreakdown = remember(cart, taxSettings, discountAmount) {
        id.skmnetwork.bukuwarung.domain.tax.TaxCalculator.calculateSaleTax(
            items = cart.map { cartLine ->
                id.skmnetwork.bukuwarung.domain.tax.SaleItemTaxInput(
                    lineSubtotal = MoneyCalculator.lineSubtotal(cartLine.product.sellingPrice, cartLine.quantity),
                    taxable = taxSettings.enabled && cartLine.product.taxable,
                    taxRateOverride = if (taxSettings.enabled && cartLine.product.taxable) cartLine.product.taxRateOverride else null
                )
            },
            discountAmount = discountAmount,
            rate = taxSettings.rate,
            priceMode = taxSettings.priceMode,
            roundingMode = taxSettings.roundingMode
        )
    }
    val totalTaxAmount = taxBreakdown.totalTaxAmount
    val grandTotal = netTotal + totalTaxAmount

    val cartSummaryList = cart.map { Pair(it.product.name, it.quantity) }
    val hasMissingProviderDestination = cart.any {
        isProvider(it.product) && it.destinationNumber.isNullOrBlank()
    }

    if (selectedSaleForDetail != null) {
        SaleDetailDialog(
            sale = selectedSaleForDetail!!,
            customers = customers,
            viewModel = viewModel,
            userSettings = userSettings,
            printerService = printerService,
            onDismiss = { selectedSaleForDetail = null },
            onRequestReturn = {
                val targetSale = selectedSaleForDetail
                selectedSaleForDetail = null
                selectedSaleForReturn = targetSale
            }
        )
    }

    if (selectedSaleForReturn != null) {
        SaleReturnDialog(
            sale = selectedSaleForReturn!!,
            viewModel = viewModel,
            userSettings = userSettings,
            printerService = printerService,
            onDismiss = { selectedSaleForReturn = null },
            onReturnSuccess = {
                val targetSale = selectedSaleForReturn
                selectedSaleForReturn = null
                selectedSaleForDetail = targetSale
            }
        )
    }

    if (showPosScannerDialog) {
        CameraBarcodeScannerDialog(
            onDismiss = { showPosScannerDialog = false },
            onBarcodeScanned = { scannedBarcode ->
                showPosScannerDialog = false
                val cleanBarcode = scannedBarcode.trim()
                if (cleanBarcode.isEmpty()) return@CameraBarcodeScannerDialog

                scope.launch {
                    val matchedProduct = viewModel.getProductByBarcode(cleanBarcode)
                    if (matchedProduct != null) {
                        val isServiceOrDigital = matchedProduct.itemType == ItemType.SERVICE.name || matchedProduct.itemType == ItemType.DIGITAL.name
                        if (isServiceOrDigital) {
                            addProductToCart(matchedProduct)
                        } else if (matchedProduct.stock <= 0.0) {
                            Toast.makeText(context, "${terminology.stockLabel} ${matchedProduct.name} habis (0)", Toast.LENGTH_SHORT).show()
                        } else {
                            addProductToCart(matchedProduct)
                        }
                    } else {
                        Toast.makeText(context, "${terminology.productLabel} barcode $cleanBarcode tidak ditemukan", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    if (showCustomerPickerSheet) {
        AlertDialog(
            onDismissRequest = { showCustomerPickerSheet = false },
            title = { Text("Pilih ${terminology.customerLabel} Hutang", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                    modifier = Modifier.height(260.dp)
                ) {
                    if (customers.isEmpty()) {
                        Text("Belum ada ${terminology.customerLabel.lowercase()}. Tambahkan ${terminology.customerLabel.lowercase()} terlebih dahulu.", color = AppColors.TextSecondary)
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                            items(customers) { cust ->
                                Surface(
                                    shape = AppShapes.CardShape,
                                    color = if (selectedCustomerForCredit?.id == cust.id) AppColors.GreenLight else AppColors.SurfaceGray,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedCustomerForCredit = cust
                                            showCustomerPickerSheet = false
                                        }
                                ) {
                                    Text(
                                        text = cust.name + if (!cust.phone.isNullOrEmpty()) " (${cust.phone})" else "",
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(AppSpacing.md)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCustomerPickerSheet = false }) {
                    Text("Tutup", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showCashPaymentDialog) {
        CashPaymentDialog(
            totalPrice = grandTotal,
            isCheckingOut = isCheckingOut,
            cashReceivedEnabled = userSettings.cashReceivedEnabled,
            onDismiss = { showCashPaymentDialog = false },
            onConfirmCashPayment = { cashReceived, change ->
                if (isCheckingOut) return@CashPaymentDialog
                isCheckingOut = true
                viewModel.checkoutCart(
                    cartLines = cart.toList(),
                    paymentMethod = "CASH",
                    discountAmount = discountAmount,
                    taxSettings = taxSettings,
                    onSuccess = { saleId ->
                        scope.launch {
                            val receiptData = viewModel.getReceiptData(saleId, userSettings, cashReceived)
                            isCheckingOut = false
                            showCashPaymentDialog = false
                            showPaymentSelectorDialog = false
                            lastCheckoutData = CheckoutSuccessData(
                                items = cartSummaryList,
                                totalAmount = grandTotal,
                                paymentMethodLabel = "Tunai (Cash)",
                                cashReceivedAmount = cashReceived,
                                changeAmount = change,
                                saleId = saleId,
                                receiptData = receiptData,
                                taxAmount = totalTaxAmount.takeIf { it > 0 },
                                subtotalAmount = grossSubtotal
                            )
                            cart.clear()
                            discountInputText = ""
                            printerStatusMessage = null
                            showCheckoutSuccessDialog = true

                            // Asynchronously attempt printing outside DB transaction
                            if (printerService != null && receiptData != null) {
                                isPrintingReceipt = true
                                val printResult = printerService.printReceipt(receiptData)
                                isPrintingReceipt = false
                                printerStatusMessage = if (printResult.isSuccess) {
                                    "Struk berhasil dicetak"
                                } else {
                                    "Struk belum tercetak (${printResult.exceptionOrNull()?.localizedMessage ?: "Printer tidak terhubung"})"
                                }
                            }
                        }
                    },
                    onError = { error ->
                        isCheckingOut = false
                        Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        )
    }

    if (showQrisPaymentDialog) {
        QrisPaymentDialog(
            totalPrice = grandTotal,
            isCheckingOut = isCheckingOut,
            qrisImagePath = userSettings.qrisImagePath,
            onNavigateToSettings = onNavigateToSettings,
            onDismiss = { showQrisPaymentDialog = false },
            onConfirmQrisPayment = {
                if (isCheckingOut) return@QrisPaymentDialog
                isCheckingOut = true
                viewModel.checkoutCart(
                    cartLines = cart.toList(),
                    paymentMethod = "QRIS",
                    discountAmount = discountAmount,
                    taxSettings = taxSettings,
                    onSuccess = { saleId ->
                        scope.launch {
                            val receiptData = viewModel.getReceiptData(saleId, userSettings)
                            isCheckingOut = false
                            showQrisPaymentDialog = false
                            showPaymentSelectorDialog = false
                            lastCheckoutData = CheckoutSuccessData(
                                items = cartSummaryList,
                                totalAmount = grandTotal,
                                paymentMethodLabel = "QRIS",
                                saleId = saleId,
                                receiptData = receiptData,
                                taxAmount = totalTaxAmount.takeIf { it > 0 },
                                subtotalAmount = grossSubtotal
                            )
                            cart.clear()
                            discountInputText = ""
                            printerStatusMessage = null
                            showCheckoutSuccessDialog = true

                            // Asynchronously attempt printing outside DB transaction
                            if (printerService != null && receiptData != null) {
                                isPrintingReceipt = true
                                val printResult = printerService.printReceipt(receiptData)
                                isPrintingReceipt = false
                                printerStatusMessage = if (printResult.isSuccess) {
                                    "Struk berhasil dicetak"
                                } else {
                                    "Struk belum tercetak (${printResult.exceptionOrNull()?.localizedMessage ?: "Printer tidak terhubung"})"
                                }
                            }
                        }
                    },
                    onError = { error ->
                        isCheckingOut = false
                        Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        )
    }

    if (showClearCartDialog) {
        AlertDialog(
            onDismissRequest = { showClearCartDialog = false },
            title = { Text("Kosongkan Keranjang?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Kosongkan semua barang dari keranjang? " +
                        "Barang yang sudah dipilih akan dihapus dan harus dipilih ulang."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearCartDialog = false
                        cart.clear()
                        discountInputText = ""
                        selectedCustomerForCredit = null
                    },
                    enabled = !isCheckingOut
                ) {
                    Text(
                        text = "Kosongkan",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCartDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    if (showPaymentSelectorDialog) {
        AlertDialog(
            onDismissRequest = { if (!isCheckingOut) showPaymentSelectorDialog = false },
            modifier = if (windowSize.isCompact) {
                Modifier.fillMaxWidth()
            } else {
                Modifier.width(if (windowSize.isExpanded) 580.dp else 520.dp)
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Receipt,
                        contentDescription = null,
                        tint = AppColors.GreenPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("RINGKASAN & PEMBAYARAN", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                    Text("Detail Pesanan:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)

                    // Itemized Order Summary
                    Surface(
                        shape = AppShapes.CardShape,
                        color = AppColors.SurfaceGray,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (windowSize.isCompact) 110.dp else 140.dp)
                                .padding(AppSpacing.sm)
                        ) {
                            items(cart) { cartLine ->
                                val prod = cartLine.product
                                val qty = cartLine.quantity
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(prod.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                                        Text("${formatQuantityValue(qty)} × ${formatRupiah(prod.sellingPrice)}", color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                                    }
                                    Text(formatRupiah(MoneyCalculator.lineSubtotal(prod.sellingPrice, qty)), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }

                // Universal Discount Section
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Diskon Transaksi:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.weight(1f))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (discountType == DiscountType.FIXED) AppColors.GreenLight else AppColors.SurfaceGray,
                                modifier = Modifier.clickable { discountType = DiscountType.FIXED }
                            ) {
                                Text(
                                    "Nominal (Rp)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (discountType == DiscountType.FIXED) AppColors.GreenPrimary else AppColors.TextSecondary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (discountType == DiscountType.PERCENTAGE) AppColors.GreenLight else AppColors.SurfaceGray,
                                modifier = Modifier.clickable { discountType = DiscountType.PERCENTAGE }
                            ) {
                                Text(
                                    "Persen (%)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (discountType == DiscountType.PERCENTAGE) AppColors.GreenPrimary else AppColors.TextSecondary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        AppTextField(
                            value = discountInputText,
                            onValueChange = { discountInputText = it },
                            label = if (discountType == DiscountType.FIXED) "Nominal Diskon (Rp)" else "Persentase Diskon (0 - 100%)",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (discountAmount > 0) {
                            Text(
                                text = "Potongan diskon: ${formatRupiah(discountAmount)}",
                                color = AppColors.RedExpense,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    // Financial Summary Breakdown
                    Surface(
                        shape = AppShapes.CardShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(AppSpacing.sm),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Subtotal ($totalItemCount ${terminology.productLabel.lowercase()})", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                                Spacer(Modifier.weight(1f))
                                Text(formatRupiah(grossSubtotal), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            }
                            if (discountAmount > 0) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Diskon", style = MaterialTheme.typography.bodySmall, color = AppColors.RedExpense)
                                    Spacer(Modifier.weight(1f))
                                    Text("-${formatRupiah(discountAmount)}", style = MaterialTheme.typography.bodySmall, color = AppColors.RedExpense, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (totalTaxAmount > 0) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("DPP / Taxable Base", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                                    Spacer(Modifier.weight(1f))
                                    Text(formatRupiah(taxBreakdown.totalTaxableBase), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("PPN (${(taxSettings.rate)}%)", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                                    Spacer(Modifier.weight(1f))
                                    Text(formatRupiah(totalTaxAmount), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("TOTAL BAYAR", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.weight(1f))
                                Text(formatRupiah(grandTotal), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AppColors.GreenPrimary)
                            }
                        }
                    }

                    val availablePaymentMethods = remember(userSettings.cashEnabled, userSettings.qrisEnabled, userSettings.creditEnabled) {
                        val list = mutableListOf<Pair<String, String>>()
                        if (userSettings.cashEnabled) list.add("CASH" to "Tunai")
                        if (userSettings.qrisEnabled) list.add("QRIS" to "QRIS")
                        if (userSettings.creditEnabled) list.add("CREDIT" to "Hutang")
                        if (list.isEmpty()) list.add("CASH" to "Tunai")
                        list
                    }

                    LaunchedEffect(availablePaymentMethods) {
                        if (availablePaymentMethods.none { it.first == selectedPaymentMethod }) {
                            selectedPaymentMethod = availablePaymentMethods.first().first
                        }
                    }

                    Text("Metode Pembayaran:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)

                    // Gate 1.3: was a hand-rolled Row of weighted Surfaces with no height at all
                    // (~36dp), so the three payment methods were the smallest touch targets in the
                    // checkout dialog, and the selected one was `primary` + `Color.White` - 2.28:1 in
                    // dark, i.e. the merchant could not see which method was chosen at night.
                    //
                    // SegmentedControl is a presentation wrapper: `selectedPaymentMethod` stays the
                    // same String, `availablePaymentMethods` is still built from the same three
                    // settings flags in the same order, and the LaunchedEffect above still coerces
                    // the selection into the available set. No payment logic moves.
                    SegmentedControl(
                        options = availablePaymentMethods.map { it.first },
                        selected = selectedPaymentMethod,
                        onSelect = { selectedPaymentMethod = it },
                        label = { code ->
                            availablePaymentMethods.firstOrNull { it.first == code }?.second ?: code
                        }
                    )

                    if (selectedPaymentMethod == "CREDIT") {
                        Surface(
                            shape = AppShapes.CardShape,
                            color = if (selectedCustomerForCredit == null) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showCustomerPickerSheet = true }
                        ) {
                            Row(
                                Modifier.padding(AppSpacing.md),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.People, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                Spacer(Modifier.width(AppSpacing.sm))
                                Text(
                                    text = selectedCustomerForCredit?.name ?: "Pilih ${terminology.customerLabel} *",
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedCustomerForCredit == null) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(Icons.Default.KeyboardArrowDown, null)
                            }
                        }
                        if (selectedCustomerForCredit == null) {
                            Text("Pilih ${terminology.customerLabel.lowercase()} terlebih dahulu.", color = AppColors.RedExpense, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            },
            confirmButton = {
                val isCreditWithoutCustomer = selectedPaymentMethod == "CREDIT" && selectedCustomerForCredit == null
                Button(
                    onClick = {
                        if (hasMissingProviderDestination) {
                            Toast.makeText(context, "Isi nomor tujuan produk digital", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (isCheckingOut) return@Button
                        when (selectedPaymentMethod) {
                            "CASH" -> {
                                showCashPaymentDialog = true
                            }
                            "QRIS" -> {
                                showQrisPaymentDialog = true
                            }
                            "CREDIT" -> {
                                if (selectedCustomerForCredit == null) {
                                    Toast.makeText(context, "Pilih ${terminology.customerLabel.lowercase()} terlebih dahulu", Toast.LENGTH_SHORT).show()
                                    showCustomerPickerSheet = true
                                    return@Button
                                }
                                isCheckingOut = true
                                customerViewModel.checkoutCreditSale(
                                    cartLines = cart.toList(),
                                    customerId = selectedCustomerForCredit!!.id,
                                    discountAmount = discountAmount,
                                    taxSettings = taxSettings,
                                    onSuccess = { saleId ->
                                        scope.launch {
                                            val receiptData = customerViewModel.getReceiptData(saleId, userSettings)
                                            isCheckingOut = false
                                            showPaymentSelectorDialog = false
                                            lastCheckoutData = CheckoutSuccessData(
                                                items = cartSummaryList,
                                                totalAmount = grandTotal,
                                                paymentMethodLabel = "Hutang (${selectedCustomerForCredit?.name ?: terminology.customerLabel})",
                                                customerName = selectedCustomerForCredit?.name,
                                                saleId = saleId,
                                                receiptData = receiptData,
                                                taxAmount = totalTaxAmount.takeIf { it > 0 },
                                                subtotalAmount = grossSubtotal
                                            )
                                            cart.clear()
                                            discountInputText = ""
                                            selectedCustomerForCredit = null
                                            printerStatusMessage = null
                                            showCheckoutSuccessDialog = true

                                            // Asynchronously attempt printing outside DB transaction
                                            if (printerService != null && receiptData != null) {
                                                isPrintingReceipt = true
                                                val printResult = printerService.printReceipt(receiptData)
                                                isPrintingReceipt = false
                                                printerStatusMessage = if (printResult.isSuccess) {
                                                    "Struk berhasil dicetak"
                                                } else {
                                                    "Struk belum tercetak (${printResult.exceptionOrNull()?.localizedMessage ?: "Printer tidak terhubung"})"
                                                }
                                            }
                                        }
                                    },
                                    onError = { error ->
                                        isCheckingOut = false
                                        Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    },
                    enabled = !isCheckingOut && !isCreditWithoutCustomer,
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.GreenPrimary)
                ) {
                    Text(
                        text = when (selectedPaymentMethod) {
                            "CREDIT" -> "SIMPAN HUTANG"
                            "QRIS" -> "LANJUTKAN BAYAR QRIS"
                            else -> "LANJUTKAN BAYAR TUNAI"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showPaymentSelectorDialog = false }, enabled = !isCheckingOut) {
                    Text("Batal")
                }
            }
        )
    }

    if (showCheckoutSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showCheckoutSuccessDialog = false },
            modifier = if (windowSize.isCompact) {
                Modifier.fillMaxWidth()
            } else {
                Modifier.width(if (windowSize.isExpanded) 540.dp else 480.dp)
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = AppColors.GreenPrimary)
                    Spacer(Modifier.width(AppSpacing.xs))
                    Text("✓ TRANSAKSI BERHASIL!", fontWeight = FontWeight.Bold, color = AppColors.GreenPrimary)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                    if (lastCheckoutData != null) {
                        val data = lastCheckoutData!!
                        Text("Ringkasan Item:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)

                        Surface(
                            shape = AppShapes.CardShape,
                            color = AppColors.SurfaceGray,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(AppSpacing.sm)) {
                                data.items.forEach { (itemName, qty) ->
                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        Text("${formatQuantityValue(qty)} × $itemName", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(AppSpacing.xs))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text("TOTAL", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            Text(formatRupiah(data.totalAmount), fontWeight = FontWeight.Bold, color = AppColors.GreenPrimary)
                        }
                        if (data.taxAmount != null && data.taxAmount > 0) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text("PPN", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                                Spacer(Modifier.weight(1f))
                                Text(formatRupiah(data.taxAmount), style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                            }
                        }

                        if (data.cashReceivedAmount != null) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text("Tunai Diterima", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                                Spacer(Modifier.weight(1f))
                                Text(formatRupiah(data.cashReceivedAmount), style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                            }
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text("Kembalian", fontWeight = FontWeight.Bold, color = AppColors.GreenPrimary)
                                Spacer(Modifier.weight(1f))
                                Text(formatRupiah(data.changeAmount ?: 0L), fontWeight = FontWeight.Bold, color = AppColors.GreenPrimary)
                            }
                        }

                        Text("Pembayaran: ${data.paymentMethodLabel}", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)

                        if (printerStatusMessage != null) {
                            val msg = printerStatusMessage ?: ""
                            Spacer(Modifier.height(AppSpacing.xs))
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (msg.contains("berhasil")) AppColors.GreenPrimary else AppColors.TextSecondary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (printerService != null && lastCheckoutData?.receiptData != null) {
                        androidx.compose.material3.OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isPrintingReceipt = true
                                    val printResult = printerService.printReceipt(lastCheckoutData!!.receiptData!!)
                                    isPrintingReceipt = false
                                    if (printResult.isSuccess) {
                                        printerStatusMessage = "Struk berhasil dicetak"
                                        Toast.makeText(context, "Struk berhasil dicetak", Toast.LENGTH_SHORT).show()
                                    } else {
                                        val errMsg = printResult.exceptionOrNull()?.localizedMessage ?: "Printer tidak terhubung"
                                        printerStatusMessage = "Struk belum tercetak ($errMsg)"
                                        Toast.makeText(context, "Gagal cetak: $errMsg", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = !isPrintingReceipt
                        ) {
                            Text(if (isPrintingReceipt) "Mencetak..." else "Cetak Struk", fontWeight = FontWeight.Bold)
                        }
                    }
                    Button(
                        onClick = { showCheckoutSuccessDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.GreenPrimary)
                    ) {
                        Text("SELESAI", fontWeight = FontWeight.Bold)
                    }
                }
            }
        )
    }

    if (showDigitalResultDialog && lastDigitalTransaction != null) {
        val tx = lastDigitalTransaction!!
        val isSuccess = tx.status == DigitalTransactionStatus.SUCCESS.name
        val isPending = tx.status == DigitalTransactionStatus.PENDING.name
        val isFailed = tx.status == DigitalTransactionStatus.FAILED.name
        val isUnknown = tx.status == DigitalTransactionStatus.UNKNOWN.name

        val statusColor = when (tx.status) {
            DigitalTransactionStatus.SUCCESS.name -> AppColors.GreenPrimary
            DigitalTransactionStatus.PENDING.name -> Color(0xFFE65100)
            DigitalTransactionStatus.FAILED.name -> AppColors.RedExpense
            else -> AppColors.TextSecondary
        }

        val statusLabel = when (tx.status) {
            DigitalTransactionStatus.SUCCESS.name -> "TRANSAKSI BERHASIL"
            DigitalTransactionStatus.PENDING.name -> "SEDANG DIPROSES (PENDING)"
            DigitalTransactionStatus.FAILED.name -> "TRANSAKSI GAGAL"
            DigitalTransactionStatus.UNKNOWN.name -> "STATUS TIDAK DIKETAHUI"
            else -> "STATUS: ${tx.status}"
        }

        AlertDialog(
            onDismissRequest = {
                if (!isCheckingDigitalStatus) showDigitalResultDialog = false
            },
            modifier = if (windowSize.isCompact) Modifier.fillMaxWidth() else Modifier.width(520.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Receipt,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "STATUS TRANSAKSI DIGITAL",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = statusColor.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = statusLabel,
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    Surface(
                        shape = AppShapes.CardShape,
                        color = AppColors.SurfaceGray,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(AppSpacing.sm),
                            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text("No Transaksi", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                                Spacer(Modifier.weight(1f))
                                Text(tx.transactionNumber ?: "-", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                            }
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text("Tujuan / No Meter", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                                Spacer(Modifier.weight(1f))
                                Text(tx.destinationNumber, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            }
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text("Provider / Produk", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                                Spacer(Modifier.weight(1f))
                                Text("${tx.providerId} (${tx.providerProductCode})", style = MaterialTheme.typography.bodySmall)
                            }
                            HorizontalDivider(color = PosPalette.Border.copy(alpha = 0.5f))
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text("Harga Jual", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                                Spacer(Modifier.weight(1f))
                                Text(formatRupiah(tx.sellingPrice), style = MaterialTheme.typography.bodySmall)
                            }
                            if (tx.adminFee > 0) {
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Text("Biaya Admin", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                                    Spacer(Modifier.weight(1f))
                                    Text(formatRupiah(tx.adminFee), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text("TOTAL DIBAYAR", fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                Text(formatRupiah(tx.sellingPrice + tx.adminFee), fontWeight = FontWeight.Bold, color = AppColors.GreenPrimary)
                            }
                        }
                    }

                    if (!tx.snToken.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AppColors.GreenLight,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("TOKEN / SN:", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = AppColors.GreenDark)
                                Text(tx.snToken!!, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AppColors.GreenPrimary)
                            }
                        }
                    }

                    if (!tx.failureReason.isNullOrBlank()) {
                        Text(
                            text = "Catatan Provider: ${tx.failureReason}",
                            style = MaterialTheme.typography.labelSmall,
                            color = AppColors.TextSecondary
                        )
                    }

                    Text(
                        text = "Catatan: Transaksi ini dieksekusi melalui mock adapter lokal v17. Tidak ada saldo riil yang didebit.",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.TextSecondary
                    )
                }
            },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isPending || isUnknown) {
                        OutlinedButton(
                            onClick = {
                                if (digitalTransactionRepository != null && !isCheckingDigitalStatus) {
                                    isCheckingDigitalStatus = true
                                    scope.launch {
                                        try {
                                            val refreshed = digitalTransactionRepository.checkStatus(tx.id)
                                            lastDigitalTransaction = refreshed
                                            Toast.makeText(context, "Status terbaru: ${refreshed.status}", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Gagal memeriksa status: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                        } finally {
                                            isCheckingDigitalStatus = false
                                        }
                                    }
                                }
                            },
                            enabled = !isCheckingDigitalStatus
                        ) {
                            Text(if (isCheckingDigitalStatus) "Memeriksa..." else "Cek Status", fontWeight = FontWeight.Bold)
                        }
                    }
                    Button(
                        onClick = { showDigitalResultDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.GreenPrimary)
                    ) {
                        Text("TUTUP", fontWeight = FontWeight.Bold)
                    }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            PosTopBar(
                title = "KASIR",
                showMenuAction = !windowSize.isCompact,
                onMenuClick = onOpenNavigation,
                onHistoryClick = { selectedTab = 1 },
                onSettingsClick = onNavigateToSettings,
                subtitle = resolvedProfile.businessType.displayName
            )
        },
        containerColor = PosPalette.ProductAreaBackground
    ) { padding ->
        if (selectedTab == 1) {
            PosSalesHistoryPane(
                modifier = Modifier.padding(padding),
                sales = filteredSales,
                allSalesCount = sales.size,
                customers = customers,
                terminology = terminology,
                selectedPeriod = salesHistoryPeriod,
                selectedPayment = salesHistoryPayment,
                onSelectPeriod = { salesHistoryPeriod = it },
                onSelectPayment = { salesHistoryPayment = it },
                onOpenSale = { selectedSaleForDetail = it },
                onBackToRegister = { selectedTab = 0 }
            )
            return@Scaffold
        }

        val productGridColumns = if (windowSize.isExpanded) {
            PosMetrics.ProductGridColumnsExpanded
        } else if (windowSize.isMedium) {
            PosMetrics.ProductGridColumnsMedium
        } else {
            PosMetrics.ProductGridColumnsCompact
        }

        val productPane: @Composable () -> Unit = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = PosMetrics.LeftPanePadding,
                        end = PosMetrics.LeftPanePadding,
                        top = PosMetrics.ContentPaddingTop,
                        bottom = PosMetrics.ContentPaddingBottom
                    )
            ) {
                if (posContextFamily == PosContextFamily.DIGITAL_OPERATOR) {
                    // DIGITAL_OPERATOR: Destination input, detected provider, category chips & denomination grid
                    OutlinedTextField(
                        value = digitalDestinationNumber,
                        onValueChange = { digitalDestinationNumber = it },
                        label = { Text("Nomor HP / No Meter PLN / ID Pelanggan") },
                        placeholder = { Text("Contoh: 08123456789 / 14123456789") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        trailingIcon = {
                            if (detectedDigitalProvider != null) {
                                Surface(
                                    shape = PosMetrics.ChipShape,
                                    color = PosPalette.Primary.copy(alpha = 0.15f),
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Text(
                                        text = detectedDigitalProvider,
                                        fontSize = PosType.ProductCode,
                                        fontWeight = FontWeight.Bold,
                                        color = PosPalette.Primary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        },
                        shape = PosMetrics.CtaShape,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(PosMetrics.SearchToTabsGap))

                    val digitalCategories = listOf("ALL", "PULSA", "DATA", "PLN", "EWALLET")
                    PosFilterChipRow(
                        labels = digitalCategories.map { if (it == "ALL") "Semua" else it },
                        selectedIndex = digitalCategories.indexOf(selectedDigitalCategory).coerceAtLeast(0),
                        onSelect = { index -> selectedDigitalCategory = digitalCategories[index] }
                    )

                    Spacer(Modifier.height(PosMetrics.CategoryToGridGap))

                    Surface(
                        shape = PosMetrics.CardShape,
                        color = AppColors.SurfaceGray,
                        border = BorderStroke(1.dp, PosPalette.Border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Receipt,
                                contentDescription = null,
                                tint = AppColors.TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Mode Simulasi: Mock Provider v17 (Transaksi lokal/pengujian)",
                                fontSize = PosType.ProductCode,
                                color = AppColors.TextSecondary
                            )
                        }
                    }

                    Spacer(Modifier.height(PosMetrics.RadiusSmall))

                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 150.dp),
                        horizontalArrangement = Arrangement.spacedBy(PosMetrics.ProductGridHorizontalGap),
                        verticalArrangement = Arrangement.spacedBy(PosMetrics.ProductGridVerticalGap),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredDigitalProducts, key = { it.code }) { item ->
                            val isSelected = selectedDigitalProduct?.code == item.code
                            PosDigitalDenomCard(
                                name = item.name,
                                category = item.category,
                                sellingPrice = item.sellingPrice,
                                purchasePrice = item.purchasePrice,
                                adminFee = item.adminFee,
                                description = item.description,
                                isSelected = isSelected,
                                onClick = {
                                    selectedDigitalProduct = if (isSelected) null else item
                                }
                            )
                        }
                    }
                } else if (posContextFamily == PosContextFamily.SERVICE_QUEUE) {
                    // SERVICE_QUEUE: Intake, service items, and spare parts workspace
                    PosSearchBar(
                        query = query,
                        onQueryChange = { query = it },
                        onScanClick = { showPosScannerDialog = true }
                    )

                    Spacer(Modifier.height(PosMetrics.SearchToTabsGap))

                    val serviceFilters = listOf("ALL", "SERVICE", "PARTS")
                    val serviceFilterLabels = listOf("Semua Item", "Layanan / Jasa", "Suku Cadang / Material")
                    PosFilterChipRow(
                        labels = serviceFilterLabels,
                        selectedIndex = serviceFilters.indexOf(serviceTypeFilter).coerceAtLeast(0),
                        onSelect = { index -> serviceTypeFilter = serviceFilters[index] }
                    )

                    Spacer(Modifier.height(PosMetrics.CategoryToGridGap))

                    Surface(
                        shape = PosMetrics.CardShape,
                        color = AppColors.SurfaceGray,
                        border = BorderStroke(1.dp, PosPalette.Border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PointOfSale,
                                contentDescription = null,
                                tint = AppColors.GreenPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Layanan & Pengerjaan: ${resolvedProfile.businessType.displayName} (Faktur Jasa Langsung)",
                                fontSize = PosType.ProductCode,
                                color = AppColors.TextSecondary
                            )
                        }
                    }

                    Spacer(Modifier.height(PosMetrics.RadiusSmall))

                    if (serviceFilteredProducts.isEmpty()) {
                        AppEmptyState(
                            icon = Icons.Default.Inventory2,
                            title = "Belum ada layanan atau suku cadang",
                            description = "Tambahkan layanan jasa atau barang fisik untuk ${resolvedProfile.businessType.displayName.lowercase()}.",
                            actionText = "+ Tambah ${terminology.productLabel}",
                            onActionClick = onNavigateToAddProduct,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(PosMetrics.RadiusSmall)
                        ) {
                            items(serviceFilteredProducts, key = { it.id }) { product ->
                                val lineIndex = cart.indexOfFirst { it.product.id == product.id }
                                val cartQuantity = if (lineIndex >= 0) cart[lineIndex].quantity else 0.0
                                val isService = product.itemType == ItemType.SERVICE.name
                                val isOutOfStock = !isService && product.stock <= 0.0

                                PosServiceQueueRow(
                                    product = product,
                                    productCode = product.barcode,
                                    cartQuantity = cartQuantity,
                                    isService = isService,
                                    isOutOfStock = isOutOfStock,
                                    stockLabel = terminology.stockLabel,
                                    onAddClick = { addProductToCart(product) },
                                    onIncrement = { addProductToCart(product) },
                                    onDecrement = {
                                        if (lineIndex >= 0) {
                                            val current = cart[lineIndex]
                                            if (current.quantity - 1.0 <= 0.0) {
                                                cart.removeAt(lineIndex)
                                            } else {
                                                cart[lineIndex] = current.copy(quantity = current.quantity - 1.0)
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                } else {
                    PosSearchBar(
                        query = query,
                        onQueryChange = { query = it },
                        onScanClick = { showPosScannerDialog = true }
                    )

                    Spacer(Modifier.height(PosMetrics.SearchToTabsGap))

                    PosCategoryRow(
                        categories = dbCategories,
                        selectedCategoryId = selectedCategoryId,
                        onSelect = { selectedCategoryId = it }
                    )

                    Spacer(Modifier.height(PosMetrics.CategoryToGridGap))

                    if (dbProducts.isEmpty()) {
                        AppEmptyState(
                            icon = Icons.Default.Inventory2,
                            title = "Belum ada ${terminology.productLabel.lowercase()} jualan",
                            description = "Tambahkan ${terminology.productLabel.lowercase()} untuk mulai berjualan",
                            actionText = "+ Tambah ${terminology.productLabel}",
                            onActionClick = onNavigateToAddProduct,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (posContextFamily == PosContextFamily.COUNTER_HYBRID) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(PosMetrics.RadiusSmall)
                        ) {
                            items(filteredProducts, key = { it.id }) { product ->
                                val lineIndex = cart.indexOfFirst { it.product.id == product.id }
                                val cartQuantity = if (lineIndex >= 0) cart[lineIndex].quantity else 0.0
                                val stockable = product.itemType == ItemType.PHYSICAL.name ||
                                        product.itemType == ItemType.FUEL.name
                                PosCounterHybridRow(
                                    product = product,
                                    productCode = product.barcode,
                                    cartQuantity = cartQuantity,
                                    isStockable = stockable,
                                    isOutOfStock = stockable && product.stock <= 0.0,
                                    stockLabel = terminology.stockLabel,
                                    onAddClick = { addProductToCart(product) },
                                    onIncrement = { addProductToCart(product) },
                                    onDecrement = {
                                        if (lineIndex >= 0) {
                                            val current = cart[lineIndex]
                                            if (current.quantity - 1.0 <= 0.0) {
                                                cart.removeAt(lineIndex)
                                            } else {
                                                cart[lineIndex] = current.copy(quantity = current.quantity - 1.0)
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 140.dp),
                            horizontalArrangement = Arrangement.spacedBy(PosMetrics.ProductGridHorizontalGap),
                            verticalArrangement = Arrangement.spacedBy(PosMetrics.ProductGridVerticalGap),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredProducts, key = { it.id }) { product ->
                                val lineIndex = cart.indexOfFirst { it.product.id == product.id }
                                val cartQuantity = if (lineIndex >= 0) cart[lineIndex].quantity else 0.0
                                val stockable = product.itemType == ItemType.PHYSICAL.name ||
                                        product.itemType == ItemType.FUEL.name
                                MeasuredProductCard(
                                    product = product,
                                    productCode = product.barcode,
                                    cartQuantity = cartQuantity,
                                    isStockable = stockable,
                                    isOutOfStock = stockable && product.stock <= 0.0,
                                    onAddClick = { addProductToCart(product) },
                                    onIncrement = { addProductToCart(product) },
                                    onDecrement = {
                                        if (lineIndex >= 0) {
                                            val current = cart[lineIndex]
                                            if (current.quantity - 1.0 <= 0.0) {
                                                cart.removeAt(lineIndex)
                                            } else {
                                                cart[lineIndex] = current.copy(quantity = current.quantity - 1.0)
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // ONE cart body. The tablet shows it permanently in the right pane, the phone shows the
        // exact same composables inside a dialog. Same customer card, same item rows, same quantity
        // control, same empty state, same calculations - only the container differs.
        // It is a ColumnScope receiver so the item list keeps the same weight(1f) fill in both.
        val cartBody: @Composable ColumnScope.() -> Unit = {
            // Customer card, 72dp
            PosCustomerCard(
                customer = selectedCustomerForCredit,
                customerLabel = terminology.customerLabel,
                onClick = { showCustomerPickerSheet = true }
            )

            Spacer(Modifier.height(PosMetrics.PrimaryColumnGap))

            if (posContextFamily == PosContextFamily.DIGITAL_OPERATOR) {
                if (selectedDigitalProduct == null) {
                    AppEmptyState(
                        icon = Icons.Default.ShoppingCart,
                        title = "Pilih Produk Digital",
                        description = "Masukkan nomor tujuan dan pilih nominal pulsa atau token PLN untuk diproses.",
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    val prod = selectedDigitalProduct!!
                    val totalToPay = prod.sellingPrice + prod.adminFee
                    val profitMargin = totalToPay - prod.purchasePrice
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(PosMetrics.CartItemGap)
                    ) {
                        Surface(
                            shape = PosMetrics.CardShape,
                            color = PosPalette.Surface,
                            border = BorderStroke(PosMetrics.ProductSelectedBorder, PosPalette.Primary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(PosMetrics.RadiusSmall),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = prod.name,
                                        fontSize = PosType.CartProduct,
                                        fontWeight = FontWeight.Bold,
                                        color = PosPalette.TextPrimary
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Hapus",
                                        tint = PosPalette.Destructive,
                                        modifier = Modifier
                                            .size(PosMetrics.ProductAddTouchTarget)
                                            .clickable { selectedDigitalProduct = null }
                                    )
                                }
                                Text(
                                    text = "Tujuan: ${digitalDestinationNumber.ifBlank { "(Belum diisi)" }}",
                                    fontSize = PosType.Helper,
                                    color = if (digitalDestinationNumber.isBlank()) PosPalette.Destructive else PosPalette.TextPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                                HorizontalDivider(color = PosPalette.Border.copy(alpha = 0.5f))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Harga Jual", fontSize = PosType.Helper, color = PosPalette.TextSecondary)
                                    Text(formatRupiah(prod.sellingPrice), fontSize = PosType.Helper, fontWeight = FontWeight.SemiBold)
                                }
                                if (prod.adminFee > 0) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Biaya Admin", fontSize = PosType.Helper, color = PosPalette.TextSecondary)
                                        Text(formatRupiah(prod.adminFee), fontSize = PosType.Helper, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Modal Provider", fontSize = PosType.Helper, color = PosPalette.TextSecondary)
                                    Text(formatRupiah(prod.purchasePrice), fontSize = PosType.Helper, color = PosPalette.TextSecondary)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Margin Keuntungan", fontSize = PosType.Helper, color = PosPalette.Primary, fontWeight = FontWeight.Bold)
                                    Text("+${formatRupiah(profitMargin)}", fontSize = PosType.Helper, color = PosPalette.Primary, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            } else if (cart.isEmpty()) {
                AppEmptyState(
                    icon = Icons.Default.ShoppingCart,
                    title = "Keranjang masih kosong",
                    description = "Pilih ${terminology.productLabel.lowercase()} di sebelah untuk memulai transaksi.",
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(PosMetrics.CartItemGap)
                ) {
                    items(cart.size) { index ->
                        val line = cart[index]
                        Column(verticalArrangement = Arrangement.spacedBy(PosMetrics.RadiusSmall)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = line.product.name,
                                        fontSize = PosType.CartProduct,
                                        fontWeight = FontWeight.SemiBold,
                                        color = PosPalette.TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = formatRupiah(line.product.sellingPrice),
                                        fontSize = PosType.CartPrice,
                                        color = PosPalette.TextSecondary
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Hapus",
                                    tint = PosPalette.Destructive,
                                    modifier = Modifier
                                        .size(PosMetrics.ProductAddTouchTarget)
                                        .clickable { cart.removeAt(index) }
                                )
                            }
                            PosQuantityControl(
                                quantity = line.quantity,
                                onIncrement = { addProductToCart(line.product) },
                                onDecrement = {
                                    if (line.quantity - 1.0 <= 0.0) {
                                        cart.removeAt(index)
                                    } else {
                                        cart[index] = line.copy(quantity = line.quantity - 1.0)
                                    }
                                },
                                enabled = line.product.itemType == ItemType.SERVICE.name ||
                                        line.product.itemType == ItemType.DIGITAL.name ||
                                        line.quantity < line.product.stock,
                                productName = line.product.name,
                                unit = line.product.unit,
                                maxQuantity = if (isStockableCartLine(line.product)) {
                                    line.product.stock
                                } else {
                                    null
                                },
                                onQuantityInput = if (isStockableCartLine(line.product)) {
                                    { precise ->
                                        if (precise <= 0.0) {
                                            cart.removeAt(index)
                                        } else if (precise <= line.product.stock) {
                                            cart[index] = line.copy(quantity = precise)
                                        }
                                    }
                                } else {
                                    null
                                }
                            )
                        }
                    }
                }
            }
        }

        val cartPane: @Composable () -> Unit = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = PosMetrics.RightPanePadding,
                        end = PosMetrics.RightPanePadding,
                        top = PosMetrics.ContentPaddingTop,
                        bottom = PosMetrics.PaymentCtaBottomMargin
                    )
            ) {
                // Cart header, 64dp
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(PosMetrics.CartHeaderHeight),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when (posContextFamily) {
                            PosContextFamily.DIGITAL_OPERATOR -> "Transaksi Digital"
                            PosContextFamily.SERVICE_QUEUE -> "Faktur Jasa & Servis"
                            else -> "Keranjang Belanja"
                        },
                        fontSize = PosType.SectionTitle,
                        fontWeight = FontWeight.SemiBold,
                        color = PosPalette.TextPrimary
                    )
                    if (posContextFamily == PosContextFamily.DIGITAL_OPERATOR) {
                        if (selectedDigitalProduct != null) {
                            PosCartClearAction(onClick = { selectedDigitalProduct = null })
                        }
                    } else if (cart.isNotEmpty()) {
                        PosCartClearAction(onClick = { showClearCartDialog = true })
                    }
                }

                cartBody()

                Spacer(Modifier.height(PosMetrics.PrimaryColumnGap))

                if (posContextFamily == PosContextFamily.DIGITAL_OPERATOR) {
                    val digitalTotal = (selectedDigitalProduct?.sellingPrice ?: 0L) + (selectedDigitalProduct?.adminFee ?: 0L)
                    PosCartSummary(
                        discountLabel = null,
                        discountValue = null,
                        subtotalLabel = if (selectedDigitalProduct?.adminFee ?: 0L > 0) "Biaya Admin" else null,
                        subtotalValue = if (selectedDigitalProduct?.adminFee ?: 0L > 0) formatRupiah(selectedDigitalProduct?.adminFee ?: 0L) else null,
                        totalLabel = "Total Tagihan",
                        totalValue = formatRupiah(digitalTotal)
                    )

                    Spacer(Modifier.height(PosMetrics.RadiusSmall))

                    PosPaymentCta(
                        label = if (isSubmittingDigital) "MEMPROSES..." else "LANJUT PEMBAYARAN",
                        enabled = selectedDigitalProduct != null && digitalDestinationNumber.isNotBlank() && !isSubmittingDigital,
                        onClick = { processStandaloneDigitalCheckout() }
                    )
                } else {
                    // 14. Summary, always in the same place regardless of the item count
                    PosCartSummary(
                        discountLabel = "Diskon",
                        discountValue = "-${formatRupiah(discountAmount)}",
                        subtotalLabel = if (taxSettings.enabled) "Total incl. PPN" else null,
                        subtotalValue = if (taxSettings.enabled) formatRupiah(grandTotal) else null,
                        totalLabel = "Total",
                        totalValue = formatRupiah(grandTotal)
                    )

                    OutlinedTextField(
                        value = discountInputText,
                        onValueChange = { discountInputText = it },
                        label = { Text("Diskon", fontSize = PosType.Helper) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        shape = PosMetrics.CtaShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(PosMetrics.SearchHeight)
                    )

                    Spacer(Modifier.height(PosMetrics.RadiusSmall))

                    // 15. Primary CTA
                    PosPaymentCta(
                        label = "LANJUT PEMBAYARAN",
                        enabled = cart.isNotEmpty() && !isCheckingOut,
                        onClick = {
                            if (hasMissingProviderDestination) {
                                Toast.makeText(context, "Isi nomor tujuan produk digital", Toast.LENGTH_SHORT).show()
                                return@PosPaymentCta
                            }
                            if (cart.any { isProvider(it.product) } && !isInternetAvailable()) {
                                Toast.makeText(
                                    context,
                                    "Transaksi produk digital membutuhkan internet. Sambungkan internet lalu coba lagi.",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@PosPaymentCta
                            }
                            showPaymentSelectorDialog = true
                        }
                    )
                }
            }
        }

        if (windowSize.isCompact) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Box(modifier = Modifier.weight(1f)) { productPane() }
                if (posContextFamily == PosContextFamily.DIGITAL_OPERATOR) {
                    if (selectedDigitalProduct != null) {
                        val digitalTotal = selectedDigitalProduct!!.sellingPrice + selectedDigitalProduct!!.adminFee
                        PosBottomCartBar(
                            itemCountLabel = "1 Transaksi Digital",
                            totalValue = formatRupiah(digitalTotal),
                            ctaLabel = if (isSubmittingDigital) "PROSES..." else "BAYAR",
                            ctaEnabled = digitalDestinationNumber.isNotBlank() && !isSubmittingDigital,
                            onBarClick = { showPhoneCartDialog = true },
                            onCtaClick = { processStandaloneDigitalCheckout() }
                        )
                    }
                } else if (cart.isNotEmpty()) {
                    PosBottomCartBar(
                        itemCountLabel = "$totalItemCount ${terminology.productLabel.lowercase()}",
                        totalValue = formatRupiah(grandTotal),
                        ctaLabel = "BAYAR",
                        ctaEnabled = !isCheckingOut,
                        onBarClick = { showPhoneCartDialog = true },
                        onCtaClick = { showPaymentSelectorDialog = true }
                    )
                }
            }
        } else {
            PosTabletSplit(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                productPane = productPane,
                cartPane = cartPane
            )
        }

        if (showPhoneCartDialog) {
            if (posContextFamily == PosContextFamily.DIGITAL_OPERATOR) {
                val digitalTotal = (selectedDigitalProduct?.sellingPrice ?: 0L) + (selectedDigitalProduct?.adminFee ?: 0L)
                PosPhoneCartDialog(
                    cartSummaryLabel = "Transaksi Digital",
                    discountValue = "-Rp 0",
                    subtotalLabel = null,
                    subtotalValue = null,
                    totalLabel = "Total Tagihan",
                    totalValue = formatRupiah(digitalTotal),
                    cartContent = { cartBody() },
                    onClearClick = { selectedDigitalProduct = null; showPhoneCartDialog = false },
                    ctaLabel = "LANJUT PEMBAYARAN",
                    ctaEnabled = selectedDigitalProduct != null && digitalDestinationNumber.isNotBlank() && !isSubmittingDigital,
                    onCtaClick = {
                        showPhoneCartDialog = false
                        processStandaloneDigitalCheckout()
                    },
                    onDismiss = { showPhoneCartDialog = false }
                )
            } else {
                PosPhoneCartDialog(
                    cartSummaryLabel = "Keranjang Belanja",
                    discountValue = "-${formatRupiah(discountAmount)}",
                    subtotalLabel = if (taxSettings.enabled) "Total incl. PPN" else null,
                    subtotalValue = if (taxSettings.enabled) formatRupiah(grandTotal) else null,
                    totalLabel = "Total",
                    totalValue = formatRupiah(grandTotal),
                    cartContent = { cartBody() },
                    onClearClick = { showClearCartDialog = true },
                    ctaLabel = "LANJUT PEMBAYARAN",
                    ctaEnabled = cart.isNotEmpty() && !isCheckingOut,
                    onCtaClick = {
                        if (hasMissingProviderDestination) {
                            Toast.makeText(context, "Isi nomor tujuan produk digital", Toast.LENGTH_SHORT).show()
                            return@PosPhoneCartDialog
                        }
                        showPhoneCartDialog = false
                        showPaymentSelectorDialog = true
                    },
                    onDismiss = { showPhoneCartDialog = false }
                )
            }
        }
    }
}
