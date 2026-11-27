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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import kotlinx.coroutines.launch
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
            setupSpeedDial()
            setupCalendarDateStrip()
            observeScheduleState()

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
                scheduleViewModel.selectDay(selectedDay)
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
                renderScheduleClasses(scheduleViewModel.classesFlow.value)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    private fun observeScheduleState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                scheduleViewModel.classesFlow.collect { classes ->
                    renderScheduleClasses(classes)
                }
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
                        TimetableGlanceWidget.pushUpdate(requireContext())
                        com.duorhythm.app.ui.custom.DuoAlertBanner.show(requireContext(), "Class deleted")
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
                            scheduleViewModel.selectDay(selectedDay)
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
        scheduleViewModel.selectDay(selectedDay)
    }

    private fun renderScheduleClasses(classes: List<ClassScheduleItem>) {
        if (_binding == null || !isAdded || !::classesAdapter.isInitialized) return
        classesAdapter.updateList(classes, selectedDay)

        val dayName = TimetableUtils.getDayFullName(selectedDay)
        val count = classes.size
        binding.tvDaySummary.text = "$dayName • $count ${if (count == 1) "Class" else "Classes"}"

        if (classes.isEmpty()) {
            binding.rvClasses.visibility = View.GONE
            binding.emptyScheduleView.visibility = View.VISIBLE
            binding.emptyScheduleView.setState(
                iconRes = R.drawable.ic_schedule,
                title = "No classes scheduled",
                subtitle = "Tap + below to organize $dayName lectures, labs, or studio sessions.",
                actionText = null,
                onAction = null
            )
            binding.emptyScheduleView.setAccentColor(android.graphics.Color.parseColor("#38BDF8"))
        } else {
            binding.rvClasses.visibility = View.VISIBLE
            binding.emptyScheduleView.visibility = View.GONE
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
            TimetableGlanceWidget.pushUpdate(requireContext())
            com.duorhythm.app.ui.custom.DuoAlertBanner.show(requireContext(), "${scheduledItem.classType} scheduled with alarm reminder")
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

        dialogView.findViewById<android.view.View>(R.id.btnToggleSubjectMoreOptions)?.let { toggle ->
            com.duorhythm.app.util.AnimationUtils.setupMoreOptionsAccordion(
                toggleRow = toggle,
                chevronView = dialogView.findViewById(R.id.ivSubjectMoreOptionsChevron),
                contentContainer = dialogView.findViewById(R.id.layoutSubjectMoreOptions),
                transitionRoot = dialogView as? android.view.ViewGroup,
                initiallyExpanded = false
            )
        }

        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(dialogView)
            .create()

        btnSave?.setOnClickListener {
            val name = etName?.text?.toString()?.trim().orEmpty()
            if (name.isEmpty() || name.all { it.isDigit() }) {
                val msg = if (name.isEmpty()) "Subject name is required" else "Subject name cannot be purely numeric"
                etName?.error = msg
                activity?.let { act -> com.duorhythm.app.ui.custom.DuoAlertBanner.show(act, msg) }
                return@setOnClickListener
            }
            val code = etCode?.text?.toString()?.trim().orEmpty()
            if (code.isNotEmpty() && !code.all { it.isDigit() }) {
                val msg = "Subject code must contain digits only"
                etCode?.error = msg
                activity?.let { act -> com.duorhythm.app.ui.custom.DuoAlertBanner.show(act, msg) }
                return@setOnClickListener
            }
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
            com.duorhythm.app.ui.custom.DuoAlertBanner.show(activity, "Subject created!")
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
            TimetableGlanceWidget.pushUpdate(requireContext())
            com.duorhythm.app.ui.custom.DuoAlertBanner.show(requireContext(), "Class schedule updated")
        }.show()
    }

    private fun setupSpeedDial() {
        try {
            binding.btnAddClass.visibility = View.GONE
            binding.speedDialSchedule.setItems(listOf(
                com.duorhythm.app.ui.views.SpeedDialFabView.SpeedDialItem("Add Class", com.duorhythm.app.R.drawable.ic_calendar, "#38BDF8") {
                    binding.btnAddClass.performClick()
                },
                com.duorhythm.app.ui.views.SpeedDialFabView.SpeedDialItem("Class Types", com.duorhythm.app.R.drawable.ic_category, "#38BDF8") {
                    try {
                        val pid = com.duorhythm.app.data.local.pref.ProfileManager.getActiveProfileId(requireContext())
                        com.duorhythm.app.ui.schedule.ManageClassTypesDialog(requireContext(), pid) {
                            loadData()
                        }.show()
                    } catch (_: Exception) {}
                }
            ))
        } catch (_: Exception) {}
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

typealias TimetableFragment = ScheduleFragment
