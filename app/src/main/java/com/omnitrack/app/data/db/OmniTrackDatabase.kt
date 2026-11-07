package com.omnitrack.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.omnitrack.app.data.db.dao.CategoryDao
import com.omnitrack.app.data.db.dao.EpisodeProgressDao
import com.omnitrack.app.data.db.dao.MediaDao
import com.omnitrack.app.data.db.dao.SyncQueueDao
import com.omnitrack.app.data.db.entity.CategoryEntity
import com.omnitrack.app.data.db.entity.EpisodeProgressEntity
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.db.entity.SyncQueueEntity

@Database(
    entities = [MediaItemEntity::class, CategoryEntity::class, SyncQueueEntity::class, EpisodeProgressEntity::class],
    version = 10,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class OmniTrackDatabase : RoomDatabase() {

    abstract fun mediaDao(): MediaDao
    abstract fun categoryDao(): CategoryDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun episodeProgressDao(): EpisodeProgressDao

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

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE media_items ADD COLUMN traktScore INTEGER")
                db.execSQL("ALTER TABLE media_items ADD COLUMN audienceScore INTEGER")
                db.execSQL("ALTER TABLE media_items ADD COLUMN releaseYear TEXT")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS episode_progress (
                        mediaId INTEGER NOT NULL,
                        seasonNumber INTEGER NOT NULL,
                        episodeNumber INTEGER NOT NULL,
                        isWatched INTEGER NOT NULL DEFAULT 0,
                        watchedAtMs INTEGER,
                        PRIMARY KEY (mediaId, seasonNumber, episodeNumber)
                    )
                """.trimIndent())
            }
        }

        fun getDatabase(context: Context): OmniTrackDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OmniTrackDatabase::class.java,
                    "omnitrack.db"
                )
                    .addMigrations(MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
