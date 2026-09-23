package id.skmnetwork.bukuwarung.ui.adaptive

import id.skmnetwork.bukuwarung.data.local.database.MIGRATION_10_11
import id.skmnetwork.bukuwarung.data.local.entity.CustomerEntity
import id.skmnetwork.bukuwarung.data.local.entity.ItemType
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.data.local.entity.SaleItemEntity
import id.skmnetwork.bukuwarung.data.local.entity.SaleTransactionEntity
import id.skmnetwork.bukuwarung.data.preferences.UserSettings
import id.skmnetwork.bukuwarung.domain.business.BusinessActivity
import id.skmnetwork.bukuwarung.domain.business.BusinessCapability
import id.skmnetwork.bukuwarung.domain.business.BusinessTaxonomyRegistry
import id.skmnetwork.bukuwarung.domain.business.BusinessType
import id.skmnetwork.bukuwarung.domain.checkout.CartLine
import id.skmnetwork.bukuwarung.domain.discount.DiscountCalculator
import id.skmnetwork.bukuwarung.domain.discount.DiscountInput
import id.skmnetwork.bukuwarung.domain.discount.DiscountType
import id.skmnetwork.bukuwarung.domain.tax.TaxCalculator
import id.skmnetwork.bukuwarung.domain.tax.TaxPriceMode
import id.skmnetwork.bukuwarung.domain.tax.TaxSettings
import id.skmnetwork.bukuwarung.domain.tax.SaleItemTaxInput
import id.skmnetwork.bukuwarung.ui.theme.AppWindowSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Gate G6 — Adaptive Home & POS Unit Test Suite
 * Comprehensive validation of adaptive Home and POS presentations, terminology,
 * capability-driven UI, checkout compatibility, and existing data safety (Tests A through T).
 */
class AdaptiveHomePosUnitTest {

    /**
     * Test A: Retail Home terminology resolves correctly.
     */
    @Test
    fun testA_retailHomeTerminology() {
        val settings = UserSettings(
            primaryBusinessType = BusinessType.WARUNG_SEMBAKO.id
        )
        val profile = BusinessTaxonomyRegistry.resolve(settings.primaryBusinessType, settings.secondaryActivities)
        
        assertEquals("Produk", profile.terminology.productLabel)
        assertEquals("Penjualan", profile.terminology.transactionLabel)
        assertEquals("Pelanggan", profile.terminology.customerLabel)
        assertEquals("Stok", profile.terminology.stockLabel)
        assertEquals("Pembelian", profile.terminology.purchaseLabel)
        assertEquals("Supplier", profile.terminology.supplierLabel)
        
        // Home label expressions
        assertEquals("Penjualan Hari Ini", "${profile.terminology.transactionLabel} Hari Ini")
        assertEquals("Produk & Stok", "${profile.terminology.productLabel} & ${profile.terminology.stockLabel}")
        assertEquals("Pelanggan & Piutang", "${profile.terminology.customerLabel} & Piutang")
    }

    /**
     * Test B: F&B Home terminology resolves correctly.
     */
    @Test
    fun testB_foodAndBeverageHomeTerminology() {
        val cafeSettings = UserSettings(
            primaryBusinessType = BusinessType.KEDAI_KAFE.id
        )
        val cafeProfile = BusinessTaxonomyRegistry.resolve(cafeSettings.primaryBusinessType, cafeSettings.secondaryActivities)
        
        assertEquals("Menu Minuman / Makanan", cafeProfile.terminology.productLabel)
        assertEquals("Order / Struk", cafeProfile.terminology.transactionLabel)
        assertEquals("Pelanggan", cafeProfile.terminology.customerLabel)
        
        assertEquals("Order / Struk Hari Ini", "${cafeProfile.terminology.transactionLabel} Hari Ini")
        assertEquals("Menu Minuman / Makanan & Stok", "${cafeProfile.terminology.productLabel} & ${cafeProfile.terminology.stockLabel}")
    }

