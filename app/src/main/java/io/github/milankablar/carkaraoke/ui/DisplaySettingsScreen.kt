package io.github.milankablar.carkaraoke.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.milankablar.carkaraoke.MediaBridgeSessionManager
import io.github.milankablar.carkaraoke.R
import io.github.milankablar.carkaraoke.SettingsManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisplaySettingsScreen(onBack: () -> Unit) {
    BackHandler {
        onBack()
    }

    val context = LocalContext.current
    var combineAppIconAndAlbumArt by remember { mutableStateOf(SettingsManager.getCombineAppIconAndAlbumArt(context)) }
    var showAlbumName by remember { mutableStateOf(SettingsManager.getShowAlbumName(context)) }
    var showNextLyricLine by remember { mutableStateOf(SettingsManager.getShowNextLyricLine(context)) }
    var showSourceApp by remember { mutableStateOf(SettingsManager.getShowSourceApp(context)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.display_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Combine App Icon & Album Art Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = R.string.combine_app_icon_and_album_art_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(id = R.string.combine_app_icon_and_album_art_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = combineAppIconAndAlbumArt,
                    onCheckedChange = {
                        combineAppIconAndAlbumArt = it
                        SettingsManager.setCombineAppIconAndAlbumArt(context, it)
                        MediaBridgeSessionManager.refreshCurrentSession(forceLyricsResync = true)
                    }
                )
            }

            HorizontalDivider()

            // Show Album Name Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = R.string.show_album_name_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(id = R.string.show_album_name_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = showAlbumName,
                    onCheckedChange = {
                        showAlbumName = it
                        SettingsManager.setShowAlbumName(context, it)
                        MediaBridgeSessionManager.refreshCurrentSession(forceLyricsResync = true)
                    }
                )
            }

            HorizontalDivider()

            // Show Source App Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = R.string.show_source_app_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(id = R.string.show_source_app_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = showSourceApp,
                    onCheckedChange = {
                        showSourceApp = it
                        SettingsManager.setShowSourceApp(context, it)
                        MediaBridgeSessionManager.refreshCurrentSession(forceLyricsResync = true)
                    }
                )
            }

            HorizontalDivider()

            // Show Next Lyric Line Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = R.string.show_next_lyric_line_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(id = R.string.show_next_lyric_line_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = showNextLyricLine,
                    onCheckedChange = {
                        showNextLyricLine = it
                        SettingsManager.setShowNextLyricLine(context, it)
                        MediaBridgeSessionManager.refreshCurrentSession(forceLyricsResync = true)
                    }
                )
            }
        }
    }
}
