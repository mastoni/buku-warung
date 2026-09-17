package id.skmnetwork.bukuwarung.purchase

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/**
 * Gate G13.3 — Native Android Intent Helper for sharing Purchase Orders via WhatsApp and generic share sheet.
 * Reuses established WhatsApp package detection and safe fallback mechanisms.
 */
object WhatsAppPurchaseOrderShareHelper {

    const val PACKAGE_WHATSAPP = "com.whatsapp"
    const val PACKAGE_WHATSAPP_BUSINESS = "com.whatsapp.w4b"

    /**
     * Checks whether WhatsApp or WhatsApp Business is installed on the host device.
     */
    fun isWhatsAppInstalled(context: Context): Boolean {
        val pm = context.packageManager
        return isPackageInstalled(pm, PACKAGE_WHATSAPP) || isPackageInstalled(pm, PACKAGE_WHATSAPP_BUSINESS)
    }

    internal fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
        return try {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Normalizes phone number to international Indonesian format (e.g. "08123456789" -> "628123456789").
     */
    fun normalizePhoneNumber(phone: String?): String? {
        if (phone.isNullOrBlank()) return null
        val digitsOnly = phone.trim().replace(Regex("[^0-9+]"), "")
        if (digitsOnly.isEmpty()) return null

        return when {
            digitsOnly.startsWith("+62") -> digitsOnly.substring(1)
            digitsOnly.startsWith("62") -> digitsOnly
            digitsOnly.startsWith("0") -> "62" + digitsOnly.substring(1)
            else -> digitsOnly
        }
    }

    /**
     * Creates an Intent to share PO text via ACTION_SEND.
     * If directToWhatsApp is true and WhatsApp is installed, targets the WhatsApp package.
     */
    fun createShareIntent(
        orderText: String,
        directToWhatsApp: Boolean = false,
        context: Context? = null
    ): Intent {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, orderText)
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
     * Creates a direct WhatsApp chat intent to a specific phone number with prefilled text.
     */
    fun createDirectWhatsAppIntent(
        phone: String,
        orderText: String
    ): Intent {
        val normalized = normalizePhoneNumber(phone) ?: ""
        val uri = Uri.parse("https://api.whatsapp.com/send?phone=$normalized&text=${Uri.encode(orderText)}")
        return Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Creates an Android Chooser Intent for PO text sharing.
     */
    fun createShareChooserIntent(
        context: Context,
        orderText: String,
        chooserTitle: String = "Kirim Purchase Order"
    ): Intent {
        val baseIntent = createShareIntent(orderText)
        return Intent.createChooser(baseIntent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Shares the Purchase Order text safely.
     * If a valid supplier phone is provided and WhatsApp is installed, opens WhatsApp directly.
     * Otherwise, falls back to the standard Android share chooser.
     */
    fun sharePurchaseOrder(
        context: Context,
        orderText: String,
        supplierPhone: String? = null,
        chooserTitle: String = "Kirim Purchase Order via WhatsApp"
    ) {
        val normalizedPhone = normalizePhoneNumber(supplierPhone)
        val hasWhatsApp = isWhatsAppInstalled(context)

        try {
            if (!normalizedPhone.isNullOrBlank() && hasWhatsApp) {
                val directIntent = createDirectWhatsAppIntent(normalizedPhone, orderText)
                context.startActivity(directIntent)
            } else {
                val chooserIntent = createShareChooserIntent(context, orderText, chooserTitle)
                context.startActivity(chooserIntent)
            }
        } catch (_: Exception) {
            // Safe fallback if direct activity launch fails
            try {
                val fallbackChooser = createShareChooserIntent(context, orderText, chooserTitle)
                context.startActivity(fallbackChooser)
            } catch (_: Exception) {
                // Ignore if in headless / background environment
            }
        }
    }
}
