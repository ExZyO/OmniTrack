package com.omnitrack.app.ui.screens.detail

import com.omnitrack.app.data.db.entity.MediaItemEntity

/**
 * Thread-safe singleton holder storing the ordered sequence of media item IDs
 * from the active library tab or discovery shelf, and the active tapped item for 0ms transition.
 *
 * Prevents horizontal swipe navigation in DetailScreen from jumping to random
 * entries when background database updates change entity timestamps.
 */
object ActiveSiblingHolder {
    @Volatile
    var activeItem: MediaItemEntity? = null

    @Volatile
    var activeSiblingIds: List<Long> = emptyList()

    fun setSiblings(ids: List<Long>) {
        activeSiblingIds = ids
    }

    fun clear() {
        activeItem = null
        activeSiblingIds = emptyList()
    }
}
