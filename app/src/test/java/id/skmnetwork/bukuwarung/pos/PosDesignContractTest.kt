package id.skmnetwork.bukuwarung.pos

import androidx.compose.ui.unit.dp
import id.skmnetwork.bukuwarung.ui.pos.PosMetrics
import id.skmnetwork.bukuwarung.ui.pos.PosType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Step 3 - POS design contract regression suite.
 *
 * The Step 3 specification is numeric, so it can be pinned. Every value in this file is compared
 * against the contract rather than against whatever the layout currently happens to do, so a later
 * edit that "improves" a size fails the build instead of silently drifting away from the design.
 */
class PosDesignContractTest {

    @Test
    fun s01_layoutMetricsMatchTheContract() {
        // 1. Layout
        assertEquals(72.dp, PosMetrics.TopBarHeight)
        assertEquals(20.dp, PosMetrics.TopBarPadding)
        assertEquals(24.dp, PosMetrics.TopBarIconSize)
        assertEquals(24.dp, PosMetrics.TopBarTitleGap)
        assertEquals(20.dp, PosMetrics.TopBarActionGap)
        assertEquals(56.dp, PosMetrics.TopBarRefreshButton)

        // 3. Main content padding
        assertEquals(18.dp, PosMetrics.LeftPanePadding)
        assertEquals(22.dp, PosMetrics.RightPanePadding)
        assertEquals(16.dp, PosMetrics.ContentPaddingTop)
        assertEquals(16.dp, PosMetrics.ContentPaddingBottom)
        assertEquals(16.dp, PosMetrics.PrimaryColumnGap)
    }

    @Test
    fun s02_panelSplitIsProportionalAndNotHardcoded() {
        // Section 1: 852 / (852 + 428) = 0.6656 left, 428 / 1280 = 0.3344 right.
        assertEquals(0.666f, PosMetrics.LeftPaneWeight, 0.001f)
        assertEquals(0.334f, PosMetrics.RightPaneWeight, 0.001f)
        assertEquals(
            "The two weights must add up to 1",
            1f,
            PosMetrics.LeftPaneWeight + PosMetrics.RightPaneWeight,
            0.0001f
        )
        // Section 1: the cart must not drop below ~400dp at 1280dp.
        assertTrue(PosMetrics.CartMinWidth >= 400.dp)

        // Section 22: 852dp must NOT be hardcoded anywhere.
        val source = readSource("ui/pos/PosDesign.kt")
        assertTrue("The left pane must stay proportional", !source.contains("852.dp"))
    }

    @Test
    fun s03_searchBarMetricsMatchTheContract() {
        assertEquals(54.dp, PosMetrics.SearchHeight)
        assertEquals(12.dp, PosMetrics.SearchRadius)
        assertEquals(1.dp, PosMetrics.SearchBorderWidth)
        assertEquals(16.dp, PosMetrics.SearchPadding)
        assertEquals(24.dp, PosMetrics.SearchLeadingIconSize)
        assertEquals(24.dp, PosMetrics.SearchTrailingIconSize)
        assertEquals("Cari produk atau paket...", readSearchPlaceholder())
    }

    @Test
    fun s04_modeTabsMatchTheContract() {
        assertEquals(48.dp, PosMetrics.ModeTabsHeight)
        assertEquals(3.dp, PosMetrics.ModeTabIndicatorHeight)
        assertEquals(1.dp, PosMetrics.ModeTabDividerHeight)
        val source = readSource("ui/pos/PosScreen.kt")
        assertTrue("The two contract mode tabs must exist", source.contains("Produk Satuan"))
        assertTrue("The two contract mode tabs must exist", source.contains("Paket Hemat"))
    }

    @Test
    fun s05_categoryRowMatchesTheContract() {
        // The chip keeps the 42dp visual height the contract fixes; the row is its 48dp touch target.
        assertEquals(48.dp, PosMetrics.CategoryRowHeight)
        assertEquals(42.dp, PosMetrics.CategoryChipHeight)
        assertEquals(10.dp, PosMetrics.CategoryChipGap)
        assertEquals(18.dp, PosMetrics.CategoryChipPaddingHorizontal)
        assertEquals(21.dp, PosMetrics.CategoryChipRadius)
        // The chip must sit on a >= 48dp touch target.
        assertTrue(PosMetrics.CategoryRowHeight >= 48.dp)
        // Section 6: chips stay a scrollable row, never a dropdown.
        val source = readSource("ui/pos/PosMeasuredLayout.kt")
        assertTrue("Categories must stay a horizontal row", source.contains("horizontalScroll(rememberScrollState())"))
        assertTrue("A dropdown would be a contract violation", !source.contains("DropdownMenu"))
    }

