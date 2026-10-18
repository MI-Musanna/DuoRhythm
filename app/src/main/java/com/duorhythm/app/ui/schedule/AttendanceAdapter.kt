package com.duorhythm.app.ui.schedule

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.duorhythm.app.R
import com.duorhythm.app.data.model.SubjectAttendanceItem
import com.duorhythm.app.databinding.ItemAttendanceBinding
import com.duorhythm.app.util.AnimationUtils
import java.util.Locale

class AttendanceAdapter(
    private var attendanceList: List<SubjectAttendanceItem>,
    private val onMarkPresent: (SubjectAttendanceItem) -> Unit,
    private val onMarkAbsent: (SubjectAttendanceItem) -> Unit,
    private val onItemClick: (SubjectAttendanceItem) -> Unit
) : RecyclerView.Adapter<AttendanceAdapter.AttendanceViewHolder>() {

    fun updateList(newList: List<SubjectAttendanceItem>) {
        attendanceList = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttendanceViewHolder {
        val binding = ItemAttendanceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AttendanceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AttendanceViewHolder, position: Int) {
        holder.bind(attendanceList[position])
        AnimationUtils.animateItemEntry(holder.itemView, position)
    }

    override fun getItemCount(): Int = attendanceList.size

    inner class AttendanceViewHolder(private val binding: ItemAttendanceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SubjectAttendanceItem) {
            val context = binding.root.context
            binding.tvAttendanceSubject.text = item.subjectName
            val pct = item.percentage

            if (item.totalClasses == 0) {
                binding.tvAttendancePercent.text = "--%"
                binding.tvAttendanceCount.text = "No sessions recorded yet (tap ✓ or ✗)"
                binding.pbAttendance.progress = 0
                val color = ContextCompat.getColor(context, R.color.text_muted)
                binding.tvAttendancePercent.setTextColor(color)
                binding.pbAttendance.progressTintList = ColorStateList.valueOf(color)
            } else {
                binding.tvAttendancePercent.text = String.format(Locale.US, "%.1f%%", pct)
                binding.tvAttendanceCount.text = "Attended: ${item.attendedClasses} / ${item.totalClasses} classes"
                binding.pbAttendance.progress = pct.toInt()

                val colorRes = if (pct >= 75f) R.color.income_green else R.color.expense_red
                val color = ContextCompat.getColor(context, colorRes)
                binding.tvAttendancePercent.setTextColor(color)
                binding.pbAttendance.progressTintList = ColorStateList.valueOf(color)
            }

            binding.root.setOnClickListener {
                onItemClick(item)
            }

            binding.btnMarkPresent.setOnClickListener {
                onMarkPresent(item)
            }

            binding.btnMarkAbsent.setOnClickListener {
                onMarkAbsent(item)
            }
            AnimationUtils.attachTactileFeedback(binding.btnMarkPresent, binding.btnMarkAbsent)
        }
    }
}
