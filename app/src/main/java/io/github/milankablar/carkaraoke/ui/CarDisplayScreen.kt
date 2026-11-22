@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package io.github.milankablar.carkaraoke.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.milankablar.carkaraoke.AppContainer
import io.github.milankablar.carkaraoke.SettingsManager

@Composable
internal fun CarDisplayScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenArtwork: () -> Unit
) {
    val context = LocalContext.current
    var nowPlayingMode by remember { mutableStateOf(SettingsManager.getCarNowPlayingMode(context)) }
    var showArtwork by remember { mutableStateOf(SettingsManager.getCarShowArtwork(context)) }
    var actionsMode by remember { mutableStateOf(SettingsManager.getCarCustomActionsMode(context)) }
    var offset by remember { mutableLongStateOf(SettingsManager.getCarLyricsOffsetMs(context)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Android Auto") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Done") } }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text("The same song. A second screen.", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Android Auto controls the Now Playing typography and layout. " +
                    "Choose what appears in the main title and subtitle slots, or open Live lyrics for a three-line list.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SettingsGroup("NOW PLAYING") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "lyric_first" to "3-line lyrics",
                        "lyric_and_song" to "Current + Song",
                        "song_and_lyric" to "Song + Current"
                    ).forEach { (id, label) ->
                        FilterChip(
                            selected = nowPlayingMode == id,
                            onClick = {
                                nowPlayingMode = id
                                SettingsManager.setCarNowPlayingMode(context, id)
                                container.refreshCarDisplay()
                            },
                            label = { Text(label) }
                        )
                    }
                }
                Text(
                    when (nowPlayingMode) {
                        "lyric_first" -> "Main text shows current line; subtitle shows the next two lines while the right card displays song title and artist."
                        "lyric_and_song" -> "Main text shows the current line; subtitle shows song title and artist."
                        else -> "Main text shows the song title; subtitle shows the current line."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Use album artwork instead of song card")
                        Text(
                            "Off by default so the right card cleanly shows song title and artist",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = showArtwork,
                        onCheckedChange = {
                            showArtwork = it
                            SettingsManager.setCarShowArtwork(context, it)
                            container.refreshCarDisplay()
                        }
                    )
                }
                SettingLink("Metadata details", "Album name and source app labels", onOpenArtwork)
            }

            SettingsGroup("CAR CONTROLS & TIMING") {
                Text("Dashboard extra buttons", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("seek" to "Seek ±10s", "timing" to "Car sync ±250ms").forEach { (id, label) ->
                        FilterChip(
                            selected = actionsMode == id,
                            onClick = {
                                actionsMode = id
                                SettingsManager.setCarCustomActionsMode(context, id)
                                container.refreshCarDisplay()
                            },
                            label = { Text(label) }
                        )
                    }
                }
                Text("Display delay: $offset ms")
                Text(
                    "Affects the car display only. Positive values show lyrics later.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            offset = (offset - 100).coerceAtLeast(-5000)
                            SettingsManager.setCarLyricsOffsetMs(context, offset)
                            container.refreshCarDisplay()
                        }
                    ) { Text("−100 ms") }
                    OutlinedButton(
                        onClick = {
                            offset = (offset + 100).coerceAtMost(5000)
                            SettingsManager.setCarLyricsOffsetMs(context, offset)
                            container.refreshCarDisplay()
                        }
                    ) { Text("+100 ms") }
                    if (offset != 0L) {
                        TextButton(
                            onClick = {
                                offset = 0L
                                SettingsManager.setCarLyricsOffsetMs(context, 0L)
                                container.refreshCarDisplay()
                            }
                        ) { Text("Reset") }
                    }
                }
            }
        }
    }
}
