package com.kdh.carculator.data.view

import androidx.room.DatabaseView

@DatabaseView(
    viewName = "v_cost_per_unit_by_car",
    value = """
        WITH settings AS (SELECT distanceUnit FROM app_settings WHERE id = 1),
             distance AS (SELECT * FROM v_car_distance),
             totals   AS (SELECT * FROM v_expense_totals_by_car)
        SELECT
          t.carId AS carId,
          t.currencyCode AS currencyCode,
          CASE s.distanceUnit WHEN 'KM' THEN 'KM' ELSE 'MILE' END AS unit,
          CASE s.distanceUnit
            WHEN 'KM'  THEN (t.totalAmountMinor * 1.0) / NULLIF(d.distanceMeters, 0) * 1000.0
            ELSE            (t.totalAmountMinor * 1.0) / NULLIF(d.distanceMeters, 0) * 1609.344
          END AS costPerUnitMinor
        FROM totals t
        JOIN distance d ON d.carId = t.carId
        CROSS JOIN settings s
    """
)
data class VCostPerUnitByCar(
    val carId: String,
    val currencyCode: String,
    val unit: String,
    val costPerUnitMinor: Double
)
