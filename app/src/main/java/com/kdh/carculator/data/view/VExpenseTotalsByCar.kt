package com.kdh.carculator.data.view

import androidx.room.DatabaseView

@DatabaseView(
    viewName = "v_expense_totals_by_car",
    value = """
        SELECT carId, currencyCode, SUM(amountMinor) AS totalAmountMinor
        FROM expense
        GROUP BY carId, currencyCode
    """
)
data class VExpenseTotalsByCar(
    val carId: String,
    val currencyCode: String,
    val totalAmountMinor: Long
)
