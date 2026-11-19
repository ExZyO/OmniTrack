package com.omnitrack.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.omnitrack.app.data.db.dao.BookEntryDao
import com.omnitrack.app.data.db.dao.BookQuoteDao
import com.omnitrack.app.data.db.dao.CategoryDao
import com.omnitrack.app.data.db.dao.EpisodeProgressDao
import com.omnitrack.app.data.db.dao.GameEntryDao
import com.omnitrack.app.data.db.dao.MediaDao
import com.omnitrack.app.data.db.dao.SyncQueueDao
import com.omnitrack.app.data.db.dao.VnRouteDao
import com.omnitrack.app.data.db.entity.BookEntryEntity
import com.omnitrack.app.data.db.entity.BookQuoteEntity
import com.omnitrack.app.data.db.entity.CategoryEntity
import com.omnitrack.app.data.db.entity.EpisodeProgressEntity
import com.omnitrack.app.data.db.entity.GameEntryEntity
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.db.entity.SyncQueueEntity
import com.omnitrack.app.data.db.entity.VnRouteEntity

@Database(
    entities = [
        MediaItemEntity::class,
        CategoryEntity::class,
        SyncQueueEntity::class,
        EpisodeProgressEntity::class,
        VnRouteEntity::class,
        GameEntryEntity::class,
        BookEntryEntity::class,
        BookQuoteEntity::class
    ],
    version = 16,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class OmniTrackDatabase : RoomDatabase() {

    abstract fun mediaDao(): MediaDao
    abstract fun categoryDao(): CategoryDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun episodeProgressDao(): EpisodeProgressDao
    abstract fun vnRouteDao(): VnRouteDao
    abstract fun gameEntryDao(): GameEntryDao
    abstract fun bookEntryDao(): BookEntryDao
    abstract fun bookQuoteDao(): BookQuoteDao

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

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE media_items ADD COLUMN imdbId TEXT")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE media_items ADD COLUMN originCountry TEXT")
                db.execSQL("ALTER TABLE media_items ADD COLUMN mangadexId TEXT")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE episode_progress ADD COLUMN userRating REAL")
                db.execSQL("ALTER TABLE episode_progress ADD COLUMN userNote TEXT")
                db.execSQL("ALTER TABLE episode_progress ADD COLUMN isBookmarked INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE episode_progress ADD COLUMN customLabel TEXT")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS vn_routes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        mediaId INTEGER NOT NULL,
                        routeName TEXT NOT NULL,
                        routeType TEXT NOT NULL DEFAULT 'HEROINE',
                        characterId TEXT,
                        characterImageUrl TEXT,
                        isCompleted INTEGER NOT NULL DEFAULT 0,
                        completedAtMs INTEGER,
                        userRating REAL,
                        userNote TEXT,
                        isBookmarked INTEGER NOT NULL DEFAULT 0,
                        isSpoiler INTEGER NOT NULL DEFAULT 0,
                        sortOrder INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_vn_routes_mediaId ON vn_routes(mediaId)")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE media_items ADD COLUMN steamAppId INTEGER")
                db.execSQL("ALTER TABLE media_items ADD COLUMN gameCompletionTier TEXT")
                db.execSQL("UPDATE media_items SET steamAppId = igdbId, igdbId = NULL WHERE mediaType = 'GAME' AND igdbId IS NOT NULL AND steamAppId IS NULL")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS game_entries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        mediaId INTEGER NOT NULL,
                        entryName TEXT NOT NULL,
                        entryType TEXT NOT NULL DEFAULT 'PLAYTHROUGH',
                        igdbExpansionId INTEGER,
                        coverUrl TEXT,
                        platform TEXT,
                        completionTier TEXT NOT NULL DEFAULT 'NONE',
                        difficulty TEXT,
                        hoursSpent REAL,
                        startedAtMs INTEGER,
                        completedAtMs INTEGER,
                        isCompleted INTEGER NOT NULL DEFAULT 0,
                        userRating REAL,
                        userNote TEXT,
                        isBookmarked INTEGER NOT NULL DEFAULT 0,
                        sortOrder INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_game_entries_mediaId ON game_entries(mediaId)")
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE media_items ADD COLUMN openLibraryId TEXT")
                db.execSQL("ALTER TABLE media_items ADD COLUMN isbn TEXT")
                db.execSQL("ALTER TABLE media_items ADD COLUMN bookAuthor TEXT")
                db.execSQL("ALTER TABLE media_items ADD COLUMN bookFormat TEXT")
                db.execSQL("ALTER TABLE media_items ADD COLUMN bookTrackingMode TEXT")
                db.execSQL("ALTER TABLE media_items ADD COLUMN bookAudioMinutes INTEGER")
                db.execSQL("ALTER TABLE media_items ADD COLUMN bookTotalAudioMinutes INTEGER")
                db.execSQL("ALTER TABLE media_items ADD COLUMN bookTranslator TEXT")
                db.execSQL("ALTER TABLE media_items ADD COLUMN bookEditionLabel TEXT")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS book_entries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        mediaId INTEGER NOT NULL,
                        entryName TEXT NOT NULL,
                        entryType TEXT NOT NULL DEFAULT 'BOOK_PART',
                        pagesOrLines INTEGER,
                        translator TEXT,
                        editionLabel TEXT,
                        isCompleted INTEGER NOT NULL DEFAULT 0,
                        completedAtMs INTEGER,
                        userRating REAL,
                        userNote TEXT,
                        isBookmarked INTEGER NOT NULL DEFAULT 0,
                        sortOrder INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_book_entries_mediaId ON book_entries(mediaId)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS book_quotes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        mediaId INTEGER NOT NULL,
                        quoteText TEXT NOT NULL,
                        locationRef TEXT,
                        personalNote TEXT,
                        isFavorite INTEGER NOT NULL DEFAULT 0,
                        createdAtMs INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_book_quotes_mediaId ON book_quotes(mediaId)")
            }
        }

        fun getDatabase(context: Context): OmniTrackDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OmniTrackDatabase::class.java,
                    "omnitrack.db"
                )
                    .addMigrations(MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16)
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            try {
                                db.query("PRAGMA foreign_keys = ON").close()
                                db.query("PRAGMA journal_mode = WAL").close()
                                db.query("PRAGMA synchronous = NORMAL").close()
                                db.query("PRAGMA cache_size = -32000").close()
                                db.query("PRAGMA temp_store = MEMORY").close()
                                db.query("PRAGMA mmap_size = 268435456").close()
                            } catch (_: Exception) {}
                        }
                    })
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
