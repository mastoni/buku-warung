package id.skmnetwork.bukuwarung.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import id.skmnetwork.bukuwarung.data.local.dao.TopProductSummary
import id.skmnetwork.bukuwarung.data.repository.ReportRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar

enum class ReportPeriod(val label: String) {
    TODAY("Hari Ini"),
    LAST_7_DAYS("7 Hari"),
    THIS_MONTH("Bulan Ini"),
    ALL_TIME("Semua")
}

data class DateRange(val startDate: Long, val endDate: Long)

@OptIn(ExperimentalCoroutinesApi::class)
class ReportViewModel(
    private val repository: ReportRepository
) : ViewModel() {

    val selectedPeriod = MutableStateFlow(ReportPeriod.TODAY)

    val dateRange: StateFlow<DateRange> = selectedPeriod
        .flatMapLatest { period ->
            MutableStateFlow(calculateDateRange(period))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = calculateDateRange(ReportPeriod.TODAY)
        )

    fun selectPeriod(period: ReportPeriod) {
        selectedPeriod.value = period
    }

    // ==========================================
    // 1. SALES & RETURNS REPORTS (Period-Filtered)
    // ==========================================
    val salesTotal: StateFlow<Long?> = dateRange
        .flatMapLatest { range -> repository.getSalesTotal(range.startDate, range.endDate) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val salesReturnTotal: StateFlow<Long?> = dateRange
        .flatMapLatest { range -> repository.getSalesReturnTotal(range.startDate, range.endDate) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val netSalesTotal: StateFlow<Long> = dateRange
        .flatMapLatest { range -> repository.getNetSalesTotal(range.startDate, range.endDate) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val salesCount: StateFlow<Int> = dateRange
        .flatMapLatest { range -> repository.getSalesCount(range.startDate, range.endDate) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val salesReturnCount: StateFlow<Int> = dateRange
        .flatMapLatest { range -> repository.getSalesReturnCount(range.startDate, range.endDate) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val itemsSoldTotal: StateFlow<Double?> = dateRange
        .flatMapLatest { range -> repository.getItemsSoldTotal(range.startDate, range.endDate) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val itemsReturnedTotal: StateFlow<Double?> = dateRange
        .flatMapLatest { range -> repository.getItemsReturnedTotal(range.startDate, range.endDate) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val cashSalesTotal: StateFlow<Long?> = dateRange
        .flatMapLatest { range -> repository.getCashSalesTotal(range.startDate, range.endDate) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val creditSalesTotal: StateFlow<Long?> = dateRange
        .flatMapLatest { range -> repository.getCreditSalesTotal(range.startDate, range.endDate) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val topSellingProducts: StateFlow<List<TopProductSummary>> = dateRange
        .flatMapLatest { range -> repository.getTopSellingProducts(range.startDate, range.endDate, limit = 5) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ==========================================
    // 2. COGS & PROFIT REPORTS (Period-Filtered)
    // ==========================================
    val saleCogsTotal: StateFlow<Long?> = dateRange
        .flatMapLatest { range -> repository.getSaleCogsTotal(range.startDate, range.endDate) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val returnCogsTotal: StateFlow<Long?> = dateRange
        .flatMapLatest { range -> repository.getReturnCogsTotal(range.startDate, range.endDate) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val netCogsTotal: StateFlow<Long> = dateRange
        .flatMapLatest { range -> repository.getNetCogsTotal(range.startDate, range.endDate) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val grossProfitTotal: StateFlow<Long> = dateRange
        .flatMapLatest { range -> repository.getGrossProfitTotal(range.startDate, range.endDate) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val operatingExpenseTotal: StateFlow<Long?> = dateRange
        .flatMapLatest { range -> repository.getOperatingExpenseTotal(range.startDate, range.endDate) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val netProfitTotal: StateFlow<Long> = dateRange
        .flatMapLatest { range -> repository.getNetProfitTotal(range.startDate, range.endDate) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    // ==========================================
    // 3. PURCHASE REPORTS (Period-Filtered)
    // ==========================================
    val purchaseTotal: StateFlow<Long?> = dateRange
        .flatMapLatest { range -> repository.getPurchaseTotal(range.startDate, range.endDate) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val purchaseCount: StateFlow<Int> = dateRange
        .flatMapLatest { range -> repository.getPurchaseCount(range.startDate, range.endDate) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val itemsPurchasedTotal: StateFlow<Double?> = dateRange
        .flatMapLatest { range -> repository.getItemsPurchasedTotal(range.startDate, range.endDate) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // ==========================================
    // 4. CASH REPORTS (Period & All-Time)
    // ==========================================
    val cashIncomeTotal: StateFlow<Long?> = dateRange
        .flatMapLatest { range -> repository.getCashIncomeTotal(range.startDate, range.endDate) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val cashExpenseTotal: StateFlow<Long?> = dateRange
        .flatMapLatest { range -> repository.getCashExpenseTotal(range.startDate, range.endDate) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    // Net cash movement for the selected period (income - expense)
    val netCashMovement: StateFlow<Long> = combine(cashIncomeTotal, cashExpenseTotal) { income, expense ->
        (income ?: 0L) - (expense ?: 0L)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    // All-time current cash balance (unaffected by selected report period)
    val currentCashBalance: StateFlow<Long?> = repository.totalCashBalance
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    // All-time current stock value
    val totalStockValue: StateFlow<Long?> = repository.totalStockValue
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    // ==========================================
    // 5. CUSTOMER DEBT REPORTS (Global / Lifetime)
    // ==========================================
    val totalOutstandingDebt: StateFlow<Long?> = repository.totalOutstandingDebt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val totalPaidDebt: StateFlow<Long?> = repository.totalPaidDebt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val totalDebtCreated: StateFlow<Long?> = repository.totalDebtCreated
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val openDebtsCount: StateFlow<Int> = repository.openDebtsCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val paidDebtsCount: StateFlow<Int> = repository.paidDebtsCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // ==========================================
    // 6. SUPPLIER PAYABLE REPORTS (Global / Lifetime)
    // ==========================================
    val totalOutstandingPayable: StateFlow<Long?> = repository.totalOutstandingPayable
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val totalPaidPayable: StateFlow<Long?> = repository.totalPaidPayable
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val totalPayableCreated: StateFlow<Long?> = repository.totalPayableCreated
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val openPayablesCount: StateFlow<Int> = repository.openPayablesCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val paidPayablesCount: StateFlow<Int> = repository.paidPayablesCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    companion object {
        fun calculateDateRange(period: ReportPeriod): DateRange {
            val calendar = Calendar.getInstance()
            
            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)
            calendar.set(Calendar.MILLISECOND, 999)
            val endOfToday = calendar.timeInMillis

            return when (period) {
                ReportPeriod.TODAY -> {
                    calendar.set(Calendar.HOUR_OF_DAY, 0)
                    calendar.set(Calendar.MINUTE, 0)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    DateRange(calendar.timeInMillis, endOfToday)
                }
                ReportPeriod.LAST_7_DAYS -> {
                    calendar.add(Calendar.DAY_OF_YEAR, -6)
                    calendar.set(Calendar.HOUR_OF_DAY, 0)
                    calendar.set(Calendar.MINUTE, 0)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    DateRange(calendar.timeInMillis, endOfToday)
                }
                ReportPeriod.THIS_MONTH -> {
                    calendar.set(Calendar.DAY_OF_MONTH, 1)
                    calendar.set(Calendar.HOUR_OF_DAY, 0)
                    calendar.set(Calendar.MINUTE, 0)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    DateRange(calendar.timeInMillis, endOfToday)
                }
                ReportPeriod.ALL_TIME -> {
                    DateRange(0L, Long.MAX_VALUE)
                }
            }
        }
    }
}

class ReportViewModelFactory(
    private val repository: ReportRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ReportViewModel::class.java)) {
            return ReportViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
