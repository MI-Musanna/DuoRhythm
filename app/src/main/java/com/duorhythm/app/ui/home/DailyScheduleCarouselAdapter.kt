package com.duorhythm.app.ui.home

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.duorhythm.app.R
import com.duorhythm.app.data.model.CarouselClassItem
import com.duorhythm.app.data.model.ClassStatus
import com.duorhythm.app.databinding.ItemHomeScheduleCardBinding

class DailyScheduleCarouselAdapter(
    private var items: List<CarouselClassItem>,
    private val onItemClick: ((CarouselClassItem) -> Unit)? = null,
    private val onScheduleActionClick: (() -> Unit)? = null
) : RecyclerView.Adapter<DailyScheduleCarouselAdapter.CarouselViewHolder>() {

    fun updateItems(newItems: List<CarouselClassItem>) {
        this.items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CarouselViewHolder {
        val binding = ItemHomeScheduleCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CarouselViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CarouselViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class CarouselViewHolder(private val binding: ItemHomeScheduleCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CarouselClassItem) {
            val ctx = binding.root.context

            if (item.isPlaceholder || item.classItem == null) {
                binding.tvScheduleStatusBadge.text = "TIMETABLE"
                binding.tvScheduleStatusBadge.setTextColor(Color.parseColor("#38BDF8"))
                binding.tvScheduleStatusBadge.setBackgroundResource(R.drawable.bg_badge_timetable)

                binding.tvScheduleTypeBadge.visibility = View.GONE
                binding.tvSubjectName.text = "No Classes Today"
                binding.tvHomeScheduleSubtext.text = "All clear • Timetable on track"
                binding.tvCarouselPosition.visibility = View.GONE
                binding.btnScheduleAction.text = "Schedule →"
            } else {
                val classItem = item.classItem
                binding.tvSubjectName.text = classItem.subjectName

                val roomText = if (classItem.room.isNotBlank()) "Room ${classItem.room} • " else ""
                binding.tvHomeScheduleSubtext.text = "⏰ ${classItem.startTime} - ${classItem.endTime} • ${roomText}Today"

                when (item.status) {
                    ClassStatus.IN_SESSION_NOW -> {
                        binding.tvScheduleStatusBadge.text = "● IN SESSION NOW"
                        binding.tvScheduleStatusBadge.setTextColor(Color.parseColor("#10B981"))
                        binding.tvScheduleStatusBadge.setBackgroundResource(R.drawable.bg_chip_unselected)
                    }
                    ClassStatus.UPCOMING_NEXT -> {
                        binding.tvScheduleStatusBadge.text = "● UPCOMING NEXT"
                        binding.tvScheduleStatusBadge.setTextColor(Color.parseColor("#38BDF8"))
                        binding.tvScheduleStatusBadge.setBackgroundResource(R.drawable.bg_badge_timetable)
                    }
                    ClassStatus.COMPLETED -> {
                        binding.tvScheduleStatusBadge.text = "✓ COMPLETED"
                        binding.tvScheduleStatusBadge.setTextColor(Color.parseColor("#94A3B8"))
                        binding.tvScheduleStatusBadge.setBackgroundResource(R.drawable.bg_chip_unselected)
                    }
                }

                if (classItem.classType.isNotBlank()) {
                    binding.tvScheduleTypeBadge.text = classItem.classType.uppercase()
                    binding.tvScheduleTypeBadge.visibility = View.VISIBLE
                } else {
                    binding.tvScheduleTypeBadge.visibility = View.GONE
                }

                if (item.totalCount > 1) {
                    binding.tvCarouselPosition.text = "${item.positionIndex + 1} of ${item.totalCount}"
                    binding.tvCarouselPosition.visibility = View.VISIBLE
                } else {
                    binding.tvCarouselPosition.visibility = View.GONE
                }
            }

            binding.root.setOnClickListener {
                onItemClick?.invoke(item) ?: onScheduleActionClick?.invoke()
            }
            binding.btnScheduleAction.setOnClickListener {
                onScheduleActionClick?.invoke()
            }
        }
    }
}
