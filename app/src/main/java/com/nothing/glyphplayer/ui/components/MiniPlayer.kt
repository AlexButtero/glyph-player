package com.nothing.glyphplayer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.nothing.glyphplayer.ui.PlayerViewModel
import com.nothing.glyphplayer.ui.theme.Red
import com.nothing.glyphplayer.ui.theme.Surface

@Composable
fun MiniPlayer(vm: PlayerViewModel = viewModel(), onClick: () -> Unit) {
    val state by vm.state.collectAsState()
    if (state.title == null) return

    val progress = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f

    Column(
        Modifier.fillMaxWidth().height(56.dp).clickable { onClick() }.padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = state.artUri, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(36.dp).clip(MaterialTheme.shapes.small)
            )
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(state.title ?: "", style = MaterialTheme.typography.bodyMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(state.artist ?: "", style = MaterialTheme.typography.labelSmall, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(if (state.isPlaying) "❚❚" else "▶", color = Red, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.clickable { vm.playPause() })
        }
        LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, color = Red, trackColor = Surface, modifier = Modifier.fillMaxWidth().height(2.dp))
    }
}
