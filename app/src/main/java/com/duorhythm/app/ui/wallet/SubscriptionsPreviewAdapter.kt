package com.duorhythm.app.ui.wallet

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.duorhythm.app.data.model.SubscriptionItem
import com.duorhythm.app.databinding.ItemSubscriptionPreviewBinding
import com.duorhythm.app.util.AnimationUtils
import java.util.Locale

class SubscriptionsPreviewAdapter(
    private var subscriptions: List<SubscriptionItem>,
    private var currencySymbol: String,
    private var currencyRate: Double,
    private val onItemClick: (SubscriptionItem) -> Unit
) : RecyclerView.Adapter<SubscriptionsPreviewAdapter.ViewHolder>() {

    fun updateData(newList: List<SubscriptionItem>, symbol: String, rate: Double) {
        subscriptions = newList
        currencySymbol = symbol
        currencyRate = rate
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSubscriptionPreviewBinding.inflate(
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

    override fun getItemCount(): Int = subscriptions.take(3).size

    inner class ViewHolder(private val binding: ItemSubscriptionPreviewBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(sub: SubscriptionItem) {
            binding.tvSubServiceName.text = sub.serviceName
            binding.tvSubBucketName.text = if (sub.bucketName.isNotBlank()) "Deducts: ${sub.bucketName}" else "No bucket linked"

            val convertedCost = sub.cost
            val cycleSuffix = if (sub.billingCycle.equals("YEARLY", ignoreCase = true)) "/ yr" else "/ mo"
            binding.tvSubCost.text = String.format(Locale.US, "%s%.0f %s", currencySymbol, convertedCost, cycleSuffix)

            val days = sub.getDaysUntilRenewal()
            binding.tvSubRenewalCountdown.text = when {
                days < 0 -> "Overdue"
                days == 0L -> "Renews today"
                days == 1L -> "Renews tomorrow"
                else -> "In $days days"
            }

            binding.root.setOnClickListener {
                onItemClick(sub)
            }
        }
    }
}
