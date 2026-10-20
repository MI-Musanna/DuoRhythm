package com.duorhythm.app.ui.wallet

import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.duorhythm.app.R
import com.duorhythm.app.data.model.CardItem
import com.duorhythm.app.data.model.TransactionItem
import com.duorhythm.app.databinding.ItemTransactionBinding
import com.duorhythm.app.util.AnimationUtils
import java.util.Locale

class TransactionsAdapter(
    private var transactions: List<TransactionItem>,
    private var cardsMap: Map<Long, CardItem>,
    private var currencySymbol: String = "$",
    private var currencyRate: Double = 1.0,
    private var runningBalanceMap: Map<Long, Double> = emptyMap(),
    private var showRunningBalance: Boolean = true,
    private var currencyCode: String = "",
    private val onTxClick: ((TransactionItem) -> Unit)? = null,
    private val onDeleteClick: (TransactionItem) -> Unit = {},
    private val onEditClick: ((TransactionItem) -> Unit)? = null
) : RecyclerView.Adapter<TransactionsAdapter.TxViewHolder>() {

    fun updateData(
        newTransactions: List<TransactionItem>,
        newCards: Map<Long, CardItem>,
        newSymbol: String = currencySymbol,
        newRate: Double = currencyRate,
        newRunningBalances: Map<Long, Double> = runningBalanceMap,
        newShowRunningBalance: Boolean = showRunningBalance,
        newCurrencyCode: String = currencyCode
    ) {
        transactions = newTransactions
        cardsMap = newCards
        currencySymbol = newSymbol
        currencyRate = newRate
        runningBalanceMap = newRunningBalances
        showRunningBalance = newShowRunningBalance
        currencyCode = newCurrencyCode
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TxViewHolder {
        val binding = ItemTransactionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TxViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TxViewHolder, position: Int) {
        holder.bind(transactions[position])
        AnimationUtils.animateItemEntry(holder.itemView, position)
    }

    override fun getItemCount(): Int = transactions.size

    inner class TxViewHolder(private val binding: ItemTransactionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(tx: TransactionItem) {
            val context = binding.root.context
            binding.tvTxTitle.text = tx.title

            val cardName = cardsMap[tx.cardId]?.cardName ?: "Cash / Default"
            binding.tvTxCategoryAndDate.text = "${tx.category} • ${tx.date} • $cardName"

            val convertedAmount = tx.amount
            if (tx.type == "INCOME") {
                binding.tvTxAmount.text = String.format(Locale.US, "+%s%.2f", currencySymbol, convertedAmount)
                binding.tvTxAmount.setTextColor(ContextCompat.getColor(context, R.color.income_green))
            } else {
                binding.tvTxAmount.text = String.format(Locale.US, "-%s%.2f", currencySymbol, convertedAmount)
                binding.tvTxAmount.setTextColor(ContextCompat.getColor(context, R.color.expense_red))
            }

            // Running Balance underneath transaction amount (Previous / Starting Balance)
            val runningBal = runningBalanceMap[tx.id] ?: if (tx.balanceAfter != 0.0) {
                if (tx.type == "INCOME") tx.balanceAfter - tx.amount else tx.balanceAfter + tx.amount
            } else null
            if (showRunningBalance && runningBal != null) {
                binding.tvRunningBalance.visibility = View.VISIBLE
                val prefix = when {
                    currencyCode.isNotBlank() -> "$currencyCode "
                    currencySymbol.isNotBlank() -> if (currencySymbol.endsWith(" ")) currencySymbol else "$currencySymbol "
                    else -> ""
                }
                binding.tvRunningBalance.text = String.format(Locale.US, "%s%,.2f", prefix, runningBal)
            } else {
                binding.tvRunningBalance.visibility = View.GONE
            }

            // Category Icon Badge (Vector Drawables)
            val iconRes = when (tx.category.lowercase(Locale.US)) {
                "salary" -> R.drawable.ic_wallet
                "education" -> R.drawable.ic_school
                "subscription", "subscriptions" -> R.drawable.ic_subscription
                "bank", "transfer" -> R.drawable.ic_bank
                "bills" -> R.drawable.ic_article
                "work orders", "work" -> R.drawable.ic_portal
                "calendar", "schedule" -> R.drawable.ic_calendar
                else -> R.drawable.ic_credit_card
            }
            binding.ivTxBadge.setImageResource(iconRes)

            binding.root.setOnClickListener {
                onTxClick?.invoke(tx)
            }

            binding.btnMoreTx.setOnClickListener { v ->
                val popup = androidx.appcompat.widget.PopupMenu(v.context, v)
                popup.menu.add(0, 1, 0, "Edit Record")
                popup.menu.add(0, 2, 1, "Delete Record")
                popup.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        1 -> {
                            onEditClick?.invoke(tx)
                            true
                        }
                        2 -> {
                            onDeleteClick(tx)
                            true
                        }
                        else -> false
                    }
                }
                popup.show()
            }
        }
    }
}

val ItemTransactionBinding.btnDeleteTx: android.widget.ImageButton get() = btnMoreTx

typealias BucketLedgerAdapter = TransactionsAdapter
typealias RecordsAdapter = TransactionsAdapter

