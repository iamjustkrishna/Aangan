package app.familyvault

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.familyvault.data.MediaPlayer

/**
 * One ExoPlayer for the whole app. Videos are shared "anyone with the link" and fetched with the
 * app's API key (with drive.file the viewer's own token gets HTTP 404 on others' files). The
 * Android headers let the key be restricted to this app in Google Cloud.
 */
class AndroidMediaPlayer(private val context: Context, private val certSha1: String) : MediaPlayer {

    override var isPlaying by mutableStateOf(false); private set
    override var isBuffering by mutableStateOf(false); private set
    override var positionMs by mutableLongStateOf(0L); private set
    override var durationMs by mutableLongStateOf(0L); private set
    override var error by mutableStateOf<String?>(null); private set

    private val main = Handler(Looper.getMainLooper())

    private val exo: ExoPlayer by lazy {
        ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) { this@AndroidMediaPlayer.isPlaying = playing }
                override fun onPlaybackStateChanged(state: Int) {
                    isBuffering = state == Player.STATE_BUFFERING
                    if (duration > 0) durationMs = duration
                }
                override fun onPlayerError(e: PlaybackException) {
                    val cause = e.cause
                    error = if (cause is HttpDataSource.InvalidResponseCodeException) "HTTP ${cause.responseCode}" else e.errorCodeName
                }
            })
        }
    }

    /** Updates the seek bar position twice a second. */
    private val ticker = object : Runnable {
        override fun run() {
            positionMs = exo.currentPosition
            if (exo.duration > 0) durationMs = exo.duration
            main.postDelayed(this, 500)
        }
    }

    override fun load(fileId: String) {
        error = null
        positionMs = 0; durationMs = 0
        if (BuildConfig.GOOGLE_API_KEY.isBlank()) { error = "This build has no google.api.key in local.properties"; return }
        val http = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(mapOf("X-Android-Package" to context.packageName, "X-Android-Cert" to certSha1))
            .setAllowCrossProtocolRedirects(true)
        val url = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media&key=${BuildConfig.GOOGLE_API_KEY}"
        exo.setMediaSource(ProgressiveMediaSource.Factory(http).createMediaSource(MediaItem.fromUri(Uri.parse(url))))
        exo.prepare()
        exo.playWhenReady = true
        main.removeCallbacks(ticker)
        main.post(ticker)
    }

    override fun play() { exo.play() }
    override fun pause() { exo.pause() }
    override fun seekTo(ms: Long) { exo.seekTo(ms); positionMs = ms }

    override fun stop() {
        exo.stop()
        exo.clearMediaItems()
        main.removeCallbacks(ticker)
        isPlaying = false
    }

    @Composable
    override fun Surface(modifier: Modifier) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    keepScreenOn = true
                    player = exo
                }
            },
            update = { it.player = exo },
            onRelease = { it.player = null },
            modifier = modifier,
        )
    }
}
