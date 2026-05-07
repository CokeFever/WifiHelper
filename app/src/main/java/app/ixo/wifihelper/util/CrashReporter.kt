package app.ixo.wifihelper.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 本地錯誤日誌收集器。
 *
 * 收集 App 的錯誤日誌到本地檔案，並在偵測到 crash 時
 * 提供透過 email 寄出報告的功能。
 */
object CrashReporter {

    private const val TAG = "CrashReporter"
    private const val LOG_DIR = "crash_logs"
    private const val LOG_FILE = "app_log.txt"
    private const val LOG_FILE_OLD = "app_log_old.txt"
    private const val MAX_LOG_SIZE = 500 * 1024L // 500KB
    private const val CRASH_FLAG_FILE = "crash_occurred"
    private const val EMAIL_RECIPIENT = "jack@ixo.app"

    private var logFile: File? = null
    private var context: Context? = null

    /**
     * 初始化 CrashReporter，設定 UncaughtExceptionHandler。
     * 應在 Application.onCreate() 中呼叫。
     */
    fun init(appContext: Context) {
        context = appContext.applicationContext
        val logDir = File(appContext.filesDir, LOG_DIR)
        if (!logDir.exists()) logDir.mkdirs()
        logFile = File(logDir, LOG_FILE)

        // Rotate log if too large
        rotateLogIfNeeded()

        // Write session start
        logInfo("=== App Session Started ===")
        logInfo("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        logInfo("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        logInfo("App Version: ${getAppVersion(appContext)}")

        // Set up crash handler
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            handleCrash(thread, throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    /**
     * 記錄 info 等級的日誌。
     */
    fun logInfo(message: String) {
        writeLog("INFO", message)
    }

    /**
     * 記錄 error 等級的日誌。
     */
    fun logError(message: String, throwable: Throwable? = null) {
        writeLog("ERROR", message)
        throwable?.let {
            val sw = java.io.StringWriter()
            it.printStackTrace(PrintWriter(sw))
            writeLog("ERROR", sw.toString())
        }
    }

    /**
     * 檢查上次是否有 crash 發生。
     * 應在 MainActivity.onCreate() 中呼叫。
     */
    fun didCrashLastSession(context: Context): Boolean {
        val flagFile = File(context.filesDir, CRASH_FLAG_FILE)
        return flagFile.exists()
    }

    /**
     * 清除 crash 標記。
     */
    fun clearCrashFlag(context: Context) {
        val flagFile = File(context.filesDir, CRASH_FLAG_FILE)
        flagFile.delete()
    }

    /**
     * 建立寄送 email 的 Intent。
     */
    fun createEmailIntent(context: Context): Intent? {
        val logDir = File(context.filesDir, LOG_DIR)
        val file = File(logDir, LOG_FILE)
        if (!file.exists()) return null

        val uri = try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get URI for log file", e)
            return null
        }

        val version = getAppVersion(context)
        return Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(EMAIL_RECIPIENT))
            putExtra(Intent.EXTRA_SUBJECT, "[WiFi Helper] Error Report v$version")
            putExtra(
                Intent.EXTRA_TEXT, "裝置：${Build.MANUFACTURER} ${Build.MODEL}\n" +
                    "Android：${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
                    "App 版本：v$version\n\n" +
                    "請描述發生問題時的操作：\n\n"
            )
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun handleCrash(thread: Thread, throwable: Throwable) {
        try {
            logError("FATAL CRASH on thread [${thread.name}]", throwable)
            // Set crash flag
            val ctx = context ?: return
            File(ctx.filesDir, CRASH_FLAG_FILE).createNewFile()
        } catch (_: Exception) {
            // Don't throw in crash handler
        }
    }

    private fun writeLog(level: String, message: String) {
        try {
            val file = logFile ?: return
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
            FileWriter(file, true).use { writer ->
                writer.appendLine("$timestamp [$level] $message")
            }
        } catch (_: Exception) {
            // Silently fail — don't crash the app for logging
        }
    }

    private fun rotateLogIfNeeded() {
        val file = logFile ?: return
        if (file.exists() && file.length() > MAX_LOG_SIZE) {
            val logDir = file.parentFile ?: return
            val oldFile = File(logDir, LOG_FILE_OLD)
            oldFile.delete()
            file.renameTo(oldFile)
        }
    }

    private fun getAppVersion(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (_: PackageManager.NameNotFoundException) {
            "unknown"
        }
    }
}
