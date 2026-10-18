package com.duorhythm.app.automation

import java.util.Locale
import java.util.regex.Pattern

data class DetectedExpenseInfo(
    val amount: Double,
    val source: String,          // e.g. "bKash", "Nagad", "Rocket", "Bank"
    val type: String,            // "Payment", "Cash Out", "Send Money", "Transfer", "Expense"
    val merchant: String,        // Merchant or recipient name
    val targetBucketName: String,// e.g. "Online Money", "Cash / Offline"
    val rawText: String
)

object FinancialExpenseParser {

    // Common financial apps package names in Bangladesh & regional Android
    private val KNOWN_MFS_PACKAGES = setOf(
        "com.bKash.customerapp",
        "com.konasl.nagad",
        "com.dbbl.nexus.pay",
        "bd.com.upay.customer",
        "com.thecitybank.citytouch",
        "com.bracbank.astronomer",
        "com.ibbl.mpay"
    )

    // SMS package identifiers
    private val SMS_PACKAGES = setOf(
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "com.android.mms",
        "com.sonyericsson.conversations",
        "com.oneplus.mms"
    )

    // Amount extraction regexes
    private val AMOUNT_PATTERN_PREFIX = Pattern.compile(
        """(?:Tk\.?|৳|BDT|USD|\$)\s*([0-9,]+(?:\.[0-9]{1,2})?)""",
        Pattern.CASE_INSENSITIVE
    )

    private val AMOUNT_PATTERN_POSTFIX = Pattern.compile(
        """([0-9,]+(?:\.[0-9]{1,2})?)\s*(?:Tk\.?|৳|BDT)""",
        Pattern.CASE_INSENSITIVE
    )

    private val AMOUNT_PATTERN_DEBIT = Pattern.compile(
        """(?:debited|paid|charged|sent|withdrawn)\s*(?:for|by|of)?\s*(?:Tk\.?|৳|BDT|\$)?\s*([0-9,]+(?:\.[0-9]{1,2})?)""",
        Pattern.CASE_INSENSITIVE
    )

    fun isPotentialFinancialNotification(title: String, text: String, packageName: String): Boolean {
        if (KNOWN_MFS_PACKAGES.contains(packageName)) return true

        val combined = "$title $text".lowercase(Locale.ROOT)

        val containsBrand = combined.contains("bkash") ||
                combined.contains("nagad") ||
                combined.contains("rocket") ||
                combined.contains("nexus") ||
                combined.contains("upay") ||
                combined.contains("city touch") ||
                combined.contains("brac") ||
                combined.contains("islami bank") ||
                combined.contains("ebl") ||
                combined.contains("scb") ||
                combined.contains("bank") ||
                combined.contains("a/c") ||
                combined.contains("debit")

        val containsFinancialKeyword = combined.contains("payment") ||
                combined.contains("cash out") ||
                combined.contains("send money") ||
                combined.contains("debited") ||
                combined.contains("transfer") ||
                combined.contains("trxid") ||
                combined.contains("fee tk") ||
                combined.contains("successful") ||
                combined.contains("tk ") ||
                combined.contains("৳") ||
                combined.contains("bdt")

        return containsBrand && containsFinancialKeyword
    }

    fun parse(title: String, text: String, packageName: String): DetectedExpenseInfo? {
        val combined = "$title $text"
        val lower = combined.lowercase(Locale.ROOT)

        // Ignore incoming money / salary / cash in so we only detect debits/expenses
        if (lower.contains("cash in successful") ||
            lower.contains("received tk") ||
            lower.contains("credited by") ||
            lower.contains("deposited")
        ) {
            return null
        }

        // 1. Extract Amount
        val amount = extractAmount(combined) ?: return null
        if (amount <= 0.0 || amount > 10000000.0) return null

        // 2. Identify Source (MFS / Bank)
        val source = when {
            lower.contains("bkash") || packageName.contains("bkash", ignoreCase = true) -> "bKash"
            lower.contains("nagad") || packageName.contains("nagad", ignoreCase = true) -> "Nagad"
            lower.contains("rocket") || lower.contains("nexus") || packageName.contains("nexus", ignoreCase = true) -> "Rocket"
            lower.contains("upay") -> "Upay"
            lower.contains("city touch") -> "City Bank"
            lower.contains("brac") -> "BRAC Bank"
            lower.contains("ebl") -> "EBL"
            lower.contains("islami bank") -> "IBBL"
            lower.contains("scb") || lower.contains("standard chartered") -> "Standard Chartered"
            else -> if (title.isNotBlank()) title.take(20).trim() else "Bank/MFS"
        }

        // 3. Identify Transaction Type
        val type = when {
            lower.contains("cash out") -> "Cash Out"
            lower.contains("send money") -> "Send Money"
            lower.contains("transfer") -> "Transfer"
            lower.contains("payment") || lower.contains("paid") || lower.contains("purchase") || lower.contains("pos") -> "Payment"
            lower.contains("withdrawn") -> "Withdrawal"
            else -> "Payment"
        }

        // 4. Extract Merchant or Recipient
        val merchant = extractMerchant(combined, source, type)

        // 5. Target Wallet Bucket
        val targetBucketName = when (type) {
            "Cash Out" -> "Cash / Offline"
            else -> "Online Money"
        }

        return DetectedExpenseInfo(
            amount = amount,
            source = source,
            type = type,
            merchant = merchant,
            targetBucketName = targetBucketName,
            rawText = combined
        )
    }

    private fun extractAmount(text: String): Double? {
        var matcher = AMOUNT_PATTERN_PREFIX.matcher(text)
        if (matcher.find()) {
            val numStr = matcher.group(1)?.replace(",", "")
            numStr?.toDoubleOrNull()?.let { return it }
        }

        matcher = AMOUNT_PATTERN_POSTFIX.matcher(text)
        if (matcher.find()) {
            val numStr = matcher.group(1)?.replace(",", "")
            numStr?.toDoubleOrNull()?.let { return it }
        }

        matcher = AMOUNT_PATTERN_DEBIT.matcher(text)
        if (matcher.find()) {
            val numStr = matcher.group(1)?.replace(",", "")
            numStr?.toDoubleOrNull()?.let { return it }
        }

        return null
    }

    private fun extractMerchant(text: String, source: String, type: String): String {
        // Pattern: "to [Merchant] successful" or "to [Merchant] is successful" or "at [Merchant] on"
        val p1 = Pattern.compile(
            """(?:to|at|merchant)\s+([A-Za-z0-9\s\.\-_&]{2,30}?)(?:\s+successful|\s+is successful|\s+fee|\s+ref|\s+on\s|\.|\,|$)""",
            Pattern.CASE_INSENSITIVE
        )
        val m1 = p1.matcher(text)
        if (m1.find()) {
            val name = m1.group(1)?.trim()
            if (!name.isNullOrBlank() && !name.equals("merchant", ignoreCase = true) && !name.equals("account", ignoreCase = true)) {
                return name
            }
        }

        // Pattern for POS or at store: "at POS [Merchant]"
        val p2 = Pattern.compile(
            """(?:POS|at)\s+([A-Za-z0-9\s\.\-_&]{2,30}?)(?:\s+on|\.|\,|$)""",
            Pattern.CASE_INSENSITIVE
        )
        val m2 = p2.matcher(text)
        if (m2.find()) {
            val name = m2.group(1)?.trim()
            if (!name.isNullOrBlank()) {
                return name
            }
        }

        return "$source $type"
    }
}
