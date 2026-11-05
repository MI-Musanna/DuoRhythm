package com.duorhythm.app.ui.assessments

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.local.entity.AssessmentEntity
import com.duorhythm.app.data.local.pref.ProfileManager
import com.duorhythm.app.data.model.StudentSubject
import com.duorhythm.app.databinding.DialogScheduleAssessmentBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ScheduleAssessmentDialogFragment : DialogFragment() {

    private var _binding: DialogScheduleAssessmentBinding? = null
    val binding get() = _binding!!

    private var existingAssessment: AssessmentEntity? = null
    private var isCreateMode: Boolean = true
    private var selectedCategory: String = "Semester Final Exam"
    private var courses: List<StudentSubject> = emptyList()
    private var selectedCourseId: Long = 0L
    private var onSavedListener: (() -> Unit)? = null

    // Aliases to match property names in prompt
    val tvDialogTitle get() = binding.tvAssessmentDialogTitle
    val btnSubmitAssessment get() = binding.btnSaveAssessment
    val etTitle get() = binding.etAssessmentTitle
    val etDate get() = binding.etAssessmentDate
    val etTime get() = binding.etAssessmentTime
    val etRoom get() = binding.etAssessmentRoom
    val etInstructor get() = binding.etAssessmentTeacher
    val etSyllabus get() = binding.etAssessmentSyllabus

    fun setOnSavedListener(listener: () -> Unit) {
        onSavedListener = listener
    }

    fun setupEditMode(assessment: AssessmentEntity) {
        existingAssessment = assessment
        isCreateMode = false
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogScheduleAssessmentBinding.inflate(inflater, container, false)
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
        val context = requireContext()
        val db = DatabaseHelper.getInstance(context)
        val profileId = ProfileManager.getActiveProfileId()

        courses = db.getAllStudentSubjects(profileId)
        val courseNames = courses.map { if (it.code.isNotBlank()) "[${it.code}] ${it.name}" else it.name }
        val courseAdapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, courseNames)
        binding.actvAssessmentSubject.setAdapter(courseAdapter)

        binding.actvAssessmentSubject.setOnItemClickListener { _, _, position, _ ->
            val selectedCourse = courses.getOrNull(position)
            if (selectedCourse != null) {
                selectedCourseId = selectedCourse.id
                val instructorName = if (selectedCourse.instructorName.isNotBlank()) selectedCourse.instructorName else selectedCourse.teacherName
                if (isCreateMode || binding.etAssessmentTeacher.text.isNullOrBlank()) {
                    binding.etAssessmentTeacher.setText(instructorName)
                }
            }
        }

        existingAssessment?.let { populateExistingData(it) }

        // Date Picker
        binding.etAssessmentDate.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(context, { _, year, month, day ->
                val chosen = Calendar.getInstance().apply { set(year, month, day) }
                binding.etAssessmentDate.setText(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(chosen.time))
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        // Time Picker
        binding.etAssessmentTime.setOnClickListener {
            val cal = Calendar.getInstance()
            TimePickerDialog(context, { _, hourOfDay, minute ->
                cal.set(Calendar.HOUR_OF_DAY, hourOfDay)
                cal.set(Calendar.MINUTE, minute)
                binding.etAssessmentTime.setText(SimpleDateFormat("hh:mm a", Locale.US).format(cal.time))
            }, 10, 0, false).show()
        }

        binding.btnSaveAssessment.setOnClickListener {
            saveAssessment()
        }
    }

    fun populateExistingData(assessment: AssessmentEntity) {
        binding.tvAssessmentDialogTitle.text = "Edit Assessment"
        binding.btnSaveAssessment.text = "UPDATE ASSESSMENT"
        binding.etAssessmentTitle.setText(assessment.title)
        binding.etAssessmentDate.setText(assessment.dueDate)
        binding.etAssessmentTime.setText(assessment.dueTime)
        binding.etAssessmentRoom.setText(assessment.room)

        val fallbackInstructor = courses.find { it.id == assessment.courseId }?.let {
            if (it.instructorName.isNotBlank()) it.instructorName else it.teacherName
        } ?: ""
        binding.etAssessmentTeacher.setText(if (assessment.instructor.isNotBlank()) assessment.instructor else fallbackInstructor)

        binding.etAssessmentSyllabus.setText(assessment.syllabus)
        selectedCourseId = assessment.courseId
        val course = courses.find { it.id == assessment.courseId }
        if (course != null) {
            val display = if (course.code.isNotBlank()) "[${course.code}] ${course.name}" else course.name
            binding.actvAssessmentSubject.setText(display, false)
        }
    }

    private fun saveAssessment() {
        val title = binding.etAssessmentTitle.text?.toString()?.trim().orEmpty()
        val date = binding.etAssessmentDate.text?.toString()?.trim().orEmpty()
        val time = binding.etAssessmentTime.text?.toString()?.trim().orEmpty()
        val room = binding.etAssessmentRoom.text?.toString()?.trim().orEmpty()
        val syllabus = binding.etAssessmentSyllabus.text?.toString()?.trim().orEmpty()
        val instructorText = binding.etAssessmentTeacher.text?.toString()?.trim().orEmpty()

        if (title.isBlank()) {
            binding.tilAssessmentTitle.error = "Assessment title is required"
            return
        }

        val db = DatabaseHelper.getInstance(requireContext())
        val profileId = ProfileManager.getActiveProfileId()

        val entityToSave = existingAssessment?.copy(
            title = title,
            courseId = selectedCourseId,
            dueDate = date,
            dueTime = time,
            room = room,
            instructor = instructorText,
            syllabus = syllabus
        ) ?: AssessmentEntity(
            profileId = profileId,
            courseId = selectedCourseId,
            title = title,
            type = selectedCategory,
            dueDate = date,
            dueTime = time,
            room = room,
            instructor = instructorText,
            syllabus = syllabus
        )

        if (isCreateMode) {
            db.insertAssessment(entityToSave)
        } else {
            db.updateAssessment(entityToSave)
        }

        Toast.makeText(requireContext(), if (isCreateMode) "Assessment scheduled!" else "Assessment updated!", Toast.LENGTH_SHORT).show()
        onSavedListener?.invoke()
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
