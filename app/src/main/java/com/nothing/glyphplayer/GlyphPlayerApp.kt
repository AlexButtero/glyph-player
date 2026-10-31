package com.nothing.glyphplayer

import android.app.Application
import androidx.room.Room
import com.nothing.glyphplayer.data.AppDatabase
import com.nothing.glyphplayer.data.MediaStoreScanner
import com.nothing.glyphplayer.data.MusicRepository

class GlyphPlayerApp : Application() {
    val database: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "glyph-db")
            .fallbackToDestructiveMigration()
            .build()
    }
    val repository: MusicRepository by lazy {
        MusicRepository(database, MediaStoreScanner(this))
    }
}
