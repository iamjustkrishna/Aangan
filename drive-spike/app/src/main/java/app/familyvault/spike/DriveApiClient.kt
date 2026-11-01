package app.familyvault.spike

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.buffer
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class DriveApiClient(
    private val logCallback: (String) -> Unit
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun log(message: String) {
        logCallback(message)
    }

    /**
     * Finds or creates a folder in Google Drive.
     */
    suspend fun findOrCreateFolder(accessToken: String, folderName: String = "FamilyVault"): String =
        withContext(Dispatchers.IO) {
            log("[Drive] Searching for existing folder '$folderName'...")
            val query = "name = '$folderName' and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
            val searchUrl = "https://www.googleapis.com/drive/v3/files?q=${java.net.URLEncoder.encode(query, "UTF-8")}&fields=files(id,name)"

            val searchReq = Request.Builder()
                .url(searchUrl)
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            client.newCall(searchReq).execute().use { response ->
                val body = response.body?.string() ?: ""
                log("[Drive] Folder search status: ${response.code}")
                if (response.isSuccessful) {
                    val json = JSONObject(body)
                    val files = json.optJSONArray("files") ?: JSONArray()
                    if (files.length() > 0) {
                        val folderId = files.getJSONObject(0).getString("id")
                        log("[Drive] Found existing '$folderName' folder: $folderId")
                        return@withContext folderId
                    }
                } else {
                    log("[Drive] Search failed: $body")
                }
            }

            // Create new folder
            log("[Drive] Creating new folder '$folderName'...")
            val createJson = JSONObject().apply {
                put("name", folderName)
                put("mimeType", "application/vnd.google-apps.folder")
            }

            val createReq = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files")
                .addHeader("Authorization", "Bearer $accessToken")
                .post(createJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(createReq).execute().use { response ->
                val body = response.body?.string() ?: ""
                log("[Drive] Create folder status: ${response.code}")
                if (!response.isSuccessful) {
                    throw IOException("Failed to create folder '$folderName': ${response.code} $body")
                }
                val json = JSONObject(body)
                val folderId = json.getString("id")
                log("[Drive] Created folder successfully. ID: $folderId")
                folderId
            }
        }

    /**
     * Resumable upload of a video file to Google Drive.
     * Returns the Drive fileId.
     */
    suspend fun uploadVideoResumable(
        accessToken: String,
        file: File,
        folderId: String?,
        onProgress: (bytesSent: Long, totalBytes: Long) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val totalBytes = file.length()
        log("[Drive] Initiating resumable upload for '${file.name}' ($totalBytes bytes)...")

        val metadataJson = JSONObject().apply {
            put("name", file.name)
            put("mimeType", "video/mp4")
            if (folderId != null) {
                put("parents", JSONArray().put(folderId))
            }
        }

        // Step 1: Initiate Resumable Session
        val initReq = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable")
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("X-Upload-Content-Type", "video/mp4")
            .addHeader("X-Upload-Content-Length", totalBytes.toString())
            .post(metadataJson.toString().toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .build()

        val sessionLocation: String
        client.newCall(initReq).execute().use { response ->
            val body = response.body?.string() ?: ""
            log("[Drive] Init session HTTP ${response.code}")
            if (!response.isSuccessful) {
                throw IOException("Failed to initiate resumable upload: HTTP ${response.code} $body")
            }
            sessionLocation = response.header("Location")
                ?: throw IOException("No Location header returned from resumable upload initiation")
            log("[Drive] Resumable session created.")
        }

        // Step 2: Upload file bytes to sessionLocation
        log("[Drive] Uploading ${totalBytes} bytes to resumable session...")
        val startTime = System.currentTimeMillis()

        val countingBody = ProgressRequestBody(
            delegate = file.asRequestBody("video/mp4".toMediaType()),
            totalBytes = totalBytes,
            onProgress = onProgress
        )

        val uploadReq = Request.Builder()
            .url(sessionLocation)
            .addHeader("Content-Range", "bytes 0-${totalBytes - 1}/$totalBytes")
            .put(countingBody)
            .build()

        client.newCall(uploadReq).execute().use { response ->
            val elapsed = System.currentTimeMillis() - startTime
            val body = response.body?.string() ?: ""
            log("[Drive] Upload finished in ${elapsed}ms. HTTP ${response.code}")
            if (!response.isSuccessful) {
                throw IOException("Upload failed with HTTP ${response.code}: $body")
            }
            val json = JSONObject(body)
            val fileId = json.getString("id")
            log("[Drive] Upload success! fileId = $fileId")
            fileId
        }
    }

    /**
     * Calls permissions.create with {role: "reader", type: "user", emailAddress: targetEmail}
     * and sendNotificationEmail=false.
     */
    suspend fun shareFileWithUser(
        accessToken: String,
        fileId: String,
        targetEmail: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            log("[Drive] Granting reader permission to '$targetEmail' on file '$fileId'...")
            val permJson = JSONObject().apply {
                put("role", "reader")
                put("type", "user")
                put("emailAddress", targetEmail)
            }

            val url = "https://www.googleapis.com/drive/v3/files/$fileId/permissions?sendNotificationEmail=false"
            val req = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .post(permJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(req).execute().use { response ->
                val body = response.body?.string() ?: ""
                log("[Drive] permissions.create HTTP ${response.code}: $body")
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code} error: $body")
                }
                body
            }
        }
    }

    /**
     * Fallback share: anyone with the link can view.
     */
    suspend fun shareFileWithAnyone(
        accessToken: String,
        fileId: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            log("[Drive] Granting 'anyone' reader permission on file '$fileId'...")
            val permJson = JSONObject().apply {
                put("role", "reader")
                put("type", "anyone")
            }

            val url = "https://www.googleapis.com/drive/v3/files/$fileId/permissions"
            val req = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .post(permJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(req).execute().use { response ->
                val body = response.body?.string() ?: ""
                log("[Drive] permissions.create (anyone) HTTP ${response.code}: $body")
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code} error: $body")
                }
                body
            }
        }
    }

    /**
     * Calls files.get with viewer's access token to check if user B can read metadata.
     */
    suspend fun getFileMetadata(
        accessToken: String,
        fileId: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            log("[Drive] Calling files.get for file '$fileId'...")
            val fields = "id,name,size,mimeType,webContentLink,thumbnailLink,permissions"
            val url = "https://www.googleapis.com/drive/v3/files/$fileId?fields=$fields"

            val req = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            client.newCall(req).execute().use { response ->
                val body = response.body?.string() ?: ""
                log("[Drive] files.get HTTP ${response.code}: $body")
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code} error: $body")
                }
                body
            }
        }
    }

    private class ProgressRequestBody(
        private val delegate: RequestBody,
        private val totalBytes: Long,
        private val onProgress: (bytesSent: Long, totalBytes: Long) -> Unit
    ) : RequestBody() {
        override fun contentType() = delegate.contentType()
        override fun contentLength() = totalBytes

        override fun writeTo(sink: BufferedSink) {
            val countingSink = object : ForwardingSink(sink) {
                var bytesWritten = 0L

                override fun write(source: Buffer, byteCount: Long) {
                    super.write(source, byteCount)
                    bytesWritten += byteCount
                    onProgress(bytesWritten, totalBytes)
                }
            }
            val bufferedSink = countingSink.buffer()
            delegate.writeTo(bufferedSink)
            bufferedSink.flush()
        }
    }
}
