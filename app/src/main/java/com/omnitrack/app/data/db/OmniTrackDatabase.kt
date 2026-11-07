package com.omnitrack.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.omnitrack.app.data.db.dao.CategoryDao
import com.omnitrack.app.data.db.dao.MediaDao
import com.omnitrack.app.data.db.dao.SyncQueueDao
import com.omnitrack.app.data.db.entity.CategoryEntity
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.db.entity.SyncQueueEntity

@Database(
    entities = [MediaItemEntity::class, CategoryEntity::class, SyncQueueEntity::class],
    version = 8,
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

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE media_items ADD COLUMN playtimeHours REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE media_items ADD COLUMN steamAchievementsUnlocked INTEGER")
                db.execSQL("ALTER TABLE media_items ADD COLUMN steamAchievementsTotal INTEGER")
                db.execSQL("ALTER TABLE media_items ADD COLUMN platforms TEXT")
                db.execSQL("ALTER TABLE media_items ADD COLUMN trackerProgressOverrides TEXT")
            }
        }

        fun getDatabase(context: Context): OmniTrackDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OmniTrackDatabase::class.java,
                    "omnitrack.db"
                )
                    .addMigrations(MIGRATION_7_8)
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
