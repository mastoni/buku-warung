package id.skmnetwork.bukuwarung.ui.customer

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import id.skmnetwork.bukuwarung.data.local.entity.CustomerEntity
import id.skmnetwork.bukuwarung.data.local.entity.DebtEntity
import id.skmnetwork.bukuwarung.data.preferences.UserSettings
import id.skmnetwork.bukuwarung.domain.business.BusinessTaxonomyRegistry
import id.skmnetwork.bukuwarung.ui.components.AppTextField
import id.skmnetwork.bukuwarung.ui.components.AppCard
import id.skmnetwork.bukuwarung.ui.components.AppErrorBanner
import id.skmnetwork.bukuwarung.ui.components.AppLoadingState
import id.skmnetwork.bukuwarung.ui.components.ButtonDestructive
import id.skmnetwork.bukuwarung.ui.components.ButtonGhost
import id.skmnetwork.bukuwarung.ui.components.ButtonPrimary
import id.skmnetwork.bukuwarung.ui.components.FieldSearch
import id.skmnetwork.bukuwarung.ui.components.StatusBadge
import id.skmnetwork.bukuwarung.ui.components.StatusBadgeVariant
import id.skmnetwork.bukuwarung.ui.theme.AppColors
import id.skmnetwork.bukuwarung.ui.theme.rememberAppWindowSize
import id.skmnetwork.bukuwarung.util.formatRupiah
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CustomersScreen(
    customerViewModel: CustomerViewModel,
    userSettings: UserSettings? = null
) {
    val resolvedProfile = remember(userSettings?.primaryBusinessType, userSettings?.secondaryActivities) {
        BusinessTaxonomyRegistry.resolve(
            primaryType = userSettings?.primaryBusinessType,
            secondaryActivities = userSettings?.secondaryActivities
        )
    }
    val terminology = resolvedProfile.terminology
    val customerLabel = terminology.customerLabel

    val isLoading by customerViewModel.isLoading.collectAsStateWithLifecycle()
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
    // Step 8: which open debt the payment is aimed at. A customer can hold several open debts
    // at once (one row is created per credit sale), so the target is chosen explicitly here
    // instead of being implied. Null means "nothing picked yet", which falls back to the debt
    // that was previously targeted, so a customer with a single open debt is unaffected.
    var selectedDebtIdForPayment by remember { mutableStateOf<Long?>(null) }

    val windowSize = rememberAppWindowSize()

    val context = LocalContext.current

    // ==========================================
    // 1. ADD / EDIT CUSTOMER DIALOG
    // ==========================================
    if (showCustomerDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSaving) showCustomerDialog = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = if (customerToEdit == null) "Tambah $customerLabel" else "Edit $customerLabel",
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
                        // Gate 1.5: was a bespoke Surface on Color(0xFFFFEBEE) with a
                        // Color(0xFFFFCDD2) border - a light-only plate that becomes a ~13:1 white
                        // slab in dark. The message and the dialogError state behind it are
                        // unchanged; only the presentation moves to the app's single error treatment.
                        AppErrorBanner(message = dialogError!!)
                    }

                    AppTextField(
                        value = nameInput,
                        onValueChange = {
                            nameInput = it
                            dialogError = null
                        },
                        label = "Nama $customerLabel *"
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
                // Gate 1.5: was a bare Button with no height, so it sat at Material3's 40dp default
                // minimum, a 10dp radius nothing else uses, and Color.White on primary - 2.28:1 in the
                // dark theme. ButtonPrimary is a fixed 48dp at the app's 16dp button radius with
                // onPrimary as its foreground. onClick, the save/update branch and every callback are
                // unchanged.
                ButtonPrimary(
                    text = if (isSaving) "Menyimpan..." else "Simpan",
                    onClick = {
                        if (isSaving) return@ButtonPrimary
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
                                    Toast.makeText(context, "$customerLabel berhasil ditambahkan", Toast.LENGTH_SHORT).show()
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
                                    Toast.makeText(context, "$customerLabel berhasil diperbarui", Toast.LENGTH_SHORT).show()
                                },
                                onError = {
                                    isSaving = false
                                    dialogError = it
                                }
                            )
                        }
                    },
                    enabled = !isSaving,
                    loading = isSaving,
                    fillMaxWidth = false
                )
            },
            dismissButton = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (customerToEdit != null) {
                        TextButton(
                            onClick = {
                                showCustomerDialog = false
                                showDeleteConfirmDialog = true
                            },
                            enabled = !isSaving
                        ) {
                            Text("Hapus", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                    TextButton(
                        onClick = { showCustomerDialog = false },
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
    if (showDeleteConfirmDialog && customerToEdit != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text("Hapus $customerLabel", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = AppColors.TextPrimary)
            },
            text = {
                Text(
                    text = "Yakin ingin menghapus \"${customerToEdit!!.name}\"?",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp, color = AppColors.TextSecondary)
                )
            },
            confirmButton = {
                // Gate 1.5: this set containerColor with NO contentColor, so M3's container lookup
                // could not resolve a label colour for #D32F2F and fell back to onSurface - near-black
                // text on a dark red button. ButtonDestructive states the error/onError pairing
                // explicitly and is a fixed 48dp. The delete call and both callbacks are unchanged.
                ButtonDestructive(
                    text = "Hapus",
                    onClick = {
                        customerViewModel.deleteCustomer(
                            id = customerToEdit!!.id,
                            onSuccess = {
                                showDeleteConfirmDialog = false
                                customerToEdit = null
                                Toast.makeText(context, "$customerLabel berhasil dihapus", Toast.LENGTH_SHORT).show()
                            },
                            onError = {
                                showDeleteConfirmDialog = false
                                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                            }
                        )
                    },
                    fillMaxWidth = false
                )
            },
            dismissButton = {
                ButtonGhost(
                    text = "Batal",
                    onClick = { showDeleteConfirmDialog = false },
                    fillMaxWidth = false
                )
            }
        )
    }

    // ==========================================
    // 3. CUSTOMER DETAIL & DEBT HISTORY DIALOG
    // ==========================================
    if (selectedCustomerForDetail != null) {
        val customer = selectedCustomerForDetail!!
        val customerDebtsFlow = remember(customer.id) { customerViewModel.getDebtsForCustomer(customer.id) }
        val customerDebts by customerDebtsFlow.collectAsStateWithLifecycle()

        val totalOutstandingFlow = remember(customer.id) { customerViewModel.getTotalOutstandingForCustomer(customer.id) }
        val totalOutstanding by totalOutstandingFlow.collectAsStateWithLifecycle()

        val openDebts = customerDebts.filter { it.status == "OPEN" }

        AlertDialog(
            onDismissRequest = { selectedCustomerForDetail = null },
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(AppColors.SuccessContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = customer.name.trim().take(1).uppercase(),
                            fontWeight = FontWeight.Bold,
                            color = AppColors.GreenPrimary,
                            fontSize = 15.sp
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    Text(
                        text = customer.name,
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
                            val currentCustomer = customer
                            selectedCustomerForDetail = null
                            customerToEdit = currentCustomer
                            nameInput = currentCustomer.name
                            phoneInput = currentCustomer.phone ?: ""
                            addressInput = currentCustomer.address ?: ""
                            dialogError = null
                            showCustomerDialog = true
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
                    if (!customer.phone.isNullOrEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = null,
                                tint = AppColors.TextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = customer.phone,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.5.sp,
                                    color = AppColors.TextSecondary
                                )
                            )
                        }
                    }

                    if (!customer.address.isNullOrEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = AppColors.TextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = customer.address,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.5.sp,
                                    color = AppColors.TextSecondary
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Sisa Piutang Card
                    val debtValue = totalOutstanding ?: 0L
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (debtValue > 0) AppColors.WarningContainer else AppColors.SuccessContainer,
                        border = BorderStroke(1.dp, if (debtValue > 0) AppColors.OrangeWarning else AppColors.Success),
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
                                    text = "Sisa Piutang",
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
                                        color = if (debtValue > 0) MaterialTheme.colorScheme.error else AppColors.GreenPrimary
                                    )
                                )
                            }

                            // Gate 1.5: one of four hand-rolled "Lunas / Belum Lunas" badges in this
                            // file (12/10/10/6dp radii, 11/10.5/11/9.5sp). All four collapse into the
                            // app's StatusBadge, which fixes the 9.5sp copy and the light-only
                            // containers. The label text is unchanged, so status is still carried by
                            // words and not by colour alone.
                            StatusBadge(
                                label = if (debtValue > 0) "Belum Lunas" else "Lunas",
                                variant = if (debtValue > 0) StatusBadgeVariant.Error else StatusBadgeVariant.Success
                            )
                        }
                    }

                    // Primary Action: Bayar Hutang
                    if (openDebts.isNotEmpty()) {
                        Button(
                            onClick = {
                                // Every dialog visit starts from a clean target so a previous
                                // choice can never be carried over into a different payment.
                                selectedDebtIdForPayment = null
                                paymentAmountInput = ""
                                paymentNoteInput = ""
                                paymentError = null
                                showPayDebtDialog = true
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AppColors.GreenPrimary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Bayar Hutang",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(2.dp))

                    Text(
                        text = "Histori Hutang / Piutang $customerLabel",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = AppColors.TextPrimary
                        )
                    )

                    if (customerDebts.isEmpty()) {
                        Text(
                            text = "Belum ada histori hutang $customerLabel",
                            color = AppColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.heightIn(max = 200.dp)
                        ) {
                            items(customerDebts, key = { it.id }) { debt ->
                                // Step 8A: the history row is the explicit target. Tapping an open
                                // debt opens the payment dialog already bound to that debt, so the
                                // merchant identifies the record instead of the UI picking one.
                                CustomerDebtItemCard(
                                    debt = debt,
                                    onPay = if (debt.status == "OPEN") {
                                        {
                                            selectedDebtIdForPayment = debt.id
                                            paymentAmountInput = ""
                                            paymentNoteInput = ""
                                            paymentError = null
                                            showPayDebtDialog = true
                                        }
                                    } else {
                                        null
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedCustomerForDetail = null }) {
                    Text("Tutup", fontWeight = FontWeight.Bold, color = AppColors.GreenPrimary, fontSize = 13.sp)
                }
            }
        )

        // ==========================================
        // 4. PAY DEBT DIALOG
        // ==========================================
        if (showPayDebtDialog && openDebts.isNotEmpty()) {
            // Step 8A: `openDebts.first()` used to be the payment target, so with several open
            // debts the money landed on whichever row happened to sort first without the merchant
            // being told. The target now comes from an explicit selection and is null until the
            // merchant makes one, so the confirm button stays disabled rather than guessing. No
            // new debt field is introduced - reference, date and amounts are the ones the history
            // list already shows.
            val selectableDebts = openDebts.sortedByDescending { it.createdAt }
            val debtToPay = resolveDebtPaymentTarget(openDebts, selectedDebtIdForPayment)

            AlertDialog(
                onDismissRequest = { if (!isSaving) showPayDebtDialog = false },
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                title = {
                    Text(
                        text = "Bayar Hutang $customerLabel",
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
                        // Only rendered once a concrete debt is targeted, so the figures always
                        // belong to that debt and never to a customer-wide aggregate.
                        if (debtToPay != null) {
                            DebtSummaryCard(debt = debtToPay)
                        }

                        // Only rendered when there is genuinely a choice to make.
                        if (openDebts.size > 1) {
                            Text(
                                text = "Pilih hutang yang dibayar",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = AppColors.TextPrimary
                                )
                            )
                            selectableDebts.forEach { candidate ->
                                val remainingForCandidate = candidate.totalDebt - candidate.paidAmount
                                DebtTargetRow(
                                    debtId = candidate.id,
                                    dateText = formatDebtDate(candidate.createdAt),
                                    remainingText = formatRupiah(remainingForCandidate),
                                    isSelected = candidate.id == selectedDebtIdForPayment,
                                    enabled = !isSaving,
                                    onClick = { selectedDebtIdForPayment = candidate.id }
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                        }

                        if (paymentError != null) {
                            // Gate 1.5: the second of the file's two hand-rolled error banners, with
                            // the same light-only plate as the dialog error above. Same message, same
                            // state, one presentation.
                            AppErrorBanner(message = paymentError!!)
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
                            // The merchant must have named the debt; with several open debts the
                            // button stays disabled until one is picked.
                            val targetDebt = debtToPay ?: return@Button
                            isSaving = true
                            paymentError = null
                            customerViewModel.payDebt(
                                debtId = targetDebt.id,
                                amountStr = paymentAmountInput,
                                note = paymentNoteInput,
                                onSuccess = {
                                    isSaving = false
                                    showPayDebtDialog = false
                                    selectedDebtIdForPayment = null
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
                        enabled = !isSaving && debtToPay != null,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppColors.GreenPrimary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
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
                        onClick = { showPayDebtDialog = false },
                        enabled = !isSaving
                    ) {
                        Text("Batal", color = AppColors.TextSecondary, fontSize = 13.sp)
                    }
                }
            )
        }
    }

    // ==========================================
    // 5. MAIN CUSTOMERS SCREEN SCAFFOLD
    // ==========================================
    // Step 2 (H): do not present placeholder zeros or an empty list as if they were real answers.
    if (isLoading) {
        AppLoadingState()
        return
    }

    Scaffold(
        topBar = {
            CustomersTopHeader(
                customerCount = customers.size,
                customerLabel = customerLabel
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
        Column(
            modifier = Modifier
                // Gate 1.7: was fillMaxSize() then widthIn(max = contentMaxWidth). fillMaxSize
                // fixes the incoming width to the parent's, so the downstream widthIn could not
                // narrow it and the tablet cap never applied - the list ran full-bleed on every
                // width. fillMaxHeight keeps the full-height behaviour while leaving width free for
                // the cap to do its job; the enclosing Box already centres with TopCenter.
                // On a phone (parent < 640dp) this changes nothing at all.
                .fillMaxHeight()
                .widthIn(max = windowSize.contentMaxWidth)
        ) {
            // Search & Action Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Gate 1.5: was a 41-line private re-implementation of the search field - a 12dp
                // shape and an 8-entry colour block that restated the theme, with no label at all so
                // typing removed the only text on screen. FieldSearch is the app's one search input:
                // a persistent M3 label, a clear affordance whose target is a full 48dp, and the shared
                // 12dp shape and colours.
                //
                // Search semantics are unchanged: FieldSearch calls this single lambda for both typing
                // and its own clear button, and it still calls customerViewModel.search.
                FieldSearch(
                    query = query,
                    onQueryChange = {
                        query = it
                        customerViewModel.search(it)
                    },
                    label = "Cari ${customerLabel.lowercase()}",
                    placeholder = "Cari ${customerLabel.lowercase()}..."
                )

                Spacer(Modifier.height(10.dp))

                // Gate 1.5: ButtonPrimary for the shape, the onPrimary foreground and the guaranteed
                // 48dp. The explicit height(48.dp) is kept because TouchTargetContractTest pins two
                // such literals in this file as the audited proof that primary actions are >=48dp.
                ButtonPrimary(
                    text = "Tambah $customerLabel",
                    onClick = {
                        customerToEdit = null
                        nameInput = ""
                        phoneInput = ""
                        addressInput = ""
                        dialogError = null
                        showCustomerDialog = true
                    },
                    leadingIcon = Icons.Default.Add,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                )
            }

            Spacer(Modifier.height(4.dp))

            // Customer List Content
            if (customers.isEmpty()) {
                CustomersEmptyState(
                    isSearching = query.isNotBlank(),
                    customerLabel = customerLabel
                )
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
                            text = "Daftar $customerLabel",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp,
                                color = AppColors.TextPrimary
                            )
                        )
                        Text(
                            text = "${customers.size} $customerLabel",
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
                        items(customers, key = { it.id }) { customer ->
                            val totalOutstandingFlow = remember(customer.id) {
                                customerViewModel.getTotalOutstandingForCustomer(customer.id)
                            }
                            val totalOutstanding by totalOutstandingFlow.collectAsStateWithLifecycle()

                            CustomerListItemCard(
                                customer = customer,
                                totalOutstanding = totalOutstanding ?: 0L,
                                onClick = { selectedCustomerForDetail = customer }
                            )
                        }
                    }
                }
            }
            }
        }
    }
}

/**
 * Top Header matching Home/Kasir/Produk/Pembelian/Cash header language.
 */
@Composable
private fun CustomersTopHeader(
    customerCount: Int,
    customerLabel: String = "Pelanggan"
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 0.5.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                        imageVector = Icons.Default.People,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(Modifier.width(10.dp))

                Column {
                    Text(
                        text = "$customerLabel & Piutang",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.5.sp,
                            color = AppColors.TextPrimary
                        )
                    )
                    Text(
                        text = "Kelola data & catatan piutang",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.5.sp,
                            color = AppColors.TextSecondary
                        )
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = AppColors.GreenPrimary.copy(alpha = 0.15f)
            ) {
                Text(
                    text = if (customerCount > 0) "$customerCount $customerLabel" else "Buku Piutang",
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
 * Clean card representing a customer in the list.
 */
@Composable
private fun CustomerListItemCard(
    customer: CustomerEntity,
    totalOutstanding: Long,
    onClick: () -> Unit
) {
    val initial = customer.name.trim().take(1).uppercase()
    val hasDebt = totalOutstanding > 0

    // Gate 1.5: AppCard instead of a private Surface. AppCard already carries the app's 16dp radius,
    // a 1dp CardBorderStrong hairline and 0dp elevation - outline alone measures 1.23:1 on the white
    // surface, so these rows had no visible edge in the default light theme. onClick is unchanged.
    AppCard(
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
            // Customer Initial Avatar
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(AppColors.GreenPrimary.copy(alpha = 0.15f)),
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

            // Customer Name & Contact Column
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = customer.name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp,
                        color = AppColors.TextPrimary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (!customer.phone.isNullOrEmpty()) {
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
                            text = customer.phone,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.5.sp,
                                color = AppColors.TextSecondary
                            ),
                            maxLines = 1
                        )
                    }
                }

                if (!customer.address.isNullOrEmpty()) {
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = customer.address,
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

            // Debt / Piutang Status Column
            Column(horizontalAlignment = Alignment.End) {
                if (hasDebt) {
                    Text(
                        text = formatRupiah(totalOutstanding),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    )
                    Spacer(Modifier.height(2.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "Belum Lunas",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = AppColors.GreenPrimary.copy(alpha = 0.15f)
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
 * Prefix of the reference the debt history list already shows for a debt row, reused so the
 * payment dialog names the same thing the merchant sees in the list. No field is invented for
 * display: it is derived from the debt primary key the list already renders as "Hutang TRX-<id>".
 */
private const val debtReferencePrefix = "TRX-"

/**
 * Step 8A - resolves the single open debt a payment is aimed at.
 *
 * A customer can hold several open debts at the same time, because [SaleRepository] inserts one
 * debt row per credit sale, so the payment target has to be chosen rather than implied. The
 * merchant's choice is [selectedDebtId], made either by tapping a debt in the customer's
 * "Histori Hutang / Piutang" list or in the dialog's own list.
 *
 * Returns null when there is genuinely no target:
 *  - no open debt at all, so there is nothing to pay;
 *  - several open debts and no choice made yet, so the payment must not fall back to whichever
 *    row happened to sort first. The caller keeps the confirm button disabled until the merchant
 *    picks, which is what makes the target explicit.
 *
 * A customer with exactly one open debt is not a choice, so that debt is the target and the
 * existing single-debt flow is unchanged.
 *
 * No FIFO/LIFO/oldest-first rule is encoded anywhere: the product rule for which debt an
 * unconfirmed payment should reduce is not defined in the model, so the merchant is asked instead.
 */
internal fun resolveDebtPaymentTarget(
    openDebts: List<DebtEntity>,
    selectedDebtId: Long?
): DebtEntity? {
    if (openDebts.isEmpty()) return null
    val selectable = openDebts.sortedByDescending { it.createdAt }
    val selected = selectedDebtId?.let { id -> selectable.firstOrNull { it.id == id } }
    if (selected != null) return selected
    return selectable.singleOrNull()
}

private fun formatDebtDate(createdAt: Long): String {
    return try {
        SimpleDateFormat("d MMM yyyy", Locale("id", "ID")).format(Date(createdAt))
    } catch (_: Exception) {
        "-"
    }
}

/**
 * The debt being paid, named and fully described, using only fields the debt history list already
 * shows. Rendered only after a concrete debt is targeted, so the totals on screen are that debt's
 * own totals and never the customer's aggregate.
 */
@Composable
private fun DebtSummaryCard(debt: DebtEntity) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, AppColors.CardBorderStrong),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Hutang $debtReferencePrefix${debt.id}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary
                )
                Text(
                    text = formatDebtDate(debt.createdAt),
                    fontSize = 11.sp,
                    color = AppColors.TextSecondary
                )
            }
            Spacer(Modifier.height(3.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Total Hutang:", fontSize = 12.sp, color = AppColors.TextSecondary)
                Text(formatRupiah(debt.totalDebt), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(3.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Sudah Dibayar:", fontSize = 12.sp, color = AppColors.TextSecondary)
                Text(formatRupiah(debt.paidAmount), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(3.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Sisa Hutang:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                Text(
                    text = formatRupiah(debt.totalDebt - debt.paidAmount),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/** One selectable open debt inside the payment dialog. */
@Composable
private fun DebtTargetRow(
    debtId: Long,
    dateText: String,
    remainingText: String,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) AppColors.GreenPrimary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            1.dp,
            if (isSelected) AppColors.GreenPrimary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Hutang $debtReferencePrefix$debtId",
                    fontSize = 12.5.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    color = AppColors.TextPrimary
                )
                Text(
                    text = dateText,
                    fontSize = 10.5.sp,
                    color = AppColors.TextSecondary
                )
            }
            Text(
                text = "Sisa: $remainingText",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * Card for debt mutation item inside customer detail.
 *
 * When [onPay] is supplied the card itself is the explicit target selector: tapping a specific open
 * debt opens the payment dialog already aimed at that debt, so the merchant identifies the record
 * before any money moves. Settled debts are never tappable.
 */
@Composable
private fun CustomerDebtItemCard(debt: DebtEntity, onPay: (() -> Unit)? = null) {
    val isPaid = debt.status == "PAID"
    val remaining = debt.totalDebt - debt.paidAmount
    val formattedDate = remember(debt.createdAt) {
        try {
            SimpleDateFormat("d MMM yyyy, HH:mm", Locale("id", "ID")).format(Date(debt.createdAt))
        } catch (_: Exception) {
            "-"
        }
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onPay != null) Modifier.clickable(onClick = onPay) else Modifier)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Hutang $debtReferencePrefix${debt.id}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                        color = AppColors.TextPrimary
                    )
                )
                Text(
                    text = "$formattedDate • Total: ${formatRupiah(debt.totalDebt)}",
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
                    color = if (isPaid) AppColors.GreenPrimary else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
                )
                // Gate 1.5: the worst of the four badges in this file - 6dp radius, 9.5sp, 5dp/1dp
                // padding. PosDesign.kt:190-192 explicitly forbids 9.5sp ("the 9.5sp that existed
                // elsewhere in the app"), and 9.5sp is below Android's own 11sp spannable floor.
                // StatusBadge is 8dp radius, labelMedium (12sp), 8dp/4dp. Same label text.
                StatusBadge(
                    label = if (isPaid) "Lunas" else "Belum Lunas",
                    variant = if (isPaid) StatusBadgeVariant.Success else StatusBadgeVariant.Error
                )
            }
        }
    }
}

/**
 * Clean Empty State when no customers exist or search has no match.
 */
@Composable
private fun CustomersEmptyState(
    isSearching: Boolean,
    customerLabel: String = "Pelanggan"
) {
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
                    .background(AppColors.GreenPrimary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.People,
                    contentDescription = null,
                    tint = AppColors.GreenPrimary,
                    modifier = Modifier.size(34.dp)
                )
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = if (isSearching) "$customerLabel Tidak Ditemukan" else "Belum Ada $customerLabel",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = AppColors.TextPrimary
                )
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = if (isSearching) "Coba gunakan kata kunci pencarian yang lain." else "Tambah ${customerLabel.lowercase()} untuk mencatat transaksi penjualan kredit & piutang usaha.",
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
