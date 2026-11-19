package com.omnitrack.app.data.db.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Immutable
@Entity(
    tableName = "book_quotes",
    indices = [Index("mediaId")]
)
data class BookQuoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val mediaId: Long,
    val quoteText: String,
    val locationRef: String? = null,
    val personalNote: String? = null,
    val isFavorite: Boolean = false,
    val createdAtMs: Long = System.currentTimeMillis()
)
