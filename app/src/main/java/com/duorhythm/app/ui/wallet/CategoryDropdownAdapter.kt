package com.duorhythm.app.ui.wallet

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Filter
import android.widget.Filterable
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.duorhythm.app.R
import com.duorhythm.app.data.model.TransactionCategoryItem
import com.duorhythm.app.databinding.ItemCategoryDropdownBinding
import database.entities.CategoryEntity

class CategoryViewHolder(val binding: ItemCategoryDropdownBinding) : RecyclerView.ViewHolder(binding.root)

class CategoryDropdownAdapter(
    private val context: Context? = null,
    allCategories: List<TransactionCategoryItem> = emptyList(),
    private val onSelectClicked: ((TransactionCategoryItem) -> Unit)? = null,
    private val onEditClicked: ((TransactionCategoryItem) -> Unit)? = null,
    private val onDeleteClicked: ((TransactionCategoryItem) -> Unit)? = null,
    private val onManageClicked: (() -> Unit)? = null,
    val categories: List<CategoryEntity> = emptyList(),
    val onSelect: ((CategoryEntity) -> Unit)? = null,
    val onEdit: ((CategoryEntity) -> Unit)? = null,
    val onDelete: ((CategoryEntity) -> Unit)? = null
) : BaseAdapter(), Filterable {

    constructor(
        categories: List<CategoryEntity>,
        onSelect: (CategoryEntity) -> Unit,
        onEdit: (CategoryEntity) -> Unit = {},
        onDelete: (CategoryEntity) -> Unit = {}
    ) : this(
        context = null,
        allCategories = categories.map {
            TransactionCategoryItem(
                id = it.id,
                name = it.name,
                type = it.type,
                icon = it.icon,
                colorHex = it.colorHex
            )
        },
        onSelectClicked = null,
        onEditClicked = null,
        onDeleteClicked = null,
        onManageClicked = null,
        categories = categories,
        onSelect = onSelect,
        onEdit = onEdit,
        onDelete = onDelete
    )

    private val allCategoriesList = mutableListOf<TransactionCategoryItem>()
    private val displayedItems = mutableListOf<TransactionCategoryItem>()
    private var lastConstraint: String = ""

    init {
        allCategoriesList.addAll(allCategories)
        updateDisplayed(allCategoriesList, "")
    }

    fun updateCategories(newList: List<TransactionCategoryItem>) {
        allCategoriesList.clear()
        allCategoriesList.addAll(newList)
        lastConstraint = ""
        updateDisplayed(allCategoriesList, "")
        notifyDataSetChanged()
    }

    private fun updateDisplayed(source: List<TransactionCategoryItem>, query: String) {
        displayedItems.clear()
        val filtered = if (query.isEmpty()) {
            source.filter { it.id > 0 }.toMutableList()
        } else {
            source.filter { it.id > 0 && it.name.contains(query, ignoreCase = true) }.toMutableList()
        }

        val addLabel = if (query.isNotEmpty() && !source.any { it.name.equals(query, ignoreCase = true) }) {
            "➕ Create \"$query\""
        } else {
            "+ Add New Category"
        }
        filtered.add(TransactionCategoryItem(id = -1L, name = addLabel, type = "", isDefault = false))
        filtered.add(TransactionCategoryItem(id = -2L, name = "Manage Categories", type = "", isDefault = false))

        displayedItems.addAll(filtered)
    }

    override fun getCount(): Int = displayedItems.size

    override fun getItem(position: Int): TransactionCategoryItem = displayedItems[position]

    override fun getItemId(position: Int): Long = displayedItems[position].id

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val ctx = context ?: parent.context
        val view = convertView ?: LayoutInflater.from(ctx).inflate(R.layout.item_wallet_category_dropdown, parent, false)
        val item = getItem(position)

        val ivIcon = view.findViewById<ImageView>(R.id.ivCategoryIcon)
        val tvName = view.findViewById<TextView>(R.id.tvCategoryName)
        val tvBadge = view.findViewById<TextView>(R.id.badgeCategoryType)
        val btnEdit = view.findViewById<View>(R.id.btnEditCategory)
        val btnDelete = view.findViewById<View>(R.id.btnDeleteCategory)
        val hitbox = view.findViewById<View>(R.id.layoutCategorySelectHitbox)
        val root = view.findViewById<View>(R.id.layoutCategoryRowRoot) ?: view

        when (item.id) {
            -1L -> {
                // "+ Add New Category" / "➕ Create '$name'"
                tvName?.text = item.name
                tvName?.setTextColor(Color.parseColor("#0284C7"))
                tvName?.typeface = Typeface.DEFAULT_BOLD
                ivIcon?.visibility = View.VISIBLE
                ivIcon?.setImageResource(R.drawable.ic_add)
                ivIcon?.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#0284C7"))
                tvBadge?.visibility = View.GONE
                btnEdit?.visibility = View.GONE
                btnDelete?.visibility = View.GONE
            }
            -2L -> {
                // "Manage Categories"
                tvName?.text = item.name
                tvName?.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
                tvName?.typeface = Typeface.DEFAULT
                ivIcon?.visibility = View.VISIBLE
                ivIcon?.setImageResource(R.drawable.ic_settings)
                ivIcon?.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#475569"))
                tvBadge?.visibility = View.GONE
                btnEdit?.visibility = View.GONE
                btnDelete?.visibility = View.GONE
            }
            else -> {
                tvName?.text = item.name
                tvName?.setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
                tvName?.typeface = Typeface.DEFAULT

                ivIcon?.visibility = View.VISIBLE
                ivIcon?.setImageResource(CategoryManagementDialog.getCategoryIconRes(item.icon))
                try {
                    ivIcon?.backgroundTintList = ColorStateList.valueOf(Color.parseColor(item.colorHex))
                } catch (_: Exception) {
                    ivIcon?.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#0284C7"))
                }

                if (item.type != "BOTH") {
                    tvBadge?.text = item.type
                    tvBadge?.visibility = View.VISIBLE
                } else {
                    tvBadge?.visibility = View.GONE
                }

                btnEdit?.visibility = View.VISIBLE
                btnDelete?.visibility = View.VISIBLE

                btnEdit?.setOnClickListener { onEditClicked?.invoke(item) }
                btnDelete?.setOnClickListener { onDeleteClicked?.invoke(item) }
            }
        }

        // Direct Tap Selection: Tapping either the hitbox or row selects immediately
        val onSelectAction = View.OnClickListener {
            onSelectClicked?.invoke(item)
        }
        hitbox?.setOnClickListener(onSelectAction)
        root.setOnClickListener(onSelectAction)

        return view
    }

    // RecyclerView ViewHolder binding implementation
    fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val item = categories[position]
        holder.binding.tvCategoryName.text = item.name

        if (item.type.equals("INCOME", ignoreCase = true)) {
            holder.binding.badgeCategoryType.visibility = View.VISIBLE
            holder.binding.badgeCategoryType.text = "INCOME"
        } else {
            holder.binding.badgeCategoryType.visibility = View.GONE
        }

        // Direct Tap Selection: Tapping either the hitbox or row selects immediately
        holder.binding.layoutCategorySelectHitbox.setOnClickListener {
            onSelect?.invoke(item)
        }
        holder.binding.root.setOnClickListener {
            onSelect?.invoke(item)
        }

        holder.binding.btnEditCategory.setOnClickListener {
            onEdit?.invoke(item)
        }

        holder.binding.btnDeleteCategory.setOnClickListener {
            onDelete?.invoke(item)
        }
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val results = FilterResults()
                val query = constraint?.toString()?.trim() ?: ""
                lastConstraint = query
                val filtered = if (query.isEmpty()) {
                    allCategoriesList.filter { it.id > 0 }.toMutableList()
                } else {
                    allCategoriesList.filter { it.id > 0 && it.name.contains(query, ignoreCase = true) }.toMutableList()
                }
                val addLabel = if (query.isNotEmpty() && !allCategoriesList.any { it.name.equals(query, ignoreCase = true) }) {
                    "➕ Create \"$query\""
                } else {
                    "+ Add New Category"
                }
                filtered.add(TransactionCategoryItem(id = -1L, name = addLabel, type = "", isDefault = false))
                filtered.add(TransactionCategoryItem(id = -2L, name = "Manage Categories", type = "", isDefault = false))

                results.values = filtered
                results.count = filtered.size
                return results
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                displayedItems.clear()
                if (results?.values is List<*>) {
                    displayedItems.addAll(results.values as List<TransactionCategoryItem>)
                }
                notifyDataSetChanged()
            }

            override fun convertResultToString(resultValue: Any?): CharSequence {
                val item = resultValue as? TransactionCategoryItem
                return if (item == null || item.id < 0) "" else item.name
            }
        }
    }
}
