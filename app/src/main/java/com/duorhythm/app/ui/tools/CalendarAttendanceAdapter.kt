package com.duorhythm.app.ui.tools

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.duorhythm.app.data.model.ClassScheduleItem
import com.duorhythm.app.databinding.ItemAttendanceClassBinding
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
        val binding = ItemAttendanceClassBinding.inflate(
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
        private val binding: ItemAttendanceClassBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ClassScheduleItem) {
            val context = binding.root.context
            val density = context.resources.displayMetrics.density

            // Row 1: Subject Name & Class Type
            binding.tvSubjectName.text = item.subjectName
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

            // Row 2: Class Timing
            binding.tvClassTime.text = "${item.startTime} - ${item.endTime}"

            // Row 3: Room & Teacher Info
            val roomPart = if (item.room.isNotBlank()) "Room: ${item.room}" else ""
            val teacherPart = if (item.teacher.isNotBlank()) "Teacher: ${item.teacher}" else ""
            val roomAndTeacher = when {
                roomPart.isNotEmpty() && teacherPart.isNotEmpty() -> "$roomPart • $teacherPart"
                roomPart.isNotEmpty() -> roomPart
                teacherPart.isNotEmpty() -> teacherPart
                item.buildingOrLink.isNotBlank() -> item.buildingOrLink
                else -> "Room & Teacher: Not specified"
            }
            binding.tvRoomAndTeacher.text = roomAndTeacher

            // Row 4: Attendance Status & Actions
            val currentStatus = attendanceMap[item.subjectName]?.uppercase()
            val greenColor = 0xFF10B981.toInt()
            val redColor = 0xFFEF4444.toInt()

            when (currentStatus) {
                "PRESENT" -> {
                    binding.tvAttendanceStatus.text = "Status: Present"
                    binding.tvAttendanceStatus.setTextColor(greenColor)

                    // Highlight Present
                    binding.btnPresent.backgroundTintList = ColorStateList.valueOf(greenColor)
                    binding.btnPresent.setTextColor(Color.WHITE)
                    binding.btnPresent.strokeWidth = 0

                    // Mute Absent
                    binding.btnAbsent.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                    binding.btnAbsent.setTextColor(0x88EF4444.toInt())
                    binding.btnAbsent.strokeColor = ColorStateList.valueOf(0x44EF4444.toInt())
                    binding.btnAbsent.strokeWidth = (1 * density).toInt()
                }
                "ABSENT" -> {
                    binding.tvAttendanceStatus.text = "Status: Absent"
                    binding.tvAttendanceStatus.setTextColor(redColor)

                    // Mute Present
                    binding.btnPresent.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                    binding.btnPresent.setTextColor(0x8810B981.toInt())
                    binding.btnPresent.strokeColor = ColorStateList.valueOf(0x4410B981.toInt())
                    binding.btnPresent.strokeWidth = (1 * density).toInt()

                    // Highlight Absent
                    binding.btnAbsent.backgroundTintList = ColorStateList.valueOf(redColor)
                    binding.btnAbsent.setTextColor(Color.WHITE)
                    binding.btnAbsent.strokeWidth = 0
                }
                else -> {
                    binding.tvAttendanceStatus.text = "Status: Not Marked"
                    binding.tvAttendanceStatus.setTextColor(0xFF94A3B8.toInt())

                    // Default outlined buttons
                    binding.btnPresent.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                    binding.btnPresent.setTextColor(greenColor)
                    binding.btnPresent.strokeColor = ColorStateList.valueOf(greenColor)
                    binding.btnPresent.strokeWidth = (1 * density).toInt()

                    binding.btnAbsent.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                    binding.btnAbsent.setTextColor(redColor)
                    binding.btnAbsent.strokeColor = ColorStateList.valueOf(redColor)
                    binding.btnAbsent.strokeWidth = (1 * density).toInt()
                }
            }

            binding.btnPresent.setOnClickListener {
                onMarkPresent(item)
            }

            binding.btnAbsent.setOnClickListener {
                onMarkAbsent(item)
            }

            AnimationUtils.attachTactileFeedback(
                binding.btnPresent,
                binding.btnAbsent
            )
        }
    }
}
