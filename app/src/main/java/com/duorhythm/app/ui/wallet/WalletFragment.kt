package com.duorhythm.app.ui.wallet

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.graphics.Color
import android.graphics.Typeface
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.duorhythm.app.R
import com.duorhythm.app.data.model.CurrencyRate
import com.duorhythm.app.data.repository.CurrencyService
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.model.TransactionItem
import com.duorhythm.app.data.model.WalletBucketItem
import com.duorhythm.app.databinding.DialogAddBucketBinding
import com.duorhythm.app.databinding.DialogAddTransactionBinding
import com.duorhythm.app.databinding.FragmentWalletBinding
import com.duorhythm.app.MainActivity
import com.duorhythm.app.data.local.pref.AppPreferences
import com.duorhythm.app.security.LifeSyncSecurityManager
import com.duorhythm.app.ui.common.AppIconRegistry
import com.duorhythm.app.ui.common.ColorPickerHelper
import com.duorhythm.app.util.AnimationUtils
import com.duorhythm.app.util.CurrencyUtils
import com.duorhythm.app.ui.tools.CurrencyConverterDialog
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WalletFragment : Fragment() {

    private var _binding: FragmentWalletBinding? = null
    private val binding get() = _binding!!

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var bucketsAdapter: WalletBucketsAdapter
    private lateinit var subscriptionsPreviewAdapter: SubscriptionsPreviewAdapter

    private var currentCurrency = "BDT"
    private var currentRate = 1.0
    private var currentSymbol = "৳"
    private var ratesList: List<CurrencyRate> = emptyList()

    private var selectedCalendar: java.util.Calendar = java.util.Calendar.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return try {
            _binding = FragmentWalletBinding.inflate(inflater, container, false)
            binding.root
        } catch (e: Throwable) {
            e.printStackTrace()
            View(requireContext())
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (_binding == null) return
        try {
            dbHelper = DatabaseHelper.getInstance(requireContext())
            setupRecyclerViews()
            setupCurrencySelector()
            setupListeners()

            val rootView = activity?.window?.decorView?.findViewById<ViewGroup>(android.R.id.content)
                ?: (binding.root as? ViewGroup)
            if (rootView != null) {
                (binding.blurWalletHero as? eightbitlab.com.blurview.BlurView)?.let {
                    com.duorhythm.app.util.BlurUtil.setupBlur(it, rootView)
                }
            }
            loadData()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            checkWalletBiometricLock()
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && _binding != null) {
            checkWalletBiometricLock()
        }
    }

    private fun checkWalletBiometricLock() {
        val ctx = context ?: return
        val isBiometricLocked = AppPreferences.isWalletBiometricLocked(ctx)
        if (isBiometricLocked && !WalletSessionManager.isWalletUnlockedForSession) {
            binding.layoutWalletLocked.visibility = View.VISIBLE
            binding.layoutZeroState.visibility = View.GONE
            binding.layoutActiveDashboard.visibility = View.GONE
            binding.tvTotalBalance.text = "••••••"
            binding.tvAccountsSummary.text = "••••••"

            LifeSyncSecurityManager.authenticate(
                requireActivity(),
                title = "Unlock DuoRhythm Wallet",
                subtitle = "Authenticate to access financial buckets & balances",
                onSuccess = {
                    WalletSessionManager.isWalletUnlockedForSession = true
                    LifeSyncSecurityManager.setSessionUnlocked(true)
                    loadData()
                },
                onError = { _ ->
                    (activity as? MainActivity)?.selectNavTab(R.id.nav_home)
                }
            )
        } else {
            try {
                loadData()
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    private fun setupRecyclerViews() {
        // Multi-Wallet Buckets Adapter
        bucketsAdapter = WalletBucketsAdapter(
            emptyList(),
            currentSymbol,
            currentRate,
            onBucketClick = { bucket ->
                val intent = Intent(requireContext(), WalletBucketDetailActivity::class.java).apply {
                    putExtra(WalletBucketDetailActivity.EXTRA_BUCKET_ID, bucket.id)
                }
                startActivity(intent)
            },
            onEditBucketClick = { bucket ->
                showAddOrEditBucketDialog(bucket)
            },
            onDeleteBucketClick = { bucket ->
                confirmDeleteBucket(bucket)
            }
        )
        binding.rvWalletBuckets.layoutManager = androidx.recyclerview.widget.GridLayoutManager(requireContext(), 2)
        binding.rvWalletBuckets.isNestedScrollingEnabled = false
        binding.rvWalletBuckets.adapter = bucketsAdapter
        AnimationUtils.applySlideUpLayoutAnimation(binding.rvWalletBuckets)

        // Subscriptions Preview Adapter
        subscriptionsPreviewAdapter = SubscriptionsPreviewAdapter(
            emptyList(),
            currentSymbol,
            currentRate
        ) {
            showSubscriptionManager()
        }
        binding.rvSubscriptionsPreview.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSubscriptionsPreview.adapter = subscriptionsPreviewAdapter
        AnimationUtils.applySlideUpLayoutAnimation(binding.rvSubscriptionsPreview)
    }

    private fun setupCurrencySelector() {
        val prefs = requireContext().getSharedPreferences("lifesync_prefs", Context.MODE_PRIVATE)
        ratesList = dbHelper.getAllCurrencyRates()

        val defaultCurrency = if (isBangladeshLocale()) "BDT" else "USD"
        val savedCurrency = prefs.getString("selected_currency", defaultCurrency) ?: defaultCurrency
        val currentObj = ratesList.find { it.code == savedCurrency } ?: ratesList.firstOrNull()

        if (currentObj != null) {
            currentCurrency = currentObj.code
            currentRate = 1.0
            currentSymbol = currentObj.symbol
        }
        binding.btnCurrencySelector.text = "$currentCurrency ($currentSymbol) ▾"

        binding.btnCurrencySelector.setOnClickListener {
            showCurrencyChooserDialog()
        }

        binding.btnLiveCurrencyConverter.setOnClickListener {
            CurrencyConverterDialog(requireContext()).show()
        }

        binding.tvRatesSyncTime.setOnClickListener {
            CurrencyConverterDialog(requireContext()).show()
        }
    }

    private fun showCurrencyChooserDialog() {
        val prefs = requireContext().getSharedPreferences("lifesync_prefs", Context.MODE_PRIVATE)
        ratesList = dbHelper.getAllCurrencyRates()
        val labels = ratesList.map { rate ->
            val name = getCurrencyFullName(rate.code)
            "${rate.code} (${rate.symbol}) - $name"
        }.toTypedArray()
        val currentIndex = ratesList.indexOfFirst { it.code == currentCurrency }.coerceAtLeast(0)

        AlertDialog.Builder(requireContext())
            .setTitle("Choose Currency")
            .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                if (which in ratesList.indices) {
                    val item = ratesList[which]
                    currentCurrency = item.code
                    currentRate = 1.0
                    currentSymbol = item.symbol
                    prefs.edit().putString("selected_currency", item.code).apply()
                    binding.btnCurrencySelector.text = "${item.code} (${item.symbol}) ▾"
                    loadData()
                    WalletDataSyncManager.notifyChanged()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmResetAllBalances() {
        AlertDialog.Builder(requireContext())
            .setTitle("Reset Balance")
            .setMessage("Reset all account balances to 0.00? (Your recorded transactions will remain intact).")
            .setPositiveButton("Reset Balances") { _, _ ->
                val buckets = dbHelper.getAllWalletBuckets()
                for (b in buckets) {
                    dbHelper.updateWalletBucket(b.copy(balance = 0.0))
                }
                loadData()
                WalletDataSyncManager.notifyChanged()
                Toast.makeText(requireContext(), "All account balances reset to 0.00", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmResetAllLedgerRecords() {
        AlertDialog.Builder(requireContext())
            .setTitle("Clear Records")
            .setMessage("Permanently delete all transaction records from the financial ledger? This clears all income and expense records so you can start clean.")
            .setPositiveButton("Clear Records") { _, _ ->
                dbHelper.clearAllTransactions()
                loadData()
                WalletDataSyncManager.notifyChanged()
                Toast.makeText(requireContext(), "All ledger records have been cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun isBangladeshLocale(): Boolean {
        val loc = Locale.getDefault()
        val tz = java.util.TimeZone.getDefault().id
        return loc.country.equals("BD", ignoreCase = true) ||
                loc.language.equals("bn", ignoreCase = true) ||
                tz.contains("Dhaka", ignoreCase = true) ||
                tz.contains("Bangladesh", ignoreCase = true)
    }

    private fun getCurrencyFullName(code: String): String = when (code) {
        "BDT" -> "Bangladeshi Taka"
        "USD" -> "US Dollar"
        "EUR" -> "Euro"
        "GBP" -> "British Pound"
        "INR" -> "Indian Rupee"
        "JPY" -> "Japanese Yen"
        "CAD" -> "Canadian Dollar"
        "AUD" -> "Australian Dollar"
        "AED" -> "UAE Dirham"
        "SAR" -> "Saudi Riyal"
        "SGD" -> "Singapore Dollar"
        "MYR" -> "Malaysian Ringgit"
        "PKR" -> "Pakistani Rupee"
        else -> code
    }

    private fun setupListeners() {
        binding.layoutWalletMonthFilter.setOnClickListener { showMonthYearPickerDialog() }

        // Dedicated 3-Dot Quick Overflow Menu (options: Reset Balance, Clear Records, Transfer, Dues & Debts)
        binding.btnWalletCardMenu.setOnClickListener { view ->
            val popup = androidx.appcompat.widget.PopupMenu(requireContext(), view)
            popup.menu.add(0, 1, 0, "Reset Balance")
            popup.menu.add(0, 2, 1, "Clear Records")
            popup.menu.add(0, 3, 2, "Create New Account")
            popup.menu.add(0, 4, 3, "Currency Settings")
            popup.menu.add(0, 5, 4, "Transfer Funds")
            popup.menu.add(0, 6, 5, "Dues & Debts")
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    1 -> confirmResetAllBalances()
                    2 -> confirmResetAllLedgerRecords()
                    3 -> showAddOrEditBucketDialog(null)
                    4 -> showCurrencyChooserDialog()
                    5 -> showAddTransactionDialog("TRANSFER")
                    6 -> showDuesDebtsTracker()
                }
                true
            }
            popup.show()
        }

        binding.layoutAccountsSummaryPill.setOnClickListener {
            binding.layoutBucketsHeader.parent?.requestChildFocus(
                binding.layoutBucketsHeader,
                binding.layoutBucketsHeader
            )
        }

        val onAddAccountClick = {
            if (dbHelper.getAllWalletBuckets().size >= 5) {
                com.google.android.material.snackbar.Snackbar.make(
                    binding.root,
                    "Maximum of 5 accounts reached",
                    com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
                ).show()
            } else {
                showAddOrEditBucketDialog(null)
            }
        }
        binding.btnAddAccount.setOnClickListener { onAddAccountClick() }
        binding.btnAddBucket.setOnClickListener { onAddAccountClick() }
        binding.btnEmptyAddBucket.setOnClickListener { onAddAccountClick() }
        binding.btnSetupFirstAccount.setOnClickListener { onAddAccountClick() }

        // Active Subscriptions Pill: opens recurring bills manager
        binding.layoutSubscriptionsAccordionHeader.setOnClickListener { showSubscriptionManager() }
        binding.cardSubscriptionsTracker.setOnClickListener { showSubscriptionManager() }
        binding.btnManageSubscriptions.setOnClickListener { showSubscriptionManager() }
        binding.layoutUpcomingAlert.setOnClickListener { showSubscriptionManager() }

        // Dues & Debts Tracker Module
        binding.cardDuesDebtsTracker.setOnClickListener { showDuesDebtsTracker() }
        binding.layoutDuesDebtsHeader.setOnClickListener { showDuesDebtsTracker() }
        binding.btnHeroDuesDebts.setOnClickListener { showDuesDebtsTracker() }

        // Monthly Overview Card
        binding.cardMonthlyOverview.setOnClickListener { showMonthYearPickerDialog() }

        // Quick Actions
        binding.btnHeroRecordTx.setOnClickListener { showAddTransactionDialog("INCOME") }
        binding.btnHeroTransfer.setOnClickListener { showAddTransactionDialog("TRANSFER") }
        binding.btnAddTransaction.setOnClickListener { showAddTransactionDialog() }

        // Category Management Shortcut
        binding.btnWalletCategories.setOnClickListener {
            ManageCategoriesDialog.newInstance {
                loadData()
            }.show(childFragmentManager, ManageCategoriesDialog.TAG)
        }

        AnimationUtils.attachTactileFeedback(
            binding.btnWalletCategories,
            binding.btnWalletCardMenu,
            binding.layoutAccountsSummaryPill,
            binding.cardMonthlyOverview,
            binding.btnSetupFirstAccount,
            binding.layoutAnalyticsAccordionHeader,
            binding.layoutSubscriptionsAccordionHeader,
            binding.cardDuesDebtsTracker,
            binding.layoutDuesDebtsHeader,
            binding.btnHeroRecordTx,
            binding.btnHeroTransfer,
            binding.btnHeroDuesDebts,
            binding.btnAddAccount,
            binding.btnAddBucket,
            binding.btnEmptyAddBucket,
            binding.btnManageSubscriptions,
            binding.btnAddTransaction,
            binding.btnCurrencySelector,
            binding.btnToggleLockWallet,
            binding.btnUnlockWallet
        )

        binding.btnUnlockWallet.setOnClickListener {
            LifeSyncSecurityManager.authenticate(
                requireActivity(),
                title = "Unlock DuoRhythm Wallet",
                subtitle = "Authenticate to access financial buckets & balances",
                onSuccess = {
                    WalletSessionManager.isWalletUnlockedForSession = true
                    LifeSyncSecurityManager.setSessionUnlocked(true)
                    loadData()
                    Toast.makeText(requireContext(), "Wallet Unlocked", Toast.LENGTH_SHORT).show()
                },
                onError = { err ->
                    Toast.makeText(requireContext(), err, Toast.LENGTH_SHORT).show()
                }
            )
        }

        binding.btnToggleLockWallet.setOnClickListener {
            val ctx = requireContext()
            val isEnabled = AppPreferences.isWalletBiometricLocked(ctx) || LifeSyncSecurityManager.isLockEnabled(ctx)
            if (isEnabled) {
                if (WalletSessionManager.isWalletUnlockedForSession || LifeSyncSecurityManager.isSessionUnlocked()) {
                    AlertDialog.Builder(ctx)
                        .setTitle("Lock Wallet")
                        .setMessage("Lock DuoRhythm wallet screen immediately?")
                        .setPositiveButton("Lock Now") { _, _ ->
                            WalletSessionManager.isWalletUnlockedForSession = false
                            LifeSyncSecurityManager.lockSession()
                            loadData()
                            Toast.makeText(ctx, "Wallet Locked", Toast.LENGTH_SHORT).show()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                } else {
                    binding.btnUnlockWallet.performClick()
                }
            } else {
                AlertDialog.Builder(ctx)
                    .setTitle("Enable Biometric Lock")
                    .setMessage("Protect your buckets, balances, and records with biometric unlock or device PIN?")
                    .setPositiveButton("Enable") { _, _ ->
                        LifeSyncSecurityManager.authenticate(
                            requireActivity(),
                            title = "Verify Identity",
                            subtitle = "Confirm credentials to enable security lock",
                            onSuccess = {
                                AppPreferences.setWalletBiometricLocked(ctx, true)
                                WalletSessionManager.isWalletUnlockedForSession = true
                                loadData()
                                Toast.makeText(ctx, "Biometric Lock Enabled!", Toast.LENGTH_SHORT).show()
                            },
                            onError = { err ->
                                Toast.makeText(ctx, "Could not enable: $err", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    fun loadData() {
        if (_binding == null || !isAdded || !::dbHelper.isInitialized || !::bucketsAdapter.isInitialized) return
        try {
            val ctx = context ?: return
            val rateObj = ratesList.find { it.code == currentCurrency }
            if (rateObj != null) {
                currentRate = 1.0
                currentSymbol = rateObj.symbol
            }

            val isLocked = (AppPreferences.isWalletBiometricLocked(ctx) && !WalletSessionManager.isWalletUnlockedForSession) ||
                           (LifeSyncSecurityManager.isLockEnabled(ctx) && !LifeSyncSecurityManager.isSessionUnlocked())

            if (isLocked) {
                binding.layoutWalletLocked.visibility = View.VISIBLE
                binding.layoutZeroState.visibility = View.GONE
                binding.layoutActiveDashboard.visibility = View.GONE

                binding.tvTotalBalance.text = "••••••"
                binding.tvAccountsSummary.text = "••••••"
                binding.tvCashBadge.text = "••••"
                binding.tvBucketsBadge.text = "••••"
                binding.tvSubscriptionsSummaryHeader.text = "••••••"
                binding.tvDuesDebtsSummaryHeader.text = "••••••"
                binding.tvActiveDuesCount.text = "••"
                binding.tvAnalyticsSummaryPill.text = "••••••"
                binding.tvCashBreakdown.text = "Biometric Lock Active • Tap Unlock below"
                binding.tvTotalIncome.text = "••••"
                binding.tvTotalExpenses.text = "••••"
                binding.btnCurrencySelector.text = "$currentCurrency ($currentSymbol) ▾"
                return
            }

            binding.layoutWalletLocked.visibility = View.GONE

            // 1. Load Accounts & Pockets (Pure 1:1 Raw Amounts)
            val buckets = dbHelper.getAllWalletBuckets()
            bucketsAdapter.updateData(buckets, currentSymbol, 1.0)

            // Progressive Disclosure: Zero-State Onboarding vs Active Dashboard
            if (buckets.isEmpty()) {
                binding.layoutZeroState.visibility = View.VISIBLE
                binding.layoutActiveDashboard.visibility = View.GONE
                binding.btnCurrencySelector.text = "$currentCurrency ($currentSymbol) ▾"
                return
            }

            binding.layoutZeroState.visibility = View.GONE
            binding.layoutActiveDashboard.visibility = View.VISIBLE

            val isLimitReached = buckets.size >= 5
            binding.tvBucketCountBadge.text = "${buckets.size}/5 Active"
            binding.tvAccountsSummary.text = "${buckets.size}/5 Active Accounts • Pure Net Total"

            binding.btnAddBucket.isEnabled = !isLimitReached
            binding.btnAddBucket.alpha = if (isLimitReached) 0.5f else 1.0f
            androidx.appcompat.widget.TooltipCompat.setTooltipText(
                binding.btnAddBucket,
                if (isLimitReached) "Maximum of 5 accounts reached" else "Add New Account"
            )

            binding.rvWalletBuckets.visibility = View.VISIBLE
            binding.layoutEmptyBuckets.visibility = View.GONE

            // 2. Load Dedicated Recurring Subscriptions
            val subscriptions = dbHelper.getAllSubscriptions()
            subscriptionsPreviewAdapter.updateData(subscriptions, currentSymbol, 1.0)

            val activeSubs = subscriptions.filter { it.isActive }
            binding.tvActiveSubCount.text = "${activeSubs.size} Active"

            val totalMonthlyCost = activeSubs.sumOf { sub ->
                sub.getMonthlyEquivalentCost()
            }
            binding.tvSubTotalCost.text = String.format(Locale.US, "%s%.0f / month", currentSymbol, totalMonthlyCost)
            binding.tvSubscriptionsSummaryHeader.text = if (activeSubs.isEmpty()) {
                "Active Subscriptions: ${currentSymbol}0/mo"
            } else {
                String.format(
                    Locale.US,
                    "Active Subscriptions: %s%.2f/mo",
                    currentSymbol,
                    totalMonthlyCost
                )
            }

            if (subscriptions.isEmpty()) {
                binding.rvSubscriptionsPreview.visibility = View.GONE
                binding.tvEmptySubscriptions.visibility = View.VISIBLE
                binding.btnManageSubscriptions.text = "+ Add Recurring Subscription"
            } else {
                binding.rvSubscriptionsPreview.visibility = View.VISIBLE
                binding.tvEmptySubscriptions.visibility = View.GONE
                binding.btnManageSubscriptions.text = "Manage Subscriptions ↗"
            }

            val upcomingSub = activeSubs.minByOrNull { it.getDaysUntilRenewal().coerceAtLeast(0) }
            if (upcomingSub != null) {
                binding.layoutUpcomingAlert.visibility = View.VISIBLE
                val days = upcomingSub.getDaysUntilRenewal()
                val countdownStr = when {
                    days < 0 -> "Overdue"
                    days == 0L -> "Renews today"
                    days == 1L -> "Renews tomorrow"
                    else -> "Renews in $days days"
                }
                val convertedCost = upcomingSub.cost
                binding.tvUpcomingSubAlert.text = "Next: ${upcomingSub.renewalDate} • ${upcomingSub.serviceName} ${currentSymbol}${String.format(Locale.US, "%.0f", convertedCost)} ($countdownStr)"
                binding.tvUpcomingSubBucket.text = if (upcomingSub.bucketName.isNotBlank()) "Deducts from ${upcomingSub.bucketName}" else "No bucket linked"
            } else {
                binding.layoutUpcomingAlert.visibility = View.GONE
            }

            // 3. Load Dues & Debts Summary
            val duesSummary = dbHelper.getDuesDebtsSummary()
            val totalLent = duesSummary.first
            val totalBorrowed = duesSummary.second
            val allDues = dbHelper.getAllDuesDebts()
            val pendingCount = allDues.count { it.status.equals("UNPAID", ignoreCase = true) }

            binding.tvActiveDuesCount.text = "$pendingCount Pending"
            binding.tvDuesDebtsSummaryHeader.text = String.format(
                Locale.US,
                "They Owe: %s%,.2f • You Owe: %s%,.2f",
                currentSymbol,
                totalLent,
                currentSymbol,
                totalBorrowed
            )

            // 4. Balance Calculations: Strictly equal Sum of all User Accounts (100% Pure Raw Values)
            val totalNet = buckets.sumOf { it.balance }

            val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.US)
            binding.tvWalletSelectedMonth.text = monthFormat.format(selectedCalendar.time)

            val monthPrefixFormat = SimpleDateFormat("yyyy-MM", Locale.US)
            val monthPrefix = monthPrefixFormat.format(selectedCalendar.time)

            val totalIncome = dbHelper.getTotalIncome(monthPrefix)
            val totalExpenses = dbHelper.getTotalExpenses(monthPrefix)

            val formattedNetBalance = if (totalNet < 0) {
                String.format(Locale.US, "-%s%.2f", currentSymbol, kotlin.math.abs(totalNet))
            } else {
                String.format(Locale.US, "%s%.2f", currentSymbol, totalNet)
            }
            binding.tvTotalBalance.text = formattedNetBalance

            // Keep legacy views updated safely in case referenced
            binding.tvCashBadge.text = "${buckets.size} Accounts"
            binding.tvBucketsBadge.text = formattedNetBalance
            binding.tvCashBreakdown.text = "${buckets.size} Accounts Active"

            binding.tvTotalIncome.text = String.format(Locale.US, "+%s%.2f", currentSymbol, totalIncome)
            binding.tvTotalExpenses.text = String.format(Locale.US, "-%s%.2f", currentSymbol, totalExpenses)

            val incomeStr = String.format(Locale.US, "+%s%.0f", currentSymbol, totalIncome)
            val expenseStr = String.format(Locale.US, "-%s%.0f", currentSymbol, totalExpenses)
            binding.tvAnalyticsSummaryPill.text = "$incomeStr In • $expenseStr Out • Tap for Charts"

            val lastUpdate = rateObj?.lastUpdated ?: "Cached"
            binding.btnCurrencySelector.text = "$currentCurrency ($currentSymbol) ▾"
            binding.tvRatesSyncTime.text = if (currentCurrency == "BDT") {
                "Base Currency: BDT (৳) • Pure Account Ledger"
            } else {
                "Rate: 1 USD = ${String.format(Locale.US, "%.2f", rateObj?.rate ?: 1.0)} $currentCurrency • Updated: $lastUpdate"
            }

            renderMonthlyCashFlowChart(totalIncome, totalExpenses, monthFormat.format(selectedCalendar.time))
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun renderMonthlyCashFlowChart(totalIncome: Double, totalExpenses: Double, monthName: String) {
        if (_binding == null) return
        val walletViewModel = WalletViewModel()
        val activeProfileId = com.duorhythm.app.data.local.pref.ProfileManager.getActiveProfileId(requireContext())
        val currentMonthYear = SimpleDateFormat("yyyy-MM", Locale.US).format(selectedCalendar.time)
        val unpaidSubsTotal = try {
            val subs = dbHelper.subscriptionDao.getSubscriptions(activeProfileId)
            subs.filter { it.lastPaidMonthYear != currentMonthYear }.sumOf { it.amount }
        } catch (_: Exception) {
            0.0
        }
        val projectedTotal = walletViewModel.calculateProjectedMonthlyOutflow(totalExpenses, unpaidSubsTotal)
        val symbol = if (currentSymbol.isNotBlank()) currentSymbol else "৳"
        val spentStr = if (totalExpenses % 1.0 == 0.0) totalExpenses.toLong().toString() else String.format(Locale.US, "%.0f", totalExpenses)
        val projStr = if (projectedTotal % 1.0 == 0.0) projectedTotal.toLong().toString() else String.format(Locale.US, "%.0f", projectedTotal)
        binding.tvMonthlyChartSubtitle.text = "Spent: $symbol$spentStr • Projected: $symbol$projStr"
        binding.tvChartInflowBadge.text = String.format(Locale.US, "＋ In: %s%,.0f", currentSymbol, totalIncome)
        binding.tvChartOutflowBadge.text = String.format(Locale.US, "－ Out: %s%,.0f", currentSymbol, totalExpenses)

        val netFlow = totalIncome - totalExpenses
        binding.tvChartNetFlow.text = String.format(
            Locale.US,
            "Net Flow: %s%s%,.2f",
            if (netFlow < 0) "-" else "+",
            currentSymbol,
            kotlin.math.abs(netFlow)
        )

        when {
            netFlow > 0 -> {
                binding.tvChartNetStatus.text = "Positive Inflow"
                binding.tvChartNetStatus.setTextColor(Color.parseColor("#10B981"))
            }
            netFlow < 0 -> {
                binding.tvChartNetStatus.text = "Deficit"
                binding.tvChartNetStatus.setTextColor(Color.parseColor("#EF4444"))
            }
            else -> {
                binding.tvChartNetStatus.text = "Balanced"
                binding.tvChartNetStatus.setTextColor(Color.parseColor("#38BDF8"))
            }
        }

        val entries = arrayListOf(
            BarEntry(0f, totalIncome.toFloat()),
            BarEntry(1f, totalExpenses.toFloat())
        )

        val barDataSet = BarDataSet(entries, "Cash Flow").apply {
            colors = listOf(
                Color.parseColor("#10B981"), // Emerald Inflow
                Color.parseColor("#EF4444")  // Rose Spending
            )
            setDrawValues(true)
            valueTextColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
            valueTextSize = 10f
            valueTypeface = Typeface.DEFAULT_BOLD
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return if (value > 0) String.format(Locale.US, "%s%,.0f", currentSymbol, value) else ""
                }
            }
        }

        val barData = BarData(barDataSet).apply {
            barWidth = 0.42f
        }

        binding.chartMonthlyFlow.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setDrawGridBackground(false)
            setDrawBorders(false)
            setTouchEnabled(false)
            setDrawBarShadow(false)
            setDrawValueAboveBar(true)
            setPinchZoom(false)
            setScaleEnabled(false)

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                setDrawAxisLine(false)
                textColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                granularity = 1f
                valueFormatter = IndexAxisValueFormatter(arrayOf("Inflow", "Spending"))
                axisMinimum = -0.5f
                axisMaximum = 1.5f
            }

            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = Color.parseColor("#1594A3B8")
                setDrawAxisLine(false)
                textColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
                textSize = 9f
                axisMinimum = 0f
            }
            axisRight.isEnabled = false

            data = barData
            animateY(500)
            invalidate()
        }
    }

    private fun showMonthYearPickerDialog() {
        val months = arrayOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val years = (currentYear - 3..currentYear + 2).toList()

        var tempYear = selectedCalendar.get(java.util.Calendar.YEAR)
        var tempMonth = selectedCalendar.get(java.util.Calendar.MONTH)

        val context = requireContext()
        val layout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val density = resources.displayMetrics.density
            val pad = (20 * density).toInt()
            setPadding(pad, pad, pad, pad)
        }

        val yearLabel = android.widget.TextView(context).apply {
            text = "Select Year"
            setTextColor(android.graphics.Color.parseColor("#94A3B8"))
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        val yearSpinner = android.widget.Spinner(context).apply {
            val yearStrings = years.map { it.toString() }
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, yearStrings)
            val selectedYearIdx = years.indexOf(tempYear).coerceAtLeast(0)
            setSelection(selectedYearIdx)
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    tempYear = years[position]
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
        }

        val monthLabel = android.widget.TextView(context).apply {
            text = "Select Month"
            setTextColor(android.graphics.Color.parseColor("#94A3B8"))
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            val topPad = (16 * resources.displayMetrics.density).toInt()
            setPadding(0, topPad, 0, 0)
        }
        val monthSpinner = android.widget.Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, months)
            setSelection(tempMonth)
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    tempMonth = position
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
        }

        layout.addView(yearLabel)
        layout.addView(yearSpinner)
        layout.addView(monthLabel)
        layout.addView(monthSpinner)

        AlertDialog.Builder(context)
            .setTitle("Financial Statement Month")
            .setView(layout)
            .setPositiveButton("Apply") { _, _ ->
                selectedCalendar.set(java.util.Calendar.YEAR, tempYear)
                selectedCalendar.set(java.util.Calendar.MONTH, tempMonth)
                selectedCalendar.set(java.util.Calendar.DAY_OF_MONTH, 1)
                loadData()
            }
            .setNeutralButton("This Month") { _, _ ->
                selectedCalendar = java.util.Calendar.getInstance()
                loadData()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSubscriptionManager() {
        RecurringSubscriptionsBottomSheet.newInstance(currentSymbol, currentRate).apply {
            onDataChangedListener = { loadData() }
        }.show(childFragmentManager, "RecurringSubscriptionsBottomSheet")
    }

    private fun showDuesDebtsTracker() {
        DuesDebtsBottomSheetDialog.newInstance(currentSymbol, currentCurrency)
            .show(childFragmentManager, "DuesDebtsBottomSheetDialog")
    }

    private fun confirmDeleteBucket(bucket: WalletBucketItem) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Category")
            .setMessage("Delete this category? Any linked transactions will also be cleared.")
            .setPositiveButton("Delete") { _, _ ->
                dbHelper.deleteWalletBucket(bucket.id)
                loadData()
                WalletDataSyncManager.notifyChanged()
                Toast.makeText(requireContext(), "Deleted '${bucket.name}'", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddOrEditBucketDialog(bucketToEdit: WalletBucketItem? = null) {
        val currentBuckets = dbHelper.getAllWalletBuckets()
        if (bucketToEdit == null && currentBuckets.size >= 5) {
            com.google.android.material.snackbar.Snackbar.make(
                binding.root,
                "Maximum of 5 accounts reached",
                com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
            ).show()
            return
        }

        val dialogBinding = DialogAddBucketBinding.inflate(layoutInflater)
        var selectedIcon = bucketToEdit?.icon ?: "cash"
        var selectedColor = bucketToEdit?.colorHex ?: "#06B6D4"

        var updateIconsRow: ((String, String) -> Unit)? = null
        var updateColorsRow: ((String) -> Unit)? = null

        fun updateUiColors() {
            val parsedColor = try { Color.parseColor(selectedColor) } catch (_: Exception) { Color.parseColor("#06B6D4") }
            dialogBinding.btnSaveBucket.backgroundTintList = ColorStateList.valueOf(parsedColor)
            updateIconsRow?.invoke(selectedIcon, selectedColor)
        }

        updateIconsRow = AppIconRegistry.setupQuickPickIconsRow(
            context = requireContext(),
            container = dialogBinding.layoutBucketIcons,
            initialIcon = selectedIcon,
            fragmentManager = childFragmentManager,
            initialAccentColor = selectedColor
        ) { newIcon ->
            selectedIcon = newIcon
            updateIconsRow?.invoke(selectedIcon, selectedColor)
        }

        updateColorsRow = ColorPickerHelper.setupPaletteRow(
            context = requireContext(),
            container = dialogBinding.layoutBucketColors,
            initialColor = selectedColor
        ) { newColor ->
            selectedColor = newColor
            updateUiColors()
        }

        if (bucketToEdit != null) {
            dialogBinding.tvBucketDialogTitle.text = "Edit Account"
            dialogBinding.tvBucketDialogSubtitle.text = "Update account name, icon, or accent color."
            dialogBinding.btnSaveBucket.text = "Save Changes"
            dialogBinding.etBucketName.setText(bucketToEdit.name)

            val convertedBalance = bucketToEdit.balance
            dialogBinding.etBucketBalance.setText(
                if (convertedBalance % 1.0 == 0.0) convertedBalance.toLong().toString()
                else String.format(Locale.US, "%.2f", convertedBalance)
            )
            dialogBinding.tvBucketBalanceLabel.text = "Current Balance ($currentSymbol - $currentCurrency)"
        } else {
            dialogBinding.tvBucketDialogTitle.text = "Create Account"
            dialogBinding.tvBucketDialogSubtitle.text = "Set up your account name, icon, and accent color."
            dialogBinding.tvBucketBalanceLabel.text = "Starting Balance"
            dialogBinding.etBucketBalance.setText("")
        }

        updateUiColors()

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnCancelBucket.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnSaveBucket.setOnClickListener {
            val name = dialogBinding.etBucketName.text?.toString()?.trim().orEmpty()
            val balanceStr = dialogBinding.etBucketBalance.text?.toString()?.trim().orEmpty()

            if (name.isEmpty()) {
                Toast.makeText(requireContext(), "Account name is required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val enteredBalance = balanceStr.toDoubleOrNull() ?: 0.0
            val baseBalance = enteredBalance

            if (bucketToEdit != null) {
                val updated = bucketToEdit.copy(
                    name = name,
                    icon = selectedIcon,
                    colorHex = selectedColor,
                    connectedAccounts = name,
                    balance = baseBalance
                )
                dbHelper.updateWalletBucket(updated)
                dialog.dismiss()
                loadData()
                WalletDataSyncManager.notifyChanged()
                Toast.makeText(requireContext(), "Updated '$name'", Toast.LENGTH_SHORT).show()
            } else {
                val newBucket = WalletBucketItem(
                    name = name,
                    icon = selectedIcon,
                    colorHex = selectedColor,
                    connectedAccounts = name,
                    balance = baseBalance,
                    note = "User-created account"
                )
                val id = dbHelper.insertWalletBucket(newBucket)
                if (id != -1L) {
                    dialog.dismiss()
                    loadData()
                    WalletDataSyncManager.notifyChanged()
                    Toast.makeText(requireContext(), "Created '$name' account", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Maximum of 5 accounts reached", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.show()
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    private fun applyToggleStyles(binding: DialogAddTransactionBinding, mode: String) {
        val unselectedStroke = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.card_stroke)
        val unselectedText = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text_secondary)
        val isNight = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES

        val incomeBg = if (mode == "INCOME") {
            if (isNight) Color.parseColor("#064E3B") else Color.parseColor("#D1FAE5")
        } else Color.TRANSPARENT
        val incomeText = if (mode == "INCOME") {
            if (isNight) Color.parseColor("#34D399") else Color.parseColor("#065F46")
        } else unselectedText
        val incomeStroke = if (mode == "INCOME") {
            if (isNight) Color.parseColor("#059669") else Color.parseColor("#10B981")
        } else unselectedStroke
        binding.btnTypeIncome.setBackgroundColor(incomeBg)
        binding.btnTypeIncome.setTextColor(incomeText)
        binding.btnTypeIncome.strokeColor = android.content.res.ColorStateList.valueOf(incomeStroke)

        val expenseBg = if (mode == "EXPENSE") {
            if (isNight) Color.parseColor("#4C1D24") else Color.parseColor("#FEE2E2")
        } else Color.TRANSPARENT
        val expenseText = if (mode == "EXPENSE") {
            if (isNight) Color.parseColor("#F87171") else Color.parseColor("#991B1B")
        } else unselectedText
        val expenseStroke = if (mode == "EXPENSE") {
            if (isNight) Color.parseColor("#991B1B") else Color.parseColor("#EF4444")
        } else unselectedStroke
        binding.btnTypeExpense.setBackgroundColor(expenseBg)
        binding.btnTypeExpense.setTextColor(expenseText)
        binding.btnTypeExpense.strokeColor = android.content.res.ColorStateList.valueOf(expenseStroke)

        val transferBg = if (mode == "TRANSFER") {
            if (isNight) Color.parseColor("#0C4A6E") else Color.parseColor("#E0F2FE")
        } else Color.TRANSPARENT
        val transferText = if (mode == "TRANSFER") {
            if (isNight) Color.parseColor("#38BDF8") else Color.parseColor("#0369A1")
        } else unselectedText
        val transferStroke = if (mode == "TRANSFER") {
            if (isNight) Color.parseColor("#0284C7") else Color.parseColor("#0284C7")
        } else unselectedStroke
        binding.btnTypeTransfer.setBackgroundColor(transferBg)
        binding.btnTypeTransfer.setTextColor(transferText)
        binding.btnTypeTransfer.strokeColor = android.content.res.ColorStateList.valueOf(transferStroke)
    }

    private fun showAddTransactionDialog(initialType: String = "INCOME") {
        val buckets = dbHelper.getAllWalletBuckets()
        if (buckets.isEmpty()) {
            AlertDialog.Builder(requireContext())
                .setTitle("Account Required")
                .setMessage("Please set up your accounts first (e.g., Cash, bKash, Bank) to start recording transactions.")
                .setPositiveButton("+ Set Up First Account") { _, _ ->
                    showAddOrEditBucketDialog(null)
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        val prefs = requireContext().getSharedPreferences("lifesync_prefs", Context.MODE_PRIVATE)
        val lastUsedBucketId = prefs.getLong("last_used_wallet_account_id", -1L)

        val dialogBinding = DialogAddTransactionBinding.inflate(layoutInflater)
        dialogBinding.tilTxAmount.hint = "Amount ($currentSymbol - $currentCurrency)"

        var txType = initialType
        when (initialType) {
            "TRANSFER" -> {
                dialogBinding.toggleTxType.check(R.id.btnTypeTransfer)
                dialogBinding.tilTxCard.hint = "From Account"
                dialogBinding.tilTxToAccount.visibility = View.VISIBLE
                dialogBinding.tilTxCategory.visibility = View.GONE
                dialogBinding.chipCreateCategory.visibility = View.GONE
                dialogBinding.btnSaveTx.text = "Execute Transfer"
            }
            "EXPENSE" -> {
                dialogBinding.toggleTxType.check(R.id.btnTypeExpense)
                dialogBinding.tilTxCard.hint = "From Account"
                dialogBinding.tilTxToAccount.visibility = View.GONE
                dialogBinding.tilTxCategory.visibility = View.VISIBLE
                dialogBinding.btnSaveTx.text = "Save Record"
            }
            else -> {
                dialogBinding.toggleTxType.check(R.id.btnTypeIncome)
                dialogBinding.tilTxCard.hint = "To Account"
                dialogBinding.tilTxToAccount.visibility = View.GONE
                dialogBinding.tilTxCategory.visibility = View.VISIBLE
                dialogBinding.btnSaveTx.text = "Save Record"
            }
        }
        applyToggleStyles(dialogBinding, txType)

        val categoryHelper = TransactionCategoryHelper(
            context = requireContext(),
            dbHelper = dbHelper,
            actvCategory = dialogBinding.actvTxCategory,
            currentTxType = if (txType == "TRANSFER") "EXPENSE" else txType,
            chipCreateCategory = dialogBinding.chipCreateCategory
        )

        val bucketNames = buckets.map { it.name }
        val bucketAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, bucketNames)
        dialogBinding.actvTxCard.setAdapter(bucketAdapter)

        val defaultBucket = buckets.find { it.id == lastUsedBucketId } ?: buckets.first()
        dialogBinding.actvTxCard.setText(defaultBucket.name, false)
        dialogBinding.actvTxCard.setOnClickListener { dialogBinding.actvTxCard.showDropDown() }

        fun updateToAccountDropdown() {
            val selectedFromName = dialogBinding.actvTxCard.text?.toString()?.trim().orEmpty()
            val destBuckets = buckets.filterNot { it.name.equals(selectedFromName, ignoreCase = true) }
            val destNames = destBuckets.map { it.name }
            val toAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, destNames)
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
        dialogBinding.actvTxCard.setOnItemClickListener { _, _, _, _ ->
            updateToAccountDropdown()
        }

        dialogBinding.toggleTxType.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btnTypeTransfer -> {
                        txType = "TRANSFER"
                        dialogBinding.tilTxCard.hint = "From Account"
                        dialogBinding.tilTxToAccount.visibility = View.VISIBLE
                        dialogBinding.tilTxCategory.visibility = View.GONE
                        dialogBinding.chipCreateCategory.visibility = View.GONE
                        dialogBinding.btnSaveTx.text = "Execute Transfer"
                        updateToAccountDropdown()
                    }
                    R.id.btnTypeExpense -> {
                        txType = "EXPENSE"
                        dialogBinding.tilTxCard.hint = "From Account"
                        dialogBinding.tilTxToAccount.visibility = View.GONE
                        dialogBinding.tilTxCategory.visibility = View.VISIBLE
                        dialogBinding.btnSaveTx.text = "Save Record"
                        categoryHelper.setTxType("EXPENSE")
                    }
                    else -> {
                        txType = "INCOME"
                        dialogBinding.tilTxCard.hint = "To Account"
                        dialogBinding.tilTxToAccount.visibility = View.GONE
                        dialogBinding.tilTxCategory.visibility = View.VISIBLE
                        dialogBinding.btnSaveTx.text = "Save Record"
                        categoryHelper.setTxType("INCOME")
                    }
                }
                applyToggleStyles(dialogBinding, txType)
            }
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnSaveTx.setOnClickListener {
            val amountStr = dialogBinding.etTxAmount.text?.toString()?.trim().orEmpty()
            if (amountStr.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter an amount", Toast.LENGTH_SHORT).show()
                dialogBinding.etTxAmount.requestFocus()
                return@setOnClickListener
            }

            val enteredAmount = amountStr.toDoubleOrNull() ?: 0.0
            if (enteredAmount <= 0.0) {
                Toast.makeText(requireContext(), "Please enter a valid amount greater than 0", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val notes = dialogBinding.etTxNotes.text?.toString()?.trim().orEmpty()
            val customTitle = dialogBinding.etTxTitle.text?.toString()?.trim().orEmpty()

            if (txType == "TRANSFER") {
                if (buckets.size < 2) {
                    Toast.makeText(requireContext(), "Transfer requires at least 2 accounts", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val fromName = dialogBinding.actvTxCard.text?.toString()?.trim().orEmpty()
                val toName = dialogBinding.actvTxToAccount.text?.toString()?.trim().orEmpty()

                val fromBucket = buckets.firstOrNull { it.name.equals(fromName, ignoreCase = true) }
                val toBucket = buckets.firstOrNull { it.name.equals(toName, ignoreCase = true) }

                if (fromBucket == null || toBucket == null || fromBucket.id == toBucket.id) {
                    Toast.makeText(requireContext(), "Please select different source and destination accounts", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val success = dbHelper.executeTransfer(
                    fromBucketId = fromBucket.id,
                    toBucketId = toBucket.id,
                    amount = enteredAmount,
                    date = today,
                    currency = currentCurrency,
                    note = notes,
                    title = customTitle
                )

                if (success) {
                    prefs.edit().putLong("last_used_wallet_account_id", fromBucket.id).apply()
                    dialog.dismiss()
                    loadData()
                    WalletDataSyncManager.notifyChanged()
                    val formatted = CurrencyUtils.formatRawCurrency(enteredAmount, currentSymbol)
                    Toast.makeText(requireContext(), "Transferred $formatted from ${fromBucket.name} to ${toBucket.name}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Failed to execute transfer", Toast.LENGTH_SHORT).show()
                }
            } else {
                val category = categoryHelper.getSelectedCategoryAndEnsurePersisted()
                if (category.isBlank()) {
                    Toast.makeText(requireContext(), "Please select or create a category", Toast.LENGTH_SHORT).show()
                    dialogBinding.actvTxCategory.requestFocus()
                    return@setOnClickListener
                }
                val title = if (customTitle.isNotEmpty()) customTitle else category

                val selectedBucketName = dialogBinding.actvTxCard.text?.toString()?.trim().orEmpty()
                val selectedBucket = buckets.firstOrNull { it.name.equals(selectedBucketName, ignoreCase = true) } ?: defaultBucket
                val bucketId = selectedBucket.id

                val tx = TransactionItem(
                    title = title,
                    amount = enteredAmount,
                    type = txType,
                    category = category,
                    date = today,
                    cardId = 0L,
                    currency = currentCurrency,
                    notes = notes,
                    bucketId = bucketId
                )
                dbHelper.insertTransaction(tx)

                prefs.edit().putLong("last_used_wallet_account_id", bucketId).apply()

                dialog.dismiss()
                loadData()
                WalletDataSyncManager.notifyChanged()
                Toast.makeText(requireContext(), "$txType recorded in ${selectedBucket.name}", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.show()
        dialogBinding.etTxAmount.requestFocus()
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
