package com.kdh.carculator.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun distanceUnitToString(value: DistanceUnit?): String? = value?.name

    @TypeConverter
    fun stringToDistanceUnit(value: String?): DistanceUnit? = value?.let { DistanceUnit.valueOf(it) }

    @TypeConverter
    fun categoryKindToString(value: CategoryKind?): String? = value?.name

    @TypeConverter
    fun stringToCategoryKind(value: String?): CategoryKind? = value?.let { CategoryKind.valueOf(it) }
}
