package app.familyvault.data

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

data class GoogleUser(val email: String, val name: String, val idToken: String)

/** A video chosen or recorded on the phone, copied somewhere the app can read it, plus a JPEG frame. */
data class PickedVideo(val path: String, val sizeBytes: Long, val durationS: Int?, val thumbnailPath: String? = null)

/**
 * Where video files live. The prototype uses each uploader's Google Drive (drive.file scope).
 * Keep everything Drive-specific behind this interface so it can be swapped for R2 later.
 */
interface VideoStorage {
    /** Uploads the file and returns its storage id (the Drive file id). */
    suspend fun upload(video: PickedVideo, title: String, onProgress: (Float) -> Unit): String

    /**
     * Makes the file viewable by anyone who has its link, so other members can stream it.
     * Needed because drive.file blocks other users' apps (HTTP 404). Links are only stored in Supabase.
     */
    suspend fun shareByLink(fileId: String)
}

/** One shared video player for the whole app, so a video keeps playing when it's minimised. */
interface MediaPlayer {
    val isPlaying: Boolean
    val isBuffering: Boolean
    val positionMs: Long
    val durationMs: Long
    val error: String?

    fun load(fileId: String)
    fun play()
    fun pause()
    fun seekTo(ms: Long)
    fun stop()

    /** Draws the current video. Only one surface is shown at a time (full or mini player). */
    @Composable
    fun Surface(modifier: Modifier)
}

/** Lines of the background log, kept on the phone until Supabase has them. */
interface LogStore {
    fun append(line: String)
    fun peek(max: Int): List<String>
    fun remove(count: Int)
}

/** Everything the shared code needs from the phone. Android: AndroidPlatform. iOS: not written yet. */
interface PlatformServices {
    /** Shows Google sign-in (with the drive.file permission) and returns the account. */
    suspend fun signInWithGoogle(): GoogleUser

    /** The Google account still signed in on this phone, if any. */
    fun currentGoogleEmail(): String?

    suspend fun signOutOfGoogle()

    /** A fresh OAuth access token for Drive (drive.file). May show a consent screen. */
    suspend fun driveAccessToken(): String

    suspend fun pickVideo(): PickedVideo?
    suspend fun recordVideo(): PickedVideo?
    fun readFile(path: String): ByteArray

    val storage: VideoStorage
    val player: MediaPlayer

    /** Landscape, edge-to-edge video when true. */
    fun setFullscreen(on: Boolean)

    /** The system back button calls this first; return true if the app handled it. */
    fun setBackHandler(handler: () -> Boolean)

    /** e.g. "Pixel 7 · Android 15". */
    val deviceName: String
    val appVersion: String
}
