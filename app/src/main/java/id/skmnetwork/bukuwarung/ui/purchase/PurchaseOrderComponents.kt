package id.skmnetwork.bukuwarung.ui.purchase

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.data.local.entity.PurchaseOrderEntity
import id.skmnetwork.bukuwarung.data.local.entity.PurchaseOrderItemEntity
import id.skmnetwork.bukuwarung.data.local.entity.PurchaseOrderStatus
import id.skmnetwork.bukuwarung.data.local.entity.SupplierEntity
import id.skmnetwork.bukuwarung.data.repository.PurchaseOrderItemInput
import id.skmnetwork.bukuwarung.domain.receipt.ReceiptPaperWidth
import id.skmnetwork.bukuwarung.printer.PrinterService
import id.skmnetwork.bukuwarung.purchase.PurchaseOrderReceiptFormatter
import id.skmnetwork.bukuwarung.ui.theme.AppColors
import id.skmnetwork.bukuwarung.util.formatRupiah
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Gate G13.5 — Purchase Order Tab Content: filterable list of POs, creation CTA, and empty states.
 */
@Composable
fun PurchaseOrderTabContent(
    poViewModel: PurchaseOrderViewModel,
    dbProducts: List<ProductEntity>,
    suppliers: List<SupplierEntity>,
    shopName: String,
    printerService: PrinterService? = null,
    supplierLabel: String = "Supplier",
    purchaseLabel: String = "Pembelian",
    onOpenCreatePo: () -> Unit
) {
    val orders by poViewModel.filteredOrders.collectAsStateWithLifecycle()
    val activeFilter by poViewModel.filterStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedOrderForDetail by remember { mutableStateOf<Long?>(null) }
    var orderToEdit by remember { mutableStateOf<Pair<PurchaseOrderEntity, List<PurchaseOrderItemEntity>>?>(null) }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Top Filter Bar & Create Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                val filterOptions = listOf(
                    "ALL" to "Semua",
                    "DRAFT" to "Draft",
                    "ORDERED" to "Dipesan",
                    "RECEIVED" to "Diterima",
                    "CANCELLED" to "Dibatalkan"
                )
                items(filterOptions) { (key, label) ->
                    val isSelected = activeFilter == key
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) AppColors.GreenPrimary else Color(0xFFF1F5F2),
                        border = BorderStroke(1.dp, if (isSelected) AppColors.GreenPrimary else Color(0xFFE2E8F0)),
                        modifier = Modifier.clickable { poViewModel.setFilter(key) }
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color.White else AppColors.TextPrimary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            Button(
                onClick = onOpenCreatePo,
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.GreenPrimary),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Buat Pesanan", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        if (orders.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE8F5E9)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ReceiptLong,
                            contentDescription = null,
                            tint = AppColors.GreenPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Text(
                        text = if (activeFilter == "ALL") "Belum ada pesanan supplier" else "Tidak ada pesanan berstatus ini",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = AppColors.TextPrimary
                    )
                    Text(
                        text = "Buat Purchase Order untuk mencatat daftar pesanan ke $supplierLabel sebelum barang diterima.",
                        fontSize = 12.5.sp,
                        color = AppColors.TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = onOpenCreatePo,
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.GreenPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Buat Pesanan Sekarang", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(orders, key = { it.id }) { order ->
                    PurchaseOrderCard(
                        order = order,
                        onClick = { selectedOrderForDetail = order.id }
                    )
                }
            }
        }
    }

    // Detail Dialog
    if (selectedOrderForDetail != null) {
        val orderId = selectedOrderForDetail!!
        PurchaseOrderDetailDialog(
            orderId = orderId,
            poViewModel = poViewModel,
            shopName = shopName,
            printerService = printerService,
            supplierLabel = supplierLabel,
            onDismiss = { selectedOrderForDetail = null },
            onEditDraft = { order, items ->
                selectedOrderForDetail = null
                orderToEdit = Pair(order, items)
            }
        )
    }

    // Edit Draft Dialog
    if (orderToEdit != null) {
        val (existingOrder, existingItems) = orderToEdit!!
        CreateEditPurchaseOrderDialog(
            existingOrder = existingOrder,
            existingItems = existingItems,
            dbProducts = dbProducts,
            suppliers = suppliers,
            supplierLabel = supplierLabel,
            onDismiss = { orderToEdit = null },
            onSave = { supplierId, items, notes ->
                scope.launch {
                    val res = poViewModel.updateDraftOrder(
                        orderId = existingOrder.id,
                        supplierId = supplierId,
                        items = items,
                        notes = notes
                    )
                    if (res.isSuccess) {
                        Toast.makeText(context, "Draft pesanan berhasil diperbarui", Toast.LENGTH_SHORT).show()
                        orderToEdit = null
                    } else {
                        Toast.makeText(context, res.exceptionOrNull()?.message ?: "Gagal memperbarui", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }
}

/**
 * Gate G13.5 — Card component displaying summary of a Purchase Order item in the list.
 */
@Composable
fun PurchaseOrderCard(
    order: PurchaseOrderEntity,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")) }
    val formattedDate = remember(order.createdAt) { dateFormat.format(Date(order.createdAt)) }

    val (statusLabel, statusBg, statusColor) = when (order.status) {
        PurchaseOrderStatus.DRAFT.name -> Triple("Draft", Color(0xFFFFF3E0), Color(0xFFE65100))
        PurchaseOrderStatus.ORDERED.name -> Triple("Dipesan", Color(0xFFE3F2FD), Color(0xFF1565C0))
        PurchaseOrderStatus.RECEIVED.name -> Triple("Diterima", Color(0xFFE8F5E9), Color(0xFF2E7D32))
        PurchaseOrderStatus.CANCELLED.name -> Triple("Dibatalkan", Color(0xFFFFEBEE), Color(0xFFC62828))
        else -> Triple(order.status, Color(0xFFF5F5F5), Color(0xFF616161))
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE8EDE9)),
        shadowElevation = 0.5.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = order.orderNumber,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = AppColors.TextPrimary
                    )
                    Text(
                        text = formattedDate,
                        fontSize = 11.5.sp,
                        color = AppColors.TextSecondary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = statusBg
                ) {
                    Text(
                        text = statusLabel,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            HorizontalDivider(color = Color(0xFFF1F5F2))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.People,
                        contentDescription = null,
                        tint = AppColors.TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = order.supplierNameSnapshot,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = AppColors.TextPrimary
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Estimasi Total",
                        fontSize = 10.5.sp,
                        color = AppColors.TextSecondary
                    )
                    Text(
                        text = formatRupiah(order.totalEstimatedAmount),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = AppColors.GreenPrimary
                    )
                }
            }
        }
    }
}

