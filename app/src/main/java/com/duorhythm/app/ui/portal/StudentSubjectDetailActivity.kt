package com.duorhythm.app.ui.portal

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.duorhythm.app.R
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.model.StudentSubject
import com.duorhythm.app.data.model.SubjectMaterial
import com.duorhythm.app.data.model.SubjectWorkItem
import com.duorhythm.app.databinding.ActivityStudentSubjectDetailBinding
import com.duorhythm.app.databinding.DialogAddSubjectItemBinding
import com.duorhythm.app.databinding.DialogEditSubjectBinding
import com.duorhythm.app.service.ReminderManager
import com.duorhythm.app.util.AnimationUtils
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class StudentSubjectDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SUBJECT_ID = "extra_subject_id"
    }

    private lateinit var binding: ActivityStudentSubjectDetailBinding
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var itemsAdapter: SubjectItemsAdapter
    private lateinit var materialsAdapter: CourseDocumentAdapter

    private var subjectId: Long = -1
    private var currentSubject: StudentSubject? = null
    private var currentFilter: String = "ALL"
    private var allMaterials: List<SubjectMaterial> = emptyList()
    private var currentDocFilter: String = "ALL"
    private var pendingAttachmentPath: String = ""
    private var activeDialogBinding: DialogAddSubjectItemBinding? = null

    private val pickAttachmentLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { saveAttachmentFromUri(it) }
        }

    private val pickMaterialLauncher =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
            if (uris.isNotEmpty()) {
                saveMaterialsFromUris(uris)
            }
        }

    private val pickSyllabusLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { saveSyllabusFromUri(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStudentSubjectDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = DatabaseHelper.getInstance(this)
        subjectId = intent.getLongExtra(EXTRA_SUBJECT_ID, -1)

        setSupportActionBar(binding.toolbarSubject)
        binding.toolbarSubject.setNavigationOnClickListener { finish() }

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            binding.appBarSubject.setPadding(0, systemBars.top, 0, 0)
            insets
        }

        setupRecyclerView()
        setupListeners()
        loadSubject()
    }

    private fun setupRecyclerView() {
        itemsAdapter = SubjectItemsAdapter(
            emptyList(),
            onToggleStatus = { item, newStatus ->
                dbHelper.updateSubjectWorkItemStatus(item.id, newStatus)
                loadItems()
            },
            onDeleteClick = { item ->
                AlertDialog.Builder(this)
                    .setTitle("Delete Assessment")
                    .setMessage("Remove '${item.title}'?")
                    .setPositiveButton("Delete") { _, _ ->
                        dbHelper.deleteSubjectWorkItem(item.id)
                        ReminderManager.cancelExamReminder(this, item.id)
                        loadItems()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )
        binding.rvSubjectItems.layoutManager = LinearLayoutManager(this)
        binding.rvSubjectItems.adapter = itemsAdapter
        AnimationUtils.applySlideUpLayoutAnimation(binding.rvSubjectItems)

        materialsAdapter = CourseDocumentAdapter(
            emptyList(),
            onDeleteClick = { material ->
                AlertDialog.Builder(this)
                    .setTitle("Delete Material")
                    .setMessage("Remove '${material.title}'?")
                    .setPositiveButton("Delete") { _, _ ->
                        dbHelper.deleteSubjectMaterial(material.id)
                        try {
                            File(material.filePath).delete()
                        } catch (_: Exception) {}
                        loadMaterials()
                        Toast.makeText(this, "Document removed", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )
        binding.rvSubjectMaterials.layoutManager = LinearLayoutManager(this)
        binding.rvSubjectMaterials.adapter = materialsAdapter
        AnimationUtils.applySlideUpLayoutAnimation(binding.rvSubjectMaterials)
    }

    private fun setupListeners() {
        binding.chipGroupDocumentFilter.setOnCheckedChangeListener { _, checkedId ->
            currentDocFilter = when (checkedId) {
                R.id.chipDocFilterPdfs -> "PDF"
                R.id.chipDocFilterImages -> "IMG"
                R.id.chipDocFilterDocs -> "DOC"
                else -> "ALL"
            }
            applyDocumentFilter()
        }

        binding.chipGroupSubjectFilter.setOnCheckedChangeListener { _, checkedId ->
            currentFilter = when (checkedId) {
                R.id.chipSubFilterHomework -> "HOMEWORK"
                R.id.chipSubFilterPractical -> "PRACTICAL"
                R.id.chipSubFilterExam -> "EXAM"
                R.id.chipSubFilterAssignment -> "ASSIGNMENT"
                else -> "ALL"
            }
            loadItems()
        }

        binding.btnAddSubjectItem.setOnClickListener { showAddItemDialog() }
        binding.btnEmptyAddSubjectItem.setOnClickListener { showAddItemDialog() }

        binding.btnAddMaterial.setOnClickListener { pickMaterialLauncher.launch("*/*") }

        binding.btnAttachSyllabusFile.setOnClickListener { pickSyllabusLauncher.launch("*/*") }
        binding.btnViewSyllabusFile.setOnClickListener { openSyllabusFile() }

        binding.btnEditSubjectDetails.setOnClickListener { showEditSubjectDialog() }

        AnimationUtils.attachTactileFeedback(
            binding.btnAddSubjectItem,
            binding.btnEmptyAddSubjectItem,
            binding.btnAddMaterial,
            binding.btnAttachSyllabusFile,
            binding.btnViewSyllabusFile,
            binding.btnEditSubjectDetails
        )

        binding.tvDetailSubjectEmail.setOnClickListener {
            val email = currentSubject?.instructorEmail.orEmpty().trim()
            if (email.isNotEmpty()) {
                try {
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:$email")
                    }
                    startActivity(intent)
                } catch (_: Exception) {
                    Toast.makeText(this, "Email: $email", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadSubject() {
        currentSubject = dbHelper.getStudentSubjectById(subjectId)
        if (currentSubject == null) {
            Toast.makeText(this, "Subject not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val subject = currentSubject!!
        binding.toolbarSubject.title = subject.name
        binding.tvDetailSubjectTitle.text = subject.name
        binding.tvDetailSubjectCode.text = subject.code.ifEmpty { "SECTION" }
        binding.tvDetailSubjectCredits.text = "${subject.credits} Credits"

        // Instructor
        if (subject.instructorName.isNotBlank()) {
            binding.tvDetailSubjectInstructor.text = "Instructor: ${subject.instructorName}"
            binding.tvDetailSubjectInstructor.visibility = View.VISIBLE
        } else {
            binding.tvDetailSubjectInstructor.text = "Instructor: Not specified"
        }

        if (subject.instructorEmail.isNotBlank()) {
            binding.tvDetailSubjectEmail.text = subject.instructorEmail
            binding.tvDetailSubjectEmail.visibility = View.VISIBLE
        } else {
            binding.tvDetailSubjectEmail.visibility = View.GONE
        }

        if (subject.instructorPhone.isNotBlank()) {
            binding.tvDetailSubjectPhone.text = subject.instructorPhone
            binding.tvDetailSubjectPhone.visibility = View.VISIBLE
            binding.tvDetailSubjectPhone.setOnClickListener {
                val clean = subject.instructorPhone.replace(Regex("[^0-9+]"), "").trim()
                if (clean.isNotEmpty()) {
                    try {
                        val cleanPhone = Uri.encode(clean)
                        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanPhone")))
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(this, "No dialer app installed on this device", Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) {
                        Toast.makeText(this, "Cannot open dialer for $clean", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else {
            binding.tvDetailSubjectPhone.visibility = View.GONE
        }

        binding.tvDetailSubjectNotes.text = subject.notes.ifEmpty { "No extra notes available." }

        // Syllabus
        val syllabusText = if (subject.syllabusText.isNotBlank()) subject.syllabusText else subject.notes
        binding.tvSyllabusText.text = if (syllabusText.isNotBlank()) syllabusText else "No text syllabus added yet. Tap Edit Course Info or Attach Syllabus PDF."

        if (subject.syllabusPath.isNotBlank() && File(subject.syllabusPath).exists()) {
            val syllabusFile = File(subject.syllabusPath)
            binding.btnViewSyllabusFile.visibility = View.VISIBLE
            binding.btnViewSyllabusFile.text = "View Syllabus (${syllabusFile.name})"
            binding.btnAttachSyllabusFile.text = "Replace Syllabus PDF"
        } else {
            binding.btnViewSyllabusFile.visibility = View.GONE
            binding.btnAttachSyllabusFile.text = "+ Attach Syllabus PDF"
        }

        loadMaterials()
        loadItems()
    }

    private fun loadMaterials() {
        allMaterials = dbHelper.getMaterialsForSubject(subjectId)
        applyDocumentFilter()
    }

    private fun applyDocumentFilter() {
        val filtered = when (currentDocFilter) {
            "PDF" -> allMaterials.filter { getFileTypeCategory(it) == "PDF" }
            "IMG" -> allMaterials.filter { getFileTypeCategory(it) == "IMG" }
            "DOC" -> allMaterials.filter { getFileTypeCategory(it) == "DOC" }
            else -> allMaterials
        }

        materialsAdapter.updateList(filtered)
        if (filtered.isEmpty()) {
            binding.rvSubjectMaterials.visibility = View.GONE
            binding.layoutEmptyMaterials.visibility = View.VISIBLE
            binding.tvEmptyMaterials.text = if (allMaterials.isEmpty()) {
                "No attached PDF documents or note images yet."
            } else {
                "No documents found matching this filter."
            }
        } else {
            binding.rvSubjectMaterials.visibility = View.VISIBLE
            binding.layoutEmptyMaterials.visibility = View.GONE
        }
    }

    private fun getFileTypeCategory(material: SubjectMaterial): String {
        val ext = File(material.filePath).extension.lowercase()
            .ifEmpty { material.title.substringAfterLast('.', "").lowercase() }
        return when {
            ext == "pdf" || (ext.isEmpty() && material.type == "PDF_DOCUMENT") -> "PDF"
            ext in listOf("jpg", "jpeg", "png", "webp", "gif") || (ext.isEmpty() && material.type == "IMAGE_NOTE") -> "IMG"
            ext in listOf("txt", "doc", "docx", "rtf") -> "DOC"
            else -> "OTHER"
        }
    }

    private fun loadItems() {
        val items = dbHelper.getItemsForSubject(subjectId, currentFilter)
        itemsAdapter.updateList(items)
        if (items.isEmpty()) {
            binding.rvSubjectItems.visibility = View.GONE
            binding.layoutEmptySubjectItems.visibility = View.VISIBLE
        } else {
            binding.rvSubjectItems.visibility = View.VISIBLE
            binding.layoutEmptySubjectItems.visibility = View.GONE
        }
    }

    private fun getFileNameFromUri(uri: Uri): String {
        var name = "document_${System.currentTimeMillis()}"
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(uri, null, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    name = cursor.getString(nameIndex) ?: name
                }
            }
        } catch (_: Exception) {
        } finally {
            cursor?.close()
        }
        return name
    }

    private fun saveMaterialsFromUris(uris: List<Uri>) {
        try {
            val materialsDir = File(filesDir, "materials").apply { mkdirs() }
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            var savedCount = 0

            for (uri in uris) {
                try {
                    val originalName = getFileNameFromUri(uri)
                    val uniquePrefix = "${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
                    val destFile = File(materialsDir, "${uniquePrefix}_$originalName")

                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }

                    val ext = originalName.substringAfterLast('.', "").lowercase()
                    val type = when {
                        ext == "pdf" -> "PDF_DOCUMENT"
                        ext in listOf("jpg", "jpeg", "png", "webp", "gif") -> "IMAGE_NOTE"
                        originalName.contains("syllabus", ignoreCase = true) -> "SYLLABUS"
                        else -> "PDF_DOCUMENT"
                    }

                    val material = SubjectMaterial(
                        subjectId = subjectId,
                        title = originalName,
                        type = type,
                        filePath = destFile.absolutePath,
                        createdAt = today
                    )
                    dbHelper.insertSubjectMaterial(material)
                    savedCount++
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (savedCount > 0) {
                loadMaterials()
                val msg = if (savedCount == 1) "Document attached successfully!" else "$savedCount documents attached successfully!"
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to attach documents", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to attach documents: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveMaterialFromUri(uri: Uri) {
        saveMaterialsFromUris(listOf(uri))
    }

    private fun saveSyllabusFromUri(uri: Uri) {
        try {
            val originalName = getFileNameFromUri(uri)
            val syllabusDir = File(filesDir, "syllabus").apply { mkdirs() }
            val destFile = File(syllabusDir, "${System.currentTimeMillis()}_$originalName")

            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            currentSubject?.let { subject ->
                val updated = subject.copy(syllabusPath = destFile.absolutePath)
                dbHelper.updateStudentSubject(updated)
                loadSubject()
                Toast.makeText(this, "Syllabus document attached!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to attach syllabus: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openSyllabusFile() {
        val path = currentSubject?.syllabusPath.orEmpty()
        if (path.isEmpty()) return
        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(this, "Syllabus file not found", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val authority = "$packageName.fileprovider"
            val fileUri = FileProvider.getUriForFile(this, authority, file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(fileUri, if (file.name.endsWith(".pdf", ignoreCase = true)) "application/pdf" else "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Open Syllabus"))
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open syllabus: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showEditSubjectDialog() {
        val subject = currentSubject ?: return
        val dialogBinding = DialogEditSubjectBinding.inflate(layoutInflater)
        dialogBinding.etEditSubjectName.setText(subject.name)
        dialogBinding.etEditSubjectCode.setText(subject.code)
        dialogBinding.etEditSubjectCredits.setText(subject.credits.toString())
        dialogBinding.etEditSubjectInstructor.setText(subject.instructorName)
        dialogBinding.etEditSubjectEmail.setText(subject.instructorEmail)
        dialogBinding.etEditSubjectPhone.setText(subject.instructorPhone)
        dialogBinding.etEditSubjectSyllabus.setText(if (subject.syllabusText.isNotBlank()) subject.syllabusText else subject.notes)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnSaveEditSubject.setOnClickListener {
            val name = dialogBinding.etEditSubjectName.text?.toString()?.trim().orEmpty()
            val code = dialogBinding.etEditSubjectCode.text?.toString()?.trim().orEmpty()
            val credits = dialogBinding.etEditSubjectCredits.text?.toString()?.trim()?.toIntOrNull() ?: 3
            val instructor = dialogBinding.etEditSubjectInstructor.text?.toString()?.trim().orEmpty()
            val email = dialogBinding.etEditSubjectEmail.text?.toString()?.trim().orEmpty()
            val phone = dialogBinding.etEditSubjectPhone.text?.toString()?.trim().orEmpty()
            val syllabus = dialogBinding.etEditSubjectSyllabus.text?.toString()?.trim().orEmpty()

            if (name.isEmpty()) {
                Toast.makeText(this, "Subject name is required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val updated = subject.copy(
                name = name,
                code = code,
                credits = credits,
                instructorName = instructor,
                instructorEmail = email,
                instructorPhone = phone,
                syllabusText = syllabus
            )
            dbHelper.updateStudentSubject(updated)
            dialog.dismiss()
            loadSubject()
            Toast.makeText(this, "Course info updated!", Toast.LENGTH_SHORT).show()
        }

        dialog.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            setDimAmount(0.65f)
        }
        dialog.show()
    }

    private fun saveAttachmentFromUri(uri: Uri) {
        try {
            val originalName = getFileNameFromUri(uri)
            val attachDir = File(filesDir, "attachments").apply { mkdirs() }
            val destFile = File(attachDir, "${System.currentTimeMillis()}_$originalName")

            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            pendingAttachmentPath = destFile.absolutePath
            activeDialogBinding?.tvAttachmentPreview?.text = "Attached: $originalName"
            activeDialogBinding?.tvAttachmentPreview?.setTextColor(0xFF10B981.toInt())
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to copy attachment", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAddItemDialog() {
        val dialogBinding = DialogAddSubjectItemBinding.inflate(layoutInflater)
        activeDialogBinding = dialogBinding
        pendingAttachmentPath = ""

        val types = listOf("HOMEWORK", "PRACTICAL", "EXAM", "ASSIGNMENT")
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, types)
        dialogBinding.spItemType.adapter = spinnerAdapter

        val examTypes = listOf("SEMESTER_FINAL", "MIDTERM", "CLASS_TEST_QUIZ")
        val examTypeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("Semester Final", "Midterm Exam", "Class Test / Quiz"))
        dialogBinding.spExamType.adapter = examTypeAdapter

        dialogBinding.spItemType.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val isExam = types[position] == "EXAM"
                dialogBinding.layoutExamTypeContainer.visibility = if (isExam) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        dialogBinding.etItemStartDate.setText(today)
        dialogBinding.etItemEndDate.setText(today)

        val pickDateListener = View.OnClickListener { view ->
            val calendar = java.util.Calendar.getInstance()
            android.app.DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    val cal = java.util.Calendar.getInstance()
                    cal.set(year, month, dayOfMonth)
                    val formatted = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
                    (view as? android.widget.EditText)?.setText(formatted)
                },
                calendar.get(java.util.Calendar.YEAR),
                calendar.get(java.util.Calendar.MONTH),
                calendar.get(java.util.Calendar.DAY_OF_MONTH)
            ).show()
        }
        dialogBinding.etItemStartDate.setOnClickListener(pickDateListener)
        dialogBinding.etItemEndDate.setOnClickListener(pickDateListener)

        dialogBinding.btnPickAttachment.setOnClickListener {
            pickAttachmentLauncher.launch("*/*")
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setOnDismissListener { activeDialogBinding = null }
            .create()

        dialogBinding.btnSaveSubjectItem.setOnClickListener {
            val title = dialogBinding.etItemTitle.text?.toString()?.trim().orEmpty()
            val desc = dialogBinding.etItemDescription.text?.toString()?.trim().orEmpty()
            val startDate = dialogBinding.etItemStartDate.text?.toString()?.trim().orEmpty()
            val endDate = dialogBinding.etItemEndDate.text?.toString()?.trim().orEmpty()
            val score = dialogBinding.etItemScore.text?.toString()?.trim().orEmpty()
            val itemType = types[dialogBinding.spItemType.selectedItemPosition]
            val examType = if (itemType == "EXAM") examTypes[dialogBinding.spExamType.selectedItemPosition] else ""

            if (title.isEmpty()) {
                Toast.makeText(this, "Title is required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val item = SubjectWorkItem(
                subjectId = subjectId,
                itemType = itemType,
                title = title,
                description = desc,
                startDate = startDate,
                endDate = endDate,
                scoreOrMarks = score,
                status = "PENDING",
                attachmentPath = pendingAttachmentPath,
                examType = examType
            )
            val newId = dbHelper.insertSubjectWorkItem(item)

            if (itemType == "EXAM") {
                ReminderManager.scheduleExamReminder(this, item.copy(id = newId), currentSubject?.name ?: "Subject")
            }

            dialog.dismiss()
            loadItems()
            Toast.makeText(this, "$itemType added to section!", Toast.LENGTH_SHORT).show()
        }

        dialog.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            setDimAmount(0.65f)
        }
        dialog.show()
    }
}

typealias CourseDetailActivity = StudentSubjectDetailActivity
