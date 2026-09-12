package id.skmnetwork.bukuwarung

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import id.skmnetwork.bukuwarung.ui.navigation.BukuWarungApp
import id.skmnetwork.bukuwarung.ui.theme.BukuWarungTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BukuWarungTheme {
                BukuWarungApp()
            }
        }
    }
}
