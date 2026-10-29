package com.duorhythm.app.data.repository

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.util.Base64
import com.duorhythm.app.LifeSyncApp
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.util.ImageUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream

object DataBackupHelper {

    fun generateSelectedProfilesBackupJson(
        selectedSlots: List<Int> = listOf(1, 2, 3),
        context: Context? = null,
        includeAttachments: Boolean = false
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

        // Process attachments for assessments and notes
        if (root.has("subject_work_items")) {
            val itemsArr = root.getJSONArray("subject_work_items")
            for (i in 0 until itemsArr.length()) {
                val itemObj = itemsArr.getJSONObject(i)
                val uriStr = itemObj.optString("attachment_uri")
                val base64Data = if (includeAttachments && !uriStr.isNullOrEmpty()) {
                    readUriToBase64(ctx, Uri.parse(uriStr))
                } else {
                    null
                }
                if (base64Data != null) {
                    itemObj.put("embeddedFileBase64", base64Data)
                    itemObj.put("embedded_file_base64", base64Data)
                }
            }
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
        root.put("include_attachments", includeAttachments)
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

    fun restoreAttachmentsFromJson(context: Context, root: JSONObject) {
        if (!root.has("subject_work_items")) return
        val dbHelper = DatabaseHelper.getInstance(context)
        val itemsArr = root.getJSONArray("subject_work_items")
        val attachDir = File(context.filesDir, "attachments").apply { mkdirs() }

        for (i in 0 until itemsArr.length()) {
            val itemObj = itemsArr.getJSONObject(i)
            val id = itemObj.optLong("id")
            val base64Data = if (itemObj.has("embeddedFileBase64")) {
                itemObj.optString("embeddedFileBase64")
            } else if (itemObj.has("embedded_file_base64")) {
                itemObj.optString("embedded_file_base64")
            } else {
                null
            }

            val rawAttachmentName = itemObj.optString("attachment_name")
            val fileName = if (!rawAttachmentName.isNullOrBlank()) rawAttachmentName else "attachment_${id}.bin"

            if (!base64Data.isNullOrBlank()) {
                val destFile = File(attachDir, fileName)
                if (writeBase64ToFile(base64Data, destFile)) {
                    val localUri = Uri.fromFile(destFile).toString()
                    val cv = ContentValues().apply {
                        put("attachment_uri", localUri)
                        put("attachment_name", fileName)
                    }
                    dbHelper.writableDatabase.update("subject_work_items", cv, "id = ?", arrayOf(id.toString()))
                }
            } else {
                // If embeddedFileBase64 is null: Re-link stored attachmentUri directly
                val existingUri = itemObj.optString("attachment_uri")
                if (!existingUri.isNullOrBlank()) {
                    val cv = ContentValues().apply {
                        put("attachment_uri", existingUri)
                        put("attachment_name", fileName)
                    }
                    dbHelper.writableDatabase.update("subject_work_items", cv, "id = ?", arrayOf(id.toString()))
                }
            }
        }
    }

    fun readUriToBase64(context: Context, uri: Uri): String? {
        return try {
            val stream = if (uri.scheme == "file") {
                FileInputStream(File(uri.path ?: return null))
            } else {
                context.contentResolver.openInputStream(uri)
            }
            stream?.use { s ->
                val bytes = s.readBytes()
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun writeBase64ToFile(base64: String, destFile: File): Boolean {
        return try {
            destFile.parentFile?.mkdirs()
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            destFile.writeBytes(bytes)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
