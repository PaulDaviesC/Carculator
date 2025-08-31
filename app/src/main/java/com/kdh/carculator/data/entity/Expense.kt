package com.kdh.carculator.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "expense",
    foreignKeys = [
        ForeignKey(
            entity = Car::class,
            parentColumns = ["id"],
            childColumns = ["carId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ExpenseCategory::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["carId", "occurredAtEpochMs"]),
        Index(value = ["categoryId"]),
        Index(value = ["carId", "categoryId", "occurredAtEpochMs"]) 
    ]
)
data class Expense(
    @PrimaryKey val id: String,
    val carId: String,
    val categoryId: String,
    val amountMinor: Long,
    val currencyCode: String,
    val occurredAtEpochMs: Long,
    val odometerAtMeters: Long?,
    val vendor: String?,
    val notes: String?,
    val attachmentUri: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)
