package id.skmnetwork.bukuwarung.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.skmnetwork.bukuwarung.data.preferences.UserPreferencesRepository
import id.skmnetwork.bukuwarung.data.preferences.UserSettings
import id.skmnetwork.bukuwarung.license.LicenseManager
import id.skmnetwork.bukuwarung.license.LicenseStatus
import id.skmnetwork.bukuwarung.ui.components.AppCard
import id.skmnetwork.bukuwarung.ui.components.AppTextField
import id.skmnetwork.bukuwarung.ui.components.PrimaryButton
import id.skmnetwork.bukuwarung.ui.components.SecondaryButton
import id.skmnetwork.bukuwarung.ui.theme.AppColors
import id.skmnetwork.bukuwarung.ui.theme.AppShapes
import id.skmnetwork.bukuwarung.ui.theme.AppSpacing
import kotlinx.coroutines.launch

import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.CircularProgressIndicator
import id.skmnetwork.bukuwarung.backup.BackupRestoreManager
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    userPreferencesRepository: UserPreferencesRepository? = null,
    licenseManager: LicenseManager? = null,
    printerService: id.skmnetwork.bukuwarung.printer.PrinterService? = null,
    backupViewModel: BackupViewModel? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefsRepo = remember { userPreferencesRepository ?: UserPreferencesRepository(context) }
    val licManager = remember { licenseManager ?: LicenseManager(prefsRepo) }
    val fallbackPrinterService = remember { id.skmnetwork.bukuwarung.printer.PrinterService() }
    val activePrinterService = printerService ?: fallbackPrinterService

    val bViewModel: BackupViewModel = backupViewModel ?: run {
        val db = remember { AppDatabase.getDatabase(context) }
        val mgr = remember { BackupRestoreManager(db, prefsRepo) }
        remember { BackupViewModel(mgr, prefsRepo) }
    }

    val backupState by bViewModel.backupState.collectAsStateWithLifecycle()
    val restoreState by bViewModel.restoreState.collectAsStateWithLifecycle()

    val settingsState by prefsRepo.userSettings.collectAsStateWithLifecycle(initialValue = UserSettings())
    val licenseStatus by licManager.licenseStatus.collectAsStateWithLifecycle()
    val licenseTier by licManager.licenseTier.collectAsStateWithLifecycle()

    // 1. Profil Warung
    var shopNameInput by remember { mutableStateOf("") }
    var ownerNameInput by remember { mutableStateOf("") }
    var phoneInput by remember { mutableStateOf("") }
    var addressInput by remember { mutableStateOf("") }

    // 2. POS Settings
    var showProductImage by remember { mutableStateOf(true) }
    var showStock by remember { mutableStateOf(true) }
    var showBarcode by remember { mutableStateOf(true) }
    var confirmCheckout by remember { mutableStateOf(true) }

    // 3. Pembayaran
    var cashEnabled by remember { mutableStateOf(true) }
    var qrisEnabled by remember { mutableStateOf(true) }
    var creditEnabled by remember { mutableStateOf(true) }
    var cashReceivedEnabled by remember { mutableStateOf(true) }
    var qrisConfirmationRequired by remember { mutableStateOf(true) }

    // 4. Stok
    var lowStockAlertEnabled by remember { mutableStateOf(true) }
    var lowStockLimitInput by remember { mutableStateOf("2") }

    // 5. Struk & Printer
    var showShopNameOnReceipt by remember { mutableStateOf(true) }
    var showAddressOnReceipt by remember { mutableStateOf(true) }
    var showPhoneOnReceipt by remember { mutableStateOf(true) }
    var showPaymentMethodOnReceipt by remember { mutableStateOf(true) }
    var showChangeOnReceipt by remember { mutableStateOf(true) }
    var receiptFooterTextInput by remember { mutableStateOf("") }
    var showPrinterSettingsDialog by remember { mutableStateOf(false) }

    // 6. Notifikasi
    var lowStockNotificationEnabled by remember { mutableStateOf(true) }
    var debtReminderEnabled by remember { mutableStateOf(false) }

    // 7. Tampilan
    var themeMode by remember { mutableStateOf("SYSTEM") }

    // 8. Keamanan & PIN
    var showSetPinDialog by remember { mutableStateOf(false) }
    var newPinInput by remember { mutableStateOf("") }
    var confirmPinInput by remember { mutableStateOf("") }
    var pinDialogError by remember { mutableStateOf<String?>(null) }

    // 10. Cadangan & Pemulihan
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(settingsState) {
        shopNameInput = settingsState.shopName
        ownerNameInput = settingsState.ownerName
        phoneInput = settingsState.phone
        addressInput = settingsState.address

        showProductImage = settingsState.showProductImage
        showStock = settingsState.showStock
        showBarcode = settingsState.showBarcode
        confirmCheckout = settingsState.confirmCheckout

        cashEnabled = settingsState.cashEnabled
        qrisEnabled = settingsState.qrisEnabled
        creditEnabled = settingsState.creditEnabled
        cashReceivedEnabled = settingsState.cashReceivedEnabled
        qrisConfirmationRequired = settingsState.qrisConfirmationRequired

        lowStockAlertEnabled = settingsState.lowStockAlertEnabled
        lowStockLimitInput = settingsState.defaultLowStockLimit.toString()

        showShopNameOnReceipt = settingsState.showShopNameOnReceipt
        showAddressOnReceipt = settingsState.showAddressOnReceipt
        showPhoneOnReceipt = settingsState.showPhoneOnReceipt
        showPaymentMethodOnReceipt = settingsState.showPaymentMethodOnReceipt
        showChangeOnReceipt = settingsState.showChangeOnReceipt
        receiptFooterTextInput = settingsState.receiptFooterText

        lowStockNotificationEnabled = settingsState.lowStockNotificationEnabled
        debtReminderEnabled = settingsState.debtReminderEnabled

        themeMode = settingsState.themeMode
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Pengaturan", fontWeight = FontWeight.Bold)
                }
            )
        },
        containerColor = Color.White
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)
        ) {
            // ==========================================
            // 1. PROFIL WARUNG
            // ==========================================
            SectionHeader(icon = Icons.Default.Storefront, title = "1. Profil Warung")
            AppCard {
                Column(
                    modifier = Modifier.padding(AppSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                ) {
                    AppTextField(
                        value = shopNameInput,
                        onValueChange = { shopNameInput = it },
                        label = "Nama Warung *"
                    )
                    AppTextField(
                        value = ownerNameInput,
                        onValueChange = { ownerNameInput = it },
                        label = "Nama Pemilik"
                    )
                    AppTextField(
                        value = phoneInput,
                        onValueChange = { phoneInput = it },
                        label = "Nomor WhatsApp",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )
                    AppTextField(
                        value = addressInput,
                        onValueChange = { addressInput = it },
                        label = "Alamat Warung"
                    )
                    Spacer(Modifier.height(AppSpacing.xs))
                    PrimaryButton(
                        text = "Simpan Profil Warung",
                        onClick = {
                            scope.launch {
                                prefsRepo.saveShopProfile(
                                    shopName = shopNameInput,
                                    ownerName = ownerNameInput,
                                    phone = phoneInput,
                                    address = addressInput
                                )
                                Toast.makeText(context, "Profil warung berhasil disimpan", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }

            // ==========================================
            // 2. PENJUALAN (POS)
            // ==========================================
            SectionHeader(icon = Icons.Default.PointOfSale, title = "2. Penjualan & Kasir")
            AppCard {
                Column(
                    modifier = Modifier.padding(AppSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                ) {
                    SettingSwitchRow("Tampilkan Foto Produk di Kasir", showProductImage) {
                        showProductImage = it
                        scope.launch {
                            prefsRepo.updatePosSettings(showProductImage, showStock, showBarcode, confirmCheckout)
                        }
                    }
                    SettingSwitchRow("Tampilkan Jumlah Stok di Kasir", showStock) {
                        showStock = it
                        scope.launch {
                            prefsRepo.updatePosSettings(showProductImage, showStock, showBarcode, confirmCheckout)
                        }
                    }
                    SettingSwitchRow("Tampilkan Tombol Scan Barcode", showBarcode) {
                        showBarcode = it
                        scope.launch {
                            prefsRepo.updatePosSettings(showProductImage, showStock, showBarcode, confirmCheckout)
                        }
                    }
                    SettingSwitchRow("Konfirmasi Sebelum Selesai Transaksi", confirmCheckout) {
                        confirmCheckout = it
                        scope.launch {
                            prefsRepo.updatePosSettings(showProductImage, showStock, showBarcode, confirmCheckout)
                        }
                    }
                }
            }

            // ==========================================
            // 3. PEMBAYARAN
            // ==========================================
            SectionHeader(icon = Icons.Default.Payment, title = "3. Metode Pembayaran")
            AppCard {
                Column(
                    modifier = Modifier.padding(AppSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                ) {
                    SettingSwitchRow("Terima Pembayaran Tunai (Cash)", cashEnabled) {
                        cashEnabled = it
                        scope.launch {
                            prefsRepo.updatePaymentSettings(
                                cashEnabled = cashEnabled,
                                qrisEnabled = qrisEnabled,
                                creditEnabled = creditEnabled,
                                cashReceivedEnabled = cashReceivedEnabled,
                                qrisConfirmationRequired = qrisConfirmationRequired
                            )
                        }
                    }
                    SettingSwitchRow("Terima Pembayaran QRIS", qrisEnabled) {
                        qrisEnabled = it
                        scope.launch {
                            prefsRepo.updatePaymentSettings(
                                cashEnabled = cashEnabled,
                                qrisEnabled = qrisEnabled,
                                creditEnabled = creditEnabled,
                                cashReceivedEnabled = cashReceivedEnabled,
                                qrisConfirmationRequired = qrisConfirmationRequired
                            )
                        }
                    }
                    SettingSwitchRow("Terima Pembayaran Hutang (Piutang)", creditEnabled) {
                        creditEnabled = it
                        scope.launch {
                            prefsRepo.updatePaymentSettings(
                                cashEnabled = cashEnabled,
                                qrisEnabled = qrisEnabled,
                                creditEnabled = creditEnabled,
                                cashReceivedEnabled = cashReceivedEnabled,
                                qrisConfirmationRequired = qrisConfirmationRequired
                            )
                        }
                    }
                    SettingSwitchRow("Hitung Uang Diterima & Kembalian (Cash)", cashReceivedEnabled) {
                        cashReceivedEnabled = it
                        scope.launch {
                            prefsRepo.updatePaymentSettings(
                                cashEnabled = cashEnabled,
                                qrisEnabled = qrisEnabled,
                                creditEnabled = creditEnabled,
                                cashReceivedEnabled = cashReceivedEnabled,
                                qrisConfirmationRequired = qrisConfirmationRequired
                            )
                        }
                    }
                    SettingSwitchRow("Wajib Konfirmasi Sukses QRIS", qrisConfirmationRequired) {
                        qrisConfirmationRequired = it
                        scope.launch {
                            prefsRepo.updatePaymentSettings(
                                cashEnabled = cashEnabled,
                                qrisEnabled = qrisEnabled,
                                creditEnabled = creditEnabled,
                                cashReceivedEnabled = cashReceivedEnabled,
                                qrisConfirmationRequired = qrisConfirmationRequired
                            )
                        }
                    }
                }
            }

            // ==========================================
            // 4. STOK & PERINGATAN
            // ==========================================
            SectionHeader(icon = Icons.Default.Inventory2, title = "4. Stok & Peringatan")
            AppCard {
                Column(
                    modifier = Modifier.padding(AppSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                ) {
                    SettingSwitchRow("Peringatan Barang Hampir Habis", lowStockAlertEnabled) {
                        lowStockAlertEnabled = it
                        scope.launch {
                            prefsRepo.updateStockSettings(
                                lowStockAlertEnabled = lowStockAlertEnabled,
                                defaultLowStockLimit = lowStockLimitInput.toIntOrNull() ?: 2,
                                allowNegativeStock = false
                            )
                        }
                    }
                    AppTextField(
                        value = lowStockLimitInput,
                        onValueChange = {
                            lowStockLimitInput = it
                            val limit = it.toIntOrNull() ?: 2
                            scope.launch {
                                prefsRepo.updateStockSettings(
                                    lowStockAlertEnabled = lowStockAlertEnabled,
                                    defaultLowStockLimit = limit,
                                    allowNegativeStock = false
                                )
                            }
                        },
                        label = "Batas Default Stok Menipis Produk Baru",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }

            // ==========================================
            // 5. STRUK & PRINTER THERMAL
            // ==========================================
            SectionHeader(icon = Icons.Default.Receipt, title = "5. Pengaturan Struk & Printer")
            AppCard {
                Column(
                    modifier = Modifier.padding(AppSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                ) {
                    // Card status & tombol konfigurasi printer thermal
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF8F9FA),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(AppSpacing.md)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Printer Struk (Thermal)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = AppColors.TextPrimary
                                )
                                Text(
                                    text = when {
                                        activePrinterService.isConnected -> "Terhubung (${settingsState.printerDeviceName.ifBlank { settingsState.printerAddress }})"
                                        settingsState.printerType != "NONE" && settingsState.printerAddress.isNotBlank() -> "Tersimpan: ${settingsState.printerDeviceName.ifBlank { settingsState.printerAddress }} (Tidak terhubung)"
                                        else -> "Belum dikonfigurasi"
                                    },
                                    fontSize = 12.sp,
                                    color = when {
                                         activePrinterService.isConnected -> AppColors.GreenPrimary
                                        settingsState.printerType != "NONE" && settingsState.printerAddress.isNotBlank() -> AppColors.TextSecondary
                                        else -> AppColors.RedExpense
                                    }
                                )
                                if (settingsState.printerType != "NONE" && settingsState.printerAddress.isNotBlank()) {
                                    Text(
                                        text = "${settingsState.printerType} | Lebar: ${settingsState.printerPaperWidth}",
                                        fontSize = 11.sp,
                                        color = AppColors.TextSecondary
                                    )
                                }
                            }
                            Button(
                                onClick = { showPrinterSettingsDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = AppColors.GreenPrimary),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Atur Printer", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    SettingSwitchRow("Tampilkan Nama Warung di Struk", showShopNameOnReceipt) {
                        showShopNameOnReceipt = it
                        scope.launch {
                            prefsRepo.updateReceiptSettings(
                                showShopName = showShopNameOnReceipt,
                                showAddress = showAddressOnReceipt,
                                showPhone = showPhoneOnReceipt,
                                showPaymentMethod = showPaymentMethodOnReceipt,
                                showChange = showChangeOnReceipt,
                                footerText = receiptFooterTextInput
                            )
                        }
                    }
                    SettingSwitchRow("Tampilkan Alamat di Struk", showAddressOnReceipt) {
                        showAddressOnReceipt = it
                        scope.launch {
                            prefsRepo.updateReceiptSettings(
                                showShopName = showShopNameOnReceipt,
                                showAddress = showAddressOnReceipt,
                                showPhone = showPhoneOnReceipt,
                                showPaymentMethod = showPaymentMethodOnReceipt,
                                showChange = showChangeOnReceipt,
                                footerText = receiptFooterTextInput
                            )
                        }
                    }
                    SettingSwitchRow("Tampilkan Nomor HP di Struk", showPhoneOnReceipt) {
                        showPhoneOnReceipt = it
                        scope.launch {
                            prefsRepo.updateReceiptSettings(
                                showShopName = showShopNameOnReceipt,
                                showAddress = showAddressOnReceipt,
                                showPhone = showPhoneOnReceipt,
                                showPaymentMethod = showPaymentMethodOnReceipt,
                                showChange = showChangeOnReceipt,
                                footerText = receiptFooterTextInput
                            )
                        }
                    }
                    AppTextField(
                        value = receiptFooterTextInput,
                        onValueChange = {
                            receiptFooterTextInput = it
                            scope.launch {
                                prefsRepo.updateReceiptSettings(
                                    showShopName = showShopNameOnReceipt,
                                    showAddress = showAddressOnReceipt,
                                    showPhone = showPhoneOnReceipt,
                                    showPaymentMethod = showPaymentMethodOnReceipt,
                                    showChange = showChangeOnReceipt,
                                    footerText = it
                                )
                            }
                        },
                        label = "Teks Catatan Kaki Struk"
                    )
                }
            }

            // ==========================================
            // 6. NOTIFIKASI
            // ==========================================
            SectionHeader(icon = Icons.Default.Notifications, title = "6. Notifikasi & Pengingat")
            AppCard {
                Column(
                    modifier = Modifier.padding(AppSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                ) {
                    SettingSwitchRow("Notifikasi Stok Menipis", lowStockNotificationEnabled) {
                        lowStockNotificationEnabled = it
                        scope.launch {
                            prefsRepo.updateNotificationSettings(
                                lowStockNotificationEnabled = lowStockNotificationEnabled,
                                debtReminderEnabled = debtReminderEnabled
                            )
                        }
                    }
                    SettingSwitchRow("Pengingat Jatuh Tempo Hutang", debtReminderEnabled) {
                        debtReminderEnabled = it
                        scope.launch {
                            prefsRepo.updateNotificationSettings(
                                lowStockNotificationEnabled = lowStockNotificationEnabled,
                                debtReminderEnabled = debtReminderEnabled
                            )
                        }
                    }
                }
            }

            // ==========================================
            // 7. TAMPILAN
            // ==========================================
            SectionHeader(icon = Icons.Default.Palette, title = "7. Tampilan Aplikasi")
            AppCard {
                Column(
                    modifier = Modifier.padding(AppSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                ) {
                    Text("Tema Aplikasi: Mengikuti Sistem (Light Theme Optimal)", style = MaterialTheme.typography.bodyMedium)
                }
            }

            // ==========================================
            // 8. KEAMANAN & PIN (SECURE SALTED HASH)
            // ==========================================
            SectionHeader(icon = Icons.Default.Lock, title = "8. Keamanan & PIN Pemilik")
            AppCard {
                Column(
                    modifier = Modifier.padding(AppSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                ) {
                    SettingSwitchRow("Kunci Aplikasi dengan PIN", settingsState.pinEnabled) { isChecked ->
                        if (isChecked && !settingsState.hasPinSet) {
                            showSetPinDialog = true
                        } else {
                            scope.launch {
                                prefsRepo.setPinEnabled(isChecked)
                            }
                        }
                    }

                    if (settingsState.hasPinSet) {
                        Text(
                            text = "Status: PIN Terpasang Aman (Enkripsi Salted SHA-256)",
                            style = MaterialTheme.typography.labelSmall,
                            color = AppColors.GreenPrimary,
                            fontWeight = FontWeight.Medium
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                            OutlinedButton(
                                onClick = { showSetPinDialog = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Ganti PIN")
                            }
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        prefsRepo.clearPin()
                                        Toast.makeText(context, "PIN pemilik dinonaktifkan", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.RedExpense)
                            ) {
                                Text("Hapus PIN")
                            }
                        }
                    } else {
                        Text(
                            text = "PIN belum dibuat. Aktifkan saklar untuk membuat PIN 4 angka.",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.TextSecondary
                        )
                    }
                }
            }

            // ==========================================
            // 9. LISENSI
            // ==========================================
            SectionHeader(icon = Icons.Default.VerifiedUser, title = "9. Lisensi Aplikasi")
            AppCard(
                backgroundColor = if (licenseStatus == LicenseStatus.ACTIVE) AppColors.GreenLight else Color(0xFFFFF7E6)
            ) {
                Column(
                    modifier = Modifier.padding(AppSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                ) {
                    Text(
                        licenseTier.label,
                        fontWeight = FontWeight.Bold,
                        color = if (licenseStatus == LicenseStatus.ACTIVE) AppColors.GreenDark else Color(0xFFD46B08)
                    )
                    Text(
                        "Status Lisensi: ${licenseStatus.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextSecondary
                    )
                    Text(
                        "Model: Sekali Bayar Offline-First (Tanpa Langganan)",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextSecondary
                    )
                    Spacer(Modifier.height(AppSpacing.xs))
                    PrimaryButton(
                        text = "Periksa / Pulihkan Lisensi",
                        onClick = {
                            scope.launch {
                                licManager.refreshLicense()
                                Toast.makeText(context, "Status lisensi diperbarui: ${licManager.licenseStatus.value.name}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }

            // ==========================================
            // 10. CADANGAN & PEMULIHAN (GOOGLE SHEETS)
            // ==========================================
            SectionHeader(icon = Icons.Default.CloudSync, title = "10. Cadangan & Pemulihan")
            AppCard {
                Column(
                    modifier = Modifier.padding(AppSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                ) {
                    Text(
                        text = "Amankan data warung Anda di Google Sheets.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = AppColors.TextPrimary
                    )
                    Text(
                        text = "Data transaksi, produk, pelanggan, dan kasir dapat dicadangkan dan dipulihkan secara aman.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextSecondary
                    )

                    Spacer(Modifier.height(4.dp))

                    // Status Information Box
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF8F9FA),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(AppSpacing.sm),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val lastBackupText = if (settingsState.lastBackupTimestamp <= 0L) {
                                "Belum pernah dicadangkan"
                            } else {
                                val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))
                                sdf.format(Date(settingsState.lastBackupTimestamp))
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Cadangan Terakhir:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppColors.TextSecondary
                                )
                                Text(
                                    text = lastBackupText,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (settingsState.lastBackupTimestamp > 0L) AppColors.GreenPrimary else AppColors.TextPrimary
                                )
                            }

                            if (settingsState.googleAccountEmail.isNotBlank()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Akun Google:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AppColors.TextSecondary
                                    )
                                    Text(
                                        text = settingsState.googleAccountEmail,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = AppColors.TextPrimary
                                    )
                                }
                            }

                            if (settingsState.backupSpreadsheetId.isNotBlank()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Status Cadangan:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AppColors.TextSecondary
                                    )
                                    Text(
                                        text = "Tersimpan di Google Sheets",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = AppColors.GreenDark
                                    )
                                }
                            }
                        }
                    }

                    // Operation state banners
                    when (val state = backupState) {
                        is BackupOpState.Loading -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = AppColors.GreenPrimary
                                )
                                Text(
                                    text = "Sedang mencadangkan data...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppColors.GreenPrimary
                                )
                            }
                        }
                        is BackupOpState.Success -> {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = AppColors.GreenLight,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppColors.GreenDark,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                        is BackupOpState.Error -> {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFFFECEC),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppColors.RedExpense,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                        else -> {}
                    }

                    when (val state = restoreState) {
                        is RestoreOpState.Loading -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = AppColors.GreenPrimary
                                )
                                Text(
                                    text = "Sedang memulihkan data...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppColors.GreenPrimary
                                )
                            }
                        }
                        is RestoreOpState.Success -> {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = AppColors.GreenLight,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppColors.GreenDark,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                        is RestoreOpState.Error -> {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFFFECEC),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppColors.RedExpense,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                        else -> {}
                    }

                    Spacer(Modifier.height(4.dp))

                    val isBusy = backupState is BackupOpState.Loading || restoreState is RestoreOpState.Loading

                    PrimaryButton(
                        text = if (backupState is BackupOpState.Loading) "Mencadangkan..." else "Cadangkan Sekarang",
                        enabled = !isBusy,
                        onClick = {
                            bViewModel.resetRestoreState()
                            bViewModel.performBackup()
                        }
                    )

                    OutlinedButton(
                        onClick = {
                            bViewModel.resetBackupState()
                            showRestoreConfirmDialog = true
                        },
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (restoreState is RestoreOpState.Loading) "Memulihkan..." else "Pulihkan Data",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // ==========================================
            // 11. TENTANG
            // ==========================================
            SectionHeader(icon = Icons.Default.Info, title = "11. Tentang Aplikasi")
            AppCard {
                Column(modifier = Modifier.padding(AppSpacing.md)) {
                    Text("Buku Warung v0.1.0", fontWeight = FontWeight.Bold)
                    Text(
                        "Aplikasi Kasir & Pembukuan Warung Kecil 100% Offline-First.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextSecondary
                    )
                    Spacer(Modifier.height(AppSpacing.xs))
                    Text(
                        "Seluruh data kas, produk, dan transaksi tersimpan lokal di perangkat Anda.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextSecondary
                    )
                }
            }

            Spacer(Modifier.height(AppSpacing.xl))
        }

        // Set / Change PIN Dialog
        if (showSetPinDialog) {
            AlertDialog(
                onDismissRequest = {
                    showSetPinDialog = false
                    newPinInput = ""
                    confirmPinInput = ""
                    pinDialogError = null
                },
                title = { Text("Buat PIN Pemilik (4 Angka)", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                        Text(
                            "PIN digunakan untuk mengamankan akses kasir & pembukuan.",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.TextSecondary
                        )

                        if (pinDialogError != null) {
                            Text(pinDialogError!!, color = AppColors.RedExpense, style = MaterialTheme.typography.bodySmall)
                        }

                        AppTextField(
                            value = newPinInput,
                            onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) newPinInput = it },
                            label = "PIN Baru (4 Digit)",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                        )

                        AppTextField(
                            value = confirmPinInput,
                            onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) confirmPinInput = it },
                            label = "Konfirmasi PIN Baru",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newPinInput.length != 4) {
                                pinDialogError = "PIN harus 4 angka"
                            } else if (newPinInput != confirmPinInput) {
                                pinDialogError = "Konfirmasi PIN tidak cocok"
                            } else {
                                scope.launch {
                                    prefsRepo.setOwnerPin(newPinInput)
                                    showSetPinDialog = false
                                    newPinInput = ""
                                    confirmPinInput = ""
                                    pinDialogError = null
                                    Toast.makeText(context, "PIN pemilik berhasil disimpan aman", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.GreenPrimary)
                    ) {
                        Text("Simpan PIN")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showSetPinDialog = false
                        newPinInput = ""
                        confirmPinInput = ""
                        pinDialogError = null
                    }) {
                        Text("Batal")
                    }
                }
            )
        }

        if (showPrinterSettingsDialog) {
            PrinterSettingsDialog(
                userSettings = settingsState,
                userPreferencesRepository = prefsRepo,
                printerService = activePrinterService,
                onDismiss = { showPrinterSettingsDialog = false }
            )
        }

        if (showRestoreConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showRestoreConfirmDialog = false },
                title = { Text("Pulihkan Data Cadangan?", fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "Data di perangkat ini akan diganti dengan data dari cadangan.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.TextPrimary
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showRestoreConfirmDialog = false
                            bViewModel.performRestore()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.GreenPrimary)
                    ) {
                        Text("Pulihkan")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRestoreConfirmDialog = false }) {
                        Text("Batal")
                    }
                }
            )
        }
    }
}

@Composable
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = AppSpacing.xs)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AppColors.GreenPrimary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(AppSpacing.xs))
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
            color = AppColors.TextPrimary
        )
    }
}

@Composable
private fun SettingSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.bodyMedium
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AppColors.GreenPrimary,
                checkedTrackColor = AppColors.GreenLight
            )
        )
    }
}
