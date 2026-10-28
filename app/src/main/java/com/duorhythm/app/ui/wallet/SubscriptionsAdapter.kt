package com.duorhythm.app.ui.wallet

import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.duorhythm.app.R
import com.duorhythm.app.databinding.ItemSubscriptionBinding
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

    fun updateData(newList: List<SubscriptionEntity>, symbol: String, rate: Double) {
        subscriptions = newList
        currencySymbol = symbol
        currencyRate = rate
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSubscriptionBinding.inflate(
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

    inner class ViewHolder(private val binding: ItemSubscriptionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(sub: SubscriptionEntity) {
            binding.tvSubName.text = sub.name

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
                "Due ${sub.startDay}${getDaySuffix(sub.startDay)} - ${sub.endDay}${getDaySuffix(sub.endDay)}"
            } else {
                "Due day ${sub.dueDay}${getDaySuffix(sub.dueDay)}"
            }

            val sourceText = if (sub.linkedAccountId == null || sub.linkedAccountId <= 0L) {
                "Payer: External"
            } else {
                "Source: ${sub.accountName.ifBlank { "Account" }}"
            }

            binding.tvSubBucketTag.text = sourceText
            binding.tvSubCategory.text = if (sub.isRange) "• Window Range" else "• Fixed Day"
            binding.tvSubRenewalDate.text = "$windowText • $sourceText"

            val convertedCost = sub.amount
            binding.tvSubPriceCycle.text = String.format(Locale.US, "%s%.0f / mo", currencySymbol, convertedCost)

            val currentMonthYear = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
            val isSettledThisMonth = (sub.lastPaidMonthYear == currentMonthYear)

            if (isSettledThisMonth) {
                binding.tvSubStatusBadge.text = "PAID"
                binding.tvSubStatusBadge.setTextColor(Color.parseColor("#34D399"))
                binding.tvSubStatusBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2210B981"))
                binding.btnMarkPaid.text = "Paid"
                binding.btnMarkPaid.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#10B981"))
            } else {
                binding.tvSubStatusBadge.text = "DUE"
                binding.tvSubStatusBadge.setTextColor(Color.parseColor("#FBBF24"))
                binding.tvSubStatusBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#22F59E0B"))
                binding.btnMarkPaid.text = "Mark Paid"
                binding.btnMarkPaid.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#0284C7"))
            }

            val iconRes = when (sub.name.lowercase(Locale.US)) {
                "spotify" -> R.drawable.ic_music
                "youtube", "youtube premium" -> R.drawable.ic_portal
                else -> R.drawable.ic_sync
            }
            binding.ivSubIcon.setImageResource(iconRes)

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

            binding.btnSubOptions.setOnClickListener {
                val context = binding.root.context
                val options = arrayOf("Edit Subscription", "Toggle Paid / Due", "Delete Subscription")
                AlertDialog.Builder(context)
                    .setTitle(sub.name)
                    .setItems(options) { _, which ->
                        when (which) {
                            0 -> onEditClick(sub)
                            1 -> onMarkPaidClick(sub)
                            2 -> onDeleteClick(sub)
                        }
                    }
                    .show()
            }
        }
    }
}
