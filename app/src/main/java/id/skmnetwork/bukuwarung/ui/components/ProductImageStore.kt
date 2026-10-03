package id.skmnetwork.bukuwarung.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.util.UUID

/**
 * App-owned storage for product photos.
 *
 * A picked gallery image is copied into [PRODUCT_IMAGES_DIR] because a `content://` grant is
 * temporary: it stops resolving after navigation, process death, or a revoked permission, which
 * would leave the product permanently without a photo. The copy is treated as untrusted until it
 * has been decoded, so a truncated stream can never become a persisted [imageUri].
 */
object ProductImageStore {

    const val PRODUCT_IMAGES_DIR = "product_images"

    private const val TEMP_SUFFIX = ".part"

    /** Thumbnail decode target; also the sanity bound used while validating a fresh copy. */
    private const val VALIDATION_DECODE_SIZE_PX = 1024

    fun imagesDir(context: Context): File =
        File(context.filesDir, PRODUCT_IMAGES_DIR).apply { if (!exists()) mkdirs() }

    /**
     * True when [path] points at a file this app owns inside [PRODUCT_IMAGES_DIR]. Anything else
     * (legacy absolute paths, shared storage, camera output the user still manages) is never
     * deleted by this helper.
     */
    fun isOwnedProductImage(context: Context, path: String?): Boolean {
        if (path.isNullOrEmpty()) return false
        return try {
            val file = File(path).canonicalFile
            val dir = imagesDir(context).canonicalFile
            file.parentFile == dir && file.isFile
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Validates that [file] is a decodable image with non-zero bounds.
     *
     * [decode] is injected so the rule can be unit tested on the JVM, where [BitmapFactory] is not
     * available. Extensions are never treated as proof: only the decoded bounds are.
     */
    fun isDecodableImage(file: File, decode: (File) -> Pair<Int, Int>): Boolean {
        if (!file.exists() || !file.isFile || file.length() <= 0L) return false
        val (width, height) = try {
            decode(file)
        } catch (e: Exception) {
            return false
        } catch (e: OutOfMemoryError) {
            return false
        }
        return width > 0 && height > 0
    }

    /** Default decoder: reads bounds only, so a full-resolution decode is never needed to validate. */
    private fun decodeBounds(file: File): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        return options.outWidth to options.outHeight
    }

    /** Full read of the source into [dest]; throws on any short read so the caller can clean up. */
    private fun copyStream(source: Uri, context: Context, dest: File) {
        val input = context.contentResolver.openInputStream(source)
            ?: throw IllegalStateException("Source stream unavailable")
        input.use { inputStream ->
            dest.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
                outputStream.flush()
            }
        }
    }

    /**
     * Copies a picked image into app-owned storage and returns the authoritative path, or null when
     * the copy could not be completed and decoded. The bytes land in a temporary file first and are
     * only promoted to the final name after validation, so a partial file is never reachable through
     * the returned path.
     */
    fun copyToProductStorage(
        context: Context,
        source: Uri,
        decode: (File) -> Pair<Int, Int> = ::decodeBounds
    ): String? {
        val dir = imagesDir(context)
        val tempFile = File(dir, "pending_${UUID.randomUUID()}$TEMP_SUFFIX")
        val finalFile = File(dir, "prod_gal_${UUID.randomUUID()}.jpg")
        return try {
            copyStream(source, context, tempFile)
            if (!isDecodableImage(tempFile, decode)) {
                tempFile.delete()
                return null
            }
            if (!tempFile.renameTo(finalFile)) {
                // renameTo can fail across some filesystems; fall back to a validated copy.
                tempFile.copyTo(finalFile, overwrite = true)
                tempFile.delete()
                if (!isDecodableImage(finalFile, decode)) {
                    finalFile.delete()
                    return null
                }
            }
            finalFile.absolutePath
        } catch (e: Exception) {
            tempFile.delete()
            null
        } catch (e: OutOfMemoryError) {
            tempFile.delete()
            null
        }
    }

    /**
     * Removes a superseded photo. Only app-owned files inside [PRODUCT_IMAGES_DIR] are eligible and
     * [keepPath] protects the image that is still referenced, so a failed replacement can never
     * destroy the photo the product is currently using.
     */
    fun deleteIfSuperseded(context: Context, path: String?, keepPath: String?) {
        if (path.isNullOrEmpty()) return
        if (keepPath != null && path == keepPath) return
        if (!isOwnedProductImage(context, path)) return
        try {
            File(path).delete()
        } catch (e: Exception) {
            // A leftover file is harmless; never fail the caller over cleanup.
        }
    }

    /** Decode target shared with the thumbnail loader. */
    const val THUMBNAIL_DECODE_SIZE_PX = VALIDATION_DECODE_SIZE_PX

    /** Exposed for callers that need to validate a path they already have. */
    fun isValidStoredImage(file: File): Boolean = isDecodableImage(file, ::decodeBounds)
}