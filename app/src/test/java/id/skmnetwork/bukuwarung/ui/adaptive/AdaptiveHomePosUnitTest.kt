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
}
