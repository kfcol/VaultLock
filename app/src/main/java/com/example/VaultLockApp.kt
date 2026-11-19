package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.example.data.InstalledAppsRepository
import com.example.data.UserPreferencesRepository
import com.example.data.VaultLockDatabase
import com.example.data.VaultLockRepository
import com.example.service.LockSessionForegroundService

class VaultLockApp : Application() {

  val database: VaultLockDatabase by lazy { VaultLockDatabase.getInstance(this) }
  val preferencesRepository: UserPreferencesRepository by lazy { UserPreferencesRepository(this) }
  val installedAppsRepository: InstalledAppsRepository by lazy { InstalledAppsRepository(this) }
  val repository: VaultLockRepository by lazy {
    VaultLockRepository(database.vaultLockDao(), preferencesRepository)
  }

  override fun onCreate() {
    super.onCreate()
    createNotificationChannel()
    com.example.service.ScheduleReminderNotifier.ensureChannel(this)
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val manager = getSystemService(NotificationManager::class.java) ?: return
      val channel =
        NotificationChannel(
            LockSessionForegroundService.CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
          )
          .apply {
            description = getString(R.string.notification_channel_description)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
          }
      manager.createNotificationChannel(channel)
    }
  }
}
