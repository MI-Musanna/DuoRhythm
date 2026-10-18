package com.duorhythm.app.ui.wallet

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.duorhythm.app.R
import com.duorhythm.app.data.model.DueDebtItem
import com.duorhythm.app.databinding.ItemDueDebtBinding
import com.duorhythm.app.util.AnimationUtils
import com.duorhythm.app.util.CurrencyUtils
import java.util.Locale

class DuesDebtsAdapter(
    private var items: List<DueDebtItem>,
    private var currencySymbol: String = "৳",
    private val onSettleClick: (DueDebtItem) -> Unit,
    private val onDeleteClick: (DueDebtItem) -> Unit,
    private val onItemClick: ((DueDebtItem) -> Unit)? = null
) : RecyclerView.Adapter<DuesDebtsAdapter.DueDebtViewHolder>() {

    fun updateData(newItems: List<DueDebtItem>, newSymbol: String = currencySymbol) {
        items = newItems
        currencySymbol = newSymbol
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DueDebtViewHolder {
        val binding = ItemDueDebtBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DueDebtViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DueDebtViewHolder, position: Int) {
        holder.bind(items[position])
        AnimationUtils.animateItemEntry(holder.itemView, position)
    }

    override fun getItemCount(): Int = items.size

    inner class DueDebtViewHolder(private val binding: ItemDueDebtBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DueDebtItem) {
            binding.tvPersonName.text = item.personName

            val subtextParts = mutableListOf<String>()
            if (item.phoneOrContact.isNotBlank()) subtextParts.add(item.phoneOrContact)
            if (item.dueDate.isNotBlank()) subtextParts.add("Due ${item.dueDate}")
            if (item.notes.isNotBlank()) subtextParts.add(item.notes)
            binding.tvDueSubtext.text = if (subtextParts.isNotEmpty()) {
                subtextParts.joinToString(" • ")
            } else {
                "Logged on ${item.createdAt}"
            }

            if (item.status.equals("SETTLED", ignoreCase = true)) {
                binding.tvTypeBadge.text = "SETTLED"
                binding.tvTypeBadge.setTextColor(Color.parseColor("#94A3B8"))
                binding.ivDueTypeIcon.setImageResource(R.drawable.ic_check)
                binding.ivDueTypeIcon.setColorFilter(Color.parseColor("#94A3B8"))
                binding.tvDueAmount.text = CurrencyUtils.formatRawCurrency(item.amount, currencySymbol)
                binding.tvDueAmount.setTextColor(Color.parseColor("#94A3B8"))
                binding.tvSettledLabel.visibility = View.VISIBLE
                binding.layoutSettleAction.visibility = View.GONE
            } else {
                binding.tvSettledLabel.visibility = View.GONE
                binding.layoutSettleAction.visibility = View.VISIBLE

                if (item.type.equals("LENT", ignoreCase = true)) {
                    binding.tvTypeBadge.text = "THEY OWE ME"
                    binding.tvTypeBadge.setTextColor(Color.parseColor("#34D399"))
                    binding.ivDueTypeIcon.setImageResource(R.drawable.ic_person)
                    binding.ivDueTypeIcon.setColorFilter(Color.parseColor("#34D399"))
                    binding.tvDueAmount.text = String.format(Locale.US, "+%s%,.2f", currencySymbol, item.amount)
                    binding.tvDueAmount.setTextColor(Color.parseColor("#34D399"))
                    binding.btnSettleDue.text = "✓ Mark as Received"
                    binding.btnSettleDue.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#059669"))
                } else {
                    binding.tvTypeBadge.text = "I OWE"
                    binding.tvTypeBadge.setTextColor(Color.parseColor("#F87171"))
                    binding.ivDueTypeIcon.setImageResource(R.drawable.ic_person)
                    binding.ivDueTypeIcon.setColorFilter(Color.parseColor("#F87171"))
                    binding.tvDueAmount.text = String.format(Locale.US, "-%s%,.2f", currencySymbol, item.amount)
                    binding.tvDueAmount.setTextColor(Color.parseColor("#F87171"))
                    binding.btnSettleDue.text = "✓ Mark as Paid"
                    binding.btnSettleDue.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#991B1B"))
                }
            }

            binding.btnSettleDue.setOnClickListener { onSettleClick(item) }
            binding.btnDeleteDue.setOnClickListener { onDeleteClick(item) }
            binding.root.setOnClickListener { onItemClick?.invoke(item) }
        }
    }
}
