package com.nothing.glyphplayer.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentTrackId: Long? = null,
    val title: String? = null,
    val artist: String? = null,
    val artUri: String? = null,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val speed: Float = 1f,
    val sleepSecondsRemaining: Int? = null,
    val queue: List<MediaItem> = emptyList(),
    val currentIndex: Int = 0
)

class PlayerViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as GlyphPlayerApp).repository
    private val context = app.applicationContext
    private val scope = viewModelScope

    private val prefs = context.getSharedPreferences("glyph_prefs", Context.MODE_PRIVATE)
    private var isFirstLaunch = prefs.getBoolean("is_first_launch", true)

    val library: StateFlow<List<Track>> = repo.tracks.stateIn(scope, SharingStarted.Lazily, emptyList())
    val playlists: StateFlow<List<Playlist>> = repo.playlists.stateIn(scope, SharingStarted.Lazily, emptyList())

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs

    private val _lyrics = MutableStateFlow<List<LyricLine>>(emptyList())
    val lyrics: StateFlow<List<LyricLine>> = _lyrics
    
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private var controller: MediaController? = null
    private var pollJob: Job? = null
    private var sleepJob: Job? = null
    private var currentLyricsTrackId: Long? = null
    private var lastBackPressTime = 0L
    private var originalQueue: List<MediaItem> = emptyList()

    // Сохраняем обзервер, чтобы удалить его в onCleared
    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) { startPolling() }
        override fun onStop(owner: LifecycleOwner) { pollJob?.cancel() }
    }

    init {
        connect()
        try {
            ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)
        } catch (_: Exception) { }
    }

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
        try {
            val item = c.currentMediaItem
            val q = (0 until c.mediaItemCount).mapNotNull { c.getMediaItemAt(it) }
            
            _state.update { current ->
                current.copy(
                    isPlaying = c.isPlaying,
                    currentTrackId = item?.mediaId?.toLongOrNull(),
                    title = item?.mediaMetadata?.title?.toString(),
                    artist = item?.mediaMetadata?.artist?.toString(),
                    artUri = item?.mediaMetadata?.artworkUri?.toString(),
                    durationMs = c.duration.coerceAtLeast(0),
                    repeatMode = c.repeatMode,
                    speed = c.playbackParameters.speed,
                    queue = q,
                    currentIndex = c.currentMediaItemIndex
                )
            }
            
            val newId = _state.value.currentTrackId
            if (newId != null && newId != currentLyricsTrackId) {
                currentLyricsTrackId = newId
                val uri = library.value.find { it.id == newId }?.uri
                if (uri != null) {
                    scope.launch { _lyrics.value = LyricsLoader.load(context, uri) }
                } else {
                    _lyrics.value = emptyList()
                }
            }
        } catch (_: Exception) { }
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        if (!scope.isActive) return // Защита от вызова после onCleared

        pollJob = scope.launch {
            while (isActive) {
                try {
                    val c = controller ?: break
                    val newPos = c.currentPosition.coerceAtLeast(0)
                    if (abs(_positionMs.value - newPos) >= 100) {
                        _positionMs.value = newPos
                    }
                    val newDur = c.duration.coerceAtLeast(0)
                    if (_state.value.durationMs != newDur) {
                        _state.update { it.copy(durationMs = newDur) }
                    }
                } catch (_: Exception) {
                    break // Контроллер освобожден или сервис убит
                }
                delay(500)
            }
        }
    }

    fun handleBackButton() {
        val now = System.currentTimeMillis()
        if (now - lastBackPressTime < 500) { prev(); lastBackPressTime = 0L } 
        else { seekTo(0); lastBackPressTime = now }
    }

    fun playTracks(tracks: List<Track>, startIndex: Int = 0) {
        val c = controller ?: return
        try {
            val items = tracks.map { it.toMediaItem() }
            if (items.isEmpty()) return
            
            originalQueue = items
            _state.update { it.copy(shuffle = false) }
            
            c.shuffleModeEnabled = false
            c.setMediaItems(items, startIndex.coerceIn(0, items.lastIndex), 0)
            c.prepare()
            c.play()
        } catch (_: Exception) { }
    }

    fun playPause() { try { controller?.let { if (it.isPlaying) it.pause() else it.play() } } catch (_: Exception) {} }
    fun next() { try { controller?.seekToNextMediaItem() } catch (_: Exception) {} }
    fun prev() { try { controller?.seekToPreviousMediaItem() } catch (_: Exception) {} }
    fun seekTo(ms: Long) { try { controller?.seekTo(ms); _positionMs.value = ms } catch (_: Exception) {} }
    
    fun seekToIndex(uiIndex: Int) {
        try {
            val c = controller ?: return
            c.seekToDefaultPosition(uiIndex)
            c.play()
        } catch (_: Exception) { }
    }
    
    fun toggleShuffle() {
        try {
            val c = controller ?: return
            val newShuffle = !_state.value.shuffle
            val currentItem = c.currentMediaItem ?: return
            val currentPos = c.currentPosition
            val wasPlaying = c.isPlaying

            val currentQueue = (0 until c.mediaItemCount).map { c.getMediaItemAt(it) }.toMutableList()
            if (currentQueue.isEmpty()) return

            if (newShuffle) {
                originalQueue = currentQueue.toList()
                currentQueue.shuffle()
            } else {
                if (originalQueue.isNotEmpty()) {
                    currentQueue.clear()
                    currentQueue.addAll(originalQueue)
                }
            }

            val newIndex = currentQueue.indexOfFirst { it.mediaId == currentItem.mediaId }
            
            _state.update { it.copy(shuffle = newShuffle, queue = currentQueue, currentIndex = if (newIndex != -1) newIndex else 0) }

            if (newIndex != -1) {
                c.setMediaItems(currentQueue, newIndex, currentPos)
            } else {
                currentQueue.add(0, currentItem)
                c.setMediaItems(currentQueue, 0, currentPos)
            }
            c.prepare()
            if (wasPlaying) c.play()
        } catch (_: Exception) { }
    }

    fun stopAndClear() {
        try {
            controller?.stop()
            controller?.clearMediaItems()
        } catch (_: Exception) { }
        originalQueue = emptyList()
        _state.value = PlaybackState()
        _positionMs.value = 0L
    }

    fun cycleRepeat() {
        try {
            controller?.let {
                it.repeatMode = when (it.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
            }
        } catch (_: Exception) { }
    }

    fun setSpeed(speed: Float) { 
        try { controller?.setPlaybackSpeed(speed) } catch (_: Exception) {}
        _state.update { it.copy(speed = speed) } 
    }

    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        if (minutes <= 0) { _state.update { it.copy(sleepSecondsRemaining = null) }; return }
        _state.update { it.copy(sleepSecondsRemaining = minutes * 60) }
        sleepJob = scope.launch {
            var remaining = minutes * 60
            while (remaining > 0) { 
                delay(1000); remaining--; _state.update { it.copy(sleepSecondsRemaining = remaining) } 
            }
            try { controller?.pause() } catch (_: Exception) {}
            _state.update { it.copy(sleepSecondsRemaining = null) }
        }
    }

    fun addToQueue(tracks: List<Track>) { 
        try {
            val c = controller ?: return
            val items = tracks.map { it.toMediaItem() }
            c.addMediaItems(items)
            originalQueue = originalQueue + items
        } catch (_: Exception) { }
    }
    
    fun playNext(tracks: List<Track>) { 
        try {
            val c = controller ?: return
            val items = tracks.map { it.toMediaItem() }
            val insertIndex = c.currentMediaItemIndex + 1
            c.addMediaItems(insertIndex, items)
            
            val mutableOriginal = originalQueue.toMutableList()
            val currentId = c.currentMediaItem?.mediaId
            val originalCurrentIndex = mutableOriginal.indexOfFirst { it.mediaId == currentId }
            if (originalCurrentIndex != -1) {
                mutableOriginal.addAll(originalCurrentIndex + 1, items)
            } else {
                mutableOriginal.addAll(items)
            }
            originalQueue = mutableOriginal
        } catch (_: Exception) { }
    }
    
    fun shuffleAll() { val lib = library.value; if (lib.isNotEmpty()) playTracks(lib.shuffled(), 0) }

    fun scanLibrary(onDone: (Int) -> Unit) { 
        scope.launch { 
            if (isFirstLaunch) _isScanning.value = true
            val n = repo.scanLibrary()
            if (isFirstLaunch) {
                _isScanning.value = false
                isFirstLaunch = false
                prefs.edit().putBoolean("is_first_launch", false).apply()
            }
            withContext(Dispatchers.Main) { onDone(n) } 
        } 
    }
    
    fun deleteTrack(track: Track) { scope.launch { repo.deleteTrack(track) } }
    fun renameTrack(track: Track, newTitle: String) { scope.launch { repo.renameTrack(track, newTitle) } }
    
    fun createPlaylist(name: String, desc: String?, poster: String?, onDone: (Long) -> Unit = {}) { 
        scope.launch { val id = repo.createPlaylist(name, desc, poster); withContext(Dispatchers.Main) { onDone(id) } } 
    }
    
    fun updatePlaylist(id: Long, name: String, desc: String?, poster: String?) {
        scope.launch { repo.updatePlaylist(id, name, desc, poster) }
    }

    fun deletePlaylist(id: Long) { scope.launch { repo.deletePlaylist(id) } }
    fun addMultipleToPlaylist(playlistId: Long, trackIds: List<Long>) { scope.launch { repo.addToPlaylist(playlistId, trackIds) } }
    fun playPlaylist(playlistId: Long) { scope.launch { val tracks = repo.tracksOf(playlistId); if (tracks.isNotEmpty()) withContext(Dispatchers.Main) { playTracks(tracks, 0) } } }
    
    fun getPlaylistTracks(playlistId: Long, onResult: (List<Track>) -> Unit) {
        scope.launch { val tracks = repo.tracksOf(playlistId); withContext(Dispatchers.Main) { onResult(tracks) } }
    }

    fun getSavedLyrics(trackId: Long, onResult: (String?) -> Unit) { scope.launch { withContext(Dispatchers.Main) { onResult(repo.getLyrics(trackId)) } } }
    fun saveLyrics(trackId: Long, text: String) { scope.launch { repo.saveLyrics(trackId, text) } }

    override fun onCleared() {
        // ОЧЕНЬ ВАЖНО: удаляем обзервер, чтобы он не держал ссылку на уничтоженный ViewModel
        try {
            ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)
        } catch (_: Exception) { }
        
        controller?.release(); controller = null; pollJob?.cancel(); sleepJob?.cancel(); super.onCleared()
    }

    private fun Track.toMediaItem(): MediaItem =
        MediaItem.Builder().setMediaId(id.toString()).setUri(uri)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist).setAlbumTitle(album).setArtworkUri(artUri?.let { Uri.parse(it) }).build()).build()
}
