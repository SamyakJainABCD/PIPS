package com.example.pips

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import java.util.UUID

class CategoryActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val category = intent.getStringExtra("category")
        val notificationId = intent.getIntExtra("notification_id", -1)
        val transactionDetails = intent.getStringExtra("transaction_details")
        val accountName = intent.getStringExtra("account_name") ?: "Unknown"
        val notificationUuid = intent.getStringExtra("notification_uuid")

        if (category != null && notificationId != -1 && transactionDetails != null) {
            val prefs = PreferencesManager(context)
            val amount = SmsUtils.extractAmount(transactionDetails)
            
            val transaction = Transaction(
                id = UUID.randomUUID().toString(),
                accountName = accountName,
                amount = amount,
                category = category,
                details = transactionDetails,
                timestamp = System.currentTimeMillis()
            )
            
            prefs.saveTransaction(transaction)

            // Update notification history status
            if (notificationUuid != null) {
                prefs.updateNotificationStatus(notificationUuid, NotificationStatus.CATEGORIZED, category)
            }

            // Notify MainActivity to refresh its data
            val updateIntent = Intent("com.example.pips.UPDATE_TRANSACTIONS")
            updateIntent.setPackage(context.packageName)
            context.sendBroadcast(updateIntent)

            // Dismiss the notification
            NotificationManagerCompat.from(context).cancel(notificationId)
        }
    }
}
