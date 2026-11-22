@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.milankablar.carkaraoke.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.milankablar.carkaraoke.AppContainer
import io.github.milankablar.carkaraoke.MediaInformationRetriever

@Composable
internal fun PlayerSelectionScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenOptions: () -> Unit
) {
    val context = LocalContext.current
    val state by container.coordinator.state.collectAsState()
    val controllers = remember(state.info) { container.activePlayers() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your music apps") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Done") } }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Choose the player to follow", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Both your phone and car will follow this selection.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = {
                    container.useAutomaticPlayer()
                    onBack()
                }
            ) { Text("Automatically follow playing music") }
            controllers.forEach { controller ->
                val info = MediaInformationRetriever.buildMediaInfoFromController(context, controller)
                Surface(
                    onClick = {
                        container.selectPlayer(controller)
                        onBack()
                    },
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    ListItem(
                        headlineContent = { Text(info?.appName ?: controller.packageName) },
                        supportingContent = { Text(info?.title ?: "Ready") },
                        leadingContent = { Icon(Icons.Default.MusicNote, null) },
                        trailingContent = {
                            if (state.info?.appPackageName == controller.packageName) {
                                Icon(Icons.Default.Check, "Selected")
                            }
                        }
                    )
                }
            }
            if (controllers.isEmpty()) Text("Start playback in a music app to see it here.")
            SettingLink("Player options", "Lyrics, controls and button preferences", onOpenOptions)
        }
    }
}
