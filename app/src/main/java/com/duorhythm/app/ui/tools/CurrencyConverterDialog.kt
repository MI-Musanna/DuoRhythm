package com.duorhythm.app.ui.tools

import android.content.Context
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.duorhythm.app.R
import com.duorhythm.app.data.model.CurrencyRate
import com.duorhythm.app.data.repository.CurrencyService
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.databinding.DialogCurrencyConverterBinding
import com.duorhythm.app.databinding.ItemCurrencyConverterCardBinding
import java.util.Locale

class CurrencyConverterDialog(context: Context) : BottomSheetDialog(context) {

    private val dbHelper = DatabaseHelper.getInstance(context)
    private var ratesList: MutableList<CurrencyRate> = mutableListOf()
    private var displayedRates: MutableList<CurrencyRate> = mutableListOf()
    private var activeCode: String = "USD"
    private var inputExpression: String = "100"

    private lateinit var binding: DialogCurrencyConverterBinding
    private lateinit var adapter: CurrencyAdapter

    // Comprehensive catalog of 40 major global currencies
    private val globalCurrencyCatalog = listOf(
        Triple("USD", "United States Dollar", "$"),
        Triple("BDT", "Bangladeshi Taka", "৳"),
        Triple("EUR", "Euro (European Union)", "€"),
        Triple("GBP", "British Pound Sterling", "£"),
        Triple("INR", "Indian Rupee", "₹"),
        Triple("SAR", "Saudi Riyal", "SAR"),
        Triple("CAD", "Canadian Dollar", "C$"),
        Triple("AUD", "Australian Dollar", "A$"),
        Triple("JPY", "Japanese Yen", "¥"),
        Triple("AED", "UAE Dirham", "AED"),
        Triple("SGD", "Singapore Dollar", "S$"),
        Triple("MYR", "Malaysian Ringgit", "RM"),
        Triple("PKR", "Pakistani Rupee", "₨"),
        Triple("CHF", "Swiss Franc", "CHF"),
        Triple("CNY", "Chinese Yuan", "¥"),
        Triple("KRW", "South Korean Won", "₩"),
        Triple("NZD", "New Zealand Dollar", "NZ$"),
        Triple("BRL", "Brazilian Real", "R$"),
        Triple("TRY", "Turkish Lira", "₺"),
        Triple("ZAR", "South African Rand", "R"),
        Triple("RUB", "Russian Ruble", "₽"),
        Triple("THB", "Thai Baht", "฿"),
        Triple("IDR", "Indonesian Rupiah", "Rp"),
        Triple("KWD", "Kuwaiti Dinar", "KD"),
        Triple("QAR", "Qatari Riyal", "QR"),
        Triple("OMR", "Omani Rial", "OMR"),
        Triple("BHD", "Bahraini Dinar", "BD"),
        Triple("EGP", "Egyptian Pound", "E£"),
        Triple("HKD", "Hong Kong Dollar", "HK$"),
        Triple("SEK", "Swedish Krona", "kr"),
        Triple("NOK", "Norwegian Krone", "kr"),
        Triple("DKK", "Danish Krone", "kr"),
        Triple("PLN", "Polish Zloty", "zł"),
        Triple("PHP", "Philippine Peso", "₱"),
        Triple("VND", "Vietnamese Dong", "₫"),
        Triple("MXN", "Mexican Peso", "Mex$"),
        Triple("NGN", "Nigerian Naira", "₦"),
        Triple("KES", "Kenyan Shilling", "KSh"),
        Triple("LKR", "Sri Lankan Rupee", "Rs"),
        Triple("NPR", "Nepalese Rupee", "Rs")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogCurrencyConverterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window?.setBackgroundDrawableResource(android.R.color.transparent)

        setupBottomSheetBehavior()
        loadCachedRates()
        setupRecyclerView()
        setupSearchFilter()
        setupNumpad()
        setupSyncButton()

        binding.btnCloseConverter.setOnClickListener {
            dismiss()
        }

        recalculate()
    }

    private fun setupBottomSheetBehavior() {
        setOnShowListener {
            val bottomSheet = findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let { sheet ->
                sheet.setBackgroundResource(android.R.color.transparent)
                val behavior = BottomSheetBehavior.from(sheet)
                val displayMetrics = context.resources.displayMetrics
                val targetHeight = (displayMetrics.heightPixels * 0.90).toInt()
                sheet.layoutParams.height = targetHeight
                behavior.peekHeight = targetHeight
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }
        }
    }

