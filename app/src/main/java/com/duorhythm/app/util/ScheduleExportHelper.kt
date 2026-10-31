package com.duorhythm.app.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import com.duorhythm.app.data.model.ClassEntity
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object ScheduleExportHelper {
    fun exportDayScheduleAsImage(context: Context, dayName: String, classes: List<ClassEntity>) {
        val width = 1080
        val baseHeight = 360
        val rowHeight = 160
        val totalHeight = (baseHeight + (classes.size * rowHeight)).coerceAtLeast(600)

        val bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background
        val bgPaint = Paint().apply { color = Color.parseColor("#0F172A") }
        canvas.drawRect(0f, 0f, width.toFloat(), totalHeight.toFloat(), bgPaint)

        // Header Bar
        val headerPaint = Paint().apply {
            color = Color.parseColor("#00E5FF")
            textSize = 42f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("DUORHYTHM • DAILY SCHEDULE", 60f, 100f, headerPaint)

        val subPaint = Paint().apply {
            color = Color.parseColor("#94A3B8")
            textSize = 28f
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }
        canvas.drawText("$dayName Timetable • ${classes.size} Lectures Scheduled", 60f, 150f, subPaint)

        val cardBgPaint = Paint().apply {
            color = Color.parseColor("#1E293B")
            isAntiAlias = true
        }
        val textMainPaint = Paint().apply {
            color = Color.WHITE
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val textDetailsPaint = Paint().apply {
            color = Color.parseColor("#94A3B8")
            textSize = 24f
            isAntiAlias = true
        }

        var startY = 220f
        if (classes.isEmpty()) {
            canvas.drawText("No classes scheduled for $dayName.", 60f, startY + 60f, textMainPaint)
        } else {
            for (item in classes) {
                val rect = RectF(60f, startY, (width - 60).toFloat(), startY + 130f)
                canvas.drawRoundRect(rect, 20f, 20f, cardBgPaint)

                canvas.drawText(item.subjectName, 90f, startY + 55f, textMainPaint)
                canvas.drawText("${item.startTime} - ${item.endTime}  |  Room: ${item.room}  |  ${item.teacherName}", 90f, startY + 100f, textDetailsPaint)

                startY += rowHeight
            }
        }

        saveBitmapToGallery(context, bitmap, "DuoRhythm_${dayName}_Schedule.png")
    }

    private fun saveBitmapToGallery(context: Context, bitmap: Bitmap, fileName: String) {
        try {
            val fos: OutputStream?
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/DuoRhythm")
                }
                val imageUri: Uri? = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                fos = imageUri?.let { resolver.openOutputStream(it) }
            } else {
                val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString() + "/DuoRhythm"
                val file = File(imagesDir)
                if (!file.exists()) file.mkdirs()
                val image = File(file, fileName)
                fos = FileOutputStream(image)
            }

            fos?.use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                Toast.makeText(context, "Schedule saved to Pictures/DuoRhythm", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to export schedule: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
