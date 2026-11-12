package com.duorhythm.app.ui.courses

import android.app.DatePickerDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.duorhythm.app.R
import com.duorhythm.app.data.local.db.DatabaseHelper
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
    private var selectedCategory: String = ""
    private var courseId: Long = 0L
    private var onSavedListener: (() -> Unit)? = null

    companion object {
        fun newInstance(courseId: Long, onSaved: (() -> Unit)? = null): AddHomeworkDialogFragment {
            return AddHomeworkDialogFragment().apply {
                arguments = Bundle().apply { putLong("ARG_COURSE_ID", courseId) }
                this.onSavedListener = onSaved
            }
        }
    }

    fun setCourseId(id: Long) {
        this.courseId = id
    }

    fun setOnSavedListener(listener: () -> Unit) {
        this.onSavedListener = listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        courseId = arguments?.getLong("ARG_COURSE_ID", 0L) ?: courseId
        setStyle(STYLE_NO_TITLE, R.style.CustomDialogTheme)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogAddHomeworkBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        com.duorhythm.app.util.ViewAnimationExtensions.applySpringToAllButtons(view)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        if (binding.etStartDate.text.isNullOrBlank()) binding.etStartDate.setText(today)
        if (binding.etDueDate.text.isNullOrBlank()) binding.etDueDate.setText(today)

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
        binding.etStartDate.setOnClickListener(pickDateListener)
        binding.etDueDate.setOnClickListener(pickDateListener)

        loadDynamicCategories()

        binding.btnSaveItem.setOnClickListener {
            saveAssessmentEntry()
        }
    }

    private fun loadDynamicCategories() {
        val context = requireContext()
        val db = DatabaseHelper.getInstance(context)
        val profileId = ProfileManager.getActiveProfileId()
        val categories = db.getAssessmentCategories(profileId).toMutableList()

        binding.chipGroupCourseCategories.removeAllViews()

        for (cat in categories) {
            val chip = Chip(context).apply {
                text = cat
                isCheckable = true
                isChecked = (cat == selectedCategory)
                chipStrokeWidth = 2f
                chipStrokeColor = ColorStateList.valueOf(Color.parseColor(if (cat == selectedCategory) "#00E5FF" else "#1F2F4D"))
                setChipBackgroundColorResource(android.R.color.transparent)
                setTextColor(Color.WHITE)
                checkedIconTint = ColorStateList.valueOf(Color.parseColor("#00E5FF"))

                // Single tap selects category
                setOnClickListener {
                    selectedCategory = cat
                    loadDynamicCategories()
                }

                // Long press opens explicit Action Menu: Edit or Delete
                setOnLongClickListener {
                    showCategoryActionDialog(cat)
                    true
                }
            }
            binding.chipGroupCourseCategories.addView(chip)
        }

        // Add "+ Add" Chip
        val addChip = Chip(context).apply {
            text = "+ Add"
            isCheckable = false
            chipStrokeWidth = 2f
            chipStrokeColor = ColorStateList.valueOf(Color.parseColor("#00E5FF"))
            setTextColor(Color.parseColor("#00E5FF"))
            setChipBackgroundColorResource(android.R.color.transparent)
            setOnClickListener {
                showAddCategoryDialog()
            }
        }
        binding.chipGroupCourseCategories.addView(addChip)

        // Add "⚙️ Manage" Chip for 1-tap Edit/Delete access
        val manageChip = Chip(context).apply {
            text = "⚙️"
            isCheckable = false
            chipStrokeWidth = 2f
            chipStrokeColor = ColorStateList.valueOf(Color.parseColor("#64748B"))
            setTextColor(Color.parseColor("#94A3B8"))
            setChipBackgroundColorResource(android.R.color.transparent)
            setOnClickListener {
                showManageCategoriesOverviewDialog(categories)
            }
        }
        binding.chipGroupCourseCategories.addView(manageChip)

        // Select first item if nothing selected
        if (selectedCategory.isBlank() && categories.isNotEmpty()) {
            selectedCategory = categories[0]
            (binding.chipGroupCourseCategories.getChildAt(0) as? Chip)?.isChecked = true
        }
    }

    private fun showCategoryActionDialog(categoryName: String) {
        val options = arrayOf("Select '$categoryName'", "✏️ Rename / Edit", "🗑️ Delete")
        AlertDialog.Builder(requireContext())
            .setTitle("Manage Category: $categoryName")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        selectedCategory = categoryName
                        loadDynamicCategories()
                    }
                    1 -> showEditCategoryDialog(categoryName)
                    2 -> confirmDeleteCategory(categoryName)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showManageCategoriesOverviewDialog(categories: List<String>) {
        if (categories.isEmpty()) {
            Toast.makeText(requireContext(), "No custom categories found. Tap '+ Add' to create one.", Toast.LENGTH_SHORT).show()
            return
        }
        val items = categories.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("Select Category to Edit / Delete")
            .setItems(items) { _, which ->
                showCategoryActionDialog(items[which])
            }
            .setPositiveButton("+ Add New") { _, _ -> showAddCategoryDialog() }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showAddCategoryDialog() {
        val input = EditText(requireContext()).apply {
            hint = "Category name (e.g. Lab Report)"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#64748B"))
        }
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 10)
            addView(input)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("New Category")
            .setView(container)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotBlank()) {
                    val db = DatabaseHelper.getInstance(requireContext())
                    val profileId = ProfileManager.getActiveProfileId()
                    db.addAssessmentCategory(profileId, name)
                    selectedCategory = name
                    loadDynamicCategories()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditCategoryDialog(oldName: String) {
        val input = EditText(requireContext()).apply {
            setText(oldName)
            setSelection(oldName.length)
            setTextColor(Color.WHITE)
        }
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 10)
            addView(input)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Rename Category")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotBlank() && newName != oldName) {
                    val db = DatabaseHelper.getInstance(requireContext())
                    val profileId = ProfileManager.getActiveProfileId()
                    db.updateAssessmentCategory(profileId, oldName, newName)
                    if (selectedCategory == oldName) selectedCategory = newName
                    loadDynamicCategories()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDeleteCategory(categoryName: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Category")
            .setMessage("Are you sure you want to delete '$categoryName'?")
            .setPositiveButton("Delete") { _, _ ->
                val db = DatabaseHelper.getInstance(requireContext())
                val profileId = ProfileManager.getActiveProfileId()
                db.deleteAssessmentCategory(profileId, categoryName)
                if (selectedCategory == categoryName) selectedCategory = ""
                loadDynamicCategories()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveAssessmentEntry() {
        val title = binding.etTitle.text.toString().trim()
        if (title.isBlank()) {
            binding.etTitle.error = "Title required"
            return
        }
        if (selectedCategory.isBlank()) {
            Toast.makeText(requireContext(), "Please select a category", Toast.LENGTH_SHORT).show()
            return
        }

        val desc = binding.etDescription.text.toString().trim()
        val startDate = binding.etStartDate.text.toString().trim()
        val dueDate = binding.etDueDate.text.toString().trim()
        val score = binding.etScore.text.toString().trim()

        val db = DatabaseHelper.getInstance(requireContext())
        val profileId = ProfileManager.getActiveProfileId()

        // Insert assessment with clean category
        db.insertAssessment(
            profileId = profileId,
            courseId = courseId,
            title = title,
            type = selectedCategory,
            description = desc,
            startDate = startDate,
            dueDate = dueDate,
            marks = score,
            status = "PENDING"
        )

        dismiss()
        onSavedListener?.invoke()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
