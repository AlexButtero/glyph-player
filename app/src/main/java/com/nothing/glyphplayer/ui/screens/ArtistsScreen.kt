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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nothing.glyphplayer.data.Track
import com.nothing.glyphplayer.ui.PlayerViewModel
import com.nothing.glyphplayer.ui.theme.Red
import java.net.URLDecoder

// ============================================================================
// ЛОГИКА УМНОЙ ГРУППИРОВКИ АРТИСТОВ
// ============================================================================

// Очистка от мусора
fun cleanArtistName(raw: String): String {
    var clean = raw
    try {
        // Раскодируем URL (например 25%2f17 -> 25/17)
        clean = URLDecoder.decode(clean, "UTF-8")
    } catch (_: Exception) {}
    // Удаляем все, что в квадратных скобках (типа [muzmo.ru])
    clean = clean.replace(Regex("\\[.*?\\]"), "")
    // Удаляем (feat. ...)
    clean = clean.replace(Regex("(?i)\\(.*?feat.*?\\)"), "")
    // Убираем лишние пробелы и кавычки по краям
    clean = clean.trim().removeSurrounding("\"").removeSurrounding("'").trim()
    return clean
}

// Разбивка строки на нескольких артистов
fun splitArtists(rawArtist: String?): List<String> {
    if (rawArtist.isNullOrBlank() || rawArtist.equals("<unknown>", true)) return emptyList()
    
    // Заменяем разделители на "|"
    // Ищем feat, ft, &, запятую, vs, x
    val processed = rawArtist.replace(Regex("(?i)\\s*(feat\\.?|ft\\.?|&|,|vs\\.?|x)\\s*"), "|")
    
    return processed.split("|")
        .map { cleanArtistName(it) }
        .filter { it.isNotBlank() && it.length > 1 && !it.equals("<unknown>", true) }
}

// Извлечение артистов из трека (с фолбэком на имя файла/название)
fun extractArtists(track: Track): List<String> {
    val fromMeta = splitArtists(track.artist)
    if (fromMeta.isNotEmpty()) return fromMeta

    // Если в метаданных <unknown>, пробуем спарсить название "Artist - Title"
    if (track.title.contains(" - ")) {
        val parts = track.title.split(" - ", limit = 2)
        if (parts.size == 2) {
            val fromTitle = splitArtists(parts[0])
            if (fromTitle.isNotEmpty()) return fromTitle
        }
    }
    
    return listOf("Unknown Artist")
}

// Нормализация для нечеткого поиска (Fuzzy Match)
fun normalizeForGrouping(name: String): String {
    return name.lowercase()
        .replace(Regex("[^a-zа-я0-9]"), "") // Оставляем только буквы и цифры
        .trim()
}

// Проверка на схожесть (первые 4 символа)
fun isSimilarArtist(a: String, b: String): Boolean {
    if (a == b) return true
    if (a.length < 3 || b.length < 3) return false
    
    val normA = normalizeForGrouping(a)
    val normB = normalizeForGrouping(b)
    if (normA == normB) return true
    
    // Если первые 4 символа совпадают (или вся строка, если она короче 4)
    val prefixLen = minOf(4, minOf(normA.length, normB.length))
    return prefixLen >= 3 && normA.take(prefixLen) == normB.take(prefixLen)
}

// Главный метод сборки карты: "Артист" -> [Треки]
fun buildArtistsMap(library: List<Track>): Map<String, List<Track>> {
    // 1. Собираем всех-всех артистов из всех треков
    val allArtists = mutableSetOf<String>()
    val trackToArtists = mutableMapOf<Track, List<String>>()
    
    library.forEach { track ->
        val artists = extractArtists(track)
        trackToArtists[track] = artists
        allArtists.addAll(artists)
    }
    
    // 2. Группируем похожих артистов
    // (например "Anacondaz" и "anacondaz" объединятся)
    val canonicalMap = mutableMapOf<String, String>() // Исходное имя -> Каноничное
    val processed = mutableSetOf<String>()
    
    val sortedArtists = allArtists.sortedBy { it.length } // Сначала короткие (они вероятнее "правильные")
    
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
        
        // Выбираем самое красивое имя для группы (с заглавной буквы, самое длинное)
        val bestName = group.maxByOrNull { it.length } ?: current
        val capitalizedName = bestName.replaceFirstChar { it.uppercase() }
        
        group.forEach { canonicalMap[it] = capitalizedName }
    }
    
    // 3. Формируем итоговую мапу
    val result = mutableMapOf<String, MutableList<Track>>()
    trackToArtists.forEach { (track, artists) ->
        artists.forEach { rawArtist ->
            val canonical = canonicalMap[rawArtist] ?: rawArtist
            result.getOrPut(canonical) { mutableListOf() }.add(track)
        }
    }
    
    // Удаляем дубликаты треков внутри одного артиста
    return result.mapValues { (_, tracks) -> tracks.distinctBy { it.id } }
}

// ============================================================================
// UI
// ============================================================================

@Composable
fun ArtistsScreen(vm: PlayerViewModel = viewModel(), onTrackClick: () -> Unit) {
    val lib by vm.library.collectAsState()
    
    // Оптимизация: пересчитываем только при изменении библиотеки
    val grouped = remember(lib) { buildArtistsMap(lib) }
    val sortedArtists = remember(grouped) { grouped.keys.sorted() }
    
    var selectedArtist by remember { mutableStateOf<String?>(null) }

    if (selectedArtist != null) {
        ArtistTracksScreen(artistName = selectedArtist!!, tracks = grouped[selectedArtist] ?: emptyList(), onBack = { selectedArtist = null }, vm = vm, onTrackClick = onTrackClick)
        return
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("ИСПОЛНИТЕЛИ", style = MaterialTheme.typography.headlineLarge, color = Color.White)
        Spacer(Modifier.height(16.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            items(sortedArtists, key = { it }) { artistName ->
                val trackCount = grouped[artistName]?.size ?: 0
                Row(Modifier.fillMaxWidth().clickable { selectedArtist = artistName }.padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(artistName, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    Text("$trackCount", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                }
                HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.3f))
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
