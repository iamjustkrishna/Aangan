package app.familyvault

import android.os.Handler
import android.os.Looper
import app.familyvault.data.PickedVideo
import app.familyvault.data.VideoStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Google Drive storage using only the drive.file scope (the app sees only files it created). */
class DriveStorage(private val token: suspend () -> String) : VideoStorage {

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()
    private val main = Handler(Looper.getMainLooper())
    private var folderId: String? = null

    private suspend fun folder(auth: String): String = folderId ?: withContext(Dispatchers.IO) {
        val q = "name = 'Aangan' and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
        val search = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files?q=${URLEncoder.encode(q, "UTF-8")}&fields=files(id)")
            .header("Authorization", "Bearer $auth").get().build()
        val found = http.newCall(search).execute().use { r ->
            val body = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw IOException("Drive folder search: HTTP ${r.code} $body")
            JSONObject(body).optJSONArray("files")?.optJSONObject(0)?.getString("id")
        }
        found ?: run {
            val meta = JSONObject().put("name", "Aangan").put("mimeType", "application/vnd.google-apps.folder")
            val create = Request.Builder().url("https://www.googleapis.com/drive/v3/files")
                .header("Authorization", "Bearer $auth")
                .post(meta.toString().toRequestBody("application/json".toMediaType())).build()
            http.newCall(create).execute().use { r ->
                val body = r.body?.string().orEmpty()
                if (!r.isSuccessful) throw IOException("Drive folder create: HTTP ${r.code} $body")
                JSONObject(body).getString("id")
            }
        }
    }.also { folderId = it }

    override suspend fun upload(video: PickedVideo, title: String, onProgress: (Float) -> Unit): String {
        val auth = token()
        val parent = folder(auth)
        return withContext(Dispatchers.IO) {
            val file = File(video.path)
            val total = file.length()
            val meta = JSONObject()
                .put("name", "$title.mp4")
                .put("mimeType", "video/mp4")
                .put("parents", JSONArray().put(parent))

            // 1. Start a resumable upload session.
            val start = Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable")
                .header("Authorization", "Bearer $auth")
                .header("X-Upload-Content-Type", "video/mp4")
                .header("X-Upload-Content-Length", total.toString())
                .post(meta.toString().toRequestBody("application/json; charset=UTF-8".toMediaType())).build()
            val session = http.newCall(start).execute().use { r ->
                if (!r.isSuccessful) throw IOException("Drive upload start: HTTP ${r.code} ${r.body?.string()}")
                r.header("Location") ?: throw IOException("Drive gave no upload session")
            }

            // 2. Send the bytes. TODO(real app): send in chunks and resume after a lost connection.
            var lastSent = -1
            val body = object : RequestBody() {
                override fun contentType() = "video/mp4".toMediaType()
                override fun contentLength() = total
                override fun writeTo(sink: BufferedSink) {
                    file.source().use { src ->
                        var sent = 0L
                        val buffer = okio.Buffer()
                        while (true) {
                            val n = src.read(buffer, 256 * 1024)
                            if (n == -1L) break
                            sink.write(buffer, n)
                            sent += n
                            val pct = (sent * 100 / total).toInt()
                            if (pct != lastSent) { lastSent = pct; main.post { onProgress(sent.toFloat() / total) } }
                        }
                    }
                }
            }
            val put = Request.Builder().url(session).put(body).build()
            http.newCall(put).execute().use { r ->
                val text = r.body?.string().orEmpty()
                if (!r.isSuccessful) throw IOException("Drive upload: HTTP ${r.code} $text")
                JSONObject(text).getString("id")
            }
        }
    }

    override suspend fun shareByLink(fileId: String) {
        val auth = token()
        withContext(Dispatchers.IO) {
            val perm = JSONObject().put("role", "reader").put("type", "anyone")
            val req = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files/$fileId/permissions")
                .header("Authorization", "Bearer $auth")
                .post(perm.toString().toRequestBody("application/json".toMediaType())).build()
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) throw IOException("HTTP ${r.code} ${r.body?.string()}")
            }
        }
    }
}
