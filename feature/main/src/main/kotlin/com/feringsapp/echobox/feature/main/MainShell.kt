package com.feringsapp.echobox.feature.main

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.feringsapp.echobox.core.model.Track
import com.feringsapp.echobox.designsystem.EchoGlass
import com.feringsapp.echobox.designsystem.FloatingBottomBar
import com.feringsapp.echobox.designsystem.FloatingBottomBarDefaults
import com.feringsapp.echobox.designsystem.LocalAccentColor
import com.feringsapp.echobox.designsystem.LocalGlassIntensity
import com.feringsapp.echobox.designsystem.LocalGlassMode
import com.feringsapp.echobox.designsystem.LocalMotionScale
import com.feringsapp.echobox.designsystem.NowPlayingBar
import com.feringsapp.echobox.feature.player.LocalPlaybackController
import com.feringsapp.echobox.feature.player.PlayerScreen
import com.feringsapp.echobox.media.PlaybackUiState
import com.feringsapp.echobox.media.QueueItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme

private object Routes {
    const val home = "home"
    const val library = "library"
    const val discover = "discover"
    const val settings = "settings"
    const val player = "player"
    const val search = "search"
    const val appearance = "appearance"
    const val network = "network"
    const val storage = "storage"
    const val transcode = "transcode"
    const val licenses = "licenses"
    const val upload = "upload"
    const val local = "local"
    const val favorites = "favorites"
    const val recent = "recent"
    const val downloads = "downloads"
}

