package com.nothing.glyphplayer.smart

import com.nothing.glyphplayer.data.Track
import kotlin.math.abs

object MyWave {
    fun next(current: Track?, library: List<Track>, recentIds: Set<Long>): Track? {
        if (library.isEmpty()) return null
        val pool = library.filter { it.id !in recentIds }.ifEmpty { library }
        return pool.maxByOrNull { t ->
            var score = Math.random() * 0.5
            if (current != null) {
                if (t.genre != null && t.genre.equals(current.genre, true)) score += 2.0
                if (t.artist.equals(current.artist, true)) score += 1.0
                if (t.bpm != null && current.bpm != null)
                    score += 1.0 - (abs(t.bpm - current.bpm) / 200.0)
            }
            score
        }
    }
}
