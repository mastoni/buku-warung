package id.skmnetwork.bukuwarung.ui.product

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Step 11 - the product form's error contract.
 *
 * The add/edit form used to receive failures as a bare sentence and then decide which field to mark
 * by comparing that sentence to a hardcoded Indonesian literal, so the user-facing copy was
 * load-bearing: reword a message and the field silently stopped being highlighted. The reason is now
 * carried as [ProductFormError], the same sealed-hierarchy shape the project already uses for
 * `license.ValidationResult`, `StockAdjustmentValidation` and `BackupValidationResult`.
 *
 * These tests pin the two properties that matter: the reason is a stable semantic value, and not a
 * single character of the Indonesian copy changed.
 */
class ProductFormErrorContractTest {

    @Test
    fun `each validation reason is its own semantic value`() {
        val reasons = listOf(
            ProductFormError.NameRequired(),
            ProductFormError.SellingPriceRequired(),
            ProductFormError.InvalidNumber(),
            ProductFormError.DigitalProviderIdRequired(),
            ProductFormError.DigitalProductCodeRequired(),
            ProductFormError.OperationFailed("Gagal menyimpan produk:原因")
        )
        assertEquals(
            "Every distinct reason must be a distinct type/value, not a shared string",
            reasons.size,
            reasons.distinct().size
        )
        assertTrue("A reason must be usable as a type check", reasons.first() is ProductFormError.NameRequired)
        assertTrue(
            "A selling-price failure must not look like a name failure",
            ProductFormError.SellingPriceRequired() !is ProductFormError.NameRequired
        )
    }

    @Test
    fun `the indonesian messages are byte for byte what the form showed before`() {
        assertEquals("Tulis nama produk", ProductFormError.NameRequired().message)
        assertEquals("Masukkan harga jual", ProductFormError.SellingPriceRequired().message)
        assertEquals("Tulis angka yang valid", ProductFormError.InvalidNumber().message)
        assertEquals("ID provider digital wajib diisi", ProductFormError.DigitalProviderIdRequired().message)
        assertEquals("Kode produk digital wajib diisi", ProductFormError.DigitalProductCodeRequired().message)
    }

    @Test
    fun `a repository failure keeps the explanation it was given`() {
        val detail = "Gagal menyimpan produk: database terkunci"
        assertEquals(detail, ProductFormError.OperationFailed(detail).message)
    }

    @Test
    fun `field attribution no longer depends on the wording`() {
        // This is the property the old string comparison could not have: the form marks the same
        // field for the same reason even when the two call sites word it differently. The screen
        // uses "ID Provider wajib diisi untuk mode Otomatis" while the ViewModel says
        // "ID provider digital wajib diisi" - same reason, different copy, same field.
        val fromScreen = ProductFormError.DigitalProviderIdRequired("ID Provider wajib diisi untuk mode Otomatis")
        val fromViewModel = ProductFormError.DigitalProviderIdRequired()

        assertNotEquals("The two call sites do word it differently", fromScreen.message, fromViewModel.message)
        assertTrue(
            "Yet both must be recognised as the provider-id reason",
            fromScreen is ProductFormError.DigitalProviderIdRequired &&
                fromViewModel is ProductFormError.DigitalProviderIdRequired
        )
    }

    @Test
    fun `the form no longer compares error text`() {
        val source = readSource("ui/product/AddProductScreen.kt")
        for (forbidden in listOf(
            "errorMessage ==",
            "errorMessage != ",
            "formError == \"",
            "formError.message ==",
            ".message == \"Tulis",
            ".message == \"Masukkan"
        )) {
            assertTrue(
                "The form must not branch on error text, found '$forbidden'",
                !source.contains(forbidden)
            )
        }
    }

    @Test
    fun `the form identifies fields by reason type`() {
        val source = readSource("ui/product/AddProductScreen.kt")
        for (reason in listOf(
            "ProductFormError.NameRequired",
            "ProductFormError.SellingPriceRequired",
            "ProductFormError.DigitalProviderIdRequired",
            "ProductFormError.DigitalProductCodeRequired"
        )) {
            assertTrue(
                "The form must mark fields with an 'is $reason' check",
                source.contains("is $reason")
            )
        }
        assertTrue(
            "The form must still show the merchant the message",
            source.contains("formError!!.message")
        )
    }

    @Test
    fun `the viewmodel reports reasons instead of sentences`() {
        val source = readSource("ui/product/ProductViewModel.kt")
        assertTrue(
            "saveProduct/updateProduct must take a typed reason",
            source.contains("onError: (ProductFormError) -> Unit")
        )
        for (bare in listOf(
            "onError(\"Tulis nama produk\")",
            "onError(\"Masukkan harga jual\")",
            "onError(\"Tulis angka yang valid\")",
            "onError(\"ID provider digital wajib diisi\")",
            "onError(\"Kode produk digital wajib diisi\")"
        )) {
            assertTrue(
                "The viewmodel must not emit a bare sentence, found '$bare'",
                !source.contains(bare)
            )
        }
    }

    @Test
    fun `other product operations keep their own string error channel`() {
        // Only the product form was decoupled. Checkout, purchase, cash and return keep the
        // existing callback shape, so no unrelated behaviour moved.
        val source = readSource("ui/product/ProductViewModel.kt")
        for (untouched in listOf("checkoutCart", "checkoutPurchase", "addManualCash", "processSaleReturn", "deleteProduct")) {
            val start = source.indexOf("fun $untouched(")
            assertTrue("Expected to find $untouched", start >= 0)
            val end = source.indexOf("\n    fun ", start + 1).let { if (it < 0) source.length else it }
            val body = source.substring(start, end)
            assertTrue(
                "$untouched must keep the string error channel it had before",
                body.contains("onError: (String) -> Unit")
            )
        }
    }

    private fun readSource(relative: String): String {
        val candidates = listOf(
            File("src/main/java/id/skmnetwork/bukuwarung/$relative"),
            File("app/src/main/java/id/skmnetwork/bukuwarung/$relative")
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Source not found; looked in ${candidates.joinToString { it.absolutePath }}")
        return file.readText()
    }
}
