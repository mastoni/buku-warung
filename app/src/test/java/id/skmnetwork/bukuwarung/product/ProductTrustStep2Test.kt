package id.skmnetwork.bukuwarung.product

import id.skmnetwork.bukuwarung.data.local.entity.ItemType
import id.skmnetwork.bukuwarung.domain.business.BusinessTaxonomyRegistry
import id.skmnetwork.bukuwarung.domain.business.BusinessType
import id.skmnetwork.bukuwarung.domain.stock.StockAdjustment
import id.skmnetwork.bukuwarung.domain.stock.StockAdjustmentValidation
import id.skmnetwork.bukuwarung.util.formatQuantityValue
import id.skmnetwork.bukuwarung.util.loadingFlag
import id.skmnetwork.bukuwarung.ui.settings.defaultActivitiesFor
import id.skmnetwork.bukuwarung.ui.settings.mergeSecondaryActivities
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Step 2 regression suite - CASHIER TRUST + BUSINESS TYPE FOUNDATION.
 *
 * Covers the findings from PRODUCT-UX-01 that this gate fixes:
 *  - A  editing a product silently rewrote live stock
 *  - B  fractional quantities were truncated with toInt() in the sale presentation
 *  - C  the business type could never be changed after onboarding
 *  - D  there was no stock adjustment / opname path from the UI
 *  - E  purchase save and cart clear were irreversible and unconfirmed
 *  - F  the business type did not seed a sensible default product type
 *  - H  "Rp 0" and "Belum ada produk" were rendered as real data while loading
 *
 * Pure JVM unit tests. No device, no instrumentation, no real server. Where the behaviour lives in a
 * Compose screen or a Room-backed repository (which needs an Android runtime) the contract is asserted
 * at the source level, and the rule itself is covered by a pure unit test.
 */
class ProductTrustStep2Test {

    // =============================================================
    // A. Editing a product must never rewrite live stock
    // =============================================================

    @Test
    fun a01_updateProductHasNoStockParameterAtAll() {
        // The strongest available guarantee without a database: if the repository method cannot accept
        // a stock value, no caller can pass one, and the silent overwrite cannot come back.
        val source = readSource("data/repository/ProductRepository.kt")
        val signature = source.substringAfter("suspend fun updateProductWithCategory(")
            .substringBefore(") = withContext")
        assertFalse(
            "updateProductWithCategory must not accept a stock value, signature was:\n$signature",
            Regex("""\bstock\s*:""").containsMatchIn(signature)
        )
        assertTrue("It must still accept the minimum-stock threshold", signature.contains("minimumStock: Double"))
    }

    @Test
    fun a02_repositoryCarriesExistingStockThroughOnEdit() {
        val source = readSource("data/repository/ProductRepository.kt")
        val updateBlock = source.substringAfter("suspend fun updateProductWithCategory")
            .substringBefore("suspend fun adjustStock")
        assertTrue(
            "Editing a product must copy the existing stock across untouched",
            updateBlock.contains("stock = existingProduct.stock")
        )
        assertFalse(
            "The edit path must no longer write an ADJUSTMENT movement",
            updateBlock.contains("Penyesuaian stok saat update produk")
        )
    }

    @Test
    fun a03_productEditScreenNeverSendsAStockValue() {
        val source = readSource("ui/product/AddProductScreen.kt")
        val editCall = source.substringAfter("viewModel.updateProduct(").substringBefore("} else {")
        assertFalse(
            "The product form must not pass a stock value on edit, call was:\n$editCall",
            editCall.contains("stockStr")
        )
        assertTrue("It must still pass the minimum-stock threshold", editCall.contains("minimumStockStr"))
    }

    @Test
    fun a04_productEditScreenShowsStockReadOnlyAndPointsAtTheAdjustment() {
        val source = readSource("ui/product/AddProductScreen.kt")
        assertTrue(
            "The edit screen must label the value as current stock, not initial stock",
            source.contains("Stok Saat Ini")
        )
        assertTrue(
            "The edit screen must offer the adjustment entry point",
            source.contains("Sesuaikan Stok / Opname")
        )
        assertTrue(
            "The opening-stock field must still exist for NEW products",
            source.contains("label = \"Stok Awal\"")
        )
    }

    // =============================================================
    // D. Stock adjustment / opname rules
    // =============================================================

