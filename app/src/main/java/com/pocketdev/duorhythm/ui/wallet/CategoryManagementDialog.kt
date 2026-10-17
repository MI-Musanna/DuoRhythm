package com.pocketdev.duorhythm.ui.wallet

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.pocketdev.duorhythm.R
import com.pocketdev.duorhythm.data.local.db.DatabaseHelper
import com.pocketdev.duorhythm.data.model.TransactionCategoryItem
import com.pocketdev.duorhythm.databinding.DialogAddCategoryBinding
import java.util.Locale

object CategoryManagementDialog {

    fun getCategoryIconRes(iconName: String): Int {
        return when (iconName.lowercase(Locale.US)) {
            "card", "credit_card" -> R.drawable.ic_credit_card
            "cash" -> R.drawable.ic_cash
            "target" -> R.drawable.ic_target
            "bolt" -> R.drawable.ic_bolt
            "school" -> R.drawable.ic_school
            "tools" -> R.drawable.ic_tools
            "analytics" -> R.drawable.ic_analytics
            "subscription" -> R.drawable.ic_subscription
            "calendar" -> R.drawable.ic_calendar
            "person" -> R.drawable.ic_person
            "settings" -> R.drawable.ic_settings
            "tasks" -> R.drawable.ic_tasks
            else -> R.drawable.ic_wallet
        }
    }

