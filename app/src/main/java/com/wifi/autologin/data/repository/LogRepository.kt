package com.wifi.autologin.data.repository

import com.wifi.autologin.data.model.LogEntry
import com.wifi.autologin.data.model.LogLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object LogRepository {

    private const val MAX_LOGS = 100
    private val _logs = MutableStateFlow<List<LogEntry>>(
        listOf(
            LogEntry(
                level = LogLevel.INFO,
                title = "Service Initialized",
                message = "WiFi AutoLogin Pro engine ready to detect captive portals."
            )
        )
    )
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    fun log(level: LogLevel, title: String, message: String, rawPayload: String? = null) {
        android.util.Log.i("WifiAutoLogin", "[$level] [$title] $message")
        val entry = LogEntry(
            level = level,
            title = title,
            message = message,
            rawPayload = rawPayload
        )
        val updated = listOf(entry) + _logs.value.take(MAX_LOGS - 1)
        _logs.value = updated
    }

    fun info(title: String, message: String, payload: String? = null) = log(LogLevel.INFO, title, message, payload)
    fun success(title: String, message: String, payload: String? = null) = log(LogLevel.SUCCESS, title, message, payload)
    fun warning(title: String, message: String, payload: String? = null) = log(LogLevel.WARNING, title, message, payload)
    fun error(title: String, message: String, payload: String? = null) = log(LogLevel.ERROR, title, message, payload)

    fun clear() {
        _logs.value = emptyList()
    }
}
