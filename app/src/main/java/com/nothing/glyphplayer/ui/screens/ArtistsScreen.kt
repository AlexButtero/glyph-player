package com.nothing.glyphplayer.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.nothing.glyphplayer.R
import com.nothing.glyphplayer.data.Track
import com.nothing.glyphplayer.ui.PlayerViewModel
import com.nothing.glyphplayer.ui.theme.Red
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder

private val BRACKET_REGEX = Regex("\\[.*?\\]")
private val FEAT_PAREN_REGEX = Regex("(?i)\\(.*?feat.*?\\)")
private val SPLIT_REGEX = Regex("(?i)\\s*(feat\\.?|ft\\.?|&|,|vs\\.?|x)\\s*")
private val NON_ALNUM_REGEX = Regex("[^a-zа-я0-9]")

fun cleanArtistName(raw: String): String {
    var clean = raw
    try { clean = URLDecoder.decode(clean, "UTF-8") } catch (_: Exception) {}
    clean = BRACKET_REGEX.replace(clean, "")
    clean = FEAT_PAREN_REGEX.replace(clean, "")
    return clean.trim().removeSurrounding("\"").removeSurrounding("'").trim()
}

fun splitArtistsWithPrimary(rawArtist: String?): List<Pair<String, Boolean>> {
    if (rawArtist.isNullOrBlank() || rawArtist.equals("<unknown>", true)) return emptyList()
    val processed = SPLIT_REGEX.replace(rawArtist, "|")
    return processed.split("|")
        .map { cleanArtistName(it) }
        .filter { it.isNotBlank() && it.length > 1 && !it.equals("<unknown>", true) }
        .mapIndexed { index, name -> name to (index == 0) }
}

fun extractArtistsWithPrimaryFlag(track: Track): List<Pair<String, Boolean>> {
    val fromMeta = splitArtistsWithPrimary(track.artist)
    if (fromMeta.isNotEmpty()) return fromMeta
    if (track.title.contains(" - ")) {
        val parts = track.title.split(" - ", limit = 2)
        if (parts.size == 2) {
            val fromTitle = splitArtistsWithPrimary(parts[0])
            if (fromTitle.isNotEmpty()) return fromTitle
        }
    }
    return listOf("Unknown Artist" to true)
}

fun normalizeForGrouping(name: String): String = NON_ALNUM_REGEX.replace(name.lowercase(), "").trim()

fun isSimilarArtist(a: String, b: String): Boolean {
    if (a == b) return true
    if (a.length < 3 || b.length < 3) return false
    val normA = normalizeForGrouping(a)
    val normB = normalizeForGrouping(b)
    if (normA == normB) return true
    val prefixLen = minOf(4, minOf(normA.length, normB.length))
    return prefixLen >= 3 && normA.take(prefixLen) == normB.take(prefixLen)
}

data class ArtistGroup(
    val name: String, 
    val tracks: List<Track>, 
    val isPrimary: Boolean,
    val posterUri: String?
)

