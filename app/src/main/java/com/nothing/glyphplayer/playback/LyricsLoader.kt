package com.nothing.glyphplayer.playback

data class LyricLine(val timeMs: Long, val text: String)

object LyricsLoader {
    private val timeRegex = Regex("""\[(\d{2}):(\d{2})(?:[.:](\d{2,3}))?]""")

    fun parse(raw: String): List<LyricLine> {
        val out = mutableListOf<LyricLine>()
        raw.lineSequence().forEach { line ->
            val match = timeRegex.find(line)
            if (match != null) {
                val (min, sec, msPart) = match.destructured
                val ms = if (msPart.isNotEmpty()) {
                    msPart.toLong().let { if (it < 100) it * 10 else it }
                } else 0L
                val timeMs = min.toLong() * 60_000 + sec.toLong() * 1_000 + ms
                val text = line.substring(match.range.last + 1).trim()
                if (text.isNotEmpty() && !text.startsWith("[")) {
                    out += LyricLine(timeMs, text)
                }
            }
        }
        return out.sortedBy { it.timeMs }
    }
}
