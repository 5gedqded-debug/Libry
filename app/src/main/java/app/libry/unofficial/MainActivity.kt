package app.libry.unofficial

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import app.libry.unofficial.ui.AppRoot
import app.libry.unofficial.ui.LibryTheme
import app.libry.unofficial.ui.LocalApi

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val api = (application as LibryApp).api
        setContent {
            val dark = when (api.prefs.theme) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            CompositionLocalProvider(LocalApi provides api) {
                LibryTheme(dark = dark) { AppRoot() }
            }
        }
    }
}
