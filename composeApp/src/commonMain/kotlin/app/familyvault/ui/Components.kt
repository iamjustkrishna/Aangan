package app.familyvault.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.familyvault.data.UploadState
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import app.familyvault.data.toneFor
import app.familyvault.theme.FV
import app.familyvault.theme.Radius
import app.familyvault.theme.Sizes
import app.familyvault.theme.Space

// ---------- Button ----------

enum class ButtonStyle { Primary, Tonal, Outline, Quiet, Danger }

@Composable
fun FVButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.Tonal,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    small: Boolean = false,
) {
    val c = FV.colors
    val (bg, fg) = when (style) {
        ButtonStyle.Primary -> c.primary to c.onPrimary
        ButtonStyle.Tonal -> c.primaryTint to c.primary
        ButtonStyle.Outline -> Color.Transparent to c.ink
        ButtonStyle.Quiet -> Color.Transparent to c.primary
        ButtonStyle.Danger -> c.danger to c.onDanger
    }
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = if (small) 40.dp else Sizes.touchMin),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = bg, contentColor = fg,
            disabledContainerColor = bg.copy(alpha = bg.alpha * 0.5f), disabledContentColor = fg.copy(alpha = 0.5f),
        ),
        border = if (style == ButtonStyle.Outline) BorderStroke(1.dp, c.lineStrong) else null,
        contentPadding = PaddingValues(horizontal = if (small) Space.s4 else Space.s5),
        elevation = null,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Space.s2))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, fontSize = if (small) 14.sp else 16.sp)
    }
}

// ---------- Avatar ----------

fun initials(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
        .joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

@Composable
fun Avatar(name: String, tone: Int = toneFor(name), size: Dp = Sizes.avatarMd, ring: Boolean = false) {
    val c = FV.colors
    Box(
        modifier = Modifier
            .size(size)
            .then(if (ring) Modifier.border(2.dp, c.surfaceRaised, CircleShape) else Modifier)
            .clip(CircleShape)
            .background(c.tones[(tone - 1).mod(c.tones.size)]),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials(name), color = c.onTone, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.36f).sp)
    }
}

@Composable
fun AvatarStack(people: List<Pair<String, Int>>, max: Int = 4, size: Dp = Sizes.avatarSm) {
    val c = FV.colors
    Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
        people.take(max).forEach { (name, tone) -> Avatar(name, tone, size, ring = true) }
        val extra = people.size - max
        if (extra > 0) {
            Box(
                Modifier.size(size).border(2.dp, c.surfaceRaised, CircleShape).clip(CircleShape).background(c.surfaceSunken),
                contentAlignment = Alignment.Center,
            ) { Text("+$extra", color = c.inkMuted, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.34f).sp) }
        }
    }
}

// ---------- Upload status ----------

