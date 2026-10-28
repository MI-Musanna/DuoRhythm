package com.duorhythm.app.ui.wallet

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.duorhythm.app.R
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.local.pref.ProfileManager
import com.duorhythm.app.data.model.TransactionItem
import com.duorhythm.app.databinding.DialogAddSubscriptionBinding
import com.duorhythm.app.databinding.DialogSubscriptionManagerBinding
import com.duorhythm.app.scheduler.SubscriptionAlarmScheduler
import database.entities.SubscriptionEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SubscriptionManagerDialog(
    private val context: Context,
    private val currentSymbol: String,
    private val currentRate: Double,
    private val onDataChanged: () -> Unit
) {

    private val dbHelper = DatabaseHelper.getInstance(context)
    private lateinit var adapter: SubscriptionsAdapter
    private var dialog: AlertDialog? = null

    fun show() {
        val binding = DialogSubscriptionManagerBinding.inflate(LayoutInflater.from(context))
        adapter = SubscriptionsAdapter(
            emptyList(),
            currentSymbol,
            currentRate,
            onEditClick = { sub ->
                showAddOrEditSubscriptionDialog(sub) {
                    loadSubscriptions(binding)
                    onDataChanged()
                }
            },
            onDeleteClick = { sub ->
                confirmDeleteSubscription(sub, binding)
            },
            onMarkPaidClick = { sub ->
                handleMarkPaidClick(sub, binding)
            }
        )

        binding.rvManagerSubscriptions.layoutManager = LinearLayoutManager(context)
        binding.rvManagerSubscriptions.adapter = adapter

        binding.btnAddNewSubscription.setOnClickListener {
            showAddOrEditSubscriptionDialog(null) {
                loadSubscriptions(binding)
                onDataChanged()
            }
        }

        loadSubscriptions(binding)

        dialog = AlertDialog.Builder(context)
            .setView(binding.root)
            .create()
        dialog?.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog?.let { com.duorhythm.app.util.AnimationUtils.applyDialogAnimation(it) }
        com.duorhythm.app.util.AnimationUtils.attachTactileFeedback(binding.btnAddNewSubscription)
        dialog?.show()
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    private fun handleMarkPaidClick(sub: SubscriptionEntity, binding: DialogSubscriptionManagerBinding) {
        val currentMonthYear = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
        val isSettledThisMonth = (sub.lastPaidMonthYear == currentMonthYear)

        if (isSettledThisMonth) {
            // Uncheck / unmark paid
            dbHelper.subscriptionDao.updatePaidMonth(sub.id, null)
            loadSubscriptions(binding)
            onDataChanged()
            Toast.makeText(context, "Unmarked '${sub.name}' as paid", Toast.LENGTH_SHORT).show()
        } else {
            // Currently unpaid
            if (sub.linkedAccountId != null && sub.linkedAccountId > 0L) {
                // Prompt confirmation dialog: DEDUCT & MARK PAID or JUST MARK PAID
                AlertDialog.Builder(context)
                    .setTitle("Record Wallet Expense?")
                    .setMessage("Do you want to deduct ${currentSymbol}${sub.amount.toInt()} from '${sub.accountName}' and log a transaction, or just mark this subscription as paid?")
                    .setPositiveButton("DEDUCT & MARK PAID") { _, _ ->
                        val tx = TransactionItem(
                            profileId = sub.profileId,
                            title = "Subscription: ${sub.name}",
                            amount = sub.amount,
                            type = "EXPENSE",
                            category = "Subscription",
                            date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
                            currency = "BDT",
                            bucketId = sub.linkedAccountId,
                            notes = "Auto-logged subscription deduction"
                        )
                        dbHelper.insertTransaction(tx)
                        dbHelper.subscriptionDao.updatePaidMonth(sub.id, currentMonthYear)
                        WalletDataSyncManager.notifyChanged()
                        loadSubscriptions(binding)
                        onDataChanged()
                        Toast.makeText(context, "Deducted and marked '${sub.name}' as paid!", Toast.LENGTH_SHORT).show()
                    }
                    .setNeutralButton("JUST MARK PAID") { _, _ ->
                        dbHelper.subscriptionDao.updatePaidMonth(sub.id, currentMonthYear)
                        loadSubscriptions(binding)
                        onDataChanged()
                        Toast.makeText(context, "Marked '${sub.name}' as paid!", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } else {
                // External payer / No linked account -> mark paid immediately without deduction
                dbHelper.subscriptionDao.updatePaidMonth(sub.id, currentMonthYear)
                loadSubscriptions(binding)
                onDataChanged()
                Toast.makeText(context, "Marked '${sub.name}' as paid (External payer)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmDeleteSubscription(sub: SubscriptionEntity, binding: DialogSubscriptionManagerBinding) {
        AlertDialog.Builder(context)
            .setTitle("Delete Subscription")
            .setMessage("Delete '${sub.name}'? This will remove it from tracked recurring expenses.")
            .setPositiveButton("Delete") { _, _ ->
                dbHelper.subscriptionDao.deleteSubscription(sub.id)
                loadSubscriptions(binding)
                onDataChanged()
                Toast.makeText(context, "Deleted '${sub.name}'", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadSubscriptions(binding: DialogSubscriptionManagerBinding) {
        val profileId = ProfileManager.getActiveProfileId(context)
        val list = dbHelper.subscriptionDao.getSubscriptions(profileId)

        val accounts = dbHelper.accountDao.getAccounts(profileId).associateBy { it.id }
        for (sub in list) {
            if (sub.linkedAccountId != null && sub.linkedAccountId > 0) {
                sub.accountName = accounts[sub.linkedAccountId]?.name ?: "Account"
            }
        }

        adapter.updateData(list, currentSymbol, currentRate)

        val totalProjected = list.sumOf { it.amount }
        binding.tvSubManagerSummary.text = String.format(
            Locale.US,
            "Total Projected: %s%.0f / mo • %d Tracked",
            currentSymbol,
            totalProjected,
            list.size
        )

        if (list.isEmpty()) {
            binding.rvManagerSubscriptions.visibility = View.GONE
            binding.tvEmptySubscriptions.visibility = View.VISIBLE
        } else {
            binding.rvManagerSubscriptions.visibility = View.VISIBLE
            binding.tvEmptySubscriptions.visibility = View.GONE
        }
    }

    fun showAddOrEditSubscriptionDialog(subToEdit: SubscriptionEntity? = null, onSaved: () -> Unit) {
        val addBinding = DialogAddSubscriptionBinding.inflate(LayoutInflater.from(context))
        val profileId = ProfileManager.getActiveProfileId(context)

        val isEdit = subToEdit != null
        if (isEdit) {
            addBinding.tvSubscriptionDialogTitle.text = "Edit Subscription"
            addBinding.tvSubscriptionDialogSubtitle.text = "Update amount, due date range, or payment source."
            addBinding.btnSaveSubscription.text = "Save Changes"
        }

        val currencies = listOf("BDT", "USD", "EUR", "GBP", "INR")
        val currencyAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, currencies)
        addBinding.spSubCurrency.adapter = currencyAdapter

        // Fetch accounts for payment source
        val accounts = dbHelper.accountDao.getAccounts(profileId)
        val sourceOptions = mutableListOf("None / External (Father / Third-Party)")
        sourceOptions.addAll(accounts.map { it.name })

        val sourceAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, sourceOptions)
        addBinding.spnPaymentSource.adapter = sourceAdapter

        // Due Mode Toggle
        var isRangeMode = subToEdit?.isRange ?: false
        if (isRangeMode) {
            addBinding.toggleDueMode.check(R.id.btnDueRange)
            addBinding.layoutFixedDue.visibility = View.GONE
            addBinding.layoutRangeDue.visibility = View.VISIBLE
        } else {
            addBinding.toggleDueMode.check(R.id.btnDueFixed)
            addBinding.layoutFixedDue.visibility = View.VISIBLE
            addBinding.layoutRangeDue.visibility = View.GONE
        }

        addBinding.toggleDueMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                isRangeMode = (checkedId == R.id.btnDueRange)
                if (isRangeMode) {
                    addBinding.layoutFixedDue.visibility = View.GONE
                    addBinding.layoutRangeDue.visibility = View.VISIBLE
                } else {
                    addBinding.layoutFixedDue.visibility = View.VISIBLE
                    addBinding.layoutRangeDue.visibility = View.GONE
                }
            }
        }

        if (subToEdit != null) {
            addBinding.etServiceName.setText(subToEdit.name)
            val costStr = if (subToEdit.amount % 1.0 == 0.0) {
                subToEdit.amount.toLong().toString()
            } else {
                String.format(Locale.US, "%.2f", subToEdit.amount)
            }
            addBinding.etSubCost.setText(costStr)
            addBinding.etDueDay.setText(subToEdit.dueDay.toString())
            addBinding.etStartDay.setText(subToEdit.startDay.toString())
            addBinding.etEndDay.setText(subToEdit.endDay.toString())

            // Select payment source
            val selectedIdx = if (subToEdit.linkedAccountId != null && subToEdit.linkedAccountId > 0L) {
                val accIdx = accounts.indexOfFirst { it.id == subToEdit.linkedAccountId }
                if (accIdx >= 0) accIdx + 1 else 0
            } else {
                0
            }
            addBinding.spnPaymentSource.setSelection(selectedIdx)
        }

        val addDialog = AlertDialog.Builder(context)
            .setView(addBinding.root)
            .create()

        addBinding.btnSaveSubscription.setOnClickListener {
            val name = addBinding.etServiceName.text?.toString()?.trim().orEmpty()
            val costStr = addBinding.etSubCost.text?.toString()?.trim().orEmpty()

            if (name.isEmpty() || costStr.isEmpty()) {
                Toast.makeText(context, "Service name and cost are required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val amount = costStr.toDoubleOrNull() ?: 0.0
            val dueDay = addBinding.etDueDay.text?.toString()?.toIntOrNull()?.coerceIn(1, 31) ?: 5
            val startDay = addBinding.etStartDay.text?.toString()?.toIntOrNull()?.coerceIn(1, 31) ?: 1
            val endDay = addBinding.etEndDay.text?.toString()?.toIntOrNull()?.coerceIn(1, 31) ?: 8

            val selectedSourcePos = addBinding.spnPaymentSource.selectedItemPosition
            val linkedAccountId: Long? = if (selectedSourcePos > 0 && selectedSourcePos - 1 < accounts.size) {
                accounts[selectedSourcePos - 1].id
            } else {
                null
            }

            if (subToEdit != null) {
                val updated = subToEdit.copy(
                    name = name,
                    amount = amount,
                    isRange = isRangeMode,
                    startDay = startDay,
                    endDay = endDay,
                    dueDay = dueDay,
                    linkedAccountId = linkedAccountId
                )
                dbHelper.subscriptionDao.updateSubscription(updated)
                Toast.makeText(context, "Updated '$name'", Toast.LENGTH_SHORT).show()
            } else {
                val newSub = SubscriptionEntity(
                    profileId = profileId,
                    name = name,
                    amount = amount,
                    isRange = isRangeMode,
                    startDay = startDay,
                    endDay = endDay,
                    dueDay = dueDay,
                    linkedAccountId = linkedAccountId
                )
                dbHelper.subscriptionDao.insertSubscription(newSub)
                Toast.makeText(context, "Added '$name' subscription", Toast.LENGTH_SHORT).show()
            }

            SubscriptionAlarmScheduler.scheduleDailyAlarms(context)
            addDialog.dismiss()
            onSaved()
        }

        addDialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        addDialog.show()
        addDialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }
}
