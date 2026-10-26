package com.duorhythm.app.ui.wallet

import android.app.AlertDialog
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.duorhythm.app.R
import com.duorhythm.app.data.model.WalletBucketItem
import com.duorhythm.app.databinding.ItemWalletBucketBinding
import com.duorhythm.app.ui.common.AppIconRegistry
import com.duorhythm.app.util.AnimationUtils
import java.util.Locale

class WalletBucketsAdapter(
    private var buckets: List<WalletBucketItem>,
    private var currencySymbol: String,
    private var currencyRate: Double,
    private val onBucketClick: (WalletBucketItem) -> Unit,
    private val onEditBucketClick: (WalletBucketItem) -> Unit,
    private val onDeleteBucketClick: (WalletBucketItem) -> Unit
) : RecyclerView.Adapter<WalletBucketsAdapter.BucketViewHolder>() {

    fun updateData(newBuckets: List<WalletBucketItem>, symbol: String, rate: Double) {
        buckets = newBuckets
        currencySymbol = symbol
        currencyRate = rate
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BucketViewHolder {
        val binding = ItemWalletBucketBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return BucketViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BucketViewHolder, position: Int) {
        holder.bind(buckets[position])
        AnimationUtils.animateItemEntry(holder.itemView, position)
    }

    override fun getItemCount(): Int = buckets.size

    inner class BucketViewHolder(private val binding: ItemWalletBucketBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(bucket: WalletBucketItem) {
            binding.tvBucketName.text = bucket.name
            binding.tvBucketAccounts.text = if (bucket.connectedAccounts.isNotBlank()) {
                bucket.connectedAccounts
            } else {
                "Standard Account"
            }

            val convertedBalance = bucket.balance
            binding.tvBucketBalance.text = String.format(
                Locale.US,
                "%s%.2f",
                currencySymbol,
                convertedBalance
            )

            // Dynamic Icon selection
            val resolved = AppIconRegistry.getIconDrawable(bucket.icon)
            val iconRes = if (resolved != R.drawable.ic_wallet || bucket.icon.equals("wallet", ignoreCase = true) || bucket.icon.equals("ic_wallet", ignoreCase = true)) {
                resolved
            } else {
                when {
                    bucket.name.contains("cash", ignoreCase = true) -> R.drawable.ic_cash
                    bucket.name.contains("bank", ignoreCase = true) -> R.drawable.ic_bank
                    bucket.name.contains("card", ignoreCase = true) -> R.drawable.ic_credit_card
                    bucket.name.contains("piggy", ignoreCase = true) || bucket.name.contains("saving", ignoreCase = true) -> R.drawable.ic_piggy_bank
                    bucket.name.contains("vault", ignoreCase = true) -> R.drawable.ic_vault
                    else -> R.drawable.ic_wallet
                }
            }
            binding.ivBucketIcon.setImageResource(iconRes)

            val parsedColor = try {
                Color.parseColor(bucket.colorHex)
            } catch (_: Exception) {
                Color.parseColor("#06B6D4")
            }

            // 1. Icon tint (100% accentColor)
            binding.ivBucketIcon.setColorFilter(parsedColor)

            // 2. Icon container: elevated floating badge with dynamic tint and illuminated stroke
            val surfaceColor = androidx.core.graphics.ColorUtils.compositeColors(
                androidx.core.graphics.ColorUtils.setAlphaComponent(parsedColor, (255 * 0.18).toInt()),
                Color.parseColor("#161F34")
            )
            val strokeColor = androidx.core.graphics.ColorUtils.compositeColors(
                androidx.core.graphics.ColorUtils.setAlphaComponent(parsedColor, (255 * 0.45).toInt()),
                Color.parseColor("#2D3E66")
            )
            binding.layoutBucketIconBg.backgroundTintList = null
            (binding.layoutBucketIconBg.background?.mutate() as? android.graphics.drawable.GradientDrawable)?.let { bg ->
                bg.setColor(surfaceColor)
                bg.setStroke(
                    android.util.TypedValue.applyDimension(
                        android.util.TypedValue.COMPLEX_UNIT_DIP,
                        1f,
                        binding.root.resources.displayMetrics
                    ).toInt(),
                    strokeColor
                )
            }

            // 3. Left/top indicator bar and subtle card border highlight (1dp accentColor stroke)
            binding.viewAccentIndicator.backgroundTintList = android.content.res.ColorStateList.valueOf(parsedColor)
            (binding.root as? com.google.android.material.card.MaterialCardView)?.strokeColor =
                androidx.core.graphics.ColorUtils.setAlphaComponent(parsedColor, (255 * 0.40).toInt())

            binding.root.setOnClickListener {
                onBucketClick(bucket)
            }

            binding.root.setOnLongClickListener {
                showBucketOptions(bucket)
                true
            }

            binding.btnBucketOptions.setOnClickListener {
                showBucketOptions(bucket)
            }
        }

        private fun showBucketOptions(bucket: WalletBucketItem) {
            val context = binding.root.context
            val options = arrayOf("View Account Ledger", "Edit Account & Balance", "Delete Account")
            AlertDialog.Builder(context)
                .setTitle(bucket.name)
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> onBucketClick(bucket)
                        1 -> onEditBucketClick(bucket)
                        2 -> onDeleteBucketClick(bucket)
                    }
                }
                .show()
        }
    }
}
