package com.nothing.glyphplayer.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.nothing.glyphplayer.R
import com.nothing.glyphplayer.data.Track
import com.nothing.glyphplayer.playback.EqualizerManager
import com.nothing.glyphplayer.playback.LyricLine
import com.nothing.glyphplayer.playback.LyricsLoader
import com.nothing.glyphplayer.ui.PlayerViewModel
import com.nothing.glyphplayer.ui.theme.Red
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun PlayerScreen(vm: PlayerViewModel = viewModel(), onBack: () -> Unit) {
    val state by vm.state.collectAsState()
    val positionMs by vm.positionMs.collectAsState()
    val lyrics by vm.lyrics.collectAsState()
    val lyricsOffsetMs by vm.lyricsOffsetMs.collectAsState()
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
    
    // Свайп постера
    var posterOffsetX by remember { mutableStateOf(0f) }
    val animatedPosterOffsetX by animateFloatAsState(
        targetValue = posterOffsetX,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "posterOffset"
    )
    val posterScale by animateFloatAsState(
        targetValue = if (abs(posterOffsetX) > 10f) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy),
        label = "posterScale"
    )

    val effectivePosition = (positionMs - lyricsOffsetMs).coerceAtLeast(0)
    val currentLine = remember(lyrics, effectivePosition) {
        lyrics.lastOrNull { it.timeMs <= effectivePosition }
    }

    if (showLyrics) { 
        KaraokeLyricsScreen(vm, state.currentTrackId, effectivePosition, onBack = { showLyrics = false }); return 
    }
    if (showQueue) { 
        QueueScreen(state.queue, state.currentIndex, onBack = { showQueue = false }, onSelect = { vm.seekToIndex(it); showQueue = false }); return 
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // РАЗМЫТЫЙ ФОН
        AsyncImage(
            model = state.artUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.4f }
        )
        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Black.copy(alpha = 0.95f)))))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            if (dragOffsetX > 150f) onBack()
                            else if (dragOffsetX < -150f) showQueue = true
                            else if (dragOffsetY > 150f) showLyrics = true
                            else if (dragOffsetY < -150f) showLyrics = true
                            
                            dragOffsetX = 0f; dragOffsetY = 0f
                        },
                        onDrag = { change, dragAmount -> 
                            change.consume(); dragOffsetX += dragAmount.x; dragOffsetY += dragAmount.y 
                        }
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ВЕРХНЯЯ ПАНЕЛЬ
            Box(
                Modifier.fillMaxWidth().padding(top = 8.dp, start = 8.dp, end = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                // Левая кнопка (Эквалайзер)
                IconButton(
                    onClick = { showEq = true },
                    modifier = Modifier.align(Alignment.CenterStart)
                ) { 
                    Icon(Icons.Outlined.Tune, contentDescription = "Equalizer", tint = Color.White) 
                }
                
                // Центральный текст (Плейлист)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("ПЛЕЙЛИСТ", color = Color.Gray, fontSize = 10.sp, letterSpacing = 1.sp)
                    Text(
                        text = state.playlistName ?: "Мои треки", 
                        color = Color.White, 
                        fontSize = 16.sp, 
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                // Правая кнопка (Меню)
                IconButton(
                    onClick = { showTrackMenu = true },
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) { 
                    Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = Color.White) 
                }
            }
            
            Spacer(Modifier.height(8.dp))

            // ТЕКУЩАЯ СТРОКА С БЕГУЩЕЙ СТРОКОЙ (УВЕЛИЧЕНА ДО 30)
            AnimatedVisibility(
                visible = currentLine != null,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .clickable { showLyrics = true },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = currentLine?.text ?: "",
                        color = Red,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // ПОСТЕР
            val posterPx = with(LocalDensity.current) { 280.dp.toPx() }
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.DarkGray)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (posterOffsetX > 100f) vm.prev()
                                else if (posterOffsetX < -100f) vm.next()
                                posterOffsetX = 0f
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                posterOffsetX += dragAmount
                            }
                        )
                    }
            ) {
                // ПРЕДЫДУЩИЙ ПОСТЕР
                val prevIndex = state.currentIndex - 1
                if (prevIndex >= 0 && prevIndex < state.queue.size) {
                    val prevArt = state.queue[prevIndex].mediaMetadata.artworkUri
                    if (prevArt != null) {
                        AsyncImage(
                            model = prevArt,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { translationX = animatedPosterOffsetX - posterPx }
                        )
                    }
                }
                
                // СЛЕДУЮЩИЙ ПОСТЕР
                val nextIndex = state.currentIndex + 1
                if (nextIndex >= 0 && nextIndex < state.queue.size) {
                    val nextArt = state.queue[nextIndex].mediaMetadata.artworkUri
                    if (nextArt != null) {
                        AsyncImage(
                            model = nextArt,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { translationX = animatedPosterOffsetX + posterPx }
                        )
                    }
                }
                
                // ТЕКУЩИЙ ПОСТЕР
                AsyncImage(
                    model = state.artUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = animatedPosterOffsetX
                            scaleX = posterScale
                            scaleY = posterScale
                        },
                    placeholder = painterResource(id = R.drawable.ic_fallback_music),
                    error = painterResource(id = R.drawable.ic_fallback_music)
                )
            }
            
            Spacer(Modifier.height(24.dp))
            
            Text(state.title ?: "—", style = MaterialTheme.typography.headlineSmall, color = Color.White, textAlign = TextAlign.Center)
            Text(state.artist ?: "", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            
            Spacer(Modifier.height(16.dp))

            // Слайдер
            val duration = state.durationMs.coerceAtLeast(1)
            val currentPos = sliderPos ?: positionMs.toFloat()
            Slider(
                value = currentPos.coerceIn(0f, duration.toFloat()), 
                onValueChange = { sliderPos = it }, 
                onValueChangeFinished = { vm.seekTo((sliderPos ?: 0f).toLong()); sliderPos = null }, 
                valueRange = 0f..duration.toFloat(), 
                colors = SliderDefaults.colors(thumbColor = Red, activeTrackColor = Red, inactiveTrackColor = Color.DarkGray)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmt(positionMs), color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                Text(fmt(state.durationMs), color = Color.Gray, style = MaterialTheme.typography.labelSmall)
            }

            Spacer(Modifier.height(16.dp))

            // Кнопки
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.toggleShuffle() }) { Icon(Icons.Default.Shuffle, contentDescription = "Shuffle", tint = if (state.shuffle) Red else Color.Gray, modifier = Modifier.size(28.dp)) }
                IconButton(onClick = { vm.handleBackButton() }) { Icon(Icons.Default.SkipPrevious, contentDescription = "Prev", tint = Color.White, modifier = Modifier.size(36.dp)) }
                IconButton(onClick = { vm.playPause() }, modifier = Modifier.size(72.dp).background(Red, RoundedCornerShape(50))) { Icon(if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Play/Pause", tint = Color.White, modifier = Modifier.size(40.dp)) }
                IconButton(onClick = { vm.next() }) { Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(36.dp)) }
                IconButton(onClick = { vm.cycleRepeat() }) { Icon(if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat, contentDescription = "Repeat", tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) Red else Color.Gray, modifier = Modifier.size(28.dp)) }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.3f))
            Spacer(Modifier.height(16.dp))

            // НИЖНИЕ КНОПКИ
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = { showQueue = true }) { 
                    Text(stringResource(R.string.queue_title), color = Color.White, style = MaterialTheme.typography.labelSmall) 
                }
                TextButton(onClick = { showLyrics = true }) { 
                    Text(stringResource(R.string.player_lyrics), color = Red, style = MaterialTheme.typography.labelSmall) 
                }
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

    if (showEq) { EqDialog(onClose = { showEq = false }) }
    
    if (showTrackMenu) {
        val currentTrack = lib.find { it.id == state.currentTrackId }
        if (currentTrack != null) {
            AlertDialog(
                onDismissRequest = { showTrackMenu = false }, title = { Text(currentTrack.title, color = Color.White) },
                text = {
                    Column {
                        TextButton(onClick = { showTrackMenu = false; showAddToPlaylist = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.menu_add_to_playlist), color = Color.White) }
                        TextButton(onClick = { vm.addToQueue(listOf(currentTrack)); showTrackMenu = false }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.menu_add_to_queue), color = Color.White) }
                        TextButton(onClick = { showTrackMenu = false; showSpeed = true }, modifier = Modifier.fillMaxWidth()) { Text("Скорость воспроизведения", color = Color.White) }
                        TextButton(onClick = { trackToRename = currentTrack; showTrackMenu = false }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.menu_rename), color = Color.White) }
                        TextButton(onClick = { trackToDelete = currentTrack; showTrackMenu = false }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.menu_delete), color = Red) }
                    }
                },
                confirmButton = {}, dismissButton = { TextButton(onClick = { showTrackMenu = false }) { Text(stringResource(R.string.player_close), color = Color.Gray) } }, containerColor = Color(0xFF121212)
            )
        }
    }
    
    if (showSpeed) SpeedDialog(currentSpeed = state.speed, onSpeedSelected = { vm.setSpeed(it); showSpeed = false }, onClose = { showSpeed = false })
    
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

