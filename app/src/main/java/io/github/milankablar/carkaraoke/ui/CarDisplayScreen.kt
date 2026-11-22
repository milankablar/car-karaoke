@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.milankablar.carkaraoke.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    var legacy by remember { mutableStateOf(SettingsManager.getCarLegacyTitle(context)) }
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
                "Open Live lyrics in Car Karaoke on Android Auto for the moving three-line view. " +
                    "Your car controls its layout and refresh behavior."
            )
            SettingsGroup("DISPLAY") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Lyrics in track title")
                        Text(
                            "Compatibility mode for displays that hide subtitles",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = legacy,
                        onCheckedChange = {
                            legacy = it
                            SettingsManager.setCarLegacyTitle(context, it)
                            container.refreshCarDisplay()
                        }
                    )
                }
                SettingLink("Artwork & media details", "Album art, source app and text", onOpenArtwork)
            }
            SettingsGroup("CAR TIMING") {
                Text("Display delay: $offset ms")
                Text(
                    "Affects the car display only. Positive values show lyrics later.",
                    style = MaterialTheme.typography.bodySmall
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
                }
            }
        }
    }
}
