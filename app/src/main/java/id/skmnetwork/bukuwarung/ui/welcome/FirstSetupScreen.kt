package id.skmnetwork.bukuwarung.ui.welcome

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.skmnetwork.bukuwarung.domain.business.BusinessActivity
import id.skmnetwork.bukuwarung.domain.business.BusinessCategory
import id.skmnetwork.bukuwarung.domain.business.BusinessTaxonomyRegistry
import id.skmnetwork.bukuwarung.domain.business.BusinessType
import id.skmnetwork.bukuwarung.ui.components.AppTextField
import id.skmnetwork.bukuwarung.ui.components.PrimaryButton
import id.skmnetwork.bukuwarung.ui.components.SecondaryButton
import id.skmnetwork.bukuwarung.ui.theme.AppColors

@Composable
fun FirstSetupScreen(
    onCompleteSetup: (
        shopName: String,
        ownerName: String,
        phone: String,
        address: String,
        primaryBusinessType: String,
        secondaryActivities: Set<String>
    ) -> Unit
) {
    // Current Wizard Step (1: Identity, 2: Business Type, 3: Activities, 4: Summary)
    var currentStep by remember { mutableIntStateOf(1) }

    // Step 1 State: Identity
    var shopNameInput by remember { mutableStateOf("") }
    var ownerNameInput by remember { mutableStateOf("") }
    var phoneInput by remember { mutableStateOf("") }
    var addressInput by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    // Step 2 State: Business Type Selection
    var selectedCategoryFilter by remember { mutableStateOf<BusinessCategory?>(null) }
    var selectedBusinessType by remember { mutableStateOf(BusinessTaxonomyRegistry.DEFAULT_BUSINESS_TYPE) }

    // Step 3 State: Operational Activities (Prepopulated from default preset)
    var selectedActivities by remember {
        mutableStateOf(BusinessTaxonomyRegistry.getPreset(BusinessTaxonomyRegistry.DEFAULT_BUSINESS_TYPE).defaultActivities)
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        color = Color(0xFFF9FBF9)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Top App Bar / Step Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentStep > 1) {
                    IconButton(
                        onClick = {
                            currentStep -= 1
                            errorText = null
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali",
                            tint = AppColors.TextPrimary
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when (currentStep) {
                            1 -> "Langkah 1 dari 4: Identitas Usaha"
                            2 -> "Langkah 2 dari 4: Jenis Usaha Utama"
                            3 -> "Langkah 3 dari 4: Aktivitas Operasional"
                            else -> "Langkah 4 dari 4: Ringkasan Profil"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.GreenPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when (currentStep) {
                            1 -> "Informasi Toko / Warung"
                            2 -> "Pilih Model Usaha"
                            3 -> "Sesuaikan Fitur Operasional"
                            else -> "Konfirmasi & Mulai"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextPrimary
                    )
                }
            }

            // Step Progress Bar
            LinearProgressIndicator(
                progress = { currentStep / 4f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = AppColors.GreenPrimary,
                trackColor = Color(0xFFE0EAE2),
                strokeCap = StrokeCap.Round
            )

            Spacer(Modifier.height(16.dp))

            // Main Step Content Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (currentStep) {
                    1 -> Step1Identity(
                        shopName = shopNameInput,
                        onShopNameChange = {
                            shopNameInput = it
                            errorText = null
                        },
                        ownerName = ownerNameInput,
                        onOwnerNameChange = { ownerNameInput = it },
                        phone = phoneInput,
                        onPhoneChange = { phoneInput = it },
                        address = addressInput,
                        onAddressChange = { addressInput = it },
                        errorText = errorText,
                        onNext = {
                            if (shopNameInput.trim().isEmpty()) {
                                errorText = "Nama warung / usaha wajib diisi"
                            } else {
                                errorText = null
                                currentStep = 2
                            }
                        }
                    )
                    2 -> Step2BusinessType(
                        selectedCategory = selectedCategoryFilter,
                        onSelectCategory = { selectedCategoryFilter = it },
                        selectedType = selectedBusinessType,
                        onSelectType = { newType ->
                            selectedBusinessType = newType
                            // Reset activities to new preset's default activities
                            val preset = BusinessTaxonomyRegistry.getPreset(newType)
                            selectedActivities = preset.defaultActivities
                        },
                        onBack = { currentStep = 1 },
                        onNext = { currentStep = 3 }
                    )
                    3 -> Step3Activities(
                        selectedBusinessType = selectedBusinessType,
                        selectedActivities = selectedActivities,
                        onToggleActivity = { activity ->
                            selectedActivities = if (selectedActivities.contains(activity)) {
                                if (selectedActivities.size > 1) {
                                    selectedActivities - activity
                                } else {
                                    selectedActivities // Keep at least one
                                }
                            } else {
                                selectedActivities + activity
                            }
                        },
                        onBack = { currentStep = 2 },
                        onNext = { currentStep = 4 }
                    )
                    4 -> Step4Summary(
                        shopName = shopNameInput.trim(),
                        ownerName = ownerNameInput.trim(),
                        phone = phoneInput.trim(),
                        address = addressInput.trim(),
                        selectedType = selectedBusinessType,
                        selectedActivities = selectedActivities,
                        onBack = { currentStep = 3 },
                        onSaveAndFinish = {
                            onCompleteSetup(
                                shopNameInput.trim(),
                                ownerNameInput.trim(),
                                phoneInput.trim(),
                                addressInput.trim(),
                                selectedBusinessType.id,
                                selectedActivities.map { it.id }.toSet()
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun Step1Identity(
    shopName: String,
    onShopNameChange: (String) -> Unit,
    ownerName: String,
    onOwnerNameChange: (String) -> Unit,
    phone: String,
    onPhoneChange: (String) -> Unit,
    address: String,
    onAddressChange: (String) -> Unit,
    errorText: String?,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(AppColors.GreenLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Storefront,
                    contentDescription = null,
                    tint = AppColors.GreenPrimary,
                    modifier = Modifier.size(30.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Selamat Datang di Buku Warung 👋",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Lengkapi data usaha Anda untuk pengalaman yang disesuaikan.",
                color = AppColors.TextSecondary,
                fontSize = 13.sp
            )

            Spacer(Modifier.height(16.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFE5EBE5)),
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (errorText != null) {
                        Text(
                            text = errorText,
                            color = AppColors.RedExpense,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    AppTextField(
                        value = shopName,
                        onValueChange = onShopNameChange,
                        label = "Nama Usaha / Toko *",
                        isError = errorText != null
                    )

                    AppTextField(
                        value = ownerName,
                        onValueChange = onOwnerNameChange,
                        label = "Nama Pemilik (opsional)"
                    )

                    AppTextField(
                        value = phone,
                        onValueChange = onPhoneChange,
                        label = "Nomor WhatsApp / HP (opsional)",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )

                    AppTextField(
                        value = address,
                        onValueChange = onAddressChange,
                        label = "Alamat Usaha (opsional)"
                    )
                }
            }
        }

        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            PrimaryButton(
                text = "Lanjut: Pilih Jenis Usaha",
                onClick = onNext
            )
        }
    }
}

@Composable
private fun Step2BusinessType(
    selectedCategory: BusinessCategory?,
    onSelectCategory: (BusinessCategory?) -> Unit,
    selectedType: BusinessType,
    onSelectType: (BusinessType) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val scrollState = rememberScrollState()

    val filteredTypes = remember(selectedCategory) {
        if (selectedCategory == null) {
            BusinessType.entries
        } else {
            BusinessType.entries.filter { it.category == selectedCategory }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Text(
                text = "Pilih kategori dan model usaha utama Anda:",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary
            )

            Spacer(Modifier.height(10.dp))

            // Category Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { onSelectCategory(null) },
                    label = { Text("Semua (${BusinessType.entries.size})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AppColors.GreenLight,
                        selectedLabelColor = AppColors.GreenDark
                    )
                )

                BusinessCategory.entries.forEach { category ->
                    val count = BusinessType.entries.count { it.category == category }
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { onSelectCategory(category) },
                        label = { Text("${category.displayName} ($count)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AppColors.GreenLight,
                            selectedLabelColor = AppColors.GreenDark
                        )
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Business Types List
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                filteredTypes.forEach { type ->
                    val isSelected = selectedType == type
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) Color(0xFFF1F9F4) else Color.White,
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) AppColors.GreenPrimary else Color(0xFFE5EBE5)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectType(type) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (isSelected) AppColors.GreenPrimary else Color(0xFF9E9E9E),
                                modifier = Modifier.size(22.dp)
                            )

                            Spacer(Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = type.displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) AppColors.GreenDark else AppColors.TextPrimary
                                    )
                                    Text(
                                        text = type.category.displayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AppColors.TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = type.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppColors.TextSecondary,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Action Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SecondaryButton(
                text = "Kembali",
                onClick = onBack,
                modifier = Modifier.weight(1f),
                contentColor = AppColors.TextSecondary
            )
            PrimaryButton(
                text = "Lanjut: Aktivitas",
                onClick = onNext,
                modifier = Modifier.weight(1.5f)
            )
        }
    }
}

@Composable
private fun Step3Activities(
    selectedBusinessType: BusinessType,
    selectedActivities: Set<BusinessActivity>,
    onToggleActivity: (BusinessActivity) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val preset = remember(selectedBusinessType) {
        BusinessTaxonomyRegistry.getPreset(selectedBusinessType)
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            // Information Card
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFEBF5FF),
                border = BorderStroke(1.dp, Color(0xFFBCE0FD)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFF0066CC),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Aktivitas standar untuk ${selectedBusinessType.displayName} telah dipilih otomatis. Anda bebas menambah atau mengurangi sesuai kebutuhan toko.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF003D7A),
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Aktivitas Operasional Usaha:",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary
            )

            Spacer(Modifier.height(8.dp))

            BusinessActivity.entries.forEach { activity ->
                val isChecked = selectedActivities.contains(activity)
                val isDefaultPreset = preset.defaultActivities.contains(activity)

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isChecked) Color.White else Color(0xFFF9F9F9),
                    border = BorderStroke(
                        width = if (isChecked) 1.5.dp else 1.dp,
                        color = if (isChecked) AppColors.GreenPrimary else Color(0xFFE0E0E0)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onToggleActivity(activity) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { onToggleActivity(activity) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = AppColors.GreenPrimary
                            )
                        )

                        Spacer(Modifier.width(8.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = activity.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isChecked) AppColors.TextPrimary else Color(0xFF757575)
                                )

                                if (isDefaultPreset) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = AppColors.GreenLight
                                    ) {
                                        Text(
                                            text = "Bawaan",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AppColors.GreenDark,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                } else if (isChecked) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFFFFF4E5)
                                    ) {
                                        Text(
                                            text = "Tambahan",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFFB76E00),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = activity.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.TextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 15.sp
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
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SecondaryButton(
                text = "Kembali",
                onClick = onBack,
                modifier = Modifier.weight(1f),
                contentColor = AppColors.TextSecondary
            )
            PrimaryButton(
                text = "Lanjut: Ringkasan",
                onClick = onNext,
                modifier = Modifier.weight(1.5f)
            )
        }
    }
}

