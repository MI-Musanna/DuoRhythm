package com.duorhythm.app.util

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.widget.ImageView
import com.duorhythm.app.R
import java.io.File
import java.io.FileOutputStream

object ProfileAvatarHelper {

    /**
     * Resilient avatar renderer that handles file:// URIs (with or without query parameters),
     * absolute disk paths, and content:// URIs. If the file is missing or invalid, renders
     * a clean fallback placeholder icon with #38BDF8 cyan tint to prevent blank black holes.
     */
    fun bindProfileAvatar(imageView: ImageView, avatarPath: String?, profileName: String = "") {
        var loaded = false
        if (!avatarPath.isNullOrBlank()) {
            try {
                val cleanPath = when {
                    avatarPath.startsWith("file://") -> Uri.parse(avatarPath).path ?: avatarPath.removePrefix("file://")
                    avatarPath.contains("?") -> avatarPath.substringBefore("?")
                    else -> avatarPath
                }

                val file = File(cleanPath)
                if (file.exists() && file.length() > 0) {
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    if (bitmap != null) {
                        imageView.setImageBitmap(bitmap)
                        imageView.imageTintList = null
                        loaded = true
                    }
                } else if (avatarPath.startsWith("content://")) {
                    val uri = Uri.parse(avatarPath)
                    imageView.context.contentResolver.openInputStream(uri)?.use { stream ->
                        val bitmap = BitmapFactory.decodeStream(stream)
                        if (bitmap != null) {
                            imageView.setImageBitmap(bitmap)
                            imageView.imageTintList = null
                            loaded = true
                        }
                    }
                }
            } catch (_: Exception) {
                loaded = false
            }
        }

        if (!loaded) {
            imageView.setImageResource(R.drawable.ic_profile_placeholder)
            imageView.imageTintList = ColorStateList.valueOf(Color.parseColor("#38BDF8"))
        }
    }

    /**
     * Persistent avatar storage: copies image streams directly into the app private files directory
     * under "workspace_avatars" so that gallery photos survive reboots and external file permission revokes.
     */
    fun saveAvatarPermanently(context: Context, sourceUri: Uri, profileId: Long): String {
        return try {
            val avatarDir = File(context.filesDir, "workspace_avatars")
            if (!avatarDir.exists()) avatarDir.mkdirs()
            val destFile = File(avatarDir, "avatar_slot_${profileId}_${System.currentTimeMillis()}.png")

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            destFile.absolutePath
        } catch (e: Exception) {
            sourceUri.toString()
        }
    }
}
