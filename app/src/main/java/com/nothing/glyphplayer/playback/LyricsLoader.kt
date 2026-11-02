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
                    ?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val columnIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                            if (columnIndex != -1) cursor.getString(columnIndex) else null
                        } else null
                    }

                if (path == null) return@withContext emptyList()

                // Ищем файл .lrc рядом с аудиофайлом
                val lrcFile = File(path.replaceAfterLast('.', "lrc"))
                if (!lrcFile.exists()) return@withContext emptyList()

                parse(lrcFile.readText())
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }

    // Улучшенный Regex: поддерживает [mm:ss.xx], [mm:ss.xxx] и [mm:ss]
    private val timeRegex = Regex("""\[(\d{2}):(\d{2})(?:[.:](\d{2,3}))?]""")

    fun parse(raw: String): List<LyricLine> {
        val out = mutableListOf<LyricLine>()
        
        raw.lineSequence().forEach { line ->
            val match = timeRegex.find(line)
            if (match != null) {
                val (min, sec, msPart) = match.destructured
                // Если миллисекунд нет, считаем их нулем
                val ms = if (msPart.isNotEmpty()) {
                    msPart.toLong().let { if (it < 100) it * 10 else it }
                } else 0L
                
                val timeMs = min.toLong() * 60_000 + sec.toLong() * 1_000 + ms
                val text = line.substring(match.range.last + 1).trim()
                
                // Отсеиваем пустые строки и метаданные (начинающиеся с [)
                if (text.isNotEmpty() && !text.startsWith("[")) {
                    out += LyricLine(timeMs, text)
                }
            }
        }
        // Сортируем по времени на всякий случай (если в LRC был беспорядок)
        return out.sortedBy { it.timeMs }
    }
}
