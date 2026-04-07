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
import java.util.UUID

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (sms in messages) {
                val messageBody = sms.messageBody
                if (SmsUtils.isUpiMessage(messageBody)) {
                    val accountName = SmsUtils.extractAccountName(messageBody)
                    val prefs = PreferencesManager(context)
                    val rememberedCategory = prefs.getRememberedCategory(accountName)
                    val amount = SmsUtils.extractAmount(messageBody)
                    val notificationUuid = UUID.randomUUID().toString()
                    val notificationId = System.currentTimeMillis().toInt()

                    if (rememberedCategory != null) {
                        // Auto-categorize
                        val transaction = Transaction(
                            id = UUID.randomUUID().toString(),
                            accountName = accountName,
                            amount = amount,
                            category = rememberedCategory,
                            details = messageBody,
                            timestamp = System.currentTimeMillis()
                        )
                        prefs.saveTransaction(transaction)
                        
                        // Save to history as categorized
                        val historyItem = NotificationHistoryItem(
                            id = notificationUuid,
                            notificationId = notificationId,
                            accountName = accountName,
                            messageBody = messageBody,
                            amount = amount,
                            category = rememberedCategory,
                            timestamp = System.currentTimeMillis(),
                            isAuto = true,
                            status = NotificationStatus.CATEGORIZED
                        )
                        prefs.saveNotificationHistoryItem(historyItem)
                        
                        // Notify user about auto-categorization
                        showAutoCategorizedNotification(context, accountName, rememberedCategory, notificationUuid, notificationId)
                        
                        // Notify MainActivity to refresh UI
                        val updateIntent = Intent("com.example.pips.UPDATE_TRANSACTIONS")
                        updateIntent.setPackage(context.packageName)
                        context.sendBroadcast(updateIntent)
                    } else {
                        // Save to history as pending
                        val historyItem = NotificationHistoryItem(
                            id = notificationUuid,
                            notificationId = notificationId,
                            accountName = accountName,
                            messageBody = messageBody,
                            amount = amount,
                            timestamp = System.currentTimeMillis(),
                            isAuto = false,
                            status = NotificationStatus.PENDING
                        )
                        prefs.saveNotificationHistoryItem(historyItem)
                        
                        showCategoryNotification(context, accountName, messageBody, notificationUuid, notificationId)
                    }
                }
            }
        }
    }

    private fun showAutoCategorizedNotification(context: Context, accountName: String, category: String, notificationUuid: String, notificationId: Int) {
        val channelId = "upi_categorization_channel"

        createNotificationChannel(context, channelId)

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_upi_settings", accountName)
            putExtra("notification_uuid", notificationUuid)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Transaction Auto-Categorized")
            .setContentText("Transaction for $accountName was automatically set to $category.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .setOngoing(false)

        with(NotificationManagerCompat.from(context)) {
            try {
                notify(notificationId, notificationBuilder.build())
            } catch (e: SecurityException) {
            }
        }
    }

    private fun showCategoryNotification(context: Context, accountName: String, messageBody: String, notificationUuid: String, notificationId: Int) {
        val channelId = "upi_categorization_channel"

        createNotificationChannel(context, channelId)

        val prefs = PreferencesManager(context)
        val allCategories = prefs.getAllCategories()
        
        val classifier = TransactionClassifier(context)
        val suggestedCategories = classifier.classify(messageBody, allCategories)
        classifier.close()

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("transaction_details", messageBody)
            putExtra("account_name", accountName)
            putExtra("notification_uuid", notificationUuid)
            putExtra("notification_id", notificationId)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Delete intent for tracking dismissal
        val deleteIntent = Intent(context, NotificationDismissedReceiver::class.java).apply {
            putExtra("notification_uuid", notificationUuid)
        }
        val deletePendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 1,
            deleteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("New UPI Transaction: $accountName")
            .setContentText("Tap to categorize or select a suggested category below.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentPendingIntent)
            .setDeleteIntent(deletePendingIntent)
            .setAutoCancel(true)
            .setOngoing(true)

        suggestedCategories.forEachIndexed { index, category ->
            val actionIntent = Intent(context, CategoryActionReceiver::class.java).apply {
                putExtra("category", category)
                putExtra("notification_id", notificationId)
                putExtra("transaction_details", messageBody)
                putExtra("account_name", accountName)
                putExtra("notification_uuid", notificationUuid)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 
                (notificationId + index + category.hashCode() + 2), 
                actionIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            notificationBuilder.addAction(0, category, pendingIntent)
        }

        with(NotificationManagerCompat.from(context)) {
            try {
                notify(notificationId, notificationBuilder.build())
            } catch (e: SecurityException) {
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
