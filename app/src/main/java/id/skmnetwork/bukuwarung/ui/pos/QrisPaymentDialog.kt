package id.skmnetwork.bukuwarung.ui.pos

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.skmnetwork.bukuwarung.ui.components.AppCard
import id.skmnetwork.bukuwarung.ui.theme.AppColors
import id.skmnetwork.bukuwarung.ui.theme.AppShapes
import id.skmnetwork.bukuwarung.ui.theme.AppSpacing
import id.skmnetwork.bukuwarung.util.formatRupiah
import java.io.File

@Composable
fun QrisPaymentDialog(
    totalPrice: Long,
    isCheckingOut: Boolean,
    qrisImagePath: String? = null,
    onNavigateToSettings: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onConfirmQrisPayment: () -> Unit
) {
    val qrisBitmap = remember(qrisImagePath) {
        if (!qrisImagePath.isNullOrBlank()) {
            try {
                val file = File(qrisImagePath)
                if (file.exists() && file.length() > 0) {
                    BitmapFactory.decodeFile(file.absolutePath)
                } else null
            } catch (e: Exception) {
                null
            }
        } else null
    }

    val isQrisConfigured = qrisBitmap != null

    if (isQrisConfigured) {
        AlertDialog(
            onDismissRequest = { if (!isCheckingOut) onDismiss() },
            title = {
                Text("PEMBAYARAN QRIS", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
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

                    // Actual configured merchant QRIS image
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(12.dp))
                            .padding(AppSpacing.sm),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = qrisBitmap!!.asImageBitmap(),
                            contentDescription = "QRIS Warung",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp),
                            contentScale = ContentScale.Fit
                        )
                    }

                    Text(
                        text = "Silakan minta pelanggan memindai QRIS warung Anda dan pastikan pembayaran telah masuk.",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall,
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
    } else {
        // QRIS Belum Diatur State
        AlertDialog(
            onDismissRequest = { if (!isCheckingOut) onDismiss() },
            title = {
                Text("QRIS Warung Belum Diatur", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFFF7E6),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(AppSpacing.md),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = Color(0xFFD46B08),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(AppSpacing.sm))
                            Text(
                                text = "Atur QRIS warung Anda di Pengaturan agar pelanggan dapat memindai QRIS dari aplikasi.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF873800)
                            )
                        }
                    }

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
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDismiss()
                        onNavigateToSettings?.invoke()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.GreenPrimary)
                ) {
                    Text("Atur QRIS", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Batal")
                }
            }
        )
    }
}

