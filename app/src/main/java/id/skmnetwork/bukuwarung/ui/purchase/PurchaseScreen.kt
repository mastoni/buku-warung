package id.skmnetwork.bukuwarung.ui.purchase

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.data.local.entity.PurchaseItemEntity
import id.skmnetwork.bukuwarung.data.local.entity.PurchaseTransactionEntity
import id.skmnetwork.bukuwarung.data.local.entity.SupplierEntity
import id.skmnetwork.bukuwarung.data.preferences.UserSettings
import id.skmnetwork.bukuwarung.domain.business.BusinessTaxonomyRegistry
import id.skmnetwork.bukuwarung.ui.components.ProductImageThumbnail
import id.skmnetwork.bukuwarung.ui.product.ProductViewModel
import id.skmnetwork.bukuwarung.ui.supplier.SupplierViewModel
import id.skmnetwork.bukuwarung.ui.theme.AppColors
import id.skmnetwork.bukuwarung.util.formatRupiah
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseScreen(
    viewModel: ProductViewModel,
    supplierViewModel: SupplierViewModel,
    poViewModel: PurchaseOrderViewModel? = null,
    printerService: id.skmnetwork.bukuwarung.printer.PrinterService? = null,
    userSettings: UserSettings? = null,
    onNavigateToAddProduct: () -> Unit = {}
) {
    val dbProducts by viewModel.products.collectAsStateWithLifecycle()
    val suppliers by supplierViewModel.suppliers.collectAsStateWithLifecycle()
    val purchases by viewModel.purchases.collectAsStateWithLifecycle()
    val purchaseCart = remember { mutableStateMapOf<Long, Double>() }

    val resolvedProfile = remember(userSettings) {
        BusinessTaxonomyRegistry.resolve(
            userSettings?.primaryBusinessType,
            userSettings?.secondaryActivities
        )
    }

    val terminology = resolvedProfile.terminology
    val purchaseLabel = terminology.purchaseLabel
    val supplierLabel = terminology.supplierLabel
    val productLabel = terminology.productLabel
    val debtLabel = terminology.debtLabel

    var selectedTab by remember { mutableStateOf(0) } // 0: Belanja Baru, 1: Pesanan Supplier (PO), 2: Riwayat Belanja
    var showCreatePoDialog by remember { mutableStateOf(false) }

    var paymentMethod by remember { mutableStateOf("CASH") } // "CASH" or "CREDIT"
    var selectedSupplierForCredit by remember { mutableStateOf<SupplierEntity?>(null) }
    var selectedSupplierForCash by remember { mutableStateOf<SupplierEntity?>(null) }
    var showSupplierPickerSheet by remember { mutableStateOf(false) }
    var isPickerForCash by remember { mutableStateOf(false) }

    var selectedPurchaseForDetail by remember { mutableStateOf<PurchaseTransactionEntity?>(null) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val currentMonthYear = remember {
        SimpleDateFormat("MMMM yyyy", Locale("id", "ID")).format(Date())
    }

    val totalPurchaseAmount = purchaseCart.entries.sumOf { (prodId, qty) ->
        val prod = dbProducts.find { it.id == prodId }
        (prod?.purchasePrice ?: 0L) * qty.toLong()
    }

    // ==========================================
    // SUPPLIER PICKER DIALOG
    // ==========================================
    if (showSupplierPickerSheet) {
        AlertDialog(
            onDismissRequest = { showSupplierPickerSheet = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFE8F5E9)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.People,
                            contentDescription = null,
                            tint = AppColors.GreenPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("Pilih $supplierLabel", fontWeight = FontWeight.Bold, fontSize = 16.5.sp)
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(280.dp)
                ) {
                    if (suppliers.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Belum ada $supplierLabel tersimpan.\nTambahkan $supplierLabel di menu $supplierLabel.",
                                color = AppColors.TextSecondary,
                                textAlign = TextAlign.Center,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(suppliers) { sup ->
                                val isSelected = if (isPickerForCash) {
                                    selectedSupplierForCash?.id == sup.id
                                } else {
                                    selectedSupplierForCredit?.id == sup.id
                                }

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) Color(0xFFE8F5E9) else Color(0xFFF8FAF8),
                                    border = BorderStroke(1.dp, if (isSelected) AppColors.GreenPrimary else Color(0xFFE2E8F0)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (isPickerForCash) {
                                                selectedSupplierForCash = sup
                                            } else {
                                                selectedSupplierForCredit = sup
                                            }
                                            showSupplierPickerSheet = false
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(
                                                text = sup.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = AppColors.TextPrimary
                                            )
                                            if (!sup.phone.isNullOrEmpty()) {
                                                Text(
                                                    text = sup.phone,
                                                    fontSize = 12.sp,
                                                    color = AppColors.TextSecondary
                                                )
                                            }
                                        }
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Terpilih",
                                                tint = AppColors.GreenPrimary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSupplierPickerSheet = false }) {
                    Text("Tutup", fontWeight = FontWeight.Bold, color = AppColors.GreenPrimary)
                }
            }
        )
    }

    // ==========================================
    // PURCHASE SUCCESS DIALOG
    // ==========================================
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showSuccessDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE8F5E9)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = AppColors.GreenPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("$purchaseLabel Berhasil!", fontWeight = FontWeight.Bold, fontSize = 16.5.sp)
                }
            },
            text = {
                Text(
                    "Stok $productLabel telah otomatis bertambah dan status transaksi $purchaseLabel berhasil dicatat.",
                    fontSize = 13.5.sp,
                    color = AppColors.TextPrimary
                )
            },
            confirmButton = {
                Button(
                    onClick = { showSuccessDialog = false },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.GreenPrimary,
                        contentColor = Color.White
                    )
                ) {
                    Text("Selesai", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // ==========================================
    // PURCHASE DETAIL DIALOG (Read-only / Immutable)
    // ==========================================
    if (selectedPurchaseForDetail != null) {
        val purchase = selectedPurchaseForDetail!!
        val supplierName = suppliers.find { it.id == purchase.supplierId }?.name ?: "Tunai Umum"
        val dateFormatted = remember(purchase.transactionDate) {
            SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale("id", "ID")).format(Date(purchase.transactionDate))
        }

        var detailItems by remember(purchase.id) { mutableStateOf<List<PurchaseItemEntity>>(emptyList()) }
        var isLoadingItems by remember(purchase.id) { mutableStateOf(true) }

        LaunchedEffect(purchase.id) {
            detailItems = viewModel.getPurchaseItems(purchase.id)
            isLoadingItems = false
        }

        AlertDialog(
            onDismissRequest = { selectedPurchaseForDetail = null },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Detail $purchaseLabel", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            purchase.transactionNumber,
                            style = MaterialTheme.typography.labelMedium,
                            color = AppColors.TextSecondary
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (purchase.paymentMethod == "CREDIT") Color(0xFFFFEBEE) else Color(0xFFE8F5E9)
                    ) {
                        Text(
                            text = if (purchase.paymentMethod == "CREDIT") debtLabel.uppercase() else "TUNAI",
                            color = if (purchase.paymentMethod == "CREDIT") Color(0xFFD32F2F) else AppColors.GreenPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Tanggal", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                        Text(dateFormatted, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(supplierLabel, color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                        Text(supplierName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                    }

                    HorizontalDivider(Modifier.padding(vertical = 4.dp), color = Color(0xFFE2E8F0))

                    Text("Rincian $productLabel:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)

                    if (isLoadingItems) {
                        Text("Memuat rincian $productLabel...", color = AppColors.TextSecondary)
                    } else if (detailItems.isEmpty()) {
                        Text("Tidak ada data $productLabel", color = AppColors.TextSecondary)
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            items(detailItems) { item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.productName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                                        Text(
                                            "${item.quantity.toInt()} x ${formatRupiah(item.purchasePrice)}",
                                            color = AppColors.TextSecondary,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                    Text(
                                        formatRupiah(item.subtotal),
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AppColors.TextPrimary
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(Modifier.padding(vertical = 4.dp), color = Color(0xFFE2E8F0))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Total Transaksi", fontWeight = FontWeight.Bold)
                        Text(
                            formatRupiah(purchase.totalAmount),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = AppColors.GreenPrimary
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedPurchaseForDetail = null }) {
                    Text("Tutup", fontWeight = FontWeight.Bold, color = AppColors.GreenPrimary)
                }
            }
        )
    }

    // ==========================================
    // MAIN SCREEN SCAFFOLD
    // ==========================================
    Scaffold(
        topBar = {
            PurchaseHeader(
                selectedTab = selectedTab,
                purchaseLabel = purchaseLabel,
                supplierLabel = supplierLabel,
                onTabSelected = { selectedTab = it }
            )
        },
        containerColor = Color(0xFFFBFDFB)
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (selectedTab == 0) {
                // ==========================================
                // TAB 0: BELANJA BARU (RESTOCK / PURCHASE)
                // ==========================================
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Month / Period Header Card
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFEFF3F0)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = null,
                                    tint = AppColors.GreenPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = currentMonthYear,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.5.sp,
                                    color = AppColors.TextPrimary
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xFFF1F5F2)
                            ) {
                                Text(
                                    text = "Periode $purchaseLabel",
                                    fontSize = 11.sp,
                                    color = AppColors.TextSecondary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    if (dbProducts.isEmpty()) {
                        // Empty State when no products exist in catalog
                        PurchaseEmptyProductsState(
                            productLabel = productLabel,
                            onAddProduct = onNavigateToAddProduct
                        )
                    } else {
                        // Product Restock List
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 4.dp,
                                bottom = if (purchaseCart.isNotEmpty()) 240.dp else 88.dp
                            ),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(dbProducts, key = { it.id }) { product ->
                                val qty = purchaseCart[product.id] ?: 0.0
                                val itemSubtotal = product.purchasePrice * qty.toLong()

                                PurchaseItemCard(
                                    product = product,
                                    quantity = qty,
                                    subtotal = itemSubtotal,
                                    onIncrease = {
                                        purchaseCart[product.id] = qty + 1.0
                                    },
                                    onDecrease = {
                                        if (qty > 0) {
                                            purchaseCart[product.id] = qty - 1.0
                                            if (purchaseCart[product.id] == 0.0) {
                                                purchaseCart.remove(product.id)
                                            }
                                        }
                                    },
                                    onClear = {
                                        purchaseCart.remove(product.id)
                                    }
                                )
                            }
                        }
                    }
                }

                // ==========================================
                // BOTTOM SUMMARY & CHECKOUT SHEET (ANCHORED)
                // ==========================================
                if (purchaseCart.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                        color = Color.White,
                        shadowElevation = 8.dp,
                        border = BorderStroke(1.dp, Color(0xFFE8EFEA)),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Total Belanja Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Total $purchaseLabel",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = AppColors.TextSecondary
                                    )
                                    Text(
                                        text = "${purchaseCart.size} jenis $productLabel",
                                        fontSize = 11.5.sp,
                                        color = AppColors.TextSecondary
                                    )
                                }
                                Text(
                                    text = formatRupiah(totalPurchaseAmount),
                                    fontWeight = FontWeight.Bold,
                                    color = AppColors.GreenPrimary,
                                    fontSize = 18.sp
                                )
                            }

                            // Payment Method Segmented Selector
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Cash Button
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (paymentMethod == "CASH") AppColors.GreenPrimary else Color(0xFFF1F4F2),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .clickable { paymentMethod = "CASH" }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "Tunai (CASH)",
                                            color = if (paymentMethod == "CASH") Color.White else AppColors.TextSecondary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                }

                                // Credit Button
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (paymentMethod == "CREDIT") Color(0xFFD32F2F) else Color(0xFFF1F4F2),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .clickable { paymentMethod = "CREDIT" }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "$debtLabel $supplierLabel",
                                            color = if (paymentMethod == "CREDIT") Color.White else AppColors.TextSecondary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }

                            // Supplier Selector Pill
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFF8FAF8),
                                border = BorderStroke(
                                    1.dp,
                                    if (paymentMethod == "CREDIT" && selectedSupplierForCredit == null) Color(0xFFFFCDD2) else Color(0xFFE2E8F0)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        isPickerForCash = (paymentMethod == "CASH")
                                        showSupplierPickerSheet = true
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (paymentMethod == "CREDIT") Icons.Default.People else Icons.Default.LocalShipping,
                                        contentDescription = null,
                                        tint = if (paymentMethod == "CREDIT" && selectedSupplierForCredit == null) Color(0xFFD32F2F) else AppColors.GreenPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = if (paymentMethod == "CASH") {
                                            selectedSupplierForCash?.name ?: "Pilih $supplierLabel (Opsional)"
                                        } else {
                                            selectedSupplierForCredit?.name ?: "+ Pilih $supplierLabel (Wajib)*"
                                        },
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = when {
                                            paymentMethod == "CREDIT" && selectedSupplierForCredit == null -> Color(0xFFD32F2F)
                                            paymentMethod == "CASH" && selectedSupplierForCash == null -> AppColors.TextSecondary
                                            else -> AppColors.TextPrimary
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (paymentMethod == "CASH" && selectedSupplierForCash != null) {
                                        IconButton(
                                            onClick = { selectedSupplierForCash = null },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Hapus $supplierLabel",
                                                tint = AppColors.TextSecondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = AppColors.TextSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            // Primary Action Button (Simpan Belanja)
                            Button(
                                onClick = {
                                    if (isSaving) return@Button
                                    if (paymentMethod == "CREDIT" && selectedSupplierForCredit == null) {
                                        Toast.makeText(context, "Pilih $supplierLabel untuk $purchaseLabel kredit", Toast.LENGTH_SHORT).show()
                                        isPickerForCash = false
                                        showSupplierPickerSheet = true
                                        return@Button
                                    }

                                    isSaving = true
                                    if (paymentMethod == "CASH") {
                                        viewModel.checkoutPurchase(
                                            purchaseItems = purchaseCart.toMap(),
                                            supplierId = selectedSupplierForCash?.id,
                                            onSuccess = {
                                                isSaving = false
                                                purchaseCart.clear()
                                                selectedSupplierForCash = null
                                                showSuccessDialog = true
                                            },
                                            onError = { error ->
                                                isSaving = false
                                                Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    } else {
                                        supplierViewModel.checkoutCreditPurchase(
                                            purchaseItems = purchaseCart.toMap(),
                                            supplierId = selectedSupplierForCredit!!.id,
                                            onSuccess = {
                                                isSaving = false
                                                purchaseCart.clear()
                                                selectedSupplierForCredit = null
                                                showSuccessDialog = true
                                            },
                                            onError = { error ->
                                                isSaving = false
                                                Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                },
                                enabled = !isSaving,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (paymentMethod == "CREDIT") Color(0xFFD32F2F) else AppColors.GreenPrimary,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = if (paymentMethod == "CREDIT") "Simpan $purchaseLabel Kredit" else "Simpan $purchaseLabel Tunai",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.5.sp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.Default.ArrowForward,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (selectedTab == 1) {
                // ==========================================
                // TAB 1: PESANAN SUPPLIER (PURCHASE ORDERS)
                // ==========================================
                if (poViewModel != null) {
                    PurchaseOrderTabContent(
                        poViewModel = poViewModel,
                        dbProducts = dbProducts,
                        suppliers = suppliers,
                        shopName = userSettings?.shopName ?: "Usaha Kami",
                        printerService = printerService,
                        supplierLabel = supplierLabel,
                        purchaseLabel = purchaseLabel,
                        onOpenCreatePo = { showCreatePoDialog = true }
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Purchase Order tidak tersedia",
                            color = AppColors.TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                // ==========================================
                // TAB 2: RIWAYAT BELANJA (PURCHASE HISTORY)
                // ==========================================
                if (purchases.isEmpty()) {
                    PurchaseEmptyHistoryState(
                        purchaseLabel = purchaseLabel,
                        onNewPurchase = { selectedTab = 0 }
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 10.dp,
                            bottom = 88.dp
                        ),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(purchases, key = { it.id }) { purchase ->
                            val supName = suppliers.find { it.id == purchase.supplierId }?.name ?: "Tunai Umum"
                            val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")).format(Date(purchase.transactionDate))
                            val isCredit = purchase.paymentMethod == "CREDIT"

                            PurchaseHistoryCard(
                                purchase = purchase,
                                supplierName = supName,
                                dateStr = dateStr,
                                isCredit = isCredit,
                                debtLabel = debtLabel,
                                onClick = { selectedPurchaseForDetail = purchase }
                            )
                        }
                    }
                }
            }
        }
    }

    // Create Purchase Order Dialog
    if (showCreatePoDialog && poViewModel != null) {
        val scope = rememberCoroutineScope()
        CreateEditPurchaseOrderDialog(
            dbProducts = dbProducts,
            suppliers = suppliers,
            supplierLabel = supplierLabel,
            onDismiss = { showCreatePoDialog = false },
            onSave = { supplierId, items, notes ->
                scope.launch {
                    val res = poViewModel.createDraftOrder(
                        supplierId = supplierId,
                        items = items,
                        notes = notes
                    )
                    if (res.isSuccess) {
                        Toast.makeText(context, "Draft pesanan berhasil dibuat", Toast.LENGTH_SHORT).show()
                        showCreatePoDialog = false
                    } else {
                        Toast.makeText(context, res.exceptionOrNull()?.message ?: "Gagal membuat draft", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }
}

/**
 * Header harmonized with Home, Kasir, and Produk screens.
 */
@Composable
private fun PurchaseHeader(
    selectedTab: Int,
    purchaseLabel: String = "Pembelian",
    supplierLabel: String = "Supplier",
    onTabSelected: (Int) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shadowElevation = 0.5.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppColors.GreenPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (selectedTab) {
                                0 -> Icons.Default.LocalShipping
                                1 -> Icons.Default.ReceiptLong
                                else -> Icons.Default.History
                            },
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = when (selectedTab) {
                                0 -> "$purchaseLabel (Kulakan)"
                                1 -> "Pesanan $supplierLabel"
                                else -> "Riwayat $purchaseLabel"
                            },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = AppColors.TextPrimary
                            )
                        )
                        Text(
                            text = when (selectedTab) {
                                0 -> "Catat stok masuk & $purchaseLabel barang"
                                1 -> "Daftar Purchase Order ke $supplierLabel"
                                else -> "Daftar transaksi $purchaseLabel produk"
                            },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                color = AppColors.TextSecondary
                            )
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFFE8F5E9)
                ) {
                    Text(
                        text = when (selectedTab) {
                            0 -> "$purchaseLabel Baru"
                            1 -> "Pesanan $supplierLabel"
                            else -> "Riwayat"
                        },
                        color = AppColors.GreenPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Segmented Capsule Tab Bar (3 Tabs)
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFFF1F4F2),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Tab 0: Belanja Baru
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (selectedTab == 0) AppColors.GreenPrimary else Color.Transparent,
                        shadowElevation = if (selectedTab == 0) 1.dp else 0.dp,
                        modifier = Modifier
                            .weight(1f)
                            .padding(3.dp)
                            .clickable { onTabSelected(0) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ShoppingCart,
                                contentDescription = null,
                                tint = if (selectedTab == 0) Color.White else AppColors.TextSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "$purchaseLabel Baru",
                                color = if (selectedTab == 0) Color.White else AppColors.TextSecondary,
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.5.sp,
                                maxLines = 1
                            )
                        }
                    }

                    // Tab 1: Pesanan Supplier (PO)
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (selectedTab == 1) AppColors.GreenPrimary else Color.Transparent,
                        shadowElevation = if (selectedTab == 1) 1.dp else 0.dp,
                        modifier = Modifier
                            .weight(1.1f)
                            .padding(3.dp)
                            .clickable { onTabSelected(1) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ReceiptLong,
                                contentDescription = null,
                                tint = if (selectedTab == 1) Color.White else AppColors.TextSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Pesanan $supplierLabel",
                                color = if (selectedTab == 1) Color.White else AppColors.TextSecondary,
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.5.sp,
                                maxLines = 1
                            )
                        }
                    }

                    // Tab 2: Riwayat Belanja
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (selectedTab == 2) AppColors.GreenPrimary else Color.Transparent,
                        shadowElevation = if (selectedTab == 2) 1.dp else 0.dp,
                        modifier = Modifier
                            .weight(0.9f)
                            .padding(3.dp)
                            .clickable { onTabSelected(2) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = if (selectedTab == 2) Color.White else AppColors.TextSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Riwayat",
                                color = if (selectedTab == 2) Color.White else AppColors.TextSecondary,
                                fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.5.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Purchase item card for restocking with quantity stepper and subtotal.
 */
@Composable
private fun PurchaseItemCard(
    product: ProductEntity,
    quantity: Double,
    subtotal: Long,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onClear: () -> Unit
) {
    val isSelected = quantity > 0

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.dp, if (isSelected) AppColors.GreenPrimary.copy(alpha = 0.4f) else Color(0xFFEFF3F0)),
        shadowElevation = if (isSelected) 1.dp else 0.5.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Product Image Thumbnail
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFF4F7F4)),
                contentAlignment = Alignment.Center
            ) {
                ProductImageThumbnail(
                    imageUri = product.imageUri,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(Modifier.width(12.dp))

            // Product Details & Stepper
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.5.sp,
                        color = AppColors.TextPrimary
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(2.dp))

                Text(
                    text = "Harga Beli: ${formatRupiah(product.purchasePrice)}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        color = AppColors.TextSecondary
                    )
                )

                Spacer(Modifier.height(6.dp))

                // Capsule Stepper
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFFF1F5F2)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        IconButton(
                            onClick = onDecrease,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = "Kurangi",
                                tint = if (quantity > 0) AppColors.TextPrimary else AppColors.TextSecondary.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Text(
                            text = "${quantity.toInt()}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = if (quantity > 0) AppColors.GreenPrimary else AppColors.TextPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )

                        IconButton(
                            onClick = onIncrease,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Tambah",
                                tint = AppColors.GreenPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Subtotal Column
            if (isSelected) {
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(
                        text = "Subtotal",
                        fontSize = 10.5.sp,
                        color = AppColors.TextSecondary
                    )
                    Text(
                        text = formatRupiah(subtotal),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = AppColors.GreenPrimary
                    )
                    Spacer(Modifier.height(4.dp))
                    IconButton(
                        onClick = onClear,
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Hapus",
                            tint = AppColors.TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Purchase History Receipt Card.
 */
@Composable
private fun PurchaseHistoryCard(
    purchase: PurchaseTransactionEntity,
    supplierName: String,
    dateStr: String,
    isCredit: Boolean,
    debtLabel: String = "Hutang",
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFEFF3F0)),
        shadowElevation = 0.5.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        if (isCredit) Color(0xFFFFEBEE) else Color(0xFFE8F5E9),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Receipt,
                    contentDescription = null,
                    tint = if (isCredit) Color(0xFFD32F2F) else AppColors.GreenPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = purchase.transactionNumber,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        color = AppColors.TextPrimary,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isCredit) Color(0xFFFFEBEE) else Color(0xFFE8F5E9)
                    ) {
                        Text(
                            text = if (isCredit) debtLabel.uppercase() else "TUNAI",
                            color = if (isCredit) Color(0xFFD32F2F) else AppColors.GreenPrimary,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(Modifier.height(2.dp))

                Text(
                    text = supplierName,
                    color = AppColors.TextPrimary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.5.sp
                )

                Text(
                    text = dateStr,
                    color = AppColors.TextSecondary,
                    fontSize = 11.5.sp
                )
            }

            Text(
                text = formatRupiah(purchase.totalAmount),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = if (isCredit) Color(0xFFD32F2F) else AppColors.GreenPrimary,
                modifier = Modifier.padding(start = 6.dp)
            )
        }
    }
}

/**
 * Empty state when no products exist in catalog.
 */
@Composable
private fun PurchaseEmptyProductsState(
    productLabel: String = "Produk",
    onAddProduct: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(Color(0xFFE8F5E9), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Inventory2,
                    contentDescription = null,
                    tint = AppColors.GreenPrimary,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Belum Ada $productLabel Untuk Dibeli",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.5.sp,
                    color = AppColors.TextPrimary
                )
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "Tambahkan $productLabel terlebih dahulu agar dapat mencatat pembelian & stok masuk.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    color = AppColors.TextSecondary,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = onAddProduct,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.GreenPrimary,
                    contentColor = Color.White
                ),
                modifier = Modifier.height(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Tambah $productLabel",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp
                )
            }
        }
    }
}

/**
 * Empty state for Purchase History.
 */
@Composable
private fun PurchaseEmptyHistoryState(
    purchaseLabel: String = "Pembelian",
    onNewPurchase: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(Color(0xFFF1F5F2), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ReceiptLong,
                    contentDescription = null,
                    tint = AppColors.TextSecondary,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Belum Ada Riwayat $purchaseLabel",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = AppColors.TextPrimary
                )
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "Semua transaksi $purchaseLabel dan restock produk akan dicatat di sini.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    color = AppColors.TextSecondary,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = onNewPurchase,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.GreenPrimary,
                    contentColor = Color.White
                ),
                modifier = Modifier.height(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ShoppingCart,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "$purchaseLabel Baru",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp
                )
            }
        }
    }
}
