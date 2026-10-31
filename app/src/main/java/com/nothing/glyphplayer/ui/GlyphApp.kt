package com.nothing.glyphplayer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nothing.glyphplayer.R
import com.nothing.glyphplayer.ui.components.MiniPlayer
import com.nothing.glyphplayer.ui.screens.ArtistsScreen
import com.nothing.glyphplayer.ui.screens.LibraryScreen
import com.nothing.glyphplayer.ui.screens.PlayerScreen
import com.nothing.glyphplayer.ui.screens.PlaylistsScreen
import com.nothing.glyphplayer.ui.screens.SettingsScreen
import com.nothing.glyphplayer.ui.theme.Red

@Composable
fun GlyphApp() {
    val nav = rememberNavController()
    var tab by remember { mutableStateOf(0) }
    
    val tabs = listOf(
        "LIBRARY" to stringResource(R.string.nav_library),
        "ARTISTS" to stringResource(R.string.nav_artists),
        "PLAYLISTS" to stringResource(R.string.nav_playlists),
        "SETTINGS" to stringResource(R.string.nav_settings)
    )

    Scaffold(
        containerColor = Color.Black,
        bottomBar = {
            Column {
                MiniPlayer(onClick = { nav.navigate("PLAYER") { launchSingleTop = true } })
                NavigationBar(containerColor = Color.Black) {
                    tabs.forEachIndexed { i, (route, label) ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = {
                                tab = i
                                nav.navigate(route) {
                                    popUpTo(nav.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                }
                            },
                            icon = { Box(Modifier) },
                            label = { Text(label, color = if (tab == i) Red else Color.Gray, fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal) }
                        )
                    }
                }
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            NavHost(nav, startDestination = "LIBRARY") {
                composable("LIBRARY") { LibraryScreen(onTrackClick = { nav.navigate("PLAYER") }) }
                composable("ARTISTS") { ArtistsScreen(onTrackClick = { nav.navigate("PLAYER") }) }
                composable("PLAYLISTS") { PlaylistsScreen(onPlaylistClick = { nav.navigate("PLAYER") }) }
                composable("SETTINGS") { SettingsScreen() }
                composable("PLAYER") { PlayerScreen(onBack = { nav.popBackStack() }) }
            }
        }
    }
}
