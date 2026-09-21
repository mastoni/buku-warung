package id.skmnetwork.bukuwarung.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * App-level window size classification for responsive layouts.
 *
 * Breakpoints:
 * - Compact:  < 600dp  (phone portrait)
 * - Medium:   600–839dp (tablet portrait, phone landscape)
 * - Expanded: >= 840dp  (tablet landscape, large screens)
 *
 * Usage:
 *   val windowSize = rememberAppWindowSize()
 *   if (windowSize.isCompact) { ... }
 *   val columns = windowSize.gridColumns(compact = 2, medium = 3, expanded = 4)
 */

/**
 * Represents the current window width classification.
 */
enum class AppWindowWidthClass {
    COMPACT,
    MEDIUM,
    EXPANDED
}

/**
 * Resolved window size state with convenience accessors.
 */
@Immutable
data class AppWindowSize(
    val widthClass: AppWindowWidthClass,
    val widthDp: Dp
) {
    val isCompact: Boolean get() = widthClass == AppWindowWidthClass.COMPACT
    val isMedium: Boolean get() = widthClass == AppWindowWidthClass.MEDIUM
    val isExpanded: Boolean get() = widthClass == AppWindowWidthClass.EXPANDED

    /**
     * Select a value based on current window width class.
     */
    fun <T> select(compact: T, medium: T, expanded: T): T = when (widthClass) {
        AppWindowWidthClass.COMPACT -> compact
        AppWindowWidthClass.MEDIUM -> medium
        AppWindowWidthClass.EXPANDED -> expanded
    }

    /**
     * Shorthand for grid column counts per breakpoint.
     */
    fun gridColumns(compact: Int, medium: Int, expanded: Int): Int =
        select(compact, medium, expanded)

    /**
     * Content max width: Compact=fill, Medium=640dp, Expanded=720dp.
     */
    val contentMaxWidth: Dp get() = select(
        compact = Dp.Unspecified,
        medium = AppResponsive.ContentMaxWidth,
        expanded = AppResponsive.ExpandedContentMaxWidth
    )

    /**
     * Horizontal padding: Compact=16dp, Tablet=24dp.
     */
    val horizontalPadding: Dp get() = select(
        compact = AppSpacing.lg,
        medium = AppResponsive.TabletHorizontalPadding,
        expanded = AppResponsive.TabletHorizontalPadding
    )
}

/**
 * Remember the current window size classification.
 * Recomposes when configuration changes (e.g. rotation, window resize).
 */
@Composable
fun rememberAppWindowSize(): AppWindowSize {
    val configuration = LocalConfiguration.current
    val widthDp = configuration.screenWidthDp.dp
    val widthClass = when {
        widthDp < 600.dp -> AppWindowWidthClass.COMPACT
        widthDp < 840.dp -> AppWindowWidthClass.MEDIUM
        else -> AppWindowWidthClass.EXPANDED
    }
    return AppWindowSize(widthClass = widthClass, widthDp = widthDp)
}
