package com.duorhythm.app.ui.home

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.duorhythm.app.R
import com.duorhythm.app.data.local.entity.ClassEntity
import com.duorhythm.app.databinding.ItemNextUpClassBinding

class NextUpClassAdapter(
    private val classList: List<ClassEntity>,
    private val isTomorrow: Boolean,
    private val onScheduleClick: (() -> Unit)? = null
) : RecyclerView.Adapter<NextUpClassAdapter.NextUpViewHolder>() {

    inner class NextUpViewHolder(val binding: ItemNextUpClassBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NextUpViewHolder {
        val binding = ItemNextUpClassBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return NextUpViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NextUpViewHolder, position: Int) {
        val item = classList[position]

        holder.binding.tvSubjectName.text = item.subjectName
        val dayLabel = if (isTomorrow) "Tomorrow" else "Today"
        holder.binding.tvClassTimeLocation.text = "${item.startTime} - ${item.endTime} • Room ${item.roomNo} • $dayLabel"

        // Dynamic Class Type Detection (Check both type and classType properties)
        val rawType = (item.type ?: item.classType ?: "").trim().uppercase()

        if (rawType.contains("PRACTICAL") || rawType.contains("LAB")) {
            holder.binding.badgeClassType.text = "PRACTICAL"
            holder.binding.badgeClassType.setTextColor(Color.parseColor("#38BDF8"))
            holder.binding.badgeClassType.setBackgroundResource(R.drawable.bg_pill_dark)
        } else {
            holder.binding.badgeClassType.text = "LECTURE"
            holder.binding.badgeClassType.setTextColor(Color.parseColor("#A855F7"))
            holder.binding.badgeClassType.setBackgroundResource(R.drawable.bg_pill_dark)
        }

        holder.binding.badgeNextUpLabel.text = if (isTomorrow) "TOMORROW" else "NEXT UP"

        holder.binding.tvCountBadge.text = "${position + 1} of ${classList.size}"
        val clickAction = {
            onScheduleClick?.invoke() ?: run {
                (holder.binding.root.context as? com.duorhythm.app.MainActivity)?.selectNavTab(R.id.menu_schedule)
            }
        }
        holder.binding.btnSchedule.setOnClickListener { clickAction() }
        holder.binding.root.setOnClickListener { clickAction() }
    }

    override fun getItemCount(): Int = classList.size
}
