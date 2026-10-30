package com.duorhythm.app.ui.wallet

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.duorhythm.app.R
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.local.pref.ProfileManager
import com.duorhythm.app.databinding.DialogAddSubscriptionBinding
import com.duorhythm.app.scheduler.SubscriptionAlarmScheduler
import database.entities.SubscriptionEntity

val DialogAddSubscriptionBinding.etCostAmount: com.google.android.material.textfield.TextInputEditText get() = etSubCost

class AddSubscriptionDialog : DialogFragment {

    private var customContext: Context? = null
    var onSubscriptionAddedListener: (() -> Unit)? = null

    constructor() : super()

    constructor(context: Context, listener: (() -> Unit)? = null) : super() {
        this.customContext = context
        this.onSubscriptionAddedListener = listener
    }

    fun show() {
        val ctx = customContext ?: try { com.duorhythm.app.LifeSyncApp.instance } catch (_: Throwable) { null }
        var current: Context? = ctx
        while (current != null) {
            if (current is androidx.fragment.app.FragmentActivity) {
                show(current.supportFragmentManager, "ADD_SUB_DIALOG")
                return
            }
            current = (current as? ContextWrapper)?.baseContext
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val ctx = context ?: customContext ?: requireContext()
        val binding = DialogAddSubscriptionBinding.inflate(LayoutInflater.from(ctx))
        val dbHelper = DatabaseHelper.getInstance(ctx)
        val activeProfileId = ProfileManager.getActiveProfileId(ctx).let { if (it > 0) it else 1L }

        binding.tvSubscriptionDialogTitle.text = "Add Recurring Subscription"
        binding.tvSubscriptionDialogSubtitle.text = "Track recurring tools, memberships, and billing windows."
        binding.btnSaveSubscription.text = "+ Add Subscription"

        val currencies = listOf("BDT", "USD", "EUR", "GBP", "INR")
        binding.spSubCurrency.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, currencies)

        val accounts = dbHelper.accountDao.getAccounts(activeProfileId)
        val sourceOptions = mutableListOf("None / External (Third-Party)")
        sourceOptions.addAll(accounts.map { it.name })
        binding.spnPaymentSource.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, sourceOptions)

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

        binding.btnSaveSubscription.setOnClickListener {
            val name = binding.etServiceName.text?.toString()?.trim().orEmpty()
            val costStr = binding.etCostAmount.text?.toString()?.trim().orEmpty()

            if (name.isEmpty() || costStr.isEmpty()) {
                Toast.makeText(requireContext(), "Service name and cost are required", Toast.LENGTH_SHORT).show()
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

            val currentProfileId = ProfileManager.getActiveProfileId()
            val sub = SubscriptionEntity(
                profileId = currentProfileId,
                name = binding.etServiceName.text.toString().trim(),
                amount = binding.etCostAmount.text.toString().toDoubleOrNull() ?: 0.0,
                isRange = isRangeMode,
                startDay = startDay,
                endDay = endDay,
                dueDay = dueDay,
                linkedAccountId = selectedAccountId,
                lastPaidMonthYear = null
            )

            val rowId = DatabaseHelper.getInstance(requireContext()).insertSubscription(sub)
            if (rowId > 0L) {
                Toast.makeText(requireContext(), "Added '${sub.name}' subscription", Toast.LENGTH_SHORT).show()
                onSubscriptionAddedListener?.invoke()
                SubscriptionAlarmScheduler.scheduleDailyAlarms(requireContext())
                dismiss()
            } else {
                Toast.makeText(requireContext(), "Failed to save subscription. Check logcat.", Toast.LENGTH_SHORT).show()
            }
        }

        val dialog = AlertDialog.Builder(ctx)
            .setView(binding.root)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        return dialog
    }
}
