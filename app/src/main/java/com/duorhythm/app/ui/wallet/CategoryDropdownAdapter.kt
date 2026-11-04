package com.duorhythm.app.ui.wallet

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.duorhythm.app.data.local.entity.CategoryEntity
import com.duorhythm.app.databinding.ItemWalletCategoryDropdownBinding

class CategoryDropdownAdapter(
    private val categories: List<CategoryEntity>,
    private val onSelect: (CategoryEntity) -> Unit,
    private val onEdit: (CategoryEntity) -> Unit = {},
    private val onDelete: (CategoryEntity) -> Unit = {}
) : RecyclerView.Adapter<CategoryDropdownAdapter.CategoryViewHolder>() {

    inner class CategoryViewHolder(val binding: ItemWalletCategoryDropdownBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = ItemWalletCategoryDropdownBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CategoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val item = categories[position]
        holder.binding.tvCategoryName.text = item.name

        // Parse and apply unique category color dynamically
        val resolvedColor = try {
            if (!item.colorHex.isNullOrBlank()) {
                Color.parseColor(item.colorHex)
            } else {
                getFallbackColorForCategory(item.name, item.type)
            }
        } catch (_: Exception) {
            getFallbackColorForCategory(item.name, item.type)
        }

        holder.binding.ivCategoryIcon.imageTintList = ColorStateList.valueOf(resolvedColor)

        if (item.type.equals("INCOME", ignoreCase = true)) {
            holder.binding.badgeCategoryType.visibility = View.VISIBLE
            holder.binding.badgeCategoryType.text = "INCOME"
            holder.binding.badgeCategoryType.setTextColor(Color.parseColor("#38BDF8"))
        } else {
            holder.binding.badgeCategoryType.visibility = View.GONE
        }

        holder.binding.layoutCategorySelectHitbox.setOnClickListener { onSelect(item) }
        holder.binding.root.setOnClickListener { onSelect(item) }
        holder.binding.btnEditCategory.setOnClickListener { onEdit(item) }
        holder.binding.btnDeleteCategory.setOnClickListener { onDelete(item) }
    }

    override fun getItemCount(): Int = categories.size

    companion object {
        fun getFallbackColorForCategory(name: String, type: String?): Int {
            val lower = name.lowercase()
            return when {
                lower.contains("bkash") -> Color.parseColor("#E2136E")
                lower.contains("nagad") -> Color.parseColor("#F7941D")
                lower.contains("rocket") -> Color.parseColor("#8C3494")
                lower.contains("gift") -> Color.parseColor("#EC4899")
                lower.contains("project") || lower.contains("salary") -> Color.parseColor("#10B981")
                type.equals("INCOME", ignoreCase = true) -> Color.parseColor("#00E5FF")
                else -> Color.parseColor("#A855F7")
            }
        }
    }

    private fun getFallbackColorForCategory(name: String, type: String?): Int {
        return Companion.getFallbackColorForCategory(name, type)
    }
}
