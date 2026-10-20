package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.utils.FarmNotificationManager

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("reminder_id", 0)
        val title = intent.getStringExtra("reminder_title") ?: "Rappel d'élevage 🐰"
        val description = intent.getStringExtra("reminder_description") ?: ""
        val channelId = intent.getStringExtra("channel_id") ?: FarmNotificationManager.CHANNEL_REMINDERS
        val isUrgent = intent.getBooleanExtra("is_urgent", true)

        FarmNotificationManager.sendPushNotification(
            context = context,
            notificationId = id,
            title = title,
            message = description,
            channelId = channelId,
            isUrgent = isUrgent,
            badgeText = "Alerte Élevage"
        )
    }
}

