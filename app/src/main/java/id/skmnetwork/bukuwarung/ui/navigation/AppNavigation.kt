package id.skmnetwork.bukuwarung.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.preferences.UserPreferencesRepository
import id.skmnetwork.bukuwarung.data.preferences.UserSettings
import id.skmnetwork.bukuwarung.data.repository.CustomerRepository
import id.skmnetwork.bukuwarung.data.repository.ProductRepository
import id.skmnetwork.bukuwarung.data.repository.ReportRepository
import id.skmnetwork.bukuwarung.data.repository.SupplierRepository
import id.skmnetwork.bukuwarung.license.LicenseManager
import id.skmnetwork.bukuwarung.license.LicenseStatus
import id.skmnetwork.bukuwarung.ui.cash.CashScreen
import id.skmnetwork.bukuwarung.ui.customer.CustomerViewModel
import id.skmnetwork.bukuwarung.ui.customer.CustomerViewModelFactory
import id.skmnetwork.bukuwarung.ui.customer.CustomersScreen
import id.skmnetwork.bukuwarung.ui.home.HomeScreen
import id.skmnetwork.bukuwarung.ui.license.LicenseGateScreen
import id.skmnetwork.bukuwarung.ui.pos.PosScreen
import id.skmnetwork.bukuwarung.ui.product.AddProductScreen
import id.skmnetwork.bukuwarung.ui.product.ProductViewModel
import id.skmnetwork.bukuwarung.ui.product.ProductViewModelFactory
import id.skmnetwork.bukuwarung.ui.product.ProductsScreen
import id.skmnetwork.bukuwarung.ui.purchase.PurchaseScreen
import id.skmnetwork.bukuwarung.ui.report.ReportViewModel
import id.skmnetwork.bukuwarung.ui.report.ReportViewModelFactory
import id.skmnetwork.bukuwarung.ui.report.ReportsScreen
import id.skmnetwork.bukuwarung.backup.BackupRestoreManager
import id.skmnetwork.bukuwarung.ui.security.PinLockScreen
import id.skmnetwork.bukuwarung.ui.settings.BackupViewModel
import id.skmnetwork.bukuwarung.ui.settings.BackupViewModelFactory
import id.skmnetwork.bukuwarung.ui.settings.SettingsScreen
import id.skmnetwork.bukuwarung.ui.supplier.SupplierViewModel
import id.skmnetwork.bukuwarung.ui.supplier.SupplierViewModelFactory
import id.skmnetwork.bukuwarung.ui.supplier.SuppliersScreen
import id.skmnetwork.bukuwarung.ui.theme.AppColors
import id.skmnetwork.bukuwarung.ui.welcome.FirstSetupScreen
import id.skmnetwork.bukuwarung.ui.welcome.WelcomeScreen
import kotlinx.coroutines.launch

