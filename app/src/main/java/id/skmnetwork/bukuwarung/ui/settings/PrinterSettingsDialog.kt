package id.skmnetwork.bukuwarung.ui.settings

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import id.skmnetwork.bukuwarung.data.preferences.UserPreferencesRepository
import id.skmnetwork.bukuwarung.data.preferences.UserSettings
import id.skmnetwork.bukuwarung.domain.receipt.ReceiptPaperWidth
import id.skmnetwork.bukuwarung.printer.PrinterService
import id.skmnetwork.bukuwarung.printer.connection.BluetoothPrinterConnection
import id.skmnetwork.bukuwarung.printer.connection.UsbPrinterConnection
import id.skmnetwork.bukuwarung.ui.theme.AppColors
import id.skmnetwork.bukuwarung.ui.theme.AppShapes
import id.skmnetwork.bukuwarung.ui.theme.AppSpacing
import kotlinx.coroutines.launch

@SuppressLint("MissingPermission")
@Composable
fun PrinterSettingsDialog(
    userSettings: UserSettings,
    userPreferencesRepository: UserPreferencesRepository,
    printerService: PrinterService,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedType by remember { mutableStateOf(if (userSettings.printerType in listOf("BLUETOOTH", "USB")) userSettings.printerType else "BLUETOOTH") }
    var selectedDeviceName by remember { mutableStateOf(userSettings.printerDeviceName) }
    var selectedAddress by remember { mutableStateOf(userSettings.printerAddress) }
    var selectedPaperWidth by remember { mutableStateOf(userSettings.printerPaperWidth) }

    var isConnecting by remember { mutableStateOf(false) }
    var isTestingPrint by remember { mutableStateOf(false) }
    var statusMessage by remember {
        mutableStateOf(
            when {
                printerService.isConnected -> "Terhubung"
                userSettings.printerType == "NONE" || userSettings.printerAddress.isBlank() -> "Belum dikonfigurasi"
                else -> "Tidak terhubung"
            }
        )
    }

    // Bluetooth Devices State
    var pairedBluetoothDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var bluetoothError by remember { mutableStateOf<String?>(null) }

    // USB Devices State
    var usbDevices by remember { mutableStateOf<List<UsbDevice>>(emptyList()) }

    // Permission state
    var hasBluetoothPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val connectGranted = permissions[Manifest.permission.BLUETOOTH_CONNECT] ?: true
        hasBluetoothPermission = connectGranted
        if (connectGranted) {
            loadBluetoothDevices(context) { list, err ->
                pairedBluetoothDevices = list
                bluetoothError = err
            }
        } else {
            bluetoothError = "Izin Bluetooth Connect diperlukan untuk mencari printer"
        }
    }

    fun refreshDevices() {
        if (selectedType == "BLUETOOTH") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasBluetoothPermission) {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN
                    )
                )
            } else {
                loadBluetoothDevices(context) { list, err ->
                    pairedBluetoothDevices = list
                    bluetoothError = err
                }
            }
        } else {
            val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
            usbDevices = usbManager?.deviceList?.values?.toList() ?: emptyList()
        }
    }

    LaunchedEffect(selectedType) {
        refreshDevices()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                Icon(
                    imageVector = Icons.Default.Print,
                    contentDescription = null,
                    tint = AppColors.GreenPrimary
                )
                Text(
                    text = "Pengaturan Printer Thermal",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
                modifier = Modifier.fillMaxWidth()
            ) {
                // 1. Status Koneksi Card
                item {
                    Surface(
                        shape = AppShapes.CardShape,
                        color = when {
                            statusMessage.contains("Terhubung", ignoreCase = true) && !statusMessage.contains("Tidak", ignoreCase = true) -> Color(0xFFE8F5E9)
                            statusMessage.contains("Menghubungkan", ignoreCase = true) -> Color(0xFFFFF3E0)
                            statusMessage.contains("Error", ignoreCase = true) || statusMessage.contains("Gagal", ignoreCase = true) -> Color(0xFFFFEBEE)
                            else -> Color(0xFFF5F5F5)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                            modifier = Modifier.padding(AppSpacing.md)
                        ) {
                            Icon(
                                imageVector = when {
                                    statusMessage.contains("Terhubung", ignoreCase = true) && !statusMessage.contains("Tidak", ignoreCase = true) -> Icons.Default.CheckCircle
                                    statusMessage.contains("Error", ignoreCase = true) || statusMessage.contains("Gagal", ignoreCase = true) -> Icons.Default.Error
                                    else -> Icons.Default.Warning
                                },
                                contentDescription = null,
                                tint = when {
                                    statusMessage.contains("Terhubung", ignoreCase = true) && !statusMessage.contains("Tidak", ignoreCase = true) -> AppColors.GreenPrimary
                                    statusMessage.contains("Error", ignoreCase = true) || statusMessage.contains("Gagal", ignoreCase = true) -> AppColors.RedExpense
                                    else -> AppColors.TextSecondary
                                },
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = "Status: $statusMessage",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = when {
                                        statusMessage.contains("Terhubung", ignoreCase = true) && !statusMessage.contains("Tidak", ignoreCase = true) -> AppColors.GreenPrimary
                                        statusMessage.contains("Error", ignoreCase = true) || statusMessage.contains("Gagal", ignoreCase = true) -> AppColors.RedExpense
                                        else -> AppColors.TextPrimary
                                    }
                                )
                                if (selectedDeviceName.isNotBlank()) {
                                    Text(
                                        text = "$selectedDeviceName ($selectedAddress)",
                                        fontSize = 12.sp,
                                        color = AppColors.TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Jenis Koneksi (Bluetooth / USB)
                item {
                    Text(
                        text = "Jenis Koneksi:",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = AppColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilterChip(
                            selected = selectedType == "BLUETOOTH",
                            onClick = { selectedType = "BLUETOOTH" },
                            label = { Text("Bluetooth") },
                            leadingIcon = { Icon(Icons.Default.Bluetooth, null, modifier = Modifier.size(16.dp)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AppColors.GreenPrimary.copy(alpha = 0.15f),
                                selectedLabelColor = AppColors.GreenPrimary
                            )
                        )
                        FilterChip(
                            selected = selectedType == "USB",
                            onClick = { selectedType = "USB" },
                            label = { Text("USB (OTG)") },
                            leadingIcon = { Icon(Icons.Default.Usb, null, modifier = Modifier.size(16.dp)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AppColors.GreenPrimary.copy(alpha = 0.15f),
                                selectedLabelColor = AppColors.GreenPrimary
                            )
                        )
                    }
                }

                // 3. Lebar Kertas Struk (58mm vs 80mm)
                item {
                    Text(
                        text = "Ukuran Kertas Struk:",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = AppColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { selectedPaperWidth = "58MM" }
                        ) {
                            RadioButton(
                                selected = selectedPaperWidth == "58MM",
                                onClick = { selectedPaperWidth = "58MM" },
                                colors = RadioButtonDefaults.colors(selectedColor = AppColors.GreenPrimary)
                            )
                            Text("58 mm (32 Kolom)", fontSize = 13.sp)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { selectedPaperWidth = "80MM" }
                        ) {
                            RadioButton(
                                selected = selectedPaperWidth == "80MM",
                                onClick = { selectedPaperWidth = "80MM" },
                                colors = RadioButtonDefaults.colors(selectedColor = AppColors.GreenPrimary)
                            )
                            Text("80 mm (48 Kolom)", fontSize = 13.sp)
                        }
                    }
                }

                // 4. Daftar Perangkat Tersedia
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (selectedType == "BLUETOOTH") "Pilih Printer Bluetooth (Paired):" else "Pilih Printer USB Terhubung:",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = AppColors.TextPrimary
                        )
                        IconButton(onClick = { refreshDevices() }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(18.dp))
                        }
                    }
                }

                if (selectedType == "BLUETOOTH") {
                    if (bluetoothError != null) {
                        item {
                            Text(
                                text = bluetoothError!!,
                                color = AppColors.RedExpense,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    } else if (pairedBluetoothDevices.isEmpty()) {
                        item {
                            Text(
                                text = "Tidak ada printer Bluetooth yang sudah dipasangkan (paired). Pasangkan printer terlebih dahulu di Pengaturan Bluetooth HP.",
                                color = AppColors.TextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    } else {
                        items(pairedBluetoothDevices) { dev ->
                            val name = dev.name ?: "Unknown Device"
                            val address = dev.address
                            val isSelected = selectedAddress == address

                            DeviceRow(
                                name = name,
                                address = address,
                                isSelected = isSelected,
                                onClick = {
                                    selectedDeviceName = name
                                    selectedAddress = address
                                }
                            )
                        }
                    }
                } else {
                    if (usbDevices.isEmpty()) {
                        item {
                            Text(
                                text = "Tidak ada printer USB yang terdeteksi via kabel OTG.",
                                color = AppColors.TextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    } else {
                        items(usbDevices) { uDev ->
                            val name = uDev.productName ?: uDev.deviceName ?: "USB Printer"
                            val address = "VID:${uDev.vendorId}-PID:${uDev.productId}"
                            val isSelected = selectedAddress == address

                            DeviceRow(
                                name = name,
                                address = address,
                                isSelected = isSelected,
                                onClick = {
                                    selectedDeviceName = name
                                    selectedAddress = address
                                }
                            )
                        }
                    }
                }

                // 5. Action Buttons (Hubungkan, Test Print, Putuskan)
                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = AppSpacing.xs))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Hubungkan / Putuskan Button
                        if (printerService.isConnected) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        printerService.clearConnection()
                                        statusMessage = "Tidak terhubung"
                                        Toast.makeText(context, "Printer diputuskan", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Putuskan", color = AppColors.RedExpense, fontSize = 12.sp)
                            }
                        } else {
                            Button(
                                onClick = {
                                    if (selectedAddress.isBlank()) {
                                        Toast.makeText(context, "Pilih printer terlebih dahulu", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    scope.launch {
                                        isConnecting = true
                                        statusMessage = "Menghubungkan ke $selectedDeviceName..."
                                        val connection = if (selectedType == "BLUETOOTH") {
                                            BluetoothPrinterConnection(macAddress = selectedAddress)
                                        } else {
                                            val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
                                            val uDev = usbDevices.find { "VID:${it.vendorId}-PID:${it.productId}" == selectedAddress }
                                                ?: usbDevices.firstOrNull()
                                            if (uDev != null) {
                                                UsbPrinterConnection(usbManager = usbManager, usbDevice = uDev)
                                            } else {
                                                null
                                            }
                                        }

                                        if (connection == null) {
                                            isConnecting = false
                                            statusMessage = "Error: Perangkat tidak ditemukan"
                                            return@launch
                                        }

                                        val connectResult = connection.connect()
                                        isConnecting = false
                                        if (connectResult.isSuccess) {
                                            printerService.setConnection(connection)
                                            printerService.setPaperWidth(
                                                if (selectedPaperWidth == "80MM") ReceiptPaperWidth.WIDTH_80MM else ReceiptPaperWidth.WIDTH_58MM
                                            )
                                            statusMessage = "Terhubung"
                                            Toast.makeText(context, "Berhasil terhubung ke printer!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            val err = connectResult.exceptionOrNull()?.localizedMessage ?: "Gagal terhubung"
                                            statusMessage = "Gagal: $err"
                                            Toast.makeText(context, "Gagal terhubung: $err", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                enabled = selectedAddress.isNotBlank() && !isConnecting,
                                colors = ButtonDefaults.buttonColors(containerColor = AppColors.GreenPrimary),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (isConnecting) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                                } else {
                                    Text("Hubungkan", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Test Print Button
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isTestingPrint = true
                                    val width = if (selectedPaperWidth == "80MM") ReceiptPaperWidth.WIDTH_80MM else ReceiptPaperWidth.WIDTH_58MM
                                    val printResult = printerService.printTestReceipt(
                                        shopName = userSettings.shopName,
                                        width = width
                                    )
                                    isTestingPrint = false
                                    if (printResult.isSuccess) {
                                        Toast.makeText(context, "Uji cetak berhasil terkirim!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        val err = printResult.exceptionOrNull()?.localizedMessage ?: "Printer tidak terhubung"
                                        Toast.makeText(context, "Uji cetak gagal: $err", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            enabled = !isTestingPrint,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isTestingPrint) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = AppColors.GreenPrimary)
                            } else {
                                Text("Uji Cetak", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        // 1. Maintain or set connection on PrinterService
                        if (selectedAddress.isNotBlank()) {
                            if (!printerService.isMatchingConnection(selectedType, selectedAddress)) {
                                val connection = if (selectedType == "BLUETOOTH") {
                                    BluetoothPrinterConnection(macAddress = selectedAddress)
                                } else {
                                    val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
                                    val uDev = usbDevices.find { "VID:${it.vendorId}-PID:${it.productId}" == selectedAddress }
                                        ?: usbDevices.firstOrNull()
                                    if (uDev != null) UsbPrinterConnection(usbManager = usbManager, usbDevice = uDev) else null
                                }
                                if (connection != null) {
                                    printerService.setConnection(connection)
                                }
                            }
                            printerService.setPaperWidth(
                                if (selectedPaperWidth == "80MM") ReceiptPaperWidth.WIDTH_80MM else ReceiptPaperWidth.WIDTH_58MM
                            )
                        }

                        // 2. Persist to DataStore
                        userPreferencesRepository.updatePrinterConfig(
                            printerType = selectedType,
                            printerDeviceName = selectedDeviceName,
                            printerAddress = selectedAddress,
                            printerPaperWidth = selectedPaperWidth,
                            printerAutoConnect = true
                        )
                        Toast.makeText(context, "Pengaturan printer berhasil disimpan", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.GreenPrimary)
            ) {
                Text("SIMPAN", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup", color = AppColors.TextSecondary)
            }
        }
    )
}

@Composable
private fun DeviceRow(
    name: String,
    address: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) AppColors.GreenPrimary.copy(alpha = 0.12f) else Color(0xFFFAFAFA),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .border(
                width = 1.dp,
                color = if (isSelected) AppColors.GreenPrimary else Color(0xFFE0E0E0),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm)
        ) {
            Column {
                Text(
                    text = name,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 13.sp,
                    color = if (isSelected) AppColors.GreenPrimary else AppColors.TextPrimary
                )
                Text(
                    text = address,
                    fontSize = 11.sp,
                    color = AppColors.TextSecondary
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = AppColors.GreenPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@SuppressLint("MissingPermission")
private fun loadBluetoothDevices(
    context: Context,
    onResult: (List<BluetoothDevice>, String?) -> Unit
) {
    val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    if (bluetoothAdapter == null) {
        onResult(emptyList(), "Perangkat ini tidak mendukung Bluetooth")
        return
    }
    if (!bluetoothAdapter.isEnabled) {
        onResult(emptyList(), "Bluetooth sedang mati. Silakan aktifkan Bluetooth di Pengaturan HP.")
        return
    }

    try {
        val paired = bluetoothAdapter.bondedDevices?.toList() ?: emptyList()
        onResult(paired, null)
    } catch (e: SecurityException) {
        onResult(emptyList(), "Izin akses Bluetooth belum diberikan")
    } catch (e: Exception) {
        onResult(emptyList(), "Gagal membaca daftar Bluetooth: ${e.localizedMessage}")
    }
}
