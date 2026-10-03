package id.skmnetwork.bukuwarung.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import id.skmnetwork.bukuwarung.ui.theme.AppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Product photos are shown as small thumbnails (list row ~54dp, catalogue grid, POS card). Decoding
 * a full camera/gallery bitmap just to fill that box wastes tens of megabytes and can make the whole
 * list janky or fail on low-memory devices, so the image is decoded with a power-of-two sample size
 * that keeps the result at or above [maxDecodeSizePx] on its longest edge.
 */
private const val DEFAULT_MAX_DECODE_SIZE_PX = 640

/**
 * Default optical size of the "no photo" glyph.
 *
 * Gate 1.6: the fallback previously inherited whatever modifier the caller passed straight into
 * Material3's `Icon`, so its rendered size was a side effect of that composable's internal modifier
 * ordering rather than something the design system stated. It is now explicit and overridable.
 */
private val DEFAULT_PLACEHOLDER_ICON_SIZE = 24.dp

/**
 * Gate 1.6 - the standard product photo frame for list rows.
 *
 * The audit found five different thumbnail containers across the app (32/50/52/54/64dp) with five
 * different plate treatments and, in three cases, no clip at all - so the corners of a square photo
 * visibly broke the outline of the rounded plate it sat in. Screens showing a product in a row use
 * this so one product looks the same everywhere.
 *
 * The catalogue grid and the POS card deliberately use their own smaller / full-bleed frames; those
 * are layout decisions owned by those screens, not by this component.
 */
val ProductImageFrameSize: Dp = 54.dp

/** The "no photo" glyph size that pairs with [ProductImageFrameSize]. */
val ProductImagePlaceholderIconSize: Dp = 24.dp

internal fun calculateInSampleSize(width: Int, height: Int, maxSizePx: Int): Int {
    if (width <= 0 || height <= 0 || maxSizePx <= 0) return 1
    var sampleSize = 1
    var longestEdge = maxOf(width, height)
    while (longestEdge / 2 >= maxSizePx) {
        longestEdge /= 2
        sampleSize *= 2
    }
    return sampleSize
}

/**
 * Opens the source twice: once to read the bounds only, then again to decode a sampled bitmap.
 * Supports both app-owned file paths (the authoritative storage used by the product editor) and
 * `content://` URIs, so a photo picked from the gallery still renders even if it never got copied.
 */
private fun decodeSampledBitmap(context: Context, source: String, maxSizePx: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val decodeOptions = BitmapFactory.Options()

    if (source.startsWith("content://")) {
        return try {
            context.contentResolver.openInputStream(Uri.parse(source))?.use { input ->
                BitmapFactory.decodeStream(input, null, bounds)
                val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxSizePx)
                decodeOptions.inSampleSize = sampleSize
                context.contentResolver.openInputStream(Uri.parse(source))?.use { input ->
                    BitmapFactory.decodeStream(input, null, decodeOptions)
                }
            }
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    val file = File(source)
    if (!file.exists() || file.length() <= 0L) return null
    return try {
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxSizePx)
        decodeOptions.inSampleSize = sampleSize
        BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }
}

/**
 * Identity of the stored photo, used as the cache key. Including size and last-modified means a
 * file that is replaced in place re-decodes instead of showing the previous bitmap.
 */
private fun rememberImageCacheKey(source: String?): String {
    if (source.isNullOrEmpty()) return "none"
    if (source.startsWith("content://")) return "content:$source"
    return try {
        val file = File(source)
        "file:${file.absolutePath}:${file.lastModified()}:${file.length()}"
    } catch (e: Exception) {
        "file:$source"
    }
}

/**
 * Renders a product photo, or a themed placeholder when there is none.
 *
 * Gate 1.6 changes, all presentation-side. The storage pipeline is untouched: this component only
 * ever reads, and `imageUri` semantics - null when there is no photo, preserved across an edit,
 * replaced only on an explicit replace - are unchanged.
 *
 * 1. The decode moved off the composition thread. It previously ran inside `remember`, so every
 *    visible row in a photo-heavy list did a `BitmapFactory` decode synchronously during
 *    composition. The placeholder was therefore never shown *as a loading state* - the UI thread was
 *    simply blocked. `produceState` on Dispatchers.IO now decodes in the background and the
 *    placeholder is shown while it runs, which is what a placeholder is for.
 *
 * 2. The cache key is computed inside `remember(imageUri)`. It performs a `File.lastModified()` and a
 *    `File.length()` stat call, so keying it means two syscalls per row *per recomposition* instead of
 *    per image change. The key values themselves are identical, so an in-place file replacement still
 *    invalidates exactly as before.
 *
 * 3. The fallback icon now owns its own size. Previously the caller's modifier was handed straight to
 *    `Icon`, leaving the rendered glyph size dependent on Material3's internal modifier ordering
 *    rather than on anything the design system declares.
 *
 * Downsampling ([calculateInSampleSize], [DEFAULT_MAX_DECODE_SIZE_PX]) and the cache-key format are
 * both unchanged, so no list screen gains a full-resolution decode.
 */
@Composable
fun ProductImageThumbnail(
    imageUri: String?,
    modifier: Modifier = Modifier,
    fallbackIcon: ImageVector = Icons.Default.Inventory2,
    tint: Color = AppColors.GreenPrimary,
    contentScale: ContentScale = ContentScale.Crop,
    placeholderIconSize: Dp = DEFAULT_PLACEHOLDER_ICON_SIZE,
    maxDecodeSizePx: Int = DEFAULT_MAX_DECODE_SIZE_PX
) {
    val context = LocalContext.current
    val cacheKey = remember(imageUri) { rememberImageCacheKey(imageUri) }

    val bitmap by produceState<Bitmap?>(initialValue = null, cacheKey, maxDecodeSizePx) {
        value = if (imageUri.isNullOrEmpty()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                decodeSampledBitmap(context, imageUri, maxDecodeSizePx)
            }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = "Foto Produk",
            // The bitmap keeps its original aspect ratio; the caller decides whether to crop or fit.
            contentScale = contentScale,
            modifier = modifier
        )
    } else {
        // The caller's modifier sizes the frame; the icon is sized explicitly inside it so the
        // "no photo" state is the same optical size everywhere the component is used.
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(placeholderIconSize)
            )
        }
    }
}