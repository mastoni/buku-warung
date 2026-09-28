package id.skmnetwork.bukuwarung.tenant

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.local.entity.CategoryEntity
import id.skmnetwork.bukuwarung.data.local.entity.CustomerEntity
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.data.preferences.UserPreferencesRepository
import id.skmnetwork.bukuwarung.data.preferences.UserSettings
import id.skmnetwork.bukuwarung.data.repository.CustomerRepository
import id.skmnetwork.bukuwarung.data.repository.ProductRepository
import id.skmnetwork.bukuwarung.data.tenant.TenantResolver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Step 3A-FIX - the P0 acceptance tests against a real Room database, on a device.
 *
 * This walks the exact production decision path: the settings value as `TenantGate` receives it,
 * [TenantResolver] - the function the gate calls - and then the repository construction
 * `ActiveTenantApp` performs with the resolved tenant.
 *
 * Why this is not written with `createComposeRule()`: this module sets `testBuildType = "release"`,
 * so the only androidTest variant is `releaseAndroidTest` and `ui-test-manifest` (a
 * `debugImplementation` dependency) never reaches the app under test. `ComponentActivity` therefore
 * cannot be hosted in the instrumentation process, and every rule-based compose androidTest fails
 * before its first assertion. That infrastructure gap is out of scope here; the evidence for this
 * gate is: the JVM suite in `ActiveTenantBindingUnitTest` (including the AppNavigation wiring
 * contract), this database suite, and a cold-start run of the installed app.
 */
