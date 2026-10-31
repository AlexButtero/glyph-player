package com.nothing.glyphplayer.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [Track::class, Playlist::class, PlaylistTrack::class],
    version = 2
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tracks(): TrackDao
    abstract fun playlists(): PlaylistDao
}
