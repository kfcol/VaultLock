package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "schedules")
data class ScheduleEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val label: String,
  val daysMask: Int,
  val startMinutes: Int,
  val endMinutes: Int,
  val enabled: Boolean = true,
  val lockAtStart: Boolean = true,
) {
  val crossesMidnight: Boolean
    get() = startMinutes > endMinutes
}

@Entity(tableName = "lock_session")
data class LockSessionEntity(
  @PrimaryKey val id: Int = SINGLETON_ID,
  val endTimeMillis: Long,
  val startedAtMillis: Long,
  val durationMinutes: Int,
) {
  companion object {
    const val SINGLETON_ID = 1
  }
}
