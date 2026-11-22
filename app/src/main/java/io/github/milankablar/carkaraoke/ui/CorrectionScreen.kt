@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.milankablar.carkaraoke.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.milankablar.carkaraoke.AppContainer
import io.github.milankablar.carkaraoke.karaoke.KaraokeState
import io.github.milankablar.carkaraoke.karaoke.LyricCandidate
import io.github.milankablar.carkaraoke.karaoke.TrackIdentity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun CorrectionScreen(
    container: AppContainer,
    state: KaraokeState,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var choices by remember(state.track?.key) { mutableStateOf(state.resolution.candidates) }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var preview by remember(state.track?.key) { mutableStateOf<LyricCandidate?>(null) }
    var importTrack by remember { mutableStateOf<TrackIdentity?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val track = importTrack
        if (uri != null && track != null) {
            scope.launch {
                try {
                    preview = container.importLyrics(uri, track)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    error = e.message ?: "Could not import lyrics"
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tune this song") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Done") } }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(state.info?.title ?: "No song selected", style = MaterialTheme.typography.headlineSmall)
            Text(
                "A selection is saved for this recording and shared with your car.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SettingsGroup("TIMING") {
                Text("Offset: ${state.resolution.offsetMs} ms", style = MaterialTheme.typography.titleMedium)
                Text("Positive values show lyrics later.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { container.adjustOffset(-250) }) { Text("Earlier −250") }
                    OutlinedButton(onClick = { container.adjustOffset(250) }) { Text("Later +250") }
                }
            }
            Button(
                enabled = !searching && state.track != null,
                onClick = {
                    val requestTrack = state.track ?: return@Button
                    scope.launch {
                        searching = true
                        error = ""
                        try {
                            val found = container.searchCandidates(requestTrack)
                            if (requestTrack.key == container.coordinator.state.value.track?.key) {
                                choices = found
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            error = "Search unavailable. Please retry."
                        } finally {
                            searching = false
                        }
                    }
                }
            ) { Text(if (searching) "Searching…" else "Find other matches") }
            if (!state.resolution.manual) {
                TextButton(onClick = { container.refreshLyrics() }) { Text("Retry automatic lookup") }
            }
            OutlinedButton(
                enabled = state.track != null,
                onClick = {
                    importTrack = state.track
                    importLauncher.launch(arrayOf("text/*", "application/octet-stream"))
                }
            ) { Text("Import LRC or text file") }
            if (state.resolution.manual) {
                TextButton(onClick = { container.resetSelection() }) { Text("Reset to automatic lyrics") }
            }
            if (state.resolution.message.isNotBlank()) {
                Text(state.resolution.message, style = MaterialTheme.typography.bodySmall)
            }
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            choices.forEach { candidate ->
                Surface(
                    onClick = { preview = candidate },
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(Modifier.fillMaxWidth().padding(20.dp)) {
                        Text(candidate.title, fontWeight = FontWeight.Bold)
                        Text("${candidate.artist} • ${candidate.album}")
                        Text(
                            "${candidate.provider} • ${timeLabel(candidate.durationMs)}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }

    preview?.let { candidate ->
        AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text(candidate.title) },
            text = {
                Text(
                    candidate.content.take(1500).ifBlank { "Instrumental recording" },
                    Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.track?.let { container.chooseCandidate(it, candidate) }
                        preview = null
                        onBack()
                    }
                ) { Text("Use these lyrics") }
            },
            dismissButton = { TextButton(onClick = { preview = null }) { Text("Cancel") } }
        )
    }
}
