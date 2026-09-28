package id.skmnetwork.bukuwarung.ui.license

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import id.skmnetwork.bukuwarung.BuildConfig
import id.skmnetwork.bukuwarung.license.ActivationResult
import id.skmnetwork.bukuwarung.license.LicenseBlockReason
import id.skmnetwork.bukuwarung.license.LicenseManager
import id.skmnetwork.bukuwarung.license.LicensePhase
import id.skmnetwork.bukuwarung.license.LicenseRuntimeState
import id.skmnetwork.bukuwarung.license.RecoveryResult
import id.skmnetwork.bukuwarung.ui.components.AppCard
import id.skmnetwork.bukuwarung.ui.components.PrimaryButton
import id.skmnetwork.bukuwarung.ui.components.SecondaryButton
import id.skmnetwork.bukuwarung.ui.theme.AppColors
import id.skmnetwork.bukuwarung.ui.theme.AppSpacing
import kotlinx.coroutines.launch

@Composable
fun LicenseGateScreen(
    licenseManager: LicenseManager,
    licenseState: LicenseRuntimeState,
    onBypassForDemo: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        // LICENSE_GATE_VIEWED is defined as "license gate meaningfully exposed to this
        // installation", not "gate composable entered". A plain LaunchedEffect(Unit) re-fires
        // on every re-entry into composition (Activity recreation, re-navigation), which
        // previously produced one row per entry. The claim is committed in DataStore before
        // the event is sent, so exactly one row is recorded per installation.
        if (licenseManager.claimLicenseGateView()) {
            licenseManager.trackMarketingEvent(
                eventType = "LICENSE_GATE_VIEWED",
                utmSource = "app_license_gate",
                utmMedium = "in_app",
                utmCampaign = "buku_warung_v020"
            )
        }
    }

    var ownerEmailInput by remember { mutableStateOf("") }
    var licenseCodeInput by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    var activationResult by remember { mutableStateOf<ActivationResult?>(null) }
    var validationErrorMessage by remember { mutableStateOf<String?>(null) }
    var recoveryInFlight by remember { mutableStateOf(false) }
    var recoveryState by remember { mutableStateOf<RecoveryResult?>(null) }
    val recoveryPending = recoveryState is RecoveryResult.RecoveryPending

    // Gate H.5.1 section 6: a lifecycle DEVICE_MISMATCH blocks access but RETAINS the owner email,
    // so prefill it. The merchant can then reach the Task 7A recovery action directly instead of
    // having to rediscover the mismatch through a fresh activation attempt.
    LaunchedEffect(licenseState.blockReason) {
        if (licenseState.blockReason == LicenseBlockReason.DEVICE_MISMATCH) {
            val retained = licenseManager.getEntitlementInfo().ownerEmail
            if (retained.isNotBlank() && ownerEmailInput.isBlank()) {
                ownerEmailInput = retained
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(AppSpacing.lg)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(AppColors.GreenLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = "Lisensi",
                    tint = AppColors.GreenPrimary,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(Modifier.height(AppSpacing.md))

            Text(
                text = "Aktivasi Buku Warung",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(AppSpacing.xs))

            Text(
                text = "Lisensi berlaku untuk 1 perangkat.",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(AppSpacing.lg))

            // Gate H.5.1 section 12 - explain the new lifecycle states without redesigning the gate.
            // Reuses the existing AppCard + colour language already used further down this screen.
            val gateNotice = when (licenseState.phase) {
                LicensePhase.BLOCKED -> when (licenseState.blockReason) {
                    LicenseBlockReason.DEVICE_MISMATCH ->
                        "Lisensi ini aktif di perangkat lain. Kode lisensi Anda tetap tersimpan di " +
                            "perangkat ini. Masukkan email dan kode lisensi di bawah, lalu gunakan " +
                            "Pemulihan Perangkat."
                    LicenseBlockReason.REVOKED ->
                        "Lisensi ini telah dicabut. Hubungi administrator untuk informasi lebih lanjut."
                    LicenseBlockReason.EMAIL_MISMATCH ->
                        "Email pemilik lisensi tidak cocok. Silakan masukkan email yang benar."
                    LicenseBlockReason.GRACE_EXPIRED ->
                        "Lisensi perlu diperpanjang: perangkat ini terlalu lama tidak terhubung ke " +
                            "server lisensi. Silakan masukkan kode lisensi Anda di bawah."
                    else ->
                        "Lisensi tidak valid. Silakan masukkan kode lisensi yang benar di bawah."
                }
                LicensePhase.TRANSIENT_ERROR ->
                    "Server lisensi tidak dapat dihubungi. Status lisensi Anda yang tersimpan tetap " +
                        "berlaku dan akan diperiksa kembali nanti."
                LicensePhase.STALE_ACTIVE ->
                    "Lisensi perlu diperiksa ulang. Status lokal Anda tetap berlaku untuk sementara."
                else -> null
            }

            if (gateNotice != null) {
                AppCard(backgroundColor = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(
                        text = gateNotice,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextPrimary
                    )
                }
                Spacer(Modifier.height(AppSpacing.md))
            }

            if (activationResult is ActivationResult.Active) {
                val activeResult = activationResult as ActivationResult.Active
                AppCard(backgroundColor = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AppSpacing.lg),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Sukses",
                            tint = AppColors.GreenDark,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "✓ Aktivasi Berhasil",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.GreenDark
                        )
                        Text(
                            text = "Email Pemilik: ${activeResult.ownerEmail}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.TextPrimary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(AppSpacing.sm))
                        PrimaryButton(
                            text = "Mulai Menggunakan",
                            onClick = {
                                scope.launch {
                                    licenseManager.refreshLicense()
                                }
                            }
                        )
                    }
                }
            } else {
                AppCard(backgroundColor = MaterialTheme.colorScheme.surface) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AppSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
                    ) {
                        OutlinedTextField(
                            value = ownerEmailInput,
                            onValueChange = {
                                ownerEmailInput = it
                                validationErrorMessage = null
                                activationResult = null
                                recoveryState = null
                            },
                            label = { Text("Email Pemilik") },
                            placeholder = { Text("contoh@email.com") },
                            leadingIcon = {
                                Icon(Icons.Default.Mail, contentDescription = null, tint = AppColors.TextSecondary)
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AppColors.GreenPrimary,
                                focusedLabelColor = AppColors.GreenPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = licenseCodeInput,
                            onValueChange = {
                                licenseCodeInput = it.uppercase()
                                validationErrorMessage = null
                                activationResult = null
                                recoveryState = null
                            },
                            label = { Text("Kode Lisensi") },
                            placeholder = { Text("BW-XXXX-XXXX-XXXX") },
                            leadingIcon = {
                                Icon(Icons.Default.Key, contentDescription = null, tint = AppColors.TextSecondary)
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Done
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AppColors.GreenPrimary,
                                focusedLabelColor = AppColors.GreenPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (validationErrorMessage != null) {
                            Text(
                                text = validationErrorMessage ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.RedExpense
                            )
                        }

                        // Activation Error Feedback
                        activationResult?.let { res ->
                            val (errorTitle, errorDesc) = when (res) {
                                is ActivationResult.EmailMismatch ->
                                    Pair("Email Lisensi Tidak Cocok", "Email yang dimasukkan tidak sesuai dengan pendaftaran lisensi.")
                                is ActivationResult.DeviceMismatch ->
                                    Pair("Lisensi Sudah Digunakan", "Lisensi ini sudah aktif pada perangkat lain. Silakan hubungi dukungan jika memerlukan pemulihan perangkat.")
                                is ActivationResult.Revoked ->
                                    Pair("Lisensi Tidak Aktif", "Lisensi ini telah dinonaktifkan atau dicabut.")
                                is ActivationResult.Invalid ->
                                    Pair("Kode Lisensi Tidak Valid", "Kode lisensi tidak ditemukan. Mohon periksa kembali kode Anda.")
                                is ActivationResult.NetworkError ->
                                    Pair("Koneksi Gagal", "Tidak dapat terhubung ke server. Periksa koneksi internet Anda.")
                                is ActivationResult.ServerError ->
                                    Pair("Kesalahan Server", "Terjadi gangguan pada server lisensi. Silakan coba beberapa saat lagi.")
                                // Gate H.5.1 section 5: no verdict was obtained, so this is explicitly
                                // NOT an invalid-licence message.
                                is ActivationResult.Transient ->
                                    Pair("Server Tidak Merespons", "Tidak ada jawaban dari server lisensi (HTTP ${res.httpStatus}). Kode lisensi Anda tidak bermasalah. Silakan coba lagi sebentar.")
                                is ActivationResult.Active ->
                                    Pair("", "")
                            }

                            if (errorTitle.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFFFEBEE))
                                        .padding(AppSpacing.md)
                                ) {
                                    Row(verticalAlignment = Alignment.Top) {
                                        Icon(
                                            imageVector = Icons.Default.Error,
                                            contentDescription = null,
                                            tint = AppColors.RedExpense,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(AppSpacing.sm))
                                        Column {
                                            Text(
                                                text = errorTitle,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = AppColors.RedExpense
                                            )
                                            Text(
                                                text = errorDesc,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = AppColors.TextPrimary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Device recovery: shown after the activation attempt reported DEVICE_MISMATCH,
                        // and also when a lifecycle validation already blocked this device for that same
                        // reason (Gate H.5.1 section 6), so the 7A flow stays reachable.
                        // A pending result means the request was recorded; the licence stays
                        // inactive until an authorised admin rebinds the device.
                        val canRequestRecovery =
                            !recoveryPending && !recoveryInFlight &&
                                (activationResult is ActivationResult.DeviceMismatch ||
                                    licenseState.blockReason == LicenseBlockReason.DEVICE_MISMATCH)

                        if (canRequestRecovery || recoveryPending || recoveryState is RecoveryResult.RecoveryPending) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                            ) {
                                recoveryState?.let { state ->
                                    val message = when (state) {
                                        is RecoveryResult.RecoveryPending ->
                                            "Permintaan pemulihan perangkat berhasil dikirim. " +
                                                "Silakan tunggu persetujuan dukungan."
                                        is RecoveryResult.InvalidRequest -> state.message
                                        is RecoveryResult.LicenseNotFound -> state.message
                                        is RecoveryResult.LicenseRevoked -> state.message
                                        is RecoveryResult.EmailMismatch -> state.message
                                        is RecoveryResult.NetworkError -> state.message
                                        is RecoveryResult.UnexpectedError -> state.message
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (recoveryPending) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                                            )
                                            .padding(AppSpacing.md)
                                    ) {
                                        Text(
                                            text = message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (recoveryPending) AppColors.GreenPrimary else AppColors.TextPrimary
                                        )
                                    }
                                }

                                if (!recoveryPending) {
                                    PrimaryButton(
                                        text = if (recoveryInFlight) "Mengirim..." else "Pulihkan Perangkat",
                                        onClick = {
                                            // Guard against duplicate taps while a request runs.
                                            if (recoveryInFlight || recoveryPending) return@PrimaryButton

                                            val cleanEmail = ownerEmailInput.trim()
                                            val cleanCode = licenseCodeInput.trim()
                                            if (cleanEmail.isBlank() || cleanCode.isBlank()) {
                                                validationErrorMessage =
                                                    "Masukkan email dan kode lisensi terlebih dahulu."
                                                return@PrimaryButton
                                            }

                                            scope.launch {
                                                recoveryInFlight = true
                                                validationErrorMessage = null
                                                recoveryState = licenseManager.requestDeviceRecovery(
                                                    licenseCode = cleanCode,
                                                    ownerEmail = cleanEmail
                                                )
                                                recoveryInFlight = false
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        if (isSubmitting) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = AppSpacing.sm),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = AppColors.GreenPrimary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        } else {
                            PrimaryButton(
                                text = if (activationResult is ActivationResult.NetworkError) "Coba Lagi" else "AKTIVASI",
                                onClick = {
                                    val cleanEmail = ownerEmailInput.trim()
                                    val cleanCode = licenseCodeInput.trim()

                                    if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
                                        validationErrorMessage = "Masukkan alamat email yang valid."
                                        return@PrimaryButton
                                    }
                                    if (cleanCode.isBlank() || cleanCode.length < 5) {
                                        validationErrorMessage = "Masukkan kode lisensi yang valid."
                                        return@PrimaryButton
                                    }

                                    scope.launch {
                                        isSubmitting = true
                                        validationErrorMessage = null
                                        val res = licenseManager.activateLicense(cleanCode, cleanEmail)
                                        activationResult = res
                                        isSubmitting = false
                                    }
                                }
                            )

                            Spacer(Modifier.height(AppSpacing.xs))

                            // Purchase CTA Section for users without license
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                            ) {
                                Text(
                                    text = "Belum memiliki kode lisensi?",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppColors.TextSecondary,
                                    textAlign = TextAlign.Center
                                )

                                SecondaryButton(
                                    text = "Beli Lisensi Resmi (Rp 50.000)",
                                    onClick = {
                                        licenseManager.trackMarketingEvent(
                                            eventType = "LICENSE_PURCHASE_CLICKED",
                                            utmSource = "app_license_gate",
                                            utmMedium = "in_app",
                                            utmCampaign = "buku_warung_v020"
                                        )
                                        try {
                                            val intent = Intent(
                                                Intent.ACTION_VIEW,
                                                Uri.parse("https://license.skmnetwork.com/beli/buku-warung?utm_source=app_license_gate&utm_medium=in_app&utm_campaign=buku_warung_v020")
                                            )
                                            context.startActivity(intent)
                                        } catch (_: Exception) {}
                                    },
                                    contentColor = AppColors.GreenDark
                                )

                                TextButton(
                                    onClick = {
                                        licenseManager.trackMarketingEvent(
                                            eventType = "LICENSE_WHATSAPP_CLICKED",
                                            utmSource = "app_license_gate",
                                            utmMedium = "in_app",
                                            utmCampaign = "buku_warung_v020"
                                        )
                                        try {
                                            val waUrl = "https://wa.me/6285157056604?text=" + Uri.encode("Halo Admin SKMNetwork, saya sudah pasang aplikasi Buku Warung dan ingin beli kode lisensi")
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(waUrl))
                                            context.startActivity(intent)
                                        } catch (_: Exception) {}
                                    }
                                ) {
                                    Text(
                                        text = "Tanya Admin via WhatsApp",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AppColors.GreenPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }

                if (BuildConfig.ENABLE_OWNER_TEST) {
                    Spacer(Modifier.height(AppSpacing.md))
                    SecondaryButton(
                        text = "Aktivasi Uji Coba Internal (Owner Testing)",
                        onClick = {
                            scope.launch {
                                isSubmitting = true
                                licenseManager.activateOwnerTest()
                                isSubmitting = false
                            }
                        },
                        contentColor = AppColors.GreenDark
                    )
                }

                if (BuildConfig.DEBUG) {
                    Spacer(Modifier.height(AppSpacing.xs))
                    SecondaryButton(
                        text = "Lanjutkan (Mode Debug Dev)",
                        onClick = onBypassForDemo,
                        contentColor = AppColors.TextSecondary
                    )
                }
            }

            Spacer(Modifier.height(AppSpacing.lg))

            Text(
                text = when (licenseState.phase) {
                    LicensePhase.FRESH_ACTIVE -> "Status Lisensi: AKTIF"
                    LicensePhase.STALE_ACTIVE -> "Status Lisensi: Aktif (perlu koneksi)"
                    LicensePhase.TRANSIENT_ERROR -> "Status Lisensi: Tidak dapat menghubungi server"
                    LicensePhase.UNLICENSED -> "Status Lisensi: Belum Aktif (UNLICENSED)"
                    LicensePhase.BLOCKED -> "Status Lisensi: Diblokir (${licenseState.blockReason})"
                    LicensePhase.CHECKING -> "Status Lisensi: Memeriksa..."
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (licenseState.grantsAccess) AppColors.GreenPrimary else AppColors.TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}
