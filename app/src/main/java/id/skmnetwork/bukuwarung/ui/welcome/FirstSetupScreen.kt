package id.skmnetwork.bukuwarung.ui.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import id.skmnetwork.bukuwarung.ui.components.AppCard
import id.skmnetwork.bukuwarung.ui.components.AppTextField
import id.skmnetwork.bukuwarung.ui.components.PrimaryButton
import id.skmnetwork.bukuwarung.ui.theme.AppColors
import id.skmnetwork.bukuwarung.ui.theme.AppSpacing

@Composable
fun FirstSetupScreen(
    onCompleteSetup: (shopName: String, ownerName: String, phone: String, address: String) -> Unit
) {
    var shopNameInput by remember { mutableStateOf("") }
    var ownerNameInput by remember { mutableStateOf("") }
    var phoneInput by remember { mutableStateOf("") }
    var addressInput by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

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
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(AppColors.GreenLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Storefront,
                    contentDescription = null,
                    tint = AppColors.GreenPrimary,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(Modifier.height(AppSpacing.md))

            Text(
                "Selamat Datang 👋",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Siapkan profil warung Anda untuk memulai.",
                color = AppColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(AppSpacing.lg))

            AppCard(backgroundColor = Color.White) {
                Column(
                    modifier = Modifier.padding(AppSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
                ) {
                    if (errorText != null) {
                        Text(errorText!!, color = AppColors.RedExpense, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    }

                    AppTextField(
                        value = shopNameInput,
                        onValueChange = {
                            shopNameInput = it
                            errorText = null
                        },
                        label = "Nama Warung *",
                        isError = errorText != null
                    )

                    AppTextField(
                        value = ownerNameInput,
                        onValueChange = { ownerNameInput = it },
                        label = "Nama Pemilik (opsional)"
                    )

                    AppTextField(
                        value = phoneInput,
                        onValueChange = { phoneInput = it },
                        label = "Nomor WhatsApp (opsional)",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )

                    AppTextField(
                        value = addressInput,
                        onValueChange = { addressInput = it },
                        label = "Alamat Warung (opsional)"
                    )

                    Spacer(Modifier.height(AppSpacing.xs))

                    PrimaryButton(
                        text = "Simpan Profil & Mulai",
                        onClick = {
                            val trimmedShopName = shopNameInput.trim()
                            if (trimmedShopName.isEmpty()) {
                                errorText = "Nama warung wajib diisi"
                            } else {
                                onCompleteSetup(
                                    trimmedShopName,
                                    ownerNameInput.trim(),
                                    phoneInput.trim(),
                                    addressInput.trim()
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}
