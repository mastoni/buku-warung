package id.skmnetwork.bukuwarung.ui.notification

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.skmnetwork.bukuwarung.notification.NotificationViewModel
import id.skmnetwork.bukuwarung.notification.model.AppNotification
import id.skmnetwork.bukuwarung.notification.model.AppNotificationPriority
import id.skmnetwork.bukuwarung.notification.model.AppNotificationType
import id.skmnetwork.bukuwarung.ui.navigation.AppScreen
import id.skmnetwork.bukuwarung.ui.theme.AppColors
import id.skmnetwork.bukuwarung.ui.theme.AppSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationCenterScreen(
    viewModel: NotificationViewModel,
    onBack: () -> Unit,
    onNavigate: (AppScreen) -> Unit
) {
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val unreadCount by viewModel.unreadCount.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Notifikasi",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = AppColors.TextPrimary
                        )
                        if (unreadCount > 0) {
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = CircleShape,
                                color = AppColors.GreenPrimary,
                                modifier = Modifier.size(20.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = unreadCount.toString(),
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali",
                            tint = AppColors.TextPrimary
                        )
                    }
                },
                actions = {
                    if (unreadCount > 0) {
                        TextButton(
                            onClick = { viewModel.markAllAsRead() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.DoneAll,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = AppColors.GreenPrimary
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Tandai dibaca",
                                color = AppColors.GreenPrimary,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        },
        containerColor = Color(0xFFF8FAF8)
    ) { padding ->
        if (notifications.isEmpty()) {
            EmptyNotificationState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(AppSpacing.md),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(notifications, key = { it.id }) { notification ->
                    NotificationCard(
                        notification = notification,
                        onActionClick = {
                            viewModel.markAsRead(notification.id)
                            onNavigate(notification.targetScreen)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(
    notification: AppNotification,
    onActionClick: () -> Unit
) {
    val (iconBg, iconTint, icon) = when (notification.type) {
        AppNotificationType.STOCK_LOW -> {
            if (notification.priority == AppNotificationPriority.CRITICAL) {
                Triple(Color(0xFFFFEBEE), Color(0xFFD32F2F), Icons.Default.WarningAmber)
            } else {
                Triple(Color(0xFFFFF3E0), Color(0xFFE65100), Icons.Default.Inventory2)
            }
        }
        AppNotificationType.DEBT_DUE -> {
            Triple(Color(0xFFE8F5E9), AppColors.GreenDark, Icons.Default.AccountBalanceWallet)
        }
        AppNotificationType.PAYABLE_DUE -> {
            Triple(Color(0xFFE3F2FD), Color(0xFF1565C0), Icons.Default.LocalShipping)
        }
    }

    val cardBorder = if (!notification.isRead) {
        BorderStroke(1.dp, AppColors.GreenPrimary.copy(alpha = 0.4f))
    } else {
        BorderStroke(1.dp, Color(0xFFEAEAEA))
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (!notification.isRead) Color(0xFFFCFFFC) else Color.White
        ),
        border = cardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onActionClick)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Category Icon
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(Modifier.width(10.dp))

                // Title & Subtitle
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = notification.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = AppColors.TextPrimary
                        )
                        if (!notification.isRead) {
                            Spacer(Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(AppColors.GreenPrimary)
                            )
                        }
                    }
                    if (!notification.subtitle.isNullOrBlank()) {
                        Text(
                            text = notification.subtitle,
                            fontSize = 11.5.sp,
                            color = AppColors.TextSecondary
                        )
                    }
                }

                if (!notification.amountFormatted.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFF1F5F2)
                    ) {
                        Text(
                            text = notification.amountFormatted,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.TextPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Body Message
            Text(
                text = notification.message,
                fontSize = 13.sp,
                color = AppColors.TextPrimary,
                lineHeight = 18.sp
            )

            Spacer(Modifier.height(10.dp))

            // Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${notification.actionLabel} →",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.GreenPrimary
                )
            }
        }
    }
}

@Composable
private fun EmptyNotificationState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(AppColors.GreenLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = null,
                    tint = AppColors.GreenDark,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Belum Ada Notifikasi",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = AppColors.TextPrimary
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "Semua stok produk dan keuangan usaha Anda dalam kondisi aman dan tercatat rapi.",
                fontSize = 13.sp,
                color = AppColors.TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}
