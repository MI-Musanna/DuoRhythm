package com.duorhythm.app.ui.portal

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.duorhythm.app.R
import com.duorhythm.app.ui.common.ManageTagsBottomSheet
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.local.pref.ProfileManager
import com.duorhythm.app.data.model.StudentSubject
import com.duorhythm.app.data.model.SubjectWorkItem
import com.duorhythm.app.databinding.DialogScheduleAssessmentBinding
import com.duorhythm.app.scheduler.AssessmentAlarmScheduler
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import database.entities.AssessmentEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ScheduleAssessmentDialog(
    private val context: Context,
    private val fragmentManager: FragmentManager? = null,
    private val existingItem: SubjectWorkItem? = null,
    private val preselectedCourseId: Long? = null,
    private val defaultType: String? = null,
    private val onSaved: () -> Unit = {}
) {

    fun show() {
        val dbHelper = DatabaseHelper.getInstance(context)
        val profileId = ProfileManager.getActiveProfileId(context)
        val binding = DialogScheduleAssessmentBinding.inflate(LayoutInflater.from(context))

        val isEdit = existingItem != null
        if (isEdit) {
            binding.tvAssessmentDialogTitle.text = "Edit Assessment"
            binding.btnSaveAssessment.text = "Update Assessment"
        }

        // 1. Assessment Types
        val dbCategories = dbHelper.assessmentCategoryDao.getCategoriesByProfile(profileId).map { it.name }
        val types = mutableListOf<String>()
        if (dbCategories.isNotEmpty()) {
            types.addAll(dbCategories)
        } else {
            types.addAll(listOf(
                "Class Test (CT)",
                "Quiz",
                "Midterm Examination",
                "Semester Final Exam",
                "Lab Exam / Practical",
                "Presentation / Viva"
            ))
        }
        types.add("+ Custom Type")
        types.add("⚙️ Manage Categories...")

        val targetType = when {
            existingItem != null -> existingItem.examType
            defaultType != null -> when (defaultType) {
                "SEMESTER_FINAL" -> "Semester Final Exam"
                "MIDTERM" -> "Midterm Examination"
                "CLASS_TEST_QUIZ" -> "Class Test (CT)"
                "QUIZ" -> "Quiz"
                else -> defaultType
            }
            else -> "Semester Final Exam"
        }

        if (targetType.isNotBlank() && !types.contains(targetType)) {
            types.add(types.size - 1, targetType)
        }

        val typeAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, types)
        binding.spAssessmentType.adapter = typeAdapter

        val initialTypeIdx = types.indexOf(targetType).let { if (it >= 0) it else 0 }
        binding.spAssessmentType.setSelection(initialTypeIdx)

        binding.spAssessmentType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (types[position] == "⚙️ Manage Categories...") {
                    binding.spAssessmentType.setSelection(0)
                    val fm = fragmentManager ?: (context as? FragmentActivity)?.supportFragmentManager
                    if (fm != null) {
                        ManageTagsBottomSheet.newInstance(profileId, isClassTypeMode = false) {
                            val refreshed = dbHelper.assessmentCategoryDao.getCategoriesByProfile(profileId).map { it.name }
                            types.clear()
                            if (refreshed.isNotEmpty()) {
                                types.addAll(refreshed)
                            } else {
                                types.addAll(listOf(
                                    "Class Test (CT)",
                                    "Quiz",
                                    "Midterm Examination",
                                    "Semester Final Exam",
                                    "Lab Exam / Practical",
                                    "Presentation / Viva"
                                ))
                            }
                            types.add("+ Custom Type")
                            types.add("⚙️ Manage Categories...")
                            typeAdapter.notifyDataSetChanged()
                        }.show(fm, ManageTagsBottomSheet.TAG)
                    }
                } else if (types[position] == "+ Custom Type") {
                    val input = EditText(context).apply {
                        hint = "e.g. Assessment Defense"
                        setPadding(48, 32, 48, 32)
                    }
                    MaterialAlertDialogBuilder(context)
                        .setTitle("Add Custom Assessment Type")
                        .setView(input)
                        .setPositiveButton("Add") { _, _ ->
                            val customName = input.text.toString().trim()
                            if (customName.isNotEmpty()) {
                                dbHelper.assessmentCategoryDao.insertCategory(
                                    database.entities.AssessmentCategoryEntity(
                                        profileId = profileId,
                                        name = customName,
                                        isDefault = false
                                    )
                                )
                                types.add(types.size - 1, customName)
                                typeAdapter.notifyDataSetChanged()
                                binding.spAssessmentType.setSelection(types.indexOf(customName))
                            } else {
                                binding.spAssessmentType.setSelection(0)
                            }
                        }
                        .setNegativeButton("Cancel") { _, _ ->
                            binding.spAssessmentType.setSelection(0)
                        }
                        .show()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 2. Enrolled Courses Dropdown (Auto-Fill)
        val enrolledCourses = dbHelper.getAllStudentSubjects(profileId)
        val courseNames = enrolledCourses.map { course ->
            if (course.code.isNotBlank()) "[${course.code}] ${course.name}" else course.name
        }
        val coursesAdapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, courseNames)
        binding.actvAssessmentSubject.setAdapter(coursesAdapter)

        var selectedCourse: StudentSubject? = null
        var selectedAttachmentUri: String? = null
        var selectedAttachmentName: String? = null

        fun requireContext(): Context = context

        fun getFileNameFromUri(ctx: Context, uri: Uri): String {
            var name = "document_${System.currentTimeMillis()}"
            var cursor: android.database.Cursor? = null
            try {
                cursor = ctx.contentResolver.query(uri, null, null, null, null)
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

        val targetCourseId = existingItem?.subjectId ?: preselectedCourseId
        if (targetCourseId != null && targetCourseId > 0) {
            selectedCourse = enrolledCourses.find { it.id == targetCourseId }
            selectedCourse?.let { course ->
                val display = if (course.code.isNotBlank()) "[${course.code}] ${course.name}" else course.name
                binding.actvAssessmentSubject.setText(display, false)
            }
        }

        binding.actvAssessmentSubject.setOnItemClickListener { _, _, position, _ ->
            val course = enrolledCourses.getOrNull(position)
            if (course != null) {
                selectedCourse = course
                val selectedTypeStr = binding.spAssessmentType.selectedItem?.toString() ?: "Exam"
                if (binding.etAssessmentTitle.text.isNullOrBlank()) {
                    binding.etAssessmentTitle.setText("${course.name} - $selectedTypeStr")
                }
                if (!course.teacherName.isNullOrBlank()) {
                    binding.etAssessmentTeacher.setText(course.teacherName)
                }
                // Keep binding.etAssessmentRoom untouched so user manually enters room hall number.
            }
        }

        // 3. Pre-fill existing data if editing
        if (existingItem != null) {
            binding.etAssessmentTitle.setText(existingItem.title)
            binding.etAssessmentDate.setText(existingItem.endDate.ifBlank { existingItem.startDate })
            binding.etAssessmentTime.setText(existingItem.scoreOrMarks)
            binding.etAssessmentRoom.setText(existingItem.attachmentPath)
            binding.etAssessmentSyllabus.setText(existingItem.description)
            if (!existingItem.attachmentUri.isNullOrBlank()) {
                selectedAttachmentUri = existingItem.attachmentUri
                selectedAttachmentName = existingItem.attachmentName ?: getFileNameFromUri(context, Uri.parse(existingItem.attachmentUri))
                binding.layoutAttachmentPreview.visibility = View.VISIBLE
                binding.tvAttachmentFileName.text = selectedAttachmentName
            }
        } else {
            val cal = Calendar.getInstance()
            binding.etAssessmentDate.setText(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time))
            binding.etAssessmentTime.setText("10:00 AM")
        }

        // File Picker Launcher with Persistable URI Guard
        val activity = context as? androidx.activity.ComponentActivity
        val filePickerLauncher = activity?.activityResultRegistry?.register(
            "assessment_file_picker_${System.currentTimeMillis()}",
            ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            uri?.let { sourceUri ->
                try {
                    val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    requireContext().contentResolver.takePersistableUriPermission(sourceUri, takeFlags)
                } catch (e: SecurityException) {
                    // Fallback for providers that don't support persistable permissions
                }
                val fileName = getFileNameFromUri(requireContext(), sourceUri)
                selectedAttachmentUri = sourceUri.toString()
                selectedAttachmentName = fileName
                binding.layoutAttachmentPreview.visibility = View.VISIBLE
                binding.tvAttachmentFileName.text = fileName
            }
        }

        binding.btnAttachAssessmentFile.setOnClickListener {
            filePickerLauncher?.launch(arrayOf("*/*"))
        }

        binding.btnRemoveAttachment.setOnClickListener {
            selectedAttachmentUri = null
            selectedAttachmentName = null
            binding.layoutAttachmentPreview.visibility = View.GONE
            binding.tvAttachmentFileName.text = ""
        }

        // 4. Date Picker
        binding.etAssessmentDate.setOnClickListener {
            val cal = Calendar.getInstance()
            val existingDate = binding.etAssessmentDate.text?.toString()?.trim()
            if (!existingDate.isNullOrEmpty()) {
                try {
                    val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(existingDate)
                    if (parsed != null) cal.time = parsed
                } catch (_: Exception) {}
            }

            DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    val selectedCal = Calendar.getInstance().apply {
                        set(year, month, dayOfMonth)
                    }
                    val dateFormatted = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(selectedCal.time)
                    binding.etAssessmentDate.setText(dateFormatted)
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        // 5. Time Picker
        binding.etAssessmentTime.setOnClickListener {
            if (fragmentManager != null) {
                val picker = MaterialTimePicker.Builder()
                    .setTimeFormat(TimeFormat.CLOCK_12H)
                    .setHour(10)
                    .setMinute(0)
                    .setTitleText("Select Assessment Time")
                    .build()

                picker.addOnPositiveButtonClickListener {
                    val cal = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, picker.hour)
                        set(Calendar.MINUTE, picker.minute)
                    }
                    val timeStr = SimpleDateFormat("hh:mm a", Locale.US).format(cal.time)
                    binding.etAssessmentTime.setText(timeStr)
                }
                picker.show(fragmentManager, "ASSESSMENT_TIME_PICKER")
            } else {
                val cal = Calendar.getInstance()
                TimePickerDialog(
                    context,
                    { _, hourOfDay, minute ->
                        cal.set(Calendar.HOUR_OF_DAY, hourOfDay)
                        cal.set(Calendar.MINUTE, minute)
                        val timeStr = SimpleDateFormat("hh:mm a", Locale.US).format(cal.time)
                        binding.etAssessmentTime.setText(timeStr)
                    },
                    10,
                    0,
                    false
                ).show()
            }
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setView(binding.root)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        // 6. Save Button
        binding.btnSaveAssessment.setOnClickListener {
            val title = binding.etAssessmentTitle.text?.toString()?.trim() ?: ""
            val date = binding.etAssessmentDate.text?.toString()?.trim() ?: ""
            val time = binding.etAssessmentTime.text?.toString()?.trim() ?: ""
            val room = binding.etAssessmentRoom.text?.toString()?.trim() ?: ""
            val teacher = binding.etAssessmentTeacher.text?.toString()?.trim() ?: ""
            val syllabus = binding.etAssessmentSyllabus.text?.toString()?.trim() ?: ""
            val chosenType = binding.spAssessmentType.selectedItem?.toString() ?: "Semester Final Exam"

            if (title.isBlank()) {
                binding.tilAssessmentTitle.error = "Assessment title is required"
                return@setOnClickListener
            }
            binding.tilAssessmentTitle.error = null

            if (date.isBlank()) {
                Toast.makeText(context, "Please select an assessment date", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // If selectedCourse was not chosen via dropdown click, check by text
            var course = selectedCourse
            if (course == null) {
                val enteredCourseText = binding.actvAssessmentSubject.text?.toString()?.trim() ?: ""
                course = enrolledCourses.find {
                    "[${it.code}] ${it.name}".equals(enteredCourseText, ignoreCase = true) ||
                            it.name.equals(enteredCourseText, ignoreCase = true) ||
                            it.code.equals(enteredCourseText, ignoreCase = true)
                }
            }

            val subjectId = course?.id ?: existingItem?.subjectId ?: 0L
            val subjectName = course?.name ?: ""
            val courseCode = course?.code ?: ""

            if (isEdit && existingItem != null) {
                val updatedItem = existingItem.copy(
                    subjectId = subjectId,
                    title = title,
                    description = syllabus,
                    startDate = date,
                    endDate = date,
                    scoreOrMarks = time,
                    attachmentPath = room,
                    examType = chosenType,
                    attachmentUri = selectedAttachmentUri,
                    attachmentName = selectedAttachmentName
                )
                dbHelper.updateSubjectWorkItem(updatedItem)

                val entity = AssessmentEntity.fromSubjectWorkItem(
                    updatedItem,
                    subjectName = subjectName,
                    courseCode = courseCode,
                    teacher = teacher
                )
                AssessmentAlarmScheduler.cancelAlarmsForAssessment(context, existingItem.id)
                AssessmentAlarmScheduler.scheduleAssessmentAlarms(context, entity)

                Toast.makeText(context, "Assessment updated successfully!", Toast.LENGTH_SHORT).show()
            } else {
                val assessmentEntity = AssessmentEntity(
                    profileId = profileId,
                    subjectId = subjectId,
                    subjectName = subjectName,
                    courseCode = courseCode,
                    type = chosenType,
                    title = title,
                    date = date,
                    time = time,
                    room = room,
                    syllabus = syllabus,
                    teacher = teacher,
                    status = "PENDING",
                    attachmentUri = selectedAttachmentUri,
                    attachmentName = selectedAttachmentName
                )
                val newId = dbHelper.insertSubjectWorkItem(assessmentEntity.toSubjectWorkItem())
                val finalEntity = assessmentEntity.copy(id = newId)
                AssessmentAlarmScheduler.scheduleAssessmentAlarms(context, finalEntity)

                Toast.makeText(context, "Assessment scheduled with smart notifications!", Toast.LENGTH_SHORT).show()
            }

            dialog.dismiss()
            onSaved()
        }

        dialog.setOnDismissListener {
            try {
                filePickerLauncher?.unregister()
            } catch (_: Exception) {}
        }

        dialog.show()
    }
}
