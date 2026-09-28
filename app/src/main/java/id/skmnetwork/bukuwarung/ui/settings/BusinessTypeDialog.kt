package id.skmnetwork.bukuwarung.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.skmnetwork.bukuwarung.domain.business.BusinessCategory
import id.skmnetwork.bukuwarung.domain.business.BusinessType
import id.skmnetwork.bukuwarung.ui.components.PrimaryButton
import id.skmnetwork.bukuwarung.ui.components.SecondaryButton
import id.skmnetwork.bukuwarung.ui.theme.AppColors
import id.skmnetwork.bukuwarung.ui.theme.AppShapes
import id.skmnetwork.bukuwarung.ui.theme.AppSpacing

/**
 * Step 2 (C) - change the business type after onboarding.
 *
 * This only ever rewrites the PREFERENCE stored in DataStore. It never touches products, sales,
 * purchases, customers, debt, or stock history, and it never changes the schema. What it does change
 * is the context the app applies: labels, suggested units, category presets, and the default product
 * type offered for NEW products.
 *
 * The merchant has to pick a type and tick an explicit acknowledgement before the save button becomes
 * available, so the consequence is never applied silently.
 */
@Composable
fun BusinessTypeDialog(
    currentBusinessType: String,
    isSaving: Boolean,
    errorText: String? = null,
    onDismiss: () -> Unit,
    onSave: (newType: BusinessType, secondaryActivities: Set<String>) -> Unit
) {
    val currentType = remember(currentBusinessType) {
        BusinessType.fromId(currentBusinessType) ?: BusinessType.WARUNG_SEMBAKO
    }
    var selectedCategory by remember { mutableStateOf<BusinessCategory?>(null) }
    var selectedType by remember(currentType) { mutableStateOf(currentType) }
    var acknowledged by remember { mutableStateOf(false) }

    val visibleTypes = remember(selectedCategory) {
        if (selectedCategory == null) BusinessType.entries
        else BusinessType.entries.filter { it.category == selectedCategory }
    }

    val hasChanged = selectedType != currentType
    val canSave = hasChanged && acknowledged && !isSaving

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = {
            Text(
                text = "Ubah Jenis Usaha",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                Surface(
                    shape = AppShapes.CardShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(AppSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                    ) {
                        Text(
                            text = "Jenis usaha saat ini",
                            style = MaterialTheme.typography.labelSmall,
                            color = AppColors.TextSecondary
                        )
                        Text(
                            text = currentType.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.TextPrimary
                        )
                    }
                }

                Text(
                    text = "Pilih jenis usaha yang paling sesuai.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextSecondary
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        FilterChip(
                            selected = selectedCategory == null,
                            onClick = { selectedCategory = null },
                            label = { Text("Semua") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AppColors.GreenLight,
                                selectedLabelColor = AppColors.GreenDark
                            )
                        )
                    }
                    items(BusinessCategory.entries.toList()) { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                            label = { Text(category.displayName) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AppColors.GreenLight,
                                selectedLabelColor = AppColors.GreenDark
                            )
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    visibleTypes.forEach { type ->
                        val isSelected = selectedType == type
                        Surface(
                            shape = AppShapes.TextFieldShape,
                            color = if (isSelected) AppColors.GreenLight
                            else MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable { selectedType = type }
                        ) {
                            Row(
                                modifier = Modifier.padding(
                                    horizontal = AppSpacing.sm,
                                    vertical = AppSpacing.xs
                                ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = isSelected, onClick = { selectedType = type })
                                Column(modifier = Modifier.padding(start = 4.dp)) {
                                    Text(
                                        text = type.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = AppColors.TextPrimary
                                    )
                                    Text(
                                        text = type.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AppColors.TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                if (hasChanged) {
                    Spacer(Modifier.height(AppSpacing.xs))
                    Surface(
                        shape = AppShapes.CardShape,
                        color = Color(0xFFFFF7E6),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(AppSpacing.md),
                            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                        ) {
                            Text(
                                text = "Yang berubah setelah disimpan",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = AppColors.TextPrimary
                            )
                            Text(
                                text = "Istilah aplikasi, saran satuan, dan kategori produk untuk produk " +
                                    "BARU, serta tipe produk awal yang disarankan.",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.TextPrimary
                            )
                            Text(
                                text = "Yang TIDAK berubah: produk yang sudah ada (termasuk tipe produknya), " +
                                    "transaksi, pelanggan, hutang, dan riwayat stok.",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.TextPrimary
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { acknowledged = !acknowledged }
                            ) {
                                Checkbox(
                                    checked = acknowledged,
                                    onCheckedChange = { acknowledged = it }
                                )
                                Text(
                                    text = "Saya mengerti produk yang sudah ada tidak otomatis berubah.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppColors.TextPrimary
                                )
                            }
                        }
                    }
                }

                if (errorText != null) {
                    Text(
                        text = errorText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            if (isSaving) {
                Text(
                    text = "Menyimpan...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextSecondary
                )
            } else if (canSave) {
                PrimaryButton(
                    text = "Simpan & Terapkan",
                    onClick = {
                        onSave(selectedType, selectedType.let { defaultActivitiesFor(it) })
                    },
                    modifier = Modifier.height(48.dp)
                )
            } else {
                Text(
                    text = if (hasChanged) "Centang pengakuan dulu" else "Pilih jenis usaha baru",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextSecondary
                )
            }
        },
        dismissButton = {
            SecondaryButton(
                text = "Batal",
                onClick = onDismiss,
                contentColor = AppColors.TextPrimary
            )
        }
    )
}

/**
 * Starting activities for a newly chosen type.
 *
 * Only the preset's own default activities are seeded. Existing secondary activities chosen by the
 * merchant are preserved by the caller, so switching type never silently drops something they had
 * switched on.
 */
internal fun defaultActivitiesFor(type: BusinessType): Set<String> =
    id.skmnetwork.bukuwarung.domain.business.BusinessTaxonomyRegistry
        .getPreset(type)
        .defaultActivities
        .map { it.id }
        .toSet()

/**
 * Step 2 (C) - combine the activities a merchant already had with the ones the new type defaults to.
 *
 * The merchant's own choices are never removed, because silently switching something off is exactly
 * the kind of surprise that makes a business type change feel unsafe.
 */
internal fun mergeSecondaryActivities(
    current: Set<String>,
    seededForNewType: Set<String>
): Set<String> = (current + seededForNewType).toSet()
