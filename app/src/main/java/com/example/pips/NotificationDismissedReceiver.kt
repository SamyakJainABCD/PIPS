package com.example.pips

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class NotificationDismissedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getStringExtra("notification_uuid")
        if (notificationId != null) {
            val prefs = PreferencesManager(context)
            val history = prefs.getNotificationHistory()
            val item = history.find { it.id == notificationId }
            
            // Only mark as DISMISSED if it's currently PENDING
            if (item != null && item.status == NotificationStatus.PENDING) {
                prefs.updateNotificationStatus(notificationId, NotificationStatus.DISMISSED)
                
                // Notify MainActivity to update the badge count
                val updateIntent = Intent("com.example.pips.UPDATE_TRANSACTIONS")
                updateIntent.setPackage(context.packageName)
                context.sendBroadcast(updateIntent)
            }
        }
    }
}
