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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import id.skmnetwork.bukuwarung.BuildConfig
import id.skmnetwork.bukuwarung.license.LicenseManager
import id.skmnetwork.bukuwarung.license.LicenseStatus
import id.skmnetwork.bukuwarung.ui.components.AppCard
import id.skmnetwork.bukuwarung.ui.components.PrimaryButton
import id.skmnetwork.bukuwarung.ui.components.SecondaryButton
import id.skmnetwork.bukuwarung.ui.theme.AppColors
import id.skmnetwork.bukuwarung.ui.theme.AppShapes
import id.skmnetwork.bukuwarung.ui.theme.AppSpacing
import kotlinx.coroutines.launch

@Composable
fun LicenseGateScreen(
    licenseManager: LicenseManager,
    licenseStatus: LicenseStatus,
    onBypassForDemo: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isChecking by remember { mutableStateOf(false) }

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
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(AppColors.GreenLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = "Lisensi",
                    tint = AppColors.GreenPrimary,
                    modifier = Modifier.size(44.dp)
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
                text = "Aplikasi kasir & pembukuan warung offline sekali bayar untuk selamanya.",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(AppSpacing.lg))

            AppCard(backgroundColor = Color.White) {
                Column(
                    modifier = Modifier.padding(AppSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
                ) {
                    LicenseBenefitRow("100% Offline-First: Data tersimpan aman di HP Anda.")
                    LicenseBenefitRow("Sekali Bayar: Tanpa biaya langganan bulanan.")
                    LicenseBenefitRow("Lengkap: Kasir, stok, hutang, kas, & laporan laba.")
                }
            }

            Spacer(Modifier.height(AppSpacing.lg))

            var activationMessage by remember { mutableStateOf<String?>(null) }

            if (isChecking || licenseStatus == LicenseStatus.CHECKING) {
                CircularProgressIndicator(
                    color = AppColors.GreenPrimary,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(Modifier.height(AppSpacing.sm))
                Text("Memeriksa status lisensi...", style = MaterialTheme.typography.bodySmall)
            } else {
                PrimaryButton(
                    text = "Periksa / Pulihkan Lisensi",
                    onClick = {
                        scope.launch {
                            isChecking = true
                            activationMessage = null
                            licenseManager.refreshLicense()
                            isChecking = false
                            if (licenseManager.licenseStatus.value == LicenseStatus.UNLICENSED) {
                                activationMessage = if (BuildConfig.ENABLE_OWNER_TEST) {
                                    "Lisensi belum terdeteksi. Gunakan aktivasi uji coba untuk pengujian owner."
                                } else {
                                    "Lisensi belum terdeteksi pada perangkat ini."
                                }
                            }
                        }
                    }
                )

                if (BuildConfig.ENABLE_OWNER_TEST) {
                    Spacer(Modifier.height(AppSpacing.sm))

                    SecondaryButton(
                        text = "Aktivasi Uji Coba Internal (Owner Testing)",
                        onClick = {
                            scope.launch {
                                isChecking = true
                                activationMessage = null
                                val success = licenseManager.activateOwnerTest()
                                isChecking = false
                                if (success) {
                                    activationMessage = "Aktivasi Uji Coba Internal berhasil. Status: AKTIF."
                                } else {
                                    activationMessage = "Gagal mengaktifkan uji coba internal."
                                }
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

            if (activationMessage != null) {
                Spacer(Modifier.height(AppSpacing.sm))
                Text(
                    text = activationMessage ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (licenseStatus == LicenseStatus.ACTIVE) AppColors.GreenDark else AppColors.RedExpense,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(AppSpacing.md))

            Text(
                text = when (licenseStatus) {
                    LicenseStatus.ACTIVE -> if (BuildConfig.ENABLE_OWNER_TEST) "Status Lisensi: AKTIF (Uji Coba Internal)" else "Status Lisensi: AKTIF"
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

@Composable
private fun LicenseBenefitRow(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = AppColors.GreenPrimary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(AppSpacing.sm))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.TextPrimary
        )
    }
}
