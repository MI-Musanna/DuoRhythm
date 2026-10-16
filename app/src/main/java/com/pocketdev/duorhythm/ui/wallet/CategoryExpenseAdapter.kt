package com.pocketdev.duorhythm.ui.wallet

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.pocketdev.duorhythm.data.model.CategoryExpense
import com.pocketdev.duorhythm.databinding.ItemCategoryExpenseBinding
import java.util.Locale

class CategoryExpenseAdapter(
    private var items: List<CategoryExpense>,
    private var currencySymbol: String = "$",
    private var currencyRate: Double = 1.0
) : RecyclerView.Adapter<CategoryExpenseAdapter.ViewHolder>() {

    fun updateData(newItems: List<CategoryExpense>, symbol: String, rate: Double) {
        items = newItems
        currencySymbol = symbol
        currencyRate = rate
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCategoryExpenseBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(private val binding: ItemCategoryExpenseBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CategoryExpense) {
            binding.tvCategoryName.text = item.category
            binding.tvCategoryPercent.text = String.format(Locale.US, "%.0f%%", item.percentage)

            val converted = item.amount
            binding.tvCategoryAmount.text = "-$currencySymbol${String.format(Locale.US, "%.2f", converted)}"

            val dot = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                try {
                    setColor(Color.parseColor(item.colorHex))
                } catch (_: Exception) {
                    setColor(0xFF2563EB.toInt())
                }
            }
            binding.viewCategoryColor.background = dot
        }
    }
}
