package com.omnitrack.app.data.db

import androidx.room.TypeConverter
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.model.TrackingStatus

class Converters {
    @TypeConverter
    fun fromMediaType(type: MediaType): String = type.name

    @TypeConverter
    fun toMediaType(value: String): MediaType = runCatching {
        MediaType.valueOf(value)
    }.getOrDefault(MediaType.ALL)

    @TypeConverter
    fun fromTrackingStatus(status: TrackingStatus): String = status.name

    @TypeConverter
    fun toTrackingStatus(value: String): TrackingStatus = runCatching {
        TrackingStatus.valueOf(value)
    }.getOrDefault(TrackingStatus.IN_PROGRESS)
}