@RunWith(AndroidJUnit4::class)
class ActiveTenantBindingAndroidTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase

    private val activeBusiness = "a5de61ab-bc79-4736-9473-9359101f1124"
    private val otherBusiness = "7f1d0c2a-1111-2222-3333-444455556666"
    private val legacySentinel = "LEGACY_BUSINESS"

    /** The gate's decision, expressed exactly as `TenantGate` computes it. */
    private fun resolveTenantFor(settings: UserSettings?): String? =
        settings
            ?.businessId
            ?.let { TenantResolver.resolve(it) }
            ?.takeIf { TenantResolver.isBindable(it) }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun seedProduct(tenant: String, name: String) = runBlocking {
        db.categoryDao().insertCategory(CategoryEntity(name = "Umum", businessId = tenant))
        val categoryId = db.categoryDao().getCategoryByNameIgnoreCase("Umum", tenant)!!.id
        db.productDao().insertProduct(
            ProductEntity(
                uuid = UUID.randomUUID().toString(),
                businessId = tenant,
                categoryId = categoryId,
                name = name,
                purchasePrice = 5000,
                sellingPrice = 7000,
                stock = 15.0
            )
        )
    }

    private fun seedCustomer(tenant: String, name: String) = runBlocking {
        db.customerDao().insertCustomer(
            CustomerEntity(
                uuid = UUID.randomUUID().toString(),
                businessId = tenant,
                name = name,
                phone = "08123456789"
            )
        )
    }

    // ------------------------------------------------------------------
    // TEST A: an empty / unset identity yields no tenant at all.
    // ------------------------------------------------------------------

    @Test
    fun testA_emptyBusinessId_producesNoRepositoryTenant() {
        assertNull("Settings have not loaded yet", resolveTenantFor(null))
        assertNull("The business identity does not exist yet", resolveTenantFor(UserSettings()))

        seedProduct(legacySentinel, "Gula Pasir")
        assertNull(
            "A repository built for a blank identity would read the sentinel, which must not happen",
            resolveTenantFor(UserSettings(businessId = "   "))
        )
    }

    // ------------------------------------------------------------------
    // TEST B: a real business id becomes the repository tenant.
    // ------------------------------------------------------------------

    @Test
    fun testB_activeBusinessId_bindsRepositoriesToTheActiveTenant() {
        seedProduct(activeBusiness, "Kapas Daring 1 Kg")

        val tenant = resolveTenantFor(UserSettings(businessId = activeBusiness))
        assertEquals(activeBusiness, tenant)

        val products = runBlocking { ProductRepository(db, tenant!!).allProducts.first() }
        assertEquals(1, products.size)
        assertEquals("Kapas Daring 1 Kg", products.first().name)
        assertEquals(activeBusiness, products.first().businessId)
    }

    // ------------------------------------------------------------------
    // TEST C: the business id changes -> the binding follows.
    // ------------------------------------------------------------------

    @Test
    fun testC_businessIdChange_rebindsToTheNewTenant() {
        seedProduct(activeBusiness, "Kapas Daring 1 Kg")
        seedProduct(otherBusiness, "Lampu LED 9W")

        val before = resolveTenantFor(UserSettings(businessId = activeBusiness))
        val after = resolveTenantFor(UserSettings(businessId = otherBusiness))

        assertNotEquals(before, after)

        val beforeProducts = runBlocking { ProductRepository(db, before!!).allProducts.first() }
        val afterProducts = runBlocking { ProductRepository(db, after!!).allProducts.first() }
        assertEquals("Kapas Daring 1 Kg", beforeProducts.single().name)
        assertEquals("Lampu LED 9W", afterProducts.single().name)
    }

    // ------------------------------------------------------------------
    // TEST D: the Products screen's data source shows the active tenant's products.
    // ------------------------------------------------------------------

    @Test
    fun testD_productsScreenSource_readsTheActiveTenantsProducts() {
        seedProduct(activeBusiness, "Indomie Goreng")

        val tenant = resolveTenantFor(UserSettings(businessId = activeBusiness))!!
        val products = runBlocking { ProductRepository(db, tenant).allProducts.first() }

        assertEquals(1, products.size)
        assertEquals("Indomie Goreng", products.first().name)
    }

    // ------------------------------------------------------------------
    // TEST E: the Customers screen's data source shows the active tenant's customers.
    // ------------------------------------------------------------------

    @Test
    fun testE_customersScreenSource_readsTheActiveTenantsCustomers() {
        seedCustomer(activeBusiness, "Pak Budi")

        val tenant = resolveTenantFor(UserSettings(businessId = activeBusiness))!!
        val customers = runBlocking { CustomerRepository(db, tenant).allCustomers.first() }

        assertEquals(1, customers.size)
        assertEquals("Pak Budi", customers.first().name)
        assertEquals(activeBusiness, customers.first().businessId)
    }

    // ------------------------------------------------------------------
    // TEST F: after legacy reconciliation the runtime tenant is still the active one.
    // ------------------------------------------------------------------

    @Test
    fun testF_afterLegacyReconciliation_theRuntimeTenantIsStillTheActiveBusiness() {
        seedProduct(legacySentinel, "Gula Pasir")

        // The real migration step, unchanged: LEGACY_BUSINESS rows move onto the real business id.
        runBlocking {
            UserPreferencesRepository(context).reconcileLegacyBusinessIdentity(db, activeBusiness)
        }

        val tenant = resolveTenantFor(UserSettings(businessId = activeBusiness))
        assertEquals(activeBusiness, tenant)

        val products = runBlocking { ProductRepository(db, tenant!!).allProducts.first() }
        assertEquals("Reconciled rows must be readable through the active tenant", 1, products.size)
        assertEquals("Gula Pasir", products.first().name)
        assertEquals(activeBusiness, products.first().businessId)

        // And nothing reads the sentinel any more.
        assertEquals(0, runBlocking { ProductRepository(db, legacySentinel).allProducts.first() }.size)
    }

    // ------------------------------------------------------------------
    // TEST G: no valid business id -> the gate stays not-ready, never LEGACY_BUSINESS.
    // ------------------------------------------------------------------

    @Test
    fun testG_unusableBusinessId_neverProducesARepositoryTenant() {
        val unusable = listOf("", "   ", legacySentinel, " $legacySentinel ")

        unusable.forEach { candidate ->
            assertNull(
                "'$candidate' must leave the gate not-ready, not fall back to the sentinel",
                resolveTenantFor(UserSettings(businessId = candidate))
            )
        }

        // A real identity still binds through the same gate.
        assertEquals(
            activeBusiness,
            resolveTenantFor(UserSettings(businessId = activeBusiness))
        )
    }
}
