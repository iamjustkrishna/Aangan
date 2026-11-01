package app.familyvault

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import app.familyvault.data.RemoteLog
import app.familyvault.data.Repository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** Set by the shared app; returns true when it handled the back press. */
    var backHandler: (() -> Boolean)? = null

    private var resultWaiter: CompletableDeferred<ActivityResult>? = null
    private val resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        resultWaiter?.complete(it); resultWaiter = null
    }

    private var pickWaiter: CompletableDeferred<Uri?>? = null
    private val pickLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) {
        pickWaiter?.complete(it); pickWaiter = null
    }

    private var captureWaiter: CompletableDeferred<Boolean>? = null
    private val captureLauncher = registerForActivityResult(ActivityResultContracts.CaptureVideo()) {
        captureWaiter?.complete(it); captureWaiter = null
    }

    suspend fun launchForResult(intent: Intent): ActivityResult {
        val waiter = CompletableDeferred<ActivityResult>().also { resultWaiter = it }
        resultLauncher.launch(intent)
        return waiter.await()
    }

    suspend fun pickVideoUri(): Uri? {
        val waiter = CompletableDeferred<Uri?>().also { pickWaiter = it }
        pickLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
        return waiter.await()
    }

    suspend fun captureVideo(output: Uri): Boolean {
        val waiter = CompletableDeferred<Boolean>().also { captureWaiter = it }
        captureLauncher.launch(output)
        return waiter.await()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val platform = AndroidPlatform(this)
        val log = AppServices.remoteLog(this, platform)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (backHandler?.invoke() == true) return
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        })

        setContent { App(platform, AppServices.repository(), log) }
    }

    override fun onStop() {
        super.onStop()
        // Going to the background is a good moment to send what's waiting.
        AppServices.flushLogs()
    }
}

/** Process-wide services: one Supabase client and one background log. */
object AppServices {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var repo: Repository? = null
    private var log: RemoteLog? = null

    fun repository(): Repository? {
        if (BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_ANON_KEY.isBlank()) return null
        return repo ?: Repository(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY).also { repo = it }
    }

    fun remoteLog(context: Context, platform: AndroidPlatform): RemoteLog? {
        log?.let { return it }
        val r = repository() ?: return null
        val created = RemoteLog(FileLogStore(context.filesDir), { rows -> r.insertLogs(rows) }, platform.deviceName, platform.appVersion)
        log = created
        created.start(scope)

        // Send waiting lines as soon as the phone is back online.
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= 24) {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = created.flushSoon()
            })
        }

        // A crash is written to the file before the app dies and sent at the next launch.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching { created.log("Crash on ${thread.name}: ${e.stackTraceToString().take(3500)}", "crash") }
            previous?.uncaughtException(thread, e)
        }
        return created
    }

    fun flushLogs() {
        val l = log ?: return
        scope.launch { l.flush() }
    }
}
