package com.duorhythm.app.ui.wallet

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.duorhythm.app.R
import com.duorhythm.app.data.local.db.AppDatabase
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.model.TransactionItem
import com.duorhythm.app.data.model.WalletBucketItem
import com.duorhythm.app.databinding.DialogAddTransactionBinding
import database.dao.CategoryDao
import database.entities.CategoryEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class TransactionType {
    INCOME,
    EXPENSE,
    TRANSFER
}

class AddRecordDialog(
    private val context: Context,
    private val initialType: TransactionType = TransactionType.INCOME,
    private val targetBucket: WalletBucketItem? = null,
    private val onTransactionSaved: ((TransactionItem) -> Unit)? = null
) {
    private val dbHelper: DatabaseHelper = DatabaseHelper.getInstance(context)
    private val categoryDao: CategoryDao by lazy {
        AppDatabase.getInstance(context).categoryDao
    }

    // 1. Maintain an Unfiltered Master List
    private var masterCategoryList: List<CategoryEntity> = emptyList()
    private var selectedType: TransactionType = initialType

    private lateinit var dialogBinding: DialogAddTransactionBinding
    private lateinit var categoryHelper: TransactionCategoryHelper
    private var alertDialog: AlertDialog? = null

    fun create(): Dialog {
        val activeProfileId = com.duorhythm.app.data.local.pref.ProfileManager.getActiveProfileId(context)
        val buckets = dbHelper.getAllWalletBuckets(activeProfileId)
        dialogBinding = DialogAddTransactionBinding.inflate(LayoutInflater.from(context))

        val prefs = context.getSharedPreferences("lifesync_prefs", Context.MODE_PRIVATE)
        val currentCurrency = prefs.getString("selected_currency", "BDT") ?: "BDT"
        val ratesList = dbHelper.getAllCurrencyRates()
        val currentSymbol = ratesList.find { it.code == currentCurrency }?.symbol ?: "৳"
        dialogBinding.tilTxAmount.hint = "Amount ($currentSymbol - $currentCurrency)"

        selectedType = initialType

        // 2. Observe / Load Master Categories
        loadMasterCategories()

        // Setup Category Helper
        categoryHelper = TransactionCategoryHelper(
            context = context,
            dbHelper = dbHelper,
            actvCategory = dialogBinding.actvTxCategory,
            currentTxType = if (selectedType == TransactionType.TRANSFER) "EXPENSE" else selectedType.name.uppercase(),
            chipCreateCategory = dialogBinding.chipCreateCategory
        )

        // Setup Buckets / Accounts
        val bucketNames = buckets.map { it.name }
        val bucketAdapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, bucketNames)
        dialogBinding.actvTxCard.setAdapter(bucketAdapter)

        val lastUsedBucketId = prefs.getLong("last_used_wallet_account_id", -1L)
        val defaultBucket = targetBucket ?: (buckets.find { it.id == lastUsedBucketId } ?: buckets.firstOrNull())
        if (defaultBucket != null) {
            dialogBinding.actvTxCard.setText(defaultBucket.name, false)
        }
        dialogBinding.actvTxCard.setOnClickListener { dialogBinding.actvTxCard.showDropDown() }

        fun updateToAccountDropdown() {
            val selectedFromName = dialogBinding.actvTxCard.text?.toString()?.trim().orEmpty()
            val destBuckets = buckets.filterNot { it.name.equals(selectedFromName, ignoreCase = true) }
            val destNames = destBuckets.map { it.name }
            val toAdapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, destNames)
            dialogBinding.actvTxToAccount.setAdapter(toAdapter)

            val currentToName = dialogBinding.actvTxToAccount.text?.toString()?.trim().orEmpty()
            if (currentToName.isEmpty() || currentToName.equals(selectedFromName, ignoreCase = true)) {
                if (destNames.isNotEmpty()) {
                    dialogBinding.actvTxToAccount.setText(destNames.first(), false)
                } else {
                    dialogBinding.actvTxToAccount.setText("", false)
                }
            }
        }

        updateToAccountDropdown()
        dialogBinding.actvTxToAccount.setOnClickListener { dialogBinding.actvTxToAccount.showDropDown() }
        dialogBinding.actvTxCard.setOnItemClickListener { _, _, _, _ -> updateToAccountDropdown() }

        // Setup Toggle Buttons
        when (selectedType) {
            TransactionType.TRANSFER -> dialogBinding.toggleTxType.check(R.id.btnTypeTransfer)
            TransactionType.EXPENSE -> dialogBinding.toggleTxType.check(R.id.btnTypeExpense)
            TransactionType.INCOME -> dialogBinding.toggleTxType.check(R.id.btnTypeIncome)
        }
        updateTypeUI(selectedType)

        dialogBinding.toggleTxType.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val newType = when (checkedId) {
                    R.id.btnTypeTransfer -> TransactionType.TRANSFER
                    R.id.btnTypeExpense -> TransactionType.EXPENSE
                    else -> TransactionType.INCOME
                }
                onTypeChanged(newType)
            }
        }

        val dialog = AlertDialog.Builder(context)
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        dialogBinding.btnSaveTx.setOnClickListener {
            handleSaveTransaction(dialog, buckets, currentCurrency)
        }

        alertDialog = dialog
        return dialog
    }

    fun show(): Dialog {
        val dialog = create()
        dialog.show()
        dialogBinding.etTxAmount.requestFocus()
        return dialog
    }

    private fun loadMasterCategories() {
        masterCategoryList = try {
            categoryDao.getAllCategories()
        } catch (_: Exception) {
            dbHelper.getAllTransactionCategories(null).map { CategoryEntity.fromItem(it) }
        }
    }

    /**
     * 3. Re-filter from Master on Tab Switch:
     * Whenever the user clicks "INCOME", "EXPENSE", or "TRANSFER":
     * - Determine current selected type in uppercase:
     *   val currentType = selectedType.name.uppercase() // "INCOME" or "EXPENSE"
     * - Always filter from masterCategoryList:
     *   val filtered = masterCategoryList.filter {
     *       it.type.equals(currentType, ignoreCase = true)
     *   }
     * - Update the Category AutoCompleteTextView / Spinner adapter with filtered.
     * - If filtered is not empty:
     *   Set selection to the first available category in filtered (or clear text if none).
     * - If filtered is empty:
     *   Clear category text field and only display "+ Add New Category" and "Manage Categories" in the dropdown.
     *
     * 4. Prevent Selection Bleed:
     * When switching from INCOME to EXPENSE, clear the previously selected INCOME category text so an invalid category is not retained.
     */
    fun onTypeChanged(newType: TransactionType) {
        selectedType = newType
        updateTypeUI(newType)

        if (newType == TransactionType.TRANSFER) {
            return
        }

        val currentType = selectedType.name.uppercase()

        // Always re-filter from masterCategoryList without mutating source data
        val filtered = masterCategoryList.filter {
            it.type.equals(currentType, ignoreCase = true) || it.type.equals("BOTH", ignoreCase = true)
        }

        categoryHelper.setTxType(currentType)
    }

    private fun updateTypeUI(type: TransactionType) {
        when (type) {
            TransactionType.TRANSFER -> {
                dialogBinding.tilTxCard.visibility = View.VISIBLE
                dialogBinding.tilTxCard.hint = "From Account"
                dialogBinding.tilTxToAccount.visibility = View.VISIBLE
                dialogBinding.tilTxCategory.visibility = View.GONE
                dialogBinding.chipCreateCategory.visibility = View.GONE
                dialogBinding.btnSaveTx.text = "Execute Transfer"
            }
            TransactionType.EXPENSE -> {
                dialogBinding.tilTxCard.visibility = if (targetBucket != null) View.GONE else View.VISIBLE
                dialogBinding.tilTxCard.hint = "From Account"
                dialogBinding.tilTxToAccount.visibility = View.GONE
                dialogBinding.tilTxCategory.visibility = View.VISIBLE
                dialogBinding.btnSaveTx.text = "Save Record"
            }
            TransactionType.INCOME -> {
                dialogBinding.tilTxCard.visibility = if (targetBucket != null) View.GONE else View.VISIBLE
                dialogBinding.tilTxCard.hint = "To Account"
                dialogBinding.tilTxToAccount.visibility = View.GONE
                dialogBinding.tilTxCategory.visibility = View.VISIBLE
                dialogBinding.btnSaveTx.text = "Save Record"
            }
        }
        applyToggleStyles(dialogBinding, type.name.uppercase())
    }

    private fun applyToggleStyles(binding: DialogAddTransactionBinding, mode: String) {
        val selectedGreen = Color.parseColor("#10B981")
        val selectedRed = Color.parseColor("#EF4444")
        val selectedPurple = Color.parseColor("#8B5CF6")
        val unselectedText = Color.parseColor("#94A3B8")
        val unselectedStroke = Color.parseColor("#334155")
        val transparent = Color.TRANSPARENT

        val incomeBg = if (mode == "INCOME") Color.parseColor("#1A10B981") else transparent
        val incomeText = if (mode == "INCOME") selectedGreen else unselectedText
        val incomeStroke = if (mode == "INCOME") selectedGreen else unselectedStroke
        binding.btnTypeIncome.setBackgroundColor(incomeBg)
        binding.btnTypeIncome.setTextColor(incomeText)
        binding.btnTypeIncome.strokeColor = ColorStateList.valueOf(incomeStroke)

        val expenseBg = if (mode == "EXPENSE") Color.parseColor("#1AEF4444") else transparent
        val expenseText = if (mode == "EXPENSE") selectedRed else unselectedText
        val expenseStroke = if (mode == "EXPENSE") selectedRed else unselectedStroke
        binding.btnTypeExpense.setBackgroundColor(expenseBg)
        binding.btnTypeExpense.setTextColor(expenseText)
        binding.btnTypeExpense.strokeColor = ColorStateList.valueOf(expenseStroke)

        val transferBg = if (mode == "TRANSFER") Color.parseColor("#1A8B5CF6") else transparent
        val transferText = if (mode == "TRANSFER") selectedPurple else unselectedText
        val transferStroke = if (mode == "TRANSFER") selectedPurple else unselectedStroke
        binding.btnTypeTransfer.setBackgroundColor(transferBg)
        binding.btnTypeTransfer.setTextColor(transferText)
        binding.btnTypeTransfer.strokeColor = ColorStateList.valueOf(transferStroke)
    }

    private fun handleSaveTransaction(dialog: AlertDialog, buckets: List<WalletBucketItem>, currentCurrency: String) {
        val amountStr = dialogBinding.etTxAmount.text?.toString()?.trim().orEmpty()
        if (amountStr.isEmpty()) {
            Toast.makeText(context, "Please enter an amount", Toast.LENGTH_SHORT).show()
            dialogBinding.etTxAmount.requestFocus()
            return
        }

        val enteredAmount = amountStr.toDoubleOrNull() ?: 0.0
        if (enteredAmount <= 0.0) {
            Toast.makeText(context, "Please enter a valid amount greater than 0", Toast.LENGTH_SHORT).show()
            return
        }

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val notes = dialogBinding.etTxNotes.text?.toString()?.trim().orEmpty()
        val customTitle = dialogBinding.etTxTitle.text?.toString()?.trim().orEmpty()

        if (selectedType == TransactionType.TRANSFER) {
            if (buckets.size < 2) {
                Toast.makeText(context, "Transfer requires at least 2 accounts", Toast.LENGTH_SHORT).show()
                return
            }
            val fromName = dialogBinding.actvTxCard.text?.toString()?.trim().orEmpty()
            val toName = dialogBinding.actvTxToAccount.text?.toString()?.trim().orEmpty()

            val fromBucket = buckets.find { it.name.equals(fromName, ignoreCase = true) }
            val toBucket = buckets.find { it.name.equals(toName, ignoreCase = true) }

            if (fromBucket == null || toBucket == null || fromBucket.id == toBucket.id) {
                Toast.makeText(context, "Select two different accounts for transfer", Toast.LENGTH_SHORT).show()
                return
            }

            val transferTitle = customTitle.ifBlank { "Transfer: ${fromBucket.name} ➔ ${toBucket.name}" }
            dbHelper.executeTransfer(
                fromBucketId = fromBucket.id,
                toBucketId = toBucket.id,
                amount = enteredAmount,
                date = today,
                currency = currentCurrency,
                note = notes,
                title = transferTitle
            )
            Toast.makeText(context, "Transfer completed successfully", Toast.LENGTH_SHORT).show()
        } else {
            val accountName = dialogBinding.actvTxCard.text?.toString()?.trim().orEmpty()
            val chosenBucket = buckets.find { it.name.equals(accountName, ignoreCase = true) }
                ?: buckets.firstOrNull()

            val chosenCategory = categoryHelper.getSelectedCategoryAndEnsurePersisted()
            val finalCategory = chosenCategory.ifBlank { "General" }
            val finalTitle = customTitle.ifBlank { finalCategory }

            val activeProfileId = com.duorhythm.app.data.local.pref.ProfileManager.getActiveProfileId(context)
            val tx = TransactionItem(
                profileId = activeProfileId,
                title = finalTitle,
                category = finalCategory,
                amount = enteredAmount,
                type = selectedType.name.uppercase(),
                date = today,
                notes = notes,
                cardId = 0L,
                currency = currentCurrency,
                bucketId = chosenBucket?.id ?: 0L
            )
            val newId = dbHelper.insertTransaction(tx)
            val savedTx = tx.copy(id = newId)

            val prefs = context.getSharedPreferences("lifesync_prefs", Context.MODE_PRIVATE)
            if (chosenBucket != null) {
                prefs.edit().putLong("last_used_wallet_account_id", chosenBucket.id).apply()
            }

            onTransactionSaved?.invoke(savedTx)
            WalletDataSyncManager.notifyChanged()
            Toast.makeText(context, "${selectedType.name} recorded in ${chosenBucket?.name ?: "account"}", Toast.LENGTH_SHORT).show()
        }
        dialog.dismiss()
    }

    companion object {
        fun show(
            context: Context,
            initialType: String = "INCOME",
            targetBucket: WalletBucketItem? = null,
            onTransactionSaved: ((TransactionItem) -> Unit)? = null
        ): Dialog {
            val type = try {
                TransactionType.valueOf(initialType.uppercase())
            } catch (_: Exception) {
                TransactionType.INCOME
            }
            return AddRecordDialog(context, type, targetBucket, onTransactionSaved).show()
        }
    }
}
