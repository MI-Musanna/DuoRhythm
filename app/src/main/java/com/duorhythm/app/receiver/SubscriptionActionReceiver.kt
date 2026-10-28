package com.duorhythm.app.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.model.TransactionItem
import com.duorhythm.app.ui.wallet.WalletDataSyncManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SubscriptionActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_MARK_SUB_PAID = "com.duorhythm.app.ACTION_MARK_SUB_PAID"
        const val EXTRA_SUB_ID = "extra_sub_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_MARK_SUB_PAID) return

        val subId = intent.getLongExtra(EXTRA_SUB_ID, -1L)
        val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        if (notifId > 0) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(notifId)
        }

        if (subId <= 0L) return

        val db = DatabaseHelper.getInstance(context)
        val sub = db.subscriptionDao.getSubscriptionById(subId) ?: return

        val currentMonthYear = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
        db.subscriptionDao.updatePaidMonth(subId, currentMonthYear)

        if (sub.linkedAccountId != null && sub.linkedAccountId > 0) {
            val tx = TransactionItem(
                profileId = sub.profileId,
                title = "Subscription: ${sub.name}",
                amount = sub.amount,
                type = "EXPENSE",
                category = "Subscription",
                date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
                currency = "BDT",
                bucketId = sub.linkedAccountId,
                notes = "Auto-logged from reminder notification for ${sub.name}"
            )
            db.insertTransaction(tx)
            WalletDataSyncManager.notifyChanged()
        }

        Toast.makeText(context, "Marked '${sub.name}' as paid", Toast.LENGTH_SHORT).show()
    }
}
