package app.familyvault

import app.familyvault.data.LogStore
import java.io.File

/**
 * Log lines waiting to reach Supabase, one JSON object per line, in the app's private files.
 * Survives offline periods, app kills and crashes. Capped at 20 MB so a phone that stays offline
 * for weeks can't fill up; past the cap the oldest lines are dropped.
 */
class FileLogStore(dir: File) : LogStore {
    private val file = File(dir, "pending_logs.jsonl")
    private val lock = Any()
    private val maxBytes = 20L * 1024 * 1024

    override fun append(line: String) = synchronized(lock) {
        if (file.length() > maxBytes) {
            val keep = file.readLines().let { it.drop(it.size / 4) }
            file.writeText(keep.joinToString("\n", postfix = "\n"))
        }
        file.appendText(line.replace('\n', ' ') + "\n")
    }

    override fun peek(max: Int): List<String> = synchronized(lock) {
        if (!file.exists()) emptyList()
        else file.bufferedReader().useLines { lines -> lines.filter { it.isNotBlank() }.take(max).toList() }
    }

    override fun remove(count: Int) = synchronized(lock) {
        if (!file.exists()) return@synchronized
        val rest = file.readLines().filter { it.isNotBlank() }.drop(count)
        if (rest.isEmpty()) file.delete() else file.writeText(rest.joinToString("\n", postfix = "\n"))
    }
}
