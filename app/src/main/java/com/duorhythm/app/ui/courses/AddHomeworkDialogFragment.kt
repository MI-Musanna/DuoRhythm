package com.duorhythm.app.ui.courses

import android.app.DatePickerDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.duorhythm.app.R
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.model.SubjectWorkItem
import com.duorhythm.app.databinding.DialogAddHomeworkBinding
import com.duorhythm.app.util.ProfileManager
import com.google.android.material.chip.Chip
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AddHomeworkDialogFragment : DialogFragment() {

    private var _binding: DialogAddHomeworkBinding? = null
    private val binding get() = _binding!!
    private var courseId: Long = 0L
    private var selectedCategory: String = "Assignment"
    private var onSavedListener: (() -> Unit)? = null

    fun setCourseId(id: Long) {
        this.courseId = id
    }

    fun setOnSavedListener(listener: () -> Unit) {
        this.onSavedListener = listener
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogAddHomeworkBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout((resources.displayMetrics.widthPixels * 0.92).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        binding.etItemStartDate.setText(today)
        binding.etItemEndDate.setText(today)

        val pickDateListener = View.OnClickListener { v ->
            val calendar = Calendar.getInstance()
            DatePickerDialog(
                requireContext(),
                { _, year, month, dayOfMonth ->
                    val cal = Calendar.getInstance()
                    cal.set(year, month, dayOfMonth)
                    val formatted = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
                    (v as? EditText)?.setText(formatted)
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
        binding.etItemStartDate.setOnClickListener(pickDateListener)
        binding.etItemEndDate.setOnClickListener(pickDateListener)

        loadDynamicCategories(courseId)

        binding.btnSaveSubjectItem.setOnClickListener {
            val title = binding.etItemTitle.text?.toString()?.trim().orEmpty()
            val desc = binding.etItemDescription.text?.toString()?.trim().orEmpty()
            val startDate = binding.etItemStartDate.text?.toString()?.trim().orEmpty()
            val endDate = binding.etItemEndDate.text?.toString()?.trim().orEmpty()
            val score = binding.etItemScore.text?.toString()?.trim().orEmpty()

            if (title.isEmpty()) {
                binding.tilItemTitle.error = "Title is required"
                return@setOnClickListener
            }
            binding.tilItemTitle.error = null

            val itemType = if (selectedCategory.contains("Exam", ignoreCase = true) ||
                selectedCategory.contains("Test", ignoreCase = true) ||
                selectedCategory.contains("Quiz", ignoreCase = true)) "EXAM" else "HOMEWORK"

            val item = SubjectWorkItem(
                subjectId = courseId,
                itemType = itemType,
                title = title,
                description = desc,
                startDate = startDate,
                endDate = endDate,
                scoreOrMarks = score,
                status = "PENDING",
                examType = selectedCategory
            )

            val db = DatabaseHelper.getInstance(requireContext())
            db.insertSubjectWorkItem(item)

            Toast.makeText(requireContext(), "$selectedCategory added to section!", Toast.LENGTH_SHORT).show()
            dismiss()
            onSavedListener?.invoke()
        }
    }

    private fun loadDynamicCategories(courseId: Long) {
        val db = DatabaseHelper.getInstance(requireContext())
        val profileId = ProfileManager.getActiveProfileId()
        val categories = db.getAssessmentCategories(profileId).toMutableList()

        binding.chipGroupCourseCategories.removeAllViews()

        for (cat in categories) {
            val chip = Chip(requireContext()).apply {
                text = cat
                isCheckable = true
                chipStrokeWidth = 2f
                chipStrokeColor = android.content.res.ColorStateList.valueOf(Color.parseColor("#1F2F4D"))
                setChipBackgroundColorResource(android.R.color.transparent)
                setTextColor(Color.WHITE)
                checkedIconTint = android.content.res.ColorStateList.valueOf(Color.parseColor("#00E5FF"))
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) selectedCategory = cat
                }
                setOnLongClickListener {
                    // Allow deleting custom category
                    AlertDialog.Builder(requireContext())
                        .setTitle("Delete Category")
                        .setMessage("Do you want to delete '$cat'?")
                        .setPositiveButton("Delete") { _, _ ->
                            db.deleteAssessmentCategory(profileId, cat)
                            loadDynamicCategories(courseId)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                    true
                }
            }
            binding.chipGroupCourseCategories.addView(chip)
        }

        // Add "+ Add Category" Chip
        val addChip = Chip(requireContext()).apply {
            text = "+ Add"
            chipStrokeWidth = 2f
            chipStrokeColor = android.content.res.ColorStateList.valueOf(Color.parseColor("#00E5FF"))
            setTextColor(Color.parseColor("#00E5FF"))
            setChipBackgroundColorResource(android.R.color.transparent)
            setOnClickListener {
                val input = EditText(requireContext()).apply {
                    hint = "Category name (e.g. Lab Viva, Project)"
                    setTextColor(Color.WHITE)
                }
                val frame = FrameLayout(requireContext()).apply {
                    setPadding(50, 20, 50, 0)
                    addView(input)
                }
                AlertDialog.Builder(requireContext())
                    .setTitle("New Category")
                    .setView(frame)
                    .setPositiveButton("Create") { _, _ ->
                        val newName = input.text.toString().trim()
                        if (newName.isNotBlank()) {
                            db.addAssessmentCategory(profileId, newName)
                            selectedCategory = newName
                            loadDynamicCategories(courseId)
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
        binding.chipGroupCourseCategories.addView(addChip)

        // Select first chip by default
        if (binding.chipGroupCourseCategories.childCount > 1) {
            val first = binding.chipGroupCourseCategories.getChildAt(0) as? Chip
            first?.isChecked = true
            selectedCategory = first?.text?.toString() ?: "Assignment"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(courseId: Long, onSaved: (() -> Unit)? = null): AddHomeworkDialogFragment {
            return AddHomeworkDialogFragment().apply {
                setCourseId(courseId)
                if (onSaved != null) setOnSavedListener(onSaved)
            }
        }
    }
}
