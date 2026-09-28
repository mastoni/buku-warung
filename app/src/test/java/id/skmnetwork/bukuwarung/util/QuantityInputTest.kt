package id.skmnetwork.bukuwarung.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Step 5 - the quantity text round trip.
 *
 * A merchant types a quantity; the register must store the number they typed, not a rounded or
 * truncated version of it. [parseQuantityInput] and [formatQuantityValue] are the two ends of that
 * round trip, and they must agree for every value the POS can be asked to hold.
 */
class QuantityInputTest {

    // ---- the values the gate names ---------------------------------------------

    @Test
    fun halfIsPreserved() {
        assertEquals(0.5, parseQuantityInput("0.5")!!, 0.0)
        assertEquals(0.5, parseQuantityInput("0,5")!!, 0.0)
        assertEquals("0.5", formatQuantityValue(parseQuantityInput("0,5")!!))
    }

    @Test
    fun onePointTwoFiveIsPreserved() {
        assertEquals(1.25, parseQuantityInput("1.25")!!, 0.0)
        assertEquals(1.25, parseQuantityInput("1,25")!!, 0.0)
        assertEquals("1.25", formatQuantityValue(parseQuantityInput("1,25")!!))
    }

    @Test
    fun twoPointFiveIsPreserved() {
        assertEquals(2.5, parseQuantityInput("2.5")!!, 0.0)
        assertEquals(2.5, parseQuantityInput("2,5")!!, 0.0)
        assertEquals("2.5", formatQuantityValue(parseQuantityInput("2,5")!!))
    }

    // ---- integers must keep working --------------------------------------------

    @Test
    fun wholeNumbersAreStillAccepted() {
        assertEquals(10.0, parseQuantityInput("10")!!, 0.0)
        assertEquals(10.0, parseQuantityInput("10,0")!!, 0.0)
        assertEquals(1.0, parseQuantityInput("1")!!, 0.0)
        assertEquals("10", formatQuantityValue(parseQuantityInput("10")!!))
        assertEquals("1", formatQuantityValue(parseQuantityInput("1")!!))
    }

    @Test
    fun aWholeNumberNeverPrintsWithATrailingZero() {
        // Step 2's rule, still required after this change: 2 must read "2", not "2.0".
        assertEquals("2", formatQuantityValue(parseQuantityInput("2,0")!!))
        assertEquals("10", formatQuantityValue(10.0))
    }

    // ---- no truncation, no rounding --------------------------------------------

    @Test
    fun aFractionalValueIsNeverTruncatedByTheInputPath() {
        listOf("0.5", "1.25", "2.5", "10.75", "0.125", "99.999").forEach { typed ->
            val parsed = parseQuantityInput(typed)
            assertEquals("'$typed' must survive the input path", typed.replace(',', '.').toDouble(), parsed!!, 0.0)
        }
    }

    @Test
    fun commaAndDotSpellingsProduceTheIdenticalNumber() {
        listOf("0,5", "1,25", "2,5", "10,75").forEach { comma ->
            val dot = comma.replace(',', '.')
            assertEquals(
                "Both spellings of $comma must be the same number",
                parseQuantityInput(dot)!!,
                parseQuantityInput(comma)!!,
                0.0
            )
        }
    }

    @Test
    fun surroundingWhitespaceIsTolerated() {
        assertEquals(1.25, parseQuantityInput("  1,25  ")!!, 0.0)
    }

    // ---- invalid input stays invalid -------------------------------------------

    @Test
    fun invalidTextIsRejectedRatherThanSilentlyBecomingZero() {
        listOf("", "   ", "abc", "1,2,3", "1.2.3", "--5", "1/2", "NaN", "Infinity", "1,25 kg")
            .forEach { typed ->
                assertNull("'$typed' must not parse", parseQuantityInput(typed))
            }
    }

    @Test
    fun zeroAndNegativeAreLeftForTheCallerToJudge() {
        // The parser is deliberately not a rule engine: it converts text, it does not decide what a
        // valid quantity is. The POS keeps the existing rules - zero empties the line, negative is
        // refused, and neither is silently clamped here.
        assertEquals(0.0, parseQuantityInput("0")!!, 0.0)
        assertEquals(0.0, parseQuantityInput("0,0")!!, 0.0)
        assertEquals(-1.5, parseQuantityInput("-1,5")!!, 0.0)
    }

    // ---- round trip ------------------------------------------------------------

    @Test
    fun formattingTheParsedValueReproducesTheTypedValue() {
        listOf("0.5", "1.25", "2.5", "10", "10.75", "0.125").forEach { typed ->
            val roundTripped = formatQuantityValue(parseQuantityInput(typed)!!)
            assertEquals("'$typed' must survive a parse/format round trip", typed, roundTripped)
        }
    }

    @Test
    fun aLargeQuantityIsNotOverflowedOrReformatted() {
        assertEquals(99999.5, parseQuantityInput("99999,5")!!, 0.0)
        assertEquals("99999.5", formatQuantityValue(99999.5))
    }
}
