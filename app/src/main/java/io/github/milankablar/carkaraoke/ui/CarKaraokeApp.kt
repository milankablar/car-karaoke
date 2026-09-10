@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package io.github.milankablar.carkaraoke.ui

import android.content.Intent
import android.net.Uri
import android.media.session.PlaybackState
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.milankablar.carkaraoke.*
import io.github.milankablar.carkaraoke.karaoke.*
import io.github.milankablar.carkaraoke.ui.theme.MediaBridgeTheme
import kotlinx.coroutines.*

@Composable
fun CarKaraokeApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("presentation", 0) }
    var appearance by rememberSaveable { mutableStateOf(prefs.getString("theme", "System")!!) }
    val systemDark = isSystemInDarkTheme()
    MediaBridgeTheme(darkTheme = if (appearance == "System") systemDark else appearance != "Light", dynamicColor = false, amoled = appearance == "Black") {
        val state by KaraokeRuntime.coordinator.state.collectAsState()
        var tab by rememberSaveable { mutableStateOf(0) }
        var page by rememberSaveable { mutableStateOf("") }
        var editor by rememberSaveable { mutableStateOf("") }
        val back = { page = "" }
        BackHandler(page.isNotEmpty()) { page = "" }
        when (page) {
            "providers" -> LyricsProvidersScreen(back)
            "apps" -> PlayerSelectionScreen(back, { page = "app-options" })
            "app-options" -> BridgedAppsScreen(back)
            "backup" -> BackupRestoreScreen(back)
            "diagnostics" -> DiagnosticLogsScreen(back)
            "cleanup" -> LyricsCleanupRulesScreen(back)
            "display" -> CarDisplayScreen(back, { page = "artwork" })
            "artwork" -> DisplaySettingsScreen(back)
            "editor" -> LyricsEditorScreen(editor, back, back, { editor = it; page = "search" })
            "search" -> ManualLyricsSearchScreen(editor, { page = "editor" })
            "correction" -> CorrectionScreen(state, back)
            else -> Scaffold(bottomBar = {
                Column {
                    if (tab != 0 && state.info != null) Surface(onClick = { tab = 0 }, color = MaterialTheme.colorScheme.secondaryContainer) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.MusicNote, null)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(state.info!!.title, maxLines = 1, fontWeight = FontWeight.SemiBold)
                                Text(state.resolution.document.lines.getOrNull(state.currentIndex)?.text ?: state.info!!.artist, maxLines = 1, style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { transport(context, if (state.info!!.isPlaying) "pause" else "play") }) { Icon(if (state.info!!.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play or pause") }
                        }
                    }
                    NavigationBar {
                        listOf("Karaoke" to Icons.Default.Mic, "Library" to Icons.Default.LibraryMusic, "Settings" to Icons.Default.Settings).forEachIndexed { index, (name, icon) ->
                            NavigationBarItem(selected = tab == index, onClick = { tab = index }, icon = { Icon(icon, null) }, label = { Text(name) })
                        }
                    }
                }
            }) { padding ->
                Box(Modifier.padding(padding).clipToBounds()) {
                    when (tab) {
                        0 -> KaraokeScreen(state, { page = "correction" }, { page = "apps" })
                        1 -> LyricsManagerScreen({ tab = 0 }, { editor = it; page = "editor" }, { page = "apps" })
                        2 -> CleanSettings(appearance, { appearance = it; prefs.edit().putString("theme", it).apply() }, { page = it })
                    }
                }
            }
        }
    }
}