// ============================================================================
// ЭКРАН КАРАОКЕ (ИСПРАВЛЕНО ЦЕНТРИРОВАНИЕ)
// ============================================================================
@Composable
fun KaraokeLyricsScreen(vm: PlayerViewModel, trackId: Long?, currentPos: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    var manualText by remember { mutableStateOf("") }
    var isEditing by remember { mutableStateOf(false) }
    var showOffsetControls by remember { mutableStateOf(false) }
    var dragOffsetX by remember { mutableStateOf(0f) }
    
    val lyricsFromFile = vm.lyrics.collectAsState().value
    val offsetMs by vm.lyricsOffsetMs.collectAsState()

    val listState = rememberLazyListState()

    var isUserScrolling by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) {
                isUserScrolling = true
            } else if (interaction is DragInteraction.Stop || interaction is DragInteraction.Cancel) {
                delay(3000)
                isUserScrolling = false
            }
        }
    }

    LaunchedEffect(trackId) { 
        trackId?.let { id -> vm.getSavedLyrics(id) { saved -> if (!saved.isNullOrBlank()) manualText = saved } } 
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { 
            val text = context.contentResolver.openInputStream(it)?.bufferedReader()?.use { r -> r.readText() }
            if (text != null) { manualText = text; isEditing = true } 
        }
    }

    val parsedLyrics = remember(manualText) { if (manualText.isNotBlank()) LyricsLoader.parse(manualText) else emptyList() }
    val displayLyrics = if (parsedLyrics.isNotEmpty()) parsedLyrics else lyricsFromFile

    val effectivePos = (currentPos - offsetMs).coerceAtLeast(0)
    val activeIndex = remember(displayLyrics, effectivePos) { displayLyrics.indexOfLast { it.timeMs <= effectivePos } }

    var countdown by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(displayLyrics, effectivePos) {
        if (displayLyrics.isNotEmpty() && activeIndex == -1) {
            val firstLineTime = displayLyrics.first().timeMs
            val diff = firstLineTime - effectivePos
            if (diff in 1..3000) {
                countdown = ((diff / 1000) + 1).toInt()
            } else {
                countdown = null
            }
        } else {
            countdown = null
        }
    }

    LaunchedEffect(activeIndex, isUserScrolling) {
        if (activeIndex in displayLyrics.indices && !isEditing && !isUserScrolling) {
            listState.animateScrollToItem(activeIndex)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1E1E1E), Color.Black)))
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = { if (dragOffsetX > 150f && !isEditing) onBack(); dragOffsetX = 0f },
                    onHorizontalDrag = { change, dragAmount -> change.consume(); dragOffsetX += dragAmount }
                )
            }
    ) {
        if (isEditing) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(top = 72.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = { 
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = clipboard.primaryClip
                        if (clip != null && clip.itemCount > 0) manualText = clip.getItemAt(0).text.toString()
                    }) { Text(stringResource(R.string.lyrics_paste), color = Color.Gray) }
                    TextButton(onClick = { filePicker.launch("text/*") }) { Text(stringResource(R.string.lyrics_import_file), color = Color.Gray) }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = manualText, onValueChange = { manualText = it }, 
                    modifier = Modifier.fillMaxSize(), 
                    placeholder = { Text(stringResource(R.string.lyrics_hint), color = Color.Gray) }, 
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Red, unfocusedBorderColor = Color.DarkGray, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
            }
        } else if (displayLyrics.isNotEmpty()) {
            // ИСПОЛЬЗУЕМ BoxWithConstraints ДЛЯ ТОЧНОГО ЦЕНТРИРОВАНИЯ
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val halfHeight = maxHeight / 2
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    // Отступ ровно половина экрана минус половина высоты текста
                    contentPadding = PaddingValues(top = halfHeight - 32.dp, bottom = halfHeight)
                ) {
                    itemsIndexed(displayLyrics) { index, line ->
                        val isActive = index == activeIndex
                        
                        val textColor by animateColorAsState(
                            targetValue = if (isActive) Red else Color.Gray,
                            animationSpec = tween(200),
                            label = "textColor"
                        )
                        val scale by animateFloatAsState(
                            targetValue = if (isActive) 1.15f else 1f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy),
                            label = "scale"
                        )
                        val alpha by animateFloatAsState(
                            targetValue = if (isActive) 1f else 0.5f,
                            animationSpec = tween(200),
                            label = "alpha"
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp, horizontal = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = line.text,
                                color = textColor.copy(alpha = alpha),
                                fontSize = 18.sp,
                                fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .graphicsLayer { scaleX = scale; scaleY = scale }
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onDoubleTap = { vm.seekTo(line.timeMs) }
                                        )
                                    }
                            )
                        }
                    }
                }
            }
        } else if (manualText.isNotBlank()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(top = 72.dp, start = 16.dp, end = 16.dp)
                    .verticalScroll(rememberScrollState())
            ) { 
                Text(manualText, color = Color.White, style = MaterialTheme.typography.bodyLarge, lineHeight = MaterialTheme.typography.bodyLarge.fontSize * 1.5f) 
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { 
                Text(stringResource(R.string.lyrics_no_text), color = Color.Gray) 
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(32.dp)) }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isEditing) {
                    IconButton(onClick = { showOffsetControls = !showOffsetControls }) {
                        Icon(Icons.Default.Settings, contentDescription = "Offset", tint = if (showOffsetControls) Red else Color.Gray, modifier = Modifier.size(20.dp))
                    }
                    TextButton(onClick = { isEditing = true }) { Text(stringResource(R.string.lyrics_edit), color = Red) }
                } else {
                    TextButton(onClick = { vm.saveLyrics(trackId!!, formatLyrics(manualText)); isEditing = false }) { 
                        Text(stringResource(R.string.lyrics_save), color = Red) 
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showOffsetControls && !isEditing,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 64.dp, start = 16.dp, end = 16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Задержка текста: ${String.format("%.1f", offsetMs / 1000f)} сек",
                        color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { vm.setLyricsOffset(offsetMs - 100) }, colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)) {
                            Text("-0.1s", color = Color.White)
                        }
                        Slider(
                            value = offsetMs.toFloat(),
                            onValueChange = { vm.setLyricsOffset(it.toLong()) },
                            valueRange = -2000f..2000f,
                            colors = SliderDefaults.colors(thumbColor = Red, activeTrackColor = Red),
                            modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
                        )
                        Button(onClick = { vm.setLyricsOffset(offsetMs + 100) }, colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)) {
                            Text("+0.1s", color = Color.White)
                        }
                    }
                }
            }
        }

        if (countdown != null && !isEditing) {
            Text(
                text = countdown.toString(),
                color = Red,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )
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
            
            val displayQueue = remember(queue, currentIndex) {
                if (queue.isNotEmpty() && currentIndex in queue.indices) {
                    val firstPart = queue.subList(currentIndex, queue.size).toList()
                    val secondPart = queue.subList(0, currentIndex).toList()
                    firstPart + secondPart
                } else queue
            }

            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(displayQueue) { uiIndex, item ->
                    val realIndex = if (queue.isNotEmpty()) (currentIndex.coerceAtLeast(0) + uiIndex) % queue.size else 0
                    val isCurrent = uiIndex == 0 

                    Row(Modifier.fillMaxWidth().clickable { onSelect(realIndex) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
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
    val isAvailable = EqualizerManager.isAvailable()
    val range = EqualizerManager.bandLevelRange()
    var levels by remember { mutableStateOf(List(bands) { EqualizerManager.getBandLevel(it.toShort()).toFloat() }) }
    
    AlertDialog(
        onDismissRequest = onClose, title = { Text(stringResource(R.string.player_equalizer), color = Color.White) },
        text = { 
            Column { 
                if (!isAvailable) {
                    Text("Эквалайзер недоступен. Включите любой трек, чтобы активировать аудиосессию.", color = Color.Gray)
                } else {
                    levels.forEachIndexed { i, lv -> 
                        Text(stringResource(R.string.player_band, i + 1), color = Color.Gray, style = MaterialTheme.typography.labelSmall); 
                        Slider(value = lv, onValueChange = { levels = levels.toMutableList().also { l -> l[i] = it }; EqualizerManager.setBandLevel(i.toShort(), it.toInt().toShort()) }, valueRange = range.first.toFloat()..range.second.toFloat(), colors = SliderDefaults.colors(thumbColor = Red, activeTrackColor = Red)) 
                    } 
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.player_close), color = Red) } }, containerColor = Color(0xFF121212)
    )
}

private fun formatLyrics(text: String): String = text.trim()

private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
