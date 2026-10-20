package com.duorhythm.app.ui.tools

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.duorhythm.app.data.model.ClassScheduleItem
import com.duorhythm.app.databinding.ItemCalendarClassAttendanceBinding
import com.duorhythm.app.util.AnimationUtils

class CalendarAttendanceAdapter(
    private var classes: List<ClassScheduleItem>,
    private var attendanceMap: Map<String, String> = emptyMap(),
    private val onMarkPresent: (ClassScheduleItem) -> Unit,
    private val onMarkAbsent: (ClassScheduleItem) -> Unit
) : RecyclerView.Adapter<CalendarAttendanceAdapter.AttendanceClassViewHolder>() {

    fun updateData(newClasses: List<ClassScheduleItem>, newAttendanceMap: Map<String, String>) {
        classes = newClasses
        attendanceMap = newAttendanceMap
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttendanceClassViewHolder {
        val binding = ItemCalendarClassAttendanceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AttendanceClassViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AttendanceClassViewHolder, position: Int) {
        holder.bind(classes[position])
        AnimationUtils.animateItemEntry(holder.itemView, position)
    }

    override fun getItemCount(): Int = classes.size

    inner class AttendanceClassViewHolder(
        private val binding: ItemCalendarClassAttendanceBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ClassScheduleItem) {
            val context = binding.root.context
            val density = context.resources.displayMetrics.density

            binding.tvClassSubject.text = item.subjectName

            // Details (Room & Teacher)
            val detailParts = mutableListOf<String>()
            if (item.room.isNotBlank()) detailParts.add("Room: ${item.room}")
            if (item.teacher.isNotBlank()) detailParts.add(item.teacher)
            if (item.buildingOrLink.isNotBlank()) detailParts.add(item.buildingOrLink)
            val detailsText = detailParts.joinToString(" • ")
            binding.tvClassDetails.text = detailsText
            binding.tvClassDetails.visibility = if (detailsText.isEmpty()) View.GONE else View.VISIBLE

            // Class Type
            val typeStr = item.classType.ifBlank { "LECTURE" }
            binding.tvClassType.text = typeStr
            when (typeStr.uppercase()) {
                "LAB" -> {
                    binding.tvClassType.setTextColor(0xFF059669.toInt())
                    binding.tvClassType.setBackgroundColor(0x2210B981)
                }
                "TUTORIAL" -> {
                    binding.tvClassType.setTextColor(0xFFD97706.toInt())
                    binding.tvClassType.setBackgroundColor(0x22F59E0B)
                }
                "SEMINAR" -> {
                    binding.tvClassType.setTextColor(0xFF7C3AED.toInt())
                    binding.tvClassType.setBackgroundColor(0x228B5CF6)
                }
                else -> {
                    binding.tvClassType.setTextColor(0xFF38BDF8.toInt())
                    binding.tvClassType.setBackgroundColor(0x2238BDF8)
                }
            }

            // Time text
            binding.tvClassTime.text = "${item.startTime} - ${item.endTime}"

            // Color bar indicator
            val parsedColor = try {
                Color.parseColor(item.colorHex)
            } catch (_: Exception) {
                0xFF2563EB.toInt()
            }
            val pillDrawable = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 2f * density
                setColor(parsedColor)
            }
            binding.viewClassColor.background = pillDrawable

            // Status: PRESENT vs ABSENT
            val currentStatus = attendanceMap[item.subjectName]?.uppercase()

            val greenColor = 0xFF10B981.toInt()
            val redColor = 0xFFEF4444.toInt()

            if (currentStatus == "PRESENT") {
                // Highlight Present
                binding.btnAttendancePresent.backgroundTintList = ColorStateList.valueOf(greenColor)
                binding.btnAttendancePresent.setTextColor(Color.WHITE)
                binding.btnAttendancePresent.iconTint = ColorStateList.valueOf(Color.WHITE)
                binding.btnAttendancePresent.strokeWidth = 0

                // Mute Absent
                binding.btnAttendanceAbsent.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                binding.btnAttendanceAbsent.setTextColor(0x88EF4444.toInt())
                binding.btnAttendanceAbsent.iconTint = ColorStateList.valueOf(0x88EF4444.toInt())
                binding.btnAttendanceAbsent.strokeColor = ColorStateList.valueOf(0x44EF4444.toInt())
                binding.btnAttendanceAbsent.strokeWidth = (1 * density).toInt()
            } else if (currentStatus == "ABSENT") {
                // Mute Present
                binding.btnAttendancePresent.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                binding.btnAttendancePresent.setTextColor(0x8810B981.toInt())
                binding.btnAttendancePresent.iconTint = ColorStateList.valueOf(0x8810B981.toInt())
                binding.btnAttendancePresent.strokeColor = ColorStateList.valueOf(0x4410B981.toInt())
                binding.btnAttendancePresent.strokeWidth = (1 * density).toInt()

                // Highlight Absent
                binding.btnAttendanceAbsent.backgroundTintList = ColorStateList.valueOf(redColor)
                binding.btnAttendanceAbsent.setTextColor(Color.WHITE)
                binding.btnAttendanceAbsent.iconTint = ColorStateList.valueOf(Color.WHITE)
                binding.btnAttendanceAbsent.strokeWidth = 0
            } else {
                // Neutral state (neither marked)
                binding.btnAttendancePresent.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                binding.btnAttendancePresent.setTextColor(greenColor)
                binding.btnAttendancePresent.iconTint = ColorStateList.valueOf(greenColor)
                binding.btnAttendancePresent.strokeColor = ColorStateList.valueOf(0x6610B981)
                binding.btnAttendancePresent.strokeWidth = (1 * density).toInt()

                binding.btnAttendanceAbsent.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                binding.btnAttendanceAbsent.setTextColor(redColor)
                binding.btnAttendanceAbsent.iconTint = ColorStateList.valueOf(redColor)
                binding.btnAttendanceAbsent.strokeColor = ColorStateList.valueOf(0x66EF4444)
                binding.btnAttendanceAbsent.strokeWidth = (1 * density).toInt()
            }

            binding.btnAttendancePresent.setOnClickListener {
                onMarkPresent(item)
            }

            binding.btnAttendanceAbsent.setOnClickListener {
                onMarkAbsent(item)
            }

            AnimationUtils.attachTactileFeedback(
                binding.btnAttendancePresent,
                binding.btnAttendanceAbsent
            )
        }
    }
}
