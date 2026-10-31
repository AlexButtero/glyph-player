package com.nothing.glyphplayer.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nothing.glyphplayer.R
import com.nothing.glyphplayer.ui.PlayerViewModel
import com.nothing.glyphplayer.ui.theme.Red

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(vm: PlayerViewModel = viewModel(), onTrackClick: () -> Unit) {
    val lib by vm.library.collectAsState()
    val playlists by vm.playlists.collectAsState()
    
    var search by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(3) } // 3: Date Newest
    var showSortMenu by remember { mutableStateOf(false) }
    
    var selectedTracks by remember { mutableStateOf(setOf<Long>()) }
    var showAddToPlaylist by remember { mutableStateOf(false) }

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

    Column(Modifier.fillMaxSize()) {
        if (selectedTracks.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().background(Color(0xFF1E1E1E)).padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.lib_selected, selectedTracks.size), color = Color.White)
                Row {
                    TextButton(onClick = { showAddToPlaylist = true }) { Text(stringResource(R.string.lib_add_to_playlist), color = Red) }
                    TextButton(onClick = { selectedTracks = emptySet() }) { Text(stringResource(R.string.lib_cancel), color = Color.Gray) }
                }
            }
        } else {
            OutlinedTextField(
                value = search, onValueChange = { search = it },
                placeholder = { Text(stringResource(R.string.lib_search_hint), color = Color.Gray) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Red, unfocusedBorderColor = Color.DarkGray, focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                modifier = Modifier.fillMaxWidth().padding(8.dp)
            )
            
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { vm.shuffleAll(); onTrackClick() }) { Text("⤨ ${stringResource(R.string.lib_shuffle)}", color = Red) }
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
                            onClick = {
                                if (selectedTracks.isEmpty()) { vm.playTracks(filtered, idx); onTrackClick() }
                                else { selectedTracks = if (isSelected) selectedTracks - track.id else selectedTracks + track.id }
                            },
                            onLongClick = { selectedTracks = selectedTracks + track.id }
                        ).padding(12.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(track.title, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                        Text("${track.artist} • ${track.album}", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                    }
                    if (isSelected) Text("✓", color = Red, style = MaterialTheme.typography.headlineSmall)
                }
                HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.3f))
            }
        }
    }

    if (showAddToPlaylist && playlists.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showAddToPlaylist = false },
            title = { Text(stringResource(R.string.playlists_add_to), color = Color.White) },
            text = {
                Column {
                    playlists.forEach { p ->
                        TextButton(onClick = {
                            vm.addMultipleToPlaylist(p.id, selectedTracks.toList())
                            showAddToPlaylist = false
                            selectedTracks = emptySet()
                        }) { Text(p.name, color = Color.White) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showAddToPlaylist = false }) { Text(stringResource(R.string.lib_cancel), color = Color.Gray) } },
            containerColor = Color(0xFF121212)
        )
    }
}
