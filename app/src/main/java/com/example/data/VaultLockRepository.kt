package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class VaultLockRepository(
  private val dao: VaultLockDao,
  private val prefsRepo: UserPreferencesRepository,
) {
  val preferencesFlow: Flow<VaultLockPreferences> = prefsRepo.preferencesFlow

  fun observeAllowedPackages(scope: AllowlistScope): Flow<Set<String>> =
    dao.observeAllowedPackages(scope.storageKey).map { it.toSet() }

  fun observeSchedules(): Flow<List<ScheduleEntity>> = dao.observeSchedules()

  fun observeLockSession(): Flow<LockSessionEntity?> = dao.observeLockSession()

  suspend fun getLockSession(): LockSessionEntity? = dao.getLockSession()

  suspend fun getEnabledSchedules(): List<ScheduleEntity> = dao.getEnabledSchedules()

  suspend fun getScheduleById(id: Long): ScheduleEntity? = dao.getScheduleById(id)

  suspend fun getAllowedPackages(scope: AllowlistScope): Set<String> =
    dao.getAllowedPackages(scope.storageKey).toSet()

  suspend fun getPreferences(): VaultLockPreferences = prefsRepo.preferencesFlow.first()

  suspend fun setAppAllowed(packageName: String, scope: AllowlistScope, allowed: Boolean) {
    if (allowed) {
      dao.insertAllowedApp(AllowedAppEntity(packageName = packageName, scope = scope.storageKey))
    } else {
      dao.deleteAllowedApp(packageName = packageName, scope = scope.storageKey)
    }
  }

  suspend fun selectAllApps(packageNames: List<String>, scope: AllowlistScope) {
    if (packageNames.isEmpty()) return
    val now = System.currentTimeMillis()
    val entities =
      packageNames.map { pkg ->
        AllowedAppEntity(packageName = pkg, scope = scope.storageKey, addedAtMillis = now)
      }
    dao.insertAllowedApps(entities)
  }

  suspend fun selectNoApps(scope: AllowlistScope) {
    dao.deleteAllAllowedApps(scope.storageKey)
  }

  suspend fun copyAllowlist(from: AllowlistScope, to: AllowlistScope) {
    val sourcePackages = dao.getAllowedPackages(from.storageKey)
    dao.deleteAllAllowedApps(to.storageKey)
    selectAllApps(sourcePackages, to)
  }

  suspend fun startLockNowSession(durationMinutes: Int, nowMillis: Long = System.currentTimeMillis()): LockSessionEntity {
    val safeMinutes = durationMinutes.coerceIn(1, 24 * 60)
    val endTime = nowMillis + safeMinutes * 60_000L
    val session =
      LockSessionEntity(
        id = LockSessionEntity.SINGLETON_ID,
        endTimeMillis = endTime,
        startedAtMillis = nowMillis,
        durationMinutes = safeMinutes,
      )
    dao.upsertLockSession(session)
    return session
  }

  suspend fun clearLockNowSession() {
    dao.clearLockSession()
  }

  suspend fun upsertSchedule(schedule: ScheduleEntity): Long {
    return if (schedule.id == 0L) {
      dao.insertSchedule(schedule)
    } else {
      dao.updateSchedule(schedule)
      schedule.id
    }
  }

  suspend fun deleteSchedule(id: Long) {
    dao.deleteScheduleById(id)
  }

  suspend fun setScheduleEnabled(id: Long, enabled: Boolean) {
    dao.setScheduleEnabled(id, enabled)
  }

  suspend fun setSeparateAllowlists(enabled: Boolean) = prefsRepo.setSeparateAllowlists(enabled)

  suspend fun setPreferDeviceAdminLock(enabled: Boolean) = prefsRepo.setPreferDeviceAdminLock(enabled)

  suspend fun setCooldownMillis(millis: Long) = prefsRepo.setCooldownMillis(millis)

  suspend fun setLockAtScheduleStart(enabled: Boolean) = prefsRepo.setLockAtScheduleStart(enabled)
}
