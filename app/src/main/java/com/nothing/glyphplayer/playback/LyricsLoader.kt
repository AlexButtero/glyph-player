package com.nothing.glyphplayer.playback

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class LyricLine(val timeMs: Long, val text: String)

object LyricsLoader {

    suspend fun load(context: Context, trackUri: String): List<LyricLine> =
        withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(trackUri)
                val proj = arrayOf(MediaStore.Audio.Media.DATA)
                val path = context.contentResolver
                    .query(uri, proj, null, null, null)
                    ?.use { if (it.moveToFirst()) it.getString(0) else null }
                    ?: return@withContext emptyList()

                val lrcFile = File(path.replaceAfterLast('.', "lrc"))
                if (!lrcFile.exists()) return@withContext emptyList()
                parse(lrcFile.readText())
            } catch (_: Exception) {
                emptyList()
            }
        }

    private val timeRegex = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})]""")

    fun parse(raw: String): List<LyricLine> {
        val out = mutableListOf<LyricLine>()
        raw.lineSequence().forEach { line ->
            val m = timeRegex.find(line) ?: return@forEach
            val (min, sec, msPart) = m.destructured
            val ms = msPart.toLong().let { if (it < 100) it * 10 else it }
            val timeMs = min.toLong() * 60_000 + sec.toLong() * 1_000 + ms
            val text = line.substring(m.range.last + 1).trim()
            if (text.isNotEmpty()) out += LyricLine(timeMs, text)
        }
        return out.sortedBy { it.timeMs }
    }
}
