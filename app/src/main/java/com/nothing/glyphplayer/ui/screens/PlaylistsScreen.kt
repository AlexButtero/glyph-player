package com.nothing.glyphplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nothing.glyphplayer.R
import com.nothing.glyphplayer.data.Playlist
import com.nothing.glyphplayer.data.Track
import com.nothing.glyphplayer.ui.PlayerViewModel
import com.nothing.glyphplayer.ui.theme.Red
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(vm: PlayerViewModel = viewModel(), onPlaylistClick: () -> Unit) {
    val playlists by vm.playlists.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    
    var openedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var playlistTracks by remember { mutableStateOf<List<Track>>(emptyList()) }

    var playlistToDelete by remember { mutableStateOf<Playlist?>(null) }
    var pendingDeletePlaylist by remember { mutableStateOf<Playlist?>(null) }

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
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                    IconButton(onClick = { openedPlaylist = null }) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White) }
                }
                Text(openedPlaylist!!.name, style = MaterialTheme.typography.headlineLarge, color = Color.White)
                Spacer(Modifier.height(16.dp))
                LazyColumn(Modifier.fillMaxSize()) {
                    items(playlistTracks) { track ->
                        Row(Modifier.fillMaxWidth().clickable { 
                            vm.playTracks(playlistTracks, playlistTracks.indexOf(track))
                            onPlaylistClick() 
                        }.padding(vertical = 12.dp)) {
                            Text(track.title, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                        }
                        HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.3f))
                    }
                }
            }
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text(stringResource(R.string.nav_playlists), style = MaterialTheme.typography.headlineLarge, color = Color.White)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { showCreate = true }, colors = ButtonDefaults.buttonColors(containerColor = Red), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.playlists_create)) }
            Spacer(Modifier.height(16.dp))
            
            LazyColumn(Modifier.fillMaxSize()) {
                items(playlists) { p ->
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
                        Row(Modifier.fillMaxWidth().background(Color.Black).clickable { openedPlaylist = p }.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(p.name, color = Color.White, style = MaterialTheme.typography.bodyMedium)
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

    if (showCreate) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false }, title = { Text(stringResource(R.string.playlists_new), color = Color.White) },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.playlists_name), color = Color.Gray) }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Red, unfocusedBorderColor = Color.DarkGray, focusedTextColor = Color.White, unfocusedTextColor = Color.White)) },
            confirmButton = { TextButton(onClick = { if (name.isNotBlank()) { vm.createPlaylist(name); showCreate = false } }) { Text(stringResource(R.string.playlists_create_action), color = Red) } },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text(stringResource(R.string.lib_cancel), color = Color.Gray) } }, containerColor = Color(0xFF121212)
        )
    }
}

@Composable
fun PlaylistDeletionTimerSnackbar(playlistName: String, onDelete: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    var timeLeft by remember { mutableStateOf(6) }
    var dragOffsetY by remember { mutableStateOf(0f) }

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
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dragOffsetY > 50f) onDelete()
                        dragOffsetY = 0f
                    },
                    onVerticalDrag = { change, dragAmount -> // dragAmount это Float
                        change.consume()
                        dragOffsetY += dragAmount // ИСПРАВЛЕНО
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
                Text("Смахните вниз, чтобы удалить", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.width(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$timeLeft сек", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.width(16.dp))
                TextButton(onClick = onCancel) { Text("ОТМЕНИТЬ", color = Red, style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}