@Composable
private fun Step4Summary(
    shopName: String,
    ownerName: String,
    phone: String,
    address: String,
    selectedType: BusinessType,
    selectedActivities: Set<BusinessActivity>,
    onBack: () -> Unit,
    onSaveAndFinish: () -> Unit
) {
    val resolvedProfile = remember(selectedType, selectedActivities) {
        BusinessTaxonomyRegistry.resolve(
            primaryType = selectedType.id,
            secondaryActivities = selectedActivities.map { it.id }.toSet()
        )
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Card 1: Profil Usaha
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFE5EBE5)),
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Storefront,
                            contentDescription = null,
                            tint = AppColors.GreenPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Profil Usaha",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.TextPrimary
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = Color(0xFFF0F0F0))
                    Spacer(Modifier.height(10.dp))

                    SummaryRow(label = "Nama Usaha", value = shopName)
                    if (ownerName.isNotEmpty()) SummaryRow(label = "Pemilik", value = ownerName)
                    if (phone.isNotEmpty()) SummaryRow(label = "WhatsApp / HP", value = phone)
                    if (address.isNotEmpty()) SummaryRow(label = "Alamat", value = address)
                }
            }

            // Card 2: Jenis & Model Usaha
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFE5EBE5)),
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Category,
                            contentDescription = null,
                            tint = AppColors.GreenPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Jenis Usaha",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.TextPrimary
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = Color(0xFFF0F0F0))
                    Spacer(Modifier.height(10.dp))

                    SummaryRow(label = "Jenis Utama", value = selectedType.displayName)
                    SummaryRow(label = "Kategori", value = selectedType.category.displayName)
                }
            }

            // Card 3: Aktivitas Operasional
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFE5EBE5)),
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = null,
                            tint = AppColors.GreenPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Aktivitas yang Diaktifkan (${selectedActivities.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.TextPrimary
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = Color(0xFFF0F0F0))
                    Spacer(Modifier.height(10.dp))

                    selectedActivities.forEach { activity ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = AppColors.GreenPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = activity.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = AppColors.TextPrimary
                            )
                        }
                    }
                }
            }

            // Card 4: Penyesuaian Antarmuka & Istilah (Adaptive Terminology Preview)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFF4FAF6),
                border = BorderStroke(1.dp, Color(0xFFCCE8D7)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = AppColors.GreenDark,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Pratinjau Istilah Aplikasi",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.GreenDark
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = Color(0xFFD8EDE1))
                    Spacer(Modifier.height(10.dp))

                    SummaryRow(label = "Istilah Produk", value = resolvedProfile.terminology.productLabel)
                    SummaryRow(label = "Istilah Transaksi", value = resolvedProfile.terminology.transactionLabel)
                    SummaryRow(label = "Istilah Pelanggan", value = resolvedProfile.terminology.customerLabel)
                    SummaryRow(label = "Satuan Terpopuler", value = resolvedProfile.preferredUnits.take(5).joinToString(", "))
                }
            }
        }

        // Action Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SecondaryButton(
                text = "Kembali",
                onClick = onBack,
                modifier = Modifier.weight(1f),
                contentColor = AppColors.TextSecondary
            )
            PrimaryButton(
                text = "Simpan & Masuk Beranda",
                onClick = onSaveAndFinish,
                modifier = Modifier.weight(1.6f)
            )
        }
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.TextSecondary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.TextPrimary,
            modifier = Modifier.weight(1.4f)
        )
    }
}
