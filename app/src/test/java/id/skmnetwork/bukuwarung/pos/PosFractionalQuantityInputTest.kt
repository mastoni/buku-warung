package id.skmnetwork.bukuwarung.pos

import androidx.compose.ui.unit.dp
import id.skmnetwork.bukuwarung.ui.pos.PosMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Step 5 - the POS offers precise quantity entry, and the calculation path is untouched.
 *
 * The gap this pins: the cart's stepper was the only way to change a quantity, so a merchant
 * selling 1.25 kg had to press "+" a dozen times. The fix adds a tappable number that opens the
 * existing input field; it must not become a second place where quantity is rounded, clamped or
 * formatted, because the money and stock paths from Step 2 are locked.
 */
class PosFractionalQuantityInputTest {

    private val measuredLayout = File("src/main/java/id/skmnetwork/bukuwarung/ui/pos/PosMeasuredLayout.kt")
    private val posScreen = File("src/main/java/id/skmnetwork/bukuwarung/ui/pos/PosScreen.kt")
    private val quantityUtils = File("src/main/java/id/skmnetwork/bukuwarung/util/QuantityUtils.kt")

    private fun source(file: File): String = file.readText().replace("\r\n", "\n")

    private fun body(text: String, from: String, to: String): String =
        text.substringAfter(from).substringBefore(to)

    @Test
    fun theStepperRemainsAndTheNumberBecomesPreciseEntry() {
        val text = source(measuredLayout)
        val control = body(text, "fun PosQuantityControl(", "fun PosCustomerCard(")

        assertTrue("The stepper must remain the quick adjustment", control.contains("onIncrement"))
        assertTrue("The stepper must remain the quick adjustment", control.contains("onDecrement"))
        assertTrue(
            "The displayed number must be tappable and open the precise editor",
            control.contains("showInput = true")
        )
        assertTrue(
            "Precise entry must be optional, so a caller without it keeps a pure stepper",
            control.contains("onQuantityInput: ((Double) -> Unit)? = null")
        )
    }

    @Test
    fun theEditorReusesTheExistingAppInputAndParser() {
        val text = source(measuredLayout)
        val dialog = body(text, "fun PosQuantityInputDialog(", "// ====")

        // Gate 1.2 replaced AppTextField with FieldAmount, which delegates to the shared
        // FieldDefault. The contract is therefore "the app's shared quantity field", asserted
        // through the component and the flag that selects it - a stronger statement than one
        // literal component name.
        assertTrue(
            "The editor must reuse the app's shared quantity field",
            dialog.contains("FieldAmount(") && dialog.contains("quantity = true")
        )
        // The decimal keyboard moved into FieldAmount together with the quantity flag, so it is
        // asserted where the decision now lives rather than as a literal this file no longer owns.
        val foundation = source(File("src/main/java/id/skmnetwork/bukuwarung/ui/components/FoundationComponents.kt"))
        assertTrue(
            "The editor must use the decimal keyboard the rest of the app uses",
            foundation.contains("if (quantity) KeyboardType.Decimal else KeyboardType.Number")
        )
        assertTrue(
            "The editor must use the one shared parser, not a private copy",
            dialog.contains("parseQuantityInput(text)")
        )
        assertTrue(
            "The editor must display the current value with the existing formatter",
            dialog.contains("formatQuantityValue(currentQuantity)")
        )
        assertTrue("The editor must be dismissible", dialog.contains("dismissButton"))
    }

    @Test
    fun theEditorInventsNoNewQuantityRules() {
        val text = source(measuredLayout)
        val dialog = body(text, "fun PosQuantityInputDialog(", "// ====")

        // Only the three refusals the stepper already implies.
        assertTrue("Non-numeric text must be refused", dialog.contains("Masukkan angka yang valid"))
        assertTrue("Negative text must be refused", dialog.contains("Jumlah tidak boleh kurang dari 0"))
        assertTrue("Over-stock must be refused", dialog.contains("Jumlah maksimal"))
        assertTrue(
            "The editor must not round or truncate a parsed value",
            !dialog.contains("Math.round") &&
                !dialog.contains(".toInt()") &&
                !dialog.contains("roundTo") &&
                !dialog.contains("setScale")
        )
    }

    @Test
    fun theCartKeepsTheExistingStepperRulesOnPreciseEntry() {
        val text = source(posScreen)
        val cartLine = body(text, "onQuantityInput = if (isStockableCartLine", "}\n                            )")

        assertTrue(
            "An emptied line must still be removed, exactly as the stepper does",
            cartLine.contains("if (precise <= 0.0) {") && cartLine.contains("cart.removeAt(index)")
        )
        assertTrue(
            "A value above the remaining stock must not be stored",
            cartLine.contains("precise <= line.product.stock")
        )
        assertTrue(
            "The line must be replaced with the exact value, not a rounded one",
            cartLine.contains("line.copy(quantity = precise)")
        )
        assertTrue(
            "Precise entry must not round or truncate",
            !cartLine.contains("Math.round") && !cartLine.contains(".toInt()")
        )
    }

