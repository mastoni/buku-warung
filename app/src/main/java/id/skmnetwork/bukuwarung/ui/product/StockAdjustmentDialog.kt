package id.skmnetwork.bukuwarung.ui.product

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.skmnetwork.bukuwarung.ui.components.AppErrorBanner
import id.skmnetwork.bukuwarung.ui.components.AppTextField
import id.skmnetwork.bukuwarung.ui.components.ButtonGhost
import id.skmnetwork.bukuwarung.ui.components.ButtonPrimary
import id.skmnetwork.bukuwarung.ui.components.CardVariant
import id.skmnetwork.bukuwarung.ui.components.FieldAmount
import id.skmnetwork.bukuwarung.ui.components.SectionCard
import id.skmnetwork.bukuwarung.ui.components.SegmentedControl
import id.skmnetwork.bukuwarung.ui.theme.AppColors
import id.skmnetwork.bukuwarung.ui.theme.AppSpacing
import id.skmnetwork.bukuwarung.util.formatQuantityValue
import id.skmnetwork.bukuwarung.util.parseQuantityInput

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

    val parsedStock = parseQuantityInput(actualStockText)
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

                SectionCard(
                    variant = CardVariant.Muted,
                    contentPadding = PaddingValues(AppSpacing.md),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                    // Gate 1.2 (audit P0 #12): this was two SecondaryButtons.
                    //
                    // SecondaryButton's default content colour was destructive red, so BOTH halves
                    // rendered red and neither showed which mode was active - `isStockCount` only
                    // ever reached a helper paragraph. A merchant could not tell an opname from an
                    // adjustment. SegmentedControl renders the selected state explicitly.
                    //
                    // State, callback and validation below are untouched.
                    SegmentedControl(
                        options = STOCK_ADJUSTMENT_MODES,
                        selected = if (isStockCount) StockAdjustmentMode.Opname else StockAdjustmentMode.Adjustment,
                        onSelect = { mode -> isStockCount = mode == StockAdjustmentMode.Opname },
                        label = { it.label },
                        enabled = !isSaving
                    )
                    Text(
                        text = if (isStockCount) {
                            "Opname: Anda menghitung ulang stok di rak dan memasukkan angka sebenarnya."
                        } else {
                            "Penyesuaian: koreksi stok karena barang datang atau hilang tanpa lewat pembelian."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextSecondary
                    )

                    // Gate 1.2: quantity entry, so the "Rp" prefix is suppressed and the decimal
                    // keyboard is used. parseQuantityInput still receives the raw text unchanged -
                    // FieldAmount deliberately applies no visual transformation.
                    FieldAmount(
                        value = actualStockText,
                        onValueChange = {
                            actualStockText = it
                            localError = null
                        },
                        label = "Stok aktual ($unit)",
                        isError = errorText != null,
                        quantity = true,
                        enabled = !isSaving
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
                        // Gate 1.2: was a detached `Text(color = colorScheme.error)` sitting between
                        // the note field and the warning, i.e. not next to the field it belongs to.
                        // The message, and the state behind it, are unchanged; only the presentation
                        // is now the app's single error treatment.
                        AppErrorBanner(message = errorText)
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
                // Gate 1.2: was a TextButton - the only committing action in the dialog, and the
                // least prominent element in it, while the file's own KDoc claimed "both actions at
                // full 48dp height".
                ButtonPrimary(
                    text = if (isSaving) "Menyimpan..." else "Simpan",
                    onClick = {
                        val parsed = parseQuantityInput(actualStockText)
                        if (parsed == null) {
                            localError = "Masukkan angka stok yang valid"
                            return@ButtonPrimary
                        }
                        if (parsed < 0) {
                            localError = "Stok tidak boleh kurang dari 0"
                            return@ButtonPrimary
                        }
                        onSave(parsed, note.trim().ifEmpty { null }, isStockCount)
                    },
                    enabled = !isSaving,
                    loading = isSaving,
                    fillMaxWidth = false
                )
            }
        },
        dismissButton = {
            ButtonGhost(
                text = "Batal",
                onClick = onDismiss,
                enabled = !isSaving
            )
        }
    )
}

/** The two mutually exclusive ways stock can be corrected. Presentation vocabulary only. */
private enum class StockAdjustmentMode(val label: String) {
    Opname("Stok Opname"),
    Adjustment("Penyesuaian")
}

private val STOCK_ADJUSTMENT_MODES = listOf(StockAdjustmentMode.Opname, StockAdjustmentMode.Adjustment)
