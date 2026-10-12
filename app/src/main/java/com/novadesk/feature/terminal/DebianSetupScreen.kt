package com.novadesk.feature.terminal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.novadesk.core.linux.DebianRelease
import com.novadesk.core.linux.DebianStatus
import com.novadesk.core.linux.DeviceArch
import com.novadesk.core.storage.AppContainer
import com.novadesk.core.ui.components.NovaCard
import kotlinx.coroutines.launch

/**
 * Debian installation wizard: architecture check → release picker →
 * download with real progress → verify → done. Every failure shows the
 * actual error with a retry path; success is only shown when os-release
 * parses inside the new tree.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebianSetupScreen(container: AppContainer, nav: NavController) {
    val installer = container.debianInstaller
    val progress by installer.progress.collectAsState()
    val arch = remember { DeviceArch.detect() }
    var release by remember { mutableStateOf(DebianRelease.default) }
    var expanded by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    var sha by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Install Debian") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            NovaCard {
                Text("1 · Device check", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Architecture: ${arch.label} (${android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "?"})",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (arch == DeviceArch.Unsupported) {
                    Text(
                        "This CPU isn't supported yet — the installer will refuse rather than download garbage.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            NovaCard {
                Text("2 · Release", style = MaterialTheme.typography.titleMedium)
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = release.prettyName, onValueChange = {}, readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DebianRelease.values().forEach { r ->
                            DropdownMenuItem(
                                text = { Text(r.prettyName) },
                                onClick = { release = r; expanded = false },
                            )
                        }
                    }
                }
            }

            NovaCard {
                Text("3 · Root filesystem", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Paste the URL of an official Debian rootfs tarball for ${release.codename} " +
                        "(${arch.label}), plus its SHA-256 when you have one. Downloads resume after interruptions.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    label = { Text("Rootfs tarball URL (.tar.xz)") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = sha, onValueChange = { sha = it },
                    label = { Text("SHA-256 (optional, recommended)") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    singleLine = true,
                )
            }

            NovaCard {
                Text("Status", style = MaterialTheme.typography.titleMedium)
                when (progress.status) {
                    DebianStatus.FetchingRuntime ->
                        Text(progress.message.ifBlank { "Fetching the Linux runtime (proot)…" })
                    DebianStatus.Downloading -> {
                        if (progress.totalBytes > 0) {
                            LinearProgressIndicator(
                                progress = { (progress.downloadedBytes.toFloat() / progress.totalBytes).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                        }
                        Text(progress.message.ifBlank { "Downloading…" })
                    }
                    DebianStatus.Verifying, DebianStatus.Extracting, DebianStatus.Configuring ->
                        Text(progress.message.ifBlank { progress.status.name })
                    DebianStatus.Ready -> Text("Debian is installed. Open the Terminal tab to use it.")
                    DebianStatus.Failed -> Text(
                        progress.message.ifBlank { "Install failed." },
                        color = MaterialTheme.colorScheme.error,
                    )
                    DebianStatus.NotInstalled -> Text("Ready when you are.")
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Button(
                        enabled = url.isNotBlank() && arch != DeviceArch.Unsupported &&
                            progress.status !in listOf(DebianStatus.Downloading, DebianStatus.Verifying, DebianStatus.Extracting, DebianStatus.Configuring),
                        onClick = {
                            error = null
                            scope.launch {
                                try {
                                    installer.install(
                                        release = release,
                                        expectedSha256 = sha.ifBlank { null },
                                        tarballUrl = url.trim(),
                                    )
                                } catch (e: Exception) {
                                    error = e.message
                                }
                            }
                        },
                    ) { Text("Install") }
                    OutlinedButton(onClick = { nav.popBackStack() }) { Text("Later") }
                }
            }

            NovaCard {
                Text("Limits, honestly", style = MaterialTheme.typography.titleMedium)
                Text(
                    "No root: packages needing real root services, systemd units, Docker and " +
                        "FUSE mounts won't work — apt, shells, compilers and interpreters work fine. " +
                        "Linux GUI apps need a display server and are out of scope for this build.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
