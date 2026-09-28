package id.skmnetwork.bukuwarung.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.preferences.UserPreferencesRepository
import id.skmnetwork.bukuwarung.data.preferences.UserSettings
import id.skmnetwork.bukuwarung.data.repository.CustomerRepository
import id.skmnetwork.bukuwarung.data.repository.DigitalTransactionRepository
import id.skmnetwork.bukuwarung.data.repository.ProductRepository
import id.skmnetwork.bukuwarung.data.repository.ReportRepository
import id.skmnetwork.bukuwarung.data.repository.SaleRepository
import id.skmnetwork.bukuwarung.data.repository.SupplierRepository
import id.skmnetwork.bukuwarung.domain.checkout.CheckoutOrchestrator
import id.skmnetwork.bukuwarung.license.ForegroundLicenseValidationObserver
import id.skmnetwork.bukuwarung.license.LicenseManager
import id.skmnetwork.bukuwarung.license.LicensePhase
import id.skmnetwork.bukuwarung.ui.cash.CashScreen
import id.skmnetwork.bukuwarung.ui.catalog.CatalogScreen
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
import id.skmnetwork.bukuwarung.ui.theme.AppWindowSize
import id.skmnetwork.bukuwarung.ui.theme.rememberAppWindowSize
import id.skmnetwork.bukuwarung.ui.welcome.FirstSetupScreen
import id.skmnetwork.bukuwarung.ui.welcome.WelcomeScreen
import kotlinx.coroutines.launch

