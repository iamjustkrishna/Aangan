package app.familyvault.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.familyvault.AppState
import app.familyvault.data.UploadState
import app.familyvault.data.Video
import app.familyvault.defaultTitle
import app.familyvault.theme.FV
import app.familyvault.theme.Radius
import app.familyvault.theme.Sizes
import app.familyvault.theme.Space
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime

// ---------- Helpers ----------

private val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

fun relativeDate(iso: String): String = runCatching {
    val tz = TimeZone.currentSystemDefault()
    val d = Instant.parse(iso).toLocalDateTime(tz).date
    val today = Clock.System.now().toLocalDateTime(tz).date
    when (val days = d.daysUntil(today)) {
        0 -> "Today"
        1 -> "Yesterday"
        in 2..6 -> "$days days ago"
        else -> "${d.dayOfMonth} ${monthNames[d.monthNumber - 1]}" + if (d.year != today.year) " ${d.year}" else ""
    }
}.getOrDefault("")

fun duration(s: Int?): String? = s?.let { "${it / 60}:${(it % 60).toString().padStart(2, '0')}" }

fun thumbToneFor(v: Video): Int = (v.id.hashCode().mod(4)) + 1

@Composable
private fun FVTextField(value: String, onChange: (String) -> Unit, label: String, keyboard: KeyboardType = KeyboardType.Text, modifier: Modifier = Modifier) {
    val c = FV.colors
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        textStyle = MaterialTheme.typography.bodyMedium,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ImeAction.Done),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.primary, unfocusedBorderColor = c.lineStrong,
            focusedLabelColor = c.primary, unfocusedLabelColor = c.inkMuted, cursorColor = c.primary,
        ),
    )
}

@Composable
private fun Point(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
        IconCircle(icon)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = FV.colors.ink)
    }
}

// ---------- Setup (missing config) ----------

@Composable
fun SetupScreen() {
    val c = FV.colors
    Column(Modifier.fillMaxSize().background(c.surface).statusBarsPadding().padding(Space.s4), verticalArrangement = Arrangement.spacedBy(Space.s3)) {
        Spacer(Modifier.height(Space.s7))
        Text("Almost ready", style = MaterialTheme.typography.headlineMedium, color = c.ink)
        Text(
            "This build has no Supabase settings. Add supabase.url and supabase.anon.key to local.properties, then build the app again.",
            style = MaterialTheme.typography.bodyLarge, color = c.inkMuted,
        )
    }
}

// ---------- Onboarding: connect Drive ----------

@Composable
fun ConnectScreen(state: AppState) {
    val c = FV.colors
    Column(
        Modifier.fillMaxSize().background(c.surface).statusBarsPadding().padding(horizontal = Space.s4).padding(top = Space.s7, bottom = Space.s5),
        verticalArrangement = Arrangement.spacedBy(Space.s5),
    ) {
        AppLogo(72.dp)
        Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
            Text("Keep your family's videos in one place", style = MaterialTheme.typography.headlineMedium, color = c.ink)
            Text("Record or upload once. Everyone in your family can watch.", style = MaterialTheme.typography.bodyLarge, color = c.inkMuted)
        }
        Spacer(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(Space.s4)) {
            Point(Icons.Outlined.Folder, "Videos are saved in your own Google Drive")
            Point(Icons.Outlined.Lock, "The app can only see videos it saves there")
            Point(Icons.Outlined.Groups, "Only your family can watch them")
        }
        FVButton(
            if (state.busy) "Waiting for Google…" else "Connect Google Drive",
            { state.connectDrive() }, Modifier.fillMaxWidth(), ButtonStyle.Primary, enabled = !state.busy,
        )
    }
}

// ---------- Onboarding: join or create a family ----------

@Composable
fun StartScreen(state: AppState) {
    val c = FV.colors
    var name by remember { mutableStateOf("Family") }
    var hidden by remember { mutableStateOf(setOf<String>()) }
    Column(Modifier.fillMaxSize().background(c.surface).statusBarsPadding()) {
        TopBar(
            title = if (state.families.isEmpty()) "Welcome" else "Join or create",
            subtitle = state.me?.googleEmail,
            onBack = if (state.family != null) ({ state.backToMain() }) else null,
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.s4).padding(bottom = Space.s5),
            verticalArrangement = Arrangement.spacedBy(Space.s5),
        ) {
            val invites = state.invitesForMe.filter { it.id !in hidden }
            if (invites.isNotEmpty()) {
                SectionLabel(if (invites.size == 1) "You have an invite" else "You have ${invites.size} invites")
                invites.forEach { inv ->
                    InviteCard(inv.invitedByName, inv.familyName, inv.memberCount, inv.videoCount,
                        onJoin = { state.acceptInvite(inv) }, onNotNow = { hidden = hidden + inv.id }, busy = state.busy)
                }
            } else {
                Banner("No invites for ${state.me?.googleEmail ?: "you"} yet. Ask a family member to invite this email, then tap Check again.", Icons.Outlined.Mail)
                FVButton("Check again", { state.refresh() }, Modifier.fillMaxWidth(), ButtonStyle.Tonal, Icons.Outlined.Refresh, enabled = !state.busy)
            }
            Column(verticalArrangement = Arrangement.spacedBy(Space.s3)) {
                SectionLabel("Start a new family")
                Text("You'll be its admin and can invite people by email.", style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
                FVTextField(name, { name = it }, "Family name")
                FVButton("Create family", { state.createFamily(name) }, Modifier.fillMaxWidth(),
                    if (invites.isEmpty()) ButtonStyle.Primary else ButtonStyle.Outline, enabled = !state.busy && name.isNotBlank())
            }
        }
    }
}

