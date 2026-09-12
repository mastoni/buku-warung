package id.skmnetwork.bukuwarung.ui.customer

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
import androidx.compose.material.icons.filled.People
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
import id.skmnetwork.bukuwarung.data.local.entity.CustomerEntity
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
fun CustomersScreen(
    customerViewModel: CustomerViewModel
) {
    val customers by customerViewModel.customers.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }

    var showCustomerDialog by remember { mutableStateOf(false) }
    var customerToEdit by remember { mutableStateOf<CustomerEntity?>(null) }

    var nameInput by remember { mutableStateOf("") }
    var phoneInput by remember { mutableStateOf("") }
    var addressInput by remember { mutableStateOf("") }
    var dialogError by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var selectedCustomerForDetail by remember { mutableStateOf<CustomerEntity?>(null) }

    var showPayDebtDialog by remember { mutableStateOf(false) }
    var paymentAmountInput by remember { mutableStateOf("") }
    var paymentNoteInput by remember { mutableStateOf("") }
    var paymentError by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current

    if (showCustomerDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSaving) showCustomerDialog = false },
            title = { Text(if (customerToEdit == null) "Tambah Pelanggan" else "Edit Pelanggan", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                    if (dialogError != null) Text(dialogError!!, color = AppColors.RedExpense, style = MaterialTheme.typography.bodySmall)
                    AppTextField(value = nameInput, onValueChange = { nameInput = it; dialogError = null }, label = "Nama Pelanggan *")
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
                        if (customerToEdit == null) {
                            customerViewModel.saveCustomer(
                                name = nameInput,
                                phone = phoneInput,
                                address = addressInput,
                                onSuccess = {
                                    isSaving = false
                                    showCustomerDialog = false
                                    nameInput = ""
                                    phoneInput = ""
                                    addressInput = ""
                                    Toast.makeText(context, "Pelanggan berhasil ditambahkan", Toast.LENGTH_SHORT).show()
                                },
                                onError = {
                                    isSaving = false
                                    dialogError = it
                                }
                            )
                        } else {
                            customerViewModel.updateCustomer(
                                id = customerToEdit!!.id,
                                name = nameInput,
                                phone = phoneInput,
                                address = addressInput,
                                onSuccess = {
                                    isSaving = false
                                    showCustomerDialog = false
                                    customerToEdit = null
                                    nameInput = ""
                                    phoneInput = ""
                                    addressInput = ""
                                    Toast.makeText(context, "Pelanggan berhasil diperbarui", Toast.LENGTH_SHORT).show()
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
                    if (customerToEdit != null) {
                        TextButton(
                            onClick = {
                                showCustomerDialog = false
                                showDeleteConfirmDialog = true
                            },
                            enabled = !isSaving
                        ) {
                            Text("Hapus", color = AppColors.RedExpense, fontWeight = FontWeight.Bold)
                        }
                    }
                    TextButton(onClick = { showCustomerDialog = false }, enabled = !isSaving) { Text("Batal") }
                }
            }
        )
    }

    if (showDeleteConfirmDialog && customerToEdit != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Hapus Pelanggan", fontWeight = FontWeight.Bold) },
            text = { Text("Yakin ingin menghapus ${customerToEdit!!.name}?") },
            confirmButton = {
                Button(
                    onClick = {
                        customerViewModel.deleteCustomer(
                            id = customerToEdit!!.id,
                            onSuccess = {
                                showDeleteConfirmDialog = false
                                customerToEdit = null
                                Toast.makeText(context, "Pelanggan berhasil dihapus", Toast.LENGTH_SHORT).show()
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

    if (selectedCustomerForDetail != null) {
        val customer = selectedCustomerForDetail!!
        val customerDebtsFlow = remember(customer.id) { customerViewModel.getDebtsForCustomer(customer.id) }
        val customerDebts by customerDebtsFlow.collectAsStateWithLifecycle()

        val totalOutstandingFlow = remember(customer.id) { customerViewModel.getTotalOutstandingForCustomer(customer.id) }
        val totalOutstanding by totalOutstandingFlow.collectAsStateWithLifecycle()

        val openDebts = customerDebts.filter { it.status == "OPEN" }

        AlertDialog(
            onDismissRequest = { selectedCustomerForDetail = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(customer.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        val currentCustomer = customer
                        selectedCustomerForDetail = null
                        customerToEdit = currentCustomer
                        nameInput = currentCustomer.name
                        phoneInput = currentCustomer.phone ?: ""
                        addressInput = currentCustomer.address ?: ""
                        dialogError = null
                        showCustomerDialog = true
                    }) {
                        Text("Edit", color = AppColors.GreenPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm), modifier = Modifier.fillMaxWidth()) {
                    if (!customer.phone.isNullOrEmpty()) Text("HP: ${customer.phone}", style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
                    if (!customer.address.isNullOrEmpty()) Text("Alamat: ${customer.address}", style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)

                    Spacer(Modifier.height(AppSpacing.xs))
                    AppCard(backgroundColor = AppColors.OrangeWarning) {
                        Row(Modifier.padding(AppSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                            Text("Sisa Piutang: ", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            Text(text = formatRupiah(totalOutstanding ?: 0L), fontWeight = FontWeight.Bold, color = AppColors.RedExpense)
                        }
                    }

                    if (openDebts.isNotEmpty()) {
                        Spacer(Modifier.height(AppSpacing.xs))
                        PrimaryButton(text = "+ Bayar Hutang", onClick = { showPayDebtDialog = true })
                    }

                    Spacer(Modifier.height(AppSpacing.sm))
                    Text("Daftar Hutang / Piutang Pelanggan:", fontWeight = FontWeight.Bold)

                    if (customerDebts.isEmpty()) {
                        Text("Belum ada histori hutang pelanggan", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs), modifier = Modifier.height(180.dp)) {
                            items(customerDebts, key = { it.id }) { debt ->
                                Surface(shape = AppShapes.CardShape, color = AppColors.SurfaceGray, modifier = Modifier.fillMaxWidth()) {
                                    Row(Modifier.padding(AppSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text("Hutang TRX-${debt.id}", fontWeight = FontWeight.Bold)
                                            Text("Total: ${formatRupiah(debt.totalDebt)}", style = MaterialTheme.typography.labelSmall)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = if (debt.status == "PAID") "Lunas" else "Sisa: ${formatRupiah(debt.totalDebt - debt.paidAmount)}",
                                                fontWeight = FontWeight.Bold,
                                                color = if (debt.status == "PAID") AppColors.GreenPrimary else AppColors.RedExpense,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                            Text(
                                                text = if (debt.status == "PAID") "Lunas" else "Belum Lunas",
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
            confirmButton = { TextButton(onClick = { selectedCustomerForDetail = null }) { Text("Tutup", fontWeight = FontWeight.Bold) } }
        )

        if (showPayDebtDialog && openDebts.isNotEmpty()) {
            val debtToPay = openDebts.first()
            val outstanding = debtToPay.totalDebt - debtToPay.paidAmount

            AlertDialog(
                onDismissRequest = { if (!isSaving) showPayDebtDialog = false },
                title = { Text("Bayar Hutang", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                        Text("Total Hutang: ${formatRupiah(debtToPay.totalDebt)}", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                        Text("Sudah Dibayar: ${formatRupiah(debtToPay.paidAmount)}", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                        Text("Sisa Hutang: ${formatRupiah(outstanding)}", fontWeight = FontWeight.Bold, color = AppColors.RedExpense)

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
                            customerViewModel.payDebt(
                                debtId = debtToPay.id,
                                amountStr = paymentAmountInput,
                                note = paymentNoteInput,
                                onSuccess = {
                                    isSaving = false
                                    showPayDebtDialog = false
                                    paymentAmountInput = ""
                                    paymentNoteInput = ""
                                    Toast.makeText(context, "Pembayaran hutang berhasil!", Toast.LENGTH_SHORT).show()
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
                dismissButton = { TextButton(onClick = { showPayDebtDialog = false }, enabled = !isSaving) { Text("Batal") } }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Pelanggan & Piutang", fontWeight = FontWeight.Bold) })
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
                    customerViewModel.search(it)
                },
                label = "Cari pelanggan..."
            )
            Spacer(Modifier.height(AppSpacing.md))
            PrimaryButton(
                text = "+ Tambah Pelanggan",
                onClick = {
                    customerToEdit = null
                    nameInput = ""
                    phoneInput = ""
                    addressInput = ""
                    dialogError = null
                    showCustomerDialog = true
                }
            )
            Spacer(Modifier.height(AppSpacing.lg))

            if (customers.isEmpty()) {
                AppEmptyState(
                    icon = Icons.Default.People,
                    title = "Belum ada pelanggan",
                    description = "Tambah pelanggan untuk mencatat transaksi penjualan kredit & piutang.",
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm), modifier = Modifier.weight(1f)) {
                    items(customers, key = { it.id }) { customer ->
                        val initial = customer.name.trim().take(1).uppercase()
                        val totalOutstandingFlow = remember(customer.id) { customerViewModel.getTotalOutstandingForCustomer(customer.id) }
                        val totalOutstanding by totalOutstandingFlow.collectAsStateWithLifecycle()

                        AppCard(onClick = { selectedCustomerForDetail = customer }) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(AppSpacing.md),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier
                                        .size(44.dp)
                                        .background(AppColors.GreenLight, CircleShape),
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
                                    Text(customer.name, fontWeight = FontWeight.Bold)
                                    if (!customer.phone.isNullOrEmpty()) {
                                        Text(customer.phone, style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
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
