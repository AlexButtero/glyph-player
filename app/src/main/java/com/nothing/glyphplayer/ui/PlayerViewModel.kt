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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.text.Normalizer
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
    val currentIndex: Int = 0,
    val playlistName: String? = "Мои треки"
)

data class ArtistGroup(
    val name: String,
    val tracks: List<Track>,
    val isPrimary: Boolean,
    val posterUri: String?
)

// 1. Unicode-нормализация и очистка от мусора
fun cleanArtistName(raw: String): String {
    var clean = raw
    try {
        // NFKC нормализация приводит разные юникод-символы к единому виду
        clean = Normalizer.normalize(clean, Normalizer.Form.NFKC)
        clean = URLDecoder.decode(clean, "UTF-8")
    } catch (_: Exception) {}

    // Удаляем квадратные скобки с мусором (например, [muzmo.ru])
    clean = clean.replace(Regex("\\[.*?\\]"), "")
    
    // Удаляем служебные пометки в круглых скобках
    clean = clean.replace(Regex("(?i)\\((official|prod\\.?|remix|version|audio|video|lyrics|music|clip|cover|instrumental).*?\\)"), "")
    
    // Удаляем эмодзи и прочие спецсимволы (кроме букв, цифр и базовой пунктуации)
    clean = clean.replace(Regex("[\\p{So}\\p{Cn}]"), "")

    // Убираем лишние пробелы и кавычки по краям
    return clean.replace(Regex("\\s+"), " ").trim().removeSurrounding("\"").removeSurrounding("'")
}

fun splitArtists(rawArtist: String?): List<String> {
    if (rawArtist.isNullOrBlank() || rawArtist.equals("<unknown>", true)) return emptyList()
    
    // Разделяем по feat, ft, &, запятой, vs и т.д.
    val processed = rawArtist.replace(Regex("(?i)\\s*(feat\\.?|ft\\.?|&|,|vs\\.?|x|с)\\s*"), "|")
    
    return processed.split("|")
        .map { cleanArtistName(it) }
        .filter { it.isNotBlank() && it.length > 1 && !it.equals("<unknown>", true) && !it.equals("unknown", true) }
}

// 5. Извлечение фитов из названия трека
private val titleFeatRegex = Regex("(?i)\\(feat\\.?\\s+([^)]+)\\)")
fun extractArtistsFromTitle(title: String): List<String> {
    val match = titleFeatRegex.find(title)
    return match?.groupValues?.get(1)?.let { splitArtists(it) } ?: emptyList()
}

fun extractArtistsWithPrimaryFlag(track: Track): List<Pair<String, Boolean>> {
    val result = mutableListOf<Pair<String, Boolean>>()
    
    // Берем из тега артиста
    val fromMeta = splitArtists(track.artist)
    fromMeta.forEachIndexed { index, name -> result.add(name to (index == 0)) }

    // Берем фиты из названия
    val fromTitle = extractArtistsFromTitle(track.title)
    fromTitle.forEach { name ->
        if (result.none { it.first.equals(name, ignoreCase = true) }) {
            result.add(name to false) // фиты из названия всегда второстепенные
        }
    }

    // Если ничего нет, пробуем "Artist - Title"
    if (result.isEmpty() && track.title.contains(" - ")) {
        val parts = track.title.split(" - ", limit = 2)
        if (parts.size == 2) {
            val fromTitleDash = splitArtists(parts[0])
            fromTitleDash.forEachIndexed { index, name -> result.add(name to (index == 0)) }
        }
    }
    
    return if (result.isEmpty()) listOf("Unknown Artist" to true) else result
}

// Утилита нормализации ключа (убираем все кроме букв и цифр)
fun normalizeArtistKey(name: String): String {
    return name.lowercase().replace(Regex("[^a-zа-я0-9]"), "").trim()
}

