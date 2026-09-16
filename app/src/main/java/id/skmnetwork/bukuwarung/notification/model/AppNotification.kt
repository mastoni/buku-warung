package id.skmnetwork.bukuwarung.notification.model

import id.skmnetwork.bukuwarung.ui.navigation.AppScreen

enum class AppNotificationType {
    STOCK_LOW,
    DEBT_DUE,
    PAYABLE_DUE
}

enum class AppNotificationPriority(val value: Int) {
    CRITICAL(1),
    HIGH(2),
    MEDIUM(3),
    LOW(4)
}

data class AppNotification(
    val id: String,
    val type: AppNotificationType,
    val title: String,
    val message: String,
    val subtitle: String? = null,
    val amountFormatted: String? = null,
    val timestamp: Long,
    val priority: AppNotificationPriority = AppNotificationPriority.MEDIUM,
    val isRead: Boolean = false,
    val targetScreen: AppScreen,
    val targetId: Long? = null,
    val actionLabel: String = "Lihat Detail"
)
