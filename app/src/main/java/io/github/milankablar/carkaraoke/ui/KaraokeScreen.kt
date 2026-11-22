package io.github.milankablar.carkaraoke.ui

import android.content.res.Configuration
import android.media.session.PlaybackState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.milankablar.carkaraoke.AppContainer
import io.github.milankablar.carkaraoke.SettingsManager
import io.github.milankablar.carkaraoke.TransportCommand
import io.github.milankablar.carkaraoke.hasNotificationAccess
import io.github.milankablar.carkaraoke.karaoke.KaraokeState
import io.github.milankablar.carkaraoke.karaoke.LyricsStatus
import io.github.milankablar.carkaraoke.openNotificationAccessSettings

@Composable
internal fun KaraokeScreen(
    container: AppContainer,
    state: KaraokeState,
    onCorrect: () -> Unit,
    onSelectApps: () -> Unit
) {
    val context = LocalContext.current
    if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        LandscapeKaraokeScreen(container, state, onCorrect)
        return
    }
    var follow by rememberSaveable { mutableStateOf(true) }
    val textSize = SettingsManager.getLyricTextSizeSp(context)
    val reducedMotion = SettingsManager.getReducedMotion(context)
    val keepScreenOn = SettingsManager.getKeepScreenOn(context)
    val view = LocalView.current
    DisposableEffect(view, keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = false }
    }
    val list = rememberLazyListState()
    val dragging by list.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(dragging) { if (dragging) follow = false }
    LaunchedEffect(state.currentIndex, follow) {
        if (follow && state.currentIndex >= 0) {
            if (reducedMotion) {
                list.scrollToItem((state.currentIndex - 1).coerceAtLeast(0))
            } else {
                list.animateScrollToItem((state.currentIndex - 1).coerceAtLeast(0))
            }
        }
    }
    LaunchedEffect(state.track?.key) { follow = true }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "CAR KARAOKE",
                    style = MaterialTheme.typography.labelMedium,
                    letterSpacing = 2.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Your music. In the moment.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onSelectApps) { Icon(Icons.Default.Devices, "Choose music app") }
        }
        if (!hasNotificationAccess(context) && state.info == null) {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text("Connect your music", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Allow music notification access, then play a song in your favorite music app.",
                        Modifier.padding(vertical = 12.dp)
                    )
                    Button(onClick = { openNotificationAccessSettings(context) }) { Text("Enable music access") }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(68.dp)
            ) {
                val art = state.info?.albumArt
                if (art != null) {
                    Image(art.asImageBitmap(), null, contentScale = ContentScale.Crop)
                } else {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.GraphicEq, null, Modifier.size(32.dp)) }
                }
            }
            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                Text(state.info?.title ?: "Ready when you are", style = MaterialTheme.typography.titleLarge, maxLines = 2)
                Text(
                    state.info?.artist ?: "Play a song to begin",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Text(
                    state.info?.appName.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        if (state.resolution.message.isNotBlank()) {
            Text(
                state.resolution.message,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        val doc = state.resolution.document
        if (doc.synced) {
            LazyColumn(
                state = list,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = PaddingValues(vertical = 32.dp)
            ) {
                itemsIndexed(doc.lines) { index, line ->
                    val active = index == state.currentIndex
                    val color by animateColorAsState(
                        if (active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = if (index < state.currentIndex) .42f else .65f)
                        },
                        label = "lyric"
                    )
                    val lyric = buildAnnotatedString {
                        if (line.words.isEmpty() || !active) {
                            append(line.text.ifBlank { "♪" })
                        } else {
                            line.words.forEach { word ->
                                val highlighted = word.timeMs <= state.positionMs - state.resolution.offsetMs
                                withStyle(
                                    SpanStyle(
                                        color = if (highlighted) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                ) { append(word.text) }
                            }
                        }
                    }
                    Text(
                        lyric,
                        Modifier.fillMaxWidth().clickable { follow = false },
                        fontSize = textSize.sp,
                        lineHeight = (textSize * 1.35f).sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        color = color
                    )
                }
            }
        } else if (doc.plainText.isNotBlank()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Text(
                    "Plain lyrics • timing unavailable",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    doc.plainText,
                    Modifier.padding(vertical = 20.dp),
                    fontSize = textSize.sp,
                    lineHeight = (textSize * 1.4f).sp
                )
            }
        } else {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (state.resolution.status == LyricsStatus.LOADING) {
                        CircularProgressIndicator(Modifier.size(28.dp))
                    } else {
                        Icon(Icons.Default.Mic, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    Text(
                        when (state.resolution.status) {
                            LyricsStatus.LOADING -> "Finding the right lyrics…"
                            LyricsStatus.INSTRUMENTAL -> "Just the music"
                            LyricsStatus.NEEDS_SELECTION -> "A few possible matches"
                            LyricsStatus.NOT_FOUND -> "No lyrics found yet"
                            LyricsStatus.ERROR -> "Couldn’t load lyrics"
                            LyricsStatus.DISABLED -> "Lyrics are turned off"
                            else -> "The words will appear here"
                        },
                        Modifier.padding(top = 16.dp),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        state.resolution.message.ifBlank { "Phone and car follow the same song." },
                        Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (state.track != null) TextButton(onClick = onCorrect) { Text("Find or import lyrics") }
                }
            }
        }
        val activeInfo = state.info
        if (activeInfo != null) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { follow = !follow }) { Text(if (follow) "Following lyrics" else "Return to live") }
                TextButton(onClick = onCorrect) { Text("Correct lyrics") }
            }
            val duration = activeInfo.duration
            if (duration > 0) {
                var scrubbing by remember(state.track?.key) { mutableStateOf(false) }
                var seekPosition by remember(state.track?.key) { mutableFloatStateOf(0f) }
                Slider(
                    value = if (scrubbing) seekPosition else state.positionMs.toFloat().coerceIn(0f, duration.toFloat()),
                    onValueChange = {
                        scrubbing = true
                        seekPosition = it
                    },
                    valueRange = 0f..duration.toFloat(),
                    enabled = activeInfo.sourceActions?.let { it and PlaybackState.ACTION_SEEK_TO != 0L } ?: false,
                    onValueChangeFinished = {
                        container.seekTo(seekPosition.toLong())
                        scrubbing = false
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(timeLabel(state.positionMs), style = MaterialTheme.typography.labelSmall)
                Text(timeLabel(duration), style = MaterialTheme.typography.labelSmall)
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { container.sendTransport(TransportCommand.PREVIOUS) }) {
                    Icon(Icons.Default.SkipPrevious, "Previous track")
                }
                FilledIconButton(
                    onClick = {
                        container.sendTransport(
                            if (activeInfo.isPlaying) TransportCommand.PAUSE else TransportCommand.PLAY
                        )
                    },
                    modifier = Modifier.size(60.dp)
                ) {
                    Icon(
                        if (activeInfo.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        "Play or pause",
                        Modifier.size(32.dp)
                    )
                }
                IconButton(onClick = { container.sendTransport(TransportCommand.NEXT) }) {
                    Icon(Icons.Default.SkipNext, "Next track")
                }
            }
        }
    }
}

@Composable
private fun LandscapeKaraokeScreen(
    container: AppContainer,
    state: KaraokeState,
    onCorrect: () -> Unit
) {
    val context = LocalContext.current
    val list = rememberLazyListState()
    var follow by remember { mutableStateOf(true) }
    val dragging by list.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(dragging) { if (dragging) follow = false }
    LaunchedEffect(state.currentIndex, follow) {
        if (follow && state.currentIndex >= 0) list.scrollToItem(state.currentIndex)
    }
    Row(Modifier.fillMaxSize().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        Column(
            Modifier.weight(.4f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "CAR KARAOKE",
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Text(state.info?.title ?: "Ready when you are", style = MaterialTheme.typography.headlineSmall)
            Text(state.info?.artist ?: "Play a song to begin", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!hasNotificationAccess(context)) {
                TextButton(onClick = { openNotificationAccessSettings(context) }) { Text("Enable music access") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { container.sendTransport(TransportCommand.PREVIOUS) }) {
                    Icon(Icons.Default.SkipPrevious, "Previous track")
                }
                FilledIconButton(
                    onClick = {
                        container.sendTransport(
                            if (state.info?.isPlaying == true) TransportCommand.PAUSE else TransportCommand.PLAY
                        )
                    }
                ) {
                    Icon(
                        if (state.info?.isPlaying == true) Icons.Default.Pause else Icons.Default.PlayArrow,
                        "Play or pause"
                    )
                }
                IconButton(onClick = { container.sendTransport(TransportCommand.NEXT) }) {
                    Icon(Icons.Default.SkipNext, "Next track")
                }
            }
            TextButton(onClick = onCorrect, enabled = state.track != null) { Text("Correct lyrics") }
            if (!follow) TextButton(onClick = { follow = true }) { Text("Return to live") }
        }
        LazyColumn(state = list, modifier = Modifier.weight(.6f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if (state.resolution.document.synced) {
                itemsIndexed(state.resolution.document.lines) { index, line ->
                    Text(
                        line.text.ifBlank { "♪" },
                        fontSize = 26.sp,
                        lineHeight = 34.sp,
                        fontWeight = if (index == state.currentIndex) FontWeight.Bold else FontWeight.Normal,
                        color = if (index == state.currentIndex) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            } else {
                item {
                    Text(
                        state.resolution.document.plainText.ifBlank {
                            state.resolution.status.name.lowercase().replace('_', ' ')
                        },
                        fontSize = 24.sp
                    )
                }
            }
        }
    }
}

internal fun timeLabel(ms: Long): String = "%d:%02d".format(ms.coerceAtLeast(0) / 60000, ms.coerceAtLeast(0) / 1000 % 60)