@Composable
fun BukuWarungApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val userPreferencesRepository = remember { UserPreferencesRepository(context) }
    val licenseManager = remember { LicenseManager(userPreferencesRepository = userPreferencesRepository, context = context) }

    // P0: the initial value is null, NOT UserSettings(). "DataStore has not emitted yet" and
    // "the business identity has not been created yet" are different states: a blank business id
    // used to be resolved to LEGACY_BUSINESS on the very first frame, which is what pinned every
    // repository to a tenant that can never contain data. See TenantGate.
    val loadedUserSettings: UserSettings? by userPreferencesRepository.userSettings
        .collectAsStateWithLifecycle(initialValue = null)
    val licenseState by licenseManager.licenseState.collectAsStateWithLifecycle()

    // Gate H.5.1 section 2 - cold start validation.
    // Keyed on the manager instance, so a recomposition or a configuration change (which retains the
    // composition) never re-runs it; a genuine process recreation builds a new manager and does.
    // LicenseManager additionally caps this at one attempt per process.
    LaunchedEffect(licenseManager) {
        licenseManager.triggerColdStartValidation()
    }

    // Gate H.5.1 section 3 - foreground validation attached to the PROCESS lifecycle, so Activity
    // recreation, configuration change and multi-Activity navigation never look like a new
    // foreground transition. All de-duplication and rate limiting live in LicenseManager.
    //
    // Gate H.5.3 (H.5.2-P2-3): the observer is acquired defensively. ProcessLifecycleOwner is
    // initialised by androidx.startup, which Android exposes a per-app kill switch for in Developer
    // Options. If it is unavailable, this block is simply skipped: the app does not crash, no fake
    // ACTIVE state is produced, and the cold-start validation above still runs normally. The
    // dependency and the global AndroidX Startup configuration are both left untouched.
    DisposableEffect(licenseManager) {
        var attachedLifecycle: Lifecycle? = null
        var attachedObserver: ForegroundLicenseValidationObserver? = null
        try {
            val processLifecycle = ProcessLifecycleOwner.get().lifecycle
            val created = ForegroundLicenseValidationObserver(licenseManager, scope)
            processLifecycle.addObserver(created)
            attachedLifecycle = processLifecycle
            attachedObserver = created
        } catch (_: Exception) {
            // Foreground validation is unavailable on this process. Nothing else depends on it.
        }
        onDispose {
            val lifecycleRef = attachedLifecycle
            val observerRef = attachedObserver
            if (lifecycleRef != null && observerRef != null) {
                try {
                    lifecycleRef.removeObserver(observerRef)
                } catch (_: Exception) {
                    // Best effort: the process may already be tearing down.
                }
            }
        }
    }

    var isCheckingExistingUser by remember { mutableStateOf(true) }
    var isDebugBypassed by remember { mutableStateOf(false) }

    val database = remember { AppDatabase.getDatabase(context) }

    // P0 - the tenant is NOT decided here any more. No repository, no ViewModel and no tenant query
    // is created in this composable: TenantGate resolves the ACTIVE business id and only then
    // composes ActiveTenantApp, which keys every tenant-scoped object on that id. There is
    // deliberately no `ifBlank { "LEGACY_BUSINESS" }` fallback left; LEGACY_BUSINESS survives only
    // as the migration/reconciliation sentinel inside autoMigrateExistingUserIfNeeded.

    LaunchedEffect(Unit) {
        userPreferencesRepository.autoMigrateExistingUserIfNeeded(database)
        isCheckingExistingUser = false
    }

    // Post-Download Telemetry: Record APP_FIRST_OPEN exactly once per installation.
    // The previous implementation read the flag from a state snapshot and fired the event
    // unconditionally whenever that snapshot said false, so repeated launches and
    // Activity recreation could each emit a row. claimAppFirstOpen() now reads DataStore
    // directly, serialises the read-modify-write, and commits the flag before the event.
    LaunchedEffect(Unit) {
        if (licenseManager.claimAppFirstOpen()) {
            licenseManager.trackMarketingEvent(
                eventType = "APP_FIRST_OPEN",
                utmSource = "app_license_gate",
                utmMedium = "in_app",
                utmCampaign = "buku_warung_v020"
            )
        }
    }

    // ==========================================
    // 1. LICENSE & INITIAL MIGRATION CHECK
    // ==========================================
    // Gate H.5.1: access is decided by the evaluated runtime state, never by a stored string.
    // STALE_ACTIVE still grants access (inside the grace window) but is surfaced as "perlu koneksi".
    if (licenseState.phase == LicensePhase.CHECKING || isCheckingExistingUser) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = AppColors.GreenPrimary)
        }
        return
    }

    if (!licenseState.grantsAccess && !isDebugBypassed) {
        LicenseGateScreen(
            licenseManager = licenseManager,
            licenseState = licenseState,
            onBypassForDemo = { isDebugBypassed = true }
        )
        return
    }

    // ==========================================
    // 2. ACTIVE TENANT GATE (P0)
    // ==========================================
    // Everything that reads tenant-scoped data lives behind this gate. It hands down the ACTIVE
    // business id and refuses to compose anything at all while that identity is unknown, so the
    // first frame can never build a repository against a placeholder tenant.
    TenantGate(userSettings = loadedUserSettings) { userSettings, activeBusinessId ->
        ActiveTenantApp(
            activeBusinessId = activeBusinessId,
            userSettings = userSettings,
            userPreferencesRepository = userPreferencesRepository,
            licenseManager = licenseManager,
            database = database
        )
    }
}

/**
 * The main app, reachable only with a resolved active tenant.
 *
 * Every tenant-scoped repository is created with `remember(activeBusinessId)`, and every
 * tenant-scoped ViewModel is fetched with a tenant-qualified `viewModel(key = ...)`, so a tenant
 * change rebuilds the repository together with the ViewModel that holds it instead of leaving a
 * stale repository from the previous tenant in place.
 */
