package com.nothing.glyphplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nothing.glyphplayer.ui.PlayerViewModel
import com.nothing.glyphplayer.ui.theme.Red

@Composable
fun SourceFoldersScreen(
    vm: PlayerViewModel = viewModel(),
    onBack: () -> Unit
) {
    val sourceFolders by vm.sourceFolders.collectAsState()
    
    LaunchedEffect(Unit) { vm.loadSourceFolders() }
    
    Column(Modifier.fillMaxSize().background(Color.Black).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
        }
        
        Text("ИСТОЧНИКИ МУЗЫКИ", style = MaterialTheme.typography.headlineLarge, color = Color.White)
        Spacer(Modifier.height(8.dp))
        Text("${sourceFolders.values.sum()} треков в ${sourceFolders.size} папках", color = Color.Gray, fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        
        if (sourceFolders.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Папки не найдены", color = Color.Gray)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(sourceFolders.entries.toList().sortedByDescending { it.value }) { (folder, count) ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Folder, 
                            contentDescription = null, 
                            tint = Red, 
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = folder.substringAfterLast('/').ifBlank { folder },
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = folder,
                                color = Color.Gray,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "$count",
                            color = Red,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.3f))
                }
            }
        }
    }
}
