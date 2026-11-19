package com.example.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.example.data.ScheduleEntity
import com.example.domain.LockEvaluator
import com.example.domain.PermissionStatusHelper
import com.example.receiver.BootAndAlarmReceiver
import com.example.receiver.VaultLockDeviceAdminReceiver
import java.util.concurrent.atomic.AtomicLong

object ScreenLockController {

  private val lastLockElapsedMs = AtomicLong(0L)

  fun triggerImmediateLock(
    context: Context,
    preferDeviceAdmin: Boolean,
    cooldownMillis: Long = 1500L,
    respectCooldown: Boolean = true,
  ): Boolean {
    val nowElapsed = SystemClock.elapsedRealtime()
    if (respectCooldown) {
      val prev = lastLockElapsedMs.get()
      if (nowElapsed - prev < cooldownMillis) {
        return false
      }
    }

    val accessibility = VaultLockAccessibilityService.instance
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
    val adminComponent = ComponentName(context, VaultLockDeviceAdminReceiver::class.java)
    val isAdminActive = dpm?.isAdminActive(adminComponent) == true

    // Go Home via Accessibility if available so the blocked app isn't left in foreground
    accessibility?.performHomeAction()

    val locked =
      when {
        preferDeviceAdmin && isAdminActive -> {
          runCatching {
            dpm?.lockNow()
            true
          }.getOrDefault(false)
        }
        accessibility != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> {
          accessibility.performLockScreenAction()
        }
        isAdminActive -> {
          runCatching {
            dpm?.lockNow()
            true
          }.getOrDefault(false)
        }
        else -> false
      }

    if (locked) {
      lastLockElapsedMs.set(nowElapsed)
    }
    return locked
  }

  fun syncScheduleAlarms(
    context: Context,
    schedules: List<ScheduleEntity>,
    lockAtScheduleStart: Boolean,
  ) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
    val nowMillis = System.currentTimeMillis()
    val canExact = PermissionStatusHelper.canScheduleExactAlarms(context)

    for (schedule in schedules) {
      // 1. Schedule start-lock alarm
      val startPendingIntent =
        buildSchedulePendingIntent(
          context = context,
          action = BootAndAlarmReceiver.ACTION_SCHEDULE_START_LOCK,
          requestCode = scheduleRequestCode(schedule.id),
          scheduleId = schedule.id,
          flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

      val nextStart =
        if (lockAtScheduleStart && schedule.enabled && schedule.lockAtStart && schedule.daysMask != 0) {
          LockEvaluator.computeNextStartTimeMillis(schedule, nowMillis)
        } else {
          null
        }

      if (nextStart == null) {
        alarmManager.cancel(startPendingIntent)
      } else {
        scheduleRtcAlarm(alarmManager, nextStart, startPendingIntent, canExact)
      }

      // 2. Schedule pre-lockdown reminder alarm
      val reminderPendingIntent =
        buildSchedulePendingIntent(
          context = context,
          action = BootAndAlarmReceiver.ACTION_SCHEDULE_REMINDER,
          requestCode = reminderRequestCode(schedule.id),
          scheduleId = schedule.id,
          flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

      val nextReminder =
        if (schedule.enabled && schedule.reminderMinutesBefore > 0 && schedule.daysMask != 0) {
          LockEvaluator.computeNextReminderTimeMillis(schedule, nowMillis)
        } else {
          null
        }

      if (nextReminder == null) {
        alarmManager.cancel(reminderPendingIntent)
      } else {
        scheduleRtcAlarm(alarmManager, nextReminder, reminderPendingIntent, canExact)
      }
    }
  }

  fun cancelScheduleAlarm(context: Context, scheduleId: Long) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
    val startPending =
      PendingIntent.getBroadcast(
        context,
        scheduleRequestCode(scheduleId),
        Intent(context, BootAndAlarmReceiver::class.java).apply {
          action = BootAndAlarmReceiver.ACTION_SCHEDULE_START_LOCK
          putExtra(BootAndAlarmReceiver.EXTRA_SCHEDULE_ID, scheduleId)
        },
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
      )
    if (startPending != null) {
      alarmManager.cancel(startPending)
    }

    val reminderPending =
      PendingIntent.getBroadcast(
        context,
        reminderRequestCode(scheduleId),
        Intent(context, BootAndAlarmReceiver::class.java).apply {
          action = BootAndAlarmReceiver.ACTION_SCHEDULE_REMINDER
          putExtra(BootAndAlarmReceiver.EXTRA_SCHEDULE_ID, scheduleId)
        },
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
      )
    if (reminderPending != null) {
      alarmManager.cancel(reminderPending)
    }
  }

  private fun buildSchedulePendingIntent(
    context: Context,
    action: String,
    requestCode: Int,
    scheduleId: Long,
    flags: Int,
  ): PendingIntent {
    val intent =
      Intent(context, BootAndAlarmReceiver::class.java).apply {
        this.action = action
        putExtra(BootAndAlarmReceiver.EXTRA_SCHEDULE_ID, scheduleId)
      }
    return PendingIntent.getBroadcast(context, requestCode, intent, flags)
  }

  private fun scheduleRtcAlarm(
    alarmManager: AlarmManager,
    triggerAtMillis: Long,
    pendingIntent: PendingIntent,
    canExact: Boolean,
  ) {
    if (canExact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
    } else {
      alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
    }
  }

  private fun scheduleRequestCode(scheduleId: Long): Int =
    (20_000 + (scheduleId % 50_000L)).toInt()

  private fun reminderRequestCode(scheduleId: Long): Int =
    (80_000 + (scheduleId % 50_000L)).toInt()
}
