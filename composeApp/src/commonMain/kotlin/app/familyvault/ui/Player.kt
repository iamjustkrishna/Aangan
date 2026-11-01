package app.familyvault.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.familyvault.AppState
import app.familyvault.data.Video
import app.familyvault.theme.FV
import app.familyvault.theme.Radius
import app.familyvault.theme.Space
import kotlinx.coroutines.delay

private fun clock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = (s % 60).toString().padStart(2, '0')
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:$sec" else "$m:$sec"
}

/**
 * The video with YouTube-style controls: tap to show or hide them, double-tap the left or right
 * half to skip 10 s, a seek bar with times, fullscreen, and a down arrow (or swipe down) to minimise.
 */
@Composable
fun VideoWithControls(state: AppState, modifier: Modifier = Modifier) {
    val c = FV.colors
    val player = state.platform.player
    var showControls by remember { mutableStateOf(true) }
    var seekHint by remember { mutableStateOf<String?>(null) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    var dragDown by remember { mutableStateOf(0f) }
    var lastTouch by remember { mutableStateOf(0L) }

    // Hide the controls 3 s after the last touch while the video plays.
    LaunchedEffect(showControls, player.isPlaying, lastTouch) {
        if (showControls && player.isPlaying) { delay(3_000); showControls = false }
    }
    LaunchedEffect(seekHint) { if (seekHint != null) { delay(700); seekHint = null } }

    fun skip(deltaMs: Long) {
        player.seekTo((player.positionMs + deltaMs).coerceIn(0, maxOf(player.durationMs, 0)))
        seekHint = if (deltaMs < 0) "−10 s" else "+10 s"
    }

    Box(modifier.background(c.camera), contentAlignment = Alignment.Center) {
        player.Surface(Modifier.fillMaxSize())
        // Touch layer above the video (the video view itself doesn't handle touches).
        Box(
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { showControls = !showControls; lastTouch++ },
                        onDoubleTap = { o -> skip(if (o.x < size.width / 2) -10_000 else 10_000) },
                    )
                }
                .pointerInput(state.fullscreen) {
                    if (!state.fullscreen) detectVerticalDragGestures(
                        onDragEnd = { if (dragDown > 120f) state.minimizePlayer(); dragDown = 0f },
                        onDragCancel = { dragDown = 0f },
                    ) { _, dy -> dragDown += dy }
                },
        )

        if (player.isBuffering) CircularProgressIndicator(color = c.onScrim, strokeWidth = 3.dp, modifier = Modifier.size(48.dp))

        seekHint?.let {
            Text(it, color = c.onScrim, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clip(CircleShape).background(c.scrim).padding(horizontal = 14.dp, vertical = 6.dp))
        }

        AnimatedVisibility(showControls || !player.isPlaying, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))) {
                // Top: minimise
                if (!state.fullscreen) {
                    IconButton(onClick = { state.minimizePlayer() }, modifier = Modifier.align(Alignment.TopStart)) {
                        Icon(Icons.Outlined.KeyboardArrowDown, "Minimise video", tint = c.onScrim, modifier = Modifier.size(30.dp))
                    }
                }
                // Middle: back 10, play/pause, forward 10
                Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(Space.s6), verticalAlignment = Alignment.CenterVertically) {
                    RoundControl(Icons.Outlined.Replay10, "Back 10 seconds", 48) { skip(-10_000); lastTouch++ }
                    RoundControl(if (player.isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        if (player.isPlaying) "Pause" else "Play", 64) {
                        if (player.isPlaying) player.pause() else player.play(); lastTouch++
                    }
                    RoundControl(Icons.Outlined.Forward10, "Forward 10 seconds", 48) { skip(10_000); lastTouch++ }
                }
                // Bottom: time, seek bar, fullscreen
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = Space.s3)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val pos = dragging?.toLong() ?: player.positionMs
                        Text("${clock(pos)} / ${clock(player.durationMs)}", color = c.onScrim, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                        IconButton(onClick = { state.changeFullscreen(!state.fullscreen); lastTouch++ }) {
                            Icon(if (state.fullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                                if (state.fullscreen) "Exit full screen" else "Full screen", tint = c.onScrim)
                        }
                    }
                    Slider(
                        value = (dragging ?: player.positionMs.toFloat()).coerceIn(0f, maxOf(player.durationMs, 1).toFloat()),
                        valueRange = 0f..maxOf(player.durationMs, 1).toFloat(),
                        onValueChange = { dragging = it; lastTouch++ },
                        onValueChangeFinished = { dragging?.let { player.seekTo(it.toLong()) }; dragging = null },
                        colors = SliderDefaults.colors(thumbColor = c.record, activeTrackColor = c.record, inactiveTrackColor = c.onScrim.copy(alpha = 0.35f)),
                        modifier = Modifier.height(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RoundControl(icon: ImageVector, label: String, size: Int, onClick: () -> Unit) {
    val c = FV.colors
    Box(Modifier.size(size.dp).clip(CircleShape).background(c.scrim).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = c.onScrim, modifier = Modifier.size((size * 0.55f).dp))
    }
}

/** The expanded player: video on top, title, uploader and "Up next". Fills the whole screen. */
@Composable
fun ExpandedPlayer(state: AppState, video: Video) {
    val c = FV.colors
    if (state.fullscreen) {
        VideoWithControls(state, Modifier.fillMaxSize())
        return
    }
    val p = state.personById(video.uploaderId)
    Column(Modifier.fillMaxSize().background(c.surface).statusBarsPadding()) {
        VideoWithControls(state, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        LazyColumn(contentPadding = PaddingValues(Space.s4), verticalArrangement = Arrangement.spacedBy(Space.s4)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Space.s1)) {
                    Text(video.title, style = MaterialTheme.typography.titleMedium, color = c.ink)
                    Text(listOfNotNull(relativeDate(video.createdAt), duration(video.durationS)).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
                }
            }
            state.platform.player.error?.let { msg ->
                item {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.md)).background(c.dangerTint).padding(Space.s3),
                        verticalArrangement = Arrangement.spacedBy(Space.s1),
                    ) {
                        Text("This video can't play", style = MaterialTheme.typography.labelLarge, color = c.danger)
                        Text("Google Drive didn't send it. $msg", style = MaterialTheme.typography.bodySmall, color = c.ink)
                    }
                }
            }
            item { MemberRow(p?.firstName ?: "Family member", "Added this video", p?.profile?.avatarTone ?: 1) }
            val next = state.videos.filter { it.id != video.id }
            if (next.isNotEmpty()) {
                item { SectionLabel("Up next") }
                items(next, key = { it.id }) { v ->
                    val up = state.personById(v.uploaderId)
                    CompactVideoRow(v.title, "${up?.firstName ?: ""} · ${relativeDate(v.createdAt)}", duration(v.durationS),
                        thumbToneFor(v), state.thumbnailUrl(v)) { state.play(v) }
                }
            }
        }
    }
}

