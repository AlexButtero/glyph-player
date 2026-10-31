package com.nothing.glyphplayer.ui

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.nothing.glyphplayer.GlyphPlayerApp
import com.nothing.glyphplayer.data.Playlist
import com.nothing.glyphplayer.data.Track
import com.nothing.glyphplayer.playback.LyricLine
import com.nothing.glyphplayer.playback.LyricsLoader
import com.nothing.glyphplayer.playback.PlayerService
import com.nothing.glyphplayer.smart.MyWave
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentTrackId: Long? = null,
    val title: String? = null,
    val artist: String? = null,
    val artUri: String? = null,
    val durationMs: Long = 0,
    val positionMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val speed: Float = 1f,
    val sleepSecondsRemaining: Int? = null
)

class PlayerViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as GlyphPlayerApp).repository
    private val context = app.applicationContext
    private val scope = viewModelScope

    val library: StateFlow<List<Track>> = repo.tracks.stateIn(scope, SharingStarted.Lazily, emptyList())
    val playlists: StateFlow<List<Playlist>> = repo.playlists.stateIn(scope, SharingStarted.Lazily, emptyList())

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state

    private val _lyrics = MutableStateFlow<List<LyricLine>>(emptyList())
    val lyrics: StateFlow<List<LyricLine>> = _lyrics

    private var controller: MediaController? = null
    private var pollJob: Job? = null
    private var sleepJob: Job? = null
    private var currentLyricsTrackId: Long? = null

    init { connect() }

    private fun connect() {
        val token = SessionToken(context, ComponentName(context, PlayerService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            try {
                controller = future.get()
                controller?.addListener(object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) = refresh()
                })
                refresh()
                startPolling()
            } catch (_: Exception) { }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun refresh() {
        val c = controller ?: return
        val item = c.currentMediaItem
        _state.value = _state.value.copy(
            isPlaying = c.isPlaying, currentTrackId = item?.mediaId?.toLongOrNull(),
            title = item?.mediaMetadata?.title?.toString(), artist = item?.mediaMetadata?.artist?.toString(),
            artUri = item?.mediaMetadata?.artworkUri?.toString(), durationMs = c.duration.coerceAtLeast(0),
            positionMs = c.currentPosition.coerceAtLeast(0), shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode, speed = c.playbackParameters.speed
        )
        val newId = _state.value.currentTrackId
        if (newId != null && newId != currentLyricsTrackId) {
            currentLyricsTrackId = newId
            val uri = library.value.find { it.id == newId }?.uri
            if (uri != null) { scope.launch { _lyrics.value = LyricsLoader.load(context, uri) } } else _lyrics.value = emptyList()
        }
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (isActive) {
                val c = controller ?: break
                _state.value = _state.value.copy(
                    positionMs = c.currentPosition.coerceAtLeast(0),
                    durationMs = c.duration.coerceAtLeast(0)
                )
                delay(500)
            }
        }
    }

    fun playTracks(tracks: List<Track>, startIndex: Int = 0) {
        val c = controller ?: return
        val items = tracks.map { it.toMediaItem() }
        if (items.isEmpty()) return
        c.setMediaItems(items, startIndex.coerceIn(0, items.lastIndex), 0)
        c.prepare()
        c.play()
    }

    fun playPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() { controller?.seekToNextMediaItem() }
    fun prev() { controller?.seekToPreviousMediaItem() }
    fun seekTo(ms: Long) { controller?.seekTo(ms) }
    fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    fun setSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed)
        _state.value = _state.value.copy(speed = speed)
    }

    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        if (minutes <= 0) {
            _state.value = _state.value.copy(sleepSecondsRemaining = null)
            return
        }
        _state.value = _state.value.copy(sleepSecondsRemaining = minutes * 60)
        sleepJob = scope.launch {
            var remaining = minutes * 60
            while (remaining > 0) {
                delay(1000)
                remaining--
                _state.value = _state.value.copy(sleepSecondsRemaining = remaining)
            }
            controller?.pause()
            _state.value = _state.value.copy(sleepSecondsRemaining = null)
        }
    }

    fun shuffleAll() {
        val lib = library.value
        if (lib.isEmpty()) return
        playTracks(lib.shuffled(), 0)
    }

    fun queueMyWave() {
        val lib = library.value
        if (lib.isEmpty()) return
        val current = _state.value.currentTrackId?.let { id -> lib.find { it.id == id } }
        val next = MyWave.next(current, lib, emptySet()) ?: return
        playTracks(lib, lib.indexOf(next))
    }

    fun scanLibrary(onDone: (Int) -> Unit) {
        scope.launch {
            val n = repo.scanLibrary()
            withContext(Dispatchers.Main) { onDone(n) }
        }
    }

    fun createPlaylist(name: String, onDone: (Long) -> Unit = {}) {
        scope.launch {
            val id = repo.createPlaylist(name)
            withContext(Dispatchers.Main) { onDone(id) }
        }
    }

    fun deletePlaylist(id: Long) { scope.launch { repo.deletePlaylist(id) } }
    fun addMultipleToPlaylist(playlistId: Long, trackIds: List<Long>) { scope.launch { repo.addToPlaylist(playlistId, trackIds) } }
    
    fun playPlaylist(playlistId: Long) {
        scope.launch {
            val tracks = repo.tracksOf(playlistId)
            if (tracks.isNotEmpty()) withContext(Dispatchers.Main) { playTracks(tracks, 0) }
        }
    }

    override fun onCleared() {
        controller?.release()
        controller = null
        pollJob?.cancel()
        sleepJob?.cancel()
        super.onCleared()
    }

    private fun Track.toMediaItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title).setArtist(artist).setAlbumTitle(album)
                    .setArtworkUri(artUri?.let { Uri.parse(it) }).build()
            )
            .build()
}
