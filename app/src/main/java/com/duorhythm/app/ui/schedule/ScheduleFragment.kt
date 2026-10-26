package com.duorhythm.app.ui.schedule

import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AutoCompleteTextView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.duorhythm.app.R
import com.duorhythm.app.data.model.ClassScheduleItem
import com.duorhythm.app.data.model.StudentSubject
import com.duorhythm.app.data.model.instructor
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.local.pref.AppSettings
import com.duorhythm.app.databinding.DialogAddClassBinding
import com.duorhythm.app.databinding.FragmentScheduleBinding
import com.duorhythm.app.service.ReminderManager
import com.duorhythm.app.scheduler.ClassAlarmScheduler
import com.duorhythm.app.util.AnimationUtils
import com.duorhythm.app.util.TimetableUtils

val DialogAddClassBinding.etClassSubject: AutoCompleteTextView get() = actvSubjectName
val DialogAddClassBinding.etTeacher: com.google.android.material.textfield.TextInputEditText get() = etClassTeacher

class ScheduleFragment : Fragment() {

    private val subjectDao get() = dbHelper

    private var _binding: FragmentScheduleBinding? = null
    private val binding get() = _binding!!

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var classesAdapter: ClassesAdapter
    private var selectedDay = TimetableUtils.calendarDayToDbDay(java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK))

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScheduleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try {
            dbHelper = DatabaseHelper.getInstance(requireContext())
            setupRecyclerViews()
            setupListeners()
            setupCalendarDateStrip()
            loadData()

            ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
                try {
                    val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                    val density = resources.displayMetrics.density
                    val baseBottomMargin = (96 * density).toInt()
                    val sideMargin = (20 * density).toInt()
                    val basePaddingBottom = (120 * density).toInt()

                    (binding.fabAddClass.layoutParams as? CoordinatorLayout.LayoutParams)?.let { lp ->
                        lp.bottomMargin = baseBottomMargin + systemBars.bottom
                        lp.rightMargin = sideMargin
                        binding.fabAddClass.layoutParams = lp
                    }
                    binding.scrollSchedule.setPadding(
                        binding.scrollSchedule.paddingLeft,
                        binding.scrollSchedule.paddingTop,
                        binding.scrollSchedule.paddingRight,
                        basePaddingBottom + systemBars.bottom
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                insets
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            try {
                setupCalendarDateStrip()
                loadData()
                context?.let { ctx ->
                    ClassAlarmScheduler.rescheduleAllActiveAlarms(ctx)
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && _binding != null) {
            try {
                setupCalendarDateStrip()
                loadData()
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    private fun setupRecyclerViews() {
        classesAdapter = ClassesAdapter(
            emptyList(),
            selectedDay,
            onDeleteClick = { item ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Delete Class")
                    .setMessage("Remove '${item.subjectName}' from timetable?")
                    .setPositiveButton("Delete") { _, _ ->
                        dbHelper.deleteClassSchedule(item.id)
                        ClassAlarmScheduler.cancelClassAlarms(requireContext(), item.id)
                        loadClasses()
                        Toast.makeText(requireContext(), "Class deleted", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            },
            onItemClick = { item ->
                showEditClassDialog(item)
            }
        )
        binding.rvDayClasses.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDayClasses.adapter = classesAdapter
        AnimationUtils.applySlideUpLayoutAnimation(binding.rvDayClasses)
    }

    private fun getChipForDay(day: Int): com.google.android.material.chip.Chip {
        return when (day) {
            1 -> binding.chipMon
            2 -> binding.chipTue
            3 -> binding.chipWed
            4 -> binding.chipThu
            5 -> binding.chipFri
            6 -> binding.chipSat
            7 -> binding.chipSun
            else -> binding.chipMon
        }
    }

    private fun setupListeners() {
        binding.btnJumpToToday.setOnClickListener {
            val nowCal = java.util.Calendar.getInstance()
            val todayDbDay = TimetableUtils.calendarDayToDbDay(nowCal.get(java.util.Calendar.DAY_OF_WEEK))
            selectedDay = todayDbDay
            val chip = getChipForDay(todayDbDay)
            binding.chipGroupDays.check(chip.id)
            loadClasses()
            Toast.makeText(requireContext(), "Switched to Today's schedule", Toast.LENGTH_SHORT).show()
        }

        binding.btnAddClass.setOnClickListener {
            showAddClassDialog()
        }
        binding.fabAddClass.setOnClickListener {
            showAddClassDialog()
        }
        binding.btnEmptyAddClass.setOnClickListener {
            showAddClassDialog()
        }

        AnimationUtils.attachTactileFeedback(
            binding.btnAddClass,
            binding.fabAddClass,
            binding.btnEmptyAddClass,
            binding.btnJumpToToday
        )
    }

    private fun setupCalendarDateStrip() {
        if (_binding == null) return
        try {
            val startOfWeek = AppSettings.getStartOfWeek(requireContext())
            val weekDays = TimetableUtils.getWeekDays(startOfWeek)

            binding.chipGroupDays.setOnCheckedChangeListener(null)
            binding.chipGroupDays.removeAllViews()

            for (dayInfo in weekDays) {
                val chip = getChipForDay(dayInfo.dbDay)
                chip.text = "${dayInfo.shortName} ${dayInfo.dayOfMonth}"
                binding.chipGroupDays.addView(chip)
            }

            // Restore listener
            binding.chipGroupDays.setOnCheckedChangeListener { _, checkedId ->
                val newDay = when (checkedId) {
                    R.id.chipMon -> 1
                    R.id.chipTue -> 2
                    R.id.chipWed -> 3
                    R.id.chipThu -> 4
                    R.id.chipFri -> 5
                    R.id.chipSat -> 6
                    R.id.chipSun -> 7
                    else -> selectedDay
                }
                if (newDay != selectedDay) {
                    selectedDay = newDay
                    loadClasses()
                }
            }

            // Ensure the active selected day chip is checked
            val activeChip = getChipForDay(selectedDay)
            binding.chipGroupDays.check(activeChip.id)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadData() {
        if (_binding == null || !isAdded || !::dbHelper.isInitialized || !::classesAdapter.isInitialized) return
        try {
            loadClasses()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun loadClasses() {
        if (_binding == null) return
        val classes = dbHelper.getScheduleForDay(selectedDay)
        classesAdapter.updateList(classes, selectedDay)

        val dayName = TimetableUtils.getDayFullName(selectedDay)
        val count = classes.size
        binding.tvScheduleDaySummary.text = "$dayName: $count Class${if (count != 1) "es" else ""} Scheduled"
        binding.tvScheduleSubtitle.text = "$dayName agenda"

        if (classes.isEmpty()) {
            binding.rvDayClasses.visibility = View.GONE
            binding.layoutEmptyClasses.visibility = View.VISIBLE
        } else {
            binding.rvDayClasses.visibility = View.VISIBLE
            binding.layoutEmptyClasses.visibility = View.GONE
        }
    }

    private fun showTimePicker(editText: android.widget.EditText) {
        val currentText = editText.text?.toString()?.trim().orEmpty()
        var initialHour = 9
        var initialMinute = 0
        val parsed = ReminderManager.parseTimeString(currentText)
        if (parsed != null) {
            initialHour = parsed.first
            initialMinute = parsed.second
        } else {
            val cal = java.util.Calendar.getInstance()
            initialHour = cal.get(java.util.Calendar.HOUR_OF_DAY)
            initialMinute = cal.get(java.util.Calendar.MINUTE)
        }

        val picker = com.google.android.material.timepicker.MaterialTimePicker.Builder()
            .setTimeFormat(com.google.android.material.timepicker.TimeFormat.CLOCK_12H)
            .setHour(initialHour)
            .setMinute(initialMinute)
            .setTitleText("Select Time")
            .build()

        picker.addOnPositiveButtonClickListener {
            val hourOfDay = picker.hour
            val minute = picker.minute
            val amPm = if (hourOfDay >= 12) "PM" else "AM"
            val hour12 = when {
                hourOfDay == 0 -> 12
                hourOfDay > 12 -> hourOfDay - 12
                else -> hourOfDay
            }
            editText.setText(String.format(java.util.Locale.US, "%02d:%02d %s", hour12, minute, amPm))
        }

        try {
            picker.show(parentFragmentManager, "ClassTimePicker")
        } catch (e: Exception) {
            try {
                picker.show(childFragmentManager, "ClassTimePicker")
            } catch (e2: Exception) {
                android.util.Log.e("ScheduleFragment", "Error showing MaterialTimePicker", e2)
            }
        }
    }

    private fun showAddClassDialog() {
        val dialogBinding = DialogAddClassBinding.inflate(layoutInflater)
        val days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        val daysAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, days)
        dialogBinding.spClassDay.adapter = daysAdapter
        dialogBinding.spClassDay.setSelection(selectedDay - 1)

        val types = listOf("Lecture", "Lab", "Tutorial", "Seminar")
        val typesAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, types)
        dialogBinding.spClassType.adapter = typesAdapter

        val enrolledSubjects = subjectDao.getAllSubjectsSync()
        if (enrolledSubjects.isNotEmpty()) {
            val subjectNames = enrolledSubjects.map { it.name }.distinct()
            val subjectAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, subjectNames)
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

        dialogBinding.etClassStartTime.setOnClickListener { showTimePicker(dialogBinding.etClassStartTime) }
        dialogBinding.etClassEndTime.setOnClickListener { showTimePicker(dialogBinding.etClassEndTime) }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .create()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

        dialogBinding.btnSaveClass.setOnClickListener {
            val subject = dialogBinding.actvSubjectName.text?.toString()?.trim().orEmpty()
            val room = dialogBinding.etClassRoom.text?.toString()?.trim().orEmpty()
            val teacher = dialogBinding.etClassTeacher.text?.toString()?.trim().orEmpty()
            val building = dialogBinding.etClassBuilding.text?.toString()?.trim().orEmpty()
            val startTime = dialogBinding.etClassStartTime.text?.toString()?.trim().orEmpty()
            val endTime = dialogBinding.etClassEndTime.text?.toString()?.trim().orEmpty()
            val dayOfWeek = dialogBinding.spClassDay.selectedItemPosition + 1
            val classType = types[dialogBinding.spClassType.selectedItemPosition].uppercase()

            if (subject.isEmpty() || startTime.isEmpty() || endTime.isEmpty()) {
                Toast.makeText(requireContext(), "Subject name and times are required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Auto-enroll subject if not existing so user never needs to pre-enroll
            val allSubjects = dbHelper.getAllStudentSubjects()
            if (allSubjects.none { it.name.equals(subject, ignoreCase = true) }) {
                val newSub = StudentSubject(
                    name = subject,
                    code = "",
                    colorHex = "#2563EB",
                    notes = "",
                    credits = 3,
                    instructorName = teacher
                )
                dbHelper.insertStudentSubject(newSub, autoTrackAttendance = false)
            }

            val item = ClassScheduleItem(
                subjectName = subject,
                room = room,
                teacher = teacher,
                dayOfWeek = dayOfWeek,
                startTime = startTime,
                endTime = endTime,
                colorHex = "#2563EB",
                classType = classType,
                buildingOrLink = building
            )
            val newId = dbHelper.insertClassSchedule(item)
            val scheduledItem = item.copy(id = newId)
            ClassAlarmScheduler.scheduleClassAlarms(requireContext(), scheduledItem)

            dialog.dismiss()
            loadData()
            Toast.makeText(requireContext(), "$classType scheduled with 15m alarm reminder", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun showAddSubjectDialog() {
        val activity = requireActivity()
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_add_subject, null)
        val etName = dialogView.findViewById<android.widget.EditText>(R.id.etSubjectName)
        val etCode = dialogView.findViewById<android.widget.EditText>(R.id.etSubjectCode)
        val etCredits = dialogView.findViewById<android.widget.EditText>(R.id.etCredits)
        val etInstructor = dialogView.findViewById<android.widget.EditText>(R.id.etInstructor)
        val btnSave = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSaveSubject)

        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(dialogView)
            .create()

        btnSave?.setOnClickListener {
            val name = etName?.text?.toString()?.trim().orEmpty()
            if (name.isEmpty()) {
                etName?.error = "Subject name is required"
                return@setOnClickListener
            }
            val code = etCode?.text?.toString()?.trim().orEmpty()
            val credits = etCredits?.text?.toString()?.trim()?.toIntOrNull() ?: 3
            val instructor = etInstructor?.text?.toString()?.trim().orEmpty()

            val subject = StudentSubject(
                name = name,
                code = code,
                colorHex = "#2563EB",
                notes = "",
                credits = credits,
                instructorName = instructor
            )
            val autoTrack = AppSettings.isAutoTrackAttendanceEnabled(activity)
            dbHelper.insertStudentSubject(subject, autoTrackAttendance = autoTrack)
            dialog.dismiss()
            Toast.makeText(activity, "Subject created!", Toast.LENGTH_SHORT).show()
        }

        dialog.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            setDimAmount(0.65f)
        }
        dialog.show()
    }

    private fun showEditClassDialog(classItem: ClassScheduleItem) {
        val dialogBinding = DialogAddClassBinding.inflate(layoutInflater)
        dialogBinding.tvAddClassTitle.text = "Edit Class Schedule"
        dialogBinding.btnSaveClass.text = "Update Class"

        val days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        val daysAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, days)
        dialogBinding.spClassDay.adapter = daysAdapter
        val safeDayIdx = (classItem.dayOfWeek - 1).coerceIn(0, days.size - 1)
        dialogBinding.spClassDay.setSelection(safeDayIdx)

        val types = listOf("Lecture", "Lab", "Tutorial", "Seminar")
        val typesAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, types)
        dialogBinding.spClassType.adapter = typesAdapter
        val typeIdx = types.indexOfFirst { it.equals(classItem.classType, ignoreCase = true) }
        if (typeIdx >= 0) {
            dialogBinding.spClassType.setSelection(typeIdx)
        }

        dialogBinding.actvSubjectName.setText(classItem.subjectName)
        val enrolledSubjects = subjectDao.getAllSubjectsSync()
        if (enrolledSubjects.isNotEmpty()) {
            val subjectNames = enrolledSubjects.map { it.name }.distinct()
            val subjectAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, subjectNames)
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

        dialogBinding.etClassRoom.setText(classItem.room)
        dialogBinding.etClassTeacher.setText(classItem.teacher)
        dialogBinding.etClassBuilding.setText(classItem.buildingOrLink)
        dialogBinding.etClassStartTime.setText(classItem.startTime)
        dialogBinding.etClassEndTime.setText(classItem.endTime)

        dialogBinding.etClassStartTime.setOnClickListener { showTimePicker(dialogBinding.etClassStartTime) }
        dialogBinding.etClassEndTime.setOnClickListener { showTimePicker(dialogBinding.etClassEndTime) }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .create()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

        dialogBinding.btnSaveClass.setOnClickListener {
            val subject = dialogBinding.actvSubjectName.text?.toString()?.trim().orEmpty()
            val room = dialogBinding.etClassRoom.text?.toString()?.trim().orEmpty()
            val teacher = dialogBinding.etClassTeacher.text?.toString()?.trim().orEmpty()
            val building = dialogBinding.etClassBuilding.text?.toString()?.trim().orEmpty()
            val startTime = dialogBinding.etClassStartTime.text?.toString()?.trim().orEmpty()
            val endTime = dialogBinding.etClassEndTime.text?.toString()?.trim().orEmpty()
            val dayOfWeek = dialogBinding.spClassDay.selectedItemPosition + 1
            val classType = types[dialogBinding.spClassType.selectedItemPosition].uppercase()

            if (subject.isEmpty() || startTime.isEmpty() || endTime.isEmpty()) {
                Toast.makeText(requireContext(), "Subject name and times are required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val updated = classItem.copy(
                subjectName = subject,
                room = room,
                teacher = teacher,
                dayOfWeek = dayOfWeek,
                startTime = startTime,
                endTime = endTime,
                classType = classType,
                buildingOrLink = building
            )

            ClassAlarmScheduler.cancelClassAlarms(requireContext(), classItem.id)
            dbHelper.updateClassSchedule(updated)
            ClassAlarmScheduler.scheduleClassAlarms(requireContext(), updated)

            dialog.dismiss()
            loadData()
            Toast.makeText(requireContext(), "Class schedule updated", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

typealias TimetableFragment = ScheduleFragment

