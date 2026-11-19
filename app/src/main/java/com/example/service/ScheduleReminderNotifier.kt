package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.ScheduleEntity
import com.example.domain.PermissionStatusHelper
import com.example.domain.formatMinutesAsTime

object ScheduleReminderNotifier {

  const val CHANNEL_ID = "vaultlock_schedule_reminders"

  fun ensureChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val manager = context.getSystemService(NotificationManager::class.java) ?: return
      val channel =
        NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
          )
          .apply {
            description = context.getString(R.string.reminder_channel_description)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableVibration(true)
          }
      manager.createNotificationChannel(channel)
    }
  }

  fun showReminder(context: Context, schedule: ScheduleEntity) {
    if (!PermissionStatusHelper.areNotificationsGranted(context)) return
    ensureChannel(context)

    val openAppIntent =
      Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }
    val pendingIntent =
      PendingIntent.getActivity(
        context,
        reminderNotificationId(schedule.id),
        openAppIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val scheduleName = schedule.label.ifBlank { "Scheduled Lock" }
    val startText = formatMinutesAsTime(schedule.startMinutes)
    val endText = formatMinutesAsTime(schedule.endMinutes)
    val leadText = formatLeadTime(schedule.reminderMinutesBefore)

    val notification =
      NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        .setContentTitle("$scheduleName starts in $leadText")
        .setContentText("Scheduled lockdown $startText – $endText begins soon. Tap to review allowed apps.")
        .setStyle(
          NotificationCompat.BigTextStyle()
            .bigText(
              "Your \"$scheduleName\" lockdown ($startText – $endText) begins in $leadText. Wrap up tasks or adjust your allowed apps now."
            )
        )
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .build()

    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    manager.notify(reminderNotificationId(schedule.id), notification)
  }

  private fun formatLeadTime(minutes: Int): String {
    val hrs = minutes / 60
    val mins = minutes % 60
    return when {
      hrs > 0 && mins > 0 -> "${hrs}h ${mins}m"
      hrs > 0 -> if (hrs == 1) "1 hour" else "$hrs hours"
      mins == 1 -> "1 minute"
      else -> "$mins minutes"
    }
  }

  private fun reminderNotificationId(scheduleId: Long): Int =
    (90_000 + (scheduleId % 50_000L)).toInt()
}
