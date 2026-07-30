package com.whatsappautoreply.domain.brain

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages reading, writing, and initializing OWL's brain files.
 *
 * Brain files exist in two locations:
 *   1. assets/brain/         — shipped defaults (read-only)
 *   2. filesDir/brain/       — runtime copies (user-editable)
 *
 * On first launch, defaults are copied to filesDir. All reads prefer filesDir
 * first, falling back to assets if the file is missing in filesDir.
 *
 * Daily memory files (memory/YYYY-MM-DD.md) are created fresh each day
 * and stored only in filesDir — there is no asset default for these.
 */
@Singleton
class BrainRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "BrainRepository"
        private const val BRAIN_ASSET_DIR = "brain"
        private const val BRAIN_DIR = "brain"
        private const val MEMORY_SUBDIR = "memory"
        private const val DATE_FORMAT = "yyyy-MM-dd"
    }

    /** Root directory for all brain files in internal storage */
    private val brainDir: File
        get() = File(context.filesDir, BRAIN_DIR).also { it.mkdirs() }

    // ───────────────────────────────────────────────────────────────────
    // Initialization
    // ───────────────────────────────────────────────────────────────────

    /**
     * Copy all asset brain files to filesDir if they don't already exist.
     * Called once on app startup (idempotent — safe to call multiple times).
     */
    suspend fun initializeIfNeeded() = withContext(Dispatchers.IO) {
        try {
            copyAssetsRecursively(BRAIN_ASSET_DIR, brainDir)
            ensureTodayMemoryFile()
            Log.d(TAG, "Brain initialized at: ${brainDir.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Brain initialization failed", e)
        }
    }

    private fun copyAssetsRecursively(assetPath: String, destDir: File) {
        val assetManager = context.assets
        val assets = assetManager.list(assetPath) ?: return

        if (assets.isEmpty()) {
            // It's a file — copy only if it doesn't exist yet (preserve user edits)
            val destFile = File(destDir, File(assetPath).name)
            if (!destFile.exists()) {
                assetManager.open(assetPath).use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d(TAG, "Copied asset: $assetPath → ${destFile.absolutePath}")
            }
        } else {
            // It's a directory — recurse
            val targetDir = if (assetPath == BRAIN_ASSET_DIR) destDir else File(destDir, File(assetPath).name).also { it.mkdirs() }
            for (asset in assets) {
                copyAssetsRecursively("$assetPath/$asset", targetDir)
            }
        }
    }

    // ───────────────────────────────────────────────────────────────────
    // Read / Write Brain Files
    // ───────────────────────────────────────────────────────────────────

    /**
     * Read a brain file. Prefers filesDir, falls back to assets.
     * Returns empty string if the file doesn't exist in either location.
     */
    suspend fun read(brainFile: BrainFile): String = withContext(Dispatchers.IO) {
        // Try filesDir first
        val file = resolveFile(brainFile)
        if (file.exists()) {
            return@withContext file.readText()
        }

        // Fall back to assets
        try {
            context.assets.open("$BRAIN_ASSET_DIR/${brainFile.relativePath}").use {
                it.bufferedReader().readText()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Brain file not found: ${brainFile.relativePath}")
            ""
        }
    }

    /**
     * Write/update a brain file in filesDir.
     */
    suspend fun write(brainFile: BrainFile, content: String) = withContext(Dispatchers.IO) {
        val file = resolveFile(brainFile)
        file.parentFile?.mkdirs()
        file.writeText(content)
        Log.d(TAG, "Brain file saved: ${brainFile.relativePath}")
    }

    /**
     * Reset a brain file to the shipped asset default.
     */
    suspend fun resetToDefault(brainFile: BrainFile) = withContext(Dispatchers.IO) {
        try {
            val content = context.assets
                .open("$BRAIN_ASSET_DIR/${brainFile.relativePath}")
                .use { it.bufferedReader().readText() }
            write(brainFile, content)
            Log.d(TAG, "Brain file reset to default: ${brainFile.relativePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reset brain file: ${brainFile.relativePath}", e)
        }
    }

    /**
     * Observe a brain file as a Flow (re-emits on manual refresh only).
     * Use in the Brain Editor ViewModel to observe file changes.
     */
    fun observe(brainFile: BrainFile): Flow<String> = flow {
        emit(read(brainFile))
    }.flowOn(Dispatchers.IO)

    // ───────────────────────────────────────────────────────────────────
    // Daily Memory Files  (memory/YYYY-MM-DD.md)
    // ───────────────────────────────────────────────────────────────────

    /** Ensure today's memory file exists. Creates it with a header if missing. */
    suspend fun ensureTodayMemoryFile() = withContext(Dispatchers.IO) {
        val today = todayMemoryFile()
        if (!today.exists()) {
            today.parentFile?.mkdirs()
            val dateStr = SimpleDateFormat(DATE_FORMAT, Locale.getDefault()).format(Date())
            today.writeText("# Daily Memory — $dateStr\n\nNo notes yet for today.\n")
            Log.d(TAG, "Created today's memory file: ${today.name}")
        }
    }

    /** Read today's memory file content. */
    suspend fun readTodayMemory(): String = withContext(Dispatchers.IO) {
        val today = todayMemoryFile()
        if (today.exists()) today.readText() else ""
    }

    /** Append a line to today's memory file. */
    suspend fun appendTodayMemory(note: String) = withContext(Dispatchers.IO) {
        ensureTodayMemoryFile()
        todayMemoryFile().appendText("\n- $note")
    }

    /** Write the full content of today's memory file. */
    suspend fun writeTodayMemory(content: String) = withContext(Dispatchers.IO) {
        todayMemoryFile().parentFile?.mkdirs()
        todayMemoryFile().writeText(content)
    }

    /** Read yesterday's memory file (for continuity context). */
    suspend fun readYesterdayMemory(): String = withContext(Dispatchers.IO) {
        val yesterday = yesterdayMemoryFile()
        if (yesterday.exists()) yesterday.readText() else ""
    }

    /** List all available daily memory files (sorted newest first). */
    suspend fun listMemoryFiles(): List<File> = withContext(Dispatchers.IO) {
        val memDir = File(brainDir, MEMORY_SUBDIR)
        memDir.listFiles()
            ?.filter { it.name.matches(Regex("\\d{4}-\\d{2}-\\d{2}\\.md")) }
            ?.sortedByDescending { it.name }
            ?: emptyList()
    }

    // ───────────────────────────────────────────────────────────────────
    // Helpers
    // ───────────────────────────────────────────────────────────────────

    private fun resolveFile(brainFile: BrainFile): File {
        return if (brainFile.subDir != null) {
            File(brainDir, brainFile.subDir).also { it.mkdirs() }
                .let { File(it, brainFile.fileName) }
        } else {
            File(brainDir, brainFile.fileName)
        }
    }

    private fun todayMemoryFile(): File {
        val dateStr = SimpleDateFormat(DATE_FORMAT, Locale.getDefault()).format(Date())
        return File(brainDir, "$MEMORY_SUBDIR/$dateStr.md")
    }

    private fun yesterdayMemoryFile(): File {
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        val dateStr = SimpleDateFormat(DATE_FORMAT, Locale.getDefault()).format(cal.time)
        return File(brainDir, "$MEMORY_SUBDIR/$dateStr.md")
    }
}
