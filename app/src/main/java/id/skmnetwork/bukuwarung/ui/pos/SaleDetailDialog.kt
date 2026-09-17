package id.skmnetwork.bukuwarung.ui.pos

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import id.skmnetwork.bukuwarung.data.local.entity.CustomerEntity
import id.skmnetwork.bukuwarung.data.local.entity.SaleItemEntity
import id.skmnetwork.bukuwarung.data.local.entity.SaleReturnItemEntity
import id.skmnetwork.bukuwarung.data.local.entity.SaleReturnTransactionEntity
import id.skmnetwork.bukuwarung.data.local.entity.SaleTransactionEntity
import id.skmnetwork.bukuwarung.ui.components.AppCard
import id.skmnetwork.bukuwarung.ui.product.ProductViewModel
import id.skmnetwork.bukuwarung.ui.theme.AppColors
import id.skmnetwork.bukuwarung.ui.theme.AppShapes
import id.skmnetwork.bukuwarung.ui.theme.AppSpacing
import id.skmnetwork.bukuwarung.util.formatRupiah
import java.text.SimpleDateFormat
import java.util.Date
import android.widget.Toast
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import id.skmnetwork.bukuwarung.data.preferences.UserSettings
import id.skmnetwork.bukuwarung.printer.PrinterService
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun SaleDetailDialog(
    sale: SaleTransactionEntity,
    customers: List<CustomerEntity> = emptyList(),
    viewModel: ProductViewModel,
    userSettings: UserSettings? = null,
    printerService: PrinterService? = null,
    onDismiss: () -> Unit,
    onRequestReturn: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isPrinting by remember { mutableStateOf(false) }
    var saleItems by remember { mutableStateOf<List<SaleItemEntity>>(emptyList()) }
    var returnTransactions by remember { mutableStateOf<List<SaleReturnTransactionEntity>>(emptyList()) }
    var returnItemsMap by remember { mutableStateOf<Map<Long, List<SaleReturnItemEntity>>>(emptyMap()) }
    var returnableQuantities by remember { mutableStateOf<Map<Long, Double>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(sale.id) {
        isLoading = true
        val items = viewModel.getSaleItems(sale.id)
        val returns = viewModel.getReturnsForSale(sale.id)
        val retItemsMap = mutableMapOf<Long, List<SaleReturnItemEntity>>()
        for (ret in returns) {
            retItemsMap[ret.id] = viewModel.getReturnItems(ret.id)
        }
        val returnable = viewModel.getReturnableQuantities(sale.id)

        saleItems = items
        returnTransactions = returns
        returnItemsMap = retItemsMap
        returnableQuantities = returnable
        isLoading = false
    }

    val customer = remember(sale.customerId, customers) {
        customers.find { it.id == sale.customerId }
    }

    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")) }
    val formattedDate = remember(sale.transactionDate) { dateFormat.format(Date(sale.transactionDate)) }

    val hasAnyReturnableItem = returnableQuantities.values.any { it > 0.0 }

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
                    Icon(Icons.Default.Receipt, contentDescription = null, tint = AppColors.GreenPrimary, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(AppSpacing.sm))
                    Column(Modifier.weight(1f)) {
                        Text("Detail Penjualan", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(sale.transactionNumber, style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = AppSpacing.sm))

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AppColors.GreenPrimary)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(bottom = AppSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                    ) {
                        // Info Card
                        item {
                            Surface(
                                shape = AppShapes.CardShape,
                                color = AppColors.SurfaceGray,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(AppSpacing.md), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        Text("Waktu Transaksi:", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                                        Spacer(Modifier.weight(1f))
                                        Text(formattedDate, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        Text("Pelanggan:", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                                        Spacer(Modifier.weight(1f))
                                        Text(customer?.name ?: "Pelanggan Umum", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Text("Metode Pembayaran:", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                                        Spacer(Modifier.weight(1f))
                                        Surface(
                                            shape = AppShapes.ChipShape,
                                            color = when (sale.paymentMethod.uppercase()) {
                                                "CASH" -> AppColors.GreenLight
                                                "QRIS" -> Color(0xFFE1F5FE)
                                                else -> Color(0xFFFFF3E0)
                                            }
                                        ) {
                                            Text(
                                                text = when (sale.paymentMethod.uppercase()) {
                                                    "CASH" -> "Tunai"
                                                    "QRIS" -> "QRIS"
                                                    "CREDIT" -> "Hutang"
                                                    else -> sale.paymentMethod
                                                },
                                                color = when (sale.paymentMethod.uppercase()) {
                                                    "CASH" -> AppColors.GreenPrimary
                                                    "QRIS" -> Color(0xFF0288D1)
                                                    else -> Color(0xFFE65100)
                                                },
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = AppSpacing.sm, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Sale Items List
                        item {
                            Text("Daftar Item Terjual:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                        }

                        items(saleItems) { item ->
                            val maxReturnable = returnableQuantities[item.id] ?: 0.0
                            val returnedQty = (item.quantity - maxReturnable).coerceAtLeast(0.0)

                            Surface(
                                shape = AppShapes.CardShape,
                                color = AppColors.SurfaceGray,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(AppSpacing.md),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(item.productName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            "${item.quantity.toInt()} × ${formatRupiah(item.price)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = AppColors.TextSecondary
                                        )
                                        if (returnedQty > 0.0) {
                                            Text(
                                                "Sudah diretur: ${returnedQty.toInt()} item",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = AppColors.RedExpense,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                    Text(
                                        formatRupiah(item.subtotal),
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }

                        // Total Amount
                        item {
                            Surface(
                                shape = AppShapes.CardShape,
                                color = AppColors.GreenLight,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(AppSpacing.md),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("TOTAL TRANSAKSI", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        formatRupiah(sale.totalAmount),
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = AppColors.GreenPrimary
                                    )
                                }
                            }
                        }

                        // Return History Section (If Any)
                        if (returnTransactions.isNotEmpty()) {
                            item {
                                Spacer(Modifier.height(AppSpacing.xs))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.History, contentDescription = null, tint = AppColors.RedExpense, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(AppSpacing.xs))
                                    Text("Riwayat Retur (${returnTransactions.size}):", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                                }
                            }

                            items(returnTransactions) { retTx ->
                                val retDateStr = dateFormat.format(Date(retTx.returnDate))
                                val itemsForThisReturn = returnItemsMap[retTx.id] ?: emptyList()

                                Surface(
                                    shape = AppShapes.CardShape,
                                    color = Color(0xFFFFF5F5),
                                    border = BorderStroke(1.dp, Color(0xFFFFCDD2)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(AppSpacing.md), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                                        Row(modifier = Modifier.fillMaxWidth()) {
                                            Text(retTx.returnNumber, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = AppColors.RedExpense)
                                            Spacer(Modifier.weight(1f))
                                            Text(formatRupiah(retTx.totalRefundAmount), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = AppColors.RedExpense)
                                        }
                                        Text("Waktu: $retDateStr", style = MaterialTheme.typography.labelSmall, color = AppColors.TextSecondary)
                                        Text("Metode: ${if (retTx.refundMethod == "CREDIT") "Pengurangan Piutang" else "Tunai"}", style = MaterialTheme.typography.labelSmall, color = AppColors.TextSecondary)
                                        if (!retTx.reason.isNullOrBlank()) {
                                            Text("Alasan: ${retTx.reason}", style = MaterialTheme.typography.labelSmall, color = AppColors.TextPrimary, fontWeight = FontWeight.Medium)
                                        }
                                        if (!retTx.notes.isNullOrBlank()) {
                                            Text("Catatan: ${retTx.notes}", style = MaterialTheme.typography.labelSmall, color = AppColors.TextSecondary)
                                        }

                                        // Item details
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color(0xFFFFCDD2))
                                        itemsForThisReturn.forEach { retItem ->
                                            Row(modifier = Modifier.fillMaxWidth()) {
                                                Text("• ${retItem.productName} (${retItem.quantity.toInt()} item)", style = MaterialTheme.typography.labelSmall, color = AppColors.TextPrimary, modifier = Modifier.weight(1f))
                                                Text(formatRupiah(retItem.subtotal), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Bottom Action Buttons
                    Spacer(Modifier.height(AppSpacing.sm))
                    if (printerService != null) {
                        OutlinedButton(
                            onClick = {
                                if (isPrinting) return@OutlinedButton
                                scope.launch {
                                    isPrinting = true
                                    val receiptData = viewModel.getReceiptData(sale.id, userSettings)
                                    if (receiptData != null) {
                                        val printResult = printerService.printReceipt(receiptData)
                                        if (printResult.isSuccess) {
                                            Toast.makeText(context, "Struk berhasil dicetak ulang", Toast.LENGTH_SHORT).show()
                                        } else {
                                            val err = printResult.exceptionOrNull()?.localizedMessage ?: "Printer tidak terhubung"
                                            Toast.makeText(context, "Gagal mencetak struk: $err", Toast.LENGTH_LONG).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "Data struk transaksi tidak ditemukan", Toast.LENGTH_SHORT).show()
                                    }
                                    isPrinting = false
                                }
                            },
                            enabled = !isPrinting,
                            modifier = Modifier.fillMaxWidth(),
                            shape = AppShapes.ButtonShape
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp), tint = AppColors.GreenPrimary)
                            Spacer(Modifier.width(AppSpacing.xs))
                            Text(
                                if (isPrinting) "Mencetak Ulang..." else "Cetak Ulang Struk",
                                fontWeight = FontWeight.Bold,
                                color = AppColors.GreenPrimary
                            )
                        }
                        Spacer(Modifier.height(AppSpacing.xs))
                    }

                    if (!hasAnyReturnableItem) {
                        Surface(
                            shape = AppShapes.CardShape,
                            color = Color(0xFFFFEBEE),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(modifier = Modifier.padding(AppSpacing.md), contentAlignment = Alignment.Center) {
                                Text(
                                    "Semua item pada transaksi ini telah diretur",
                                    fontWeight = FontWeight.Bold,
                                    color = AppColors.RedExpense,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    } else {
                        Button(
                            onClick = onRequestReturn,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.GreenPrimary),
                            shape = AppShapes.ButtonShape
                        ) {
                            Icon(Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(AppSpacing.xs))
                            Text("Retur Barang", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
