package com.duorhythm.app.util

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.ImageView
import java.io.File
import java.io.FileOutputStream

object ProfileAvatarHelper {

    /**
     * Unified avatar renderer:
     * 1. Loads valid file://, path, or content:// avatar bitmap with 85% dark-mode glare dimming.
     * 2. When no photo exists, delegates directly to DeterministicAvatarHelper so named profiles
     *    render crisp 2-letter initials (e.g., "MI") on a deterministic name-hashed background.
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
                        imageView.setPadding(0, 0, 0, 0)
                        imageView.imageTintList = null
                        imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                        imageView.setImageBitmap(bitmap)
                        DuoMotionHelper.applyDarkPhotoDimming(imageView, hasPhoto = true)
                        loaded = true
                    }
                } else if (avatarPath.startsWith("content://")) {
                    val uri = Uri.parse(avatarPath)
                    imageView.context.contentResolver.openInputStream(uri)?.use { stream ->
                        val bitmap = BitmapFactory.decodeStream(stream)
                        if (bitmap != null) {
                            imageView.setPadding(0, 0, 0, 0)
                            imageView.imageTintList = null
                            imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                            imageView.setImageBitmap(bitmap)
                            DuoMotionHelper.applyDarkPhotoDimming(imageView, hasPhoto = true)
                            loaded = true
                        }
                    }
                }
            } catch (_: Exception) {
                loaded = false
            }
        }

        if (!loaded) {
            DuoMotionHelper.applyDarkPhotoDimming(imageView, hasPhoto = false)
            val sizeDp = if (imageView.layoutParams != null && imageView.layoutParams.width > 0) {
                (imageView.layoutParams.width / imageView.resources.displayMetrics.density).toInt().coerceAtLeast(24)
            } else {
                40
            }
            DeterministicAvatarHelper.bindAvatar(
                imageView = imageView,
                name = profileName,
                photoUri = null,
                sizeDp = sizeDp
            )
        }
    }

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
