package com.nothing.glyphplayer.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.nothing.glyphplayer.playback.LyricLine
import com.nothing.glyphplayer.playback.LyricsLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class MusicRepository(
    private val db: AppDatabase,
    private val scanner: MediaStoreScanner,
    private val context: Context
) {
    val tracks: Flow<List<Track>> = db.tracks().observeAll()
    val playlists: Flow<List<Playlist>> = db.playlists().observeAll()

    val appFolder: File by lazy {
        val base = File(Environment.getExternalStorageDirectory(), "Music")
        if (!base.exists()) base.mkdirs()
        val candidates = listOf("GlyphPlayer", "Glyph Player", "Nothing Player")
        candidates.map { File(base, it) }.firstOrNull { it.exists() }
            ?: File(base, "GlyphPlayer").apply { mkdirs() }
    }

    val lyricsFallbackFolder: File by lazy {
        File(appFolder, "lyrics").apply { mkdirs() }
    }

    // Извлекаем albumId из artUri (для кэша валидации)
    private fun extractAlbumIdFromArtUri(uri: String?): Long? {
        if (uri.isNullOrBlank()) return null
        return try {
            uri.substringAfterLast("/").toLongOrNull()?.takeIf { it > 0 }
        } catch (_: Exception) { null }
    }

    suspend fun scanLibrary(): Int = withContext(Dispatchers.IO) {
        val existing = db.tracks().all().associateBy { it.id }

        // Кэш валидных альбомов — пропускаем повторную валидацию (ускоряет сканирование)
        val knownValidAlbums = existing.values
            .mapNotNull { extractAlbumIdFromArtUri(it.artUri) }
            .toSet()

        val found = scanner.scan(knownValidAlbums)

        var timestamp = System.currentTimeMillis()
        val merged = found.map { newTrack ->
            val oldTrack = existing[newTrack.id]
            when {
                oldTrack != null -> {
                    val preservedDate = if (oldTrack.dateAdded > 0) oldTrack.dateAdded else (timestamp++)
                    newTrack.copy(
                        title = if (oldTrack.title != newTrack.title) oldTrack.title else newTrack.title,
                        dateAdded = preservedDate
                    )
                }
                newTrack.dateAdded > 0 -> newTrack
                else -> {
                    timestamp += 1000L
                    newTrack.copy(dateAdded = timestamp)
                }
            }
        }

        val foundIds = merged.map { it.id }.toSet()
        existing.keys.forEach { id ->
            if (id !in foundIds) db.tracks().deleteById(id)
        }
        db.tracks().upsert(merged)
        merged.size
    }

    suspend fun deleteTrack(track: Track) {
        withContext(Dispatchers.IO) {
            try { context.contentResolver.delete(Uri.parse(track.uri), null, null) } catch (_: Exception) { }
        }
        db.tracks().deleteById(track.id)
    }

    suspend fun renameTrack(track: Track, newTitle: String) {
        withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(track.uri)
                var extension = ""
                context.contentResolver.query(uri, arrayOf(MediaStore.Audio.Media.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val displayName = cursor.getString(0)
                        if (displayName != null) {
                            val dotIndex = displayName.lastIndexOf('.')
                            if (dotIndex != -1) extension = displayName.substring(dotIndex)
                        }
                    }
                }
                val newDisplayName = newTitle + extension
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, newDisplayName)
                    put(MediaStore.Audio.Media.TITLE, newTitle)
                }
                context.contentResolver.update(uri, values, null, null)
            } catch (e: Exception) { e.printStackTrace() }
        }
        db.tracks().updateTitle(track.id, newTitle)
    }

    suspend fun createPlaylist(name: String, desc: String?, poster: String?): Long =
        db.playlists().insert(Playlist(name = name, description = desc, posterUri = poster))

    suspend fun updatePlaylist(id: Long, name: String, desc: String?, poster: String?) {
        db.playlists().updatePlaylist(id, name, desc, poster)
    }

    suspend fun deletePlaylist(id: Long) {
        db.playlists().deletePlaylistTracks(id)
        db.playlists().delete(id)
    }

    suspend fun addToPlaylist(playlistId: Long, trackIds: List<Long>) {
        val items = trackIds.mapIndexed { i, id -> PlaylistTrack(playlistId, id, i) }
        db.playlists().addTracks(items)
    }

    suspend fun tracksOf(playlistId: Long): List<Track> = db.playlists().tracksOf(playlistId)

    suspend fun getSourceFolders(): Map<String, Int> = scanner.getSourceFolders()

    suspend fun getLyrics(trackId: Long): String? = db.lyrics().getLyrics(trackId)

    suspend fun saveLyrics(trackId: Long, text: String) {
        db.lyrics().saveLyrics(LyricsEntity(trackId, text))
    }

    suspend fun loadLrcText(trackId: Long, trackUri: String): String? = withContext(Dispatchers.IO) {
        try {
            val audioPath = scanner.getTrackPath(trackUri)
            if (audioPath != null) {
                val audioFile = File(audioPath)
                val lrcFile = File(audioFile.parentFile, audioFile.nameWithoutExtension + ".lrc")
                if (lrcFile.exists() && lrcFile.length() > 0) {
                    return@withContext lrcFile.readText()
                }
            }
            val fallbackFile = File(lyricsFallbackFolder, "$trackId.lrc")
            if (fallbackFile.exists() && fallbackFile.length() > 0) {
                return@withContext fallbackFile.readText()
            }
            null
        } catch (_: Exception) { null }
    }

    suspend fun loadLrcLines(trackId: Long, trackUri: String): List<LyricLine> {
        val text = loadLrcText(trackId, trackUri) ?: return emptyList()
        return LyricsLoader.parse(text)
    }

    suspend fun saveLrc(track: Track, text: String) = withContext(Dispatchers.IO) {
        try {
            val audioPath = scanner.getTrackPath(track.uri)
            if (audioPath != null) {
                val audioFile = File(audioPath)
                val lrcFile = File(audioFile.parentFile, audioFile.nameWithoutExtension + ".lrc")
                try {
                    if (text.isBlank()) {
                        if (lrcFile.exists()) lrcFile.delete()
                    } else {
                        lrcFile.writeText(text)
                    }
                    return@withContext
                } catch (_: Exception) { }
            }
            val fallbackFile = File(lyricsFallbackFolder, "${track.id}.lrc")
            if (text.isBlank()) {
                if (fallbackFile.exists()) fallbackFile.delete()
            } else {
                fallbackFile.writeText(text)
            }
        } catch (_: Exception) { }
    }
}