    private fun loadCachedRates() {
        val existing = dbHelper.getAllCurrencyRates()
        if (existing.isEmpty() || existing.size < globalCurrencyCatalog.size) {
            dbHelper.ensureDefaultCurrencies(dbHelper.writableDatabase)
            ratesList = dbHelper.getAllCurrencyRates().toMutableList()
        } else {
            ratesList = existing.toMutableList()
        }

        val prefs = context.getSharedPreferences("lifesync_prefs", Context.MODE_PRIVATE)
        val saved = prefs.getString("selected_currency", "USD") ?: "USD"
        activeCode = if (ratesList.any { it.code == saved }) saved else ratesList.firstOrNull()?.code ?: "USD"
        val search = if (::binding.isInitialized) binding.etCurrencySearch.text?.toString().orEmpty() else ""
        filterCurrencies(search)
    }

    private fun setupSearchFilter() {
        binding.etCurrencySearch.addTextChangedListener { text ->
            filterCurrencies(text?.toString().orEmpty())
        }
    }

    private fun filterCurrencies(query: String) {
        val q = query.trim().lowercase()
        displayedRates = if (q.isEmpty()) {
            ratesList.toMutableList()
        } else {
            ratesList.filter { rate ->
                val fullName = getCurrencyFullName(rate.code).lowercase()
                val countryBadge = getCountryBadgeText(rate.code).lowercase()
                val catalogEntry = globalCurrencyCatalog.find { it.first.equals(rate.code, ignoreCase = true) }
                val catalogName = catalogEntry?.second?.lowercase().orEmpty()
                rate.code.lowercase().contains(q) ||
                        fullName.contains(q) ||
                        countryBadge.contains(q) ||
                        catalogName.contains(q) ||
                        rate.symbol.lowercase().contains(q)
            }.toMutableList()
        }
        if (::adapter.isInitialized) {
            adapter.notifyDataSetChanged()
        }
    }

    private fun setupRecyclerView() {
        adapter = CurrencyAdapter()
        binding.rvCurrencies.layoutManager = LinearLayoutManager(context)
        binding.rvCurrencies.adapter = adapter
    }

    private fun setupSyncButton() {
        binding.btnSyncRates.setOnClickListener {
            binding.btnSyncRates.isEnabled = false
            binding.btnSyncRates.text = "Syncing..."
            binding.tvSyncStatus.text = "Connecting to live FX exchange server..."

            CurrencyService.syncLatestRates(context, object : CurrencyService.SyncCallback {
                override fun onSyncSuccess(message: String) {
                    binding.root.post {
                        binding.btnSyncRates.isEnabled = true
                        binding.btnSyncRates.text = "Sync"
                        loadCachedRates()
                        recalculate()
                        binding.tvSyncStatus.text = "Rates updated live (${getLatestSyncTimestamp()})"
                        Toast.makeText(context, "Live exchange rates synced successfully!", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onSyncError(errorMessage: String) {
                    binding.root.post {
                        binding.btnSyncRates.isEnabled = true
                        binding.btnSyncRates.text = "Sync"
                        binding.tvSyncStatus.text = "Offline cached rates active"
                        Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
                    }
                }
            })
        }
    }

    private fun getLatestSyncTimestamp(): String {
        return ratesList.firstOrNull()?.lastUpdated ?: "Recent"
    }

    private fun setupNumpad() {
        val digitButtons = mapOf(
            binding.btnKey0 to "0",
            binding.btnKey00 to "00",
            binding.btnKey1 to "1",
            binding.btnKey2 to "2",
            binding.btnKey3 to "3",
            binding.btnKey4 to "4",
            binding.btnKey5 to "5",
            binding.btnKey6 to "6",
            binding.btnKey7 to "7",
            binding.btnKey8 to "8",
            binding.btnKey9 to "9",
            binding.btnKeyDot to "."
        )

        for ((btn, char) in digitButtons) {
            btn.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                appendInput(char)
            }
        }

        binding.btnKeyDel.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            if (inputExpression.length > 1) {
                inputExpression = inputExpression.substring(0, inputExpression.length - 1)
            } else {
                inputExpression = "0"
            }
            recalculate()
        }

        binding.btnKeyClear.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            inputExpression = "0"
            recalculate()
        }

        binding.btnKeyPlus.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            if (!inputExpression.endsWith("+") && !inputExpression.endsWith(".")) {
                evaluateExpression()
                inputExpression += "+"
                recalculate()
            }
        }

