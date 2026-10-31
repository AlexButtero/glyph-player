package com.nothing.glyphplayer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

@Composable
fun PlayerScreen(vm: PlayerViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val lyrics by vm.lyrics.collectAsState()
    var showEq by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var sliderPos by remember { mutableStateOf<Float?>(null) }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = state.artUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.large)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            state.title ?: "—",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Text(state.artist ?: "", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)

        Spacer(Modifier.height(16.dp))

        val duration = state.durationMs.coerceAtLeast(1)
        val currentPos = sliderPos ?: state.positionMs.toFloat()
        Slider(
            value = currentPos.coerceIn(0f, duration.toFloat()),
            onValueChange = { sliderPos = it },
            onValueChangeFinished = {
                vm.seekTo((sliderPos ?: 0f).toLong()); sliderPos = null
            },
            valueRange = 0f..duration.toFloat(),
            colors = SliderDefaults.colors(thumbColor = Red, activeTrackColor = Red)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmt(state.positionMs), color = Color.Gray, style = MaterialTheme.typography.labelSmall)
            Text(fmt(state.durationMs), color = Color.Gray, style = MaterialTheme.typography.labelSmall)
        }

        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { vm.toggleShuffle() }) {
                Text(
                    "⇄",
                    color = if (state.shuffle) Red else Color.Gray,
                    style = MaterialTheme.typography.headlineSmall
                )
            }
            TextButton(onClick = { vm.prev() }) {
                Text("⏮", color = Color.White, style = MaterialTheme.typography.headlineSmall)
            }
            Button(
                onClick = { vm.playPause() },
                colors = ButtonDefaults.buttonColors(containerColor = Red)
            ) {
                Text(if (state.isPlaying) "❚❚" else "▶",
                    color = Color.White, style = MaterialTheme.typography.headlineSmall)
            }
            TextButton(onClick = { vm.next() }) {
                Text("⏭", color = Color.White, style = MaterialTheme.typography.headlineSmall)
            }
            TextButton(onClick = { vm.cycleRepeat() }) {
                val label = when (state.repeatMode) {
                    Player.REPEAT_MODE_ALL -> "↻↻"
                    Player.REPEAT_MODE_ONE -> "↻1"
                    else -> "↻"
                }
                Text(label,
                    color = if (state.repeatMode != Player.REPEAT_MODE_OFF) Red else Color.Gray,
                    style = MaterialTheme.typography.headlineSmall)
            }
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.4f))
        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(onClick = { showEq = true }) {
                Text(stringResource(R.string.player_equalizer), color = Red)
            }
            TextButton(onClick = { showSleep = true }) {
                val remaining = state.sleepSecondsRemaining
                Text(
                    if (remaining != null) stringResource(R.string.player_sleep_remaining, remaining / 60)
                    else stringResource(R.string.player_sleep),
                    color = if (remaining != null) Red else Color.Gray
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.player_speed),
            color = Color.Gray, style = MaterialTheme.typography.labelSmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { sp ->
                TextButton(onClick = { vm.setSpeed(sp) }) {
                    Text(
                        "${sp}x",
                        color = if (state.speed == sp) Red else Color.Gray,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        if (lyrics.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.4f))
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.player_lyrics),
                color = Red, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(8.dp))
            lyrics.forEach { line ->
                val active = line.timeMs <= state.positionMs
                Text(
                    line.text,
                    color = if (active) Red else Color.Gray,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    if (showEq) EqDialog(onClose = { showEq = false })
    if (showSleep) SleepDialog(vm = vm, onClose = { showSleep = false })
}

@Composable
private fun EqDialog(onClose: () -> Unit) {
    val bands = EqualizerManager.bandCount()
    val range = EqualizerManager.bandLevelRange()
    val minL = range.first.toFloat()
    val maxL = range.second.toFloat()

    var levels by remember {
        mutableStateOf(List(bands) { EqualizerManager.getBandLevel(it.toShort()).toFloat() })
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.player_equalizer), color = Color.White) },
        text = {
            Column {
                if (bands == 0) {
                    Text(stringResource(R.string.player_eq_unavailable), color = Color.Gray)
                } else {
                    levels.forEachIndexed { i, lv ->
                        Text(stringResource(R.string.player_band, i + 1),
                            color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                        Slider(
                            value = lv,
                            onValueChange = {
                                levels = levels.toMutableList().also { l -> l[i] = it }
                                EqualizerManager.setBandLevel(i.toShort(), it.toInt().toShort())
                            },
                            valueRange = minL..maxL,
                            colors = SliderDefaults.colors(thumbColor = Red, activeTrackColor = Red)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text(stringResource(R.string.player_close), color = Red) }
        },
        containerColor = Color(0xFF121212)
    )
}

@Composable
private fun SleepDialog(vm: PlayerViewModel, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.player_sleep_timer), color = Color.White) },
        text = {
            Column {
                listOf(0, 15, 30, 45, 60).forEach { m ->
                    TextButton(onClick = { vm.setSleepTimer(m); onClose() }) {
                        Text(
                            if (m == 0) stringResource(R.string.player_off)
                            else stringResource(R.string.player_minutes, m),
                            color = Color.White
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text(stringResource(R.string.player_close), color = Red) }
        },
        containerColor = Color(0xFF121212)
    )
}

private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