fun resolveArtistKey(title: String?, artist: String?): String {
    val rawArtist = if (artist.isNullOrBlank() || artist.equals("<unknown>", true)) {
        if (!title.isNullOrBlank() && title.contains(" - ")) title.split(" - ", limit = 2)[0] else "Unknown"
    } else artist

    var clean = rawArtist
    try { clean = URLDecoder.decode(clean, "UTF-8") } catch (_: Exception) {}
    clean = clean.replace(Regex("\\[.*?\\]"), "")
        .replace(Regex("(?i)\\(.*?feat.*?\\)"), "")
        .trim()

    val parts = clean.replace(Regex("(?i)\\s*(feat\\.?|ft\\.?|&|,|vs\\.?|x)\\s*"), "|").split("|")
    val main = parts.firstOrNull()?.trim() ?: ""
    return if (main.isNotBlank()) main.replaceFirstChar { it.uppercase() } else "Unknown"
}

fun buildArtistsMap(library: List<Track>, trackPosters: Map<Long, String>): Map<String, ArtistGroup> {
    // 1. Начальное извлечение артистов
    val trackToRawArtists = mutableMapOf<Track, List<Pair<String, Boolean>>>()
    library.forEach { track -> trackToRawArtists[track] = extractArtistsWithPrimaryFlag(track) }

    // 4. Эвристика по альбомам (выравниваем имена артистов внутри одного альбома)
    val albumGroups = library.groupBy { it.album }.filter { it.key.isNotBlank() && !it.key.equals("<unknown>", true) }
    val albumCanonicalArtist = mutableMapOf<String, String>()
    
    albumGroups.forEach { (album, tracks) ->
        val artistCounts = mutableMapOf<String, Int>()
        tracks.forEach { t ->
            trackToRawArtists[t]?.firstOrNull()?.first?.let { raw ->
                artistCounts[raw] = (artistCounts[raw] ?: 0) + 1
            }
        }
        val bestArtist = artistCounts.maxByOrNull { it.value }?.key
        if (bestArtist != null) albumCanonicalArtist[album] = bestArtist
    }

    val resolvedTrackToArtists = mutableMapOf<Track, List<Pair<String, Boolean>>>()
    trackToRawArtists.forEach { (track, artists) ->
        val albumArtist = albumCanonicalArtist[track.album]
        if (albumArtist != null && artists.isNotEmpty()) {
            val primary = artists.first()
            if (primary.first != albumArtist) {
                val updated = artists.toMutableList()
                updated[0] = albumArtist to true
                resolvedTrackToArtists[track] = updated
            } else {
                resolvedTrackToArtists[track] = artists
            }
        } else {
            resolvedTrackToArtists[track] = artists
        }
    }

    // 2. Частотный словарь имен (для выбора канонического названия)
    val rawNameCounts = mutableMapOf<String, Int>()
    resolvedTrackToArtists.values.forEach { list ->
        list.forEach { (name, _) -> 
            val cleaned = cleanArtistName(name)
            rawNameCounts[cleaned] = (rawNameCounts[cleaned] ?: 0) + 1 
        }
    }

    // 3. Группировка по нормализованному ключу (без учета регистра и знаков)
    val normalizedToRawNames = mutableMapOf<String, MutableSet<String>>()
    resolvedTrackToArtists.values.forEach { list ->
        list.forEach { (name, _) ->
            val cleaned = cleanArtistName(name)
            val norm = normalizeArtistKey(cleaned)
            if (norm.isNotBlank()) {
                normalizedToRawNames.getOrPut(norm) { mutableSetOf() }.add(cleaned)
            }
        }
    }

    // 5. Выбор канонического имени (сначала по частоте, потом по длине)
    val normToCanonical = mutableMapOf<String, String>()
    normalizedToRawNames.forEach { (norm, rawNames) ->
        val best = rawNames.maxWithOrNull(
            compareBy<String> { rawNameCounts[it] ?: 0 }.thenBy { it.length }
        ) ?: rawNames.first()
        normToCanonical[norm] = best
    }

    // Сборка итогового списка
    val result = mutableMapOf<String, MutableList<Pair<Track, Boolean>>>()
    resolvedTrackToArtists.forEach { (track, artists) ->
        artists.forEach { (rawName, isPrimary) ->
            val cleaned = cleanArtistName(rawName)
            val norm = normalizeArtistKey(cleaned)
            val canonical = normToCanonical[norm] ?: cleaned
            result.getOrPut(canonical) { mutableListOf() }.add(track to isPrimary)
        }
    }

    return result.mapValues { (canonicalName, list) ->
        val uniqueTracks = list.map { it.first }.distinctBy { it.id }
        val isPrimary = list.any { it.second }
        val posterUri = uniqueTracks.firstNotNullOfOrNull { trackPosters[it.id] }
        ArtistGroup(canonicalName, uniqueTracks, isPrimary, posterUri)
    }
}

class PlayerViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as GlyphPlayerApp).repository
    private val context = app.applicationContext
    private val scope = viewModelScope

    private val prefs = context.getSharedPreferences("glyph_prefs", Context.MODE_PRIVATE)
    private var isFirstLaunch = prefs.getBoolean("is_first_launch", true)

    val library: StateFlow<List<Track>> = repo.tracks.stateIn(scope, SharingStarted.Lazily, emptyList())
    val playlists: StateFlow<List<Playlist>> = repo.playlists.stateIn(scope, SharingStarted.Lazily, emptyList())

    // КАРТА ПОСТЕРОВ
    val trackPosters: StateFlow<Map<Long, String>> = library
        .map { lib ->
            val artistToPoster = mutableMapOf<String, String>()
            lib.forEach { track ->
                if (!track.artUri.isNullOrBlank()) {
                    val key = resolveArtistKey(track.title, track.artist)
                    if (!artistToPoster.containsKey(key)) artistToPoster[key] = track.artUri!!
                }
            }
            val result = mutableMapOf<Long, String>()
            lib.forEach { track ->
                val own = track.artUri
                if (!own.isNullOrBlank()) {
                    result[track.id] = own
                } else {
                    val key = resolveArtistKey(track.title, track.artist)
                    artistToPoster[key]?.let { result[track.id] = it }
                }
            }
            result
        }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    // КЭШИРОВАННЫЙ СПИСОК АРТИСТОВ (Считается один раз в фоне)
    val artistGroups: StateFlow<Map<String, ArtistGroup>> = combine(library, trackPosters) { lib, posters ->
        buildArtistsMap(lib, posters)
    }
    .flowOn(Dispatchers.Default)
    .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    // === НАВИГАЦИОННЫЙ СТЕЙТ ===
    private val _selectedArtist = MutableStateFlow<String?>(null)
    val selectedArtist: StateFlow<String?> = _selectedArtist
    fun selectArtist(name: String?) { _selectedArtist.value = name }

    private val _selectedPlaylistId = MutableStateFlow<Long?>(null)
    val selectedPlaylistId: StateFlow<Long?> = _selectedPlaylistId
    fun selectPlaylist(id: Long?) { _selectedPlaylistId.value = id }

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs

    private val _lyrics = MutableStateFlow<List<LyricLine>>(emptyList())
    val lyrics: StateFlow<List<LyricLine>> = _lyrics
    
    private val _lyricsOffsetMs = MutableStateFlow(prefs.getLong("lyrics_offset", 0L))
    val lyricsOffsetMs: StateFlow<Long> = _lyricsOffsetMs

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private var controller: MediaController? = null
    private var pollJob: Job? = null
    private var sleepJob: Job? = null
    private var currentLyricsTrackId: Long? = null
    private var lastBackPressTime = 0L
    private var originalQueue: List<MediaItem> = emptyList()

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
                val newController = future.get()
                controller = newController
                
                newController.addListener(object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) = refresh()
                })
                
                val c = newController
                scope.launch(Dispatchers.IO) {
                    try {
                        val count = c.mediaItemCount
                        if (count > 0) {
                            val initialQueue = (0 until count).mapNotNull { c.getMediaItemAt(it) }
                            if (initialQueue.isNotEmpty()) {
                                originalQueue = initialQueue
                                withContext(Dispatchers.Main) {
                                    _state.update { it.copy(queue = initialQueue, currentIndex = c.currentMediaItemIndex) }
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
                
                refresh()
                startPolling()
            } catch (_: Exception) { }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun refresh() {
        val c = controller ?: return
        try {
            val item = c.currentMediaItem
            _state.update { current ->
                current.copy(
                    isPlaying = c.isPlaying,
                    currentTrackId = item?.mediaId?.toLongOrNull(),
                    title = item?.mediaMetadata?.title?.toString(),
                    artist = item?.mediaMetadata?.artist?.toString(),
                    artUri = item?.mediaMetadata?.artworkUri?.toString()?.takeIf { it.isNotBlank() },
                    durationMs = c.duration.coerceAtLeast(0),
                    repeatMode = c.repeatMode,
                    speed = c.playbackParameters.speed,
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
        if (!scope.isActive) return

        pollJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                try {
                    val c = controller
                    if (c != null) {
                        val newPos = c.currentPosition.coerceAtLeast(0)
                        if (abs(_positionMs.value - newPos) >= 100) _positionMs.value = newPos
                        val newDur = c.duration.coerceAtLeast(0)
                        if (_state.value.durationMs != newDur) _state.update { it.copy(durationMs = newDur) }
                    }
                } catch (_: Exception) {}
                delay(500)
            }
        }
    }

    fun setLyricsOffset(offsetMs: Long) {
        _lyricsOffsetMs.value = offsetMs
        prefs.edit().putLong("lyrics_offset", offsetMs).apply()
    }

    fun handleBackButton() {
        val now = System.currentTimeMillis()
        if (now - lastBackPressTime < 500) { prev(); lastBackPressTime = 0L } 
        else { seekTo(0); lastBackPressTime = now }
    }

    fun playTracks(tracks: List<Track>, startIndex: Int = 0, playlistName: String = "Мои треки") {
        val c = controller ?: return
        scope.launch(Dispatchers.Default) {
            try {
                val posters = trackPosters.value
                val items = tracks.map { track -> track.toMediaItem(posters[track.id]) }
                if (items.isEmpty()) return@launch
                
                originalQueue = items
                withContext(Dispatchers.Main) {
                    _state.update { it.copy(shuffle = false, playlistName = playlistName, queue = items) }
                    try {
                        c.shuffleModeEnabled = false
                        c.setMediaItems(items, startIndex.coerceIn(0, items.lastIndex), 0)
                        c.prepare()
                        c.play()
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }
    }

    fun playPause() { try { controller?.let { if (it.isPlaying) it.pause() else it.play() } } catch (_: Exception) {} }
    fun next() { try { controller?.seekToNextMediaItem() } catch (_: Exception) {} }
    fun prev() { try { controller?.seekToPreviousMediaItem() } catch (_: Exception) {} }
    fun seekTo(ms: Long) { try { controller?.seekTo(ms); _positionMs.value = ms } catch (_: Exception) {} }
    
    fun seekToIndex(uiIndex: Int) {
        try {
            val c = controller ?: return
            if (uiIndex in 0 until c.mediaItemCount) {
                c.seekToDefaultPosition(uiIndex)
                c.play()
            }
        } catch (_: Exception) { }
    }
    
    fun toggleShuffle() {
        val c = controller ?: return
        val currentItem = try { c.currentMediaItem } catch (e: Exception) { null } ?: return
        val currentPos = try { c.currentPosition } catch (e: Exception) { 0L }
        val wasPlaying = try { c.isPlaying } catch (e: Exception) { false }
        val newShuffle = !_state.value.shuffle

        scope.launch(Dispatchers.Default) {
            try {
                val currentQueue = _state.value.queue.toMutableList()
                if (currentQueue.isEmpty()) return@launch

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
                val finalQueue = if (newIndex != -1) currentQueue else currentQueue.apply { add(0, currentItem) }
                val finalIndex = if (newIndex != -1) newIndex else 0
                
                withContext(Dispatchers.Main) {
                    _state.update { it.copy(shuffle = newShuffle, queue = finalQueue, currentIndex = finalIndex) }
                    try {
                        c.setMediaItems(finalQueue, finalIndex, currentPos)
                        c.prepare()
                        if (wasPlaying) c.play()
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }
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
        val c = controller ?: return
        scope.launch(Dispatchers.Default) {
            try {
                val posters = trackPosters.value
                val items = tracks.map { track -> track.toMediaItem(posters[track.id]) }
                val newQueue = _state.value.queue + items
                withContext(Dispatchers.Main) {
                    try {
                        c.addMediaItems(items)
                        originalQueue = originalQueue + items
                        _state.update { it.copy(queue = newQueue) }
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }
    }
    
    fun playNext(tracks: List<Track>) { 
        val c = controller ?: return
        scope.launch(Dispatchers.Default) {
            try {
                val posters = trackPosters.value
                val items = tracks.map { track -> track.toMediaItem(posters[track.id]) }
                var insertIndex = 0
                var currentId: String? = null
                
                withContext(Dispatchers.Main) {
                    try {
                        val currentIdx = c.currentMediaItemIndex.coerceAtLeast(0)
                        insertIndex = (currentIdx + 1).coerceAtMost(c.mediaItemCount)
                        currentId = c.currentMediaItem?.mediaId
                    } catch (_: Exception) {}
                }

                val mutableOriginal = originalQueue.toMutableList()
                val originalCurrentIndex = mutableOriginal.indexOfFirst { it.mediaId == currentId }
                if (originalCurrentIndex != -1) {
                    mutableOriginal.addAll((originalCurrentIndex + 1).coerceAtMost(mutableOriginal.size), items)
                } else {
                    mutableOriginal.addAll(items)
                }
                
                val newQueue = _state.value.queue.toMutableList().apply {
                    addAll(insertIndex.coerceAtMost(size), items)
                }
                
                withContext(Dispatchers.Main) {
                    try {
                        c.addMediaItems(insertIndex, items)
                        originalQueue = mutableOriginal
                        _state.update { it.copy(queue = newQueue) }
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }
    }
    
    fun shuffleAll(playlistName: String = "Мои треки") { 
        val lib = library.value
        if (lib.isNotEmpty()) playTracks(lib.shuffled(), 0, playlistName) 
    }

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
    
    fun playPlaylist(playlistId: Long, playlistName: String) { 
        scope.launch { 
            val tracks = repo.tracksOf(playlistId)
            if (tracks.isNotEmpty()) withContext(Dispatchers.Main) { playTracks(tracks, 0, playlistName) } 
        } 
    }
    
    fun getPlaylistTracks(playlistId: Long, onResult: (List<Track>) -> Unit) {
        scope.launch { val tracks = repo.tracksOf(playlistId); withContext(Dispatchers.Main) { onResult(tracks) } }
    }

    fun getSavedLyrics(trackId: Long, onResult: (String?) -> Unit) { scope.launch { withContext(Dispatchers.Main) { onResult(repo.getLyrics(trackId)) } } }
    fun saveLyrics(trackId: Long, text: String) { scope.launch { repo.saveLyrics(trackId, text) } }

    override fun onCleared() {
        try {
            ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)
        } catch (_: Exception) { }
        
        controller?.release(); controller = null; pollJob?.cancel(); sleepJob?.cancel(); super.onCleared()
    }

    private fun Track.toMediaItem(fallbackArt: String? = null): MediaItem {
        val finalArt = if (!artUri.isNullOrBlank()) artUri else fallbackArt
        return MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setArtworkUri(finalArt?.let { Uri.parse(it) })
                    .build()
            ).build()
    }
}
