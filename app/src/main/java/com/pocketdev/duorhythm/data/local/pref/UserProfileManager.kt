package com.pocketdev.duorhythm.data.local.pref

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

object UserProfileManager {

    private const val PREFS_NAME = "duorhythm_user_profile_prefs"

    const val KEY_USER_NAME = "key_user_name"
    const val KEY_USER_AVATAR_URI = "key_user_avatar_uri"
    const val KEY_INSTITUTION_NAME = "key_institution_name"
    const val KEY_ACADEMIC_FACULTY = "key_academic_faculty"

    private const val AVATAR_FILE_NAME = "user_avatar_profile.jpg"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getUserName(context: Context, default: String = ""): String {
        return getPrefs(context).getString(KEY_USER_NAME, null)?.takeIf { it.isNotBlank() } ?: default
    }

    fun getDisplayName(context: Context): String {
        return getUserName(context, default = "User")
    }

    fun setUserName(context: Context, name: String) {
        getPrefs(context).edit().putString(KEY_USER_NAME, name.trim()).apply()
    }

    fun getAvatarUri(context: Context): String {
        return getPrefs(context).getString(KEY_USER_AVATAR_URI, "") ?: ""
    }

    fun setAvatarUri(context: Context, uriString: String) {
        getPrefs(context).edit().putString(KEY_USER_AVATAR_URI, uriString).apply()
    }

    fun getInstitutionName(context: Context): String {
        return getPrefs(context).getString(KEY_INSTITUTION_NAME, "") ?: ""
    }

    fun setInstitutionName(context: Context, institution: String) {
        getPrefs(context).edit().putString(KEY_INSTITUTION_NAME, institution.trim()).apply()
    }

    fun getAcademicFaculty(context: Context): String {
        return getPrefs(context).getString(KEY_ACADEMIC_FACULTY, "") ?: ""
    }

    fun setAcademicFaculty(context: Context, faculty: String) {
        getPrefs(context).edit().putString(KEY_ACADEMIC_FACULTY, faculty.trim()).apply()
    }

    /**
     * Copies the content URI to internal storage cache to ensure persistent access
     * across app restarts without permission expiration.
     */
    fun saveAvatarFromUri(context: Context, uri: Uri): String? {
        return try {
            val destinationFile = File(context.filesDir, AVATAR_FILE_NAME)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    input.copyTo(output)
                }
            }
            val path = destinationFile.absolutePath
            setAvatarUri(context, path)
            path
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Loads the saved avatar as a circular cropped Bitmap.
     * Returns null if no avatar is saved or if file cannot be decoded.
     */
    fun loadCircularAvatarBitmap(context: Context, targetSizePx: Int = 128): Bitmap? {
        val path = getAvatarUri(context)
        if (path.isBlank()) return null

        val file = File(path)
        if (!file.exists()) return null

        return try {
            // First decode bounds
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            if (options.outWidth <= 0 || options.outHeight <= 0) return null

            // Calculate sample size
            var inSampleSize = 1
            val maxDim = maxOf(options.outWidth, options.outHeight)
            while (maxDim / (inSampleSize * 2) >= targetSizePx) {
                inSampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
            }
            val originalBitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return null

            val circularBitmap = getCircularBitmap(originalBitmap)
            if (originalBitmap != circularBitmap) {
                originalBitmap.recycle()
            }
            circularBitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getCircularBitmap(bitmap: Bitmap): Bitmap {
        val minEdge = min(bitmap.width, bitmap.height)
        val output = Bitmap.createBitmap(minEdge, minEdge, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
        }

        val srcRect = Rect(
            (bitmap.width - minEdge) / 2,
            (bitmap.height - minEdge) / 2,
            (bitmap.width + minEdge) / 2,
            (bitmap.height + minEdge) / 2
        )
        val dstRect = Rect(0, 0, minEdge, minEdge)

        canvas.drawARGB(0, 0, 0, 0)
        canvas.drawCircle(minEdge / 2f, minEdge / 2f, minEdge / 2f, paint)

        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, srcRect, dstRect, paint)

        return output
    }
}
