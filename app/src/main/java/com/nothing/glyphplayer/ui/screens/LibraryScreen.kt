package com.nothing.glyphplayer.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nothing.glyphplayer.R
import com.nothing.glyphplayer.data.Track
import com.nothing.glyphplayer.ui.PlayerViewModel
import com.nothing.glyphplayer.ui.theme.Red
import com.nothing.glyphplayer.ui.theme.Surface
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    vm: PlayerViewModel = viewModel(), 
    trackPosters: Map<Long, String>,
    onTrackClick: () -> Unit
) {
    val lib by vm.library.collectAsState()
    val state by vm.state.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val isScanning by vm.isScanning.collectAsState()
    val context = LocalContext.current
    
    LaunchedEffect(Unit) { if (lib.isEmpty()) vm.scanLibrary { } }

    var search by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(3) }
    var showSortMenu by remember { mutableStateOf(false) }
    
    var isShuffled by remember { mutableStateOf(false) }
    var shuffleOrder by remember { mutableStateOf<List<Long>>(emptyList()) }
    
    var selectedTracks by remember { mutableStateOf(listOf<Long>()) }
    var showAddToPlaylist by remember { mutableStateOf(false) }
    var showMenuForTrack by remember { mutableStateOf<Track?>(null) }
    var trackToDelete by remember { mutableStateOf<Track?>(null) }
    var trackToRename by remember { mutableStateOf<Track?>(null) }
    
    var pendingDeleteTrack by remember { mutableStateOf<Track?>(null) }
    var showCreateFromAdd by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    
    val pullToRefreshState = rememberPullToRefreshState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        vm.libraryRefreshEvent.collect {
            val wasAtTop = !listState.canScrollBackward
            if (!wasAtTop) {
                listState.scrollToItem(0)
            } else {
                isRefreshing = true
                search = ""
                sortMode = 3
                isShuffled = false
                vm.scanLibrary { isRefreshing = false }
            }
        }
    }

    val filtered = remember(lib, search, sortMode, isShuffled, shuffleOrder) {
        val filteredList = lib.filter {
            search.isBlank() || it.title.contains(search, true) || it.artist.contains(search, true)
        }
        
        if (isShuffled && shuffleOrder.isNotEmpty()) {
            val orderMap = shuffleOrder.withIndex().associate { it.value to it.index }
            filteredList.sortedBy { orderMap[it.id] ?: Int.MAX_VALUE }
        } else {
            when (sortMode) {
                1 -> filteredList.sortedBy { t -> t.title }
                2 -> filteredList.sortedBy { t -> t.artist }
                3 -> filteredList.sortedByDescending { t -> t.dateAdded }
                else -> filteredList
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                sortMode = 3
                isShuffled = false
                search = ""
                vm.scanLibrary { 
                    isRefreshing = false 
                    scope.launch { listState.scrollToItem(0) }
                }
            },
            state = pullToRefreshState,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(Modifier.fillMaxSize()) {
                if (selectedTracks.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().background(Color(0xFF1A1A1A)).padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${selectedTracks.size}", color = Color.White, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { vm.playNext(selectedTracks.mapNotNull { id -> lib.find { it.id == id } }); selectedTracks = emptyList() }, contentPadding = PaddingValues(4.dp)) { 
                                Text("Следующими", color = Red, style = MaterialTheme.typography.labelSmall) 
                            }
                            TextButton(onClick = { vm.addToQueue(selectedTracks.mapNotNull { id -> lib.find { it.id == id } }); selectedTracks = emptyList() }, contentPadding = PaddingValues(4.dp)) { 
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
                } else {
                    Column(Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = search, 
                            onValueChange = { search = it }, 
                            placeholder = { Text(stringResource(R.string.lib_search_hint), color = Color.Gray) },
                            singleLine = true, 
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Red, 
                                unfocusedBorderColor = Color.DarkGray, 
                                focusedTextColor = Color.White, 
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth().padding(8.dp)
                        )
                        
                        Box(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            TextButton(
                                onClick = { 
                                    isShuffled = true
                                    shuffleOrder = lib.map { it.id }.shuffled()
                                },
                                modifier = Modifier.align(Alignment.CenterStart),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) { 
                                Icon(Icons.Default.Shuffle, contentDescription = null, tint = Red, modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.lib_shuffle), color = Red, style = MaterialTheme.typography.bodyMedium) 
                            }
                            
                            Text(
                                text = if (search.isBlank()) "${filtered.size} треков" else "${filtered.size} из ${lib.size}",
                                color = Color(0xFF6A6A6A),
                                style = MaterialTheme.typography.labelSmall
                            )
                            
                            Box(Modifier.align(Alignment.CenterEnd)) {
                                TextButton(
                                    onClick = { showSortMenu = true },
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) { Text("${stringResource(R.string.lib_sort)} ▼", color = Red) }
                                DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }, containerColor = Color(0xFF1E1E1E)) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.lib_sort_date_newest), color = Color.White) }, onClick = { sortMode = 3; isShuffled = false; showSortMenu = false })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.lib_sort_default), color = Color.White) }, onClick = { sortMode = 0; isShuffled = false; showSortMenu = false })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.lib_sort_title), color = Color.White) }, onClick = { sortMode = 1; isShuffled = false; showSortMenu = false })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.lib_sort_artist), color = Color.White) }, onClick = { sortMode = 2; isShuffled = false; showSortMenu = false })
                                }
                            }
                        }
                    }
                }

                if (isScanning) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Red)
                            Spacer(Modifier.height(8.dp))
                            Text("Сканирование библиотеки...", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(
                            items = filtered,
                            key = { _, track -> track.id },
                            contentType = { _, _ -> "track" }
                        ) { idx, track ->
                            val isSelected = selectedTracks.contains(track.id)
                            TrackRow(
                                context = context,
                                track = track,
                                posterUri = trackPosters[track.id],
                                isSelected = isSelected,
                                onClick = {
                                    if (selectedTracks.isEmpty()) { 
                                        if (state.currentTrackId == track.id) onTrackClick()
                                        else { vm.playTracks(filtered, idx, "Мои треки"); onTrackClick() }
                                    } else { 
                                        selectedTracks = if (isSelected) selectedTracks - track.id else selectedTracks + track.id 
                                    }
                                },
                                onLongClick = { if (!isSelected) selectedTracks = selectedTracks + track.id },
                                onMenuClick = { showMenuForTrack = track },
                                showMenu = showMenuForTrack?.id == track.id,
                                onMenuDismiss = { showMenuForTrack = null },
                                onPlayNext = { vm.playNext(listOf(track)); showMenuForTrack = null },
                                onAddToQueue = { vm.addToQueue(listOf(track)); showMenuForTrack = null },
                                onAddToPlaylist = { 
                                    selectedTracks = listOf(track.id)
                                    showAddToPlaylist = true
                                    showMenuForTrack = null 
                                },
                                onRename = { trackToRename = track; showMenuForTrack = null },
                                onDelete = { trackToDelete = track; showMenuForTrack = null }
                            )
                        }
                    }
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

    if (showAddToPlaylist) {
        if (showCreateFromAdd) {
            PlaylistEditorDialog(
                initialName = "",
                onSave = { name, desc, poster ->
                    val tracksToAdd = selectedTracks.toList()
                    vm.createPlaylist(name, desc, poster) { newId ->
                        vm.addMultipleToPlaylist(newId, tracksToAdd)
                    }
                    showAddToPlaylist = false
                    showCreateFromAdd = false
                    selectedTracks = emptyList()
                },
                onDismiss = { showCreateFromAdd = false }
            )
        } else {
            AlertDialog(
                onDismissRequest = { showAddToPlaylist = false },
                title = { Text(stringResource(R.string.playlists_add_to), color = Color.White) },
                text = {
                    Column {
                        playlists.forEach { p ->
                            TextButton(onClick = { 
                                vm.addMultipleToPlaylist(p.id, selectedTracks)
                                showAddToPlaylist = false
                                selectedTracks = emptyList()
                            }) { Text(p.name, color = Color.White) }
                        }
                        TextButton(onClick = { showCreateFromAdd = true }) { Text("+ Создать новый", color = Red) }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { showAddToPlaylist = false; showCreateFromAdd = false }) { Text(stringResource(R.string.lib_cancel), color = Color.Gray) } },
                containerColor = Color(0xFF121212)
            )
        }
    }

    trackToDelete?.let { track ->
        AlertDialog(
            onDismissRequest = { trackToDelete = null }, title = { Text(stringResource(R.string.dialog_delete_title), color = Color.White) },
            text = { Text(stringResource(R.string.dialog_delete_message), color = Color.Gray) },
            confirmButton = { TextButton(onClick = { pendingDeleteTrack = track; trackToDelete = null }) { Text(stringResource(R.string.action_delete), color = Red) } },
            dismissButton = { TextButton(onClick = { trackToDelete = null }) { Text(stringResource(R.string.lib_cancel), color = Color.Gray) } }, containerColor = Color(0xFF121212)
        )
    }

    trackToRename?.let { track ->
        var newTitle by remember { mutableStateOf(track.title) }
        AlertDialog(
            onDismissRequest = { trackToRename = null }, title = { Text(stringResource(R.string.dialog_rename_title), color = Color.White) },
            text = { OutlinedTextField(value = newTitle, onValueChange = { newTitle = it }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Red, unfocusedBorderColor = Color.DarkGray, focusedTextColor = Color.White, unfocusedTextColor = Color.White)) },
            confirmButton = { 
                TextButton(onClick = { 
                    if (newTitle.isNotBlank()) { 
                        vm.renameTrack(track, newTitle)
                        trackToRename = null 
                    } 
                }) { Text(stringResource(R.string.action_save), color = Red) } 
            },
            dismissButton = { TextButton(onClick = { trackToRename = null }) { Text(stringResource(R.string.lib_cancel), color = Color.Gray) } }, containerColor = Color(0xFF121212)
        )
    }
}

