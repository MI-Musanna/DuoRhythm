package com.duorhythm.app.util

import android.content.Context
import android.net.Uri
import java.io.File

object FileUtils {
    /**
     * Verifies if a given URI points to a valid, accessible file.
     */
    fun checkFileExists(context: Context, uri: Uri): Boolean {
        return try {
            when (uri.scheme) {
                "file" -> {
                    val path = uri.path ?: return false
                    File(path).exists()
                }
                "content" -> {
                    context.contentResolver.openInputStream(uri)?.use { true } ?: false
                }
                else -> {
                    val path = uri.path
                    if (path != null && File(path).exists()) {
                        true
                    } else {
                        context.contentResolver.openInputStream(uri)?.use { true } ?: false
                    }
                }
            }
        } catch (_: Exception) {
            false
        }
    }
}
