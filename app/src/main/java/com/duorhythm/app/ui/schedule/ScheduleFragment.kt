package com.duorhythm.app.ui.schedule

import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.duorhythm.app.R
import com.duorhythm.app.data.model.ClassScheduleItem
import com.duorhythm.app.data.model.StudentSubject
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.local.pref.AppSettings
import com.duorhythm.app.data.repository.TimetableRepository
import com.duorhythm.app.databinding.DialogAddClassBinding
import com.duorhythm.app.databinding.FragmentScheduleBinding
import com.duorhythm.app.service.ReminderManager
import com.duorhythm.app.scheduler.AlarmSyncManager
import com.duorhythm.app.scheduler.ClassAlarmScheduler
import com.duorhythm.app.util.AnimationUtils
import com.duorhythm.app.util.CalendarUtils
import com.duorhythm.app.util.ScheduleExportHelper
import com.duorhythm.app.util.TimetableUtils
import com.duorhythm.app.widget.TimetableGlanceWidget

val DialogAddClassBinding.etClassSubject: AutoCompleteTextView get() = actvSubjectName
val DialogAddClassBinding.etTeacher: com.google.android.material.textfield.TextInputEditText get() = etClassTeacher

class ScheduleFragment : Fragment() {

    private val subjectDao get() = dbHelper

    private var _binding: FragmentScheduleBinding? = null
    private val binding get() = _binding!!

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var timetableRepository: TimetableRepository
    private lateinit var classesAdapter: ClassesAdapter
    private val scheduleViewModel by lazy {
        androidx.lifecycle.ViewModelProvider(this)[ScheduleViewModel::class.java]
    }
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
        com.duorhythm.app.util.ViewAnimationExtensions.applySpringToAllButtons(view)
        try {
            dbHelper = DatabaseHelper.getInstance(requireContext())
            timetableRepository = TimetableRepository(requireContext())
            setupRecyclerViews()
            setupListeners()
            setupCalendarDateStrip()
            loadData()

            ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
                try {
                    val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                    val density = resources.displayMetrics.density
                    val basePaddingBottom = (80 * density).toInt()
                    binding.rvClasses.setPadding(
                        binding.rvClasses.paddingLeft,
                        binding.rvClasses.paddingTop,
                        binding.rvClasses.paddingRight,
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
                val activeProfileId = com.duorhythm.app.data.local.pref.ProfileManager.getActiveProfileId(requireContext())
                scheduleViewModel.loadClassesForDay(activeProfileId, selectedDay)
                loadData()
                context?.let { ctx ->
                    AlarmSyncManager.resyncAll(ctx)
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
                        timetableRepository.deleteClass(item.id)
                        loadClasses()
                        TimetableGlanceWidget.pushUpdate(requireContext())
                        Toast.makeText(requireContext(), "Class deleted", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            },
            onItemClick = { item ->
                showEditClassDialog(item)
            }
        )
        binding.rvClasses.layoutManager = LinearLayoutManager(requireContext())
        binding.rvClasses.adapter = classesAdapter
        AnimationUtils.applySlideUpLayoutAnimation(binding.rvClasses)

        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.bindingAdapterPosition
                val toPos = target.bindingAdapterPosition
                (binding.rvClasses.adapter as? ScheduleAdapter)?.onItemMove(fromPos, toPos)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
        })
        itemTouchHelper.attachToRecyclerView(binding.rvClasses)
    }

    private fun getSelectedDayName(): String = TimetableUtils.getDayFullName(selectedDay)

    private fun setupListeners() {
        binding.btnExportSchedule.setOnClickListener {
            ExportScheduleBottomSheet().show(childFragmentManager, "EXPORT_SCHEDULE_SHEET")
        }

        binding.btnAddClass.setOnClickListener {
            showAddClassDialog()
        }

        AnimationUtils.attachTactileFeedback(
            binding.btnAddClass,
            binding.btnExportSchedule
        )
    }

