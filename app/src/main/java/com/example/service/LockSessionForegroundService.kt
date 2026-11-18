package com.example.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.R
import com.example.VaultLockApp
import com.example.domain.ActiveLockState
import com.example.domain.LockEvaluator
import com.example.domain.formatMinutesAsTime
import com.example.domain.formatRemainingMillis
import com.example.ui.drawer.AllowedLauncherActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LockSessionForegroundService : Service() {

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private var tickerJob: Job? = null

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP) {
      stopSelfCleanly()
      return START_NOT_STICKY
    }

    isRunning = true
    val initialNotification = buildNotification(ActiveLockState(isLockNowActive = true), System.currentTimeMillis())
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      startForeground(
        NOTIFICATION_ID,
        initialNotification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
      )
    } else {
      startForeground(NOTIFICATION_ID, initialNotification)
    }

    startTickerLoop()
    return START_STICKY
  }

  private fun startTickerLoop() {
    if (tickerJob?.isActive == true) return
    val repo = (application as VaultLockApp).repository
    val notificationManager = getSystemService(NotificationManager::class.java)

    tickerJob =
      serviceScope.launch {
        while (isActive) {
          val now = System.currentTimeMillis()
          val session = repo.getLockSession()
          val schedules = repo.getEnabledSchedules()
          val prefs = repo.getPreferences()

          if (session != null && session.endTimeMillis <= now) {
            repo.clearLockNowSession()
          }

          val activeState =
            LockEvaluator.evaluateActiveLock(
              lockSession = session,
              schedules = schedules,
              nowMillis = now,
              separateAllowlists = prefs.separateAllowlists,
            )

          if (!activeState.isAnyLockActive) {
            stopSelfCleanly()
            break
          }

          notificationManager?.notify(NOTIFICATION_ID, buildNotification(activeState, now))
          delay(if (activeState.isLockNowActive) 1000L else 15_000L)
        }
      }
  }

  private fun buildNotification(state: ActiveLockState, nowMillis: Long): Notification {
    val drawerIntent =
      Intent(this, AllowedLauncherActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
      }
    val drawerPendingIntent =
      PendingIntent.getActivity(
        this,
        101,
        drawerIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val title: String
    val subtitle: String
    if (state.isLockNowActive) {
      val remaining = (state.lockNowEndTimeMillis - nowMillis).coerceAtLeast(0L)
      title = "VaultLock Active • ${formatRemainingMillis(remaining)} left"
      subtitle = "Tap to open your Allowed Apps Drawer"
    } else if (state.activeSchedule != null) {
      val sched = state.activeSchedule
      val label = sched.label.ifBlank { "Scheduled Lock" }
      title = "$label Active • Until ${formatMinutesAsTime(sched.endMinutes)}"
      subtitle = "Tap to launch allowed apps without unlocking into blocked apps"
    } else {
      title = "VaultLock Guard Running"
      subtitle = "Tap to open Allowed Apps Drawer"
    }

    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.ic_lock_lock)
      .setContentTitle(title)
      .setContentText(subtitle)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .setCategory(NotificationCompat.CATEGORY_STATUS)
      .setContentIntent(drawerPendingIntent)
      .addAction(
        android.R.drawable.ic_menu_view,
        "Allowed Apps",
        drawerPendingIntent,
      )
      .build()
  }

  private fun stopSelfCleanly() {
    isRunning = false
    tickerJob?.cancel()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      stopForeground(STOP_FOREGROUND_REMOVE)
    } else {
      @Suppress("DEPRECATION")
      stopForeground(true)
    }
    stopSelf()
  }

  override fun onDestroy() {
    isRunning = false
    serviceScope.cancel()
    super.onDestroy()
  }

  companion object {
    const val CHANNEL_ID = "vaultlock_active_session"
    const val NOTIFICATION_ID = 7001
    private const val ACTION_STOP = "com.example.action.STOP_LOCK_SERVICE"

    @Volatile
    var isRunning: Boolean = false
      private set

    fun ensureRunning(context: Context) {
      val intent = Intent(context, LockSessionForegroundService::class.java)
      runCatching { ContextCompat.startForegroundService(context, intent) }
    }

    fun stop(context: Context) {
      if (!isRunning) return
      val intent =
        Intent(context, LockSessionForegroundService::class.java).apply { action = ACTION_STOP }
      runCatching { context.startService(intent) }
    }
  }
}
