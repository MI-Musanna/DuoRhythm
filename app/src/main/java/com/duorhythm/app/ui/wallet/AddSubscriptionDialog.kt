package com.duorhythm.app.ui.wallet

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Toast
import com.duorhythm.app.R
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.local.pref.ProfileManager
import com.duorhythm.app.databinding.DialogAddSubscriptionBinding
import com.duorhythm.app.scheduler.SubscriptionAlarmScheduler
import database.dao.SubscriptionDao
import database.entities.SubscriptionEntity

class AddSubscriptionDialog(
    private val context: Context,
    private val onSubscriptionAddedListener: (() -> Unit)? = null
) {
    private val dbHelper = DatabaseHelper.getInstance(context)
    private val subscriptionDao: SubscriptionDao = dbHelper.subscriptionDao

    fun show() {
        val binding = DialogAddSubscriptionBinding.inflate(LayoutInflater.from(context))
        val activeProfileId = ProfileManager.getActiveProfileId(context).let { if (it > 0) it else 1L }

        binding.tvSubscriptionDialogTitle.text = "Add Recurring Subscription"
        binding.tvSubscriptionDialogSubtitle.text = "Track recurring tools, memberships, and billing windows."
        binding.btnSaveSubscription.text = "+ Add Subscription"

        val currencies = listOf("BDT", "USD", "EUR", "GBP", "INR")
        binding.spSubCurrency.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, currencies)

        val accounts = dbHelper.accountDao.getAccounts(activeProfileId)
        val sourceOptions = mutableListOf("None / External (Third-Party / Direct)")
        sourceOptions.addAll(accounts.map { it.name })
        binding.spnPaymentSource.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, sourceOptions)

        var isRangeMode = false
        binding.toggleDueMode.check(R.id.btnDueFixed)
        binding.layoutFixedDue.visibility = View.VISIBLE
        binding.layoutRangeDue.visibility = View.GONE

        binding.toggleDueMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                isRangeMode = (checkedId == R.id.btnDueRange)
                binding.layoutFixedDue.visibility = if (isRangeMode) View.GONE else View.VISIBLE
                binding.layoutRangeDue.visibility = if (isRangeMode) View.VISIBLE else View.GONE
            }
        }

        val dialog = AlertDialog.Builder(context)
            .setView(binding.root)
            .create()

        binding.btnSaveSubscription.setOnClickListener {
            val name = binding.etServiceName.text?.toString()?.trim().orEmpty()
            val costStr = binding.etSubCost.text?.toString()?.trim().orEmpty()

            if (name.isEmpty() || costStr.isEmpty()) {
                Toast.makeText(context, "Service name and cost are required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val amount = costStr.toDoubleOrNull() ?: 0.0
            val dueDay = binding.etDueDay.text?.toString()?.toIntOrNull()?.coerceIn(1, 31) ?: 5
            val startDay = binding.etStartDay.text?.toString()?.toIntOrNull()?.coerceIn(1, 31) ?: 1
            val endDay = binding.etEndDay.text?.toString()?.toIntOrNull()?.coerceIn(1, 31) ?: 8

            val selectedSourcePos = binding.spnPaymentSource.selectedItemPosition
            val selectedAccountId: Long? = if (selectedSourcePos > 0 && selectedSourcePos - 1 < accounts.size) {
                accounts[selectedSourcePos - 1].id
            } else {
                null
            }

            val newSub = SubscriptionEntity(
                profileId = activeProfileId, // Ensure NOT 0 or null
                name = name,
                amount = amount,
                isRange = isRangeMode,
                startDay = startDay,
                endDay = endDay,
                dueDay = dueDay,
                linkedAccountId = selectedAccountId,
                lastPaidMonthYear = null
            )

            subscriptionDao.insertSubscription(newSub)
            onSubscriptionAddedListener?.invoke()

            SubscriptionAlarmScheduler.scheduleDailyAlarms(context)
            Toast.makeText(context, "Added '$name' subscription", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }
}
