package id.skmnetwork.bukuwarung.ui.pos

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.skmnetwork.bukuwarung.data.preferences.UserSettings
import id.skmnetwork.bukuwarung.data.local.entity.CustomerEntity
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
import kotlinx.coroutines.launch

import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ShoppingCart
import id.skmnetwork.bukuwarung.data.local.entity.SaleTransactionEntity
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
    onNavigateToAddProduct: () -> Unit = {}
) {
    val dbProducts by viewModel.products.collectAsStateWithLifecycle()
    val dbCategories by viewModel.categories.collectAsStateWithLifecycle()
    val customers by customerViewModel.customers.collectAsStateWithLifecycle()
    val sales by viewModel.sales.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(0) } // 0: Kasir, 1: Riwayat Penjualan

    var query by remember { mutableStateOf("") }
    var salesHistoryQuery by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) } // null = Semua

    val cart = remember { mutableStateMapOf<Long, Double>() }
    var isCartExpanded by remember { mutableStateOf(false) }

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

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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

    val totalItemCount = cart.values.sum().toInt()
    val totalPrice = cart.entries.sumOf { (productId, qty) ->
        val prod = dbProducts.find { it.id == productId }
        (prod?.sellingPrice ?: 0L) * qty.toLong()
    }

    val cartSummaryList = cart.entries.mapNotNull { (prodId, qty) ->
        val prod = dbProducts.find { it.id == prodId }
        if (prod != null) Pair(prod.name, qty) else null
    }

    if (selectedSaleForDetail != null) {
        SaleDetailDialog(
            sale = selectedSaleForDetail!!,
            customers = customers,
            viewModel = viewModel,
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
                        if (matchedProduct.stock <= 0.0) {
                            Toast.makeText(context, "Stok ${matchedProduct.name} habis (0)", Toast.LENGTH_SHORT).show()
                        } else {
                            val currentQty = cart[matchedProduct.id] ?: 0.0
                            if (currentQty + 1.0 <= matchedProduct.stock) {
                                cart[matchedProduct.id] = currentQty + 1.0
                                Toast.makeText(context, "+1 ${matchedProduct.name}", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Stok ${matchedProduct.name} hanya tersisa ${matchedProduct.stock.toInt()}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        Toast.makeText(context, "Produk barcode $cleanBarcode tidak ditemukan", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    if (showCustomerPickerSheet) {
        AlertDialog(
            onDismissRequest = { showCustomerPickerSheet = false },
            title = { Text("Pilih Pelanggan Hutang", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                    modifier = Modifier.height(260.dp)
                ) {
                    if (customers.isEmpty()) {
                        Text("Belum ada pelanggan. Tambahkan pelanggan terlebih dahulu.", color = AppColors.TextSecondary)
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
            totalPrice = totalPrice,
            isCheckingOut = isCheckingOut,
            cashReceivedEnabled = userSettings.cashReceivedEnabled,
            onDismiss = { showCashPaymentDialog = false },
            onConfirmCashPayment = { cashReceived, change ->
                if (isCheckingOut) return@CashPaymentDialog
                isCheckingOut = true
                viewModel.checkoutCart(
                    cartItems = cart.toMap(),
                    paymentMethod = "CASH",
                    onSuccess = { saleId ->
                        scope.launch {
                            val receiptData = viewModel.getReceiptData(saleId, userSettings, cashReceived)
                            isCheckingOut = false
                            showCashPaymentDialog = false
                            showPaymentSelectorDialog = false
                            lastCheckoutData = CheckoutSuccessData(
                                items = cartSummaryList,
                                totalAmount = totalPrice,
                                paymentMethodLabel = "Tunai (Cash)",
                                cashReceivedAmount = cashReceived,
                                changeAmount = change,
                                saleId = saleId,
                                receiptData = receiptData
                            )
                            cart.clear()
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
            totalPrice = totalPrice,
            isCheckingOut = isCheckingOut,
            onDismiss = { showQrisPaymentDialog = false },
            onConfirmQrisPayment = {
                if (isCheckingOut) return@QrisPaymentDialog
                isCheckingOut = true
                viewModel.checkoutCart(
                    cartItems = cart.toMap(),
                    paymentMethod = "QRIS",
                    onSuccess = { saleId ->
                        scope.launch {
                            val receiptData = viewModel.getReceiptData(saleId, userSettings)
                            isCheckingOut = false
                            showQrisPaymentDialog = false
                            showPaymentSelectorDialog = false
                            lastCheckoutData = CheckoutSuccessData(
                                items = cartSummaryList,
                                totalAmount = totalPrice,
                                paymentMethodLabel = "QRIS",
                                saleId = saleId,
                                receiptData = receiptData
                            )
                            cart.clear()
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
                                .height(130.dp)
                                .padding(AppSpacing.sm)
                        ) {
                            items(cart.entries.toList()) { (prodId, qty) ->
                                val prod = dbProducts.find { it.id == prodId }
                                if (prod != null) {
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
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = AppSpacing.xs)
                    ) {
                        Text("TOTAL ($totalItemCount item)", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text(formatRupiah(totalPrice), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AppColors.GreenPrimary)
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
                                    text = selectedCustomerForCredit?.name ?: "Pilih Pelanggan *",
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedCustomerForCredit == null) AppColors.RedExpense else AppColors.TextPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(Icons.Default.KeyboardArrowDown, null)
                            }
                        }
                        if (selectedCustomerForCredit == null) {
                            Text("Pilih pelanggan terlebih dahulu.", color = AppColors.RedExpense, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            },
            confirmButton = {
                val isCreditWithoutCustomer = selectedPaymentMethod == "CREDIT" && selectedCustomerForCredit == null
                Button(
                    onClick = {
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
                                    Toast.makeText(context, "Pilih pelanggan terlebih dahulu", Toast.LENGTH_SHORT).show()
                                    showCustomerPickerSheet = true
                                    return@Button
                                }
                                isCheckingOut = true
                                customerViewModel.checkoutCreditSale(
                                    cartItems = cart.toMap(),
                                    customerId = selectedCustomerForCredit!!.id,
                                    onSuccess = { saleId ->
                                        scope.launch {
                                            val receiptData = customerViewModel.getReceiptData(saleId, userSettings)
                                            isCheckingOut = false
                                            showPaymentSelectorDialog = false
                                            lastCheckoutData = CheckoutSuccessData(
                                                items = cartSummaryList,
                                                totalAmount = totalPrice,
                                                paymentMethodLabel = "Hutang (${selectedCustomerForCredit?.name ?: "Pelanggan"})",
                                                customerName = selectedCustomerForCredit?.name,
                                                saleId = saleId,
                                                receiptData = receiptData
                                            )
                                            cart.clear()
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
                            Spacer(Modifier.height(AppSpacing.xs))
                            Text(
                                text = printerStatusMessage!!,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (printerStatusMessage!!.contains("berhasil")) AppColors.GreenPrimary else AppColors.TextSecondary,
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
            TopAppBar(title = { Text(if (selectedTab == 0) "Jualan (Kasir)" else "Riwayat Penjualan", fontWeight = FontWeight.Bold) })
        },
        containerColor = Color.White
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Screen Tab Navigation (Kasir vs Riwayat Penjualan)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.xs),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                Surface(
                    shape = AppShapes.ChipShape,
                    color = if (selectedTab == 0) AppColors.GreenPrimary else AppColors.SurfaceGray,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedTab = 0 }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = AppSpacing.sm),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.ShoppingCart,
                            null,
                            tint = if (selectedTab == 0) Color.White else AppColors.TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(AppSpacing.xs))
                        Text(
                            "Kasir",
                            color = if (selectedTab == 0) Color.White else AppColors.TextSecondary,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                Surface(
                    shape = AppShapes.ChipShape,
                    color = if (selectedTab == 1) AppColors.GreenPrimary else AppColors.SurfaceGray,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedTab = 1 }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = AppSpacing.sm),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.History,
                            null,
                            tint = if (selectedTab == 1) Color.White else AppColors.TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(AppSpacing.xs))
                        Text(
                            "Riwayat Penjualan",
                            color = if (selectedTab == 1) Color.White else AppColors.TextSecondary,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium
                        )
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
                            label = "Cari nama atau barcode...",
                            modifier = Modifier.weight(1f)
                        )
                        if (userSettings.showBarcode) {
                            Spacer(Modifier.width(AppSpacing.sm))
                            IconButton(
                                onClick = { showPosScannerDialog = true },
                                modifier = Modifier
                                    .size(52.dp)
                                    .background(AppColors.GreenLight, AppShapes.ButtonShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCodeScanner,
                                    contentDescription = "Pindai Barcode",
                                    tint = AppColors.GreenPrimary
                                )
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
                                    shape = AppShapes.ChipShape,
                                    color = if (isSelected) AppColors.GreenPrimary else AppColors.SurfaceGray,
                                    modifier = Modifier.clickable { selectedCategoryId = null }
                                ) {
                                    Text(
                                        text = "Semua",
                                        color = if (isSelected) Color.White else AppColors.TextSecondary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.padding(horizontal = AppSpacing.lg, vertical = AppSpacing.sm)
                                    )
                                }
                            }
                            items(dbCategories, key = { it.id }) { category ->
                                val isSelected = selectedCategoryId == category.id
                                Surface(
                                    shape = AppShapes.ChipShape,
                                    color = if (isSelected) AppColors.GreenPrimary else AppColors.SurfaceGray,
                                    modifier = Modifier.clickable { selectedCategoryId = category.id }
                                ) {
                                    Text(
                                        text = category.name,
                                        color = if (isSelected) Color.White else AppColors.TextSecondary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.padding(horizontal = AppSpacing.lg, vertical = AppSpacing.sm)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(AppSpacing.md))

                if (dbProducts.isEmpty()) {
                    AppEmptyState(
                        icon = Icons.Default.Inventory2,
                        title = "Belum ada produk jualan",
                        actionText = "+ Tambah Produk",
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
                                    val currentCartQty = cart[product.id] ?: 0.0
                                    val isOutofStock = product.stock <= 0.0
                                    val isInCart = currentCartQty > 0.0

                                    AppCard(
                                        onClick = {
                                            if (!isOutofStock) {
                                                if (currentCartQty + 1.0 <= product.stock) {
                                                    cart[product.id] = currentCartQty + 1.0
                                                } else {
                                                    Toast.makeText(context, "Stok ${product.name} hanya tersisa ${product.stock.toInt()}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        backgroundColor = if (isInCart) AppColors.GreenLight else Color.White,
                                        modifier = Modifier
                                            .weight(1f)
                                            .then(
                                                if (isInCart) Modifier.border(BorderStroke(2.dp, AppColors.GreenPrimary), AppShapes.CardShape) else Modifier
                                            )
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(AppSpacing.md)
                                        ) {
                                            if (userSettings.showProductImage) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(68.dp)
                                                        .background(if (isOutofStock) Color(0xFFFFE8E8) else if (isInCart) Color.White else AppColors.GreenLight, AppShapes.CardShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    ProductImageThumbnail(
                                                        imageUri = product.imageUri,
                                                        modifier = Modifier.size(48.dp),
                                                        tint = if (isOutofStock) AppColors.RedExpense else AppColors.GreenPrimary
                                                    )
                                                    if (isInCart) {
                                                        Box(
                                                            modifier = Modifier
                                                                .align(Alignment.TopEnd)
                                                                .padding(4.dp)
                                                                .size(24.dp)
                                                                .background(AppColors.GreenPrimary, CircleShape),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(
                                                                text = "${currentCartQty.toInt()}",
                                                                color = Color.White,
                                                                fontWeight = FontWeight.Bold,
                                                                style = MaterialTheme.typography.labelSmall
                                                            )
                                                        }
                                                    }
                                                }

                                                Spacer(Modifier.height(AppSpacing.sm))
                                            }

                                            Text(
                                                text = product.name,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )

                                            Text(
                                                text = formatRupiah(product.sellingPrice),
                                                color = AppColors.GreenPrimary,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyMedium
                                            )

                                            if (userSettings.showStock) {
                                                Text(
                                                    text = if (isOutofStock) "Stok habis" else "Stok: ${product.stock.toInt()} ${product.unit}",
                                                    color = if (isOutofStock) AppColors.RedExpense else AppColors.TextSecondary,
                                                    style = MaterialTheme.typography.labelSmall
                                                )
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
                    Card(
                        colors = CardDefaults.cardColors(containerColor = AppColors.GreenLight),
                        shape = AppShapes.CardShape,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(AppSpacing.lg)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { isCartExpanded = !isCartExpanded }
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("$totalItemCount item", fontWeight = FontWeight.Bold)
                                    Text(formatRupiah(totalPrice), style = MaterialTheme.typography.titleMedium, color = AppColors.GreenPrimary, fontWeight = FontWeight.Bold)
                                }
                                Icon(
                                    imageVector = if (isCartExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                    contentDescription = "Detail Keranjang"
                                )
                            }

                            AnimatedVisibility(visible = isCartExpanded) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                                    modifier = Modifier.padding(top = AppSpacing.md)
                                ) {
                                    cart.entries.forEach { (prodId, qty) ->
                                        val prod = dbProducts.find { it.id == prodId }
                                        if (prod != null) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(prod.name, fontWeight = FontWeight.Bold)
                                                    Text("${formatRupiah(prod.sellingPrice)} × ${qty.toInt()} = ${formatRupiah(prod.sellingPrice * qty.toLong())}", color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                                                }
                                                IconButton(
                                                    onClick = {
                                                        val newQty = qty - 1.0
                                                        if (newQty <= 0) {
                                                            cart.remove(prodId)
                                                        } else {
                                                            cart[prodId] = newQty
                                                        }
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Default.Remove, "Kurangi", tint = AppColors.RedExpense)
                                                }
                                                Text("${qty.toInt()}", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = AppSpacing.xs))
                                                IconButton(
                                                    onClick = {
                                                        if (qty + 1.0 <= prod.stock) {
                                                            cart[prodId] = qty + 1.0
                                                        } else {
                                                            Toast.makeText(context, "Stok ${prod.name} hanya tersisa ${prod.stock.toInt()}", Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Default.Add, "Tambah", tint = AppColors.GreenPrimary)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(AppSpacing.md))

                            Button(
                                onClick = { showPaymentSelectorDialog = true },
                                shape = AppShapes.ButtonShape,
                                colors = ButtonDefaults.buttonColors(containerColor = AppColors.GreenPrimary),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text("BAYAR", fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.width(AppSpacing.sm))
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
                                }
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.height(AppSpacing.sm))
                }
            } else {
                // TAB 1: RIWAYAT PENJUALAN (SALES HISTORY)
                Column(Modifier.padding(horizontal = AppSpacing.lg)) {
                    AppTextField(
                        value = salesHistoryQuery,
                        onValueChange = { salesHistoryQuery = it },
                        label = "Cari nomor struk / transaksi...",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(AppSpacing.sm))

                if (sales.isEmpty()) {
                    AppEmptyState(
                        icon = Icons.Default.Receipt,
                        title = "Belum ada riwayat penjualan",
                        actionText = "+ Kasir Baru",
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
                        Text("Tidak ada transaksi penjualan yang cocok", color = AppColors.TextSecondary)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = AppSpacing.lg)
                    ) {
                        items(filteredSales, key = { it.id }) { sale ->
                            val custName = customers.find { it.id == sale.customerId }?.name ?: "Pelanggan Umum"
                            val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")).format(Date(sale.transactionDate))
                            val isCredit = sale.paymentMethod == "CREDIT"
                            val paymentLabel = when (sale.paymentMethod) {
                                "CREDIT" -> "HUTANG"
                                "QRIS" -> "QRIS"
                                else -> "TUNAI"
                            }

                            AppCard(onClick = { selectedSaleForDetail = sale }) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(AppSpacing.md),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .background(
                                                if (isCredit) Color(0xFFFFE8E8) else AppColors.GreenLight,
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Receipt,
                                            null,
                                            tint = if (isCredit) AppColors.RedExpense else AppColors.GreenPrimary
                                        )
                                    }
                                    Spacer(Modifier.width(AppSpacing.md))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                                        ) {
                                            Text(
                                                sale.transactionNumber,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Surface(
                                                shape = AppShapes.ChipShape,
                                                color = if (isCredit) Color(0xFFFFE8E8) else AppColors.GreenLight
                                            ) {
                                                Text(
                                                    text = paymentLabel,
                                                    color = if (isCredit) AppColors.RedExpense else AppColors.GreenPrimary,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            custName,
                                            color = AppColors.TextPrimary,
                                            fontWeight = FontWeight.Medium,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            dateStr,
                                            color = AppColors.TextSecondary,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                    Text(
                                        formatRupiah(sale.totalAmount),
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
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
