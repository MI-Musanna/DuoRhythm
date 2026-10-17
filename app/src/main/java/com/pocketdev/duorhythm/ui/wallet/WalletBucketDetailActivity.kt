package com.pocketdev.duorhythm.ui.wallet

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import android.content.res.ColorStateList
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.pocketdev.duorhythm.R
import com.pocketdev.duorhythm.data.model.CurrencyRate
import com.pocketdev.duorhythm.data.local.db.DatabaseHelper
import com.pocketdev.duorhythm.data.local.pref.AppSettings
import com.pocketdev.duorhythm.data.model.TransactionItem
import com.pocketdev.duorhythm.data.model.WalletBucketItem
import com.pocketdev.duorhythm.databinding.ActivityWalletBucketDetailBinding
import com.pocketdev.duorhythm.databinding.DialogAddTransactionBinding
import com.pocketdev.duorhythm.util.AnimationUtils
import com.pocketdev.duorhythm.util.CurrencyUtils
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class WalletBucketDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_BUCKET_ID = "extra_bucket_id"
    }

    private lateinit var binding: ActivityWalletBucketDetailBinding
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var transactionsAdapter: TransactionsAdapter

    private var bucketId: Long = 0
    private var currentBucket: WalletBucketItem? = null
    private var currentFilter = "ALL"
    private var currentCurrency = "USD"
    private var currentRate = 1.0
    private var currentSymbol = "$"

    private enum class MiniChartMode { PIE, BAR }
    private var miniChartMode = MiniChartMode.PIE

    private val miniChartColors = listOf(
        Color.parseColor("#10B981"),
        Color.parseColor("#38BDF8"),
        Color.parseColor("#F43F5E"),
        Color.parseColor("#8B5CF6"),
        Color.parseColor("#F59E0B"),
        Color.parseColor("#06B6D4"),
        Color.parseColor("#EC4899"),
        Color.parseColor("#6366F1")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWalletBucketDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = DatabaseHelper.getInstance(this)
        bucketId = intent.getLongExtra(EXTRA_BUCKET_ID, 0L)

        setupCurrency()
        setupRecyclerView()
        setupMiniCharts()
        setupListeners()
        loadBucketData()
    }

    override fun onResume() {
        super.onResume()
        setupCurrency()
        loadBucketData()
    }

    private fun setupCurrency() {
        val prefs = getSharedPreferences("lifesync_prefs", MODE_PRIVATE)
        val defaultCurrency = if (isBangladeshLocale()) "BDT" else "USD"
        currentCurrency = prefs.getString("selected_currency", defaultCurrency) ?: defaultCurrency
        val rates = dbHelper.getAllCurrencyRates()
        val rateObj = rates.find { it.code == currentCurrency }
        if (rateObj != null) {
            currentRate = 1.0
            currentSymbol = rateObj.symbol
        }
    }

    private fun isBangladeshLocale(): Boolean {
        val loc = Locale.getDefault()
        val tz = java.util.TimeZone.getDefault().id
        return loc.country.equals("BD", ignoreCase = true) ||
                loc.language.equals("bn", ignoreCase = true) ||
                tz.contains("Dhaka", ignoreCase = true) ||
                tz.contains("Bangladesh", ignoreCase = true)
    }

    private fun setupRecyclerView() {
        transactionsAdapter = TransactionsAdapter(
            emptyList(),
            emptyMap(),
            currentSymbol,
            1.0,
            onTxClick = { tx ->
                showEditTransactionDialog(tx)
            },
            onDeleteClick = { tx ->
                AlertDialog.Builder(this)
                    .setTitle("Delete Record")
                    .setMessage("Remove '${tx.title}' (${currentSymbol}${String.format(Locale.US, "%.2f", tx.amount)})?")
                    .setPositiveButton("Delete") { _, _ ->
                        dbHelper.deleteTransaction(tx.id)
                        loadBucketData()
                        WalletDataSyncManager.notifyChanged()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )
        binding.rvBucketTransactions.layoutManager = LinearLayoutManager(this)
        binding.rvBucketTransactions.adapter = transactionsAdapter
        AnimationUtils.applySlideUpLayoutAnimation(binding.rvBucketTransactions)
    }

    private fun setupListeners() {
        binding.btnBucketBack.setOnClickListener { finish() }

        binding.btnEditBucket.setOnClickListener {
            showAdjustOrResetBalanceDialog()
        }
        binding.btnEditBucket.setOnLongClickListener {
            showEditBucketDialog()
            true
        }

        binding.btnAdjustBalanceHero.setOnClickListener {
            showAdjustOrResetBalanceDialog()
        }

        binding.btnDeleteBucket.setOnClickListener {
            showDeleteBucketDialog()
        }

        binding.chipGroupBucketTxFilter.setOnCheckedChangeListener { _, checkedId ->
            currentFilter = when (checkedId) {
                R.id.chipBucketIncome -> "INCOME"
                R.id.chipBucketExpense -> "EXPENSE"
                else -> "ALL"
            }
            loadBucketData()
        }

        binding.btnToggleMiniChartMode.setOnClickListener {
            miniChartMode = if (miniChartMode == MiniChartMode.PIE) MiniChartMode.BAR else MiniChartMode.PIE
            binding.tvMiniChartToggleIcon.text = if (miniChartMode == MiniChartMode.PIE) "📊 / 🥧" else "🥧 / 📊"
            renderMiniChart()
        }

        binding.containerBucketMiniChart.setOnClickListener {
            val bucket = currentBucket ?: return@setOnClickListener
            BucketAnalyticsBottomSheet.newInstance(bucket.id, bucket.name, currentSymbol)
                .show(supportFragmentManager, "BucketAnalyticsBottomSheet")
        }

        binding.fabAddBucketRecord.setOnClickListener {
            showAddTransactionDialog()
        }

        AnimationUtils.attachTactileFeedback(
            binding.btnBucketBack,
            binding.btnEditBucket,
            binding.btnAdjustBalanceHero,
            binding.btnDeleteBucket,
            binding.btnToggleMiniChartMode,
            binding.containerBucketMiniChart,
            binding.fabAddBucketRecord
        )
    }

    private fun showAdjustOrResetBalanceDialog() {
        val bucket = currentBucket ?: return
        val context = this
        val convertedBalance = bucket.balance

        val dialogBinding = com.pocketdev.duorhythm.databinding.DialogEditBalanceBinding.inflate(layoutInflater)
        dialogBinding.tvEditBalanceSubtitle.text = "Directly adjust the balance of '${bucket.name}', or reset to ${currentSymbol}0 instantly."
        dialogBinding.tvBalanceLabel.text = "Enter New Balance ($currentSymbol - $currentCurrency)"

        val textStr = if (convertedBalance % 1.0 == 0.0) convertedBalance.toLong().toString()
        else String.format(Locale.US, "%.2f", convertedBalance)
        dialogBinding.etBalance.setText(textStr)
        dialogBinding.etBalance.setSelection(dialogBinding.etBalance.text?.length ?: 0)

        val dialog = AlertDialog.Builder(context)
            .setTitle("Edit / Reset Balance")
            .setView(dialogBinding.root)
            .setPositiveButton("Save Balance") { _, _ ->
                val entered = dialogBinding.etBalance.text?.toString()?.trim()?.toDoubleOrNull() ?: convertedBalance
                val baseBalance = entered
                dbHelper.updateBucketBalance(bucket.id, baseBalance)
                loadBucketData()
                WalletDataSyncManager.notifyChanged()
                Toast.makeText(context, "${bucket.name} balance set to $currentSymbol${String.format(Locale.US, "%.2f", entered)}", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("RESET TO ৳0") { _, _ ->
                AlertDialog.Builder(context)
                    .setTitle("Reset Balance to ৳0")
                    .setMessage("Are you sure you want to reset '${bucket.name}' balance to ${currentSymbol}0.00? Ledger transactions will be retained.")
                    .setPositiveButton("Reset to ৳0") { _, _ ->
                        dbHelper.updateBucketBalance(bucket.id, 0.0)
                        loadBucketData()
                        WalletDataSyncManager.notifyChanged()
                        Toast.makeText(context, "${bucket.name} balance reset to ${currentSymbol}0.00", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
        dialog.getButton(android.content.DialogInterface.BUTTON_NEUTRAL)?.setTextColor(android.graphics.Color.parseColor("#EF4444"))
        dialogBinding.etBalance.requestFocus()
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    private fun showEditBucketDialog() {
        val bucket = currentBucket ?: return
        val dialogBinding = com.pocketdev.duorhythm.databinding.DialogAddBucketBinding.inflate(layoutInflater)
        dialogBinding.tvBucketDialogTitle.text = "Edit Account"
        dialogBinding.tvBucketDialogSubtitle.text = "Update account name, icon, or accent color."
        dialogBinding.btnSaveBucket.text = "Save Changes"

        dialogBinding.etBucketName.setText(bucket.name)
        val convertedBalance = bucket.balance
        dialogBinding.etBucketBalance.setText(
            if (convertedBalance % 1.0 == 0.0) convertedBalance.toLong().toString()
            else String.format(Locale.US, "%.2f", convertedBalance)
        )
        dialogBinding.tvBucketBalanceLabel.text = "Current Balance ($currentSymbol - $currentCurrency)"

        var selectedIcon = bucket.icon
        var selectedColor = bucket.colorHex

        val iconOptions = listOf(
            Triple("cash", dialogBinding.btnIconCash, dialogBinding.ivIconCash),
            Triple("wallet", dialogBinding.btnIconWallet, dialogBinding.ivIconWallet),
            Triple("bank", dialogBinding.btnIconBank, dialogBinding.ivIconBank),
            Triple("card", dialogBinding.btnIconCard, dialogBinding.ivIconCard),
            Triple("piggybank", dialogBinding.btnIconPiggy, dialogBinding.ivIconPiggy),
            Triple("vault", dialogBinding.btnIconVault, dialogBinding.ivIconVault)
        )

        val colorOptions = listOf(
            Triple("#06B6D4", dialogBinding.btnColorCyan, Pair(dialogBinding.ringColorCyan, dialogBinding.checkColorCyan)),
            Triple("#10B981", dialogBinding.btnColorEmerald, Pair(dialogBinding.ringColorEmerald, dialogBinding.checkColorEmerald)),
            Triple("#8B5CF6", dialogBinding.btnColorViolet, Pair(dialogBinding.ringColorViolet, dialogBinding.checkColorViolet)),
            Triple("#F59E0B", dialogBinding.btnColorAmber, Pair(dialogBinding.ringColorAmber, dialogBinding.checkColorAmber)),
            Triple("#F43F5E", dialogBinding.btnColorRose, Pair(dialogBinding.ringColorRose, dialogBinding.checkColorRose)),
            Triple("#38BDF8", dialogBinding.btnColorSkyBlue, Pair(dialogBinding.ringColorSkyBlue, dialogBinding.checkColorSkyBlue))
        )

        fun updateColorSelectionUi() {
            val parsedColor = try { Color.parseColor(selectedColor) } catch (_: Exception) { Color.parseColor("#06B6D4") }
            val glowColor = ColorUtils.setAlphaComponent(parsedColor, (255 * 0.18).toInt())

            for ((hex, _, views) in colorOptions) {
                val isSelected = hex.equals(selectedColor, ignoreCase = true)
                views.first.visibility = if (isSelected) View.VISIBLE else View.GONE
                views.second.visibility = if (isSelected) View.VISIBLE else View.GONE
            }

            dialogBinding.btnSaveBucket.backgroundTintList = ColorStateList.valueOf(parsedColor)

            for ((iconKey, card, iv) in iconOptions) {
                val isSelected = iconKey.equals(selectedIcon, ignoreCase = true)
                if (isSelected) {
                    card.setCardBackgroundColor(glowColor)
                    card.strokeColor = parsedColor
                    card.strokeWidth = (1.5f * resources.displayMetrics.density).toInt()
                    iv.setColorFilter(parsedColor)
                } else {
                    card.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.surface_variant))
                    card.strokeColor = androidx.core.content.ContextCompat.getColor(this, R.color.card_stroke)
                    card.strokeWidth = (1f * resources.displayMetrics.density).toInt()
                    iv.setColorFilter(androidx.core.content.ContextCompat.getColor(this, R.color.text_secondary))
                }
            }
        }

        for ((iconKey, card, _) in iconOptions) {
            card.setOnClickListener {
                selectedIcon = iconKey
                updateColorSelectionUi()
            }
        }

        for ((hex, button, _) in colorOptions) {
            button.setOnClickListener {
                selectedColor = hex
                updateColorSelectionUi()
            }
        }

        updateColorSelectionUi()

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnCancelBucket.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnSaveBucket.setOnClickListener {
            val name = dialogBinding.etBucketName.text?.toString()?.trim().orEmpty()
            val balanceStr = dialogBinding.etBucketBalance.text?.toString()?.trim().orEmpty()

            if (name.isEmpty()) {
                Toast.makeText(this, "Account name is required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val enteredBalance = balanceStr.toDoubleOrNull() ?: convertedBalance
            val baseBalance = enteredBalance

            val updated = bucket.copy(
                name = name,
                icon = selectedIcon,
                colorHex = selectedColor,
                connectedAccounts = name,
                balance = baseBalance
            )
            dbHelper.updateWalletBucket(updated)
            dialog.dismiss()
            loadBucketData()
            WalletDataSyncManager.notifyChanged()
            Toast.makeText(this, "Account updated", Toast.LENGTH_SHORT).show()
        }

        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.show()
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    private fun showDeleteBucketDialog() {
        val bucket = currentBucket ?: return
        AlertDialog.Builder(this)
            .setTitle("Delete Category")
            .setMessage("Delete this category? Any linked transactions will also be cleared.")
            .setPositiveButton("Delete") { _, _ ->
                dbHelper.deleteWalletBucket(bucket.id)
                WalletDataSyncManager.notifyChanged()
                Toast.makeText(this, "Category deleted", Toast.LENGTH_SHORT).show()
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadBucketData() {
        currentBucket = dbHelper.getWalletBucketById(bucketId)
        val bucket = currentBucket
        if (bucket == null) {
            Toast.makeText(this, "Bucket not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val parsedColor = try {
            Color.parseColor(bucket.colorHex)
        } catch (_: Exception) {
            Color.parseColor("#06B6D4")
        }

        // Apply accent color to header trim and edit balance button
        binding.viewBucketAccentTrim.backgroundTintList = ColorStateList.valueOf(parsedColor)
        binding.btnAdjustBalanceHero.strokeColor = ColorStateList.valueOf(parsedColor)
        binding.btnAdjustBalanceHero.setTextColor(parsedColor)
        binding.btnAdjustBalanceHero.iconTint = ColorStateList.valueOf(parsedColor)
        val btnGlow = ColorUtils.setAlphaComponent(parsedColor, (255 * 0.12).toInt())
        binding.btnAdjustBalanceHero.backgroundTintList = ColorStateList.valueOf(btnGlow)

        binding.tvBucketDetailTitle.text = bucket.name

        val convertedBalance = bucket.balance
        binding.tvBucketTotalBalance.text = String.format(
            Locale.US,
            "%s%.2f",
            currentSymbol,
            convertedBalance
        )

        // Populate Linked Payment Methods Chips with accent tint
        binding.chipGroupLinkedMethods.removeAllViews()
        val accounts = bucket.connectedAccounts.split("•", ",").map { it.trim() }.filter { it.isNotEmpty() }
        val chipGlow = ColorUtils.setAlphaComponent(parsedColor, (255 * 0.15).toInt())
        if (accounts.isNotEmpty()) {
            for (acc in accounts) {
                val chip = Chip(this).apply {
                    text = acc
                    setChipBackgroundColor(ColorStateList.valueOf(chipGlow))
                    setTextColor(parsedColor)
                    textSize = 11f
                    isClickable = false
                    isCheckable = false
                }
                binding.chipGroupLinkedMethods.addView(chip)
            }
        } else {
            val chip = Chip(this).apply {
                text = "Direct Funds"
                setChipBackgroundColor(ColorStateList.valueOf(chipGlow))
                setTextColor(parsedColor)
                textSize = 11f
                isClickable = false
                isCheckable = false
            }
            binding.chipGroupLinkedMethods.addView(chip)
        }

        // Calculate Inflow & Outflow for this bucket (100% Pure Raw Values)
        val income = dbHelper.getBucketIncome(bucket.id)
        val expense = dbHelper.getBucketExpenses(bucket.id)
        binding.tvBucketInflow.text = String.format(Locale.US, "+%s%.2f", currentSymbol, income)
        binding.tvBucketOutflow.text = String.format(Locale.US, "-%s%.2f", currentSymbol, expense)

        // Calculate chronological running balances for each transaction
        val allBucketTransactions = dbHelper.getTransactionsByBucket(bucket.id, null)
        val runningBalances = mutableMapOf<Long, Double>()
        var runningBal = bucket.balance
        for (tx in allBucketTransactions) {
            runningBalances[tx.id] = runningBal
            if (tx.type == "INCOME") {
                runningBal -= tx.amount
            } else {
                runningBal += tx.amount
            }
        }
        val showRunningBalance = AppSettings.isShowRunningBalanceEnabled(this)

        // Load filtered transactions for this bucket
        val transactions = dbHelper.getTransactionsByBucket(bucket.id, currentFilter)
        transactionsAdapter.updateData(
            newTransactions = transactions,
            newCards = emptyMap(),
            newSymbol = currentSymbol,
            newRate = 1.0,
            newRunningBalances = runningBalances,
            newShowRunningBalance = showRunningBalance,
            newCurrencyCode = currentCurrency
        )

        if (transactions.isEmpty()) {
            binding.rvBucketTransactions.visibility = View.GONE
            binding.tvEmptyBucketTransactions.visibility = View.VISIBLE
        } else {
            binding.rvBucketTransactions.visibility = View.VISIBLE
            binding.tvEmptyBucketTransactions.visibility = View.GONE
        }

        renderMiniChart()
    }

    private fun setupMiniCharts() {
        binding.bucketMiniPieChart.apply {
            setBackgroundColor(Color.TRANSPARENT)
            description.isEnabled = false
            legend.isEnabled = false
            setDrawEntryLabels(false)
            isDrawHoleEnabled = true
            setHoleColor(Color.TRANSPARENT)
            holeRadius = 65f
            transparentCircleRadius = 0f
            setDrawCenterText(true)
            setCenterTextColor(Color.WHITE)
            setCenterTextSize(10f)
            setCenterTextTypeface(android.graphics.Typeface.DEFAULT_BOLD)
            setTouchEnabled(false)
        }

        binding.bucketMiniBarChart.apply {
            setBackgroundColor(Color.TRANSPARENT)
            description.isEnabled = false
            legend.isEnabled = false
            xAxis.isEnabled = false
            axisLeft.apply {
                isEnabled = false
                axisMinimum = 0f
            }
            axisRight.isEnabled = false
            setDrawGridBackground(false)
            setDrawBorders(false)
            setTouchEnabled(false)
            setDrawBarShadow(false)
            setDrawValueAboveBar(false)
            setPinchZoom(false)
            setScaleEnabled(false)
        }
    }

    private fun renderMiniChart() {
        val bucket = currentBucket ?: return
        val allTransactions = dbHelper.getTransactionsByBucket(bucket.id, "ALL")

        if (allTransactions.isEmpty()) {
            if (miniChartMode == MiniChartMode.PIE) {
                binding.layoutMiniChartEmpty.visibility = View.GONE
                binding.bucketMiniPieChart.visibility = View.VISIBLE
                binding.bucketMiniBarChart.visibility = View.GONE
                renderMiniPieChart(emptyList())
            } else {
                binding.layoutMiniChartEmpty.visibility = View.VISIBLE
                binding.bucketMiniPieChart.visibility = View.GONE
                binding.bucketMiniBarChart.visibility = View.GONE
            }
            return
        }

        binding.layoutMiniChartEmpty.visibility = View.GONE

        if (miniChartMode == MiniChartMode.PIE) {
            binding.bucketMiniPieChart.visibility = View.VISIBLE
            binding.bucketMiniBarChart.visibility = View.GONE
            renderMiniPieChart(allTransactions)
        } else {
            binding.bucketMiniPieChart.visibility = View.GONE
            binding.bucketMiniBarChart.visibility = View.VISIBLE
            renderMiniBarChart(allTransactions)
        }
    }

    private fun renderMiniPieChart(transactions: List<TransactionItem>) {
        if (transactions.isEmpty()) {
            val emptyEntries = arrayListOf(PieEntry(1f, ""))
            val emptyDataSet = PieDataSet(emptyEntries, "").apply {
                colors = listOf(Color.parseColor("#334155"))
                sliceSpace = 0f
                setDrawValues(false)
            }
            binding.bucketMiniPieChart.apply {
                setHoleColor(Color.TRANSPARENT)
                centerText = "No Records"
                setCenterTextColor(Color.parseColor("#94A3B8"))
                setCenterTextSize(10f)
                setCenterTextTypeface(android.graphics.Typeface.DEFAULT_BOLD)
                data = PieData(emptyDataSet)
                invalidate()
            }
            return
        }

        val expenses = transactions.filter { it.type.equals("EXPENSE", ignoreCase = true) }
        val incomes = transactions.filter { it.type.equals("INCOME", ignoreCase = true) }

        val totalIncome = incomes.sumOf { it.amount }
        val totalExpense = expenses.sumOf { it.amount }
        val totalVolume = totalIncome + totalExpense

        if (totalVolume <= 0.0) {
            val emptyEntries = arrayListOf(PieEntry(1f, ""))
            val emptyDataSet = PieDataSet(emptyEntries, "").apply {
                colors = listOf(Color.parseColor("#334155"))
                sliceSpace = 0f
                setDrawValues(false)
            }
            binding.bucketMiniPieChart.apply {
                setHoleColor(Color.TRANSPARENT)
                centerText = "No Records"
                setCenterTextColor(Color.parseColor("#94A3B8"))
                setCenterTextSize(10f)
                setCenterTextTypeface(android.graphics.Typeface.DEFAULT_BOLD)
                data = PieData(emptyDataSet)
                invalidate()
            }
            return
        }

        val defaultCategoryColors = mapOf(
            "salary" to "#10B981",        // Emerald
            "income" to "#10B981",        // Emerald
            "deposit" to "#10B981",       // Emerald
            "parent's gift" to "#10B981", // Emerald
            "parents gift" to "#10B981",
            "gift" to "#34D399",          // Light emerald/mint
            "freelance" to "#06B6D4",     // Teal
            "business" to "#3B82F6",      // Blue
            "investment" to "#8B5CF6",    // Violet
            "allowance" to "#10B981",     // Emerald
            "food" to "#F59E0B",          // Amber
            "dining" to "#F59E0B",
            "groceries" to "#F97316",     // Orange (distinct from income emerald)
            "gadget" to "#8B5CF6",        // Violet
            "gadgets" to "#8B5CF6",       // Violet
            "electronics" to "#8B5CF6",
            "tech" to "#06B6D4",          // Teal
            "shopping" to "#EC4899",      // Pink
            "entertainment" to "#A855F7", // Purple
            "bills" to "#38BDF8",         // Sky
            "utilities" to "#0EA5E9",
            "transport" to "#06B6D4",     // Teal
            "travel" to "#38BDF8",
            "health" to "#EF4444",        // Red/Rose
            "medical" to "#EF4444",
            "education" to "#6366F1",     // Indigo
            "study" to "#6366F1",
            "tuition" to "#6366F1",
            "subscription" to "#A855F7",
            "other" to "#64748B"
        )

        val expenseFallbackPalette = listOf(
            "#F43F5E", // Rose
            "#F59E0B", // Amber
            "#8B5CF6", // Violet
            "#06B6D4", // Teal
            "#38BDF8", // Sky
            "#EC4899", // Pink
            "#F97316", // Orange
            "#6366F1"  // Indigo
        )

        val incomePalette = listOf(
            "#10B981", // Emerald Green
            "#059669", // Dark Emerald
            "#34D399", // Mint / Light Emerald
            "#14B8A6", // Teal-Green
            "#6EE7B7"  // Light Green
        )

        val entries = ArrayList<PieEntry>()
        val colors = ArrayList<Int>()

        // 1. If income exists, group and add Income slices (in Emerald Green)
        if (incomes.isNotEmpty()) {
            val incomeCatMap = mutableMapOf<String, Double>()
            for (tx in incomes) {
                val cat = if (tx.category.isNotBlank()) tx.category.trim() else "Income"
                incomeCatMap[cat] = (incomeCatMap[cat] ?: 0.0) + tx.amount
            }
            var incIdx = 0
            val sortedIncome = incomeCatMap.entries.sortedByDescending { it.value }
            for (entry in sortedIncome) {
                val label = if (expenses.isNotEmpty() && !entry.key.startsWith("Income", ignoreCase = true)) {
                    "Income: ${entry.key}"
                } else {
                    entry.key
                }
                entries.add(PieEntry(entry.value.toFloat(), label))
                val colorHex = incomePalette[incIdx % incomePalette.size]
                colors.add(Color.parseColor(colorHex))
                incIdx++
            }
        }

        // 2. If expenses exist, group and add Expense slices (in Category Colors / Rose)
        if (expenses.isNotEmpty()) {
            val expenseCatMap = mutableMapOf<String, Double>()
            for (tx in expenses) {
                val cat = if (tx.category.isNotBlank()) tx.category.trim() else "Expense"
                expenseCatMap[cat] = (expenseCatMap[cat] ?: 0.0) + tx.amount
            }
            var expIdx = 0
            val sortedExpense = expenseCatMap.entries.sortedByDescending { it.value }
            for (entry in sortedExpense) {
                val catKey = entry.key.lowercase(Locale.US).trim()
                val customCat = dbHelper.getTransactionCategoryByName(entry.key)
                val colorHex = when {
                    customCat != null && customCat.colorHex.isNotBlank() && customCat.colorHex != "#0284C7" -> customCat.colorHex
                    defaultCategoryColors.containsKey(catKey) -> defaultCategoryColors[catKey]!!
                    customCat != null && customCat.colorHex.isNotBlank() -> customCat.colorHex
                    else -> expenseFallbackPalette[expIdx % expenseFallbackPalette.size]
                }
                val parsedColor = try {
                    Color.parseColor(colorHex)
                } catch (_: Exception) {
                    Color.parseColor(expenseFallbackPalette[expIdx % expenseFallbackPalette.size])
                }
                entries.add(PieEntry(entry.value.toFloat(), entry.key))
                colors.add(parsedColor)
                expIdx++
            }
        }

        val dataSet = PieDataSet(entries, "").apply {
            this.colors = colors
            sliceSpace = 2f
            setDrawValues(false)
        }

        // Center Hole Net Summary calculation
        val net = totalIncome - totalExpense
        val formattedCenter: String
        val centerTextColor: Int

        if (incomes.isNotEmpty() && expenses.isNotEmpty()) {
            // Dual-segment Cashflow Donut: display Net Cashflow
            if (net >= 0) {
                formattedCenter = if (net % 1.0 == 0.0) {
                    String.format(Locale.US, "+%s%,d", currentSymbol, net.toLong())
                } else {
                    String.format(Locale.US, "+%s%,.0f", currentSymbol, net)
                }
                centerTextColor = Color.parseColor("#10B981") // Emerald Green
            } else {
                val absNet = Math.abs(net)
                formattedCenter = if (absNet % 1.0 == 0.0) {
                    String.format(Locale.US, "-%s%,d", currentSymbol, absNet.toLong())
                } else {
                    String.format(Locale.US, "-%s%,.0f", currentSymbol, absNet)
                }
                centerTextColor = Color.parseColor("#F43F5E") // Rose
            }
        } else if (incomes.isNotEmpty()) {
            formattedCenter = if (totalIncome % 1.0 == 0.0) {
                String.format(Locale.US, "+%s%,d", currentSymbol, totalIncome.toLong())
            } else {
                String.format(Locale.US, "+%s%,.0f", currentSymbol, totalIncome)
            }
            centerTextColor = Color.parseColor("#10B981")
        } else {
            formattedCenter = if (totalExpense % 1.0 == 0.0) {
                String.format(Locale.US, "-%s%,d", currentSymbol, totalExpense.toLong())
            } else {
                String.format(Locale.US, "-%s%,.0f", currentSymbol, totalExpense)
            }
            centerTextColor = Color.parseColor("#F43F5E")
        }

        binding.bucketMiniPieChart.apply {
            setHoleColor(Color.TRANSPARENT)
            centerText = formattedCenter
            setCenterTextColor(centerTextColor)
            setCenterTextSize(10f)
            setCenterTextTypeface(android.graphics.Typeface.DEFAULT_BOLD)
            data = PieData(dataSet)
            invalidate()
        }
    }

    private fun renderMiniBarChart(transactions: List<TransactionItem>) {
        val expenses = transactions.filter { it.type.equals("EXPENSE", ignoreCase = true) }
        val incomes = transactions.filter { it.type.equals("INCOME", ignoreCase = true) }

        val totalIncome = incomes.sumOf { it.amount }.toFloat()
        val totalExpense = expenses.sumOf { it.amount }.toFloat()

        val entries = arrayListOf(
            BarEntry(0f, totalIncome),
            BarEntry(1f, totalExpense)
        )

        val barDataSet = BarDataSet(entries, "Dual Comparison").apply {
            colors = listOf(
                Color.parseColor("#10B981"), // Emerald Green for Income
                Color.parseColor("#EF4444")  // Rose Red for Expense
            )
            setDrawValues(false)
        }

        val barData = BarData(barDataSet).apply {
            barWidth = 0.35f
        }

        binding.bucketMiniBarChart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            xAxis.apply {
                isEnabled = false
                axisMinimum = -0.5f
                axisMaximum = 1.5f
            }
            axisLeft.apply {
                isEnabled = false
                axisMinimum = 0f
            }
            axisRight.isEnabled = false
            setDrawGridBackground(false)
            setDrawBorders(false)
            setTouchEnabled(false)
            setDrawBarShadow(false)
            setDrawValueAboveBar(false)
            setPinchZoom(false)
            setScaleEnabled(false)
            data = barData
            invalidate()
        }
    }

    private fun applyToggleStyles(binding: DialogAddTransactionBinding, mode: String) {
        val unselectedStroke = androidx.core.content.ContextCompat.getColor(this, R.color.card_stroke)
        val unselectedText = androidx.core.content.ContextCompat.getColor(this, R.color.text_secondary)

        val incomeBg = if (mode == "INCOME") Color.parseColor("#064E3B") else Color.TRANSPARENT
        val incomeText = if (mode == "INCOME") Color.parseColor("#34D399") else unselectedText
        val incomeStroke = if (mode == "INCOME") Color.parseColor("#059669") else unselectedStroke
        binding.btnTypeIncome.setBackgroundColor(incomeBg)
        binding.btnTypeIncome.setTextColor(incomeText)
        binding.btnTypeIncome.strokeColor = android.content.res.ColorStateList.valueOf(incomeStroke)

        val expenseBg = if (mode == "EXPENSE") Color.parseColor("#4C1D24") else Color.TRANSPARENT
        val expenseText = if (mode == "EXPENSE") Color.parseColor("#F87171") else unselectedText
        val expenseStroke = if (mode == "EXPENSE") Color.parseColor("#991B1B") else unselectedStroke
        binding.btnTypeExpense.setBackgroundColor(expenseBg)
        binding.btnTypeExpense.setTextColor(expenseText)
        binding.btnTypeExpense.strokeColor = android.content.res.ColorStateList.valueOf(expenseStroke)

        val transferBg = if (mode == "TRANSFER") Color.parseColor("#0C4A6E") else Color.TRANSPARENT
        val transferText = if (mode == "TRANSFER") Color.parseColor("#38BDF8") else unselectedText
        val transferStroke = if (mode == "TRANSFER") Color.parseColor("#0284C7") else unselectedStroke
        binding.btnTypeTransfer.setBackgroundColor(transferBg)
        binding.btnTypeTransfer.setTextColor(transferText)
        binding.btnTypeTransfer.strokeColor = android.content.res.ColorStateList.valueOf(transferStroke)
    }

    private fun showAddTransactionDialog() {
        val bucket = currentBucket ?: return
        val allBuckets = dbHelper.getAllWalletBuckets()
        val dialogBinding = DialogAddTransactionBinding.inflate(layoutInflater)
        dialogBinding.tilTxAmount.hint = "Amount ($currentSymbol - $currentCurrency)"

        var txType = "INCOME"
        dialogBinding.toggleTxType.check(R.id.btnTypeIncome)
        applyToggleStyles(dialogBinding, "INCOME")

        val categoryHelper = TransactionCategoryHelper(
            context = this,
            dbHelper = dbHelper,
            actvCategory = dialogBinding.actvTxCategory,
            currentTxType = txType,
            chipCreateCategory = dialogBinding.chipCreateCategory
        )

        // Pre-select current bucket
        dialogBinding.tilTxCard.visibility = View.GONE
        dialogBinding.tilTxToAccount.visibility = View.GONE
        dialogBinding.spTxCard.visibility = View.GONE

        val bucketNames = allBuckets.map { it.name }
        val bucketAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, bucketNames)
        dialogBinding.actvTxCard.setAdapter(bucketAdapter)
        dialogBinding.actvTxCard.setText(bucket.name, false)

        fun updateToAccountDropdown() {
            val selectedFromName = dialogBinding.actvTxCard.text?.toString()?.trim().orEmpty()
            val destBuckets = allBuckets.filterNot { it.name.equals(selectedFromName, ignoreCase = true) }
            val destNames = destBuckets.map { it.name }
            val toAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, destNames)
            dialogBinding.actvTxToAccount.setAdapter(toAdapter)
            if (destNames.isNotEmpty()) {
                dialogBinding.actvTxToAccount.setText(destNames.first(), false)
            } else {
                dialogBinding.actvTxToAccount.setText("", false)
            }
        }

        dialogBinding.actvTxToAccount.setOnClickListener { dialogBinding.actvTxToAccount.showDropDown() }
        dialogBinding.actvTxCard.setOnClickListener { dialogBinding.actvTxCard.showDropDown() }
        dialogBinding.actvTxCard.setOnItemClickListener { _, _, _, _ -> updateToAccountDropdown() }

        dialogBinding.toggleTxType.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btnTypeTransfer -> {
                        txType = "TRANSFER"
                        dialogBinding.tilTxCard.visibility = View.VISIBLE
                        dialogBinding.tilTxCard.hint = "From Account"
                        dialogBinding.tilTxToAccount.visibility = View.VISIBLE
                        dialogBinding.tilTxToAccount.hint = "To Account"
                        dialogBinding.tilTxCategory.visibility = View.GONE
                        dialogBinding.chipCreateCategory.visibility = View.GONE
                        dialogBinding.btnSaveTx.text = "Execute Transfer"
                        updateToAccountDropdown()
                    }
                    R.id.btnTypeExpense -> {
                        txType = "EXPENSE"
                        dialogBinding.tilTxCard.visibility = View.GONE
                        dialogBinding.tilTxToAccount.visibility = View.GONE
                        dialogBinding.tilTxCategory.visibility = View.VISIBLE
                        dialogBinding.btnSaveTx.text = "Save Record"
                        categoryHelper.setTxType("EXPENSE")
                    }
                    else -> {
                        txType = "INCOME"
                        dialogBinding.tilTxCard.visibility = View.GONE
                        dialogBinding.tilTxToAccount.visibility = View.GONE
                        dialogBinding.tilTxCategory.visibility = View.VISIBLE
                        dialogBinding.btnSaveTx.text = "Save Record"
                        categoryHelper.setTxType("INCOME")
                    }
                }
                applyToggleStyles(dialogBinding, txType)
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnSaveTx.setOnClickListener {
            val amountStr = dialogBinding.etTxAmount.text?.toString()?.trim().orEmpty()
            if (amountStr.isEmpty()) {
                Toast.makeText(this, "Amount is required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val enteredAmount = amountStr.toDoubleOrNull() ?: 0.0
            if (enteredAmount <= 0.0) {
                Toast.makeText(this, "Please enter a valid amount greater than 0", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val notes = dialogBinding.etTxNotes.text?.toString()?.trim().orEmpty()
            val customTitle = dialogBinding.etTxTitle.text?.toString()?.trim().orEmpty()

            if (txType == "TRANSFER") {
                if (allBuckets.size < 2) {
                    Toast.makeText(this, "Transfer requires at least 2 accounts", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val fromName = dialogBinding.actvTxCard.text?.toString()?.trim().orEmpty()
                val toName = dialogBinding.actvTxToAccount.text?.toString()?.trim().orEmpty()

                val fromBucket = allBuckets.firstOrNull { it.name.equals(fromName, ignoreCase = true) } ?: bucket
                val toBucket = allBuckets.firstOrNull { it.name.equals(toName, ignoreCase = true) }

                if (toBucket == null || fromBucket.id == toBucket.id) {
                    Toast.makeText(this, "Please select different source and destination accounts", Toast.LENGTH_SHORT).show()
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
                    dialog.dismiss()
                    loadBucketData()
                    WalletDataSyncManager.notifyChanged()
                    val formatted = CurrencyUtils.formatRawCurrency(enteredAmount, currentSymbol)
                    Toast.makeText(this, "Transferred $formatted from ${fromBucket.name} to ${toBucket.name}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Failed to execute transfer", Toast.LENGTH_SHORT).show()
                }
            } else {
                val category = categoryHelper.getSelectedCategoryAndEnsurePersisted()
                if (category.isBlank()) {
                    Toast.makeText(this, "Please select or create a category", Toast.LENGTH_SHORT).show()
                    dialogBinding.actvTxCategory.requestFocus()
                    return@setOnClickListener
                }
                val finalTitle = if (customTitle.isNotBlank()) customTitle else category

                val tx = TransactionItem(
                    title = finalTitle,
                    amount = enteredAmount,
                    type = txType,
                    category = category,
                    date = today,
                    cardId = 0L,
                    currency = currentCurrency,
                    notes = if (notes.isNotBlank()) "$notes (${bucket.name})" else "Account: ${bucket.name}",
                    bucketId = bucket.id
                )
                dbHelper.insertTransaction(tx)
                dialog.dismiss()
                loadBucketData()
                WalletDataSyncManager.notifyChanged()
                Toast.makeText(this, "Recorded to ${bucket.name}", Toast.LENGTH_SHORT).show()
            }
        }

        dialogBinding.etTxAmount.requestFocus()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.show()
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    private fun showEditTransactionDialog(tx: TransactionItem) {
        val bucket = currentBucket ?: return
        val dialogBinding = DialogAddTransactionBinding.inflate(layoutInflater)
        dialogBinding.tilTxAmount.hint = "Amount ($currentSymbol - $currentCurrency)"

        var txType = tx.type
        val isIncome = (txType == "INCOME")
        dialogBinding.toggleTxType.check(if (isIncome) R.id.btnTypeIncome else R.id.btnTypeExpense)
        applyToggleStyles(dialogBinding, if (isIncome) "INCOME" else "EXPENSE")

        val categoryHelper = TransactionCategoryHelper(
            context = this,
            dbHelper = dbHelper,
            actvCategory = dialogBinding.actvTxCategory,
            currentTxType = txType,
            chipCreateCategory = dialogBinding.chipCreateCategory
        )

        dialogBinding.tilTxCard.visibility = View.GONE
        dialogBinding.spTxCard.visibility = View.GONE

        // Pre-fill existing transaction data (Pure 100% Raw Values)
        val convertedAmount = tx.amount
        dialogBinding.etTxAmount.setText(
            if (convertedAmount % 1.0 == 0.0) String.format(Locale.US, "%.0f", convertedAmount)
            else String.format(Locale.US, "%.2f", convertedAmount)
        )
        dialogBinding.actvTxCategory.setText(tx.category, false)
        dialogBinding.etTxTitle.setText(tx.title)
        dialogBinding.etTxNotes.setText(tx.notes)

        dialogBinding.toggleTxType.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val activeIncome = (checkedId == R.id.btnTypeIncome)
                txType = if (activeIncome) "INCOME" else "EXPENSE"
                applyToggleStyles(dialogBinding, txType)
                categoryHelper.setTxType(txType)
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Edit Record")
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnSaveTx.text = "Save Changes"
        dialogBinding.btnSaveTx.setOnClickListener {
            val title = dialogBinding.etTxTitle.text?.toString()?.trim().orEmpty()
            val amountStr = dialogBinding.etTxAmount.text?.toString()?.trim().orEmpty()
            val notes = dialogBinding.etTxNotes.text?.toString()?.trim().orEmpty()

            if (amountStr.isEmpty()) {
                Toast.makeText(this, "Amount is required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val enteredAmount = amountStr.toDoubleOrNull() ?: 0.0
            val baseAmount = enteredAmount
            val category = categoryHelper.getSelectedCategoryAndEnsurePersisted()
            if (category.isBlank()) {
                Toast.makeText(this, "Please select or create a category", Toast.LENGTH_SHORT).show()
                dialogBinding.actvTxCategory.requestFocus()
                return@setOnClickListener
            }
            val finalTitle = if (title.isNotBlank()) title else category

            val updatedTx = tx.copy(
                title = finalTitle,
                amount = baseAmount,
                type = txType,
                category = category,
                notes = notes,
                bucketId = bucket.id
            )
            dbHelper.updateTransaction(updatedTx)
            dialog.dismiss()
            loadBucketData()
            WalletDataSyncManager.notifyChanged()
            Toast.makeText(this, "Record updated", Toast.LENGTH_SHORT).show()
        }

        dialogBinding.etTxAmount.requestFocus()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.show()
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }
}

typealias BucketLedgerActivity = WalletBucketDetailActivity

