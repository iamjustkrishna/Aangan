package app.familyvault.spike

import app.familyvault.BuildConfig
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.google.android.gms.auth.GoogleAuthException
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private val logEntries = mutableStateListOf<String>()
    private var googleSignInClient: GoogleSignInClient? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF1976D2),
                    surface = Color(0xFFFFFFFF),
                    background = Color(0xFFF8F9FA)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SpikeScreen(
                        logs = logEntries,
                        onLog = { addLog(it) }
                    )
                }
            }
        }
    }

    private fun addLog(message: String) {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        val formatted = "[$time] $message"
        runOnUiThread {
            logEntries.add(formatted)
            Log.d("DriveSpike", formatted)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpikeScreen(
    logs: List<String>,
    onLog: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var signedInAccount by remember { mutableStateOf<GoogleSignInAccount?>(GoogleSignIn.getLastSignedInAccount(context)) }
    var accessToken by remember { mutableStateOf<String?>(null) }
    var userBEmail by remember { mutableStateOf("") }
    var uploadedFileId by remember { mutableStateOf("") }
    var uploadProgress by remember { mutableFloatStateOf(0f) }
    var isBusy by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Ready") }

    // ExoPlayer state
    var isStreamingActive by remember { mutableStateOf(false) }
    var firstFrameLatencyMs by remember { mutableStateOf<Long?>(null) }
    var streamError by remember { mutableStateOf<String?>(null) }

    val driveClient = remember { DriveApiClient(onLog) }

    // Launcher for Recoverable Auth
    val recoverableAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            onLog("User approved recoverable authorization, re-fetching token...")
            signedInAccount?.let { acc ->
                scope.launch {
                    fetchAccessToken(context, acc, onLog) { token ->
                        accessToken = token
                    }
                }
            }
        } else {
            onLog("User cancelled recoverable authorization")
        }
    }

    // Sign-In launcher
    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(Exception::class.java)
            signedInAccount = account
            onLog("Google Sign-In success: ${account.email}")

            scope.launch {
                fetchAccessToken(context, account, onLog, onRecoverable = { intent ->
                    recoverableAuthLauncher.launch(intent)
                }) { token ->
                    accessToken = token
                }
            }
        } catch (e: Exception) {
            onLog("Google Sign-In failed: ${e.message}")
        }
    }

    // Gallery video picker launcher
    val pickVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isBusy = true
                statusText = "Importing selected video..."
                try {
                    val tempFile = File(context.cacheDir, "picked_video_${System.currentTimeMillis()}.mp4")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    onLog("Imported video from gallery: ${tempFile.name} (${tempFile.length()} bytes)")
                    runUploadSequence(
                        videoFile = tempFile,
                        targetEmail = userBEmail,
                        token = accessToken,
                        driveClient = driveClient,
                        onProgress = { sent, total ->
                            uploadProgress = sent.toFloat() / total
                        },
                        onSuccess = { fileId ->
                            uploadedFileId = fileId
                            statusText = "Upload & share completed!"
                        },
                        onError = { err ->
                            statusText = "Error: $err"
                        },
                        onLog = onLog
                    )
                } catch (e: Exception) {
                    onLog("Error reading picked video: ${e.message}")
                } finally {
                    isBusy = false
                }
            }
        }
    }

    fun initiateSignIn() {
        val gsoBuilder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .requestScopes(Scope("https://www.googleapis.com/auth/drive.file"))

        val webClientId = BuildConfig.WEB_CLIENT_ID
        if (webClientId.isNotBlank()) {
            gsoBuilder.requestIdToken(webClientId)
        }

        val client = GoogleSignIn.getClient(context, gsoBuilder.build())
        signInLauncher.launch(client.signInIntent)
    }

    fun signOut() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
        GoogleSignIn.getClient(context, gso).signOut().addOnCompleteListener {
            signedInAccount = null
            accessToken = null
            isStreamingActive = false
            onLog("Signed out of Google account.")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Task 1: Drive Sharing Spike", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            "Scope: https://www.googleapis.com/auth/drive.file",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Spike Logs", logs.joinToString("\n")))
                        Toast.makeText(context, "Logs copied to clipboard", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy logs")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Authentication Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("1. Google Account & Scope", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }

                        if (signedInAccount != null) {
                            Text("Signed in as: ${signedInAccount?.email}", fontWeight = FontWeight.SemiBold)
                            val tokenSnippet = accessToken?.let {
                                if (it.length > 20) it.take(10) + "..." + it.takeLast(10) else it
                            } ?: "Fetching access token..."
                            Text("OAuth Access Token: $tokenSnippet", fontSize = 12.sp, color = Color.Gray)

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { signOut() },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Sign Out / Switch User")
                                }
                                Button(
                                    onClick = {
                                        signedInAccount?.let { acc ->
                                            scope.launch {
                                                fetchAccessToken(context, acc, onLog, onRecoverable = {
                                                    recoverableAuthLauncher.launch(it)
                                                }) { token -> accessToken = token }
                                            }
                                        }
                                    }
                                ) {
                                    Text("Refresh Token")
                                }
                            }
                        } else {
                            Text(
                                "Sign in with Google to grant access to the drive.file scope.",
                                fontSize = 14.sp
                            )
                            Button(
                                onClick = { initiateSignIn() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Sign in with Google")
                            }
                        }
                    }
                }
            }

            // Section 2: User A (Uploader) Flow
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("2. User A (Uploader) Actions", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }

                        Text(
                            "Creates 'FamilyVault' folder, uploads test MP4 via resumable upload, and shares with User B via permissions.create.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = userBEmail,
                            onValueChange = { userBEmail = it },
                            label = { Text("User B Google Email (Invitee)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("relative@gmail.com") }
                        )

                        if (uploadProgress > 0f) {
                            LinearProgressIndicator(
                                progress = { uploadProgress },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text("Upload progress: ${(uploadProgress * 100).toInt()}%", fontSize = 12.sp)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (accessToken.isNullOrBlank()) {
                                        onLog("Error: Sign in first!")
                                        return@Button
                                    }
                                    if (userBEmail.isBlank()) {
                                        onLog("Error: Enter User B email!")
                                        return@Button
                                    }
                                    scope.launch {
                                        isBusy = true
                                        statusText = "Generating synthetic test video..."
                                        try {
                                            val syntheticFile = TestVideoGenerator.createTestVideo(context)
                                            onLog("Created test video: ${syntheticFile.name} (${syntheticFile.length()} bytes)")
                                            runUploadSequence(
                                                videoFile = syntheticFile,
                                                targetEmail = userBEmail,
                                                token = accessToken,
                                                driveClient = driveClient,
                                                onProgress = { sent, total ->
                                                    uploadProgress = sent.toFloat() / total
                                                },
                                                onSuccess = { fileId ->
                                                    uploadedFileId = fileId
                                                    statusText = "Uploaded & Shared!"
                                                },
                                                onError = { err -> statusText = "Error: $err" },
                                                onLog = onLog
                                            )
                                        } catch (e: Exception) {
                                            onLog("Error generating video: ${e.message}")
                                        } finally {
                                            isBusy = false
                                        }
                                    }
                                },
                                enabled = !isBusy && accessToken != null,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Upload Synthetic Video")
                            }

                            OutlinedButton(
                                onClick = {
                                    if (accessToken.isNullOrBlank()) {
                                        onLog("Error: Sign in first!")
                                        return@OutlinedButton
                                    }
                                    pickVideoLauncher.launch("video/*")
                                },
                                enabled = !isBusy && accessToken != null,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Pick Gallery Video")
                            }
                        }

                        if (uploadedFileId.isNotBlank()) {
                            Text(
                                "Resulting fileId: $uploadedFileId",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // Section 3: User B (Viewer) Verification
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("3. User B (Viewer) Verification", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }

                        Text(
                            "Switch to User B account above, then test if User B can read metadata and stream User A's file with drive.file scope.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = uploadedFileId,
                            onValueChange = { uploadedFileId = it },
                            label = { Text("Drive File ID to test") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val token = accessToken
                                    if (token.isNullOrBlank() || uploadedFileId.isBlank()) {
                                        onLog("Error: Must be signed in as User B and provide a fileId!")
                                        return@Button
                                    }
                                    scope.launch {
                                        isBusy = true
                                        onLog("--- Running files.get test ---")
                                        val res = driveClient.getFileMetadata(token, uploadedFileId)
                                        if (res.isSuccess) {
                                            onLog("SUCCESS: files.get succeeded! Body:\n${res.getOrNull()}")
                                        } else {
                                            onLog("FAILURE: files.get failed! Exception:\n${res.exceptionOrNull()?.message}")
                                        }
                                        isBusy = false
                                    }
                                },
                                enabled = !isBusy && accessToken != null && uploadedFileId.isNotBlank(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Test files.get")
                            }

                            Button(
                                onClick = {
                                    val token = accessToken
                                    if (token.isNullOrBlank() || uploadedFileId.isBlank()) {
                                        onLog("Error: Must be signed in as User B and provide a fileId!")
                                        return@Button
                                    }
                                    isStreamingActive = true
                                    firstFrameLatencyMs = null
                                    streamError = null
                                },
                                enabled = !isBusy && accessToken != null && uploadedFileId.isNotBlank(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Stream in ExoPlayer")
                            }
                        }

                        // Fallback sharing options
                        OutlinedButton(
                            onClick = {
                                val token = accessToken
                                if (token.isNullOrBlank() || uploadedFileId.isBlank()) {
                                    onLog("Error: Sign in as User A and provide fileId to set fallback permission!")
                                    return@OutlinedButton
                                }
                                scope.launch {
                                    isBusy = true
                                    onLog("--- Granting 'anyone' reader permission ---")
                                    val res = driveClient.shareFileWithAnyone(token, uploadedFileId)
                                    if (res.isSuccess) {
                                        onLog("SUCCESS: Anyone permission granted! Res: ${res.getOrNull()}")
                                    } else {
                                        onLog("FAILURE: ${res.exceptionOrNull()?.message}")
                                    }
                                    isBusy = false
                                }
                            },
                            enabled = !isBusy && accessToken != null && uploadedFileId.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Test Fallback: Grant 'Anyone with Link' Reader")
                        }

                        // Embedded ExoPlayer
                        if (isStreamingActive && accessToken != null && uploadedFileId.isNotBlank()) {
                            Text("ExoPlayer Stream View:", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                                    .background(Color.Black, RoundedCornerShape(8.dp))
                            ) {
                                ExoPlayerView(
                                    fileId = uploadedFileId,
                                    accessToken = accessToken!!,
                                    onFirstFrame = { latency ->
                                        firstFrameLatencyMs = latency
                                        onLog("ExoPlayer rendered first frame in ${latency}ms!")
                                    },
                                    onError = { err ->
                                        streamError = err
                                        onLog("ExoPlayer error: $err")
                                    }
                                )
                            }

                            if (firstFrameLatencyMs != null) {
                                Text(
                                    "First frame rendered in: ${firstFrameLatencyMs}ms",
                                    color = Color(0xFF2E7D32),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (streamError != null) {
                                Text(
                                    "Streaming Error: $streamError",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            // Section 4: Live Event Log
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF212121))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Diagnostics Log", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("${logs.size} entries", color = Color.LightGray, fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .background(Color.Black, RoundedCornerShape(4.dp))
                                .padding(8.dp),
                            reverseLayout = true
                        ) {
                            items(logs.reversed()) { line ->
                                Text(
                                    text = line,
                                    color = when {
                                        line.contains("SUCCESS") || line.contains("first frame") -> Color(0xFF81C784)
                                        line.contains("FAILURE") || line.contains("Error") || line.contains("failed") -> Color(0xFFE57373)
                                        line.contains("HTTP 200") || line.contains("HTTP 201") -> Color(0xFF64B5F6)
                                        else -> Color(0xFFE0E0E0)
                                    },
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExoPlayerView(
    fileId: String,
    accessToken: String,
    onFirstFrame: (Long) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }

    DisposableEffect(fileId, accessToken) {
        val player = ExoPlayer.Builder(context).build()
        exoPlayer = player

        val startTime = System.currentTimeMillis()

        player.addListener(object : Player.Listener {
            override fun onRenderedFirstFrame() {
                val latency = System.currentTimeMillis() - startTime
                onFirstFrame(latency)
            }

            override fun onPlayerError(error: PlaybackException) {
                val cause = error.cause
                val msg = if (cause is HttpDataSource.InvalidResponseCodeException) {
                    "HTTP ${cause.responseCode}: ${cause.message}"
                } else {
                    error.message ?: "Unknown error"
                }
                onError(msg)
            }
        })

        val streamUrl = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(mapOf("Authorization" to "Bearer $accessToken"))
            .setAllowCrossProtocolRedirects(true)

        val mediaSource = ProgressiveMediaSource.Factory(httpDataSourceFactory)
            .createMediaSource(MediaItem.fromUri(streamUrl))

        player.setMediaSource(mediaSource)
        player.prepare()
        player.playWhenReady = true

        onDispose {
            player.release()
            exoPlayer = null
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = exoPlayer
                useController = true
            }
        },
        update = { view ->
            view.player = exoPlayer
        },
        modifier = Modifier.fillMaxSize()
    )
}

suspend fun fetchAccessToken(
    context: Context,
    account: GoogleSignInAccount,
    onLog: (String) -> Unit,
    onRecoverable: (Intent) -> Unit = {},
    onResult: (String) -> Unit
) {
    withContext(Dispatchers.IO) {
        try {
            onLog("Fetching OAuth access token for ${account.email}...")
            val scopeString = "oauth2:https://www.googleapis.com/auth/drive.file"
            val token = GoogleAuthUtil.getToken(context, account.account!!, scopeString)
            onLog("Successfully retrieved OAuth token.")
            withContext(Dispatchers.Main) {
                onResult(token)
            }
        } catch (recoverable: UserRecoverableAuthException) {
            onLog("User consent required for OAuth token: launching consent intent...")
            withContext(Dispatchers.Main) {
                onRecoverable(recoverable.intent ?: Intent())
            }
        } catch (e: GoogleAuthException) {
            onLog("GoogleAuthException: ${e.message}")
        } catch (e: Exception) {
            onLog("Error fetching token: ${e.message}")
        }
    }
}

suspend fun runUploadSequence(
    videoFile: File,
    targetEmail: String,
    token: String?,
    driveClient: DriveApiClient,
    onProgress: (Long, Long) -> Unit,
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit,
    onLog: (String) -> Unit
) {
    if (token.isNullOrBlank()) {
        onError("No access token")
        return
    }

    try {
        onLog("=== Starting User A Upload Sequence ===")
        val folderId = driveClient.findOrCreateFolder(token, "FamilyVault")
        val fileId = driveClient.uploadVideoResumable(
            accessToken = token,
            file = videoFile,
            folderId = folderId,
            onProgress = onProgress
        )

        onLog("File uploaded with fileId: $fileId")
        onLog("Granting read permission to $targetEmail...")
        val shareRes = driveClient.shareFileWithUser(token, fileId, targetEmail)
        if (shareRes.isSuccess) {
            onLog("SUCCESS: Read permission granted to $targetEmail")
            onSuccess(fileId)
        } else {
            val err = shareRes.exceptionOrNull()?.message ?: "Unknown share error"
            onLog("WARNING: Share returned error: $err")
            // Even if share failed, we pass fileId for testing
            onSuccess(fileId)
        }
    } catch (e: Exception) {
        onLog("Sequence failed: ${e.message}")
        onError(e.message ?: "Unknown error")
    }
}
