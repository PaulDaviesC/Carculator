package com.kdh.carculator.util

import com.kdh.carculator.data.DistanceUnit
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Formatters {
    fun formatDateTime(epochMs: Long, locale: Locale = Locale.getDefault()): String {
        val fmt = SimpleDateFormat("d MMMM yyyy h:mm a", locale)
        return fmt.format(Date(epochMs))
    }

    fun formatDistance(meters: Long, unit: DistanceUnit, locale: Locale = Locale.getDefault()): String {
        val nf = NumberFormat.getNumberInstance(locale).apply {
            maximumFractionDigits = 2
            minimumFractionDigits = 0
        }
        return when (unit) {
            DistanceUnit.KM -> {
                val km = BigDecimal(meters).divide(BigDecimal(1000), 2, RoundingMode.HALF_UP)
                "${nf.format(km)} km"
            }
            DistanceUnit.MILE -> {
                val miles = BigDecimal(meters).divide(BigDecimal("1609.344"), 2, RoundingMode.HALF_UP)
                "${nf.format(miles)} mi"
            }
        }
    }

    fun toMetersFromUnit(value: Double, unit: DistanceUnit): Long {
        return when (unit) {
            DistanceUnit.KM -> BigDecimal(value.toString()).multiply(BigDecimal(1000)).setScale(0, RoundingMode.HALF_UP).toLong()
            DistanceUnit.MILE -> BigDecimal(value.toString()).multiply(BigDecimal("1609.344")).setScale(0, RoundingMode.HALF_UP).toLong()
        }
    }

    fun majorToMinorCurrency(major: BigDecimal): Long {
        return major.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).toLong()
    }
}