    @Test
    fun d01_increaseIsAcceptedAsAnAdjustment() {
        val result = StockAdjustment.validate(10.0, 13.0, ItemType.PHYSICAL.name, isStockCount = false)
        val valid = result as StockAdjustmentValidation.Valid
        assertEquals(10.0, valid.previousStock, 0.0)
        assertEquals(13.0, valid.newStock, 0.0)
        assertEquals(3.0, valid.delta, 0.0)
        assertEquals("ADJUSTMENT", valid.suggestedMovementType)
    }

    @Test
    fun d02_decreaseIsAccepted() {
        val result = StockAdjustment.validate(10.0, 7.5, ItemType.PHYSICAL.name, isStockCount = true)
        val valid = result as StockAdjustmentValidation.Valid
        assertEquals(-2.5, valid.delta, 0.0)
        assertEquals("OPNAME", valid.suggestedMovementType)
    }

    @Test
    fun d03_zeroStockIsAValidTarget() {
        val result = StockAdjustment.validate(10.0, 0.0, ItemType.PHYSICAL.name)
        assertTrue(result is StockAdjustmentValidation.Valid)
        assertEquals(-10.0, (result as StockAdjustmentValidation.Valid).delta, 0.0)
    }

    @Test
    fun d04_negativeStockIsRejected() {
        val result = StockAdjustment.validate(10.0, -1.0, ItemType.PHYSICAL.name)
        assertTrue(result is StockAdjustmentValidation.Rejected)
        assertTrue((result as StockAdjustmentValidation.Rejected).reason.contains("tidak boleh kurang dari 0"))
    }

    @Test
    fun d05_unchangedStockIsRejectedWithAClearReason() {
        val result = StockAdjustment.validate(10.0, 10.0, ItemType.PHYSICAL.name)
        assertTrue(result is StockAdjustmentValidation.Rejected)
        assertTrue((result as StockAdjustmentValidation.Rejected).reason.contains("tidak ada yang perlu"))
    }

    @Test
    fun d06_serviceAndDigitalProductsHaveNoStockToAdjust() {
        assertTrue(
            StockAdjustment.validate(0.0, 5.0, ItemType.SERVICE.name)
                is StockAdjustmentValidation.Rejected
        )
        assertTrue(
            StockAdjustment.validate(0.0, 5.0, ItemType.DIGITAL.name)
                is StockAdjustmentValidation.Rejected
        )
    }

    @Test
    fun d07_fuelIsAdjustableBecauseItCarriesStock() {
        val result = StockAdjustment.validate(20.0, 18.5, ItemType.FUEL.name, isStockCount = true)
        assertTrue(result is StockAdjustmentValidation.Valid)
        assertEquals(-1.5, (result as StockAdjustmentValidation.Valid).delta, 0.0)
    }

    @Test
    fun d08_adjustmentWritesAMovementInTheSameTransaction() {
        val source = readSource("data/repository/ProductRepository.kt")
        val block = source.substringAfter("suspend fun adjustStock(").substringBefore("fun getStockMovementHistory")
        assertTrue("An adjustment must record a stock movement", block.contains("stockMovementDao.insertMovement"))
        assertTrue("The movement must carry the difference", block.contains("deltaQuantity = accepted.delta"))
        assertTrue("The product row must be updated too", block.contains("productDao.updateProduct"))
        assertTrue("Both writes must be in one transaction", block.contains("appDatabase.withTransaction"))
    }

    @Test
    fun d09_adjustmentScreenExplainsTheLedgerEntry() {
        val source = readSource("ui/product/StockAdjustmentDialog.kt")
        assertTrue("The merchant must see the current stock", source.contains("Stok saat ini"))
        assertTrue("The merchant must see the difference", source.contains("Selisih:"))
        assertTrue("The merchant must be able to give a reason", source.contains("Alasan / catatan"))
        assertTrue("Both opname and correction must be offered", source.contains("Stok Opname"))
        assertTrue("The irreversible nature must be stated", source.contains("tidak dapat dibatalkan"))
    }

    // =============================================================
    // B. Fractional quantity presentation
    // =============================================================

    @Test
    fun b01_fractionalQuantitiesKeepTheirDecimals() {
        assertEquals("2.5", formatQuantityValue(2.5))
        assertEquals("1.25", formatQuantityValue(1.25))
        assertEquals("0.5", formatQuantityValue(0.5))
        assertEquals("0.25", formatQuantityValue(0.25))
        assertEquals("10.75", formatQuantityValue(10.75))
    }

    @Test
    fun b02_wholeQuantitiesStayWholeWithoutADecimalPoint() {
        assertEquals("2", formatQuantityValue(2.0))
        assertEquals("7", formatQuantityValue(7.0))
        assertEquals("0", formatQuantityValue(0.0))
        assertEquals("100", formatQuantityValue(100.0))
    }

