package id.skmnetwork.bukuwarung.ui.security

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.skmnetwork.bukuwarung.data.preferences.UserPreferencesRepository
import id.skmnetwork.bukuwarung.ui.theme.AppColors
import id.skmnetwork.bukuwarung.ui.theme.AppSpacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PinLockScreen(
    userPreferencesRepository: UserPreferencesRepository,
    onUnlockSuccess: () -> Unit
) {
    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var failedAttempts by remember { mutableIntStateOf(0) }
    var lockCooldownSeconds by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(lockCooldownSeconds) {
        if (lockCooldownSeconds > 0) {
            delay(1000)
            lockCooldownSeconds -= 1
        }
    }

    fun handleDigitPress(digit: String) {
        if (lockCooldownSeconds > 0) return
        if (enteredPin.length < 4) {
            val newPin = enteredPin + digit
            enteredPin = newPin
            errorMessage = null

            if (newPin.length == 4) {
                scope.launch {
                    val isValid = userPreferencesRepository.verifyPin(newPin)
                    if (isValid) {
                        failedAttempts = 0
                        onUnlockSuccess()
                    } else {
                        failedAttempts += 1
                        enteredPin = ""
                        if (failedAttempts >= 5) {
                            lockCooldownSeconds = 30
                            errorMessage = "Terlalu banyak percobaan. Tunggu 30 detik."
                        } else {
                            errorMessage = "PIN salah (percobaan ke-$failedAttempts)"
                        }
                    }
                }
            }
        }
    }

    fun handleBackspace() {
        if (lockCooldownSeconds > 0) return
        if (enteredPin.isNotEmpty()) {
            enteredPin = enteredPin.dropLast(1)
            errorMessage = null
        }
    }

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
                .padding(AppSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = AppSpacing.xl)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(AppColors.GreenLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Keamanan PIN",
                        tint = AppColors.GreenPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(Modifier.height(AppSpacing.lg))

                Text(
                    text = "Buku Warung Terkunci",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(AppSpacing.xs))

                Text(
                    text = "Masukkan 4 angka PIN pemilik untuk membuka",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextSecondary
                )

                Spacer(Modifier.height(AppSpacing.xl))

                // PIN 4-Dots Display
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(4) { index ->
                        val isFilled = index < enteredPin.length
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isFilled) AppColors.GreenPrimary else Color.Transparent
                                )
                                .border(
                                    width = 2.dp,
                                    color = if (isFilled) AppColors.GreenPrimary else AppColors.CardBorder,
                                    shape = CircleShape
                                )
                        )
                    }
                }

                Spacer(Modifier.height(AppSpacing.md))

                AnimatedVisibility(visible = errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = AppColors.RedExpense,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }

                AnimatedVisibility(visible = lockCooldownSeconds > 0) {
                    Text(
                        text = "Terkunci sementara: $lockCooldownSeconds dtk",
                        color = AppColors.RedExpense,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            // Keypad 1-9, Backspace, 0
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = AppSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val keypadRows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("", "0", "DEL")
                )

                keypadRows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        row.forEach { key ->
                            if (key.isEmpty()) {
                                Spacer(modifier = Modifier.size(72.dp))
                            } else if (key == "DEL") {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .clickable(enabled = lockCooldownSeconds == 0) { handleBackspace() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                                        contentDescription = "Hapus",
                                        tint = AppColors.TextSecondary
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                        .border(1.dp, AppColors.CardBorder, CircleShape)
                                        .clickable(enabled = lockCooldownSeconds == 0) { handleDigitPress(key) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = key,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AppColors.TextPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
