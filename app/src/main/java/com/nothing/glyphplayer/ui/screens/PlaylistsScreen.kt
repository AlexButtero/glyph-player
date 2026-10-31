package com.nothing.glyphplayer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nothing.glyphplayer.R
import com.nothing.glyphplayer.data.Playlist
import com.nothing.glyphplayer.data.Track
import com.nothing.glyphplayer.ui.PlayerViewModel
import com.nothing.glyphplayer.ui.theme.Red

@Composable
fun PlaylistsScreen(vm: PlayerViewModel = viewModel(), onPlaylistClick: () -> Unit) {
    val playlists by vm.playlists.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    var openedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var playlistTracks by remember { mutableStateOf<List<Track>>(emptyList()) }

    // Загрузка треков при открытии плейлиста
    LaunchedEffect(openedPlaylist) {
        openedPlaylist?.let { p ->
            playlistTracks = vm.repo.tracksOf(p.id)
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

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(stringResource(R.string.nav_playlists), style = MaterialTheme.typography.headlineLarge, color = Color.White)
        Spacer(Modifier.height(16.dp))
        Button(onClick = { showCreate = true }, colors = ButtonDefaults.buttonColors(containerColor = Red), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.playlists_create)) }
        Spacer(Modifier.height(16.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            items(playlists) { p ->
                Row(Modifier.fillMaxWidth().clickable { openedPlaylist = p }.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(p.name, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { vm.deletePlaylist(p.id) }) { Text(stringResource(R.string.playlists_delete), color = Color.Gray, style = MaterialTheme.typography.labelSmall) }
                }
                HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.3f))
            }
        }
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
