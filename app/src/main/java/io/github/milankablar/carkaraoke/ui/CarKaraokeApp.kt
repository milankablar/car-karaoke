@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.milankablar.carkaraoke.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.milankablar.carkaraoke.AppContainer
import io.github.milankablar.carkaraoke.SettingsManager
import io.github.milankablar.carkaraoke.TransportCommand
import io.github.milankablar.carkaraoke.ui.theme.MediaBridgeTheme

sealed interface AppRoute {
    data object Home : AppRoute
    data object Providers : AppRoute
    data object Apps : AppRoute
    data object AppOptions : AppRoute
    data object Backup : AppRoute
    data object Diagnostics : AppRoute
    data object Cleanup : AppRoute
    data object CarDisplay : AppRoute
    data object Artwork : AppRoute
    data object Correction : AppRoute
    data class Editor(val key: String) : AppRoute
    data class ManualSearch(val key: String) : AppRoute

    companion object {
        val Saver: Saver<AppRoute, List<String>> = Saver(
            save = { route ->
                when (route) {
                    Home -> listOf("home")
                    Providers -> listOf("providers")
                    Apps -> listOf("apps")
                    AppOptions -> listOf("app-options")
                    Backup -> listOf("backup")
                    Diagnostics -> listOf("diagnostics")
                    Cleanup -> listOf("cleanup")
                    CarDisplay -> listOf("display")
                    Artwork -> listOf("artwork")
                    Correction -> listOf("correction")
                    is Editor -> listOf("editor", route.key)
                    is ManualSearch -> listOf("search", route.key)
                }
            },
            restore = { saved ->
                when (saved.firstOrNull()) {
                    "providers" -> Providers
                    "apps" -> Apps
                    "app-options" -> AppOptions
                    "backup" -> Backup
                    "diagnostics" -> Diagnostics
                    "cleanup" -> Cleanup
                    "display" -> CarDisplay
                    "artwork" -> Artwork
                    "correction" -> Correction
                    "editor" -> Editor(saved.getOrElse(1) { "" })
                    "search" -> ManualSearch(saved.getOrElse(1) { "" })
                    else -> Home
                }
            }
        )
    }
}

@Composable
fun CarKaraokeApp(container: AppContainer) {
    val context = LocalContext.current
    var appearance by rememberSaveable { mutableStateOf(SettingsManager.getAppearanceTheme(context)) }
    val systemDark = isSystemInDarkTheme()
    val darkTheme = if (appearance == "System") systemDark else appearance != "Light"

    MediaBridgeTheme(darkTheme = darkTheme, dynamicColor = false, amoled = appearance == "Black") {
        val state by container.coordinator.state.collectAsState()
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var route by rememberSaveable(stateSaver = AppRoute.Saver) { mutableStateOf<AppRoute>(AppRoute.Home) }
        val goHome = { route = AppRoute.Home }

        BackHandler(route != AppRoute.Home) { route = AppRoute.Home }

        when (val current = route) {
            AppRoute.Providers -> LyricsProvidersScreen(goHome)
            AppRoute.Apps -> PlayerSelectionScreen(container, goHome) { route = AppRoute.AppOptions }
            AppRoute.AppOptions -> BridgedAppsScreen(goHome)
            AppRoute.Backup -> BackupRestoreScreen(goHome)
            AppRoute.Diagnostics -> DiagnosticLogsScreen(goHome)
            AppRoute.Cleanup -> LyricsCleanupRulesScreen(goHome)
            AppRoute.CarDisplay -> CarDisplayScreen(container, goHome) { route = AppRoute.Artwork }
            AppRoute.Artwork -> DisplaySettingsScreen(goHome)
            is AppRoute.Editor -> LyricsEditorScreen(
                lyricsKey = current.key,
                onBack = goHome,
                onDeleted = goHome,
                onManualSearch = { route = AppRoute.ManualSearch(it) }
            )
            is AppRoute.ManualSearch -> ManualLyricsSearchScreen(current.key) {
                route = AppRoute.Editor(current.key)
            }
            AppRoute.Correction -> CorrectionScreen(container, state, goHome)
            AppRoute.Home -> Scaffold(
                bottomBar = {
                    Column {
                        val activeInfo = state.info
                        if (tab != 0 && activeInfo != null) {
                            Surface(onClick = { tab = 0 }, color = MaterialTheme.colorScheme.secondaryContainer) {
                                Row(
                                    Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.MusicNote, null)
                                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                        Text(activeInfo.title, maxLines = 1, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            state.resolution.document.lines.getOrNull(state.currentIndex)?.text
                                                ?: activeInfo.artist,
                                            maxLines = 1,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            container.sendTransport(
                                                if (activeInfo.isPlaying) TransportCommand.PAUSE else TransportCommand.PLAY
                                            )
                                        }
                                    ) {
                                        Icon(
                                            if (activeInfo.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            "Play or pause"
                                        )
                                    }
                                }
                            }
                        }
                        NavigationBar {
                            listOf(
                                "Karaoke" to Icons.Default.Mic,
                                "Library" to Icons.Default.LibraryMusic,
                                "Settings" to Icons.Default.Settings
                            ).forEachIndexed { index, (name, icon) ->
                                NavigationBarItem(
                                    selected = tab == index,
                                    onClick = { tab = index },
                                    icon = { Icon(icon, null) },
                                    label = { Text(name) }
                                )
                            }
                        }
                    }
                }
            ) { padding ->
                Box(Modifier.padding(padding).clipToBounds()) {
                    when (tab) {
                        0 -> KaraokeScreen(
                            container = container,
                            state = state,
                            onCorrect = { route = AppRoute.Correction },
                            onSelectApps = { route = AppRoute.Apps }
                        )
                        1 -> LyricsManagerScreen(
                            onBack = { tab = 0 },
                            onOpenEditor = { route = AppRoute.Editor(it) },
                            onNavigateToBridgedApps = { route = AppRoute.Apps }
                        )
                        2 -> SettingsHomeScreen(
                            container = container,
                            appearance = appearance,
                            onAppearanceChange = {
                                appearance = it
                                SettingsManager.setAppearanceTheme(context, it)
                            },
                            onNavigate = { route = it }
                        )
                    }
                }
            }
        }
    }
}
