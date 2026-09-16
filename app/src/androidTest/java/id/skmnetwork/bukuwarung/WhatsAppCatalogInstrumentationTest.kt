package id.skmnetwork.bukuwarung

import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.skmnetwork.bukuwarung.catalog.WhatsAppCatalogFormatter
import id.skmnetwork.bukuwarung.catalog.WhatsAppCatalogShareHelper
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.local.entity.CategoryEntity
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.data.preferences.UserSettings
import id.skmnetwork.bukuwarung.data.repository.ProductRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WhatsAppCatalogInstrumentationTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var productRepository: ProductRepository

    private var catSembakoId: Long = 0
    private var catMinumanId: Long = 0

    private val sampleProducts = mutableListOf<ProductEntity>()

    @Before
    fun setup() {
        runBlocking {
            context = ApplicationProvider.getApplicationContext()
            database = Room.inMemoryDatabaseBuilder(
                context,
                AppDatabase::class.java
            ).allowMainThreadQueries().build()

            productRepository = ProductRepository(database)

            catSembakoId = database.categoryDao().insertCategory(CategoryEntity(name = "Sembako"))
            catMinumanId = database.categoryDao().insertCategory(CategoryEntity(name = "Minuman"))

            val p1 = ProductEntity(
                categoryId = catSembakoId,
                name = "Beras Rojolele 5kg",
                purchasePrice = 58000,
                sellingPrice = 65000,
                stock = 15.0,
                unit = "sak"
            )
            val p1Id = database.productDao().insertProduct(p1)
            sampleProducts.add(p1.copy(id = p1Id))

            val p2 = ProductEntity(
                categoryId = catSembakoId,
                name = "Minyak Goreng 1L",
                purchasePrice = 13500,
                sellingPrice = 15000,
                stock = 24.0,
                unit = "pouch"
            )
            val p2Id = database.productDao().insertProduct(p2)
            sampleProducts.add(p2.copy(id = p2Id))

            val p3 = ProductEntity(
                categoryId = catMinumanId,
                name = "Teh Botol 350ml",
                purchasePrice = 3000,
                sellingPrice = 4500,
                stock = 30.0,
                unit = "btl"
            )
            val p3Id = database.productDao().insertProduct(p3)
            sampleProducts.add(p3.copy(id = p3Id))
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun test1_emptyProductCatalog_formatsEmptyMessageGracefully() {
        val result = WhatsAppCatalogFormatter.formatCatalogText(
            shopName = "Warung Berkah",
            products = emptyList()
        )
        assertTrue(result.contains("KATALOG PRODUK"))
        assertTrue(result.contains("Warung Berkah"))
        assertTrue(result.contains("Belum ada produk yang dipilih"))
        assertTrue(result.contains("Dibuat dengan Buku Warung"))
    }

    @Test
    fun test2_singleProductSelection_formatsCorrectly() {
        val singleList = listOf(sampleProducts[0])
        val result = WhatsAppCatalogFormatter.formatCatalogText(
            shopName = "Toko Makmur",
            products = singleList
        )
        assertTrue(result.contains("Toko Makmur"))
        assertTrue(result.contains("• *Beras Rojolele 5kg* — Rp 65.000"))
        assertFalse(result.contains("Minyak Goreng"))
    }

    @Test
    fun test3_multipleProductSelection_formatsBulletPoints() {
        val multipleList = listOf(sampleProducts[0], sampleProducts[1], sampleProducts[2])
        val result = WhatsAppCatalogFormatter.formatCatalogText(
            shopName = "Warung Berkah",
            products = multipleList
        )
        assertTrue(result.contains("• *Beras Rojolele 5kg* — Rp 65.000"))
        assertTrue(result.contains("• *Minyak Goreng 1L* — Rp 15.000"))
        assertTrue(result.contains("• *Teh Botol 350ml* — Rp 4.500"))
    }

    @Test
    fun test4_selectedProductDataCorrect_namePriceStockUnit() {
        val list = listOf(sampleProducts[0], sampleProducts[1])
        val resultWithStock = WhatsAppCatalogFormatter.formatCatalogText(
            shopName = "Warung Bu Siti",
            products = list,
            includeStock = true
        )
        assertTrue(resultWithStock.contains("• *Beras Rojolele 5kg* — Rp 65.000 (Stok: 15 sak)"))
        assertTrue(resultWithStock.contains("• *Minyak Goreng 1L* — Rp 15.000 (Stok: 24 pouch)"))

        val resultWithoutStock = WhatsAppCatalogFormatter.formatCatalogText(
            shopName = "Warung Bu Siti",
            products = list,
            includeStock = false
        )
        assertTrue(resultWithoutStock.contains("• *Beras Rojolele 5kg* — Rp 65.000"))
        assertFalse(resultWithoutStock.contains("(Stok: 15 sak)"))
    }

    @Test
    fun test5_priceFormattingCorrect_withIndonesianRupiah() {
        val prod = sampleProducts[0].copy(sellingPrice = 1250000)
        val result = WhatsAppCatalogFormatter.formatCatalogText(
            shopName = "Warung Maju",
            products = listOf(prod)
        )
        assertTrue(result.contains("Rp 1.250.000"))
    }

    @Test
    fun test6_shopIdentityIncluded_shopNameOwnerPhoneAddress() {
        val userSettings = UserSettings(
            shopName = "Kios Barokah",
            ownerName = "Pak H. Ahmad",
            phone = "081234567890",
            address = "Jl. Raya Pasar No. 45"
        )
        val result = WhatsAppCatalogFormatter.formatCatalogText(
            shopName = userSettings.shopName,
            ownerName = userSettings.ownerName,
            phone = userSettings.phone,
            address = userSettings.address,
            products = listOf(sampleProducts[0])
        )
        assertTrue(result.contains("Kios Barokah"))
        assertTrue(result.contains("📍 *Alamat:* Jl. Raya Pasar No. 45"))
        assertTrue(result.contains("📞 *Hubungi / Pesan:* 081234567890 (Pak H. Ahmad)"))
    }

    @Test
    fun test7_catalogTextFormatting_headerBodyFooterStructure() {
        val result = WhatsAppCatalogFormatter.formatCatalogText(
            shopName = "Warung Kita",
            ownerName = "Bu Ani",
            phone = "08987654321",
            products = listOf(sampleProducts[0], sampleProducts[1])
        )
        // Verify Header
        assertTrue(result.startsWith("*KATALOG PRODUK*\n*Warung Kita*"))
        // Verify Body
        assertTrue(result.contains("Berikut daftar produk & harga kami:"))
        // Verify Footer
        assertTrue(result.endsWith("_Dibuat dengan Buku Warung_"))
    }

    @Test
    fun test8_deselectProduct_removesFromCatalogList() {
        val selectedSet = mutableSetOf(sampleProducts[0].id, sampleProducts[1].id, sampleProducts[2].id)
        // Deselect item 2
        selectedSet.remove(sampleProducts[1].id)

        val finalProducts = sampleProducts.filter { it.id in selectedSet }
        val result = WhatsAppCatalogFormatter.formatCatalogText(
            shopName = "Warung Bersama",
            products = finalProducts
        )
        assertTrue(result.contains("Beras Rojolele 5kg"))
        assertFalse(result.contains("Minyak Goreng 1L"))
        assertTrue(result.contains("Teh Botol 350ml"))
    }

    @Test
    fun test9_noProductMutationDuringCatalog_stockAndPricesUnchanged() = runBlocking {
        val initialProduct = database.productDao().getProductById(sampleProducts[0].id)
        assertNotNull(initialProduct)
        val initialStock = initialProduct!!.stock
        val initialPrice = initialProduct.sellingPrice

        // Generate catalog
        val catalogText = WhatsAppCatalogFormatter.formatCatalogText(
            shopName = "Warung Aman",
            products = listOf(initialProduct),
            includeStock = true
        )
        assertNotNull(catalogText)

        // Verify product in database remains completely unmodified
        val afterProduct = database.productDao().getProductById(sampleProducts[0].id)
        assertNotNull(afterProduct)
        assertEquals(initialStock, afterProduct!!.stock, 0.001)
        assertEquals(initialPrice, afterProduct.sellingPrice)
    }

    @Test
    fun test10_shareIntentCreated_withActionSendAndTextPlain() {
        val catalogText = "*KATALOG PRODUK*\n*Warung Sejahtera*"
        val intent = WhatsAppCatalogShareHelper.createShareIntent(catalogText)

        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("text/plain", intent.type)
        assertEquals(catalogText, intent.getStringExtra(Intent.EXTRA_TEXT))
    }

    @Test
    fun test11_genericChooserFallback_createsChooserIntent() {
        val catalogText = "*KATALOG PRODUK*\n*Warung Sejahtera*"
        val chooser = WhatsAppCatalogShareHelper.createShareChooserIntent(
            context = context,
            catalogText = catalogText,
            chooserTitle = "Bagikan Katalog Produk"
        )
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val targetIntent = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertNotNull(targetIntent)
        assertEquals(Intent.ACTION_SEND, targetIntent?.action)
        assertEquals(catalogText, targetIntent?.getStringExtra(Intent.EXTRA_TEXT))
    }

    @Test
    fun test12_whatsappTargetWhenAvailable_handlesPackageTargetingSafely() {
        val catalogText = "*KATALOG PRODUK*"
        // Direct to WhatsApp without crashing even if WhatsApp package isn't installed
        val intent = WhatsAppCatalogShareHelper.createShareIntent(
            catalogText = catalogText,
            directToWhatsApp = true,
            context = context
        )
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals(catalogText, intent.getStringExtra(Intent.EXTRA_TEXT))
    }

    @Test
    fun test13_largeCatalogRendering_renders50ItemsWithoutFailure() {
        val largeList = (1..50).map { i ->
            ProductEntity(
                id = i.toLong(),
                categoryId = catSembakoId,
                name = "Item Produk Nomor $i",
                purchasePrice = (i * 1000).toLong(),
                sellingPrice = (i * 1500).toLong(),
                stock = (i * 5).toDouble(),
                unit = "pcs"
            )
        }
        val result = WhatsAppCatalogFormatter.formatCatalogText(
            shopName = "Super Warung Grosir",
            products = largeList,
            includeStock = true
        )
        assertTrue(result.contains("Item Produk Nomor 1"))
        assertTrue(result.contains("Item Produk Nomor 50"))
        assertTrue(result.contains("Super Warung Grosir"))
    }

    @Test
    fun test14_readOnlyGuarantee_zeroDbTransactionsOrEventsProduced() = runBlocking {
        val initialSalesCount = database.saleDao().getAllTransactions().first().size
        val initialCashCount = database.cashDao().getAllCashTransactions().first().size
        val initialDebtCount = database.debtDao().getAllOpenDebts().first().size
        val initialSyncQueueCount = database.syncQueueDao().getPendingCount().first()

        // Perform full catalog formation workflow
        val products = database.productDao().getAllProducts().first()
        val text = WhatsAppCatalogFormatter.formatCatalogText(
            shopName = "Warung ReadOnly",
            ownerName = "Owner",
            phone = "08111111",
            products = products,
            includeStock = true
        )
        assertTrue(text.isNotBlank())

        // Verify zero mutations across all tables
        assertEquals(initialSalesCount, database.saleDao().getAllTransactions().first().size)
        assertEquals(initialCashCount, database.cashDao().getAllCashTransactions().first().size)
        assertEquals(initialDebtCount, database.debtDao().getAllOpenDebts().first().size)
        assertEquals(initialSyncQueueCount, database.syncQueueDao().getPendingCount().first())
    }
}
