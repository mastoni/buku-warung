package id.skmnetwork.bukuwarung.ui.supplier

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.skmnetwork.bukuwarung.data.local.entity.SupplierEntity
import id.skmnetwork.bukuwarung.ui.components.AppCard
import id.skmnetwork.bukuwarung.ui.components.AppEmptyState
import id.skmnetwork.bukuwarung.ui.components.AppTextField
import id.skmnetwork.bukuwarung.ui.components.PrimaryButton
import id.skmnetwork.bukuwarung.ui.theme.AppColors
import id.skmnetwork.bukuwarung.ui.theme.AppShapes
import id.skmnetwork.bukuwarung.ui.theme.AppSpacing
import id.skmnetwork.bukuwarung.util.formatRupiah

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuppliersScreen(
    supplierViewModel: SupplierViewModel
) {
    val suppliers by supplierViewModel.suppliers.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }

    var showSupplierDialog by remember { mutableStateOf(false) }
    var supplierToEdit by remember { mutableStateOf<SupplierEntity?>(null) }

    var nameInput by remember { mutableStateOf("") }
    var phoneInput by remember { mutableStateOf("") }
    var addressInput by remember { mutableStateOf("") }
    var dialogError by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var selectedSupplierForDetail by remember { mutableStateOf<SupplierEntity?>(null) }

    var showPaySupplierDialog by remember { mutableStateOf(false) }
    var paymentAmountInput by remember { mutableStateOf("") }
    var paymentNoteInput by remember { mutableStateOf("") }
    var paymentError by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current

    if (showSupplierDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSaving) showSupplierDialog = false },
            title = { Text(if (supplierToEdit == null) "Tambah Supplier" else "Edit Supplier", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                    if (dialogError != null) Text(dialogError!!, color = AppColors.RedExpense, style = MaterialTheme.typography.bodySmall)
                    AppTextField(value = nameInput, onValueChange = { nameInput = it; dialogError = null }, label = "Nama Supplier *")
                    AppTextField(value = phoneInput, onValueChange = { phoneInput = it; dialogError = null }, label = "Nomor HP (opsional)", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                    AppTextField(value = addressInput, onValueChange = { addressInput = it; dialogError = null }, label = "Alamat (opsional)")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isSaving) return@Button
                        isSaving = true
                        dialogError = null
                        if (supplierToEdit == null) {
                            supplierViewModel.saveSupplier(
                                name = nameInput,
                                phone = phoneInput,
                                address = addressInput,
                                onSuccess = {
                                    isSaving = false
                                    showSupplierDialog = false
                                    nameInput = ""
                                    phoneInput = ""
                                    addressInput = ""
                                    Toast.makeText(context, "Supplier berhasil ditambahkan", Toast.LENGTH_SHORT).show()
                                },
                                onError = {
                                    isSaving = false
                                    dialogError = it
                                }
                            )
                        } else {
                            supplierViewModel.updateSupplier(
                                id = supplierToEdit!!.id,
                                name = nameInput,
                                phone = phoneInput,
                                address = addressInput,
                                onSuccess = {
                                    isSaving = false
                                    showSupplierDialog = false
                                    supplierToEdit = null
                                    nameInput = ""
                                    phoneInput = ""
                                    addressInput = ""
                                    Toast.makeText(context, "Supplier berhasil diperbarui", Toast.LENGTH_SHORT).show()
                                },
                                onError = {
                                    isSaving = false
                                    dialogError = it
                                }
                            )
                        }
                    },
                    enabled = !isSaving,
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.GreenPrimary)
                ) { Text("Simpan", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                Row {
                    if (supplierToEdit != null) {
                        TextButton(
                            onClick = {
                                showSupplierDialog = false
                                showDeleteConfirmDialog = true
                            },
                            enabled = !isSaving
                        ) {
                            Text("Hapus", color = AppColors.RedExpense, fontWeight = FontWeight.Bold)
                        }
                    }
                    TextButton(onClick = { showSupplierDialog = false }, enabled = !isSaving) { Text("Batal") }
                }
            }
        )
    }

    if (showDeleteConfirmDialog && supplierToEdit != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Hapus Supplier", fontWeight = FontWeight.Bold) },
            text = { Text("Yakin ingin menghapus ${supplierToEdit!!.name}?") },
            confirmButton = {
                Button(
                    onClick = {
                        supplierViewModel.deleteSupplier(
                            id = supplierToEdit!!.id,
                            onSuccess = {
                                showDeleteConfirmDialog = false
                                supplierToEdit = null
                                Toast.makeText(context, "Supplier berhasil dihapus", Toast.LENGTH_SHORT).show()
                            },
                            onError = {
                                showDeleteConfirmDialog = false
                                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.RedExpense)
                ) { Text("Hapus", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) { Text("Batal") }
            }
        )
    }

    if (selectedSupplierForDetail != null) {
        val supplier = selectedSupplierForDetail!!
        val supplierPayablesFlow = remember(supplier.id) { supplierViewModel.getPayablesForSupplier(supplier.id) }
        val supplierPayables by supplierPayablesFlow.collectAsStateWithLifecycle()

        val totalOutstandingFlow = remember(supplier.id) { supplierViewModel.getTotalOutstandingForSupplier(supplier.id) }
        val totalOutstanding by totalOutstandingFlow.collectAsStateWithLifecycle()

        val openPayables = supplierPayables.filter { it.status == "OPEN" }

        AlertDialog(
            onDismissRequest = { selectedSupplierForDetail = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(supplier.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        val currentSupplier = supplier
                        selectedSupplierForDetail = null
                        supplierToEdit = currentSupplier
                        nameInput = currentSupplier.name
                        phoneInput = currentSupplier.phone ?: ""
                        addressInput = currentSupplier.address ?: ""
                        dialogError = null
                        showSupplierDialog = true
                    }) {
                        Text("Edit", color = AppColors.GreenPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm), modifier = Modifier.fillMaxWidth()) {
                    if (!supplier.phone.isNullOrEmpty()) Text("HP: ${supplier.phone}", style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
                    if (!supplier.address.isNullOrEmpty()) Text("Alamat: ${supplier.address}", style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)

                    Spacer(Modifier.height(AppSpacing.xs))
                    AppCard(backgroundColor = Color(0xFFFFE8E8)) {
                        Row(Modifier.padding(AppSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                            Text("Sisa Hutang Supplier: ", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            Text(text = formatRupiah(totalOutstanding ?: 0L), fontWeight = FontWeight.Bold, color = AppColors.RedExpense)
                        }
                    }

                    if (openPayables.isNotEmpty()) {
                        Spacer(Modifier.height(AppSpacing.xs))
                        PrimaryButton(text = "+ Bayar Supplier", onClick = { showPaySupplierDialog = true })
                    }

                    Spacer(Modifier.height(AppSpacing.sm))
                    Text("Daftar Hutang / Tagihan Supplier:", fontWeight = FontWeight.Bold)

                    if (supplierPayables.isEmpty()) {
                        Text("Belum ada histori hutang supplier", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs), modifier = Modifier.height(180.dp)) {
                            items(supplierPayables, key = { it.id }) { payable ->
                                Surface(shape = AppShapes.CardShape, color = AppColors.SurfaceGray, modifier = Modifier.fillMaxWidth()) {
                                    Row(Modifier.padding(AppSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text("Belanja PUR-${payable.id}", fontWeight = FontWeight.Bold)
                                            Text("Total: ${formatRupiah(payable.totalDebt)}", style = MaterialTheme.typography.labelSmall)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = if (payable.status == "PAID") "Lunas" else "Sisa: ${formatRupiah(payable.totalDebt - payable.paidAmount)}",
                                                fontWeight = FontWeight.Bold,
                                                color = if (payable.status == "PAID") AppColors.GreenPrimary else AppColors.RedExpense,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                            Text(
                                                text = if (payable.status == "PAID") "Lunas" else "Belum Lunas",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = AppColors.TextSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selectedSupplierForDetail = null }) { Text("Tutup", fontWeight = FontWeight.Bold) } }
        )

        if (showPaySupplierDialog && openPayables.isNotEmpty()) {
            val payableToPay = openPayables.first()
            val outstanding = payableToPay.totalDebt - payableToPay.paidAmount

            AlertDialog(
                onDismissRequest = { if (!isSaving) showPaySupplierDialog = false },
                title = { Text("Bayar Supplier", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                        Text("Total Tagihan: ${formatRupiah(payableToPay.totalDebt)}", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                        Text("Sudah Dibayar: ${formatRupiah(payableToPay.paidAmount)}", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                        Text("Sisa Tagihan: ${formatRupiah(outstanding)}", fontWeight = FontWeight.Bold, color = AppColors.RedExpense)

                        if (paymentError != null) Text(paymentError!!, color = AppColors.RedExpense, style = MaterialTheme.typography.bodySmall)

                        AppTextField(
                            value = paymentAmountInput,
                            onValueChange = { paymentAmountInput = it; paymentError = null },
                            label = "Jumlah Pembayaran (Rp) *",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        AppTextField(
                            value = paymentNoteInput,
                            onValueChange = { paymentNoteInput = it; paymentError = null },
                            label = "Catatan (opsional)"
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (isSaving) return@Button
                            isSaving = true
                            paymentError = null
                            supplierViewModel.paySupplier(
                                payableId = payableToPay.id,
                                amountStr = paymentAmountInput,
                                note = paymentNoteInput,
                                onSuccess = {
                                    isSaving = false
                                    showPaySupplierDialog = false
                                    paymentAmountInput = ""
                                    paymentNoteInput = ""
                                    Toast.makeText(context, "Pembayaran supplier berhasil!", Toast.LENGTH_SHORT).show()
                                },
                                onError = {
                                    isSaving = false
                                    paymentError = it
                                }
                            )
                        },
                        enabled = !isSaving,
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.GreenPrimary)
                    ) { Text("Simpan Pembayaran", fontWeight = FontWeight.Bold) }
                },
                dismissButton = { TextButton(onClick = { showPaySupplierDialog = false }, enabled = !isSaving) { Text("Batal") } }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Supplier & Hutang", fontWeight = FontWeight.Bold) })
        },
        containerColor = Color.White
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = AppSpacing.lg)
        ) {
            AppTextField(
                value = query,
                onValueChange = {
                    query = it
                    supplierViewModel.search(it)
                },
                label = "Cari supplier..."
            )
            Spacer(Modifier.height(AppSpacing.md))
            PrimaryButton(
                text = "+ Tambah Supplier",
                onClick = {
                    supplierToEdit = null
                    nameInput = ""
                    phoneInput = ""
                    addressInput = ""
                    dialogError = null
                    showSupplierDialog = true
                }
            )
            Spacer(Modifier.height(AppSpacing.lg))

            if (suppliers.isEmpty()) {
                AppEmptyState(
                    icon = Icons.Default.LocalShipping,
                    title = "Belum ada supplier",
                    description = "Tambah supplier untuk mencatat transaksi pembelian barang & hutang usaha.",
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm), modifier = Modifier.weight(1f)) {
                    items(suppliers, key = { it.id }) { supplier ->
                        val initial = supplier.name.trim().take(1).uppercase()
                        val totalOutstandingFlow = remember(supplier.id) { supplierViewModel.getTotalOutstandingForSupplier(supplier.id) }
                        val totalOutstanding by totalOutstandingFlow.collectAsStateWithLifecycle()

                        AppCard(onClick = { selectedSupplierForDetail = supplier }) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(AppSpacing.md),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier
                                        .size(44.dp)
                                        .background(AppColors.BlueCash, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = initial,
                                        fontWeight = FontWeight.Bold,
                                        color = AppColors.GreenPrimary,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                                Spacer(Modifier.width(AppSpacing.md))
                                Column(Modifier.weight(1f)) {
                                    Text(supplier.name, fontWeight = FontWeight.Bold)
                                    if (!supplier.phone.isNullOrEmpty()) {
                                        Text(supplier.phone, style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    val debtValue = totalOutstanding ?: 0L
                                    Text(
                                        text = if (debtValue > 0) formatRupiah(debtValue) else "Lunas",
                                        fontWeight = FontWeight.Bold,
                                        color = if (debtValue > 0) AppColors.RedExpense else AppColors.GreenPrimary,
                                        style = MaterialTheme.typography.bodyMedium
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
