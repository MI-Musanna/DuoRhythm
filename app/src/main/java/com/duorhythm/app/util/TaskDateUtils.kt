package com.duorhythm.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object TaskDateUtils {

    private val storageFormat: SimpleDateFormat
        get() = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

    private val displayFormat: SimpleDateFormat
        get() = SimpleDateFormat("d MMM, yyyy", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

    fun formatDisplayDate(dateStr: String?): String {
        if (dateStr.isNullOrBlank()) return ""
        return try {
            val date = storageFormat.parse(dateStr.trim()) ?: return dateStr
            displayFormat.format(date)
        } catch (_: Exception) {
            dateStr
        }
    }

    fun toUtcMillis(dateStr: String?): Long? {
        if (dateStr.isNullOrBlank()) return null
        return try {
            storageFormat.parse(dateStr.trim())?.time
        } catch (_: Exception) {
            null
        }
    }

    fun fromUtcMillisToStorage(millis: Long): String {
        return storageFormat.format(Date(millis))
    }

    fun fromUtcMillisToDisplay(millis: Long): String {
        return displayFormat.format(Date(millis))
    }

    fun getTodayStorageDate(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }
}
