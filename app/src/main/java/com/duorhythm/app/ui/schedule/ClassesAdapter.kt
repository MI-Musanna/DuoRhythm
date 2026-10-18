package com.duorhythm.app.ui.schedule

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.duorhythm.app.data.model.ClassScheduleItem
import com.duorhythm.app.databinding.ItemClassScheduleBinding
import com.duorhythm.app.service.ReminderManager
import com.duorhythm.app.util.AnimationUtils
import java.util.Calendar

class ClassesAdapter(
    private var classes: List<ClassScheduleItem>,
    private var selectedDayOfWeek: Int = 1,
    private val onDeleteClick: (ClassScheduleItem) -> Unit,
    private val onRecordAttendance: (subjectName: String, isPresent: Boolean) -> Unit,
    private val onItemClick: ((ClassScheduleItem) -> Unit)? = null
) : RecyclerView.Adapter<ClassesAdapter.ClassViewHolder>() {

    fun updateList(newClasses: List<ClassScheduleItem>, dayOfWeek: Int) {
        classes = newClasses
        selectedDayOfWeek = dayOfWeek
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClassViewHolder {
        val binding = ItemClassScheduleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ClassViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ClassViewHolder, position: Int) {
        holder.bind(classes[position])
        AnimationUtils.animateItemEntry(holder.itemView, position)
    }

    override fun getItemCount(): Int = classes.size

    inner class ClassViewHolder(private val binding: ItemClassScheduleBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ClassScheduleItem) {
            binding.tvClassSubject.text = item.subjectName

            // Details (Room & Teacher)
            val detailParts = mutableListOf<String>()
            if (item.room.isNotBlank()) detailParts.add("Room: ${item.room}")
            if (item.teacher.isNotBlank()) detailParts.add(item.teacher)
            val detailsText = detailParts.joinToString(" • ")
            binding.tvClassDetails.text = detailsText
            binding.tvClassDetails.visibility = if (detailsText.isEmpty()) View.GONE else View.VISIBLE

            // Class Type Pill
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
                    binding.tvClassType.setTextColor(0xFF2563EB.toInt())
                    binding.tvClassType.setBackgroundColor(0x222563EB)
                }
            }

            // Building or link
            if (item.buildingOrLink.isNotBlank()) {
                binding.tvClassBuilding.visibility = View.VISIBLE
                binding.tvClassBuilding.text = item.buildingOrLink
            } else {
                binding.tvClassBuilding.visibility = View.GONE
            }

            binding.tvClassTime.text = "${item.startTime} - ${item.endTime}"

            val parsedColor = try {
                Color.parseColor(item.colorHex)
            } catch (_: Exception) {
                0xFF2563EB.toInt()
            }
            val density = binding.root.resources.displayMetrics.density
            val pillDrawable = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 2f * density
                setColor(parsedColor)
            }
            binding.viewClassColor.background = pillDrawable

            // Calculate live status: In Session Now vs Next Up
            val nowCal = Calendar.getInstance()
            val currentDayOfWeek = when (nowCal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> 1
                Calendar.TUESDAY -> 2
                Calendar.WEDNESDAY -> 3
                Calendar.THURSDAY -> 4
                Calendar.FRIDAY -> 5
                Calendar.SATURDAY -> 6
                Calendar.SUNDAY -> 7
                else -> 1
            }

            val isToday = (currentDayOfWeek == selectedDayOfWeek)
            if (isToday) {
                val nowMinutes = nowCal.get(Calendar.HOUR_OF_DAY) * 60 + nowCal.get(Calendar.MINUTE)
                val startPair = ReminderManager.parseTimeString(item.startTime)
                val endPair = ReminderManager.parseTimeString(item.endTime)

                if (startPair != null && endPair != null) {
                    val startMinutes = startPair.first * 60 + startPair.second
                    val endMinutes = endPair.first * 60 + endPair.second

                    if (nowMinutes in startMinutes..endMinutes) {
                        binding.tvClassLiveStatus.visibility = View.VISIBLE
                        binding.tvClassLiveStatus.text = "● IN SESSION NOW"
                        binding.tvClassLiveStatus.setTextColor(0xFF10B981.toInt())
                        binding.tvClassLiveStatus.setBackgroundColor(0x2210B981)
                    } else if (isNextUpcomingClass(nowMinutes, startMinutes)) {
                        binding.tvClassLiveStatus.visibility = View.VISIBLE
                        binding.tvClassLiveStatus.text = "⏳ NEXT UP"
                        binding.tvClassLiveStatus.setTextColor(0xFF0EA5E9.toInt())
                        binding.tvClassLiveStatus.setBackgroundColor(0x220EA5E9)
                    } else {
                        binding.tvClassLiveStatus.visibility = View.GONE
                    }
                } else {
                    binding.tvClassLiveStatus.visibility = View.GONE
                }
            } else {
                binding.tvClassLiveStatus.visibility = View.GONE
            }

            // Quick Attendance
            binding.btnQuickPresent.setOnClickListener {
                onRecordAttendance(item.subjectName, true)
            }
            binding.btnQuickAbsent.setOnClickListener {
                onRecordAttendance(item.subjectName, false)
            }

            binding.btnDeleteClass.setOnClickListener {
                onDeleteClick(item)
            }
            binding.root.setOnClickListener {
                onItemClick?.invoke(item)
            }
            AnimationUtils.attachTactileFeedback(binding.btnQuickPresent, binding.btnQuickAbsent, binding.btnDeleteClass)
        }

        private fun isNextUpcomingClass(nowMinutes: Int, thisStartMinutes: Int): Boolean {
            if (thisStartMinutes <= nowMinutes) return false
            // Check if any other class starts earlier than this one but still in future
            for (other in classes) {
                val pair = ReminderManager.parseTimeString(other.startTime) ?: continue
                val otherStart = pair.first * 60 + pair.second
                if (otherStart > nowMinutes && otherStart < thisStartMinutes) {
                    return false
                }
            }
            return true
        }
    }
}
