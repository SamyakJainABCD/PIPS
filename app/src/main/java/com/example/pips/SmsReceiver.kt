package com.example.pips

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (sms in messages) {
                val messageBody = sms.messageBody
                if (SmsUtils.isUpiMessage(messageBody)) {
                    val accountName = SmsUtils.extractAccountName(messageBody)
                    showCategoryNotification(context, accountName, messageBody)
                }
            }
        }
    }

    private fun showCategoryNotification(context: Context, accountName: String, messageBody: String) {
        val channelId = "upi_categorization_channel"
        val notificationId = System.currentTimeMillis().toInt()

        createNotificationChannel(context, channelId)

        val prefs = PreferencesManager(context)
        val categories = prefs.getNotificationCategories()

        // Content intent to open the app
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("transaction_details", messageBody)
            putExtra("account_name", accountName)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("New UPI Transaction: $accountName")
            .setContentText("Tap to categorize your recent transaction.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)

        categories.take(3).forEachIndexed { index, category ->
            val actionIntent = Intent(context, CategoryActionReceiver::class.java).apply {
                putExtra("category", category)
                putExtra("notification_id", notificationId)
                putExtra("transaction_details", messageBody)
                putExtra("account_name", accountName)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 
                (notificationId + index + category.hashCode()), 
                actionIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            notificationBuilder.addAction(0, category, pendingIntent)
        }

        with(NotificationManagerCompat.from(context)) {
            try {
                notify(notificationId, notificationBuilder.build())
            } catch (e: SecurityException) {
                // Handle permission not granted
            }
        }
    }

    private fun createNotificationChannel(context: Context, channelId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "UPI Categorization"
            val descriptionText = "Notifications for categorizing UPI transactions"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
