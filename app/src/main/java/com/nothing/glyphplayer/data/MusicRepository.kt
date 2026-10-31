package com.nothing.glyphplayer.data

import android.content.Context
import android.net.Uri
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
        db.tracks().clear()
        db.tracks().upsert(found)
        return found.size
    }

    suspend fun deleteTrack(track: Track) {
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.delete(Uri.parse(track.uri), null, null)
            } catch (_: Exception) { }
        }
        db.tracks().deleteById(track.id)
    }

    suspend fun renameTrack(id: Long, newTitle: String) {
        db.tracks().updateTitle(id, newTitle)
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
