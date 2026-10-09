package id.skmnetwork.bukuwarung.ui.cash

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.skmnetwork.bukuwarung.data.local.entity.CashTransactionEntity
import id.skmnetwork.bukuwarung.data.preferences.UserSettings
import id.skmnetwork.bukuwarung.ui.components.AppLoadingState
import id.skmnetwork.bukuwarung.ui.components.AppTextField
import id.skmnetwork.bukuwarung.ui.product.ProductViewModel
import id.skmnetwork.bukuwarung.ui.theme.AppColors
import id.skmnetwork.bukuwarung.ui.theme.AppShapes
import id.skmnetwork.bukuwarung.ui.theme.rememberAppWindowSize
import id.skmnetwork.bukuwarung.util.formatRupiah
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashScreen(
    viewModel: ProductViewModel,
    userSettings: UserSettings? = null
) {
    val windowSize = rememberAppWindowSize()
    val context = LocalContext.current
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val cashBalance by viewModel.cashBalance.collectAsStateWithLifecycle()
    val cashTransactions by viewModel.cashTransactions.collectAsStateWithLifecycle()

    var selectedFilter by remember { mutableStateOf("Semua") }
    var showCashDialog by remember { mutableStateOf(false) }
    var dialogType by remember { mutableStateOf("INCOME") }

    var manualAmount by remember { mutableStateOf("") }
    var manualDescription by remember { mutableStateOf("") }
    var dialogError by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    val filteredTransactions = cashTransactions.filter { tx ->
        when (selectedFilter) {
            "Pemasukan" -> tx.type == "INCOME"
            "Pengeluaran" -> tx.type == "EXPENSE"
            else -> true
        }
    }

    // ==========================================
    // MANUAL TRANSACTION DIALOG
    // ==========================================
    if (showCashDialog) {
        val isIncome = dialogType == "INCOME"
        AlertDialog(
            onDismissRequest = { if (!isSaving) showCashDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isIncome) AppColors.SuccessContainer else MaterialTheme.colorScheme.errorContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isIncome) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                            contentDescription = null,
                            tint = if (isIncome) AppColors.GreenPrimary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = if (isIncome) "Tambah Pemasukan" else "Tambah Pengeluaran",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = AppColors.TextPrimary
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (dialogError != null) {
                        Surface(
                            shape = AppShapes.ChipShape,
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = dialogError!!,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    AppTextField(
                        value = manualAmount,
                        onValueChange = {
                            manualAmount = it
                            dialogError = null
                        },
                        label = "Jumlah (Rp) *",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    AppTextField(
                        value = manualDescription,
                        onValueChange = {
                            manualDescription = it
                            dialogError = null
                        },
                        label = "Deskripsi (contoh: Tambah Modal, Listrik, dsb) *"
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isSaving) return@Button
                        isSaving = true
                        val recordedType = dialogType
                        val recordedAmount = manualAmount.trim().toLongOrNull() ?: 0L
                        viewModel.addManualCash(
                            type = recordedType,
                            amountStr = manualAmount,
                            description = manualDescription,
                            onSuccess = {
                                isSaving = false
                                Toast.makeText(
                                    context,
                                    if (recordedType == "INCOME") {
                                        "Pemasukan ${formatRupiah(recordedAmount)} tersimpan"
                                    } else {
                                        "Pengeluaran ${formatRupiah(recordedAmount)} tersimpan"
                                    },
                                    Toast.LENGTH_SHORT
                                ).show()
                                showCashDialog = false
                                manualAmount = ""
                                manualDescription = ""
                            },
                            onError = { error ->
                                isSaving = false
                                dialogError = error
                            }
                        )
                    },
                    enabled = !isSaving,
                    shape = AppShapes.CardShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isIncome) AppColors.GreenPrimary else MaterialTheme.colorScheme.error,
                        contentColor = if (isIncome) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(
                        text = if (isSaving) "Menyimpan..." else "Simpan Transaksi",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCashDialog = false },
                    enabled = !isSaving
                ) {
                    Text("Batal", fontWeight = FontWeight.SemiBold, color = AppColors.TextSecondary)
                }
            }
        )
    }

    // ==========================================
    // MAIN CASH SCREEN SCAFFOLD
    // ==========================================
    if (isLoading) {
        AppLoadingState()
        return
    }

    Scaffold(
        topBar = {
            CashHeader(transactionCount = cashTransactions.size)
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
                    .fillMaxHeight()
                    .widthIn(max = windowSize.contentMaxWidth)
            ) {
                // ==========================================
                // FILTER CHIPS ROW
                // ==========================================
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    items(listOf("Semua", "Pemasukan", "Pengeluaran")) { filterName ->
                        val isSelected = filterName == selectedFilter
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) AppColors.GreenPrimary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            shadowElevation = 0.dp,
                            modifier = Modifier.clickable { selectedFilter = filterName }
                        ) {
                            Text(
                                text = filterName,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else AppColors.TextSecondary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    // ==========================================
                    // SALDO KAS CARD (PRIMARY FOCAL POINT)
                    // ==========================================
                    Surface(
                        shape = AppShapes.CardShape,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        shadowElevation = 0.5.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(AppColors.SuccessContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountBalanceWallet,
                                    contentDescription = null,
                                    tint = AppColors.GreenPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Saldo Kas",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = AppColors.TextSecondary
                                    )
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = formatRupiah(cashBalance ?: 0L),
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AppColors.TextPrimary
                                    )
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // ==========================================
                    // PRIMARY ACTION BUTTONS (INCOME / EXPENSE)
                    // ==========================================
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                dialogType = "INCOME"
                                manualAmount = ""
                                manualDescription = ""
                                dialogError = null
                                showCashDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AppColors.GreenPrimary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = AppShapes.CardShape,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Pemasukan Kas",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }

                        Button(
                            onClick = {
                                dialogType = "EXPENSE"
                                manualAmount = ""
                                manualDescription = ""
                                dialogError = null
                                showCashDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            shape = AppShapes.CardShape,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Pengeluaran Kas",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // ==========================================
                    // SECTION TITLE: TRANSAKSI TERBARU
                    // ==========================================
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Transaksi Terbaru",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 16.sp,
                                color = AppColors.TextPrimary
                            )
                        )
                        if (filteredTransactions.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = "${filteredTransactions.size} Transaksi",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AppColors.TextSecondary
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // ==========================================
                    // TRANSACTION MUTATION LIST OR EMPTY STATE
                    // ==========================================
                    if (filteredTransactions.isEmpty()) {
                        CashEmptyState(selectedFilter = selectedFilter)
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 88.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredTransactions, key = { it.id }) { tx ->
                                CashTransactionCard(tx = tx)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Top Header for Cash screen, harmonized with other screens.
 */
@Composable
private fun CashHeader(transactionCount: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 0.5.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppColors.GreenPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountBalanceWallet,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column {
                    Text(
                        text = "Uang Kas",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = AppColors.TextPrimary
                        )
                    )
                    Text(
                        text = "Catat arus kas & saldo usaha",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            color = AppColors.TextSecondary
                        )
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = AppColors.SuccessContainer
            ) {
                Text(
                    text = if (transactionCount > 0) "$transactionCount Mutasi" else "Buku Kas",
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
 * Clean card representing a single cash transaction (INCOME or EXPENSE).
 */
@Composable
private fun CashTransactionCard(tx: CashTransactionEntity) {
    val isIncome = tx.type == "INCOME"
    val formattedDate = remember(tx.createdAt) {
        try {
            SimpleDateFormat("d MMM yyyy, HH:mm", Locale("id", "ID")).format(Date(tx.createdAt))
        } catch (_: Exception) {
            "-"
        }
    }

    Surface(
        shape = AppShapes.CardShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 0.5.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Transaction Icon Circle
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isIncome) AppColors.SuccessContainer else MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isIncome) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                    contentDescription = if (isIncome) "Pemasukan" else "Pengeluaran",
                    tint = if (isIncome) AppColors.GreenPrimary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            // Details Column
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tx.description,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = AppColors.TextPrimary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(2.dp))

                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        color = AppColors.TextSecondary
                    )
                )
            }

            // Amount Column
            Text(
                text = "${if (isIncome) "+ " else "- "}${formatRupiah(tx.amount)}",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = if (isIncome) AppColors.GreenPrimary else AppColors.RedExpense
                ),
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

/**
 * Clean Empty State when no transactions exist.
 */
@Composable
private fun CashEmptyState(selectedFilter: String) {
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
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(AppColors.SuccessContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AccountBalanceWallet,
                    contentDescription = null,
                    tint = AppColors.GreenPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = if (selectedFilter == "Semua") "Belum Ada Transaksi Kas" else "Tidak Ada $selectedFilter",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = AppColors.TextPrimary
                )
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = "Pemasukan dan pengeluaran kas otomatis maupun manual akan tercatat di sini.",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    color = AppColors.TextSecondary,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
    }
}
