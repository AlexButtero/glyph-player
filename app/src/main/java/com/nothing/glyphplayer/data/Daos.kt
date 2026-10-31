package com.nothing.glyphplayer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks ORDER BY title")
    fun observeAll(): Flow<List<Track>>
    @Upsert
    suspend fun upsert(list: List<Track>)
    @Query("DELETE FROM tracks")
    suspend fun clear()
    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun deleteById(id: Long)
    @Query("UPDATE tracks SET title = :newTitle WHERE id = :id")
    suspend fun updateTitle(id: Long, newTitle: String)
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Playlist>>
    @Insert
    suspend fun insert(p: Playlist): Long
    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addTracks(items: List<PlaylistTrack>)
    @Query("""SELECT t.* FROM tracks t
              INNER JOIN playlist_tracks pt ON t.id = pt.trackId
              WHERE pt.playlistId = :id ORDER BY pt.position""")
    suspend fun tracksOf(id: Long): List<Track>
}

@Dao
interface LyricsDao {
    @Query("SELECT text FROM lyrics WHERE trackId = :trackId")
    suspend fun getLyrics(trackId: Long): String?
    @Upsert
    suspend fun saveLyrics(lyrics: LyricsEntity)
}
