package com.duorhythm.app.data.repository

import android.content.Context
import android.net.Uri
import com.duorhythm.app.LifeSyncApp
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.util.ImageUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

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

        // Attach Base64 avatars for selected slots
        val avatarsObj = root.optJSONObject("profile_avatars") ?: JSONObject()
        val profiles = dbHelper.getAllProfiles().filter { it.slotNumber in selectedSlots }
        for (profile in profiles) {
            val file = File(ctx.filesDir, "avatar_profile_${profile.id}.jpg")
            val base64 = ImageUtils.encodeFileToBase64(file)
            if (!base64.isNullOrBlank()) {
                avatarsObj.put(profile.id.toString(), base64)
                avatarsObj.put("slot_${profile.slotNumber}", base64)
            }
        }
        root.put("profile_avatars", avatarsObj)

        val slotsArray = JSONArray()
        for (slot in selectedSlots) {
            slotsArray.put(slot)
        }
        root.put("selected_slots", slotsArray)
        root.put("version", "1.0.1-beta")
        root.put("exported_at", System.currentTimeMillis())

        return root.toString(2)
    }

    fun restoreProfileAvatarsFromJson(context: Context, root: JSONObject) {
        if (!root.has("profile_avatars")) return
        val dbHelper = DatabaseHelper.getInstance(context)
        val avatarsObj = root.getJSONObject("profile_avatars")
        val profiles = dbHelper.getAllProfiles()

        for (profile in profiles) {
            var b64: String? = null
            if (avatarsObj.has(profile.id.toString())) {
                b64 = avatarsObj.optString(profile.id.toString())
            } else if (avatarsObj.has("slot_${profile.slotNumber}")) {
                b64 = avatarsObj.optString("slot_${profile.slotNumber}")
            }

            if (!b64.isNullOrBlank()) {
                val destFile = File(context.filesDir, "avatar_profile_${profile.id}.jpg")
                if (ImageUtils.decodeBase64ToFile(b64, destFile)) {
                    val updatedUri = Uri.fromFile(destFile).toString() + "?t=" + System.currentTimeMillis()
                    dbHelper.profileDao.updateProfile(profile.copy(avatarUri = updatedUri))
                }
            }
        }
    }
}
