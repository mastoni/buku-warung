package id.skmnetwork.bukuwarung.ui.pos

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import id.skmnetwork.bukuwarung.data.local.entity.SaleItemEntity
import id.skmnetwork.bukuwarung.data.local.entity.SaleTransactionEntity
import id.skmnetwork.bukuwarung.ui.components.AppCard
import id.skmnetwork.bukuwarung.ui.product.ProductViewModel
import id.skmnetwork.bukuwarung.ui.theme.AppColors
import id.skmnetwork.bukuwarung.ui.theme.AppShapes
import id.skmnetwork.bukuwarung.ui.theme.AppSpacing
import id.skmnetwork.bukuwarung.util.formatRupiah
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val RETURN_REASONS = listOf(
    "Barang rusak/cacat",
    "Salah barang",
    "Barang tidak sesuai",
    "Pelanggan berubah pikiran",
    "Kelebihan pembelian",
    "Lainnya"
)

@Composable
fun SaleReturnDialog(
    sale: SaleTransactionEntity,
    viewModel: ProductViewModel,
    onDismiss: () -> Unit,
    onReturnSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var saleItems by remember { mutableStateOf<List<SaleItemEntity>>(emptyList()) }
    var returnableQuantities by remember { mutableStateOf<Map<Long, Double>>(emptyMap()) }
    var isLoadingItems by remember { mutableStateOf(true) }

    // Map of saleItemId -> returnQuantity
    val returnCart = remember { mutableStateMapOf<Long, Double>() }
    var selectedReason by remember { mutableStateOf<String?>(null) }
    var reasonDropdownExpanded by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }

    var showConfirmationDialog by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }

    var successReturnNumber by remember { mutableStateOf<String?>(null) }
    var showSuccessDialog by remember { mutableStateOf(false) }

    LaunchedEffect(sale.id) {
        isLoadingItems = true
        saleItems = viewModel.getSaleItems(sale.id)
        returnableQuantities = viewModel.getReturnableQuantities(sale.id)
        isLoadingItems = false
    }

    val totalReturnItemCount = returnCart.values.sum()
    val totalRefundAmount = returnCart.entries.sumOf { (itemId, qty) ->
        val item = saleItems.find { it.id == itemId }
        (item?.price ?: 0L) * qty.toLong()
    }

    if (showConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSubmitting) showConfirmationDialog = false },
            icon = {
                Icon(Icons.Default.Replay, contentDescription = null, tint = AppColors.GreenPrimary, modifier = Modifier.size(32.dp))
            },
            title = {
                Text("Konfirmasi Retur?", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                    Text(
                        text = "Ringkasan Pengembalian:",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Surface(
                        shape = AppShapes.CardShape,
                        color = AppColors.SurfaceGray,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(AppSpacing.md), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text("Jumlah Item:", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                                Spacer(Modifier.weight(1f))
                                Text("${returnCart.filter { it.value > 0 }.size} barang (${totalReturnItemCount.toInt()} total qty)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            }
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text("Total Nilai Retur:", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                                Spacer(Modifier.weight(1f))
                                Text(formatRupiah(totalRefundAmount), fontWeight = FontWeight.Bold, color = AppColors.GreenPrimary, style = MaterialTheme.typography.bodyMedium)
                            }
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text("Metode Pengembalian:", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                                Spacer(Modifier.weight(1f))
                                Text(
                                    when (sale.paymentMethod.uppercase()) {
                                        "CREDIT" -> "Pengurangan Piutang"
                                        "QRIS" -> "Tunai (Refund QRIS)"
                                        else -> "Tunai"
                                    },
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (!selectedReason.isNullOrBlank()) {
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Text("Alasan:", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                                    Spacer(Modifier.weight(1f))
                                    Text(selectedReason!!, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }

                    Surface(
                        shape = AppShapes.CardShape,
                        color = Color(0xFFFFF8E1),
                        border = BorderStroke(1.dp, Color(0xFFFFD54F)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(AppSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFF57F17), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(AppSpacing.xs))
                            Text(
                                text = "Transaksi retur akan dicatat sebagai transaksi baru. Transaksi penjualan asli tidak diubah.",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF5D4037)
                            )
                        }
                    }

                    if (isSubmitting) {
                        Spacer(Modifier.height(AppSpacing.sm))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = AppColors.GreenPrimary)
                            Spacer(Modifier.width(AppSpacing.sm))
                            Text("Memproses retur...", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isSubmitting) return@Button
                        isSubmitting = true
                        val validCart = returnCart.filter { it.value > 0.0 }.toMap()
                        viewModel.processSaleReturn(
                            saleId = sale.id,
                            itemsToReturn = validCart,
                            reason = selectedReason,
                            notes = notes,
                            onSuccess = { returnId ->
                                isSubmitting = false
                                showConfirmationDialog = false
                                scope.launch {
                                    val returnTx = viewModel.getReturnsForSale(sale.id).find { it.id == returnId }
                                    successReturnNumber = returnTx?.returnNumber ?: "RET-${sale.id}"
                                    showSuccessDialog = true
                                }
                            },
                            onError = { err ->
                                isSubmitting = false
                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                            }
                        )
                    },
                    enabled = !isSubmitting,
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.GreenPrimary)
                ) {
                    Text("Proses Retur", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { if (!isSubmitting) showConfirmationDialog = false },
                    enabled = !isSubmitting
                ) {
                    Text("Batal")
                }
            }
        )
    }

    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = {
                showSuccessDialog = false
                onReturnSuccess()
            },
            icon = {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AppColors.GreenPrimary, modifier = Modifier.size(48.dp))
            },
            title = {
                Text("Retur Berhasil", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                    Text("Transaksi retur penjualan telah berhasil dicatat.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(AppSpacing.xs))
                    Surface(
                        shape = AppShapes.CardShape,
                        color = AppColors.GreenLight,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(AppSpacing.md), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text("Nomor Retur:", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                                Spacer(Modifier.weight(1f))
                                Text(successReturnNumber ?: "-", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            }
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text("Total Pengembalian:", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                                Spacer(Modifier.weight(1f))
                                Text(formatRupiah(totalRefundAmount), fontWeight = FontWeight.Bold, color = AppColors.GreenPrimary, style = MaterialTheme.typography.bodyMedium)
                            }
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text("Metode Refund:", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                                Spacer(Modifier.weight(1f))
                                Text(
                                    when (sale.paymentMethod.uppercase()) {
                                        "CREDIT" -> "Pengurangan Piutang"
                                        else -> "Tunai (Cash)"
                                    },
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSuccessDialog = false
                        onReturnSuccess()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.GreenPrimary)
                ) {
                    Text("Selesai", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = AppSpacing.lg),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppSpacing.lg)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Replay, contentDescription = null, tint = AppColors.GreenPrimary, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(AppSpacing.sm))
                    Column(Modifier.weight(1f)) {
                        Text("Retur Penjualan", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(sale.transactionNumber, style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = AppSpacing.sm))

                if (isLoadingItems) {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AppColors.GreenPrimary)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(bottom = AppSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                    ) {
                        item {
                            Text("Pilih Item & Kuantitas Retur:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                        }

                        items(saleItems) { item ->
                            val maxReturnable = returnableQuantities[item.id] ?: 0.0
                            val currentReturnQty = returnCart[item.id] ?: 0.0
                            val alreadyReturned = (item.quantity - maxReturnable).coerceAtLeast(0.0)

                            Surface(
                                shape = AppShapes.CardShape,
                                color = if (maxReturnable <= 0.0) AppColors.SurfaceGray.copy(alpha = 0.5f) else AppColors.SurfaceGray,
                                border = if (currentReturnQty > 0.0) BorderStroke(1.5.dp, AppColors.GreenPrimary) else null,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(AppSpacing.md)) {
                                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(item.productName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                            Text(
                                                "Harga Jual: ${formatRupiah(item.price)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = AppColors.TextSecondary
                                            )
                                            Text(
                                                "Terjual: ${item.quantity.toInt()} | Sudah diretur: ${alreadyReturned.toInt()}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = AppColors.TextSecondary
                                            )
                                        }

                                        if (maxReturnable <= 0.0) {
                                            Surface(
                                                shape = AppShapes.ChipShape,
                                                color = Color(0xFFFFEBEE),
                                                modifier = Modifier.padding(AppSpacing.xs)
                                            ) {
                                                Text(
                                                    "Sudah diretur semua",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = AppColors.RedExpense,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier.padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs)
                                                )
                                            }
                                        } else {
                                            // Stepper
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                                            ) {
                                                IconButton(
                                                    onClick = {
                                                        val current = returnCart[item.id] ?: 0.0
                                                        if (current > 0.0) {
                                                            returnCart[item.id] = (current - 1.0).coerceAtLeast(0.0)
                                                        }
                                                    },
                                                    enabled = currentReturnQty > 0.0,
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .background(if (currentReturnQty > 0.0) AppColors.GreenLight else Color.Transparent, CircleShape)
                                                ) {
                                                    Icon(Icons.Default.Remove, contentDescription = "Kurang", tint = if (currentReturnQty > 0.0) AppColors.GreenPrimary else Color.Gray, modifier = Modifier.size(16.dp))
                                                }

                                                Text(
                                                    text = "${currentReturnQty.toInt()}",
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    modifier = Modifier.padding(horizontal = AppSpacing.xs)
                                                )

                                                IconButton(
                                                    onClick = {
                                                        val current = returnCart[item.id] ?: 0.0
                                                        if (current < maxReturnable) {
                                                            returnCart[item.id] = current + 1.0
                                                        }
                                                    },
                                                    enabled = currentReturnQty < maxReturnable,
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .background(if (currentReturnQty < maxReturnable) AppColors.GreenPrimary else Color.LightGray, CircleShape)
                                                ) {
                                                    Icon(Icons.Default.Add, contentDescription = "Tambah", tint = Color.White, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }

                                    if (maxReturnable > 0.0) {
                                        Spacer(Modifier.height(AppSpacing.xs))
                                        Row(modifier = Modifier.fillMaxWidth()) {
                                            Text(
                                                "Maksimal dapat diretur: ${maxReturnable.toInt()}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = AppColors.GreenPrimary,
                                                fontWeight = FontWeight.Medium
                                            )
                                            if (currentReturnQty > 0.0) {
                                                Spacer(Modifier.weight(1f))
                                                Text(
                                                    "Refund: ${formatRupiah(item.price * currentReturnQty.toLong())}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = AppColors.GreenPrimary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(Modifier.height(AppSpacing.xs))
                            Text("Alasan Retur (Opsional):", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Surface(
                                    shape = AppShapes.CardShape,
                                    color = AppColors.SurfaceGray,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { reasonDropdownExpanded = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(AppSpacing.md),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = selectedReason ?: "Pilih Alasan (Opsional)",
                                            color = if (selectedReason != null) AppColors.TextPrimary else AppColors.TextSecondary,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                                    }
                                }

                                DropdownMenu(
                                    expanded = reasonDropdownExpanded,
                                    onDismissRequest = { reasonDropdownExpanded = false },
                                    modifier = Modifier.fillMaxWidth(0.85f)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Tanpa Alasan", color = AppColors.TextSecondary) },
                                        onClick = {
                                            selectedReason = null
                                            reasonDropdownExpanded = false
                                        }
                                    )
                                    RETURN_REASONS.forEach { reasonText ->
                                        DropdownMenuItem(
                                            text = { Text(reasonText) },
                                            onClick = {
                                                selectedReason = reasonText
                                                reasonDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(Modifier.height(AppSpacing.xs))
                            Text("Catatan (Opsional):", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                            OutlinedTextField(
                                value = notes,
                                onValueChange = { notes = it },
                                placeholder = { Text("Tulis catatan retur jika ada...", color = AppColors.TextSecondary) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = AppShapes.CardShape
                            )
                        }

                        item {
                            Spacer(Modifier.height(AppSpacing.sm))
                            Surface(
                                shape = AppShapes.CardShape,
                                color = AppColors.GreenLight,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(AppSpacing.md), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        Text("Metode Pengembalian:", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                                        Spacer(Modifier.weight(1f))
                                        Text(
                                            when (sale.paymentMethod.uppercase()) {
                                                "CREDIT" -> "Pengembalian: Piutang"
                                                "QRIS" -> "Pengembalian: Tunai"
                                                else -> "Pengembalian: Tunai"
                                            },
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                    if (sale.paymentMethod.uppercase() == "QRIS") {
                                        Text(
                                            "Transaksi QRIS akan dikembalikan secara tunai.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AppColors.TextSecondary
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("TOTAL REFUND:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                        Spacer(Modifier.weight(1f))
                                        Text(
                                            formatRupiah(totalRefundAmount),
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleLarge,
                                            color = AppColors.GreenPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Action Buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = AppSpacing.md),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = AppShapes.ButtonShape
                        ) {
                            Text("Batal", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                if (totalRefundAmount <= 0L || totalReturnItemCount <= 0.0) {
                                    Toast.makeText(context, "Pilih minimal 1 item untuk diretur", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                showConfirmationDialog = true
                            },
                            enabled = totalRefundAmount > 0L && totalReturnItemCount > 0.0,
                            modifier = Modifier.weight(1.5f),
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.GreenPrimary),
                            shape = AppShapes.ButtonShape
                        ) {
                            Text("Retur Barang", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
