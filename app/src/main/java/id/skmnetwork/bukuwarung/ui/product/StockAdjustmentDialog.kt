package id.skmnetwork.bukuwarung.ui.product

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import id.skmnetwork.bukuwarung.ui.components.AppTextField
import id.skmnetwork.bukuwarung.ui.components.SecondaryButton
import id.skmnetwork.bukuwarung.ui.theme.AppColors
import id.skmnetwork.bukuwarung.ui.theme.AppSpacing
import id.skmnetwork.bukuwarung.util.formatQuantityValue

/**
 * Step 2 (D) - stock correction.
 *
 * A merchant who notices the shelf count is wrong needs one safe path: type the real quantity, see
 * the difference, and record it. This is the ONLY place an existing product's stock can change, and
 * it is written as a stock movement so the ledger and the product row stay in agreement.
 *
 * Mobile-first: a single scrollable column, the current stock shown first as context, the difference
 * computed live, and both actions at full 48dp height.
 */
@Composable
fun StockAdjustmentDialog(
    productName: String,
    unit: String,
    currentStock: Double,
    isStockable: Boolean,
    isSaving: Boolean,
    externalError: String? = null,
    onDismiss: () -> Unit,
    onSave: (newStock: Double, note: String?, isStockCount: Boolean) -> Unit
) {
    var actualStockText by remember(currentStock) { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var isStockCount by remember { mutableStateOf(true) }
    var localError by remember { mutableStateOf<String?>(null) }
    val errorText = localError ?: externalError

    val parsedStock = actualStockText.trim().replace(',', '.').toDoubleOrNull()
    val delta = parsedStock?.minus(currentStock)

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = {
            Text(
                text = "Sesuaikan Stok",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                Text(
                    text = productName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AppSpacing.md),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Stok saat ini",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.TextSecondary
                        )
                        Text(
                            text = "${formatQuantityValue(currentStock)} $unit",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.GreenDark
                        )
                    }
                }

                if (!isStockable) {
                    Text(
                        text = "Produk ini bukan produk berbobot, jadi tidak ada stok fisik yang bisa " +
                            "sesuai. Produk jasa dan produk digital tidak memiliki jumlah stok.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextSecondary
                    )
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SecondaryButton(
                            text = "Stok Opname",
                            onClick = { isStockCount = true },
                            modifier = Modifier.weight(1f)
                        )
                        SecondaryButton(
                            text = "Penyesuaian",
                            onClick = { isStockCount = false },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        text = if (isStockCount) {
                            "Opname: Anda menghitung ulang stok di rak dan memasukkan angka sebenarnya."
                        } else {
                            "Penyesuaian: koreksi stok karena barang datang atau hilang tanpa lewat pembelian."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextSecondary
                    )

                    AppTextField(
                        value = actualStockText,
                        onValueChange = {
                            actualStockText = it
                            localError = null
                        },
                        label = "Stok aktual ($unit)",
                        isError = errorText != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )

                    if (delta != null && delta != 0.0) {
                        val isIncrease = delta > 0
                        Text(
                            text = "Selisih: ${if (isIncrease) "+" else ""}" +
                                "${formatQuantityValue(delta)} $unit",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isIncrease) AppColors.GreenDark else MaterialTheme.colorScheme.error
                        )
                    }

                    AppTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = "Alasan / catatan (opsional)"
                    )

                    if (errorText != null) {
                        Text(
                            text = errorText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Text(
                        text = "Penyesuaian ini akan tercatat di riwayat stok dan tidak dapat dibatalkan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextSecondary
                    )
                }
            }
        },
        confirmButton = {
            if (isStockable) {
                TextButton(
                    onClick = {
                        val parsed = actualStockText.trim().replace(',', '.').toDoubleOrNull()
                        if (parsed == null) {
                            localError = "Masukkan angka stok yang valid"
                            return@TextButton
                        }
                        if (parsed < 0) {
                            localError = "Stok tidak boleh kurang dari 0"
                            return@TextButton
                        }
                        onSave(parsed, note.trim().ifEmpty { null }, isStockCount)
                    },
                    enabled = !isSaving
                ) {
                    Text(
                        text = if (isSaving) "Menyimpan..." else "Simpan",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("Batal")
            }
        }
    )
}
