package id.skmnetwork.bukuwarung.catalog

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Gate H — Native Android Intent Helper for sharing catalog via WhatsApp and generic share sheet.
 */
object WhatsAppCatalogShareHelper {

    const val PACKAGE_WHATSAPP = "com.whatsapp"
    const val PACKAGE_WHATSAPP_BUSINESS = "com.whatsapp.w4b"

    /**
     * Checks whether WhatsApp or WhatsApp Business is installed on the host device.
     */
    fun isWhatsAppInstalled(context: Context): Boolean {
        val pm = context.packageManager
        return isPackageInstalled(pm, PACKAGE_WHATSAPP) || isPackageInstalled(pm, PACKAGE_WHATSAPP_BUSINESS)
    }

    private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
        return try {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Creates an Intent to share catalog text.
     * If directToWhatsApp is true and WhatsApp is installed, sets WhatsApp package.
     */
    fun createShareIntent(
        catalogText: String,
        directToWhatsApp: Boolean = false,
        context: Context? = null
    ): Intent {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, catalogText)
        }

        if (directToWhatsApp && context != null) {
            val pm = context.packageManager
            if (isPackageInstalled(pm, PACKAGE_WHATSAPP)) {
                intent.setPackage(PACKAGE_WHATSAPP)
            } else if (isPackageInstalled(pm, PACKAGE_WHATSAPP_BUSINESS)) {
                intent.setPackage(PACKAGE_WHATSAPP_BUSINESS)
            }
        }

        return intent
    }

    /**
     * Creates an Android Chooser Intent for catalog text sharing.
     */
    fun createShareChooserIntent(
        context: Context,
        catalogText: String,
        chooserTitle: String = "Bagikan Katalog Produk"
    ): Intent {
        val baseIntent = createShareIntent(catalogText)
        return Intent.createChooser(baseIntent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Launches the share chooser intent safely on the given context.
     */
    fun shareCatalog(
        context: Context,
        catalogText: String,
        chooserTitle: String = "Bagikan Katalog via WhatsApp"
    ) {
        val chooserIntent = createShareChooserIntent(context, catalogText, chooserTitle)
        context.startActivity(chooserIntent)
    }
}
