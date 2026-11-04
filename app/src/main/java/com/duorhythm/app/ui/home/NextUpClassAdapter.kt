package com.duorhythm.app.ui.home

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.duorhythm.app.MainActivity
import com.duorhythm.app.R
import com.duorhythm.app.data.model.ClassEntity
import com.duorhythm.app.databinding.ItemNextUpCardBinding

class NextUpClassAdapter(
    private val items: List<ClassEntity>,
    private val onScheduleClick: (() -> Unit)? = null
) : RecyclerView.Adapter<NextUpClassAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemNextUpCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], position, items.size)
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(private val binding: ItemNextUpCardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(cls: ClassEntity, position: Int, total: Int) {
            binding.tvSubjectTitle.text = cls.subjectName.ifBlank { "Lecture" }
            val roomStr = if (cls.room.isNotBlank()) "Room ${cls.room}" else "General"
            binding.tvTimeRoom.text = "${cls.startTime} - ${cls.endTime} • $roomStr • Today"
            binding.tvCountBadge.text = "${position + 1} of $total"
            binding.chipStatus.text = if (position == 0) "NEXT UP" else "SCHEDULED"
            binding.chipStatus.setTextColor(Color.parseColor("#00E5FF"))
            binding.btnSchedule.text = "Schedule →"
            binding.btnSchedule.setTextColor(Color.parseColor("#00E5FF"))

            val clickAction = {
                onScheduleClick?.invoke() ?: run {
                    (binding.root.context as? MainActivity)?.selectNavTab(R.id.menu_schedule)
                }
            }
            binding.btnSchedule.setOnClickListener { clickAction() }
            binding.root.setOnClickListener { clickAction() }
        }
    }
}
