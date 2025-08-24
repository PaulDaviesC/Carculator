package com.kdh.kitnadetihai.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "car",
    indices = [
        Index(value = ["registrationNumber"], unique = true)
    ]
)
data class Car(
    @PrimaryKey val id: String, // UUID
    val registrationNumber: String,
    val name: String?,
    val createdAtEpochMs: Long,
    val archivedAtEpochMs: Long?,
    val initialOdometerMeters: Long? = null,
    val acquisitionCostMinor: Long? = null,
    val acquisitionDateEpochMs: Long? = null,
    val remainingLifeMonths: Int? = null
)
