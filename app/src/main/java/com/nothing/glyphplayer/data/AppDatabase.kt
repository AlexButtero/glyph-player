package com.nothing.glyphplayer.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [Track::class, Playlist::class, PlaylistTrack::class, LyricsEntity::class],
    version = 4
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tracks(): TrackDao
    abstract fun playlists(): PlaylistDao
    abstract fun lyrics(): LyricsDao
}