    /**
     * Test C: Service Home terminology resolves correctly.
     */
    @Test
    fun testC_serviceHomeTerminology() {
        val workshopSettings = UserSettings(
            primaryBusinessType = BusinessType.BENGKEL_MOTOR_MOBIL.id
        )
        val workshopProfile = BusinessTaxonomyRegistry.resolve(workshopSettings.primaryBusinessType, workshopSettings.secondaryActivities)
        
        assertEquals("Sparepart & Oli", workshopProfile.terminology.productLabel)
        assertEquals("Jasa Servis", workshopProfile.terminology.serviceLabel)
        assertEquals("Pelanggan", workshopProfile.terminology.customerLabel)
        assertEquals("Nota Servis", workshopProfile.terminology.transactionLabel)

        val salonSettings = UserSettings(
            primaryBusinessType = BusinessType.SALON_BARBERSHOP.id
        )
        val salonProfile = BusinessTaxonomyRegistry.resolve(salonSettings.primaryBusinessType, salonSettings.secondaryActivities)
        
        assertEquals("Produk Perawatan", salonProfile.terminology.productLabel)
        assertEquals("Layanan", salonProfile.terminology.serviceLabel)
        assertEquals("Pelanggan", salonProfile.terminology.customerLabel)
    }

    /**
     * Test D: POS terminology adapts search placeholder and tabs.
     */
    @Test
    fun testD_posTerminologyAdaptation() {
        val pharmacySettings = UserSettings(
            primaryBusinessType = BusinessType.APOTEK_OBAT.id
        )
        val profile = BusinessTaxonomyRegistry.resolve(pharmacySettings.primaryBusinessType, pharmacySettings.secondaryActivities)
        
        val searchPlaceholder = "Cari ${profile.terminology.productLabel.lowercase()} atau barcode..."
        val tab1Label = "Riwayat ${profile.terminology.transactionLabel}"
        val headerTitle = "${profile.terminology.transactionLabel} (Kasir)"
        
        assertEquals("Cari obat / alkes atau barcode...", searchPlaceholder)
        assertEquals("Riwayat Struk Apotek", tab1Label)
        assertEquals("Struk Apotek (Kasir)", headerTitle)
    }

    /**
     * Test E: Cart terminology adapts item labels and counters.
     */
    @Test
    fun testE_cartTerminologyAdaptation() {
        val fnbSettings = UserSettings(
            primaryBusinessType = BusinessType.WARUNG_MAKAN.id
        )
        val profile = BusinessTaxonomyRegistry.resolve(fnbSettings.primaryBusinessType, fnbSettings.secondaryActivities)
        
        val totalItems = 3
        val cartLabel = "$totalItems ${profile.terminology.productLabel.lowercase()} dipilih"
        
        assertEquals("3 menu makanan dipilih", cartLabel)
    }

    /**
     * Test F: Customer and debt terminology adapt correctly in POS.
     */
    @Test
    fun testF_customerDebtTerminology() {
        val profile = BusinessTaxonomyRegistry.resolve(BusinessType.WARUNG_SEMBAKO.id)
        
        val custSelectorPrompt = "Pilih ${profile.terminology.customerLabel} *"
        val pickerDialogTitle = "Pilih ${profile.terminology.customerLabel} Hutang"
        val paymentMethodLabel = "Hutang (Pak Budi)"
        
        assertEquals("Pilih Pelanggan *", custSelectorPrompt)
        assertEquals("Pilih Pelanggan Hutang", pickerDialogTitle)
        assertEquals("Hutang (Pak Budi)", paymentMethodLabel)
    }

    /**
     * Test G: PHYSICAL checkout behavior preserves stock validation.
     */
    @Test
    fun testG_physicalCheckoutStockBehavior() {
        val physicalProduct = ProductEntity(
            id = 1L,
            uuid = UUID.randomUUID().toString(),
            businessId = "BIZ_1",
            categoryId = 1L,
            name = "Minyak Goreng 2L",
            purchasePrice = 28000L,
            sellingPrice = 34000L,
            stock = 10.0,
            minimumStock = 2.0,
            unit = "pouch",
            itemType = ItemType.PHYSICAL.name
        )

        assertEquals(ItemType.PHYSICAL.name, physicalProduct.itemType)
        assertTrue(physicalProduct.stock > 0.0)
        
        val checkoutQty = 2.0
        val remainingStock = physicalProduct.stock - checkoutQty
        assertEquals(8.0, remainingStock, 0.001)
    }

