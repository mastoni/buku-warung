package id.skmnetwork.bukuwarung

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import id.skmnetwork.bukuwarung.data.preferences.UserPreferencesRepository
import id.skmnetwork.bukuwarung.data.preferences.UserSettings
import id.skmnetwork.bukuwarung.ui.navigation.BukuWarungApp
import id.skmnetwork.bukuwarung.ui.theme.BukuWarungTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefsRepo = UserPreferencesRepository(applicationContext)
        setContent {
            val userSettings by prefsRepo.userSettings.collectAsState(initial = UserSettings())
            val isDark = when (userSettings.themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> isSystemInDarkTheme()
            }
            BukuWarungTheme(darkTheme = isDark) {
                BukuWarungApp()
            }
        }
    }
}

