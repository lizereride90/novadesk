package com.novadesk.feature.onboarding

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.novadesk.core.storage.AppContainer
import com.novadesk.core.ui.theme.NovaMono
import com.novadesk.setup.SetupCoordinator

/**
 * Full-screen first-run experience. It owns the whole "prepare the machine"
 * flow so the rest of the app can assume a working Debian:
 *
 *   Debian download → storage access → dev tools → home
 *
 * Real progress only: the bar is driven by bytes on the wire and apt output,
 * never a timer.
 */
@Composable
fun OnboardingScreen(container: AppContainer, onFinished: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by container.setup.state.collectAsState()

    LaunchedEffect(Unit) { container.setup.start() }

    // Advance the moment the guest is ready.
    LaunchedEffect(state.phase) {
        if (state.phase == SetupCoordinator.Phase.Done) onFinished()
    }

    // If the user grants storage access in system settings, continue as soon
    // as we come back to the foreground.
    DisposableEffect(lifecycleOwner, state.phase) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME &&
                state.phase == SetupCoordinator.Phase.Permission &&
                hasAllFilesAccess()
            ) {
                container.setup.onPermissionResolved()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surfaceVariant),
            ),
        ),
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "NovaDesk",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "A real Linux workspace for your phone",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(36.dp))

            when (state.phase) {
                SetupCoordinator.Phase.Welcome -> Welcome(onStart = { container.setup.start() })
                SetupCoordinator.Phase.Debian -> Step(
                    title = "Installing Debian",
                    message = state.message,
                    fraction = state.fraction,
                    indeterminate = state.fraction <= 0f,
                )
                SetupCoordinator.Phase.Permission -> Permission(
                    onGrant = {
                        if (hasAllFilesAccess()) {
                            container.setup.onPermissionResolved()
                        } else {
                            context.startActivity(allFilesAccessIntent(context.packageName))
                        }
                    },
                    onSkip = { container.setup.onPermissionResolved() },
                )
                SetupCoordinator.Phase.DevTools -> Step(
                    title = "Setting up your tools",
                    message = state.message,
                    fraction = null,
                    indeterminate = true,
                    log = state.log,
                )
                SetupCoordinator.Phase.Done -> Step(
                    title = "Ready",
                    message = state.message,
                    fraction = 1f,
                    indeterminate = false,
                )
                SetupCoordinator.Phase.Failed -> Failed(
                    state = state,
                    onRetry = { container.setup.start() },
                    onContinue = onFinished,
                )
            }
        }
    }
}

@Composable
private fun Welcome(onStart: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "NovaDesk installs a local Debian (proot), then adds Node, Python, git, " +
                "OpenCode and Codex. Nothing leaves your device unless you ask it to.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onStart, shape = RoundedCornerShape(16.dp)) {
            Text("Set up NovaDesk")
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "First run downloads ~45 MB and needs about 1.4 GB free.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Step(
    title: String,
    message: String,
    fraction: Float?,
    indeterminate: Boolean,
    log: List<String> = emptyList(),
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        if (fraction != null && !indeterminate) {
            LinearProgressIndicator(
                progress = { fraction.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(12.dp))
        Text(
            message.ifBlank { "Working…" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (log.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Column(
                Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                    .padding(12.dp),
            ) {
                log.forEach { line ->
                    Text(
                        line,
                        fontFamily = NovaMono,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun Permission(onGrant: () -> Unit, onSkip: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Almost there", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(
            "NovaDesk needs full file access to bind your shared storage into Debian " +
                "at /sdcard, so projects can live where your other apps can reach them.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onGrant, shape = RoundedCornerShape(16.dp)) {
            Text("Allow file access")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onSkip) { Text("Not now") }
    }
}

@Composable
private fun Failed(
    state: SetupCoordinator.SetupState,
    onRetry: () -> Unit,
    onContinue: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(56.dp).background(MaterialTheme.colorScheme.errorContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("!", style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onErrorContainer)
        }
        Spacer(Modifier.height(16.dp))
        Text("Setup didn't finish", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            state.error ?: "Something went wrong.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onRetry) { Text("Retry") }
            OutlinedButton(onClick = onContinue) { Text("Continue anyway") }
        }
    }
}

private fun hasAllFilesAccess(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

private fun allFilesAccessIntent(packageName: String): Intent =
    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
        data = Uri.parse("package:$packageName")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Fallback handled by the system if the per-app page is absent.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