    @Test
    fun b03_largeAndDegenerateValuesAreHandled() {
        assertEquals("1000", formatQuantityValue(1000.0))
        assertEquals("0", formatQuantityValue(Double.NaN))
        assertEquals("0", formatQuantityValue(Double.POSITIVE_INFINITY))
    }

    @Test
    fun b04_noSaleSurfaceTruncatesAQuantityWithToInt() {
        // Every surface a merchant or customer can see must go through the shared formatter.
        val saleSurfaces = listOf(
            "ui/pos/PosScreen.kt",
            "ui/pos/SaleDetailDialog.kt",
            "ui/pos/SaleReturnDialog.kt",
            "ui/purchase/PurchaseScreen.kt",
            "ui/report/ReportsScreen.kt",
            "ui/product/ProductsScreen.kt"
        )
        saleSurfaces.forEach { path ->
            val source = readSource(path)
            val offenders = Regex("""(quantity|qty|Quantity|totalQuantity|alreadyReturned)\s*\.\s*toInt\(\)""")
                .findAll(source)
                .map { it.value }
                .toList()
            assertTrue(
                "$path must not truncate a displayed quantity, found: $offenders",
                offenders.isEmpty()
            )
        }
    }

    @Test
    fun b05_everySaleSurfaceActuallyUsesTheSharedFormatter() {
        listOf(
            "ui/pos/PosScreen.kt",
            "ui/pos/SaleDetailDialog.kt",
            "ui/pos/SaleReturnDialog.kt",
            "ui/purchase/PurchaseScreen.kt",
            "ui/report/ReportsScreen.kt",
            "ui/product/ProductsScreen.kt"
        ).forEach { path ->
            val source = readSource(path)
            assertTrue(
                "$path must use formatQuantityValue",
                source.contains("import id.skmnetwork.bukuwarung.util.formatQuantityValue")
            )
            assertTrue(
                "$path must actually call formatQuantityValue",
                source.contains("formatQuantityValue(")
            )
        }
    }

    @Test
    fun b06_receiptMapperKeepsTheCanonicalMoneyRules() {
        // Presentation changed, business calculation must not: money is still computed on the real
        // fraction, so a 2.5 kg sale still charges for 2.5 kg.
        val source = readSource("domain/money/MoneyCalculator.kt")
        assertTrue(source.contains("class MoneyCalculator") || source.contains("object MoneyCalculator"))
    }

    // =============================================================
    // C. Changing the business type after onboarding
    // =============================================================

    @Test
    fun c01_settingsOffersAWayToChangeTheBusinessType() {
        val source = readSource("ui/settings/SettingsScreen.kt")
        assertTrue("Settings must offer the change action", source.contains("Ubah Jenis Usaha"))
        assertTrue("The dialog must be rendered", source.contains("BusinessTypeDialog("))
        assertTrue("The preference must be written", source.contains("updateBusinessProfile("))
    }

    @Test
    fun c02_theChangeRequiresAnExplicitAcknowledgement() {
        val source = readSource("ui/settings/BusinessTypeDialog.kt")
        assertTrue("The consequence must be spelled out", source.contains("TIDAK berubah"))
        assertTrue("The merchant must acknowledge it", source.contains("Checkbox("))
        assertTrue("Saving must be gated on the acknowledgement", source.contains("hasChanged && acknowledged"))
        assertTrue("The current type must be shown", source.contains("Jenis usaha saat ini"))
    }

    @Test
    fun c03_theChangeNeverDeletesOrRewritesBusinessData() {
        val dialog = readSource("ui/settings/BusinessTypeDialog.kt")
        listOf("deleteProduct", "deleteCustomer", "deleteSale", "deletePurchase", "DROP TABLE", "clearLicenseEntitlement")
            .forEach { forbidden ->
                assertFalse(
                    "The business type dialog must not contain '$forbidden'",
                    dialog.contains(forbidden)
                )
            }
    }

    @Test
    fun c04_existingSecondaryActivitiesAreNeverRemoved() {
        val merged = mergeSecondaryActivities(
            current = setOf("ACTIVITY_GOODS_SELLING", "ACTIVITY_CUSTOMER_DEBT"),
            seededForNewType = setOf("ACTIVITY_SERVICE_WORK")
        )
        assertTrue("ACTIVITY_CUSTOMER_DEBT must survive", merged.contains("ACTIVITY_CUSTOMER_DEBT"))
        assertTrue("ACTIVITY_GOODS_SELLING must survive", merged.contains("ACTIVITY_GOODS_SELLING"))
        assertTrue("The new type's default must be added", merged.contains("ACTIVITY_SERVICE_WORK"))
        assertEquals(3, merged.size)
    }

