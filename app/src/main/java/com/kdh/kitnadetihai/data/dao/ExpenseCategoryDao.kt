package com.kdh.kitnadetihai.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kdh.kitnadetihai.data.entity.ExpenseCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseCategoryDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(category: ExpenseCategory)

    @Update
    suspend fun update(category: ExpenseCategory)

    @Query("SELECT * FROM expense_category WHERE isDeleted = 0 ORDER BY sortOrder, name")
    fun observeActive(): Flow<List<ExpenseCategory>>

    @Query("SELECT * FROM expense_category WHERE id = :id")
    suspend fun getById(id: String): ExpenseCategory?
}
