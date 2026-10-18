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
import com.duorhythm.app.data.model.SubscriptionItem
import com.duorhythm.app.databinding.DialogAddSubscriptionBinding
import com.duorhythm.app.databinding.DialogSubscriptionManagerBinding
import java.text.SimpleDateFormat
import java.util.Calendar
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

    private fun confirmDeleteSubscription(sub: SubscriptionItem, binding: DialogSubscriptionManagerBinding) {
        AlertDialog.Builder(context)
            .setTitle("Delete Subscription")
            .setMessage("Delete '${sub.serviceName}'? This will remove it from tracked recurring expenses.")
            .setPositiveButton("Delete") { _, _ ->
                dbHelper.deleteSubscription(sub.id)
                loadSubscriptions(binding)
                onDataChanged()
                Toast.makeText(context, "Deleted '${sub.serviceName}'", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadSubscriptions(binding: DialogSubscriptionManagerBinding) {
        val list = dbHelper.getAllSubscriptions()
        adapter.updateData(list, currentSymbol, currentRate)

        val totalMonthlyBDT = list.filter { it.isActive }.sumOf { sub ->
            sub.getMonthlyEquivalentCost()
        }

        binding.tvSubManagerSummary.text = String.format(
            Locale.US,
            "Total Projected: %s%.0f / mo • %d Active",
            currentSymbol,
            totalMonthlyBDT,
            list.count { it.isActive }
        )

        if (list.isEmpty()) {
            binding.rvManagerSubscriptions.visibility = View.GONE
            binding.tvEmptySubscriptions.visibility = View.VISIBLE
        } else {
            binding.rvManagerSubscriptions.visibility = View.VISIBLE
            binding.tvEmptySubscriptions.visibility = View.GONE
        }
    }

    fun showAddOrEditSubscriptionDialog(subToEdit: SubscriptionItem? = null, onSaved: () -> Unit) {
        val addBinding = DialogAddSubscriptionBinding.inflate(LayoutInflater.from(context))

        val isEdit = subToEdit != null
        if (isEdit) {
            addBinding.tvSubscriptionDialogTitle.text = "Edit Subscription"
            addBinding.tvSubscriptionDialogSubtitle.text = "Update cost, renewal date, or linked deducting bucket."
            addBinding.btnSaveSubscription.text = "Save Changes"
        }

        val currencies = listOf("BDT", "USD", "EUR", "GBP", "INR")
        val currencyAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, currencies)
        addBinding.spSubCurrency.adapter = currencyAdapter

        // Dynamically fetch all created wallet buckets
        val buckets = dbHelper.getAllWalletBuckets()
        if (buckets.isEmpty()) {
            AlertDialog.Builder(context)
                .setTitle("Account Required")
                .setMessage("Please set up at least one account (e.g., Bank, bKash, Card) before adding a recurring subscription.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val bucketOptions = buckets.map { it.name }
        val bucketAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, bucketOptions)
        addBinding.spLinkedBucket.adapter = bucketAdapter

        // Default renewal date (30 days from now)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, 30)
        val defaultDate = sdf.format(cal.time)

        var billingCycle = subToEdit?.billingCycle ?: "MONTHLY"

        if (subToEdit != null) {
            addBinding.etServiceName.setText(subToEdit.serviceName)
            val costStr = if (subToEdit.cost % 1.0 == 0.0) {
                subToEdit.cost.toLong().toString()
            } else {
                String.format(Locale.US, "%.2f", subToEdit.cost)
            }
            addBinding.etSubCost.setText(costStr)
            val currIdx = currencies.indexOf(subToEdit.currency).coerceAtLeast(0)
            addBinding.spSubCurrency.setSelection(currIdx)
            addBinding.etRenewalDate.setText(subToEdit.renewalDate)

            // Select linked bucket in spinner
            val bucketIdx = if (subToEdit.bucketName.isNotBlank()) {
                bucketOptions.indexOf(subToEdit.bucketName).takeIf { it >= 0 }
                    ?: (buckets.indexOfFirst { it.id == subToEdit.bucketId }.takeIf { it >= 0 } ?: 0)
            } else {
                0
            }
            addBinding.spLinkedBucket.setSelection(bucketIdx)

            if (subToEdit.billingCycle.equals("YEARLY", ignoreCase = true)) {
                addBinding.toggleBillingCycle.check(R.id.btnCycleYearly)
                billingCycle = "YEARLY"
            } else {
                addBinding.toggleBillingCycle.check(R.id.btnCycleMonthly)
                billingCycle = "MONTHLY"
            }
        } else {
            addBinding.etRenewalDate.setText(defaultDate)
        }

        addBinding.toggleBillingCycle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                billingCycle = if (checkedId == R.id.btnCycleYearly) "YEARLY" else "MONTHLY"
            }
        }

        val addDialog = AlertDialog.Builder(context)
            .setView(addBinding.root)
            .create()

        addBinding.btnSaveSubscription.setOnClickListener {
            val name = addBinding.etServiceName.text?.toString()?.trim().orEmpty()
            val costStr = addBinding.etSubCost.text?.toString()?.trim().orEmpty()
            val date = addBinding.etRenewalDate.text?.toString()?.trim().orEmpty()

            if (name.isEmpty() || costStr.isEmpty()) {
                Toast.makeText(context, "Service name and cost are required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val cost = costStr.toDoubleOrNull() ?: 0.0
            val currency = currencies[addBinding.spSubCurrency.selectedItemPosition]
            val matchedBucket = buckets.getOrNull(addBinding.spLinkedBucket.selectedItemPosition)
            if (matchedBucket == null) {
                Toast.makeText(context, "Please select an account for subscription deductions", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val bucketId = matchedBucket.id
            val finalBucketName = matchedBucket.name

            val category = when (name.lowercase(Locale.US)) {
                "chatgpt", "chatgpt plus", "claude", "claude ai" -> "AI / Tech"
                "youtube", "youtube premium", "netflix" -> "Entertainment"
                "spotify" -> "Music"
                "google one", "icloud" -> "Cloud Storage"
                else -> "Subscription"
            }

            if (subToEdit != null) {
                val updated = subToEdit.copy(
                    serviceName = name,
                    cost = cost,
                    currency = currency,
                    billingCycle = billingCycle,
                    renewalDate = if (date.isNotBlank()) date else defaultDate,
                    bucketId = bucketId,
                    bucketName = finalBucketName,
                    category = category
                )
                dbHelper.updateSubscription(updated)
                Toast.makeText(context, "Updated '$name'", Toast.LENGTH_SHORT).show()
            } else {
                val sub = SubscriptionItem(
                    serviceName = name,
                    cost = cost,
                    currency = currency,
                    billingCycle = billingCycle,
                    renewalDate = if (date.isNotBlank()) date else defaultDate,
                    bucketId = bucketId,
                    bucketName = finalBucketName,
                    category = category,
                    icon = "subscription",
                    isActive = true
                )
                dbHelper.insertSubscription(sub)
                Toast.makeText(context, "Added '$name' subscription", Toast.LENGTH_SHORT).show()
            }

            addDialog.dismiss()
            onSaved()
        }

        addDialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        addDialog.show()
        addDialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }
}