@Composable
private fun TrackRow(
    context: android.content.Context,
    track: Track,
    posterUri: String?,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenuClick: () -> Unit,
    showMenu: Boolean,
    onMenuDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (isSelected) Red.copy(alpha = 0.2f) else Color.Transparent)
            .pointerInput(track.id) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() }
                )
            }
            .drawBehind {
                val y = size.height - 1f
                drawLine(
                    color = Color(0xFF444444).copy(alpha = 0.3f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f
                )
            }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)).background(Surface),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(posterUri)
                    .crossfade(false)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                placeholder = painterResource(id = R.drawable.ic_fallback_music),
                error = painterResource(id = R.drawable.ic_fallback_music)
            )
        }
        
        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(track.title, color = Color.White, style = MaterialTheme.typography.bodyMedium)
            Text("${track.artist} • ${track.album}", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
        }
        
        if (isSelected) {
            Text("✓", color = Red, style = MaterialTheme.typography.headlineSmall)
        } else {
            Box {
                Box(
                    Modifier.size(36.dp).clickable(onClick = onMenuClick),
                    contentAlignment = Alignment.Center
                ) {
                    Text("⋮", color = Color.Gray, style = MaterialTheme.typography.titleLarge)
                }
                if (showMenu) {
                    DropdownMenu(
                        expanded = true,
                        onDismissRequest = onMenuDismiss,
                        containerColor = Color(0xFF1E1E1E)
                    ) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.menu_play_next), color = Color.White) }, onClick = onPlayNext)
                        DropdownMenuItem(text = { Text(stringResource(R.string.menu_add_to_queue), color = Color.White) }, onClick = onAddToQueue)
                        DropdownMenuItem(text = { Text(stringResource(R.string.menu_add_to_playlist), color = Color.White) }, onClick = onAddToPlaylist)
                        DropdownMenuItem(text = { Text(stringResource(R.string.menu_rename), color = Color.White) }, onClick = onRename)
                        DropdownMenuItem(text = { Text(stringResource(R.string.menu_delete), color = Red) }, onClick = onDelete)
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistEditorDialog(
    initialName: String = "",
    initialDesc: String = "",
    initialPoster: String? = null,
    onSave: (String, String?, String?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initialName) }
    var desc by remember { mutableStateOf(initialDesc) }
    var poster by remember { mutableStateOf(initialPoster) }
    
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) { }
            poster = uri.toString()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialName.isEmpty()) "Новый плейлист" else "Редактировать", color = Color.White) },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Название") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Red, unfocusedBorderColor = Color.DarkGray, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = desc, onValueChange = { desc = it },
                    label = { Text("Описание") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Red, unfocusedBorderColor = Color.DarkGray, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { imagePicker.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)) {
                    Text(if (poster == null) "Выбрать постер" else "Постер выбран", color = Color.White)
                }
            }
        },
        confirmButton = { 
            TextButton(onClick = { if (name.isNotBlank()) onSave(name, desc.ifBlank { null }, poster) }) { Text("СОХРАНИТЬ", color = Red) } 
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ОТМЕНА", color = Color.Gray) } },
        containerColor = Color(0xFF121212)
    )
}

@Composable
fun DeletionTimerSnackbar(track: Track, onDelete: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    var timeLeft by remember { mutableStateOf(6) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    val animatedOffset by animateFloatAsState(targetValue = dragOffsetY, label = "offset")

    LaunchedEffect(track.id) {
        while (timeLeft > 0) {
            delay(1000)
            timeLeft--
        }
        if (timeLeft == 0) onDelete()
    }

    Card(
        modifier = modifier
            .fillMaxWidth(0.9f)
            .offset { IntOffset(0, animatedOffset.roundToInt()) }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dragOffsetY > 100f) onDelete()
                        else dragOffsetY = 0f
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        dragOffsetY += dragAmount
                    }
                )
            },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text("Удаление: ${track.title}", color = Color.White, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$timeLeft сек", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.width(16.dp))
                TextButton(onClick = onCancel) { Text("ОТМЕНИТЬ", color = Red, style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}
