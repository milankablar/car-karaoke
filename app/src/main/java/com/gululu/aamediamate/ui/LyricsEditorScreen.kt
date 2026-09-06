@file:OptIn(ExperimentalMaterial3Api::class)
package com.gululu.aamediamate.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gululu.aamediamate.R
import com.gululu.aamediamate.lyrics.LyricsRepository
import kotlinx.coroutines.launch

@Composable
fun LyricsEditorScreen(
    lyricsKey: String,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    onManualSearch: (String) -> Unit
) {
    BackHandler {
        onBack()
    }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val activity = context as androidx.activity.ComponentActivity
    val editor = remember(activity, lyricsKey) {
        androidx.lifecycle.ViewModelProvider(activity)[lyricsKey, LyricsEditorViewModel::class.java]
    }
    var content by editor.content
    var isLoaded by editor.isLoaded
    var showMenu by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(lyricsKey) {
        try {
            if (!isLoaded || content == editor.savedContent) {
                content = LyricsRepository.loadLyricsText(context, lyricsKey)
                editor.savedContent = content
                isLoaded = true
            }
            LyricsRepository.lyricsUpdatedFlow.collect {
                // External/manual-search saves refresh a clean editor, never overwrite a draft.
                if (content == editor.savedContent) {
                    content = LyricsRepository.loadLyricsText(context, lyricsKey)
                    editor.savedContent = content
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.localizedMessage
        }
    }

    fun save(value: String, closeEditor: Boolean = true) {
        coroutineScope.launch {
            try {
                LyricsRepository.saveLyricsText(context, lyricsKey, value)
                editor.savedContent = value
                content = value
                if (closeEditor) onBack()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.localizedMessage
            }
        }
    }

    if (error != null) {
        AlertDialog(
            onDismissRequest = { error = null },
            text = { Text(error.orEmpty()) },
            confirmButton = { TextButton(onClick = { error = null }) { Text(stringResource(R.string.confirm)) } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.edit_lyrics)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(id = R.string.back))
                    }
                },
                actions = {
                    TextButton(onClick = {
                        content = com.gululu.aamediamate.lyrics.LrcFormat.shift(content, -500)
                        Toast.makeText(
                            context,
                            context.getString(R.string.shifted_by_seconds, -0.5f, 1),
                            Toast.LENGTH_SHORT
                        ).show()
                    }) {
                        Text(text = stringResource(id = R.string.shift_backward_half))
                    }
                    TextButton(onClick = {
                        content = com.gululu.aamediamate.lyrics.LrcFormat.shift(content, 500)
                        Toast.makeText(
                            context,
                            context.getString(R.string.shifted_by_seconds, 0.5f, 1),
                            Toast.LENGTH_SHORT
                        ).show()
                    }) {
                        Text(text = stringResource(id = R.string.shift_forward_half))
                    }

                    IconButton(onClick = {
                        save(content)
                    }) {
                        Icon(Icons.Default.Done, contentDescription = stringResource(id = R.string.save))
                    }
                    IconButton(onClick = {
                        coroutineScope.launch {
                            try {
                                LyricsRepository.deleteLyrics(context, listOf(lyricsKey))
                                content = ""
                                editor.savedContent = ""
                                isLoaded = false
                                onDeleted()
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                error = e.localizedMessage
                            }
                        }
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(id = R.string.delete))
                    }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(id = R.string.manual_search)) },
                            onClick = {
                                showMenu = false
                                onManualSearch(lyricsKey)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(id = R.string.clear_lyrics)) },
                            onClick = {
                                showMenu = false
                                save("", closeEditor = false)
                            }
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        if (isLoaded) {
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                singleLine = false,
                label = { Text(text = stringResource(id = R.string.lyrics_content_label)) }
            )
        }
    }
}
