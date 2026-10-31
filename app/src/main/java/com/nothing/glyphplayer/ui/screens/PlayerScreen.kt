package com.nothing.glyphplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.nothing.glyphplayer.R
import com.nothing.glyphplayer.playback.EqualizerManager
import com.nothing.glyphplayer.ui.PlayerViewModel
import com.nothing.glyphplayer.ui.theme.Red
import kotlin.math.abs

@Composable
fun PlayerScreen(vm: PlayerViewModel = viewModel(), onBack: () -> Unit) {
    val state by vm.state.collectAsState()
    val lyrics by vm.lyrics.collectAsState()
    var showEq by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var sliderPos by remember { mutableStateOf<Float?>(null) }

    if (showLyrics) {
        LyricsScreen(lyrics, state.positionMs, onBack = { showLyrics = false })
        return
    }

    Column(
        Modifier.fillMaxSize().background(Color.Black)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    if (abs(dragAmount.x) > abs(dragAmount.y)) {
                        if (dragAmount.x > 100) vm.next()
                        else if (dragAmount.x < -100) vm.prev()
                    } else {
                        if (dragAmount.y < -150 && lyrics.isNotEmpty()) showLyrics = true
                    }
                }
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
            TextButton(onClick = { if (lyrics.isNotEmpty()) showLyrics = true }) { Text(stringResource(R.string.player_lyrics), color = if (lyrics.isNotEmpty()) Red else Color.Gray, style = MaterialTheme.typography.labelSmall) }
        }
    }

    if (showEq) EqDialog(onClose = { showEq = false })
    if (showSpeed) SpeedDialog(currentSpeed = state.speed, onSpeedSelected = { vm.setSpeed(it); showSpeed = false }, onClose = { showSpeed = false })
}

@Composable
private fun LyricsScreen(lyrics: List<com.nothing.glyphplayer.playback.LyricLine>, currentPos: Long, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Color.Black).padding(16.dp)) {
        TextButton(onClick = onBack) { Text("⌄", color = Color.White, style = MaterialTheme.typography.headlineLarge) }
        Spacer(Modifier.height(16.dp))
        Column(Modifier.verticalScroll(rememberScrollState())) {
            lyrics.forEach { line ->
                val active = line.timeMs <= currentPos && (lyrics.getOrNull(lyrics.indexOf(line) + 1)?.timeMs ?: Long.MAX_VALUE) > currentPos
                Text(line.text, color = if (active) Red else Color.Gray, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), textAlign = TextAlign.Center)
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
