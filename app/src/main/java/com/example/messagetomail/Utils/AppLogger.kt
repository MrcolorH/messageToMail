package com.example.messagetomail.Utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

class AppLogger private constructor(private val context: Context) {

    private val logs = CopyOnWriteArrayList<LogEntry>()
    private val maxMemoryLogs = 200
    private val maxFileSize = 5 * 1024 * 1024L // 5MB
    private val maxBackupIndex = 3
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val scope = CoroutineScope(Dispatchers.IO)

    private val logFile: File by lazy {
        File(context.filesDir, "app_logs.txt")
    }

    private fun rotateLogsIfNeeded() {
        if (!logFile.exists() || logFile.length() < maxFileSize) return
        val oldestBackup = File(context.filesDir, "app_logs.txt.$maxBackupIndex")
        if (oldestBackup.exists()) oldestBackup.delete()
        for (i in maxBackupIndex - 1 downTo 1) {
            val src = File(context.filesDir, "app_logs.txt.$i")
            val dst = File(context.filesDir, "app_logs.txt.${i + 1}")
            if (src.exists()) src.renameTo(dst)
        }
        val backup1 = File(context.filesDir, "app_logs.txt.1")
        logFile.renameTo(backup1)
    }

    data class LogEntry(
        val timestamp: Long,
        val level: String,
        val tag: String,
        val message: String
    ) {
        fun formatTime(): String {
            return SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
        }

        override fun toString(): String {
            return "[${formatTime()}] [$level] [$tag] $message"
        }
    }

    companion object {
        const val LEVEL_DEBUG = "D"
        const val LEVEL_INFO = "I"
        const val LEVEL_WARN = "W"
        const val LEVEL_ERROR = "E"

        @Volatile
        private var instance: AppLogger? = null

        fun getInstance(context: Context): AppLogger {
            return instance ?: synchronized(this) {
                instance ?: AppLogger(context.applicationContext).also { instance = it }
            }
        }

        // 便捷方法：无需上下文即可记录（需要先初始化）
        fun d(tag: String, message: String) {
            instance?.log(LEVEL_DEBUG, tag, message)
                ?: Log.d(tag, message)
        }

        fun i(tag: String, message: String) {
            instance?.log(LEVEL_INFO, tag, message)
                ?: Log.i(tag, message)
        }

        fun w(tag: String, message: String) {
            instance?.log(LEVEL_WARN, tag, message)
                ?: Log.w(tag, message)
        }

        fun e(tag: String, message: String, throwable: Throwable? = null) {
            val fullMsg = if (throwable != null) "$message\n${throwable.stackTraceToString()}" else message
            instance?.log(LEVEL_ERROR, tag, fullMsg)
                ?: Log.e(tag, message, throwable)
        }
    }

    @Synchronized
    fun log(level: String, tag: String, message: String) {
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message
        )

        logs.add(entry)
        if (logs.size > maxMemoryLogs) {
            logs.removeAt(0)
        }

        // 同时输出到系统 Logcat
        when (level) {
            LEVEL_DEBUG -> Log.d(tag, message)
            LEVEL_INFO -> Log.i(tag, message)
            LEVEL_WARN -> Log.w(tag, message)
            LEVEL_ERROR -> Log.e(tag, message)
        }

        // 异步写入文件
        scope.launch {
            try {
                rotateLogsIfNeeded()
                logFile.appendText("${entry}\n")
            } catch (e: Exception) {
                Log.e("AppLogger", "写入日志文件失败", e)
            }
        }
    }

    fun getLogs(): List<LogEntry> {
        return logs.toList().sortedByDescending { it.timestamp }
    }

    fun getLogsFromFile(): List<LogEntry> {
        return try {
            val allFiles = listOf(logFile) + (1..maxBackupIndex).map { i ->
                File(context.filesDir, "app_logs.txt.$i")
            }
            allFiles.filter { it.exists() }.flatMap { file ->
                file.readLines().mapNotNull { line -> parseLogLine(line) }
            }.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseLogLine(line: String): LogEntry? {
        // 格式: [yyyy-MM-dd HH:mm:ss.SSS] [LEVEL] [TAG] message...
        val regex = Regex("""\[(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3})\] \[(\w)\] \[([^\]]+)\] (.+)""")
        val match = regex.find(line) ?: return null
        val (timeStr, level, tag, msg) = match.destructured
        return try {
            LogEntry(
                timestamp = dateFormat.parse(timeStr)?.time ?: System.currentTimeMillis(),
                level = level,
                tag = tag,
                message = msg
            )
        } catch (e: Exception) {
            null
        }
    }

    fun clearLogs() {
        logs.clear()
        scope.launch {
            try {
                logFile.delete()
                for (i in 1..maxBackupIndex) {
                    File(context.filesDir, "app_logs.txt.$i").delete()
                }
            } catch (e: Exception) {
                Log.e("AppLogger", "清空日志文件失败", e)
            }
        }
    }

    fun getLogFilePath(): String = logFile.absolutePath
}
