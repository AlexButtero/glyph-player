package com.nothing.glyphplayer.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.nothing.glyphplayer.R
import com.nothing.glyphplayer.data.Playlist
import com.nothing.glyphplayer.data.Track
import com.nothing.glyphplayer.ui.PlayerViewModel
import com.nothing.glyphplayer.ui.theme.Red
import com.nothing.glyphplayer.ui.theme.Surface
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlaylistsScreen(
    vm: PlayerViewModel = viewModel(), 
    trackPosters: Map<Long, String>,
    onPlaylistClick: () -> Unit
) {
    val playlists by vm.playlists.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    
    var openedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var playlistTracks by remember { mutableStateOf<List<Track>>(emptyList()) }

    var playlistToDelete by remember { mutableStateOf<Playlist?>(null) }
    var pendingDeletePlaylist by remember { mutableStateOf<Playlist?>(null) }

    var playlistToEdit by remember { mutableStateOf<Playlist?>(null) }
    
    // Для меню треков внутри плейлиста
    var showMenuForTrack by remember { mutableStateOf<Track?>(null) }
    var trackToDelete by remember { mutableStateOf<Track?>(null) }
    var trackToRename by remember { mutableStateOf<Track?>(null) }
    var pendingDeleteTrack by remember { mutableStateOf<Track?>(null) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var trackForPlaylist by remember { mutableStateOf<Track?>(null) }
    
    // Мультивыбор для треков в плейлисте
    var selectedTracks by remember { mutableStateOf(listOf<Long>()) }
    
    LaunchedEffect(openedPlaylist) {
        openedPlaylist?.let { p ->
            vm.getPlaylistTracks(p.id) { tracks -> playlistTracks = tracks }
        }
    }

    if (openedPlaylist != null) {
        var dragOffsetX by remember { mutableStateOf(0f) }
        Box(modifier = Modifier.fillMaxSize().pointerInput(Unit) {
            detectDragGestures(onDragEnd = { if (dragOffsetX > 150f) openedPlaylist = null; dragOffsetX = 0f }, onDrag = { change, dragAmount -> change.consume(); dragOffsetX += dragAmount.x })
        }) {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                        IconButton(onClick = { openedPlaylist = null }) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White) }
                    }
                    
                    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(100.dp).clip(RoundedCornerShape(8.dp)).background(Surface), contentAlignment = Alignment.Center) {
                            if (openedPlaylist!!.posterUri != null) {
                                AsyncImage(model = openedPlaylist!!.posterUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            } else {
                                Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(openedPlaylist!!.name, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                            if (!openedPlaylist!!.description.isNullOrBlank()) {
                                Text(openedPlaylist!!.description!!, style = MaterialTheme.typography.bodySmall, color = Color.Gray, maxLines = 3)
                            }
                            Text("${playlistTracks.size} треков", color = Color.DarkGray, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    
                    if (selectedTracks.isNotEmpty()) {
                        Row(
                            Modifier.fillMaxWidth().background(Color(0xFF1A1A1A)).padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${selectedTracks.size}", color = Color.White, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { 
                                    vm.playNext(selectedTracks.mapNotNull { id -> playlistTracks.find { it.id == id } }); 
                                    selectedTracks = emptyList() 
                                }, contentPadding = PaddingValues(4.dp)) { 
                                    Text("Следующими", color = Red, style = MaterialTheme.typography.labelSmall) 
                                }
                                TextButton(onClick = { 
                                    vm.addToQueue(selectedTracks.mapNotNull { id -> playlistTracks.find { it.id == id } }); 
                                    selectedTracks = emptyList() 
                                }, contentPadding = PaddingValues(4.dp)) { 
                                    Text("В очередь", color = Red, style = MaterialTheme.typography.labelSmall) 
                                }
                                IconButton(onClick = { selectedTracks = emptyList() }) { 
                                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray) 
                                }
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(playlistTracks, key = { it.id }) { track ->
                            val isSelected = selectedTracks.contains(track.id)
                            
                            Row(Modifier.fillMaxWidth()
                                .background(if (isSelected) Red.copy(alpha = 0.2f) else Color.Transparent)
                                .combinedClickable(
                                    onClick = {
                                        if (selectedTracks.isEmpty()) { 
                                            vm.playTracks(playlistTracks, playlistTracks.indexOf(track), openedPlaylist!!.name)
                                            onPlaylistClick() 
                                        } else { 
                                            selectedTracks = if (isSelected) selectedTracks - track.id else selectedTracks + track.id 
                                        }
                                    },
                                    onLongClick = { if (!isSelected) selectedTracks = selectedTracks + track.id }
                                )
                                .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)).background(Surface), contentAlignment = Alignment.Center) {
                                    AsyncImage(
                                        model = trackPosters[track.id],
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                        error = painterResource(id = R.drawable.ic_fallback_music),
                                        placeholder = painterResource(id = R.drawable.ic_fallback_music)
                                    )
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
                                                showAddToPlaylistDialog = true 
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
                        onDelete = { 
                            vm.deleteTrack(pendingDeleteTrack!!)
                            pendingDeleteTrack = null
                            openedPlaylist?.let { p -> vm.getPlaylistTracks(p.id) { playlistTracks = it } }
                        },
                        onCancel = { pendingDeleteTrack = null },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
                    )
                }
            }
        }
    } else {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Text(stringResource(R.string.nav_playlists), style = MaterialTheme.typography.headlineLarge, color = Color.White)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { showCreate = true }, colors = ButtonDefaults.buttonColors(containerColor = Red), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.playlists_create)) }
                Spacer(Modifier.height(16.dp))
                
                LazyColumn(Modifier.fillMaxSize()) {
                    items(playlists, key = { it.id }) { p ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart) {
                                    playlistToDelete = p
                                }
                                false
                            }
                        )

                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = false,
                            backgroundContent = {
                                val color = if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) Red else Color.Transparent
                                Box(Modifier.fillMaxSize().background(color).padding(horizontal = 20.dp), contentAlignment = Alignment.CenterEnd) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White)
                                }
                            }
                        ) {
                            Row(Modifier.fillMaxWidth().background(Color.Black).clickable { openedPlaylist = p }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)).background(Surface), contentAlignment = Alignment.Center) {
                                    if (p.posterUri != null) AsyncImage(model = p.posterUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                    else Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.Gray)
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(p.name, color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                
                                IconButton(onClick = { playlistToEdit = p }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.Gray, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                        HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.3f))
                    }
                }
            }

            if (pendingDeletePlaylist != null) {
                PlaylistDeletionTimerSnackbar(
                    playlistName = pendingDeletePlaylist!!.name,
                    onDelete = { vm.deletePlaylist(pendingDeletePlaylist!!.id); pendingDeletePlaylist = null },
                    onCancel = { pendingDeletePlaylist = null },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
                )
            }
        }
    }

    // ДИАЛОГИ
    if (showAddToPlaylistDialog && trackForPlaylist != null) {
        AlertDialog(
            onDismissRequest = { showAddToPlaylistDialog = false; trackForPlaylist = null }, title = { Text(stringResource(R.string.playlists_add_to), color = Color.White) },
            text = { 
                Column { 
                    playlists.forEach { p -> TextButton(onClick = { vm.addMultipleToPlaylist(p.id, listOf(trackForPlaylist!!.id)); showAddToPlaylistDialog = false; trackForPlaylist = null }) { Text(p.name, color = Color.White) } }
                } 
            },
            confirmButton = {}, dismissButton = { TextButton(onClick = { showAddToPlaylistDialog = false; trackForPlaylist = null }) { Text(stringResource(R.string.lib_cancel), color = Color.Gray) } }, containerColor = Color(0xFF121212)
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

    playlistToDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            title = { Text("Удалить плейлист?", color = Color.White) },
            text = { Text("Вы уверены, что хотите удалить плейлист «${p.name}»? Сами треки останутся на устройстве.", color = Color.Gray) },
            confirmButton = { 
                TextButton(onClick = { 
                    pendingDeletePlaylist = p
                    playlistToDelete = null 
                }) { Text("УДАЛИТЬ", color = Red) } 
            },
            dismissButton = { TextButton(onClick = { playlistToDelete = null }) { Text("ОТМЕНА", color = Color.Gray) } },
            containerColor = Color(0xFF121212)
        )
    }

    if (playlistToEdit != null) {
        PlaylistEditorDialog(
            initialName = playlistToEdit!!.name,
            initialDesc = playlistToEdit!!.description ?: "",
            initialPoster = playlistToEdit!!.posterUri,
            onSave = { name, desc, poster ->
                vm.updatePlaylist(playlistToEdit!!.id, name, desc, poster)
                playlistToEdit = null
            },
            onDismiss = { playlistToEdit = null }
        )
    }

    if (showCreate) {
        PlaylistEditorDialog(
            onSave = { name, desc, poster ->
                vm.createPlaylist(name, desc, poster)
                showCreate = false
            },
            onDismiss = { showCreate = false }
        )
    }
}

@Composable
fun PlaylistDeletionTimerSnackbar(playlistName: String, onDelete: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    var timeLeft by remember { mutableStateOf(6) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    val animatedOffset by animateFloatAsState(targetValue = dragOffsetY, label = "offset")

    LaunchedEffect(playlistName) {
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
                Text("Удаление: $playlistName", color = Color.White, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$timeLeft сек", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.width(16.dp))
                TextButton(onClick = onCancel) { Text("ОТМЕНИТЬ", color = Red, style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}
