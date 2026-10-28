package com.duorhythm.app.data.repository

import android.content.Context
import com.duorhythm.app.LifeSyncApp
import com.duorhythm.app.data.local.db.DatabaseHelper
import org.json.JSONArray
import org.json.JSONObject

object DataBackupHelper {

    fun generateSelectedProfilesBackupJson(
        selectedSlots: List<Int> = listOf(1, 2, 3),
        context: Context? = null
    ): String {
        val ctx = context ?: LifeSyncApp.instance
        val dbHelper = DatabaseHelper.getInstance(ctx)
        
        // Export full database JSON with meta header
        val jsonString = dbHelper.exportDatabaseToJson()
        val root = try {
            JSONObject(jsonString)
        } catch (_: Exception) {
            JSONObject()
        }

        val slotsArray = JSONArray()
        for (slot in selectedSlots) {
            slotsArray.put(slot)
        }
        root.put("selected_slots", slotsArray)
        root.put("version", "1.0.1-beta")
        root.put("exported_at", System.currentTimeMillis())

        return root.toString(2)
    }
}
