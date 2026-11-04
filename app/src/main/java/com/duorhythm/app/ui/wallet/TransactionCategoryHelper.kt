package com.duorhythm.app.ui.wallet

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.android.material.chip.Chip
import com.duorhythm.app.R
import com.duorhythm.app.data.local.db.AppDatabase
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.model.TransactionCategoryItem
import database.dao.CategoryDao
import database.entities.CategoryEntity

class TransactionCategoryHelper(
    private val context: Context,
    private val dbHelper: DatabaseHelper,
    private val actvCategory: AutoCompleteTextView,
    private var currentTxType: String = "EXPENSE",
    initialCategory: String? = null,
    private val chipCreateCategory: Chip? = null,
    private val onCategorySelectedCallback: ((String) -> Unit)? = null
) {

    private var masterCategoryList: List<CategoryEntity> = emptyList()
    private val categoriesList = mutableListOf<TransactionCategoryItem>()
    private lateinit var adapter: CategoryDropdownAdapter

    private val categoryDao: CategoryDao by lazy {
        AppDatabase.getInstance(context).categoryDao
    }

    init {
        setup(initialCategory)
    }

    private fun setup(initialCategory: String?) {
        loadCategoriesFromDb()

        val handleCategorySelection: (TransactionCategoryItem) -> Unit = { item ->
            when (item.id) {
                -1L -> {
                    // "+ Add New Category" or "➕ Create '$name'"
                    actvCategory.dismissDropDown()
                    val currentText = actvCategory.text?.toString()?.trim().orEmpty()
                    val suggested = if (currentText.startsWith("➕") || currentText.startsWith("+ Add")) "" else currentText
                    actvCategory.setText("", false)
                    showAddCategoryDialog(suggested)
                }
                -2L -> {
                    // "Manage Categories"
                    actvCategory.dismissDropDown()
                    CategoryManagementDialog.show(context, dbHelper) {
                        loadCategoriesFromDb()
                        adapter.updateCategories(categoriesList)
                    }
                }
                else -> {
                    actvCategory.setText(item.name, false)
                    actvCategory.dismissDropDown()
                    chipCreateCategory?.visibility = View.GONE
                    onCategorySelectedCallback?.invoke(item.name)
                }
            }
        }

        adapter = CategoryDropdownAdapter(
            context = context,
            allCategories = categoriesList,
            onSelectClicked = handleCategorySelection,
            onEditClicked = { item ->
                actvCategory.dismissDropDown()
                CategoryManagementDialog.showAddOrEditCategoryDialog(context, dbHelper, item) { updated ->
                    loadCategoriesFromDb()
                    adapter.updateCategories(categoriesList)
                    val currentSelected = actvCategory.text?.toString()?.trim().orEmpty()
                    if (currentSelected.equals(item.name, ignoreCase = true)) {
                        actvCategory.setText(updated.name, false)
                        onCategorySelectedCallback?.invoke(updated.name)
                    }
                }
            },
            onDeleteClicked = { item ->
                actvCategory.dismissDropDown()
                showDeleteCategoryDialog(item)
            },
            onManageClicked = {
                actvCategory.dismissDropDown()
                CategoryManagementDialog.show(context, dbHelper) {
                    loadCategoriesFromDb()
                    adapter.updateCategories(categoriesList)
                }
            }
        )

        actvCategory.setAdapter(adapter)

        // Select initial if provided, otherwise select first available in filtered or clear text
        if (!initialCategory.isNullOrBlank()) {
            actvCategory.setText(initialCategory, false)
        } else if (categoriesList.isNotEmpty()) {
            actvCategory.setText(categoriesList.first().name, false)
        } else {
            actvCategory.setText("", false)
        }

        actvCategory.setOnClickListener {
            if (actvCategory.text.isNullOrEmpty()) {
                (actvCategory.adapter as? Filterable)?.filter?.filter("")
            }
            actvCategory.showDropDown()
        }

        actvCategory.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                if (actvCategory.text.isNullOrEmpty()) {
                    (actvCategory.adapter as? Filterable)?.filter?.filter("")
                }
                actvCategory.showDropDown()
            }
        }

        actvCategory.setOnItemClickListener { _, _, position, _ ->
            val item = adapter.getItem(position)
            handleCategorySelection(item)
        }

        // Live text watcher for 1-tap chip category creation
        actvCategory.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val typed = s?.toString()?.trim().orEmpty()
                updateQuickCreateChip(typed)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        updateQuickCreateChip(actvCategory.text?.toString()?.trim().orEmpty())
    }

    private fun updateQuickCreateChip(typed: String) {
        if (chipCreateCategory == null) return

        if (categoriesList.isEmpty()) {
            chipCreateCategory.visibility = View.VISIBLE
            if (typed.isEmpty()) {
                chipCreateCategory.text = "➕ Add New Category"
                chipCreateCategory.setOnClickListener { showAddCategoryDialog("") }
            } else {
                chipCreateCategory.text = "➕ Create \"$typed\""
                chipCreateCategory.setOnClickListener { performQuickOneTapCreate(typed) }
            }
        } else if (typed.isNotEmpty() && !categoriesList.any { it.name.equals(typed, ignoreCase = true) }) {
            chipCreateCategory.visibility = View.VISIBLE
            chipCreateCategory.text = "➕ Create \"$typed\""
            chipCreateCategory.setOnClickListener { performQuickOneTapCreate(typed) }
        } else {
            chipCreateCategory.visibility = View.GONE
        }
    }

    private fun performQuickOneTapCreate(name: String) {
        val trimmed = name.trim().removePrefix("➕ Create \"").removeSuffix("\"").trim()
        if (trimmed.isEmpty()) return
        val existing = dbHelper.getTransactionCategoryByName(trimmed)
        if (existing == null) {
            dbHelper.insertTransactionCategory(trimmed, currentTxType)
            Toast.makeText(context, "Category '$trimmed' created", Toast.LENGTH_SHORT).show()
        }
        loadCategoriesFromDb()
        adapter.updateCategories(categoriesList)
        actvCategory.setText(trimmed, false)
        actvCategory.clearFocus()
        chipCreateCategory?.visibility = View.GONE
        onCategorySelectedCallback?.invoke(trimmed)
    }

    private fun loadCategoriesFromDb() {
        masterCategoryList = try {
            categoryDao.getAllCategories()
        } catch (_: Exception) {
            dbHelper.getAllTransactionCategories(null).map { CategoryEntity.fromItem(it) }
        }
        filterCategoriesForCurrentType()
    }

    private fun filterCategoriesForCurrentType() {
        val currentType = currentTxType.uppercase()
        val filtered = masterCategoryList.filter {
            it.type.equals(currentType, ignoreCase = true) || it.type.equals("BOTH", ignoreCase = true)
        }
        categoriesList.clear()
        categoriesList.addAll(filtered.map { it.toItem() })
    }

    fun setTxType(newType: String) {
        currentTxType = newType
        val currentType = newType.uppercase()

        // 3. Re-filter from master on tab switch (always filter from masterCategoryList)
        val filtered = masterCategoryList.filter {
            it.type.equals(currentType, ignoreCase = true) || it.type.equals("BOTH", ignoreCase = true)
        }

        categoriesList.clear()
        categoriesList.addAll(filtered.map { it.toItem() })
        adapter.updateCategories(categoriesList)

        // 4. Prevent Selection Bleed:
        // Clear previously selected category so invalid category is not retained.
        // If filtered is not empty, select first available; otherwise clear text field.
        if (categoriesList.isNotEmpty()) {
            val firstCategory = categoriesList.first().name
            actvCategory.setText(firstCategory, false)
            onCategorySelectedCallback?.invoke(firstCategory)
        } else {
            actvCategory.setText("", false)
            onCategorySelectedCallback?.invoke("")
        }

        updateQuickCreateChip(actvCategory.text?.toString()?.trim().orEmpty())
    }

    fun getSelectedCategoryAndEnsurePersisted(): String {
        val raw = actvCategory.text?.toString()?.trim().orEmpty()
        val clean = if (raw.startsWith("➕") || raw.startsWith("+ Add") || raw.startsWith("⚙️") || raw.startsWith("Manage")) {
            categoriesList.firstOrNull()?.name ?: ""
        } else {
            raw
        }

        if (clean.isBlank()) {
            return ""
        }

        // Persist dynamically if user typed in a custom category that doesn't exist yet
        val existing = dbHelper.getTransactionCategoryByName(clean)
        if (existing == null) {
            dbHelper.insertTransactionCategory(clean, currentTxType)
            loadCategoriesFromDb()
            adapter.updateCategories(categoriesList)
        }

        return clean
    }

    fun showAddCategoryDialog(suggestedName: String = "") {
        CategoryManagementDialog.showAddOrEditCategoryDialog(
            context = context,
            dbHelper = dbHelper,
            itemToEdit = null,
            suggestedName = suggestedName,
            initialType = currentTxType
        ) { created ->
            loadCategoriesFromDb()
            adapter.updateCategories(categoriesList)
            actvCategory.setText(created.name, false)
            chipCreateCategory?.visibility = View.GONE
            onCategorySelectedCallback?.invoke(created.name)
        }
    }

    private fun showDeleteCategoryDialog(item: TransactionCategoryItem) {
        val dialog = AlertDialog.Builder(context)
            .setTitle("Delete Category")
            .setMessage("Are you sure you want to delete '${item.name}'? Existing transaction records will retain this category tag.")
            .setPositiveButton("Delete") { _, _ ->
                dbHelper.deleteTransactionCategory(item.id)
                Toast.makeText(context, "Category '${item.name}' deleted", Toast.LENGTH_SHORT).show()

                loadCategoriesFromDb()
                adapter.updateCategories(categoriesList)

                val currentSelected = actvCategory.text?.toString()?.trim().orEmpty()
                if (currentSelected.equals(item.name, ignoreCase = true)) {
                    val fallback = categoriesList.firstOrNull()?.name ?: ""
                    actvCategory.setText(fallback, false)
                    onCategorySelectedCallback?.invoke(fallback)
                }
                updateQuickCreateChip(actvCategory.text?.toString()?.trim().orEmpty())
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.parseColor("#EF4444"))
    }
}