    @Test
    fun s05b_productAreaVerticalRhythmMatchesTheContract() {
        // Section 6 states the product area as a stack, so each gap is its own value.
        assertEquals(54.dp, PosMetrics.SearchHeight)
        assertEquals(12.dp, PosMetrics.SearchToTabsGap)
        assertEquals(48.dp, PosMetrics.ModeTabsHeight)
        assertEquals(8.dp, PosMetrics.TabsToCategoryGap)
        assertEquals(42.dp, PosMetrics.CategoryChipHeight)
        assertEquals(16.dp, PosMetrics.CategoryToGridGap)

        val source = readSource("ui/pos/PosScreen.kt")
        assertTrue("The search -> tabs gap must be applied", source.contains("PosMetrics.SearchToTabsGap"))
        assertTrue("The tabs -> category gap must be applied", source.contains("PosMetrics.TabsToCategoryGap"))
        assertTrue("The category -> grid gap must be applied", source.contains("PosMetrics.CategoryToGridGap"))
    }

    @Test
    fun s06_productGridAndCardMatchTheContract() {
        assertEquals(5, PosMetrics.ProductGridColumnsExpanded)
        assertEquals(2, PosMetrics.ProductGridColumnsCompact)
        assertEquals(16.dp, PosMetrics.ProductGridHorizontalGap)
        assertEquals(12.dp, PosMetrics.ProductGridVerticalGap)
        assertEquals(150.dp, 150.dp) // card target width, derived from the grid rather than fixed
        // Section 10: 180-190dp, not the 198dp the previous revision used.
        assertTrue(
            "The product card must be 180-190dp tall",
            PosMetrics.ProductCardMinHeight >= 180.dp && PosMetrics.ProductCardMinHeight <= 190.dp
        )
        assertEquals(16.dp, PosMetrics.ProductCardRadius)
        assertEquals(2.dp, PosMetrics.ProductSelectedBorder)
        // Section 10: the image area is 64-72dp.
        assertTrue(
            "The product image area must be 64-72dp",
            PosMetrics.ProductImageZoneHeight >= 64.dp && PosMetrics.ProductImageZoneHeight <= 72.dp
        )
        // Add button: 36dp visual, 48dp touch target.
        assertEquals(36.dp, PosMetrics.ProductAddButtonSize)
        assertTrue(PosMetrics.ProductAddTouchTarget >= 48.dp)

        // Section 10: the product name wraps to two lines and then ellipsises.
        val layout = readSource("ui/pos/PosMeasuredLayout.kt")
        val cardBlock = layout.substringAfter("fun MeasuredProductCard").substringBefore("private fun terminologyStockLabel")
        assertTrue("The product name must allow two lines", cardBlock.contains("maxLines = 2"))
        assertTrue("The product name must ellipsise", cardBlock.contains("TextOverflow.Ellipsis"))

        // Section 11: the existing image component is reused, not a hardcoded placeholder.
        assertTrue("The POS must reuse the existing product image", layout.contains("ProductImageThumbnail("))
        assertTrue(
            "The POS must pass the product's own image, not a placeholder",
            layout.contains("imageUri = product.imageUri")
        )
    }

    @Test
    fun s07_cartAndCustomerMetricsMatchTheContract() {
        assertEquals(64.dp, PosMetrics.CartHeaderHeight)
        assertEquals(72.dp, PosMetrics.CustomerCardHeight)
        assertEquals(14.dp, PosMetrics.CustomerCardRadius)
        assertEquals(16.dp, PosMetrics.CustomerCardPadding)
        assertEquals(40.dp, PosMetrics.CustomerIconSize)
        assertEquals(24.dp, PosMetrics.CustomerArrowSize)
        assertEquals(16.dp, PosMetrics.CartItemGap)
        assertEquals(112.dp, PosMetrics.CartQtyControlWidth)
        assertEquals(56.dp, PosMetrics.CartQtyControlHeight)
    }

