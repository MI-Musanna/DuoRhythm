package com.pocketdev.duorhythm.ui.wallet

import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.pocketdev.duorhythm.R
import com.pocketdev.duorhythm.data.local.db.DatabaseHelper
import com.pocketdev.duorhythm.data.model.TransactionItem
import com.pocketdev.duorhythm.databinding.ItemAnalyticsCategoryBinding
import com.pocketdev.duorhythm.databinding.SheetFinancialAnalyticsBinding
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class BucketAnalyticsBottomSheet : BottomSheetDialogFragment() {

    companion object {
        private const val ARG_BUCKET_ID = "arg_bucket_id"
        private const val ARG_BUCKET_NAME = "arg_bucket_name"
        private const val ARG_CURRENCY_SYMBOL = "arg_currency_symbol"

        fun newInstance(bucketId: Long, bucketName: String, currencySymbol: String): BucketAnalyticsBottomSheet {
            return BucketAnalyticsBottomSheet().apply {
                arguments = Bundle().apply {
                    putLong(ARG_BUCKET_ID, bucketId)
                    putString(ARG_BUCKET_NAME, bucketName)
                    putString(ARG_CURRENCY_SYMBOL, currencySymbol)
                }
            }
        }
    }

    private var _binding: SheetFinancialAnalyticsBinding? = null
    private val binding get() = _binding!!

    private lateinit var dbHelper: DatabaseHelper
    private var bucketId: Long = 0L
    private var bucketName: String = "Bucket"
    private var currentSymbol: String = "৳"

    private enum class Mode { BAR_TREND, CATEGORY_PIE }
    private enum class Timeframe { DAILY, WEEKLY, MONTHLY, YEARLY }

    private var currentMode = Mode.BAR_TREND
    private var currentTimeframe = Timeframe.MONTHLY

    private val chartColors = listOf(
        Color.parseColor("#10B981"), // Emerald Green
        Color.parseColor("#38BDF8"), // Sky Blue
        Color.parseColor("#F43F5E"), // Rose
        Color.parseColor("#8B5CF6"), // Violet
        Color.parseColor("#F59E0B"), // Amber
        Color.parseColor("#06B6D4"), // Cyan
        Color.parseColor("#EC4899"), // Pink
        Color.parseColor("#6366F1"), // Indigo
        Color.parseColor("#14B8A6"), // Teal
        Color.parseColor("#F97316"), // Orange
        Color.parseColor("#A855F7"), // Purple
        Color.parseColor("#84CC16")  // Lime
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bucketId = arguments?.getLong(ARG_BUCKET_ID, 0L) ?: 0L
        bucketName = arguments?.getString(ARG_BUCKET_NAME, "Bucket") ?: "Bucket"
        currentSymbol = arguments?.getString(ARG_CURRENCY_SYMBOL, "৳") ?: "৳"
        dbHelper = DatabaseHelper.getInstance(requireContext())
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            if (bottomSheet != null) {
                val behavior = BottomSheetBehavior.from(bottomSheet)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
                bottomSheet.setBackgroundColor(Color.TRANSPARENT)
            }
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SheetFinancialAnalyticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvSheetTitle.text = "$bucketName Analytics"
        setupListeners()
        setupCharts()
        renderAnalytics()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupListeners() {
        binding.btnSheetClose.setOnClickListener { dismiss() }

        binding.btnSheetModeBarTrend.setOnClickListener {
            if (currentMode != Mode.BAR_TREND) {
                currentMode = Mode.BAR_TREND
                updateModeButtons()
                renderAnalytics()
            }
        }

        binding.btnSheetModeCategoryPie.setOnClickListener {
            if (currentMode != Mode.CATEGORY_PIE) {
                currentMode = Mode.CATEGORY_PIE
                updateModeButtons()
                renderAnalytics()
            }
        }

        binding.chipGroupSheetTimeframe.setOnCheckedChangeListener { _, checkedId ->
            currentTimeframe = when (checkedId) {
                R.id.chipSheetDaily -> Timeframe.DAILY
                R.id.chipSheetWeekly -> Timeframe.WEEKLY
                R.id.chipSheetMonthly -> Timeframe.MONTHLY
                R.id.chipSheetYearly -> Timeframe.YEARLY
                else -> Timeframe.MONTHLY
            }
            renderAnalytics()
        }

        binding.rvSheetCategoryBreakdown.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun updateModeButtons() {
        if (currentMode == Mode.BAR_TREND) {
            binding.btnSheetModeBarTrend.setBackgroundColor(Color.parseColor("#0284C7"))
            binding.btnSheetModeBarTrend.setTextColor(Color.WHITE)
            binding.btnSheetModeCategoryPie.setBackgroundColor(Color.TRANSPARENT)
            binding.btnSheetModeCategoryPie.setTextColor(Color.parseColor("#94A3B8"))
        } else {
            binding.btnSheetModeCategoryPie.setBackgroundColor(Color.parseColor("#0284C7"))
            binding.btnSheetModeCategoryPie.setTextColor(Color.WHITE)
            binding.btnSheetModeBarTrend.setBackgroundColor(Color.TRANSPARENT)
            binding.btnSheetModeBarTrend.setTextColor(Color.parseColor("#94A3B8"))
        }
    }

    private fun setupCharts() {
        // Obsidian transparent styling for BarChart
        binding.sheetBarChart.apply {
            setBackgroundColor(Color.TRANSPARENT)
            setDrawGridBackground(false)
            setDrawBarShadow(false)
            setDrawValueAboveBar(true)
            description.isEnabled = false
            setPinchZoom(false)
            setScaleEnabled(false)

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                textColor = Color.WHITE
                gridColor = Color.parseColor("#1E293B")
                setDrawAxisLine(true)
                axisLineColor = Color.parseColor("#243656")
                granularity = 1f
                setCenterAxisLabels(true)
            }

            axisLeft.apply {
                textColor = Color.parseColor("#94A3B8")
                gridColor = Color.parseColor("#1E293B")
                axisLineColor = Color.parseColor("#243656")
                axisMinimum = 0f
            }

            axisRight.isEnabled = false

            legend.apply {
                textColor = Color.WHITE
                verticalAlignment = Legend.LegendVerticalAlignment.TOP
                horizontalAlignment = Legend.LegendHorizontalAlignment.RIGHT
                orientation = Legend.LegendOrientation.HORIZONTAL
                setDrawInside(false)
            }
        }

        // Obsidian transparent styling for PieChart
        binding.sheetPieChart.apply {
            setBackgroundColor(Color.TRANSPARENT)
            description.isEnabled = false
            setUsePercentValues(true)
            isDrawHoleEnabled = true
            setHoleColor(Color.TRANSPARENT)
            setTransparentCircleColor(Color.parseColor("#1E293B"))
            setTransparentCircleAlpha(110)
            holeRadius = 55f
            transparentCircleRadius = 60f
            setDrawCenterText(true)
            setCenterTextColor(Color.WHITE)
            setCenterTextSize(12f)
            setDrawEntryLabels(false)

            legend.apply {
                textColor = Color.WHITE
                verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
                horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
                orientation = Legend.LegendOrientation.HORIZONTAL
                setDrawInside(false)
                isWordWrapEnabled = true
            }
        }
    }

    private fun renderAnalytics() {
        val allTx = dbHelper.getTransactionsByBucket(bucketId, "ALL")
        val filteredTx = filterTransactionsByTimeframe(allTx, currentTimeframe)

        // Summary Calculations
        val totalIncome = filteredTx.filter { it.type == "INCOME" }.sumOf { it.amount }
        val totalExpense = filteredTx.filter { it.type == "EXPENSE" }.sumOf { it.amount }
        val netBalance = totalIncome - totalExpense

        val numFormat = NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }

        binding.tvSheetSummaryIncome.text = "+$currentSymbol${numFormat.format(totalIncome)}"
        binding.tvSheetSummaryExpense.text = "-$currentSymbol${numFormat.format(totalExpense)}"
        binding.tvSheetSummaryNet.text = if (netBalance >= 0) {
            "+$currentSymbol${numFormat.format(netBalance)}"
        } else {
            "-$currentSymbol${numFormat.format(Math.abs(netBalance))}"
        }
        binding.tvSheetSummaryNet.setTextColor(
            if (netBalance >= 0) Color.parseColor("#10B981") else Color.parseColor("#EF4444")
        )

        if (filteredTx.isEmpty()) {
            binding.sheetBarChart.visibility = View.GONE
            binding.sheetPieChart.visibility = View.GONE
            binding.layoutSheetEmptyChart.visibility = View.VISIBLE
            binding.rvSheetCategoryBreakdown.visibility = View.GONE
            binding.tvSheetEmptyBreakdown.visibility = View.VISIBLE
            binding.tvSheetCategoryCount.text = "0 Categories"
            return
        }

        binding.layoutSheetEmptyChart.visibility = View.GONE

        if (currentMode == Mode.BAR_TREND) {
            binding.sheetBarChart.visibility = View.VISIBLE
            binding.sheetPieChart.visibility = View.GONE
            renderBarChart(filteredTx)
        } else {
            binding.sheetBarChart.visibility = View.GONE
            binding.sheetPieChart.visibility = View.VISIBLE
            renderPieChart(filteredTx, totalExpense, numFormat)
        }

        renderCategoryBreakdown(filteredTx, totalExpense, numFormat)
    }

    private fun filterTransactionsByTimeframe(
        transactions: List<TransactionItem>,
        timeframe: Timeframe
    ): List<TransactionItem> {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()

        return when (timeframe) {
            Timeframe.DAILY -> {
                cal.add(Calendar.DAY_OF_YEAR, -6)
                val minDateStr = sdf.format(cal.time)
                transactions.filter { it.date >= minDateStr }
            }
            Timeframe.WEEKLY -> {
                cal.add(Calendar.DAY_OF_YEAR, -27)
                val minDateStr = sdf.format(cal.time)
                transactions.filter { it.date >= minDateStr }
            }
            Timeframe.MONTHLY -> {
                val yearPrefix = SimpleDateFormat("yyyy-", Locale.US).format(Date())
                transactions.filter { it.date.startsWith(yearPrefix) }
            }
            Timeframe.YEARLY -> {
                transactions
            }
        }
    }

    private fun renderBarChart(transactions: List<TransactionItem>) {
        val groupSpace = 0.30f
        val barSpace = 0.05f
        val barWidth = 0.30f

        val (labels, incomeEntries, expenseEntries) = when (currentTimeframe) {
            Timeframe.DAILY -> generateDailyBarData(transactions, 7)
            Timeframe.WEEKLY -> generateWeeklyBarData(transactions)
            Timeframe.MONTHLY -> generateMonthlyBarData(transactions)
            Timeframe.YEARLY -> generateYearlyBarData(transactions)
        }

        if (labels.isEmpty() || (incomeEntries.isEmpty() && expenseEntries.isEmpty())) {
            binding.sheetBarChart.visibility = View.GONE
            binding.layoutSheetEmptyChart.visibility = View.VISIBLE
            return
        }

        val incomeSet = BarDataSet(incomeEntries, "Income").apply {
            color = Color.parseColor("#10B981") // Emerald Green
            valueTextColor = Color.WHITE
            valueTextSize = 9f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return if (value > 0) String.format(Locale.US, "%.0f", value) else ""
                }
            }
        }

        val expenseSet = BarDataSet(expenseEntries, "Expense").apply {
            color = Color.parseColor("#EF4444") // Red
            valueTextColor = Color.WHITE
            valueTextSize = 9f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return if (value > 0) String.format(Locale.US, "%.0f", value) else ""
                }
            }
        }

        val data = BarData(incomeSet, expenseSet).apply {
            this.barWidth = barWidth
        }

        binding.sheetBarChart.apply {
            this.data = data
            xAxis.valueFormatter = IndexAxisValueFormatter(labels)
            xAxis.axisMinimum = 0f
            xAxis.axisMaximum = labels.size.toFloat()
            groupBars(0f, groupSpace, barSpace)
            notifyDataSetChanged()
            invalidate()
        }
    }

    private fun renderPieChart(
        transactions: List<TransactionItem>,
        totalExpense: Double,
        numFormat: NumberFormat
    ) {
        val expenseTx = transactions.filter { it.type.equals("EXPENSE", ignoreCase = true) }
        val incomeTx = transactions.filter { it.type.equals("INCOME", ignoreCase = true) }
        val isIncomeOnly = expenseTx.isEmpty() && incomeTx.isNotEmpty()
        val targetTx = if (isIncomeOnly) incomeTx else expenseTx
        val targetTotal = if (isIncomeOnly) incomeTx.sumOf { it.amount } else totalExpense
        val categoryMap = mutableMapOf<String, Double>()

        for (tx in targetTx) {
            val cat = if (tx.category.isNotBlank()) tx.category else if (isIncomeOnly) "Income" else "Other"
            categoryMap[cat] = (categoryMap[cat] ?: 0.0) + tx.amount
        }

        if (categoryMap.isEmpty() || targetTotal <= 0.0) {
            binding.sheetPieChart.visibility = View.GONE
            binding.layoutSheetEmptyChart.visibility = View.VISIBLE
            return
        }

        binding.sheetPieChart.visibility = View.VISIBLE
        binding.layoutSheetEmptyChart.visibility = View.GONE

        val entries = ArrayList<PieEntry>()
        val colors = ArrayList<Int>()
        var colorIdx = 0

        val sortedCategories = categoryMap.entries.sortedByDescending { it.value }
        for (entry in sortedCategories) {
            val label = entry.key
            entries.add(PieEntry(entry.value.toFloat(), label))
            val customCat = dbHelper.getTransactionCategoryByName(label)
            val parsedColor = if (customCat != null && customCat.colorHex.isNotBlank() && customCat.colorHex != "#0284C7") {
                try { Color.parseColor(customCat.colorHex) } catch (_: Exception) { chartColors[colorIdx % chartColors.size] }
            } else if (isIncomeOnly) {
                Color.parseColor("#10B981")
            } else {
                chartColors[colorIdx % chartColors.size]
            }
            colors.add(parsedColor)
            colorIdx++
        }

        val dataSet = PieDataSet(entries, "").apply {
            this.colors = colors
            sliceSpace = 3f
            selectionShift = 5f
            valueTextColor = Color.WHITE
            valueTextSize = 10f
            valueFormatter = PercentFormatter(binding.sheetPieChart)
        }

        binding.sheetPieChart.apply {
            centerText = if (isIncomeOnly) {
                "Income\n+$currentSymbol${numFormat.format(targetTotal)}"
            } else {
                "Expense\n-$currentSymbol${numFormat.format(totalExpense)}"
            }
            data = PieData(dataSet)
            highlightValues(null)
            invalidate()
        }
    }

    private fun renderCategoryBreakdown(
        transactions: List<TransactionItem>,
        totalExpense: Double,
        numFormat: NumberFormat
    ) {
        val expenseTx = transactions.filter { it.type.equals("EXPENSE", ignoreCase = true) }
        val incomeTx = transactions.filter { it.type.equals("INCOME", ignoreCase = true) }
        val isIncomeOnly = expenseTx.isEmpty() && incomeTx.isNotEmpty()
        val targetTx = if (isIncomeOnly) incomeTx else expenseTx
        val targetTotal = if (isIncomeOnly) incomeTx.sumOf { it.amount } else totalExpense
        val categoryMap = mutableMapOf<String, Double>()

        for (tx in targetTx) {
            val cat = if (tx.category.isNotBlank()) tx.category else if (isIncomeOnly) "Income" else "Other"
            categoryMap[cat] = (categoryMap[cat] ?: 0.0) + tx.amount
        }

        if (categoryMap.isEmpty() || targetTotal <= 0.0) {
            binding.rvSheetCategoryBreakdown.visibility = View.GONE
            binding.tvSheetEmptyBreakdown.visibility = View.VISIBLE
            binding.tvSheetCategoryCount.text = "0 Categories"
            return
        }

        binding.rvSheetCategoryBreakdown.visibility = View.VISIBLE
        binding.tvSheetEmptyBreakdown.visibility = View.GONE

        val sortedList = categoryMap.entries.sortedByDescending { it.value }
        binding.tvSheetCategoryCount.text = "${sortedList.size} Categories"

        val items = sortedList.mapIndexed { idx, entry ->
            val pct = if (targetTotal > 0) (entry.value / targetTotal) * 100.0 else 0.0
            val customCat = dbHelper.getTransactionCategoryByName(entry.key)
            val assignedColor = if (customCat != null && customCat.colorHex.isNotBlank() && customCat.colorHex != "#0284C7") {
                try { Color.parseColor(customCat.colorHex) } catch (_: Exception) { chartColors[idx % chartColors.size] }
            } else if (isIncomeOnly) {
                Color.parseColor("#10B981")
            } else {
                chartColors[idx % chartColors.size]
            }
            CategoryBreakdownItem(
                category = entry.key,
                amount = entry.value,
                percentage = pct,
                color = assignedColor
            )
        }

        binding.rvSheetCategoryBreakdown.adapter = CategoryBreakdownAdapter(items, currentSymbol, numFormat)
    }

    // Helper data generation methods for bar trend
    private fun generateDailyBarData(transactions: List<TransactionItem>, days: Int): Triple<List<String>, List<BarEntry>, List<BarEntry>> {
        val labels = ArrayList<String>()
        val inEntries = ArrayList<BarEntry>()
        val exEntries = ArrayList<BarEntry>()

        val sdf = SimpleDateFormat("EEE", Locale.US)
        val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        val dailyIncome = mutableMapOf<String, Float>()
        val dailyExpense = mutableMapOf<String, Float>()

        for (tx in transactions) {
            val key = tx.date
            if (tx.type == "INCOME") {
                dailyIncome[key] = (dailyIncome[key] ?: 0f) + tx.amount.toFloat()
            } else if (tx.type == "EXPENSE") {
                dailyExpense[key] = (dailyExpense[key] ?: 0f) + tx.amount.toFloat()
            }
        }

        for (i in (days - 1) downTo 0) {
            val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -i) }
            val key = dayFormat.format(c.time)
            val label = sdf.format(c.time)
            val idx = days - 1 - i

            labels.add(label)
            inEntries.add(BarEntry(idx.toFloat(), dailyIncome[key] ?: 0f))
            exEntries.add(BarEntry(idx.toFloat(), dailyExpense[key] ?: 0f))
        }

        return Triple(labels, inEntries, exEntries)
    }

    private fun generateWeeklyBarData(transactions: List<TransactionItem>): Triple<List<String>, List<BarEntry>, List<BarEntry>> {
        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val labels = listOf("W1", "W2", "W3", "W4")
        val inEntries = ArrayList<BarEntry>()
        val exEntries = ArrayList<BarEntry>()

        for (w in 3 downTo 0) {
            val calEnd = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -w * 7) }
            val calStart = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -(w + 1) * 7 + 1) }
            val startStr = sdfDate.format(calStart.time)
            val endStr = sdfDate.format(calEnd.time)
            val inSum = transactions.filter { it.date in startStr..endStr && it.type == "INCOME" }.sumOf { it.amount }.toFloat()
            val exSum = transactions.filter { it.date in startStr..endStr && it.type == "EXPENSE" }.sumOf { it.amount }.toFloat()
            val idx = 3 - w
            inEntries.add(BarEntry(idx.toFloat(), inSum))
            exEntries.add(BarEntry(idx.toFloat(), exSum))
        }
        return Triple(labels, inEntries, exEntries)
    }

    private fun generateMonthlyBarData(transactions: List<TransactionItem>): Triple<List<String>, List<BarEntry>, List<BarEntry>> {
        val labels = ArrayList<String>()
        val inEntries = ArrayList<BarEntry>()
        val exEntries = ArrayList<BarEntry>()
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, -5)
        val monthLabelFormat = SimpleDateFormat("MMM", Locale.US)
        val monthKeyFormat = SimpleDateFormat("yyyy-MM", Locale.US)

        for (m in 0..5) {
            val monthKey = monthKeyFormat.format(cal.time)
            val label = monthLabelFormat.format(cal.time)
            val inSum = transactions.filter { it.date.startsWith(monthKey) && it.type == "INCOME" }.sumOf { it.amount }.toFloat()
            val exSum = transactions.filter { it.date.startsWith(monthKey) && it.type == "EXPENSE" }.sumOf { it.amount }.toFloat()
            labels.add(label)
            inEntries.add(BarEntry(m.toFloat(), inSum))
            exEntries.add(BarEntry(m.toFloat(), exSum))
            cal.add(Calendar.MONTH, 1)
        }
        return Triple(labels, inEntries, exEntries)
    }

    private fun generateYearlyBarData(transactions: List<TransactionItem>): Triple<List<String>, List<BarEntry>, List<BarEntry>> {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val years = listOf(currentYear - 2, currentYear - 1, currentYear)
        val labels = years.map { it.toString() }
        val inEntries = ArrayList<BarEntry>()
        val exEntries = ArrayList<BarEntry>()

        years.forEachIndexed { idx, year ->
            val yearStr = year.toString()
            val inSum = transactions.filter { it.date.startsWith(yearStr) && it.type == "INCOME" }.sumOf { it.amount }.toFloat()
            val exSum = transactions.filter { it.date.startsWith(yearStr) && it.type == "EXPENSE" }.sumOf { it.amount }.toFloat()
            inEntries.add(BarEntry(idx.toFloat(), inSum))
            exEntries.add(BarEntry(idx.toFloat(), exSum))
        }
        return Triple(labels, inEntries, exEntries)
    }

    data class CategoryBreakdownItem(
        val category: String,
        val amount: Double,
        val percentage: Double,
        val color: Int
    )

    class CategoryBreakdownAdapter(
        private val items: List<CategoryBreakdownItem>,
        private val currencySymbol: String,
        private val numFormat: NumberFormat
    ) : RecyclerView.Adapter<CategoryBreakdownAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemAnalyticsCategoryBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemAnalyticsCategoryBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.binding.apply {
                viewCategoryColor.backgroundTintList = ColorStateList.valueOf(item.color)
                tvCategoryName.text = item.category
                tvCategoryAmount.text = "$currencySymbol${numFormat.format(item.amount)}"
                tvCategoryPercent.text = String.format(Locale.US, "%.1f%%", item.percentage)
            }
        }

        override fun getItemCount() = items.size
    }
}
