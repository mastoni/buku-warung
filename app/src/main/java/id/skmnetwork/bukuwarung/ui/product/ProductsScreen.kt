package id.skmnetwork.bukuwarung.ui.product

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.skmnetwork.bukuwarung.data.local.entity.ItemType
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.data.preferences.UserSettings
import id.skmnetwork.bukuwarung.domain.business.BusinessTaxonomyRegistry
import id.skmnetwork.bukuwarung.ui.components.ProductImageThumbnail
import id.skmnetwork.bukuwarung.ui.theme.AppColors
import id.skmnetwork.bukuwarung.util.formatRupiah

@Composable
fun ProductsScreen(
    viewModel: ProductViewModel,
    userSettings: UserSettings? = null,
    onAddProduct: () -> Unit = {},
    onEditProduct: (Long) -> Unit = {},
    onNavigateToCatalog: () -> Unit = {}
) {
    val resolvedProfile = remember(userSettings?.primaryBusinessType, userSettings?.secondaryActivities) {
        BusinessTaxonomyRegistry.resolve(
            primaryType = userSettings?.primaryBusinessType,
            secondaryActivities = userSettings?.secondaryActivities
        )
    }
    val productLabel = resolvedProfile.terminology.productLabel

    val dbProducts by viewModel.products.collectAsStateWithLifecycle()
    val dbCategories by viewModel.categories.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var selectedCategoryName by remember { mutableStateOf("Semua") }

    val selectedCategoryObj = dbCategories.find { it.name.equals(selectedCategoryName, ignoreCase = true) }

    val filteredProducts = dbProducts.filter { product ->
        val matchesQuery = product.name.contains(query, ignoreCase = true) ||
                (!product.barcode.isNullOrBlank() && product.barcode.contains(query, ignoreCase = true))
        val matchesCategory = selectedCategoryName == "Semua" ||
                (selectedCategoryObj != null && product.categoryId == selectedCategoryObj.id)
        matchesQuery && matchesCategory
    }

    val categoryChipNames = listOf("Semua") + dbCategories.map { it.name }

    Scaffold(
        topBar = {
            ProductsHeader(
                productLabel = productLabel,
                productCount = dbProducts.size,
                onNavigateToCatalog = onNavigateToCatalog
            )
        },
        floatingActionButton = {
            // Only show FAB when products are available to prevent double CTA in empty state
            if (dbProducts.isNotEmpty()) {
                Surface(
                    onClick = onAddProduct,
                    shape = RoundedCornerShape(22.dp),
                    color = AppColors.GreenPrimary,
                    contentColor = Color.White,
                    shadowElevation = 3.dp,
                    modifier = Modifier.height(44.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Tambah $productLabel",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Tambah $productLabel",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.5.sp,
                            color = Color.White
                        )
                    }
                }
            }
        },
        containerColor = Color(0xFFFBFDFB)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ==========================================
            // SEARCH & CATEGORY FILTER AREA
            // ==========================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(bottom = 12.dp)
            ) {
                // Search Input
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = {
                        Text(
                            text = "Cari $productLabel / barcode...",
                            fontSize = 13.5.sp,
                            color = AppColors.TextSecondary
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Cari",
                            tint = AppColors.TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Hapus",
                                    tint = AppColors.TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFFF9FAF9),
                        unfocusedContainerColor = Color(0xFFF9FAF9),
                        focusedBorderColor = AppColors.GreenPrimary,
                        unfocusedBorderColor = Color(0xFFE2E8F0)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .height(52.dp)
                )

                // Category Filter Chips
                if (categoryChipNames.size > 1) {
                    Spacer(Modifier.height(4.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(categoryChipNames.distinct()) { catName ->
                            val isSelected = catName == selectedCategoryName
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isSelected) AppColors.GreenPrimary else Color.White,
                                border = if (isSelected) null else BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                shadowElevation = if (isSelected) 1.dp else 0.dp,
                                onClick = { selectedCategoryName = catName }
                            ) {
                                Text(
                                    text = catName,
                                    color = if (isSelected) Color.White else AppColors.TextSecondary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ==========================================
            // CONTENT: PRODUCT LIST OR EMPTY STATE
            // ==========================================
            if (dbProducts.isEmpty()) {
                // Global Empty State (No products added yet)
                ProductsEmptyState(
                    productLabel = productLabel,
                    onAddProduct = onAddProduct
                )
            } else if (filteredProducts.isEmpty()) {
                // Filter / Search Empty State
                ProductsSearchEmptyState(
                    productLabel = productLabel,
                    query = query,
                    onResetFilter = {
                        query = ""
                        selectedCategoryName = "Semua"
                    }
                )
            } else {
                // Populated Product List
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 10.dp,
                        bottom = 88.dp // Space for FAB & Bottom Navigation
                    ),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredProducts, key = { it.id }) { product ->
                        ProductItemCard(
                            product = product,
                            serviceLabel = resolvedProfile.terminology.serviceLabel,
                            onEdit = {
                                if (product.id > 0) {
                                    onEditProduct(product.id)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Top Header for Products & Stock screen, harmonized with Home and Kasir.
 */
@Composable
private fun ProductsHeader(
    productLabel: String,
    productCount: Int,
    onNavigateToCatalog: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shadowElevation = 0.5.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
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
                        imageVector = Icons.Default.Inventory2,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column {
                    Text(
                        text = "$productLabel & Stok",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.5.sp,
                            color = AppColors.TextPrimary
                        )
                    )
                    Text(
                        text = "Katalog & manajemen stok",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.5.sp,
                            color = AppColors.TextSecondary
                        )
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (productCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFFE8F5E9)
                    ) {
                        Text(
                            text = "$productCount $productLabel",
                            color = AppColors.GreenPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.5.sp,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                }

                // WhatsApp Catalog Button
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFE8F5E9),
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("btn_products_catalog"),
                    onClick = onNavigateToCatalog
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Katalog WhatsApp",
                            tint = AppColors.GreenPrimary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Clean & rounded Product Card component with stock status badges.
 */
@Composable
private fun ProductItemCard(
    product: ProductEntity,
    serviceLabel: String = "Layanan",
    onEdit: () -> Unit
) {
    val stock = product.stock
    val minStock = product.minimumStock

    Surface(
        onClick = onEdit,
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
            // Product Thumbnail Image
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

            // Product Details
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

                Spacer(Modifier.height(3.dp))

                Text(
                    text = formatRupiah(product.sellingPrice),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = AppColors.GreenPrimary
                    )
                )

                Spacer(Modifier.height(4.dp))

                // Stock Status Pill / Non-Stock Identity Badge
                StockStatusBadge(
                    stock = stock,
                    minStock = minStock,
                    unit = product.unit,
                    itemType = product.itemType,
                    serviceLabel = serviceLabel
                )
            }

            // Edit / Action Menu Button
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Opsi Produk",
                    tint = AppColors.TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Stock Status indicator pill (Out of stock, Low stock, Normal) for stockable items,
 * or non-stock identity badge for DIGITAL and SERVICE items.
 */
@Composable
private fun StockStatusBadge(
    stock: Double,
    minStock: Double,
    unit: String,
    itemType: String = ItemType.PHYSICAL.name,
    serviceLabel: String = "Layanan"
) {
    when (itemType) {
        ItemType.SERVICE.name -> {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFFE8F3FF)
            ) {
                Text(
                    text = serviceLabel,
                    color = Color(0xFF096DD9),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        ItemType.DIGITAL.name -> {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFFF6FFED)
            ) {
                Text(
                    text = "Produk Digital",
                    color = Color(0xFF389E0D),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        else -> {
            val displayStock = if (stock % 1.0 == 0.0) stock.toInt().toString() else stock.toString()

            when {
                stock <= 0 -> {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFFFEBEE)
                    ) {
                        Text(
                            text = "Stok Habis",
                            color = Color(0xFFD32F2F),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                minStock > 0 && stock <= minStock -> {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFFFF3E0)
                    ) {
                        Text(
                            text = "Sisa $displayStock $unit (Menipis)",
                            color = Color(0xFFE65100),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                else -> {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFF1F5F2)
                    ) {
                        Text(
                            text = "Stok: $displayStock $unit",
                            color = AppColors.TextSecondary,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Single clean empty state without duplicate buttons.
 */
@Composable
private fun ProductsEmptyState(
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
                text = "Belum Ada $productLabel",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.5.sp,
                    color = AppColors.TextPrimary
                )
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "Tambahkan $productLabel jualan untuk mulai mencatat stok dan transaksi kasir.",
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
                    text = "Tambah $productLabel Pertama",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp
                )
            }
        }
    }
}

/**
 * Empty state when search or filter returns zero matches.
 */
@Composable
private fun ProductsSearchEmptyState(
    productLabel: String = "Produk",
    query: String,
    onResetFilter: () -> Unit
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
                    .size(64.dp)
                    .background(Color(0xFFF1F5F2), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SearchOff,
                    contentDescription = null,
                    tint = AppColors.TextSecondary,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = "$productLabel Tidak Ditemukan",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.5.sp,
                    color = AppColors.TextPrimary
                )
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = if (query.isNotEmpty()) "Tidak ada $productLabel yang cocok dengan \"$query\"" else "Tidak ada $productLabel di kategori ini",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.5.sp,
                    color = AppColors.TextSecondary,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onResetFilter,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE8F5E9),
                    contentColor = AppColors.GreenPrimary
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                modifier = Modifier.height(38.dp)
            ) {
                Text(
                    text = "Reset Pencarian",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp
                )
            }
        }
    }
}

/**
 * Legacy composables preserved for backward compatibility.
 */
@Composable
fun ProductRowWithMoreOptions(
    name: String,
    price: Long,
    stock: Double,
    unit: String = "pcs",
    imageUri: String? = null,
    onEdit: () -> Unit = {}
) {
    Surface(
        onClick = onEdit,
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFEFF3F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFF4F7F4)),
                contentAlignment = Alignment.Center
            ) {
                ProductImageThumbnail(
                    imageUri = imageUri,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp)
                Text(formatRupiah(price), color = AppColors.GreenPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Stok: ${if (stock % 1.0 == 0.0) stock.toInt().toString() else stock.toString()} $unit", style = MaterialTheme.typography.labelSmall, color = AppColors.TextSecondary)
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.MoreVert, "Opsi", tint = AppColors.TextSecondary)
            }
        }
    }
}

@Composable
fun ProductRow(
    name: String,
    price: Long,
    stock: Double,
    unit: String = "pcs",
    imageUri: String? = null,
    onAdd: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFEFF3F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFF4F7F4)),
                contentAlignment = Alignment.Center
            ) {
                ProductImageThumbnail(
                    imageUri = imageUri,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp)
                Text(formatRupiah(price), color = AppColors.GreenPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Stok: ${if (stock % 1.0 == 0.0) stock.toInt().toString() else stock.toString()} $unit", style = MaterialTheme.typography.labelSmall, color = AppColors.TextSecondary)
            }
            IconButton(onClick = onAdd) {
                Icon(Icons.Default.Add, "Tambah", tint = AppColors.GreenPrimary)
            }
        }
    }
}