    @Test
    fun c05_changingTheTypeProducesANewResolvedProfileImmediately() {
        // The new context has to be visible without reinstalling or restoring a backup.
        val before = BusinessTaxonomyRegistry.resolve("WARUNG_SEMBAKO", setOf("ACTIVITY_GOODS_SELLING"))
        val after = BusinessTaxonomyRegistry.resolve("WARUNG_MAKAN", setOf("ACTIVITY_FOOD_BEVERAGE"))
        assertTrue("WARUNG_SEMBAKO", before.businessType.id == "WARUNG_SEMBAKO")
        assertTrue("WARUNG_MAKAN", after.businessType.id == "WARUNG_MAKAN")
        assertTrue("The resolved type must actually change", before.businessType != after.businessType)
        assertTrue(
            "The POS vocabulary must follow the type",
            before.terminology.productLabel != after.terminology.productLabel
        )
    }

    @Test
    fun c06_aNewTypeSeedsItsOwnDefaultActivities() {
        val warungMakan = defaultActivitiesFor(BusinessType.WARUNG_MAKAN)
        assertTrue(
            "A warung makan seeds its own operational activities, got $warungMakan",
            warungMakan.contains("ACTIVITY_WHOLESALE_PURCHASE")
        )
        assertTrue(
            "A warung seeds goods selling",
            defaultActivitiesFor(BusinessType.WARUNG_SEMBAKO).contains("ACTIVITY_GOODS_SELLING")
        )
        assertTrue(
            "The seeded set must actually differ between types",
            defaultActivitiesFor(BusinessType.WARUNG_SEMBAKO) != warungMakan
        )
    }

    // =============================================================
    // F. Business type seeds a sensible default product type
    // =============================================================

    @Test
    fun f01_goodsAndFoodBusinessesDefaultToPhysicalGoods() {
        listOf("WARUNG_SEMBAKO", "MINIMARKET_RETAIL", "TOKO_PAKAIAN", "TOKO_ELEKTRONIK", "TOKO_BANGUNAN",
            "APOTEK_OBAT", "WARUNG_MAKAN", "KEDAI_KAFE", "BAKERY_KUE", "INDUSTRI_RUMAHAN")
            .forEach { type ->
                val profile = BusinessTaxonomyRegistry.resolve(type, emptySet())
                assertEquals(
                    "$type should start on PHYSICAL",
                    ItemType.PHYSICAL,
                    profile.suggestedItemType
                )
            }
    }

    @Test
    fun f02_serviceBusinessesDefaultToService() {
        listOf("BENGKEL_MOTOR_MOBIL", "CUCI_KENDARAAN", "SERVICE_ELEKTRONIK_HP", "LAUNDRY", "SALON_BARBERSHOP",
            "JAHIT_TAILOR", "PERCETAKAN_FOTOCOPY", "JASA_TEKNISI")
            .forEach { type ->
                val profile = BusinessTaxonomyRegistry.resolve(type, emptySet())
                assertEquals(
                    "$type should start on SERVICE",
                    ItemType.SERVICE,
                    profile.suggestedItemType
                )
            }
    }

    @Test
    fun f03_aPulsaShopDefaultsToDigital() {
        val profile = BusinessTaxonomyRegistry.resolve("KONTER_PULSA_HP", emptySet())
        assertEquals(ItemType.DIGITAL, profile.suggestedItemType)
    }

    @Test
    fun f04_theSuggestionIsOnlyADefaultAndNotAConstraint() {
        // A warung must still be able to sell pulsa: the chips are not filtered by business type.
        val source = readSource("ui/product/AddProductScreen.kt")
        assertTrue("All four product types must remain selectable", source.contains("ItemType.DIGITAL to \"Produk Digital\""))
        assertTrue("Fuel must remain selectable", source.contains("ItemType.FUEL to \"Bahan Bakar\""))
        assertTrue("Service must remain selectable", source.contains("ItemType.SERVICE to \"Jasa / Layanan\""))
        assertTrue(
            "The merchant must be told the choice is only a starting point",
            source.contains("Anda tetap dapat menjual jenis produk lainnya")
        )
    }

    @Test
    fun f05_anExistingProductKeepsTheTypeItWasCreatedWith() {
        val source = readSource("ui/product/AddProductScreen.kt")
        assertTrue(
            "The default must only be applied while adding",
            source.contains("if (productIdToEdit == null)")
        )
    }

