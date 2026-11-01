package app.familyvault

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.familyvault.data.Family
import app.familyvault.data.Invite
import app.familyvault.data.InvitePreview
import app.familyvault.data.Person
import app.familyvault.data.PickedVideo
import app.familyvault.data.PlatformServices
import app.familyvault.data.Profile
import app.familyvault.data.RemoteLog
import app.familyvault.data.Repository
import app.familyvault.data.UploadItem
import app.familyvault.data.UploadState
import app.familyvault.data.Video
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.random.Random

enum class Screen { Loading, Setup, Connect, Start, Main }
enum class Tab { Home, Family, Uploads, You }

/** All app state and actions. Screens read this and call its functions. */
class AppState(
    val platform: PlatformServices,
    private val repo: Repository?,
    private val scope: CoroutineScope,
    private val remoteLog: RemoteLog?,
) {
    var screen by mutableStateOf(Screen.Loading)
    var tab by mutableStateOf(Tab.Home)
    var busy by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)

    var me by mutableStateOf<Profile?>(null)
    var families by mutableStateOf<List<Family>>(emptyList())
    var family by mutableStateOf<Family?>(null)
    var invitesForMe by mutableStateOf<List<InvitePreview>>(emptyList())
    var members by mutableStateOf<List<Person>>(emptyList())
    var pendingInvites by mutableStateOf<List<Invite>>(emptyList())
    var videos by mutableStateOf<List<Video>>(emptyList())
    var filter by mutableStateOf<String?>(null) // uploader id, null = All

    var playing by mutableStateOf<Video?>(null)
    var playerExpanded by mutableStateOf(true)
    var fullscreen by mutableStateOf(false)
        private set
    var showAddSheet by mutableStateOf(false)
    var videoToName by mutableStateOf<PickedVideo?>(null)
    val uploads = mutableStateListOf<UploadItem>()

    /** Drive files this phone already shared by link during this session. */
    private val shared = mutableSetOf<String>()

    fun personById(id: String): Person? = members.firstOrNull { it.id == id }

    /** Sent to Supabase `app_logs` in the background (see RemoteLog). Not shown in the app. */
    fun log(text: String, level: String = "info") {
        remoteLog?.log(text, level)
    }

    fun thumbnailUrl(v: Video): String? = v.thumbnailPath?.let { p -> repo?.thumbnailUrl(p) }

    // ---------- Player ----------

    fun play(v: Video) {
        playing = v
        playerExpanded = true
        platform.player.load(v.driveFileId)
        log("Play \"${v.title}\" (${v.driveFileId})")
    }

    fun minimizePlayer() { changeFullscreen(false); playerExpanded = false }
    fun expandPlayer() { playerExpanded = true }

    fun closePlayer() {
        changeFullscreen(false)
        platform.player.stop()
        playing = null
    }

    fun changeFullscreen(on: Boolean) {
        if (fullscreen == on) return
        fullscreen = on
        platform.setFullscreen(on)
    }

    /** System back button. Returns false to let Android close the app. */
    fun onBack(): Boolean = when {
        fullscreen -> { changeFullscreen(false); true }
        videoToName != null -> { videoToName = null; true }
        showAddSheet -> { showAddSheet = false; true }
        playing != null && playerExpanded -> { minimizePlayer(); true }
        screen == Screen.Start && family != null -> { backToMain(); true }
        screen == Screen.Main && tab != Tab.Home -> { tab = Tab.Home; true }
        else -> false
    }

    private fun run(label: String, block: suspend () -> Unit) {
        scope.launch {
            busy = true
            try {
                block()
            } catch (e: Throwable) {
                log("$label failed: ${e.message}", "error")
                message = "$label didn't work. ${e.message?.lineSequence()?.firstOrNull().orEmpty()}"
            } finally {
                busy = false
            }
        }
    }

    fun start() {
        platform.setBackHandler { onBack() }
        if (repo == null) { screen = Screen.Setup; return }
        log("App opened")
        scope.launch {
            try {
                if (repo.hasSession() && platform.currentGoogleEmail() != null) {
                    afterSignIn()
                } else {
                    screen = Screen.Connect
                }
            } catch (e: Throwable) {
                log("Restore failed: ${e.message}")
                screen = Screen.Connect
            }
        }
    }

    fun connectDrive() = run("Connecting Google Drive") {
        val r = repo!!
        val user = platform.signInWithGoogle()
        remoteLog?.email = user.email
        log("Google sign-in: ${user.email}")
        r.signInWithGoogle(user.idToken)
        log("Signed in to Aangan")
        platform.driveAccessToken() // asks for Drive permission now, not at the first upload
        log("Google Drive connected")
        me = r.ensureProfile(user.name, user.email)
        afterSignIn()
    }

    private suspend fun afterSignIn() {
        val r = repo!!
        if (me == null) {
            val email = platform.currentGoogleEmail() ?: error("No Google account")
            me = r.myProfile() ?: r.ensureProfile(email.substringBefore('@'), email)
        }
        remoteLog?.email = me?.googleEmail
        families = r.myFamilies()
        invitesForMe = r.myInvites()
        log("Families: ${families.size}, invites for me: ${invitesForMe.size}")
        if (families.isEmpty()) {
            screen = Screen.Start
        } else {
            family = family?.let { f -> families.firstOrNull { it.id == f.id } } ?: families.first()
            screen = Screen.Main
            loadFamily()
        }
    }

    fun refresh() = run("Refreshing") {
        invitesForMe = repo!!.myInvites()
        families = repo.myFamilies()
        loadFamily()
    }

    private suspend fun loadFamily() {
        val r = repo!!
        val f = family ?: return
        members = r.members(f.id)
        pendingInvites = r.pendingInvites(f.id)
        videos = r.videos(f.id)
        log("${f.name}: ${members.size} members, ${pendingInvites.size} pending, ${videos.size} videos")
        shareMyVideosByLink()
    }

    /**
     * drive.file can't let other members' apps open our files (tested 2026-11-02: HTTP 404), so each
     * video is shared as "anyone with the link" and played with the app's API key. Drive only lets the
     * owner change sharing, so each phone makes sure its own uploads are shared. This also fixes videos
     * uploaded before this change. The set avoids repeats in one session.
     */
    private suspend fun shareMyVideosByLink() {
        val myId = me?.id ?: return
        for (v in videos.filter { it.uploaderId == myId && it.driveFileId !in shared }) {
            try {
                platform.storage.shareByLink(v.driveFileId)
                shared += v.driveFileId
            } catch (e: Throwable) {
                log("Could not share \"${v.title}\" by link: ${e.message}")
            }
        }
    }

    fun createFamily(name: String) = run("Creating the family") {
        val id = repo!!.createFamily(name.trim().ifBlank { "Family" })
        log("Created family $id")
        families = repo.myFamilies()
        family = families.first { it.id == id }
        screen = Screen.Main
        tab = Tab.Family
        loadFamily()
    }

    fun acceptInvite(invite: InvitePreview) = run("Joining the family") {
        val id = repo!!.acceptInvite(invite.id)
        log("Joined ${invite.familyName}")
        invitesForMe = repo.myInvites()
        families = repo.myFamilies()
        family = families.first { it.id == id }
        screen = Screen.Main
        tab = Tab.Home
        message = "Joined ${invite.familyName}"
        loadFamily()
    }

    fun switchFamily(f: Family) = run("Opening ${f.name}") {
        family = f
        filter = null
        tab = Tab.Home
        loadFamily()
    }

    fun showStart() { screen = Screen.Start }
    fun backToMain() { if (family != null) screen = Screen.Main }

    fun invite(email: String) = run("Sending the invite") {
        val mail = email.trim().lowercase()
        require(mail.contains('@') && mail.contains('.')) { "Enter a full email address." }
        repo!!.createInvite(family!!.id, mail)
        log("Invited $mail")
        message = "Invite sent to $mail"
        loadFamily()
    }

    fun pickFromGallery() {
        showAddSheet = false
        scope.launch { platform.pickVideo()?.let { videoToName = it } }
    }

    fun recordVideo() {
        showAddSheet = false
        scope.launch { platform.recordVideo()?.let { videoToName = it } }
    }

    fun startUpload(title: String) {
        val video = videoToName ?: return
        videoToName = null
        val item = UploadItem(Random.nextLong().toString(), title.trim().ifBlank { defaultTitle() }, video, UploadState.Queued)
        uploads.add(0, item)
        upload(item.localId)
    }

    fun retry(localId: String) = upload(localId)

    private fun update(localId: String, change: (UploadItem) -> UploadItem) {
        val i = uploads.indexOfFirst { it.localId == localId }
        if (i >= 0) uploads[i] = change(uploads[i])
    }

    private fun upload(localId: String) {
        val item = uploads.firstOrNull { it.localId == localId } ?: return
        val f = family ?: return
        scope.launch {
            update(localId) { it.copy(state = UploadState.Uploading, progress = 0f, error = null) }
            try {
                log("Uploading \"${item.title}\" (${item.video.sizeBytes / 1_000_000} MB)")
                val fileId = platform.storage.upload(item.video, item.title) { p ->
                    update(localId) { it.copy(progress = p) }
                }
                log("Saved to Drive: $fileId")
                platform.storage.shareByLink(fileId)
                shared += fileId
                // The thumbnail is optional: a failure here must not lose the video.
                val thumb = item.video.thumbnailPath?.let { path ->
                    try {
                        repo!!.uploadThumbnail(f.id, fileId, platform.readFile(path))
                    } catch (e: Throwable) {
                        log("Thumbnail upload failed: ${e.message}", "warn"); null
                    }
                }
                repo!!.addVideo(f.id, fileId, item.title, item.video.durationS, item.video.sizeBytes, thumb)
                update(localId) { it.copy(state = UploadState.Saved, progress = 1f) }
                loadFamily()
            } catch (e: Throwable) {
                log("Upload failed: ${e.message}", "error")
                update(localId) { it.copy(state = UploadState.Failed, error = e.message) }
            }
        }
    }

    fun signOut() = run("Signing out") {
        closePlayer()
        log("Signed out")
        repo?.signOut()
        remoteLog?.email = null
        platform.signOutOfGoogle()
        me = null; family = null; families = emptyList(); videos = emptyList(); members = emptyList()
        pendingInvites = emptyList(); invitesForMe = emptyList(); uploads.clear(); shared.clear()
        tab = Tab.Home
        screen = Screen.Connect
    }
}

private val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

fun defaultTitle(): String {
    val d = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    return "Video from ${d.dayOfMonth} ${months[d.monthNumber - 1]}"
}
