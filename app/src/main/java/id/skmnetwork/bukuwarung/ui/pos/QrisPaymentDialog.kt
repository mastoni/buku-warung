package id.skmnetwork.bukuwarung.ui.pos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import id.skmnetwork.bukuwarung.ui.components.AppCard
import id.skmnetwork.bukuwarung.ui.theme.AppColors
import id.skmnetwork.bukuwarung.ui.theme.AppShapes
import id.skmnetwork.bukuwarung.ui.theme.AppSpacing
import id.skmnetwork.bukuwarung.util.formatRupiah

@Composable
fun QrisPaymentDialog(
    totalPrice: Long,
    isCheckingOut: Boolean,
    onDismiss: () -> Unit,
    onConfirmQrisPayment: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isCheckingOut) onDismiss() },
        title = {
            Text("PEMBAYARAN QRIS", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
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

                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .background(AppColors.SurfaceGray, AppShapes.CardShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCode2,
                        contentDescription = "QRIS",
                        tint = AppColors.GreenPrimary,
                        modifier = Modifier.size(80.dp)
                    )
                }

                Text(
                    text = "Silakan minta pelanggan memindai QRIS warung Anda dan pastikan dana telah masuk.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextSecondary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmQrisPayment,
                enabled = !isCheckingOut,
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.GreenPrimary)
            ) {
                Text(if (isCheckingOut) "MEMPROSES..." else "SUDAH DIBAYAR", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isCheckingOut) {
                Text("Batal")
            }
        }
    )
}
