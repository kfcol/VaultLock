package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.vaultLockDataStore: DataStore<Preferences> by
  preferencesDataStore(name = "vaultlock_preferences")

data class VaultLockPreferences(
  val separateAllowlists: Boolean = true,
  val preferDeviceAdminLock: Boolean = false,
  val cooldownMillis: Long = 1500L,
  val lockAtScheduleStart: Boolean = true,
)

class UserPreferencesRepository(private val context: Context) {

  private object Keys {
    val SEPARATE_ALLOWLISTS = booleanPreferencesKey("separate_allowlists")
    val PREFER_DEVICE_ADMIN_LOCK = booleanPreferencesKey("prefer_device_admin_lock")
    val COOLDOWN_MILLIS = longPreferencesKey("cooldown_millis")
    val LOCK_AT_SCHEDULE_START = booleanPreferencesKey("lock_at_schedule_start")
  }

  val preferencesFlow: Flow<VaultLockPreferences> =
    context.vaultLockDataStore.data.map { prefs ->
      VaultLockPreferences(
        separateAllowlists = prefs[Keys.SEPARATE_ALLOWLISTS] ?: true,
        preferDeviceAdminLock = prefs[Keys.PREFER_DEVICE_ADMIN_LOCK] ?: false,
        cooldownMillis = (prefs[Keys.COOLDOWN_MILLIS] ?: 1500L).coerceIn(1000L, 5000L),
        lockAtScheduleStart = prefs[Keys.LOCK_AT_SCHEDULE_START] ?: true,
      )
    }

  suspend fun setSeparateAllowlists(enabled: Boolean) {
    context.vaultLockDataStore.edit { it[Keys.SEPARATE_ALLOWLISTS] = enabled }
  }

  suspend fun setPreferDeviceAdminLock(enabled: Boolean) {
    context.vaultLockDataStore.edit { it[Keys.PREFER_DEVICE_ADMIN_LOCK] = enabled }
  }

  suspend fun setCooldownMillis(millis: Long) {
    context.vaultLockDataStore.edit { it[Keys.COOLDOWN_MILLIS] = millis.coerceIn(1000L, 5000L) }
  }

  suspend fun setLockAtScheduleStart(enabled: Boolean) {
    context.vaultLockDataStore.edit { it[Keys.LOCK_AT_SCHEDULE_START] = enabled }
  }
}
