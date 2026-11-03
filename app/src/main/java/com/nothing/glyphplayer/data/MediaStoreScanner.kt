package com.nothing.glyphplayer.data

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaStoreScanner(private val ctx: Context) {

    private data class RawTrack(
        val id: Long,
        val albumId: Long,
        val title: String,
        val artist: String,
        val album: String,
        val durationMs: Long,
        val dateAdded: Long
    )

    suspend fun scan(): List<Track> = withContext(Dispatchers.IO) {
        val proj = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATE_ADDED
        )

        val rawTracks = mutableListOf<RawTrack>()

        ctx.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0", null,
            "${MediaStore.Audio.Media.TITLE} ASC"
        )?.use { c ->
            val idI = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val tI = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val aI = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val alI = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val dI = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val alIdI = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val dAddI = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

            while (c.moveToNext()) {
                rawTracks += RawTrack(
                    id = c.getLong(idI),
                    albumId = c.getLong(alIdI),
                    title = c.getString(tI) ?: "Unknown",
                    artist = c.getString(aI) ?: "Unknown",
                    album = c.getString(alI) ?: "Unknown",
                    durationMs = c.getLong(dI),
                    dateAdded = c.getLong(dAddI)
                )
            }
        }

        // ФИЗИЧЕСКАЯ ПРОВЕРКА: узнаем, какие обложки реально существуют
        val uniqueAlbumIds = rawTracks.map { it.albumId }.filter { it > 0 }.toSet()
        val validAlbums = mutableSetOf<Long>()

        for (albumId in uniqueAlbumIds) {
            val artUri = "content://media/external/audio/albumart/$albumId".toUri()
            try {
                ctx.contentResolver.openFileDescriptor(artUri, "r")?.use { fd ->
                    if (fd.fileDescriptor.valid()) validAlbums.add(albumId)
                }
            } catch (_: Exception) {
                // Обложки нет в системе или она битая — пропускаем
            }
        }

        rawTracks.map { r ->
            Track(
                id = r.id,
                title = r.title,
                artist = r.artist,
                album = r.album,
                durationMs = r.durationMs,
                uri = "content://media/external/audio/media/${r.id}",
                artUri = if (r.albumId in validAlbums)
                    "content://media/external/audio/albumart/${r.albumId}"
                else null,
                dateAdded = r.dateAdded * 1000L
            )
        }
    }
}
