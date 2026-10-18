package com.duorhythm.app.ui.wallet

import android.app.AlertDialog
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.duorhythm.app.R
import com.duorhythm.app.data.model.SubscriptionItem
import com.duorhythm.app.databinding.ItemSubscriptionBinding
import com.duorhythm.app.util.AnimationUtils
import java.util.Locale

class SubscriptionsAdapter(
    private var subscriptions: List<SubscriptionItem>,
    private var currencySymbol: String,
    private var currencyRate: Double,
    private val onEditClick: (SubscriptionItem) -> Unit,
    private val onDeleteClick: (SubscriptionItem) -> Unit
) : RecyclerView.Adapter<SubscriptionsAdapter.ViewHolder>() {

    fun updateData(newList: List<SubscriptionItem>, symbol: String, rate: Double) {
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

        fun bind(sub: SubscriptionItem) {
            binding.tvSubName.text = sub.serviceName
            binding.tvSubBucketTag.text = if (sub.bucketName.isNotBlank()) sub.bucketName else "Unassigned"
            binding.tvSubCategory.text = "• ${sub.category}"

            val days = sub.getDaysUntilRenewal()
            val countdownStr = when {
                days < 0 -> "Overdue"
                days == 0L -> "Renews today"
                days == 1L -> "Renews tomorrow"
                else -> "In $days days"
            }
            binding.tvSubRenewalDate.text = "Next: ${sub.renewalDate} ($countdownStr)"

            val convertedCost = sub.cost
            val cycleSuffix = if (sub.billingCycle.equals("YEARLY", ignoreCase = true)) "/ yr" else "/ mo"
            binding.tvSubPriceCycle.text = String.format(Locale.US, "%s%.0f %s", currencySymbol, convertedCost, cycleSuffix)

            val iconRes = when (sub.serviceName.lowercase(Locale.US)) {
                "spotify" -> R.drawable.ic_music
                "youtube", "youtube premium" -> R.drawable.ic_portal
                else -> R.drawable.ic_sync
            }
            binding.ivSubIcon.setImageResource(iconRes)

            binding.root.setOnClickListener {
                onEditClick(sub)
            }

            binding.btnEditSub.setOnClickListener {
                onEditClick(sub)
            }

            binding.btnDeleteSub.setOnClickListener {
                onDeleteClick(sub)
            }

            binding.btnSubOptions.setOnClickListener {
                val context = binding.root.context
                val options = arrayOf("Edit Subscription", "Delete Subscription")
                AlertDialog.Builder(context)
                    .setTitle(sub.serviceName)
                    .setItems(options) { _, which ->
                        when (which) {
                            0 -> onEditClick(sub)
                            1 -> onDeleteClick(sub)
                        }
                    }
                    .show()
            }
        }
    }
}
