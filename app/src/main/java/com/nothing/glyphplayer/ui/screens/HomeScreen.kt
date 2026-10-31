package com.nothing.glyphplayer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nothing.glyphplayer.ui.PlayerViewModel
import com.nothing.glyphplayer.ui.theme.Red

@Composable
fun HomeScreen(vm: PlayerViewModel = viewModel()) {
    val lib by vm.library.collectAsState()
    var prompt by remember { mutableStateOf("") }
    var scannedMsg by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("GLYPH PLAYER", style = MaterialTheme.typography.headlineLarge, color = Color.White)
        Spacer(Modifier.height(8.dp))
        Text("offline • nothing os style", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Spacer(Modifier.height(32.dp))
        Text("${lib.size} треков в библиотеке",
            style = MaterialTheme.typography.bodyMedium, color = Color.White)
        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { vm.scanLibrary { n -> scannedMsg = "Найдено: $n" } },
            colors = ButtonDefaults.buttonColors(containerColor = Red),
            modifier = Modifier.fillMaxWidth()
        ) { Text("SCAN LIBRARY") }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { vm.queueMyWave() },
            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
            modifier = Modifier.fillMaxWidth()
        ) { Text("MY WAVE ▶") }

        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            placeholder = { Text("sad / workout / chill / rain", color = Color.Gray) },
            label = { Text("PROMPT", color = Color.Gray) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Red,
                unfocusedBorderColor = Color.DarkGray,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { if (prompt.isNotBlank()) vm.playPrompted(prompt) },
            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
            modifier = Modifier.fillMaxWidth()
        ) { Text("BUILD PLAYLIST") }

        if (scannedMsg != null) {
            Spacer(Modifier.height(16.dp))
            Text(scannedMsg ?: "", color = Red, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
