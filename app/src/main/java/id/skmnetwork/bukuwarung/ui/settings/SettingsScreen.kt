package id.skmnetwork.bukuwarung.ui.settings

import android.accounts.AccountManager
import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.skmnetwork.bukuwarung.backup.BackupRestoreManager
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.preferences.UserPreferencesRepository
import id.skmnetwork.bukuwarung.data.preferences.UserSettings
import id.skmnetwork.bukuwarung.domain.business.BusinessActivity
import id.skmnetwork.bukuwarung.domain.business.BusinessCategory
import id.skmnetwork.bukuwarung.domain.business.BusinessType
import id.skmnetwork.bukuwarung.domain.business.BusinessTaxonomyRegistry
import id.skmnetwork.bukuwarung.domain.business.ResolvedBusinessProfile
import id.skmnetwork.bukuwarung.license.LicenseManager
import id.skmnetwork.bukuwarung.license.LicenseStatus
import id.skmnetwork.bukuwarung.license.ValidationResult
import id.skmnetwork.bukuwarung.ui.components.AppCard
import id.skmnetwork.bukuwarung.ui.components.AppTextField
import id.skmnetwork.bukuwarung.ui.components.CameraQrisPhotoDialog
import id.skmnetwork.bukuwarung.ui.components.PrimaryButton
import id.skmnetwork.bukuwarung.ui.components.SecondaryButton
import id.skmnetwork.bukuwarung.ui.theme.AppColors
import id.skmnetwork.bukuwarung.ui.theme.AppShapes
import id.skmnetwork.bukuwarung.ui.theme.AppSpacing
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import id.skmnetwork.bukuwarung.BuildConfig

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

    val resolvedProfile = remember(settingsState.primaryBusinessType, settingsState.secondaryActivities) {
        BusinessTaxonomyRegistry.resolve(
            primaryType = settingsState.primaryBusinessType,
            secondaryActivities = settingsState.secondaryActivities
        )
    }
    val terminology = resolvedProfile.terminology
    val shopLabel = "Usaha"
    val productLabel = terminology.productLabel
    val stockLabel = terminology.stockLabel
    val transactionLabel = terminology.transactionLabel
    val receiptLabel = "Struk"
    val debtLabel = terminology.debtLabel
    val customerLabel = terminology.customerLabel
    val supplierLabel = terminology.supplierLabel
    val purchaseLabel = terminology.purchaseLabel

    var showBusinessProfileDialog by remember { mutableStateOf(false) }
    var isCheckingLicense by remember { mutableStateOf(false) }

    // 1. Profil Warung / Usaha
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

    // 11. QRIS Warung
    var showQrisCameraDialog by remember { mutableStateOf(false) }
    var showQrisChoiceDialog by remember { mutableStateOf(false) }
    var showDeleteQrisConfirmDialog by remember { mutableStateOf(false) }

    var showDisconnectGoogleDialog by remember { mutableStateOf(false) }
    var showManualEmailDialog by remember { mutableStateOf(false) }
    var manualEmailInput by remember { mutableStateOf("") }
    var pendingAuthEmail by remember { mutableStateOf("") }

    val googleAuthState by bViewModel.googleAuthState.collectAsStateWithLifecycle()

    val authorizationResolutionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val emailToUse = pendingAuthEmail.ifBlank { settingsState.googleAccountEmail }
        bViewModel.onAuthorizationResolutionResult(emailToUse, result.resultCode, result.data)
    }

    LaunchedEffect(googleAuthState) {
        val authState = googleAuthState
        if (authState is id.skmnetwork.bukuwarung.backup.transport.GoogleAuthConnectionState.AuthorizationRequired) {
            pendingAuthEmail = authState.email
            try {
                val intentSenderRequest = androidx.activity.result.IntentSenderRequest.Builder(
                    authState.resolutionIntent.intentSender
                ).build()
                authorizationResolutionLauncher.launch(intentSenderRequest)
            } catch (_: Exception) {}
        }
    }

    val accountPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val email = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            if (!email.isNullOrBlank()) {
                pendingAuthEmail = email
                bViewModel.initiateAccountAuthorization(email)
            }
        }
    }

    fun launchGoogleAccountPicker() {
        try {
            val intent = AccountManager.newChooseAccountIntent(
                null,
                null,
                arrayOf("com.google"),
                null,
                null,
                null,
                null
            )
            accountPickerLauncher.launch(intent)
        } catch (e: Exception) {
            manualEmailInput = settingsState.googleAccountEmail
            showManualEmailDialog = true
        }
    }

    val qrisImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val result = prefsRepo.saveQrisImageFromUri(uri)
                if (result.isSuccess) {
                    Toast.makeText(context, "Foto QRIS warung berhasil disimpan", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(
                        context,
                        result.exceptionOrNull()?.localizedMessage ?: "QRIS tidak dapat digunakan. Pastikan foto QRIS terlihat jelas dan tidak terpotong.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

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
            Surface(
                color = Color.White,
                shadowElevation = 0.5.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(AppColors.GreenPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storefront,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Pengaturan",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = AppColors.TextPrimary
                            ),
                            maxLines = 1
                        )
                        Text(
                            text = "Atur profil dan preferensi usaha",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                color = AppColors.TextSecondary
                            ),
                            maxLines = 1
                        )
                    }
                }
            }
        },
        containerColor = Color.White
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ==========================================
            // 0. TIPE USAHA & MODEL OPERASIONAL
            // ==========================================
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "TIPE USAHA & MODEL OPERASIONAL",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = AppColors.GreenPrimary,
                        letterSpacing = 0.5.sp
                    )
                )
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFEFF3F0)),
                    shadowElevation = 0.5.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(AppColors.GreenLight),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Business,
                                        contentDescription = null,
                                        tint = AppColors.GreenPrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = resolvedProfile.businessType.displayName,
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.5.sp,
                                            color = AppColors.TextPrimary
                                        )
                                    )
                                    Text(
                                        text = "Kategori: ${resolvedProfile.businessType.category.displayName}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.sp,
                                            color = AppColors.TextSecondary
                                        )
                                    )
                                }
                            }
                        }

                        Text(
                            text = resolvedProfile.businessType.description,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.5.sp,
                                color = AppColors.TextSecondary
                            )
                        )

                        if (resolvedProfile.activities.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Fitur & Aktivitas Operasional Aktif:",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                        color = AppColors.TextPrimary
                                    )
                                )
                                Text(
                                    text = resolvedProfile.activities.joinToString(", ") { it.displayName },
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.sp,
                                        color = AppColors.GreenDark,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }

                        Spacer(Modifier.height(2.dp))

                        if (settingsState.isSetupCompleted && settingsState.businessTypeLocked) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFF5F5F5),
                                border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = Color.Gray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Tipe usaha terkunci setelah onboarding",
                                        color = Color.Gray,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = { showBusinessProfileDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, AppColors.GreenPrimary)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = AppColors.GreenPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Ubah Tipe & Model Usaha",
                                    color = AppColors.GreenPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp
                                )
                            }
                        }
                    }
                }
            }

            // ==========================================
            // 1. PROFIL USAHA
            // ==========================================
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "PROFIL ${shopLabel.uppercase()}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = AppColors.GreenPrimary,
                        letterSpacing = 0.5.sp
                    )
                )
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFEFF3F0)),
                    shadowElevation = 0.5.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AppTextField(
                            value = shopNameInput,
                            onValueChange = { shopNameInput = it },
                            label = "Nama $shopLabel *"
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
                            label = "Alamat $shopLabel"
                        )
                        Spacer(Modifier.height(2.dp))
                        PrimaryButton(
                            text = "Simpan Profil $shopLabel",
                            onClick = {
                                scope.launch {
                                    prefsRepo.saveShopProfile(
                                        shopName = shopNameInput,
                                        ownerName = ownerNameInput,
                                        phone = phoneInput,
                                        address = addressInput
                                    )
                                    Toast.makeText(context, "Profil $shopLabel berhasil disimpan", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }

            // ==========================================
            // 2. PENJUALAN & KASIR (POS)
            // ==========================================
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "${transactionLabel.uppercase()} & KASIR",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = AppColors.GreenPrimary,
                        letterSpacing = 0.5.sp
                    )
                )
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFEFF3F0)),
                    shadowElevation = 0.5.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SettingSwitchRow("Tampilkan Foto $productLabel di Kasir", showProductImage) {
                            showProductImage = it
                            scope.launch {
                                prefsRepo.updatePosSettings(showProductImage, showStock, showBarcode, confirmCheckout)
                            }
                        }
                        HorizontalDivider(color = Color(0xFFF0F4F0))
                        SettingSwitchRow("Tampilkan Jumlah $stockLabel di Kasir", showStock) {
                            showStock = it
                            scope.launch {
                                prefsRepo.updatePosSettings(showProductImage, showStock, showBarcode, confirmCheckout)
                            }
                        }
                        HorizontalDivider(color = Color(0xFFF0F4F0))
                        SettingSwitchRow("Tampilkan Tombol Scan Barcode", showBarcode) {
                            showBarcode = it
                            scope.launch {
                                prefsRepo.updatePosSettings(showProductImage, showStock, showBarcode, confirmCheckout)
                            }
                        }
                        HorizontalDivider(color = Color(0xFFF0F4F0))
                        SettingSwitchRow("Konfirmasi Sebelum Selesai Transaksi", confirmCheckout) {
                            confirmCheckout = it
                            scope.launch {
                                prefsRepo.updatePosSettings(showProductImage, showStock, showBarcode, confirmCheckout)
                            }
                        }
                    }
                }
            }

            // ==========================================
            // 3. METODE PEMBAYARAN
            // ==========================================
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "METODE PEMBAYARAN",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = AppColors.GreenPrimary,
                        letterSpacing = 0.5.sp
                    )
                )
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFEFF3F0)),
                    shadowElevation = 0.5.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
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
                        HorizontalDivider(color = Color(0xFFF0F4F0))
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
                        HorizontalDivider(color = Color(0xFFF0F4F0))
                        SettingSwitchRow("Terima Pembayaran $debtLabel", creditEnabled) {
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
                        HorizontalDivider(color = Color(0xFFF0F4F0))
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
                        HorizontalDivider(color = Color(0xFFF0F4F0))
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
            }

            // ==========================================
            // 4. QRIS WARUNG / USAHA
            // ==========================================
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "QRIS STATIS ${shopLabel.uppercase()}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = AppColors.GreenPrimary,
                        letterSpacing = 0.5.sp
                    )
                )
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFEFF3F0)),
                    shadowElevation = 0.5.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val qrisBitmap = remember(settingsState.qrisImagePath) {
                            if (settingsState.qrisImagePath.isNotBlank()) {
                                try {
                                    val file = File(settingsState.qrisImagePath)
                                    if (file.exists() && file.length() > 0) {
                                        BitmapFactory.decodeFile(file.absolutePath)
                                    } else null
                                } catch (e: Exception) {
                                    null
                                }
                            } else null
                        }

                        val isConfigured = qrisBitmap != null

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Status QRIS Merchant",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp, color = AppColors.TextPrimary)
                            )
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isConfigured) Color(0xFFE8F5E9) else Color(0xFFFFF7E6),
                                border = BorderStroke(1.dp, if (isConfigured) Color(0xFFC8E6C9) else Color(0xFFFFE0B2))
                            ) {
                                Text(
                                    text = if (isConfigured) "Sudah Diatur" else "Belum Diatur",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = if (isConfigured) AppColors.GreenDark else Color(0xFFD46B08),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        if (isConfigured) {
                            Text(
                                text = "QRIS resmi $shopLabel Anda aktif dan akan ditampilkan saat checkout QRIS di kasir.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                color = AppColors.TextSecondary
                            )

                            // QRIS Image Preview Card
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFF9FBF9))
                                    .border(1.dp, Color(0xFFEFF3F0), RoundedCornerShape(10.dp))
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = qrisBitmap!!.asImageBitmap(),
                                    contentDescription = "Preview QRIS $shopLabel",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 200.dp),
                                    contentScale = ContentScale.Fit
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { showQrisChoiceDialog = true },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Ganti QRIS", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { showDeleteQrisConfirmDialog = true },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.RedExpense)
                                ) {
                                    Text("Hapus QRIS", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            Text(
                                text = "Unggah atau foto QRIS resmi merchant Anda agar pelanggan dapat memindai langsung dari aplikasi kasir.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                color = AppColors.TextSecondary
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { showQrisCameraDialog = true },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.GreenPrimary),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Ambil Foto", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { qrisImagePickerLauncher.launch("image/*") },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Pilih Galeri", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // 5. STOK & PERINGATAN
            // ==========================================
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "${stockLabel.uppercase()} & PERINGATAN",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = AppColors.GreenPrimary,
                        letterSpacing = 0.5.sp
                    )
                )
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFEFF3F0)),
                    shadowElevation = 0.5.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SettingSwitchRow("Peringatan $productLabel Hampir Habis", lowStockAlertEnabled) {
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
                            label = "Batas Default $stockLabel Menipis $productLabel Baru",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                }
            }

            // ==========================================
            // 6. STRUK & PRINTER THERMAL
            // ==========================================
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "PRINTER & ${receiptLabel.uppercase()}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = AppColors.GreenPrimary,
                        letterSpacing = 0.5.sp
                    )
                )
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFEFF3F0)),
                    shadowElevation = 0.5.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Card status & tombol konfigurasi printer thermal
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF9FBF9),
                            border = BorderStroke(1.dp, Color(0xFFEFF3F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Printer $receiptLabel (Thermal)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp,
                                        color = AppColors.TextPrimary
                                    )
                                    Text(
                                        text = when {
                                            activePrinterService.isConnected -> "Terhubung (${settingsState.printerDeviceName.ifBlank { settingsState.printerAddress }})"
                                            settingsState.printerType != "NONE" && settingsState.printerAddress.isNotBlank() -> "Tersimpan: ${settingsState.printerDeviceName.ifBlank { settingsState.printerAddress }} (Tidak terhubung)"
                                            else -> "Belum dikonfigurasi"
                                        },
                                        fontSize = 11.5.sp,
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
                                Spacer(Modifier.width(8.dp))
                                Button(
                                    onClick = { showPrinterSettingsDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.GreenPrimary),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Atur Printer", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        HorizontalDivider(color = Color(0xFFF0F4F0))

                        SettingSwitchRow("Tampilkan Nama $shopLabel di $receiptLabel", showShopNameOnReceipt) {
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
                        HorizontalDivider(color = Color(0xFFF0F4F0))
                        SettingSwitchRow("Tampilkan Alamat di $receiptLabel", showAddressOnReceipt) {
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
                        HorizontalDivider(color = Color(0xFFF0F4F0))
                        SettingSwitchRow("Tampilkan Nomor HP di $receiptLabel", showPhoneOnReceipt) {
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
                            label = "Teks Catatan Kaki $receiptLabel"
                        )
                    }
                }
            }

            // ==========================================
            // 7. NOTIFIKASI
            // ==========================================
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "NOTIFIKASI & PENGINGAT",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = AppColors.GreenPrimary,
                        letterSpacing = 0.5.sp
                    )
                )
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFEFF3F0)),
                    shadowElevation = 0.5.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SettingSwitchRow("Notifikasi $stockLabel Menipis", lowStockNotificationEnabled) {
                            lowStockNotificationEnabled = it
                            scope.launch {
                                prefsRepo.updateNotificationSettings(
                                    lowStockNotificationEnabled = lowStockNotificationEnabled,
                                    debtReminderEnabled = debtReminderEnabled
                                )
                            }
                        }
                        HorizontalDivider(color = Color(0xFFF0F4F0))
                        SettingSwitchRow("Pengingat Jatuh Tempo $debtLabel", debtReminderEnabled) {
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
            }

            // ==========================================
            // 8. KEAMANAN & PIN
            // ==========================================
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "KEAMANAN & PIN PEMILIK",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = AppColors.GreenPrimary,
                        letterSpacing = 0.5.sp
                    )
                )
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFEFF3F0)),
                    shadowElevation = 0.5.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                                color = AppColors.GreenPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { showSetPinDialog = true },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Ganti PIN", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            prefsRepo.clearPin()
                                            Toast.makeText(context, "PIN pemilik dinonaktifkan", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.RedExpense)
                                ) {
                                    Text("Hapus PIN", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        } else {
                            Text(
                                text = "PIN belum dibuat. Aktifkan saklar untuk membuat PIN 4 angka.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                color = AppColors.TextSecondary
                            )
                        }
                    }
                }
            }

            // ==========================================
            // 9. CADANGAN & PEMULIHAN (GOOGLE SHEETS)
            // ==========================================
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "CADANGAN & PEMULIHAN",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = AppColors.GreenPrimary,
                        letterSpacing = 0.5.sp
                    )
                )
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFEFF3F0)),
                    shadowElevation = 0.5.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Amankan data $shopLabel Anda di Google Sheets.",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                            fontWeight = FontWeight.SemiBold,
                            color = AppColors.TextPrimary
                        )
                        Text(
                            text = "Data transaksi, $productLabel, $customerLabel, dan kas dapat dicadangkan dan dipulihkan secara aman.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                            color = AppColors.TextSecondary
                        )

                        // 1. Akun Google Box
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF9FBF9),
                            border = BorderStroke(1.dp, Color(0xFFEFF3F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Akun Google",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                        fontWeight = FontWeight.Bold,
                                        color = AppColors.TextPrimary
                                    )
                                    when (val authState = googleAuthState) {
                                        is id.skmnetwork.bukuwarung.backup.transport.GoogleAuthConnectionState.Connected -> {
                                            Text(
                                                text = "🟢 Terhubung",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                                fontWeight = FontWeight.Bold,
                                                color = AppColors.GreenPrimary
                                            )
                                        }
                                        is id.skmnetwork.bukuwarung.backup.transport.GoogleAuthConnectionState.Authorizing -> {
                                            Text(
                                                text = "⏳ Menghubungkan...",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                                fontWeight = FontWeight.Medium,
                                                color = AppColors.GreenDark
                                            )
                                        }
                                        is id.skmnetwork.bukuwarung.backup.transport.GoogleAuthConnectionState.AuthorizationRequired -> {
                                            Text(
                                                text = "🟡 Perlu Otorisasi Ulang",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFE65100)
                                            )
                                        }
                                        else -> {
                                            Text(
                                                text = "⚪ Belum terhubung",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                                color = AppColors.TextSecondary
                                            )
                                        }
                                    }
                                }

                                when (val authState = googleAuthState) {
                                    is id.skmnetwork.bukuwarung.backup.transport.GoogleAuthConnectionState.Connected -> {
                                        Text(
                                            text = authState.email,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.5.sp),
                                            fontWeight = FontWeight.Medium,
                                            color = AppColors.TextPrimary
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = { launchGoogleAccountPicker() },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(10.dp)
                                            ) {
                                                Text("Ganti Akun", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Bold))
                                            }
                                            OutlinedButton(
                                                onClick = { showDisconnectGoogleDialog = true },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(10.dp),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.RedExpense)
                                            ) {
                                                Text("Putuskan", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Bold))
                                            }
                                        }
                                    }
                                    is id.skmnetwork.bukuwarung.backup.transport.GoogleAuthConnectionState.Authorizing -> {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(14.dp),
                                                strokeWidth = 2.dp,
                                                color = AppColors.GreenPrimary
                                            )
                                            Text(
                                                text = "Memverifikasi otorisasi akun Google...",
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                                color = AppColors.TextSecondary
                                            )
                                        }
                                    }
                                    is id.skmnetwork.bukuwarung.backup.transport.GoogleAuthConnectionState.AuthorizationRequired -> {
                                        Text(
                                            text = "Akun: ${authState.email}\nIzin akses Google Sheets / Drive perlu diperbarui.",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                            color = AppColors.TextSecondary
                                        )
                                        PrimaryButton(
                                            text = "Hubungkan Kembali",
                                            onClick = {
                                                try {
                                                    val intentSenderRequest = androidx.activity.result.IntentSenderRequest.Builder(
                                                        authState.resolutionIntent.intentSender
                                                    ).build()
                                                    authorizationResolutionLauncher.launch(intentSenderRequest)
                                                } catch (_: Exception) {
                                                    bViewModel.initiateAccountAuthorization(authState.email)
                                                }
                                            }
                                        )
                                    }
                                    is id.skmnetwork.bukuwarung.backup.transport.GoogleAuthConnectionState.Error -> {
                                        Text(
                                            text = authState.message,
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                            color = AppColors.RedExpense
                                        )
                                        PrimaryButton(
                                            text = "Hubungkan Akun Google",
                                            onClick = { launchGoogleAccountPicker() }
                                        )
                                    }
                                    is id.skmnetwork.bukuwarung.backup.transport.GoogleAuthConnectionState.Disconnected -> {
                                        Text(
                                            text = "Hubungkan akun Google untuk menyimpan cadangan data warung Anda.",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                            color = AppColors.TextSecondary
                                        )
                                        PrimaryButton(
                                            text = "Hubungkan Akun Google",
                                            onClick = { launchGoogleAccountPicker() }
                                        )
                                    }
                                }
                            }
                        }

                        // 2. Spreadsheet Cadangan Box
                        if (settingsState.googleAccountEmail.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFF9FBF9),
                                border = BorderStroke(1.dp, Color(0xFFEFF3F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val sheetTitle = settingsState.backupSpreadsheetName.ifBlank {
                                        "Buku Warung - " + settingsState.shopName.ifBlank { "Warung Saya" }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Spreadsheet Cadangan",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                            fontWeight = FontWeight.Bold,
                                            color = AppColors.TextPrimary
                                        )
                                        if (settingsState.backupSpreadsheetId.isNotBlank()) {
                                            Text(
                                                text = "Tersimpan",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                                fontWeight = FontWeight.Medium,
                                                color = AppColors.GreenDark
                                            )
                                        }
                                    }

                                    Text(
                                        text = "📊 $sheetTitle",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.5.sp),
                                        fontWeight = FontWeight.Medium,
                                        color = AppColors.TextPrimary
                                    )

                                    if (settingsState.backupSpreadsheetId.isNotBlank()) {
                                        OutlinedButton(
                                            onClick = {
                                                try {
                                                    val uri = Uri.parse(bViewModel.getSpreadsheetWebUrl(settingsState.backupSpreadsheetId))
                                                    val intent = Intent(Intent.ACTION_VIEW, uri)
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Tidak dapat membuka browser", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Buka Spreadsheet ↗", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }

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
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                            color = AppColors.TextSecondary
                                        )
                                        Text(
                                            text = lastBackupText,
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                            fontWeight = FontWeight.Bold,
                                            color = if (settingsState.lastBackupTimestamp > 0L) AppColors.GreenPrimary else AppColors.TextPrimary
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
                                        text = "Sedang mencadangkan data ke Google Sheets...",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                        color = AppColors.GreenPrimary
                                    )
                                }
                            }
                            is BackupOpState.Success -> {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFE8F5E9),
                                    border = BorderStroke(1.dp, Color(0xFFC8E6C9)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = state.message,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                        color = AppColors.GreenDark,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                            is BackupOpState.Error -> {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFFFECEC),
                                    border = BorderStroke(1.dp, Color(0xFFFFCDD2)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = state.message,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                        color = AppColors.RedExpense,
                                        modifier = Modifier.padding(10.dp)
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
                                        text = "Sedang memulihkan data dari Google Sheets...",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                        color = AppColors.GreenPrimary
                                    )
                                }
                            }
                            is RestoreOpState.Success -> {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFE8F5E9),
                                    border = BorderStroke(1.dp, Color(0xFFC8E6C9)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = state.message,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                        color = AppColors.GreenDark,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                            is RestoreOpState.Error -> {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFFFECEC),
                                    border = BorderStroke(1.dp, Color(0xFFFFCDD2)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = state.message,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                        color = AppColors.RedExpense,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                            else -> {}
                        }

                        val isBusy = backupState is BackupOpState.Loading || restoreState is RestoreOpState.Loading

                        PrimaryButton(
                            text = if (backupState is BackupOpState.Loading) "Mencadangkan..." else "Cadangkan Sekarang",
                            enabled = !isBusy,
                            onClick = {
                                bViewModel.resetRestoreState()
                                if (settingsState.googleAccountEmail.isBlank()) {
                                    launchGoogleAccountPicker()
                                } else {
                                    bViewModel.performBackup()
                                }
                            }
                        )

                        OutlinedButton(
                            onClick = {
                                bViewModel.resetBackupState()
                                if (settingsState.googleAccountEmail.isBlank()) {
                                    launchGoogleAccountPicker()
                                } else {
                                    showRestoreConfirmDialog = true
                                }
                            },
                            enabled = !isBusy,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = if (restoreState is RestoreOpState.Loading) "Memulihkan..." else "Pulihkan Data",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // ==========================================
            // 10. LISENSI APLIKASI
            // ==========================================
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "LISENSI APLIKASI",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = AppColors.GreenPrimary,
                        letterSpacing = 0.5.sp
                    )
                )
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (licenseStatus == LicenseStatus.ACTIVE) Color(0xFFE8F5E9) else Color(0xFFFFF7E6),
                    border = BorderStroke(1.dp, if (licenseStatus == LicenseStatus.ACTIVE) Color(0xFFC8E6C9) else Color(0xFFFFE0B2)),
                    shadowElevation = 0.5.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = licenseTier.label,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = if (licenseStatus == LicenseStatus.ACTIVE) AppColors.GreenDark else Color(0xFFD46B08)
                        )
                        Text(
                            text = "Status Lisensi: ${licenseStatus.name}",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                            color = AppColors.TextSecondary
                        )
                        Text(
                            text = "Model: Sekali Bayar Offline-First (Tanpa Langganan)",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                            color = AppColors.TextSecondary
                        )
                        Spacer(Modifier.height(4.dp))
                        PrimaryButton(
                            text = if (isCheckingLicense) "Memverifikasi..." else "Periksa / Pulihkan Lisensi",
                            enabled = !isCheckingLicense,
                            onClick = {
                                scope.launch {
                                    isCheckingLicense = true
                                    val result = licManager.validateOnline()
                                    licManager.refreshLicense()
                                    isCheckingLicense = false
                                    val message = when (result) {
                                        is ValidationResult.Valid -> "Lisensi valid dan aktif"
                                        is ValidationResult.NetworkError -> "Gagal terhubung ke server. Status lokal tetap aktif."
                                        is ValidationResult.ServerError -> "Kesalahan server. Status lokal tetap aktif."
                                        is ValidationResult.EmailMismatch -> result.message
                                        is ValidationResult.DeviceMismatch -> result.message
                                        is ValidationResult.Revoked -> result.message
                                        is ValidationResult.Invalid -> result.message
                                    }
                                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                }
                            }
                        )
                    }
                }
            }

            // ==========================================
            // 11. TENTANG APLIKASI
            // ==========================================
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "TENTANG APLIKASI",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = AppColors.GreenPrimary,
                        letterSpacing = 0.5.sp
                    )
                )
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFEFF3F0)),
                    shadowElevation = 0.5.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Buku Warung v${BuildConfig.VERSION_NAME}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = AppColors.TextPrimary
                        )
                        Text(
                            text = "Aplikasi kasir dan pembukuan sederhana untuk usaha kecil.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                            color = AppColors.TextSecondary
                        )
                        Text(
                            text = "Data usaha tersimpan secara lokal di perangkat dan dapat dicadangkan melalui Google Sheets.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                            color = AppColors.TextSecondary
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
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
            val lastBackupFormatted = if (settingsState.lastBackupTimestamp <= 0L) {
                "Belum ada data cadangan"
            } else {
                val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))
                sdf.format(Date(settingsState.lastBackupTimestamp))
            }
            val sheetTitle = settingsState.backupSpreadsheetName.ifBlank {
                "Buku Warung - " + settingsState.shopName.ifBlank { "Warung Saya" }
            }

            AlertDialog(
                onDismissRequest = { showRestoreConfirmDialog = false },
                title = { Text("Pulihkan Data Cadangan?", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                        Text("Sumber Cadangan:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        Text("• Akun: ${settingsState.googleAccountEmail}", style = MaterialTheme.typography.bodySmall)
                        Text("• Spreadsheet: $sheetTitle", style = MaterialTheme.typography.bodySmall)
                        Text("• Cadangan: $lastBackupFormatted", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "PERINGATAN: Pemulihan akan mengganti seluruh data transaksi, produk, dan kas di perangkat ini dengan data dari cadangan Google Sheets.",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.RedExpense,
                            fontWeight = FontWeight.Medium
                        )
                    }
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

        if (showDisconnectGoogleDialog) {
            AlertDialog(
                onDismissRequest = { showDisconnectGoogleDialog = false },
                title = { Text("Putuskan Akun Google?", fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "Koneksi ke akun ${settingsState.googleAccountEmail} dan spreadsheet cadangan akan diputus. Seluruh data transaksi, kas, dan produk di perangkat ini tetap aman dan tidak akan terhapus.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.TextPrimary
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showDisconnectGoogleDialog = false
                            bViewModel.disconnectGoogleAccount()
                            Toast.makeText(context, "Akun Google berhasil diputuskan", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.RedExpense)
                    ) {
                        Text("Putuskan", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDisconnectGoogleDialog = false }) {
                        Text("Batal")
                    }
                }
            )
        }

        if (showManualEmailDialog) {
            AlertDialog(
                onDismissRequest = { showManualEmailDialog = false },
                title = { Text("Hubungkan Akun Google", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                        Text(
                            "Masukkan email akun Google untuk pencadangan warung:",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.TextSecondary
                        )
                        AppTextField(
                            value = manualEmailInput,
                            onValueChange = { manualEmailInput = it },
                            label = "Email Google (nama@gmail.com)"
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val email = manualEmailInput.trim()
                            if (email.contains("@") && email.contains(".")) {
                                showManualEmailDialog = false
                                bViewModel.connectGoogleAccount(email)
                                Toast.makeText(context, "Akun Google terhubung: $email", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Format email tidak valid", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.GreenPrimary)
                    ) {
                        Text("Simpan")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showManualEmailDialog = false }) {
                        Text("Batal")
                    }
                }
            )
        }

        if (showQrisCameraDialog) {
            CameraQrisPhotoDialog(
                onDismiss = { showQrisCameraDialog = false },
                onPhotoCaptured = { photoPath ->
                    showQrisCameraDialog = false
                    val file = File(photoPath)
                    if (file.exists() && file.length() > 0) {
                        scope.launch {
                            val bitmap = BitmapFactory.decodeFile(photoPath)
                            if (bitmap != null) {
                                val result = prefsRepo.saveQrisImageBitmap(bitmap)
                                if (result.isSuccess) {
                                    file.delete()
                                    Toast.makeText(context, "Foto QRIS warung berhasil disimpan", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, result.exceptionOrNull()?.localizedMessage ?: "Gagal menyimpan QRIS", Toast.LENGTH_LONG).show()
                                }
                            } else {
                                Toast.makeText(context, "QRIS tidak dapat digunakan. Pastikan foto QRIS terlihat jelas dan tidak terpotong.", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            )
        }

        if (showQrisChoiceDialog) {
            AlertDialog(
                onDismissRequest = { showQrisChoiceDialog = false },
                title = { Text("Ganti QRIS Warung", fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "Pilih sumber foto QRIS resmi merchant Anda:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.TextPrimary
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showQrisChoiceDialog = false
                            showQrisCameraDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.GreenPrimary)
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Ambil Foto")
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = {
                            showQrisChoiceDialog = false
                            qrisImagePickerLauncher.launch("image/*")
                        }
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Dari Galeri")
                    }
                }
            )
        }

        if (showDeleteQrisConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteQrisConfirmDialog = false },
                title = { Text("Hapus QRIS Warung?", fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "Foto QRIS warung akan dihapus dari aplikasi. Pelanggan tidak dapat memindai QRIS sampai Anda mengunggah QRIS baru.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.TextPrimary
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteQrisConfirmDialog = false
                            scope.launch {
                                prefsRepo.deleteQrisImage()
                                Toast.makeText(context, "QRIS warung berhasil dihapus", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.RedExpense)
                    ) {
                        Text("Hapus", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteQrisConfirmDialog = false }) {
                        Text("Batal")
                    }
                }
            )
        }

        if (showBusinessProfileDialog) {
            ChangeBusinessProfileDialog(
                currentPrimaryType = settingsState.primaryBusinessType,
                currentSecondaryActivities = settingsState.secondaryActivities,
                onDismiss = { showBusinessProfileDialog = false },
                onSave = { selectedType, selectedActivities ->
                    showBusinessProfileDialog = false
                    scope.launch {
                        prefsRepo.updateBusinessProfile(
                            primaryType = selectedType.id,
                            secondaryActivities = selectedActivities.map { it.id }.toSet()
                        )
                        Toast.makeText(context, "Tipe usaha diperbarui: ${selectedType.displayName}", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChangeBusinessProfileDialog(
    currentPrimaryType: String,
    currentSecondaryActivities: Set<String>,
    onDismiss: () -> Unit,
    onSave: (BusinessType, Set<BusinessActivity>) -> Unit
) {
    var selectedCategory by remember { mutableStateOf<BusinessCategory?>(null) }
    var selectedType by remember {
        mutableStateOf(
            BusinessType.fromId(currentPrimaryType) ?: BusinessTaxonomyRegistry.DEFAULT_BUSINESS_TYPE
        )
    }
    var selectedActivities by remember {
        mutableStateOf(
            currentSecondaryActivities.mapNotNull { BusinessActivity.fromId(it) }.toSet().ifEmpty {
                BusinessTaxonomyRegistry.getPreset(selectedType).defaultActivities
            }
        )
    }

    val availableTypes = remember(selectedCategory) {
        if (selectedCategory != null) {
            BusinessTaxonomyRegistry.getBusinessTypesByCategory(selectedCategory!!)
        } else {
            BusinessType.entries
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = 680.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Ubah Tipe & Model Usaha",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = AppColors.TextPrimary,
                                fontSize = 16.sp
                            )
                        )
                        Text(
                            text = "Sesuaikan terminologi & model operasional",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = AppColors.TextSecondary,
                                fontSize = 11.5.sp
                            )
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Scrollable Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Category Filter Chips
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "PILIH KATEGORI",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = AppColors.GreenPrimary,
                                fontSize = 10.5.sp,
                                letterSpacing = 0.5.sp
                            )
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = selectedCategory == null,
                                onClick = { selectedCategory = null },
                                label = { Text("Semua (19)", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AppColors.GreenPrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                            BusinessCategory.entries.forEach { cat ->
                                FilterChip(
                                    selected = selectedCategory == cat,
                                    onClick = { selectedCategory = cat },
                                    label = { Text(cat.displayName, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AppColors.GreenPrimary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }

                    // Business Type Presets List
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "PILIH PRESET USAHA",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = AppColors.GreenPrimary,
                                fontSize = 10.5.sp,
                                letterSpacing = 0.5.sp
                            )
                        )

                        availableTypes.forEach { type ->
                            val isSelected = selectedType == type
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) Color(0xFFF0FDF4) else Color(0xFFF8FAF8),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) AppColors.GreenPrimary else Color(0xFFE2E8F0)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedType = type
                                        // Auto-load preset default activities when switching primary type
                                        val preset = BusinessTaxonomyRegistry.getPreset(type)
                                        selectedActivities = preset.defaultActivities
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = {
                                            selectedType = type
                                            val preset = BusinessTaxonomyRegistry.getPreset(type)
                                            selectedActivities = preset.defaultActivities
                                        },
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = AppColors.GreenPrimary
                                        ),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = type.displayName,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            color = if (isSelected) AppColors.GreenDark else AppColors.TextPrimary
                                        )
                                        Text(
                                            text = type.description,
                                            fontSize = 11.sp,
                                            color = AppColors.TextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFFEFF3F0))

                    // Secondary Activities Customization
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "AKTIVITAS OPERASIONAL & FITUR TAMBAHAN",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = AppColors.GreenPrimary,
                                fontSize = 10.5.sp,
                                letterSpacing = 0.5.sp
                            )
                        )
                        Text(
                            text = "Pilih aktivitas operasional yang dijalankan bisnis Anda:",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                color = AppColors.TextSecondary
                            )
                        )

                        BusinessActivity.entries.forEach { activity ->
                            val isChecked = selectedActivities.contains(activity)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isChecked) Color(0xFFF9FCF9) else Color.White,
                                border = BorderStroke(
                                    0.5.dp,
                                    if (isChecked) AppColors.GreenPrimary.copy(alpha = 0.5f) else Color(0xFFE5E7EB)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedActivities = if (isChecked) {
                                            selectedActivities - activity
                                        } else {
                                            selectedActivities + activity
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            selectedActivities = if (checked) {
                                                selectedActivities + activity
                                            } else {
                                                selectedActivities - activity
                                            }
                                        },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = AppColors.GreenPrimary
                                        ),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = activity.displayName,
                                            fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Normal,
                                            fontSize = 12.sp,
                                            color = AppColors.TextPrimary
                                        )
                                        Text(
                                            text = activity.description,
                                            fontSize = 10.5.sp,
                                            color = AppColors.TextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Batal", fontSize = 12.5.sp)
                    }
                    Button(
                        onClick = {
                            onSave(selectedType, selectedActivities)
                        },
                        modifier = Modifier.weight(1.5f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.GreenPrimary)
                    ) {
                        Text("Simpan Tipe Usaha", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    }
                }
            }
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
