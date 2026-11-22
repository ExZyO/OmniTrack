package com.omnitrack.app.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogLevel {
    DEBUG, INFO, WARN, ERROR
}

data class LogEntry(
    val timestamp: String,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val stackTrace: String? = null
) {
    fun toFormattedString(): String {
        val base = "[$timestamp] [${level.name}] [$tag] $message"
        return if (stackTrace != null) "$base\n$stackTrace" else base
    }
}

object AppLogger {

    private const val PREFS_NAME = "omnitrack_diagnostics"
    private const val KEY_LOGGING_ENABLED = "is_logging_enabled_v2"
    private const val KEY_LIVE_LOGCAT_ENABLED = "is_live_logcat_enabled_v2"
    private const val KEY_LIVE_LOGCAT_AUTO_REFRESH = "is_live_logcat_auto_refresh_v2"
    private const val LOG_FILE_NAME = "omnitrack_diagnostics.log"
    private const val MAX_IN_MEMORY_LOGS = 600

    private var prefs: SharedPreferences? = null
    private var logFile: File? = null
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private val _isLoggingEnabled = MutableStateFlow(false)
    val isLoggingEnabled: StateFlow<Boolean> = _isLoggingEnabled.asStateFlow()

    private val _isLiveLogcatEnabled = MutableStateFlow(false)
    val isLiveLogcatEnabled: StateFlow<Boolean> = _isLiveLogcatEnabled.asStateFlow()

    private val _isLiveLogcatAutoRefresh = MutableStateFlow(false)
    val isLiveLogcatAutoRefresh: StateFlow<Boolean> = _isLiveLogcatAutoRefresh.asStateFlow()

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    fun init(context: Context) {
        val appCtx = context.applicationContext
        prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val enabled = prefs?.getBoolean(KEY_LOGGING_ENABLED, false) ?: false
        _isLoggingEnabled.value = enabled
        val liveLogcat = prefs?.getBoolean(KEY_LIVE_LOGCAT_ENABLED, false) ?: false
        _isLiveLogcatEnabled.value = liveLogcat
        val autoRefresh = prefs?.getBoolean(KEY_LIVE_LOGCAT_AUTO_REFRESH, false) ?: false
        _isLiveLogcatAutoRefresh.value = autoRefresh

        logFile = File(appCtx.filesDir, LOG_FILE_NAME)

        // Install global uncaught exception crash handler
        installCrashHandler(appCtx)

        if (enabled) {
            i("AppLogger", "OmniTrack diagnostics initialized. Logging enabled: $enabled")
        }
    }

    fun setLoggingEnabled(enabled: Boolean) {
        _isLoggingEnabled.value = enabled
        prefs?.edit()?.putBoolean(KEY_LOGGING_ENABLED, enabled)?.apply()
        if (enabled) {
            i("AppLogger", "Diagnostics logging enabled by user.")
        }
    }

    fun setLiveLogcatEnabled(enabled: Boolean) {
        _isLiveLogcatEnabled.value = enabled
        prefs?.edit()?.putBoolean(KEY_LIVE_LOGCAT_ENABLED, enabled)?.apply()
        if (!enabled) {
            setLiveLogcatAutoRefresh(false)
        }
    }

    fun setLiveLogcatAutoRefresh(enabled: Boolean) {
        _isLiveLogcatAutoRefresh.value = enabled
        prefs?.edit()?.putBoolean(KEY_LIVE_LOGCAT_AUTO_REFRESH, enabled)?.apply()
    }

    fun d(tag: String, message: String) = log(LogLevel.DEBUG, tag, message, null)
    fun i(tag: String, message: String) = log(LogLevel.INFO, tag, message, null)
    fun w(tag: String, message: String, tr: Throwable? = null) = log(LogLevel.WARN, tag, message, tr)
    fun e(tag: String, message: String, tr: Throwable? = null) = log(LogLevel.ERROR, tag, message, tr)

    private val authHeaderRegex = Regex("""(?i)(Authorization\s*:\s*(?:Bearer|Token|Basic)\s+)([^\s\r\n]+)""")
    private val bearerTokenRegex = Regex("""(?i)\b(Bearer|Token)\s+[A-Za-z0-9._~+/=-]{12,}""")
    private val jsonSecretRegex = Regex("""(?i)("?(?:access_token|refresh_token|id_token|client_secret|session_id|api_key|apikey|password|token)"?\s*[:=]\s*"?)([^"&,\s\}\]]+)("?)""")

    fun sanitize(raw: String): String {
        var cleaned = authHeaderRegex.replace(raw, "$1[REDACTED]")
        cleaned = bearerTokenRegex.replace(cleaned, "$1 [REDACTED]")
        cleaned = jsonSecretRegex.replace(cleaned) { match ->
            "${match.groupValues[1]}[REDACTED]${match.groupValues[3]}"
        }
        return cleaned
    }

    private fun log(level: LogLevel, tag: String, message: String, tr: Throwable?) {
        if (!_isLoggingEnabled.value && tag != "FATAL_CRASH") return

        val safeMessage = sanitize(message)

        // Mirror to Logcat for ADB / Wireless Debugging
        when (level) {
            LogLevel.DEBUG -> Log.d("OmniTrack-$tag", safeMessage, tr)
            LogLevel.INFO -> Log.i("OmniTrack-$tag", safeMessage, tr)
            LogLevel.WARN -> Log.w("OmniTrack-$tag", safeMessage, tr)
            LogLevel.ERROR -> Log.e("OmniTrack-$tag", safeMessage, tr)
        }

        if (!_isLoggingEnabled.value) return

        val stackTrace = tr?.let {
            val sw = StringWriter()
            it.printStackTrace(PrintWriter(sw))
            sanitize(sw.toString())
        }

        val entry = LogEntry(
            timestamp = dateFormat.format(Date()),
            level = level,
            tag = tag,
            message = safeMessage,
            stackTrace = stackTrace
        )

        // Append to in-memory reactive flow
        val current = _logs.value.toMutableList()
        if (current.size >= MAX_IN_MEMORY_LOGS) {
            current.removeAt(0)
        }
        current.add(entry)
        _logs.value = current

        // Append to persistent disk log file
        try {
            logFile?.appendText(entry.toFormattedString() + "\n")
        } catch (_: Exception) {
            // Ignore file write errors
        }
    }

    fun getLogsAsText(): String {
        return _logs.value.joinToString("\n") { it.toFormattedString() }
    }

    fun clearLogs() {
        _logs.value = emptyList()
        try {
            logFile?.writeText("")
        } catch (_: Exception) {}
        if (_isLoggingEnabled.value) {
            i("AppLogger", "Logs cleared by user.")
        }
    }

    fun getLogFile(): File? = logFile

    private fun installCrashHandler(context: Context) {
        val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                e("FATAL_CRASH", "Uncaught exception on thread ${thread.name}: ${throwable.message}", throwable)
            } catch (_: Exception) {}
            originalHandler?.uncaughtException(thread, throwable)
        }
    }
}
