package com.novadesk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.novadesk.core.navigation.NovaNavGraph
import com.novadesk.core.ui.theme.NovaDeskTheme
import com.novadesk.feature.onboarding.OnboardingScreen

/**
 * Single-activity host. All navigation happens in Compose so state
 * restoration and deep links stay in one place ([NovaNavGraph]).
 *
 * On a fresh install we show the full-screen setup flow first; once Debian
 * is on disk the app opens straight to Home on every later launch.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as NovaDeskApp).container

        setContent {
            val prefs by container.prefs.flow.collectAsState(initial = null)
            var onboarded by remember { mutableStateOf(container.setup.isComplete) }
            NovaDeskTheme(
                theme = prefs?.theme,
                accent = prefs?.accent,
                amoled = prefs?.amoled == true,
            ) {
                if (onboarded) {
                    NovaNavGraph(container)
                } else {
                    OnboardingScreen(container, onFinished = { onboarded = true })
                }
            }
        }
    }
}
