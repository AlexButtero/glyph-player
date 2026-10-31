package com.nothing.glyphplayer.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nothing.glyphplayer.R
import com.nothing.glyphplayer.data.Track
import com.nothing.glyphplayer.ui.PlayerViewModel
import com.nothing.glyphplayer.ui.theme.Red

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(vm: PlayerViewModel = viewModel(), onTrackClick: () -> Unit) {
    val lib by vm.library.collectAsState()
    val playlists by vm.playlists.collectAsState()
    
    LaunchedEffect(Unit) { if (lib.isEmpty()) vm.scanLibrary { } }

    var search by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(3) }
    var showSortMenu by remember { mutableStateOf(false) }
    
    var selectedTracks by remember { mutableStateOf(listOf<Long>()) }
    var showAddToPlaylist by remember { mutableStateOf(false) }
    var showMenuForTrack by remember { mutableStateOf<Track?>(null) }
    var trackToDelete by remember { mutableStateOf<Track?>(null) }
    var trackToRename by remember { mutableStateOf<Track?>(null) }
    
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingDeleteTrack by remember { mutableStateOf<Track?>(null) }

    val filtered = lib.filter {
        search.isBlank() || it.title.contains(search, true) || it.artist.contains(search, true)
    }.let {
        when (sortMode) {
            1 -> it.sortedBy { t -> t.title }
            2 -> it.sortedBy { t -> t.artist }
            3 -> it.sortedByDescending { t -> t.dateAdded }
            else -> it
        }
    }

    LaunchedEffect(pendingDeleteTrack) {
        pendingDeleteTrack?.let { track ->
            val result = snackbarHostState.showSnackbar("Удаление через 6 сек...", "ОТМЕНИТЬ", duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) pendingDeleteTrack = null
            else { vm.deleteTrack(track); pendingDeleteTrack = null }
        }
    }

    Scaffold(containerColor = Color.Black, snackbarHost = { SnackbarHost(snackbarHostState) }) { paddingValues ->
        Column(Modifier.fillMaxSize().padding(paddingValues)) {
            if (selectedTracks.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().background(Color(0xFF1E1E1E)).padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.lib_selected, selectedTracks.size), color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    Row {
                        TextButton(onClick = { vm.playNext(selectedTracks.mapNotNull { id -> lib.find { it.id == id } }); selectedTracks = emptyList() }) { Text("⏭", color = Red, style = MaterialTheme.typography.titleLarge) }
                        TextButton(onClick = { vm.addToQueue(selectedTracks.mapNotNull { id -> lib.find { it.id == id } }); selectedTracks = emptyList() }) { Text("＋", color = Red, style = MaterialTheme.typography.titleLarge) }
                        TextButton(onClick = { showAddToPlaylist = true }) { Text("💾", color = Red, style = MaterialTheme.typography.titleLarge) }
                        TextButton(onClick = { selectedTracks = emptyList() }) { Text("✕", color = Color.Gray, style = MaterialTheme.typography.titleLarge) }
                    }
                }
            } else {
                OutlinedTextField(
                    value = search, onValueChange = { search = it }, placeholder = { Text(stringResource(R.string.lib_search_hint), color = Color.Gray) },
                    singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Red, unfocusedBorderColor = Color.DarkGray, focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                    modifier = Modifier.fillMaxWidth().padding(8.dp)
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { vm.shuffleAll(); onTrackClick() }) { 
                        Icon(Icons.Default.Shuffle, contentDescription = null, tint = Red, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.lib_shuffle), color = Red, style = MaterialTheme.typography.bodyMedium) 
                    }
                    Box {
                        TextButton(onClick = { showSortMenu = true }) { Text("${stringResource(R.string.lib_sort)} ▼", color = Red) }
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }, containerColor = Color(0xFF1E1E1E)) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.lib_sort_date_newest), color = Color.White) }, onClick = { sortMode = 3; showSortMenu = false })
                            DropdownMenuItem(text = { Text(stringResource(R.string.lib_sort_default), color = Color.White) }, onClick = { sortMode = 0; showSortMenu = false })
                            DropdownMenuItem(text = { Text(stringResource(R.string.lib_sort_title), color = Color.White) }, onClick = { sortMode = 1; showSortMenu = false })
                            DropdownMenuItem(text = { Text(stringResource(R.string.lib_sort_artist), color = Color.White) }, onClick = { sortMode = 2; showSortMenu = false })
                        }
                    }
                }
            }

            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(filtered) { idx, track ->
                    val isSelected = selectedTracks.contains(track.id)
                    Row(
                        Modifier.fillMaxWidth().background(if (isSelected) Red.copy(alpha = 0.2f) else Color.Transparent)
                            .combinedClickable(
                                onClick = { if (selectedTracks.isEmpty()) { vm.playTracks(filtered, idx); onTrackClick() } else { selectedTracks = if (isSelected) selectedTracks - track.id else selectedTracks + track.id } },
                                onLongClick = { if (!isSelected) selectedTracks = selectedTracks + track.id }
                            ).padding(12.dp), verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(track.title, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                            Text("${track.artist} • ${track.album}", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                        }
                        if (isSelected) Text("✓", color = Red, style = MaterialTheme.typography.headlineSmall)
                        else {
                            Box {
                                TextButton(onClick = { showMenuForTrack = track }) { Text("⋮", color = Color.Gray, style = MaterialTheme.typography.titleLarge) }
                                DropdownMenu(expanded = showMenuForTrack?.id == track.id, onDismissRequest = { showMenuForTrack = null }, containerColor = Color(0xFF1E1E1E)) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.menu_play_next), color = Color.White) }, onClick = { vm.playNext(listOf(track)); showMenuForTrack = null })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.menu_add_to_queue), color = Color.White) }, onClick = { vm.addToQueue(listOf(track)); showMenuForTrack = null })
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
    }

    if (showAddToPlaylist && playlists.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showAddToPlaylist = false }, title = { Text(stringResource(R.string.playlists_add_to), color = Color.White) },
            text = { Column { playlists.forEach { p -> TextButton(onClick = { vm.addMultipleToPlaylist(p.id, selectedTracks); showAddToPlaylist = false; selectedTracks = emptyList() }) { Text(p.name, color = Color.White) } } } },
            confirmButton = {}, dismissButton = { TextButton(onClick = { showAddToPlaylist = false }) { Text(stringResource(R.string.lib_cancel), color = Color.Gray) } }, containerColor = Color(0xFF121212)
        )
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
            confirmButton = { TextButton(onClick = { if (newTitle.isNotBlank()) { vm.renameTrack(track.id, newTitle); trackToRename = null } }) { Text(stringResource(R.string.action_save), color = Red) } },
            dismissButton = { TextButton(onClick = { trackToRename = null }) { Text(stringResource(R.string.lib_cancel), color = Color.Gray) } }, containerColor = Color(0xFF121212)
        )
    }
}