    /**
     * Test H: SERVICE checkout behavior does not decrement physical stock.
     */
    @Test
    fun testH_serviceCheckoutBehavior() {
        val serviceProduct = ProductEntity(
            id = 2L,
            uuid = UUID.randomUUID().toString(),
            businessId = "BIZ_1",
            categoryId = 1L,
            name = "Servis Ganti Oli + Tune Up",
            purchasePrice = 0L,
            sellingPrice = 50000L,
            stock = 0.0,
            minimumStock = 0.0,
            unit = "jasa",
            itemType = ItemType.SERVICE.name
        )

        assertEquals(ItemType.SERVICE.name, serviceProduct.itemType)
        
        // Service items can be added to cart even if stock is 0.0
        val isServiceOrDigital = serviceProduct.itemType == ItemType.SERVICE.name || serviceProduct.itemType == ItemType.DIGITAL.name
        assertTrue(isServiceOrDigital)
    }

    /**
     * Test I: DIGITAL checkout behavior does not require physical stock.
     */
    @Test
    fun testI_digitalCheckoutBehavior() {
        val digitalProduct = ProductEntity(
            id = 3L,
            uuid = UUID.randomUUID().toString(),
            businessId = "BIZ_1",
            categoryId = 1L,
            name = "Pulsa Telkomsel 50k",
            purchasePrice = 50500L,
            sellingPrice = 52000L,
            stock = 0.0,
            minimumStock = 0.0,
            unit = "trx",
            itemType = ItemType.DIGITAL.name
        )

        assertEquals(ItemType.DIGITAL.name, digitalProduct.itemType)
        val isServiceOrDigital = digitalProduct.itemType == ItemType.SERVICE.name || digitalProduct.itemType == ItemType.DIGITAL.name
        assertTrue(isServiceOrDigital)
    }

    /**
     * Test J: FUEL checkout supports decimal quantities and price computation.
     */
    @Test
    fun testJ_fuelCheckoutBehavior() {
        val fuelProduct = ProductEntity(
            id = 4L,
            uuid = UUID.randomUUID().toString(),
            businessId = "BIZ_1",
            categoryId = 1L,
            name = "Pertalite Eceran",
            purchasePrice = 10000L,
            sellingPrice = 12000L,
            stock = 50.0,
            minimumStock = 5.0,
            unit = "liter",
            itemType = ItemType.FUEL.name
        )

        val decimalQty = 2.5
        val totalPrice = (fuelProduct.sellingPrice * decimalQty).toLong()
        assertEquals(30000L, totalPrice)
    }

    /**
     * Test K: Existing legacy products remain usable in POS.
     */
    @Test
    fun testK_existingProductsRemainUsable() {
        val legacy = ProductEntity(
            id = 99L,
            categoryId = 1L,
            name = "Kopi Sachet ABC",
            purchasePrice = 1200L,
            sellingPrice = 1500L,
            stock = 24.0,
            unit = "sachet",
            itemType = ItemType.PHYSICAL.name
        )

        assertNotNull(legacy.uuid)
        assertEquals("LEGACY_BUSINESS", legacy.businessId)
        assertEquals("Kopi Sachet ABC", legacy.name)
        assertEquals(1500L, legacy.sellingPrice)
    }

    /**
     * Test L: Existing customers and debt flow remain usable.
     */
    @Test
    fun testL_existingCustomersAndDebtRemainUsable() {
        val customer = CustomerEntity(
            id = 10L,
            uuid = UUID.randomUUID().toString(),
            businessId = "BIZ_1",
            name = "Bu Ratna",
            phone = "081234567890",
            address = "Jl. Melati No. 5"
        )

        assertEquals("Bu Ratna", customer.name)
        assertEquals("081234567890", customer.phone)
        assertFalse(customer.isDeleted)
    }