    @Test
    fun f06_theDefaultActuallyReachesTheForm() {
        val source = readSource("ui/product/AddProductScreen.kt")
        assertTrue(source.contains("resolvedProfile.suggestedItemType"))
        assertTrue(source.contains("selectedItemType = suggestedItemType"))
    }

    // =============================================================
    // E. Confirmation for irreversible actions
    // =============================================================

    @Test
    fun e01_purchaseSaveIsConfirmedAndExplainsTheConsequence() {
        val source = readSource("ui/purchase/PurchaseScreen.kt")
        assertTrue("Saving a purchase must be confirmed", source.contains("showPurchaseConfirmDialog"))
        assertTrue(
            "The dialog must say stock is increased",
            source.contains("akan menambah stok produk secara permanen")
        )
        assertTrue(
            "A credit purchase must say a payable is recorded",
            source.contains("mencatat")
        )
        assertTrue("The confirmation must offer Batal", source.contains("\"Batal\""))
        assertTrue("The irreversible nature must be stated", source.contains("tidak dapat dibatalkan"))
    }

    @Test
    fun e02_cartClearIsConfirmedOnEveryLayout() {
        val source = readSource("ui/pos/PosScreen.kt")
        assertTrue("Clearing the cart must be confirmed", source.contains("showClearCartDialog"))
        assertTrue(
            "The dialog must explain what is lost",
            source.contains("Kosongkan semua barang dari keranjang?")
        )
        // Step 2 requirement: it must also be reachable on a phone, which previously had no way to
        // empty the cart at all.
        assertTrue(
            "The phone layout must offer the action too",
            source.contains("Kosongkan Keranjang")
        )
    }

    @Test
    fun e03_smallActionsAreNotConfirmed() {
        // The gate explicitly warns against confirming every small action. Changing a cart quantity is
        // a two-tap, trivially reversible action and must not raise a dialog.
        val posSource = readSource("ui/pos/PosScreen.kt")
        val increment = posSource.substringAfter("onIncrement = {").substringBefore("onDecrement = {")
        val decrement = posSource.substringAfter("onDecrement = {").substringBefore("onDestinationChanged = {")
        assertFalse("Incrementing a quantity must not open a dialog", increment.contains("AlertDialog"))
        assertFalse("Decrementing a quantity must not open a dialog", decrement.contains("AlertDialog"))
    }

    // =============================================================
    // H. Loading state
    // =============================================================

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun h01_loadingFlagIsTrueUntilTheFirstValueArrives() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val flag = flow { gate.await(); emit(listOf(1)) }.loadingFlag(TestScope(testScheduler))

        advanceUntilIdle()
        assertTrue("Must read as loading before any value arrives", flag.value)

        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse("Must stop reading as loading once a real value arrives", flag.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun h02_anEmptyResultIsARealResultNotAnEndlessSpinner() = runTest {
        val flag = flowOf(emptyList<Int>()).loadingFlag(TestScope(testScheduler))
        advanceUntilIdle()
        assertFalse("An empty table is real data and must end the loading state", flag.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun h03_aFailedReadAlsoEndsTheLoadingState() = runTest {
        val flag = flow<Int> { throw java.io.IOException("corrupt") }
            .loadingFlag(TestScope(testScheduler))
        advanceUntilIdle()
        assertFalse("A failed read must not leave a spinner forever", flag.value)
    }

    @Test
    fun h04_everyDataBackedScreenWaitsForTheFirstValue() {
        listOf(
            "ui/home/HomeScreen.kt",
            "ui/product/ProductsScreen.kt",
            "ui/customer/CustomersScreen.kt",
            "ui/cash/CashScreen.kt",
            "ui/report/ReportsScreen.kt"
        ).forEach { path ->
            val source = readSource(path)
            assertTrue("$path must gate on the loading flag", source.contains("if (isLoading) {"))
            assertTrue("$path must use the shared loading affordance", source.contains("AppLoadingState()"))
        }
    }

    @Test
    fun h05_theLoadingAffordanceIsSharedAndSimple() {
        val source = readSource("ui/components/AppComponents.kt")
        assertTrue("There must be exactly one shared loading composable", source.contains("fun AppLoadingState("))
    }

    // =============================================================
    // helpers
    // =============================================================

    private fun readSource(relativePath: String): String {
        val file = File("src/main/java/id/skmnetwork/bukuwarung/$relativePath")
        assertTrue("Source file must exist: ${file.path}", file.exists())
        return file.readText()
    }
}
