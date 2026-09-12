package id.skmnetwork.bukuwarung.ui.product

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.skmnetwork.bukuwarung.ui.components.AppCard
import id.skmnetwork.bukuwarung.ui.components.AppEmptyState
import id.skmnetwork.bukuwarung.ui.components.AppTextField
import id.skmnetwork.bukuwarung.ui.components.ProductImageThumbnail
import id.skmnetwork.bukuwarung.ui.theme.AppColors
import id.skmnetwork.bukuwarung.ui.theme.AppShapes
import id.skmnetwork.bukuwarung.ui.theme.AppSpacing
import id.skmnetwork.bukuwarung.util.formatRupiah

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsScreen(
    viewModel: ProductViewModel,
    onAddProduct: () -> Unit = {},
    onEditProduct: (Long) -> Unit = {}
) {
    val dbProducts by viewModel.products.collectAsStateWithLifecycle()
    val dbCategories by viewModel.categories.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var selectedCategoryName by remember { mutableStateOf("Semua") }

    val selectedCategoryObj = dbCategories.find { it.name.equals(selectedCategoryName, ignoreCase = true) }

    val filteredProducts = dbProducts.filter { product ->
        val matchesQuery = product.name.contains(query, ignoreCase = true)
        val matchesCategory = selectedCategoryName == "Semua" ||
                (selectedCategoryObj != null && product.categoryId == selectedCategoryObj.id)
        matchesQuery && matchesCategory
    }

    val categoryChipNames = listOf("Semua") + dbCategories.map { it.name }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Produk & Stok", fontWeight = FontWeight.Bold) }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddProduct,
                containerColor = AppColors.GreenPrimary,
                contentColor = Color.White,
                shape = AppShapes.PillShape
            ) {
                Icon(Icons.Default.Add, "Tambah")
                Spacer(Modifier.width(AppSpacing.sm))
                Text("Tambah Produk", fontWeight = FontWeight.Bold)
            }
        },
        containerColor = Color.White
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(Modifier.padding(horizontal = AppSpacing.lg)) {
                AppTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = "Cari produk..."
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
                    title = "Belum ada produk",
                    actionText = "+ Tambah Produk",
                    onActionClick = onAddProduct,
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = AppSpacing.lg)
                ) {
                    items(filteredProducts, key = { it.id }) { product ->
                        ProductRowWithMoreOptions(
                            name = product.name,
                            price = product.sellingPrice,
                            stock = product.stock,
                            unit = product.unit,
                            imageUri = product.imageUri,
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

@Composable
fun ProductRowWithMoreOptions(
    name: String,
    price: Long,
    stock: Double,
    unit: String = "pcs",
    imageUri: String? = null,
    onEdit: () -> Unit = {}
) {
    AppCard(onClick = onEdit) {
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
                ProductImageThumbnail(
                    imageUri = imageUri,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(Modifier.width(AppSpacing.md))
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.SemiBold)
                Text(formatRupiah(price), color = AppColors.TextSecondary)
                Text("Stok: ${stock.toInt()} $unit", style = MaterialTheme.typography.labelSmall)
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
                ProductImageThumbnail(
                    imageUri = imageUri,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(Modifier.width(AppSpacing.md))
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.SemiBold)
                Text(formatRupiah(price), color = AppColors.TextSecondary)
                Text("Stok: ${stock.toInt()} $unit", style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = onAdd) {
                Icon(Icons.Default.Add, "Tambah", tint = AppColors.GreenPrimary)
            }
        }
    }
}
