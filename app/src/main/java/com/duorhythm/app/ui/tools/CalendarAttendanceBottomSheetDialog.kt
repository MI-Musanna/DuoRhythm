package com.duorhythm.app.ui.tools

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.duorhythm.app.data.local.db.AppDatabase
import com.duorhythm.app.data.model.ClassScheduleItem
import database.dao.AttendanceDao
import database.dao.TimetableDao
import database.entities.AttendanceRecord
import com.duorhythm.app.databinding.DialogAttendanceCalendarBinding
import com.duorhythm.app.util.AnimationUtils
import com.duorhythm.app.util.TimetableUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

open class CalendarAttendanceBottomSheetDialog(context: Context) : BottomSheetDialog(context) {

    private lateinit var binding: DialogAttendanceCalendarBinding
    private val dialogScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var recordsJob: Job? = null

    private lateinit var appDatabase: AppDatabase
    private lateinit var attendanceDao: AttendanceDao
    private lateinit var timetableDao: TimetableDao

    private lateinit var adapter: CalendarAttendanceAdapter
    private var selectedCalendar: Calendar = Calendar.getInstance()
    private var currentClasses: List<ClassScheduleItem> = emptyList()
    private var currentAttendanceMap: Map<String, String> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogAttendanceCalendarBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appDatabase = AppDatabase.getInstance(context)
        attendanceDao = appDatabase.attendanceDao
        timetableDao = appDatabase.timetableDao

        setupViews()
        setupListeners()

        // Initialize with today's date
        selectedCalendar = Calendar.getInstance()
        binding.calendarViewAttendance.date = selectedCalendar.timeInMillis
        loadAttendanceForDate(selectedCalendar)

        // Expand bottom sheet nicely
        setOnShowListener {
            val bottomSheet = findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let {
                val behavior = BottomSheetBehavior.from(it)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }
        }
    }

    private fun setupViews() {
        adapter = CalendarAttendanceAdapter(
            classes = emptyList(),
            attendanceMap = emptyMap(),
            onMarkPresent = { classItem ->
                markAttendance(classItem, "PRESENT")
            },
            onMarkAbsent = { classItem ->
                markAttendance(classItem, "ABSENT")
            }
        )

        binding.rvAttendanceClasses.layoutManager = LinearLayoutManager(context)
        binding.rvAttendanceClasses.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnCloseAttendanceCalendar.setOnClickListener {
            dismiss()
        }

        binding.calendarViewAttendance.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val cal = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, dayOfMonth)
            }
            selectedCalendar = cal
            loadAttendanceForDate(cal)
        }

        AnimationUtils.attachTactileFeedback(binding.btnCloseAttendanceCalendar)
    }

    private fun loadAttendanceForDate(calendar: Calendar) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dayOfWeekFormat = SimpleDateFormat("EEEE", Locale.US)
        val displayFormat = SimpleDateFormat("EEEE, d MMM yyyy", Locale.US)

        val dateString = dateFormat.format(calendar.time)
        val dayOfWeekName = dayOfWeekFormat.format(calendar.time)
        val displayDate = displayFormat.format(calendar.time)

        binding.tvSelectedDateIndicator.text = displayDate

        // Map Calendar day of week to Db day
        val calDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val dbDay = TimetableUtils.calendarDayToDbDay(calDayOfWeek)

        // Fetch classes scheduled for this weekday from timetableDao
        currentClasses = timetableDao.getClassesForDay(dbDay)

        // Cancel previous collector job and observe attendanceDao Flow for dateString
        recordsJob?.cancel()
        recordsJob = dialogScope.launch {
            attendanceDao.getRecordsByDate(dateString).collectLatest { records ->
                val map = records.associate { it.subjectName to it.status }
                currentAttendanceMap = map
                updateAttendanceUI(dayOfWeekName)
            }
        }
    }

    private fun updateAttendanceUI(dayOfWeekName: String) {
        adapter.updateData(currentClasses, currentAttendanceMap)

        val totalClasses = currentClasses.size
        val presentCount = currentAttendanceMap.values.count { it.equals("PRESENT", ignoreCase = true) }
        val absentCount = currentAttendanceMap.values.count { it.equals("ABSENT", ignoreCase = true) }

        binding.tvSelectedDateSummary.text =
            "$totalClasses Class${if (totalClasses != 1) "es" else ""} Scheduled • $presentCount Present • $absentCount Absent"

        if (currentClasses.isEmpty()) {
            binding.rvAttendanceClasses.visibility = View.GONE
            binding.layoutEmptyAttendanceClasses.visibility = View.VISIBLE
            binding.tvEmptyTitle.text = "No classes scheduled for $dayOfWeekName"
        } else {
            binding.rvAttendanceClasses.visibility = View.VISIBLE
            binding.layoutEmptyAttendanceClasses.visibility = View.GONE
        }
    }

    private fun markAttendance(classItem: ClassScheduleItem, status: String) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dayOfWeekFormat = SimpleDateFormat("EEEE", Locale.US)

        val dateString = dateFormat.format(selectedCalendar.time)
        val dayOfWeekName = dayOfWeekFormat.format(selectedCalendar.time)

        val record = AttendanceRecord(
            subjectName = classItem.subjectName,
            date = dateString,
            dayOfWeek = dayOfWeekName,
            status = status
        )

        dialogScope.launch(Dispatchers.IO) {
            attendanceDao.recordAttendance(record)
        }
    }

    override fun onStop() {
        super.onStop()
        recordsJob?.cancel()
        dialogScope.cancel()
    }
}

typealias CalendarAttendanceDialog = CalendarAttendanceBottomSheetDialog
