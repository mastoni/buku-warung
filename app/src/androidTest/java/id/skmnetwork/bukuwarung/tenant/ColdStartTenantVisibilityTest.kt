package id.skmnetwork.bukuwarung.tenant

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.local.entity.CategoryEntity
import id.skmnetwork.bukuwarung.data.local.entity.CustomerEntity
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.data.repository.CustomerRepository
import id.skmnetwork.bukuwarung.data.repository.ProductRepository
import id.skmnetwork.bukuwarung.data.tenant.TenantResolver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Step 3A - cold start tenant visibility, against a real Room database.
 *
 * This is the suite that would have caught the P0. It seeds rows under the ACTIVE business id exactly
 * as `reconcileLegacyBusinessIdentity` leaves them, then binds the repositories the way
 * `AppNavigation` does and proves the merchant's data is visible.
 *
 * Instrumented: it needs a real SQLite database, so it cannot run on the JVM. Run with
 * `./gradlew :app:connectedDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class ColdStartTenantVisibilityTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase

    private val activeTenant = "a5de61ab-bc79-4736-9473-9359101f1124"
    private val legacySentinel = TenantResolver.MIGRATION_ONLY_SENTINEL

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

    private fun seedProduct(tenant: String, name: String, categoryId: Long) = runBlocking {
        val now = System.currentTimeMillis()
        db.categoryDao().insertCategory(CategoryEntity(name = "Umum", businessId = tenant))
        val id = db.categoryDao().getCategoryByNameIgnoreCase("Umum", tenant)!!.id
        db.productDao().insertProduct(
            ProductEntity(
                uuid = UUID.randomUUID().toString(),
                businessId = tenant,
                categoryId = id,
                name = name,
                purchasePrice = 5000,
                sellingPrice = 7000,
                stock = 15.0,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    // ---------------- CASE 2: Products shows the active tenant's products ----------------

    @Test
    fun case2_productsScreenSeesProductsOwnedByTheActiveTenant() = runBlocking {
        val categoryId = db.categoryDao().let {
            db.categoryDao().insertCategory(CategoryEntity(name = "Umum", businessId = activeTenant))
            db.categoryDao().getCategoryByNameIgnoreCase("Umum", activeTenant)!!.id
        }
        db.productDao().insertProduct(
            ProductEntity(
                uuid = UUID.randomUUID().toString(),
                businessId = activeTenant,
                categoryId = categoryId,
                name = "Kapas Daring 1 Kg",
                purchasePrice = 5800,
                sellingPrice = 7000,
                stock = 15.0
            )
        )

        // This is the ProductRepository that AppNavigation builds once the tenant is known.
        val productRepository = ProductRepository(db, TenantResolver.resolve(activeTenant)!!)
        val products = productRepository.allProducts.first()

        assertEquals("The active tenant's product must be visible", 1, products.size)
        assertEquals("Kapas Daring 1 Kg", products.first().name)
    }

    @Test
    fun case2b_aRepositoryBoundToTheLegacySentinelSeesNothing() = runBlocking {
        seedProduct(activeTenant, "Kapas Daring 1 Kg", 0L)

        // The old, broken binding. It must be provably blind, which is why the P0 was invisible until
        // the UI was compared against the database.
        val broken = ProductRepository(db, legacySentinel)
        assertEquals("A LEGACY_BUSINESS-bound repository must see nothing", 0, broken.allProducts.first().size)
    }

    // ---------------- CASE 3: POS sees the same products ----------------

    @Test
    fun case3_posProductFlowSeesTheSameProductsAsTheProductsScreen() = runBlocking {
        val categoryId = run {
            db.categoryDao().insertCategory(CategoryEntity(name = "Umum", businessId = activeTenant))
            db.categoryDao().getCategoryByNameIgnoreCase("Umum", activeTenant)!!.id
        }
        listOf("Kapas Daring 1 Kg", "Lampu LED 9W", "Indomie Goreng").forEach { name ->
            db.productDao().insertProduct(
                ProductEntity(
                    uuid = UUID.randomUUID().toString(),
                    businessId = activeTenant,
                    categoryId = categoryId,
                    name = name,
                    purchasePrice = 5000,
                    sellingPrice = 7000,
                    stock = 10.0
                )
            )
        }

        val repository = ProductRepository(db, TenantResolver.resolve(activeTenant)!!)
        val posGrid = repository.allProducts.first()

        assertEquals("POS must be able to populate its grid", 3, posGrid.size)
        assertTrue(posGrid.any { it.name == "Lampu LED 9W" })
    }

    // ---------------- CASE 4: Customers ----------------

    @Test
    fun case4_customersScreenSeesCustomersOwnedByTheActiveTenant() = runBlocking {
        db.customerDao().insertCustomer(
            CustomerEntity(
                uuid = UUID.randomUUID().toString(),
                businessId = activeTenant,
                name = "Pak Budi",
                phone = "08123456789"
            )
        )

        val customerRepository = CustomerRepository(db, TenantResolver.resolve(activeTenant)!!)
        val customers = customerRepository.allCustomers.first()

        assertEquals(1, customers.size)
        assertEquals("Pak Budi", customers.first().name)
    }

    // ---------------- CASE 5: reconciliation does not leave a stale binding ----------------

    @Test
    fun case5_afterLegacyRowsAreReconciledTheActiveTenantStillReadsThem() = runBlocking {
        // A pre-reconciliation install: rows still sitting on the migration sentinel.
        val legacyCategory = run {
            db.categoryDao().insertCategory(CategoryEntity(name = "Warung", businessId = legacySentinel))
            db.categoryDao().getCategoryByNameIgnoreCase("Warung", legacySentinel)!!.id
        }
        db.productDao().insertProduct(
            ProductEntity(
                uuid = UUID.randomUUID().toString(),
                businessId = legacySentinel,
                categoryId = legacyCategory,
                name = "Gula Pasir",
                purchasePrice = 6000,
                sellingPrice = 7000,
                stock = 22.0
            )
        )

        // This mirrors reconcileLegacyBusinessIdentity: rows move to the real tenant.
        val raw = db.openHelper.writableDatabase
        listOf("products", "categories").forEach { table ->
            raw.execSQL("UPDATE `$table` SET `business_id` = ? WHERE `business_id` = ?", arrayOf(activeTenant, legacySentinel))
        }

        val repository = ProductRepository(db, TenantResolver.resolve(activeTenant)!!)
        val products = repository.allProducts.first()

        assertEquals("Reconciled rows must be readable through the active tenant", 1, products.size)
        assertEquals("Gula Pasir", products.first().name)
        assertEquals("The row must carry the active tenant", activeTenant, products.first().businessId)
    }

    @Test
    fun case5b_theMigrationSentinelIsStillAcceptedByReconciliation() = runBlocking {
        // The reconciliation contract is unchanged: it still knows the sentinel by name.
        assertTrue(TenantResolver.isMigrationOnlySentinel(legacySentinel))
        assertTrue(legacySentinel.isNotBlank())
    }

    // ---------------- CASE 6: a tenant change rebinds ----------------

    @Test
    fun case6_aNewTenantDoesNotSeeThePreviousTenantsData() = runBlocking {
        val otherTenant = "7f1d0c2a-1111-2222-3333-444455556666"
        val categoryId = run {
            db.categoryDao().insertCategory(CategoryEntity(name = "Umum", businessId = activeTenant))
            db.categoryDao().getCategoryByNameIgnoreCase("Umum", activeTenant)!!.id
        }
        db.productDao().insertProduct(
            ProductEntity(
                uuid = UUID.randomUUID().toString(),
                businessId = activeTenant,
                categoryId = categoryId,
                name = "Kapas Daring 1 Kg",
                purchasePrice = 5800,
                sellingPrice = 7000,
                stock = 15.0
            )
        )

        val afterSwitch = ProductRepository(db, TenantResolver.resolve(otherTenant)!!).allProducts.first()

        assertEquals("A new tenant must not inherit the old tenant's products", 0, afterSwitch.size)
    }

    // ---------------- CASE 7: no silent fallback ----------------

    @Test
    fun case7_withNoBusinessIdTheAppMustNotBindAnything() {
        assertFalse("A blank id must not be bindable", TenantResolver.isBindable(""))
        assertFalse("A null id must not be bindable", TenantResolver.isBindable(null))
        assertFalse("The sentinel must not be bindable", TenantResolver.isBindable(legacySentinel))
    }
}