@Composable
fun BukuWarungApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val userPreferencesRepository = remember { UserPreferencesRepository(context) }
    val licenseManager = remember { LicenseManager(userPreferencesRepository) }

    val userSettings by userPreferencesRepository.userSettings.collectAsStateWithLifecycle(initialValue = UserSettings())
    val licenseStatus by licenseManager.licenseStatus.collectAsStateWithLifecycle()

    var isCheckingExistingUser by remember { mutableStateOf(true) }
    var isDebugBypassed by remember { mutableStateOf(false) }
    var isPinUnlocked by remember { mutableStateOf(false) }
    var showFirstSetupScreen by remember { mutableStateOf(false) }

    var screen by remember { mutableStateOf(AppScreen.HOME) }
    var previousScreen by remember { mutableStateOf(AppScreen.PRODUCTS) }
    var selectedProductId by remember { mutableStateOf<Long?>(null) }

    val database = remember { AppDatabase.getDatabase(context) }
    val productRepository = remember { ProductRepository(database) }
    val customerRepository = remember { CustomerRepository(database) }
    val supplierRepository = remember { SupplierRepository(database) }
    val reportRepository = remember { ReportRepository(database) }

    LaunchedEffect(Unit) {
        userPreferencesRepository.autoMigrateExistingUserIfNeeded(database)
        isCheckingExistingUser = false
    }

    val printerService = remember { id.skmnetwork.bukuwarung.printer.PrinterService() }

    // Restore printer configuration from DataStore on startup / settings change (Idempotent)
    LaunchedEffect(userSettings.printerType, userSettings.printerAddress, userSettings.printerPaperWidth) {
        val paperWidth = if (userSettings.printerPaperWidth == "80MM") {
            id.skmnetwork.bukuwarung.domain.receipt.ReceiptPaperWidth.WIDTH_80MM
        } else {
            id.skmnetwork.bukuwarung.domain.receipt.ReceiptPaperWidth.WIDTH_58MM
        }
        printerService.setPaperWidth(paperWidth)

        if (userSettings.printerType == "NONE" || userSettings.printerAddress.isBlank()) {
            return@LaunchedEffect
        }

        // Check if already matching existing active connection
        if (printerService.isMatchingConnection(userSettings.printerType, userSettings.printerAddress)) {
            // Already configured to this printer. If autoConnect is on and not yet connected, attempt reconnect.
            if (userSettings.printerAutoConnect && !printerService.isConnected) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        printerService.connect()
                    } catch (_: Exception) {}
                }
            }
            return@LaunchedEffect
        }

        // New or different printer address/type configured
        if (userSettings.printerType == "BLUETOOTH") {
            val conn = id.skmnetwork.bukuwarung.printer.connection.BluetoothPrinterConnection(
                macAddress = userSettings.printerAddress
            )
            printerService.setConnection(conn)
            if (userSettings.printerAutoConnect) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        conn.connect()
                    } catch (_: Exception) {}
                }
            }
        } else if (userSettings.printerType == "USB") {
            val usbManager = context.getSystemService(android.content.Context.USB_SERVICE) as? android.hardware.usb.UsbManager
            val targetDevice = usbManager?.deviceList?.values?.find {
                "VID:${it.vendorId}-PID:${it.productId}" == userSettings.printerAddress
            }
            if (targetDevice != null && usbManager != null) {
                val conn = id.skmnetwork.bukuwarung.printer.connection.UsbPrinterConnection(
                    usbManager = usbManager,
                    usbDevice = targetDevice
                )
                printerService.setConnection(conn)
                if (userSettings.printerAutoConnect && usbManager.hasPermission(targetDevice)) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            conn.connect()
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    val productViewModel: ProductViewModel = viewModel(
        factory = ProductViewModelFactory(productRepository)
    )
    val customerViewModel: CustomerViewModel = viewModel(
        factory = CustomerViewModelFactory(customerRepository)
    )
    val supplierViewModel: SupplierViewModel = viewModel(
        factory = SupplierViewModelFactory(supplierRepository)
    )
    val reportViewModel: ReportViewModel = viewModel(
        factory = ReportViewModelFactory(reportRepository)
    )
    val backupRestoreManager = remember {
        BackupRestoreManager(database, userPreferencesRepository)
    }
    val backupViewModel: BackupViewModel = viewModel(
        factory = BackupViewModelFactory(backupRestoreManager, userPreferencesRepository)
    )

    // ==========================================
    // 1. LICENSE & INITIAL MIGRATION CHECK
    // ==========================================
    if (licenseStatus == LicenseStatus.CHECKING || isCheckingExistingUser) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = AppColors.GreenPrimary)
        }
        return
    }

    if (licenseStatus != LicenseStatus.ACTIVE && !isDebugBypassed) {
        LicenseGateScreen(
            licenseManager = licenseManager,
            licenseStatus = licenseStatus,
            onBypassForDemo = { isDebugBypassed = true }
        )
        return
    }

    // ==========================================
    // 2. FIRST INSTALL / SETUP FLOW
    // ==========================================
    if (!userSettings.isSetupCompleted) {
        if (!showFirstSetupScreen) {
            WelcomeScreen(
                onStartSetup = { showFirstSetupScreen = true }
            )
        } else {
            FirstSetupScreen(
                onCompleteSetup = { shopName, ownerName, phone, address ->
                    scope.launch {
                        userPreferencesRepository.saveShopProfile(shopName, ownerName, phone, address)
                    }
                }
            )
        }
        return
    }

    // ==========================================
    // 3. SECURITY / PIN LOCK GATE
    // ==========================================
    if (userSettings.pinEnabled && userSettings.hasPinSet && !isPinUnlocked) {
        PinLockScreen(
            userPreferencesRepository = userPreferencesRepository,
            onUnlockSuccess = { isPinUnlocked = true }
        )
        return
    }

    // ==========================================
    // 4. MAIN APP SCAFFOLD
    // ==========================================
    fun navigateToAddProduct(fromScreen: AppScreen, productId: Long? = null) {
        selectedProductId = productId
        previousScreen = fromScreen
        screen = AppScreen.ADD_PRODUCT
    }

    Scaffold(
        bottomBar = {
            BottomNav(
                current = screen,
                previousScreen = previousScreen,
                onSelect = {
                    selectedProductId = null
                    screen = it
                }
            )
        },
        containerColor = Color.White
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (screen) {
                AppScreen.HOME -> HomeScreen(
                    viewModel = productViewModel,
                    userSettings = userSettings,
                    onNavigate = { screen = it }
                )
                AppScreen.POS -> PosScreen(
                    viewModel = productViewModel,
                    customerViewModel = customerViewModel,
                    userSettings = userSettings,
                    printerService = printerService,
                    onNavigateToAddProduct = {
                        navigateToAddProduct(fromScreen = AppScreen.POS)
                    }
                )
                AppScreen.PRODUCTS -> ProductsScreen(
                    viewModel = productViewModel,
                    onAddProduct = {
                        navigateToAddProduct(fromScreen = AppScreen.PRODUCTS)
                    },
                    onEditProduct = { productId ->
                        navigateToAddProduct(fromScreen = AppScreen.PRODUCTS, productId = productId)
                    }
                )
                AppScreen.ADD_PRODUCT -> AddProductScreen(
                    viewModel = productViewModel,
                    productIdToEdit = selectedProductId,
                    defaultLowStockLimit = userSettings.defaultLowStockLimit,
                    onBack = {
                        selectedProductId = null
                        screen = previousScreen
                    }
                )
                AppScreen.PURCHASE -> PurchaseScreen(
                    viewModel = productViewModel,
                    supplierViewModel = supplierViewModel,
                    onNavigateToAddProduct = {
                        navigateToAddProduct(fromScreen = AppScreen.PURCHASE)
                    }
                )
                AppScreen.CASH -> CashScreen(viewModel = productViewModel)
                AppScreen.REPORTS -> ReportsScreen(reportViewModel = reportViewModel)
                AppScreen.CUSTOMERS -> CustomersScreen(customerViewModel = customerViewModel)
                AppScreen.SUPPLIERS -> SuppliersScreen(supplierViewModel = supplierViewModel)
                AppScreen.SETTINGS -> SettingsScreen(
                    userPreferencesRepository = userPreferencesRepository,
                    licenseManager = licenseManager,
                    printerService = printerService,
                    backupViewModel = backupViewModel
                )
            }
        }
    }
}

@Composable
private fun BottomNav(
    current: AppScreen,
    previousScreen: AppScreen,
    onSelect: (AppScreen) -> Unit
) {
    NavigationBar(
        modifier = Modifier.navigationBarsPadding(),
        containerColor = Color.White
    ) {
        val items = listOf(
            Triple(AppScreen.HOME, Icons.Default.Storefront, "Beranda"),
            Triple(AppScreen.POS, Icons.Default.PointOfSale, "Jualan"),
            Triple(AppScreen.PRODUCTS, Icons.Default.Inventory2, "Produk"),
            Triple(AppScreen.REPORTS, Icons.Default.Assessment, "Laporan"),
            Triple(AppScreen.SETTINGS, Icons.Default.Settings, "Pengaturan")
        )

        items.forEach { (screen, icon, navLabel) ->
            val isSelected = if (current == AppScreen.ADD_PRODUCT) {
                screen == previousScreen
            } else {
                current == screen
            }

            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelect(screen) },
                icon = { Icon(icon, null) },
                label = {
                    Text(
                        text = navLabel,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            )
        }
    }
}
