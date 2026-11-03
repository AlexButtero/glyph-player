package com.nothing.glyphplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.nothing.glyphplayer.data.Track
import com.nothing.glyphplayer.ui.PlayerViewModel
import com.nothing.glyphplayer.ui.theme.Red
import kotlinx.coroutines.Dispatchers
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

fun normalizeForGrouping(name: String): String {
    return NON_ALNUM_REGEX.replace(name.lowercase(), "").trim()
}

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

fun buildArtistsMap(library: List<Track>): Map<String, ArtistGroup> {
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
        // Ищем первый трек с постером
        val posterUri = uniqueTracks.firstOrNull { !it.artUri.isNullOrBlank() }?.artUri
        ArtistGroup(canonicalName, uniqueTracks, isPrimary, posterUri)
    }
}

@Composable
fun ArtistsScreen(vm: PlayerViewModel = viewModel(), onTrackClick: () -> Unit) {
    val lib by vm.library.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var grouped by remember { mutableStateOf<Map<String, ArtistGroup>>(emptyMap()) }
    var isCalculating by remember { mutableStateOf(false) }

    LaunchedEffect(lib) {
        if (lib.isNotEmpty()) {
            isCalculating = true
            grouped = withContext(Dispatchers.Default) { buildArtistsMap(lib) }
            isCalculating = false
        } else {
            grouped = emptyMap()
        }
    }

    val displayedArtists = remember(grouped, searchQuery) {
        val query = searchQuery.trim().lowercase()
        val filtered = if (query.isEmpty()) {
            grouped.values.filter { it.isPrimary }
        } else {
            grouped.values.filter { it.name.lowercase().contains(query) }
        }
        filtered.sortedByDescending { it.tracks.size }.map { it.name }
    }

    var selectedArtist by remember { mutableStateOf<String?>(null) }

    if (selectedArtist != null) {
        val group = grouped[selectedArtist]
        if (group != null) {
            ArtistTracksScreen(artistName = group.name, tracks = group.tracks, onBack = { selectedArtist = null }, vm = vm, onTrackClick = onTrackClick)
        } else {
            selectedArtist = null
        }
        return
    }

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
            LazyColumn(Modifier.fillMaxSize()) {
                items(displayedArtists, key = { it }) { artistName ->
                    val group = grouped[artistName]
                    val trackCount = group?.tracks?.size ?: 0
                    Row(
                        Modifier.fillMaxWidth().clickable { selectedArtist = artistName }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Круглый постер или буква
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(Color(0xFF222222)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (group?.posterUri != null) {
                                AsyncImage(
                                    model = group.posterUri,
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

@Composable
fun ArtistTracksScreen(artistName: String, tracks: List<Track>, onBack: () -> Unit, vm: PlayerViewModel, onTrackClick: () -> Unit) {
    var dragOffsetX by remember { mutableStateOf(0f) }
    Box(modifier = Modifier.fillMaxSize().pointerInput(Unit) {
        detectDragGestures(onDragEnd = { if (dragOffsetX > 150f) onBack(); dragOffsetX = 0f }, onDrag = { change, dragAmount -> change.consume(); dragOffsetX += dragAmount.x })
    }) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White) } }
            Text(artistName, style = MaterialTheme.typography.headlineLarge, color = Color.White)
            Spacer(Modifier.height(16.dp))
            LazyColumn(Modifier.fillMaxSize()) {
                items(tracks, key = { it.id }) { track ->
                    Row(Modifier.fillMaxWidth().clickable { vm.playTracks(tracks, tracks.indexOf(track), artistName); onTrackClick() }.padding(vertical = 12.dp)) {
                        Text(track.title, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    }
                    HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.3f))
                }
            }
        }
    }
}
