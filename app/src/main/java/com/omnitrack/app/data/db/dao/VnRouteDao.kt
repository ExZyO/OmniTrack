package com.omnitrack.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.omnitrack.app.data.db.entity.VnRouteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VnRouteDao {
    @Query("SELECT * FROM vn_routes WHERE mediaId = :mediaId ORDER BY sortOrder ASC, id ASC")
    fun getRoutesForMedia(mediaId: Long): Flow<List<VnRouteEntity>>

    @Query("SELECT * FROM vn_routes WHERE mediaId = :mediaId ORDER BY sortOrder ASC, id ASC")
    suspend fun getRoutesForMediaOnce(mediaId: Long): List<VnRouteEntity>

    @Query("SELECT * FROM vn_routes")
    suspend fun getAllRoutesOnce(): List<VnRouteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoute(route: VnRouteEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutes(routes: List<VnRouteEntity>)

    @Update
    suspend fun updateRoute(route: VnRouteEntity)

    @Query("DELETE FROM vn_routes WHERE id = :id")
    suspend fun deleteRouteById(id: Long)

    @Query("DELETE FROM vn_routes WHERE mediaId = :mediaId")
    suspend fun deleteRoutesForMedia(mediaId: Long)

    @Query("DELETE FROM vn_routes")
    suspend fun deleteAllRoutes()

    @Query("SELECT COUNT(*) FROM vn_routes WHERE mediaId NOT IN (SELECT id FROM media_items)")
    suspend fun countOrphaned(): Int

    @Query("DELETE FROM vn_routes WHERE mediaId NOT IN (SELECT id FROM media_items)")
    suspend fun deleteOrphaned(): Int
}
