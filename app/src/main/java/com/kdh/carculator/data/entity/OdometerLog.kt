package com.kdh.carculator.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "odometer_log",
    foreignKeys = [
        ForeignKey(
            entity = Car::class,
            parentColumns = ["id"],
            childColumns = ["carId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["carId", "readingAtEpochMs"], unique = true),
        Index(value = ["carId", "readingMeters"]) 
    ]
)
data class OdometerLog(
    @PrimaryKey val id: String, // UUID
    val carId: String,
    val readingMeters: Long,
    val readingAtEpochMs: Long,
    val source: String, // USER | IMPORT | SYSTEM (keep as string for flexibility)
    val note: String?,
    val createdAtEpochMs: Long
)