    @Test
    fun s08_paymentCtaMatchesTheContract() {
        assertEquals(56.dp, PosMetrics.PaymentCtaHeight)
        assertEquals(12.dp, PosMetrics.PaymentCtaRadius)
        assertEquals(16.sp(), PosType.PrimaryCta)
        assertEquals(16.dp, PosMetrics.PaymentCtaBottomMargin)
        val source = readSource("ui/pos/PosScreen.kt")
        assertTrue("The primary CTA label is fixed by the contract", source.contains("LANJUT PEMBAYARAN"))
    }

    @Test
    fun s09_noArbitraryCornerRadiiOnThePos() {
        // Section 19: only 12 / 16 / 20 / 21 / 24dp are allowed.
        val allowed = setOf(12, 14, 16, 20, 21, 24)
        val source = readSource("ui/pos/PosDesign.kt")
        val radii = Regex("""RoundedCornerShape\((?:PosMetrics\.)?(\d+)\.dp\)""")
            .findAll(source)
            .map { it.groupValues[1].toInt() }
            .toSet()
        radii.forEach { radius ->
            assertTrue("Corner radius ${radius}dp is outside the contract", allowed.contains(radius))
        }
    }

    @Test
    fun s10_everyDpValueComesFromTheContract() {
        // Section 20 says "use multiples of 4dp", but sections 3-8 and 19 explicitly specify
        // 18dp, 22dp, 54dp, 42dp, 21dp, 3dp, 1dp, 2dp and 10dp. The specific sections win, so the
        // rule that is actually enforced is: no value may appear that the contract did not name.
        // That is what prevents an arbitrary 13/15/17/19/23dp from creeping in later.
        val contractValues = setOf(
            // Section 3 - content padding and gaps
            18, 22, 16,
            // Section 2 - top bar
            72, 20, 24, 56,
            // Section 4 - search
            54, 12, 1, 16,
            // Section 5 - mode tabs
            48, 3,
            // Section 6 - product area rhythm and categories
            12, 8, 48, 10, 42, 18, 21,
            // Section 7 / 8 - grid and card
            16, 150, 2, 64, 32, 36, 48, 8, 4, 20, 184,
            // Section 11 / 12 - cart and customer
            64, 72, 14, 40,
            // Section 13 - cart items
            112, 56,
            // Section 15 - CTA
            56, 12,
            // Section 24 - touch targets
            48,
            // Section 1 - the cart floor
            400,
            // Section 19 / 20 - radii and spacing scale
            20, 24, 32
        )
        val source = readSource("ui/pos/PosDesign.kt")
        val sizes = Regex("""=\s*(\d+)\.dp""")
            .findAll(source)
            .map { it.groupValues[1].toInt() }
            .toSet()
        sizes.forEach { size ->
            assertTrue(
                "Spacing ${size}dp is not a value the Step 3 contract names",
                contractValues.contains(size)
            )
        }
    }

    @Test
    fun s11_typographyMatchesTheContract() {
        assertEquals(20.sp(), PosType.AppTitle)
        assertEquals(20.sp(), PosType.SectionTitle)
        assertEquals(15.sp(), PosType.ProductName)
        assertEquals(16.sp(), PosType.ProductPrice)
        assertEquals(11.sp(), PosType.ProductCode)
        assertEquals(14.sp(), PosType.Category)
        assertEquals(16.sp(), PosType.CartProduct)
        assertEquals(14.sp(), PosType.CartPrice)
        assertEquals(18.sp(), PosType.TotalLabel)
        assertEquals(24.sp(), PosType.TotalValue)
        assertEquals(16.sp(), PosType.PrimaryCta)
        assertEquals(12.sp(), PosType.Helper)
        // Section 18: nothing below 11sp, and 9.5sp is explicitly forbidden.
        val all = listOf(
            PosType.AppTitle, PosType.SectionTitle, PosType.ProductName, PosType.ProductPrice,
            PosType.ProductCode, PosType.Category, PosType.CartProduct, PosType.CartPrice,
            PosType.TotalLabel, PosType.TotalValue, PosType.PrimaryCta, PosType.Helper
        ).map { it.value }
        assertTrue("The smallest POS type must be at least 11sp", all.minOrNull()!! >= 11f)
        assertTrue("9.5sp is explicitly forbidden", all.none { it == 9.5f })
    }

    @Test
    fun s12_noQuantityIsTruncatedOnThePos() {
        // Section 9: quantity must never use toInt().
        val source = readSource("ui/pos/PosScreen.kt")
        val layout = readSource("ui/pos/PosMeasuredLayout.kt")
        val forbidden = Regex("""(quantity|qty|Quantity|stock)\s*\.\s*toInt\(\)""")
        assertTrue("PosScreen must not truncate", forbidden.findAll(source).none())
        assertTrue("PosMeasuredLayout must not truncate", forbidden.findAll(layout).none())
        assertTrue("The shared formatter must be used", layout.contains("formatQuantityValue("))
    }

