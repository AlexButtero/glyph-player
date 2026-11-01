package com.nothing.glyphplayer.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.nothing.glyphplayer.R
import com.nothing.glyphplayer.data.Track
import com.nothing.glyphplayer.playback.EqualizerManager
import com.nothing.glyphplayer.ui.PlayerViewModel
import com.nothing.glyphplayer.ui.theme.Red

@Composable
fun PlayerScreen(vm: PlayerViewModel = viewModel(), onBack: () -> Unit) {
    val state by vm.state.collectAsState()
    val lyrics by vm.lyrics.collectAsState()
    val lib by vm.library.collectAsState()
    val playlists by vm.playlists.collectAsState()
    
    var showEq by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showTrackMenu by remember { mutableStateOf(false) }
    
    var showAddToPlaylist by remember { mutableStateOf(false) }
    var showCreateFromAdd by remember { mutableStateOf(false) }
    
    var trackToDelete by remember { mutableStateOf<Track?>(null) }
    var trackToRename by remember { mutableStateOf<Track?>(null) }
    var pendingDeleteTrack by remember { mutableStateOf<Track?>(null) }
    
    var sliderPos by remember { mutableStateOf<Float?>(null) }
    var dragOffsetX by remember { mutableStateOf(0f) }
    var dragOffsetY by remember { mutableStateOf(0f) }

    if (showLyrics) { 
        LyricsScreen(vm, state.currentTrackId, state.positionMs, onBack = { showLyrics = false }); return 
    }
    if (showQueue) { 
        QueueScreen(state.queue, state.currentIndex, onBack = { showQueue = false }, onSelect = { vm.seekToIndex(it); showQueue = false }); return 
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            // ИЗМЕНЕНО: Свайп вправо закрывает, влево - очередь, вверх - текст
                            if (dragOffsetX > 150f) onBack()
                            else if (dragOffsetX < -150f) showQueue = true
                            else if (dragOffsetY < -150f) showLyrics = true
                            dragOffsetX = 0f; dragOffsetY = 0f
                        },
                        onDrag = { change, dragAmount -> change.consume(); dragOffsetX += dragAmount.x; dragOffsetY += dragAmount.y }
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(32.dp)) }
                IconButton(onClick = { showTrackMenu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = Color.White) }
            }
            Spacer(Modifier.height(24.dp))

            Box(Modifier.size(280.dp).clip(MaterialTheme.shapes.large).background(Color.DarkGray)) {
                AsyncImage(
                    model = state.artUri, 
                    contentDescription = null, 
                    contentScale = ContentScale.Crop, 
                    modifier = Modifier.fillMaxSize(),
                    placeholder = painterResource(id = R.drawable.ic_fallback_music),
                    error = painterResource(id = R.drawable.ic_fallback_music)
                )
            }
            Spacer(Modifier.height(24.dp))
            
            Text(state.title ?: "—", style = MaterialTheme.typography.headlineSmall, color = Color.White, textAlign = TextAlign.Center)
            Text(state.artist ?: "", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            Spacer(Modifier.height(24.dp))

            val duration = state.durationMs.coerceAtLeast(1)
            val currentPos = sliderPos ?: state.positionMs.toFloat()
            Slider(value = currentPos.coerceIn(0f, duration.toFloat()), onValueChange = { sliderPos = it }, onValueChangeFinished = { vm.seekTo((sliderPos ?: 0f).toLong()); sliderPos = null }, valueRange = 0f..duration.toFloat(), colors = SliderDefaults.colors(thumbColor = Red, activeTrackColor = Red))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmt(state.positionMs), color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                Text(fmt(state.durationMs), color = Color.Gray, style = MaterialTheme.typography.labelSmall)
            }

            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.toggleShuffle() }) { Icon(Icons.Default.Shuffle, contentDescription = "Shuffle", tint = if (state.shuffle) Red else Color.Gray, modifier = Modifier.size(28.dp)) }
                IconButton(onClick = { vm.handleBackButton() }) { Icon(Icons.Default.SkipPrevious, contentDescription = "Prev", tint = Color.White, modifier = Modifier.size(36.dp)) }
                IconButton(onClick = { vm.playPause() }, modifier = Modifier.size(64.dp).background(Red, MaterialTheme.shapes.extraLarge)) { Icon(if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Play/Pause", tint = Color.White, modifier = Modifier.size(36.dp)) }
                IconButton(onClick = { vm.next() }) { Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(36.dp)) }
                IconButton(onClick = { vm.cycleRepeat() }) { Icon(if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat, contentDescription = "Repeat", tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) Red else Color.Gray, modifier = Modifier.size(28.dp)) }
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

        if (pendingDeleteTrack != null) {
            DeletionTimerSnackbar(
                track = pendingDeleteTrack!!,
                onDelete = { vm.deleteTrack(pendingDeleteTrack!!); pendingDeleteTrack = null; onBack() },
                onCancel = { pendingDeleteTrack = null },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
            )
        }
    }

    if (showEq) EqDialog(onClose = { showEq = false })
    if (showSpeed) SpeedDialog(currentSpeed = state.speed, onSpeedSelected = { vm.setSpeed(it); showSpeed = false }, onClose = { showSpeed = false })
    
    if (showTrackMenu) {
        val currentTrack = lib.find { it.id == state.currentTrackId }
        if (currentTrack != null) {
            AlertDialog(
                onDismissRequest = { showTrackMenu = false }, title = { Text(currentTrack.title, color = Color.White) },
                text = {
                    Column {
                        TextButton(onClick = { showTrackMenu = false; showAddToPlaylist = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.menu_add_to_playlist), color = Color.White) }
                        TextButton(onClick = { vm.addToQueue(listOf(currentTrack)); showTrackMenu = false }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.menu_add_to_queue), color = Color.White) }
                        TextButton(onClick = { trackToRename = currentTrack; showTrackMenu = false }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.menu_rename), color = Color.White) }
                        TextButton(onClick = { trackToDelete = currentTrack; showTrackMenu = false }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.menu_delete), color = Red) }
                    }
                },
                confirmButton = {}, dismissButton = { TextButton(onClick = { showTrackMenu = false }) { Text(stringResource(R.string.player_close), color = Color.Gray) } }, containerColor = Color(0xFF121212)
            )
        }
    }
    
    if (showAddToPlaylist) {
        if (showCreateFromAdd) {
            PlaylistEditorDialog(
                initialName = "",
                onSave = { name, desc, poster ->
                    val trackToAdd = state.currentTrackId
                    vm.createPlaylist(name, desc, poster) { newId ->
                        if (trackToAdd != null) vm.addMultipleToPlaylist(newId, listOf(trackToAdd))
                    }
                    showAddToPlaylist = false
                    showCreateFromAdd = false
                },
                onDismiss = { showCreateFromAdd = false }
            )
        } else {
            AlertDialog(
                onDismissRequest = { showAddToPlaylist = false }, title = { Text(stringResource(R.string.playlists_add_to), color = Color.White) },
                text = { 
                    Column { 
                        playlists.forEach { p -> TextButton(onClick = { state.currentTrackId?.let { vm.addMultipleToPlaylist(p.id, listOf(it)) }; showAddToPlaylist = false }) { Text(p.name, color = Color.White) } }
                        TextButton(onClick = { showCreateFromAdd = true }) { Text("+ Создать новый", color = Red) }
                    } 
                },
                confirmButton = {}, dismissButton = { TextButton(onClick = { showAddToPlaylist = false; showCreateFromAdd = false }) { Text(stringResource(R.string.lib_cancel), color = Color.Gray) } }, containerColor = Color(0xFF121212)
            )
        }
    }

    trackToRename?.let { track ->
        var newTitle by remember { mutableStateOf(track.title) }
        AlertDialog(
            onDismissRequest = { trackToRename = null }, title = { Text(stringResource(R.string.dialog_rename_title), color = Color.White) },
            text = { OutlinedTextField(value = newTitle, onValueChange = { newTitle = it }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Red, unfocusedBorderColor = Color.DarkGray, focusedTextColor = Color.White, unfocusedTextColor = Color.White)) },
            confirmButton = { TextButton(onClick = { if (newTitle.isNotBlank()) { vm.renameTrack(track, newTitle); trackToRename = null } }) { Text(stringResource(R.string.action_save), color = Red) } },
            dismissButton = { TextButton(onClick = { trackToRename = null }) { Text(stringResource(R.string.lib_cancel), color = Color.Gray) } }, containerColor = Color(0xFF121212)
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
}

