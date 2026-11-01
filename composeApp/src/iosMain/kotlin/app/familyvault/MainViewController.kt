package app.familyvault

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import app.familyvault.data.GoogleUser
import app.familyvault.data.MediaPlayer
import app.familyvault.data.PickedVideo
import app.familyvault.data.PlatformServices
import app.familyvault.data.VideoStorage

// iOS entry point. Not built yet (needs a Mac). Everything phone-specific below is still TODO:
// Google sign-in (GoogleSignIn-iOS), Drive upload, PHPicker/camera, AVPlayer, file log store.
fun MainViewController() = ComposeUIViewController { App(IosPlatform(), null, null) }

private class IosPlatform : PlatformServices {
    override suspend fun signInWithGoogle(): GoogleUser = TODO("iOS Google sign-in")
    override fun currentGoogleEmail(): String? = null
    override suspend fun signOutOfGoogle() {}
    override suspend fun driveAccessToken(): String = TODO("iOS Drive token")
    override suspend fun pickVideo(): PickedVideo? = TODO("iOS video picker")
    override suspend fun recordVideo(): PickedVideo? = TODO("iOS camera")
    override fun readFile(path: String): ByteArray = TODO("iOS file read")
    override val storage: VideoStorage = object : VideoStorage {
        override suspend fun upload(video: PickedVideo, title: String, onProgress: (Float) -> Unit): String = TODO("iOS Drive upload")
        override suspend fun shareByLink(fileId: String) = TODO("iOS Drive share")
    }
    override val player: MediaPlayer = object : MediaPlayer {
        override val isPlaying = false
        override val isBuffering = false
        override val positionMs = 0L
        override val durationMs = 0L
        override val error: String? = "Not available on iOS yet"
        override fun load(fileId: String) {}
        override fun play() {}
        override fun pause() {}
        override fun seekTo(ms: Long) {}
        override fun stop() {}
        @Composable override fun Surface(modifier: Modifier) { Box(modifier) }
    }
    override fun setFullscreen(on: Boolean) {}
    override fun setBackHandler(handler: () -> Boolean) {}
    override val deviceName = "iOS"
    override val appVersion = "0.1.0-test"
}
