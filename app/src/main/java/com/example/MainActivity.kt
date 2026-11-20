package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.configureAppCheck
import com.example.data.repository.AppSettings
import com.example.data.repository.SettingsRepository
import com.example.ui.navigation.AppNavigation
import com.example.ui.theme.MangaLingoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check with launch Intent before any UI or ViewModels
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            val settingsRepo = remember { SettingsRepository.getInstance(applicationContext) }
            val settings by settingsRepo.settingsFlow.collectAsStateWithLifecycle(
                initialValue = AppSettings()
            )
            MangaLingoTheme(darkTheme = settings.darkThemeEnabled) {
                Surface {
                    AppNavigation()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        configureAppCheck(this, intent)
    }
}
