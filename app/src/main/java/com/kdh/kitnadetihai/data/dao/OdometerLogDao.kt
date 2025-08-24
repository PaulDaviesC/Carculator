package com.kdh.kitnadetihai.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kdh.kitnadetihai.data.entity.OdometerLog
import kotlinx.coroutines.flow.Flow

@Dao
interface OdometerLogDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(log: OdometerLog)

    @Update
    suspend fun update(log: OdometerLog)

    @Delete
    suspend fun delete(log: OdometerLog)

    @Query("SELECT * FROM odometer_log WHERE carId = :carId ORDER BY readingAtEpochMs DESC")
    fun observeForCar(carId: String): Flow<List<OdometerLog>>

    @Query("SELECT * FROM odometer_log WHERE carId = :carId ORDER BY readingAtEpochMs DESC LIMIT 1")
    suspend fun getLatestForCar(carId: String): OdometerLog?

    @Query("""
        SELECT MAX(readingMeters) - MIN(readingMeters)
        FROM odometer_log
        WHERE carId = :carId
    """)
    suspend fun getTotalDistanceMeters(carId: String): Long?
}