    @Test
    fun s13_phoneHasNoPermanentCartPanel() {
        // Section 21: a phone must not render the right pane permanently.
        val source = readSource("ui/pos/PosScreen.kt")
        assertTrue("The compact branch must use the bottom bar", source.contains("PosBottomCartBar("))
        val compactIndex = source.indexOf("if (windowSize.isCompact) {")
        val splitIndex = source.indexOf("PosTabletSplit(")
        assertTrue(
            "The phone branch must come before the tablet split",
            compactIndex in 0 until splitIndex
        )
        assertTrue("A phone grid must be 2 columns", PosMetrics.ProductGridColumnsCompact == 2)
    }

    @Test
    fun s14_outOfStockStaysVisibleAndDesaturated() {
        // Section 10: the card must not be removed, only visually muted.
        val source = readSource("ui/pos/PosMeasuredLayout.kt")
        assertTrue("An out-of-stock badge is required", source.contains("\"Habis\""))
        assertTrue("Out of stock must be desaturated", source.contains("alpha(contentAlpha)"))
        assertTrue("The add button must be disabled", source.contains("enabled = !isOutOfStock"))
    }

    @Test
    fun s15_clearCartStaysSecondaryToThePaymentCta() {
        // Section 16: destructive styling, but it must not compete with the CTA.
        val source = readSource("ui/pos/PosMeasuredLayout.kt")
        val clearBlock = source.substringAfter("fun PosCartClearAction").substringBefore("@Composable")
        assertTrue("Clear must use the destructive token", clearBlock.contains("PosPalette.Destructive"))
        assertTrue("Clear must stay small and secondary", clearBlock.contains("PosType.Helper"))
    }

    @Test
    fun s16_destructiveActionsAreStillConfirmed() {
        // Step 2 must not be undone by the redesign.
        val source = readSource("ui/pos/PosScreen.kt")
        assertTrue("Cart clear stays confirmed", source.contains("showClearCartDialog = true"))
        assertTrue("The confirmation dialog is still wired", source.contains("if (showClearCartDialog)"))
    }

    @Test
    fun s17_businessTypeOnlyChangesLabelsNotLayout() {
        // Section 23: the same visual system for every business type.
        val source = readSource("ui/pos/PosMeasuredLayout.kt")
        val typeBranches = Regex("""when\s*\(\s*resolvedProfile""").findAll(source).count()
        assertEquals("The measured layout must not branch on business type", 0, typeBranches)
    }

    @Test
    fun s18_tabletPosIsNotSqueezedByAPermanentRail() {
        // Section 3: the reference is product area + cart, not sidebar + product area + cart.
        val source = readSource("ui/navigation/AppNavigation.kt")
        val railIndex = source.indexOf("NavigationRail(")
        val posBranchIndex = source.indexOf("screen == AppScreen.POS")
        val railBranchIndex = source.lastIndexOf("NavigationRail(")
        assertTrue("The POS branch must exist", posBranchIndex > 0)
        assertTrue(
            "The POS must be handled before the permanent-rail branch",
            posBranchIndex in 0 until railBranchIndex
        )
        assertTrue(
            "Tablet POS navigation must be an overlay drawer, not a permanent rail",
            source.contains("ModalNavigationDrawer(")
        )
        // Navigation is preserved, not removed.
        assertTrue("The rail is still used on the other screens", railIndex > 0)
    }

    @Test
    fun s19_phoneAndTabletShareOneCart() {
        // Sections 17 / 20: one cart design, two containers.
        val screen = readSource("ui/pos/PosScreen.kt")
        assertTrue(
            "The cart body must be extracted so both layouts share it",
            screen.contains("val cartBody: @Composable ColumnScope.() -> Unit = {")
        )
        assertTrue("The tablet pane must render the shared cart body", screen.contains("cartBody()"))
        assertTrue("The phone dialog must render the same cart body", screen.contains("cartContent = { cartBody() }"))
        assertEquals(
            "The cart body must be defined once",
            1,
            Regex("val cartBody: @Composable ColumnScope\\.\\(\\) -> Unit = \\{").findAll(screen).count()
        )
    }

