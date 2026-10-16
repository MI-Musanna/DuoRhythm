package com.pocketdev.duorhythm.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.pocketdev.duorhythm.data.local.db.DatabaseHelper
import com.pocketdev.duorhythm.data.model.TransactionItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FinancialExpenseActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_LOG_EXPENSE = "com.pocketdev.duorhythm.ACTION_LOG_EXPENSE"
        const val ACTION_DISMISS_EXPENSE = "com.pocketdev.duorhythm.ACTION_DISMISS_EXPENSE"

        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_AMOUNT = "extra_amount"
        const val EXTRA_SOURCE = "extra_source"
        const val EXTRA_TYPE = "extra_type"
        const val EXTRA_MERCHANT = "extra_merchant"
        const val EXTRA_TARGET_BUCKET = "extra_target_bucket"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        // Dismiss notification
        if (notifId != -1) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(notifId)
        }

        if (action == ACTION_LOG_EXPENSE) {
            val amount = intent.getDoubleExtra(EXTRA_AMOUNT, 0.0)
            if (amount <= 0.0) return

            val source = intent.getStringExtra(EXTRA_SOURCE) ?: "MFS"
            val type = intent.getStringExtra(EXTRA_TYPE) ?: "Payment"
            val merchant = intent.getStringExtra(EXTRA_MERCHANT) ?: "$source $type"
            val dbHelper = DatabaseHelper.getInstance(context)
            val buckets = dbHelper.getAllWalletBuckets()
            val targetBucketName = intent.getStringExtra(EXTRA_TARGET_BUCKET) ?: (buckets.firstOrNull()?.name.orEmpty())

            // Find matching bucket or closest bucket
            val matchedBucket = if (targetBucketName.isNotBlank()) {
                buckets.firstOrNull {
                    it.name.equals(targetBucketName, ignoreCase = true)
                } ?: buckets.firstOrNull()
            } else {
                buckets.firstOrNull()
            }

            val bucketId = matchedBucket?.id ?: 0L
            val finalBucketName = matchedBucket?.name ?: targetBucketName

            val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val category = when (type.lowercase(Locale.ROOT)) {
                "cash out" -> "Cash Out"
                "send money", "transfer" -> "Transfer"
                else -> "Shopping"
            }

            val tx = TransactionItem(
                title = "$source: $merchant",
                amount = amount,
                type = "EXPENSE",
                category = category,
                date = todayDate,
                currency = "BDT",
                notes = "Auto-detected via $source $type",
                bucketId = bucketId
            )

            dbHelper.insertTransaction(tx)

            Handler(Looper.getMainLooper()).post {
                val formattedAmount = String.format(Locale.US, "%,.0f", amount)
                Toast.makeText(
                    context,
                    "Logged ৳$formattedAmount to '$finalBucketName'",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
