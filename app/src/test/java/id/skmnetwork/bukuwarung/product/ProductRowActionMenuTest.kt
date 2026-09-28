package id.skmnetwork.bukuwarung.product

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Step 4 - the product row's overflow menu is an action entry point, not an Edit shortcut.
 *
 * The defect this pins: the MoreVert `IconButton` and the row's own `Surface` were both wired to the
 * same `onEdit`. An icon that promises a list therefore opened a form, and the two affordances were
 * indistinguishable. The fix is a menu whose entries each name an action the product workflow
 * already had.
 *
 * These are source-shape assertions for the same reason the licence and POS contract suites use
 * them: the wiring itself is the contract, and a Compose UI assertion cannot be run on this module
 * (see PosDesignContractTest's note about the release-only androidTest variant).
 */
class ProductRowActionMenuTest {

    private val productsScreen = File("src/main/java/id/skmnetwork/bukuwarung/ui/product/ProductsScreen.kt")
    private val addProductScreen = File("src/main/java/id/skmnetwork/bukuwarung/ui/product/AddProductScreen.kt")
    private val navigation = File("src/main/java/id/skmnetwork/bukuwarung/ui/navigation/AppNavigation.kt")

    // Newlines are normalised so the multi-line assertions below are not defeated by this
    // repository's CRLF line endings.
    private fun source(file: File): String = file.readText().replace("\r\n", "\n")

    /** The body of a top-level declaration, so an assertion cannot be satisfied by a sibling. */
    private fun body(text: String, from: String, to: String): String =
        text.substringAfter(from).substringBefore(to)

    @Test
    fun moreVertNoLongerWiresDirectlyToEdit() {
        val text = source(productsScreen)
        val card = body(text, "private fun ProductItemCard(", "@Composable\nprivate fun StockStatusBadge")

        val overflowButtonBoundToEdit = Regex(
            """onClick = onEdit[^)]*\)\s*\{\s*Icon\(\s*imageVector = Icons\.Default\.MoreVert""",
            RegexOption.DOT_MATCHES_ALL
        )
        assertFalse(
            "The overflow button must not call onEdit: the row itself is the Edit target",
            overflowButtonBoundToEdit.containsMatchIn(text)
        )
        assertFalse(
            "The MoreVert button must open the menu, not edit",
            card.contains("""onClick = onEdit,
                    modifier = Modifier.size(36.dp)""")
        )
        // The row tap stays the Edit target; that is the primary action and is unchanged.
        assertTrue("The row itself must still open Edit", card.contains("Surface(\n        onClick = onEdit,"))
    }

    @Test
    fun moreVertOpensTheExistingActionMenuSurface() {
        val text = source(productsScreen)
        val card = body(text, "private fun ProductItemCard(", "@Composable\nprivate fun StockStatusBadge")

        assertTrue(
            "The overflow button must open a menu",
            card.contains("menuExpanded = true")
        )
        assertTrue(
            "The menu must be the app's existing Material3 dropdown",
            card.contains("DropdownMenu(")
        )
        assertTrue(
            "The menu must be dismissible",
            card.contains("onDismissRequest = { menuExpanded = false }")
        )
    }

    @Test
    fun theMenuOffersEditStockAndDelete() {
        val text = source(productsScreen)
        val card = body(text, "private fun ProductItemCard(", "@Composable\nprivate fun StockStatusBadge")

        listOf("Edit \$productLabel", "Sesuaikan Stok / Opname", "Hapus \$productLabel").forEach { label ->
            assertTrue("The menu must offer '$label'", card.contains("\"$label\""))
        }

        listOf(
            "ProductRowAction.EDIT",
            "ProductRowAction.ADJUST_STOCK",
            "ProductRowAction.DELETE"
        ).forEach { action ->
            assertTrue("The menu must be able to dispatch $action", card.contains(action))
        }
    }

    @Test
    fun deleteUsesTheExistingDestructiveToken() {
        val text = source(productsScreen)
        val card = body(text, "private fun ProductItemCard(", "@Composable\nprivate fun StockStatusBadge")

        assertTrue(
            "Delete must reuse the app's existing error token, not a new colour",
            body(card, "\"Hapus \$productLabel\"", "onClick").contains("MaterialTheme.colorScheme.error")
        )
    }

    @Test
    fun openingTheMenuMutatesNothing() {
        val text = source(productsScreen)
        val card = body(text, "private fun ProductItemCard(", "@Composable\nprivate fun StockStatusBadge")

        // The only state the card owns is the menu flag: no repository, view model or navigation
        // call may be reachable from opening it.
        assertTrue(
            "The card must own exactly one piece of state: the menu flag",
            card.contains("var menuExpanded by remember { mutableStateOf(false) }")
        )
        assertFalse(
            "Opening the menu must not touch the repository or the view model",
            card.contains("viewModel.") || card.contains("repository.")
        )
        assertFalse(
            "Opening the menu must not navigate",
            card.contains("onEdit()") && !card.contains("onMoreActions")
        )
    }

    @Test
    fun stockAndDeleteLandOnTheExistingDialogs() {
        val text = source(addProductScreen)

        // The two dialogs must still be the ones this screen has always hosted.
        assertTrue(
            "Stock must still use the existing StockAdjustmentDialog",
            text.contains("StockAdjustmentDialog(")
        )
        assertTrue(
            "Delete must still use the existing confirmation dialog",
            body(text, "if (showDeleteDialog && productIdToEdit != null)", "Scaffold(").contains("AlertDialog(")
        )
        assertTrue(
            "Delete must still be confirmed, and still cancellable",
            body(text, "if (showDeleteDialog && productIdToEdit != null)", "Scaffold(")
                .contains("""Text("Batal")""")
        )
        // The atomic path and the Step 2 stock-preservation rule are untouched.
        assertTrue(
            "Stock must still be written through adjustStock",
            text.contains("viewModel.adjustStock(")
        )
        assertTrue(
            "The Step 2 stock-preservation rule must survive",
            text.contains("Stok tidak lagi ikut berubah saat produk ini diedit")
        )
    }

    @Test
    fun anActionRequestOnlyOpensADialogOnAnExistingProduct() {
        val text = source(addProductScreen)
        val effect = body(text, "LaunchedEffect(initialAction, productIdToEdit)", "var showBarcodeScannerDialog")

        assertTrue(
            "The action request must be inert while adding a product",
            effect.contains("if (productIdToEdit == null) return@LaunchedEffect")
        )
        assertTrue(
            "The stock request must open the existing stock dialog",
            effect.contains("ProductRowAction.ADJUST_STOCK -> showStockAdjustmentDialog = true")
        )
        assertTrue(
            "The delete request must open the existing delete confirmation",
            effect.contains("ProductRowAction.DELETE -> showDeleteDialog = true")
        )
    }

    @Test
    fun eachActionTargetsTheProductTheRowBelongsTo() {
        val text = source(productsScreen)
        val listBlock = body(text, "items(filteredProducts, key = { it.id })", "private fun ProductsHeader")

        assertTrue(
            "The row must pass its own product id to the action",
            listBlock.contains("onProductAction(product.id, action)")
        )
        assertTrue(
            "A row must not dispatch an action for a product that was never saved",
            listBlock.contains("if (product.id > 0) {")
        )
    }

    @Test
    fun navigationCarriesTheActionAndResetsIt() {
        val text = source(navigation)

        assertTrue(
            "The navigation helper must accept the requested action",
            body(text, "fun navigateToAddProduct(", "\n    }").contains("action:")
        )
        assertTrue(
            "The product screen must receive the action callback",
            text.contains("onProductAction = { productId, action ->")
        )
        assertTrue(
            "The form must receive the requested action",
            text.contains("initialAction = selectedProductAction")
        )
        assertTrue(
            "Leaving the form must clear the request, so a later visit never inherits it",
            body(text, "AppScreen.ADD_PRODUCT -> AddProductScreen(", "\n                )")
                .contains("selectedProductAction =")
        )
    }

    @Test
    fun tenantBindingIsUntouched() {
        val text = source(navigation)
        // Step 3A: the tenant gate and the keyed repositories must survive this change.
        assertTrue("The tenant gate must remain", text.contains("TenantGate(userSettings = loadedUserSettings)"))
        assertTrue(
            "Repositories must remain keyed on the active tenant",
            text.contains("remember(activeBusinessId) { ProductRepository(database, activeBusinessId) }")
        )
        assertTrue(
            "There must still be no LEGACY_BUSINESS runtime fallback",
            !text.lines().any { it.contains("LEGACY_BUSINESS") && !it.trimStart().startsWith("//") }
        )
    }

    @Test
    fun everyProductRowOverflowIsAMenu() {
        val text = source(productsScreen)
        val overflows = Regex("Icons\\.Default\\.MoreVert").findAll(text).count()
        assertEquals("Both product rows expose an overflow affordance", 2, overflows)

        // No overflow icon anywhere in this file may still be bound straight to onEdit.
        Regex("""IconButton\(\s*onClick = onEdit[^)]*\)[^@]{0,200}?MoreVert""", RegexOption.DOT_MATCHES_ALL)
            .findAll(text)
            .forEach { assertFalse("An overflow icon is still wired to onEdit", it.value.contains("MoreVert")) }
    }
}
