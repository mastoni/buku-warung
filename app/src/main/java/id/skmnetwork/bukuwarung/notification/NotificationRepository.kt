package id.skmnetwork.bukuwarung.notification

import id.skmnetwork.bukuwarung.data.local.dao.DebtWithCustomerItem
import id.skmnetwork.bukuwarung.data.local.dao.PayableWithSupplierItem
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.data.preferences.UserPreferencesRepository
import id.skmnetwork.bukuwarung.data.preferences.UserSettings
import id.skmnetwork.bukuwarung.notification.model.AppNotification
import id.skmnetwork.bukuwarung.notification.model.AppNotificationPriority
import id.skmnetwork.bukuwarung.notification.model.AppNotificationType
import id.skmnetwork.bukuwarung.ui.navigation.AppScreen
import id.skmnetwork.bukuwarung.util.formatRupiah
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class NotificationRepository(
    private val appDatabase: AppDatabase,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    private val productDao = appDatabase.productDao()
    private val debtDao = appDatabase.debtDao()
    private val supplierPayableDao = appDatabase.supplierPayableDao()

    val notifications: Flow<List<AppNotification>> = combine(
        productDao.getAllProducts(),
        debtDao.getOpenDebtsWithCustomer(),
        supplierPayableDao.getOpenPayablesWithSupplier(),
        userPreferencesRepository.userSettings,
        userPreferencesRepository.readNotificationIds
    ) { products, debts, payables, userSettings, readIds ->
        computeNotifications(
            products = products,
            debts = debts,
            payables = payables,
            userSettings = userSettings,
            readIds = readIds
        )
    }

    val unreadCount: Flow<Int> = notifications.map { list ->
        list.count { !it.isRead }
    }

    suspend fun markAsRead(notificationId: String) {
        userPreferencesRepository.markNotificationAsRead(notificationId)
    }

    suspend fun markAllAsRead(notificationIds: Collection<String>) {
        userPreferencesRepository.markAllNotificationsAsRead(notificationIds)
    }

    companion object {
        fun computeNotifications(
            products: List<ProductEntity>,
            debts: List<DebtWithCustomerItem>,
            payables: List<PayableWithSupplierItem>,
            userSettings: UserSettings,
            readIds: Set<String>
        ): List<AppNotification> {
            val activeBusinessId = userSettings.businessId.trim()
            val list = mutableListOf<AppNotification>()

            // 1. Low Stock Notifications
            if (userSettings.lowStockNotificationEnabled && userSettings.lowStockAlertEnabled) {
                val lowStockProducts = products.filter { product ->
                    !product.isDeleted &&
                    product.stock <= product.minimumStock &&
                    isMatchingBusiness(product.businessId, activeBusinessId)
                }

                for (p in lowStockProducts) {
                    val isZero = p.stock <= 0.0
                    val stockDisplay = if (p.stock % 1.0 == 0.0) p.stock.toInt().toString() else p.stock.toString()
                    val minDisplay = if (p.minimumStock % 1.0 == 0.0) p.minimumStock.toInt().toString() else p.minimumStock.toString()
                    val notifId = "STOCK_LOW_${p.id}"

                    list.add(
                        AppNotification(
                            id = notifId,
                            type = AppNotificationType.STOCK_LOW,
                            title = if (isZero) "Stok Habis" else "Stok Menipis",
                            message = if (isZero) {
                                "${p.name} habis (0 ${p.unit})"
                            } else {
                                "${p.name} tersisa $stockDisplay ${p.unit} (Min: $minDisplay ${p.unit})"
                            },
                            subtitle = "Produk & Stok",
                            amountFormatted = "$stockDisplay ${p.unit}",
                            timestamp = p.updatedAt,
                            priority = if (isZero) AppNotificationPriority.CRITICAL else AppNotificationPriority.HIGH,
                            isRead = notifId in readIds,
                            targetScreen = AppScreen.PRODUCTS,
                            targetId = p.id,
                            actionLabel = "Lihat Produk"
                        )
                    )
                }
            }

            // 2. Customer Debt (Piutang) Notifications
            val openDebts = debts.filter { debt ->
                debt.status == "OPEN" &&
                (debt.totalDebt - debt.paidAmount) > 0L
            }

            for (d in openDebts) {
                val outstanding = d.totalDebt - d.paidAmount
                val customerName = d.customerName?.trim()?.ifEmpty { null } ?: "Pelanggan"
                val notifId = "DEBT_OPEN_${d.debtId}"

                list.add(
                    AppNotification(
                        id = notifId,
                        type = AppNotificationType.DEBT_DUE,
                        title = "Piutang Belum Lunas",
                        message = "$customerName memiliki tagihan belum lunas",
                        subtitle = "Pelanggan & Piutang",
                        amountFormatted = formatRupiah(outstanding),
                        timestamp = d.createdAt,
                        priority = AppNotificationPriority.HIGH,
                        isRead = notifId in readIds,
                        targetScreen = AppScreen.CUSTOMERS,
                        targetId = d.customerId,
                        actionLabel = "Lihat Pelanggan"
                    )
                )
            }

            // 3. Supplier Payable (Hutang) Notifications
            val openPayables = payables.filter { payable ->
                payable.status == "OPEN" &&
                (payable.totalDebt - payable.paidAmount) > 0L
            }

            for (sp in openPayables) {
                val outstanding = sp.totalDebt - sp.paidAmount
                val supplierName = sp.supplierName?.trim()?.ifEmpty { null } ?: "Supplier"
                val notifId = "PAYABLE_OPEN_${sp.payableId}"

                list.add(
                    AppNotification(
                        id = notifId,
                        type = AppNotificationType.PAYABLE_DUE,
                        title = "Hutang Supplier",
                        message = "Hutang belum lunas kepada $supplierName",
                        subtitle = "Supplier & Hutang",
                        amountFormatted = formatRupiah(outstanding),
                        timestamp = sp.createdAt,
                        priority = AppNotificationPriority.MEDIUM,
                        isRead = notifId in readIds,
                        targetScreen = AppScreen.SUPPLIERS,
                        targetId = sp.supplierId,
                        actionLabel = "Lihat Supplier"
                    )
                )
            }

            // Sort notifications: Priority first, then timestamp descending
            return list.sortedWith(
                compareBy<AppNotification> { it.priority.value }
                    .thenByDescending { it.timestamp }
            )
        }

        private fun isMatchingBusiness(itemBusinessId: String, activeBusinessId: String): Boolean {
            if (activeBusinessId.isBlank() || activeBusinessId == "LEGACY_BUSINESS") return true
            return itemBusinessId == activeBusinessId || itemBusinessId == "LEGACY_BUSINESS" || itemBusinessId.isBlank()
        }
    }
}
