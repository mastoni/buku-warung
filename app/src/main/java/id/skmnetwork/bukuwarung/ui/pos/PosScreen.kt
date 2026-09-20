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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
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
import id.skmnetwork.bukuwarung.util.formatRupiah
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
    onNavigateToSettings: () -> Unit = {}
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

    var query by remember { mutableStateOf("") }
    var salesHistoryQuery by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) } // null = Semua

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cart = remember { mutableStateListOf<CartLine>() }
    var isCartExpanded by remember { mutableStateOf(false) }

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
                Toast.makeText(context, "${terminology.stockLabel} ${product.name} hanya tersisa ${product.stock.toInt()}", Toast.LENGTH_SHORT).show()
            }
        }
    }

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
    var showCashPaymentDialog by remember { mutableStateOf(false) }
    var showQrisPaymentDialog by remember { mutableStateOf(false) }
    var showCheckoutSuccessDialog by remember { mutableStateOf(false) }

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

    val totalItemCount = cart.sumOf { it.quantity }.toInt()
    val totalPrice = cart.sumOf { (it.product.sellingPrice) * it.quantity.toLong() }

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
                    lineSubtotal = (cartLine.product.sellingPrice * cartLine.quantity.toLong()),
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
            onDismiss = { selectedSaleForReturn = null },
            onReturnSuccess = {
                val targetSale = selectedSaleForReturn
                selectedSaleForReturn = null
                // Re-open detail dialog so merchant sees updated return history immediately
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

    if (showPaymentSelectorDialog) {
        AlertDialog(
            onDismissRequest = { if (!isCheckingOut) showPaymentSelectorDialog = false },
            title = {
                Text("RINGKASAN & PEMBAYARAN", fontWeight = FontWeight.Bold)
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
                                .height(110.dp)
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
                                        Text("${qty.toInt()} × ${formatRupiah(prod.sellingPrice)}", color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                                    }
                                    Text(formatRupiah(prod.sellingPrice * qty.toLong()), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
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
                                        Text("${qty.toInt()} × $itemName", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
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
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(AppColors.GreenPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PointOfSale,
                                contentDescription = "Kasir Logo",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(Modifier.width(10.dp))

                        Column(
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Text(
                                text = if (selectedTab == 0) "${terminology.transactionLabel} (Kasir)" else "Riwayat ${terminology.transactionLabel}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.5.sp,
                                color = AppColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (selectedTab == 0) "Catat ${terminology.transactionLabel.lowercase()} & kasir cepat" else "Daftar struk & riwayat ${terminology.transactionLabel.lowercase()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = AppColors.TextSecondary,
                                fontSize = 11.5.sp
                            )
                        }
                    }
                },
                actions = {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = AppColors.GreenLight,
                        border = BorderStroke(1.dp, Color(0xFFCCE8D7)),
                        modifier = Modifier.padding(end = AppSpacing.sm)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(AppColors.GreenPrimary)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = if (selectedTab == 0) "Mode Kasir" else "Riwayat",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppColors.GreenDark
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        },
        containerColor = Color.White
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Segmented Capsule Navigation (Kasir vs Riwayat Penjualan)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFF1F4F2),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.lg, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val tab0Active = selectedTab == 0
                    Surface(
                        shape = RoundedCornerShape(11.dp),
                        color = if (tab0Active) AppColors.GreenPrimary else Color.Transparent,
                        shadowElevation = if (tab0Active) 1.dp else 0.dp,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedTab = 0 }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.ShoppingCart,
                                null,
                                tint = if (tab0Active) Color.White else AppColors.TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Kasir",
                                color = if (tab0Active) Color.White else AppColors.TextSecondary,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    val tab1Active = selectedTab == 1
                    Surface(
                        shape = RoundedCornerShape(11.dp),
                        color = if (tab1Active) AppColors.GreenPrimary else Color.Transparent,
                        shadowElevation = if (tab1Active) 1.dp else 0.dp,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedTab = 1 }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.History,
                                null,
                                tint = if (tab1Active) Color.White else AppColors.TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Riwayat ${terminology.transactionLabel}",
                                color = if (tab1Active) Color.White else AppColors.TextSecondary,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(AppSpacing.xs))

            if (selectedTab == 0) {
                // TAB 0: KASIR / JUALAN BARU
                Column(Modifier.padding(horizontal = AppSpacing.lg)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = "Cari ${terminology.productLabel.lowercase()} atau barcode...",
                            modifier = Modifier.weight(1f)
                        )
                        if (userSettings.showBarcode) {
                            Spacer(Modifier.width(AppSpacing.sm))
                            Surface(
                                shape = AppShapes.TextFieldShape,
                                color = AppColors.GreenLight,
                                border = BorderStroke(1.dp, Color(0xFFCCE8D7)),
                                modifier = Modifier
                                    .size(52.dp)
                                    .clickable { showPosScannerDialog = true }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.QrCodeScanner,
                                        contentDescription = "Pindai Barcode",
                                        tint = AppColors.GreenPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (dbCategories.isNotEmpty()) {
                        Spacer(Modifier.height(AppSpacing.sm))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            item {
                                val isSelected = selectedCategoryId == null
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (isSelected) AppColors.GreenPrimary else Color.White,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) AppColors.GreenPrimary else Color(0xFFE0E5E2)
                                    ),
                                    modifier = Modifier.clickable { selectedCategoryId = null }
                                ) {
                                    Text(
                                        text = "Semua",
                                        color = if (isSelected) Color.White else AppColors.TextSecondary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 12.5.sp,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                    )
                                }
                            }
                            items(dbCategories, key = { it.id }) { category ->
                                val isSelected = selectedCategoryId == category.id
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (isSelected) AppColors.GreenPrimary else Color.White,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) AppColors.GreenPrimary else Color(0xFFE0E5E2)
                                    ),
                                    modifier = Modifier.clickable { selectedCategoryId = category.id }
                                ) {
                                    Text(
                                        text = category.name,
                                        color = if (isSelected) Color.White else AppColors.TextSecondary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 12.5.sp,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(AppSpacing.sm))

                if (dbProducts.isEmpty()) {
                    AppEmptyState(
                        icon = Icons.Default.Inventory2,
                        title = "Belum ada ${terminology.productLabel.lowercase()} jualan",
                        description = "Tambahkan ${terminology.productLabel.lowercase()} di menu ${terminology.productLabel} & ${terminology.stockLabel} untuk mulai berjualan",
                        actionText = "+ Tambah ${terminology.productLabel}",
                        onActionClick = onNavigateToAddProduct,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    // 2-Column Product Grid
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = AppSpacing.lg)
                    ) {
                        items(filteredProducts.chunked(2)) { rowProducts ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                rowProducts.forEach { product ->
                                    val currentCartQty = cart.filter { it.product.id == product.id }.sumOf { it.quantity }
                                    val isServiceOrDigital = product.itemType == ItemType.SERVICE.name || product.itemType == ItemType.DIGITAL.name
                                    val isOutofStock = if (isServiceOrDigital) false else product.stock <= 0.0
                                    val isInCart = currentCartQty > 0.0

                                    Surface(
                                        onClick = { addProductToCart(product) },
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (isInCart) AppColors.GreenLight else Color.White,
                                        border = BorderStroke(
                                            if (isInCart) 1.5.dp else 1.dp,
                                            if (isInCart) AppColors.GreenPrimary else Color(0xFFE8ECE9)
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(AppSpacing.sm)
                                        ) {
                                            if (userSettings.showProductImage) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(80.dp)
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(
                                                            if (isOutofStock) Color(0xFFFFEBEE)
                                                             else if (isInCart) Color.White
                                                            else Color(0xFFF4F8F5)
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (!product.imageUri.isNullOrEmpty()) {
                                                        ProductImageThumbnail(
                                                            imageUri = product.imageUri,
                                                            contentScale = ContentScale.Crop,
                                                            modifier = Modifier.fillMaxSize()
                                                        )
                                                    } else {
                                                        ProductImageThumbnail(
                                                            imageUri = null,
                                                            modifier = Modifier.size(30.dp),
                                                            tint = if (isOutofStock) AppColors.RedExpense else AppColors.GreenPrimary
                                                        )
                                                    }
                                                    if (isInCart) {
                                                        Surface(
                                                            shape = RoundedCornerShape(8.dp),
                                                            color = AppColors.GreenPrimary,
                                                            modifier = Modifier
                                                                .align(Alignment.TopEnd)
                                                                .padding(4.dp)
                                                        ) {
                                                            Text(
                                                                text = "${currentCartQty.toInt()}x",
                                                                color = Color.White,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 11.sp,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                }

                                                Spacer(Modifier.height(8.dp))
                                            }

                                            Text(
                                                text = product.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.5.sp,
                                                color = AppColors.TextPrimary,
                                                minLines = 2,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )

                                            Spacer(Modifier.height(4.dp))

                                            Text(
                                                text = formatRupiah(product.sellingPrice),
                                                color = AppColors.GreenPrimary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.5.sp
                                            )

                                            if (userSettings.showStock) {
                                                Spacer(Modifier.height(2.dp))
                                                when (product.itemType) {
                                                    ItemType.SERVICE.name -> {
                                                        Surface(
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = Color(0xFFE8F3FF)
                                                        ) {
                                                            Text(
                                                                text = terminology.serviceLabel,
                                                                color = Color(0xFF096DD9),
                                                                fontSize = 10.5.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                            )
                                                        }
                                                    }
                                                    ItemType.DIGITAL.name -> {
                                                        Surface(
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = Color(0xFFF6FFED)
                                                        ) {
                                                            Text(
                                                                text = when (product.fulfillmentMode) {
                                                                    FulfillmentMode.PROVIDER.name -> "Digital • Provider"
                                                                    else -> "Digital • Manual"
                                                                },
                                                                color = Color(0xFF389E0D),
                                                                fontSize = 10.5.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                            )
                                                        }
                                                    }
                                                    else -> {
                                                        if (isOutofStock) {
                                                            Surface(
                                                                shape = RoundedCornerShape(6.dp),
                                                                color = Color(0xFFFFEBEE)
                                                            ) {
                                                                Text(
                                                                    text = "Habis",
                                                                    color = AppColors.RedExpense,
                                                                    fontSize = 10.5.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                                )
                                                            }
                                                        } else if (product.stock <= product.minimumStock) {
                                                            Text(
                                                                text = "Sisa: ${product.stock.toInt()} ${product.unit}",
                                                                color = Color(0xFFD97706),
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.SemiBold
                                                            )
                                                        } else {
                                                            Text(
                                                                text = "${terminology.stockLabel}: ${product.stock.toInt()} ${product.unit}",
                                                                color = AppColors.TextSecondary,
                                                                fontSize = 11.sp
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                if (rowProducts.size == 1) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                if (cart.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFCCE8D7)),
                        shadowElevation = 8.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { isCartExpanded = !isCartExpanded }
                                    .padding(vertical = 2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(AppColors.GreenLight),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.ShoppingCart,
                                        contentDescription = null,
                                        tint = AppColors.GreenPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Spacer(Modifier.width(10.dp))

                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = "$totalItemCount ${terminology.productLabel.lowercase()} dipilih",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.5.sp,
                                        color = AppColors.TextSecondary
                                    )
                                    Text(
                                        text = formatRupiah(totalPrice),
                                        fontSize = 16.5.sp,
                                        color = AppColors.GreenPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFF1F5F2)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (isCartExpanded) "Tutup" else "Detail",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = AppColors.TextSecondary
                                        )
                                        Spacer(Modifier.width(2.dp))
                                        Icon(
                                            imageVector = if (isCartExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                            contentDescription = "Detail Keranjang",
                                            tint = AppColors.TextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            AnimatedVisibility(visible = isCartExpanded) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                                    modifier = Modifier
                                        .padding(top = AppSpacing.sm, bottom = AppSpacing.xs)
                                        .fillMaxWidth()
                                ) {
                                    cart.forEach { cartLine ->
                                        val prod = cartLine.product
                                        val lineIndex = cart.indexOfFirst { it.lineId == cartLine.lineId }
                                        val isServiceOrDigitalProd = prod.itemType == ItemType.SERVICE.name || prod.itemType == ItemType.DIGITAL.name
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = Color(0xFFF8FAF9),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            prod.name,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 13.sp,
                                                            color = AppColors.TextPrimary,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Text(
                                                            "${formatRupiah(prod.sellingPrice)} × ${cartLine.quantity.toInt()} = ${formatRupiah(prod.sellingPrice * cartLine.quantity.toLong())}",
                                                            color = AppColors.TextSecondary,
                                                            fontSize = 11.sp
                                                        )
                                                        if (isProvider(prod)) {
                                                            Text(
                                                                "Nomor tujuan: ${cartLine.destinationNumber.orEmpty().ifEmpty { "Belum diisi" }}",
                                                                color = AppColors.TextSecondary,
                                                                fontSize = 10.5.sp
                                                            )
                                                        }
                                                    }
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Surface(
                                                            shape = CircleShape,
                                                            color = Color(0xFFFFEBEE),
                                                            modifier = Modifier
                                                                .size(28.dp)
                                                                .clickable {
                                                                    val newQty = cartLine.quantity - 1.0
                                                                    if (newQty <= 0) {
                                                                        cart.removeIf { it.lineId == cartLine.lineId }
                                                                    } else if (lineIndex >= 0) {
                                                                        cart[lineIndex] = cartLine.copy(quantity = newQty)
                                                                    }
                                                                }
                                                        ) {
                                                            Box(contentAlignment = Alignment.Center) {
                                                                Icon(
                                                                    Icons.Default.Remove,
                                                                    "Kurangi",
                                                                    tint = AppColors.RedExpense,
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                            }
                                                        }
                                                        Text(
                                                            "${cartLine.quantity.toInt()}",
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 13.sp,
                                                            modifier = Modifier.padding(horizontal = 8.dp)
                                                        )
                                                        Surface(
                                                            shape = CircleShape,
                                                            color = AppColors.GreenLight,
                                                            modifier = Modifier
                                                                .size(28.dp)
                                                                .clickable {
                                                                    if (isServiceOrDigitalProd || cartLine.quantity + 1.0 <= prod.stock) {
                                                                        if (lineIndex >= 0) {
                                                                            cart[lineIndex] = cartLine.copy(quantity = cartLine.quantity + 1.0)
                                                                        }
                                                                    } else {
                                                                        Toast.makeText(context, "${terminology.stockLabel} ${prod.name} hanya tersisa ${prod.stock.toInt()}", Toast.LENGTH_SHORT).show()
                                                                    }
                                                                }
                                                        ) {
                                                            Box(contentAlignment = Alignment.Center) {
                                                                Icon(
                                                                    Icons.Default.Add,
                                                                    "Tambah",
                                                                    tint = AppColors.GreenPrimary,
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                                if (isProvider(prod)) {
                                                    OutlinedTextField(
                                                        value = cartLine.destinationNumber.orEmpty(),
                                                        onValueChange = { if (lineIndex >= 0) updateCartDestination(lineIndex, it) },
                                                        label = { Text("Nomor tujuan", fontSize = 10.sp) },
                                                        singleLine = true,
                                                        modifier = Modifier.fillMaxWidth(),
                                                        enabled = !isCheckingOut
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(AppSpacing.sm))

                            Button(
                                onClick = {
                                    if (hasMissingProviderDestination) {
                                        Toast.makeText(context, "Isi nomor tujuan produk digital", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    if (cart.any { isProvider(it.product) } && !isInternetAvailable()) {
                                        Toast.makeText(context, "Transaksi produk digital membutuhkan internet. Sambungkan internet lalu coba lagi.", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    showPaymentSelectorDialog = true
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AppColors.GreenPrimary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        "BAYAR / PROSES (${totalItemCount})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Spacer(Modifier.width(AppSpacing.sm))
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.height(AppSpacing.xs))
                }
            } else {
                // TAB 1: RIWAYAT PENJUALAN (SALES HISTORY)
                Column(Modifier.padding(horizontal = AppSpacing.lg)) {
                    AppTextField(
                        value = salesHistoryQuery,
                        onValueChange = { salesHistoryQuery = it },
                        label = "Cari nomor struk atau nama ${terminology.customerLabel.lowercase()}...",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(AppSpacing.sm))

                if (sales.isEmpty()) {
                    AppEmptyState(
                        icon = Icons.Default.Receipt,
                        title = "Belum ada riwayat ${terminology.transactionLabel.lowercase()}",
                        description = "Transaksi kasir yang selesai akan tercatat otomatis di sini",
                        actionText = "+ ${terminology.transactionLabel} Baru",
                        onActionClick = { selectedTab = 0 },
                        modifier = Modifier.weight(1f)
                    )
                } else if (filteredSales.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Tidak ada transaksi ${terminology.transactionLabel.lowercase()} yang cocok", color = AppColors.TextSecondary)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = AppSpacing.lg)
                    ) {
                        items(filteredSales, key = { it.id }) { sale ->
                            val custName = customers.find { it.id == sale.customerId }?.name ?: "${terminology.customerLabel} Umum"
                            val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.forLanguageTag("id-ID")).format(Date(sale.transactionDate))
                            val isCredit = sale.paymentMethod == "CREDIT"
                            val paymentLabel = when (sale.paymentMethod) {
                                "CREDIT" -> "HUTANG"
                                "QRIS" -> "QRIS"
                                else -> "TUNAI"
                            }

                            Surface(
                                onClick = { selectedSaleForDetail = sale },
                                shape = RoundedCornerShape(14.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, Color(0xFFE8ECE9)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(AppSpacing.md),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isCredit) Color(0xFFFFEBEE) else AppColors.GreenLight
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Receipt,
                                            null,
                                            tint = if (isCredit) AppColors.RedExpense else AppColors.GreenPrimary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(AppSpacing.md))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                sale.transactionNumber,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.5.sp,
                                                color = AppColors.TextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (isCredit) Color(0xFFFFEBEE) else AppColors.GreenLight
                                            ) {
                                                Text(
                                                    text = paymentLabel,
                                                    color = if (isCredit) AppColors.RedExpense else AppColors.GreenDark,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            custName,
                                            color = AppColors.TextPrimary,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            dateStr,
                                            color = AppColors.TextSecondary,
                                            fontSize = 11.sp,
                                            maxLines = 1
                                        )
                                    }
                                    Spacer(Modifier.width(AppSpacing.sm))
                                    Text(
                                        formatRupiah(sale.totalAmount),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (isCredit) AppColors.RedExpense else AppColors.GreenPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


