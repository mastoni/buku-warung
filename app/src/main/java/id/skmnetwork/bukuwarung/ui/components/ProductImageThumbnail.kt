package id.skmnetwork.bukuwarung.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import id.skmnetwork.bukuwarung.ui.theme.AppColors
import java.io.File

@Composable
fun ProductImageThumbnail(
    imageUri: String?,
    modifier: Modifier = Modifier,
    fallbackIcon: ImageVector = Icons.Default.Inventory2,
    tint: Color = AppColors.GreenPrimary,
    contentScale: ContentScale = ContentScale.Crop
) {
    val bitmap = remember(imageUri) {
        if (!imageUri.isNullOrEmpty()) {
            try {
                val file = File(imageUri)
                if (file.exists() && file.length() > 0) {
                    BitmapFactory.decodeFile(file.absolutePath)
                } else null
            } catch (e: Exception) {
                null
            }
        } else null
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Foto Produk",
            contentScale = contentScale,
            modifier = modifier
        )
    } else {
        Icon(
            imageVector = fallbackIcon,
            contentDescription = null,
            tint = tint,
            modifier = modifier
        )
    }
}