@Composable
fun UploadStatusChip(state: UploadState, progress: Float = 0f, compact: Boolean = false) {
    val c = FV.colors
    val (icon, text, colors) = when (state) {
        UploadState.Queued -> Triple(Icons.Outlined.Schedule, "Queued", c.surfaceSunken to c.inkMuted)
        UploadState.Uploading -> Triple(Icons.Outlined.Upload, "Uploading ${(progress * 100).toInt()}%", c.primaryTint to c.primary)
        UploadState.Waiting -> Triple(Icons.Outlined.WifiOff, "Waiting for internet", c.waitingTint to c.waiting)
        UploadState.Saved -> Triple(Icons.Outlined.Check, "Saved", c.successTint to c.success)
        UploadState.Failed -> Triple(Icons.Outlined.ErrorOutline, "Upload failed", c.dangerTint to c.danger)
    }
    Row(
        Modifier.clip(CircleShape).background(colors.first)
            .padding(start = if (compact) 6.dp else 8.dp, end = if (compact) 8.dp else 10.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s1),
    ) {
        Icon(icon, null, tint = colors.second, modifier = Modifier.size(if (compact) 14.dp else 16.dp))
        Text(text, color = colors.second, style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun ProgressBar(value: Float, modifier: Modifier = Modifier, warn: Boolean = false) {
    val c = FV.colors
    LinearProgressIndicator(
        progress = { value.coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
        color = if (warn) c.waiting else c.primary,
        trackColor = c.surfaceSunken,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

// ---------- Thumbnail and video card ----------

@Composable
fun Thumb(tone: Int, duration: String?, status: UploadState?, progress: Float, rounded: Boolean, modifier: Modifier = Modifier, imageUrl: String? = null) {
    val c = FV.colors
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(if (rounded) Radius.md else 0.dp))
            .background(c.thumbs[(tone - 1).mod(c.thumbs.size)])
            .drawBehind {
                val w = size.width * 1.3f
                drawOval(c.thumbHill, topLeft = Offset(-size.width * 0.15f, size.height * 0.7f), size = Size(w, size.height * 0.75f))
            },
    ) {
        // The coloured placeholder stays underneath until (or if) the real thumbnail loads.
        if (imageUrl != null) {
            AsyncImage(model = imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        }
        if (duration != null) {
            Text(
                duration,
                color = c.onScrim,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.BottomEnd).padding(Space.s2)
                    .clip(RoundedCornerShape(4.dp)).background(c.scrim).padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
        if (status != null && status != UploadState.Saved) {
            Box(Modifier.align(Alignment.TopStart).padding(Space.s2)) { UploadStatusChip(status, progress, compact = true) }
        }
        if (status == UploadState.Uploading) {
            LinearProgressIndicator(
                progress = { progress }, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp),
                color = c.primary, trackColor = c.scrim, gapSize = 0.dp, drawStopIndicator = {},
            )
        }
    }
}

@Composable
fun VideoCard(
    title: String,
    uploader: String,
    uploaderFullName: String,
    uploaderTone: Int,
    whenText: String,
    duration: String?,
    thumbTone: Int,
    onClick: (() -> Unit)?,
    status: UploadState? = null,
    progress: Float = 0f,
    bleed: Boolean = true,
    imageUrl: String? = null,
) {
    val c = FV.colors
    Column(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalArrangement = Arrangement.spacedBy(Space.s3),
    ) {
        Thumb(thumbTone, duration, status, progress, rounded = !bleed, imageUrl = imageUrl)
        Row(
            Modifier.padding(horizontal = if (bleed) Space.s4 else 0.dp),
            horizontalArrangement = Arrangement.spacedBy(Space.s3),
        ) {
            Avatar(uploaderFullName, uploaderTone)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelLarge, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("$uploader · $whenText", style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
            }
        }
    }
}

@Composable
fun CompactVideoRow(title: String, sub: String, duration: String?, thumbTone: Int, imageUrl: String?, onClick: () -> Unit) {
    val c = FV.colors
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
        Thumb(thumbTone, duration, null, 0f, rounded = true, modifier = Modifier.width(160.dp), imageUrl = imageUrl)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge, fontSize = 15.sp, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
        }
    }
}

// ---------- Filter chips ----------

@Composable
fun FilterChips(items: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = FV.colors
    androidx.compose.foundation.lazy.LazyRow(
        modifier, horizontalArrangement = Arrangement.spacedBy(Space.s2),
        contentPadding = PaddingValues(horizontal = Space.s4),
    ) {
        items(items.size) { i ->
            val on = i == selected
            Box(
                Modifier.height(36.dp).clip(RoundedCornerShape(Radius.sm))
                    .background(if (on) c.ink else c.surfaceSunken)
                    .clickable { onSelect(i) }.padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text(items[i], style = MaterialTheme.typography.labelMedium, color = if (on) c.surface else c.ink) }
        }
    }
}

// ---------- Top bar ----------

@Composable
fun TopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    me: Pair<String, Int>? = null,
    onMe: (() -> Unit)? = null,
) {
    val c = FV.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = if (onBack != null) Space.s1 else Space.s4, end = Space.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = c.ink) }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontSize = 20.sp, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, fontSize = 13.sp, color = c.inkMuted)
        }
        if (me != null) {
            IconButton(onClick = { onMe?.invoke() }) { Avatar(me.first, me.second, Sizes.avatarSm) }
        }
    }
}

// ---------- Bottom navigation ----------

