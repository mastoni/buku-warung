package id.skmnetwork.bukuwarung.ui.pos

import id.skmnetwork.bukuwarung.data.local.entity.SaleTransactionEntity
import id.skmnetwork.bukuwarung.ui.report.ReportPeriod
import id.skmnetwork.bukuwarung.ui.report.ReportViewModel

/**
 * Step 9 - the payment methods a sale can actually carry.
 *
 * `CheckoutOrchestrator` only accepts `CASH`, `QRIS` and `CREDIT`, and those are exactly the codes
 * the POS already writes and displays, so the filter offers those three and nothing else. The
 * labels are the ones the payment selector and the sale detail dialog already use
 * ("Tunai" / "QRIS" / "Hutang"); `ALL` is the clear state and matches every method.
 *
 * `value == null` means "no filter", which is deliberately not the same as an empty string: an
 * unrecognised code can never be selected by mistake, and clearing returns the full history.
 */
enum class SalesPaymentFilter(val value: String?, val label: String) {
    ALL(null, "Semua"),
    CASH("CASH", "Tunai"),
    QRIS("QRIS", "QRIS"),
    CREDIT("CREDIT", "Hutang");

    companion object {
        /**
         * Resolves a stored payment code to its filter, so a history row whose code is not one of
         * the three known methods is never hidden by a payment filter it cannot match.
         */
        fun fromValue(value: String?): SalesPaymentFilter =
            entries.firstOrNull { it.value != null && it.value.equals(value, ignoreCase = true) } ?: ALL
    }
}

/**
 * Step 9 - read-only filtering for the sale history list.
 *
 * The history is already loaded in memory from `getAllTransactions`, which returns the tenant's
 * sales ordered `transaction_date DESC`, so filtering happens on that list: no new DAO query, no
 * schema change, and nothing is written to Room when a filter changes.
 *
 * Search, period and payment method intersect - a sale must satisfy all active criteria - and the
 * input order is preserved, so the list keeps the DAO's newest-first ordering and filtering can
 * never reorder it.
 *
 * The period bounds come from [ReportViewModel.calculateDateRange], the same function the reports
 * screen uses, so "Hari Ini" / "7 Hari" / "Bulan Ini" / "Semua" mean exactly what they mean
 * everywhere else in the app instead of a second, subtly different date rule.
 *
 * @param customerNamesById customer id to name, used by the existing search over customer name.
 */
internal fun filterSalesHistory(
    sales: List<SaleTransactionEntity>,
    customerNamesById: Map<Long, String> = emptyMap(),
    query: String = "",
    period: ReportPeriod? = null,
    paymentFilter: SalesPaymentFilter = SalesPaymentFilter.ALL
): List<SaleTransactionEntity> {
    val trimmedQuery = query.trim()
    val range = period?.let { ReportViewModel.calculateDateRange(it) }
    val wantedMethod = paymentFilter.value

    return sales.filter { sale ->
        val matchesQuery = trimmedQuery.isEmpty() ||
            sale.transactionNumber.contains(trimmedQuery, ignoreCase = true) ||
            customerNamesById[sale.customerId]?.contains(trimmedQuery, ignoreCase = true) == true

        val matchesPeriod = range == null ||
            (sale.transactionDate >= range.startDate && sale.transactionDate <= range.endDate)

        val matchesPayment = wantedMethod == null ||
            sale.paymentMethod.equals(wantedMethod, ignoreCase = true)

        matchesQuery && matchesPeriod && matchesPayment
    }
}