@Composable
fun LyricsScreen(vm: PlayerViewModel, trackId: Long?, currentPos: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    var manualText by remember { mutableStateOf("") }
    var isEditing by remember { mutableStateOf(false) }
    var dragOffsetX by remember { mutableStateOf(0f) }
    val lyrics = vm.lyrics.collectAsState().value

    LaunchedEffect(trackId) { trackId?.let { id -> vm.getSavedLyrics(id) { saved -> if (!saved.isNullOrBlank()) manualText = saved } } }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { val text = context.contentResolver.openInputStream(it)?.bufferedReader()?.use { r -> r.readText() }; if (text != null) { manualText = text; isEditing = true } }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragEnd = { 
                    if (dragOffsetX > 150f && !isEditing) onBack()
                    dragOffsetX = 0f 
                },
                onHorizontalDrag = { change, dragAmount -> 
                    change.consume()
                    dragOffsetX += dragAmount 
                }
            )
        }
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(32.dp)) }
                if (trackId != null) {
                    if (!isEditing) TextButton(onClick = { isEditing = true }) { Text(stringResource(R.string.lyrics_edit), color = Red) }
                    else TextButton(onClick = { vm.saveLyrics(trackId, formatLyrics(manualText)); isEditing = false }) { Text(stringResource(R.string.lyrics_save), color = Red) }
                }
            }
            
            if (isEditing) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = { val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager; val clip = clipboard.primaryClip; if (clip != null && clip.itemCount > 0) manualText = clip.getItemAt(0).text.toString() }) { Text(stringResource(R.string.lyrics_paste), color = Color.Gray) }
                    TextButton(onClick = { filePicker.launch("text/*") }) { Text(stringResource(R.string.lyrics_import_file), color = Color.Gray) }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = manualText, onValueChange = { manualText = it }, modifier = Modifier.fillMaxSize(), placeholder = { Text(stringResource(R.string.lyrics_hint), color = Color.Gray) }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Red, unfocusedBorderColor = Color.DarkGray, focusedTextColor = Color.White, unfocusedTextColor = Color.White))
            } else {
                val formattedText = if (manualText.isNotBlank()) formatLyrics(manualText) else ""
                if (formattedText.isNotBlank()) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(top = 16.dp)) { Text(formattedText, color = Color.White, style = MaterialTheme.typography.bodyLarge, lineHeight = MaterialTheme.typography.bodyLarge.fontSize * 1.5f) }
                } else if (lyrics.isNotEmpty()) {
                    Column(Modifier.verticalScroll(rememberScrollState())) { lyrics.forEach { line -> val active = line.timeMs <= currentPos && (lyrics.getOrNull(lyrics.indexOf(line) + 1)?.timeMs ?: Long.MAX_VALUE) > currentPos; Text(line.text, color = if (active) Red else Color.Gray, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), textAlign = TextAlign.Center) } }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.lyrics_no_text), color = Color.Gray) }
                }
            }
        }
    }
}

