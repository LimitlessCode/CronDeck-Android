package com.example.termux

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.AlertMode
import com.example.data.AppSettings
import java.util.Calendar
import java.util.Locale

data class DndStatus(
    val systemDndActive: Boolean,
    val systemFilterLabel: String,
    val quietHoursActive: Boolean,
    val shouldSuppressAudioAlerts: Boolean,
    val reason: String
)

object AlertDispatcher {
    private const val CHANNEL_STANDARD = "crondeck_standard"
    private const val CHANNEL_ALARM = "crondeck_alarm"

    @Volatile
    private var ttsEngine: TextToSpeech? = null
    @Volatile
    private var activeRingtone: Ringtone? = null

    fun evaluateDnd(
        context: Context,
        settings: AppSettings,
        jobRespectsDnd: Boolean,
        isFailure: Boolean
    ): DndStatus {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val filter = nm.currentInterruptionFilter
        val systemDndActive = filter != NotificationManager.INTERRUPTION_FILTER_ALL
        val filterLabel = when (filter) {
            NotificationManager.INTERRUPTION_FILTER_NONE -> "Total Silence"
            NotificationManager.INTERRUPTION_FILTER_ALARMS -> "Alarms Only"
            NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "Priority Only"
            else -> "Off"
        }

        val quietActive = settings.quietHoursEnabled && isWithinQuietHours(settings)

        if (!jobRespectsDnd) {
            // Even when per-job respectDnd is off, strictly honor Android Total Silence
            val blockedByTotalSilence = settings.respectSystemDnd &&
                filter == NotificationManager.INTERRUPTION_FILTER_NONE
            return DndStatus(
                systemDndActive = systemDndActive,
                systemFilterLabel = filterLabel,
                quietHoursActive = quietActive,
                shouldSuppressAudioAlerts = blockedByTotalSilence,
                reason = if (blockedByTotalSilence) "System Total Silence active" else "Allowed"
            )
        }

        if (settings.respectSystemDnd && systemDndActive) {
            return DndStatus(
                systemDndActive = true,
                systemFilterLabel = filterLabel,
                quietHoursActive = quietActive,
                shouldSuppressAudioAlerts = true,
                reason = "Muted by System DND ($filterLabel)"
            )
        }

        if (quietActive && !(isFailure && settings.allowCriticalBypassQuietHours)) {
            return DndStatus(
                systemDndActive = systemDndActive,
                systemFilterLabel = filterLabel,
                quietHoursActive = true,
                shouldSuppressAudioAlerts = true,
                reason = "Muted by Custom Quiet Hours"
            )
        }

        return DndStatus(
            systemDndActive = systemDndActive,
            systemFilterLabel = filterLabel,
            quietHoursActive = quietActive,
            shouldSuppressAudioAlerts = false,
            reason = if (quietActive && isFailure && settings.allowCriticalBypassQuietHours) {
                "Critical failure bypassed Quiet Hours"
            } else {
                "Allowed"
            }
        )
    }

    fun isWithinQuietHours(settings: AppSettings, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val nowMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val startMinutes = settings.quietStartHour * 60 + settings.quietStartMinute
        val endMinutes = settings.quietEndHour * 60 + settings.quietEndMinute
        return if (startMinutes <= endMinutes) {
            nowMinutes in startMinutes until endMinutes
        } else {
            // Overnight range (e.g., 22:00 -> 07:00)
            nowMinutes >= startMinutes || nowMinutes < endMinutes
        }
    }

    fun dispatchAlert(
        context: Context,
        jobName: String,
        alertMode: AlertMode,
        settings: AppSettings,
        jobRespectsDnd: Boolean,
        isFailure: Boolean,
        detailMessage: String
    ): String {
        if (alertMode == AlertMode.SILENT) return "Silent log only"

        val dnd = evaluateDnd(context, settings, jobRespectsDnd, isFailure)
        ensureChannels(context)

        if (dnd.shouldSuppressAudioAlerts) {
            // Post silent notification entry if permission granted, no sound/TTS/alarm
            postNotification(
                context = context,
                title = jobName,
                body = "$detailMessage (${dnd.reason})",
                isAlarmChannel = false,
                silent = true
            )
            return dnd.reason
        }

        val actions = mutableListOf<String>()

        when (alertMode) {
            AlertMode.SILENT -> return "Silent log only"
            AlertMode.NOTIFICATION -> {
                val posted = postNotification(context, jobName, detailMessage, isAlarmChannel = false, silent = false)
                actions += if (posted) "Notification sent" else "Notification permission needed"
            }
            AlertMode.ALARM -> {
                playShortAlarmChime(context)
                postNotification(context, "Alarm: $jobName", detailMessage, isAlarmChannel = true, silent = false)
                actions += "Alarm chime triggered"
            }
            AlertMode.TTS -> {
                postNotification(context, jobName, detailMessage, isAlarmChannel = false, silent = true)
                speakTts(context, buildSpokenMessage(jobName, isFailure, detailMessage))
                actions += "TTS spoken alert"
            }
            AlertMode.ALARM_AND_TTS -> {
                playShortAlarmChime(context)
                postNotification(context, "Alarm: $jobName", detailMessage, isAlarmChannel = true, silent = false)
                speakTts(context, buildSpokenMessage(jobName, isFailure, detailMessage))
                actions += "Alarm + TTS alert"
            }
        }
        return actions.joinToString(" · ")
    }

    private fun buildSpokenMessage(jobName: String, isFailure: Boolean, detail: String): String {
        return if (isFailure) {
            "Attention. Automation task $jobName reported an issue. $detail"
        } else {
            "Automation task $jobName completed. $detail"
        }
    }

    private fun postNotification(
        context: Context,
        title: String,
        body: String,
        isAlarmChannel: Boolean,
        silent: Boolean
    ): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tapPending = PendingIntent.getActivity(
            context,
            title.hashCode(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = if (isAlarmChannel) CHANNEL_ALARM else CHANNEL_STANDARD
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(if (isAlarmChannel) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setSilent(silent)
            .setContentIntent(tapPending)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify((System.currentTimeMillis() % 100_000).toInt(), builder.build())
        return true
    }

    private fun playShortAlarmChime(context: Context) {
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post {
            runCatching {
                activeRingtone?.stop()
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val ringtone = RingtoneManager.getRingtone(context.applicationContext, uri)
                if (ringtone != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        ringtone.isLooping = false
                    }
                    ringtone.play()
                    activeRingtone = ringtone
                    mainHandler.postDelayed({
                        runCatching { ringtone.stop() }
                    }, 2400L)
                }
            }
        }
    }

    fun speakTts(context: Context, text: String) {
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post {
            val existing = ttsEngine
            if (existing != null) {
                existing.speak(text, TextToSpeech.QUEUE_ADD, null, "crondeck_${System.currentTimeMillis()}")
            } else {
                var newTts: TextToSpeech? = null
                newTts = TextToSpeech(context.applicationContext) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        newTts?.language = Locale.getDefault()
                        newTts?.speak(text, TextToSpeech.QUEUE_ADD, null, "crondeck_init")
                        ttsEngine = newTts
                    }
                }
            }
        }
    }

    private fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_STANDARD) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_STANDARD,
                        "CronDeck Task Updates",
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = "Completion updates for scheduled Termux tasks"
                    }
                )
            }
            if (nm.getNotificationChannel(CHANNEL_ALARM) == null) {
                val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ALARM,
                        "CronDeck Task Alarms",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "High-priority alarms for critical automation tasks"
                        setSound(
                            alarmUri,
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                    }
                )
            }
        }
    }
}