@Composable
private fun KaraokeScreen(state: KaraokeState, correct: () -> Unit, apps: () -> Unit) {
    val context = LocalContext.current
    if (androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) { LandscapeKaraoke(state, correct); return }
    var follow by rememberSaveable { mutableStateOf(true) }
    val prefs = remember { context.getSharedPreferences("presentation", 0) }
    val textSize = prefs.getFloat("lyricSize", 28f)
    val reducedMotion = prefs.getBoolean("reducedMotion", false)
    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(view) { view.keepScreenOn = prefs.getBoolean("keepScreenOn", false); onDispose { view.keepScreenOn = false } }
    val list = rememberLazyListState()
    val dragging by list.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(dragging) { if (dragging) follow = false }
    LaunchedEffect(state.currentIndex, follow) {
        if (follow && state.currentIndex >= 0) {
            if (reducedMotion) list.scrollToItem((state.currentIndex - 1).coerceAtLeast(0))
            else list.animateScrollToItem((state.currentIndex - 1).coerceAtLeast(0))
        }
    }
    LaunchedEffect(state.track?.key) { follow = true }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("CAR KARAOKE", style = MaterialTheme.typography.labelMedium, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.primary)
                Text("Your music. In the moment.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = apps) { Icon(Icons.Default.Devices, "Choose music app") }
        }
        if (!hasNotificationAccess(context) && state.info == null) {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text("Connect your music", style = MaterialTheme.typography.titleLarge)
                    Text("Allow music notification access, then play a song in your favorite music app.", Modifier.padding(vertical = 12.dp))
                    Button(onClick = { openNotificationAccessSettings(context) }) { Text("Enable music access") }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(68.dp)) {
                val art = state.info?.albumArt
                if (art != null) Image(art.asImageBitmap(), null, contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                else Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.GraphicEq, null, Modifier.size(32.dp)) }
            }
            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                Text(state.info?.title ?: "Ready when you are", style = MaterialTheme.typography.titleLarge, maxLines = 2)
                Text(state.info?.artist ?: "Play a song to begin", color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Text(state.info?.appName.orEmpty(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (state.resolution.message.isNotBlank()) Text(state.resolution.message, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        val doc = state.resolution.document
        if (doc.synced) {
            LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp), contentPadding = PaddingValues(vertical = 32.dp)) {
                itemsIndexed(doc.lines) { index, line ->
                    val active = index == state.currentIndex
                    val color by animateColorAsState(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = if (index < state.currentIndex) .42f else .65f), label = "lyric")
                    val lyric = buildAnnotatedString {
                        if (line.words.isEmpty() || !active) append(line.text.ifBlank { "♪" })
                        else line.words.forEach { word -> withStyle(SpanStyle(color = if (word.timeMs <= state.positionMs - state.resolution.offsetMs) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)) { append(word.text) } }
                    }
                    Text(lyric, Modifier.fillMaxWidth().clickable { follow = false }, fontSize = textSize.sp, lineHeight = (textSize * 1.35f).sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, color = color)
                }
            }
        } else if (doc.plainText.isNotBlank()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Text("Plain lyrics • timing unavailable", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(doc.plainText, Modifier.padding(vertical = 20.dp), fontSize = textSize.sp, lineHeight = (textSize * 1.4f).sp)
            }
        } else Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (state.resolution.status == LyricsStatus.LOADING) CircularProgressIndicator(Modifier.size(28.dp)) else Icon(Icons.Default.Mic, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
                Text(when (state.resolution.status) {
                    LyricsStatus.LOADING -> "Finding the right lyrics…"
                    LyricsStatus.INSTRUMENTAL -> "Just the music"
                    LyricsStatus.NEEDS_SELECTION -> "A few possible matches"
                    LyricsStatus.NOT_FOUND -> "No lyrics found yet"
                    LyricsStatus.ERROR -> "Couldn’t load lyrics"
                    LyricsStatus.DISABLED -> "Lyrics are turned off"
                    else -> "The words will appear here"
                }, Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleMedium)
                Text(state.resolution.message.ifBlank { "Phone and car follow the same song." }, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
                if (state.track != null) TextButton(onClick = correct) { Text("Find or import lyrics") }
            }
        }
        if (state.info != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { follow = !follow }) { Text(if (follow) "Following lyrics" else "Return to live") }
                TextButton(onClick = correct) { Text("Correct lyrics") }
            }
            val duration = state.info!!.duration
            if (duration > 0) {
                var scrubbing by remember(state.track?.key) { mutableStateOf(false) }
                var seekPosition by remember(state.track?.key) { mutableFloatStateOf(0f) }
                Slider(value = if (scrubbing) seekPosition else state.positionMs.toFloat().coerceIn(0f, duration.toFloat()),
                    onValueChange = { scrubbing = true; seekPosition = it }, valueRange = 0f..duration.toFloat(),
                    enabled = state.info!!.sourceActions?.let { it and PlaybackState.ACTION_SEEK_TO != 0L } ?: false,
                    onValueChangeFinished = {
                        MediaControllerManager.getActiveController(context)?.transportControls?.seekTo(seekPosition.toLong())
                        scrubbing = false
                        MediaBridgeSessionManager.requestRefresh("Phone seek", 250)
                    }, modifier = Modifier.fillMaxWidth())
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(timeLabel(state.positionMs), style = MaterialTheme.typography.labelSmall)
                Text(timeLabel(duration), style = MaterialTheme.typography.labelSmall)
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { transport(context, "previous") }) { Icon(Icons.Default.SkipPrevious, "Previous track") }
                FilledIconButton(onClick = { transport(context, if (state.info!!.isPlaying) "pause" else "play") }, modifier = Modifier.size(60.dp)) { Icon(if (state.info!!.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play or pause", Modifier.size(32.dp)) }
                IconButton(onClick = { transport(context, "next") }) { Icon(Icons.Default.SkipNext, "Next track") }
            }
        }
    }
}