    private fun setupCalendarDateStrip() {
        if (_binding == null) return
        try {
            val startOfWeek = CalendarUtils.getFirstDayOfWeekConstant(requireContext())
            val weekDays = TimetableUtils.getWeekDays(startOfWeek)
            binding.layoutDaysStrip.removeAllViews()

            val density = resources.displayMetrics.density
            val padH = (14 * density).toInt()
            val padV = (8 * density).toInt()
            val marginH = (4 * density).toInt()

            for (dayInfo in weekDays) {
                val isSelected = dayInfo.dbDay == selectedDay
                val dayView = android.widget.TextView(requireContext()).apply {
                    text = "${dayInfo.shortName} ${dayInfo.dayOfMonth}"
                    textSize = 12f
                    gravity = android.view.Gravity.CENTER
                    setPadding(padH, padV, padH, padV)
                    val lp = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                        (36 * density).toInt()
                    ).apply {
                        setMargins(marginH, 0, marginH, 0)
                    }
                    layoutParams = lp

                    if (isSelected) {
                        setBackgroundResource(R.drawable.bg_pill_blue)
                        setTextColor(android.graphics.Color.WHITE)
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    } else {
                        setBackgroundResource(R.drawable.bg_pill_dark)
                        setTextColor(android.graphics.Color.parseColor("#94A3B8"))
                        typeface = android.graphics.Typeface.DEFAULT
                    }

                    setOnClickListener {
                        if (selectedDay != dayInfo.dbDay) {
                            selectedDay = dayInfo.dbDay
                            setupCalendarDateStrip()
                            loadClasses()
                        }
                    }
                }
                binding.layoutDaysStrip.addView(dayView)
            }
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
        scheduleViewModel.setClassesForDay(classes)
        classesAdapter.updateList(classes, selectedDay)

        val dayName = TimetableUtils.getDayFullName(selectedDay)
        val count = classes.size
        binding.tvDaySummary.text = "$dayName • $count ${if (count == 1) "Class" else "Classes"}"

        if (classes.isEmpty()) {
            binding.rvClasses.visibility = View.GONE
            binding.tvEmptySchedule.visibility = View.VISIBLE
        } else {
            binding.rvClasses.visibility = View.VISIBLE
            binding.tvEmptySchedule.visibility = View.GONE
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
        EditClassDialog(
            context = requireContext(),
            fragmentManager = parentFragmentManager,
            classItem = null,
            defaultDayOfWeek = selectedDay
        ) { scheduledItem ->
            val activeProfileId = com.duorhythm.app.data.local.pref.ProfileManager.getActiveProfileId(requireContext())
            val allSubjects = dbHelper.getAllStudentSubjects(activeProfileId)
            if (allSubjects.none { it.name.equals(scheduledItem.subjectName, ignoreCase = true) }) {
                val newSub = StudentSubject(
                    profileId = activeProfileId,
                    name = scheduledItem.subjectName,
                    code = "",
                    colorHex = "#2563EB",
                    notes = "",
                    credits = 3,
                    instructorName = scheduledItem.teacher
                )
                dbHelper.insertStudentSubject(newSub, autoTrackAttendance = false)
            }

            val classWithProfile = scheduledItem.copy(profileId = activeProfileId)
            timetableRepository.insertClass(classWithProfile)
            loadData()
            TimetableGlanceWidget.pushUpdate(requireContext())
            Toast.makeText(requireContext(), "${scheduledItem.classType} scheduled with alarm reminder", Toast.LENGTH_SHORT).show()
        }.show()
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

            val activeProfileId = com.duorhythm.app.data.local.pref.ProfileManager.getActiveProfileId(activity)
            val subject = StudentSubject(
                profileId = activeProfileId,
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
        com.duorhythm.app.util.ViewAnimationExtensions.attachToDialog(dialog)
    }

    private fun showEditClassDialog(classItem: ClassScheduleItem) {
        EditClassDialog(
            context = requireContext(),
            fragmentManager = parentFragmentManager,
            classItem = classItem,
            defaultDayOfWeek = selectedDay
        ) { updated ->
            timetableRepository.updateClass(updated)
            loadData()
            TimetableGlanceWidget.pushUpdate(requireContext())
            Toast.makeText(requireContext(), "Class schedule updated", Toast.LENGTH_SHORT).show()
        }.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

typealias TimetableFragment = ScheduleFragment
