package com.kdh.carculator.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kdh.carculator.data.DistanceUnit

@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey val id: Int = 1,
    val distanceUnit: DistanceUnit,
    val currencyCode: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val schemaVersion: Int
)