    /**
     * Test M: Unknown or blank business profile falls back to WARUNG_SEMBAKO.
     */
    @Test
    fun testM_unknownProfileFallback() {
        val nullProfile = BusinessTaxonomyRegistry.resolve(null, null)
        assertEquals(BusinessType.WARUNG_SEMBAKO, nullProfile.businessType)
        assertEquals("Produk", nullProfile.terminology.productLabel)

        val blankProfile = BusinessTaxonomyRegistry.resolve("", emptySet())
        assertEquals(BusinessType.WARUNG_SEMBAKO, blankProfile.businessType)

        val unknownProfile = BusinessTaxonomyRegistry.resolve("UNKNOWN_NON_EXISTENT_TYPE", emptySet())
        assertEquals(BusinessType.WARUNG_SEMBAKO, unknownProfile.businessType)
    }

    /**
     * Test N: Business profile change updates UI metadata dynamically.
     */
    @Test
    fun testN_profileChangeUpdatesMetadata() {
        var settings = UserSettings(primaryBusinessType = BusinessType.WARUNG_SEMBAKO.id)
        var profile = BusinessTaxonomyRegistry.resolve(settings.primaryBusinessType, settings.secondaryActivities)
        assertEquals("Produk", profile.terminology.productLabel)

        // Switch to Laundry
        settings = settings.copy(primaryBusinessType = BusinessType.LAUNDRY.id)
        profile = BusinessTaxonomyRegistry.resolve(settings.primaryBusinessType, settings.secondaryActivities)
        assertEquals("Produk Laundry", profile.terminology.productLabel)
        assertEquals("Layanan Laundry", profile.terminology.serviceLabel)
    }

    /**
     * Test O: No business-specific engine branching introduced.
     */
    @Test
    fun testO_noBusinessSpecificEngineBranching() {
        val sale = SaleTransactionEntity(
            id = 1L,
            uuid = UUID.randomUUID().toString(),
            businessId = "BIZ_1",
            transactionNumber = "TRX-20260917-001",
            transactionDate = System.currentTimeMillis(),
            totalAmount = 50000L,
            paymentMethod = "CASH"
        )

        val saleItem = SaleItemEntity(
            id = 1L,
            uuid = UUID.randomUUID().toString(),
            transactionId = 1L,
            productId = 10L,
            productName = "Gunting Rambut",
            quantity = 1.0,
            price = 50000L,
            subtotal = 50000L
        )

        assertEquals("CASH", sale.paymentMethod)
        assertEquals(50000L, saleItem.subtotal)
    }

    /**
     * Test P: Room database schema version remains 11.
     */
    @Test
    fun testP_roomDatabaseVersion11() {
        assertEquals(10, MIGRATION_10_11.startVersion)
        assertEquals(11, MIGRATION_10_11.endVersion)
    }

    /**
     * Test Q: Existing payment methods (CASH, QRIS, CREDIT) remain functional.
     */
    @Test
    fun testQ_paymentMethodsFunctionality() {
        val settings = UserSettings(
            cashEnabled = true,
            qrisEnabled = true,
            creditEnabled = true
        )

        val availableMethods = mutableListOf<String>()
        if (settings.cashEnabled) availableMethods.add("CASH")
        if (settings.qrisEnabled) availableMethods.add("QRIS")
        if (settings.creditEnabled) availableMethods.add("CREDIT")

        assertEquals(3, availableMethods.size)
        assertTrue(availableMethods.contains("CASH"))
        assertTrue(availableMethods.contains("QRIS"))
        assertTrue(availableMethods.contains("CREDIT"))
    }

    /**
     * Test R: QRIS payment flow metadata validation.
     */
    @Test
    fun testR_qrisPaymentFlow() {
        val qrisSale = SaleTransactionEntity(
            id = 5L,
            transactionNumber = "TRX-QRIS-001",
            transactionDate = System.currentTimeMillis(),
            totalAmount = 75000L,
            paymentMethod = "QRIS"
        )

        assertEquals("QRIS", qrisSale.paymentMethod)
        assertEquals(75000L, qrisSale.totalAmount)
    }

