package com.duorhythm.app.ui.tools

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.duorhythm.app.databinding.FragmentCalendarBinding
import java.text.SimpleDateFormat
import java.util.Locale

class CalendarFragment : Fragment() {

    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Initialize calendar views safely
        try {
            val ctx = context ?: return
            val calendar = com.duorhythm.app.util.CalendarUtils.getAppConfiguredCalendar(ctx)
            binding.calendarViewAttendance.firstDayOfWeek =
                com.duorhythm.app.util.CalendarUtils.getFirstDayOfWeekConstant(ctx)
            binding.calendarViewAttendance.date = calendar.timeInMillis
            val displayFormat = SimpleDateFormat("EEEE, d MMM yyyy", Locale.US)
            binding.tvSelectedDateIndicator.text = displayFormat.format(calendar.time)

            binding.calendarViewAttendance.setOnDateChangeListener { _, year, month, dayOfMonth ->
                val cal = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.YEAR, year)
                    set(java.util.Calendar.MONTH, month)
                    set(java.util.Calendar.DAY_OF_MONTH, dayOfMonth)
                }
                binding.tvSelectedDateIndicator.text = displayFormat.format(cal.time)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
