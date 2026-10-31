package com.nothing.glyphplayer.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.nothing.glyphplayer.R
import com.nothing.glyphplayer.playback.EqualizerManager
import com.nothing.glyphplayer.ui.PlayerViewModel
import com.nothing.glyphplayer.ui.theme.Red
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun PlayerScreen(vm: PlayerViewModel = viewModel(), onBack: () -> Unit) {
    val state by vm.state.collectAsState()
    val lyrics by vm.lyrics.collectAsState()
    var showEq by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    
    var showLyrics by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    
    var sliderPos by remember { mutableStateOf<Float?>(null) }
    
    // Переменные для свайпов
    var dragOffsetX by remember { mutableStateOf(0f) }
    var dragOffsetY by remember { mutableStateOf(0f) }

    if (showLyrics) {
        LyricsScreen(vm, state.currentTrackId, state.positionMs, onBack = { showLyrics = false })
        return
    }
    
    if (showQueue) {
        QueueScreen(state.queue, state.currentIndex, onBack = { showQueue = false }, onSelect = { vm.seekToIndex(it); showQueue = false })
        return
    }

    Column(
        Modifier.fillMaxSize().background(Color.Black)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        if (dragOffsetX > 150f) { showQueue = true }
                        else if (dragOffsetY > 150f) { onBack() }
                        else if (dragOffsetY < -150f) { showLyrics = true }
                        
                        dragOffsetX = 0f
                        dragOffsetY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragOffsetX += dragAmount.x
                        dragOffsetY += dragAmount.y
                    }
                )
            }.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            TextButton(onClick = onBack) { Text("⌄", color = Color.White, style = MaterialTheme.typography.headlineLarge) }
        }
        Spacer(Modifier.height(24.dp))

        AsyncImage(
            model = state.artUri, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(280.dp).clip(MaterialTheme.shapes.large).background(Color.DarkGray)
        )
        Spacer(Modifier.height(24.dp))
        
        Text(state.title ?: "—", style = MaterialTheme.typography.headlineSmall, color = Color.White, textAlign = TextAlign.Center)
        Text(state.artist ?: "", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        Spacer(Modifier.height(24.dp))

        val duration = state.durationMs.coerceAtLeast(1)
        val currentPos = sliderPos ?: state.positionMs.toFloat()
        Slider(
            value = currentPos.coerceIn(0f, duration.toFloat()), onValueChange = { sliderPos = it },
            onValueChangeFinished = { vm.seekTo((sliderPos ?: 0f).toLong()); sliderPos = null },
            valueRange = 0f..duration.toFloat(), colors = SliderDefaults.colors(thumbColor = Red, activeTrackColor = Red)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmt(state.positionMs), color = Color.Gray, style = MaterialTheme.typography.labelSmall)
            Text(fmt(state.durationMs), color = Color.Gray, style = MaterialTheme.typography.labelSmall)
        }

        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { vm.toggleShuffle() }) { Text("⤨", color = if (state.shuffle) Red else Color.Gray, style = MaterialTheme.typography.headlineSmall) }
            TextButton(onClick = { vm.prev() }) { Text("⏮", color = Color.White, style = MaterialTheme.typography.headlineSmall) }
            Button(onClick = { vm.playPause() }, colors = ButtonDefaults.buttonColors(containerColor = Red)) {
                Text(if (state.isPlaying) "❚❚" else "▶", color = Color.White, style = MaterialTheme.typography.headlineSmall)
            }
            TextButton(onClick = { vm.next() }) { Text("⏭", color = Color.White, style = MaterialTheme.typography.headlineSmall) }
            TextButton(onClick = { vm.cycleRepeat() }) {
                val label = when (state.repeatMode) {
                    Player.REPEAT_MODE_ALL -> "↻↻"; Player.REPEAT_MODE_ONE -> "↻1"; else -> "↻"
                }
                Text(label, color = if (state.repeatMode != Player.REPEAT_MODE_OFF) Red else Color.Gray, style = MaterialTheme.typography.headlineSmall)
            }
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.4f))
        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(onClick = { showEq = true }) { Text(stringResource(R.string.player_equalizer), color = Red, style = MaterialTheme.typography.labelSmall) }
            TextButton(onClick = { showSpeed = true }) { Text("${stringResource(R.string.player_speed)} ${state.speed}x", color = Color.Gray, style = MaterialTheme.typography.labelSmall) }
            TextButton(onClick = { showLyrics = true }) { Text(stringResource(R.string.player_lyrics), color = Red, style = MaterialTheme.typography.labelSmall) }
        }
    }

    if (showEq) EqDialog(onClose = { showEq = false })
    if (showSpeed) SpeedDialog(currentSpeed = state.speed, onSpeedSelected = { vm.setSpeed(it); showSpeed = false }, onClose = { showSpeed = false })
}

