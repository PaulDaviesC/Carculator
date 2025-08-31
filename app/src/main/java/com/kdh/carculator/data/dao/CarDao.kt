package com.kdh.carculator.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kdh.carculator.data.entity.Car
import kotlinx.coroutines.flow.Flow

@Dao
interface CarDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(car: Car)

    @Update
    suspend fun update(car: Car)

    @Delete
    suspend fun delete(car: Car)

    @Query("SELECT * FROM car ORDER BY createdAtEpochMs DESC")
    fun observeAll(): Flow<List<Car>>

    @Query("SELECT * FROM car ORDER BY createdAtEpochMs DESC")
    suspend fun listAll(): List<Car>

    @Query("SELECT * FROM car WHERE id = :id")
    suspend fun getById(id: String): Car?

    @Query("SELECT * FROM car WHERE registrationNumber = :reg COLLATE NOCASE")
    suspend fun getByRegistration(reg: String): Car?
}
