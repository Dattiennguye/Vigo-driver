package com.vigo.driverlog.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(trip: TripEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(trips: List<TripEntity>): List<Long>

    @Update
    suspend fun update(trip: TripEntity)

    @Query("SELECT * FROM trips WHERE status = 'ACTIVE' ORDER BY startTime DESC LIMIT 1")
    fun observeActive(): Flow<TripEntity?>

    @Query("SELECT * FROM trips WHERE status = 'ACTIVE' ORDER BY startTime DESC LIMIT 1")
    suspend fun getActive(): TripEntity?

    @Query("SELECT * FROM trips WHERE tripId = :id")
    suspend fun getById(id: String): TripEntity?

    /** Chuyến đi có khoảng thời gian giao với [start, end). */
    @Query("SELECT * FROM trips WHERE startTime < :end AND (endTime IS NULL OR endTime >= :start) ORDER BY startTime")
    fun observeOverlapping(start: Long, end: Long): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE startTime < :end AND (endTime IS NULL OR endTime >= :start) ORDER BY startTime")
    suspend fun getOverlapping(start: Long, end: Long): List<TripEntity>

    @Query("SELECT * FROM trips WHERE status != 'ACTIVE' AND endTime IS NOT NULL ORDER BY endTime DESC LIMIT 1")
    fun observeLastFinished(): Flow<TripEntity?>

    @Query("SELECT * FROM trips ORDER BY startTime")
    suspend fun all(): List<TripEntity>

    @Query("SELECT COUNT(*) FROM trips")
    fun observeCount(): Flow<Int>
}

@Dao
interface EventDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(event: EventEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(events: List<EventEntity>): List<Long>

    @Update
    suspend fun update(event: EventEntity)

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun getById(id: String): EventEntity?

    @Query("SELECT * FROM events WHERE id = :id")
    fun observeById(id: String): Flow<EventEntity?>

    @Query("SELECT * FROM events WHERE tripId = :tripId ORDER BY eventTimestamp, createdAt")
    fun observeForTrip(tripId: String): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE tripId = :tripId ORDER BY eventTimestamp, createdAt")
    suspend fun getForTrip(tripId: String): List<EventEntity>

    @Query("SELECT * FROM events WHERE tripId = :tripId AND eventType != 'QUICK_LOG' ORDER BY rowid DESC LIMIT 1")
    suspend fun lastTripEvent(tripId: String): EventEntity?

    @Query("SELECT * FROM events ORDER BY rowid DESC LIMIT 1")
    suspend fun lastEvent(): EventEntity?

    @Query("SELECT * FROM events WHERE eventTimestamp >= :start AND eventTimestamp < :end ORDER BY eventTimestamp, createdAt")
    fun observeBetween(start: Long, end: Long): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE eventTimestamp >= :start AND eventTimestamp < :end ORDER BY eventTimestamp, createdAt")
    suspend fun getBetween(start: Long, end: Long): List<EventEntity>

    @Query("SELECT * FROM events WHERE addressStatus = 'PENDING' AND latitude IS NOT NULL ORDER BY eventTimestamp")
    suspend fun pendingAddress(): List<EventEntity>

    @Query("SELECT COUNT(*) FROM events WHERE addressStatus = 'PENDING'")
    fun observePendingAddressCount(): Flow<Int>

    @Query("UPDATE events SET address = :address, addressStatus = :status, updatedAt = :now WHERE id = :id AND addressStatus = 'PENDING'")
    suspend fun fillAddress(id: String, address: String?, status: String, now: Long): Int

    @Query("SELECT * FROM events ORDER BY eventTimestamp")
    suspend fun all(): List<EventEntity>

    @Query("SELECT COUNT(*) FROM events")
    fun observeCount(): Flow<Int>
}

@Dao
interface EditLogDao {
    @Insert
    suspend fun insert(log: EditLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(logs: List<EditLogEntity>): List<Long>

    @Query("SELECT * FROM edit_logs WHERE eventId = :eventId ORDER BY editedAt")
    fun observeForEvent(eventId: String): Flow<List<EditLogEntity>>

    @Query("SELECT * FROM edit_logs WHERE eventId IN (:ids) ORDER BY editedAt")
    suspend fun forEvents(ids: List<String>): List<EditLogEntity>

    @Query("SELECT * FROM edit_logs ORDER BY editedAt")
    suspend fun all(): List<EditLogEntity>
}
