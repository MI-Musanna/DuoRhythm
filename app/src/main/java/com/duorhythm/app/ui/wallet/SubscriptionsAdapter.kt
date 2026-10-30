package com.duorhythm.app.ui.wallet

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.duorhythm.app.databinding.ItemSubscriptionCardBinding
import com.duorhythm.app.util.AnimationUtils
import database.entities.SubscriptionEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SubscriptionsAdapter(
    private var subscriptions: List<SubscriptionEntity>,
    private var currencySymbol: String,
    private var currencyRate: Double,
    private val onEditClick: (SubscriptionEntity) -> Unit,
    private val onDeleteClick: (SubscriptionEntity) -> Unit,
    private val onMarkPaidClick: (SubscriptionEntity) -> Unit
) : RecyclerView.Adapter<SubscriptionsAdapter.ViewHolder>() {

    fun submitList(list: List<SubscriptionEntity>, commitCallback: (() -> Unit)? = null) {
        subscriptions = list
        notifyDataSetChanged()
        commitCallback?.invoke()
    }

    fun updateData(newList: List<SubscriptionEntity>, symbol: String, rate: Double) {
        subscriptions = newList
        currencySymbol = symbol
        currencyRate = rate
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSubscriptionCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(subscriptions[position])
        AnimationUtils.animateItemEntry(holder.itemView, position)
    }

    override fun getItemCount(): Int = subscriptions.size

    inner class ViewHolder(private val binding: ItemSubscriptionCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(sub: SubscriptionEntity) {
            binding.tvSubscriptionName.text = sub.name

            // Day suffix helper (e.g. 1st, 2nd, 3rd, 4th)
            val getDaySuffix = { day: Int ->
                if (day in 11..13) "th" else when (day % 10) {
                    1 -> "st"
                    2 -> "nd"
                    3 -> "rd"
                    else -> "th"
                }
            }

            val windowText = if (sub.isRange) {
                "Window: ${sub.startDay}${getDaySuffix(sub.startDay)} - ${sub.endDay}${getDaySuffix(sub.endDay)}"
            } else {
                "Due: ${sub.dueDay}${getDaySuffix(sub.dueDay)}"
            }

            val sourceText = if (sub.linkedAccountId == null || sub.linkedAccountId <= 0L) {
                "Source: External"
            } else {
                "Source: ${sub.accountName.ifBlank { "Account" }}"
            }

            binding.tvSubscriptionMeta.text = "$windowText • $sourceText"

            val symbol = if (currencySymbol.isNotBlank()) currencySymbol else "৳"
            val costVal = sub.amount
            val amountStr = if (costVal % 1.0 == 0.0) {
                String.format(Locale.US, "%.0f", costVal)
            } else {
                String.format(Locale.US, "%.2f", costVal)
            }
            binding.tvSubscriptionAmount.text = "$symbol$amountStr/mo"

            val currentMonthYear = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
            val isSettledThisMonth = (sub.lastPaidMonthYear == currentMonthYear)
            val currentMonthShort = SimpleDateFormat("MMM", Locale.US).format(Date())

            if (isSettledThisMonth) {
                binding.tvStatusBadge.text = "PAID for $currentMonthShort"
                binding.tvStatusBadge.setTextColor(Color.parseColor("#34D399")) // Emerald
                binding.tvStatusBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2210B981"))
                binding.btnMarkPaid.text = "Paid"
                binding.btnMarkPaid.setTextColor(Color.WHITE)
                binding.btnMarkPaid.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#10B981"))
            } else {
                binding.tvStatusBadge.text = "UNPAID"
                binding.tvStatusBadge.setTextColor(Color.parseColor("#EF4444")) // Red/Amber
                binding.tvStatusBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#22EF4444"))
                binding.btnMarkPaid.text = "MARK PAID"
                binding.btnMarkPaid.setTextColor(Color.parseColor("#0F172A"))
                binding.btnMarkPaid.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#00E5FF"))
            }

            binding.root.setOnClickListener {
                onEditClick(sub)
            }

            binding.btnMarkPaid.setOnClickListener {
                onMarkPaidClick(sub)
            }

            binding.btnEditSub.setOnClickListener {
                onEditClick(sub)
            }

            binding.btnDeleteSub.setOnClickListener {
                onDeleteClick(sub)
            }
        }
    }
}