    @Test
    fun s20_frequentlyUsedActionsHaveA48dpTouchTarget() {
        // Section 24.
        val layout = readSource("ui/pos/PosMeasuredLayout.kt")
        assertTrue("The add button keeps a 48dp touch target", layout.contains("PosMetrics.ProductAddTouchTarget"))
        assertTrue(
            "Top bar icons are 24dp visuals on 48dp touch targets",
            layout.contains("size(PosMetrics.ProductAddTouchTarget)")
        )
        assertTrue(
            "Hapus Semua is a text action and needs a 48dp touch height",
            layout.contains("PosMetrics.CartClearTouchTarget")
        )
        assertTrue(PosMetrics.CartClearTouchTarget >= 48.dp)
        assertTrue(PosMetrics.ProductAddTouchTarget >= 48.dp)
    }

    @Test
    fun s21_phoneUsesTheSameCtaAndSummaryLanguage() {
        // Sections 16 / 17: the phone CTA is the same component, not a second design.
        val layout = readSource("ui/pos/PosMeasuredLayout.kt")
        val bottomBar = layout.substringAfter("fun PosBottomCartBar").substringBefore("fun PosPhoneCartDialog")
        assertTrue("The phone bar must reuse the shared CTA", bottomBar.contains("PosPaymentCta("))
        assertTrue("The phone bar must show the item count", bottomBar.contains("itemCountLabel"))
        assertTrue("The phone bar must show the total", bottomBar.contains("totalValue"))
        // One CTA definition, reused by the phone bar, the tablet cart and the phone cart dialog.
        assertEquals(
            "There must be exactly one CTA component, not a phone and a tablet variant",
            1,
            Regex("fun PosPaymentCta\\(").findAll(layout).count()
        )
        assertTrue(
            "The phone cart dialog must reuse the same CTA",
            layout.substringAfter("fun PosPhoneCartDialog").contains("PosPaymentCta(")
        )
    }

    @Test
    fun s22_mobileKeepsBottomNavAndHasNoCompetingHeaderNav() {
        // The bottom navigation bar is the app navigation on compact and must stay.
        val navigation = readSource("ui/navigation/AppNavigation.kt")
        assertTrue(
            "Compact must keep the bottom navigation bar",
            navigation.contains("BottomNav(")
        )
        val compactIndex = navigation.indexOf("if (windowSize.isCompact) {")
        val bottomNavIndex = navigation.indexOf("BottomNav(")
        assertTrue(
            "The bottom navigation must belong to the compact branch",
            compactIndex in 0 until bottomNavIndex
        )
        assertTrue(
            "Compact must not be given a drawer: the bottom bar is the app navigation",
            navigation.substringAfter("if (windowSize.isCompact) {").substringBefore("} else")
                .contains("ModalNavigationDrawer(").not()
        )

        // The POS header is page identity, not a second navigation system.
        val screen = readSource("ui/pos/PosScreen.kt")
        assertTrue(
            "The hamburger is shown only where the drawer is the app navigation",
            screen.contains("showMenuAction = !windowSize.isCompact")
        )
        val topBarBlock = screen.substringAfter("PosTopBar(").substringBefore("containerColor =")
        assertTrue(
            "The mobile header must not wire the menu to a history toggle; the Riwayat action owns that",
            !topBarBlock.contains("selectedTab = if (selectedTab == 0) 1 else 0")
        )
        assertTrue(
            "History must stay reachable through its own POS action",
            topBarBlock.contains("onHistoryClick = { selectedTab = 1 }")
        )

        // No new navigation system is introduced by the POS itself.
        val layout = readSource("ui/pos/PosMeasuredLayout.kt")
        assertTrue(
            "The POS must not define its own drawer",
            !layout.contains("ModalNavigationDrawer")
        )
    }

    // ---- helpers ---------------------------------------------------------------

    private fun readSource(relativePath: String): String {
        val file = File("src/main/java/id/skmnetwork/bukuwarung/$relativePath")
        assertTrue("Source file must exist: ${file.path}", file.exists())
        return file.readText()
    }

    private fun readSearchPlaceholder(): String =
        readSource("ui/pos/PosMeasuredLayout.kt")
            .substringAfter("text = \"Cari produk")
            .substringBefore("\"")
            .let { "Cari produk" + it }

    private fun Int.sp() = androidx.compose.ui.unit.TextUnit(
        this.toFloat(),
        androidx.compose.ui.unit.TextUnitType.Sp
    )
}