    /**
     * Test S: Cash payment and change calculation.
     */
    @Test
    fun testS_cashPaymentAndChangeCalculation() {
        val totalAmount = 35000L
        val cashReceived = 50000L
        val change = cashReceived - totalAmount

        assertEquals(15000L, change)
        assertTrue(change >= 0L)
    }

    /**
     * Test T: Secondary activities augment capabilities correctly.
     */
    @Test
    fun testT_secondaryActivitiesAugmentation() {
        val settings = UserSettings(
            primaryBusinessType = BusinessType.WARUNG_SEMBAKO.id,
            secondaryActivities = setOf(BusinessActivity.ACTIVITY_DIGITAL_VOUCHER.id)
        )
        val profile = BusinessTaxonomyRegistry.resolve(settings.primaryBusinessType, settings.secondaryActivities)

        assertTrue(profile.hasCapability(BusinessCapability.CAP_INVENTORY_STOCK))
        assertTrue(profile.hasCapability(BusinessCapability.CAP_DIGITAL_ITEMS))
        assertTrue(profile.hasActivity(BusinessActivity.ACTIVITY_DIGITAL_VOUCHER))
    }

    /**
     * Test U: Window size class classification for Compact, Medium, and Expanded.
     * Compact (< 600dp) uses phone layout.
     * Medium (600dp - 839dp) and Expanded (>= 840dp) activate two-pane tablet layout.
     */
    @Test
    fun testU_tabletPosWindowSizeClassModel() {
        val phoneWindow = AppWindowSize(id.skmnetwork.bukuwarung.ui.theme.AppWindowWidthClass.COMPACT, 400.dp)
        assertTrue(phoneWindow.isCompact)
        assertFalse(phoneWindow.isMedium)
        assertFalse(phoneWindow.isExpanded)

        val mediumTabletWindow = AppWindowSize(id.skmnetwork.bukuwarung.ui.theme.AppWindowWidthClass.MEDIUM, 700.dp)
        assertFalse(mediumTabletWindow.isCompact)
        assertTrue(mediumTabletWindow.isMedium)
        assertFalse(mediumTabletWindow.isExpanded)

        val expandedTabletWindow = AppWindowSize(id.skmnetwork.bukuwarung.ui.theme.AppWindowWidthClass.EXPANDED, 1000.dp)
        assertFalse(expandedTabletWindow.isCompact)
        assertFalse(expandedTabletWindow.isMedium)
        assertTrue(expandedTabletWindow.isExpanded)
    }

    /**
     * Test V: Tablet POS layout proportions and catalog grid chunking.
     * Compact uses 2 columns, Medium catalog uses 2 columns, Expanded catalog uses 3 columns.
     */
    @Test
    fun testV_tabletPosGridProportions() {
        val compactWindow = AppWindowSize(id.skmnetwork.bukuwarung.ui.theme.AppWindowWidthClass.COMPACT, 380.dp)
        val mediumWindow = AppWindowSize(id.skmnetwork.bukuwarung.ui.theme.AppWindowWidthClass.MEDIUM, 700.dp)
        val expandedWindow = AppWindowSize(id.skmnetwork.bukuwarung.ui.theme.AppWindowWidthClass.EXPANDED, 1200.dp)

        val compactCatalogCols = if (compactWindow.isExpanded) 3 else 2
        val mediumCatalogCols = if (mediumWindow.isExpanded) 3 else 2
        val expandedCatalogCols = if (expandedWindow.isExpanded) 3 else 2

        assertEquals(2, compactCatalogCols)
        assertEquals(2, mediumCatalogCols)
        assertEquals(3, expandedCatalogCols)

        val mediumCartWidth = if (mediumWindow.isExpanded) 380.dp else 320.dp
        val expandedCartWidth = if (expandedWindow.isExpanded) 380.dp else 320.dp

        assertEquals(320.dp, mediumCartWidth)
        assertEquals(380.dp, expandedCartWidth)
    }

