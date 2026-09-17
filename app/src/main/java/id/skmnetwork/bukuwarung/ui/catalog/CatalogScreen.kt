package id.skmnetwork.bukuwarung.ui.catalog

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.skmnetwork.bukuwarung.catalog.WhatsAppCatalogFormatter
import id.skmnetwork.bukuwarung.catalog.WhatsAppCatalogShareHelper
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.data.preferences.UserSettings
import id.skmnetwork.bukuwarung.domain.business.BusinessTaxonomyRegistry
import id.skmnetwork.bukuwarung.ui.components.AppCard
import id.skmnetwork.bukuwarung.ui.components.AppEmptyState
import id.skmnetwork.bukuwarung.ui.components.AppTextField
import id.skmnetwork.bukuwarung.ui.components.ProductImageThumbnail
import id.skmnetwork.bukuwarung.ui.product.ProductViewModel
import id.skmnetwork.bukuwarung.ui.theme.AppColors
import id.skmnetwork.bukuwarung.ui.theme.AppShapes
import id.skmnetwork.bukuwarung.ui.theme.AppSpacing
import id.skmnetwork.bukuwarung.util.formatRupiah

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    viewModel: ProductViewModel,
    userSettings: UserSettings,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val dbProducts by viewModel.products.collectAsStateWithLifecycle()
    val dbCategories by viewModel.categories.collectAsStateWithLifecycle()

    val resolvedProfile = remember(userSettings.primaryBusinessType, userSettings.secondaryActivities) {
        BusinessTaxonomyRegistry.resolve(
            primaryType = userSettings.primaryBusinessType,
            secondaryActivities = userSettings.secondaryActivities
        )
    }
    val productLabel = resolvedProfile.terminology.productLabel

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryName by remember { mutableStateOf("Semua") }
    var selectedProductIds by remember { mutableStateOf(setOf<Long>()) }
    var showPreviewDialog by remember { mutableStateOf(false) }
    var includeStockInCatalog by remember { mutableStateOf(false) }

    val selectedCategoryObj = dbCategories.find { it.name.equals(selectedCategoryName, ignoreCase = true) }

    val filteredProducts = dbProducts.filter { product ->
        val matchesQuery = product.name.contains(searchQuery, ignoreCase = true)
        val matchesCategory = selectedCategoryName == "Semua" ||
                (selectedCategoryObj != null && product.categoryId == selectedCategoryObj.id)
        matchesQuery && matchesCategory
    }

    val categoryChipNames = listOf("Semua") + dbCategories.map { it.name }
    val allFilteredSelected = filteredProducts.isNotEmpty() && filteredProducts.all { it.id in selectedProductIds }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Katalog $productLabel", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("btn_catalog_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    if (filteredProducts.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                selectedProductIds = if (allFilteredSelected) {
                                    selectedProductIds - filteredProducts.map { it.id }.toSet()
                                } else {
                                    selectedProductIds + filteredProducts.map { it.id }.toSet()
                                }
                            },
                            modifier = Modifier.testTag("btn_catalog_toggle_all")
                        ) {
                            Text(
                                text = if (allFilteredSelected) "Batal Semua" else "Pilih Semua",
                                fontWeight = FontWeight.Bold,
                                color = AppColors.GreenPrimary
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                color = Color.White,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppSpacing.lg),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${selectedProductIds.size} $productLabel dipilih",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.testTag("txt_catalog_selected_count")
                        )
                        Text(
                            text = "Siap dibagikan ke WhatsApp",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.TextSecondary
                        )
                    }

                    Button(
                        onClick = { showPreviewDialog = true },
                        enabled = selectedProductIds.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppColors.GreenPrimary,
                            disabledContainerColor = AppColors.SurfaceGray
                        ),
                        shape = AppShapes.PillShape,
                        modifier = Modifier.testTag("btn_catalog_preview_share")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(AppSpacing.sm))
                        Text("Pratinjau & Bagikan", fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        containerColor = Color.White
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search Bar & Categories
            Column(Modifier.padding(horizontal = AppSpacing.lg)) {
                AppTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = "Cari $productLabel untuk katalog...",
                    modifier = Modifier.testTag("input_catalog_search")
                )

                if (categoryChipNames.size > 1) {
                    Spacer(Modifier.height(AppSpacing.sm))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(categoryChipNames.distinct()) { catName ->
                            val isSelected = catName == selectedCategoryName
                            Surface(
                                shape = AppShapes.ChipShape,
                                color = if (isSelected) AppColors.GreenPrimary else AppColors.SurfaceGray,
                                modifier = Modifier.clickable { selectedCategoryName = catName }
                            ) {
                                Text(
                                    text = catName,
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
                    title = "Belum ada $productLabel",
                    description = "Tambahkan $productLabel terlebih dahulu sebelum membuat katalog WhatsApp.",
                    actionText = null,
                    onActionClick = null,
                    modifier = Modifier.weight(1f)
                )
            } else if (filteredProducts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$productLabel tidak ditemukan",
                        color = AppColors.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                    contentPadding = PaddingValues(bottom = AppSpacing.lg),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = AppSpacing.lg)
                        .testTag("list_catalog_products")
                ) {
                    items(filteredProducts, key = { it.id }) { product ->
                        val isSelected = product.id in selectedProductIds
                        CatalogProductItemRow(
                            product = product,
                            isSelected = isSelected,
                            onToggle = {
                                selectedProductIds = if (isSelected) {
                                    selectedProductIds - product.id
                                } else {
                                    selectedProductIds + product.id
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showPreviewDialog) {
        val selectedProductsList = dbProducts.filter { it.id in selectedProductIds }
        val catalogText = WhatsAppCatalogFormatter.formatCatalogText(
            shopName = userSettings.shopName,
            ownerName = userSettings.ownerName,
            phone = userSettings.phone,
            address = userSettings.address,
            products = selectedProductsList,
            includeStock = includeStockInCatalog,
            productLabel = productLabel
        )

        CatalogPreviewDialog(
            catalogText = catalogText,
            productCount = selectedProductsList.size,
            productLabel = productLabel,
            includeStock = includeStockInCatalog,
            onToggleStock = { includeStockInCatalog = it },
            onShare = {
                showPreviewDialog = false
                val shopDisplay = userSettings.shopName.trim().ifBlank { "Toko Kami" }
                WhatsAppCatalogShareHelper.shareCatalog(
                    context = context,
                    catalogText = catalogText,
                    chooserTitle = "Bagikan Katalog $productLabel ($shopDisplay)"
                )
            },
            onDismiss = { showPreviewDialog = false }
        )
    }
}

@Composable
fun CatalogProductItemRow(
    product: ProductEntity,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    AppCard(
        onClick = onToggle,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("item_catalog_product_${product.id}")
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(AppSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = AppColors.GreenPrimary,
                    checkmarkColor = Color.White
                )
            )

            Spacer(Modifier.width(AppSpacing.sm))

            Box(
                Modifier
                    .size(46.dp)
                    .background(AppColors.GreenLight, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                ProductImageThumbnail(
                    imageUri = product.imageUri,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(Modifier.width(AppSpacing.md))

            Column(Modifier.weight(1f)) {
                Text(
                    text = product.name,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = formatRupiah(product.sellingPrice),
                    fontWeight = FontWeight.Bold,
                    color = AppColors.GreenPrimary
                )
                val stockText = if (product.stock % 1.0 == 0.0) {
                    product.stock.toInt().toString()
                } else {
                    product.stock.toString()
                }
                val unitText = product.unit.trim().ifBlank { "pcs" }
                Text(
                    text = "Stok: $stockText $unitText",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.TextSecondary
                )
            }
        }
    }
}

@Composable
fun CatalogPreviewDialog(
    catalogText: String,
    productCount: Int,
    productLabel: String = "Produk",
    includeStock: Boolean,
    onToggleStock: (Boolean) -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    tint = AppColors.GreenPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(AppSpacing.sm))
                Text(
                    text = "Pratinjau Katalog ($productCount $productLabel)",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Toggle Include Stock
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = AppSpacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tampilkan jumlah stok",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Switch(
                        checked = includeStock,
                        onCheckedChange = onToggleStock,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AppColors.GreenPrimary
                        ),
                        modifier = Modifier.testTag("switch_catalog_stock")
                    )
                }

                // Chat bubble preview
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AppColors.GreenLight.copy(alpha = 0.35f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(AppSpacing.md)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = catalogText,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Black,
                            modifier = Modifier.testTag("txt_catalog_preview_content")
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onShare,
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.GreenPrimary),
                shape = AppShapes.PillShape,
                modifier = Modifier.testTag("btn_catalog_confirm_share")
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(AppSpacing.xs))
                Text("Bagikan Sekarang", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("btn_catalog_cancel")) {
                Text("Tutup", color = AppColors.TextSecondary)
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(16.dp)
    )
}
