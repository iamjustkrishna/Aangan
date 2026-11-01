package app.familyvault

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.familyvault.data.GoogleUser
import app.familyvault.data.MediaPlayer
import app.familyvault.data.PickedVideo
import app.familyvault.data.PlatformServices
import app.familyvault.data.VideoStorage
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.resume

const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"

class AndroidPlatform(private val activity: MainActivity) : PlatformServices {

    private val signInOptions: GoogleSignInOptions by lazy {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .requestScopes(Scope(DRIVE_FILE_SCOPE))
            .requestIdToken(BuildConfig.WEB_CLIENT_ID)
            .build()
    }

    override suspend fun signInWithGoogle(): GoogleUser {
        val client = GoogleSignIn.getClient(activity, signInOptions)
        val result = activity.launchForResult(client.signInIntent)
        val account = try {
            GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(ApiException::class.java)
        } catch (e: ApiException) {
            // 10 = DEVELOPER_ERROR: package name / SHA-1 / client ID mismatch in Google Cloud.
            // 12501 = the person closed the sign-in screen.
            error("Google sign-in stopped (code ${e.statusCode})")
        }
        val idToken = account.idToken ?: error("Google gave no ID token. Check google.web.client.id in local.properties.")
        return GoogleUser(account.email ?: error("No email on this Google account"), account.displayName ?: account.email!!, idToken)
    }

    override fun currentGoogleEmail(): String? = GoogleSignIn.getLastSignedInAccount(activity)?.email

    override suspend fun signOutOfGoogle() {
        GoogleSignIn.getClient(activity, signInOptions).signOut().await()
    }

    override suspend fun driveAccessToken(): String = withContext(Dispatchers.IO) {
        val account = GoogleSignIn.getLastSignedInAccount(activity)?.account ?: error("Not signed in to Google")
        try {
            GoogleAuthUtil.getToken(activity, account, "oauth2:$DRIVE_FILE_SCOPE")
        } catch (e: UserRecoverableAuthException) {
            // Google wants the person to approve Drive access first.
            val consent = withContext(Dispatchers.Main) { activity.launchForResult(e.intent!!) }
            if (consent.resultCode != Activity.RESULT_OK) error("Google Drive access was not allowed")
            GoogleAuthUtil.getToken(activity, account, "oauth2:$DRIVE_FILE_SCOPE")
        }
    }

    override suspend fun pickVideo(): PickedVideo? {
        val uri = activity.pickVideoUri() ?: return null
        return withContext(Dispatchers.IO) {
            val file = File(videoDir(), "picked_${System.currentTimeMillis()}.mp4")
            activity.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
            describe(file)
        }
    }

    override suspend fun recordVideo(): PickedVideo? {
        val file = File(videoDir(), "recorded_${System.currentTimeMillis()}.mp4")
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.files", file)
        val ok = activity.captureVideo(uri)
        return if (ok && file.length() > 0) withContext(Dispatchers.IO) { describe(file) } else null
    }

    override fun readFile(path: String): ByteArray = File(path).readBytes()

    private fun videoDir() = File(activity.cacheDir, "videos").apply { mkdirs() }

    /** Duration and a 640px-wide JPEG frame from about 1 s in (the thumbnail). */
    private fun describe(file: File): PickedVideo {
        val r = MediaMetadataRetriever()
        var seconds: Int? = null
        var thumbPath: String? = null
        try {
            r.setDataSource(file.absolutePath)
            seconds = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong()?.div(1000)?.toInt()
            val at = if ((seconds ?: 0) >= 2) 1_000_000L else 0L
            r.getFrameAtTime(at, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { frame ->
                val w = 640
                val h = (frame.height * w / frame.width.coerceAtLeast(1)).coerceAtLeast(1)
                val small = Bitmap.createScaledBitmap(frame, w, h, true)
                val out = File(file.parentFile, file.nameWithoutExtension + ".jpg")
                out.outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 80, it) }
                thumbPath = out.absolutePath
            }
        } catch (e: Exception) {
            // No duration or thumbnail is fine; the video still uploads.
        } finally {
            r.release()
        }
        return PickedVideo(file.absolutePath, file.length(), seconds, thumbPath)
    }

    override val storage: VideoStorage = DriveStorage { driveAccessToken() }

    override val player: MediaPlayer by lazy { AndroidMediaPlayer(activity.applicationContext, signingCertSha1(activity)) }

    override fun setFullscreen(on: Boolean) {
        val window = activity.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (on) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun setBackHandler(handler: () -> Boolean) {
        activity.backHandler = handler
    }

    override val deviceName: String = "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}"
    override val appVersion: String = BuildConfig.VERSION_NAME
}

/** SHA-1 of the app's signing certificate, uppercase hex without colons (what Google API keys check). */
@Suppress("DEPRECATION")
fun signingCertSha1(context: Context): String = runCatching {
    val pm = context.packageManager
    val sig = if (Build.VERSION.SDK_INT >= 28) {
        pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo!!.apkContentsSigners[0]
    } else {
        pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures!![0]
    }
    MessageDigest.getInstance("SHA-1").digest(sig.toByteArray()).joinToString("") { "%02X".format(it) }
}.getOrDefault("")

private suspend fun <T> Task<T>.await(): T? = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { cont.resume(if (it.isSuccessful) it.result else null) }
}
