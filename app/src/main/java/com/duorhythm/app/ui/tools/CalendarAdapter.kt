package com.duorhythm.app.ui.tools

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.duorhythm.app.R

enum class AttendanceDotStatus {
    NONE,
    PRESENT,
    ABSENT,
    SCHEDULED
}

data class CalendarDayItem(
    val dayNumber: Int,
    val dateString: String,
    val isCurrentMonth: Boolean,
    val isSelected: Boolean = false,
    val isToday: Boolean = false,
    val attendanceStatus: AttendanceDotStatus = AttendanceDotStatus.NONE,
    val hasSubscriptionDue: Boolean = false
)

class CalendarAdapter(
    private var days: List<CalendarDayItem> = emptyList(),
    private val onDayClickListener: (CalendarDayItem) -> Unit
) : RecyclerView.Adapter<CalendarAdapter.DayViewHolder>() {

    fun setDays(newDays: List<CalendarDayItem>) {
        this.days = newDays
        notifyDataSetChanged()
    }

    fun populateMonth(
        context: android.content.Context,
        currentYear: Int,
        currentMonth: Int,
        monthDays: List<CalendarDayItem>
    ) {
        val firstDayOfWeek = com.duorhythm.app.util.CalendarUtils.getFirstDayOfWeekConstant(context)
        val emptyLeadingSlots = com.duorhythm.app.util.CalendarUtils.getLeadingEmptyCellCount(currentYear, currentMonth, firstDayOfWeek)
        val items = ArrayList<CalendarDayItem>()
        // Insert empty leading slots before day 1 so date cells align accurately with day headers.
        for (i in 0 until emptyLeadingSlots) {
            items.add(CalendarDayItem(dayNumber = 0, dateString = "", isCurrentMonth = false))
        }
        items.addAll(monthDays)
        setDays(items)
    }

    fun populateMonthDates(
        context: android.content.Context,
        currentYear: Int,
        currentMonth: Int,
        selectedDay: Int = -1,
        attendanceMap: Map<Int, AttendanceDotStatus> = emptyMap(),
        subscriptionDueDays: Set<Int> = emptySet()
    ) {
        val firstDayOfWeek = com.duorhythm.app.util.CalendarUtils.getFirstDayOfWeekConstant(context)
        val emptyLeadingSlots = com.duorhythm.app.util.CalendarUtils.getLeadingEmptyCellCount(currentYear, currentMonth, firstDayOfWeek)
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.YEAR, currentYear)
            set(java.util.Calendar.MONTH, currentMonth)
            set(java.util.Calendar.DAY_OF_MONTH, 1)
        }
        val maxDays = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
        val todayCal = java.util.Calendar.getInstance()
        val isThisYearMonth = todayCal.get(java.util.Calendar.YEAR) == currentYear &&
                todayCal.get(java.util.Calendar.MONTH) == currentMonth
        val todayDay = if (isThisYearMonth) todayCal.get(java.util.Calendar.DAY_OF_MONTH) else -1

        val list = ArrayList<CalendarDayItem>()
        // Insert empty leading slots before day 1 so date cells align accurately with day headers.
        for (i in 0 until emptyLeadingSlots) {
            list.add(CalendarDayItem(dayNumber = 0, dateString = "", isCurrentMonth = false))
        }
        for (day in 1..maxDays) {
            val dateStr = String.format(java.util.Locale.US, "%04d-%02d-%02d", currentYear, currentMonth + 1, day)
            list.add(
                CalendarDayItem(
                    dayNumber = day,
                    dateString = dateStr,
                    isCurrentMonth = true,
                    isSelected = (day == selectedDay),
                    isToday = (day == todayDay),
                    attendanceStatus = attendanceMap[day] ?: AttendanceDotStatus.NONE,
                    hasSubscriptionDue = subscriptionDueDays.contains(day)
                )
            )
        }
        setDays(list)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_calendar_day, parent, false)
        return DayViewHolder(view)
    }

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
        holder.bind(days[position])
    }

    override fun getItemCount(): Int = days.size

    inner class DayViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDayNumber: TextView = itemView.findViewById(R.id.tvDayNumber)
        private val layoutDotIndicators: View = itemView.findViewById(R.id.layoutDotIndicators)
        private val dotAttendance: View = itemView.findViewById(R.id.dotAttendance)
        private val dotSubscription: View = itemView.findViewById(R.id.dotSubscription)

        fun bind(item: CalendarDayItem) {
            if (item.dayNumber <= 0 || (!item.isCurrentMonth && item.dateString.isEmpty())) {
                tvDayNumber.text = ""
                dotAttendance.visibility = View.GONE
                dotSubscription.visibility = View.GONE
                layoutDotIndicators.visibility = View.GONE
                itemView.setOnClickListener(null)
                itemView.isClickable = false
                return
            }
            itemView.isClickable = true

            tvDayNumber.text = item.dayNumber.toString()

            // Month dimming & today/selected styling
            if (!item.isCurrentMonth) {
                tvDayNumber.setTextColor(Color.parseColor("#4B5563"))
            } else if (item.isSelected) {
                tvDayNumber.setTextColor(Color.parseColor("#38BDF8"))
            } else if (item.isToday) {
                tvDayNumber.setTextColor(Color.parseColor("#F59E0B"))
            } else {
                tvDayNumber.setTextColor(Color.parseColor("#F1F5F9"))
            }

            // Attendance Dot (Green for Present, Red for Absent, Cyan for Scheduled)
            when (item.attendanceStatus) {
                AttendanceDotStatus.PRESENT -> {
                    dotAttendance.visibility = View.VISIBLE
                    dotAttendance.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#10B981"))
                }
                AttendanceDotStatus.ABSENT -> {
                    dotAttendance.visibility = View.VISIBLE
                    dotAttendance.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EF4444"))
                }
                AttendanceDotStatus.SCHEDULED -> {
                    dotAttendance.visibility = View.VISIBLE
                    dotAttendance.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#06B6D4"))
                }
                AttendanceDotStatus.NONE -> {
                    dotAttendance.visibility = View.GONE
                }
            }

            // Subscription Dot (Amber #F59E0B)
            if (item.hasSubscriptionDue) {
                dotSubscription.visibility = View.VISIBLE
                dotSubscription.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F59E0B"))
            } else {
                dotSubscription.visibility = View.GONE
            }

            // Zero overlap multi-dot indicators container visibility
            if (item.attendanceStatus == AttendanceDotStatus.NONE && !item.hasSubscriptionDue) {
                layoutDotIndicators.visibility = View.GONE
            } else {
                layoutDotIndicators.visibility = View.VISIBLE
            }

            itemView.setOnClickListener {
                onDayClickListener(item)
            }
        }
    }
}
