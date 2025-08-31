package com.kdh.carculator.data.view

import androidx.room.DatabaseView

@DatabaseView(
    viewName = "v_current_odometer_by_car",
    value = """
        SELECT carId,
               MAX(readingMeters) AS currentReadingMeters,
               MAX(readingAtEpochMs) AS readingAtEpochMs
        FROM odometer_log
        GROUP BY carId
    """
)
data class VCurrentOdometerByCar(
    val carId: String,
    val currentReadingMeters: Long,
    val readingAtEpochMs: Long
)
