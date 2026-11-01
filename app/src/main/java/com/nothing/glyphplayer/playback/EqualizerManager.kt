package com.nothing.glyphplayer.playback

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.Equalizer

object EqualizerManager {
    private var equalizer: Equalizer? = null
    private var sessionId: Int = 0
    private var isEnabledState = false
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences("eq_prefs", Context.MODE_PRIVATE)
            // ИСПРАВЛЕНО: используем безопасный вызов ?.
            isEnabledState = prefs?.getBoolean("eq_enabled", false) ?: false
        }
    }

    fun attach(session: Int) {
        if (session == 0) return
        if (session == sessionId && equalizer != null) {
            try { equalizer?.enabled = isEnabledState } catch (_: Exception) {}
            return
        }
        release()
        sessionId = session
        try {
            equalizer = Equalizer(0, session).apply { 
                enabled = isEnabledState 
            }
        } catch (_: Exception) {
            equalizer = null
        }
    }

    fun release() {
        try { equalizer?.release() } catch (_: Exception) {}
        equalizer = null
        sessionId = 0
    }

    fun isEnabled(): Boolean = isEnabledState
    fun isAvailable(): Boolean = equalizer != null

    fun setEnabled(on: Boolean) {
        isEnabledState = on
        prefs?.edit()?.putBoolean("eq_enabled", on)?.apply()
        try { equalizer?.enabled = on } catch (_: Exception) {}
    }

    fun bandCount(): Int = try { equalizer?.numberOfBands?.toInt() ?: 0 } catch (_: Exception) { 0 }
    
    fun bandLevelRange(): Pair<Short, Short> = try {
        val r: ShortArray = equalizer?.bandLevelRange ?: shortArrayOf(-1500, 1500)
        r[0] to r[1]
    } catch (_: Exception) { (-1500).toShort() to 1500.toShort() }
    
    fun getBandLevel(band: Short): Short = try { equalizer?.getBandLevel(band) ?: 0.toShort() } catch (_: Exception) { 0.toShort() }
    fun setBandLevel(band: Short, levelMib: Short) { try { equalizer?.setBandLevel(band, levelMib) } catch (_: Exception) {} }
}
