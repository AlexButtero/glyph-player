package com.nothing.glyphplayer.playback

import android.media.audiofx.Equalizer

object EqualizerManager {
    private var equalizer: Equalizer? = null
    private var sessionId: Int = 0

    fun attach(session: Int) {
        if (session == sessionId && equalizer != null) return
        release()
        sessionId = session
        try {
            equalizer = Equalizer(0, session).apply { enabled = true }
        } catch (_: Exception) {
            equalizer = null
        }
    }

    fun release() {
        try { equalizer?.release() } catch (_: Exception) {}
        equalizer = null
        sessionId = 0
    }

    fun isAvailable(): Boolean = equalizer != null

    fun setEnabled(on: Boolean) {
        try { equalizer?.enabled = on } catch (_: Exception) {}
    }

    fun isEnabled(): Boolean = try { equalizer?.enabled ?: false } catch (_: Exception) { false }

    fun bandCount(): Int = try { equalizer?.numberOfBands?.toInt() ?: 0 } catch (_: Exception) { 0 }

    fun bandLevelRange(): Pair<Short, Short> = try {
        val r = equalizer?.bandLevelRange ?: shortArrayOf(-1500, 1500)
        r[0] to r[1]
    } catch (_: Exception) { -1500 to 1500 }

    fun getBandLevel(band: Short): Short = try {
        equalizer?.getBandLevel(band) ?: 0
    } catch (_: Exception) { 0 }

    fun setBandLevel(band: Short, levelMib: Short) {
        try { equalizer?.setBandLevel(band, levelMib) } catch (_: Exception) {}
    }
}
