package com.omnitrack.app.ui.screens.detail

import com.omnitrack.app.data.db.entity.MediaItemEntity

/**
 * Singleton holder for passing non-persisted media entities into DetailScreen for ephemeral preview.
 */
object EphemeralPreviewHolder {
    @Volatile
    var previewItem: MediaItemEntity? = null
}
