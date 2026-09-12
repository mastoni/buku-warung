package id.skmnetwork.bukuwarung.ui.purchase

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.skmnetwork.bukuwarung.data.local.entity.PurchaseItemEntity
import id.skmnetwork.bukuwarung.data.local.entity.PurchaseTransactionEntity
import id.skmnetwork.bukuwarung.data.local.entity.SupplierEntity
import id.skmnetwork.bukuwarung.ui.components.AppCard
import id.skmnetwork.bukuwarung.ui.components.AppEmptyState
import id.skmnetwork.bukuwarung.ui.components.PrimaryButton
import id.skmnetwork.bukuwarung.ui.product.ProductViewModel
import id.skmnetwork.bukuwarung.ui.supplier.SupplierViewModel
import id.skmnetwork.bukuwarung.ui.theme.AppColors
import id.skmnetwork.bukuwarung.ui.theme.AppShapes
import id.skmnetwork.bukuwarung.ui.theme.AppSpacing
import id.skmnetwork.bukuwarung.util.formatRupiah
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseScreen(
    viewModel: ProductViewModel,
    supplierViewModel: SupplierViewModel,
    onNavigateToAddProduct: () -> Unit = {}
) {
    val dbProducts by viewModel.products.collectAsStateWithLifecycle()
    val suppliers by supplierViewModel.suppliers.collectAsStateWithLifecycle()
    val purchases by viewModel.purchases.collectAsStateWithLifecycle()
    val purchaseCart = remember { mutableStateMapOf<Long, Double>() }

    var selectedTab by remember { mutableStateOf(0) } // 0: Belanja Baru, 1: Riwayat Belanja

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

    // Supplier Picker Dialog
    if (showSupplierPickerSheet) {
        AlertDialog(
            onDismissRequest = { showSupplierPickerSheet = false },
            title = { Text("Pilih Supplier", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                    modifier = Modifier.height(260.dp)
                ) {
                    if (suppliers.isEmpty()) {
                        Text(
                            "Belum ada supplier. Tambahkan supplier di menu Supplier & Hutang.",
                            color = AppColors.TextSecondary
                        )
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                            items(suppliers) { sup ->
                                val isSelected = if (isPickerForCash) {
                                    selectedSupplierForCash?.id == sup.id
                                } else {
                                    selectedSupplierForCredit?.id == sup.id
                                }

                                Surface(
                                    shape = AppShapes.CardShape,
                                    color = if (isSelected) AppColors.GreenLight else AppColors.SurfaceGray,
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
                                    Text(
                                        text = sup.name + if (!sup.phone.isNullOrEmpty()) " (${sup.phone})" else "",
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
                TextButton(onClick = { showSupplierPickerSheet = false }) {
                    Text("Tutup", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Purchase Success Dialog
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showSuccessDialog = false },
            title = { Text("Pembelian Berhasil!", fontWeight = FontWeight.Bold) },
            text = { Text("Stok produk telah bertambah dan status transaksi pembelian berhasil dicatat.") },
            confirmButton = {
                TextButton(onClick = { showSuccessDialog = false }) {
                    Text("OK", fontWeight = FontWeight.Bold, color = AppColors.GreenPrimary)
                }
            }
        )
    }

    // Purchase Detail Dialog (Read-only / Immutable)
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
                Column {
                    Text("Detail Pembelian", fontWeight = FontWeight.Bold)
                    Text(
                        purchase.transactionNumber,
                        style = MaterialTheme.typography.labelMedium,
                        color = AppColors.TextSecondary
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
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
                        Text("Supplier", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                        Text(supplierName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Metode", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                        Text(
                            if (purchase.paymentMethod == "CREDIT") "Hutang Supplier (Kredit)" else "Tunai (CASH)",
                            fontWeight = FontWeight.Bold,
                            color = if (purchase.paymentMethod == "CREDIT") AppColors.RedExpense else AppColors.GreenPrimary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    HorizontalDivider(Modifier.padding(vertical = AppSpacing.xs))

                    Text("Daftar Barang:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)

                    if (isLoadingItems) {
                        Text("Memuat rincian barang...", color = AppColors.TextSecondary)
                    } else if (detailItems.isEmpty()) {
                        Text("Tidak ada data barang", color = AppColors.TextSecondary)
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
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
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(Modifier.padding(vertical = AppSpacing.xs))

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

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Pembelian / Kulakan", fontWeight = FontWeight.Bold) })
        },
        containerColor = Color.White
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Screen Tab Navigation (Belanja Baru vs Riwayat Belanja)
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
                            "Belanja Baru",
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
                            "Riwayat Belanja",
                            color = if (selectedTab == 1) Color.White else AppColors.TextSecondary,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            Spacer(Modifier.height(AppSpacing.xs))

            if (selectedTab == 0) {
                // TAB 0: BELANJA BARU
                Column(Modifier.padding(horizontal = AppSpacing.lg)) {
                    Surface(
                        shape = AppShapes.TextFieldShape,
                        color = AppColors.SurfaceGray,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(AppSpacing.md),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Sync, null, tint = AppColors.TextSecondary)
                            Spacer(Modifier.width(AppSpacing.sm))
                            Text(currentMonthYear, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                Spacer(Modifier.height(AppSpacing.sm))

                if (dbProducts.isEmpty()) {
                    AppEmptyState(
                        icon = Icons.Default.Inventory2,
                        title = "Belum ada produk untuk dibeli",
                        actionText = "+ Tambah Barang",
                        onActionClick = onNavigateToAddProduct,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = AppSpacing.lg)
                    ) {
                        items(dbProducts, key = { it.id }) { product ->
                            val qty = purchaseCart[product.id] ?: 0.0
                            val itemSubtotal = product.purchasePrice * qty.toLong()

                            AppCard {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(AppSpacing.md),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        Modifier
                                            .size(46.dp)
                                            .background(AppColors.GreenLight, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Inventory2, null, tint = AppColors.GreenPrimary)
                                    }
                                    Spacer(Modifier.width(AppSpacing.md))
                                    Column(Modifier.weight(1f)) {
                                        Text(product.name, fontWeight = FontWeight.SemiBold)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = {
                                                    if (qty > 0) purchaseCart[product.id] = qty - 1.0
                                                    if (purchaseCart[product.id] == 0.0) purchaseCart.remove(product.id)
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Default.Remove, "Kurangi", tint = AppColors.TextSecondary)
                                            }
                                            Text(
                                                "${qty.toInt()}",
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = AppSpacing.sm)
                                            )
                                            IconButton(
                                                onClick = { purchaseCart[product.id] = qty + 1.0 },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Default.Add, "Tambah", tint = AppColors.GreenPrimary)
                                            }
                                        }
                                        Text(
                                            formatRupiah(product.purchasePrice),
                                            color = AppColors.TextSecondary,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                    Text(
                                        formatRupiah(itemSubtotal),
                                        fontWeight = FontWeight.Bold,
                                        color = AppColors.GreenPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                if (purchaseCart.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = AppColors.GreenLight),
                        shape = AppShapes.CardShape,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(AppSpacing.lg)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Total Belanja", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text(
                                    formatRupiah(totalPurchaseAmount),
                                    fontWeight = FontWeight.Bold,
                                    color = AppColors.GreenPrimary,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }

                            Spacer(Modifier.height(AppSpacing.md))

                            // Payment Method Toggle
                            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                                Surface(
                                    shape = AppShapes.ChipShape,
                                    color = if (paymentMethod == "CASH") AppColors.GreenPrimary else AppColors.SurfaceGray,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { paymentMethod = "CASH" }
                                ) {
                                    Box(
                                        Modifier.padding(vertical = AppSpacing.sm),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "Tunai",
                                            color = if (paymentMethod == "CASH") Color.White else AppColors.TextSecondary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Surface(
                                    shape = AppShapes.ChipShape,
                                    color = if (paymentMethod == "CREDIT") AppColors.GreenPrimary else AppColors.SurfaceGray,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { paymentMethod = "CREDIT" }
                                ) {
                                    Box(
                                        Modifier.padding(vertical = AppSpacing.sm),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "Hutang Supplier",
                                            color = if (paymentMethod == "CREDIT") Color.White else AppColors.TextSecondary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(AppSpacing.sm))

                            // Supplier Selector (Optional for CASH, Required for CREDIT)
                            if (paymentMethod == "CASH") {
                                Surface(
                                    shape = AppShapes.CardShape,
                                    color = Color.White,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            isPickerForCash = true
                                            showSupplierPickerSheet = true
                                        }
                                ) {
                                    Row(
                                        Modifier.padding(AppSpacing.md),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.LocalShipping, null, tint = AppColors.GreenPrimary)
                                        Spacer(Modifier.width(AppSpacing.sm))
                                        Text(
                                            text = selectedSupplierForCash?.name ?: "Pilih Supplier (Opsional)",
                                            fontWeight = FontWeight.Bold,
                                            color = if (selectedSupplierForCash != null) AppColors.TextPrimary else AppColors.TextSecondary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (selectedSupplierForCash != null) {
                                            IconButton(
                                                onClick = { selectedSupplierForCash = null },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Default.Close, "Hapus Supplier", tint = AppColors.TextSecondary)
                                            }
                                        } else {
                                            Icon(Icons.Default.KeyboardArrowDown, null)
                                        }
                                    }
                                }
                            } else {
                                Surface(
                                    shape = AppShapes.CardShape,
                                    color = Color.White,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            isPickerForCash = false
                                            showSupplierPickerSheet = true
                                        }
                                ) {
                                    Row(
                                        Modifier.padding(AppSpacing.md),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.People, null, tint = AppColors.GreenPrimary)
                                        Spacer(Modifier.width(AppSpacing.sm))
                                        Text(
                                            text = selectedSupplierForCredit?.name ?: "+ Pilih Supplier *",
                                            fontWeight = FontWeight.Bold,
                                            color = if (selectedSupplierForCredit == null) AppColors.RedExpense else AppColors.TextPrimary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(Icons.Default.KeyboardArrowDown, null)
                                    }
                                }
                            }

                            Spacer(Modifier.height(AppSpacing.md))

                            PrimaryButton(
                                text = if (paymentMethod == "CREDIT") "Simpan Belanja Kredit" else "Simpan Belanja Tunai",
                                onClick = {
                                    if (isSaving) return@PrimaryButton
                                    if (paymentMethod == "CREDIT" && selectedSupplierForCredit == null) {
                                        Toast.makeText(context, "Pilih supplier untuk pembelian kredit", Toast.LENGTH_SHORT).show()
                                        isPickerForCash = false
                                        showSupplierPickerSheet = true
                                        return@PrimaryButton
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
                                enabled = !isSaving
                            )
                        }
                    }
                } else {
                    Spacer(Modifier.height(AppSpacing.sm))
                }
            } else {
                // TAB 1: RIWAYAT BELANJA (PURCHASE HISTORY)
                if (purchases.isEmpty()) {
                    AppEmptyState(
                        icon = Icons.Default.Receipt,
                        title = "Belum ada riwayat belanja",
                        actionText = "+ Belanja Baru",
                        onActionClick = { selectedTab = 0 },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = AppSpacing.lg)
                    ) {
                        items(purchases, key = { it.id }) { purchase ->
                            val supName = suppliers.find { it.id == purchase.supplierId }?.name ?: "Tunai Umum"
                            val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")).format(Date(purchase.transactionDate))
                            val isCredit = purchase.paymentMethod == "CREDIT"

                            AppCard(onClick = { selectedPurchaseForDetail = purchase }) {
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
                                                purchase.transactionNumber,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Surface(
                                                shape = AppShapes.ChipShape,
                                                color = if (isCredit) Color(0xFFFFE8E8) else AppColors.GreenLight
                                            ) {
                                                Text(
                                                    text = if (isCredit) "HUTANG" else "TUNAI",
                                                    color = if (isCredit) AppColors.RedExpense else AppColors.GreenPrimary,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            supName,
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
                                        formatRupiah(purchase.totalAmount),
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
