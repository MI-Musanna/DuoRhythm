package com.duorhythm.app.ui.home

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.duorhythm.app.data.model.ClassEntity
import com.duorhythm.app.databinding.ItemNextUpCardBinding

class NextUpCardAdapter(
    private val onScheduleClick: () -> Unit
) : RecyclerView.Adapter<NextUpCardAdapter.ViewHolder>() {

    private val items = mutableListOf<ClassEntity>()
    private var isTomorrow: Boolean = false
    private var dayLabel: String = "Today"

    fun submitClasses(newItems: List<ClassEntity>, isTomorrow: Boolean, dayLabel: String) {
        items.clear()
        items.addAll(newItems)
        this.isTomorrow = isTomorrow
        this.dayLabel = dayLabel
        notifyDataSetChanged()
    }

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
            binding.tvSubjectTitle.text = cls.subjectName
            binding.tvTimeRoom.text = "${cls.startTime} - ${cls.endTime} • Room ${cls.room} • $dayLabel"
            binding.tvCountBadge.text = "${position + 1} of $total"

            if (isTomorrow) {
                binding.chipStatus.text = if (position == 0) "NEXT UP" else "UPCOMING"
                binding.chipStatus.setTextColor(Color.parseColor(if (position == 0) "#00E5FF" else "#94A3B8"))
            } else {
                binding.chipStatus.text = if (position == 0) "NEXT UP" else "SCHEDULED"
                binding.chipStatus.setTextColor(Color.parseColor("#00E5FF"))
            }

            binding.btnSchedule.setOnClickListener { onScheduleClick() }
            binding.root.setOnClickListener { onScheduleClick() }
        }
    }
}