@Composable
fun QueueScreen(queue: List<MediaItem>, currentIndex: Int, onBack: () -> Unit, onSelect: (Int) -> Unit) {
    var dragOffsetX by remember { mutableStateOf(0f) }
    Box(modifier = Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) {
        detectDragGestures(onDragEnd = { if (dragOffsetX > 150f) onBack(); dragOffsetX = 0f }, onDrag = { change, dragAmount -> change.consume(); dragOffsetX += dragAmount.x })
    }) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White) } }
            Text(stringResource(R.string.queue_title), style = MaterialTheme.typography.headlineLarge, color = Color.White)
            Spacer(Modifier.height(16.dp))
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(queue, key = { _, item -> item.mediaId }) { idx, item ->
                    val isCurrent = idx == currentIndex
                    Row(Modifier.fillMaxWidth().clickable { onSelect(idx) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.mediaMetadata.title?.toString() ?: "Unknown", color = if (isCurrent) Red else Color.White, style = MaterialTheme.typography.bodyMedium)
                            Text(item.mediaMetadata.artist?.toString() ?: "", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                        }
                        if (isCurrent) Text(stringResource(R.string.queue_now_playing), color = Red, style = MaterialTheme.typography.labelSmall)
                    }
                    HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.3f))
                }
            }
        }
    }
}

@Composable
private fun SpeedDialog(currentSpeed: Float, onSpeedSelected: (Float) -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose, title = { Text(stringResource(R.string.player_speed_title), color = Color.White) },
        text = { Column { listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { sp -> TextButton(onClick = { onSpeedSelected(sp) }, modifier = Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("${sp}x", color = if (currentSpeed == sp) Red else Color.White); if (currentSpeed == sp) Text("✓", color = Red) } } } } },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.player_close), color = Red) } }, containerColor = Color(0xFF121212)
    )
}

@Composable
private fun EqDialog(onClose: () -> Unit) {
    val bands = EqualizerManager.bandCount()
    val range = EqualizerManager.bandLevelRange()
    var levels by remember { mutableStateOf(List(bands) { EqualizerManager.getBandLevel(it.toShort()).toFloat() }) }
    AlertDialog(
        onDismissRequest = onClose, title = { Text(stringResource(R.string.player_equalizer), color = Color.White) },
        text = { Column { if (bands == 0) Text(stringResource(R.string.player_eq_unavailable), color = Color.Gray) else levels.forEachIndexed { i, lv -> Text(stringResource(R.string.player_band, i + 1), color = Color.Gray, style = MaterialTheme.typography.labelSmall); Slider(value = lv, onValueChange = { levels = levels.toMutableList().also { l -> l[i] = it }; EqualizerManager.setBandLevel(i.toShort(), it.toInt().toShort()) }, valueRange = range.first.toFloat()..range.second.toFloat(), colors = SliderDefaults.colors(thumbColor = Red, activeTrackColor = Red)) } } },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.player_close), color = Red) } }, containerColor = Color(0xFF121212)
    )
}

private fun formatLyrics(text: String): String {
    return text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("[") && !it.endsWith("]") }.joinToString("\n\n")
}

private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
