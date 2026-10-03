package id.skmnetwork.bukuwarung.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Gate 1.1 - system bar control.
 *
 * Fixes the audit's P0 #1: with no `values-night` and a platform theme hardcoded to
 * `Theme.Material.Light.NoActionBar` + `statusBarColor=#FFFFFF` + `navigationBarColor=#FFFFFF`,
 * the dark Compose theme rendered a white strip above and below a #111827 app.
 *
 * ## Why this is not `enableEdgeToEdge()`
 *
 * `ComponentActivity.enableEdgeToEdge()` always calls
 * `WindowCompat.setDecorFitsSystemWindows(window, false)`. That is the correct call for an app that
 * has already migrated every screen to explicit insets - and this app has NOT. Exactly four screens
 * handle status/navigation insets today (WelcomeScreen, FirstSetupScreen, PinLockScreen,
 * LicenseGateScreen); the other eleven would immediately start drawing their top bars and bottom
 * bars underneath the system bars. Fixing that is a per-screen change, which Gate 1.1 is explicitly
 * forbidden from making.
 *
 * The *equivalent correct AndroidX approach* is used instead, and is the same mechanism
 * `SystemBarStyle` performs internally:
 *
 *   - `WindowInsetsControllerCompat.isAppearanceLightStatusBars` / `...LightNavigationBars`
 *     control icon appearance on every API level, and are the only control that still has effect
 *     on API 35+ where `statusBarColor` / `navigationBarColor` are deprecated no-ops.
 *   - the bar colours are set for the API levels where they still apply, so there is no white strip
 *     on older devices either.
 *
 * Layout behaviour is therefore untouched, and no screen needed to change.
 *
 * ## When edge-to-edge can be adopted
 *
 * Once every screen applies insets (audit Gate 1.7, P0 #15 / P1 #40-#44), this can be replaced by
 * `enableEdgeToEdge(statusBarStyle = SystemBarStyle.auto(...), navigationBarStyle = ...)` with a
 * `detectDarkMode` that returns [BwTheme.isDark] rather than the system UI mode, because the app's
 * theme is chosen in Settings and can differ from the system's.
 */
object BwSystemBars {

    /**
     * Applies bar colours and icon appearance for [isDark]. Safe to call on every theme change and
     * safe to call from a preview or an instrumented test where no real [Activity] exists.
     */
    fun apply(context: Context, isDark: Boolean) {
        val activity = context.findActivity() ?: return
        runCatching {
            val window = activity.window
            val controller = WindowCompat.getInsetsController(window, window.decorView)

            // isAppearanceLight* = true means "dark icons on a light bar".
            controller.isAppearanceLightStatusBars = !isDark
            controller.isAppearanceLightNavigationBars = !isDark

            // Deprecated on API 35+ (edge-to-edge is enforced there and the bars are transparent),
            // but still the only way to colour the bars on API 24..34, where minSdk puts us.
            @Suppress("DEPRECATION")
            run {
                val scrim = if (isDark) SystemBarDark else SystemBarLight
                window.statusBarColor = scrim.toArgbInt()
                window.navigationBarColor = scrim.toArgbInt()
            }
        }
    }

    /** Applies the system bars for the theme currently in effect at this composition. */
    @Composable
    fun Apply() {
        val context = LocalContext.current
        val view = LocalView.current
        val isDark = BwTheme.isDark

        // SideEffect runs after every successful composition of this frame, so switching LIGHT <-> DARK
        // in Settings re-applies the bars without needing a lifecycle callback. It reads
        // LocalView.current purely to make this recompose when the theme flips; the window is taken
        // from the context, which is the Activity for a real app and is guarded in apply().
        @Suppress("UNUSED_EXPRESSION")
        view

        SideEffect {
            apply(context, isDark)
        }
    }

    private fun Context.findActivity(): Activity? {
        var current: Context? = this
        var depth = 0
        while (current != null && depth < MAX_CONTEXT_DEPTH) {
            if (current is Activity) return current
            current = (current as? ContextWrapper)?.baseContext
            depth++
        }
        return null
    }

    private fun androidx.compose.ui.graphics.Color.toArgbInt(): Int =
        android.graphics.Color.argb(
            (alpha * 255f + 0.5f).toInt().coerceIn(0, 255),
            (red * 255f + 0.5f).toInt().coerceIn(0, 255),
            (green * 255f + 0.5f).toInt().coerceIn(0, 255),
            (blue * 255f + 0.5f).toInt().coerceIn(0, 255)
        )

    private const val MAX_CONTEXT_DEPTH = 8
}
