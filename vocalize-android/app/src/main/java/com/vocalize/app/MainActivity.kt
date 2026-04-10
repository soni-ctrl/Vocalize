package com.vocalize.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.vocalize.app.presentation.NavGraph
import com.vocalize.app.presentation.theme.VocalizeTheme
import com.vocalize.app.util.CrashReporter
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
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
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        var keepSplash = true
        splashScreen.setKeepOnScreenCondition { keepSplash }

        setContent {
            VocalizeTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NavGraph(onSplashComplete = { keepSplash = false })
                }
            }
        }
    }
}
