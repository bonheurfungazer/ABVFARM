package com.example

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.data.Reminder
import com.example.utils.FarmNotificationManager

object ReminderScheduler {
    fun scheduleReminder(context: Context, reminder: Reminder) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        
        val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                alarmManager.canScheduleExactAlarms()
            } catch (e: Exception) {
                false
            }
        } else {
            true
        }

        val isUrgent = reminder.priority == "Haute"
        val channelId = if (isUrgent) FarmNotificationManager.CHANNEL_CRITICAL_ALERTS else FarmNotificationManager.CHANNEL_REMINDERS
        
        // 1. Exact Alarm (will trigger a persistent notification)
        val intentExact = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("reminder_id", reminder.id)
            putExtra("reminder_title", reminder.title)
            putExtra("reminder_description", reminder.description)
            putExtra("reminder_type", "exact")
            putExtra("channel_id", channelId)
            putExtra("is_urgent", isUrgent)
        }

        val pendingIntentExact = PendingIntent.getBroadcast(
            context,
            reminder.id,
            intentExact,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (reminder.dueDate > System.currentTimeMillis()) {
            setAlarm(alarmManager, reminder.dueDate, pendingIntentExact, canScheduleExact)
        }

        // 2. 2h30 before alarm (150 minutes before)
        val time2h30Before = reminder.dueDate - (150 * 60 * 1000L)
        if (time2h30Before > System.currentTimeMillis()) {
            val intentBefore1 = Intent(context, ReminderReceiver::class.java).apply {
                putExtra("reminder_id", reminder.id + 1000000)
                putExtra("reminder_title", "[Rappel - 2h30] " + reminder.title)
                putExtra("reminder_description", reminder.description)
                putExtra("reminder_type", "pre")
                putExtra("channel_id", channelId)
                putExtra("is_urgent", isUrgent)
            }
            val pendingBefore1 = PendingIntent.getBroadcast(
                context,
                reminder.id + 1000000,
                intentBefore1,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            setAlarm(alarmManager, time2h30Before, pendingBefore1, canScheduleExact)
        }

        // 3. 30min before alarm (30 minutes before)
        val time30minBefore = reminder.dueDate - (30 * 60 * 1000L)
        if (time30minBefore > System.currentTimeMillis()) {
            val intentBefore2 = Intent(context, ReminderReceiver::class.java).apply {
                putExtra("reminder_id", reminder.id + 2000000)
                putExtra("reminder_title", "[Rappel - 30min] " + reminder.title)
                putExtra("reminder_description", reminder.description)
                putExtra("reminder_type", "pre")
                putExtra("channel_id", channelId)
                putExtra("is_urgent", isUrgent)
            }
            val pendingBefore2 = PendingIntent.getBroadcast(
                context,
                reminder.id + 2000000,
                intentBefore2,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            setAlarm(alarmManager, time30minBefore, pendingBefore2, canScheduleExact)
        }
    }

    private fun setAlarm(alarmManager: AlarmManager, triggerAtMillis: Long, pendingIntent: PendingIntent, canScheduleExact: Boolean) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerAtMillis, pendingIntent)
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
                return
            }
        } catch (e: Exception) {
            // Fallback to setExactAndAllowWhileIdle if setAlarmClock throws
        }

        if (canScheduleExact) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
            } catch (e: Exception) {
                try {
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            }
        } else {
            try {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun cancelReminder(context: Context, reminder: Reminder) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        
        cancelIntent(context, alarmManager, reminder.id)
        cancelIntent(context, alarmManager, reminder.id + 1000000)
        cancelIntent(context, alarmManager, reminder.id + 2000000)

        // Force dismiss actual active notifications from status bar
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        notificationManager?.cancel(reminder.id)
        notificationManager?.cancel(reminder.id + 1000000)
        notificationManager?.cancel(reminder.id + 2000000)
    }

    private fun cancelIntent(context: Context, alarmManager: AlarmManager, id: Int) {
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            id,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    fun triggerImmediateNotification(context: Context, reminder: Reminder) {
        val isUrgent = reminder.priority == "Haute"
        val channelId = if (isUrgent) FarmNotificationManager.CHANNEL_CRITICAL_ALERTS else FarmNotificationManager.CHANNEL_REMINDERS
        
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("reminder_id", reminder.id)
            putExtra("reminder_title", reminder.title)
            putExtra("reminder_description", reminder.description)
            putExtra("reminder_type", "exact")
            putExtra("channel_id", channelId)
            putExtra("is_urgent", isUrgent)
        }
        context.sendBroadcast(intent)
    }
}
