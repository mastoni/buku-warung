package id.skmnetwork.bukuwarung.ui.pos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import id.skmnetwork.bukuwarung.ui.components.AppCard
import id.skmnetwork.bukuwarung.ui.components.AppTextField
import id.skmnetwork.bukuwarung.ui.theme.AppColors
import id.skmnetwork.bukuwarung.ui.theme.AppShapes
import id.skmnetwork.bukuwarung.ui.theme.AppSpacing
import id.skmnetwork.bukuwarung.util.formatRupiah

@Composable
fun CashPaymentDialog(
    totalPrice: Long,
    isCheckingOut: Boolean,
    cashReceivedEnabled: Boolean = true,
    onDismiss: () -> Unit,
    onConfirmCashPayment: (cashReceived: Long, changeAmount: Long) -> Unit
) {
    var cashReceivedInput by remember {
        mutableStateOf(if (!cashReceivedEnabled) totalPrice.toString() else "")
    }

    val cashReceived = cashReceivedInput.trim().toLongOrNull() ?: 0L
    val isInsufficient = cashReceived < totalPrice
    val changeAmount = if (isInsufficient) 0L else (cashReceived - totalPrice)

    val quickTenderAmounts = listOf(
        Pair("Uang Pas", totalPrice),
        Pair("10.000", 10000L),
        Pair("20.000", 20000L),
        Pair("50.000", 50000L),
        Pair("100.000", 100000L)
    ).filter { it.second >= totalPrice }

    AlertDialog(
        onDismissRequest = { if (!isCheckingOut) onDismiss() },
        title = {
            Text("PEMBAYARAN TUNAI", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                AppCard(backgroundColor = AppColors.GreenLight) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AppSpacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Total Belanja", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = formatRupiah(totalPrice),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.GreenPrimary
                        )
                    }
                }

                AppTextField(
                    value = cashReceivedInput,
                    onValueChange = {
                        val filtered = it.filter { char -> char.isDigit() }
                        cashReceivedInput = filtered
                    },
                    label = "Uang Diterima (Rp) *",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                if (quickTenderAmounts.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                        items(quickTenderAmounts) { (label, amount) ->
                            Surface(
                                shape = AppShapes.ChipShape,
                                color = AppColors.SurfaceGray,
                                modifier = Modifier.clickable {
                                    cashReceivedInput = amount.toString()
                                }
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm)
                                ) {
                                    Text(
                                        text = if (label == "Uang Pas") "Uang Pas" else formatRupiah(amount),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                if (cashReceivedInput.isNotEmpty()) {
                    if (isInsufficient) {
                        Text(
                            text = "Uang diterima kurang ${formatRupiah(totalPrice - cashReceived)}",
                            color = AppColors.RedExpense,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        AppCard(backgroundColor = AppColors.BlueCash) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(AppSpacing.md),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Kembalian", fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                Text(
                                    text = formatRupiah(changeAmount),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = AppColors.GreenPrimary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val canSubmit = !isCheckingOut && cashReceivedInput.isNotEmpty() && !isInsufficient
            Button(
                onClick = {
                    if (canSubmit) {
                        onConfirmCashPayment(cashReceived, changeAmount)
                    }
                },
                enabled = canSubmit,
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.GreenPrimary)
            ) {
                Text(if (isCheckingOut) "MEMPROSES..." else "BAYAR TUNAI", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isCheckingOut) {
                Text("Batal")
            }
        }
    )
}
