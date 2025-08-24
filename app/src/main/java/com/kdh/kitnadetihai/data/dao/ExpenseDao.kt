package com.kdh.kitnadetihai.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kdh.kitnadetihai.data.entity.Expense
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(expense: Expense)

    @Update
    suspend fun update(expense: Expense)

    @Delete
    suspend fun delete(expense: Expense)

    @Query("SELECT * FROM expense WHERE carId = :carId ORDER BY occurredAtEpochMs DESC")
    fun observeForCar(carId: String): Flow<List<Expense>>

    @Query("SELECT * FROM expense WHERE carId = :carId AND categoryId = :categoryId ORDER BY occurredAtEpochMs DESC")
    fun observeForCarAndCategory(carId: String, categoryId: String): Flow<List<Expense>>

    // Aggregates via views (created in DB callback)
    @Query("SELECT currencyCode, totalAmountMinor FROM v_expense_totals_by_car WHERE carId = :carId")
    fun observeTotalsByCar(carId: String): Flow<List<CarTotalRow>>

    @Query("SELECT categoryId, currencyCode, totalAmountMinor FROM v_expense_totals_by_car_category WHERE carId = :carId")
    fun observeTotalsByCarCategory(carId: String): Flow<List<CategoryTotalRow>>

    @Query("SELECT currencyCode, unit, costPerUnitMinor FROM v_cost_per_unit_by_car WHERE carId = :carId")
    fun observeCostPerUnitByCar(carId: String): Flow<List<CarCostPerUnitRow>>

    @Query("SELECT categoryId, currencyCode, unit, costPerUnitMinor FROM v_cost_per_unit_by_car_category WHERE carId = :carId")
    fun observeCostPerUnitByCarCategory(carId: String): Flow<List<CategoryCostPerUnitRow>>

    // Keyset pagination: initial page and subsequent pages using (occurredAtEpochMs, id) as cursor
    @Query("""
        SELECT * FROM expense
        WHERE carId = :carId
        ORDER BY occurredAtEpochMs DESC, id DESC
        LIMIT :limit
    """)
    suspend fun pageInitialByCar(carId: String, limit: Int): List<Expense>

    @Query("""
        SELECT * FROM expense
        WHERE carId = :carId
          AND (
            occurredAtEpochMs < :beforeTs OR (occurredAtEpochMs = :beforeTs AND id < :beforeId)
          )
        ORDER BY occurredAtEpochMs DESC, id DESC
        LIMIT :limit
    """)
    suspend fun pageAfterByCar(carId: String, beforeTs: Long, beforeId: String, limit: Int): List<Expense>
}

// Lightweight DTOs for view rows
data class CarTotalRow(
    val currencyCode: String,
    val totalAmountMinor: Long
)

data class CategoryTotalRow(
    val categoryId: String,
    val currencyCode: String,
    val totalAmountMinor: Long
)

data class CarCostPerUnitRow(
    val currencyCode: String,
    val unit: String,
    val costPerUnitMinor: Double
)

data class CategoryCostPerUnitRow(
    val categoryId: String,
    val currencyCode: String,
    val unit: String,
    val costPerUnitMinor: Double
)
