package com.gululu.aamediamate.diagnostics

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import com.gululu.aamediamate.MediaBridgeSessionManager
import com.gululu.aamediamate.SettingsManager
import com.gululu.aamediamate.hasNotificationAccess
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.net.URI
import java.time.Instant
import java.time.format.DateTimeFormatter

object DiagnosticLogger {
    private const val LOGCAT_TAG = "AAMediaMateDiag"
    private const val LOG_FILE = "diagnostic_events.jsonl"
    private const val PREFS_NAME = "diagnostic_settings"
    private const val KEY_DEBUG_UNTIL_MS = "debug_until_ms"
    private const val MAX_EVENTS = 500
    private const val RETENTION_MS = 24L * 60L * 60L * 1000L
    private const val MAX_DETAIL_LENGTH = 300
    private val sensitiveKeyRegex = Regex("(?i)(token|api[_-]?key|authorization|password|secret)")
    private val authorizationValueRegex = Regex("(?i)\\b(bearer|basic|token)\\s+[A-Za-z0-9._~+/=-]+")
    private val writer = Executors.newSingleThreadExecutor { task ->
        Thread(task, "diagnostic-writer").apply { isDaemon = true }
    }
    private val pendingWrites = Semaphore(500)
    private var writesSinceCompaction = 0
    private val lock = Any()
    private var initialized = false
    private var previousExceptionHandler: Thread.UncaughtExceptionHandler? = null

    fun initialize(context: Context) {
        val appContext = context.applicationContext ?: context
        synchronized(lock) {
            if (initialized) return
            initialized = true
            previousExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                error(
                    appContext,
                    DiagnosticModule.SYSTEM,
                    "Uncaught exception",
                    mapOf("thread" to thread.name),
                    throwable
                )
                runCatching { writer.submit {}.get(500, TimeUnit.MILLISECONDS) }
                previousExceptionHandler?.uncaughtException(thread, throwable)
            }
        }

