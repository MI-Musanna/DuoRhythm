package com.duorhythm.app.ui.portal

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.databinding.ActivityAssessmentDetailsBinding
import com.duorhythm.app.util.FileUtils
import com.google.android.material.snackbar.Snackbar
import database.entities.AssessmentEntity

class AssessmentDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAssessmentDetailsBinding
    private lateinit var dbHelper: DatabaseHelper
    private var assessmentId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAssessmentDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = DatabaseHelper.getInstance(this)
        assessmentId = intent.getLongExtra(EXTRA_ASSESSMENT_ID, -1L)

        binding.toolbarAssessmentDetails.setNavigationOnClickListener {
            finish()
        }

        loadAssessment()
    }

    private fun loadAssessment() {
        val item = dbHelper.getItemsForSubject(-1).firstOrNull { it.id == assessmentId }
            ?: dbHelper.getAllExams().firstOrNull { it.first.id == assessmentId }?.first
        if (item == null) {
            finish()
            return
        }

        val assessment = AssessmentEntity.fromSubjectWorkItem(item)

        binding.tvAssessmentTitle.text = assessment.title
        binding.tvAssessmentType.text = assessment.type
        binding.tvAssessmentRoom.text = if (assessment.room.isNotBlank()) "Room: ${assessment.room}" else "Room: Not specified"
        binding.tvAssessmentDate.text = "Date: ${assessment.date} • ${assessment.time}"
        binding.tvAssessmentSyllabus.text = if (assessment.syllabus.isNotBlank()) "Syllabus / Topics:\n${assessment.syllabus}" else "No syllabus notes specified."

        if (!assessment.attachmentName.isNullOrBlank()) {
            binding.layoutAttachmentPreview.visibility = View.VISIBLE
            binding.tvAttachmentFileName.text = assessment.attachmentName
            binding.layoutAttachmentPreview.setOnClickListener {
                val context = this
                try {
                    val uri = Uri.parse(assessment.attachmentUri)
                    val fileExists = FileUtils.checkFileExists(context, uri)
                    if (!fileExists) {
                        Snackbar.make(binding.root, "Original file not found on this device.", Snackbar.LENGTH_LONG).show()
                        return@setOnClickListener
                    }
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Snackbar.make(binding.root, "Unable to open attachment.", Snackbar.LENGTH_SHORT).show()
                }
            }
        } else {
            binding.layoutAttachmentPreview.visibility = View.GONE
        }
    }

    companion object {
        const val EXTRA_ASSESSMENT_ID = "extra_assessment_id"

        fun start(context: Context, assessmentId: Long) {
            val intent = Intent(context, AssessmentDetailsActivity::class.java).apply {
                putExtra(EXTRA_ASSESSMENT_ID, assessmentId)
            }
            context.startActivity(intent)
        }
    }
}
