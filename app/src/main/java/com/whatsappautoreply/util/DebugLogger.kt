package com.whatsappautoreply.util

import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Extensive debug logger that writes structured, timestamped entries
 * to both Logcat and a file on-device for post-mortem inspection.
 *
 * Log file: /sdcard/Documents/whatsapp_autoreply_debug.log
 */
object DebugLogger {

    private const val TAG = "DebugLogger"
    private const val MAX_FILE_SIZE_BYTES = 2 * 1024 * 1024 // 2 MB cap
    private const val LOG_FILE_NAME = "whatsapp_autoreply_debug.log"

    @Volatile
    var isDebugEnabled: Boolean = true

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    // In-memory ring buffer of the last 500 entries for quick UI access
    private val recentLogs = ConcurrentLinkedQueue<String>()
    private const val MAX_RECENT_LOGS = 500

    private val logFile: File by lazy {
        val docs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        docs.mkdirs()
        File(docs, LOG_FILE_NAME)
    }

    /**
     * Log a debug event with structured key-value data.
     */
    fun logEvent(tag: String, event: String, data: Map<String, Any?> = emptyMap()) {
        if (!isDebugEnabled) return
        val entry = formatEntry("EVENT", tag, event, data)
        writeEntry(entry)
        try { Log.d(tag, "[$event] ${formatData(data)}") } catch (_: Throwable) {}
    }

    /**
     * Log a warning event.
     */
    fun logWarning(tag: String, event: String, data: Map<String, Any?> = emptyMap()) {
        if (!isDebugEnabled) return
        val entry = formatEntry("WARN", tag, event, data)
        writeEntry(entry)
        try { Log.w(tag, "[$event] ${formatData(data)}") } catch (_: Throwable) {}
    }

    /**
     * Log an error event with optional exception.
     */
    fun logError(tag: String, event: String, error: Throwable? = null, data: Map<String, Any?> = emptyMap()) {
        if (!isDebugEnabled) return
        val errorData = data.toMutableMap()
        if (error != null) {
            errorData["error_class"] = error.javaClass.simpleName
            errorData["error_message"] = error.message
            errorData["error_stacktrace"] = error.stackTrace.take(5).joinToString(" <- ") {
                "${it.className}.${it.methodName}:${it.lineNumber}"
            }
        }
        val entry = formatEntry("ERROR", tag, event, errorData)
        writeEntry(entry)
        try { Log.e(tag, "[$event] ${formatData(errorData)}", error) } catch (_: Throwable) {}
    }

    /**
     * Get the last N log entries from the in-memory buffer.
     */
    fun getRecentLogs(count: Int = 100): List<String> {
        return recentLogs.toList().takeLast(count)
    }

    /**
     * Get the log file path for sharing or viewing.
     */
    fun getLogFilePath(): String = try { logFile.absolutePath } catch (_: Throwable) { "" }

    /**
     * Clear all logs (file + memory).
     */
    fun clearLogs() {
        recentLogs.clear()
        try {
            if (logFile.exists()) {
                logFile.writeText("")
            }
        } catch (e: Throwable) {
            try { Log.e(TAG, "Failed to clear log file", e) } catch (_: Throwable) {}
        }
    }

    // -- Internal --

    private fun formatEntry(level: String, tag: String, event: String, data: Map<String, Any?>): String {
        val timestamp = dateFormat.format(Date())
        val dataStr = if (data.isNotEmpty()) {
            data.entries.joinToString(", ") { "${it.key}=${it.value}" }
        } else ""
        return "[$timestamp] $level/$tag [$event] $dataStr"
    }

    private fun formatData(data: Map<String, Any?>): String {
        if (data.isEmpty()) return ""
        return data.entries.joinToString(", ") { "${it.key}=${it.value}" }
    }

    private fun writeEntry(entry: String) {
        // Add to in-memory buffer
        recentLogs.add(entry)
        while (recentLogs.size > MAX_RECENT_LOGS) {
            recentLogs.poll()
        }

        // Write to file (best effort, don't crash the app)
        try {
            // Rotate if file is too large
            if (logFile.exists() && logFile.length() > MAX_FILE_SIZE_BYTES) {
                val backupFile = File(logFile.parent, "${LOG_FILE_NAME}.bak")
                if (backupFile.exists()) backupFile.delete()
                logFile.renameTo(backupFile)
            }
            FileWriter(logFile, true).use { writer ->
                writer.appendLine(entry)
            }
        } catch (e: Throwable) {
            try { Log.e(TAG, "Failed to write to log file: ${e.message}") } catch (_: Throwable) {}
        }
    }
}
