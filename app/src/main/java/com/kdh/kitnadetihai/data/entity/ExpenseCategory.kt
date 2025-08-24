package com.kdh.kitnadetihai.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kdh.kitnadetihai.data.CategoryKind

@Entity(tableName = "expense_category")
data class ExpenseCategory(
    @PrimaryKey val id: String, // UUID or stable SYSTEM key
    val name: String,
    val kind: CategoryKind,
    val isDeleted: Boolean = false,
    val sortOrder: Int = 0,
    val createdAtEpochMs: Long
)
