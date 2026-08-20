package com.gululu.aamediamate.lyrics

import kotlinx.coroutines.*
import android.util.Log

object LyricSyncEngine {
    private var currentJob: Job? = null

    fun start(lyrics: List<LyricLine>, startPositionMs: Long, offsetMs: Long = 0L, onLineChanged: (String, String?) -> Unit) {
        currentJob?.cancel()

        currentJob = CoroutineScope(Dispatchers.Default).launch {
            Log.d("MediaBridge", "🎤 LyricSyncEngine starting with position: ${startPositionMs}ms, offset: ${offsetMs}ms")

            val effectiveStartPositionMs = startPositionMs - offsetMs
            val startTime = System.currentTimeMillis() - effectiveStartPositionMs
            var lastLineIndex = -1

            // Find the current line based on actual playback position (not offset)
            val currentTimeSec = startPositionMs / 1000.0f
            val currentLineIndex = lyrics.indexOfFirst { it.timeSec > currentTimeSec }
            
            Log.d("MediaBridge", "🎤 Current time: ${currentTimeSec}s, starting from line: ${currentLineIndex.coerceAtLeast(0)}")

            for (i in currentLineIndex.coerceAtLeast(0) until lyrics.size) {
                val line = lyrics[i]
                val delayMs = (line.timeSec * 1000 - (System.currentTimeMillis() - startTime))
                
                if (delayMs > 0) {
                    Log.d("MediaBridge", "🎤 Waiting ${delayMs}ms for lyric line index $i")
                    delay(delayMs.toLong())
                } else {
                    Log.d("MediaBridge", "🎤 Playing catch-up for lyric line index $i")
                }
                
                onLineChanged(line.text, lyrics.getOrNull(i + 1)?.text?.takeIf { nextLine -> nextLine.isNotBlank() })
                lastLineIndex = i
            }
            
            Log.d("MediaBridge", "🎤 LyricSyncEngine finished, processed ${lastLineIndex + 1} lines")
        }
    }

    fun stop() {
        currentJob?.cancel()
        currentJob = null
        Log.d("MediaBridge", "🎤 LyricSyncEngine stopped")
    }
}