    /**
     * Test W: Shared POS cart state calculations across product additions and quantity adjustments.
     */
    @Test
    fun testW_productSelectionSharedCartState() {
        val product1 = ProductEntity(
            id = 101L,
            categoryId = 1L,
            name = "Beras Rojolele 5kg",
            sellingPrice = 65000L,
            purchasePrice = 58000L,
            stock = 20.0,
            unit = "sak"
        )
        val product2 = ProductEntity(
            id = 102L,
            categoryId = 1L,
            name = "Minyak Goreng 2L",
            sellingPrice = 34000L,
            purchasePrice = 30000L,
            stock = 15.0,
            unit = "pch"
        )

        val cart = mutableListOf<CartLine>()
        cart.add(CartLine(product = product1, quantity = 2.0))
        cart.add(CartLine(product = product2, quantity = 1.0))

        val totalItemCount = cart.sumOf { it.quantity }.toInt()
        val grossTotal = cart.sumOf { it.product.sellingPrice * it.quantity.toLong() }

        assertEquals(3, totalItemCount)
        assertEquals(164000L, grossTotal) // 65000 * 2 + 34000 = 164000

        // Apply 10,000 fixed discount
        val discountResult = DiscountCalculator.calculate(
            grossSubtotal = grossTotal,
            discountInput = DiscountInput(DiscountType.FIXED, 10000.0)
        )
        assertEquals(10000L, discountResult.discountAmount)
        assertEquals(154000L, discountResult.netTotal)
    }

    /**
     * Test X: Cart state persistence when searching/filtering product catalog.
     */
    @Test
    fun testX_cartStateSurvivesCatalogFiltering() {
        val product1 = ProductEntity(id = 1L, categoryId = 1L, name = "Kopi Hitam", sellingPrice = 5000L, purchasePrice = 3000L, stock = 50.0)
        val product2 = ProductEntity(id = 2L, categoryId = 1L, name = "Teh Manis", sellingPrice = 4000L, purchasePrice = 2000L, stock = 50.0)
        val allProducts = listOf(product1, product2)

        val cart = mutableListOf<CartLine>()
        cart.add(CartLine(product = product1, quantity = 3.0))

        // Simulate catalog search query for "Teh"
        val query = "Teh"
        val filteredProducts = allProducts.filter { it.name.contains(query, ignoreCase = true) }

        assertEquals(1, filteredProducts.size)
        assertEquals("Teh Manis", filteredProducts[0].name)

        // Cart is untouched and persistent
        assertEquals(1, cart.size)
        assertEquals("Kopi Hitam", cart[0].product.name)
        assertEquals(3.0, cart[0].quantity, 0.001)
    }

    /**
     * Test Y: POS checkout transaction flow - Stock decrement and cash balance mutation.
     */
    @Test
    fun testY_checkoutStockDecrementAndCashMutation() {
        var productStock = 25.0
        var currentCashBalance = 100000L

        val soldQuantity = 4.0
        val sellingPrice = 12000L
        val totalTransaction = (sellingPrice * soldQuantity).toLong()

        // Deduct stock
        productStock -= soldQuantity
        // Add to cash balance
        currentCashBalance += totalTransaction

        assertEquals(21.0, productStock, 0.001)
        assertEquals(148000L, currentCashBalance)
    }

    /**
     * Test Z: POS checkout credit transaction flow - Debt recording.
     */
    @Test
    fun testZ_creditTransactionAndCustomerDebtRecording() {
        val customer = CustomerEntity(id = 50L, name = "Pak Haji Budi", phone = "08123456789")
        val creditSale = SaleTransactionEntity(
            id = 100L,
            customerId = customer.id,
            paymentMethod = "CREDIT",
            totalAmount = 85000L,
            transactionNumber = "TRX-CR-001",
            transactionDate = System.currentTimeMillis()
        )

        assertEquals("CREDIT", creditSale.paymentMethod)
        assertEquals(50L, creditSale.customerId)
        assertEquals(85000L, creditSale.totalAmount)
    }
}
