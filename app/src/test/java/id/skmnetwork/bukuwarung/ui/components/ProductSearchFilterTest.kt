package id.skmnetwork.bukuwarung.ui.components

import id.skmnetwork.bukuwarung.data.local.entity.CategoryEntity
import id.skmnetwork.bukuwarung.data.local.entity.ItemType
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Step 6 - the purchase/catalogue search and filter semantics.
 *
 * These are the rules the Purchase restock list now runs on. They are the app's existing rules -
 * the same name/barcode match and the same category narrowing the Products screen used, plus the
 * low-stock predicate the home screen counts with - so they are pinned here rather than
 * re-invented per screen.
 */
class ProductSearchFilterTest {

    private val groceries = 1L
    private val drinks = 2L

    private fun category(id: Long, name: String) = CategoryEntity(
        id = id,
        uuid = UUID.randomUUID().toString(),
        businessId = "b1",
        name = name
    )

    private fun product(
        name: String,
        categoryId: Long = groceries,
        stock: Double = 10.0,
        minimumStock: Double = 3.0,
        barcode: String? = null,
        id: Long = 0L
    ): ProductEntity {
        var counter = id
        return ProductEntity(
            id = if (counter > 0) counter else (name.hashCode().toLong() and 0x7FFFFFFF),
            uuid = UUID.randomUUID().toString(),
            businessId = "b1",
            categoryId = categoryId,
            name = name,
            purchasePrice = 1000L,
            sellingPrice = 1500L,
            stock = stock,
            minimumStock = minimumStock,
            barcode = barcode,
            unit = "pcs",
            itemType = ItemType.PHYSICAL.name
        )
    }

    private val catalogue = listOf(
        product("Beras Premium 5 Kg", groceries, barcode = "8991002101017"),
        product("Gula Pasir 1 Kg", groceries, stock = 1.0, minimumStock = 3.0),
        product("Minyak Goreng 1 L", groceries),
        product("Air Mineral 600 ml", drinks, stock = 2.0, minimumStock = 3.0),
        product("Susu Ultra 250 ml", drinks)
    )

    private fun names(products: List<ProductEntity>) = products.map { it.name }.sorted()

    // ---- an unfiltered list ----------------------------------------------------

    @Test
    fun anEmptyFilterReturnsEveryProduct() {
        val result = filterProductsBySearch(catalogue, ProductFilterState())
        assertEquals("No filter must mean no exclusion", catalogue.size, result.size)
        assertFalse("An untouched filter must not be reported as active", ProductFilterState().isActive)
    }

    // ---- search ----------------------------------------------------------------

    @Test
    fun aPartialNameSearchMatchesTheProduct() {
        val result = filterProductsBySearch(catalogue, ProductFilterState(query = "beras"))
        assertEquals(listOf("Beras Premium 5 Kg"), names(result))
    }

    @Test
    fun searchIsCaseInsensitive() {
        val lower = filterProductsBySearch(catalogue, ProductFilterState(query = "minyak"))
        val upper = filterProductsBySearch(catalogue, ProductFilterState(query = "MINYAK"))
        val mixed = filterProductsBySearch(catalogue, ProductFilterState(query = "mInYaK"))
        assertEquals(listOf("Minyak Goreng 1 L"), names(lower))
        assertEquals(names(lower), names(upper))
        assertEquals(names(lower), names(mixed))
    }

    @Test
    fun aPartialNameMatchesFromTheMiddle() {
        val result = filterProductsBySearch(catalogue, ProductFilterState(query = "premium"))
        assertEquals(listOf("Beras Premium 5 Kg"), names(result))
    }

    @Test
    fun aBarcodeSearchFindsTheProduct() {
        val result = filterProductsBySearch(catalogue, ProductFilterState(query = "8991002101017"))
        assertEquals("A barcode must be searchable, as it is on the Products screen", listOf("Beras Premium 5 Kg"), names(result))
    }

    @Test
    fun aPartialBarcodeAlsoMatches() {
        val result = filterProductsBySearch(catalogue, ProductFilterState(query = "0210"))
        assertEquals(listOf("Beras Premium 5 Kg"), names(result))
    }

    @Test
    fun aSearchThatMatchesNothingReturnsNothing() {
        val result = filterProductsBySearch(catalogue, ProductFilterState(query = "zzzz"))
        assertTrue(result.isEmpty())
    }

    @Test
    fun clearingTheSearchRestoresTheWholeList() {
        val filtered = filterProductsBySearch(catalogue, ProductFilterState(query = "beras"))
        assertEquals(1, filtered.size)
        val cleared = filterProductsBySearch(catalogue, ProductFilterState(query = ""))
        assertEquals(catalogue.size, cleared.size)
    }

    // ---- category filter -------------------------------------------------------

    @Test
    fun aCategoryFilterReturnsOnlyThatCategory() {
        val result = filterProductsBySearch(catalogue, ProductFilterState(selectedCategoryId = drinks))
        assertEquals(listOf("Air Mineral 600 ml", "Susu Ultra 250 ml"), names(result))
    }

