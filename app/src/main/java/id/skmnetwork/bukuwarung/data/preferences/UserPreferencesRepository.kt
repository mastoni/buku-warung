package id.skmnetwork.bukuwarung.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

data class UserSettings(
    // 0. Identitas Multi-Device
    val businessId: String = "",
    val deviceId: String = "",
    val deviceName: String = "HP Utama",
    val deviceRole: String = "OWNER",

    // 1. Profil Warung
    val isSetupCompleted: Boolean = false,
    val shopName: String = "Warung Saya",
    val ownerName: String = "",
    val phone: String = "",
    val address: String = "",

    // 1.1 Business Profile (Adaptive Layer v0.2.0)
    val primaryBusinessType: String = "WARUNG_SEMBAKO",
    val secondaryActivities: Set<String> = setOf("ACTIVITY_GOODS_SELLING"),
    val profileVersion: Int = 1,

    // 16. BUSINESS TYPE LOCKING (PR-11.1)
    val businessTypeLocked: Boolean = false,

    // 2. POS Settings
    val showProductImage: Boolean = true,
    val showStock: Boolean = true,
    val showBarcode: Boolean = true,
    val confirmCheckout: Boolean = true,

    // 3. Pembayaran
    val cashEnabled: Boolean = true,
    val qrisEnabled: Boolean = true,
    val creditEnabled: Boolean = true,
    val cashReceivedEnabled: Boolean = true,
    val qrisConfirmationRequired: Boolean = true,

    // 4. Stok & Peringatan
    val lowStockAlertEnabled: Boolean = true,
    val defaultLowStockLimit: Int = 2,
    val allowNegativeStock: Boolean = false,

    // 5. Struk & Nota
    val showShopNameOnReceipt: Boolean = true,
    val showAddressOnReceipt: Boolean = true,
    val showPhoneOnReceipt: Boolean = true,
    val showPaymentMethodOnReceipt: Boolean = true,
    val showChangeOnReceipt: Boolean = true,
    val receiptFooterText: String = "Terima Kasih Atas Kunjungan Anda!",

    // 6. Notifikasi
    val lowStockNotificationEnabled: Boolean = true,
    val debtReminderEnabled: Boolean = false,

    // 7. Tampilan
    val themeMode: String = "SYSTEM", // "SYSTEM", "LIGHT"
    val productViewMode: String = "GRID", // "GRID", "LIST"

    // 8. Keamanan & PIN (NO PLAINTEXT PIN)
    val pinEnabled: Boolean = false,
    val hasPinSet: Boolean = false,

    // 9. Printer Struk (Thermal)
    val printerType: String = "NONE", // "NONE", "BLUETOOTH", "USB"
    val printerDeviceName: String = "",
    val printerAddress: String = "",
    val printerPaperWidth: String = "58MM", // "58MM", "80MM"
    val printerAutoConnect: Boolean = true,

    // 10. Cadangan Google Sheets
    val lastBackupTimestamp: Long = 0L,
    val backupSpreadsheetId: String = "",
    val backupSpreadsheetName: String = "",
    val googleAccountEmail: String = "",

    // 11. Static QRIS Warung
    val qrisImagePath: String = ""
)

