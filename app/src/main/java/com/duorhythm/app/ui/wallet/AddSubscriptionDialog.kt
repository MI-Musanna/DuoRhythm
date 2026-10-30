package com.duorhythm.app.ui.wallet

import android.app.AlertDialog
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.duorhythm.app.R
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.local.pref.ProfileManager
import com.duorhythm.app.databinding.DialogAddSubscriptionBinding
import com.duorhythm.app.scheduler.SubscriptionAlarmScheduler
import database.dao.SubscriptionDao
import database.entities.SubscriptionEntity

val DialogAddSubscriptionBinding.etCostAmount: com.google.android.material.textfield.TextInputEditText get() = etSubCost

class AddSubscriptionDialog(
    private val context: Context,
    private val onSubscriptionAddedListener: (() -> Unit)? = null
) {
    private val dbHelper = DatabaseHelper.getInstance(context)
    private val subscriptionDao: SubscriptionDao = dbHelper.subscriptionDao

    private val walletViewModel: WalletViewModel by activityViewModels()

    private val sessionManager = object {
        fun getActiveProfileId(): Long =
            ProfileManager.getActiveProfileId(context).let { if (it > 0) it else 1L }
    }

    fun show() {
        val binding = DialogAddSubscriptionBinding.inflate(LayoutInflater.from(context))
        val activeProfileId = sessionManager.getActiveProfileId()

        binding.tvSubscriptionDialogTitle.text = "Add Recurring Subscription"
        binding.tvSubscriptionDialogSubtitle.text = "Track recurring tools, memberships, and billing windows."
        binding.btnSaveSubscription.text = "+ Add Subscription"

        val currencies = listOf("BDT", "USD", "EUR", "GBP", "INR")
        binding.spSubCurrency.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, currencies)

        val accounts = dbHelper.accountDao.getAccounts(activeProfileId)
        val sourceOptions = mutableListOf("None / External (Third-Party)")
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
            val costStr = binding.etCostAmount.text?.toString()?.trim().orEmpty()

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

            val sub = SubscriptionEntity(
                profileId = sessionManager.getActiveProfileId(),
                name = name,
                amount = amount,
                isRange = isRangeMode,
                startDay = startDay,
                endDay = endDay,
                dueDay = dueDay,
                linkedAccountId = selectedAccountId,
                lastPaidMonthYear = null
            )

            walletViewModel.addSubscription(sub)
            onSubscriptionAddedListener?.invoke()

            SubscriptionAlarmScheduler.scheduleDailyAlarms(context)
            Toast.makeText(context, "Added '${sub.name}' subscription", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    private inline fun <reified VM : androidx.lifecycle.ViewModel> activityViewModels(): Lazy<VM> = lazy {
        var current: Context? = context
        var owner: ViewModelStoreOwner? = null
        while (current != null) {
            if (current is ViewModelStoreOwner) {
                owner = current
                break
            }
            current = (current as? ContextWrapper)?.baseContext
        }
        if (owner == null && context is ViewModelStoreOwner) {
            owner = context
        }
        if (owner != null) {
            ViewModelProvider(owner)[VM::class.java]
        } else {
            val app = (context.applicationContext as? android.app.Application)
                ?: com.duorhythm.app.LifeSyncApp.instance
            ViewModelProvider.AndroidViewModelFactory.getInstance(app).create(VM::class.java)
        }
    }
}
