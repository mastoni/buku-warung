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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.skmnetwork.bukuwarung.BuildConfig
import id.skmnetwork.bukuwarung.data.preferences.UserPreferencesRepository
import id.skmnetwork.bukuwarung.data.preferences.UserSettings
import id.skmnetwork.bukuwarung.domain.business.BusinessActivity
import id.skmnetwork.bukuwarung.domain.business.BusinessCategory
import id.skmnetwork.bukuwarung.domain.business.BusinessTaxonomyRegistry
import id.skmnetwork.bukuwarung.domain.business.BusinessType
import id.skmnetwork.bukuwarung.domain.tax.TaxApplicability
import id.skmnetwork.bukuwarung.domain.tax.TaxPriceMode
import id.skmnetwork.bukuwarung.license.LicenseManager
import id.skmnetwork.bukuwarung.license.LicensePhase
import id.skmnetwork.bukuwarung.license.ValidationResult
import id.skmnetwork.bukuwarung.ui.components.AppErrorBanner
import id.skmnetwork.bukuwarung.ui.components.AppTextField
import id.skmnetwork.bukuwarung.ui.components.AppTouchTarget
import id.skmnetwork.bukuwarung.ui.components.ButtonDestructive
import id.skmnetwork.bukuwarung.ui.components.ButtonGhost
import id.skmnetwork.bukuwarung.ui.components.ButtonNeutral
import id.skmnetwork.bukuwarung.ui.components.ButtonPrimary
import id.skmnetwork.bukuwarung.ui.components.CameraQrisPhotoDialog
import id.skmnetwork.bukuwarung.ui.components.SectionCard
import id.skmnetwork.bukuwarung.ui.components.SectionHeader
import id.skmnetwork.bukuwarung.ui.components.StatusBadge
import id.skmnetwork.bukuwarung.ui.components.StatusBadgeVariant
import id.skmnetwork.bukuwarung.ui.theme.AppColors
import id.skmnetwork.bukuwarung.ui.theme.AppShapes
import id.skmnetwork.bukuwarung.ui.theme.AppSpacing
import id.skmnetwork.bukuwarung.ui.theme.AppTypeScale
import id.skmnetwork.bukuwarung.ui.theme.rememberAppWindowSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val PRIVACY_POLICY_URL = "https://bukuwarung.skmnetwork.com/privacy-policy"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    /**
     * Gate H.4.2-CODE-FIX.1 - REQUIRED, no default.
     */
    backupViewModel: BackupViewModel,
    userPreferencesRepository: UserPreferencesRepository? = null,
    licenseManager: LicenseManager? = null,
    printerService: id.skmnetwork.bukuwarung.printer.PrinterService? = null
) {
    val context = LocalContext.current
    val windowSize = rememberAppWindowSize()
    val scope = rememberCoroutineScope()
    val prefsRepo = remember { userPreferencesRepository ?: UserPreferencesRepository(context) }
    val licManager = remember { licenseManager ?: LicenseManager(prefsRepo) }
    val fallbackPrinterService = remember { id.skmnetwork.bukuwarung.printer.PrinterService() }
    val activePrinterService = printerService ?: fallbackPrinterService

    val bViewModel: BackupViewModel = backupViewModel

    val backupState by bViewModel.backupState.collectAsStateWithLifecycle()
    val restoreState by bViewModel.restoreState.collectAsStateWithLifecycle()

    val settingsState by prefsRepo.userSettings.collectAsStateWithLifecycle(initialValue = UserSettings())
    val licenseState by licManager.licenseState.collectAsStateWithLifecycle()
    val licenseTier by licManager.licenseTier.collectAsStateWithLifecycle()

    val resolvedProfile = remember(settingsState.primaryBusinessType, settingsState.secondaryActivities) {
        BusinessTaxonomyRegistry.resolve(
            primaryType = settingsState.primaryBusinessType,
            secondaryActivities = settingsState.secondaryActivities
        )
    }

    var showBusinessTypeDialog by remember { mutableStateOf(false) }
    var isSavingBusinessType by remember { mutableStateOf(false) }
    var businessTypeError by remember { mutableStateOf<String?>(null) }
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

    // 1.5 Pajak / PPN
    var taxEnabled by remember { mutableStateOf(false) }
    var taxRateInput by remember { mutableStateOf("") }
    var taxPriceMode by remember { mutableStateOf(TaxPriceMode.EXCLUSIVE.name) }
    var taxApplicability by remember { mutableStateOf(TaxApplicability.GLOBAL.name) }
    var taxRoundingMode by remember { mutableStateOf("HALF_UP") }

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
    var themeMode by remember { mutableStateOf("LIGHT") }

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

        taxEnabled = settingsState.taxEnabled
        taxRateInput = if (settingsState.taxRate > 0.0) settingsState.taxRate.toString() else ""
        taxPriceMode = settingsState.taxPriceMode.name
        taxApplicability = settingsState.taxApplicability.name
        taxRoundingMode = settingsState.taxRoundingMode
    }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 0.5.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
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
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Pengaturan",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.5.sp,
                                color = AppColors.TextPrimary
                            ),
                            maxLines = 1
                        )
                        Text(
                            text = "Atur profil dan preferensi usaha",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.5.sp,
                                color = AppColors.TextSecondary
                            ),
                            maxLines = 1
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = windowSize.contentMaxWidth)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ==========================================
                // 0. TIPE USAHA & MODEL OPERASIONAL
                // ==========================================
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionHeader(
                        title = "TIPE USAHA & MODEL OPERASIONAL",
                        modifier = Modifier.padding(top = AppSpacing.xs)
                    )
                    SectionCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
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
                                            .background(MaterialTheme.colorScheme.primaryContainer),
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
                                                color = AppColors.TextPrimary
                                            )
                                        )
                                        Text(
                                            text = "Kategori: ${resolvedProfile.businessType.category.displayName}",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = AppColors.TextSecondary
                                            )
                                        )
                                    }
                                }
                            }

                            Text(
                                text = resolvedProfile.businessType.description,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = AppColors.TextSecondary
                                )
                            )

                            Spacer(Modifier.height(4.dp))
                            ButtonNeutral(
                                text = "Ubah Jenis Usaha",
                                onClick = { showBusinessTypeDialog = true },
                                fillMaxWidth = true
                            )

                            if (resolvedProfile.activities.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "Fitur & Aktivitas Operasional Aktif:",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = AppColors.TextPrimary
                                        )
                                    )
                                    Text(
                                        text = resolvedProfile.activities.joinToString(", ") { it.displayName },
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = AppColors.GreenDark,
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                }
                            }

                            if (!settingsState.isSetupCompleted) {
                                Spacer(Modifier.height(2.dp))
                                ButtonNeutral(
                                    text = "Ubah Tipe & Model Usaha",
                                    onClick = { showBusinessProfileDialog = true },
                                    leadingIcon = Icons.Default.Edit,
                                    fillMaxWidth = true
                                )
                            }
                        }
                    }
                }

                // ==========================================
                // 1. PROFIL USAHA
                // ==========================================
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionHeader(
                        title = "PROFIL ${shopLabel.uppercase()}",
                        modifier = Modifier.padding(top = AppSpacing.xs)
                    )
                    SectionCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
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
                            ButtonPrimary(
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
                // 1.5 PAJAK / PPN
                // ==========================================
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionHeader(
                        title = "PAJAK / PPN",
                        modifier = Modifier.padding(top = AppSpacing.xs)
                    )
                    SectionCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SettingSwitchRow("Aktifkan Pajak / PPN", taxEnabled) {
                                taxEnabled = it
                                scope.launch {
                                    prefsRepo.setTaxEnabled(it)
                                }
                            }
                            if (taxEnabled) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                AppTextField(
                                    value = taxRateInput,
                                    onValueChange = { taxRateInput = it },
                                    label = "Tarif PPN (%)",
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Text(
                                    text = "Mode Harga: ${if (taxPriceMode == TaxPriceMode.EXCLUSIVE.name) "Exclusive (PPN ditambah)" else "Inclusive (PPN sudah termasuk)"}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = AppTouchTarget)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(AppShapes.ChipShape)
                                            .clickable {
                                                taxPriceMode = TaxPriceMode.EXCLUSIVE.name
                                                scope.launch {
                                                    prefsRepo.updateTaxSettings(
                                                        enabled = taxEnabled,
                                                        rate = taxRateInput.toDoubleOrNull() ?: 0.0,
                                                        priceMode = TaxPriceMode.EXCLUSIVE,
                                                        applicability = TaxApplicability.GLOBAL,
                                                        roundingMode = taxRoundingMode
                                                    )
                                                }
                                            }
                                            .padding(vertical = 4.dp, horizontal = 4.dp)
                                    ) {
                                        RadioButton(
                                            selected = taxPriceMode == TaxPriceMode.EXCLUSIVE.name,
                                            onClick = {
                                                taxPriceMode = TaxPriceMode.EXCLUSIVE.name
                                                scope.launch {
                                                    prefsRepo.updateTaxSettings(
                                                        enabled = taxEnabled,
                                                        rate = taxRateInput.toDoubleOrNull() ?: 0.0,
                                                        priceMode = TaxPriceMode.EXCLUSIVE,
                                                        applicability = TaxApplicability.GLOBAL,
                                                        roundingMode = taxRoundingMode
                                                    )
                                                }
                                            },
                                            colors = RadioButtonDefaults.colors(selectedColor = AppColors.GreenPrimary)
                                        )
                                        Text("Exclusive", modifier = Modifier.padding(start = 4.dp), style = MaterialTheme.typography.bodyMedium)
                                    }
                                    Spacer(Modifier.width(16.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(AppShapes.ChipShape)
                                            .clickable {
                                                taxPriceMode = TaxPriceMode.INCLUSIVE.name
                                                scope.launch {
                                                    prefsRepo.updateTaxSettings(
                                                        enabled = taxEnabled,
                                                        rate = taxRateInput.toDoubleOrNull() ?: 0.0,
                                                        priceMode = TaxPriceMode.INCLUSIVE,
                                                        applicability = TaxApplicability.GLOBAL,
                                                        roundingMode = taxRoundingMode
                                                    )
                                                }
                                            }
                                            .padding(vertical = 4.dp, horizontal = 4.dp)
                                    ) {
                                        RadioButton(
                                            selected = taxPriceMode == TaxPriceMode.INCLUSIVE.name,
                                            onClick = {
                                                taxPriceMode = TaxPriceMode.INCLUSIVE.name
                                                scope.launch {
                                                    prefsRepo.updateTaxSettings(
                                                        enabled = taxEnabled,
                                                        rate = taxRateInput.toDoubleOrNull() ?: 0.0,
                                                        priceMode = TaxPriceMode.INCLUSIVE,
                                                        applicability = TaxApplicability.GLOBAL,
                                                        roundingMode = taxRoundingMode
                                                    )
                                                }
                                            },
                                            colors = RadioButtonDefaults.colors(selectedColor = AppColors.GreenPrimary)
                                        )
                                        Text("Inclusive", modifier = Modifier.padding(start = 4.dp), style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }
                }

                // ==========================================
                // 2. PENJUALAN & KASIR (POS)
                // ==========================================
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionHeader(
                        title = "${transactionLabel.uppercase()} & KASIR",
                        modifier = Modifier.padding(top = AppSpacing.xs)
                    )
                    SectionCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SettingSwitchRow("Tampilkan Foto $productLabel di Kasir", showProductImage) {
                                showProductImage = it
                                scope.launch {
                                    prefsRepo.updatePosSettings(showProductImage, showStock, showBarcode, confirmCheckout)
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            SettingSwitchRow("Tampilkan Jumlah $stockLabel di Kasir", showStock) {
                                showStock = it
                                scope.launch {
                                    prefsRepo.updatePosSettings(showProductImage, showStock, showBarcode, confirmCheckout)
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            SettingSwitchRow("Tampilkan Tombol Scan Barcode", showBarcode) {
                                showBarcode = it
                                scope.launch {
                                    prefsRepo.updatePosSettings(showProductImage, showStock, showBarcode, confirmCheckout)
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
                    SectionHeader(
                        title = "METODE PEMBAYARAN",
                        modifier = Modifier.padding(top = AppSpacing.xs)
                    )
                    SectionCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
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
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
                    SectionHeader(
                        title = "QRIS STATIS ${shopLabel.uppercase()}",
                        modifier = Modifier.padding(top = AppSpacing.xs)
                    )
                    SectionCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
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
                                    style = MaterialTheme.typography.bodyMedium.copy(color = AppColors.TextPrimary)
                                )
                                StatusBadge(
                                    label = if (isConfigured) "Sudah Diatur" else "Belum Diatur",
                                    variant = if (isConfigured) StatusBadgeVariant.Success else StatusBadgeVariant.Warning
                                )
                            }

                            if (isConfigured) {
                                Text(
                                    text = "QRIS resmi $shopLabel Anda aktif dan akan ditampilkan saat checkout QRIS di kasir.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppColors.TextSecondary
                                )

                                // QRIS Image Preview Card
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(AppShapes.CardShape)
                                        .background(MaterialTheme.colorScheme.surface)
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, AppShapes.CardShape)
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
                                    ButtonNeutral(
                                        text = "Ganti QRIS",
                                        onClick = { showQrisChoiceDialog = true },
                                        modifier = Modifier.weight(1f)
                                    )
                                    ButtonNeutral(
                                        text = "Hapus QRIS",
                                        onClick = { showDeleteQrisConfirmDialog = true },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            } else {
                                Text(
                                    text = "Unggah atau foto QRIS resmi merchant Anda agar pelanggan dapat memindai langsung dari aplikasi kasir.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppColors.TextSecondary
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    ButtonPrimary(
                                        text = "Ambil Foto",
                                        onClick = { showQrisCameraDialog = true },
                                        leadingIcon = Icons.Default.PhotoCamera,
                                        modifier = Modifier.weight(1f)
                                    )
                                    ButtonNeutral(
                                        text = "Pilih Galeri",
                                        onClick = { qrisImagePickerLauncher.launch("image/*") },
                                        leadingIcon = Icons.Default.PhotoLibrary,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                // ==========================================
                // 5. STOK & PERINGATAN
                // ==========================================
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionHeader(
                        title = "${stockLabel.uppercase()} & PERINGATAN",
                        modifier = Modifier.padding(top = AppSpacing.xs)
                    )
                    SectionCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
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
                    SectionHeader(
                        title = "PRINTER & ${receiptLabel.uppercase()}",
                        modifier = Modifier.padding(top = AppSpacing.xs)
                    )
                    SectionCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = AppShapes.CardShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                                            style = MaterialTheme.typography.bodyMedium.copy(color = AppColors.TextPrimary)
                                        )
                                        Text(
                                            text = when {
                                                activePrinterService.isConnected -> "Terhubung (${settingsState.printerDeviceName.ifBlank { settingsState.printerAddress }})"
                                                settingsState.printerType != "NONE" && settingsState.printerAddress.isNotBlank() -> "Tersimpan: ${settingsState.printerDeviceName.ifBlank { settingsState.printerAddress }} (Tidak terhubung)"
                                                else -> "Belum dikonfigurasi"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = when {
                                                activePrinterService.isConnected -> AppColors.GreenPrimary
                                                settingsState.printerType != "NONE" && settingsState.printerAddress.isNotBlank() -> AppColors.TextSecondary
                                                else -> AppColors.RedExpense
                                            }
                                        )
                                        if (settingsState.printerType != "NONE" && settingsState.printerAddress.isNotBlank()) {
                                            Text(
                                                text = "${settingsState.printerType} | Lebar: ${settingsState.printerPaperWidth}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = AppColors.TextSecondary
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    ButtonPrimary(
                                        text = "Atur Printer",
                                        onClick = { showPrinterSettingsDialog = true },
                                        fillMaxWidth = false
                                    )
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

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
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
                    SectionHeader(
                        title = "NOTIFIKASI & PENGINGAT",
                        modifier = Modifier.padding(top = AppSpacing.xs)
                    )
                    SectionCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
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
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
                    SectionHeader(
                        title = "KEAMANAN & PIN PEMILIK",
                        modifier = Modifier.padding(top = AppSpacing.xs)
                    )
                    SectionCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
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
                                    text = "Status: PIN Terpasang (disimpan sebagai hash dengan salt)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AppColors.GreenPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ButtonNeutral(
                                        text = "Ganti PIN",
                                        onClick = { showSetPinDialog = true },
                                        modifier = Modifier.weight(1f)
                                    )
                                    ButtonNeutral(
                                        text = "Hapus PIN",
                                        onClick = {
                                            scope.launch {
                                                prefsRepo.clearPin()
                                                Toast.makeText(context, "PIN pemilik dinonaktifkan", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
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
                }

                // ==========================================
                // 9. CADANGAN & PEMULIHAN (GOOGLE SHEETS)
                // ==========================================
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionHeader(
                        title = "CADANGAN & PEMULIHAN",
                        modifier = Modifier.padding(top = AppSpacing.xs)
                    )
                    SectionCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Amankan data $shopLabel Anda di Google Sheets.",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = AppColors.TextPrimary
                            )
                            Text(
                                text = "Backup bersifat opsional dan MANUAL. Pencadangan hanya berjalan saat Anda menekan \"Cadangkan Sekarang\", bukan otomatis. Data yang ikut disalin mencakup $productLabel, $customerLabel, supplier, transaksi penjualan dan pembelian, kas, hutang piutang, hutang supplier, serta catatan bebas pada kolom keterangan. Spreadsheet dibuat di Google Drive akun Google Anda sendiri. PIN dan kode lisensi tidak pernah disertakan.",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.TextSecondary
                            )

                            // 1. Akun Google Box
                            Surface(
                                shape = AppShapes.CardShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Akun Google",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = AppColors.TextPrimary
                                        )
                                        when (val authState = googleAuthState) {
                                            is id.skmnetwork.bukuwarung.backup.transport.GoogleAuthConnectionState.Connected -> {
                                                Text(
                                                    text = "🟢 Terhubung",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = AppColors.GreenPrimary
                                                )
                                            }
                                            is id.skmnetwork.bukuwarung.backup.transport.GoogleAuthConnectionState.Authorizing -> {
                                                Text(
                                                    text = "⏳ Menghubungkan...",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Medium,
                                                    color = AppColors.GreenDark
                                                )
                                            }
                                            is id.skmnetwork.bukuwarung.backup.transport.GoogleAuthConnectionState.AuthorizationRequired -> {
                                                Text(
                                                    text = "🟡 Perlu Otorisasi Ulang",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = AppColors.OrangeWarning
                                                )
                                            }
                                            else -> {
                                                Text(
                                                    text = "⚪ Belum terhubung",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = AppColors.TextSecondary
                                                )
                                            }
                                        }
                                    }

                                    when (val authState = googleAuthState) {
                                        is id.skmnetwork.bukuwarung.backup.transport.GoogleAuthConnectionState.Connected -> {
                                            Text(
                                                text = authState.email,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                color = AppColors.TextPrimary
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                ButtonNeutral(
                                                    text = "Ganti Akun",
                                                    onClick = { launchGoogleAccountPicker() },
                                                    modifier = Modifier.weight(1f)
                                                )
                                                ButtonNeutral(
                                                    text = "Putuskan",
                                                    onClick = { showDisconnectGoogleDialog = true },
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                        is id.skmnetwork.bukuwarung.backup.transport.GoogleAuthConnectionState.Authorizing -> {
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
                                                    text = "Memverifikasi otorisasi akun Google...",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = AppColors.TextSecondary
                                                )
                                            }
                                        }
                                        is id.skmnetwork.bukuwarung.backup.transport.GoogleAuthConnectionState.AuthorizationRequired -> {
                                            Text(
                                                text = "Akun: ${authState.email}\nIzin akses Google Sheets / Drive perlu diperbarui.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = AppColors.TextSecondary
                                            )
                                            ButtonPrimary(
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
                                                style = MaterialTheme.typography.bodySmall,
                                                color = AppColors.RedExpense
                                            )
                                            ButtonPrimary(
                                                text = "Hubungkan Akun Google",
                                                onClick = { launchGoogleAccountPicker() }
                                            )
                                        }
                                        is id.skmnetwork.bukuwarung.backup.transport.GoogleAuthConnectionState.Disconnected -> {
                                            Text(
                                                text = "Hubungkan akun Google untuk menyimpan cadangan data warung Anda.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = AppColors.TextSecondary
                                            )
                                            ButtonPrimary(
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
                                    shape = AppShapes.CardShape,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = AppColors.TextPrimary
                                            )
                                            if (settingsState.backupSpreadsheetId.isNotBlank()) {
                                                Text(
                                                    text = "Tersimpan",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Medium,
                                                    color = AppColors.GreenDark
                                                )
                                            }
                                        }

                                        Text(
                                            text = "📊 $sheetTitle",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = AppColors.TextPrimary
                                        )

                                        if (settingsState.backupSpreadsheetId.isNotBlank()) {
                                            ButtonNeutral(
                                                text = "Buka Spreadsheet ↗",
                                                onClick = {
                                                    try {
                                                        val uri = Uri.parse(bViewModel.getSpreadsheetWebUrl(settingsState.backupSpreadsheetId))
                                                        val intent = Intent(Intent.ACTION_VIEW, uri)
                                                        context.startActivity(intent)
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "Tidak dapat membuka browser", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                fillMaxWidth = true
                                            )
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
                                            style = MaterialTheme.typography.bodySmall,
                                            color = AppColors.GreenPrimary
                                        )
                                    }
                                }
                                is BackupOpState.Success -> {
                                    Surface(
                                        shape = AppShapes.CardShape,
                                        color = AppColors.SuccessContainer,
                                        border = BorderStroke(1.dp, AppColors.Success),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = state.message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = AppColors.OnSuccessContainer,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }
                                is BackupOpState.Error -> {
                                    AppErrorBanner(message = state.message)
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
                                            style = MaterialTheme.typography.bodySmall,
                                            color = AppColors.GreenPrimary
                                        )
                                    }
                                }
                                is RestoreOpState.Success -> {
                                    Surface(
                                        shape = AppShapes.CardShape,
                                        color = AppColors.SuccessContainer,
                                        border = BorderStroke(1.dp, AppColors.Success),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = state.message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = AppColors.OnSuccessContainer,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }
                                is RestoreOpState.Error -> {
                                    AppErrorBanner(message = state.message)
                                }
                                else -> {}
                            }

                            val isBusy = backupState is BackupOpState.Loading || restoreState is RestoreOpState.Loading

                            ButtonPrimary(
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

                            ButtonNeutral(
                                text = if (restoreState is RestoreOpState.Loading) "Memulihkan..." else "Pulihkan Data",
                                onClick = {
                                    bViewModel.resetBackupState()
                                    if (settingsState.googleAccountEmail.isBlank()) {
                                        launchGoogleAccountPicker()
                                    } else {
                                        showRestoreConfirmDialog = true
                                    }
                                },
                                enabled = !isBusy,
                                fillMaxWidth = true
                            )
                        }
                    }
                }

                // ==========================================
                // 10. LISENSI APLIKASI
                // ==========================================
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionHeader(
                        title = "LISENSI APLIKASI",
                        modifier = Modifier.padding(top = AppSpacing.xs)
                    )
                    Surface(
                        shape = AppShapes.CardShape,
                        color = if (licenseState.grantsAccess) AppColors.SuccessContainer else AppColors.WarningContainer,
                        border = BorderStroke(1.dp, if (licenseState.grantsAccess) AppColors.Success else AppColors.OrangeWarning),
                        shadowElevation = 0.5.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = licenseTier.label,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (licenseState.grantsAccess) AppColors.OnSuccessContainer else AppColors.OnWarningContainer
                            )
                            Text(
                                text = "Status Lisensi: " + when (licenseState.phase) {
                                    LicensePhase.FRESH_ACTIVE -> "AKTIF"
                                    LicensePhase.STALE_ACTIVE -> "Aktif (perlu koneksi)"
                                    LicensePhase.TRANSIENT_ERROR -> "Tidak dapat menghubungi server (status lokal tetap berlaku)"
                                    LicensePhase.UNLICENSED -> "Belum Aktif (UNLICENSED)"
                                    LicensePhase.BLOCKED -> "Diblokir (${licenseState.blockReason})"
                                    LicensePhase.CHECKING -> "Memeriksa..."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.TextSecondary
                            )
                            Text(
                                text = "Model: Sekali Bayar Offline-First (Tanpa Langganan)",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.TextSecondary
                            )
                            Spacer(Modifier.height(4.dp))
                            ButtonPrimary(
                                text = if (isCheckingLicense) "Memverifikasi..." else "Periksa / Pulihkan Lisensi",
                                enabled = !isCheckingLicense,
                                onClick = {
                                    scope.launch {
                                        isCheckingLicense = true
                                        try {
                                            val result = licManager.validateOnline()
                                            val message = when (result) {
                                                is ValidationResult.Valid -> "Lisensi valid dan aktif"
                                                is ValidationResult.CredentialUnavailable -> result.message
                                                is ValidationResult.Transient ->
                                                    "Tidak dapat menghubungi server lisensi. Status lokal dan kode lisensi Anda tetap tersimpan."
                                                is ValidationResult.EmailMismatch -> result.message
                                                is ValidationResult.DeviceMismatch -> result.message
                                                is ValidationResult.Revoked -> result.message
                                                is ValidationResult.Invalid -> result.message
                                            }
                                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (_: Exception) {
                                            Toast.makeText(
                                                context,
                                                "Gagal memeriksa lisensi. Silakan coba lagi.",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        } finally {
                                            isCheckingLicense = false
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                // ==========================================
                // 11. TAMPILAN & TEMA (THEME)
                // ==========================================
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionHeader(
                        title = "TAMPILAN & TEMA",
                        modifier = Modifier.padding(top = AppSpacing.xs)
                    )
                    SectionCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Tema Tampilan Aplikasi",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Pilih tema warna yang nyaman untuk operasional kasir & pembukuan.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            val currentTheme = settingsState.themeMode
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    Triple("LIGHT", "☀ Terang", "Terang"),
                                    Triple("DARK", "🌙 Gelap", "Gelap"),
                                    Triple("SYSTEM", "⚙ Sistem", "Sistem")
                                ).forEach { (mode, label, _) ->
                                    val selected = currentTheme == mode
                                    Surface(
                                        shape = AppShapes.ChipShape,
                                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                        border = BorderStroke(
                                            1.dp,
                                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = AppTouchTarget)
                                            .clip(AppShapes.ChipShape)
                                            .clickable {
                                                scope.launch {
                                                    prefsRepo.updateAppearanceSettings(
                                                        themeMode = mode,
                                                        productViewMode = settingsState.productViewMode
                                                    )
                                                }
                                            }
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ==========================================
                // 12. TENTANG APLIKASI
                // ==========================================
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionHeader(
                        title = "TENTANG APLIKASI",
                        modifier = Modifier.padding(top = AppSpacing.xs)
                    )
                    SectionCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Buku Warung v${BuildConfig.VERSION_NAME}",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                                color = AppColors.TextPrimary
                            )
                            Text(
                                text = "Aplikasi kasir dan pembukuan sederhana untuk usaha kecil.",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.TextSecondary
                            )
                            Text(
                                text = "Data operasional usaha terutama disimpan secara lokal di perangkat. Data tertentu untuk lisensi, pembelian, dan dukungan dapat diproses oleh SKMNetwork.",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.TextSecondary
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .clip(AppShapes.ChipShape)
                                    .clickable {
                                        val privacyUri = Uri.parse(PRIVACY_POLICY_URL)
                                        runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, privacyUri)
                                            )
                                        }.onFailure {
                                            Toast.makeText(
                                                context,
                                                "Tidak dapat membuka browser. Alamat: $PRIVACY_POLICY_URL",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                    .padding(vertical = 8.dp, horizontal = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.VerifiedUser,
                                    contentDescription = null,
                                    tint = AppColors.GreenPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Kebijakan Privasi",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = AppColors.GreenPrimary
                                    )
                                    Text(
                                        text = PRIVACY_POLICY_URL,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AppColors.TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(88.dp))
            }
        }
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
            shape = AppShapes.CardShape,
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Buat PIN Pemilik (4 Angka)", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                ) {
                    Text(
                        "PIN digunakan untuk mengamankan akses kasir & pembukuan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextSecondary
                    )

                    if (pinDialogError != null) {
                        AppErrorBanner(message = pinDialogError!!)
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
                ButtonPrimary(
                    text = "Simpan PIN",
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
                    fillMaxWidth = false
                )
            },
            dismissButton = {
                ButtonGhost(
                    text = "Batal",
                    onClick = {
                        showSetPinDialog = false
                        newPinInput = ""
                        confirmPinInput = ""
                        pinDialogError = null
                    }
                )
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
            shape = AppShapes.CardShape,
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Pulihkan Data Cadangan?", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                ) {
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
                ButtonPrimary(
                    text = "Pulihkan",
                    onClick = {
                        showRestoreConfirmDialog = false
                        bViewModel.performRestore()
                    },
                    fillMaxWidth = false
                )
            },
            dismissButton = {
                ButtonGhost(
                    text = "Batal",
                    onClick = { showRestoreConfirmDialog = false }
                )
            }
        )
    }

    if (showDisconnectGoogleDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectGoogleDialog = false },
            shape = AppShapes.CardShape,
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Putuskan Akun Google?", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
            text = {
                Text(
                    "Koneksi ke akun ${settingsState.googleAccountEmail} dan spreadsheet cadangan akan diputus. Seluruh data transaksi, kas, dan produk di perangkat ini tetap aman dan tidak akan terhapus.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextPrimary
                )
            },
            confirmButton = {
                ButtonDestructive(
                    text = "Putuskan",
                    onClick = {
                        showDisconnectGoogleDialog = false
                        bViewModel.disconnectGoogleAccount()
                        Toast.makeText(context, "Akun Google berhasil diputuskan", Toast.LENGTH_SHORT).show()
                    },
                    fillMaxWidth = false
                )
            },
            dismissButton = {
                ButtonGhost(
                    text = "Batal",
                    onClick = { showDisconnectGoogleDialog = false }
                )
            }
        )
    }

    if (showManualEmailDialog) {
        AlertDialog(
            onDismissRequest = { showManualEmailDialog = false },
            shape = AppShapes.CardShape,
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Hubungkan Akun Google", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                ) {
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
                ButtonPrimary(
                    text = "Simpan",
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
                    fillMaxWidth = false
                )
            },
            dismissButton = {
                ButtonGhost(
                    text = "Batal",
                    onClick = { showManualEmailDialog = false }
                )
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
            shape = AppShapes.CardShape,
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Ganti QRIS Warung", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
            text = {
                Text(
                    "Pilih sumber foto QRIS resmi merchant Anda:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextPrimary
                )
            },
            confirmButton = {
                ButtonPrimary(
                    text = "Ambil Foto",
                    onClick = {
                        showQrisChoiceDialog = false
                        showQrisCameraDialog = true
                    },
                    leadingIcon = Icons.Default.PhotoCamera,
                    fillMaxWidth = false
                )
            },
            dismissButton = {
                ButtonNeutral(
                    text = "Dari Galeri",
                    onClick = {
                        showQrisChoiceDialog = false
                        qrisImagePickerLauncher.launch("image/*")
                    },
                    leadingIcon = Icons.Default.PhotoLibrary,
                    fillMaxWidth = false
                )
            }
        )
    }

    if (showBusinessTypeDialog) {
        BusinessTypeDialog(
            currentBusinessType = settingsState.primaryBusinessType,
            isSaving = isSavingBusinessType,
            errorText = businessTypeError,
            onDismiss = {
                showBusinessTypeDialog = false
                businessTypeError = null
            },
            onSave = { newType, seededActivities ->
                isSavingBusinessType = true
                businessTypeError = null
                scope.launch {
                    val merged = mergeSecondaryActivities(
                        current = settingsState.secondaryActivities,
                        seededForNewType = seededActivities
                    )
                    try {
                        prefsRepo.updateBusinessProfile(
                            primaryType = newType.id,
                            secondaryActivities = merged,
                            version = settingsState.profileVersion
                        )
                        isSavingBusinessType = false
                        showBusinessTypeDialog = false
                        Toast.makeText(
                            context,
                            "Jenis usaha diubah ke ${newType.displayName}",
                            Toast.LENGTH_LONG
                        ).show()
                    } catch (e: Exception) {
                        isSavingBusinessType = false
                        businessTypeError = e.localizedMessage ?: "Gagal menyimpan jenis usaha"
                    }
                }
            }
        )
    }

    if (showDeleteQrisConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteQrisConfirmDialog = false },
            shape = AppShapes.CardShape,
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Hapus QRIS Warung?", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
            text = {
                Text(
                    "Foto QRIS warung akan dihapus dari aplikasi. Pelanggan tidak dapat memindai QRIS sampai Anda mengunggah QRIS baru.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextPrimary
                )
            },
            confirmButton = {
                ButtonDestructive(
                    text = "Hapus",
                    onClick = {
                        showDeleteQrisConfirmDialog = false
                        scope.launch {
                            prefsRepo.deleteQrisImage()
                            Toast.makeText(context, "QRIS warung berhasil dihapus", Toast.LENGTH_SHORT).show()
                        }
                    },
                    fillMaxWidth = false
                )
            },
            dismissButton = {
                ButtonGhost(
                    text = "Batal",
                    onClick = { showDeleteQrisConfirmDialog = false }
                )
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
            shape = AppShapes.CardShape,
            color = MaterialTheme.colorScheme.surface,
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
                                color = AppColors.TextPrimary
                            )
                        )
                        Text(
                            text = "Sesuaikan terminologi & model operasional",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = AppColors.TextSecondary
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
                                label = { Text("Semua (19)") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AppColors.GreenPrimary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                            BusinessCategory.entries.forEach { cat ->
                                FilterChip(
                                    selected = selectedCategory == cat,
                                    onClick = { selectedCategory = cat },
                                    label = { Text(cat.displayName) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AppColors.GreenPrimary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
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
                                letterSpacing = 0.5.sp
                            )
                        )

                        availableTypes.forEach { type ->
                            val isSelected = selectedType == type
                            Surface(
                                shape = AppShapes.CardShape,
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) AppColors.GreenPrimary else MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(AppShapes.CardShape)
                                    .clickable {
                                        selectedType = type
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
                                            selectedColor = AppColors.GreenPrimary,
                                            unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = type.displayName,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (isSelected) AppColors.GreenDark else AppColors.TextPrimary
                                        )
                                        Text(
                                            text = type.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = AppColors.TextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Secondary Activities Customization
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "AKTIVITAS OPERASIONAL & FITUR TAMBAHAN",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = AppColors.GreenPrimary,
                                letterSpacing = 0.5.sp
                            )
                        )
                        Text(
                            text = "Pilih aktivitas operasional yang dijalankan bisnis Anda:",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.TextSecondary
                        )

                        BusinessActivity.entries.forEach { activity ->
                            val isChecked = selectedActivities.contains(activity)
                            Surface(
                                shape = AppShapes.ChipShape,
                                color = if (isChecked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    1.dp,
                                    if (isChecked) AppColors.GreenPrimary else MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(AppShapes.ChipShape)
                                    .clickable {
                                        selectedActivities = if (isChecked) {
                                            selectedActivities - activity
                                        } else {
                                            selectedActivities + activity
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
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
                                            checkedColor = AppColors.GreenPrimary,
                                            uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = activity.displayName,
                                            fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Normal,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = AppColors.TextPrimary
                                        )
                                        Text(
                                            text = activity.description,
                                            style = MaterialTheme.typography.labelSmall,
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
                    ButtonNeutral(
                        text = "Batal",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                    ButtonPrimary(
                        text = "Simpan Tipe Usaha",
                        onClick = { onSave(selectedType, selectedActivities) },
                        modifier = Modifier.weight(1.5f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AppTouchTarget)
            .clip(AppShapes.ChipShape)
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
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
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = AppColors.GreenPrimary,
                checkedBorderColor = AppColors.GreenPrimary,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedBorderColor = AppColors.CardBorderStrong
            )
        )
    }
}
