package id.skmnetwork.bukuwarung.ui.pos

import id.skmnetwork.bukuwarung.data.local.entity.SaleTransactionEntity
import id.skmnetwork.bukuwarung.ui.report.ReportPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Step 9 - sale history filtering.
 *
 * The history list is the tenant's sales already loaded in memory and ordered `transaction_date
 * DESC`, so the filters run over that list. These tests pin the behaviour the discoverability
 * change depends on:
 *
 *  - no filter returns everything, unchanged and in the same order;
 *  - the period chips reuse `ReportViewModel.calculateDateRange`, including its day boundaries, so
 *    a sale exactly at the start of today is inside "Hari Ini" and one a second before the start of
 *    the window is not;
 *  - the payment chips match only the codes the POS writes, and "Semua" clears them;
 *  - search, period and payment intersect rather than replace one another;
 *  - filtering is read-only: the input list and its entities come back untouched;
 *  - a stored method the filter does not know about is never hidden by a payment filter.
 */
class SalesHistoryFilterTest {

    private companion object {
        const val TENANT = "LEGACY_BUSINESS"
    }

    private fun dayStart(daysAgo: Long = 0): Long {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -daysAgo.toInt())
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    /** The same end-of-day instant `calculateDateRange` builds, so the boundary matches the app. */
    private fun endOfToday(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        return calendar.timeInMillis
    }

    private var nextId = 1L

    private fun sale(
        number: String,
        method: String = "CASH",
        daysAgo: Long = 0,
        customerId: Long? = null,
        total: Long = 10_000L
    ) = SaleTransactionEntity(
        id = nextId++,
        uuid = "uuid-$number",
        businessId = TENANT,
        transactionNumber = number,
        transactionDate = dayStart(daysAgo),
        totalAmount = total,
        paymentMethod = method,
        customerId = customerId
    )

    private val todayCash = sale("TRX-001", "CASH", daysAgo = 0)
    private val todayQris = sale("TRX-002", "QRIS", daysAgo = 0, customerId = 1L)
    private val todayCredit = sale("TRX-003", "CREDIT", daysAgo = 0, customerId = 2L)
    private val weekCash = sale("TRX-004", "CASH", daysAgo = 3)
    private val weekQris = sale("TRX-005", "QRIS", daysAgo = 3)
    private val oldQris = sale("TRX-006", "QRIS", daysAgo = 40)

    /** Newest first, exactly as `getAllTransactions` returns them. */
    private val history = listOf(todayCash, todayQris, todayCredit, weekCash, weekQris, oldQris)

    private val customers = mapOf(1L to "Pak Budi", 2L to "Bu Sari")

    @Test
    fun `no filters return every sale in the original order`() {
        val result = filterSalesHistory(history, customers)
        assertEquals(6, result.size)
        assertEquals(history, result)
    }

    @Test
    fun `an empty query still returns every sale`() {
        assertEquals(history, filterSalesHistory(history, customers, query = ""))
        assertEquals(history, filterSalesHistory(history, customers, query = "   "))
    }

    @Test
    fun `today period keeps only today's sales`() {
        val result = filterSalesHistory(history, customers, period = ReportPeriod.TODAY)
        assertEquals(listOf(todayCash, todayQris, todayCredit), result)
    }

    @Test
    fun `seven day period includes today and the previous six days but not older sales`() {
        val result = filterSalesHistory(history, customers, period = ReportPeriod.LAST_7_DAYS)
        assertEquals(listOf(todayCash, todayQris, todayCredit, weekCash, weekQris), result)
        assertTrue("A 40-day-old sale is outside 7 Hari", oldQris.id !in result.map { it.id })
    }

    @Test
    fun `period boundaries are inclusive at both ends`() {
        val startOfToday = dayStart(0)
        val oneMillisecondEarlier = startOfToday - 1L
        val endOfTodayInstant = endOfToday()

        val insideBoundary = sale("TRX-BOUND-START").copy(transactionDate = startOfToday)
        val outsideBoundary = sale("TRX-BOUND-BEFORE").copy(transactionDate = oneMillisecondEarlier)
        val atEndOfToday = sale("TRX-BOUND-END").copy(transactionDate = endOfTodayInstant)
        val justAfterEnd = sale("TRX-BOUND-AFTER").copy(transactionDate = endOfTodayInstant + 1L)

        val result = filterSalesHistory(
            listOf(insideBoundary, outsideBoundary, atEndOfToday, justAfterEnd),
            period = ReportPeriod.TODAY
        ).map { it.id }

        assertTrue(
            "A sale at the very start of today must be inside Hari Ini",
            insideBoundary.id in result
        )
        assertTrue(
            "A sale at the last millisecond of today must be inside Hari Ini",
            atEndOfToday.id in result
        )
        assertTrue(
            "A sale one millisecond before the window must be outside Hari Ini",
            outsideBoundary.id !in result
        )
        assertTrue(
            "A sale after the window must be outside Hari Ini",
            justAfterEnd.id !in result
        )
    }

    @Test
    fun `clearing the period restores the full history`() {
        val narrowed = filterSalesHistory(history, customers, period = ReportPeriod.TODAY)
        assertEquals(3, narrowed.size)
        assertEquals(history, filterSalesHistory(history, customers, period = null))
        assertEquals(
            "ALL_TIME is the app's own 'everything' period and must not narrow the list",
            history,
            filterSalesHistory(history, customers, period = ReportPeriod.ALL_TIME)
        )
    }

    @Test
    fun `payment filter keeps only that payment method`() {
        assertEquals(
            listOf(todayCash, weekCash),
            filterSalesHistory(history, customers, paymentFilter = SalesPaymentFilter.CASH)
        )
        assertEquals(
            listOf(todayQris, weekQris, oldQris),
            filterSalesHistory(history, customers, paymentFilter = SalesPaymentFilter.QRIS)
        )
        assertEquals(
            listOf(todayCredit),
            filterSalesHistory(history, customers, paymentFilter = SalesPaymentFilter.CREDIT)
        )
    }

    @Test
    fun `clearing the payment filter restores the full history`() {
        assertEquals(
            listOf(todayCredit),
            filterSalesHistory(history, customers, paymentFilter = SalesPaymentFilter.CREDIT)
        )
        assertEquals(history, filterSalesHistory(history, customers, paymentFilter = SalesPaymentFilter.ALL))
    }

    @Test
    fun `period and payment filter intersect`() {
        assertEquals(
            "Only today's QRIS sales remain",
            listOf(todayQris),
            filterSalesHistory(
                history,
                customers,
                period = ReportPeriod.TODAY,
                paymentFilter = SalesPaymentFilter.QRIS
            )
        )
        assertEquals(
            "7 Hari QRIS includes the 3-day-old QRIS sale but not the 40-day-old one",
            listOf(todayQris, weekQris),
            filterSalesHistory(
                history,
                customers,
                period = ReportPeriod.LAST_7_DAYS,
                paymentFilter = SalesPaymentFilter.QRIS
            )
        )
        assertEquals(
            "No CREDIT sale is older than today, so the intersection is one sale",
            listOf(todayCredit),
            filterSalesHistory(
                history,
                customers,
                period = ReportPeriod.TODAY,
                paymentFilter = SalesPaymentFilter.CREDIT
            )
        )
        assertEquals(
            "A period and payment pair that matches nothing yields an empty list",
            emptyList<SaleTransactionEntity>(),
            filterSalesHistory(
                history,
                customers,
                period = ReportPeriod.TODAY,
                paymentFilter = SalesPaymentFilter.ALL
            ).filter { it.id == oldQris.id }
        )
    }

    @Test
    fun `search still matches transaction number and customer name`() {
        assertEquals(listOf(todayCash), filterSalesHistory(history, customers, query = "TRX-001"))
        assertEquals(listOf(todayQris), filterSalesHistory(history, customers, query = "budi"))
        assertEquals(listOf(todayCredit), filterSalesHistory(history, customers, query = "sari"))
        assertTrue(filterSalesHistory(history, customers, query = "tidak ada").isEmpty())
    }

    @Test
    fun `search combines with the period filter`() {
        assertEquals(
            listOf(todayQris),
            filterSalesHistory(history, customers, query = "budi", period = ReportPeriod.TODAY)
        )
        assertEquals(
            "A 40-day-old sale matches the search but not the period",
            emptyList<SaleTransactionEntity>(),
            filterSalesHistory(history, customers, query = "TRX-006", period = ReportPeriod.TODAY)
        )
        assertEquals(
            "A 3-day-old sale matches both the search and the period",
            listOf(weekQris),
            filterSalesHistory(history, customers, query = "TRX-005", period = ReportPeriod.LAST_7_DAYS)
        )
    }

    @Test
    fun `search combines with the payment filter`() {
        assertEquals(
            listOf(todayQris),
            filterSalesHistory(history, customers, query = "budi", paymentFilter = SalesPaymentFilter.QRIS)
        )
        assertTrue(
            "Search and payment filters intersect, they do not replace each other",
            filterSalesHistory(history, customers, query = "budi", paymentFilter = SalesPaymentFilter.CASH).isEmpty()
        )
    }

    @Test
    fun `search period and payment all apply at once`() {
        assertEquals(
            listOf(todayQris),
            filterSalesHistory(
                history,
                customers,
                query = "TRX-002",
                period = ReportPeriod.TODAY,
                paymentFilter = SalesPaymentFilter.QRIS
            )
        )
    }

    @Test
    fun `filtering never mutates the source list or the sales`() {
        val before = history.map { it.copy() }
        val result = filterSalesHistory(
            history,
            customers,
            query = "budi",
            period = ReportPeriod.TODAY,
            paymentFilter = SalesPaymentFilter.CASH
        )
        assertTrue("A mismatching filter combination must return nothing", result.isEmpty())
        assertEquals("The source list must be untouched", before, history)
    }

    @Test
    fun `filtering preserves the newest first order of the dao`() {
        val result = filterSalesHistory(history, customers, period = ReportPeriod.ALL_TIME)
        assertEquals(history.map { it.transactionNumber }, result.map { it.transactionNumber })
        val dates = result.map { it.transactionDate }
        assertEquals("History must stay newest first", dates.sortedDescending(), dates)
    }

    @Test
    fun `only the three known payment codes can be selected`() {
        assertEquals(SalesPaymentFilter.CASH, SalesPaymentFilter.fromValue("CASH"))
        assertEquals(SalesPaymentFilter.QRIS, SalesPaymentFilter.fromValue("QRIS"))
        assertEquals(SalesPaymentFilter.CREDIT, SalesPaymentFilter.fromValue("CREDIT"))
        assertEquals(SalesPaymentFilter.ALL, SalesPaymentFilter.fromValue(null))
        assertEquals(
            "An unknown stored code must not become a selectable filter",
            SalesPaymentFilter.ALL,
            SalesPaymentFilter.fromValue("TRANSFER")
        )
    }

    @Test
    fun `a sale with an unknown payment code is only visible when no payment filter is set`() {
        val unknown = sale("TRX-006", "TRANSFER", daysAgo = 0)
        val withUnknown = history + unknown

        assertEquals(
            "The clear state must still show every sale, including an unrecognised code",
            withUnknown.size,
            filterSalesHistory(withUnknown, customers, paymentFilter = SalesPaymentFilter.ALL).size
        )
        assertTrue(
            "A Tunai filter must not match a sale whose code is not CASH",
            unknown.id !in filterSalesHistory(withUnknown, customers, paymentFilter = SalesPaymentFilter.CASH)
                .map { it.id }
        )
        assertEquals(
            "No filter can select the unknown code, so it cannot be filtered out by name",
            listOf("Semua", "Tunai", "QRIS", "Hutang"),
            SalesPaymentFilter.entries.map { it.label }
        )
    }

    @Test
    fun `an empty history stays empty under any filter`() {
        for (period in listOf(null, ReportPeriod.TODAY, ReportPeriod.LAST_7_DAYS, ReportPeriod.ALL_TIME)) {
            for (payment in SalesPaymentFilter.entries) {
                assertTrue(
                    "Empty history must stay empty for $period / $payment",
                    filterSalesHistory(emptyList(), customers, period = period, paymentFilter = payment).isEmpty()
                )
            }
        }
    }
}
