package id.skmnetwork.bukuwarung.ui.cash

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.skmnetwork.bukuwarung.ui.components.AppCard
import id.skmnetwork.bukuwarung.ui.components.AppTextField
import id.skmnetwork.bukuwarung.ui.product.ProductViewModel
import id.skmnetwork.bukuwarung.ui.theme.AppColors
import id.skmnetwork.bukuwarung.ui.theme.AppShapes
import id.skmnetwork.bukuwarung.ui.theme.AppSpacing
import id.skmnetwork.bukuwarung.util.formatRupiah

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashScreen(viewModel: ProductViewModel) {
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

    if (showCashDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSaving) showCashDialog = false },
            title = { Text(if (dialogType == "INCOME") "+ Tambah Pemasukan" else "+ Tambah Pengeluaran", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                    if (dialogError != null) {
                        Text(dialogError!!, color = AppColors.RedExpense, style = MaterialTheme.typography.bodySmall)
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
                        label = "Deskripsi *"
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isSaving) return@Button
                        isSaving = true
                        viewModel.addManualCash(
                            type = dialogType,
                            amountStr = manualAmount,
                            description = manualDescription,
                            onSuccess = {
                                isSaving = false
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
                    colors = ButtonDefaults.buttonColors(containerColor = if (dialogType == "INCOME") AppColors.GreenPrimary else AppColors.RedExpense)
                ) {
                    Text("Simpan", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCashDialog = false },
                    enabled = !isSaving
                ) {
                    Text("Batal")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Uang Kas", fontWeight = FontWeight.Bold) })
        },
        containerColor = Color.White
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = AppSpacing.lg)
        ) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                items(listOf("Semua", "Pemasukan", "Pengeluaran")) { filterName ->
                    val isSelected = filterName == selectedFilter
                    Surface(
                        shape = AppShapes.ChipShape,
                        color = if (isSelected) AppColors.GreenPrimary else AppColors.SurfaceGray,
                        modifier = Modifier.clickable { selectedFilter = filterName }
                    ) {
                        Text(
                            text = filterName,
                            color = if (isSelected) Color.White else AppColors.TextSecondary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = AppSpacing.lg, vertical = AppSpacing.sm)
                        )
                    }
                }
            }

            Spacer(Modifier.height(AppSpacing.md))

            AppCard(
                backgroundColor = AppColors.BlueCash,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(AppSpacing.lg)) {
                    Text("Saldo Kas", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(AppSpacing.xs))
                    Text(formatRupiah(cashBalance ?: 0L), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(AppSpacing.md))

            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                Button(
                    onClick = {
                        dialogType = "INCOME"
                        manualAmount = ""
                        manualDescription = ""
                        dialogError = null
                        showCashDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.GreenPrimary),
                    shape = AppShapes.ButtonShape,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("+ Tambah Pemasukan", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        dialogType = "EXPENSE"
                        manualAmount = ""
                        manualDescription = ""
                        dialogError = null
                        showCashDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.RedExpense),
                    shape = AppShapes.ButtonShape,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("+ Tambah Pengeluaran", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(AppSpacing.lg))

            Text("Transaksi Terbaru", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

            Spacer(Modifier.height(AppSpacing.sm))

            if (filteredTransactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Belum ada transaksi kas", color = AppColors.TextSecondary, fontWeight = FontWeight.Medium)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                    modifier = Modifier.weight(1f)
                ) {
                    items(filteredTransactions, key = { it.id }) { tx ->
                        val isIncome = tx.type == "INCOME"
                        AppCard {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(AppSpacing.md),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier
                                        .size(40.dp)
                                        .background(if (isIncome) AppColors.GreenLight else AppColors.RedExpense, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isIncome) Icons.Default.Add else Icons.Default.Remove,
                                        contentDescription = null,
                                        tint = if (isIncome) AppColors.GreenPrimary else Color.White
                                    )
                                }
                                Spacer(Modifier.width(AppSpacing.md))
                                Column(Modifier.weight(1f)) {
                                    Text(tx.description, fontWeight = FontWeight.SemiBold)
                                    Text("Ref ID: ${tx.refId ?: "-"}", style = MaterialTheme.typography.labelSmall, color = AppColors.TextSecondary)
                                }
                                Text(
                                    text = "${if (isIncome) "+ " else "- "}${formatRupiah(tx.amount)}",
                                    fontWeight = FontWeight.Bold,
                                    color = if (isIncome) AppColors.GreenPrimary else AppColors.RedExpense
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
