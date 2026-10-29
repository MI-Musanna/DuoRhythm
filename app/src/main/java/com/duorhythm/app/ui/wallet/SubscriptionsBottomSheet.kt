package com.duorhythm.app.ui.wallet

import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.local.pref.ProfileManager
import com.duorhythm.app.data.model.TransactionItem
import com.duorhythm.app.databinding.DialogSubscriptionManagerBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import database.entities.SubscriptionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SubscriptionsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: DialogSubscriptionManagerBinding? = null
    private val binding get() = _binding!!

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var adapter: SubscriptionsAdapter
    private var currencySymbol: String = "৳"
    private var currencyRate: Double = 1.0
    private var flowJob: Job? = null
    var onDataChangedListener: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogSubscriptionManagerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val context = requireContext()
        dbHelper = DatabaseHelper.getInstance(context)

        currencySymbol = arguments?.getString(ARG_SYMBOL) ?: "৳"
        currencyRate = arguments?.getDouble(ARG_RATE) ?: 1.0

        val activeProfileId = ProfileManager.getActiveProfileId(context).let { if (it > 0) it else 1L }

        adapter = SubscriptionsAdapter(
            emptyList(),
            currencySymbol,
            currencyRate,
            onEditClick = { sub ->
                EditSubscriptionDialog(context, sub) {
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

        binding.rvManagerSubscriptions.layoutManager = LinearLayoutManager(context)
        binding.rvManagerSubscriptions.adapter = adapter

        binding.btnAddNewSubscription.setOnClickListener {
            AddSubscriptionDialog(context) {
                onDataChangedListener?.invoke()
            }.show()
        }

        // Observe Flow directly to automatically refresh RecyclerView items
        flowJob = CoroutineScope(Dispatchers.Main).launch {
            dbHelper.subscriptionDao.getSubscriptionsFlow(activeProfileId).collectLatest { list ->
                val accounts = dbHelper.accountDao.getAccounts(activeProfileId).associateBy { it.id }
                for (sub in list) {
                    if (sub.linkedAccountId != null && sub.linkedAccountId > 0) {
                        sub.accountName = accounts[sub.linkedAccountId]?.name ?: "Account"
                    }
                }
                adapter.updateData(list, currencySymbol, currencyRate)

                val totalProjected = list.sumOf { it.amount }
                binding.tvSubManagerSummary.text = String.format(
                    Locale.US,
                    "Total Projected: %s%.0f / mo • %d Tracked",
                    currencySymbol,
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
        }
    }

    private fun handleMarkPaid(sub: SubscriptionEntity) {
        val currentMonthYear = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
        val isSettledThisMonth = (sub.lastPaidMonthYear == currentMonthYear)

        if (isSettledThisMonth) {
            dbHelper.subscriptionDao.updatePaidMonth(sub.id, null)
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
                        dbHelper.subscriptionDao.updatePaidMonth(sub.id, currentMonthYear)
                        onDataChangedListener?.invoke()
                        Toast.makeText(requireContext(), "Deducted and marked '${sub.name}' as paid!", Toast.LENGTH_SHORT).show()
                    }
                    .setNeutralButton("JUST MARK PAID") { _, _ ->
                        dbHelper.subscriptionDao.updatePaidMonth(sub.id, currentMonthYear)
                        onDataChangedListener?.invoke()
                        Toast.makeText(requireContext(), "Marked '${sub.name}' as paid!", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } else {
                dbHelper.subscriptionDao.updatePaidMonth(sub.id, currentMonthYear)
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
                dbHelper.subscriptionDao.deleteSubscription(sub.id)
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

        fun newInstance(symbol: String = "৳", rate: Double = 1.0): SubscriptionsBottomSheet {
            return SubscriptionsBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_SYMBOL, symbol)
                    putDouble(ARG_RATE, rate)
                }
            }
        }
    }
}
