package id.skmnetwork.bukuwarung.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Step 19 - the touch-target contract for the primary actions audited in Step 18.
 *
 * The audit proved three concrete gaps: the POS barcode scanner was a 24dp clickable, the purchase
 * cart line delete was a 22dp IconButton, and ten primary action buttons were 40-46dp tall. All of
 * them are money or scanner controls, so they follow the 48dp rule the project already documents in
 * `PosDesign.kt` and applies to the POS product, cart and stepper controls.
 *
 * This is a source-contract test rather than a Compose test: the project's only runnable
 * instrumentation variant is `release`, and `androidx.compose.ui:ui-test-manifest` - which supplies
 * the `ComponentActivity` that `createComposeRule()` needs - is a `debugImplementation` dependency,
 * so Compose UI tests cannot run here. Asserting the measured constants directly keeps the
 * regression closed without touching that infrastructure.
 */
class TouchTargetContractTest {

    private companion object {
        const val MIN_TOUCH_TARGET_DP = 48
    }

    private fun source(relative: String): String {
        val candidates = listOf(
            File("src/main/java/id/skmnetwork/bukuwarung/$relative"),
            File("app/src/main/java/id/skmnetwork/bukuwarung/$relative")
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Source not found; looked at ${candidates.joinToString { it.absolutePath }}")
        return file.readText()
    }

    private fun assertDpAtLeast(label: String, value: String, minimum: Int = MIN_TOUCH_TARGET_DP) {
        val dp = value.removeSuffix(".dp").toInt()
        assertTrue(
            "$label is ${value} - it must be at least ${minimum}dp",
            dp >= minimum
        )
    }

    // ---- G1: POS barcode scanner -------------------------------------------

    @Test
    fun `pos scanner keeps its visual icon but gains a 48dp target`() {
        val metrics = source("ui/pos/PosDesign.kt")
        assertTrue(
            "The scanner must keep its 24dp visual icon",
            metrics.contains("val SearchTrailingIconSize: Dp = 24.dp")
        )
        assertTrue(
            "The scanner must declare a 48dp touch target",
            metrics.contains("val SearchTrailingTouchTarget: Dp = 48.dp")
        )

        val layout = source("ui/pos/PosMeasuredLayout.kt")
        val scanner = layout.substring(layout.indexOf("trailingIcon = {")).substringBefore("colors = OutlinedTextFieldDefaults")
        assertTrue(
            "The scanner click must sit on the 48dp target",
            scanner.contains(".size(PosMetrics.SearchTrailingTouchTarget)")
        )
        assertTrue(
            "The scanner must still receive the scan callback",
            scanner.contains("onScanClick()")
        )
        assertTrue(
            "The scanner must still render the barcode icon at its 24dp visual size",
            scanner.contains("modifier = Modifier.size(PosMetrics.SearchTrailingIconSize)")
        )
        assertTrue(
            "The clickable must no longer sit directly on the small icon",
            !scanner.contains(".size(PosMetrics.SearchTrailingIconSize)\n                    .clickable")
        )
    }

    @Test
    fun `the pos search field height is unchanged so the measured layout is not distorted`() {
        val metrics = source("ui/pos/PosDesign.kt")
        assertTrue(
            "SearchHeight must stay 54dp; a 48dp target must not resize the measured POS search bar",
            metrics.contains("val SearchHeight: Dp = 54.dp")
        )
    }

    // ---- G2: purchase cart delete ------------------------------------------

    @Test
    fun `purchase cart delete has a 48dp target`() {
        val purchase = source("ui/purchase/PurchaseScreen.kt")
        assertTrue(
            "The 22dp cart delete target must be gone",
            !purchase.contains("Modifier.size(22.dp)\n                    ) {\n                        Icon(\n                            imageVector = Icons.Default.Close")
        )
        val clearBlock = purchase.substring(purchase.indexOf("onClick = onClear") - 200, purchase.indexOf("onClick = onClear") + 200)
        assertTrue(
            "The cart clear control must be 48dp",
            clearBlock.contains("size(48.dp)")
        )
        assertTrue(
            "The cart clear control must keep its semantics",
            clearBlock.contains("onClick = onClear")
        )
    }

    // ---- G3: verified primary action buttons -------------------------------

    @Test
    fun `every verified primary action button is at least 48dp`() {
        val expectations = listOf(
            Triple("ui/customer/CustomersScreen.kt", "Tambah Pelanggan", 2),
            Triple("ui/cash/CashScreen.kt", "Cash income/expense", 2),
            Triple("ui/product/ProductsScreen.kt", "Add product", 2),
            Triple("ui/purchase/PurchaseScreen.kt", "Purchase actions", 4)
        )
        for ((path, label, expectedCount) in expectations) {
            val text = source(path)
            val found = Regex("height\\((4[8-9]|[5-9]\\d)\\.dp\\)").findAll(text).count()
            assertTrue(
                "$label in $path should expose at least $expectedCount 48dp+ actions, found $found",
                found >= expectedCount
            )
        }
    }

    @Test
    fun `no verified site is still below 48dp`() {
        // The exact lines audited in Step 18, re-checked by content rather than line number.
        val checks = listOf(
            "ui/customer/CustomersScreen.kt" to listOf("height(42.dp)", "height(46.dp)"),
            "ui/cash/CashScreen.kt" to listOf("height(46.dp)"),
            "ui/product/ProductsScreen.kt" to listOf("height(44.dp)"),
            "ui/purchase/PurchaseScreen.kt" to listOf("height(40.dp)", "height(44.dp)")
        )
        for ((path, forbidden) in checks) {
            val text = source(path)
            for (value in forbidden) {
                assertTrue(
                    "$path must no longer contain $value",
                    !text.contains(".$value")
                )
            }
        }
    }

    @Test
    fun `the compact controls that were deliberately out of scope are untouched`() {
        // The purchase screen's segmented tab bar is a container, not a primary action, and the
        // search reset is a secondary control. Neither is part of this gate.
        val purchase = source("ui/purchase/PurchaseScreen.kt")
        assertTrue(
            "The segmented tab bar must be left as it was",
            purchase.contains(".height(42.dp)")
        )
        val filters = source("ui/components/ProductSearchFilters.kt")
        assertTrue(
            "The search reset control must be left as it was",
            filters.contains("height(38.dp)")
        )
    }

    @Test
    fun `the documented pos accessibility rule is intact`() {
        val metrics = source("ui/pos/PosDesign.kt")
        assertTrue(
            "The product add target must stay 48dp",
            metrics.contains("ProductAddTouchTarget")
        )
        assertTrue(
            "The cart clear target must stay 48dp",
            metrics.contains("CartClearTouchTarget")
        )
        assertTrue(
            "The category row must stay 48dp",
            metrics.contains("CategoryRowHeight: Dp = 48.dp")
        )
    }
}
