package com.duorhythm.app.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
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
import androidx.core.content.FileProvider
import com.duorhythm.app.data.model.AssessmentEntity
import com.duorhythm.app.data.model.ClassEntity
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object ScheduleExportHelper {

    fun generateWeeklyScheduleBitmap(context: Context, weeklyClasses: Map<String, List<ClassEntity>>): Bitmap {
        val width = 1080
        var calculatedHeight = 260
        for ((_, list) in weeklyClasses) {
            calculatedHeight += 70
            calculatedHeight += if (list.isEmpty()) 90 else (list.size * 140)
        }
        val totalHeight = calculatedHeight.coerceAtLeast(800)

        val bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint().apply { color = Color.parseColor("#0B1120") }
        canvas.drawRect(0f, 0f, width.toFloat(), totalHeight.toFloat(), bgPaint)

        val brandPaint = Paint().apply {
            color = Color.parseColor("#00E5FF")
            textSize = 40f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("DUORHYTHM • ACADEMIC TIMETABLE", 60f, 90f, brandPaint)

        val subHeaderPaint = Paint().apply {
            color = Color.parseColor("#94A3B8")
            textSize = 24f
            isAntiAlias = true
        }
        canvas.drawText("Verified Semester Schedule • Offline Generated Snapshot", 60f, 135f, subHeaderPaint)

        val dayHeaderPaint = Paint().apply {
            color = Color.parseColor("#38BDF8")
            textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val cardBgPaint = Paint().apply {
            color = Color.parseColor("#1E293B")
            isAntiAlias = true
        }
        val textMainPaint = Paint().apply {
            color = Color.WHITE
            textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val textDetailsPaint = Paint().apply {
            color = Color.parseColor("#94A3B8")
            textSize = 22f
            isAntiAlias = true
        }

        var currentY = 200f
        for ((dayName, list) in weeklyClasses) {
            canvas.drawText(dayName.uppercase(), 60f, currentY + 30f, dayHeaderPaint)
            currentY += 55f

            if (list.isEmpty()) {
                val rect = RectF(60f, currentY, (width - 60).toFloat(), currentY + 70f)
                canvas.drawRoundRect(rect, 14f, 14f, cardBgPaint)
                canvas.drawText("No classes scheduled", 90f, currentY + 45f, textDetailsPaint)
                currentY += 90f
            } else {
                for (cls in list) {
                    val rect = RectF(60f, currentY, (width - 60).toFloat(), currentY + 115f)
                    canvas.drawRoundRect(rect, 16f, 16f, cardBgPaint)
                    canvas.drawText(cls.subjectName, 90f, currentY + 48f, textMainPaint)
                    canvas.drawText("${cls.startTime} - ${cls.endTime}  |  Room: ${cls.room}  |  ${cls.teacherName}", 90f, currentY + 92f, textDetailsPaint)
                    currentY += 135f
                }
            }
            currentY += 25f
        }
        return bitmap
    }

    fun generateExamsScheduleBitmap(context: Context, exams: List<AssessmentEntity>): Bitmap {
        val width = 1080
        val baseHeight = 260
        val rowHeight = 155
        val totalHeight = (baseHeight + (exams.size * rowHeight)).coerceAtLeast(700)

        val bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint().apply { color = Color.parseColor("#0B1120") }
        canvas.drawRect(0f, 0f, width.toFloat(), totalHeight.toFloat(), bgPaint)

        val brandPaint = Paint().apply {
            color = Color.parseColor("#F59E0B")
            textSize = 40f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("DUORHYTHM • EXAMS & ASSESSMENTS", 60f, 90f, brandPaint)

        val subHeaderPaint = Paint().apply {
            color = Color.parseColor("#94A3B8")
            textSize = 24f
            isAntiAlias = true
        }
        canvas.drawText("Upcoming Assessments • Total ${exams.size} Items Included", 60f, 135f, subHeaderPaint)

        val cardBgPaint = Paint().apply {
            color = Color.parseColor("#1E293B")
            isAntiAlias = true
        }
        val textMainPaint = Paint().apply {
            color = Color.WHITE
            textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val textDetailsPaint = Paint().apply {
            color = Color.parseColor("#94A3B8")
            textSize = 22f
            isAntiAlias = true
        }
        val tagPaint = Paint().apply {
            color = Color.parseColor("#F59E0B")
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        var currentY = 200f
        if (exams.isEmpty()) {
            canvas.drawText("No upcoming assessments match criteria.", 60f, currentY + 60f, textMainPaint)
        } else {
            for (item in exams) {
                val rect = RectF(60f, currentY, (width - 60).toFloat(), currentY + 130f)
                canvas.drawRoundRect(rect, 16f, 16f, cardBgPaint)

                canvas.drawText(item.title, 90f, currentY + 50f, textMainPaint)
                canvas.drawText("[${item.category.uppercase()}]", (width - 240).toFloat(), currentY + 50f, tagPaint)

                val syllabusText = if (!item.syllabus.isNullOrBlank()) " • Syllabus: ${item.syllabus}" else ""
                val roomText = if (item.room.isNotBlank()) item.room else "N/A"
                canvas.drawText("Due: ${item.dueDate}  |  Time: ${item.dueTime}  |  Room: $roomText$syllabusText", 90f, currentY + 98f, textDetailsPaint)
                currentY += rowHeight
            }
        }
        return bitmap
    }

    fun saveBitmapToGallery(context: Context, bitmap: Bitmap, fileName: String): Boolean {
        return try {
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
                fos = FileOutputStream(File(file, fileName))
            }

            fos?.use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                Toast.makeText(context, "Saved to Pictures/DuoRhythm", Toast.LENGTH_SHORT).show()
            }
            true
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun shareBitmapViaIntent(context: Context, bitmap: Bitmap, shareTitle: String) {
        try {
            val cacheDir = File(context.cacheDir, "exports")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val file = File(cacheDir, "DuoRhythm_Shared_Schedule.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, shareTitle)
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(Intent.createChooser(intent, "Share via..."))
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to share: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

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
}
