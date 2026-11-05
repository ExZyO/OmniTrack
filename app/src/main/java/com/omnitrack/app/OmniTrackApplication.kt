package com.omnitrack.app

import android.app.Application
import com.omnitrack.app.data.db.OmniTrackDatabase
import com.omnitrack.app.data.repository.MediaRepository
import com.omnitrack.app.util.AppLogger

class OmniTrackApplication : Application() {

    val database by lazy { OmniTrackDatabase.getDatabase(this) }
    val repository by lazy { MediaRepository(database.mediaDao(), database.categoryDao()) }

    override fun onCreate() {
        super.onCreate()
        AppLogger.init(this)
        AppLogger.i("Application", "OmniTrack launched successfully.")
    }
}