fun buildArtistsMap(library: List<Track>, trackPosters: Map<Long, String>): Map<String, ArtistGroup> {
    val allArtists = mutableSetOf<String>()
    val trackToArtists = mutableMapOf<Track, List<Pair<String, Boolean>>>()

    library.forEach { track ->
        val artists = extractArtistsWithPrimaryFlag(track)
        trackToArtists[track] = artists
        artists.forEach { allArtists.add(it.first) }
    }

    val canonicalMap = mutableMapOf<String, String>()
    val processed = mutableSetOf<String>()
    val sortedArtists = allArtists.sortedBy { it.length }

    for (i in sortedArtists.indices) {
        val current = sortedArtists[i]
        if (current in processed) continue
        val group = mutableListOf(current)
        processed.add(current)
        for (j in i + 1 until sortedArtists.size) {
            val other = sortedArtists[j]
            if (other in processed) continue
            if (isSimilarArtist(current, other)) {
                group.add(other)
                processed.add(other)
            }
        }
        val bestName = group.maxByOrNull { it.length } ?: current
        val capitalizedName = bestName.replaceFirstChar { it.uppercase() }
        group.forEach { canonicalMap[it] = capitalizedName }
    }

    val result = mutableMapOf<String, MutableList<Pair<Track, Boolean>>>()
    trackToArtists.forEach { (track, artists) ->
        artists.forEach { (rawArtist, isPrimary) ->
            val canonical = canonicalMap[rawArtist] ?: rawArtist
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistsScreen(
    vm: PlayerViewModel = viewModel(), 
    trackPosters: Map<Long, String>,
    onTrackClick: () -> Unit
) {
    val lib by vm.library.collectAsState()
    val selectedArtist by vm.selectedArtist.collectAsState()
    
    var searchQuery by remember { mutableStateOf("") }
    var grouped by remember { mutableStateOf<Map<String, ArtistGroup>>(emptyMap()) }
    var isCalculating by remember { mutableStateOf(false) }
    
    var isRefreshing by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(lib, trackPosters) {
        if (lib.isNotEmpty()) {
            isCalculating = true
            grouped = withContext(Dispatchers.Default) { buildArtistsMap(lib, trackPosters) }
            isCalculating = false
        } else {
            grouped = emptyMap()
        }
    }

    val displayedArtists = remember(grouped, searchQuery) {
        val query = searchQuery.trim().lowercase()
        val filtered = if (query.isEmpty()) grouped.values.filter { it.isPrimary }
                       else grouped.values.filter { it.name.lowercase().contains(query) }
        filtered.sortedByDescending { it.tracks.size }.map { it.name }
    }

    // Логика обработки выбранного артиста — стейт живет в ViewModel
    if (selectedArtist != null) {
        val group = grouped[selectedArtist]
        when {
            group != null -> {
                ArtistTracksScreen(
                    artistName = group.name, 
                    tracks = group.tracks, 
                    trackPosters = trackPosters,
                    onBack = { vm.selectArtist(null) }, 
                    vm = vm, 
                    onTrackClick = onTrackClick
                )
            }
            lib.isNotEmpty() && grouped.isEmpty() -> {
                // Библиотека загружена, но группировка еще не закончена
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Red)
                }
            }
            lib.isEmpty() -> {
                // Библиотека еще не загружена
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Red)
                }
            }
            else -> {
                // Артист реально не найден — сбрасываем
                vm.selectArtist(null)
            }
        }
        return
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            searchQuery = ""
            vm.scanLibrary { 
                isRefreshing = false 
                scope.launch { listState.animateScrollToItem(0) }
            }
        },
        state = pullToRefreshState,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("ИСПОЛНИТЕЛИ", style = MaterialTheme.typography.headlineLarge, color = Color.White)
            Spacer(Modifier.height(16.dp))
            
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Поиск исполнителя...", color = Color.Gray) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Red,
                    unfocusedBorderColor = Color.DarkGray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            )

            if (isCalculating) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Red)
                }
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(displayedArtists, key = { it }) { artistName ->
                        val group = grouped[artistName]
                        val trackCount = group?.tracks?.size ?: 0
                        val posterUri = group?.posterUri
                        
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.selectArtist(artistName) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(Color(0xFF222222)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (posterUri != null) {
                                    AsyncImage(
                                        model = posterUri,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text(
                                        text = artistName.take(1).uppercase(),
                                        color = Color.Gray,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(artistName, color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text("$trackCount", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                        }
                        HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.3f))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArtistTracksScreen(
    artistName: String, 
    tracks: List<Track>, 
    trackPosters: Map<Long, String>,
    onBack: () -> Unit, 
    vm: PlayerViewModel, 
    onTrackClick: () -> Unit
) {
    var dragOffsetX by remember { mutableStateOf(0f) }
    
    var showMenuForTrack by remember { mutableStateOf<Track?>(null) }
    var trackToDelete by remember { mutableStateOf<Track?>(null) }
    var trackToRename by remember { mutableStateOf<Track?>(null) }
    var pendingDeleteTrack by remember { mutableStateOf<Track?>(null) }
    var showAddToPlaylist by remember { mutableStateOf(false) }
    var showCreateFromAdd by remember { mutableStateOf(false) }
    
    var selectedTracks by remember { mutableStateOf(listOf<Long>()) }
    var trackForPlaylist by remember { mutableStateOf<Track?>(null) }
    
    val playlists by vm.playlists.collectAsState()
    
    Box(modifier = Modifier.fillMaxSize().pointerInput(Unit) {
        detectDragGestures(onDragEnd = { if (dragOffsetX > 150f) onBack(); dragOffsetX = 0f }, onDrag = { change, dragAmount -> change.consume(); dragOffsetX += dragAmount.x })
    }) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) { 
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White) } 
                }
                Text(artistName, style = MaterialTheme.typography.headlineLarge, color = Color.White)
                Spacer(Modifier.height(16.dp))
                
                if (selectedTracks.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().background(Color(0xFF1A1A1A)).padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${selectedTracks.size}", color = Color.White, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { 
                                vm.playNext(selectedTracks.mapNotNull { id -> tracks.find { it.id == id } }); 
                                selectedTracks = emptyList() 
                            }, contentPadding = PaddingValues(4.dp)) { 
                                Text("Следующими", color = Red, style = MaterialTheme.typography.labelSmall) 
                            }
                            TextButton(onClick = { 
                                vm.addToQueue(selectedTracks.mapNotNull { id -> tracks.find { it.id == id } }); 
                                selectedTracks = emptyList() 
                            }, contentPadding = PaddingValues(4.dp)) { 
                                Text("В очередь", color = Red, style = MaterialTheme.typography.labelSmall) 
                            }
                            TextButton(onClick = { showAddToPlaylist = true }, contentPadding = PaddingValues(4.dp)) { 
                                Text("В плейлист", color = Red, style = MaterialTheme.typography.labelSmall) 
                            }
                            IconButton(onClick = { selectedTracks = emptyList() }) { 
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray) 
                            }
                        }
                    }
                }
                
                LazyColumn(Modifier.fillMaxSize()) {
                    items(tracks, key = { it.id }) { track ->
                        val isSelected = selectedTracks.contains(track.id)
                        
                        Row(
                            Modifier.fillMaxWidth()
                                .background(if (isSelected) Red.copy(alpha = 0.2f) else Color.Transparent)
                                .combinedClickable(
                                    onClick = {
                                        if (selectedTracks.isEmpty()) { 
                                            vm.playTracks(tracks, tracks.indexOf(track), artistName)
                                            onTrackClick() 
                                        } else { 
                                            selectedTracks = if (isSelected) selectedTracks - track.id else selectedTracks + track.id 
                                        }
                                    },
                                    onLongClick = { if (!isSelected) selectedTracks = selectedTracks + track.id }
                                )
                                .padding(vertical = 8.dp), 
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF222222)),
                                contentAlignment = Alignment.Center
                            ) {
                                val poster = trackPosters[track.id]
                                if (poster != null) {
                                    AsyncImage(
                                        model = poster,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                        error = painterResource(id = R.drawable.ic_fallback_music),
                                        placeholder = painterResource(id = R.drawable.ic_fallback_music)
                                    )
                                } else {
                                    Text(track.title.take(1).uppercase(), color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(track.title, color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            
                            if (isSelected) {
                                Text("✓", color = Red, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(end = 12.dp))
                            } else {
                                Box {
                                    TextButton(onClick = { showMenuForTrack = track }) { Text("⋮", color = Color.Gray, style = MaterialTheme.typography.titleLarge) }
                                    DropdownMenu(expanded = showMenuForTrack?.id == track.id, onDismissRequest = { showMenuForTrack = null }, containerColor = Color(0xFF1E1E1E)) {
                                        DropdownMenuItem(text = { Text(stringResource(R.string.menu_play_next), color = Color.White) }, onClick = { vm.playNext(listOf(track)); showMenuForTrack = null })
                                        DropdownMenuItem(text = { Text(stringResource(R.string.menu_add_to_queue), color = Color.White) }, onClick = { vm.addToQueue(listOf(track)); showMenuForTrack = null })
                                        DropdownMenuItem(text = { Text(stringResource(R.string.menu_add_to_playlist), color = Color.White) }, onClick = { 
                                            trackForPlaylist = track
                                            showMenuForTrack = null
                                            showAddToPlaylist = true 
                                        })
                                        DropdownMenuItem(text = { Text(stringResource(R.string.menu_rename), color = Color.White) }, onClick = { trackToRename = track; showMenuForTrack = null })
                                        DropdownMenuItem(text = { Text(stringResource(R.string.menu_delete), color = Red) }, onClick = { trackToDelete = track; showMenuForTrack = null })
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.3f))
                    }
                }
            }
            
            if (pendingDeleteTrack != null) {
                DeletionTimerSnackbar(
                    track = pendingDeleteTrack!!,
                    onDelete = { vm.deleteTrack(pendingDeleteTrack!!); pendingDeleteTrack = null },
                    onCancel = { pendingDeleteTrack = null },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
                )
            }
        }
    }
    
    if (showAddToPlaylist) {
        if (showCreateFromAdd) {
            PlaylistEditorDialog(
                initialName = "",
                onSave = { name, desc, poster ->
                    val tracksToAdd = if (selectedTracks.isNotEmpty()) selectedTracks.toList() else listOfNotNull(trackForPlaylist?.id)
                    vm.createPlaylist(name, desc, poster) { newId ->
                        vm.addMultipleToPlaylist(newId, tracksToAdd)
                    }
                    showAddToPlaylist = false
                    showCreateFromAdd = false
                    selectedTracks = emptyList()
                    trackForPlaylist = null
                },
                onDismiss = { showCreateFromAdd = false }
            )
        } else {
            AlertDialog(
                onDismissRequest = { showAddToPlaylist = false; trackForPlaylist = null },
                title = { Text(stringResource(R.string.playlists_add_to), color = Color.White) },
                text = {
                    Column {
                        playlists.forEach { p ->
                            TextButton(onClick = { 
                                val tracksToAdd = if (selectedTracks.isNotEmpty()) selectedTracks else listOfNotNull(trackForPlaylist?.id)
                                vm.addMultipleToPlaylist(p.id, tracksToAdd)
                                showAddToPlaylist = false
                                selectedTracks = emptyList()
                                trackForPlaylist = null
                            }) { Text(p.name, color = Color.White) }
                        }
                        TextButton(onClick = { showCreateFromAdd = true }) { Text("+ Создать новый", color = Red) }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { showAddToPlaylist = false; showCreateFromAdd = false; trackForPlaylist = null }) { Text(stringResource(R.string.lib_cancel), color = Color.Gray) } },
                containerColor = Color(0xFF121212)
            )
        }
    }
    
    if (trackToDelete != null) {
        AlertDialog(
            onDismissRequest = { trackToDelete = null }, title = { Text(stringResource(R.string.dialog_delete_title), color = Color.White) },
            text = { Text(stringResource(R.string.dialog_delete_message), color = Color.Gray) },
            confirmButton = { TextButton(onClick = { pendingDeleteTrack = trackToDelete; trackToDelete = null }) { Text(stringResource(R.string.action_delete), color = Red) } },
            dismissButton = { TextButton(onClick = { trackToDelete = null }) { Text(stringResource(R.string.lib_cancel), color = Color.Gray) } }, containerColor = Color(0xFF121212)
        )
    }
    
    if (trackToRename != null) {
        var newTitle by remember { mutableStateOf(trackToRename!!.title) }
        AlertDialog(
            onDismissRequest = { trackToRename = null }, title = { Text(stringResource(R.string.dialog_rename_title), color = Color.White) },
            text = { OutlinedTextField(value = newTitle, onValueChange = { newTitle = it }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Red, unfocusedBorderColor = Color.DarkGray, focusedTextColor = Color.White, unfocusedTextColor = Color.White)) },
            confirmButton = { 
                TextButton(onClick = { 
                    if (newTitle.isNotBlank()) {
                        vm.renameTrack(trackToRename!!, newTitle)
                        trackToRename = null
                    }
                }) { Text(stringResource(R.string.action_save), color = Red) } 
            },
            dismissButton = { TextButton(onClick = { trackToRename = null }) { Text(stringResource(R.string.lib_cancel), color = Color.Gray) } }, containerColor = Color(0xFF121212)
        )
    }
}