        binding.btnKeyEqual.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            evaluateExpression()
            recalculate()
        }
    }

    private fun appendInput(str: String) {
        if (inputExpression == "0" && str != ".") {
            inputExpression = str
        } else if (str == ".") {
            val parts = inputExpression.split("+")
            val currentSegment = parts.lastOrNull().orEmpty()
            if (!currentSegment.contains(".")) {
                inputExpression += "."
            }
        } else {
            inputExpression += str
        }
        recalculate()
    }

    private fun evaluateExpression() {
        try {
            if (inputExpression.contains("+")) {
                val sum = inputExpression.split("+")
                    .mapNotNull { it.trim().toDoubleOrNull() }
                    .sum()
                inputExpression = if (sum % 1.0 == 0.0) {
                    sum.toLong().toString()
                } else {
                    String.format(Locale.US, "%.2f", sum)
                }
            }
        } catch (_: Exception) {}
    }

    private fun parseCurrentActiveAmount(): Double {
        return try {
            if (inputExpression.contains("+")) {
                inputExpression.split("+")
                    .mapNotNull { it.trim().toDoubleOrNull() }
                    .sum()
            } else {
                inputExpression.toDoubleOrNull() ?: 0.0
            }
        } catch (_: Exception) {
            0.0
        }
    }

    private fun recalculate() {
        val activeRate = ratesList.find { it.code == activeCode } ?: ratesList.firstOrNull()
        val activeSymbol = activeRate?.symbol ?: "$"
        binding.tvActiveInputPreview.text = "$inputExpression $activeCode ($activeSymbol)"
        adapter.notifyDataSetChanged()
    }

    private fun getCountryBadgeText(code: String): String = when (code) {
        "USD" -> "US"
        "BDT" -> "BD"
        "EUR" -> "EU"
        "GBP" -> "GB"
        "INR" -> "IN"
        "JPY" -> "JP"
        "CAD" -> "CA"
        "AUD" -> "AU"
        "AED" -> "AE"
        "SAR" -> "SA"
        "SGD" -> "SG"
        "MYR" -> "MY"
        "PKR" -> "PK"
        "CHF" -> "CH"
        "CNY" -> "CN"
        "KRW" -> "KR"
        "NZD" -> "NZ"
        "BRL" -> "BR"
        "TRY" -> "TR"
        "ZAR" -> "ZA"
        "RUB" -> "RU"
        "THB" -> "TH"
        "IDR" -> "ID"
        "KWD" -> "KW"
        "QAR" -> "QA"
        "OMR" -> "OM"
        "BHD" -> "BH"
        "EGP" -> "EG"
        "HKD" -> "HK"
        "SEK" -> "SE"
        "NOK" -> "NO"
        "DKK" -> "DK"
        "PLN" -> "PL"
        "PHP" -> "PH"
        "VND" -> "VN"
        "MXN" -> "MX"
        "NGN" -> "NG"
        "KES" -> "KE"
        "LKR" -> "LK"
        "NPR" -> "NP"
        else -> code.take(2)
    }

    private fun getCurrencyFullName(code: String): String = when (code) {
        "USD" -> "US Dollar"
        "BDT" -> "Bangladeshi Taka"
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
        "CHF" -> "Swiss Franc"
        "CNY" -> "Chinese Yuan"
        "KRW" -> "South Korean Won"
        "NZD" -> "New Zealand Dollar"
        "BRL" -> "Brazilian Real"
        "TRY" -> "Turkish Lira"
        "ZAR" -> "South African Rand"
        "RUB" -> "Russian Ruble"
        "THB" -> "Thai Baht"
        "IDR" -> "Indonesian Rupiah"
        "KWD" -> "Kuwaiti Dinar"
        "QAR" -> "Qatari Riyal"
        "OMR" -> "Omani Rial"
        "BHD" -> "Bahraini Dinar"
        "EGP" -> "Egyptian Pound"
        "HKD" -> "Hong Kong Dollar"
        "SEK" -> "Swedish Krona"
        "NOK" -> "Norwegian Krone"
        "DKK" -> "Danish Krone"
        "PLN" -> "Polish Zloty"
        "PHP" -> "Philippine Peso"
        "VND" -> "Vietnamese Dong"
        "MXN" -> "Mexican Peso"
        "NGN" -> "Nigerian Naira"
        "KES" -> "Kenyan Shilling"
        "LKR" -> "Sri Lankan Rupee"
        "NPR" -> "Nepalese Rupee"
        else -> globalCurrencyCatalog.find { it.first == code }?.second ?: "$code Currency"
    }

    inner class CurrencyAdapter : RecyclerView.Adapter<CurrencyAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val itemBinding = ItemCurrencyConverterCardBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(itemBinding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(displayedRates[position])
        }

        override fun getItemCount(): Int = displayedRates.size

        inner class ViewHolder(private val itemBinding: ItemCurrencyConverterCardBinding) :
            RecyclerView.ViewHolder(itemBinding.root) {

            fun bind(item: CurrencyRate) {
                val isActive = item.code == activeCode
                val activeRateObj = ratesList.find { it.code == activeCode } ?: item
                val activeAmount = parseCurrentActiveAmount()

                // Stylized badge
                itemBinding.tvCurrencyCountryBadge.text = getCountryBadgeText(item.code)
                itemBinding.tvCurrencyCode.text = item.code
                itemBinding.tvCurrencySymbol.text = "(${item.symbol})"

                val fullName = getCurrencyFullName(item.code)
                val rateVsBase = if (item.rate != 0.0) {
                    String.format(Locale.US, "1 USD = %.2f %s", item.rate, item.code)
                } else {
                    "Rate: 1.00"
                }
                itemBinding.tvCurrencyRateDetail.text = "$fullName • $rateVsBase"

                // Reactive calculation: Base USD = activeAmount / activeRate; Target = Base USD * targetRate
                val convertedAmount = if (isActive) {
                    activeAmount
                } else {
                    val baseUsd = if (activeRateObj.rate > 0.0) activeAmount / activeRateObj.rate else 0.0
                    baseUsd * item.rate
                }

                val formattedAmount = if (isActive) {
                    "$inputExpression ${item.symbol}"
                } else if (convertedAmount % 1.0 == 0.0 && convertedAmount < 1_000_000) {
                    String.format(Locale.US, "%,.0f %s", convertedAmount, item.symbol)
                } else {
                    String.format(Locale.US, "%,.2f %s", convertedAmount, item.symbol)
                }

                itemBinding.tvCurrencyAmount.text = formattedAmount

                // Dynamic theme card styling
                val primaryColor = ContextCompat.getColor(context, R.color.primary)
                val strokeColor = ContextCompat.getColor(context, R.color.card_stroke)
                val surfaceColor = ContextCompat.getColor(context, R.color.surface_card)
                val selectedBg = ContextCompat.getColor(context, R.color.surface_card_selected)
                val textPrimary = ContextCompat.getColor(context, R.color.text_primary)
                val textSecondary = ContextCompat.getColor(context, R.color.text_secondary)

                if (isActive) {
                    itemBinding.cardCurrencyItem.strokeColor = primaryColor
                    itemBinding.cardCurrencyItem.strokeWidth = (2 * context.resources.displayMetrics.density).toInt()
                    itemBinding.cardCurrencyItem.setCardBackgroundColor(selectedBg)
                    itemBinding.tvActiveBadge.visibility = View.VISIBLE
                    itemBinding.tvCurrencyAmount.setTextColor(primaryColor)
                    itemBinding.tvCurrencyCode.setTextColor(primaryColor)
                } else {
                    itemBinding.cardCurrencyItem.strokeColor = strokeColor
                    itemBinding.cardCurrencyItem.strokeWidth = (1 * context.resources.displayMetrics.density).toInt()
                    itemBinding.cardCurrencyItem.setCardBackgroundColor(surfaceColor)
                    itemBinding.tvActiveBadge.visibility = View.GONE
                    itemBinding.tvCurrencyAmount.setTextColor(textPrimary)
                    itemBinding.tvCurrencyCode.setTextColor(textPrimary)
                }
                itemBinding.tvCurrencySymbol.setTextColor(textSecondary)
                itemBinding.tvCurrencyRateDetail.setTextColor(textSecondary)

                // Tapping selects as active editing base
                itemBinding.root.setOnClickListener {
                    if (activeCode != item.code) {
                        activeCode = item.code
                        val formattedVal = if (convertedAmount % 1.0 == 0.0) {
                            convertedAmount.toLong().toString()
                        } else {
                            String.format(Locale.US, "%.2f", convertedAmount)
                        }
                        inputExpression = formattedVal
                        recalculate()
                    }
                }
            }
        }
    }
}