@Composable
private fun ActiveTenantApp(
    activeBusinessId: String,
    userSettings: UserSettings,
    userPreferencesRepository: UserPreferencesRepository,
    licenseManager: LicenseManager,
    database: AppDatabase
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showFirstSetupScreen by remember(activeBusinessId) { mutableStateOf(false) }
    var isPinUnlocked by remember(activeBusinessId) { mutableStateOf(false) }
    var screen by remember(activeBusinessId) { mutableStateOf(AppScreen.HOME) }
    var previousScreen by remember(activeBusinessId) { mutableStateOf(AppScreen.PRODUCTS) }
    var selectedProductId by remember(activeBusinessId) { mutableStateOf<Long?>(null) }

    val productRepository = remember(activeBusinessId) { ProductRepository(database, activeBusinessId) }
    val customerRepository = remember(activeBusinessId) { CustomerRepository(database, activeBusinessId) }
    val saleRepository = remember(activeBusinessId) { SaleRepository(database, activeBusinessId) }
    val mockBackendApi = remember { id.skmnetwork.bukuwarung.data.remote.MockBackendApi() }
    val digitalTransactionRepository = remember { DigitalTransactionRepository(database.digitalTransactionDao(), mockBackendApi) }
    val checkoutOrchestrator = remember(activeBusinessId) {
        CheckoutOrchestrator(database, saleRepository, digitalTransactionRepository)
    }
    val supplierRepository = remember(activeBusinessId) { SupplierRepository(database, activeBusinessId) }
    val reportRepository = remember(activeBusinessId) { ReportRepository(database, activeBusinessId) }
    val notificationRepository = remember(activeBusinessId) {
        id.skmnetwork.bukuwarung.notification.NotificationRepository(database, userPreferencesRepository, activeBusinessId)
    }
    val purchaseOrderRepository = remember(activeBusinessId) {
        id.skmnetwork.bukuwarung.data.repository.PurchaseOrderRepository(database, activeBusinessId)
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
        key = "tenant:$activeBusinessId:product",
        factory = ProductViewModelFactory(productRepository, checkoutOrchestrator)
    )
    val customerViewModel: CustomerViewModel = viewModel(
        key = "tenant:$activeBusinessId:customer",
        factory = CustomerViewModelFactory(customerRepository, checkoutOrchestrator)
    )
    val supplierViewModel: SupplierViewModel = viewModel(
        key = "tenant:$activeBusinessId:supplier",
        factory = SupplierViewModelFactory(supplierRepository)
    )
    val reportViewModel: ReportViewModel = viewModel(
        key = "tenant:$activeBusinessId:report",
        factory = ReportViewModelFactory(reportRepository)
    )
    val authCredentialProvider = remember {
        id.skmnetwork.bukuwarung.backup.transport.AndroidGoogleAuthCredentialProvider(context, userPreferencesRepository)
    }
    val googleSheetsTransport = remember {
        id.skmnetwork.bukuwarung.backup.transport.GoogleSheetsApiTransport(authCredentialProvider)
    }
    val backupRestoreManager = remember {
        BackupRestoreManager(
            database = database,
            userPreferencesRepository = userPreferencesRepository,
            transport = googleSheetsTransport
        )
    }
    // Deliberately NOT re-keyed per tenant: the backup manager works on the database and the
    // DataStore, so this ViewModel holds no tenant-scoped state.
    val backupViewModel: BackupViewModel = viewModel(
        factory = BackupViewModelFactory(backupRestoreManager, userPreferencesRepository, authCredentialProvider)
    )
    val purchaseOrderViewModel: id.skmnetwork.bukuwarung.ui.purchase.PurchaseOrderViewModel = viewModel(
        key = "tenant:$activeBusinessId:purchaseOrder",
        factory = id.skmnetwork.bukuwarung.ui.purchase.PurchaseOrderViewModelFactory(purchaseOrderRepository, userPreferencesRepository)
    )
    val notificationViewModel: id.skmnetwork.bukuwarung.notification.NotificationViewModel = viewModel(
        key = "tenant:$activeBusinessId:notification",
        factory = id.skmnetwork.bukuwarung.notification.NotificationViewModelFactory(notificationRepository)
    )
    val unreadNotificationCount by notificationViewModel.unreadCount.collectAsStateWithLifecycle(initialValue = 0)

    // ==========================================
    // 3. FIRST INSTALL / SETUP FLOW
    // ==========================================
    if (!userSettings.isSetupCompleted) {
        if (!showFirstSetupScreen) {
            WelcomeScreen(
                onStartSetup = { showFirstSetupScreen = true }
            )
        } else {
            FirstSetupScreen(
                onCompleteSetup = { shopName, ownerName, phone, address, primaryBusinessType, secondaryActivities ->
                    scope.launch {
                        userPreferencesRepository.saveInitialSetupProfile(
                            shopName = shopName,
                            ownerName = ownerName,
                            phone = phone,
                            address = address,
                            primaryBusinessType = primaryBusinessType,
                            secondaryActivities = secondaryActivities,
                            profileVersion = 1
                        )
                    }
                }
            )
        }
        return
    }

    // ==========================================
    // 4. SECURITY / PIN LOCK GATE
    // ==========================================
    if (userSettings.pinEnabled && userSettings.hasPinSet && !isPinUnlocked) {
        PinLockScreen(
            userPreferencesRepository = userPreferencesRepository,
            onUnlockSuccess = { isPinUnlocked = true }
        )
        return
    }

    // ==========================================
    // 5. MAIN APP SCAFFOLD
    // ==========================================
    fun navigateToAddProduct(fromScreen: AppScreen, productId: Long? = null) {
        selectedProductId = productId
        previousScreen = fromScreen
        screen = AppScreen.ADD_PRODUCT
    }

    val windowSize = rememberAppWindowSize()

    val onNavSelect: (AppScreen) -> Unit = {
        selectedProductId = null
        screen = it
    }

    val content: @Composable (Modifier) -> Unit = { modifier ->
        Box(modifier = modifier) {
            when (screen) {
                AppScreen.HOME -> HomeScreen(
                    viewModel = productViewModel,
                    userSettings = userSettings,
                    unreadNotificationCount = unreadNotificationCount,
                    onNavigate = {
                        if (it == AppScreen.NOTIFICATIONS) {
                            previousScreen = AppScreen.HOME
                        }
                        screen = it
                    }
                )
                AppScreen.POS -> PosScreen(
                    viewModel = productViewModel,
                    customerViewModel = customerViewModel,
                    userSettings = userSettings,
                    printerService = printerService,
                    onNavigateToAddProduct = {
                        navigateToAddProduct(fromScreen = AppScreen.POS)
                    },
                    onNavigateToSettings = {
                        screen = AppScreen.SETTINGS
                    }
                )
                AppScreen.PRODUCTS -> ProductsScreen(
                    viewModel = productViewModel,
                    userSettings = userSettings,
                    onAddProduct = {
                        navigateToAddProduct(fromScreen = AppScreen.PRODUCTS)
                    },
                    onEditProduct = { productId ->
                        navigateToAddProduct(fromScreen = AppScreen.PRODUCTS, productId = productId)
                    },
                    onNavigateToCatalog = {
                        previousScreen = AppScreen.PRODUCTS
                        screen = AppScreen.CATALOG
                    }
                )
                AppScreen.CATALOG -> CatalogScreen(
                    viewModel = productViewModel,
                    userSettings = userSettings,
                    onBack = {
                        screen = previousScreen
                    }
                )
                AppScreen.ADD_PRODUCT -> AddProductScreen(
                    viewModel = productViewModel,
                    userSettings = userSettings,
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
                    poViewModel = purchaseOrderViewModel,
                    printerService = printerService,
                    userSettings = userSettings,
                    onNavigateToAddProduct = {
                        navigateToAddProduct(fromScreen = AppScreen.PURCHASE)
                    }
                )
                AppScreen.CASH -> CashScreen(
                    viewModel = productViewModel,
                    userSettings = userSettings
                )
                AppScreen.REPORTS -> ReportsScreen(
                    reportViewModel = reportViewModel,
                    userPreferencesRepository = userPreferencesRepository,
                    userSettings = userSettings
                )
                AppScreen.CUSTOMERS -> CustomersScreen(
                    customerViewModel = customerViewModel,
                    userSettings = userSettings
                )
                AppScreen.SUPPLIERS -> SuppliersScreen(
                    supplierViewModel = supplierViewModel,
                    userSettings = userSettings
                )
                AppScreen.SETTINGS -> SettingsScreen(
                    userPreferencesRepository = userPreferencesRepository,
                    licenseManager = licenseManager,
                    printerService = printerService,
                    backupViewModel = backupViewModel
                )
                AppScreen.NOTIFICATIONS -> id.skmnetwork.bukuwarung.ui.notification.NotificationCenterScreen(
                    viewModel = notificationViewModel,
                    onBack = {
                        screen = previousScreen
                    },
                    onNavigate = { target ->
                        previousScreen = AppScreen.NOTIFICATIONS
                        screen = target
                    }
                )
            }
        }
    }

    if (windowSize.isCompact) {
        Scaffold(
            bottomBar = {
                BottomNav(
                    current = screen,
                    previousScreen = previousScreen,
                    onSelect = onNavSelect
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            content(Modifier.fillMaxSize().padding(padding))
        }
    } else {
        Row(modifier = Modifier.fillMaxSize()) {
            NavigationRail(
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxHeight()
            ) {
                RailNav(
                    current = screen,
                    previousScreen = previousScreen,
                    onSelect = onNavSelect
                )
            }
            content(Modifier.fillMaxSize())
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
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        val items = listOf(
            Triple(AppScreen.HOME, Icons.Default.Storefront, "Beranda"),
            Triple(AppScreen.POS, Icons.Default.PointOfSale, "Jualan"),
            Triple(AppScreen.PRODUCTS, Icons.Default.Inventory2, "Produk"),
            Triple(AppScreen.REPORTS, Icons.Default.Assessment, "Laporan"),
            Triple(AppScreen.SETTINGS, Icons.Default.Settings, "Pengaturan")
        )

        items.forEach { (screen, icon, navLabel) ->
            val isSelected = if (current == AppScreen.ADD_PRODUCT || current == AppScreen.CATALOG || current == AppScreen.NOTIFICATIONS) {
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
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = AppColors.GreenDark,
                    selectedTextColor = AppColors.GreenDark,
                    unselectedIconColor = AppColors.TextSecondary,
                    unselectedTextColor = AppColors.TextSecondary,
                    indicatorColor = AppColors.GreenLight
                )
            )
        }
    }
}

@Composable
private fun RailNav(
    current: AppScreen,
    previousScreen: AppScreen,
    onSelect: (AppScreen) -> Unit
) {
    val items = listOf(
        Triple(AppScreen.HOME, Icons.Default.Storefront, "Beranda"),
        Triple(AppScreen.POS, Icons.Default.PointOfSale, "Jualan"),
        Triple(AppScreen.PRODUCTS, Icons.Default.Inventory2, "Produk"),
        Triple(AppScreen.REPORTS, Icons.Default.Assessment, "Laporan"),
        Triple(AppScreen.SETTINGS, Icons.Default.Settings, "Pengaturan")
    )

    items.forEach { (screen, icon, navLabel) ->
        val isSelected = if (current == AppScreen.ADD_PRODUCT || current == AppScreen.CATALOG || current == AppScreen.NOTIFICATIONS) {
            screen == previousScreen
        } else {
            current == screen
        }

        NavigationRailItem(
            selected = isSelected,
            onClick = { onSelect(screen) },
            icon = { Icon(icon, null) },
            label = {
                Text(
                    text = navLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false
                )
            },
            colors = NavigationRailItemDefaults.colors(
                selectedIconColor = AppColors.GreenDark,
                selectedTextColor = AppColors.GreenDark,
                unselectedIconColor = AppColors.TextSecondary,
                unselectedTextColor = AppColors.TextSecondary,
                indicatorColor = AppColors.GreenLight
            )
        )
    }
}
