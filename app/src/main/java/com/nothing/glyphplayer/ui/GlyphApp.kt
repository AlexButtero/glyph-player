package com.nothing.glyphplayer.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
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
import com.nothing.glyphplayer.ui.screens.SourceFoldersScreen
import com.nothing.glyphplayer.ui.theme.Red
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlyphApp() {
    val vm: PlayerViewModel = viewModel()
    val trackPosters by vm.trackPosters.collectAsState()
    
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
            Column(Modifier.background(Color.Black).navigationBarsPadding()) {
                MiniPlayer(vm = vm, onClick = { nav.navigate("PLAYER") { launchSingleTop = true } })
                Row(
                    Modifier.fillMaxWidth().height(64.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEachIndexed { i, label ->
                        val isSelected = pagerState.currentPage == i
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .combinedClickable(
                                    onClick = { 
                                        scope.launch { pagerState.animateScrollToPage(i) } 
                                    },
                                    onDoubleClick = {
                                        if (pagerState.currentPage == i) {
                                            when (i) {
                                                0 -> vm.triggerLibraryRefresh()
                                                1 -> vm.triggerArtistsRefresh()
                                            }
                                        } else {
                                            scope.launch { pagerState.animateScrollToPage(i) }
                                        }
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    Modifier
                                        .width(24.dp)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(if (isSelected) Color(0xFF808080) else Color.Transparent)
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = label,
                                    color = if (isSelected) Red else Color.Gray,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            NavHost(nav, startDestination = "MAIN") {
                composable("MAIN") {
                    HorizontalPager(
                        state = pagerState,
                        beyondViewportPageCount = 1 
                    ) { page ->
                        when (page) {
                            0 -> LibraryScreen(vm = vm, trackPosters = trackPosters, onTrackClick = { nav.navigate("PLAYER") })
                            1 -> ArtistsScreen(vm = vm, trackPosters = trackPosters, onTrackClick = { nav.navigate("PLAYER") })
                            2 -> PlaylistsScreen(vm = vm, trackPosters = trackPosters, onPlaylistClick = { nav.navigate("PLAYER") })
                            3 -> SettingsScreen(vm = vm, onOpenSources = { nav.navigate("SOURCES") })
                        }
                    }
                }
                composable("PLAYER") { PlayerScreen(vm = vm, trackPosters = trackPosters, onBack = { nav.popBackStack() }) }
                composable("SOURCES") { SourceFoldersScreen(vm = vm, onBack = { nav.popBackStack() }) }
            }
        }
    }
}
