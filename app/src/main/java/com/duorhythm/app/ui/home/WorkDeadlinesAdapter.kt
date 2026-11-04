package com.duorhythm.app.ui.home

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.duorhythm.app.MainActivity
import com.duorhythm.app.R
import com.duorhythm.app.data.model.ClientOrder
import com.duorhythm.app.databinding.ItemNextUpCardBinding

class WorkDeadlinesAdapter(
    private val items: List<ClientOrder>,
    private val onOrderClick: ((ClientOrder) -> Unit)? = null
) : RecyclerView.Adapter<WorkDeadlinesAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemNextUpCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], position, items.size)
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(private val binding: ItemNextUpCardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(order: ClientOrder, position: Int, total: Int) {
            binding.tvSubjectTitle.text = order.title.ifBlank { "Client Order #${order.id}" }
            binding.tvTimeRoom.text = "${order.clientName} • Due: ${order.dueDate.ifBlank { "Flexible" }}"
            binding.tvCountBadge.text = "${position + 1} of $total"
            binding.chipStatus.text = order.status.uppercase()
            binding.chipStatus.setTextColor(Color.parseColor("#10B981"))
            binding.btnSchedule.text = "Work Hub →"
            binding.btnSchedule.setTextColor(Color.parseColor("#10B981"))
            binding.btnSchedule.setOnClickListener {
                (binding.root.context as? MainActivity)?.selectNavTab(R.id.menu_portal)
            }
            binding.root.setOnClickListener {
                onOrderClick?.invoke(order) ?: (binding.root.context as? MainActivity)?.selectNavTab(R.id.menu_portal)
            }
        }
    }
}
