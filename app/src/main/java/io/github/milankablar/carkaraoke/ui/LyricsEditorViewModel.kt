package io.github.milankablar.carkaraoke.ui

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

/** Retains each song's unsaved draft across activity recreation and navigation. */
class LyricsEditorViewModel : ViewModel() {
    val content = mutableStateOf("")
    val isLoaded = mutableStateOf(false)
    var savedContent = ""
}
