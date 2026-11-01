package app.familyvault.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    val id: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("google_email") val googleEmail: String,
    @SerialName("avatar_tone") val avatarTone: Int = 1,
)

@Serializable
data class Family(
    val id: String,
    val name: String,
    @SerialName("created_by") val createdBy: String,
)

@Serializable
data class MemberRow(
    @SerialName("family_id") val familyId: String,
    @SerialName("user_id") val userId: String,
    val role: String,
    val relation: String? = null,
)

/** A member of the active family, with their profile. */
data class Person(val profile: Profile, val role: String, val relation: String?) {
    val id get() = profile.id
    val name get() = profile.displayName
    val firstName get() = profile.displayName.trim().substringBefore(' ').ifBlank { profile.displayName }
}

@Serializable
data class Invite(
    val id: String,
    @SerialName("family_id") val familyId: String,
    val email: String? = null,
    @SerialName("invited_by") val invitedBy: String,
    val status: String,
    @SerialName("created_at") val createdAt: String,
)

/** An invite addressed to the signed-in person, from the my_invites() function. */
@Serializable
data class InvitePreview(
    val id: String,
    @SerialName("family_id") val familyId: String,
    @SerialName("family_name") val familyName: String,
    @SerialName("invited_by_name") val invitedByName: String,
    @SerialName("member_count") val memberCount: Long,
    @SerialName("video_count") val videoCount: Long,
)

@Serializable
data class Video(
    val id: String,
    @SerialName("family_id") val familyId: String,
    @SerialName("uploader_id") val uploaderId: String,
    @SerialName("drive_file_id") val driveFileId: String,
    val title: String,
    @SerialName("duration_s") val durationS: Int? = null,
    @SerialName("size_bytes") val sizeBytes: Long? = null,
    @SerialName("thumbnail_path") val thumbnailPath: String? = null,
    @SerialName("created_at") val createdAt: String,
)

/** Same state names as the UploadStatus component in the design system. */
enum class UploadState { Queued, Uploading, Waiting, Saved, Failed }

data class UploadItem(
    val localId: String,
    val title: String,
    val video: PickedVideo,
    val state: UploadState,
    val progress: Float = 0f,
    val error: String? = null,
)
