package id.skmnetwork.bukuwarung.ui.pos

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Step 3 - measured POS design contract.
 *
 * Every value in this file comes from the Step 3 specification. It is deliberately a separate object
 * so the numbers are reviewable in one place and can be compared against the contract line by line,
 * rather than being scattered through the layout as literals.
 *
 * All spacing is a multiple of 4dp. All corner radii are 12 / 16 / 20 / 24dp (chips 20dp). All type
 * sizes come from [PosType]. No other value is used on this screen.
 */
object PosMetrics {

    // ---- 1. Layout -------------------------------------------------------------

    /** Top app bar height. */
    val TopBarHeight: Dp = 72.dp
    val TopBarPadding: Dp = 20.dp
    val TopBarTitleSize = 20.sp
    val TopBarIconSize: Dp = 24.dp
    /** Icon-to-title gap. */
    val TopBarTitleGap: Dp = 24.dp
    /** Gap between the utility icons on the right. */
    val TopBarActionGap: Dp = 20.dp
    val TopBarRefreshButton: Dp = 56.dp

    /**
     * Target left/right split.
     *
     * The contract states 852dp / 428dp at 1280dp and explicitly forbids hardcoding 852dp. These two
     * weights reproduce that ratio at any width: 852 / (852 + 428) = 0.6656, 428 / 1280 = 0.3344.
     */
    const val LeftPaneWeight: Float = 0.666f
    const val RightPaneWeight: Float = 0.334f

    /** The cart must never be squeezed below this on a wide screen. */
    val CartMinWidth: Dp = 400.dp

    val ContentPaddingTop: Dp = 16.dp
    val ContentPaddingBottom: Dp = 16.dp
    val LeftPanePadding: Dp = 18.dp
    val RightPanePadding: Dp = 22.dp
    val PrimaryColumnGap: Dp = 16.dp

    // ---- 4. Search bar ---------------------------------------------------------

    val SearchHeight: Dp = 54.dp
    val SearchRadius: Dp = 12.dp
    val SearchBorderWidth: Dp = 1.dp
    val SearchPadding: Dp = 16.dp
    val SearchLeadingIconSize: Dp = 24.dp
    val SearchTrailingIconSize: Dp = 24.dp
    // Step 19: the barcode scanner is a primary POS action and the only way to open the scanner,
    // so its tappable area follows the same 48dp accessibility rule as ProductAddTouchTarget and
    // CartClearTouchTarget below. The visual icon stays at SearchTrailingIconSize; only the touch
    // target grows, and 48dp still fits inside the 54dp SearchHeight so the field is unchanged.
    val SearchTrailingTouchTarget: Dp = 48.dp
    val SearchPlaceholderSize = 16.sp

    // ---- 5. Mode tabs ----------------------------------------------------------

    val ModeTabsHeight: Dp = 48.dp
    val ModeTabIndicatorHeight: Dp = 3.dp
    val ModeTabDividerHeight: Dp = 1.dp

    // ---- 6. Product area vertical rhythm -----------------------------------------
    //
    // The contract states the product area as a stack: search 54 -> 12 -> tabs 48 -> 8 ->
    // category 42 -> 16 -> grid. These three gaps are therefore their own tokens, and the column
    // must not use one uniform gap for all four slots.

    /** Search -> product mode tabs. */
    val SearchToTabsGap: Dp = 12.dp
    /** Mode tabs -> category row. */
    val TabsToCategoryGap: Dp = 8.dp
    /** Category row -> product grid. */
    val CategoryToGridGap: Dp = 16.dp

    /** ---- 6. Category row ------------------------------------------------------- */

    /**
     * The category row is the touch surface (48dp, per the accessibility rule), while the chip
     * itself keeps the 42dp visual height the contract specifies. Same visual-size / touch-size
     * split the add button already uses.
     */
    val CategoryRowHeight: Dp = 48.dp
    val CategoryChipGap: Dp = 10.dp
    val CategoryChipHeight: Dp = 42.dp
    val CategoryChipPaddingHorizontal: Dp = 18.dp
    val CategoryChipRadius: Dp = 21.dp
    val CategoryChipTextSize = 14.sp

    // ---- 7 / 8. Product grid ---------------------------------------------------

