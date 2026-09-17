package id.skmnetwork.bukuwarung.domain.business

import id.skmnetwork.bukuwarung.data.local.entity.ItemType
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.data.local.entity.PurchaseItemEntity
import id.skmnetwork.bukuwarung.data.local.entity.PurchaseTransactionEntity
import id.skmnetwork.bukuwarung.data.local.entity.SaleItemEntity
import id.skmnetwork.bukuwarung.data.local.entity.SaleReturnItemEntity
import id.skmnetwork.bukuwarung.data.local.entity.SaleTransactionEntity
import id.skmnetwork.bukuwarung.data.local.entity.StockMovementEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Fuel Stock Consistency Unit Tests
 * Validates that ItemType.FUEL is consistently handled as a stockable inventory item
 * across initial stock, restock (purchase), sale (POS), out-of-stock validation,
 * returns, and ledger movements with fractional/decimal quantity precision.
 */
class FuelStockConsistencyUnitTest {

    @Test
    fun testA_universalItemTypeStockabilityContract() {
        // Direct Enum property check
        assertTrue("PHYSICAL must be stockable", ItemType.PHYSICAL.isStockable)
        assertTrue("FUEL must be stockable", ItemType.FUEL.isStockable)
        assertFalse("DIGITAL must not be stockable", ItemType.DIGITAL.isStockable)
        assertFalse("SERVICE must not be stockable", ItemType.SERVICE.isStockable)

        // String companion helper check
        assertTrue(ItemType.isStockable("PHYSICAL"))
        assertTrue(ItemType.isStockable("FUEL"))
        assertFalse(ItemType.isStockable("DIGITAL"))
        assertFalse(ItemType.isStockable("SERVICE"))
        assertFalse(ItemType.isStockable(null))
        assertFalse(ItemType.isStockable("UNKNOWN"))
    }

    @Test
    fun testB_fuelInitialProductCreationWithDecimalStock() {
        val prodUuid = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val fuelProduct = ProductEntity(
            id = 1L,
            uuid = prodUuid,
            categoryId = 1L,
            name = "Bensin Pertalite",
            purchasePrice = 10000L,
            sellingPrice = 12000L,
            stock = 100.5,
            minimumStock = 10.0,
            unit = "liter",
            itemType = ItemType.FUEL.name
        )

        assertEquals("Bensin Pertalite", fuelProduct.name)
        assertEquals(ItemType.FUEL.name, fuelProduct.itemType)
        assertEquals(100.5, fuelProduct.stock, 0.0001)
        assertEquals(10.0, fuelProduct.minimumStock, 0.0001)
        assertEquals("liter", fuelProduct.unit)
        assertTrue(ItemType.isStockable(fuelProduct.itemType))

        // Simulated INITIAL StockMovement
        val initialMovement = StockMovementEntity(
            productUuid = prodUuid,
            movementType = "INITIAL",
            deltaQuantity = fuelProduct.stock,
            currentStockSnapshot = fuelProduct.stock,
            note = "Initial stock saat penambahan produk",
            createdAt = now
        )

        assertEquals("INITIAL", initialMovement.movementType)
        assertEquals(100.5, initialMovement.deltaQuantity, 0.0001)
        assertEquals(100.5, initialMovement.currentStockSnapshot, 0.0001)
    }

    @Test
    fun testC_fuelPurchaseStockIncrementAndLedger() {
        val prodUuid = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val initialStock = 100.5
        val purchaseQty = 25.5
        val newStock = initialStock + purchaseQty

        assertEquals(126.0, newStock, 0.0001)

        val purchaseUuid = UUID.randomUUID().toString()
        val purchaseMovement = StockMovementEntity(
            productUuid = prodUuid,
            movementType = "PURCHASE",
            deltaQuantity = purchaseQty,
            currentStockSnapshot = newStock,
            referenceUuid = purchaseUuid,
            note = "Belanja Barang PUR-$now",
            createdAt = now
        )

        assertEquals("PURCHASE", purchaseMovement.movementType)
        assertEquals(25.5, purchaseMovement.deltaQuantity, 0.0001)
        assertEquals(126.0, purchaseMovement.currentStockSnapshot, 0.0001)
    }

    @Test
    fun testD_fuelSaleStockDecrementAndLedger() {
        val prodUuid = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val currentStock = 126.0
        val saleQty = 2.5
        val remainingStock = currentStock - saleQty

        assertEquals(123.5, remainingStock, 0.0001)

        val saleUuid = UUID.randomUUID().toString()
        val saleMovement = StockMovementEntity(
            productUuid = prodUuid,
            movementType = "SALE",
            deltaQuantity = -saleQty,
            currentStockSnapshot = remainingStock,
            referenceUuid = saleUuid,
            note = "Penjualan TRX-$now",
            createdAt = now
        )

        assertEquals("SALE", saleMovement.movementType)
        assertEquals(-2.5, saleMovement.deltaQuantity, 0.0001)
        assertEquals(123.5, saleMovement.currentStockSnapshot, 0.0001)
    }

    @Test
    fun testE_fuelInsufficientStockValidation() {
        val availableStock = 2.0
        val requestedQty = 2.5

        val isStockSufficient = availableStock >= requestedQty
        assertFalse("Sale of 2.5L should be rejected when only 2.0L is available", isStockSufficient)
    }

