package app.kinkeep.diagnostics

import java.io.File
import java.io.IOException
import java.time.Instant

/**
 * A small on-device crash and error record. Entries hold only the error type, the call stack
 * and the app and phone version: never messages (which can contain names or numbers), contacts or notes.
 * Nothing is sent anywhere; the person decides whether to share it.
 */
class ErrorLog(private val file: File, private val maxChars: Int = 64 * 1024) {

    @Synchronized
    fun record(tag: String, error: Throwable, appVersion: String = "", now: Long = System.currentTimeMillis()) {
        append(format(now, tag, error, appVersion))
    }

    @Synchronized
    fun append(entry: String) {
        try {
            val existing = if (file.exists()) file.readText() else ""
            val combined = existing + entry + "\n"
            file.writeText(if (combined.length > maxChars) combined.takeLast(maxChars) else combined)
        } catch (_: IOException) {
            // Logging must never crash the app.
        }
    }

    @Synchronized
    fun read(): String = try { if (file.exists()) file.readText() else "" } catch (_: IOException) { "" }

    @Synchronized
    fun clear() { file.delete() }

    companion object {
        fun format(now: Long, tag: String, error: Throwable, appVersion: String, maxFrames: Int = 20): String = buildString {
            append(Instant.ofEpochMilli(now)).append(" [").append(tag).append("] ").append(appVersion).append('\n')
            append(error.javaClass.name).append('\n')
            error.stackTrace.take(maxFrames).forEach { f ->
                append("  at ").append(f.className).append('.').append(f.methodName)
                append('(').append(f.fileName ?: "?").append(':').append(f.lineNumber).append(")\n")
            }
            var cause = error.cause
            var depth = 0
            while (cause != null && depth < 3) {
                append("Caused by: ").append(cause.javaClass.name).append('\n')
                cause = cause.cause
                depth++
            }
        }
    }
}
