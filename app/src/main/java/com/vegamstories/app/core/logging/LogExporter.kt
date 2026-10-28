package com.vegamstories.app.core.logging

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.vegamstories.app.BuildConfig
import com.vegamstories.app.data.settings.ContentSyncStore
import com.vegamstories.app.di.IoDispatcher
import com.vegamstories.app.domain.repository.ContentLanguageRepository
import com.vegamstories.app.domain.repository.LanguageRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "LogExporter"

/** The logs packed for sharing: the zip, and a short text to go with it in the message. */
data class SharedLogs(val uri: Uri, val text: String)

/**
 * Packs the log files (see [LogFiles]) for Settings → Support → Share logs: one zip with
 *
 *     report.txt              the user's note, the app and the phone, the story language,
 *                             when stories were last downloaded
 *     logs/2026-10-26/09.log  every log file of the last days
 *
 * in the app's cache (cache/shared/, which the FileProvider shares: res/xml/share_paths.xml).
 * Only the latest zip is kept.
 */
@Singleton
class LogExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contentLanguages: ContentLanguageRepository,
    private val appLanguage: LanguageRepository,
    private val syncStore: ContentSyncStore,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val log = AppLogger(TAG)

    suspend fun export(note: String): SharedLogs {
        // Read here, not on the IO thread: AppCompat's language is meant to be read from the app's side.
        val appLanguageName = appLanguage.getLanguage().name
        return withContext(io) {
            log.timed("$TAG: Pack logs to share", summary = { it.uri }) {
                AppLog.flush()
                val files = AppLog.logFiles()
                val logDir = AppLog.logDir
                val dir = File(context.cacheDir, SHARED_DIR).apply { mkdirs() }
                dir.listFiles { file -> file.name.startsWith(ZIP_PREFIX) }.orEmpty().forEach { it.delete() }
                val zip = File(dir, ZIP_PREFIX + SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date()) + ".zip")
                val report = report(note, files, appLanguageName)
                ZipOutputStream(BufferedOutputStream(FileOutputStream(zip))).use { out ->
                    out.putNextEntry(ZipEntry("report.txt"))
                    out.write(report.toByteArray(Charsets.UTF_8))
                    out.closeEntry()
                    files.forEach { file ->
                        val name = logDir?.let { file.relativeTo(it).path } ?: file.name
                        // A file deleted meanwhile (the oldest, as the logs roll over) is simply left out.
                        runCatching {
                            out.putNextEntry(ZipEntry("logs/$name"))
                            file.inputStream().use { it.copyTo(out) }
                            out.closeEntry()
                        }
                    }
                }
                log.d("$TAG: Logs packed", data = mapOf("files" to files.size, "zipBytes" to zip.length(), "zip" to zip.name))
                SharedLogs(
                    uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zip),
                    text = shareText(note),
                )
            }
        }
    }

    private suspend fun report(note: String, files: List<File>, appLanguageName: String): String {
        val languages = withTimeoutOrNull(2_000) { contentLanguages.contentLanguages.first() }
        val lastDownload = syncStore.lastRefreshAt()
        val zone = TimeZone.getDefault()
        return buildString {
            appendLine("Vegam Stories – support report")
            appendLine("Created: ${logTime(System.currentTimeMillis())} (${zone.id})")
            appendLine()
            appendLine("Note from the user:")
            appendLine(note.trim().ifEmpty { "(no note)" })
            appendLine()
            appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}), ${BuildConfig.BUILD_TYPE} build")
            appendLine("Stories from: ${if (BuildConfig.USE_FAKE_BACKEND) "sample data (no server set up)" else "the server"}")
            appendLine("Phone: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("App language: $appLanguageName (phone: ${Locale.getDefault().toLanguageTag()})")
            appendLine("Story language: ${languages?.selected ?: "unknown"} (in the app: ${languages?.all?.joinToString { it.code } ?: "unknown"})")
            appendLine("Stories last downloaded: ${logTime(lastDownload)}")
            appendLine("Log files: ${files.size}, ${files.sumOf { it.length() } / 1024} KB")
            files.firstOrNull()?.let { appendLine("Logs from: ${it.parentFile?.name} to ${files.last().parentFile?.name}") }
        }
    }

    /** Goes in the message itself (Gmail's body, WhatsApp's caption): the note and the essentials. */
    private fun shareText(note: String): String = buildString {
        note.trim().takeIf { it.isNotEmpty() }?.let { append(it).append("\n\n") }
        append("Vegam Stories ${BuildConfig.VERSION_NAME} · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}")
    }

    private companion object {
        const val SHARED_DIR = "shared"
        const val ZIP_PREFIX = "vegam-stories-logs-"
    }
}