    fun show(
        context: Context,
        dbHelper: DatabaseHelper,
        onChanged: (() -> Unit)? = null
    ) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_manage_categories, null)
        val rv = view.findViewById<RecyclerView>(R.id.rvManageCategories)
        val layoutEmpty = view.findViewById<View>(R.id.layoutEmptyCategories)
        val btnAdd = view.findViewById<MaterialButton>(R.id.btnDialogAddCategory)
        val btnDone = view.findViewById<MaterialButton>(R.id.btnDoneManageCategories)

        val categories = mutableListOf<TransactionCategoryItem>()
        lateinit var adapter: ManageCategoriesAdapter

        val dialog = AlertDialog.Builder(context)
            .setView(view)
            .create()

        fun refreshList() {
            categories.clear()
            categories.addAll(dbHelper.getAllTransactionCategories())
            if (categories.isEmpty()) {
                layoutEmpty.visibility = View.VISIBLE
                rv.visibility = View.GONE
            } else {
                layoutEmpty.visibility = View.GONE
                rv.visibility = View.VISIBLE
            }
            adapter.notifyDataSetChanged()
            onChanged?.invoke()
        }

        adapter = ManageCategoriesAdapter(
            context = context,
            items = categories,
            onEdit = { item ->
                showAddOrEditCategoryDialog(context, dbHelper, item) {
                    refreshList()
                }
            },
            onDelete = { item ->
                AlertDialog.Builder(context)
                    .setTitle("Delete Category")
                    .setMessage("Are you sure you want to delete '${item.name}'? Existing transaction records will retain this category tag.")
                    .setPositiveButton("Delete") { _, _ ->
                        dbHelper.deleteTransactionCategory(item.id)
                        Toast.makeText(context, "Deleted '${item.name}'", Toast.LENGTH_SHORT).show()
                        refreshList()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )

        rv.layoutManager = LinearLayoutManager(context)
        rv.adapter = adapter
        refreshList()

        btnAdd.setOnClickListener {
            showAddOrEditCategoryDialog(context, dbHelper, null) {
                refreshList()
            }
        }

        btnDone.setOnClickListener {
            dialog.dismiss()
        }

        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.show()
    }

    fun showAddOrEditCategoryDialog(
        context: Context,
        dbHelper: DatabaseHelper,
        itemToEdit: TransactionCategoryItem? = null,
        suggestedName: String = "",
        initialType: String = "EXPENSE",
        onSaved: ((TransactionCategoryItem) -> Unit)? = null
    ) {
        val binding = DialogAddCategoryBinding.inflate(LayoutInflater.from(context))
        val dialog = AlertDialog.Builder(context)
            .setView(binding.root)
            .create()

        val isEdit = itemToEdit != null
        binding.tvCategoryDialogTitle.text = if (isEdit) "Edit Category" else "Add New Category"
        binding.tvCategoryDialogSubtitle.text = if (isEdit) "Update category details." else "Choose name, type, color, and icon."
        binding.btnSaveCategory.text = if (isEdit) "Save Changes" else "Create"

        val initialName = itemToEdit?.name ?: suggestedName
        binding.etCategoryName.setText(initialName)
        if (initialName.isNotEmpty()) {
            binding.etCategoryName.setSelection(initialName.length)
        }

        // Smart Context-Aware Default
        val targetType = (itemToEdit?.type ?: initialType).uppercase(Locale.US)
        when (targetType) {
            "INCOME" -> binding.toggleCategoryType.check(R.id.btnTypeIncome)
            "BOTH" -> binding.toggleCategoryType.check(R.id.btnTypeBoth)
            else -> binding.toggleCategoryType.check(R.id.btnTypeExpense)
        }

        fun updateTypeToggleUi() {
            val checkedId = binding.toggleCategoryType.checkedButtonId
            val buttons = listOf(
                Pair(binding.btnTypeExpense, R.id.btnTypeExpense),
                Pair(binding.btnTypeIncome, R.id.btnTypeIncome),
                Pair(binding.btnTypeBoth, R.id.btnTypeBoth)
            )
            for ((btn, id) in buttons) {
                if (id == checkedId) {
                    btn.setBackgroundColor(Color.parseColor("#0284C7"))
                    btn.setTextColor(Color.WHITE)
                    btn.strokeColor = ColorStateList.valueOf(Color.parseColor("#38BDF8"))
                } else {
                    btn.setBackgroundColor(androidx.core.content.ContextCompat.getColor(context, R.color.surface_variant))
                    btn.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.text_secondary))
                    btn.strokeColor = ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(context, R.color.card_stroke))
                }
            }
        }
        binding.toggleCategoryType.addOnButtonCheckedListener { _, _, _ ->
            updateTypeToggleUi()
        }
        updateTypeToggleUi()

        var selectedColor = itemToEdit?.colorHex ?: "#0284C7"
        var selectedIcon = itemToEdit?.icon ?: "wallet"
        val dp = { value: Int -> (value * context.resources.displayMetrics.density).toInt() }

        val colors = listOf(
            "#0284C7", // Sky Blue
            "#10B981", // Emerald Green
            "#F59E0B", // Amber
            "#EF4444", // Rose Red
            "#8B5CF6", // Purple
            "#EC4899", // Pink
            "#14B8A6", // Teal
            "#64748B"  // Slate
        )
        val colorViews = mutableListOf<ImageView>()

        val iconList = listOf(
            Pair("wallet", R.drawable.ic_wallet),
            Pair("card", R.drawable.ic_credit_card),
            Pair("cash", R.drawable.ic_cash),
            Pair("target", R.drawable.ic_target),
            Pair("bolt", R.drawable.ic_bolt),
            Pair("school", R.drawable.ic_school),
            Pair("tools", R.drawable.ic_tools),
            Pair("analytics", R.drawable.ic_analytics),
            Pair("subscription", R.drawable.ic_subscription)
        )
        val iconViews = mutableListOf<ImageView>()

        fun updateColorSelection() {
            for ((idx, cHex) in colors.withIndex()) {
                val iv = colorViews.getOrNull(idx) ?: continue
                val isSelected = cHex.equals(selectedColor, ignoreCase = true)
                val drawable = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor(cHex))
                    if (isSelected) {
                        setStroke(dp(3), Color.WHITE)
                    } else {
                        setStroke(dp(1), Color.parseColor("#334155"))
                    }
                }
                iv.background = drawable
                if (isSelected) {
                    iv.setImageResource(R.drawable.ic_check)
                    iv.imageTintList = ColorStateList.valueOf(Color.WHITE)
                } else {
                    iv.setImageDrawable(null)
                }
            }
        }

        fun updateIconSelection() {
            for ((idx, pair) in iconList.withIndex()) {
                val iv = iconViews.getOrNull(idx) ?: continue
                val isSelected = pair.first.equals(selectedIcon, ignoreCase = true)
                val bg = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    if (isSelected) {
                        try {
                            setColor(Color.parseColor(selectedColor))
                        } catch (_: Exception) {
                            setColor(Color.parseColor("#0284C7"))
                        }
                        setStroke(dp(2), Color.WHITE)
                    } else {
                        setColor(androidx.core.content.ContextCompat.getColor(context, R.color.surface_variant))
                        setStroke(dp(1), androidx.core.content.ContextCompat.getColor(context, R.color.card_stroke))
                    }
                }
                iv.background = bg
                iv.imageTintList = ColorStateList.valueOf(if (isSelected) Color.WHITE else androidx.core.content.ContextCompat.getColor(context, R.color.text_secondary))
            }
        }

        for (cHex in colors) {
            val iv = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)).apply {
                    marginEnd = dp(10)
                }
                setPadding(dp(6), dp(6), dp(6), dp(6))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    selectedColor = cHex
                    updateColorSelection()
                    updateIconSelection()
                }
            }
            colorViews.add(iv)
            binding.layoutCategoryColors.addView(iv)
        }
        updateColorSelection()

        for (pair in iconList) {
            val iv = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(38), dp(38)).apply {
                    marginEnd = dp(10)
                }
                setImageResource(pair.second)
                setPadding(dp(8), dp(8), dp(8), dp(8))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    selectedIcon = pair.first
                    updateIconSelection()
                }
            }
            iconViews.add(iv)
            binding.layoutCategoryIcons.addView(iv)
        }
        updateIconSelection()

        binding.btnCancelCategory.setOnClickListener {
            dialog.dismiss()
        }

        binding.btnSaveCategory.setOnClickListener {
            val name = binding.etCategoryName.text?.toString()?.trim().orEmpty()
            if (name.isBlank()) {
                Toast.makeText(context, "Category name cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val type = when (binding.toggleCategoryType.checkedButtonId) {
                R.id.btnTypeIncome -> "INCOME"
                R.id.btnTypeBoth -> "BOTH"
                else -> "EXPENSE"
            }

            if (itemToEdit == null) {
                val existing = dbHelper.getTransactionCategoryByName(name)
                if (existing != null) {
                    Toast.makeText(context, "Category '$name' already exists", Toast.LENGTH_SHORT).show()
                    onSaved?.invoke(existing)
                } else {
                    val newId = dbHelper.insertTransactionCategory(name, type, selectedColor, selectedIcon)
                    Toast.makeText(context, "Category '$name' created", Toast.LENGTH_SHORT).show()
                    val created = TransactionCategoryItem(
                        id = newId,
                        name = name,
                        type = type,
                        isDefault = false,
                        colorHex = selectedColor,
                        icon = selectedIcon
                    )
                    onSaved?.invoke(created)
                }
            } else {
                dbHelper.updateTransactionCategory(itemToEdit.id, name, type, selectedColor, selectedIcon)
                Toast.makeText(context, "Category updated", Toast.LENGTH_SHORT).show()
                val updated = itemToEdit.copy(
                    name = name,
                    type = type,
                    colorHex = selectedColor,
                    icon = selectedIcon
                )
                onSaved?.invoke(updated)
            }
            dialog.dismiss()
        }

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
    }

    data class CategoryFormData(
        val name: String,
        val type: String,
        val colorHex: String,
        val icon: String
    )

    fun buildCategoryFormView(
        context: Context,
        initialName: String,
        initialType: String,
        initialColor: String,
        initialIcon: String
    ): Pair<View, () -> CategoryFormData> {
        val binding = DialogAddCategoryBinding.inflate(LayoutInflater.from(context))
        binding.tvCategoryDialogTitle.visibility = View.GONE
        binding.tvCategoryDialogSubtitle.visibility = View.GONE
        binding.btnCancelCategory.visibility = View.GONE
        binding.btnSaveCategory.visibility = View.GONE

        binding.etCategoryName.setText(initialName)
        if (initialName.isNotEmpty()) {
            binding.etCategoryName.setSelection(initialName.length)
        }

        val targetType = initialType.uppercase(Locale.US)
        when (targetType) {
            "INCOME" -> binding.toggleCategoryType.check(R.id.btnTypeIncome)
            "BOTH" -> binding.toggleCategoryType.check(R.id.btnTypeBoth)
            else -> binding.toggleCategoryType.check(R.id.btnTypeExpense)
        }

        fun updateTypeToggleUi() {
            val checkedId = binding.toggleCategoryType.checkedButtonId
            val buttons = listOf(
                Pair(binding.btnTypeExpense, R.id.btnTypeExpense),
                Pair(binding.btnTypeIncome, R.id.btnTypeIncome),
                Pair(binding.btnTypeBoth, R.id.btnTypeBoth)
            )
            for ((btn, id) in buttons) {
                if (id == checkedId) {
                    btn.setBackgroundColor(Color.parseColor("#0284C7"))
                    btn.setTextColor(Color.WHITE)
                    btn.strokeColor = ColorStateList.valueOf(Color.parseColor("#38BDF8"))
                } else {
                    btn.setBackgroundColor(androidx.core.content.ContextCompat.getColor(context, R.color.surface_variant))
                    btn.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.text_secondary))
                    btn.strokeColor = ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(context, R.color.card_stroke))
                }
            }
        }
        binding.toggleCategoryType.addOnButtonCheckedListener { _, _, _ ->
            updateTypeToggleUi()
        }
        updateTypeToggleUi()

        var selectedColor = initialColor
        var selectedIcon = initialIcon
        val dp = { value: Int -> (value * context.resources.displayMetrics.density).toInt() }

        val colors = listOf(
            "#0284C7", "#10B981", "#F59E0B", "#EF4444", "#8B5CF6", "#EC4899", "#14B8A6", "#64748B"
        )
        val colorViews = mutableListOf<ImageView>()

        val iconList = listOf(
            Pair("wallet", R.drawable.ic_wallet),
            Pair("card", R.drawable.ic_credit_card),
            Pair("cash", R.drawable.ic_cash),
            Pair("target", R.drawable.ic_target),
            Pair("bolt", R.drawable.ic_bolt),
            Pair("school", R.drawable.ic_school),
            Pair("tools", R.drawable.ic_tools),
            Pair("analytics", R.drawable.ic_analytics),
            Pair("subscription", R.drawable.ic_subscription)
        )
        val iconViews = mutableListOf<ImageView>()

        fun updateColorSelection() {
            for ((idx, cHex) in colors.withIndex()) {
                val iv = colorViews.getOrNull(idx) ?: continue
                val isSelected = cHex.equals(selectedColor, ignoreCase = true)
                val drawable = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor(cHex))
                    if (isSelected) {
                        setStroke(dp(3), Color.WHITE)
                    } else {
                        setStroke(dp(1), Color.parseColor("#334155"))
                    }
                }
                iv.background = drawable
                if (isSelected) {
                    iv.setImageResource(R.drawable.ic_check)
                    iv.imageTintList = ColorStateList.valueOf(Color.WHITE)
                } else {
                    iv.setImageDrawable(null)
                }
            }
        }

        fun updateIconSelection() {
            for ((idx, pair) in iconList.withIndex()) {
                val iv = iconViews.getOrNull(idx) ?: continue
                val isSelected = pair.first.equals(selectedIcon, ignoreCase = true)
                val bg = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    if (isSelected) {
                        try {
                            setColor(Color.parseColor(selectedColor))
                        } catch (_: Exception) {
                            setColor(Color.parseColor("#0284C7"))
                        }
                        setStroke(dp(2), Color.WHITE)
                    } else {
                        setColor(androidx.core.content.ContextCompat.getColor(context, R.color.surface_variant))
                        setStroke(dp(1), androidx.core.content.ContextCompat.getColor(context, R.color.card_stroke))
                    }
                }
                iv.background = bg
                iv.imageTintList = ColorStateList.valueOf(if (isSelected) Color.WHITE else androidx.core.content.ContextCompat.getColor(context, R.color.text_secondary))
            }
        }

        for (cHex in colors) {
            val iv = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)).apply {
                    marginEnd = dp(10)
                }
                setPadding(dp(6), dp(6), dp(6), dp(6))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    selectedColor = cHex
                    updateColorSelection()
                    updateIconSelection()
                }
            }
            colorViews.add(iv)
            binding.layoutCategoryColors.addView(iv)
        }
        updateColorSelection()

        for (pair in iconList) {
            val iv = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(38), dp(38)).apply {
                    marginEnd = dp(10)
                }
                setImageResource(pair.second)
                setPadding(dp(8), dp(8), dp(8), dp(8))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    selectedIcon = pair.first
                    updateIconSelection()
                }
            }
            iconViews.add(iv)
            binding.layoutCategoryIcons.addView(iv)
        }
        updateIconSelection()

        val getter = {
            val name = binding.etCategoryName.text?.toString()?.trim().orEmpty()
            val type = when (binding.toggleCategoryType.checkedButtonId) {
                R.id.btnTypeIncome -> "INCOME"
                R.id.btnTypeBoth -> "BOTH"
                else -> "EXPENSE"
            }
            CategoryFormData(name, type, selectedColor, selectedIcon)
        }

        return Pair(binding.root, getter)
    }

    private class ManageCategoriesAdapter(
        private val context: Context,
        private val items: List<TransactionCategoryItem>,
        private val onEdit: (TransactionCategoryItem) -> Unit,
        private val onDelete: (TransactionCategoryItem) -> Unit
    ) : RecyclerView.Adapter<ManageCategoriesAdapter.ViewHolder>() {

        class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val ivIcon: ImageView = v.findViewById(R.id.ivManageCategoryIcon)
            val tvName: TextView = v.findViewById(R.id.tvManageCategoryName)
            val tvType: TextView = v.findViewById(R.id.tvManageCategoryType)
            val btnEdit: ImageButton = v.findViewById(R.id.btnManageCategoryEdit)
            val btnDelete: ImageButton = v.findViewById(R.id.btnManageCategoryDelete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(context).inflate(R.layout.item_manage_category, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvName.text = item.name
            holder.tvType.text = if (item.type == "BOTH") "Income & Expense" else item.type.lowercase().replaceFirstChar { it.uppercase() }

            holder.ivIcon.setImageResource(getCategoryIconRes(item.icon))
            try {
                holder.ivIcon.backgroundTintList = ColorStateList.valueOf(Color.parseColor(item.colorHex))
            } catch (_: Exception) {
                holder.ivIcon.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#0284C7"))
            }

            holder.btnEdit.setOnClickListener { onEdit(item) }
            holder.btnDelete.setOnClickListener { onDelete(item) }
        }

        override fun getItemCount(): Int = items.size
    }
}