// ---------- Home ----------

@Composable
fun HomeScreen(state: AppState) {
    val c = FV.colors
    val uploaders = state.members.filter { m -> state.videos.any { it.uploaderId == m.id } }
    val chips = listOf("All") + uploaders.map { it.firstName }
    val selected = state.filter?.let { id -> uploaders.indexOfFirst { it.id == id } + 1 } ?: 0
    val shown = state.videos.filter { state.filter == null || it.uploaderId == state.filter }
    val active = state.uploads.filter { it.state != UploadState.Saved }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Space.s5), verticalArrangement = Arrangement.spacedBy(Space.s5)) {
        if (chips.size > 2) item {
            FilterChips(chips, selected, { i -> state.filter = if (i == 0) null else uploaders[i - 1].id }, Modifier.padding(top = Space.s1))
        }
        items(active, key = { it.localId }) { up ->
            val me = state.me
            VideoCard(
                up.title, "You", me?.displayName ?: "You", me?.avatarTone ?: 1,
                if (up.state == UploadState.Failed) "Tap Uploads to try again" else "Uploading now",
                duration(up.video.durationS), 1, onClick = null, status = up.state, progress = up.progress,
            )
        }
        items(shown, key = { it.id }) { v ->
            val p = state.personById(v.uploaderId)
            VideoCard(
                v.title, p?.firstName ?: "Family member", p?.name ?: "?", p?.profile?.avatarTone ?: 1,
                relativeDate(v.createdAt), duration(v.durationS), thumbToneFor(v), onClick = { state.play(v) },
                imageUrl = state.thumbnailUrl(v),
            )
        }
        if (shown.isEmpty() && active.isEmpty()) item {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = Space.s4, vertical = Space.s7),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.s3),
            ) {
                IconCircle(Icons.Outlined.PlayArrow, size = 64.dp, iconSize = 32.dp)
                Text("No videos yet", style = MaterialTheme.typography.titleMedium, color = c.ink)
                Text("Tap + to record a video or choose one from your gallery.", style = MaterialTheme.typography.bodyMedium, color = c.inkMuted, textAlign = TextAlign.Center)
            }
        }
    }
}

// ---------- Family ----------

@Composable
fun FamilyScreen(state: AppState) {
    val c = FV.colors
    var email by remember { mutableStateOf("") }
    val myId = state.me?.id
    val iAmAdmin = state.members.any { it.id == myId && it.role == "admin" }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(Space.s4), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(Space.s3), modifier = Modifier.padding(bottom = Space.s4)) {
                SectionLabel("Invite someone")
                Text(
                    "Type their Google email. They'll see the invite when they open Aangan with that account.",
                    style = MaterialTheme.typography.bodySmall, color = c.inkMuted,
                )
                FVTextField(email, { email = it }, "Email address", KeyboardType.Email)
                FVButton("Send invite", { state.invite(email); email = "" }, Modifier.fillMaxWidth(), ButtonStyle.Primary,
                    Icons.Outlined.PersonAdd, enabled = !state.busy && email.contains('@'))
            }
        }
        if (state.pendingInvites.isNotEmpty()) {
            item { SectionLabel("Not joined yet") }
            items(state.pendingInvites, key = { it.id }) { inv ->
                MemberRow(inv.email ?: "", "Invited ${relativeDate(inv.createdAt).lowercase()}", app.familyvault.data.toneFor(inv.email ?: ""))
            }
            item { Spacer(Modifier.height(Space.s3)) }
        }
        item { SectionLabel("Members") }
        items(state.members, key = { it.id }) { p ->
            MemberRow(p.name, p.profile.googleEmail, p.profile.avatarTone, admin = p.role == "admin", you = p.id == myId)
        }
        if (!iAmAdmin) item {
            Text("Anyone in the family can invite people in this test version.", style = MaterialTheme.typography.bodySmall, color = c.inkMuted, modifier = Modifier.padding(top = Space.s3))
        }
    }
}

// ---------- Uploads ----------

