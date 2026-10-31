package com.nothing.glyphplayer.data

import kotlinx.coroutines.flow.Flow

class MusicRepository(
    private val db: AppDatabase,
    private val scanner: MediaStoreScanner
) {
    val tracks: Flow<List<Track>> = db.tracks().observeAll()
    val playlists: Flow<List<Playlist>> = db.playlists().observeAll()

    suspend fun scanLibrary(): Int {
        val found = scanner.scan()
        db.tracks().clear()
        db.tracks().upsert(found)
        return found.size
    }

    suspend fun createPlaylist(name: String): Long =
        db.playlists().insert(Playlist(name = name))

    suspend fun deletePlaylist(id: Long) = db.playlists().delete(id)

    suspend fun addToPlaylist(playlistId: Long, trackIds: List<Long>) {
        val items = trackIds.mapIndexed { i, id -> PlaylistTrack(playlistId, id, i) }
        db.playlists().addTracks(items)
    }

    suspend fun tracksOf(playlistId: Long): List<Track> =
        db.playlists().tracksOf(playlistId)
}