        info(
            appContext,
            DiagnosticModule.SYSTEM,
            "Diagnostics initialized",
            mapOf(
                "android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                "package" to appContext.packageName
            )
        )
    }

    fun debug(
        context: Context,
        module: DiagnosticModule,
        message: String,
        details: Map<String, Any?> = emptyMap(),
        throwable: Throwable? = null
    ) {
        log(context, DiagnosticLevel.DEBUG, module, message, details, throwable)
    }

    fun info(
        context: Context,
        module: DiagnosticModule,
        message: String,
        details: Map<String, Any?> = emptyMap(),
        throwable: Throwable? = null
    ) {
        log(context, DiagnosticLevel.INFO, module, message, details, throwable)
    }

    fun warn(
        context: Context,
        module: DiagnosticModule,
        message: String,
        details: Map<String, Any?> = emptyMap(),
        throwable: Throwable? = null
    ) {
        log(context, DiagnosticLevel.WARN, module, message, details, throwable)
    }

    fun error(
        context: Context,
        module: DiagnosticModule,
        message: String,
        details: Map<String, Any?> = emptyMap(),
        throwable: Throwable? = null
    ) {
        log(context, DiagnosticLevel.ERROR, module, message, details, throwable)
    }

    fun log(
        context: Context,
        level: DiagnosticLevel,
        module: DiagnosticModule,
        message: String,
        details: Map<String, Any?> = emptyMap(),
        throwable: Throwable? = null
    ) {
        val appContext = context.applicationContext ?: context
        if (level == DiagnosticLevel.DEBUG && !isDebugEnabled(appContext)) return

        val event = DiagnosticEvent(
            timestampMs = System.currentTimeMillis(),
            level = level,
            module = module,
            message = sanitizeText(message),
            details = sanitizeDetails(details),
            throwable = throwable?.toDiagnosticSummary()
        )

        writeLogcat(level, event.toLogcatLine())
        persist(appContext, event)
    }

    @androidx.annotation.WorkerThread
    fun getEvents(context: Context): List<DiagnosticEvent> {
        writer.submit {}.get()
        return synchronized(lock) {
            readEventsLocked(context.applicationContext ?: context)
        }
    }

    @androidx.annotation.WorkerThread
    fun clear(context: Context) {
        writer.submit {}.get()
        synchronized(lock) {
            getLogFile(context.applicationContext ?: context)?.delete()
        }
    }

    fun enableDebugFor(context: Context, durationMs: Long = 30L * 60L * 1000L) {
        val untilMs = System.currentTimeMillis() + durationMs
        getPrefs(context.applicationContext ?: context)
            .edit()
            .putLong(KEY_DEBUG_UNTIL_MS, untilMs)
            .apply()
        info(
            context,
            DiagnosticModule.SYSTEM,
            "Detailed diagnostics enabled",
            mapOf("until" to formatTimestamp(untilMs))
        )
    }

    fun disableDebug(context: Context) {
        getPrefs(context.applicationContext ?: context)
            .edit()
            .remove(KEY_DEBUG_UNTIL_MS)
            .apply()
        info(context, DiagnosticModule.SYSTEM, "Detailed diagnostics disabled")
    }

    fun isDebugEnabled(context: Context): Boolean {
        return getDebugUntilMs(context) > System.currentTimeMillis()
    }

    fun getDebugUntilMs(context: Context): Long {
        return getPrefs(context.applicationContext ?: context).getLong(KEY_DEBUG_UNTIL_MS, 0L)
    }

    fun buildIssueReport(
        context: Context,
        events: List<DiagnosticEvent> = getEvents(context)
    ): String {
        val appContext = context.applicationContext ?: context
        val selectedEvents = events.takeLast(120)
        val recentProblems = events
            .filter { it.level == DiagnosticLevel.WARN || it.level == DiagnosticLevel.ERROR }
            .takeLast(20)

        return buildString {
            appendLine("## AAMediaMate Diagnostic Report")
            appendLine()
            appendLine("### Environment")
            buildEnvironmentSummary(appContext).forEach { (key, value) ->
                appendLine("- $key: $value")
            }
            appendLine()
            appendLine("### Recent Warnings / Errors")
            if (recentProblems.isEmpty()) {
                appendLine("- None")
            } else {
                recentProblems.forEach { appendLine("- ${it.toReportLine()}") }
            }
            appendLine()
            appendLine("### Timeline")
            if (selectedEvents.isEmpty()) {
                appendLine("- No diagnostic events captured.")
            } else {
                selectedEvents.forEach { appendLine("- ${it.toReportLine()}") }
            }
        }
    }

    fun sanitizeUrl(rawUrl: String): String {
        return runCatching {
            val uri = URI(rawUrl)
            val scheme = uri.scheme ?: return rawUrl.substringBefore("?").take(MAX_DETAIL_LENGTH)
            val host = uri.host ?: return rawUrl.substringBefore("?").take(MAX_DETAIL_LENGTH)
            val port = if (uri.port >= 0) ":${uri.port}" else ""
            val path = uri.rawPath?.takeIf { it.isNotBlank() } ?: ""
            "$scheme://$host$port$path"
        }.getOrElse {
            rawUrl.substringBefore("?").take(MAX_DETAIL_LENGTH)
        }
    }

    internal fun pruneEvents(
        events: List<DiagnosticEvent>,
        nowMs: Long = System.currentTimeMillis()
    ): List<DiagnosticEvent> {
        val cutoff = nowMs - RETENTION_MS
        return events
            .filter { it.timestampMs >= cutoff }
            .takeLast(MAX_EVENTS)
    }

    private fun persist(context: Context, event: DiagnosticEvent) {
        if (!pendingWrites.tryAcquire()) return
        writer.execute {
            try { persistOnWorker(context, event) } finally { pendingWrites.release() }
        }
    }

    private fun persistOnWorker(context: Context, event: DiagnosticEvent) {
        synchronized(lock) {
            runCatching {
                val file = getLogFile(context) ?: return
                file.parentFile?.mkdirs()
                file.appendText(event.toJson().toString() + "\n")
                if (++writesSinceCompaction >= 50 || file.length() > 1024 * 1024) {
                    readEventsLocked(context)
                    writesSinceCompaction = 0
                }
            }.onFailure {
                writeInternalWarning("Failed to persist diagnostic event: ${it.message}")
            }
        }
    }

    private fun readEventsLocked(context: Context): List<DiagnosticEvent> {
        return runCatching {
            val file = getLogFile(context) ?: return emptyList()
            if (!file.exists()) return emptyList()
            val events = file.readLines()
                .mapNotNull { line -> line.takeIf { it.isNotBlank() }?.let(::eventFromJson) }
            val pruned = pruneEvents(events)
            if (pruned.size != events.size) {
                file.writeText(pruned.joinToString(separator = "\n", postfix = "\n") { it.toJson().toString() })
            }
            pruned
        }.getOrElse {
            writeInternalWarning("Failed to read diagnostic events: ${it.message}")
            emptyList()
        }
    }

    private fun getLogFile(context: Context): File? {
        return runCatching {
            val dir = context.filesDir ?: return null
            File(dir, LOG_FILE)
        }.getOrNull()
    }

    private fun getPrefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun buildEnvironmentSummary(context: Context): List<Pair<String, String>> {
        val providers = runCatching {
            SettingsManager.getLyricsProviders(context).joinToString(", ") { provider ->
                "${provider.priority}:${provider.id}:${if (provider.isEnabled) "on" else "off"}"
            }
        }.getOrDefault("unavailable")

        val lrcApi = runCatching {
            SettingsManager.getLrcApiBaseUri(context)
                .takeIf { it.isNotBlank() }
                ?.let(::sanitizeUrl)
                ?: "not configured"
        }.getOrDefault("unavailable")

        val debugUntilMs = getDebugUntilMs(context)
        val debugState = if (debugUntilMs > System.currentTimeMillis()) {
            "on until ${formatTimestamp(debugUntilMs)}"
        } else {
            "off"
        }

        return listOf(
            "Generated at" to formatTimestamp(System.currentTimeMillis()),
            "App version" to getAppVersion(context),
            "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "Package" to context.packageName,
            "Notification access" to runCatching { hasNotificationAccess(context).toString() }.getOrDefault("unavailable"),
            "Current media package" to (MediaBridgeSessionManager.getCurrentMediaPackage() ?: "none"),
            "Lyrics enabled" to runCatching { SettingsManager.getLyricsEnabled(context).toString() }.getOrDefault("unavailable"),
            "Lyrics providers" to providers,
            "LRC API" to lrcApi,
            "Detailed diagnostics" to debugState
        )
    }

    private fun getAppVersion(context: Context): String {
        return runCatching {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            "${packageInfo.versionName} (${PackageInfoCompat.getLongVersionCode(packageInfo)})"
        }.getOrDefault("unknown")
    }

    private fun sanitizeDetails(details: Map<String, Any?>): Map<String, String> {
        return details
            .filterKeys { it.isNotBlank() }
            .mapValues { (key, value) ->
                when {
                    sensitiveKeyRegex.containsMatchIn(key) -> "[redacted]"
                    key.equals("url", ignoreCase = true) || key.endsWith("Url", ignoreCase = true) -> sanitizeUrl(value.orEmptyString())
                    else -> sanitizeText(value.orEmptyString())
                }
            }
    }

    private fun sanitizeText(value: String): String {
        val redacted = authorizationValueRegex.replace(value, "$1 [redacted]")
        return redacted
            .replace("\r", "\\r")
            .replace("\n", "\\n")
            .let { if (it.length > MAX_DETAIL_LENGTH) it.take(MAX_DETAIL_LENGTH) + "…" else it }
    }

    private fun Any?.orEmptyString(): String = this?.toString() ?: ""

    private fun Throwable.toDiagnosticSummary(): String {
        val topFrame = stackTrace.firstOrNull()?.let { "${it.className}.${it.methodName}:${it.lineNumber}" }
        return listOfNotNull(
            this::class.java.name,
            message?.let(::sanitizeText),
            topFrame
        ).joinToString(" | ")
    }

    private fun DiagnosticLevel.toAndroidPriority(): Int {
        return when (this) {
            DiagnosticLevel.DEBUG -> Log.DEBUG
            DiagnosticLevel.INFO -> Log.INFO
            DiagnosticLevel.WARN -> Log.WARN
            DiagnosticLevel.ERROR -> Log.ERROR
        }
    }

    private fun writeLogcat(level: DiagnosticLevel, message: String) {
        runCatching {
            Log.println(level.toAndroidPriority(), LOGCAT_TAG, message)
        }
    }

    private fun writeInternalWarning(message: String) {
        runCatching {
            Log.w(LOGCAT_TAG, message)
        }
    }

    private fun DiagnosticEvent.toLogcatLine(): String {
        val detailText = details.entries.joinToString(" ") { "${it.key}=${it.value}" }
        return listOf(level.name, module.name, message, detailText, throwable.orEmpty())
            .filter { it.isNotBlank() }
            .joinToString(" | ")
    }

    private fun DiagnosticEvent.toReportLine(): String {
        val detailText = details.entries.joinToString(", ") { "${it.key}=${it.value}" }
        return buildString {
            append(formatTimestamp(timestampMs))
            append(" ")
            append(level.name)
            append(" ")
            append(module.name)
            append(" - ")
            append(message)
            if (detailText.isNotBlank()) append(" ($detailText)")
            if (!throwable.isNullOrBlank()) append(" [$throwable]")
        }
    }

    private fun DiagnosticEvent.toJson(): JSONObject {
        return JSONObject().apply {
            put("timestampMs", timestampMs)
            put("level", level.name)
            put("module", module.name)
            put("message", message)
            put("details", JSONObject(details))
            throwable?.let { put("throwable", it) }
        }
    }

    private fun formatTimestamp(timestampMs: Long): String =
        DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(timestampMs))

    private fun eventFromJson(line: String): DiagnosticEvent? {
        return runCatching {
            val json = JSONObject(line)
            val detailsJson = json.optJSONObject("details") ?: JSONObject()
            val details = mutableMapOf<String, String>()
            val keys = detailsJson.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                details[key] = detailsJson.optString(key)
            }
            DiagnosticEvent(
                timestampMs = json.getLong("timestampMs"),
                level = DiagnosticLevel.valueOf(json.getString("level")),
                module = DiagnosticModule.valueOf(json.getString("module")),
                message = json.getString("message"),
                details = details,
                throwable = json.optString("throwable").takeIf { it.isNotBlank() }
            )
        }.getOrNull()
    }
}
