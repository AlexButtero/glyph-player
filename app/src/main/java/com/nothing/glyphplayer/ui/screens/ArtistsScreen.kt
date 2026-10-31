package com.nothing.glyphplayer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nothing.glyphplayer.data.Track
import com.nothing.glyphplayer.ui.PlayerViewModel
import com.nothing.glyphplayer.ui.theme.Red

// Функция нормализации имени исполнителя
fun String.normalizeArtist(): String {
    return this.lowercase()
        .replace(Regex("(?i)\\s*(feat|ft|&|,|vs)\\s*.*$"), "")
        .replace(Regex("(?i)^the\\s+"), "")
        .trim()
}

@Composable
fun ArtistsScreen(vm: PlayerViewModel = viewModel(), onTrackClick: () -> Unit) {
    val lib by vm.library.collectAsState()
    
    // Группировка по нормализованному имени
    val grouped = lib.groupBy { it.artist.normalizeArtist() }
    val sortedArtists = grouped.keys.sorted()

    var selectedArtist by remember { mutableStateOf<String?>(null) }

    if (selectedArtist != null) {
        val tracks = grouped[selectedArtist] ?: emptyList()
        ArtistTracksScreen(artistName = selectedArtist!!, tracks = tracks, onBack = { selectedArtist = null }, vm = vm, onTrackClick = onTrackClick)
        return
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("ИСПОЛНИТЕЛИ", style = MaterialTheme.typography.headlineLarge, color = Color.White)
        Spacer(Modifier.height(16.dp))
        
        LazyColumn(Modifier.fillMaxSize()) {
            items(sortedArtists) { artistKey ->
                val displayName = grouped[artistKey]?.firstOrNull()?.artist ?: artistKey
                val trackCount = grouped[artistKey]?.size ?: 0
                
                Row(
                    Modifier.fillMaxWidth().clickable { selectedArtist = artistKey }.padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(displayName, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    Text("$trackCount", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                }
                HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.3f))
            }
        }
    }
}

@Composable
fun ArtistTracksScreen(artistName: String, tracks: List<Track>, onBack: () -> Unit, vm: PlayerViewModel, onTrackClick: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            TextButton(onClick = onBack) { Text("←", color = Color.White, style = MaterialTheme.typography.headlineLarge) }
        }
        Text(artistName, style = MaterialTheme.typography.headlineLarge, color = Color.White)
        Spacer(Modifier.height(16.dp))
        
        LazyColumn(Modifier.fillMaxSize()) {
            items(tracks) { track ->
                Row(
                    Modifier.fillMaxWidth().clickable { 
                        vm.playTracks(tracks, tracks.indexOf(track))
                        onTrackClick() 
                    }.padding(vertical = 12.dp)
                ) {
                    Text(track.title, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                }
                HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.3f))
            }
        }
    }
}
