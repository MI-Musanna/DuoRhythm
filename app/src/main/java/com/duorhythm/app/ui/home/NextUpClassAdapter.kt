package com.duorhythm.app.ui.home

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.duorhythm.app.MainActivity
import com.duorhythm.app.R
import com.duorhythm.app.data.model.ClassEntity
import com.duorhythm.app.databinding.ItemNextUpCardBinding

private val ItemNextUpCardBinding.tvSubjectName get() = tvSubjectTitle
private val ItemNextUpCardBinding.tvClassTimeLocation get() = tvTimeRoom
private val ItemNextUpCardBinding.badgeClassType get() = chipType
private val ItemNextUpCardBinding.badgeNextUpLabel get() = chipStatus

class NextUpClassAdapter(
    private val classList: List<ClassEntity>,
    private val isTomorrow: Boolean = false,
    private val onScheduleClick: (() -> Unit)? = null
) : RecyclerView.Adapter<NextUpClassAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemNextUpCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = classList[position]
        val binding = holder.binding

        binding.tvSubjectName.text = item.subjectName.ifBlank { "Lecture" }
        val roomDisplay = if (item.roomNo.isNotBlank()) item.roomNo else "General"
        binding.tvClassTimeLocation.text = "${item.startTime} - ${item.endTime} • Room $roomDisplay • ${if (isTomorrow) "Tomorrow" else "Today"}"

        // BIND REAL TYPE: PRACTICAL vs LECTURE
        val rawType = item.type?.trim()?.uppercase() ?: "LECTURE"
        binding.badgeClassType.text = rawType

        if (rawType.contains("PRACTICAL")) {
            binding.badgeClassType.setTextColor(Color.parseColor("#38BDF8"))
            binding.badgeClassType.setBackgroundResource(R.drawable.bg_pill_blue_subtle)
        } else {
            binding.badgeClassType.setTextColor(Color.parseColor("#A855F7"))
            binding.badgeClassType.setBackgroundResource(R.drawable.bg_pill_dark)
        }

        binding.badgeNextUpLabel.text = if (isTomorrow) "TOMORROW" else "NEXT UP"
        binding.badgeNextUpLabel.setTextColor(Color.parseColor("#00E5FF"))

        binding.tvCountBadge.text = "${position + 1} of ${classList.size}"
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

    override fun getItemCount(): Int = classList.size

    inner class ViewHolder(val binding: ItemNextUpCardBinding) : RecyclerView.ViewHolder(binding.root)
}
