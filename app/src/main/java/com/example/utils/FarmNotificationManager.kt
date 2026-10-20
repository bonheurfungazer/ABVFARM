package com.example.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Gestionnaire centralisé des notifications push, alertes sonores (bip) et vibrations
 * pour l'élevage cunicole ABV Farm.
 */
object FarmNotificationManager {

    // Notification Channel Identifiers
    const val CHANNEL_CRITICAL_ALERTS = "farm_critical_alerts"
    const val CHANNEL_REPRODUCTION = "farm_reproduction"
    const val CHANNEL_HEALTH = "farm_health"
    const val CHANNEL_REMINDERS = "farm_reminders"
    const val CHANNEL_STOCK = "farm_stock"

    // High-visibility rhythmic vibration pattern (delay, vibe, pause, vibe, pause, vibe)
    val CRITICAL_VIBRATION_PATTERN = longArrayOf(0, 450, 180, 450, 180, 650)
    val STANDARD_VIBRATION_PATTERN = longArrayOf(0, 350, 150, 350)

    /**
     * Initialise tous les canaux de notifications Android (Android 8.0+) avec son et vibration configurés.
     */
    fun createAllNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val alarmSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notifSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val audioAttributesAlarm = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_ALARM)
            .build()

        val audioAttributesEvent = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .build()

        // 1. Canal Alertes Critiques / Urgences
        val criticalChannel = NotificationChannel(
            CHANNEL_CRITICAL_ALERTS,
            "🚨 Alertes Critiques Élevage",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alertes urgentes : Mises bas imminentes, urgences sanitaires, ruptures de stocks"
            enableLights(true)
            lightColor = Color.RED
            enableVibration(true)
            vibrationPattern = CRITICAL_VIBRATION_PATTERN
            setSound(alarmSoundUri, audioAttributesAlarm)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(true)
        }

        // 2. Canal Reproduction & Maternité
        val reproChannel = NotificationChannel(
            CHANNEL_REPRODUCTION,
            "🐇 Reproduction & Maternité",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Palpations à J+10, Boîtes à nid à J+28, Mises bas et Sevrages"
            enableLights(true)
            lightColor = Color.MAGENTA
            enableVibration(true)
            vibrationPattern = STANDARD_VIBRATION_PATTERN
            setSound(notifSoundUri, audioAttributesEvent)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(true)
        }

        // 3. Canal Soins & Vaccinations
        val healthChannel = NotificationChannel(
            CHANNEL_HEALTH,
            "💉 Soins Vétérinaires & Vaccins",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Rappels de vaccins VHD/Myxomatose, traitements et quarantaines"
            enableLights(true)
            lightColor = Color.CYAN
            enableVibration(true)
            vibrationPattern = STANDARD_VIBRATION_PATTERN
            setSound(notifSoundUri, audioAttributesEvent)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(true)
        }

        // 4. Canal Rappels & Tâches
        val remindersChannel = NotificationChannel(
            CHANNEL_REMINDERS,
            "📅 Tâches & Calendrier",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Rappels quotidiens de l'éleveur et planification"
            enableLights(true)
            lightColor = Color.GREEN
            enableVibration(true)
            vibrationPattern = STANDARD_VIBRATION_PATTERN
            setSound(notifSoundUri, audioAttributesEvent)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(true)
        }

        // 5. Canal Stocks & Aliments
        val stockChannel = NotificationChannel(
            CHANNEL_STOCK,
            "🌾 Stocks & Granulés",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Alertes de réapprovisionnement en aliment et matériel"
            enableLights(true)
            lightColor = Color.YELLOW
            enableVibration(true)
            vibrationPattern = STANDARD_VIBRATION_PATTERN
            setSound(notifSoundUri, audioAttributesEvent)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        notificationManager.createNotificationChannels(
            listOf(criticalChannel, reproChannel, healthChannel, remindersChannel, stockChannel)
        )
    }

    /**
     * Déclenche immédiatement un signal sonore (bip matériel distinct) et une vibration haptique.
     */
    fun playAudibleBeepAndVibration(context: Context, isUrgent: Boolean = false) {
        // 1. Déclenchement de la vibration tactile
        try {
            val pattern = if (isUrgent) CRITICAL_VIBRATION_PATTERN else STANDARD_VIBRATION_PATTERN
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(pattern, -1)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Déclenchement du signal sonore (Bip matériel haute fidélité via ToneGenerator + Ringtone)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val streamType = if (isUrgent) AudioManager.STREAM_ALARM else AudioManager.STREAM_NOTIFICATION
                val toneGen = ToneGenerator(streamType, 100)
                
                if (isUrgent) {
                    // Double bip d'alerte incisif
                    toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 350)
                    delay(400)
                    toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 450)
                } else {
                    // Bip standard de confirmation
                    toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 300)
                    delay(350)
                    toneGen.startTone(ToneGenerator.TONE_PROP_ACK, 250)
                }
                delay(600)
                toneGen.release()
            } catch (e: Exception) {
                // Fallback direct sur le lecteur de sonnerie système
                try {
                    val soundUri = RingtoneManager.getDefaultUri(
                        if (isUrgent) RingtoneManager.TYPE_ALARM else RingtoneManager.TYPE_NOTIFICATION
                    )
                    val ringtone = RingtoneManager.getRingtone(context.applicationContext, soundUri)
                    ringtone?.play()
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            }
        }
    }

    /**
     * Émet une notification push complète avec titre, contenu détaillé, icône, son et vibration.
     */
    fun sendPushNotification(
        context: Context,
        notificationId: Int,
        title: String,
        message: String,
        channelId: String = CHANNEL_REMINDERS,
        isUrgent: Boolean = false,
        badgeText: String? = null
    ) {
        // Garantit que les canaux existent
        createAllNotificationChannels(context)

        // Déclenche le bip sonore et la vibration en direct
        playAudibleBeepAndVibration(context, isUrgent)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val soundUri = RingtoneManager.getDefaultUri(
            if (isUrgent) RingtoneManager.TYPE_ALARM else RingtoneManager.TYPE_NOTIFICATION
        )
        val vibrationPattern = if (isUrgent) CRITICAL_VIBRATION_PATTERN else STANDARD_VIBRATION_PATTERN

        // PendingIntent pour ouvrir l'application au tap
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("notification_source", channelId)
            putExtra("notification_id", notificationId)
        }

        val pendingIntent = if (launchIntent != null) {
            PendingIntent.getActivity(
                context,
                notificationId,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else null

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(if (message.isNotEmpty()) "$message\n\n🐰 ABV Farm • Gestion d'élevage" else "🐰 ABV Farm • Gestion d'élevage")
            )
            .setPriority(if (isUrgent) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (isUrgent) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSound(soundUri)
            .setVibrate(vibrationPattern)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)

        if (badgeText != null) {
            builder.setSubText(badgeText)
        }

        if (pendingIntent != null) {
            builder.setContentIntent(pendingIntent)
        }

        notificationManager.notify(notificationId, builder.build())
    }

    /**
     * Envoie une alerte de reproduction (Palpation, Mise bas, Nid, Sevrage).
     */
    fun sendReproductionAlert(
        context: Context,
        title: String,
        message: String,
        isUrgent: Boolean = false
    ) {
        val id = (System.currentTimeMillis() % 100000).toInt() + 1000
        sendPushNotification(
            context = context,
            notificationId = id,
            title = title,
            message = message,
            channelId = CHANNEL_REPRODUCTION,
            isUrgent = isUrgent,
            badgeText = "Maternité"
        )
    }

    /**
     * Envoie une alerte de santé ou vétérinaire (Vaccination, Traitement, Quarantaine).
     */
    fun sendHealthAlert(
        context: Context,
        title: String,
        message: String,
        isUrgent: Boolean = false
    ) {
        val id = (System.currentTimeMillis() % 100000).toInt() + 2000
        sendPushNotification(
            context = context,
            notificationId = id,
            title = title,
            message = message,
            channelId = CHANNEL_HEALTH,
            isUrgent = isUrgent,
            badgeText = "Santé Animale"
        )
    }

    /**
     * Envoie une alerte de stock (Nourriture faible ou épuisée).
     */
    fun sendStockAlert(
        context: Context,
        title: String,
        message: String,
        isUrgent: Boolean = false
    ) {
        val id = (System.currentTimeMillis() % 100000).toInt() + 3000
        sendPushNotification(
            context = context,
            notificationId = id,
            title = title,
            message = message,
            channelId = CHANNEL_STOCK,
            isUrgent = isUrgent,
            badgeText = "Stocks"
        )
    }

    /**
     * Déclenche une alerte de test complète (Bip sonore + vibration + notification système).
     */
    fun triggerTestAlert(context: Context) {
        sendPushNotification(
            context = context,
            notificationId = 99999,
            title = "🔔 Test Alerte Sonore & Vibration",
            message = "Le signal sonore (bip) et la vibration de l'élevage fonctionnent parfaitement !",
            channelId = CHANNEL_CRITICAL_ALERTS,
            isUrgent = true,
            badgeText = "Test Élevage"
        )
    }
}
