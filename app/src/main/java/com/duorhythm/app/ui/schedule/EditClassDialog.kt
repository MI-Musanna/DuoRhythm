package com.duorhythm.app.ui.schedule

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.FragmentManager
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.local.pref.ProfileManager
import com.duorhythm.app.data.model.*
import com.duorhythm.app.databinding.DialogAddClassBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class EditClassDialog(
    private val context: Context,
    private val fragmentManager: FragmentManager?,
    private val classItem: ClassScheduleItem? = null,
    private val defaultDayOfWeek: Int = 1,
    private val onSave: (ClassScheduleItem) -> Unit
) {

    fun show() {
        val dbHelper = DatabaseHelper.getInstance(context)
        val activeProfileId = ProfileManager.getActiveProfileId(context)
        val dialogBinding = DialogAddClassBinding.inflate(LayoutInflater.from(context))

        val isEdit = classItem != null
        dialogBinding.tvAddClassTitle.text = if (isEdit) "Edit Class Schedule" else "Add Class Routine"
        dialogBinding.btnSaveClass.text = if (isEdit) "Update Class" else "Save Class"

        val days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        val daysAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, days)
        dialogBinding.spClassDay.adapter = daysAdapter
        val initialDay = if (isEdit) (classItem!!.dayOfWeek - 1).coerceIn(0, days.size - 1) else (defaultDayOfWeek - 1).coerceIn(0, days.size - 1)
        dialogBinding.spClassDay.setSelection(initialDay)

        var typeList = mutableListOf<String>()
        var typeAdapter: ArrayAdapter<String>? = null

        fun refreshTypes(selectedType: String? = null) {
            val entities = dbHelper.classTypeDao.getTypesByProfile(activeProfileId)
            typeList = entities.map { it.name }.toMutableList()
            typeList.add("+ Manage / Add Type")
            typeAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, typeList)
            dialogBinding.spClassType.adapter = typeAdapter

            val target = selectedType ?: classItem?.classType ?: "Lecture"
            val idx = typeList.indexOfFirst { it.equals(target, ignoreCase = true) }
            if (idx >= 0) {
                dialogBinding.spClassType.setSelection(idx)
            }
        }

        refreshTypes()

        var previousSelection = dialogBinding.spClassType.selectedItemPosition
        dialogBinding.spClassType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                if (position == typeList.size - 1) { // "+ Manage / Add Type"
                    dialogBinding.spClassType.setSelection(previousSelection)
                    ManageClassTypesDialog(context, activeProfileId) {
                        refreshTypes(typeList.getOrNull(previousSelection))
                    }.show()
                } else {
                    previousSelection = position
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // AutoComplete enrolled subjects
        val enrolledSubjects = dbHelper.getAllStudentSubjects(activeProfileId)
        if (enrolledSubjects.isNotEmpty()) {
            val subjectNames = enrolledSubjects.map { it.name }.distinct()
            val subjectAdapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, subjectNames)
            dialogBinding.actvSubjectName.setAdapter(subjectAdapter)
            dialogBinding.actvSubjectName.setOnItemClickListener { _, _, position, _ ->
                val selectedName = subjectAdapter.getItem(position) ?: return@setOnItemClickListener
                val matchedSubject = enrolledSubjects.find { it.name.equals(selectedName, ignoreCase = true) }
                if (matchedSubject != null && matchedSubject.instructor.isNotBlank()) {
                    dialogBinding.etClassTeacher.setText(matchedSubject.instructor)
                }
            }
            dialogBinding.actvSubjectName.setOnClickListener {
                dialogBinding.actvSubjectName.showDropDown()
            }
        }

        if (isEdit) {
            val item = classItem!!
            dialogBinding.actvSubjectName.setText(item.subjectName)
            dialogBinding.etClassRoom.setText(item.room)
            dialogBinding.etClassTeacher.setText(item.teacher)
            dialogBinding.etClassBuilding.setText(item.buildingOrLink)
            dialogBinding.etClassStartTime.setText(item.startTime)
            dialogBinding.etClassEndTime.setText(item.endTime)
        }

        fun showPicker(targetField: com.google.android.material.textfield.TextInputEditText) {
            if (fragmentManager == null) return
            val picker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_12H)
                .setHour(9)
                .setMinute(0)
                .setTitleText("Select Time")
                .build()

            picker.addOnPositiveButtonClickListener {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, picker.hour)
                    set(Calendar.MINUTE, picker.minute)
                }
                val format = SimpleDateFormat("hh:mm a", Locale.US)
                targetField.setText(format.format(cal.time))
            }
            picker.show(fragmentManager, "ClassTimePicker")
        }

        dialogBinding.etClassStartTime.setOnClickListener { showPicker(dialogBinding.etClassStartTime) }
        dialogBinding.etClassEndTime.setOnClickListener { showPicker(dialogBinding.etClassEndTime) }

        val dialog = MaterialAlertDialogBuilder(context)
            .setView(dialogBinding.root)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialogBinding.btnSaveClass.setOnClickListener {
            val subject = dialogBinding.actvSubjectName.text?.toString()?.trim().orEmpty()
            val room = dialogBinding.etClassRoom.text?.toString()?.trim().orEmpty()
            val teacher = dialogBinding.etClassTeacher.text?.toString()?.trim().orEmpty()
            val building = dialogBinding.etClassBuilding.text?.toString()?.trim().orEmpty()
            val startTime = dialogBinding.etClassStartTime.text?.toString()?.trim().orEmpty()
            val endTime = dialogBinding.etClassEndTime.text?.toString()?.trim().orEmpty()
            val dayOfWeek = dialogBinding.spClassDay.selectedItemPosition + 1

            val selectedTypeIdx = dialogBinding.spClassType.selectedItemPosition
            val classType = if (selectedTypeIdx in 0 until typeList.size - 1) {
                typeList[selectedTypeIdx].uppercase()
            } else {
                "LECTURE"
            }

            if (subject.isEmpty() || startTime.isEmpty() || endTime.isEmpty()) {
                Toast.makeText(context, "Subject name and times are required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val result = classItem?.copy(
                subjectName = subject,
                room = room,
                teacher = teacher,
                dayOfWeek = dayOfWeek,
                startTime = startTime,
                endTime = endTime,
                classType = classType,
                buildingOrLink = building
            ) ?: ClassScheduleItem(
                profileId = activeProfileId,
                subjectName = subject,
                room = room,
                teacher = teacher,
                dayOfWeek = dayOfWeek,
                startTime = startTime,
                endTime = endTime,
                classType = classType,
                buildingOrLink = building
            )

            dialog.dismiss()
            onSave(result)
        }

        dialog.show()
    }
}