@Composable
fun BottomNav(active: app.familyvault.Tab, uploadsInProgress: Int, onSelect: (app.familyvault.Tab) -> Unit, onAdd: () -> Unit) {
    val c = FV.colors
    Column(Modifier.fillMaxWidth().background(c.surfaceRaised).navigationBarsPadding()) {
        HorizontalDivider(color = c.line)
        Row(Modifier.fillMaxWidth().padding(top = Space.s1, bottom = Space.s2), verticalAlignment = Alignment.CenterVertically) {
            NavItem("Home", if (active == app.familyvault.Tab.Home) Icons.Filled.Home else Icons.Outlined.Home, active == app.familyvault.Tab.Home) { onSelect(app.familyvault.Tab.Home) }
            NavItem("Family", Icons.Outlined.Groups, active == app.familyvault.Tab.Family) { onSelect(app.familyvault.Tab.Family) }
            Box(Modifier.weight(1f).heightIn(min = Sizes.touchMin), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).border(1.5.dp, c.lineStrong, CircleShape).clickable(onClick = onAdd),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Add, "Add a video", tint = c.ink, modifier = Modifier.size(28.dp)) }
            }
            NavItem("Uploads", Icons.Outlined.Upload, active == app.familyvault.Tab.Uploads, badge = uploadsInProgress) { onSelect(app.familyvault.Tab.Uploads) }
            NavItem("You", Icons.Outlined.Person, active == app.familyvault.Tab.You) { onSelect(app.familyvault.Tab.You) }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavItem(label: String, icon: ImageVector, active: Boolean, badge: Int = 0, onClick: () -> Unit) {
    val c = FV.colors
    Column(
        Modifier.weight(1f).heightIn(min = Sizes.touchMin).clip(RoundedCornerShape(Radius.md)).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box {
            Box(
                Modifier.width(56.dp).height(30.dp).clip(CircleShape).background(if (active) c.primaryTint else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = if (active) c.primary else c.inkMuted) }
            if (badge > 0) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(end = 6.dp).widthIn(min = 18.dp).height(18.dp).clip(CircleShape).background(c.primary),
                    contentAlignment = Alignment.Center,
                ) { Text("$badge", color = c.onPrimary, style = MaterialTheme.typography.labelSmall, fontSize = 11.sp) }
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (active) c.ink else c.inkMuted)
    }
}

// ---------- People ----------

@Composable
fun MemberRow(name: String, sub: String?, tone: Int, admin: Boolean = false, you: Boolean = false, trailing: (@Composable () -> Unit)? = null) {
    val c = FV.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = Sizes.touchComfy),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s3),
    ) {
        Avatar(name, tone)
        Column(Modifier.weight(1f)) {
            Row {
                Text(name, style = MaterialTheme.typography.labelLarge, color = c.ink)
                if (you) Text(" (you)", style = MaterialTheme.typography.bodyMedium, color = c.inkMuted)
            }
            if (!sub.isNullOrBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
        }
        if (admin) {
            Text(
                "Admin", style = MaterialTheme.typography.labelSmall, color = c.inkMuted,
                modifier = Modifier.clip(RoundedCornerShape(Radius.sm)).background(c.surfaceSunken).padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        trailing?.invoke()
    }
}

@Composable
fun InviteCard(from: String, family: String, members: Long, videos: Long, onJoin: () -> Unit, onNotNow: (() -> Unit)?, busy: Boolean) {
    val c = FV.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.md)).border(1.dp, c.line, RoundedCornerShape(Radius.md))
            .background(c.surfaceRaised).padding(horizontal = Space.s4, vertical = Space.s5),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.s3),
    ) {
        Avatar(from, toneFor(from), Sizes.avatarLg)
        Text(
            buildAnnotated(from, " invited you to ", family),
            style = MaterialTheme.typography.bodyLarge, color = c.ink, textAlign = TextAlign.Center,
        )
        Text("$members members · $videos videos", style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
        FVButton("Join family", onJoin, Modifier.fillMaxWidth(), ButtonStyle.Primary, enabled = !busy)
        if (onNotNow != null) FVButton("Not now", onNotNow, Modifier.fillMaxWidth(), ButtonStyle.Quiet)
    }
}

private fun buildAnnotated(a: String, mid: String, b: String) = androidx.compose.ui.text.buildAnnotatedString {
    pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold)); append(a); pop()
    append(mid)
    pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold)); append(b); pop()
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = FV.colors.ink, modifier = modifier)
}

@Composable
fun Banner(text: String, icon: ImageVector, modifier: Modifier = Modifier) {
    val c = FV.colors
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.md)).background(c.primaryTint).padding(horizontal = Space.s4, vertical = Space.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s3),
    ) {
        Icon(icon, null, tint = c.primary)
        Text(text, style = MaterialTheme.typography.bodySmall, fontSize = 15.sp, color = c.ink)
    }
}

@Composable
fun IconCircle(icon: ImageVector, size: Dp = 40.dp, iconSize: Dp = 20.dp) {
    val c = FV.colors
    Box(Modifier.size(size).clip(CircleShape).background(c.primaryTint), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = c.primary, modifier = Modifier.size(iconSize))
    }
}
