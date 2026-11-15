package com.feringsapp.echobox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.feringsapp.echobox.core.settings.Appearance
import com.feringsapp.echobox.designsystem.EchoBoxTheme
import com.feringsapp.echobox.designsystem.LocalAppearance
import com.feringsapp.echobox.designsystem.LocalAppearanceStore
import com.feringsapp.echobox.feature.main.LocalAudioTranscoder
import com.feringsapp.echobox.feature.main.LocalCoreGraph
import com.feringsapp.echobox.feature.main.LocalDemoLibrary
import com.feringsapp.echobox.feature.main.LocalDownloadRepository
import com.feringsapp.echobox.feature.main.LocalHistoryRepository
import com.feringsapp.echobox.feature.main.LocalLibraryStore
import com.feringsapp.echobox.feature.main.LocalMediaStorage
import com.feringsapp.echobox.feature.main.LocalTrackRepository
import com.feringsapp.echobox.feature.player.LocalPlaybackController
import com.feringsapp.echobox.feature.upload.LocalSubmissionRepository
import com.feringsapp.echobox.media.PlaybackController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false
        val graph = (application as EchoBoxApplication).graph
        setContent {
            val appearance by graph.appearanceStore.flow.collectAsState(initial = Appearance())
            val playback = remember { PlaybackController(applicationContext) }
            LaunchedEffect(Unit) { playback.connect() }
            CompositionLocalProvider(
                LocalAppearance provides appearance,
                LocalAppearanceStore provides graph.appearanceStore,
                LocalPlaybackController provides playback,
                LocalTrackRepository provides graph.trackRepository,
                LocalDemoLibrary provides graph.demoLibrary,
                LocalLibraryStore provides graph.libraryStore,
                LocalMediaStorage provides graph.mediaStorage,
                LocalAudioTranscoder provides graph.audioTranscoder,
                LocalDownloadRepository provides graph.downloadRepository,
                LocalHistoryRepository provides graph.historyRepository,
                LocalCoreGraph provides graph,
                LocalSubmissionRepository provides graph.submissionRepository,
            ) {
                EchoBoxTheme(appearance = appearance) {
                    EchoBoxApp()
                }
            }
        }
    }
}
