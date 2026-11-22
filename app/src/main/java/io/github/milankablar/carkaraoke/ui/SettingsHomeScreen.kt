@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package io.github.milankablar.carkaraoke.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.milankablar.carkaraoke.AppContainer
import io.github.milankablar.carkaraoke.BuildConfig
import io.github.milankablar.carkaraoke.SettingsManager

@Composable
internal fun SettingsHomeScreen(
    container: AppContainer,
    appearance: String,
    onAppearanceChange: (String) -> Unit,
    onNavigate: (AppRoute) -> Unit
) {
    val context = LocalContext.current
    var size by remember { mutableFloatStateOf(SettingsManager.getLyricTextSizeSp(context)) }
    var reduced by remember { mutableStateOf(SettingsManager.getReducedMotion(context)) }
    var keepOn by remember { mutableStateOf(SettingsManager.getKeepScreenOn(context)) }
    var lyricsEnabled by remember { mutableStateOf(SettingsManager.getLyricsEnabled(context)) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("Make it yours", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("A little tuning. A better sing-along.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingsGroup("APPEARANCE") {
            Text("Theme", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("System", "Light", "Dark", "Black").forEach { option ->
                    FilterChip(
                        selected = appearance == option,
                        onClick = { onAppearanceChange(option) },
                        label = { Text(option) }
                    )
                }
            }
            Text("Lyric size", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = size,
                onValueChange = { size = it },
                valueRange = 22f..40f,
                steps = 8,
                onValueChangeFinished = { SettingsManager.setLyricTextSizeSp(context, size) }
            )
            Text("The next line is yours", fontSize = size.sp, color = MaterialTheme.colorScheme.primary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Reduce motion", Modifier.weight(1f))
                Switch(
                    checked = reduced,
                    onCheckedChange = {
                        reduced = it
                        SettingsManager.setReducedMotion(context, it)
                    }
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Keep lyrics screen awake", Modifier.weight(1f))
                Switch(
                    checked = keepOn,
                    onCheckedChange = {
                        keepOn = it
                        SettingsManager.setKeepScreenOn(context, it)
                    }
                )
            }
        }
        SettingsGroup("MUSIC & LYRICS") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Show lyrics", Modifier.weight(1f))
                Switch(
                    checked = lyricsEnabled,
                    onCheckedChange = {
                        lyricsEnabled = it
                        SettingsManager.setLyricsEnabled(context, it)
                        container.refreshLyrics()
                    }
                )
            }
            SettingLink("Music apps", "Choose players and playback behavior") { onNavigate(AppRoute.Apps) }
            SettingLink("Lyric sources", "LRCLIB and optional providers") { onNavigate(AppRoute.Providers) }
            SettingLink("Search cleanup", "Fine-tune song and artist queries") { onNavigate(AppRoute.Cleanup) }
            SettingLink("Car display", "Artwork and media presentation") { onNavigate(AppRoute.CarDisplay) }
        }
        SettingsGroup("YOUR LIBRARY") {
            SettingLink("Backup & restore", "Keep your saved lyrics and corrections") { onNavigate(AppRoute.Backup) }
            SettingLink("Diagnostics", "Review and export troubleshooting details") { onNavigate(AppRoute.Diagnostics) }
        }
        SettingsGroup("CAR KARAOKE") {
            SettingLink("Get updates with Obtainium", "Install releases directly from GitHub") { openObtainium(context) }
            Text("Version ${BuildConfig.VERSION_NAME} • Built on AAMediaMate", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 1.5.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(
                Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = content
            )
        }
    }
}

@Composable
internal fun SettingLink(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight, null)
    }
}

private fun openObtainium(context: Context) {
    val configuration = context.assets.open("obtainium.json").bufferedReader().use { it.readText() }
    val deepLink = "obtainium://app/" + Uri.encode(configuration)
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(deepLink)))
    } catch (_: ActivityNotFoundException) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://apps.obtainium.imranr.dev/redirect?r=" + Uri.encode(deepLink)))
        )
    }
}
