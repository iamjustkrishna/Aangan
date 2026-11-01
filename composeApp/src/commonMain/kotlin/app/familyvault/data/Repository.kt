package app.familyvault.data

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Supabase (Postgres) holds the facts: families, members, invites and the video index. */
class Repository(url: String, anonKey: String) {
    private val client = createSupabaseClient(url, anonKey) {
        install(Auth)
        install(Postgrest)
        install(Storage)
    }

    val uid: String get() = client.auth.currentUserOrNull()?.id ?: error("Not signed in")

    suspend fun hasSession(): Boolean {
        client.auth.awaitInitialization()
        return client.auth.currentSessionOrNull() != null
    }

    suspend fun signInWithGoogle(idToken: String) {
        client.auth.signInWith(IDToken) {
            this.idToken = idToken
            provider = Google
        }
    }

    suspend fun signOut() = runCatching { client.auth.signOut() }

    suspend fun myProfile(): Profile? =
        client.from("profiles").select { filter { eq("id", uid) } }.decodeSingleOrNull()

    suspend fun ensureProfile(name: String, email: String): Profile {
        val mail = email.lowercase()
        val existing = client.from("profiles").select { filter { eq("id", uid) } }.decodeSingleOrNull<Profile>()
        if (existing == null) {
            val profile = Profile(uid, name, mail, toneFor(mail))
            client.from("profiles").insert(profile)
            return profile
        }
        client.from("profiles").update({
            set("display_name", name)
            set("google_email", mail)
        }) { filter { eq("id", uid) } }
        return existing.copy(displayName = name, googleEmail = mail)
    }

    suspend fun myFamilies(): List<Family> =
        client.from("families").select { order("created_at", Order.ASCENDING) }.decodeList()

    suspend fun createFamily(name: String): String =
        client.postgrest.rpc("create_family", buildJsonObject { put("family_name", name) }).decodeAs<String>()

    suspend fun myInvites(): List<InvitePreview> =
        client.postgrest.rpc("my_invites").decodeList()

    suspend fun acceptInvite(inviteId: String): String =
        client.postgrest.rpc("accept_invite_by_id", buildJsonObject { put("invite_id", inviteId) }).decodeAs<String>()

    suspend fun members(familyId: String): List<Person> {
        val rows = client.from("family_members").select { filter { eq("family_id", familyId) } }.decodeList<MemberRow>()
        if (rows.isEmpty()) return emptyList()
        val profiles = client.from("profiles").select { filter { isIn("id", rows.map { it.userId }) } }
            .decodeList<Profile>().associateBy { it.id }
        return rows.mapNotNull { r -> profiles[r.userId]?.let { Person(it, r.role, r.relation) } }
            .sortedWith(compareBy({ it.role != "admin" }, { it.name }))
    }

    suspend fun pendingInvites(familyId: String): List<Invite> =
        client.from("invites").select {
            filter { eq("family_id", familyId); eq("status", "pending") }
            order("created_at", Order.DESCENDING)
        }.decodeList()

    suspend fun createInvite(familyId: String, email: String) {
        client.from("invites").insert(buildJsonObject {
            put("family_id", familyId)
            put("email", email.trim().lowercase())
            put("invited_by", uid)
        })
    }

    suspend fun videos(familyId: String): List<Video> =
        client.from("videos").select {
            filter { eq("family_id", familyId); eq("status", "ready") }
            order("created_at", Order.DESCENDING)
        }.decodeList()

    /** Uploads a JPEG frame to the public `thumbnails` bucket. Returns its path inside the bucket. */
    suspend fun uploadThumbnail(familyId: String, name: String, jpeg: ByteArray): String {
        val path = "$familyId/$name.jpg"
        client.storage.from("thumbnails").upload(path, jpeg) { upsert = false }
        return path
    }

    fun thumbnailUrl(path: String): String = client.storage.from("thumbnails").publicUrl(path)

    /** Appends rows to `app_logs`. Insert-only: the app can never read logs back. */
    suspend fun insertLogs(rows: List<JsonObject>) {
        client.from("app_logs").insert(rows)
    }

    suspend fun addVideo(familyId: String, driveFileId: String, title: String, durationS: Int?, sizeBytes: Long, thumbnailPath: String?) {
        client.from("videos").insert(buildJsonObject {
            put("family_id", familyId)
            put("uploader_id", uid)
            put("drive_file_id", driveFileId)
            put("title", title)
            durationS?.let { put("duration_s", it) }
            put("size_bytes", sizeBytes)
            thumbnailPath?.let { put("thumbnail_path", it) }
        })
    }
}

fun toneFor(key: String): Int = (key.sumOf { it.code } % 6) + 1
