package com.novadesk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.novadesk.core.navigation.NovaNavGraph
import com.novadesk.core.ui.theme.NovaDeskTheme

/**
 * Single-activity host. All navigation happens in Compose so state
 * restoration and deep links stay in one place ([NovaNavGraph]).
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as NovaDeskApp).container

        setContent {
            val prefs by container.prefs.flow.collectAsState(initial = null)
            NovaDeskTheme(
                theme = prefs?.theme,
                accent = prefs?.accent,
                amoled = prefs?.amoled == true,
            ) {
                NovaNavGraph(container)
            }
        }
    }
}