@Composable
fun UploadsScreen(state: AppState) {
    val c = FV.colors
    if (state.uploads.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(Space.s4), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            IconCircle(Icons.Outlined.Upload, 64.dp, 32.dp)
            Spacer(Modifier.height(Space.s3))
            Text("Nothing uploading", style = MaterialTheme.typography.titleMedium, color = c.ink)
            Text("Videos you add show up here until they're saved.", style = MaterialTheme.typography.bodyMedium, color = c.inkMuted, textAlign = TextAlign.Center)
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(Space.s4), verticalArrangement = Arrangement.spacedBy(Space.s4)) {
        items(state.uploads, key = { it.localId }) { up ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                Thumb(1, duration(up.video.durationS), null, 0f, rounded = true, modifier = Modifier.size(width = 120.dp, height = 68.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                    Text(up.title, style = MaterialTheme.typography.labelLarge, color = c.ink, maxLines = 2)
                    UploadStatusChip(up.state, up.progress)
                    if (up.state == UploadState.Uploading) ProgressBar(up.progress)
                    if (up.state == UploadState.Failed) {
                        up.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.danger, maxLines = 3) }
                        FVButton("Try again", { state.retry(up.localId) }, small = true)
                    }
                }
            }
        }
    }
}

// ---------- You ----------

@Composable
fun YouScreen(state: AppState) {
    val c = FV.colors
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(Space.s4), verticalArrangement = Arrangement.spacedBy(Space.s3)) {
        item {
            state.me?.let { me ->
                MemberRow(me.displayName, me.googleEmail, me.avatarTone)
            }
        }
        item { SectionLabel("Your families", Modifier.padding(top = Space.s3)) }
        items(state.families, key = { it.id }) { f ->
            val on = f.id == state.family?.id
            Row(
                Modifier.fillMaxWidth().heightIn(min = Sizes.touchMin).clip(RoundedCornerShape(Radius.md))
                    .background(if (on) c.primaryTint else c.surfaceSunken).clickable { state.switchFamily(f) }.padding(horizontal = Space.s4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(f.name, style = MaterialTheme.typography.labelLarge, color = if (on) c.primary else c.ink, modifier = Modifier.weight(1f))
                if (on) Text("Open", style = MaterialTheme.typography.labelSmall, color = c.primary)
            }
        }
        item {
            val n = state.invitesForMe.size
            FVButton(if (n > 0) "See invites ($n) or start a family" else "Join or start another family", { state.showStart() }, Modifier.fillMaxWidth(), ButtonStyle.Tonal)
        }
        item { FVButton("Switch Google account", { state.signOut() }, Modifier.fillMaxWidth(), ButtonStyle.Outline) }
    }
}

// ---------- Add sheet and naming ----------

@Composable
fun AddSheetContent(state: AppState) {
    val c = FV.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.s4).padding(bottom = Space.s4), verticalArrangement = Arrangement.spacedBy(Space.s1)) {
        Text("Add a video", style = MaterialTheme.typography.titleMedium, color = c.ink, modifier = Modifier.padding(bottom = Space.s2))
        SheetRow(Icons.Outlined.CameraAlt, "Record a video", "Opens the camera") { state.recordVideo() }
        SheetRow(Icons.Outlined.PhotoLibrary, "Choose from gallery", "Pick a video already on your phone") { state.pickFromGallery() }
        FVButton("Cancel", { state.showAddSheet = false }, Modifier.fillMaxWidth(), ButtonStyle.Quiet)
    }
}

@Composable
private fun SheetRow(icon: ImageVector, label: String, hint: String, onClick: () -> Unit) {
    val c = FV.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = Sizes.touchComfy).clip(RoundedCornerShape(Radius.md)).clickable(onClick = onClick).padding(Space.s2),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s4),
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(c.surfaceSunken), contentAlignment = Alignment.Center) { Icon(icon, null, tint = c.ink) }
        Column {
            Text(label, style = MaterialTheme.typography.labelLarge, fontSize = 17.sp, color = c.ink)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
        }
    }
}

@Composable
fun NameVideoContent(state: AppState) {
    val c = FV.colors
    var title by remember { mutableStateOf(defaultTitle()) }
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.s4).padding(bottom = Space.s4), verticalArrangement = Arrangement.spacedBy(Space.s3)) {
        Text("Save to ${state.family?.name ?: "family"}", style = MaterialTheme.typography.titleMedium, color = c.ink)
        state.videoToName?.let { v ->
            Text(listOfNotNull(duration(v.durationS), "${v.sizeBytes / 1_000_000} MB").joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
        }
        FVTextField(title, { title = it }, "Title")
        FVButton("Upload", { state.startUpload(title) }, Modifier.fillMaxWidth(), ButtonStyle.Primary, Icons.Outlined.Upload)
        FVButton("Cancel", { state.videoToName = null }, Modifier.fillMaxWidth(), ButtonStyle.Quiet)
    }
}

@Composable
fun LoadingScreen() {
    Box(Modifier.fillMaxSize().background(FV.colors.surface), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = FV.colors.primary)
    }
}
