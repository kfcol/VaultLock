package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.VaultLockApp
import com.example.domain.LockEvaluator
import com.example.service.LockSessionForegroundService
import com.example.service.ScreenLockController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootAndAlarmReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    val action = intent.action ?: return
    val pendingResult = goAsync()
    val app = context.applicationContext as VaultLockApp
    val repo = app.repository

    CoroutineScope(Dispatchers.IO).launch {
      try {
        val nowMillis = System.currentTimeMillis()
        val session = repo.getLockSession()
        val schedules = repo.getEnabledSchedules()
        val prefs = repo.getPreferences()

        if (session != null && session.endTimeMillis <= nowMillis) {
          repo.clearLockNowSession()
        }

        val activeState =
          LockEvaluator.evaluateActiveLock(
            lockSession = session,
            schedules = schedules,
            nowMillis = nowMillis,
            separateAllowlists = prefs.separateAllowlists,
          )

        when (action) {
          Intent.ACTION_BOOT_COMPLETED,
          Intent.ACTION_MY_PACKAGE_REPLACED -> {
            if (activeState.isAnyLockActive) {
              LockSessionForegroundService.ensureRunning(context)
            }
            ScreenLockController.syncScheduleAlarms(
              context = context,
              schedules = schedules,
              lockAtScheduleStart = prefs.lockAtScheduleStart,
            )
          }
          ACTION_SCHEDULE_START_LOCK -> {
            if (activeState.isAnyLockActive) {
              LockSessionForegroundService.ensureRunning(context)
              ScreenLockController.triggerImmediateLock(
                context = context,
                preferDeviceAdmin = prefs.preferDeviceAdminLock,
                cooldownMillis = prefs.cooldownMillis,
                respectCooldown = false,
              )
            }
            ScreenLockController.syncScheduleAlarms(
              context = context,
              schedules = schedules,
              lockAtScheduleStart = prefs.lockAtScheduleStart,
            )
          }
        }
      } finally {
        pendingResult.finish()
      }
    }
  }

  companion object {
    const val ACTION_SCHEDULE_START_LOCK = "com.example.action.SCHEDULE_START_LOCK"
    const val EXTRA_SCHEDULE_ID = "extra_schedule_id"
  }
}
