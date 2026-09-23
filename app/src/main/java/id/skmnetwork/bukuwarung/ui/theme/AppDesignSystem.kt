package id.skmnetwork.bukuwarung.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object AppColors {
    val GreenPrimary: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primary
    val GreenDark: Color
        @Composable @ReadOnlyComposable get() = if (MaterialTheme.colorScheme.background == Color(0xFF111827)) Color(0xFF22C55E) else Color(0xFF087A43)
    val GreenLight: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primaryContainer
    val BackgroundLight: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.background
    val SurfaceGray: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceVariant
    val TextPrimary: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurface
    val TextSecondary: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant
    val RedExpense = Color(0xFFE53935)
    val BlueCash: Color
        @Composable @ReadOnlyComposable get() = if (MaterialTheme.colorScheme.background == Color(0xFF111827)) Color(0xFF1E293B) else Color(0xFFE8F3FF)
    val OrangeWarning: Color
        @Composable @ReadOnlyComposable get() = if (MaterialTheme.colorScheme.background == Color(0xFF111827)) Color(0xFF332200) else Color(0xFFFFF1DD)
    val CardBorder: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.outline
}

object AppShapes {
    val CardShape = RoundedCornerShape(16.dp)
    val ButtonShape = RoundedCornerShape(16.dp)
    val TextFieldShape = RoundedCornerShape(12.dp)
    val ChipShape = RoundedCornerShape(20.dp)
    val PillShape = RoundedCornerShape(24.dp)
}

object AppSpacing {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
}

object AppResponsive {
    val ContentMaxWidth = 640.dp
    val ExpandedContentMaxWidth = 720.dp
    val TabletHorizontalPadding = 24.dp
    val DialogMaxWidth = 560.dp
    val DialogMaxHeight = 520.dp
}