/**
 * Gate G13.5 — Dialog for viewing full Purchase Order details and triggering actions
 * (WhatsApp share, thermal printing, PDF export, marking ordered, cancellation).
 */
@Composable
fun PurchaseOrderDetailDialog(
    orderId: Long,
    poViewModel: PurchaseOrderViewModel,
    shopName: String,
    printerService: PrinterService? = null,
    supplierLabel: String = "Supplier",
    onDismiss: () -> Unit,
    onEditDraft: (PurchaseOrderEntity, List<PurchaseOrderItemEntity>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var orderData by remember { mutableStateOf<Pair<PurchaseOrderEntity, List<PurchaseOrderItemEntity>>?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showCancelConfirmDialog by remember { mutableStateOf(false) }
    var showMarkOrderedConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(orderId) {
        isLoading = true
        orderData = poViewModel.loadOrderDetails(orderId)
        isLoading = false
    }

    if (isLoading || orderData == null) {
        Dialog(onDismissRequest = onDismiss) {
            Surface(shape = RoundedCornerShape(16.dp), color = Color.White, modifier = Modifier.padding(24.dp)) {
                Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AppColors.GreenPrimary)
                }
            }
        }
        return
    }

    val (order, items) = orderData!!

    val (statusLabel, statusBg, statusColor) = when (order.status) {
        PurchaseOrderStatus.DRAFT.name -> Triple("Draft", Color(0xFFFFF3E0), Color(0xFFE65100))
        PurchaseOrderStatus.ORDERED.name -> Triple("Dipesan", Color(0xFFE3F2FD), Color(0xFF1565C0))
        PurchaseOrderStatus.RECEIVED.name -> Triple("Diterima", Color(0xFFE8F5E9), Color(0xFF2E7D32))
        PurchaseOrderStatus.CANCELLED.name -> Triple("Dibatalkan", Color(0xFFFFEBEE), Color(0xFFC62828))
        else -> Triple(order.status, Color(0xFFF5F5F5), Color(0xFF616161))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 680.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Detail Pesanan",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = AppColors.TextPrimary
                        )
                        Text(
                            text = order.orderNumber,
                            fontSize = 12.sp,
                            color = AppColors.TextSecondary
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = statusBg
                    ) {
                        Text(
                            text = statusLabel,
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFEFF3F0))

                // Scrollable Content
                LazyColumn(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Supplier Info Card
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF8FAF8),
                            border = BorderStroke(1.dp, Color(0xFFE8EDE9)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(text = "$supplierLabel:", fontSize = 11.5.sp, color = AppColors.TextSecondary)
                                Text(text = order.supplierNameSnapshot, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                if (!order.supplierPhoneSnapshot.isNullOrBlank()) {
                                    Text(text = "Telp: ${order.supplierPhoneSnapshot}", fontSize = 12.sp, color = AppColors.TextSecondary)
                                }
                            }
                        }
                    }

                    // Items List
                    item {
                        Text(
                            text = "Daftar Barang (${items.size} item):",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = AppColors.TextPrimary
                        )
                    }

                    items(items) { item ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFFAFCFA),
                            border = BorderStroke(1.dp, Color(0xFFEFF3F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.productName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    val qtyStr = PurchaseOrderReceiptFormatter.formatQuantity(item.orderedQuantity, item.unit)
                                    Text(
                                        text = "$qtyStr × ${formatRupiah(item.estimatedPrice)}",
                                        fontSize = 11.5.sp,
                                        color = AppColors.TextSecondary
                                    )
                                }
                                Text(
                                    text = formatRupiah(item.estimatedSubtotal),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = AppColors.TextPrimary
                                )
                            }
                        }
                    }

                    // Notes
                    if (!order.notes.isNullOrBlank()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFFFFDE7),
                                border = BorderStroke(1.dp, Color(0xFFFFF59D)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("Catatan:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF57F17))
                                    Text(order.notes, fontSize = 12.sp, color = Color(0xFF424242))
                                }
                            }
                        }
                    }

                    // Total Row
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFE8F5E9),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Estimasi Total Pesanan", fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = AppColors.GreenPrimary)
                                Text(formatRupiah(order.totalEstimatedAmount), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AppColors.GreenPrimary)
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFEFF3F0))

                // Action Buttons Matrix
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Sharing & Print Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                poViewModel.shareWhatsApp(context, order, items, shopName)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("WhatsApp", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                if (printerService != null) {
                                    scope.launch {
                                        val res = poViewModel.printOrder(printerService, order, items, shopName)
                                        if (res.isSuccess) {
                                            Toast.makeText(context, "Berhasil mencetak Purchase Order", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Gagal cetak: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                } else {
                                    Toast.makeText(context, "Printer belum dikonfigurasi", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Print", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val res = poViewModel.exportAndSharePdf(context, order, items, shopName)
                                    if (res.isFailure) {
                                        Toast.makeText(context, "Gagal PDF: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Lifecycle Actions
                    if (order.status == PurchaseOrderStatus.DRAFT.name) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onEditDraft(order, items) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Edit Draft", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { showMarkOrderedConfirmDialog = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                            ) {
                                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                Spacer(Modifier.width(4.dp))
                                Text("Sudah Dipesan", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }

                        TextButton(
                            onClick = { showCancelConfirmDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Batalkan Pesanan", color = Color(0xFFC62828), fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
                        }
                    } else if (order.status == PurchaseOrderStatus.ORDERED.name) {
                        TextButton(
                            onClick = { showCancelConfirmDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Batalkan Pesanan", color = Color(0xFFC62828), fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
                        }
                    }

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Tutup", fontWeight = FontWeight.Bold, color = AppColors.TextSecondary)
                    }
                }
            }
        }
    }

    // Mark as Ordered Confirmation
    if (showMarkOrderedConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showMarkOrderedConfirmDialog = false },
            title = { Text("Tandai Sudah Dipesan?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Status akan berubah menjadi 'Dipesan'. Dokumen pesanan tidak dapat diedit kembali setelah dikirim ke supplier.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showMarkOrderedConfirmDialog = false
                        scope.launch {
                            val res = poViewModel.markOrderOrdered(order.id)
                            if (res.isSuccess) {
                                Toast.makeText(context, "Pesanan berhasil ditandai 'Dipesan'", Toast.LENGTH_SHORT).show()
                                orderData = poViewModel.loadOrderDetails(order.id)
                            } else {
                                Toast.makeText(context, res.exceptionOrNull()?.message ?: "Gagal", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                ) {
                    Text("Ya, Sudah Dipesan", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showMarkOrderedConfirmDialog = false }) {
                    Text("Batal", color = AppColors.TextSecondary)
                }
            }
        )
    }

    // Cancel Order Confirmation
    if (showCancelConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showCancelConfirmDialog = false },
            title = { Text("Batalkan Pesanan Ini?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Pesanan yang sudah dibatalkan tidak dapat diubah kembali menjadi draft atau dipesan.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCancelConfirmDialog = false
                        scope.launch {
                            val res = poViewModel.cancelOrder(order.id)
                            if (res.isSuccess) {
                                Toast.makeText(context, "Pesanan berhasil dibatalkan", Toast.LENGTH_SHORT).show()
                                orderData = poViewModel.loadOrderDetails(order.id)
                            } else {
                                Toast.makeText(context, res.exceptionOrNull()?.message ?: "Gagal", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                ) {
                    Text("Ya, Batalkan", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirmDialog = false }) {
                    Text("Kembali", color = AppColors.TextSecondary)
                }
            }
        )
    }
}

/**
 * Gate G13.5 — Dialog / Form to Create a new Purchase Order or Edit an existing DRAFT.
 */
@Composable
fun CreateEditPurchaseOrderDialog(
    existingOrder: PurchaseOrderEntity? = null,
    existingItems: List<PurchaseOrderItemEntity> = emptyList(),
    dbProducts: List<ProductEntity>,
    suppliers: List<SupplierEntity>,
    supplierLabel: String = "Supplier",
    onDismiss: () -> Unit,
    onSave: (supplierId: Long, items: List<PurchaseOrderItemInput>, notes: String?) -> Unit
) {
    var selectedSupplier by remember {
        mutableStateOf(
            if (existingOrder != null) suppliers.find { it.id == existingOrder.supplierId } else null
        )
    }
    var showSupplierPicker by remember { mutableStateOf(false) }
    var showProductPicker by remember { mutableStateOf(false) }
    var notesText by remember { mutableStateOf(existingOrder?.notes ?: "") }

    // Draft line items list: productId -> (quantity, estimatedPrice, unit, name)
    data class DraftLineItem(
        val productId: Long,
        val productName: String,
        val unit: String,
        var quantity: Double,
        var estimatedPrice: Long
    )

    val draftItems = remember {
        mutableStateListOf<DraftLineItem>().apply {
            if (existingItems.isNotEmpty()) {
                existingItems.forEach { item ->
                    add(
                        DraftLineItem(
                            productId = item.productId,
                            productName = item.productName,
                            unit = item.unit,
                            quantity = item.orderedQuantity,
                            estimatedPrice = item.estimatedPrice
                        )
                    )
                }
            }
        }
    }

    val totalEstimated = draftItems.sumOf { (it.quantity * it.estimatedPrice).toLong() }
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = 720.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (existingOrder != null) "Edit Draft Pesanan" else "Buat Pesanan ($supplierLabel)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = AppColors.TextPrimary
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup", tint = AppColors.TextSecondary)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color(0xFFEFF3F0))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Supplier Selection Section
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "1. Pilih $supplierLabel *",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = AppColors.TextPrimary
                            )

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (selectedSupplier != null) Color(0xFFF0FDF4) else Color(0xFFF8FAF8),
                                border = BorderStroke(1.dp, if (selectedSupplier != null) AppColors.GreenPrimary else Color(0xFFE2E8F0)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showSupplierPicker = true }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (selectedSupplier != null) {
                                        Column {
                                            Text(
                                                text = selectedSupplier!!.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = AppColors.TextPrimary
                                            )
                                            if (!selectedSupplier!!.phone.isNullOrBlank()) {
                                                Text(
                                                    text = "Telp: ${selectedSupplier!!.phone}",
                                                    fontSize = 12.sp,
                                                    color = AppColors.TextSecondary
                                                )
                                            }
                                        }
                                    } else {
                                        Text(
                                            text = "Sentuh untuk memilih $supplierLabel...",
                                            fontSize = 13.sp,
                                            color = AppColors.TextSecondary
                                        )
                                    }

                                    Icon(
                                        imageVector = Icons.Default.ArrowForward,
                                        contentDescription = null,
                                        tint = if (selectedSupplier != null) AppColors.GreenPrimary else AppColors.TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Product Items Section
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "2. Daftar Barang Pesanan *",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = AppColors.TextPrimary
                            )

                            Button(
                                onClick = { showProductPicker = true },
                                colors = ButtonDefaults.buttonColors(containerColor = AppColors.GreenPrimary),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                                Spacer(Modifier.width(4.dp))
                                Text("Tambah Produk", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }

                    if (draftItems.isEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFFAFCFA),
                                border = BorderStroke(1.dp, Color(0xFFEFF3F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Belum ada produk ditambahkan ke pesanan.",
                                        fontSize = 12.5.sp,
                                        color = AppColors.TextSecondary,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        items(draftItems.size) { index ->
                            val item = draftItems[index]
                            var qtyInput by remember(item.quantity) {
                                mutableStateOf(
                                    if (item.quantity % 1.0 == 0.0) item.quantity.toLong().toString() else item.quantity.toString()
                                )
                            }
                            var priceInput by remember(item.estimatedPrice) {
                                mutableStateOf(item.estimatedPrice.toString())
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFFAFCFA),
                                border = BorderStroke(1.dp, Color(0xFFE8EDE9)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${index + 1}. ${item.productName}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.5.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = { draftItems.removeAt(index) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = Color(0xFFC62828), modifier = Modifier.size(18.dp))
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Quantity Input
                                        OutlinedTextField(
                                            value = qtyInput,
                                            onValueChange = { newVal ->
                                                val clean = newVal.replace(',', '.')
                                                qtyInput = clean
                                                val parsed = clean.toDoubleOrNull()
                                                if (parsed != null && parsed > 0.0) {
                                                    item.quantity = parsed
                                                }
                                            },
                                            label = { Text("Jumlah (${item.unit})", fontSize = 10.sp) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            singleLine = true,
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp)
                                        )

                                        // Estimated Price Input
                                        OutlinedTextField(
                                            value = priceInput,
                                            onValueChange = { newVal ->
                                                priceInput = newVal
                                                val parsed = newVal.toLongOrNull()
                                                if (parsed != null && parsed >= 0L) {
                                                    item.estimatedPrice = parsed
                                                }
                                            },
                                            label = { Text("Harga Estimasi (Rp)", fontSize = 10.sp) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            modifier = Modifier.weight(1.3f),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                    }

                                    // Line Subtotal
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        Text(
                                            text = "Subtotal: ${formatRupiah((item.quantity * item.estimatedPrice).toLong())}",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp,
                                            color = AppColors.TextPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Notes Section
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "3. Catatan Pesanan (Opsional)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = AppColors.TextPrimary
                            )
                            OutlinedTextField(
                                value = notesText,
                                onValueChange = { notesText = it },
                                placeholder = { Text("Contoh: Tolong kirim pagi sebelum jam 10...", fontSize = 12.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 3,
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }

                    // Total Calculation Preview
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFE8F5E9),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Estimasi Total Pesanan",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    color = AppColors.GreenPrimary
                                )
                                Text(
                                    text = formatRupiah(totalEstimated),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = AppColors.GreenPrimary
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color(0xFFEFF3F0))

                // Bottom Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Batal", fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = {
                            if (selectedSupplier == null) {
                                Toast.makeText(context, "Silakan pilih $supplierLabel terlebih dahulu", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (draftItems.isEmpty()) {
                                Toast.makeText(context, "Tambahkan minimal 1 barang pesanan", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            val itemsInput = draftItems.map {
                                PurchaseOrderItemInput(
                                    productId = it.productId,
                                    quantity = it.quantity,
                                    estimatedPrice = it.estimatedPrice
                                )
                            }
                            onSave(
                                selectedSupplier!!.id,
                                itemsInput,
                                notesText.trim().ifBlank { null }
                            )
                        },
                        modifier = Modifier.weight(1.3f),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.GreenPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Simpan Draft", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }

    // Supplier Picker
    if (showSupplierPicker) {
        AlertDialog(
            onDismissRequest = { showSupplierPicker = false },
            title = { Text("Pilih $supplierLabel", fontWeight = FontWeight.Bold) },
            text = {
                if (suppliers.isEmpty()) {
                    Text("Belum ada $supplierLabel tersimpan. Tambahkan $supplierLabel terlebih dahulu di menu $supplierLabel.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.height(260.dp)) {
                        items(suppliers) { sup ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFF8FAF8),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedSupplier = sup
                                        showSupplierPicker = false
                                    }
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(sup.name, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                                    if (!sup.phone.isNullOrBlank()) {
                                        Text("Telp: ${sup.phone}", fontSize = 11.5.sp, color = AppColors.TextSecondary)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSupplierPicker = false }) {
                    Text("Tutup", color = AppColors.GreenPrimary, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Product Picker Dialog
    if (showProductPicker) {
        ProductPickerForPoDialog(
            products = dbProducts,
            existingSelectedProductIds = draftItems.map { it.productId }.toSet(),
            onDismiss = { showProductPicker = false },
            onSelectProduct = { product ->
                draftItems.add(
                    DraftLineItem(
                        productId = product.id,
                        productName = product.name,
                        unit = product.unit,
                        quantity = 1.0,
                        estimatedPrice = product.purchasePrice
                    )
                )
                showProductPicker = false
            }
        )
    }
}

/**
 * Gate G13.5 — Product Picker with Search for adding products to Purchase Order.
 */
@Composable
fun ProductPickerForPoDialog(
    products: List<ProductEntity>,
    existingSelectedProductIds: Set<Long>,
    onDismiss: () -> Unit,
    onSelectProduct: (ProductEntity) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filtered = remember(searchQuery, products) {
        if (searchQuery.isBlank()) {
            products
        } else {
            products.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pilih Produk", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Cari nama produk...", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                if (filtered.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Produk tidak ditemukan", color = AppColors.TextSecondary, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(filtered) { prod ->
                            val isAlreadyAdded = existingSelectedProductIds.contains(prod.id)
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isAlreadyAdded) Color(0xFFF1F5F2) else Color(0xFFFAFCFA),
                                border = BorderStroke(1.dp, Color(0xFFE8EDE9)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !isAlreadyAdded) {
                                        onSelectProduct(prod)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = prod.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (isAlreadyAdded) AppColors.TextSecondary else AppColors.TextPrimary
                                        )
                                        Text(
                                            text = "Harga Beli Standar: ${formatRupiah(prod.purchasePrice)} / ${prod.unit}",
                                            fontSize = 11.5.sp,
                                            color = AppColors.TextSecondary
                                        )
                                    }

                                    if (isAlreadyAdded) {
                                        Text("Sudah Dipilih", fontSize = 11.sp, color = AppColors.TextSecondary, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup", color = AppColors.GreenPrimary, fontWeight = FontWeight.Bold)
            }
        }
    )
}