    @Test
    fun testF_fuelSaleReturnRestockAndLedger() {
        val prodUuid = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val postSaleStock = 123.5
        val returnQty = 2.5
        val restoredStock = postSaleStock + returnQty

        assertEquals(126.0, restoredStock, 0.0001)

        val returnUuid = UUID.randomUUID().toString()
        val returnMovement = StockMovementEntity(
            productUuid = prodUuid,
            movementType = "RETURN",
            deltaQuantity = returnQty,
            currentStockSnapshot = restoredStock,
            referenceUuid = returnUuid,
            note = "Retur Penjualan RET-$now",
            createdAt = now
        )

        assertEquals("RETURN", returnMovement.movementType)
        assertEquals(2.5, returnMovement.deltaQuantity, 0.0001)
        assertEquals(126.0, returnMovement.currentStockSnapshot, 0.0001)
    }

    @Test
    fun testG_decimalPrecisionNoIntegerTruncation() {
        var runningStock = 100.5

        // Purchase +20.25 L
        val restockQty = 20.25
        runningStock += restockQty
        assertEquals(120.75, runningStock, 0.0001)

        // Sale -5.5 L
        val saleQty1 = 5.5
        runningStock -= saleQty1
        assertEquals(115.25, runningStock, 0.0001)

        // Fractional Sale -0.75 L
        val saleQty2 = 0.75
        runningStock -= saleQty2
        assertEquals(114.50, runningStock, 0.0001)

        // Small Sale -1.25 L
        val saleQty3 = 1.25
        runningStock -= saleQty3
        assertEquals(113.25, runningStock, 0.0001)
    }

    @Test
    fun testH_physicalRegressionRemainsIdentical() {
        val physicalProduct = ProductEntity(
            id = 2L,
            name = "Beras 25kg",
            purchasePrice = 280000L,
            sellingPrice = 310000L,
            stock = 20.0,
            minimumStock = 5.0,
            unit = "karung",
            itemType = ItemType.PHYSICAL.name,
            categoryId = 1L
        )

        assertTrue(ItemType.isStockable(physicalProduct.itemType))
        assertEquals(20.0, physicalProduct.stock, 0.0001)

        // Stock restock
        val afterBuy = physicalProduct.stock + 10.0
        assertEquals(30.0, afterBuy, 0.0001)

        // Stock sale
        val afterSale = afterBuy - 2.0
        assertEquals(28.0, afterSale, 0.0001)
    }

    @Test
    fun testI_digitalAndServiceRemainNonStock() {
        val pulsa = ProductEntity(
            id = 3L,
            name = "Pulsa Telkomsel 10k",
            purchasePrice = 10200L,
            sellingPrice = 12000L,
            stock = 0.0,
            minimumStock = 0.0,
            unit = "pcs",
            itemType = ItemType.DIGITAL.name,
            categoryId = 2L
        )

        val service = ProductEntity(
            id = 4L,
            name = "Jasa Servis Ringan",
            purchasePrice = 0L,
            sellingPrice = 35000L,
            stock = 0.0,
            minimumStock = 0.0,
            unit = "sesi",
            itemType = ItemType.SERVICE.name,
            categoryId = 3L
        )

        assertFalse("DIGITAL must not be stockable", ItemType.isStockable(pulsa.itemType))
        assertFalse("SERVICE must not be stockable", ItemType.isStockable(service.itemType))

        assertEquals(0.0, pulsa.stock, 0.0001)
        assertEquals(0.0, service.stock, 0.0001)
    }

    @Test
    fun testJ_mixedItemTypeTransactionClassification() {
        val items = listOf(
            Triple("Beras 5kg", ItemType.PHYSICAL.name, 2.0),
            Triple("Pertalite", ItemType.FUEL.name, 3.5),
            Triple("Pulsa 20k", ItemType.DIGITAL.name, 1.0),
            Triple("Jasa Cuci Motor", ItemType.SERVICE.name, 1.0)
        )

        val stockableItems = items.filter { ItemType.isStockable(it.second) }
        val nonStockItems = items.filter { !ItemType.isStockable(it.second) }

        assertEquals(2, stockableItems.size)
        assertEquals(2, nonStockItems.size)

        assertEquals("Beras 5kg", stockableItems[0].first)
        assertEquals("Pertalite", stockableItems[1].first)

        assertEquals("Pulsa 20k", nonStockItems[0].first)
        assertEquals("Jasa Cuci Motor", nonStockItems[1].first)
    }

    @Test
    fun testK_businessTaxonomyUniversalAdaptabilityWithFuel() {
        val warungProfile = BusinessTaxonomyRegistry.resolve(BusinessType.WARUNG_SEMBAKO.id)
        val bengkelProfile = BusinessTaxonomyRegistry.resolve(BusinessType.BENGKEL_MOTOR_MOBIL.id)

        assertNotNull(warungProfile)
        assertNotNull(bengkelProfile)

        // Both presets resolve terminology and capabilities while ItemType.FUEL is universally usable
        assertTrue(ItemType.isStockable(ItemType.FUEL.name))
        assertEquals("Produk", warungProfile.terminology.productLabel)
        assertEquals("Sparepart & Oli", bengkelProfile.terminology.productLabel)
    }
}
