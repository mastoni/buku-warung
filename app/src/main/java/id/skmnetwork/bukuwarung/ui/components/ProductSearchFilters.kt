package id.skmnetwork.bukuwarung.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.skmnetwork.bukuwarung.data.local.entity.CategoryEntity
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.ui.theme.AppColors

/**
 * Step 6 - the product search and category filter, shared by the screens that let a merchant pick
 * a product from the catalogue.
 *
 * This was lifted out of the Products screen verbatim rather than re-authored, so the Purchase
 * screen's discovery controls are the same control, not a lookalike: same field, same clear icon,
 * same chip, same empty state, same tokens.
 */
@Composable
fun ProductSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    productLabel: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
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
                IconButton(onClick = { onQueryChange("") }) {
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
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedBorderColor = AppColors.GreenPrimary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .height(52.dp)
    )
}

/**
 * The category chips, plus an optional trailing toggle.
 *
 * The toggle reuses the chip shape and active state exactly. It exists so a screen can offer the
 * app's own "low stock" predicate without inventing a second visual language.
 */
@Composable
fun ProductCategoryChips(
    categoryNames: List<String>,
    selectedCategoryName: String,
    onSelectCategory: (String) -> Unit,
    modifier: Modifier = Modifier,
    toggleLabel: String? = null,
    isToggleActive: Boolean = false,
    onToggle: (() -> Unit)? = null
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        items(categoryNames.distinct()) { catName ->
            val isSelected = catName == selectedCategoryName
            ProductFilterChip(
                label = catName,
                isActive = isSelected,
                onClick = { onSelectCategory(catName) }
            )
        }
        if (toggleLabel != null && onToggle != null) {
            item(key = "__toggle__") {
                ProductFilterChip(
                    label = toggleLabel,
                    isActive = isToggleActive,
                    onClick = onToggle
                )
            }
        }
    }
}

@Composable
private fun ProductFilterChip(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    androidx.compose.material3.Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isActive) AppColors.GreenPrimary else MaterialTheme.colorScheme.surface,
        border = if (isActive) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = if (isActive) 1.dp else 0.dp,
        onClick = onClick
    ) {
        Text(
            text = label,
            color = if (isActive) Color.White else AppColors.TextSecondary,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
        )
    }
}

/**
 * Step 6 - the "nothing matched" state, shown when a search or a filter excluded everything.
 *
 * Distinct from the "no products at all" state: here the catalogue is fine, the query is not, so
 * the message names the query and the action clears it.
 */
@Composable
fun ProductFilterEmptyState(
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
                text = if (query.isNotEmpty()) {
                    "Tidak ada $productLabel yang cocok dengan \"$query\""
                } else {
                    "Tidak ada $productLabel di kategori ini"
                },
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

/** The label for "every category", matching the one the Products screen already used. */
const val ALL_CATEGORIES_LABEL: String = "Semua"

/**
 * Step 6 - the search and filter state, and the single pure function that applies it.
 *
 * Kept pure and separate from the composable so the semantics are testable without a device, and
 * so the Products and Purchase screens cannot drift apart on what "search" means.
 *
 * The rules are the app's own, not new ones:
 *  - search matches the product name or its barcode, case-insensitively and partially, exactly as
 *    the Products screen already did;
 *  - a category narrows to that `categoryId`;
 *  - "low stock" is the app's existing predicate, `stock <= minimumStock`, the same one the home
 *    screen counts with.
 *
 * Filtering is in memory over the catalogue the ViewModel already holds, which is the pattern the
 * Products screen established. It reads only: no product, stock or purchase state is touched.
 */
data class ProductFilterState(
    val query: String = "",
    val selectedCategoryId: Long? = null,
    val lowStockOnly: Boolean = false
) {
    val isActive: Boolean get() = query.isNotBlank() || selectedCategoryId != null || lowStockOnly
}

fun filterProductsBySearch(
    products: List<ProductEntity>,
    filter: ProductFilterState
): List<ProductEntity> {
    val query = filter.query.trim()
    return products.filter { product ->
        val matchesQuery = query.isEmpty() ||
            product.name.contains(query, ignoreCase = true) ||
            (!product.barcode.isNullOrBlank() && product.barcode.contains(query, ignoreCase = true))
        val matchesCategory = filter.selectedCategoryId == null || product.categoryId == filter.selectedCategoryId
        val matchesLowStock = !filter.lowStockOnly || isLowStock(product)
        matchesQuery && matchesCategory && matchesLowStock
    }
}

/** The app's existing low-stock definition, shared with the home screen's "stok menipis" count. */
fun isLowStock(product: ProductEntity): Boolean = product.stock <= product.minimumStock

/** The chip names for the category row: every category, then the distinct names in catalogue order. */
fun buildCategoryChipNames(
    categories: List<CategoryEntity>,
    selectedCategoryId: Long?
): List<String> = buildList {
    add(ALL_CATEGORIES_LABEL)
    categories.forEach { category ->
        if (category.name !in this) add(category.name)
    }
}
