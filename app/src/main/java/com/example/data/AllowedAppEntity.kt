package com.example.data

import androidx.room.Entity

enum class AllowlistScope(val storageKey: String, val displayTitle: String) {
  LOCK_NOW("LOCK_NOW", "Lock Now"),
  SCHEDULED("SCHEDULED", "Scheduled"),
}

@Entity(tableName = "allowed_apps", primaryKeys = ["packageName", "scope"])
data class AllowedAppEntity(
  val packageName: String,
  val scope: String = AllowlistScope.LOCK_NOW.storageKey,
  val addedAtMillis: Long = System.currentTimeMillis(),
)
