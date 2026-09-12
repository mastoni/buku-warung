package id.skmnetwork.bukuwarung.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object AppColors {
    val GreenPrimary = Color(0xFF0B9F57)
    val GreenDark = Color(0xFF087A43)
    val GreenLight = Color(0xFFE8F7EF)
    val BackgroundLight = Color(0xFFF7FCF9)
    val SurfaceGray = Color(0xFFF0F0F0)
    val TextPrimary = Color(0xFF1C1B1F)
    val TextSecondary = Color(0xFF666666)
    val RedExpense = Color(0xFFE53935)
    val BlueCash = Color(0xFFE8F3FF)
    val OrangeWarning = Color(0xFFFFF1DD)
    val CardBorder = Color(0xFFE0E0E0)
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