/** The docked mini player above the bottom bar. Tap to open it again. */
@Composable
fun MiniPlayer(state: AppState, video: Video) {
    val c = FV.colors
    val player = state.platform.player
    val p = state.personById(video.uploaderId)
    Column(Modifier.fillMaxWidth().background(c.surfaceRaised)) {
        val progress = if (player.durationMs > 0) player.positionMs.toFloat() / player.durationMs else 0f
        Box(Modifier.fillMaxWidth().height(2.dp).background(c.line)) {
            Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(2.dp).background(c.record))
        }
        Row(
            Modifier.fillMaxWidth().height(64.dp).clickable { state.expandPlayer() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(114.dp).height(64.dp).background(c.camera)) { player.Surface(Modifier.fillMaxSize()) }
            Column(Modifier.weight(1f).padding(horizontal = Space.s3)) {
                Text(video.title, style = MaterialTheme.typography.labelMedium, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(p?.firstName ?: "", style = MaterialTheme.typography.bodySmall, color = c.inkMuted, maxLines = 1)
            }
            IconButton(onClick = { if (player.isPlaying) player.pause() else player.play() }) {
                Icon(if (player.isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, if (player.isPlaying) "Pause" else "Play", tint = c.ink)
            }
            IconButton(onClick = { state.closePlayer() }) { Icon(Icons.Outlined.Close, "Close video", tint = c.ink) }
        }
    }
}
