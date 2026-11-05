package com.nothing.glyphplayer.data

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaStoreScanner(private val ctx: Context) {

    suspend fun scan(): List<Track> = withContext(Dispatchers.IO) {
        val proj = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED
        )
        val out = mutableListOf<Track>()
        ctx.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, "${MediaStore.Audio.Media.TITLE} ASC"
        )?.use { c ->
            val idI = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val tI = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val aI = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val alI = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val dI = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val alIdI = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val dAddI = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val dModI = c.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)

            while (c.moveToNext()) {
                val id = c.getLong(idI)
                val albumId = c.getLong(alIdI)
                val dateAdd = c.getLong(dAddI)
                val dateMod = if (dModI != -1) c.getLong(dModI) else 0L

                // Приоритет: DATE_ADDED → DATE_MODIFIED → 0 (обработается в репозитории)
                val effectiveDate = when {
                    dateAdd > 0 -> dateAdd
                    dateMod > 0 -> dateMod
                    else -> 0L
                }

                val artUri = if (albumId > 0) {
                    val candidate = "content://media/external/audio/albumart/$albumId"
                    try {
                        ctx.contentResolver.openFileDescriptor(Uri.parse(candidate), "r")?.use { fd ->
                            if (fd.fileDescriptor.valid()) candidate else null
                        }
                    } catch (_: Exception) { null }
                } else null

                out += Track(
                    id = id,
                    title = c.getString(tI) ?: "Unknown",
                    artist = c.getString(aI) ?: "Unknown",
                    album = c.getString(alI) ?: "Unknown",
                    durationMs = c.getLong(dI),
                    uri = "content://media/external/audio/media/$id",
                    artUri = artUri,
                    dateAdded = effectiveDate * 1000L
                )
            }
        }
        out
    }

    suspend fun getTrackPath(uriString: String): String? = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(uriString)
            ctx.contentResolver.query(uri, arrayOf(MediaStore.Audio.Media.DATA), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                    return@withContext c.getString(idx)
                }
            }
            null
        } catch (_: Exception) { null }
    }

    suspend fun getSourceFolders(): Map<String, Int> = withContext(Dispatchers.IO) {
        val folders = mutableMapOf<String, Int>()
        try {
            ctx.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Media.DATA),
                "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                null, null
            )?.use { c ->
                val dataIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                while (c.moveToNext()) {
                    val path = c.getString(dataIdx) ?: continue
                    val folder = path.substringBeforeLast('/', "Unknown")
                    folders[folder] = (folders[folder] ?: 0) + 1
                }
            }
        } catch (_: Exception) { }
        folders
    }
}
