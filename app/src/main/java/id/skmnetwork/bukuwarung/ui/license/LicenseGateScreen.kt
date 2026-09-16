package id.skmnetwork.bukuwarung.ui.license

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import id.skmnetwork.bukuwarung.BuildConfig
import id.skmnetwork.bukuwarung.license.ActivationResult
import id.skmnetwork.bukuwarung.license.LicenseManager
import id.skmnetwork.bukuwarung.license.LicenseStatus
import id.skmnetwork.bukuwarung.ui.components.AppCard
import id.skmnetwork.bukuwarung.ui.components.PrimaryButton
import id.skmnetwork.bukuwarung.ui.components.SecondaryButton
import id.skmnetwork.bukuwarung.ui.theme.AppColors
import id.skmnetwork.bukuwarung.ui.theme.AppSpacing
import kotlinx.coroutines.launch

@Composable
fun LicenseGateScreen(
    licenseManager: LicenseManager,
    licenseStatus: LicenseStatus,
    onBypassForDemo: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var ownerEmailInput by remember { mutableStateOf("") }
    var licenseCodeInput by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    var activationResult by remember { mutableStateOf<ActivationResult?>(null) }
    var validationErrorMessage by remember { mutableStateOf<String?>(null) }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        color = AppColors.BackgroundLight
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

            if (activationResult is ActivationResult.Active) {
                val activeResult = activationResult as ActivationResult.Active
                AppCard(backgroundColor = Color(0xFFE8F5E9)) {
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
                AppCard(backgroundColor = Color.White) {
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
                text = when (licenseStatus) {
                    LicenseStatus.ACTIVE -> "Status Lisensi: AKTIF"
                    LicenseStatus.UNLICENSED -> "Status Lisensi: Belum Aktif (UNLICENSED)"
                    LicenseStatus.CHECKING -> "Status Lisensi: Memeriksa..."
                    else -> "Status Lisensi: ${licenseStatus.name}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (licenseStatus == LicenseStatus.ACTIVE) AppColors.GreenPrimary else AppColors.TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}
