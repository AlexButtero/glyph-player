package com.nothing.glyphplayer.smart

import com.nothing.glyphplayer.data.Track

object PromptedPlaylist {
    private val keywords = mapOf(
        "sad"     to listOf("sad", "груст", "melanchol", "mellow"),
        "happy"   to listOf("happy", "весел", "dance", "pop"),
        "workout" to listOf("workout", "спорт", "edm", "hip-hop", "rock"),
        "chill"   to listOf("chill", "спокой", "lofi", "ambient"),
        "rain"    to listOf("rain", "дожд", "piano", "acoustic"),
        "focus"   to listOf("focus", "работ", "instrumental", "classical")
    )

    fun build(prompt: String, library: List<Track>): List<Track> {
        val p = prompt.lowercase()
        val tags = keywords.entries
            .firstOrNull { (_, kws) -> kws.any { p.contains(it) } }
            ?.value
            ?: return library.shuffled().take(20)

        val matched = library.filter { t ->
            tags.any { tag ->
                t.genre?.contains(tag, true) == true ||
                t.title.contains(tag, true) ||
                t.artist.contains(tag, true)
            }
        }
        return (matched.ifEmpty { library }).shuffled().take(20)
    }
}
