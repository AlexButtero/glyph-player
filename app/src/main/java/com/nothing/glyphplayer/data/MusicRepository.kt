package com.nothing.glyphplayer.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class MusicRepository(
    private val db: AppDatabase,
    private val scanner: MediaStoreScanner,
    private val context: Context
) {
    val tracks: Flow<List<Track>> = db.tracks().observeAll()
    val playlists: Flow<List<Playlist>> = db.playlists().observeAll()

    suspend fun scanLibrary(): Int {
        val found = scanner.scan()
        
        val existing = db.tracks().all().associateBy { it.id }
        
        val merged = found.map { newTrack ->
            val oldTrack = existing[newTrack.id]
            if (oldTrack != null && oldTrack.title != newTrack.title) {
                newTrack.copy(title = oldTrack.title)
            } else {
                newTrack
            }
        }
        
        val foundIds = merged.map { it.id }.toSet()
        existing.keys.forEach { id ->
            if (id !in foundIds) {
                db.tracks().deleteById(id)
            }
        }
        
        db.tracks().upsert(merged)
        return merged.size
    }

    suspend fun deleteTrack(track: Track) {
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.delete(Uri.parse(track.uri), null, null)
            } catch (_: Exception) { }
        }
        db.tracks().deleteById(track.id)
    }

    suspend fun renameTrack(track: Track, newTitle: String) {
        withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(track.uri)
                var extension = ""
                
                // Получаем текущее имя файла, чтобы сохранить расширение (.mp3, .flac и т.д.)
                context.contentResolver.query(uri, arrayOf(MediaStore.Audio.Media.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val displayName = cursor.getString(0)
                        if (displayName != null) {
                            val dotIndex = displayName.lastIndexOf('.')
                            if (dotIndex != -1) {
                                extension = displayName.substring(dotIndex)
                            }
                        }
                    }
                }
                
                val newDisplayName = newTitle + extension
                
                // Обновляем метаданные в системе
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, newDisplayName)
                    put(MediaStore.Audio.Media.TITLE, newTitle)
                }
                context.contentResolver.update(uri, values, null, null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        // Обновляем в локальной базе для мгновенного отображения в UI
        db.tracks().updateTitle(track.id, newTitle)
    }

    suspend fun createPlaylist(name: String): Long =
        db.playlists().insert(Playlist(name = name))

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

    suspend fun tracksOf(playlistId: Long): List<Track> =
        db.playlists().tracksOf(playlistId)

    suspend fun getLyrics(trackId: Long): String? = db.lyrics().getLyrics(trackId)
    suspend fun saveLyrics(trackId: Long, text: String) = db.lyrics().saveLyrics(LyricsEntity(trackId, text))
}
