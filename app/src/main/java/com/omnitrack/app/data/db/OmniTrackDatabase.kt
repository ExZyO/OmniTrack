package com.omnitrack.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.omnitrack.app.data.db.dao.CategoryDao
import com.omnitrack.app.data.db.dao.MediaDao
import com.omnitrack.app.data.db.dao.SyncQueueDao
import com.omnitrack.app.data.db.entity.CategoryEntity
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.db.entity.SyncQueueEntity

@Database(
    entities = [MediaItemEntity::class, CategoryEntity::class, SyncQueueEntity::class],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class OmniTrackDatabase : RoomDatabase() {

    abstract fun mediaDao(): MediaDao
    abstract fun categoryDao(): CategoryDao
    abstract fun syncQueueDao(): SyncQueueDao

    companion object {
        @Volatile
        private var INSTANCE: OmniTrackDatabase? = null

        fun getDatabase(context: Context): OmniTrackDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OmniTrackDatabase::class.java,
                    "omnitrack.db"
                )
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
