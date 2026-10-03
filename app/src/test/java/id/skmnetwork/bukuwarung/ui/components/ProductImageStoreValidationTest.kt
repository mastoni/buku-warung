package id.skmnetwork.bukuwarung.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Covers the rules that decide whether a picked photo may become the persisted product image, and
 * the rule that protects the photo a product is still using.
 *
 * [ProductImageStore.isDecodableImage] takes the decoder as a parameter precisely so this rule can
 * be exercised on the JVM, where BitmapFactory is unavailable, and so a truncated or non-image
 * payload is rejected instead of being trusted because of its file extension.
 */
class ProductImageStoreValidationTest {

    private fun tempFile(name: String, bytes: ByteArray): File {
        val dir = System.getProperty("java.io.tmpdir")?.let { File(it, "bw-image-store-test") }
            ?: File("target/bw-image-store-test")
        dir.mkdirs()
        val file = File(dir, name)
        file.writeBytes(bytes)
        return file
    }

    @Test
    fun `valid image with positive bounds passes validation`() {
        val file = tempFile("valid.jpg", ByteArray(2048) { 0x42 })
        assertTrue(ProductImageStore.isDecodableImage(file) { 1600 to 1200 })
    }

    @Test
    fun `empty file fails validation`() {
        val file = tempFile("empty.jpg", ByteArray(0))
        assertFalse(ProductImageStore.isDecodableImage(file) { 1600 to 1200 })
    }

    @Test
    fun `missing file fails validation`() {
        val file = File(System.getProperty("java.io.tmpdir"), "bw-does-not-exist.jpg")
        assertFalse(ProductImageStore.isDecodableImage(file) { 1600 to 1200 })
    }

    @Test
    fun `decoder reporting zero width fails validation`() {
        val file = tempFile("zero-width.jpg", ByteArray(512) { 0x11 })
        assertFalse(ProductImageStore.isDecodableImage(file) { 0 to 1200 })
    }

    @Test
    fun `decoder reporting zero height fails validation`() {
        val file = tempFile("zero-height.jpg", ByteArray(512) { 0x11 })
        assertFalse(ProductImageStore.isDecodableImage(file) { 1600 to 0 })
    }

    @Test
    fun `decoder that cannot read the payload fails validation`() {
        val file = tempFile("truncated.jpg", byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x01))
        assertFalse(ProductImageStore.isDecodableImage(file) { throw IllegalStateException("incomplete image") })
    }

    @Test
    fun `extension alone never makes a file valid`() {
        // Named like an image, but the decoder finds no image data behind it.
        val file = tempFile("looks-like-a-photo.jpg", "not an image at all".toByteArray())
        assertFalse(ProductImageStore.isDecodableImage(file) { 0 to 0 })
    }

    @Test
    fun `sampled decode never requests full resolution for a thumbnail`() {
        // The loader samples by longest edge; a photo must never be decoded at its stored size.
        val bounds = 4000 to 3000
        val sample = calculateInSampleSize(bounds.first, bounds.second, 640)
        assertTrue("sampled decode must stay at or above the requested cap", bounds.first / sample >= 640)
        assertEquals(4, sample)
    }

    @Test
    fun `small image is not downscaled further`() {
        assertEquals(1, calculateInSampleSize(320, 240, 640))
    }
}