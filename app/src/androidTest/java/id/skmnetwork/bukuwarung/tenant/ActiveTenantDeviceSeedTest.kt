package id.skmnetwork.bukuwarung.tenant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.local.entity.CategoryEntity
import id.skmnetwork.bukuwarung.data.local.entity.CustomerEntity
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Step 3A-FIX - device fixture for the P0 cold-start validation.
 *
 * Seeds the installed app's REAL DataStore and REAL Room database for the active business identity:
 * a completed setup profile, a category, a product catalogue, a customer, and one product that is
 * still parked on the LEGACY_BUSINESS migration sentinel which is then reconciled onto the active
 * tenant. After this test the app can be cold-started and the Products, POS and Customers screens
 * must show this data - which is only possible if the repositories bind to the ACTIVE tenant.
 *
 * Not an assertion of the fix itself: the assertions live in `ActiveTenantBindingAndroidTest`,
 * `TenantBindingUnitTest` and `ActiveTenantBindingUnitTest`. This only prepares the device.
 */
@RunWith(AndroidJUnit4::class)
class ActiveTenantDeviceSeedTest {

    @Test
    fun seedTheActiveTenantOnTheDevice() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val prefs = UserPreferencesRepository(context)

        prefs.saveInitialSetupProfile(
            shopName = "Warung Tenant P0",
            ownerName = "Pemilik Warung",
            phone = "081200000000",
            address = "Jl. Cold Start 1",
            primaryBusinessType = "WARUNG_SEMBAKO",
            secondaryActivities = setOf("ACTIVITY_GOODS_SELLING"),
            profileVersion = 1
        )

        val businessId = prefs.getOrCreateBusinessId()
        val settings = prefs.userSettings.first()
        assertEquals("Setup must be complete for the app to reach the main screens", true, settings.isSetupCompleted)
        assertTrue("The active business id must exist", businessId.isNotBlank())
        assertEquals(businessId, settings.businessId)

        val db = AppDatabase.getDatabase(context)

        // A product still parked on the migration sentinel: after reconciliation it must belong to
        // the active tenant, which is exactly the row the old first-frame binding could not see.
        db.categoryDao().insertCategory(CategoryEntity(name = "Sembako", businessId = "LEGACY_BUSINESS"))
        db.productDao().insertProduct(
            ProductEntity(
                uuid = UUID.randomUUID().toString(),
                businessId = "LEGACY_BUSINESS",
                categoryId = db.categoryDao().getCategoryByNameIgnoreCase("Sembako", "LEGACY_BUSINESS")!!.id,
                name = "Gula Pasir 1 Kg (Rekonsiliasi)",
                purchasePrice = 14000L,
                sellingPrice = 15500L,
                stock = 20.0
            )
        )

        prefs.reconcileLegacyBusinessIdentity(db, businessId)

        db.categoryDao().insertCategory(CategoryEntity(name = "Sembako", businessId = businessId))
        val categoryId = db.categoryDao().getCategoryByNameIgnoreCase("Sembako", businessId)!!.id

        val catalogue = listOf(
            Triple("Beras Premium 5 Kg", 62000L, 68000L),
            Triple("Minyak Goreng 1 L", 16500L, 18000L),
            Triple("Indomie Goreng", 2800L, 3500L),
            Triple("Telur Ayam 1 Kg", 26000L, 28500L),
            Triple("Kopi Kapal Api", 13000L, 15000L),
            Triple("Susu Ultra 250 ml", 4800L, 5500L),
            Triple("Gula Pasir 1 Kg", 14000L, 15500L)
        )
        catalogue.forEach { (name, purchase, selling) ->
            db.productDao().insertProduct(
                ProductEntity(
                    uuid = UUID.randomUUID().toString(),
                    businessId = businessId,
                    categoryId = categoryId,
                    name = name,
                    purchasePrice = purchase,
                    sellingPrice = selling,
                    stock = 20.0
                )
            )
        }

        db.customerDao().insertCustomer(
            CustomerEntity(
                uuid = UUID.randomUUID().toString(),
                businessId = businessId,
                name = "Pak Budi",
                phone = "08123456789"
            )
        )
        db.customerDao().insertCustomer(
            CustomerEntity(
                uuid = UUID.randomUUID().toString(),
                businessId = businessId,
                name = "Bu Sari",
                phone = "08129876543"
            )
        )

        val products = id.skmnetwork.bukuwarung.data.repository.ProductRepository(db, businessId)
            .allProducts.first()
        val customers = id.skmnetwork.bukuwarung.data.repository.CustomerRepository(db, businessId)
            .allCustomers.first()

        assertEquals(
            "The catalogue plus the reconciled sentinel row must belong to the active tenant",
            catalogue.size + 1,
            products.size
        )
        assertTrue("The reconciled sentinel row must now belong to the active tenant", products.all { it.businessId == businessId })
        assertEquals(2, customers.size)
    }
}
