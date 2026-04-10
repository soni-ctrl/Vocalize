package com.vocalize.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vocalize.app.presentation.NavGraph
import com.vocalize.app.presentation.theme.VocalizeTheme
import com.vocalize.app.util.Constants
import com.vocalize.app.util.CrashReporter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.map

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val dataStore by preferencesDataStore(name = "vocalize_prefs")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        CrashReporter.getSavedCrashLog(this)?.let {
            startActivity(
                Intent(this, CrashReportActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
            )
            finish()
            return
        }

        val splashScreen = installSplashScreen()
        enableEdgeToEdge()

        var keepSplash = true
        splashScreen.setKeepOnScreenCondition { keepSplash }

        setContent {
            val isDarkMode by dataStore.data.map { prefs ->
                prefs[booleanPreferencesKey(Constants.PREFS_DARK_MODE)] ?: true
            }.collectAsState(initial = true)

            VocalizeTheme(darkTheme = isDarkMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NavGraph(onSplashComplete = { keepSplash = false })
                }
            }
        }
    }
}
