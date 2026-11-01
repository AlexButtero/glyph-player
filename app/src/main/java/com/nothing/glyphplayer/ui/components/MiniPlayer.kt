package com.nothing.glyphplayer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.nothing.glyphplayer.R
import com.nothing.glyphplayer.ui.PlayerViewModel
import com.nothing.glyphplayer.ui.theme.Red
import com.nothing.glyphplayer.ui.theme.Surface

@Composable
fun MiniPlayer(vm: PlayerViewModel = viewModel(), onClick: () -> Unit) {
    val state by vm.state.collectAsState()
    val positionMs by vm.positionMs.collectAsState()
    if (state.title == null) return

    val progress = if (state.durationMs > 0) positionMs.toFloat() / state.durationMs else 0f

    Column(
        Modifier.fillMaxWidth().height(64.dp).clickable { onClick() }.padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            AsyncImage(
                model = state.artUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(40.dp).clip(MaterialTheme.shapes.small),
                placeholder = painterResource(id = R.drawable.ic_fallback_music),
                error = painterResource(id = R.drawable.ic_fallback_music)
            )
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(state.title ?: "", style = MaterialTheme.typography.bodyMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(state.artist ?: "", style = MaterialTheme.typography.labelSmall, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.handleBackButton() }) { Icon(Icons.Default.SkipPrevious, contentDescription = "Prev", tint = Color.White) }
                IconButton(onClick = { vm.playPause() }) { Icon(if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Play/Pause", tint = Red) }
                IconButton(onClick = { vm.next() }) { Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White) }
                IconButton(onClick = { vm.stopAndClear() }) { 
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray, modifier = Modifier.size(20.dp)) 
                }
            }
        }
        LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, color = Red, trackColor = Surface, modifier = Modifier.fillMaxWidth().height(2.dp))
    }
}
