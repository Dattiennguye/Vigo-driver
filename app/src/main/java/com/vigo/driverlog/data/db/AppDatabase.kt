package com.vigo.driverlog.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Cơ sở dữ liệu cục bộ. Khi nâng version PHẢI viết Migration – tuyệt đối không dùng
 * fallbackToDestructiveMigration để không mất dữ liệu cũ khi cập nhật ứng dụng.
 */
@Database(
    entities = [TripEntity::class, EventEntity::class, EditLogEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun eventDao(): EventDao
    abstract fun editLogDao(): EditLogDao

    companion object {
        const val NAME = "vigo_driver_log.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
    }
}
