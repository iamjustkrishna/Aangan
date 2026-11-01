package app.familyvault

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.familyvault.data.PlatformServices
import app.familyvault.data.RemoteLog
import app.familyvault.data.Repository
import app.familyvault.data.UploadState
import app.familyvault.theme.FV
import app.familyvault.theme.FamilyVaultTheme
import app.familyvault.ui.AddSheetContent
import app.familyvault.ui.BottomNav
import app.familyvault.ui.ConnectScreen
import app.familyvault.ui.FamilyScreen
import app.familyvault.ui.HomeScreen
import app.familyvault.ui.LoadingScreen
import app.familyvault.ui.NameVideoContent
import app.familyvault.ui.ExpandedPlayer
import app.familyvault.ui.MiniPlayer
import app.familyvault.ui.SetupScreen
import app.familyvault.ui.StartScreen
import app.familyvault.ui.TopBar
import app.familyvault.ui.UploadsScreen
import app.familyvault.ui.YouScreen

@Composable
fun App(platform: PlatformServices, repository: Repository?, remoteLog: RemoteLog?) {
    val scope = rememberCoroutineScope()
    val state = remember { AppState(platform, repository, scope, remoteLog) }
    LaunchedEffect(Unit) { state.start() }

    FamilyVaultTheme {
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(state.message) {
            state.message?.let { snackbar.showSnackbar(it); state.message = null }
        }
        Box(Modifier.fillMaxSize().background(FV.colors.surface)) {
            when (state.screen) {
                Screen.Loading -> LoadingScreen()
                Screen.Setup -> SetupScreen()
                Screen.Connect -> ConnectScreen(state)
                Screen.Start -> StartScreen(state)
                Screen.Main -> MainScreen(state)
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(state: AppState) {
    val me = state.me
    val playing = state.playing
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(FV.colors.surface)) {
        Column(Modifier.statusBarsPadding()) {
            when (state.tab) {
                Tab.Home -> TopBar(state.family?.name ?: "Family", me = me?.let { it.displayName to it.avatarTone }, onMe = { state.tab = Tab.You })
                Tab.Family -> TopBar("Family", "${state.family?.name} · ${state.members.size} people", me = me?.let { it.displayName to it.avatarTone }, onMe = { state.tab = Tab.You })
                Tab.Uploads -> TopBar("Uploads", me = me?.let { it.displayName to it.avatarTone }, onMe = { state.tab = Tab.You })
                Tab.You -> TopBar("You", state.me?.googleEmail)
            }
        }
        Box(Modifier.weight(1f)) {
            when (state.tab) {
                Tab.Home -> HomeScreen(state)
                Tab.Family -> FamilyScreen(state)
                Tab.Uploads -> UploadsScreen(state)
                Tab.You -> YouScreen(state)
            }
        }
        // Mini player docks above the bottom bar while you keep browsing.
        if (playing != null && !state.playerExpanded) MiniPlayer(state, playing)
        BottomNav(
            active = state.tab,
            uploadsInProgress = state.uploads.count { it.state != UploadState.Saved },
            // Opening Home or Family reloads, so new members and videos from other phones show up.
            onSelect = { tab -> state.tab = tab; if (tab == Tab.Home || tab == Tab.Family) state.refresh() },
            onAdd = { state.showAddSheet = true },
        )
    }
    if (playing != null) {
        AnimatedVisibility(
            state.playerExpanded,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) { ExpandedPlayer(state, playing) }
    }
    }

    if (state.showAddSheet) {
        ModalBottomSheet(onDismissRequest = { state.showAddSheet = false }, sheetState = rememberModalBottomSheetState(), containerColor = FV.colors.surfaceRaised) {
            AddSheetContent(state)
        }
    }
    if (state.videoToName != null) {
        ModalBottomSheet(onDismissRequest = { state.videoToName = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = FV.colors.surfaceRaised) {
            NameVideoContent(state)
        }
    }
}
