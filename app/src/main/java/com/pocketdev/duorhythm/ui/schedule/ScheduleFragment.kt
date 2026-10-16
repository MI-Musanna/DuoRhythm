package com.pocketdev.duorhythm.ui.schedule

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.pocketdev.duorhythm.R
import com.pocketdev.duorhythm.data.model.ClassScheduleItem
import com.pocketdev.duorhythm.data.local.db.DatabaseHelper
import com.pocketdev.duorhythm.data.local.pref.AppSettings
import com.pocketdev.duorhythm.databinding.DialogAddClassBinding
import com.pocketdev.duorhythm.databinding.FragmentScheduleBinding
import com.pocketdev.duorhythm.service.ReminderManager
import com.pocketdev.duorhythm.util.AnimationUtils
import com.pocketdev.duorhythm.util.TimetableUtils

class ScheduleFragment : Fragment() {

    private var _binding: FragmentScheduleBinding? = null
    private val binding get() = _binding!!

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var classesAdapter: ClassesAdapter
    private lateinit var attendanceAdapter: AttendanceAdapter
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
                AlertDialog.Builder(requireContext())
                    .setTitle("Delete Class")
                    .setMessage("Remove '${item.subjectName}' from timetable?")
                    .setPositiveButton("Delete") { _, _ ->
                        dbHelper.deleteClassSchedule(item.id)
                        ReminderManager.cancelClassReminder(requireContext(), item.id)
                        com.pocketdev.duorhythm.automation.SmartAutomationManager.cancelClassDndForClass(requireContext(), item.id)
                        loadClasses()
                        Toast.makeText(requireContext(), "Class deleted", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            },
            onRecordAttendance = { subjectName, isPresent ->
                dbHelper.recordAttendance(subjectName, isPresent)
                loadAttendance()
                val statusStr = if (isPresent) "present" else "absent"
                Toast.makeText(requireContext(), "Marked $subjectName as $statusStr", Toast.LENGTH_SHORT).show()
            }
        )
        binding.rvDayClasses.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDayClasses.adapter = classesAdapter
        AnimationUtils.applySlideUpLayoutAnimation(binding.rvDayClasses)

        attendanceAdapter = AttendanceAdapter(
            emptyList(),
            onMarkPresent = { item ->
                dbHelper.recordAttendance(item.subjectName, true)
                loadAttendance()
                Toast.makeText(requireContext(), "Marked present for ${item.subjectName}", Toast.LENGTH_SHORT).show()
            },
            onMarkAbsent = { item ->
                dbHelper.recordAttendance(item.subjectName, false)
                loadAttendance()
                Toast.makeText(requireContext(), "Marked absent for ${item.subjectName}", Toast.LENGTH_SHORT).show()
            },
            onItemClick = { item ->
                val options = arrayOf(
                    "Reset Attendance Counters (0/0)",
                    "Delete Subject Attendance"
                )
                AlertDialog.Builder(requireContext())
                    .setTitle("Attendance: ${item.subjectName}")
                    .setItems(options) { _, which ->
                        when (which) {
                            0 -> {
                                dbHelper.resetAttendance(item.subjectName)
                                loadAttendance()
                                Toast.makeText(requireContext(), "Attendance counters reset", Toast.LENGTH_SHORT).show()
                            }
                            1 -> {
                                AlertDialog.Builder(requireContext())
                                    .setTitle("Confirm Delete")
                                    .setMessage("Remove attendance record for '${item.subjectName}'?")
                                    .setPositiveButton("Delete") { _, _ ->
                                        dbHelper.deleteAttendance(item.subjectName)
                                        loadAttendance()
                                        Toast.makeText(requireContext(), "Attendance record removed", Toast.LENGTH_SHORT).show()
                                    }
                                    .setNegativeButton("Cancel", null)
                                    .show()
                            }
                        }
                    }
                    .setNegativeButton("Close", null)
                    .show()
            }
        )
        binding.rvAttendance.layoutManager = LinearLayoutManager(requireContext())
        binding.rvAttendance.adapter = attendanceAdapter
        AnimationUtils.applySlideUpLayoutAnimation(binding.rvAttendance)
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

        binding.fabAddClass.setOnClickListener {
            showAddClassDialog()
        }
        binding.btnEmptyAddClass.setOnClickListener {
            showAddClassDialog()
        }

        AnimationUtils.attachTactileFeedback(
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
        if (_binding == null || !isAdded || !::dbHelper.isInitialized || !::classesAdapter.isInitialized || !::attendanceAdapter.isInitialized) return
        try {
            loadClasses()
            loadAttendance()
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

    private fun loadAttendance() {
        if (_binding == null) return
        val attendance = dbHelper.getAllAttendance()
        attendanceAdapter.updateList(attendance)
        if (attendance.isEmpty()) {
            binding.rvAttendance.visibility = View.GONE
            binding.layoutEmptyAttendance.visibility = View.VISIBLE
        } else {
            binding.rvAttendance.visibility = View.VISIBLE
            binding.layoutEmptyAttendance.visibility = View.GONE
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

        fun pickTime(editText: android.widget.EditText) {
            val cal = java.util.Calendar.getInstance()
            android.app.TimePickerDialog(
                requireContext(),
                { _, hourOfDay, minute ->
                    val amPm = if (hourOfDay >= 12) "PM" else "AM"
                    val hour12 = when {
                        hourOfDay == 0 -> 12
                        hourOfDay > 12 -> hourOfDay - 12
                        else -> hourOfDay
                    }
                    editText.setText(String.format(java.util.Locale.US, "%02d:%02d %s", hour12, minute, amPm))
                },
                cal.get(java.util.Calendar.HOUR_OF_DAY),
                cal.get(java.util.Calendar.MINUTE),
                false
            ).show()
        }

        dialogBinding.etClassStartTime.setOnClickListener { pickTime(dialogBinding.etClassStartTime) }
        dialogBinding.etClassEndTime.setOnClickListener { pickTime(dialogBinding.etClassEndTime) }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

        dialogBinding.btnSaveClass.setOnClickListener {
            val subject = dialogBinding.etClassSubject.text?.toString()?.trim().orEmpty()
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
            ReminderManager.scheduleClassReminder(requireContext(), scheduledItem)
            com.pocketdev.duorhythm.automation.SmartAutomationManager.scheduleClassDndForClass(requireContext(), scheduledItem)

            dialog.dismiss()
            loadData()
            Toast.makeText(requireContext(), "$classType scheduled with 15m alarm reminder", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
