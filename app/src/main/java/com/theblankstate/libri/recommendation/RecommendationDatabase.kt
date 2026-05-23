package com.theblankstate.libri.recommendation

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(tableName = "recommendation_events")
data class RecommendationEventEntity(
    @PrimaryKey val eventId: String,
    val type: String,
    val itemId: String?,
    val source: String,
    val title: String?,
    val authorsJson: String,
    val subjectsJson: String,
    val languagesJson: String,
    val value: Double?,
    val query: String?,
    val timestamp: Long
)

@Entity(tableName = "recommendation_items")
data class RecommendationItemEntity(
    @PrimaryKey val itemId: String,
    val source: String,
    val sourceKey: String?,
    val title: String,
    val authorsJson: String,
    val subjectsJson: String,
    val languagesJson: String,
    val publishYear: Int?,
    val coverUrl: String?,
    val availability: String?,
    val popularity: Double?,
    val bookJson: String,
    val lastSeenAt: Long
)

@Entity(tableName = "recommendation_snapshots")
data class RecommendationSnapshotEntity(
    @PrimaryKey val snapshotKey: String,
    val sectionsJson: String,
    val updatedAt: Long
)

@Entity(tableName = "recommendation_api_cache")
data class RecommendationApiCacheEntity(
    @PrimaryKey val cacheKey: String,
    val responseJson: String,
    val updatedAt: Long,
    val expiresAt: Long
)

@Dao
interface RecommendationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEvent(event: RecommendationEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEvents(events: List<RecommendationEventEntity>)

    @Query("SELECT * FROM recommendation_events ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentEvents(limit: Int): List<RecommendationEventEntity>

    @Query("DELETE FROM recommendation_events WHERE eventId NOT IN (SELECT eventId FROM recommendation_events ORDER BY timestamp DESC LIMIT :maxCount)")
    suspend fun pruneEvents(maxCount: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItems(items: List<RecommendationItemEntity>)

    @Query("SELECT * FROM recommendation_items ORDER BY lastSeenAt DESC LIMIT :limit")
    suspend fun getRecentItems(limit: Int): List<RecommendationItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSnapshot(snapshot: RecommendationSnapshotEntity)

    @Query("SELECT * FROM recommendation_snapshots WHERE snapshotKey = :snapshotKey LIMIT 1")
    suspend fun getSnapshot(snapshotKey: String): RecommendationSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertApiCache(cache: RecommendationApiCacheEntity)

    @Query("SELECT * FROM recommendation_api_cache WHERE cacheKey = :cacheKey AND expiresAt > :now LIMIT 1")
    suspend fun getApiCache(cacheKey: String, now: Long = System.currentTimeMillis()): RecommendationApiCacheEntity?

    @Query("DELETE FROM recommendation_api_cache WHERE expiresAt <= :now")
    suspend fun pruneExpiredApiCache(now: Long = System.currentTimeMillis())

    @Query("DELETE FROM recommendation_events")
    suspend fun clearEvents()

    @Query("DELETE FROM recommendation_items")
    suspend fun clearItems()

    @Query("DELETE FROM recommendation_snapshots")
    suspend fun clearSnapshots()

    @Query("DELETE FROM recommendation_api_cache")
    suspend fun clearApiCache()
}

@Database(
    entities = [
        RecommendationEventEntity::class,
        RecommendationItemEntity::class,
        RecommendationSnapshotEntity::class,
        RecommendationApiCacheEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class RecommendationDatabase : RoomDatabase() {
    abstract fun recommendationDao(): RecommendationDao

    companion object {
        @Volatile
        private var INSTANCE: RecommendationDatabase? = null

        fun getInstance(context: Context): RecommendationDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RecommendationDatabase::class.java,
                    "libri_recommendations.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
