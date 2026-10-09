package id.skmnetwork.bukuwarung

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import id.skmnetwork.bukuwarung.data.preferences.UserPreferencesRepository
import id.skmnetwork.bukuwarung.data.preferences.UserSettings
import id.skmnetwork.bukuwarung.ui.navigation.BukuWarungApp
import id.skmnetwork.bukuwarung.ui.theme.BukuWarungTheme
import id.skmnetwork.bukuwarung.ui.theme.BwSystemBars

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefsRepo = UserPreferencesRepository(applicationContext)
        setContent {
            val userSettings by prefsRepo.userSettings.collectAsState(initial = UserSettings())
            val isDark = when (userSettings.themeMode) {
                "DARK" -> true
                "SYSTEM" -> isSystemInDarkTheme()
                // "LIGHT" is the product default, also used when no preference was ever stored.
                else -> false
            }
            BukuWarungTheme(darkTheme = isDark) {
                // Gate 1.1: drive the system bars from the app's own resolved theme, inside the theme
                // so they follow the same LIGHT / DARK / SYSTEM choice the content does. Composed
                // here rather than in BukuWarungTheme itself so the theme stays side-effect free for
                // Compose previews and the instrumented screenshot tests.
                BwSystemBars.Apply()
                BukuWarungApp()
            }
        }
    }
}
