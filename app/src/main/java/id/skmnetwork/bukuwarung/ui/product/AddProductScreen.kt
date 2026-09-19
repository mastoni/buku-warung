package id.skmnetwork.bukuwarung.ui.product

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import id.skmnetwork.bukuwarung.data.local.entity.FulfillmentMode
import id.skmnetwork.bukuwarung.data.local.entity.ItemType
import id.skmnetwork.bukuwarung.data.preferences.UserSettings
import id.skmnetwork.bukuwarung.domain.business.BusinessTaxonomyRegistry
import id.skmnetwork.bukuwarung.ui.components.AppCard
import id.skmnetwork.bukuwarung.ui.components.AppTextField
import id.skmnetwork.bukuwarung.ui.components.CameraBarcodeScannerDialog
import id.skmnetwork.bukuwarung.ui.components.CameraProductPhotoDialog
import id.skmnetwork.bukuwarung.ui.components.PrimaryButton
import id.skmnetwork.bukuwarung.ui.components.ProductImageThumbnail
import id.skmnetwork.bukuwarung.ui.components.SecondaryButton
import id.skmnetwork.bukuwarung.ui.theme.AppColors
import id.skmnetwork.bukuwarung.ui.theme.AppShapes
import id.skmnetwork.bukuwarung.ui.theme.AppSpacing
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddProductScreen(
    viewModel: ProductViewModel,
    userSettings: UserSettings? = null,
    productIdToEdit: Long? = null,
    defaultLowStockLimit: Int = 2,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isEditMode = productIdToEdit != null
    val dbCategories by viewModel.categories.collectAsStateWithLifecycle()

    val resolvedProfile = remember(userSettings?.primaryBusinessType, userSettings?.secondaryActivities) {
        BusinessTaxonomyRegistry.resolve(
            primaryType = userSettings?.primaryBusinessType,
            secondaryActivities = userSettings?.secondaryActivities
        )
    }
    val productLabel = resolvedProfile.terminology.productLabel
    val preferredUnits = resolvedProfile.preferredUnits
    val defaultCategories = resolvedProfile.defaultCategories

    var name by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }
    var category by remember { mutableStateOf("") }
    var purchasePrice by remember { mutableStateOf("") }
    var sellingPrice by remember { mutableStateOf("") }
    var stock by remember { mutableStateOf("") }
    var minimumStock by remember { mutableStateOf(if (isEditMode) "" else defaultLowStockLimit.toString()) }
    var unit by remember { mutableStateOf(preferredUnits.firstOrNull() ?: "pcs") }
    var selectedItemType by remember { mutableStateOf(ItemType.PHYSICAL) }
    var selectedFulfillmentMode by remember { mutableStateOf(FulfillmentMode.MANUAL) }
    var providerId by remember { mutableStateOf("") }
    var providerProductCode by remember { mutableStateOf("") }

    var barcode by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<String?>(null) }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showBarcodeScannerDialog by remember { mutableStateOf(false) }
    var showCameraPhotoDialog by remember { mutableStateOf(false) }
    var showCategorySelectionDialog by remember { mutableStateOf(false) }
    var showCreateCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryInput by remember { mutableStateOf("") }
    var categoryCreateError by remember { mutableStateOf<String?>(null) }
    var isCreatingCategory by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val imagesDir = File(context.filesDir, "product_images").apply { if (!exists()) mkdirs() }
                val destFile = File(imagesDir, "prod_gal_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                imageUri = destFile.absolutePath
            } catch (e: Exception) {
                imageUri = uri.toString()
            }
        }
    }

    LaunchedEffect(productIdToEdit) {
        if (productIdToEdit != null) {
            val product = viewModel.getProductById(productIdToEdit)
            if (product != null) {
                name = product.name
                selectedCategoryId = product.categoryId
                category = viewModel.getCategoryNameById(product.categoryId)
                purchasePrice = product.purchasePrice.toString()
                sellingPrice = product.sellingPrice.toString()
                stock = if (product.stock % 1.0 == 0.0) product.stock.toLong().toString() else product.stock.toString()
                minimumStock = if (product.minimumStock % 1.0 == 0.0) product.minimumStock.toLong().toString() else product.minimumStock.toString()
                unit = product.unit
                barcode = product.barcode ?: ""
                imageUri = product.imageUri
                selectedItemType = try {
                    ItemType.valueOf(product.itemType)
                } catch (e: Exception) {
                    ItemType.PHYSICAL
                }
                selectedFulfillmentMode = try {
                    FulfillmentMode.valueOf(product.fulfillmentMode)
                } catch (e: Exception) {
                    FulfillmentMode.MANUAL
                }
                providerId = product.digitalProviderId ?: ""
                providerProductCode = product.digitalProductCode ?: ""
            }
        }
    }

    if (showBarcodeScannerDialog) {
        CameraBarcodeScannerDialog(
            onDismiss = { showBarcodeScannerDialog = false },
            onBarcodeScanned = { scannedCode ->
                barcode = scannedCode
                showBarcodeScannerDialog = false
            }
        )
    }

    if (showCameraPhotoDialog) {
        CameraProductPhotoDialog(
            onDismiss = { showCameraPhotoDialog = false },
            onPhotoCaptured = { photoPath ->
                imageUri = photoPath
                showCameraPhotoDialog = false
            }
        )
    }

    if (showCategorySelectionDialog) {
        AlertDialog(
            onDismissRequest = { showCategorySelectionDialog = false },
            title = {
                Text(
                    text = "Pilih Kategori",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
                ) {
                    OutlinedButton(
                        onClick = {
                            showCategorySelectionDialog = false
                            newCategoryInput = ""
                            categoryCreateError = null
                            showCreateCategoryDialog = true
                        },
                        shape = AppShapes.ButtonShape,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.GreenPrimary),
                        border = BorderStroke(1.dp, AppColors.GreenPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(AppSpacing.xs))
                        Text("Buat Kategori Baru", fontWeight = FontWeight.Bold)
                    }

                    // Suggested Categories from Taxonomy
                    if (defaultCategories.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Saran Kategori Usaha:",
                                style = MaterialTheme.typography.labelSmall,
                                color = AppColors.TextSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                defaultCategories.forEach { suggestedCat ->
                                    val matchedExisting = dbCategories.find { it.name.equals(suggestedCat, ignoreCase = true) }
                                    FilterChip(
                                        selected = category.equals(suggestedCat, ignoreCase = true),
                                        onClick = {
                                            if (matchedExisting != null) {
                                                selectedCategoryId = matchedExisting.id
                                                category = matchedExisting.name
                                            } else {
                                                selectedCategoryId = null
                                                category = suggestedCat
                                            }
                                            errorMessage = null
                                            showCategorySelectionDialog = false
                                        },
                                        label = { Text(suggestedCat, fontSize = 12.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = AppColors.GreenLight,
                                            selectedLabelColor = AppColors.GreenDark
                                        )
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFFEFEFEF))

                    if (dbCategories.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = AppSpacing.sm),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Category,
                                    contentDescription = null,
                                    tint = AppColors.CardBorder,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(Modifier.height(AppSpacing.xs))
                                Text(
                                    text = "Belum ada kategori tersimpan",
                                    fontWeight = FontWeight.SemiBold,
                                    color = AppColors.TextSecondary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp),
                            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                        ) {
                            items(dbCategories, key = { it.id }) { cat ->
                                val isSelected = selectedCategoryId == cat.id ||
                                        (selectedCategoryId == null && category.equals(cat.name, ignoreCase = true))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (isSelected) AppColors.GreenLight else Color.Transparent,
                                            AppShapes.TextFieldShape
                                        )
                                        .clickable {
                                            selectedCategoryId = cat.id
                                            category = cat.name
                                            errorMessage = null
                                            showCategorySelectionDialog = false
                                        }
                                        .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = cat.name,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) AppColors.GreenDark else AppColors.TextPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Terpilih",
                                            tint = AppColors.GreenPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showCategorySelectionDialog = false }) {
                    Text("Tutup", color = AppColors.TextSecondary)
                }
            }
        )
    }

    if (showCreateCategoryDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isCreatingCategory) {
                    showCreateCategoryDialog = false
                }
            },
            title = {
                Text(
                    text = "Buat Kategori Baru",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                ) {
                    Text(
                        text = "Masukkan nama kategori baru untuk $productLabel Anda.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextSecondary
                    )
                    AppTextField(
                        value = newCategoryInput,
                        onValueChange = {
                            newCategoryInput = it
                            categoryCreateError = null
                        },
                        label = "Nama Kategori",
                        isError = categoryCreateError != null
                    )
                    if (categoryCreateError != null) {
                        Text(
                            text = categoryCreateError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newCategoryInput.trim()
                        if (trimmed.isEmpty()) {
                            categoryCreateError = "Nama kategori tidak boleh kosong"
                            return@Button
                        }
                        isCreatingCategory = true
                        viewModel.createCategory(
                            name = trimmed,
                            onSuccess = { createdCat ->
                                isCreatingCategory = false
                                selectedCategoryId = createdCat.id
                                category = createdCat.name
                                newCategoryInput = ""
                                categoryCreateError = null
                                showCreateCategoryDialog = false
                                showCategorySelectionDialog = false
                            },
                            onError = { err ->
                                isCreatingCategory = false
                                categoryCreateError = err
                            }
                        )
                    },
                    enabled = !isCreatingCategory,
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.GreenPrimary),
                    shape = AppShapes.ButtonShape
                ) {
                    Text(if (isCreatingCategory) "Menyimpan..." else "Simpan", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCreateCategoryDialog = false
                        categoryCreateError = null
                    },
                    enabled = !isCreatingCategory
                ) {
                    Text("Batal", color = AppColors.TextSecondary)
                }
            }
        )
    }

    if (showDeleteDialog && productIdToEdit != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Hapus $productLabel", fontWeight = FontWeight.Bold) },
            text = { Text("Apakah Anda yakin ingin menghapus $productLabel '$name'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        isSaving = true
                        viewModel.deleteProduct(
                            productId = productIdToEdit,
                            onSuccess = {
                                isSaving = false
                                onBack()
                            },
                            onError = { error ->
                                isSaving = false
                                errorMessage = error
                            }
                        )
                    }
                ) {
                    Text("Hapus", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditMode) "Edit $productLabel" else "Tambah $productLabel", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        enabled = !isSaving
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Kembali",
                        )
                    }
                },
            )
        },
        containerColor = Color.White,
        modifier = modifier,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
        ) {
            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = AppSpacing.xs)
                )
            }

            // Image Picker Section
            AppCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppSpacing.md)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .background(AppColors.GreenLight, AppShapes.CardShape),
                            contentAlignment = Alignment.Center
                        ) {
                            ProductImageThumbnail(
                                imageUri = imageUri,
                                modifier = Modifier.size(52.dp)
                            )
                        }
                        Spacer(Modifier.width(AppSpacing.md))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (imageUri.isNullOrEmpty()) "Foto $productLabel (opsional)" else "Foto Tersimpan",
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (imageUri.isNullOrEmpty()) "Ambil dari Kamera / Galeri HP" else "Siap digunakan",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.TextSecondary
                            )
                        }
                        if (!imageUri.isNullOrEmpty()) {
                            IconButton(onClick = { imageUri = null }) {
                                Icon(Icons.Default.Delete, contentDescription = "Hapus Foto", tint = AppColors.RedExpense)
                            }
                        }
                    }

                    Spacer(Modifier.height(AppSpacing.sm))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                    ) {
                        OutlinedButton(
                            onClick = { showCameraPhotoDialog = true },
                            shape = AppShapes.ButtonShape,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(AppSpacing.xs))
                            Text("Kamera", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            shape = AppShapes.ButtonShape,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(AppSpacing.xs))
                            Text("Galeri", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Item Type Selection (Barang Fisik, Jasa / Layanan, Produk Digital, Bahan Bakar)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Tipe $productLabel",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.TextSecondary
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val types = listOf(
                        ItemType.PHYSICAL to "Barang Fisik",
                        ItemType.SERVICE to "Jasa / Layanan",
                        ItemType.DIGITAL to "Produk Digital",
                        ItemType.FUEL to "Bahan Bakar"
                    )
                    types.forEach { (type, label) ->
                        val isSelected = selectedItemType == type
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedItemType = type
                                if (type != id.skmnetwork.bukuwarung.data.local.entity.ItemType.DIGITAL) {
                                    selectedFulfillmentMode = id.skmnetwork.bukuwarung.data.local.entity.FulfillmentMode.MANUAL
                                }
                            },
                            label = { Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AppColors.GreenLight,
                                selectedLabelColor = AppColors.GreenDark
                            )
                        )
                    }
                }
            }


            // Digital Fulfillment Mode Selection
            if (selectedItemType == ItemType.DIGITAL) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Metode Pemenuhan (Fulfillment)",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.TextSecondary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val fulfillmentTypes = listOf(
                            FulfillmentMode.MANUAL to "Manual (Internal)",
                            FulfillmentMode.PROVIDER to "Otomatis (Provider API)"
                        )
                        fulfillmentTypes.forEach { (type, label) ->
                            val isSelected = selectedFulfillmentMode == type
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedFulfillmentMode = type },
                                label = { Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AppColors.GreenLight,
                                    selectedLabelColor = AppColors.GreenDark
                                )
                            )
                        }
                    }
                }

                if (selectedFulfillmentMode == FulfillmentMode.PROVIDER) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = AppColors.GreenLight.copy(alpha = 0.3f)),
                        shape = AppShapes.CardShape,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(AppSpacing.md),
                            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                        ) {
                            Text(
                                text = "Konfigurasi Provider",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            AppTextField(
                                value = providerId,
                                onValueChange = {
                                    providerId = it
                                    errorMessage = null
                                },
                                label = "ID Provider (contoh: DIGIFLAZZ) *",
                                isError = errorMessage == "ID Provider wajib diisi untuk mode Otomatis"
                            )
                            AppTextField(
                                value = providerProductCode,
                                onValueChange = {
                                    providerProductCode = it
                                    errorMessage = null
                                },
                                label = "Kode Produk Provider (SKU) *",
                                isError = errorMessage == "Kode Produk wajib diisi untuk mode Otomatis"
                            )
                        }
                    }
                }
            }

            AppTextField(
                value = name,
                onValueChange = {
                    name = it
                    errorMessage = null
                },
                label = "Nama $productLabel *",
                isError = errorMessage == "Tulis nama produk"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppTextField(
                    value = barcode,
                    onValueChange = {
                        barcode = it
                        errorMessage = null
                    },
                    label = "Barcode (opsional)",
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(AppSpacing.sm))
                IconButton(
                    onClick = { showBarcodeScannerDialog = true },
                    modifier = Modifier
                        .size(52.dp)
                        .background(AppColors.GreenLight, AppShapes.ButtonShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "Pindai Barcode",
                        tint = AppColors.GreenPrimary
                    )
                }
            }

            // Category Selection Field
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showCategorySelectionDialog = true }
            ) {
                OutlinedTextField(
                    value = category,
                    onValueChange = { },
                    readOnly = true,
                    label = { Text("Kategori") },
                    placeholder = { Text("Pilih kategori...") },
                    trailingIcon = {
                        IconButton(onClick = { showCategorySelectionDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Pilih Kategori",
                                tint = AppColors.TextSecondary
                            )
                        }
                    },
                    shape = AppShapes.TextFieldShape,
                    modifier = Modifier.fillMaxWidth()
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { showCategorySelectionDialog = true }
                )
            }

            AppTextField(
                value = purchasePrice,
                onValueChange = {
                    purchasePrice = it
                    errorMessage = null
                },
                label = "Harga Beli (Rp)",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            AppTextField(
                value = sellingPrice,
                onValueChange = {
                    sellingPrice = it
                    errorMessage = null
                },
                label = "Harga Jual (Rp) *",
                isError = errorMessage == "Masukkan harga jual",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            // Stock Fields: Active for PHYSICAL and FUEL; hidden/untracked for SERVICE
            if (selectedItemType == ItemType.PHYSICAL || selectedItemType == ItemType.FUEL) {
                AppTextField(
                    value = stock,
                    onValueChange = {
                        stock = it
                        errorMessage = null
                    },
                    label = "Stok Awal",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                AppTextField(
                    value = minimumStock,
                    onValueChange = {
                        minimumStock = it
                        errorMessage = null
                    },
                    label = "Stok Minimum",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }

            // Unit Field with Suggested Quick Chips
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (preferredUnits.isNotEmpty()) {
                    Text(
                        text = "Saran Satuan:",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.TextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        preferredUnits.forEach { unitSuggestion ->
                            val isSelected = unit.equals(unitSuggestion, ignoreCase = true)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    unit = unitSuggestion
                                    errorMessage = null
                                },
                                label = { Text(unitSuggestion, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AppColors.GreenLight,
                                    selectedLabelColor = AppColors.GreenDark
                                )
                            )
                        }
                    }
                }

                AppTextField(
                    value = unit,
                    onValueChange = {
                        unit = it
                        errorMessage = null
                    },
                    label = "Satuan (misal: pcs, kg, porsi, dsb.)"
                )
            }

            Spacer(modifier = Modifier.height(AppSpacing.sm))

            PrimaryButton(
                text = if (isSaving) "Menyimpan..." else if (isEditMode) "Simpan Perubahan" else "Simpan $productLabel",
                onClick = {
                    if (isSaving) return@PrimaryButton
                    isSaving = true
                    errorMessage = null


                    if (selectedItemType == ItemType.DIGITAL && selectedFulfillmentMode == FulfillmentMode.PROVIDER) {
                        if (providerId.trim().isEmpty()) {
                            errorMessage = "ID Provider wajib diisi untuk mode Otomatis"
                            isSaving = false
                            return@PrimaryButton
                        }
                        if (providerProductCode.trim().isEmpty()) {
                            errorMessage = "Kode Produk wajib diisi untuk mode Otomatis"
                            isSaving = false
                            return@PrimaryButton
                        }
                    }

                    val finalStockStr = if (selectedItemType == ItemType.SERVICE || selectedItemType == ItemType.DIGITAL) "0" else stock
                    val finalMinStockStr = if (selectedItemType == ItemType.SERVICE || selectedItemType == ItemType.DIGITAL) "0" else minimumStock

                    if (isEditMode && productIdToEdit != null) {
                        viewModel.updateProduct(
                            productId = productIdToEdit,
                            name = name,
                            categoryName = category,
                            purchasePriceStr = purchasePrice,
                            sellingPriceStr = sellingPrice,
                            stockStr = finalStockStr,
                            minimumStockStr = finalMinStockStr,
                            unitStr = unit,
                            barcodeStr = barcode,
                            imageUriStr = imageUri,
                            categoryId = selectedCategoryId,
                            itemType = selectedItemType,
                            fulfillmentMode = selectedFulfillmentMode,
                            digitalProviderId = providerId.trim().ifEmpty { null },
                            digitalProductCode = providerProductCode.trim().ifEmpty { null },
                            onSuccess = {
                                isSaving = false
                                onBack()
                            },
                            onError = { error ->
                                isSaving = false
                                errorMessage = error
                            }
                        )
                    } else {
                        viewModel.saveProduct(
                            name = name,
                            categoryName = category,
                            purchasePriceStr = purchasePrice,
                            sellingPriceStr = sellingPrice,
                            stockStr = finalStockStr,
                            minimumStockStr = finalMinStockStr,
                            unitStr = unit,
                            barcodeStr = barcode,
                            imageUriStr = imageUri,
                            categoryId = selectedCategoryId,
                            itemType = selectedItemType,
                            fulfillmentMode = selectedFulfillmentMode,
                            digitalProviderId = providerId.trim().ifEmpty { null },
                            digitalProductCode = providerProductCode.trim().ifEmpty { null },
                            onSuccess = {
                                isSaving = false
                                onBack()
                            },
                            onError = { error ->
                                isSaving = false
                                errorMessage = error
                            }
                        )
                    }
                },
                enabled = !isSaving
            )

            if (isEditMode) {
                SecondaryButton(
                    text = "Hapus $productLabel",
                    onClick = { showDeleteDialog = true },
                    enabled = !isSaving
                )
            }

            Spacer(modifier = Modifier.height(AppSpacing.lg))
        }
    }
}
