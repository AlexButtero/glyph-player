package com.nothing.glyphplayer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nothing.glyphplayer.playback.EqualizerManager
import com.nothing.glyphplayer.ui.PlayerViewModel
import com.nothing.glyphplayer.ui.theme.Red

@Composable
fun SettingsScreen(vm: PlayerViewModel = viewModel()) {
    var eqOn by remember { mutableStateOf(EqualizerManager.isEnabled()) }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())
    ) {
        Text("SETTINGS", style = MaterialTheme.typography.headlineLarge, color = Color.White)
        Spacer(Modifier.height(24.dp))

        SectionTitle("LIBRARY")
        Button(
            onClick = { vm.scanLibrary { } },
            colors = ButtonDefaults.buttonColors(containerColor = Red),
            modifier = Modifier.fillMaxWidth()
        ) { Text("RESCAN LIBRARY") }

        Spacer(Modifier.height(16.dp))
        SectionTitle("AUDIO")

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Equalizer", color = Color.White, style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = eqOn,
                onCheckedChange = { eqOn = it; EqualizerManager.setEnabled(it) },
                colors = SwitchDefaults.colors(checkedTrackColor = Red)
            )
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("ABOUT")
        Text("Glyph Player 1.0.0", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
        Text("Nothing OS style • fully offline",
            color = Color.Gray, style = MaterialTheme.typography.bodyMedium)

        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.3f))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = Red, style = MaterialTheme.typography.labelSmall)
    Spacer(Modifier.height(8.dp))
}
