package com.duorhythm.app.util

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.ImageView
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
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
                        val rounded = RoundedBitmapDrawableFactory.create(imageView.resources, bitmap).apply {
                            isCircular = true
                        }
                        imageView.clipToOutline = true
                        imageView.setPadding(0, 0, 0, 0)
                        imageView.imageTintList = null
                        imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                        imageView.setImageDrawable(rounded)
                        DuoMotionHelper.applyDarkPhotoDimming(imageView, hasPhoto = true)
                        loaded = true
                    }
                } else if (avatarPath.startsWith("content://")) {
                    val uri = Uri.parse(avatarPath)
                    imageView.context.contentResolver.openInputStream(uri)?.use { stream ->
                        val bitmap = BitmapFactory.decodeStream(stream)
                        if (bitmap != null) {
                            val rounded = RoundedBitmapDrawableFactory.create(imageView.resources, bitmap).apply {
                                isCircular = true
                            }
                            imageView.clipToOutline = true
                            imageView.setPadding(0, 0, 0, 0)
                            imageView.imageTintList = null
                            imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                            imageView.setImageDrawable(rounded)
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

    /**
     * Resolves the actual File on disk representing the profile's avatar,
     * checking workspace_avatars/, custom paths, and avatar_profile_${profileId}.jpg fallback.
     */
    fun resolveAvatarFile(context: Context, profileId: Long, avatarUriString: String?): File? {
        if (!avatarUriString.isNullOrBlank()) {
            val cleanPath = when {
                avatarUriString.startsWith("file://") -> Uri.parse(avatarUriString).path ?: avatarUriString.removePrefix("file://")
                avatarUriString.contains("?") -> avatarUriString.substringBefore("?")
                else -> avatarUriString
            }
            val cleanNoQuery = if (cleanPath.contains("?")) cleanPath.substringBefore("?") else cleanPath
            val f = File(cleanNoQuery)
            if (f.exists() && f.isFile) return f

            val relFile = File(context.filesDir, cleanNoQuery.removePrefix("/"))
            if (relFile.exists() && relFile.isFile) return relFile

            val wsFile = File(context.filesDir, "workspace_avatars/${File(cleanNoQuery).name}")
            if (wsFile.exists() && wsFile.isFile) return wsFile
        }

        // Check fallback standard file
        val fallback = File(context.filesDir, "avatar_profile_${profileId}.jpg")
        if (fallback.exists() && fallback.isFile) return fallback

        // Check latest file in workspace_avatars for this profileId
        val wsDir = File(context.filesDir, "workspace_avatars")
        if (wsDir.exists()) {
            val match = wsDir.listFiles { _, name -> name.startsWith("avatar_slot_${profileId}_") }?.maxByOrNull { it.lastModified() }
            if (match != null && match.exists() && match.isFile) return match
        }

        return fallback
    }
}
