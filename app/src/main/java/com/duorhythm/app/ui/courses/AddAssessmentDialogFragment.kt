package com.duorhythm.app.ui.courses

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.model.StudentSubject
import com.duorhythm.app.databinding.DialogAddAssessmentBinding
import com.duorhythm.app.scheduler.AssessmentAlarmScheduler
import com.duorhythm.app.util.ProfileManager
import com.google.android.material.chip.Chip
import database.entities.AssessmentEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AddAssessmentDialogFragment : DialogFragment() {

    private var _binding: DialogAddAssessmentBinding? = null
    private val binding get() = _binding!!

    private var selectedCategory: String = "Class Test"
    private var enrolledCourses: List<StudentSubject> = emptyList()
    private var selectedCourse: StudentSubject? = null
    private var onSavedListener: (() -> Unit)? = null

    fun setOnSavedListener(listener: () -> Unit) {
        onSavedListener = listener
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogAddAssessmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (resources.displayMetrics.widthPixels * 0.92).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        com.duorhythm.app.util.ViewAnimationExtensions.applySpringToAllButtons(view)
        val context = requireContext()
        val db = DatabaseHelper.getInstance(context)
        val profileId = ProfileManager.getActiveProfileId()

        // 1. Setup enrolled courses
        enrolledCourses = db.getAllStudentSubjects(profileId)
        val courseNames = enrolledCourses.map { "[${it.code}] ${it.name}" }
        val courseAdapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, courseNames)
        binding.actvAssessmentSubject.setAdapter(courseAdapter)
        binding.actvAssessmentSubject.setOnItemClickListener { _, _, position, _ ->
            selectedCourse = enrolledCourses.getOrNull(position)
        }

        // 2. Setup dynamic categories & inline "+ Add" chip
        fun populateCategories() {
            binding.chipGroupAssessmentCategory.removeAllViews()
            val categories = db.getAssessmentCategories(profileId)
            if (categories.isNotEmpty() && !categories.contains(selectedCategory)) {
                selectedCategory = categories.first()
            }

            for (cat in categories) {
                val chip = Chip(context).apply {
                    text = cat
                    isCheckable = true
                    isChecked = (cat == selectedCategory)
                    setChipBackgroundColorResource(android.R.color.transparent)
                    chipStrokeWidth = 2f
                    chipStrokeColor = android.content.res.ColorStateList.valueOf(Color.parseColor("#1F2F4D"))
                    setTextColor(Color.WHITE)
                    checkedIconTint = android.content.res.ColorStateList.valueOf(Color.parseColor("#00E5FF"))

                    setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) {
                            selectedCategory = cat
                        }
                    }

                    setOnLongClickListener {
                        AlertDialog.Builder(context)
                            .setTitle("Delete Category")
                            .setMessage("Delete category '$cat'?")
                            .setPositiveButton("Delete") { _, _ ->
                                db.deleteAssessmentCategory(profileId, cat)
                                populateCategories()
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                        true
                    }
                }
                binding.chipGroupAssessmentCategory.addView(chip)
            }

            // Inline "+ Add" chip
            val addChip = Chip(context).apply {
                text = "+ Add"
                isCheckable = false
                setChipBackgroundColorResource(android.R.color.transparent)
                chipStrokeWidth = 2f
                chipStrokeColor = android.content.res.ColorStateList.valueOf(Color.parseColor("#00E5FF"))
                setTextColor(Color.parseColor("#00E5FF"))

                setOnClickListener {
                    val input = EditText(context).apply {
                        hint = "Category name (e.g. Lab Report)"
                        setSingleLine()
                    }
                    val frame = android.widget.FrameLayout(context).apply {
                        setPadding(50, 20, 50, 0)
                        addView(input)
                    }

                    AlertDialog.Builder(context)
                        .setTitle("Add Assessment Category")
                        .setView(frame)
                        .setPositiveButton("Add") { _, _ ->
                            val name = input.text.toString().trim()
                            if (name.isNotEmpty()) {
                                db.addAssessmentCategory(profileId, name)
                                selectedCategory = name
                                populateCategories()
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
            binding.chipGroupAssessmentCategory.addView(addChip)
        }

        populateCategories()

        // 3. Date & Time picker
        binding.etAssessmentDate.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(
                context,
                { _, year, month, day ->
                    val chosen = Calendar.getInstance().apply { set(year, month, day) }
                    binding.etAssessmentDate.setText(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(chosen.time))
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        binding.etAssessmentTime.setOnClickListener {
            val cal = Calendar.getInstance()
            TimePickerDialog(
                context,
                { _, hourOfDay, minute ->
                    val chosen = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, hourOfDay)
                        set(Calendar.MINUTE, minute)
                    }
                    binding.etAssessmentTime.setText(SimpleDateFormat("hh:mm a", Locale.US).format(chosen.time))
                },
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                false
            ).show()
        }

        // 4. Buttons
        binding.btnCancelAssessment.setOnClickListener {
            dismiss()
        }

        binding.btnSaveAssessment.setOnClickListener {
            val title = binding.etAssessmentTitle.text?.toString()?.trim().orEmpty()
            val date = binding.etAssessmentDate.text?.toString()?.trim().orEmpty()
            val time = binding.etAssessmentTime.text?.toString()?.trim().orEmpty()
            val room = binding.etAssessmentRoom.text?.toString()?.trim().orEmpty()
            val syllabus = binding.etAssessmentSyllabus.text?.toString()?.trim().orEmpty()

            if (title.isBlank()) {
                binding.tilAssessmentTitle.error = "Assessment title is required"
                return@setOnClickListener
            }
            binding.tilAssessmentTitle.error = null

            if (date.isBlank()) {
                Toast.makeText(context, "Please select an assessment date", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            var course = selectedCourse
            if (course == null) {
                val enteredCourseText = binding.actvAssessmentSubject.text?.toString()?.trim() ?: ""
                course = enrolledCourses.find {
                    "[${it.code}] ${it.name}".equals(enteredCourseText, ignoreCase = true) ||
                            it.name.equals(enteredCourseText, ignoreCase = true) ||
                            it.code.equals(enteredCourseText, ignoreCase = true)
                }
            }

            val subjectId = course?.id ?: 0L
            val subjectName = course?.name ?: ""
            val courseCode = course?.code ?: ""

            val entity = AssessmentEntity(
                profileId = profileId,
                subjectId = subjectId,
                subjectName = subjectName,
                courseCode = courseCode,
                type = selectedCategory,
                title = title,
                date = date,
                time = time,
                room = room,
                syllabus = syllabus,
                teacher = "",
                status = "PENDING"
            )

            val newId = db.insertSubjectWorkItem(entity.toSubjectWorkItem())
            val savedEntity = entity.copy(id = newId)
            AssessmentAlarmScheduler.scheduleAssessmentAlarms(context, savedEntity)

            Toast.makeText(context, "Assessment scheduled successfully!", Toast.LENGTH_SHORT).show()
            dismiss()
            onSavedListener?.invoke()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(onSaved: (() -> Unit)? = null): AddAssessmentDialogFragment {
            return AddAssessmentDialogFragment().apply {
                if (onSaved != null) setOnSavedListener(onSaved)
            }
        }

        fun show(fragmentManager: androidx.fragment.app.FragmentManager, onDismiss: () -> Unit = {}) {
            val dialog = newInstance(onDismiss)
            dialog.show(fragmentManager, "ADD_ASSESSMENT_DIALOG")
        }
    }
}
