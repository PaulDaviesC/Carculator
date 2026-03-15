package com.kdh.carculator.work

import java.util.Calendar

internal object AmortizationCalc {

    fun monthsBetween(start: Calendar, end: Calendar): Int {
        val y = end.get(Calendar.YEAR) - start.get(Calendar.YEAR)
        val m = end.get(Calendar.MONTH) - start.get(Calendar.MONTH)
        var total = y * 12 + m
        if (end.get(Calendar.DAY_OF_MONTH) < start.get(Calendar.DAY_OF_MONTH)) total--
        return total
    }

    fun monthIndexToDate(start: Calendar, monthIndex: Int): Long {
        val c = start.clone() as Calendar
        c.set(Calendar.DAY_OF_MONTH, 1)
        c.add(Calendar.MONTH, monthIndex - 1)
        return c.timeInMillis
    }

    fun amortizedAmountPerMonth(totalMinor: Long, totalMonths: Int): Long {
        if (totalMonths <= 0) return 0L
        return totalMinor / totalMonths
    }
}