class UserPreferencesRepository(
    private val context: Context? = null,
    private val dataStore: DataStore<Preferences> = context?.dataStore
        ?: throw IllegalStateException("Context or DataStore must be provided")
) {

    object Keys {
        val BUSINESS_ID = stringPreferencesKey("business_id")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val DEVICE_NAME = stringPreferencesKey("device_name")
        val DEVICE_ROLE = stringPreferencesKey("device_role")

        val IS_SETUP_COMPLETED = booleanPreferencesKey("is_setup_completed")
        val LEGACY_OWNER_PIN = stringPreferencesKey("owner_pin")
        val SHOP_NAME = stringPreferencesKey("shop_name")
        val OWNER_NAME = stringPreferencesKey("owner_name")
        val PHONE = stringPreferencesKey("phone")
        val ADDRESS = stringPreferencesKey("address")

        val SHOW_PRODUCT_IMAGE = booleanPreferencesKey("show_product_image")
        val SHOW_STOCK = booleanPreferencesKey("show_stock")
        val SHOW_BARCODE = booleanPreferencesKey("show_barcode")
        val CONFIRM_CHECKOUT = booleanPreferencesKey("confirm_checkout")

        val CASH_ENABLED = booleanPreferencesKey("cash_enabled")
        val QRIS_ENABLED = booleanPreferencesKey("qris_enabled")
        val CREDIT_ENABLED = booleanPreferencesKey("credit_enabled")
        val CASH_RECEIVED_ENABLED = booleanPreferencesKey("cash_received_enabled")
        val QRIS_CONFIRMATION_REQUIRED = booleanPreferencesKey("qris_confirmation_required")

        val LOW_STOCK_ALERT_ENABLED = booleanPreferencesKey("low_stock_alert_enabled")
        val DEFAULT_LOW_STOCK_LIMIT = intPreferencesKey("default_low_stock_limit")
        val ALLOW_NEGATIVE_STOCK = booleanPreferencesKey("allow_negative_stock")

        val SHOW_SHOP_NAME_ON_RECEIPT = booleanPreferencesKey("show_shop_name_on_receipt")
        val SHOW_ADDRESS_ON_RECEIPT = booleanPreferencesKey("show_address_on_receipt")
        val SHOW_PHONE_ON_RECEIPT = booleanPreferencesKey("show_phone_on_receipt")
        val SHOW_PAYMENT_METHOD_ON_RECEIPT = booleanPreferencesKey("show_payment_method_on_receipt")
        val SHOW_CHANGE_ON_RECEIPT = booleanPreferencesKey("show_change_on_receipt")
        val RECEIPT_FOOTER_TEXT = stringPreferencesKey("receipt_footer_text")

        val LOW_STOCK_NOTIFICATION_ENABLED = booleanPreferencesKey("low_stock_notification_enabled")
        val DEBT_REMINDER_ENABLED = booleanPreferencesKey("debt_reminder_enabled")

        val THEME_MODE = stringPreferencesKey("theme_mode")
        val PRODUCT_VIEW_MODE = stringPreferencesKey("product_view_mode")

        // SECURE PIN STORAGE: Salt + Hash (SHA-256)
        val PIN_ENABLED = booleanPreferencesKey("pin_enabled")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val PIN_HASH = stringPreferencesKey("pin_hash")

        // 9. PRINTER STRUK (THERMAL)
        val PRINTER_TYPE = stringPreferencesKey("printer_type")
        val PRINTER_DEVICE_NAME = stringPreferencesKey("printer_device_name")
        val PRINTER_ADDRESS = stringPreferencesKey("printer_address")
        val PRINTER_PAPER_WIDTH = stringPreferencesKey("printer_paper_width")
        val PRINTER_AUTO_CONNECT = booleanPreferencesKey("printer_auto_connect")

        // 10. OWNER / INTERNAL TESTING ENTITLEMENT
        val OWNER_TEST_ACTIVATED = booleanPreferencesKey("owner_test_activated")
        val OWNER_TEST_ACTIVATION_DATE = stringPreferencesKey("owner_test_activation_date")

        // 11. GOOGLE SHEETS BACKUP & RESTORE
        val LAST_BACKUP_TIMESTAMP = androidx.datastore.preferences.core.longPreferencesKey("last_backup_timestamp")
        val BACKUP_SPREADSHEET_ID = stringPreferencesKey("backup_spreadsheet_id")
        val BACKUP_SPREADSHEET_NAME = stringPreferencesKey("backup_spreadsheet_name")
        val GOOGLE_ACCOUNT_EMAIL = stringPreferencesKey("google_account_email")

        // 12. STATIC QRIS WARUNG
        val QRIS_IMAGE_PATH = stringPreferencesKey("qris_image_path")

        // 13. NOTIFICATION STATE PERSISTENCE
        val READ_NOTIFICATION_IDS = androidx.datastore.preferences.core.stringSetPreferencesKey("read_notification_ids")

        // 14. COMMERCIAL LICENSE ENTITLEMENT METADATA
        val LICENSE_STATUS = stringPreferencesKey("commercial_license_status")
        val LICENSE_OWNER_EMAIL = stringPreferencesKey("commercial_license_owner_email")
        val LICENSE_ACTIVATED_AT = androidx.datastore.preferences.core.longPreferencesKey("commercial_license_activated_at")
        val LICENSE_LAST_VALIDATED_AT = androidx.datastore.preferences.core.longPreferencesKey("commercial_license_last_validated_at")

        // 15. ADAPTIVE BUSINESS PROFILE (v0.2.0)
        val PRIMARY_BUSINESS_TYPE = stringPreferencesKey("primary_business_type")
        val SECONDARY_ACTIVITIES = androidx.datastore.preferences.core.stringSetPreferencesKey("secondary_activities")
        val PROFILE_VERSION = intPreferencesKey("profile_version")

        // 16. BUSINESS TYPE LOCKING (PR-11.1)
        val BUSINESS_TYPE_LOCKED = booleanPreferencesKey("business_type_locked")
    }

    val userSettings: Flow<UserSettings> = dataStore.data.map { prefs ->
        val salt = prefs[Keys.PIN_SALT]
        val hash = prefs[Keys.PIN_HASH]
        val legacyPin = prefs[Keys.LEGACY_OWNER_PIN]
        val hasPin = (!salt.isNullOrEmpty() && !hash.isNullOrEmpty()) || !legacyPin.isNullOrEmpty()

        val isExplicitlyCompleted = prefs[Keys.IS_SETUP_COMPLETED] ?: false
        val hasCustomProfile = (!prefs[Keys.SHOP_NAME].isNullOrBlank() && prefs[Keys.SHOP_NAME] != "Warung Saya") ||
                !prefs[Keys.OWNER_NAME].isNullOrBlank() ||
                !prefs[Keys.PHONE].isNullOrBlank() ||
                !prefs[Keys.ADDRESS].isNullOrBlank()

        UserSettings(
            businessId = prefs[Keys.BUSINESS_ID] ?: "",
            deviceId = prefs[Keys.DEVICE_ID] ?: "",
            deviceName = prefs[Keys.DEVICE_NAME] ?: "HP Utama",
            deviceRole = prefs[Keys.DEVICE_ROLE] ?: "OWNER",

            isSetupCompleted = isExplicitlyCompleted || hasCustomProfile,
            shopName = prefs[Keys.SHOP_NAME] ?: "Warung Saya",
            ownerName = prefs[Keys.OWNER_NAME] ?: "",
            phone = prefs[Keys.PHONE] ?: "",
            address = prefs[Keys.ADDRESS] ?: "",

            primaryBusinessType = prefs[Keys.PRIMARY_BUSINESS_TYPE] ?: "WARUNG_SEMBAKO",
            secondaryActivities = prefs[Keys.SECONDARY_ACTIVITIES] ?: setOf("ACTIVITY_GOODS_SELLING"),
            profileVersion = prefs[Keys.PROFILE_VERSION] ?: 1,
            businessTypeLocked = prefs[Keys.BUSINESS_TYPE_LOCKED] ?: false,

            showProductImage = prefs[Keys.SHOW_PRODUCT_IMAGE] ?: true,
            showStock = prefs[Keys.SHOW_STOCK] ?: true,
            showBarcode = prefs[Keys.SHOW_BARCODE] ?: true,
            confirmCheckout = prefs[Keys.CONFIRM_CHECKOUT] ?: true,

            cashEnabled = prefs[Keys.CASH_ENABLED] ?: true,
            qrisEnabled = prefs[Keys.QRIS_ENABLED] ?: true,
            creditEnabled = prefs[Keys.CREDIT_ENABLED] ?: true,
            cashReceivedEnabled = prefs[Keys.CASH_RECEIVED_ENABLED] ?: true,
            qrisConfirmationRequired = prefs[Keys.QRIS_CONFIRMATION_REQUIRED] ?: true,

            lowStockAlertEnabled = prefs[Keys.LOW_STOCK_ALERT_ENABLED] ?: true,
            defaultLowStockLimit = prefs[Keys.DEFAULT_LOW_STOCK_LIMIT] ?: 2,
            allowNegativeStock = prefs[Keys.ALLOW_NEGATIVE_STOCK] ?: false,

            showShopNameOnReceipt = prefs[Keys.SHOW_SHOP_NAME_ON_RECEIPT] ?: true,
            showAddressOnReceipt = prefs[Keys.SHOW_ADDRESS_ON_RECEIPT] ?: true,
            showPhoneOnReceipt = prefs[Keys.SHOW_PHONE_ON_RECEIPT] ?: true,
            showPaymentMethodOnReceipt = prefs[Keys.SHOW_PAYMENT_METHOD_ON_RECEIPT] ?: true,
            showChangeOnReceipt = prefs[Keys.SHOW_CHANGE_ON_RECEIPT] ?: true,
            receiptFooterText = prefs[Keys.RECEIPT_FOOTER_TEXT] ?: "Terima Kasih Atas Kunjungan Anda!",

            lowStockNotificationEnabled = prefs[Keys.LOW_STOCK_NOTIFICATION_ENABLED] ?: true,
            debtReminderEnabled = prefs[Keys.DEBT_REMINDER_ENABLED] ?: false,

            themeMode = prefs[Keys.THEME_MODE] ?: "SYSTEM",
            productViewMode = prefs[Keys.PRODUCT_VIEW_MODE] ?: "GRID",

            pinEnabled = prefs[Keys.PIN_ENABLED] ?: (!legacyPin.isNullOrEmpty()),
            hasPinSet = hasPin,

            printerType = prefs[Keys.PRINTER_TYPE] ?: "NONE",
            printerDeviceName = prefs[Keys.PRINTER_DEVICE_NAME] ?: "",
            printerAddress = prefs[Keys.PRINTER_ADDRESS] ?: "",
            printerPaperWidth = prefs[Keys.PRINTER_PAPER_WIDTH] ?: "58MM",
            printerAutoConnect = prefs[Keys.PRINTER_AUTO_CONNECT] ?: true,

            lastBackupTimestamp = prefs[Keys.LAST_BACKUP_TIMESTAMP] ?: 0L,
            backupSpreadsheetId = prefs[Keys.BACKUP_SPREADSHEET_ID] ?: "",
            backupSpreadsheetName = prefs[Keys.BACKUP_SPREADSHEET_NAME] ?: "",
            googleAccountEmail = prefs[Keys.GOOGLE_ACCOUNT_EMAIL] ?: "",
            qrisImagePath = prefs[Keys.QRIS_IMAGE_PATH] ?: ""
        )
    }

    suspend fun updatePrinterConfig(
        printerType: String,
        printerDeviceName: String,
        printerAddress: String,
        printerPaperWidth: String = "58MM",
        printerAutoConnect: Boolean = true
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.PRINTER_TYPE] = printerType
            prefs[Keys.PRINTER_DEVICE_NAME] = printerDeviceName
            prefs[Keys.PRINTER_ADDRESS] = printerAddress
            prefs[Keys.PRINTER_PAPER_WIDTH] = printerPaperWidth
            prefs[Keys.PRINTER_AUTO_CONNECT] = printerAutoConnect
        }
    }

    suspend fun clearPrinterConfig() {
        dataStore.edit { prefs ->
            prefs[Keys.PRINTER_TYPE] = "NONE"
            prefs[Keys.PRINTER_DEVICE_NAME] = ""
            prefs[Keys.PRINTER_ADDRESS] = ""
            prefs[Keys.PRINTER_PAPER_WIDTH] = "58MM"
            prefs[Keys.PRINTER_AUTO_CONNECT] = false
        }
    }

    suspend fun getOrCreateBusinessId(): String {
        val prefs = dataStore.data.first()
        val existing = prefs[Keys.BUSINESS_ID]
        if (!existing.isNullOrBlank()) return existing

        val newBusinessId = java.util.UUID.randomUUID().toString()
        dataStore.edit { editPrefs ->
            editPrefs[Keys.BUSINESS_ID] = newBusinessId
        }
        return newBusinessId
    }

    suspend fun setBusinessId(businessId: String) {
        dataStore.edit { prefs ->
            prefs[Keys.BUSINESS_ID] = businessId
        }
    }

    suspend fun getOrCreateDeviceId(): String {
        val prefs = dataStore.data.first()
        val existing = prefs[Keys.DEVICE_ID]
        if (!existing.isNullOrBlank()) return existing

        val newDeviceId = java.util.UUID.randomUUID().toString()
        dataStore.edit { editPrefs ->
            editPrefs[Keys.DEVICE_ID] = newDeviceId
            if (editPrefs[Keys.DEVICE_NAME].isNullOrBlank()) {
                editPrefs[Keys.DEVICE_NAME] = "HP Utama"
            }
            if (editPrefs[Keys.DEVICE_ROLE].isNullOrBlank()) {
                editPrefs[Keys.DEVICE_ROLE] = "OWNER"
            }
        }
        return newDeviceId
    }

    suspend fun reconcileLegacyBusinessIdentity(
        database: id.skmnetwork.bukuwarung.data.local.database.AppDatabase,
        businessId: String
    ) = withContext(Dispatchers.IO) {
        if (businessId.isBlank()) return@withContext
        try {
            val sdb = database.openHelper.writableDatabase
            val tables = listOf(
                "products",
                "categories",
                "sales_transactions",
                "sale_items",
                "purchase_transactions",
                "purchase_items",
                "cash_transactions",
                "customers",
                "debts",
                "debt_payments",
                "suppliers",
                "supplier_payables",
                "supplier_payments",
                "stock_movements",
                "purchase_orders",
                "purchase_order_items"
            )
            for (table in tables) {
                sdb.execSQL("UPDATE `$table` SET `business_id` = ? WHERE `business_id` = 'LEGACY_BUSINESS'", arrayOf(businessId))
            }
        } catch (e: Exception) {
            // Ignore if columns do not exist yet (e.g. during fresh setup before migration)
        }
    }

    suspend fun autoMigrateExistingUserIfNeeded(database: id.skmnetwork.bukuwarung.data.local.database.AppDatabase? = null) {
        val initialPrefs = dataStore.data.first()
        if (database != null && initialPrefs[Keys.BUSINESS_ID].isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                try {
                    val sdb = database.openHelper.readableDatabase
                    val tables = listOf("categories", "products", "sales_transactions", "purchase_transactions", "customers", "suppliers", "cash_transactions")
                    for (table in tables) {
                        val cursor = sdb.query("SELECT business_id FROM `$table` WHERE business_id != '' AND business_id != 'LEGACY_BUSINESS' LIMIT 1")
                        val foundId = cursor.use {
                            if (it.moveToFirst()) it.getString(0) else null
                        }
                        if (!foundId.isNullOrBlank()) {
                            dataStore.edit { editPrefs ->
                                editPrefs[Keys.BUSINESS_ID] = foundId
                            }
                            break
                        }
                    }
                } catch (e: Exception) {
                    // Ignore during fresh database setup
                }
            }
        }

        val businessId = getOrCreateBusinessId()
        getOrCreateDeviceId()

        if (database != null) {
            reconcileLegacyBusinessIdentity(database, businessId)
        }

        val prefs = dataStore.data.first()

        // 1. Migrate Legacy Plaintext PIN (if present)
        val legacyPin = prefs[Keys.LEGACY_OWNER_PIN]
        val currentHash = prefs[Keys.PIN_HASH]
        if (!legacyPin.isNullOrEmpty() && currentHash.isNullOrEmpty()) {
            val cleanPin = legacyPin.trim()
            if (cleanPin.isNotEmpty()) {
                val salt = generateSalt()
                val hash = hashPin(salt, cleanPin)
                dataStore.edit { editPrefs ->
                    editPrefs[Keys.PIN_SALT] = salt
                    editPrefs[Keys.PIN_HASH] = hash
                    editPrefs[Keys.PIN_ENABLED] = editPrefs[Keys.PIN_ENABLED] ?: true
                    editPrefs.remove(Keys.LEGACY_OWNER_PIN)
                }
            }
        } else if (!legacyPin.isNullOrEmpty() && !currentHash.isNullOrEmpty()) {
            dataStore.edit { editPrefs ->
                editPrefs.remove(Keys.LEGACY_OWNER_PIN)
            }
        }

        // 2. Auto-detect Existing User Setup Status
        val isExplicitlyCompleted = prefs[Keys.IS_SETUP_COMPLETED] ?: false
        if (!isExplicitlyCompleted) {
            val hasCustomProfile = (!prefs[Keys.SHOP_NAME].isNullOrBlank() && prefs[Keys.SHOP_NAME] != "Warung Saya") ||
                    !prefs[Keys.OWNER_NAME].isNullOrBlank() ||
                    !prefs[Keys.PHONE].isNullOrBlank() ||
                    !prefs[Keys.ADDRESS].isNullOrBlank()

            if (hasCustomProfile) {
                dataStore.edit { editPrefs ->
                    editPrefs[Keys.IS_SETUP_COMPLETED] = true
                }
            } else if (database != null) {
                val hasBusinessData = withContext(Dispatchers.IO) {
                    try {
                        val sdb = database.openHelper.readableDatabase
                        val tables = listOf(
                            "products",
                            "categories",
                            "sales_transactions",
                            "purchase_transactions",
                            "customers",
                            "suppliers",
                            "cash_transactions"
                        )
                        var exists = false
                        for (table in tables) {
                            val cursor = sdb.query("SELECT 1 FROM `$table` LIMIT 1")
                            cursor.use {
                                if (it.moveToFirst()) {
                                    exists = true
                                }
                            }
                            if (exists) break
                        }
                        exists
                    } catch (e: Exception) {
                        false
                    }
                }

                if (hasBusinessData) {
                    dataStore.edit { editPrefs ->
                        editPrefs[Keys.IS_SETUP_COMPLETED] = true
                    }
                }
            }
        }

        // 3. Initialize Default Business Profile if missing (Idempotent - preserves existing profile)
        if (prefs[Keys.PRIMARY_BUSINESS_TYPE] == null || prefs[Keys.SECONDARY_ACTIVITIES] == null || prefs[Keys.PROFILE_VERSION] == null) {
            dataStore.edit { editPrefs ->
                if (editPrefs[Keys.PRIMARY_BUSINESS_TYPE] == null) {
                    editPrefs[Keys.PRIMARY_BUSINESS_TYPE] = "WARUNG_SEMBAKO"
                }
                if (editPrefs[Keys.SECONDARY_ACTIVITIES] == null) {
                    editPrefs[Keys.SECONDARY_ACTIVITIES] = setOf("ACTIVITY_GOODS_SELLING")
                }
                if (editPrefs[Keys.PROFILE_VERSION] == null) {
                    editPrefs[Keys.PROFILE_VERSION] = 1
                }
            }
        }
    }

    suspend fun updateBusinessProfile(
        primaryType: String,
        secondaryActivities: Set<String>,
        version: Int = 1
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.PRIMARY_BUSINESS_TYPE] = primaryType.trim()
            prefs[Keys.SECONDARY_ACTIVITIES] = secondaryActivities
            prefs[Keys.PROFILE_VERSION] = version
        }
    }

    suspend fun saveShopProfile(shopName: String, ownerName: String, phone: String, address: String) {
        val businessId = getOrCreateBusinessId()
        getOrCreateDeviceId()
        dataStore.edit { prefs ->
            prefs[Keys.IS_SETUP_COMPLETED] = true
            prefs[Keys.BUSINESS_ID] = businessId
            prefs[Keys.SHOP_NAME] = shopName.trim().ifEmpty { "Warung Saya" }
            prefs[Keys.OWNER_NAME] = ownerName.trim()
            prefs[Keys.PHONE] = phone.trim()
            prefs[Keys.ADDRESS] = address.trim()
            prefs[Keys.BUSINESS_TYPE_LOCKED] = true
        }
    }

    suspend fun saveInitialSetupProfile(
        shopName: String,
        ownerName: String,
        phone: String,
        address: String,
        primaryBusinessType: String,
        secondaryActivities: Set<String>,
        profileVersion: Int = 1
    ) {
        val businessId = getOrCreateBusinessId()
        getOrCreateDeviceId()
        dataStore.edit { prefs ->
            prefs[Keys.IS_SETUP_COMPLETED] = true
            prefs[Keys.BUSINESS_ID] = businessId
            prefs[Keys.SHOP_NAME] = shopName.trim().ifEmpty { "Warung Saya" }
            prefs[Keys.OWNER_NAME] = ownerName.trim()
            prefs[Keys.PHONE] = phone.trim()
            prefs[Keys.ADDRESS] = address.trim()
            prefs[Keys.PRIMARY_BUSINESS_TYPE] = primaryBusinessType.trim()
            prefs[Keys.SECONDARY_ACTIVITIES] = secondaryActivities
            prefs[Keys.PROFILE_VERSION] = profileVersion
            prefs[Keys.BUSINESS_TYPE_LOCKED] = true
        }
    }

    suspend fun updatePaymentSettings(
        cashEnabled: Boolean,
        qrisEnabled: Boolean,
        creditEnabled: Boolean,
        cashReceivedEnabled: Boolean,
        qrisConfirmationRequired: Boolean
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.CASH_ENABLED] = cashEnabled
            prefs[Keys.QRIS_ENABLED] = qrisEnabled
            prefs[Keys.CREDIT_ENABLED] = creditEnabled
            prefs[Keys.CASH_RECEIVED_ENABLED] = cashReceivedEnabled
            prefs[Keys.QRIS_CONFIRMATION_REQUIRED] = qrisConfirmationRequired
        }
    }

    suspend fun updatePosSettings(
        showProductImage: Boolean,
        showStock: Boolean,
        showBarcode: Boolean,
        confirmCheckout: Boolean
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.SHOW_PRODUCT_IMAGE] = showProductImage
            prefs[Keys.SHOW_STOCK] = showStock
            prefs[Keys.SHOW_BARCODE] = showBarcode
            prefs[Keys.CONFIRM_CHECKOUT] = confirmCheckout
        }
    }

    suspend fun updateStockSettings(
        lowStockAlertEnabled: Boolean,
        defaultLowStockLimit: Int,
        allowNegativeStock: Boolean
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.LOW_STOCK_ALERT_ENABLED] = lowStockAlertEnabled
            prefs[Keys.DEFAULT_LOW_STOCK_LIMIT] = defaultLowStockLimit
            prefs[Keys.ALLOW_NEGATIVE_STOCK] = allowNegativeStock
        }
    }

    suspend fun updateReceiptSettings(
        showShopName: Boolean,
        showAddress: Boolean,
        showPhone: Boolean,
        showPaymentMethod: Boolean,
        showChange: Boolean,
        footerText: String
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.SHOW_SHOP_NAME_ON_RECEIPT] = showShopName
            prefs[Keys.SHOW_ADDRESS_ON_RECEIPT] = showAddress
            prefs[Keys.SHOW_PHONE_ON_RECEIPT] = showPhone
            prefs[Keys.SHOW_PAYMENT_METHOD_ON_RECEIPT] = showPaymentMethod
            prefs[Keys.SHOW_CHANGE_ON_RECEIPT] = showChange
            prefs[Keys.RECEIPT_FOOTER_TEXT] = footerText.trim()
        }
    }

    suspend fun updateNotificationSettings(
        lowStockNotificationEnabled: Boolean,
        debtReminderEnabled: Boolean
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.LOW_STOCK_NOTIFICATION_ENABLED] = lowStockNotificationEnabled
            prefs[Keys.DEBT_REMINDER_ENABLED] = debtReminderEnabled
        }
    }

    suspend fun updateAppearanceSettings(
        themeMode: String,
        productViewMode: String
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = themeMode
            prefs[Keys.PRODUCT_VIEW_MODE] = productViewMode
        }
    }

    // ==========================================
    // SECURE PIN METHODS (SHA-256 + Unique Salt)
    // ==========================================

    private fun generateSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hashPin(salt: String, pin: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest("$salt:$pin".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    suspend fun setOwnerPin(newPin: String) {
        val cleanPin = newPin.trim()
        val salt = generateSalt()
        val hash = hashPin(salt, cleanPin)
        dataStore.edit { prefs ->
            prefs[Keys.PIN_ENABLED] = true
            prefs[Keys.PIN_SALT] = salt
            prefs[Keys.PIN_HASH] = hash
            prefs.remove(Keys.LEGACY_OWNER_PIN)
        }
    }

    suspend fun setPinEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.PIN_ENABLED] = enabled
        }
    }

    suspend fun verifyPin(inputPin: String): Boolean {
        val prefs = dataStore.data.first()
        val salt = prefs[Keys.PIN_SALT]
        val storedHash = prefs[Keys.PIN_HASH]
        if (!salt.isNullOrEmpty() && !storedHash.isNullOrEmpty()) {
            val computedHash = hashPin(salt, inputPin.trim())
            return MessageDigest.isEqual(
                computedHash.toByteArray(Charsets.UTF_8),
                storedHash.toByteArray(Charsets.UTF_8)
            )
        }
        val legacyPin = prefs[Keys.LEGACY_OWNER_PIN]
        if (!legacyPin.isNullOrEmpty()) {
            return legacyPin.trim() == inputPin.trim()
        }
        return false
    }

    suspend fun clearPin() {
        dataStore.edit { prefs ->
            prefs[Keys.PIN_ENABLED] = false
            prefs.remove(Keys.PIN_SALT)
            prefs.remove(Keys.PIN_HASH)
            prefs.remove(Keys.LEGACY_OWNER_PIN)
        }
    }

    suspend fun isOwnerTestActivated(): Boolean {
        val prefs = dataStore.data.first()
        return prefs[Keys.OWNER_TEST_ACTIVATED] ?: false
    }

    suspend fun isBusinessTypeLocked(): Boolean {
        val prefs = dataStore.data.first()
        return prefs[Keys.BUSINESS_TYPE_LOCKED] ?: false
    }

    suspend fun setOwnerTestActivated(activated: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.OWNER_TEST_ACTIVATED] = activated
            if (activated) {
                prefs[Keys.OWNER_TEST_ACTIVATION_DATE] = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())
            } else {
                prefs.remove(Keys.OWNER_TEST_ACTIVATION_DATE)
            }
        }
    }

    suspend fun updateBackupInfo(
        lastBackupTimestamp: Long,
        backupSpreadsheetId: String = "",
        backupSpreadsheetName: String = "",
        googleAccountEmail: String = ""
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.LAST_BACKUP_TIMESTAMP] = lastBackupTimestamp
            if (backupSpreadsheetId.isNotBlank()) {
                prefs[Keys.BACKUP_SPREADSHEET_ID] = backupSpreadsheetId
            }
            if (backupSpreadsheetName.isNotBlank()) {
                prefs[Keys.BACKUP_SPREADSHEET_NAME] = backupSpreadsheetName
            }
            if (googleAccountEmail.isNotBlank()) {
                prefs[Keys.GOOGLE_ACCOUNT_EMAIL] = googleAccountEmail
            }
        }
    }

    suspend fun setGoogleAccount(email: String) {
        dataStore.edit { prefs ->
            prefs[Keys.GOOGLE_ACCOUNT_EMAIL] = email.trim()
        }
    }

    suspend fun clearGoogleAccount() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.GOOGLE_ACCOUNT_EMAIL)
            prefs.remove(Keys.BACKUP_SPREADSHEET_ID)
            prefs.remove(Keys.BACKUP_SPREADSHEET_NAME)
        }
    }

    // ==========================================
    // 12. STATIC QRIS WARUNG PERSISTENCE
    // ==========================================

    suspend fun getQrisImagePath(): String {
        val prefs = dataStore.data.first()
        return prefs[Keys.QRIS_IMAGE_PATH] ?: ""
    }

    suspend fun saveQrisImagePath(path: String) {
        dataStore.edit { prefs ->
            prefs[Keys.QRIS_IMAGE_PATH] = path
        }
    }

    suspend fun clearQrisImagePath() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.QRIS_IMAGE_PATH)
        }
    }

    suspend fun saveQrisImageFromUri(uri: android.net.Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val ctx = context ?: return@withContext Result.failure(Exception("Context tidak tersedia"))
            val qrisDir = java.io.File(ctx.filesDir, "qris").apply { if (!exists()) mkdirs() }
            val tempFile = java.io.File(qrisDir, "merchant_qris_${System.currentTimeMillis()}.jpg")
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                java.io.FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(Exception("Tidak dapat membaca data gambar"))

            if (!tempFile.exists() || tempFile.length() <= 0L) {
                tempFile.delete()
                return@withContext Result.failure(Exception("File gambar kosong"))
            }

            val bitmap = android.graphics.BitmapFactory.decodeFile(tempFile.absolutePath)
            if (bitmap == null) {
                tempFile.delete()
                return@withContext Result.failure(Exception("QRIS tidak dapat digunakan. Pastikan foto QRIS terlihat jelas dan tidak terpotong."))
            }

            // Cleanup previous files in qris dir to prevent orphan files
            qrisDir.listFiles()?.forEach { file ->
                if (file.absolutePath != tempFile.absolutePath) {
                    file.delete()
                }
            }

            val savedPath = tempFile.absolutePath
            saveQrisImagePath(savedPath)
            Result.success(savedPath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveQrisImageBitmap(bitmap: android.graphics.Bitmap): Result<String> = withContext(Dispatchers.IO) {
        try {
            val ctx = context ?: return@withContext Result.failure(Exception("Context tidak tersedia"))
            val qrisDir = java.io.File(ctx.filesDir, "qris").apply { if (!exists()) mkdirs() }
            val tempFile = java.io.File(qrisDir, "merchant_qris_${System.currentTimeMillis()}.jpg")
            java.io.FileOutputStream(tempFile).use { output ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, output)
            }

            if (!tempFile.exists() || tempFile.length() <= 0L) {
                tempFile.delete()
                return@withContext Result.failure(Exception("Gagal menyimpan gambar QRIS"))
            }

            val decoded = android.graphics.BitmapFactory.decodeFile(tempFile.absolutePath)
            if (decoded == null) {
                tempFile.delete()
                return@withContext Result.failure(Exception("QRIS tidak dapat digunakan. Pastikan foto QRIS terlihat jelas dan tidak terpotong."))
            }

            // Cleanup previous files in qris dir to prevent orphan files
            qrisDir.listFiles()?.forEach { file ->
                if (file.absolutePath != tempFile.absolutePath) {
                    file.delete()
                }
            }

            val savedPath = tempFile.absolutePath
            saveQrisImagePath(savedPath)
            Result.success(savedPath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteQrisImage(): Boolean = withContext(Dispatchers.IO) {
        try {
            val ctx = context ?: return@withContext false
            val qrisDir = java.io.File(ctx.filesDir, "qris")
            if (qrisDir.exists()) {
                qrisDir.listFiles()?.forEach { it.delete() }
            }
            clearQrisImagePath()
            true
        } catch (e: Exception) {
            false
        }
    }

    // ==========================================
    // 13. NOTIFICATION PERSISTENCE METHODS
    // ==========================================

    val readNotificationIds: Flow<Set<String>> = dataStore.data.map { prefs ->
        prefs[Keys.READ_NOTIFICATION_IDS] ?: emptySet()
    }

    suspend fun markNotificationAsRead(notificationId: String) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.READ_NOTIFICATION_IDS] ?: emptySet()
            prefs[Keys.READ_NOTIFICATION_IDS] = current + notificationId
        }
    }

    suspend fun markAllNotificationsAsRead(notificationIds: Collection<String>) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.READ_NOTIFICATION_IDS] ?: emptySet()
            prefs[Keys.READ_NOTIFICATION_IDS] = current + notificationIds
        }
    }

    suspend fun cleanupReadNotifications(activeIds: Set<String>) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.READ_NOTIFICATION_IDS] ?: emptySet()
            prefs[Keys.READ_NOTIFICATION_IDS] = current.filter { it in activeIds }.toSet()
        }
    }

    // ==========================================
    // 14. COMMERCIAL LICENSE PERSISTENCE METHODS
    // ==========================================

    suspend fun saveLicenseEntitlement(
        status: String,
        ownerEmail: String,
        activatedAt: Long,
        lastValidatedAt: Long
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.LICENSE_STATUS] = status
            prefs[Keys.LICENSE_OWNER_EMAIL] = ownerEmail
            prefs[Keys.LICENSE_ACTIVATED_AT] = activatedAt
            prefs[Keys.LICENSE_LAST_VALIDATED_AT] = lastValidatedAt
        }
    }

    suspend fun updateLicenseStatus(status: String, lastValidatedAt: Long = System.currentTimeMillis()) {
        dataStore.edit { prefs ->
            prefs[Keys.LICENSE_STATUS] = status
            prefs[Keys.LICENSE_LAST_VALIDATED_AT] = lastValidatedAt
        }
    }

    suspend fun clearLicenseEntitlement() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.LICENSE_STATUS)
            prefs.remove(Keys.LICENSE_OWNER_EMAIL)
            prefs.remove(Keys.LICENSE_ACTIVATED_AT)
            prefs.remove(Keys.LICENSE_LAST_VALIDATED_AT)
        }
    }

    suspend fun getLicenseEntitlement(): id.skmnetwork.bukuwarung.license.LicenseEntitlementData {
        val prefs = dataStore.data.first()
        return id.skmnetwork.bukuwarung.license.LicenseEntitlementData(
            status = prefs[Keys.LICENSE_STATUS] ?: "UNLICENSED",
            ownerEmail = prefs[Keys.LICENSE_OWNER_EMAIL] ?: "",
            activatedAt = prefs[Keys.LICENSE_ACTIVATED_AT] ?: 0L,
            lastValidatedAt = prefs[Keys.LICENSE_LAST_VALIDATED_AT] ?: 0L
        )
    }
}

