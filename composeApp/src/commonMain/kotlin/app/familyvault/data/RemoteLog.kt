package app.familyvault.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.random.Random

/**
 * Background log for the testing phase. Every line is written to a file on the phone first, then
 * sent to the Supabase `app_logs` table in batches:
 * - online: within a few seconds;
 * - offline or Supabase down: lines stay in the file and go out on the next try
 *   (every 30 s, when the network comes back, and when the app goes to the background);
 * - app killed or crashed: the file survives and is sent at the next launch.
 * Nothing is deleted from Supabase. Never log tokens, keys or passwords.
 */
class RemoteLog(
    private val store: LogStore,
    private val send: suspend (List<JsonObject>) -> Unit,
    private val device: String,
    private val appVersion: String,
) {
    /** One id per app launch, to group lines from the same run. */
    val sessionId: String = Random.nextLong().toULong().toString(36)
    var email: String? = null
    private val mutex = Mutex()
    private var scope: CoroutineScope? = null

    fun log(message: String, level: String = "info") {
        val line = buildJsonObject {
            put("logged_at", Clock.System.now().toString())
            put("level", level)
            put("message", message.take(4000))
            put("session_id", sessionId)
            put("device", device)
            put("app_version", appVersion)
            email?.let { put("email", it) }
        }
        store.append(line.toString())
        flushSoon()
    }

    fun start(scope: CoroutineScope) {
        this.scope = scope
        scope.launch {
            while (true) {
                flush()
                delay(30_000)
            }
        }
    }

    fun flushSoon() {
        scope?.launch {
            delay(3_000) // gather a few lines into one request
            flush()
        }
    }

    suspend fun flush() {
        if (mutex.isLocked) return
        mutex.withLock {
            while (true) {
                val lines = store.peek(100)
                if (lines.isEmpty()) return
                val rows = lines.mapNotNull { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
                try {
                    if (rows.isNotEmpty()) send(rows)
                } catch (e: Throwable) {
                    return // offline or server error: keep the lines, try again later
                }
                store.remove(lines.size)
            }
        }
    }
}
