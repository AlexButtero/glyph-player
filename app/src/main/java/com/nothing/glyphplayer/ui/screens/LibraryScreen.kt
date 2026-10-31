package com.nothing.glyphplayer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nothing.glyphplayer.R
import com.nothing.glyphplayer.data.Track
import com.nothing.glyphplayer.ui.PlayerViewModel
import com.nothing.glyphplayer.ui.theme.Red

@Composable
fun LibraryScreen(vm: PlayerViewModel = viewModel()) {
    val lib by vm.library.collectAsState()
    val playlists by vm.playlists.collectAsState()
    var tab by remember { mutableStateOf(0) }
    var search by remember { mutableStateOf("") }
    var showCreate by remember { mutableStateOf(false) }
    var showAddTrack by remember { mutableStateOf<Track?>(null) }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab, containerColor = Color.Black, contentColor = Red) {
            listOf(
                stringResource(R.string.lib_tracks),
                stringResource(R.string.lib_playlists)
            ).forEachIndexed { i, label ->
                Tab(
                    selected = tab == i,
                    onClick = { tab = i },
                    text = { Text(label, color = if (tab == i) Red else Color.Gray) }
                )
            }
        }

        if (tab == 0) {
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

            val filtered = lib.filter {
                search.isBlank() ||
                it.title.contains(search, true) ||
                it.artist.contains(search, true)
            }

            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(filtered) { idx, t ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            vm.playTracks(filtered, idx)
                        }.padding(12.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(t.title, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                            Text("${t.artist} • ${t.album}", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = { showAddTrack = t }) {
                            Text("＋", color = Red, style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                    HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.3f))
                }
            }
        } else {
            Button(
                onClick = { showCreate = true },
                colors = ButtonDefaults.buttonColors(containerColor = Red),
                modifier = Modifier.fillMaxWidth().padding(8.dp)
            ) { Text(stringResource(R.string.lib_create_playlist)) }

            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(playlists) { _, p ->
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.playPlaylist(p.id) }.padding(12.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                        }
                        TextButton(onClick = { vm.deletePlaylist(p.id) }) {
                            Text(stringResource(R.string.lib_delete),
                                color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.3f))
                }
            }
        }
    }

    if (showCreate) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text(stringResource(R.string.lib_new_playlist), color = Color.White) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.lib_name), color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Red,
                        unfocusedBorderColor = Color.DarkGray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) { vm.createPlaylist(name); showCreate = false }
                }) { Text(stringResource(R.string.lib_create), color = Red) }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }) {
                    Text(stringResource(R.string.lib_cancel), color = Color.Gray)
                }
            },
            containerColor = Color(0xFF121212)
        )
    }

    val trackToAdd = showAddTrack
    if (trackToAdd != null && playlists.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showAddTrack = null },
            title = { Text(stringResource(R.string.lib_add_to), color = Color.White) },
            text = {
                Column {
                    playlists.forEach { p ->
                        TextButton(onClick = {
                            vm.addToPlaylist(p.id, listOf(trackToAdd.id))
                            showAddTrack = null
                        }) { Text(p.name, color = Color.White) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAddTrack = null }) {
                    Text(stringResource(R.string.lib_cancel), color = Color.Gray)
                }
            },
            containerColor = Color(0xFF121212)
        )
    }
}