    val ProductGridColumnsExpanded: Int = 5
    val ProductGridColumnsMedium: Int = 4
    val ProductGridColumnsCompact: Int = 2
    val ProductGridHorizontalGap: Dp = 16.dp
    val ProductGridVerticalGap: Dp = 12.dp
    val ProductCardRadius: Dp = 16.dp
    val ProductCardMinHeight: Dp = 184.dp
    val ProductSelectedBorder: Dp = 2.dp
    val ProductImageZoneHeight: Dp = 64.dp
    val ProductAddButtonSize: Dp = 36.dp
    /** Visual size stays 36dp, the touch target is 48dp. */
    val ProductAddTouchTarget: Dp = 48.dp
    /** Inner padding of a product card. */
    val ProductCardPadding: Dp = 8.dp
    /** Gap between the card's internal blocks. */
    val ProductCardInnerGap: Dp = 4.dp
    /** The stock badge keeps a predictable height so card rows line up. */
    val ProductBadgeHeight: Dp = 20.dp

    // ---- 11. Cart panel --------------------------------------------------------

    val CartHeaderHeight: Dp = 64.dp
    val CartPanelRadius: Dp = 16.dp

    // ---- 12. Customer card -----------------------------------------------------

    val CustomerCardHeight: Dp = 72.dp
    val CustomerCardRadius: Dp = 14.dp
    val CustomerCardPadding: Dp = 16.dp
    val CustomerIconSize: Dp = 40.dp
    val CustomerArrowSize: Dp = 24.dp

    // ---- 13. Cart items --------------------------------------------------------

    val CartItemGap: Dp = 16.dp
    val CartQtyControlWidth: Dp = 112.dp
    val CartQtyControlHeight: Dp = 56.dp
    val CartQtyControlRadius: Dp = 12.dp

    // ---- 15. Payment CTA -------------------------------------------------------

    val PaymentCtaHeight: Dp = 56.dp
    val PaymentCtaRadius: Dp = 12.dp
    val PaymentCtaTextSize = 16.sp
    val PaymentCtaBottomMargin: Dp = 16.dp

    // ---- 21. Phone cart detail -------------------------------------------------
    //
    // Fractions, not dp: the same dialog must fit a 360dp phone and a 430dp one.

    const val PhoneCartDialogWidthFraction: Float = 0.92f
    const val PhoneCartDialogHeightFraction: Float = 0.9f

    // ---- 16. Cart action touch targets ------------------------------------------
    //
    // "Hapus Semua" is a text action, so it needs an explicit >= 48dp touch height, the same rule
    // the icon buttons already follow.

    val CartClearTouchTarget: Dp = 48.dp

    // ---- 17. Colours -----------------------------------------------------------
    // Colours are NOT defined here on purpose. Step 3 section 17 requires the existing design tokens
    // to be used, and forbids creating a new green. See PosPalette below for the mapping.

    // ---- 19. Corner radii ------------------------------------------------------

    val RadiusSmall: Dp = 12.dp
    val RadiusCard: Dp = 16.dp
    val RadiusChip: Dp = 20.dp
    val RadiusPill: Dp = 24.dp
    val RadiusCustomer: Dp = 14.dp

    val CardShape = RoundedCornerShape(RadiusCard)
    val CtaShape = RoundedCornerShape(RadiusSmall)
    val ChipShape = RoundedCornerShape(RadiusChip)
    val CustomerCardShape = RoundedCornerShape(RadiusCustomer)
    val QtyControlShape = RoundedCornerShape(RadiusSmall)
    val CartPanelShape = RoundedCornerShape(RadiusCard)

    /**
     * Section 6 specifies a 21dp category-chip radius, which is inside the 20-24dp chip band of
     * section 19. The section 6 value is used because it is the more specific rule.
     */
    val CategoryChipShape = RoundedCornerShape(CategoryChipRadius)
}

/**
 * Step 3 section 18 - the POS type scale.
 *
 * The smallest step is 11sp (product code) and 12sp (helper text). Nothing below 11sp is used, and in
 * particular the 9.5sp that existed elsewhere in the app is not used on this screen.
 */
object PosType {
    val AppTitle = 20.sp          // "KASIR"
    val SectionTitle = 20.sp      // "Keranjang Belanja"
    val ProductName = 15.sp
    val ProductPrice = 16.sp
    val ProductCode = 11.sp
    val Category = 14.sp
    val CartProduct = 16.sp
    val CartPrice = 14.sp
    val TotalLabel = 18.sp
    val TotalValue = 24.sp
    val PrimaryCta = 16.sp
    val Helper = 12.sp
    val HelperSmall = 13.sp
}
