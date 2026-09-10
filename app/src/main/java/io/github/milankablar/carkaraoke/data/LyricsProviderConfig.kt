package io.github.milankablar.carkaraoke.data

import android.content.Context
import io.github.milankablar.carkaraoke.R
import io.github.milankablar.carkaraoke.lyrics.providers.LrcApiProvider
import io.github.milankablar.carkaraoke.lyrics.providers.LRCLibProvider
import io.github.milankablar.carkaraoke.lyrics.providers.LyricsProvider
import io.github.milankablar.carkaraoke.lyrics.providers.SpotifyProvider

data class LyricsProviderConfig(
    val id: String,
    val name: String,
    val descriptionRes: Int,
    val isEnabled: Boolean,
    val priority: Int,
    val provider: LyricsProvider
) {
    fun getDescription(context: Context): String = context.getString(descriptionRes)
}

object LyricsProviderRegistry {
    fun getAllProviders(): List<LyricsProviderConfig> = listOf(
        LyricsProviderConfig(
            id = "lrclib",
            name = "LRCLib",
            descriptionRes = R.string.lrclib_description,
            isEnabled = true,
            priority = 1,
            provider = LRCLibProvider
        ),
        LyricsProviderConfig(
            id = "Spotify",
            name = "Spotify",
            descriptionRes = R.string.spotify_description,
            isEnabled = false,
            priority = 2,
            provider = SpotifyProvider
        ),
        LyricsProviderConfig(
            id = "lrc_api",
            name = "LRC API",
            descriptionRes = R.string.lrc_api_description,
            isEnabled = false,
            priority = 3,
            provider = LrcApiProvider
        )
    )
    
    fun getProviderById(id: String): LyricsProviderConfig? {
        return getAllProviders().find { it.id == id }
    }
}
