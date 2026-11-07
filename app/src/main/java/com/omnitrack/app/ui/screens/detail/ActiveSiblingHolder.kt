package com.omnitrack.app.ui.screens.detail

/**
 * Thread-safe singleton holder storing the ordered sequence of media item IDs
 * from the active library tab or discovery shelf.
 *
 * Prevents horizontal swipe navigation in DetailScreen from jumping to random
 * entries when background database updates change entity timestamps.
 */
object ActiveSiblingHolder {
    @Volatile
    var activeSiblingIds: List<Long> = emptyList()

    fun setSiblings(ids: List<Long>) {
        activeSiblingIds = ids
    }

    fun clear() {
        activeSiblingIds = emptyList()
    }
}