@Composable
private fun CleanSettings(appearance: String, setAppearance: (String) -> Unit, navigate: (String) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("presentation", 0) }
    var size by remember { mutableFloatStateOf(prefs.getFloat("lyricSize", 28f)) }
    var reduced by remember { mutableStateOf(prefs.getBoolean("reducedMotion", false)) }
    var keepOn by remember { mutableStateOf(prefs.getBoolean("keepScreenOn", false)) }
    var lyricsEnabled by remember { mutableStateOf(SettingsManager.getLyricsEnabled(context)) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Make it yours", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("A little tuning. A better sing-along.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingsGroup("APPEARANCE") {
            Text("Theme", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("System", "Light", "Dark", "Black").forEach { FilterChip(appearance == it, { setAppearance(it) }, { Text(it) }) } }
            Text("Lyric size", style = MaterialTheme.typography.titleMedium)
            Slider(size, { size = it }, valueRange = 22f..40f, steps = 8, onValueChangeFinished = { prefs.edit().putFloat("lyricSize", size).apply() })
            Text("The next line is yours", fontSize = size.sp, color = MaterialTheme.colorScheme.primary)
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Reduce motion", Modifier.weight(1f)); Switch(reduced, { reduced = it; prefs.edit().putBoolean("reducedMotion", it).apply() }) }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Keep lyrics screen awake", Modifier.weight(1f)); Switch(keepOn, { keepOn = it; prefs.edit().putBoolean("keepScreenOn", it).apply() }) }
        }
        SettingsGroup("MUSIC & LYRICS") {
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Show lyrics", Modifier.weight(1f)); Switch(lyricsEnabled, { lyricsEnabled = it; SettingsManager.setLyricsEnabled(context, it); KaraokeRuntime.coordinator.refresh() }) }
            SettingLink("Music apps", "Choose players and playback behavior") { navigate("apps") }
            SettingLink("Lyric sources", "LRCLIB and optional providers") { navigate("providers") }
            SettingLink("Search cleanup", "Fine-tune song and artist queries") { navigate("cleanup") }
            SettingLink("Car display", "Artwork and media presentation") { navigate("display") }
        }
        SettingsGroup("YOUR LIBRARY") {
            SettingLink("Backup & restore", "Keep your saved lyrics and corrections") { navigate("backup") }
            SettingLink("Diagnostics", "Review and export troubleshooting details") { navigate("diagnostics") }
        }
        SettingsGroup("CAR KARAOKE") {
            SettingLink("Get updates with Obtainium", "Install releases directly from GitHub") { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/milankablar/car-karaoke#install-and-updates"))) }
            Text("Version ${BuildConfig.VERSION_NAME} • Built on AAMediaMate", style = MaterialTheme.typography.bodySmall)
        }
    }
}
@Composable private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.5.sp, color = MaterialTheme.colorScheme.primary)
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) { Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content) }
    }
}
@Composable private fun SettingLink(title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Icon(Icons.Default.ChevronRight, null)
    }
}

