package com.kdh.kitnadetihai.data.view

import androidx.room.DatabaseView

@DatabaseView(
    viewName = "v_car_distance",
    value = """
        WITH bounds AS (
          SELECT carId,
                 MIN(readingMeters) AS startMeters,
                 MAX(readingMeters) AS endMeters
          FROM odometer_log
          GROUP BY carId
        )
        SELECT carId,
               startMeters,
               endMeters,
               MAX(endMeters - startMeters, 0) AS distanceMeters
        FROM bounds
    """
)
data class VCarDistance(
    val carId: String,
    val startMeters: Long?,
    val endMeters: Long?,
    val distanceMeters: Long?
)
