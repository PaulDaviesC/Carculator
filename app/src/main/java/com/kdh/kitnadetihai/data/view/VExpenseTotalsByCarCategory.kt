package com.kdh.kitnadetihai.data.view

import androidx.room.DatabaseView

@DatabaseView(
    viewName = "v_expense_totals_by_car_category",
    value = """
        SELECT carId, categoryId, currencyCode, SUM(amountMinor) AS totalAmountMinor
        FROM expense
        GROUP BY carId, categoryId, currencyCode
    """
)
data class VExpenseTotalsByCarCategory(
    val carId: String,
    val categoryId: String,
    val currencyCode: String,
    val totalAmountMinor: Long
)
