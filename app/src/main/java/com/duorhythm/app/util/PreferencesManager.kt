package com.duorhythm.app.util

import android.content.Context
import android.content.SharedPreferences
import com.duorhythm.app.data.local.pref.AppPreferences
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class PreferencesManager private constructor(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        AppPreferences.PREFS_NAME,
        Context.MODE_PRIVATE
    )

    companion object {
        @Volatile
        private var instance: PreferencesManager? = null

        fun getInstance(context: Context): PreferencesManager {
            return instance ?: synchronized(this) {
                instance ?: PreferencesManager(context).also { instance = it }
            }
        }
    }

    fun getInt(key: String, defValue: Int): Int {
        if (prefs.contains(key)) {
            return try {
                prefs.getInt(key, defValue)
            } catch (_: Exception) {
                defValue
            }
        }
        // Fallback: If querying hour or minute, check if "pref_morning_briefing_time" is set
        if (key == "pref_morning_briefing_hour" || key == "pref_morning_briefing_minute") {
            val timeStr = prefs.getString(
                AppPreferences.KEY_MORNING_BRIEFING_TIME,
                AppPreferences.DEFAULT_MORNING_BRIEFING_TIME
            ) ?: AppPreferences.DEFAULT_MORNING_BRIEFING_TIME
            val pair = parseTime(timeStr)
            if (pair != null) {
                return if (key == "pref_morning_briefing_hour") pair.first else pair.second
            }
        }
        return defValue
    }

    fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    fun getString(key: String, defValue: String? = null): String? {
        return prefs.getString(key, defValue)
    }

    fun putString(key: String, value: String?) {
        prefs.edit().putString(key, value).apply()
    }

    fun getBoolean(key: String, defValue: Boolean = false): Boolean {
        return prefs.getBoolean(key, defValue)
    }

    fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    private fun parseTime(timeStr: String): Pair<Int, Int>? {
        val formats = listOf(
            SimpleDateFormat("hh:mm a", Locale.US),
            SimpleDateFormat("h:mm a", Locale.US),
            SimpleDateFormat("HH:mm", Locale.US),
            SimpleDateFormat("H:mm", Locale.US)
        )
        for (fmt in formats) {
            try {
                val parsed = fmt.parse(timeStr.trim())
                if (parsed != null) {
                    val cal = Calendar.getInstance().apply { time = parsed }
                    return Pair(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
                }
            } catch (_: Exception) {}
        }
        return null
    }
}
