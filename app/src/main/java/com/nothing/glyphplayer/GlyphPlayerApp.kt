package com.nothing.glyphplayer

import android.app.Application
import androidx.room.Room
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.nothing.glyphplayer.data.AppDatabase
import com.nothing.glyphplayer.data.MediaStoreScanner
import com.nothing.glyphplayer.data.MusicRepository

class GlyphPlayerApp : Application(), ImageLoaderFactory {
    val database: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "glyph-db")
            .fallbackToDestructiveMigration()
            .build()
    }
    val repository: MusicRepository by lazy {
        MusicRepository(database, MediaStoreScanner(this), this)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
    }
}
