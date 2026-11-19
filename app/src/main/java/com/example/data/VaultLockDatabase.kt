package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
  entities = [AllowedAppEntity::class, ScheduleEntity::class, LockSessionEntity::class],
  version = 2,
  exportSchema = false,
)
abstract class VaultLockDatabase : RoomDatabase() {
  abstract fun vaultLockDao(): VaultLockDao

  companion object {
    @Volatile private var instance: VaultLockDatabase? = null

    val MIGRATION_1_2 =
      object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL(
            "ALTER TABLE schedules ADD COLUMN reminderMinutesBefore INTEGER NOT NULL DEFAULT 0"
          )
        }
      }

    fun getInstance(context: Context): VaultLockDatabase =
      instance
        ?: synchronized(this) {
          instance
            ?: Room.databaseBuilder(
                context.applicationContext,
                VaultLockDatabase::class.java,
                "vaultlock.db",
              )
              .addMigrations(MIGRATION_1_2)
              .build()
              .also { instance = it }
        }
  }
}
