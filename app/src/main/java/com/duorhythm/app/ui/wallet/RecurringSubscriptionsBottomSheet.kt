package com.duorhythm.app.ui.wallet

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.util.Log
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.local.pref.ProfileManager
import com.duorhythm.app.data.model.TransactionItem
import com.duorhythm.app.data.repository.SubscriptionRepository
import com.duorhythm.app.databinding.DialogRecurringSubscriptionsBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import database.entities.SubscriptionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val DialogRecurringSubscriptionsBinding.btnAddSubscription: View get() = btnAddNewSubscription

class RecurringSubscriptionsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: DialogRecurringSubscriptionsBinding? = null
    val binding get() = _binding!!

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var repository: SubscriptionRepository
    private lateinit var subscriptionsAdapter: SubscriptionsAdapter
    private var currencySymbol: String = "৳"
    private var currencyRate: Double = 1.0
    private var activeProfileId: Long = 1L
    private var flowJob: Job? = null
    var onDataChangedListener: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogRecurringSubscriptionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    fun loadSubscriptions() {
        if (_binding == null || !isAdded) return
        val profileId = ProfileManager.getActiveProfileId()
        val items = DatabaseHelper.getInstance(requireContext()).getSubscriptions(profileId)
        val accounts = dbHelper.accountDao.getAccounts(profileId).associateBy { it.id }
        for (sub in items) {
            if (sub.linkedAccountId != null && sub.linkedAccountId > 0) {
                sub.accountName = accounts[sub.linkedAccountId]?.name ?: "Account"
            }
        }
        subscriptionsAdapter.submitList(ArrayList(items)) {
            if (_binding != null) {
                binding.rvSubscriptions.scrollToPosition(0)
            }
        }
        val totalProjected = items.sumOf { it.amount }
        binding.tvHeaderSubtitle.text = "Total Projected: ৳${totalProjected.toInt()} / mo • ${items.size} Tracked"
        binding.tvEmptyState.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        binding.rvSubscriptions.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val context = requireContext()
        dbHelper = DatabaseHelper.getInstance(context)
        repository = SubscriptionRepository(context)

        currencySymbol = arguments?.getString(ARG_SYMBOL) ?: "৳"
        currencyRate = arguments?.getDouble(ARG_RATE) ?: 1.0

        activeProfileId = ProfileManager.getActiveProfileId(context).let { if (it > 0) it else 1L }

        subscriptionsAdapter = SubscriptionsAdapter(
            emptyList(),
            currencySymbol,
            currencyRate,
            onEditClick = { sub ->
                EditSubscriptionDialog(context, sub) {
                    loadSubscriptions()
                    onDataChangedListener?.invoke()
                }.show()
            },
            onDeleteClick = { sub ->
                confirmDelete(sub)
            },
            onMarkPaidClick = { sub ->
                handleMarkPaid(sub)
            }
        )

        binding.rvSubscriptions.layoutManager = LinearLayoutManager(context)
        binding.rvSubscriptions.adapter = subscriptionsAdapter

        loadSubscriptions()

        binding.btnAddSubscription.setOnClickListener {
            val dialog = AddSubscriptionDialog()
            dialog.onSubscriptionAddedListener = {
                loadSubscriptions()
                onDataChangedListener?.invoke()
            }
            dialog.show(childFragmentManager, "ADD_SUB_DIALOG")
        }

        // Observe reactive database updates
        flowJob = CoroutineScope(Dispatchers.Main).launch {
            try {
                dbHelper.subscriptionUpdateFlow.collect {
                    loadSubscriptions()
                }
            } catch (e: Exception) {
                Log.e("DUO_WALLET", "Safe flow collection catch", e)
            }
        }
    }

    private fun handleMarkPaid(sub: SubscriptionEntity) {
        val currentMonthYear = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
        val isSettledThisMonth = (sub.lastPaidMonthYear == currentMonthYear)

        if (isSettledThisMonth) {
            repository.updatePaidMonth(sub.id, null)
            loadSubscriptions()
            onDataChangedListener?.invoke()
            Toast.makeText(requireContext(), "Unmarked '${sub.name}' as paid", Toast.LENGTH_SHORT).show()
        } else {
            if (sub.linkedAccountId != null && sub.linkedAccountId > 0L) {
                AlertDialog.Builder(requireContext())
                    .setTitle("Record Wallet Expense?")
                    .setMessage("Do you want to deduct ${currencySymbol}${sub.amount.toInt()} from '${sub.accountName}' and log a transaction, or just mark this subscription as paid?")
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
                        repository.updatePaidMonth(sub.id, currentMonthYear)
                        loadSubscriptions()
                        onDataChangedListener?.invoke()
                        Toast.makeText(requireContext(), "Deducted and marked '${sub.name}' as paid!", Toast.LENGTH_SHORT).show()
                    }
                    .setNeutralButton("JUST MARK PAID") { _, _ ->
                        repository.updatePaidMonth(sub.id, currentMonthYear)
                        loadSubscriptions()
                        onDataChangedListener?.invoke()
                        Toast.makeText(requireContext(), "Marked '${sub.name}' as paid!", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } else {
                repository.updatePaidMonth(sub.id, currentMonthYear)
                loadSubscriptions()
                onDataChangedListener?.invoke()
                Toast.makeText(requireContext(), "Marked '${sub.name}' as paid (External payer)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmDelete(sub: SubscriptionEntity) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Subscription")
            .setMessage("Delete '${sub.name}'? This will remove it from tracked recurring expenses.")
            .setPositiveButton("Delete") { _, _ ->
                repository.deleteSubscription(sub.id)
                loadSubscriptions()
                onDataChangedListener?.invoke()
                Toast.makeText(requireContext(), "Deleted '${sub.name}'", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        flowJob?.cancel()
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_SYMBOL = "arg_symbol"
        private const val ARG_RATE = "arg_rate"

        fun newInstance(symbol: String = "৳", rate: Double = 1.0): RecurringSubscriptionsBottomSheet {
            return RecurringSubscriptionsBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_SYMBOL, symbol)
                    putDouble(ARG_RATE, rate)
                }
            }
        }
    }
}
