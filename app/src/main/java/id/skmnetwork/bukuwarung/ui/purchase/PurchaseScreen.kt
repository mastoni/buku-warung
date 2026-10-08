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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import id.skmnetwork.bukuwarung.domain.money.MoneyCalculator
import id.skmnetwork.bukuwarung.ui.components.ALL_CATEGORIES_LABEL
import id.skmnetwork.bukuwarung.ui.components.ProductCategoryChips
import id.skmnetwork.bukuwarung.ui.components.ProductFilterEmptyState
import id.skmnetwork.bukuwarung.ui.components.ProductFilterState
import id.skmnetwork.bukuwarung.ui.components.ProductImageFrameSize
import id.skmnetwork.bukuwarung.ui.components.ProductImagePlaceholderIconSize
import id.skmnetwork.bukuwarung.ui.components.ProductImageThumbnail
import id.skmnetwork.bukuwarung.ui.theme.AppShapes
import id.skmnetwork.bukuwarung.ui.components.ProductSearchField
import id.skmnetwork.bukuwarung.ui.components.buildCategoryChipNames
import id.skmnetwork.bukuwarung.ui.components.filterProductsBySearch
import id.skmnetwork.bukuwarung.ui.product.ProductViewModel
import id.skmnetwork.bukuwarung.ui.supplier.SupplierViewModel
import id.skmnetwork.bukuwarung.util.formatQuantityValue
import id.skmnetwork.bukuwarung.ui.theme.AppColors
import id.skmnetwork.bukuwarung.ui.theme.rememberAppWindowSize
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
    val dbCategories by viewModel.categories.collectAsStateWithLifecycle()
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

    // Step 6 - discovery for the restock list. The list used to be the whole catalogue in whatever
    // order the query returned, so finding one product meant scrolling past every other one.
    // These controls reuse the Products screen's search field, category chips and empty state, and
    // the same pure filter, so "search" means one thing in the app. Filtering is read-only: it
    // never touches stock, the purchase cart or any persisted state.
    var productQuery by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }
    var lowStockOnly by remember { mutableStateOf(false) }

    val selectedCategoryObj = dbCategories.find { it.id == selectedCategoryId }
    val productFilter = ProductFilterState(
        query = productQuery,
        selectedCategoryId = selectedCategoryId,
        lowStockOnly = lowStockOnly
    )
    val restockProducts = filterProductsBySearch(dbProducts, productFilter)
    val categoryChipNames = buildCategoryChipNames(dbCategories, selectedCategoryId)

    var paymentMethod by remember { mutableStateOf("CASH") } // "CASH" or "CREDIT"
    var selectedSupplierForCredit by remember { mutableStateOf<SupplierEntity?>(null) }
    var selectedSupplierForCash by remember { mutableStateOf<SupplierEntity?>(null) }
    var showSupplierPickerSheet by remember { mutableStateOf(false) }
    var isPickerForCash by remember { mutableStateOf(false) }

    var selectedPurchaseForDetail by remember { mutableStateOf<PurchaseTransactionEntity?>(null) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    // Step 2 (E): saving a purchase increases stock and may create a supplier payable. Confirmed.
    var showPurchaseConfirmDialog by remember { mutableStateOf(false) }
    var pendingPurchaseMethod by remember { mutableStateOf("CASH") }
    val context = LocalContext.current

    val currentMonthYear = remember {
        SimpleDateFormat("MMMM yyyy", Locale("id", "ID")).format(Date())
    }

    val totalPurchaseAmount = purchaseCart.entries.sumOf { (prodId, qty) ->
        val prod = dbProducts.find { it.id == prodId }
        MoneyCalculator.lineSubtotal(prod?.purchasePrice ?: 0L, qty)
    }

    val windowSize = rememberAppWindowSize()

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
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(AppColors.SuccessContainer),
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
                    Text("Pilih $supplierLabel", fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
                                    shape = AppShapes.CardShape,
                                    color = if (isSelected) AppColors.SuccessContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(1.dp, if (isSelected) AppColors.GreenPrimary else MaterialTheme.colorScheme.outline),
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
    if (showPurchaseConfirmDialog) {
        val isCredit = pendingPurchaseMethod == "CREDIT"
        AlertDialog(
            onDismissRequest = { if (!isSaving) showPurchaseConfirmDialog = false },
            title = {
                Text(
                    text = "Simpan $purchaseLabel?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = if (isCredit) {
                            "$purchaseLabel kredit ini akan menambah stok produk dan mencatat " +
                                "hutang ke $supplierLabel. Tindakan ini tidak dapat dibatalkan."
                        } else {
                            "$purchaseLabel ini akan menambah stok produk secara permanen. " +
                                "Tindakan ini tidak dapat dibatalkan."
                        },
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Total: ${formatRupiah(totalPurchaseAmount)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = AppColors.GreenDark
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${purchaseCart.size} produk",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = AppColors.TextSecondary
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPurchaseConfirmDialog = false
                        isSaving = true
                        if (pendingPurchaseMethod == "CASH") {
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
                ) {
                    Text(
                        text = if (isSaving) "Menyimpan..." else "Simpan $purchaseLabel",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showPurchaseConfirmDialog = false }, enabled = !isSaving) {
                    Text("Batal", fontSize = 13.sp)
                }
            }
        )
    }

    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showSuccessDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(AppColors.SuccessContainer),
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
                    Text("$purchaseLabel Berhasil!", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Text(
                    "Stok $productLabel telah otomatis bertambah dan status transaksi $purchaseLabel berhasil dicatat.",
                    fontSize = 13.sp,
                    color = AppColors.TextPrimary
                )
            },
            confirmButton = {
                Button(
                    onClick = { showSuccessDialog = false },
                    shape = AppShapes.CardShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.GreenPrimary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Selesai", fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                        color = if (purchase.paymentMethod == "CREDIT") MaterialTheme.colorScheme.errorContainer else AppColors.SuccessContainer
                    ) {
                        Text(
                            text = if (purchase.paymentMethod == "CREDIT") debtLabel.uppercase() else "TUNAI",
                            color = if (purchase.paymentMethod == "CREDIT") MaterialTheme.colorScheme.error else AppColors.GreenPrimary,
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

                    HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline)

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
                                            "${formatQuantityValue(item.quantity)} x ${formatRupiah(item.purchasePrice)}",
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

                    HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline)

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
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            if (selectedTab == 0) {
                // ==========================================
                // TAB 0: BELANJA BARU (RESTOCK / PURCHASE)
                // ==========================================
                Column(
                    modifier = Modifier
                        // Gate 1.7: was fillMaxSize() then widthIn(max = contentMaxWidth) - the fixed
                        // width defeated the cap, so this column ran full-bleed on tablets.
                        .fillMaxHeight()
                        .widthIn(max = windowSize.contentMaxWidth)
                ) {
                    // Month / Period Header Card
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, AppColors.CardBorderStrong),
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
                                    fontSize = 13.sp,
                                    color = AppColors.TextPrimary
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
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

                    // Step 6: search + filters, only when the catalogue actually has products.
                    if (dbProducts.isNotEmpty() && (dbCategories.isNotEmpty() || lowStockOnly)) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(bottom = 12.dp)
                        ) {
                            ProductSearchField(
                                query = productQuery,
                                onQueryChange = { productQuery = it },
                                productLabel = productLabel
                            )
                            Spacer(Modifier.height(4.dp))
                            ProductCategoryChips(
                                categoryNames = categoryChipNames,
                                selectedCategoryName = selectedCategoryObj?.name ?: ALL_CATEGORIES_LABEL,
                                onSelectCategory = { name ->
                                    // Selecting the same category again clears it, so a merchant can
                                    // always get back to the whole list without hunting for a chip.
                                    selectedCategoryId =
                                        if (name == ALL_CATEGORIES_LABEL) null
                                        else if (name == selectedCategoryObj?.name) null
                                        else dbCategories.firstOrNull { it.name == name }?.id
                                },
                                toggleLabel = "Stok Menipis",
                                isToggleActive = lowStockOnly,
                                onToggle = { lowStockOnly = !lowStockOnly }
                            )
                        }
                    }

                    if (dbProducts.isEmpty()) {
                        // Empty State when no products exist in catalog
                        PurchaseEmptyProductsState(
                            productLabel = productLabel,
                            onAddProduct = onNavigateToAddProduct
                        )
                    } else if (restockProducts.isEmpty()) {
                        // Search / filter excluded everything. The catalogue is fine; the query is not.
                        ProductFilterEmptyState(
                            productLabel = productLabel,
                            query = productQuery,
                            onResetFilter = {
                                productQuery = ""
                                selectedCategoryId = null
                                lowStockOnly = false
                            }
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
                            items(restockProducts, key = { it.id }) { product ->
                                val qty = purchaseCart[product.id] ?: 0.0
                                val itemSubtotal = MoneyCalculator.lineSubtotal(product.purchasePrice, qty)

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
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 8.dp,
                        border = BorderStroke(1.dp, AppColors.CardBorderStrong),
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
                                        fontSize = 11.sp,
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
                                    color = if (paymentMethod == "CASH") AppColors.GreenPrimary else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .clickable { paymentMethod = "CASH" }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "Tunai (CASH)",
                                            // Gate 1.8: was Color.White - 2.28:1 on the dark-theme green.
                                            color = if (paymentMethod == "CASH") MaterialTheme.colorScheme.onPrimary else AppColors.TextSecondary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                }

                                // Credit Button
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (paymentMethod == "CREDIT") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .clickable { paymentMethod = "CREDIT" }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "$debtLabel $supplierLabel",
                                            // Gate 1.8: was Color.White. error is #FF8A85 in the
                                            // dark theme, and white on it measures 2.09:1.
                                            color = if (paymentMethod == "CREDIT") MaterialTheme.colorScheme.onError else AppColors.TextSecondary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }

                            // Supplier Selector Pill
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(
                                    1.dp,
                                    if (paymentMethod == "CREDIT" && selectedSupplierForCredit == null) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.outline
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
                                        tint = if (paymentMethod == "CREDIT" && selectedSupplierForCredit == null) MaterialTheme.colorScheme.error else AppColors.GreenPrimary,
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
                                            paymentMethod == "CREDIT" && selectedSupplierForCredit == null -> MaterialTheme.colorScheme.error
                                            paymentMethod == "CASH" && selectedSupplierForCash == null -> AppColors.TextSecondary
                                            else -> AppColors.TextPrimary
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (paymentMethod == "CASH" && selectedSupplierForCash != null) {
                                        // Gate 1.7: was size(24.dp), so the control that clears the
                                        // chosen supplier - a destructive action on a primary checkout
                                        // field - had a 24dp target. The glyph stays 16dp.
                                        IconButton(
                                            onClick = { selectedSupplierForCash = null },
                                            modifier = Modifier.size(48.dp)
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
                                    // Step 2 (E): saving a purchase permanently increases stock and, for a
                                    // credit purchase, records a supplier payable. There is no undo, so the
                                    // merchant confirms the consequence first.
                                    pendingPurchaseMethod = paymentMethod
                                    showPurchaseConfirmDialog = true
                                },
                                enabled = !isSaving,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (paymentMethod == "CREDIT") MaterialTheme.colorScheme.error else AppColors.GreenPrimary,
                                    // Gate 1.8: was Color.White. The container is conditional, so a
                                    // fixed white foreground is only correct on the green branch:
                                    // error is #FF8A85 in the dark theme and white on it measures
                                    // 2.09:1. The foreground now tracks the container it sits on.
                                    contentColor = if (paymentMethod == "CREDIT") {
                                        MaterialTheme.colorScheme.onError
                                    } else {
                                        MaterialTheme.colorScheme.onPrimary
                                    }
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
                                        fontSize = 15.sp
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
                    Box(
                        modifier = Modifier
                            // Gate 1.7: fillMaxSize() fixed the incoming width, so the widthIn cap
                            // could not narrow it. fillMaxHeight keeps the full-height behaviour and
                            // lets the cap apply on tablets; this Box already centres with TopCenter.
                            .fillMaxHeight()
                            .widthIn(max = windowSize.contentMaxWidth),
                        contentAlignment = Alignment.TopCenter
                    ) {
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
                    }
                } else {
                    Box(
                        modifier = Modifier
                            // Gate 1.7: same dead width cap - see the poViewModel branch above.
                            .fillMaxHeight()
                            .widthIn(max = windowSize.contentMaxWidth),
                        contentAlignment = Alignment.TopCenter
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
                    Box(
                        modifier = Modifier
                            // Gate 1.7: same dead width cap - see the poViewModel branch above.
                            .fillMaxHeight()
                            .widthIn(max = windowSize.contentMaxWidth),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        PurchaseEmptyHistoryState(
                            purchaseLabel = purchaseLabel,
                            onNewPurchase = { selectedTab = 0 }
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 10.dp,
                            bottom = 88.dp
                        ),
                        modifier = Modifier
                            // Gate 1.7: same dead width cap - see the poViewModel branch above.
                            .fillMaxHeight()
                            .widthIn(max = windowSize.contentMaxWidth)
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
        color = MaterialTheme.colorScheme.surface,
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
                            // Gate 1.8: was Color.White on the GreenPrimary badge - 2.28:1 in dark.
                            tint = MaterialTheme.colorScheme.onPrimary,
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
                    color = AppColors.SuccessContainer
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
                color = MaterialTheme.colorScheme.surfaceVariant,
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
                                tint = if (selectedTab == 0) MaterialTheme.colorScheme.onPrimary else AppColors.TextSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "$purchaseLabel Baru",
                                color = if (selectedTab == 0) MaterialTheme.colorScheme.onPrimary else AppColors.TextSecondary,
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp,
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
                                tint = if (selectedTab == 1) MaterialTheme.colorScheme.onPrimary else AppColors.TextSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Pesanan $supplierLabel",
                                color = if (selectedTab == 1) MaterialTheme.colorScheme.onPrimary else AppColors.TextSecondary,
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp,
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
                                tint = if (selectedTab == 2) MaterialTheme.colorScheme.onPrimary else AppColors.TextSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Riwayat",
                                color = if (selectedTab == 2) MaterialTheme.colorScheme.onPrimary else AppColors.TextSecondary,
                                fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp,
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
        shape = AppShapes.CardShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (isSelected) AppColors.GreenPrimary.copy(alpha = 0.4f) else AppColors.CardBorderStrong),
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
                    .size(ProductImageFrameSize)
                    .clip(AppShapes.CardShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                ProductImageThumbnail(
                    imageUri = product.imageUri,
                    placeholderIconSize = ProductImagePlaceholderIconSize,
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
                        fontSize = 15.sp,
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
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        IconButton(
                            onClick = onDecrease,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = "Kurangi",
                                tint = if (quantity > 0) AppColors.TextPrimary else AppColors.TextSecondary.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Text(
                            text = formatQuantityValue(quantity),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (quantity > 0) AppColors.GreenPrimary else AppColors.TextPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )

                        IconButton(
                            onClick = onIncrease,
                            modifier = Modifier.size(48.dp)
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
                        fontSize = 11.sp,
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
                        modifier = Modifier.size(48.dp)
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
        shape = AppShapes.CardShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                        if (isCredit) MaterialTheme.colorScheme.errorContainer else AppColors.SuccessContainer,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Receipt,
                    contentDescription = null,
                    tint = if (isCredit) MaterialTheme.colorScheme.error else AppColors.GreenPrimary,
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
                        fontSize = 14.sp,
                        color = AppColors.TextPrimary,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isCredit) MaterialTheme.colorScheme.errorContainer else AppColors.SuccessContainer
                    ) {
                        Text(
                            text = if (isCredit) debtLabel.uppercase() else "TUNAI",
                            color = if (isCredit) MaterialTheme.colorScheme.error else AppColors.GreenPrimary,
                            fontSize = 11.sp,
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
                    fontSize = 13.sp
                )

                Text(
                    text = dateStr,
                    color = AppColors.TextSecondary,
                    fontSize = 11.sp
                )
            }

            Text(
                text = formatRupiah(purchase.totalAmount),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = if (isCredit) MaterialTheme.colorScheme.error else AppColors.GreenPrimary,
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
                    .background(AppColors.SuccessContainer, CircleShape),
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
                    fontSize = 16.sp,
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
                shape = AppShapes.CardShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.GreenPrimary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.height(48.dp)
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
                    fontSize = 13.sp
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
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
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
                shape = AppShapes.CardShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.GreenPrimary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.height(48.dp)
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
                    fontSize = 13.sp
                )
            }
        }
    }
}
