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
import id.skmnetwork.bukuwarung.domain.business.ResolvedBusinessProfile
import id.skmnetwork.bukuwarung.domain.discount.DiscountCalculator
import id.skmnetwork.bukuwarung.domain.discount.DiscountInput
import id.skmnetwork.bukuwarung.domain.discount.DiscountType
import id.skmnetwork.bukuwarung.ui.components.AppCard
import id.skmnetwork.bukuwarung.ui.components.AppEmptyState
import id.skmnetwork.bukuwarung.ui.components.AppTextField
import id.skmnetwork.bukuwarung.ui.components.CameraBarcodeScannerDialog
import id.skmnetwork.bukuwarung.ui.components.ProductImageThumbnail
import id.skmnetwork.bukuwarung.ui.customer.CustomerViewModel
import id.skmnetwork.bukuwarung.ui.navigation.CheckoutSuccessData
import id.skmnetwork.bukuwarung.ui.product.ProductViewModel
import id.skmnetwork.bukuwarung.ui.theme.AppColors
import id.skmnetwork.bukuwarung.ui.theme.AppShapes
import id.skmnetwork.bukuwarung.ui.theme.AppSpacing
import id.skmnetwork.bukuwarung.ui.theme.rememberAppWindowSize
import id.skmnetwork.bukuwarung.util.formatQuantityValue
import id.skmnetwork.bukuwarung.util.formatRupiah
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

    val dbProducts by viewModel.products.collectAsStateWithLifecycle()
    val dbCategories by viewModel.categories.collectAsStateWithLifecycle()
    val customers by customerViewModel.customers.collectAsStateWithLifecycle()
    val sales by viewModel.sales.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(0) } // 0: Kasir, 1: Riwayat Penjualan

    // Step 3 section 5 - product mode. 0: Produk Satuan, 1: Paket Hemat.
    var productMode by remember { mutableStateOf(0) }

    var query by remember { mutableStateOf("") }
    var salesHistoryQuery by remember { mutableStateOf("") }
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

    val filteredProducts = dbProducts.filter { product ->
        val matchesQuery = product.name.contains(query, ignoreCase = true) ||
                (product.barcode != null && product.barcode.contains(query, ignoreCase = true))
        val matchesCategory = selectedCategoryId == null || product.categoryId == selectedCategoryId
        matchesQuery && matchesCategory
    }

    val filteredSales = sales.filter { sale ->
        val customer = customers.find { it.id == sale.customerId }
        val matchesNumber = sale.transactionNumber.contains(salesHistoryQuery, ignoreCase = true)
        val matchesCust = customer?.name?.contains(salesHistoryQuery, ignoreCase = true) == true
        matchesNumber || matchesCust
    }

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
                        color = Color(0xFFF9FBFA),
                        border = BorderStroke(1.dp, Color(0xFFE2EBE5)),
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
                                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp), color = Color(0xFFE2EBE5))
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
                            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp), color = Color(0xFFE2EBE5))
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

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        availablePaymentMethods.forEach { (methodCode, methodLabel) ->
                            Surface(
                                shape = AppShapes.ChipShape,
                                color = if (selectedPaymentMethod == methodCode) AppColors.GreenPrimary else AppColors.SurfaceGray,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedPaymentMethod = methodCode }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = AppSpacing.sm),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = methodLabel,
                                        color = if (selectedPaymentMethod == methodCode) Color.White else AppColors.TextSecondary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    if (selectedPaymentMethod == "CREDIT") {
                        Surface(
                            shape = AppShapes.CardShape,
                            color = if (selectedCustomerForCredit == null) Color(0xFFFFE8E8) else AppColors.GreenLight,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showCustomerPickerSheet = true }
                        ) {
                            Row(
                                Modifier.padding(AppSpacing.md),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.People, null, tint = AppColors.GreenPrimary)
                                Spacer(Modifier.width(AppSpacing.sm))
                                Text(
                                    text = selectedCustomerForCredit?.name ?: "Pilih ${terminology.customerLabel} *",
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedCustomerForCredit == null) AppColors.RedExpense else AppColors.TextPrimary,
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

    Scaffold(
        topBar = {
            PosTopBar(
                title = "KASIR",
                // The hamburger is the drawer's trigger. It is shown only where the drawer is the
                // app navigation. On a phone the bottom navigation bar is permanently visible and is
                // the app navigation, so the header shows page identity and POS actions only - no
                // second navigation affordance competing with it.
                showMenuAction = !windowSize.isCompact,
                onMenuClick = onOpenNavigation,
                onHistoryClick = { selectedTab = 1 },
                onAddProductClick = onNavigateToAddProduct,
                onSettingsClick = onNavigateToSettings,
                onRefreshClick = {
                    // The product list is a live Room flow, so it is always current. Refresh means
                    // "start the register again": clear the search and the category filter.
                    query = ""
                    selectedCategoryId = null
                    productMode = 0
                }
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
                // The product area is a measured stack - search 54 -> 12 -> tabs 48 -> 8 ->
                // category 42 -> 16 -> grid - so the gaps are explicit rather than one uniform
                // spacing applied between all four slots.
                PosSearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    onScanClick = { showPosScannerDialog = true }
                )

                Spacer(Modifier.height(PosMetrics.SearchToTabsGap))

                PosModeTabs(
                    labels = listOf("Produk Satuan", "Paket Hemat"),
                    selectedIndex = productMode,
                    onSelect = { productMode = it }
                )

                Spacer(Modifier.height(PosMetrics.TabsToCategoryGap))

                PosCategoryRow(
                    categories = dbCategories,
                    selectedCategoryId = selectedCategoryId,
                    onSelect = { selectedCategoryId = it }
                )

                Spacer(Modifier.height(PosMetrics.CategoryToGridGap))

                if (productMode == 1) {
                    // Section 5 requires the second mode tab. There is no bundle/paket data model in
                    // this release, so it is reported honestly rather than faked. See the gate report.
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Belum ada Paket Hemat",
                            fontSize = PosType.SectionTitle,
                            fontWeight = FontWeight.SemiBold,
                            color = PosPalette.TextPrimary
                        )
                    }
                } else if (dbProducts.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(PosMetrics.RadiusSmall)
                        ) {
                            Text(
                                text = "Belum ada ${terminology.productLabel.lowercase()} jualan",
                                fontSize = PosType.SectionTitle,
                                fontWeight = FontWeight.SemiBold,
                                color = PosPalette.TextPrimary
                            )
                            Text(
                                text = "Tambahkan ${terminology.productLabel.lowercase()} untuk mulai berjualan",
                                fontSize = PosType.Helper,
                                color = PosPalette.TextSecondary
                            )
                            PosPaymentCta(
                                label = "+ Tambah ${terminology.productLabel}",
                                enabled = true,
                                onClick = onNavigateToAddProduct
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(productGridColumns),
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

            // Cart items
            if (cart.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Keranjang masih kosong",
                        fontSize = PosType.Helper,
                        color = PosPalette.TextSecondary
                    )
                }
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
                                    tint = PosPalette.TextSecondary,
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
                                // Step 5: precise entry for stockable goods, which is where a
                                // merchant sells by weight or volume and the stepper alone would
                                // mean pressing "+" a dozen times for 1.25.
                                productName = line.product.name,
                                unit = line.product.unit,
                                maxQuantity = if (isStockableCartLine(line.product)) {
                                    line.product.stock
                                } else {
                                    null
                                },
                                onQuantityInput = if (isStockableCartLine(line.product)) {
                                    { precise ->
                                        // The same rules the stepper obeys: an emptied line is
                                        // removed, and a value above the remaining stock is
                                        // refused. No rounding, no truncation.
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
                        text = "Keranjang Belanja",
                        fontSize = PosType.SectionTitle,
                        fontWeight = FontWeight.SemiBold,
                        color = PosPalette.TextPrimary
                    )
                    if (cart.isNotEmpty()) {
                        PosCartClearAction(onClick = { showClearCartDialog = true })
                    }
                }

                cartBody()

                Spacer(Modifier.height(PosMetrics.PrimaryColumnGap))

                // 14. Summary, always in the same place regardless of the item count
                PosCartSummary(
                    discountLabel = "Diskon",
                    discountValue = "-${formatRupiah(discountAmount)}",
                    subtotalLabel = if (taxSettings.enabled) "Total incl. PPN" else null,
                    subtotalValue = if (taxSettings.enabled) formatRupiah(grandTotal) else null,
                    totalLabel = "Total",
                    totalValue = formatRupiah(grandTotal)
                )

                // Discount stays editable; only the visible sizing follows the contract.
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

        if (windowSize.isCompact) {
            // PHONE - no permanent cart panel. The bottom bar carries the count, the total and the
            // same CTA, and opens the same cart body the tablet shows permanently.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Box(modifier = Modifier.weight(1f)) { productPane() }
                if (cart.isNotEmpty()) {
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
            // TABLET - 66.6 / 33.4 split over the full screen width.
            PosTabletSplit(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                productPane = productPane,
                cartPane = cartPane
            )
        }

        // PHONE cart detail: the same cart body the tablet shows permanently, hosted on demand.
        // It lives here rather than after the Scaffold so it shares the cartBody composable.
        if (showPhoneCartDialog) {
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
