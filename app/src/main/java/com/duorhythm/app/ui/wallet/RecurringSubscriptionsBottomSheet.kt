package com.duorhythm.app.ui.wallet

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecurringSubscriptionsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: DialogRecurringSubscriptionsBinding? = null
    private val binding get() = _binding!!

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var repository: SubscriptionRepository
    private lateinit var subscriptionsAdapter: SubscriptionsAdapter
    private var currencySymbol: String = "৳"
    private var currencyRate: Double = 1.0
    private var activeProfileId: Long = 1L
    private var lifecycleJob: Job? = null
    var onDataChangedListener: (() -> Unit)? = null

    private val walletViewModel: WalletViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogRecurringSubscriptionsBinding.inflate(inflater, container, false)
        return binding.root
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
                    walletViewModel.loadSubscriptions(activeProfileId)
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

        binding.btnAddNewSubscription.setOnClickListener {
            AddSubscriptionDialog(context) {
                walletViewModel.loadSubscriptions(activeProfileId)
                onDataChangedListener?.invoke()
            }.show()
        }

        // Observe StateFlow from activity-scoped WalletViewModel
        lifecycleJob = viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                walletViewModel.subscriptionsFlow.collect { list ->
                    // Resolve account display names
                    val accounts = dbHelper.accountDao.getAccounts(activeProfileId).associateBy { it.id }
                    for (sub in list) {
                        if (sub.linkedAccountId != null && sub.linkedAccountId > 0) {
                            sub.accountName = accounts[sub.linkedAccountId]?.name ?: "Account"
                        }
                    }
                    subscriptionsAdapter.submitList(ArrayList(list)) {
                        binding.rvSubscriptions.scrollToPosition(0)
                    }
                    val totalProjected = list.sumOf { it.amount }
                    binding.tvHeaderSubtitle.text = "Total Projected: ৳${totalProjected.toInt()} / mo • ${list.size} Tracked"
                    binding.tvEmptyState.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                    binding.rvSubscriptions.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
                }
            }
        }

        walletViewModel.loadSubscriptions(activeProfileId)
    }

    private fun handleMarkPaid(sub: SubscriptionEntity) {
        val currentMonthYear = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
        val isSettledThisMonth = (sub.lastPaidMonthYear == currentMonthYear)

        if (isSettledThisMonth) {
            repository.updatePaidMonth(sub.id, null)
            walletViewModel.loadSubscriptions(activeProfileId)
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
                        walletViewModel.loadSubscriptions(activeProfileId)
                        onDataChangedListener?.invoke()
                        Toast.makeText(requireContext(), "Deducted and marked '${sub.name}' as paid!", Toast.LENGTH_SHORT).show()
                    }
                    .setNeutralButton("JUST MARK PAID") { _, _ ->
                        repository.updatePaidMonth(sub.id, currentMonthYear)
                        walletViewModel.loadSubscriptions(activeProfileId)
                        onDataChangedListener?.invoke()
                        Toast.makeText(requireContext(), "Marked '${sub.name}' as paid!", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } else {
                repository.updatePaidMonth(sub.id, currentMonthYear)
                walletViewModel.loadSubscriptions(activeProfileId)
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
                walletViewModel.loadSubscriptions(activeProfileId)
                onDataChangedListener?.invoke()
                Toast.makeText(requireContext(), "Deleted '${sub.name}'", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        lifecycleJob?.cancel()
        super.onDestroyView()
        _binding = null
    }

    private val LifecycleOwner.lifecycleScope: CoroutineScope
        get() = CoroutineScope(Dispatchers.Main)

    private suspend fun LifecycleOwner.repeatOnLifecycle(
        state: Lifecycle.State,
        block: suspend CoroutineScope.() -> Unit
    ) {
        coroutineScope {
            block()
        }
    }

    private inline fun <reified VM : androidx.lifecycle.ViewModel> Fragment.activityViewModels(): Lazy<VM> = lazy {
        ViewModelProvider(requireActivity())[VM::class.java]
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
