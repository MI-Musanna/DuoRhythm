package com.pocketdev.duorhythm.ui.wallet

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
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
import com.pocketdev.duorhythm.R
import com.pocketdev.duorhythm.data.local.db.DatabaseHelper
import com.pocketdev.duorhythm.data.model.TransactionItem
import com.pocketdev.duorhythm.databinding.DialogFinancialAnalyticsBinding
import com.pocketdev.duorhythm.databinding.ItemAnalyticsCategoryBinding
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class FinancialAnalyticsDialog(
    private val context: Context,
    private val currentSymbol: String = "৳"
) {

    private val dbHelper = DatabaseHelper.getInstance(context)
    private var currentMode = Mode.BAR_TREND
    private var currentTimeframe = Timeframe.MONTHLY
    private var dialog: AlertDialog? = null

    private enum class Mode { BAR_TREND, CATEGORY_PIE }
    private enum class Timeframe { DAILY, WEEKLY, MONTHLY, YEARLY }

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

    fun show() {
        val binding = DialogFinancialAnalyticsBinding.inflate(LayoutInflater.from(context))

        dialog = AlertDialog.Builder(context)
            .setView(binding.root)
            .create()

        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog?.show()

        val width = (context.resources.displayMetrics.widthPixels * 0.96).toInt()
        val height = (context.resources.displayMetrics.heightPixels * 0.88).toInt()
        dialog?.window?.setLayout(width, height)

        setupListeners(binding)
        setupCharts(binding)
        renderAnalytics(binding)
    }

    private fun setupListeners(binding: DialogFinancialAnalyticsBinding) {
        binding.btnCloseAnalytics.setOnClickListener {
            dialog?.dismiss()
        }

        binding.toggleChartMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                currentMode = if (checkedId == R.id.btnModeBarTrend) Mode.BAR_TREND else Mode.CATEGORY_PIE
                renderAnalytics(binding)
            }
        }

        binding.chipGroupTimeframe.setOnCheckedChangeListener { _, checkedId ->
            currentTimeframe = when (checkedId) {
                R.id.chipDaily -> Timeframe.DAILY
                R.id.chipWeekly -> Timeframe.WEEKLY
                R.id.chipMonthly -> Timeframe.MONTHLY
                R.id.chipYearly -> Timeframe.YEARLY
                else -> Timeframe.MONTHLY
            }
            renderAnalytics(binding)
        }

        binding.rvCategoryBreakdown.layoutManager = LinearLayoutManager(context)
    }

    private fun setupCharts(binding: DialogFinancialAnalyticsBinding) {
        // Obsidian transparent styling for BarChart
        binding.barChart.apply {
            setBackgroundColor(Color.TRANSPARENT)
            setDrawGridBackground(false)
            setDrawBarShadow(false)
            setDrawValueAboveBar(true)
            description.isEnabled = false
            setPinchZoom(false)
            setScaleEnabled(false)

            val textPrimary = androidx.core.content.ContextCompat.getColor(context, R.color.text_primary)
            val textSecondary = androidx.core.content.ContextCompat.getColor(context, R.color.text_secondary)
            val gridColorVal = androidx.core.content.ContextCompat.getColor(context, R.color.card_stroke)

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                textColor = textPrimary
                gridColor = gridColorVal
                setDrawAxisLine(true)
                axisLineColor = gridColorVal
                granularity = 1f
                setCenterAxisLabels(true)
            }

            axisLeft.apply {
                textColor = textSecondary
                gridColor = gridColorVal
                axisLineColor = gridColorVal
                axisMinimum = 0f
            }

            axisRight.isEnabled = false

            legend.apply {
                textColor = textPrimary
                verticalAlignment = Legend.LegendVerticalAlignment.TOP
                horizontalAlignment = Legend.LegendHorizontalAlignment.RIGHT
                orientation = Legend.LegendOrientation.HORIZONTAL
                setDrawInside(false)
            }
        }

        // Dynamic styling for PieChart
        binding.pieChart.apply {
            val textPrimary = androidx.core.content.ContextCompat.getColor(context, R.color.text_primary)
            val gridColorVal = androidx.core.content.ContextCompat.getColor(context, R.color.card_stroke)
            setBackgroundColor(Color.TRANSPARENT)
            description.isEnabled = false
            setUsePercentValues(true)
            isDrawHoleEnabled = true
            setHoleColor(Color.TRANSPARENT)
            setTransparentCircleColor(gridColorVal)
            setTransparentCircleAlpha(110)
            holeRadius = 55f
            transparentCircleRadius = 60f
            setDrawCenterText(true)
            setCenterTextColor(textPrimary)
            setCenterTextSize(12f)
            setDrawEntryLabels(false)
            legend.isEnabled = false
            animateY(600)
        }
    }

    private fun renderAnalytics(binding: DialogFinancialAnalyticsBinding) {
        val allTx = dbHelper.getAllTransactions()
        val filteredTx = filterTransactionsByTimeframe(allTx, currentTimeframe)

        val totalIncome = filteredTx.filter { it.type == "INCOME" }.sumOf { it.amount }
        val totalExpense = filteredTx.filter { it.type == "EXPENSE" }.sumOf { it.amount }
        val netCashflow = totalIncome - totalExpense

        val numFormat = NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }

        binding.tvSummaryIncome.text = String.format(Locale.US, "+%s%s", currentSymbol, numFormat.format(totalIncome))
        binding.tvSummaryExpense.text = String.format(Locale.US, "-%s%s", currentSymbol, numFormat.format(totalExpense))
        binding.tvSummaryNet.text = if (netCashflow < 0) {
            String.format(Locale.US, "-%s%s", currentSymbol, numFormat.format(kotlin.math.abs(netCashflow)))
        } else {
            String.format(Locale.US, "+%s%s", currentSymbol, numFormat.format(netCashflow))
        }

        if (filteredTx.isEmpty()) {
            binding.barChart.visibility = View.GONE
            binding.pieChart.visibility = View.GONE
            binding.layoutEmptyChart.visibility = View.VISIBLE
            binding.layoutBreakdownSection.visibility = View.GONE
            return
        }

        binding.layoutEmptyChart.visibility = View.GONE
        binding.layoutBreakdownSection.visibility = View.VISIBLE

        if (currentMode == Mode.BAR_TREND) {
            binding.barChart.visibility = View.VISIBLE
            binding.pieChart.visibility = View.GONE
            binding.tvBreakdownTitle.text = "Cashflow Overview (${currentTimeframe.name.lowercase().replaceFirstChar { it.uppercase() }})"
            renderBarChart(binding, filteredTx)
        } else {
            binding.barChart.visibility = View.GONE
            binding.pieChart.visibility = View.VISIBLE
            binding.tvBreakdownTitle.text = "Spending by Category (${currentTimeframe.name.lowercase().replaceFirstChar { it.uppercase() }})"
            renderPieChart(binding, filteredTx)
        }
    }

    private fun filterTransactionsByTimeframe(transactions: List<TransactionItem>, timeframe: Timeframe): List<TransactionItem> {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()

        return when (timeframe) {
            Timeframe.DAILY -> {
                // Last 7 days
                cal.add(Calendar.DAY_OF_YEAR, -6)
                val minDateStr = sdf.format(cal.time)
                transactions.filter { it.date >= minDateStr }
            }
            Timeframe.WEEKLY -> {
                // Last 4 weeks (28 days)
                cal.add(Calendar.DAY_OF_YEAR, -27)
                val minDateStr = sdf.format(cal.time)
                transactions.filter { it.date >= minDateStr }
            }
            Timeframe.MONTHLY -> {
                // Current calendar year months
                val yearPrefix = SimpleDateFormat("yyyy-", Locale.US).format(Date())
                transactions.filter { it.date.startsWith(yearPrefix) }
            }
            Timeframe.YEARLY -> {
                // All transactions grouped across years
                transactions
            }
        }
    }

    private fun renderBarChart(binding: DialogFinancialAnalyticsBinding, transactions: List<TransactionItem>) {
        val buckets = prepareBarChartBuckets(transactions, currentTimeframe)
        if (buckets.isEmpty()) {
            binding.barChart.visibility = View.GONE
            binding.layoutEmptyChart.visibility = View.VISIBLE
            return
        }

        val incomeEntries = mutableListOf<BarEntry>()
        val expenseEntries = mutableListOf<BarEntry>()
        val labels = mutableListOf<String>()

        buckets.forEachIndexed { index, b ->
            labels.add(b.label)
            incomeEntries.add(BarEntry(index.toFloat(), b.income.toFloat()))
            expenseEntries.add(BarEntry(index.toFloat(), b.expense.toFloat()))
        }

        val incomeSet = BarDataSet(incomeEntries, "Income").apply {
            color = Color.parseColor("#10B981") // Emerald Green
            valueTextColor = Color.WHITE
            valueTextSize = 9f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String =
                    if (value > 0) String.format(Locale.US, "%.0f", value) else ""
            }
        }

        val expenseSet = BarDataSet(expenseEntries, "Expense").apply {
            color = Color.parseColor("#EF4444") // Rose Red
            valueTextColor = Color.WHITE
            valueTextSize = 9f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String =
                    if (value > 0) String.format(Locale.US, "%.0f", value) else ""
            }
        }

        val groupSpace = 0.30f
        val barSpace = 0.05f
        val barWidth = 0.30f
        // (0.30 + 0.05) * 2 + 0.30 = 1.00

        val barData = BarData(incomeSet, expenseSet)
        barData.barWidth = barWidth

        binding.barChart.apply {
            data = barData
            xAxis.valueFormatter = IndexAxisValueFormatter(labels)
            xAxis.axisMinimum = 0f
            xAxis.axisMaximum = labels.size.toFloat()
            groupBars(0f, groupSpace, barSpace)
            animateY(600)
            invalidate()
        }

        // Render breakdown list as period summaries
        val breakdownItems = buckets.reversed().mapIndexed { i, b ->
            val color = if (b.income >= b.expense) Color.parseColor("#10B981") else Color.parseColor("#EF4444")
            val net = b.income - b.expense
            val netPercent = if (b.income + b.expense > 0) ((kotlin.math.abs(net) / (b.income + b.expense)) * 100).toFloat() else 0f
            CategoryBreakdownItem(
                name = "${b.label} • In: +$currentSymbol${String.format(Locale.US, "%.0f", b.income)} | Out: -$currentSymbol${String.format(Locale.US, "%.0f", b.expense)}",
                amount = net,
                percent = netPercent,
                color = color
            )
        }
        binding.rvCategoryBreakdown.adapter = CategoryBreakdownAdapter(breakdownItems, currentSymbol)
    }

    private fun renderPieChart(binding: DialogFinancialAnalyticsBinding, transactions: List<TransactionItem>) {
        val expenses = transactions.filter { it.type == "EXPENSE" }
        val targetList = if (expenses.isNotEmpty()) expenses else transactions
        val isShowingIncome = expenses.isEmpty()

        val grouped = targetList.groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
            .filter { it.value > 0 }

        if (grouped.isEmpty()) {
            binding.pieChart.visibility = View.GONE
            binding.layoutEmptyChart.visibility = View.VISIBLE
            return
        }

        val totalSum = grouped.values.sum()
        val entries = mutableListOf<PieEntry>()
        val colors = mutableListOf<Int>()
        val breakdownItems = mutableListOf<CategoryBreakdownItem>()

        grouped.entries.sortedByDescending { it.value }.forEachIndexed { index, entry ->
            val color = chartColors[index % chartColors.size]
            val pct = ((entry.value / totalSum) * 100).toFloat()
            entries.add(PieEntry(entry.value.toFloat(), entry.key))
            colors.add(color)
            breakdownItems.add(
                CategoryBreakdownItem(
                    name = entry.key,
                    amount = entry.value,
                    percent = pct,
                    color = color
                )
            )
        }

        val dataSet = PieDataSet(entries, "").apply {
            this.colors = colors
            sliceSpace = 3f
            selectionShift = 5f
            valueTextColor = Color.WHITE
            valueTextSize = 10f
            valueFormatter = PercentFormatter(binding.pieChart)
        }

        val pieData = PieData(dataSet)
        binding.pieChart.apply {
            data = pieData
            centerText = if (isShowingIncome) {
                "Total Income\n$currentSymbol${String.format(Locale.US, "%.0f", totalSum)}"
            } else {
                "Total Spent\n$currentSymbol${String.format(Locale.US, "%.0f", totalSum)}"
            }
            animateY(600)
            invalidate()
        }

        binding.rvCategoryBreakdown.adapter = CategoryBreakdownAdapter(breakdownItems, currentSymbol)
    }

    private data class BarBucket(val label: String, var income: Double = 0.0, var expense: Double = 0.0)

    private fun prepareBarChartBuckets(transactions: List<TransactionItem>, timeframe: Timeframe): List<BarBucket> {
        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return when (timeframe) {
            Timeframe.DAILY -> {
                // Last 7 days
                val buckets = mutableListOf<BarBucket>()
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, -6)
                val dayFormat = SimpleDateFormat("EEE", Locale.US)
                for (i in 0..6) {
                    val dateKey = sdfDate.format(cal.time)
                    val label = dayFormat.format(cal.time)
                    val inSum = transactions.filter { it.date == dateKey && it.type == "INCOME" }.sumOf { it.amount }
                    val outSum = transactions.filter { it.date == dateKey && it.type == "EXPENSE" }.sumOf { it.amount }
                    buckets.add(BarBucket(label, inSum, outSum))
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                }
                buckets
            }
            Timeframe.WEEKLY -> {
                // Last 4 weeks
                val buckets = mutableListOf<BarBucket>()
                for (w in 3 downTo 0) {
                    val label = "W${4 - w}"
                    val calEnd = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -w * 7) }
                    val calStart = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -(w + 1) * 7 + 1) }
                    val startStr = sdfDate.format(calStart.time)
                    val endStr = sdfDate.format(calEnd.time)
                    val inSum = transactions.filter { it.date in startStr..endStr && it.type == "INCOME" }.sumOf { it.amount }
                    val outSum = transactions.filter { it.date in startStr..endStr && it.type == "EXPENSE" }.sumOf { it.amount }
                    buckets.add(BarBucket(label, inSum, outSum))
                }
                buckets
            }
            Timeframe.MONTHLY -> {
                // Last 6 calendar months
                val buckets = mutableListOf<BarBucket>()
                val cal = Calendar.getInstance()
                cal.add(Calendar.MONTH, -5)
                val monthLabelFormat = SimpleDateFormat("MMM", Locale.US)
                val monthKeyFormat = SimpleDateFormat("yyyy-MM", Locale.US)
                for (m in 0..5) {
                    val monthKey = monthKeyFormat.format(cal.time)
                    val label = monthLabelFormat.format(cal.time)
                    val inSum = transactions.filter { it.date.startsWith(monthKey) && it.type == "INCOME" }.sumOf { it.amount }
                    val outSum = transactions.filter { it.date.startsWith(monthKey) && it.type == "EXPENSE" }.sumOf { it.amount }
                    buckets.add(BarBucket(label, inSum, outSum))
                    cal.add(Calendar.MONTH, 1)
                }
                buckets
            }
            Timeframe.YEARLY -> {
                // Years present in data (up to last 3 years)
                val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                val years = listOf(currentYear - 2, currentYear - 1, currentYear)
                years.map { year ->
                    val yearStr = year.toString()
                    val inSum = transactions.filter { it.date.startsWith(yearStr) && it.type == "INCOME" }.sumOf { it.amount }
                    val outSum = transactions.filter { it.date.startsWith(yearStr) && it.type == "EXPENSE" }.sumOf { it.amount }
                    BarBucket(yearStr, inSum, outSum)
                }
            }
        }
    }

    private data class CategoryBreakdownItem(
        val name: String,
        val amount: Double,
        val percent: Float,
        val color: Int
    )

    private class CategoryBreakdownAdapter(
        private val items: List<CategoryBreakdownItem>,
        private val currencySymbol: String
    ) : RecyclerView.Adapter<CategoryBreakdownAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemAnalyticsCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        inner class ViewHolder(private val binding: ItemAnalyticsCategoryBinding) :
            RecyclerView.ViewHolder(binding.root) {

            fun bind(item: CategoryBreakdownItem) {
                binding.viewCategoryColor.backgroundTintList = ColorStateList.valueOf(item.color)
                binding.tvCategoryName.text = item.name
                binding.tvCategoryPercent.text = String.format(Locale.US, "%.1f%%", item.percent)
                val numFormat = NumberFormat.getNumberInstance(Locale.US).apply {
                    minimumFractionDigits = 2
                    maximumFractionDigits = 2
                }
                binding.tvCategoryAmount.text = if (item.amount < 0) {
                    String.format(Locale.US, "-%s%s", currencySymbol, numFormat.format(kotlin.math.abs(item.amount)))
                } else {
                    String.format(Locale.US, "%s%s", currencySymbol, numFormat.format(item.amount))
                }
            }
        }
    }
}
