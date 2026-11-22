package io.github.milankablar.carkaraoke.backup

import org.json.JSONArray
import org.json.JSONObject

/** Validates a complete import before any preference is changed. */
internal object BackupSettingsValidator {
    private val booleans = setOf(
        "lyrics_enabled",
        "simplify_chinese",
        "ignore_native_auto_apps",
        "combine_app_icon_and_album_art",
        "show_album_name",
        "show_next_lyric_line",
        "show_source_app",
        "reduced_motion",
        "keep_screen_on",
        "car_legacy_title",
        "car_show_artwork"
    )
    private val strings = setOf("lrc_api_uri", "language_pref", "api_key", "lrc_api_auth_token")
    private val arrays = setOf("bridged_apps", "lyrics_providers", "lyrics_cleanup_rules")
    private val themes = setOf("System", "Light", "Dark", "Black")
    private val carModes = setOf("lyric_first", "lyric_and_song", "song_and_lyric")
    private val carCustomActions = setOf("seek", "timing")

    fun validate(source: JSONObject): JSONObject {
        val result = JSONObject()
        source.keys().forEach { key ->
            val value = source.get(key)
            when (key) {
                in booleans -> {
                    require(value is Boolean) { "Invalid boolean: $key" }
                    result.put(key, value)
                }
                in strings -> {
                    require(value is String && value.length <= 16_384) { "Invalid text: $key" }
                    result.put(key, value)
                }
                "lyrics_timing_offset" -> {
                    require(value is Number && value.toDouble() == value.toInt().toDouble() && value.toInt() in -10000..10000) {
                        "Invalid lyric timing offset"
                    }
                    result.put(key, value.toInt())
                }
                "appearance_theme" -> {
                    require(value is String && value in themes) { "Invalid appearance theme" }
                    result.put(key, value)
                }
                "lyric_text_size_sp" -> {
                    require(value is Number && value.toDouble().isFinite() && value.toDouble() in 22.0..40.0) {
                        "Invalid lyric text size"
                    }
                    result.put(key, value.toDouble())
                }
                "car_lyrics_offset_ms" -> {
                    require(value is Number && value.toDouble() == value.toLong().toDouble() && value.toLong() in -5000L..5000L) {
                        "Invalid car lyrics offset"
                    }
                    result.put(key, value.toLong())
                }
                "car_now_playing_mode" -> {
                    require(value is String && value in carModes) { "Invalid car now playing mode" }
                    result.put(key, value)
                }
                "car_custom_actions_mode" -> {
                    require(value is String && value in carCustomActions) { "Invalid car custom actions mode" }
                    result.put(key, value)
                }
                in arrays -> {
                    val array = when (value) {
                        is JSONArray -> value
                        is String -> JSONArray(value)
                        else -> throw IllegalArgumentException("Invalid array: $key")
                    }
                    require(array.length() <= 1000) { "Too many entries: $key" }
                    val seen = mutableSetOf<String>()
                    for (index in 0 until array.length()) {
                        val item = array.getJSONObject(index)
                        validateEntry(key, item)
                        val id = item.getString(if (key == "bridged_apps") "packageName" else "id")
                        require(seen.add(id)) { "Duplicate entry: $key" }
                    }
                    result.put(key, array)
                }
            }
        }
        return result
    }

    fun validateEntry(key: String, item: JSONObject) {
        fun text(name: String, allowEmpty: Boolean = false) {
            val value = item.get(name)
            require(value is String && value.length <= 16_384 && (allowEmpty || value.isNotBlank())) { "Invalid $key.$name" }
        }
        fun number(name: String) {
            val value = item.get(name)
            require(value is Number && value.toDouble().isFinite() && value.toDouble() == value.toLong().toDouble() && value.toLong() >= 0) {
                "Invalid $key.$name"
            }
        }
        fun optionalBoolean(name: String) {
            if (item.has(name)) require(item.get(name) is Boolean) { "Invalid $key.$name" }
        }
        when (key) {
            "bridged_apps" -> {
                text("packageName")
                text("appName", true)
                number("firstSeen")
                number("lastSeen")
                optionalBoolean("lyricsEnabled")
                optionalBoolean("headUnitControlEnabled")
                optionalBoolean("swapRewindFastForward")
            }
            "lyrics_providers" -> {
                text("id")
                if (item.has("priority")) number("priority")
                optionalBoolean("isEnabled")
            }
            "lyrics_cleanup_rules" -> {
                text("id")
                text("name", true)
                text("pattern", true)
                text("field")
                require(item.getString("field") in setOf("TITLE", "ARTIST")) { "Invalid cleanup field" }
                optionalBoolean("isEnabled")
                Regex(item.getString("pattern"))
            }
        }
    }
}