private data class Tab(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val tabs = listOf(
    Tab(Routes.home, "主页", MiuixIcons.Home),
    Tab(Routes.discover, "发现", MiuixIcons.Search),
    Tab(Routes.library, "音乐", MiuixIcons.Music),
    Tab(Routes.settings, "设置", MiuixIcons.Settings),
)

private fun NavHostController.openTrack(trackId: String) {
    navigate("track/$trackId") { launchSingleTop = true }
}

@Composable
fun MainShell(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val playback = LocalPlaybackController.current
    val graph = LocalCoreGraph.current
    val repo = LocalTrackRepository.current
    val libraryStore = LocalLibraryStore.current
    val historyRepo = LocalHistoryRepository.current
    val downloadRepo = LocalDownloadRepository.current

    val allTracks by repo.observeLatest(300).collectAsState(initial = emptyList())
    val favorites by libraryStore.favorites.collectAsState(initial = emptySet())
    val selectedMirrorId by graph.networkStore.selectedId.collectAsState(initial = "direct")
    val coverBase = remember(selectedMirrorId) { graph.mirrorResolver.coverBase() }
    val emptyState = remember { MutableStateFlow(PlaybackUiState()) }
    val playbackState by (playback?.state ?: emptyState).collectAsState()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Routes.home
    val selectedIndex = tabs.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)

    val glassMode = LocalGlassMode.current
    val glassIntensity = LocalGlassIntensity.current
    val pageBrush = remember {
        Brush.verticalGradient(colors = listOf(Color(0xFFF4F8FF), Color.White))
    }
    val backdrop = rememberLayerBackdrop {
        drawRect(pageBrush)
        drawContent()
    }

    val tabRoutes = remember { tabs.map { it.route }.toSet() }
    val motion = LocalMotionScale.current
    val enterMs = (240f * motion).toInt().coerceIn(90, 900)
    val exitMs = (200f * motion).toInt().coerceIn(70, 800)

    // 播放地址：已下载用本地文件，否则走当前选中源的在线流
    fun uriFor(track: Track): String {
        downloadRepo.localFile(track.file)?.let { return it.toURI().toString() }
        return runCatching { graph.mirrorResolver.resolveAsset(track.file) }.getOrDefault("")
    }

    fun playTracks(tracks: List<Track>, startIndex: Int) {
        if (tracks.isEmpty()) return
        navController.navigate(Routes.player) { launchSingleTop = true }
        scope.launch {
            val index = startIndex.coerceIn(tracks.indices)
            val current = tracks[index]
            val queue = tracks.map { item ->
                QueueItem(
                    id = item.id,
                    title = item.title,
                    artist = item.author,
                    uri = uriFor(item),
                    lyric = "",
                )
            }
            playback?.playQueue(queue, index)
            historyRepo.record(current.id)
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(pageBrush),
    ) {
        CompositionLocalProvider(LocalCoverBase provides coverBase) {
            Box(
                Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop),
            ) {
                NavHost(
                    navController = navController,
                    startDestination = Routes.home,
                    modifier = Modifier.fillMaxSize(),
                    enterTransition = {
                        if (initialState.destination.route in tabRoutes && targetState.destination.route in tabRoutes) {
                            fadeIn(tween(enterMs))
                        } else {
                            slideInHorizontally(tween(enterMs)) { fullWidth -> fullWidth }
                        }
                    },
                    exitTransition = {
                        if (initialState.destination.route in tabRoutes && targetState.destination.route in tabRoutes) {
                            fadeOut(tween(exitMs))
                        } else {
                            slideOutHorizontally(tween(enterMs)) { fullWidth -> -fullWidth }
                        }
                    },
                    popEnterTransition = { slideInHorizontally(tween(enterMs)) { fullWidth -> -fullWidth } },
                    popExitTransition = { slideOutHorizontally(tween(enterMs)) { fullWidth -> fullWidth } },
                ) {
                    composable(Routes.home) {
                        HomeScreen(
                            onTrackClick = { navController.openTrack(it) },
                            onSearch = { navController.navigate(Routes.search) },
                        )
                    }
                    composable(Routes.library) {
                        LibraryScreen(
                            onTrackClick = { navController.openTrack(it) },
                            onLocalMusic = { navController.navigate(Routes.local) },
                            onFavorites = { navController.navigate(Routes.favorites) },
                            onRecent = { navController.navigate(Routes.recent) },
                            onDownloads = { navController.navigate(Routes.downloads) },
                        )
                    }
                    composable(Routes.discover) {
                        DiscoverScreen(
                            onTrackClick = { navController.openTrack(it) },
                            onSearch = { navController.navigate(Routes.search) },
                            onAlbumClick = { navController.openTrack(it) },
                        )
                    }
                    composable(Routes.settings) {
                        SettingsScreen(
                            onAppearance = { navController.navigate(Routes.appearance) },
                            onUpload = { navController.navigate(Routes.upload) },
                            onNetwork = { navController.navigate(Routes.network) },
                            onStorage = { navController.navigate(Routes.storage) },
                            onTranscode = { navController.navigate(Routes.transcode) },
                            onLicenses = { navController.navigate(Routes.licenses) },
                        )
                    }
                    composable(Routes.appearance) {
                        AppearanceScreen(onBack = { navController.popBackStack() })
                    }
                    composable(Routes.network) { NetworkScreen(onBack = { navController.popBackStack() }) }
                    composable(Routes.storage) { StorageScreen(onBack = { navController.popBackStack() }) }
                    composable(Routes.transcode) { TranscodeScreen(onBack = { navController.popBackStack() }) }
                    composable(Routes.licenses) { LicensesScreen(onBack = { navController.popBackStack() }) }
                    composable(Routes.upload) {
                        com.feringsapp.echobox.feature.upload.UploadScreen(
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable(Routes.local) {
                        LocalMusicScreen(
                            onBack = { navController.popBackStack() },
                            onPlay = { tracks, index -> playTracks(tracks, index) },
                        )
                    }
                    composable(Routes.downloads) {
                        DownloadListScreen(
                            onBack = { navController.popBackStack() },
                            onOpenTrack = { navController.openTrack(it) },
                        )
                    }
                    composable(Routes.favorites) {
                        val list = allTracks.filter { it.id in favorites }
                        TrackListScreen(
                            title = "我的收藏",
                            tracks = list,
                            emptyTitle = "还没有收藏",
                            emptySummary = "在曲目详情页右上角点心形",
                            onBack = { navController.popBackStack() },
                            onTrackClick = { navController.openTrack(it) },
                        )
                    }
                    composable(Routes.recent) {
                        val history by historyRepo.observeRecent(50).collectAsState(initial = emptyList())
                        val byId = remember(allTracks) { allTracks.associateBy { it.id } }
                        val list = remember(history, byId) {
                            history.mapNotNull { byId[it.trackId] }.distinctBy { it.id }
                        }
                        TrackListScreen(
                            title = "最近播放",
                            tracks = list,
                            emptyTitle = "还没有播放记录",
                            emptySummary = "随便播一首就会出现",
                            onBack = { navController.popBackStack() },
                            onTrackClick = { navController.openTrack(it) },
                        )
                    }
                    composable(
                        route = "track/{trackId}",
                        arguments = listOf(navArgument("trackId") { type = NavType.StringType }),
                    ) { entry ->
                        TrackDetailScreen(
                            trackId = entry.arguments?.getString("trackId").orEmpty(),
                            onBack = { navController.popBackStack() },
                            onPlay = { track ->
                                val index = allTracks.indexOfFirst { it.id == track.id }
                                playTracks(allTracks, if (index >= 0) index else 0)
                            },
                            onOpenLocal = { navController.navigate(Routes.local) },
                        )
                    }
                    composable(Routes.player) {
                        PlayerScreen(
                            onBack = { navController.popBackStack() },
                            isFavorite = playbackState.trackId in favorites,
                            onToggleFavorite = {
                                scope.launch { libraryStore.toggleFavorite(playbackState.trackId) }
                            },
                        )
                    }
                    composable(Routes.search) {
                        SearchScreen(
                            onBack = { navController.popBackStack() },
                            onTrackClick = { navController.openTrack(it) },
                        )
                    }
                }
            }
        }

        val showTabChrome = currentRoute in tabRoutes
        if (showTabChrome) {
            if (playbackState.hasTrack) {
                NowPlayingBar(
                    title = playbackState.title.ifBlank { "未在播放" },
                    artist = playbackState.artist,
                    isPlaying = playbackState.isPlaying,
                    progress = if (playbackState.durationMs > 0) {
                        playbackState.positionMs.toFloat() / playbackState.durationMs
                    } else {
                        0f
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 92.dp),
                    onClick = { navController.navigate(Routes.player) { launchSingleTop = true } },
                    onToggle = { playback?.toggle() },
                    onOpenPlayer = { navController.navigate(Routes.player) { launchSingleTop = true } },
                )
            }
            FloatingBottomBar(
                items = tabs,
                selectedIndex = { selectedIndex },
                onSelected = { index ->
                    val route = tabs[index].route
                    if (route != currentRoute) {
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                backdrop = backdrop,
                colors = FloatingBottomBarDefaults.colors(
                    indicatorColor = LocalAccentColor.current,
                ),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                iconContent = { item, _ ->
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        modifier = Modifier.size(24.dp),
                    )
                },
                labelContent = { item, _ ->
                    Text(text = item.label, style = MiuixTheme.textStyles.footnote2)
                },
            )
        }
    }
}
