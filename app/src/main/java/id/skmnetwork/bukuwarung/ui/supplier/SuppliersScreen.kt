package id.skmnetwork.bukuwarung.ui.supplier

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.skmnetwork.bukuwarung.data.local.entity.SupplierEntity
import id.skmnetwork.bukuwarung.data.local.entity.SupplierPayableEntity
import id.skmnetwork.bukuwarung.ui.components.AppTextField
import id.skmnetwork.bukuwarung.ui.theme.AppColors
import id.skmnetwork.bukuwarung.util.formatRupiah
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    // ==========================================
    // 1. ADD / EDIT SUPPLIER DIALOG
    // ==========================================
    if (showSupplierDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSaving) showSupplierDialog = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = Color.White,
            title = {
                Text(
                    text = if (supplierToEdit == null) "Tambah Supplier" else "Edit Supplier",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = AppColors.TextPrimary
                    )
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (dialogError != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFFEBEE),
                            border = BorderStroke(1.dp, Color(0xFFFFCDD2)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = dialogError!!,
                                color = Color(0xFFD32F2F),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    AppTextField(
                        value = nameInput,
                        onValueChange = {
                            nameInput = it
                            dialogError = null
                        },
                        label = "Nama Supplier *"
                    )

                    AppTextField(
                        value = phoneInput,
                        onValueChange = {
                            phoneInput = it
                            dialogError = null
                        },
                        label = "Nomor HP (opsional)",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )

                    AppTextField(
                        value = addressInput,
                        onValueChange = {
                            addressInput = it
                            dialogError = null
                        },
                        label = "Alamat (opsional)"
                    )
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
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.GreenPrimary,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = if (isSaving) "Menyimpan..." else "Simpan",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            },
            dismissButton = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (supplierToEdit != null) {
                        TextButton(
                            onClick = {
                                showSupplierDialog = false
                                showDeleteConfirmDialog = true
                            },
                            enabled = !isSaving
                        ) {
                            Text("Hapus", color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                    TextButton(
                        onClick = { showSupplierDialog = false },
                        enabled = !isSaving
                    ) {
                        Text("Batal", color = AppColors.TextSecondary, fontSize = 13.sp)
                    }
                }
            }
        )
    }

    // ==========================================
    // 2. DELETE CONFIRMATION DIALOG
    // ==========================================
    if (showDeleteConfirmDialog && supplierToEdit != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = Color.White,
            title = {
                Text("Hapus Supplier", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = AppColors.TextPrimary)
            },
            text = {
                Text(
                    text = "Yakin ingin menghapus \"${supplierToEdit!!.name}\"?",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp, color = AppColors.TextSecondary)
                )
            },
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
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Text("Hapus", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Batal", color = AppColors.TextSecondary, fontSize = 13.sp)
                }
            }
        )
    }

    // ==========================================
    // 3. SUPPLIER DETAIL & PAYABLE HISTORY DIALOG
    // ==========================================
    if (selectedSupplierForDetail != null) {
        val supplier = selectedSupplierForDetail!!
        val supplierPayablesFlow = remember(supplier.id) { supplierViewModel.getPayablesForSupplier(supplier.id) }
        val supplierPayables by supplierPayablesFlow.collectAsStateWithLifecycle()

        val totalOutstandingFlow = remember(supplier.id) { supplierViewModel.getTotalOutstandingForSupplier(supplier.id) }
        val totalOutstanding by totalOutstandingFlow.collectAsStateWithLifecycle()

        val openPayables = supplierPayables.filter { it.status == "OPEN" }

        AlertDialog(
            onDismissRequest = { selectedSupplierForDetail = null },
            shape = RoundedCornerShape(16.dp),
            containerColor = Color.White,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE8F5E9)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = supplier.name.trim().take(1).uppercase(),
                            fontWeight = FontWeight.Bold,
                            color = AppColors.GreenPrimary,
                            fontSize = 15.sp
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    Text(
                        text = supplier.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.5.sp,
                            color = AppColors.TextPrimary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    TextButton(
                        onClick = {
                            val currentSupplier = supplier
                            selectedSupplierForDetail = null
                            supplierToEdit = currentSupplier
                            nameInput = currentSupplier.name
                            phoneInput = currentSupplier.phone ?: ""
                            addressInput = currentSupplier.address ?: ""
                            dialogError = null
                            showSupplierDialog = true
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = AppColors.GreenPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Edit",
                            color = AppColors.GreenPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Contact Info
                    if (!supplier.phone.isNullOrEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = null,
                                tint = AppColors.TextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = supplier.phone,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.5.sp,
                                    color = AppColors.TextSecondary
                                )
                            )
                        }
                    }

                    if (!supplier.address.isNullOrEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = AppColors.TextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = supplier.address,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.5.sp,
                                    color = AppColors.TextSecondary
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Sisa Hutang Card
                    val debtValue = totalOutstanding ?: 0L
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (debtValue > 0) Color(0xFFFFF3E0) else Color(0xFFE8F5E9),
                        border = BorderStroke(1.dp, if (debtValue > 0) Color(0xFFFFE0B2) else Color(0xFFC8E6C9)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Sisa Hutang",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AppColors.TextSecondary
                                    )
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = formatRupiah(debtValue),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = 16.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (debtValue > 0) Color(0xFFD32F2F) else AppColors.GreenPrimary
                                    )
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (debtValue > 0) Color(0xFFFFEBEE) else Color(0xFFE8F5E9)
                            ) {
                                Text(
                                    text = if (debtValue > 0) "Belum Lunas" else "Lunas",
                                    color = if (debtValue > 0) Color(0xFFD32F2F) else AppColors.GreenPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    // Primary Action: Bayar Hutang Supplier
                    if (openPayables.isNotEmpty()) {
                        Button(
                            onClick = { showPaySupplierDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AppColors.GreenPrimary,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Bayar Hutang Supplier",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(2.dp))

                    Text(
                        text = "Histori Tagihan / Hutang Supplier",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = AppColors.TextPrimary
                        )
                    )

                    if (supplierPayables.isEmpty()) {
                        Text(
                            text = "Belum ada histori hutang supplier",
                            color = AppColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.heightIn(max = 200.dp)
                        ) {
                            items(supplierPayables, key = { it.id }) { payable ->
                                SupplierPayableItemCard(payable = payable)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedSupplierForDetail = null }) {
                    Text("Tutup", fontWeight = FontWeight.Bold, color = AppColors.GreenPrimary, fontSize = 13.sp)
                }
            }
        )

        // ==========================================
        // 4. PAY SUPPLIER DIALOG
        // ==========================================
        if (showPaySupplierDialog && openPayables.isNotEmpty()) {
            val payableToPay = openPayables.first()
            val outstanding = payableToPay.totalDebt - payableToPay.paidAmount

            AlertDialog(
                onDismissRequest = { if (!isSaving) showPaySupplierDialog = false },
                shape = RoundedCornerShape(16.dp),
                containerColor = Color.White,
                title = {
                    Text(
                        text = "Bayar Hutang Supplier",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = AppColors.TextPrimary
                        )
                    )
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFF9FBF9),
                            border = BorderStroke(1.dp, Color(0xFFEFF3F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Total Tagihan:", fontSize = 12.sp, color = AppColors.TextSecondary)
                                    Text(formatRupiah(payableToPay.totalDebt), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Spacer(Modifier.height(3.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Sudah Dibayar:", fontSize = 12.sp, color = AppColors.TextSecondary)
                                    Text(formatRupiah(payableToPay.paidAmount), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Spacer(Modifier.height(3.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Sisa Hutang:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F))
                                    Text(formatRupiah(outstanding), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F))
                                }
                            }
                        }

                        if (paymentError != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFFFEBEE),
                                border = BorderStroke(1.dp, Color(0xFFFFCDD2)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = paymentError!!,
                                    color = Color(0xFFD32F2F),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }

                        AppTextField(
                            value = paymentAmountInput,
                            onValueChange = {
                                paymentAmountInput = it
                                paymentError = null
                            },
                            label = "Jumlah Pembayaran (Rp) *",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )

                        AppTextField(
                            value = paymentNoteInput,
                            onValueChange = {
                                paymentNoteInput = it
                                paymentError = null
                            },
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
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppColors.GreenPrimary,
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = if (isSaving) "Memproses..." else "Simpan Pembayaran",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showPaySupplierDialog = false },
                        enabled = !isSaving
                    ) {
                        Text("Batal", color = AppColors.TextSecondary, fontSize = 13.sp)
                    }
                }
            )
        }
    }

    // ==========================================
    // 5. MAIN SUPPLIERS SCREEN SCAFFOLD
    // ==========================================
    Scaffold(
        topBar = {
            SuppliersTopHeader(supplierCount = suppliers.size)
        },
        containerColor = Color.White
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search & Action Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        supplierViewModel.search(it)
                    },
                    placeholder = { Text("Cari supplier...", fontSize = 13.sp, color = AppColors.TextSecondary) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Cari",
                            tint = AppColors.TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = if (query.isNotEmpty()) {
                        {
                            IconButton(onClick = {
                                query = ""
                                supplierViewModel.search("")
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Hapus",
                                    tint = AppColors.TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    } else null,
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppColors.GreenPrimary,
                        unfocusedBorderColor = Color(0xFFE0E0E0),
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(10.dp))

                Button(
                    onClick = {
                        supplierToEdit = null
                        nameInput = ""
                        phoneInput = ""
                        addressInput = ""
                        dialogError = null
                        showSupplierDialog = true
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.GreenPrimary,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Tambah Supplier",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // Supplier List Content
            if (suppliers.isEmpty()) {
                SuppliersEmptyState(isSearching = query.isNotBlank())
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Daftar Supplier",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp,
                                color = AppColors.TextPrimary
                            )
                        )
                        Text(
                            text = "${suppliers.size} Distributor",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.5.sp,
                                color = AppColors.TextSecondary
                            )
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(suppliers, key = { it.id }) { supplier ->
                            val totalOutstandingFlow = remember(supplier.id) {
                                supplierViewModel.getTotalOutstandingForSupplier(supplier.id)
                            }
                            val totalOutstanding by totalOutstandingFlow.collectAsStateWithLifecycle()

                            SupplierListItemCard(
                                supplier = supplier,
                                totalOutstanding = totalOutstanding ?: 0L,
                                onClick = { selectedSupplierForDetail = supplier }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Top Header matching Home/Kasir/Produk/Pembelian/Cash/Customers header language.
 */
@Composable
private fun SuppliersTopHeader(supplierCount: Int) {
    Surface(
        color = Color.White,
        shadowElevation = 0.5.dp,
        border = BorderStroke(1.dp, Color(0xFFEFF3F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(AppColors.GreenPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalShipping,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(Modifier.width(10.dp))

                Column {
                    Text(
                        text = "Supplier & Hutang",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.5.sp,
                            color = AppColors.TextPrimary
                        )
                    )
                    Text(
                        text = "Kelola distributor & hutang kulakan",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.5.sp,
                            color = AppColors.TextSecondary
                        )
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFFE8F5E9)
            ) {
                Text(
                    text = if (supplierCount > 0) "$supplierCount Supplier" else "Buku Hutang",
                    color = AppColors.GreenPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/**
 * Clean card representing a supplier in the list.
 */
@Composable
private fun SupplierListItemCard(
    supplier: SupplierEntity,
    totalOutstanding: Long,
    onClick: () -> Unit
) {
    val initial = supplier.name.trim().take(1).uppercase()
    val hasDebt = totalOutstanding > 0

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFEFF3F0)),
        shadowElevation = 0.5.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Supplier Initial Avatar
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE8F5E9)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.GreenPrimary,
                    fontSize = 17.sp
                )
            }

            Spacer(Modifier.width(12.dp))

            // Supplier Name & Contact Column
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = supplier.name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp,
                        color = AppColors.TextPrimary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (!supplier.phone.isNullOrEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = null,
                            tint = AppColors.TextSecondary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = supplier.phone,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.5.sp,
                                color = AppColors.TextSecondary
                            ),
                            maxLines = 1
                        )
                    }
                }

                if (!supplier.address.isNullOrEmpty()) {
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = supplier.address,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            color = AppColors.TextSecondary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            // Debt / Payable Status Column
            Column(horizontalAlignment = Alignment.End) {
                if (hasDebt) {
                    Text(
                        text = formatRupiah(totalOutstanding),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFFD32F2F)
                        )
                    )
                    Spacer(Modifier.height(2.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFFFEBEE)
                    ) {
                        Text(
                            text = "Belum Lunas",
                            color = Color(0xFFD32F2F),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFE8F5E9)
                    ) {
                        Text(
                            text = "Lunas",
                            color = AppColors.GreenPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Card for payable mutation item inside supplier detail.
 */
@Composable
private fun SupplierPayableItemCard(payable: SupplierPayableEntity) {
    val isPaid = payable.status == "PAID"
    val remaining = payable.totalDebt - payable.paidAmount
    val formattedDate = remember(payable.createdAt) {
        try {
            SimpleDateFormat("d MMM yyyy, HH:mm", Locale("id", "ID")).format(Date(payable.createdAt))
        } catch (_: Exception) {
            "-"
        }
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFFF9FBF9),
        border = BorderStroke(1.dp, Color(0xFFEFF3F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Belanja PUR-${payable.id}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                        color = AppColors.TextPrimary
                    )
                )
                Text(
                    text = "$formattedDate • Total: ${formatRupiah(payable.totalDebt)}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.5.sp,
                        color = AppColors.TextSecondary
                    )
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (isPaid) "Lunas" else "Sisa: ${formatRupiah(remaining)}",
                    fontWeight = FontWeight.Bold,
                    color = if (isPaid) AppColors.GreenPrimary else Color(0xFFD32F2F),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isPaid) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                ) {
                    Text(
                        text = if (isPaid) "Lunas" else "Belum Lunas",
                        color = if (isPaid) AppColors.GreenPrimary else Color(0xFFD32F2F),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}

/**
 * Clean Empty State when no suppliers exist or search has no match.
 */
@Composable
private fun SuppliersEmptyState(isSearching: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE8F5E9)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LocalShipping,
                    contentDescription = null,
                    tint = AppColors.GreenPrimary,
                    modifier = Modifier.size(34.dp)
                )
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = if (isSearching) "Supplier Tidak Ditemukan" else "Belum Ada Supplier",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = AppColors.TextPrimary
                )
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = if (isSearching) "Coba gunakan kata kunci pencarian yang lain." else "Tambah supplier untuk mencatat transaksi pembelian barang & hutang usaha.",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.5.sp,
                    color = AppColors.TextSecondary,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
    }
}
