package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultLockDao {

  @Query("SELECT packageName FROM allowed_apps WHERE scope = :scope")
  fun observeAllowedPackages(scope: String): Flow<List<String>>

  @Query("SELECT * FROM allowed_apps")
  fun observeAllAllowedApps(): Flow<List<AllowedAppEntity>>

  @Query("SELECT packageName FROM allowed_apps WHERE scope = :scope")
  suspend fun getAllowedPackages(scope: String): List<String>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAllowedApp(entity: AllowedAppEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAllowedApps(entities: List<AllowedAppEntity>)

  @Query("DELETE FROM allowed_apps WHERE packageName = :packageName AND scope = :scope")
  suspend fun deleteAllowedApp(packageName: String, scope: String)

  @Query("DELETE FROM allowed_apps WHERE scope = :scope")
  suspend fun deleteAllAllowedApps(scope: String)

  @Query("SELECT * FROM schedules ORDER BY startMinutes ASC, id ASC")
  fun observeSchedules(): Flow<List<ScheduleEntity>>

  @Query("SELECT * FROM schedules WHERE enabled = 1")
  suspend fun getEnabledSchedules(): List<ScheduleEntity>

  @Query("SELECT * FROM schedules WHERE id = :id LIMIT 1")
  suspend fun getScheduleById(id: Long): ScheduleEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSchedule(schedule: ScheduleEntity): Long

  @Update
  suspend fun updateSchedule(schedule: ScheduleEntity)

  @Query("DELETE FROM schedules WHERE id = :id")
  suspend fun deleteScheduleById(id: Long)

  @Query("UPDATE schedules SET enabled = :enabled WHERE id = :id")
  suspend fun setScheduleEnabled(id: Long, enabled: Boolean)

  @Query("SELECT * FROM lock_session WHERE id = 1 LIMIT 1")
  fun observeLockSession(): Flow<LockSessionEntity?>

  @Query("SELECT * FROM lock_session WHERE id = 1 LIMIT 1")
  suspend fun getLockSession(): LockSessionEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertLockSession(session: LockSessionEntity)

  @Query("DELETE FROM lock_session")
  suspend fun clearLockSession()
}
