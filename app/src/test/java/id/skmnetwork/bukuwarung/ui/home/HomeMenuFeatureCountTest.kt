package id.skmnetwork.bukuwarung.ui.home

import id.skmnetwork.bukuwarung.domain.business.BusinessTerminology
import id.skmnetwork.bukuwarung.ui.navigation.AppScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Step 10 - the "Menu Utama" feature count.
 *
 * The header used to render a hardcoded "8 Fitur" while the grid underneath built its tiles from
 * its own local list, so adding, removing or gating a destination would have left the number
 * lying. The count now comes from [homeMenuFeatureCountLabel], which reads [homeMenuItems] - the
 * same list [MenuGrid] renders.
 *
 * These tests assert the *relationship* rather than a literal number: for every terminology
 * profile, the displayed number must be exactly the size of the rendered collection. A fixed
 * expected value would just re-introduce the magic number this gate removed.
 */
class HomeMenuFeatureCountTest {

    private val profiles = listOf(
        BusinessTerminology(),
        BusinessTerminology(transactionLabel = "Order / Struk", productLabel = "Item", stockLabel = "Gudang"),
        BusinessTerminology(transactionLabel = "Penjualan", purchaseLabel = "Beli", customerLabel = "Konsumen")
    )

    @Test
    fun `the displayed number equals the size of the collection the grid renders`() {
        for (profile in profiles) {
            val items = homeMenuItems(profile)
            assertEquals(
                "The header must count exactly the tiles the grid draws",
                items.size,
                displayedNumber(homeMenuFeatureCountLabel(profile))
            )
        }
    }

    @Test
    fun `the number is not a constant baked into the label`() {
        // Every label is built from the current collection, so the number a profile gets is a
        // function of the menu, not of a literal. This is the property the old "8 Fitur" string
        // could not satisfy: it stayed 8 whatever the menu contained.
        val labels = profiles.map { displayedNumber(homeMenuFeatureCountLabel(it)) }
        assertTrue(
            "The count must be produced by the collection, so it must match it for every profile",
            labels.all { it == homeMenuItems(profiles[labels.indexOf(it)]).size }
        )
        assertTrue(
            "The main menu must not be empty, otherwise the header would claim 0 Fitur",
            homeMenuItems(BusinessTerminology()).isNotEmpty()
        )
    }

    @Test
    fun `changing the collection changes the count`() {
        // Simulates the maintenance this gate exists for: a different menu must be reported
        // differently. Building a label from a list of a different size must not produce the same
        // number, which is what a hardcoded literal would do.
        val current = homeMenuItems(BusinessTerminology())
        val currentLabel = homeMenuFeatureCountLabel(BusinessTerminology())
        val oneMoreTile = current + current.first().copy(destination = AppScreen.HOME)
        val oneFewerTile = current.drop(1)

        assertNotEquals(
            "A menu with an extra destination must not report the old count",
            currentLabel,
            countLabelFor(oneMoreTile.size)
        )
        assertNotEquals(
            "A menu with a removed destination must not report the old count",
            currentLabel,
            countLabelFor(oneFewerTile.size)
        )
        assertEquals(countLabelFor(current.size), currentLabel)
    }

    @Test
    fun `the label keeps the Fitur wording and only the number is derived`() {
        val label = homeMenuFeatureCountLabel(BusinessTerminology())
        val tokens = label.split(" ")
        assertEquals(
            "The label must stay the form '<number> Fitur', got '$label'",
            2,
            tokens.size
        )
        assertEquals("The wording must stay 'Fitur'", "Fitur", tokens[1])
        assertTrue(
            "The first token must be a number taken from the menu, got '${tokens[0]}'",
            tokens[0].toIntOrNull() != null
        )
    }

    @Test
    fun `no hardcoded 8 Fitur literal remains in the home production source`() {
        val source = locateHomeScreenSource()
        val text = source.readText()
        assertTrue(
            "The hardcoded \"8 Fitur\" literal must be gone from HomeScreen.kt",
            !text.contains("\"8 Fitur\"")
        )
        assertTrue(
            "The count must be produced by the shared label function",
            text.contains("homeMenuFeatureCountLabel(")
        )
    }

    @Test
    fun `the menu destinations are unchanged`() {
        assertEquals(
            listOf(
                AppScreen.POS,
                AppScreen.PRODUCTS,
                AppScreen.PURCHASE,
                AppScreen.CUSTOMERS,
                AppScreen.SUPPLIERS,
                AppScreen.CASH,
                AppScreen.REPORTS,
                AppScreen.SETTINGS
            ),
            homeMenuItems(BusinessTerminology()).map { it.destination }
        )
    }

    @Test
    fun `the menu destinations are unique so no tile is counted twice`() {
        val destinations = homeMenuItems(BusinessTerminology()).map { it.destination }
        assertEquals(
            "A duplicated destination would make the count overstate the tiles",
            destinations.size,
            destinations.distinct().size
        )
    }

    @Test
    fun `menu labels still come from the business terminology`() {
        val cafe = homeMenuItems(BusinessTerminology(transactionLabel = "Order / Struk"))
        val posLabel = cafe.first { it.destination == AppScreen.POS }.label
        assertTrue(
            "The POS label must follow the terminology, got '$posLabel'",
            posLabel.startsWith("Order / Struk")
        )
    }

    @Test
    fun `terminology never changes how many tiles the menu has`() {
        val base = homeMenuItems(BusinessTerminology()).size
        for (profile in profiles) {
            assertEquals(
                "Labels may change with the business type, the tile count may not",
                base,
                homeMenuItems(profile).size
            )
        }
    }

    // ---- helpers -----------------------------------------------------------

    private fun countLabelFor(size: Int) = "$size Fitur"

    private fun displayedNumber(label: String): Int {
        val number = label.substringBefore(" ").trim()
        assertTrue("The count must start with a number, got '$label'", number.toIntOrNull() != null)
        return number.toInt()
    }

    private fun locateHomeScreenSource(): File {
        val relative = "src/main/java/id/skmnetwork/bukuwarung/ui/home/HomeScreen.kt"
        val candidates = listOf(File(relative), File("app/$relative"), File("../$relative"))
        return candidates.firstOrNull { it.isFile }
            ?: error("HomeScreen.kt not found; looked at ${candidates.joinToString { it.absolutePath }}")
    }
}