    @Test
    fun clearingTheCategoryRestoresTheWholeList() {
        val filtered = filterProductsBySearch(catalogue, ProductFilterState(selectedCategoryId = drinks))
        assertTrue(filtered.size < catalogue.size)
        val cleared = filterProductsBySearch(catalogue, ProductFilterState(selectedCategoryId = null))
        assertEquals(catalogue.size, cleared.size)
    }

    @Test
    fun theChipRowOffersEveryCategoryAndAnAllOption() {
        val categories = listOf(category(groceries, "Sembako"), category(drinks, "Minuman"))
        val names = buildCategoryChipNames(categories, selectedCategoryId = null)
        assertEquals("The all option must come first", ALL_CATEGORIES_LABEL, names[0])
        assertTrue(names.contains("Sembako"))
        assertTrue(names.contains("Minuman"))
    }

    @Test
    fun theChipRowNeverRepeatsACategoryName() {
        // The catalogue can legitimately hold two categories with the same name; a duplicated chip
        // would make the filter look broken.
        val categories = listOf(category(1L, "Sembako"), category(2L, "Sembako"), category(3L, "Minuman"))
        val names = buildCategoryChipNames(categories, selectedCategoryId = null)
        assertEquals(names.size, names.distinct().size)
    }

    // ---- low stock -------------------------------------------------------------

    @Test
    fun theLowStockFilterMatchesTheAppsOwnPredicate() {
        val lowStockOnly = filterProductsBySearch(catalogue, ProductFilterState(lowStockOnly = true))
        assertEquals(
            listOf("Air Mineral 600 ml", "Gula Pasir 1 Kg"),
            names(lowStockOnly)
        )
        // The same predicate the home screen counts with.
        val expected = catalogue.filter { it.stock <= it.minimumStock }
        assertEquals(names(expected), names(lowStockOnly))
    }

    @Test
    fun lowStockIsFalseForAProductWellAboveItsMinimum() {
        val healthy = product("Beras", stock = 20.0, minimumStock = 3.0)
        assertFalse(isLowStock(healthy))
    }

    @Test
    fun aProductExactlyAtItsMinimumCountsAsLowStock() {
        val atMinimum = product("Beras", stock = 3.0, minimumStock = 3.0)
        assertTrue("The home screen's predicate is <=, and this must match it", isLowStock(atMinimum))
    }

    @Test
    fun clearingTheLowStockToggleRestoresTheWholeList() {
        val filtered = filterProductsBySearch(catalogue, ProductFilterState(lowStockOnly = true))
        assertTrue(filtered.size < catalogue.size)
        val cleared = filterProductsBySearch(catalogue, ProductFilterState(lowStockOnly = false))
        assertEquals(catalogue.size, cleared.size)
    }

    // ---- search and filter combined -------------------------------------------

    @Test
    fun searchAndCategoryCombineSafely() {
        val result = filterProductsBySearch(
            catalogue,
            ProductFilterState(query = "mineral", selectedCategoryId = drinks)
        )
        assertEquals(listOf("Air Mineral 600 ml"), names(result))
    }

    @Test
    fun aSearchThatExcludesTheChosenCategoryReturnsNothing() {
        val result = filterProductsBySearch(
            catalogue,
            ProductFilterState(query = "beras", selectedCategoryId = drinks)
        )
        assertTrue("The two must intersect, not union", result.isEmpty())
    }

    @Test
    fun searchAndLowStockCombineSafely() {
        val result = filterProductsBySearch(
            catalogue,
            ProductFilterState(query = "gula", lowStockOnly = true)
        )
        assertEquals(listOf("Gula Pasir 1 Kg"), names(result))
    }

    @Test
    fun aFullFilterStateIsReportedAsActive() {
        assertTrue(ProductFilterState(query = "x").isActive)
        assertTrue(ProductFilterState(selectedCategoryId = 1L).isActive)
        assertTrue(ProductFilterState(lowStockOnly = true).isActive)
        assertFalse(ProductFilterState(query = "   ").isActive)
    }

    // ---- the filter must not change anything -----------------------------------

    @Test
    fun filteringPreservesTheOriginalOrderAndValues() {
        val result = filterProductsBySearch(catalogue, ProductFilterState(query = "a"))
        assertEquals(
            "Filtering must keep the catalogue's existing order, not re-sort it",
            catalogue.map { it.id }.filter { id -> result.any { it.id == id } },
            result.map { it.id }
        )
        // Every returned product is the very same instance, so no copy or mutation can have happened.
        result.forEach { filtered ->
            assertTrue(
                "Filtering must return the original instances, not copies",
                catalogue.any { it === filtered }
            )
        }
    }

    @Test
    fun filteringNeverChangesStockOrPrice() {
        val before = catalogue.associate { it.id to it.stock to it.purchasePrice }
        filterProductsBySearch(catalogue, ProductFilterState(query = "a", lowStockOnly = true))
        val after = catalogue.associate { it.id to it.stock to it.purchasePrice }
        assertEquals("Filtering is read-only", before, after)
    }
}