@Composable
private fun CorrectionScreen(state: KaraokeState, back: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var choices by remember(state.track?.key) { mutableStateOf(state.resolution.candidates) }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var preview by remember(state.track?.key) { mutableStateOf<LyricCandidate?>(null) }
    var importTrack by remember { mutableStateOf<TrackIdentity?>(null) }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val track = importTrack
        if (uri != null && track != null) scope.launch {
            try {
                val content = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    while (true) { val count = input.read(buffer); if (count < 0) break; require(output.size() + count <= 2 * 1024 * 1024) { "Lyrics must be smaller than 2 MB" }; output.write(buffer, 0, count) }
                    val bytes = output.toByteArray()
                    require(bytes.size <= 2 * 1024 * 1024) { "Lyrics must be smaller than 2 MB" }; bytes.toString(Charsets.UTF_8)
                } }
                if (track.key != KaraokeRuntime.coordinator.state.value.track?.key) { error = "Song changed. Select the original song and import again."; return@launch }
                preview = LyricCandidate("import", "Imported LRC", track.title, track.artist, track.album, track.durationMs, content, verifiedIdentity = false)
            } catch (e: Exception) { error = e.message ?: "Could not import lyrics" }
        }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Tune this song") }, navigationIcon = { TextButton(onClick = back) { Text("Done") } }) }) { padding ->
        Column(Modifier.padding(padding).padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(state.info?.title ?: "No song selected", style = MaterialTheme.typography.headlineSmall)
            Text("A selection is saved for this recording and shared with your car.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            SettingsGroup("TIMING") {
                Text("Offset: ${state.resolution.offsetMs} ms", style = MaterialTheme.typography.titleMedium)
                Text("Positive values show lyrics later.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { KaraokeRuntime.offset(-250) }) { Text("Earlier −250") }
                    OutlinedButton(onClick = { KaraokeRuntime.offset(250) }) { Text("Later +250") }
                }
            }
            Button(enabled = !searching && state.track != null, onClick = {
                scope.launch {
                    searching = true; error = ""
                    try {
                        val requestTrack = state.track!!
                        val found = ConfiguredCandidates(context).search(requestTrack)
                        if (requestTrack.key == KaraokeRuntime.coordinator.state.value.track?.key) choices = found
                    }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = "Search unavailable. Please retry." }
                    finally { searching = false }
                }
            }) { Text(if (searching) "Searching…" else "Find other matches") }
            if (!state.resolution.manual) TextButton(onClick = { KaraokeRuntime.coordinator.refresh() }) { Text("Retry automatic lookup") }
            OutlinedButton(enabled = state.track != null, onClick = { importTrack = state.track; import.launch(arrayOf("text/*", "application/octet-stream")) }) { Text("Import LRC or text file") }
            if (state.resolution.manual) TextButton(onClick = { KaraokeRuntime.resetSelection() }) { Text("Reset to automatic lyrics") }
            if (state.resolution.message.isNotBlank()) Text(state.resolution.message, style = MaterialTheme.typography.bodySmall)
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            choices.forEach { candidate ->
                Surface(onClick = { preview = candidate }, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.fillMaxWidth().padding(20.dp)) { Text(candidate.title, fontWeight = FontWeight.Bold); Text("${candidate.artist} • ${candidate.album}"); Text("${candidate.provider} • ${timeLabel(candidate.durationMs)}", style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
    }
    preview?.let { candidate -> AlertDialog(onDismissRequest = { preview = null }, title = { Text(candidate.title) }, text = { Text(candidate.content.take(1500).ifBlank { "Instrumental recording" }, Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) }, confirmButton = { TextButton(onClick = { state.track?.let { KaraokeRuntime.choose(it, candidate) }; preview = null; back() }) { Text("Use these lyrics") } }, dismissButton = { TextButton(onClick = { preview = null }) { Text("Cancel") } }) }
}
private fun timeLabel(ms: Long): String = "%d:%02d".format(ms.coerceAtLeast(0) / 60000, ms.coerceAtLeast(0) / 1000 % 60)
private fun transport(context: android.content.Context, command: String) {
    val controller = MediaControllerManager.getActiveController(context) ?: return
    val controls = controller.transportControls
    when (command) { "play" -> controls.play(); "pause" -> controls.pause(); "next" -> controls.skipToNext(); "previous" -> controls.skipToPrevious() }
    MediaBridgeSessionManager.requestRefresh("Phone transport", 250)
}

@Composable
private fun PlayerSelectionScreen(back: () -> Unit, options: () -> Unit) {
    val context = LocalContext.current
    val state by KaraokeRuntime.coordinator.state.collectAsState()
    val controllers = remember(state.info) { MediaControllerManager.getAllControllers(context) }
    Scaffold(topBar = { TopAppBar(title = { Text("Your music apps") }, navigationIcon = { TextButton(onClick = back) { Text("Done") } }) }) { padding ->
        Column(Modifier.padding(padding).padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Choose the player to follow", style = MaterialTheme.typography.headlineSmall)
            Text("Both your phone and car will follow this selection.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { MediaControllerManager.useAutomaticSelection(); MediaBridgeSessionManager.refreshCurrentSession(); back() }) { Text("Automatically follow playing music") }
            controllers.forEach { controller ->
                val info = MediaInformationRetriever.buildMediaInfoFromController(context, controller)
                Surface(onClick = { MediaControllerManager.select(controller); MediaBridgeSessionManager.refreshCurrentSession(); back() }, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    ListItem(headlineContent = { Text(info?.appName ?: controller.packageName) }, supportingContent = { Text(info?.title ?: "Ready") }, leadingContent = { Icon(Icons.Default.MusicNote, null) }, trailingContent = { if (state.info?.appPackageName == controller.packageName) Icon(Icons.Default.Check, "Selected") })
                }
            }
            if (controllers.isEmpty()) Text("Start playback in a music app to see it here.")
            SettingLink("Player options", "Lyrics, controls and button preferences", options)
        }
    }
}
@Composable
private fun CarDisplayScreen(back: () -> Unit, artwork: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("presentation", 0) }
    var legacy by remember { mutableStateOf(prefs.getBoolean("carLegacyTitle", false)) }
    var offset by remember { mutableLongStateOf(prefs.getLong("carOffset", 0)) }
    Scaffold(topBar = { TopAppBar(title = { Text("Android Auto") }, navigationIcon = { TextButton(onClick = back) { Text("Done") } }) }) { padding ->
        Column(Modifier.padding(padding).padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("The same song. A second screen.", style = MaterialTheme.typography.headlineSmall)
            Text("Open Live lyrics in Car Karaoke on Android Auto for the moving three-line view. Your car controls its layout and refresh behavior.")
            SettingsGroup("DISPLAY") {
                Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Lyrics in track title"); Text("Compatibility mode for displays that hide subtitles", style = MaterialTheme.typography.bodySmall) }; Switch(legacy, { legacy = it; prefs.edit().putBoolean("carLegacyTitle", it).apply(); MediaBridgeSessionManager.refreshCurrentSession(true) }) }
                SettingLink("Artwork & media details", "Album art, source app and text", artwork)
            }
            SettingsGroup("CAR TIMING") {
                Text("Display delay: $offset ms")
                Text("Affects the car display only. Positive values show lyrics later.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { offset = (offset - 100).coerceAtLeast(-5000); prefs.edit().putLong("carOffset", offset).apply(); MediaBridgeSessionManager.refreshCurrentSession(true) }) { Text("−100 ms") }
                    OutlinedButton(onClick = { offset = (offset + 100).coerceAtMost(5000); prefs.edit().putLong("carOffset", offset).apply(); MediaBridgeSessionManager.refreshCurrentSession(true) }) { Text("+100 ms") }
                }
            }
        }
    }
}

@Composable
private fun LandscapeKaraoke(state: KaraokeState, correct: () -> Unit) {
    val context = LocalContext.current
    val list = rememberLazyListState()
    var follow by remember { mutableStateOf(true) }
    val dragging by list.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(dragging) { if (dragging) follow = false }
    LaunchedEffect(state.currentIndex, follow) { if (follow && state.currentIndex >= 0) list.scrollToItem(state.currentIndex) }
    Row(Modifier.fillMaxSize().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        Column(Modifier.weight(.4f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("CAR KARAOKE", style = MaterialTheme.typography.labelMedium, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.primary)
            Text(state.info?.title ?: "Ready when you are", style = MaterialTheme.typography.headlineSmall)
            Text(state.info?.artist ?: "Play a song to begin", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!hasNotificationAccess(context)) TextButton(onClick = { openNotificationAccessSettings(context) }) { Text("Enable music access") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { transport(context, "previous") }) { Icon(Icons.Default.SkipPrevious, "Previous track") }
                FilledIconButton(onClick = { transport(context, if (state.info?.isPlaying == true) "pause" else "play") }) { Icon(if (state.info?.isPlaying == true) Icons.Default.Pause else Icons.Default.PlayArrow, "Play or pause") }
                IconButton(onClick = { transport(context, "next") }) { Icon(Icons.Default.SkipNext, "Next track") }
            }
            TextButton(onClick = correct, enabled = state.track != null) { Text("Correct lyrics") }
            if (!follow) TextButton(onClick = { follow = true }) { Text("Return to live") }
        }
        LazyColumn(state = list, modifier = Modifier.weight(.6f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if (state.resolution.document.synced) itemsIndexed(state.resolution.document.lines) { index, line ->
                Text(line.text.ifBlank { "♪" }, fontSize = 26.sp, lineHeight = 34.sp, fontWeight = if (index == state.currentIndex) FontWeight.Bold else FontWeight.Normal, color = if (index == state.currentIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            } else item { Text(state.resolution.document.plainText.ifBlank { state.resolution.status.name.lowercase().replace('_', ' ') }, fontSize = 24.sp) }
        }
    }
}
