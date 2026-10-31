package com.nothing.glyphplayer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch

@Composable
fun GlyphApp() {
    val nav = rememberNavController()
    val pagerState = rememberPagerState(pageCount = { 4 })
    val scope = rememberCoroutineScope()
    
    val tabs = listOf(
        stringResource(R.string.nav_library),
        stringResource(R.string.nav_artists),
        stringResource(R.string.nav_playlists),
        stringResource(R.string.nav_settings)
    )

    Scaffold(
        containerColor = Color.Black,
        bottomBar = {
            Column {
                MiniPlayer(onClick = { nav.navigate("PLAYER") { launchSingleTop = true } })
                NavigationBar(containerColor = Color.Black) {
                    tabs.forEachIndexed { i, label ->
                        NavigationBarItem(
                            selected = pagerState.currentPage == i,
                            onClick = { scope.launch { pagerState.animateScrollToPage(i) } },
                            icon = { Box(Modifier) },
                            label = { Text(label, color = if (pagerState.currentPage == i) Red else Color.Gray, fontWeight = if (pagerState.currentPage == i) FontWeight.Bold else FontWeight.Normal) }
                        )
                    }
                }
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            NavHost(nav, startDestination = "MAIN") {
                composable("MAIN") {
                    // ОПТИМИЗАЦИЯ: подгрузка соседних экранов
                    HorizontalPager(
                        state = pagerState,
                        beyondViewportPageCount = 1 
                    ) { page ->
                        when (page) {
                            0 -> LibraryScreen(onTrackClick = { nav.navigate("PLAYER") })
                            1 -> ArtistsScreen(onTrackClick = { nav.navigate("PLAYER") })
                            2 -> PlaylistsScreen(onPlaylistClick = { nav.navigate("PLAYER") })
                            3 -> SettingsScreen()
                        }
                    }
                }
                composable("PLAYER") { PlayerScreen(onBack = { nav.popBackStack() }) }
            }
        }
    }
}
