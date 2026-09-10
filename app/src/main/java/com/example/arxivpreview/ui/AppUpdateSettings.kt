package com.example.arxivpreview.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.example.arxivpreview.BuildConfig
import com.example.arxivpreview.data.update.UpdatePhase
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun AppUpdateSettings(state: SettingsUiState, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val scope = rememberCoroutineScope()
    var showNotes by remember { mutableStateOf(false) }
    var launchingInstaller by remember { mutableStateOf(false) }
    val update = state.update
    val release = update.release
    val busy = update.phase != UpdatePhase.IDLE || state.updateQueued || launchingInstaller
    val launchInstaller: () -> Unit = {
        if (!launchingInstaller) {
            launchingInstaller = true
            scope.launch {
                try {
                    context.startActivity(viewModel.appUpdateInstallIntent())
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    viewModel.reportAppUpdateError(error.message ?: "Could not open the Android installer.")
                } finally {
                    launchingInstaller = false
                }
            }
        }
    }
    val installPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (context.packageManager.canRequestPackageInstalls()) {
            launchInstaller()
        } else {
            viewModel.reportAppUpdateError("Allow installs from arXiV to install this update. Tap Install to try again.")
        }
    }
    ListItem(
        headlineContent = { Text("Current version") },
        supportingContent = { Text("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})") },
    )
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Updates are checked automatically every day. Download when you’re ready.", style = MaterialTheme.typography.bodySmall)
        Text(
            when (update.phase) {
                UpdatePhase.CHECKING -> "Checking for updates…"
                UpdatePhase.DOWNLOADING -> "Downloading update… ${update.progress}%"
                UpdatePhase.VERIFYING -> "Verifying update…"
                UpdatePhase.IDLE -> when {
                    state.updateQueued -> "Update queued. Waiting for a network connection or another update task…"
                    update.readyToInstall -> "Version ${release?.version} is ready to install"
                    release != null -> "Version ${release.version} is available"
                    update.error != null -> "Could not complete the update. Please try again."
                    update.lastCheckedAt > 0 -> "You’re up to date"
                    else -> "Check for a newer version of arXiV"
                }
            },
            style = MaterialTheme.typography.bodyLarge,
        )
        if (busy) {
            if (update.phase == UpdatePhase.DOWNLOADING) {
                LinearProgressIndicator(progress = { update.progress / 100f }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        if (update.lastCheckedAt > 0) {
            val checked = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                .withLocale(locale)
                .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(update.lastCheckedAt))
            Text("Last checked: $checked", style = MaterialTheme.typography.bodySmall)
        }
        update.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (release != null) {
            Text("Download size: ${String.format(locale, "%.1f", release.bytes / (1024.0 * 1024.0))} MB")
            if (release.notes.isNotBlank()) TextButton(onClick = { showNotes = true }) { Text("Release notes") }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = viewModel::checkForAppUpdate, enabled = !busy) { Text("Check for updates") }
            if (release != null) {
                Button(
                    enabled = !busy,
                    onClick = {
                        if (!update.readyToInstall) {
                            viewModel.downloadAppUpdate()
                        } else if (context.packageManager.canRequestPackageInstalls()) {
                            launchInstaller()
                        } else {
                            try {
                                installPermission.launch(
                                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri()),
                                )
                            } catch (error: Exception) {
                                viewModel.reportAppUpdateError("Could not open install permissions. Enable them in Android Settings and try again.")
                            }
                        }
                    },
                ) { Text(if (update.readyToInstall) "Install" else "Download") }
            }
        }
    }
    if (showNotes && release != null) {
        AlertDialog(
            onDismissRequest = { showNotes = false },
            title = { Text("Version ${release.version}") },
            text = { Text(release.notes, Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { showNotes = false }) { Text("Close") } },
        )
    }
}