@Composable
fun QueueScreen(queue: List<MediaItem>, currentIndex: Int, onBack: () -> Unit, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxSize().background(Color.Black).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            TextButton(onClick = onBack) { Text("⌄", color = Color.White, style = MaterialTheme.typography.headlineLarge) }
        }
        Text(stringResource(R.string.queue_title), style = MaterialTheme.typography.headlineLarge, color = Color.White)
        Spacer(Modifier.height(16.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(queue) { idx, item ->
                val isCurrent = idx == currentIndex
                Row(
                    Modifier.fillMaxWidth().clickable { onSelect(idx) }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.mediaMetadata.title?.toString() ?: "Unknown",
                            color = if (isCurrent) Red else Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(item.mediaMetadata.artist?.toString() ?: "", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                    }
                    if (isCurrent) Text(stringResource(R.string.queue_now_playing), color = Red, style = MaterialTheme.typography.labelSmall)
                }
                HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.3f))
            }
        }
    }
}

@Composable
fun LyricsScreen(vm: PlayerViewModel, trackId: Long?, currentPos: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var manualText by remember { mutableStateOf("") }
    var isEditing by remember { mutableStateOf(false) }
    
    val lyrics = vm.lyrics.collectAsState().value

    // Загружаем сохраненный текст при открытии, если он есть
    LaunchedEffect(trackId) {
        trackId?.let { id ->
            vm.getSavedLyrics(id) { saved ->
                if (!saved.isNullOrBlank()) {
                    manualText = saved
                }
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val text = context.contentResolver.openInputStream(it)?.bufferedReader()?.use { r -> r.readText() }
            if (text != null) { manualText = text; isEditing = true }
        }
    }

    Column(Modifier.fillMaxSize().background(Color.Black).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("⌄", color = Color.White, style = MaterialTheme.typography.headlineLarge) }
            if (trackId != null) {
                Row {
                    if (!isEditing) {
                        TextButton(onClick = { isEditing = true }) { Text(stringResource(R.string.lyrics_edit), color = Red) }
                    } else {
                        TextButton(onClick = { 
                            vm.saveLyrics(trackId, manualText)
                            isEditing = false 
                        }) { Text(stringResource(R.string.lyrics_save), color = Red) }
                    }
                }
            }
        }
        
        if (isEditing) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = { 
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = clipboard.primaryClip
                    if (clip != null && clip.itemCount > 0) {
                        manualText = clip.getItemAt(0).text.toString()
                    }
                }) { Text(stringResource(R.string.lyrics_paste), color = Color.Gray) }
                TextButton(onClick = { filePicker.launch("text/*") }) { Text(stringResource(R.string.lyrics_import_file), color = Color.Gray) }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = manualText,
                onValueChange = { manualText = it },
                modifier = Modifier.fillMaxSize(),
                placeholder = { Text(stringResource(R.string.lyrics_hint), color = Color.Gray) },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Red, unfocusedBorderColor = Color.DarkGray, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
            )
        } else {
            if (manualText.isNotBlank()) {
                // Отображаем введенный вручную текст
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(manualText, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                }
            } else if (lyrics.isNotEmpty()) {
                // Отображаем LRC файл
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    lyrics.forEach { line ->
                        val active = line.timeMs <= currentPos && (lyrics.getOrNull(lyrics.indexOf(line) + 1)?.timeMs ?: Long.MAX_VALUE) > currentPos
                        Text(line.text, color = if (active) Red else Color.Gray, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), textAlign = TextAlign.Center)
                    }
                }
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.lyrics_no_text), color = Color.Gray)
                }
            }
        }
    }
}

@Composable
private fun SpeedDialog(currentSpeed: Float, onSpeedSelected: (Float) -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.player_speed_title), color = Color.White) },
        text = {
            Column {
                listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { sp ->
                    TextButton(onClick = { onSpeedSelected(sp) }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${sp}x", color = if (currentSpeed == sp) Red else Color.White)
                            if (currentSpeed == sp) Text("✓", color = Red)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.player_close), color = Red) } },
        containerColor = Color(0xFF121212)
    )
}

@Composable
private fun EqDialog(onClose: () -> Unit) {
    val bands = EqualizerManager.bandCount()
    val range = EqualizerManager.bandLevelRange()
    var levels by remember { mutableStateOf(List(bands) { EqualizerManager.getBandLevel(it.toShort()).toFloat() }) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.player_equalizer), color = Color.White) },
        text = {
            Column {
                if (bands == 0) Text(stringResource(R.string.player_eq_unavailable), color = Color.Gray)
                else levels.forEachIndexed { i, lv ->
                    Text(stringResource(R.string.player_band, i + 1), color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                    Slider(value = lv, onValueChange = { levels = levels.toMutableList().also { l -> l[i] = it }; EqualizerManager.setBandLevel(i.toShort(), it.toInt().toShort()) }, valueRange = range.first.toFloat()..range.second.toFloat(), colors = SliderDefaults.colors(thumbColor = Red, activeTrackColor = Red))
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.player_close), color = Red) } },
        containerColor = Color(0xFF121212)
    )
}

private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