    @Test
    fun preciseEntryIsLimitedToStockableGoods() {
        val text = source(posScreen)

        assertTrue(
            "Only stockable goods may get fractional entry; services and digital items keep the +1 stepper",
            text.contains("product.itemType != ItemType.SERVICE.name && product.itemType != ItemType.DIGITAL.name")
        )
        assertTrue(
            "The stock cap must come from the product's own stock",
            text.contains("maxQuantity = if (isStockableCartLine(line.product)) {")
        )
    }

    @Test
    fun theMoneyAndStockCalculationPathIsUntouched() {
        val text = source(posScreen)

        // The stepper's own arithmetic must be unchanged, and the totals must still be derived
        // from the cart lines rather than from anything the editor produced.
        assertTrue(
            "The stepper's stock check must be unchanged",
            text.contains("if (currentQty + 1.0 <= product.stock) {")
        )
        assertTrue(
            "The stepper's decrement must be unchanged",
            text.contains("if (line.quantity - 1.0 <= 0.0) {")
        )
        assertTrue(
            "The discount must still be parsed the way it was",
            text.contains("val parsedDiscountValue = discountInputText.trim().toDoubleOrNull() ?: 0.0")
        )
        // No BigDecimal or money helper may have been introduced into the POS by this change.
        assertTrue(
            "The POS must not start computing money itself",
            !text.contains("BigDecimal")
        )
    }

    @Test
    fun theLockedStep3bQuantityControlMeasurementsAreUnchanged() {
        // Step 3B locked these; the precise entry was added inside the control, not by resizing it.
        // Gate 1.3 replaced the non-compact 96dp literal with a named token: 96dp could not contain
        // the control's own children (a 48dp decrement target, the value column and a 48dp increment
        // target is 132dp), so Compose coerced the trailing "+" down to roughly 12dp. The compact
        // width and the height below are still the Step 3B values and are still asserted unchanged.
        assertEquals(112.dp, PosMetrics.CartQtyControlWidth)
        assertEquals(56.dp, PosMetrics.CartQtyControlHeight)
        // Pin the replacement numerically as well as structurally, so editing the token's value
        // fails here instead of silently changing the control's geometry.
        assertEquals(136.dp, PosMetrics.ProductQtyControlWidth)
        val text = source(measuredLayout)
        val control = body(text, "fun PosQuantityControl(", "fun PosQuantityInputDialog(")
        assertTrue(
            "The control must keep its locked width rule",
            control.contains(
                "val width = if (compact) PosMetrics.CartQtyControlWidth else PosMetrics.ProductQtyControlWidth"
            )
        )
        assertTrue(
            "The control must keep its locked height",
            control.contains(".height(PosMetrics.CartQtyControlHeight)")
        )
    }

    @Test
    fun theSharedParserIsTheSingleQuantityEntryPoint() {
        val utils = source(quantityUtils)
        assertTrue("The parser must live beside the formatter", utils.contains("fun parseQuantityInput"))
        assertTrue(
            "The parser must normalise the Indonesian decimal comma",
            utils.contains("replace(',', '.')")
        )
        assertTrue(
            "The parser must return null rather than inventing a zero",
            utils.contains("toDoubleOrNull()")
        )
    }

    @Test
    fun theStep2StockPreservationAndTenantBindingSurvive() {
        val product = File("src/main/java/id/skmnetwork/bukuwarung/ui/product/StockAdjustmentDialog.kt")
        val productText = source(product)
        assertTrue(
            "The stock dialog must keep writing through adjustStock",
            productText.contains("onSave(parsed, note.trim().ifEmpty { null }, isStockCount)")
        )
        assertTrue(
            "The stock dialog must keep refusing a negative stock",
            productText.contains("Stok tidak boleh kurang dari 0")
        )

        val navigation = File("src/main/java/id/skmnetwork/bukuwarung/ui/navigation/AppNavigation.kt")
        val navText = source(navigation)
        assertTrue("The tenant gate must remain", navText.contains("TenantGate(userSettings = loadedUserSettings)"))
        assertTrue(
            "Repositories must remain keyed on the active tenant",
            navText.contains("remember(activeBusinessId) { ProductRepository(database, activeBusinessId) }")
        )
    }
}
