package com.kdh.carculator.data.view

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
        SELECT b.carId,
               b.startMeters,
               b.endMeters,
               MAX(b.endMeters - COALESCE(c.initialOdometerMeters, b.startMeters, 0), 0) AS distanceMeters
        FROM bounds b
        LEFT JOIN car c ON c.id = b.carId
    """
)
data class VCarDistance(
    val carId: String,
    val startMeters: Long?,
    val endMeters: Long?,
    val distanceMeters: Long?
)
