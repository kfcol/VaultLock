package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
  entities = [AllowedAppEntity::class, ScheduleEntity::class, LockSessionEntity::class],
  version = 1,
  exportSchema = false,
)
abstract class VaultLockDatabase : RoomDatabase() {
  abstract fun vaultLockDao(): VaultLockDao

  companion object {
    @Volatile private var instance: VaultLockDatabase? = null

    fun getInstance(context: Context): VaultLockDatabase =
      instance
        ?: synchronized(this) {
          instance
            ?: Room.databaseBuilder(
                context.applicationContext,
                VaultLockDatabase::class.java,
                "vaultlock.db",
              )
              .build()
              .also { instance = it }
        }
  }
}
