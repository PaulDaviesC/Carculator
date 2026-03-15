package com.kdh.carculator.util

import com.google.common.truth.Truth.assertThat
import com.kdh.carculator.data.DistanceUnit
import org.junit.Test
import java.math.BigDecimal
import java.util.Locale

class FormattersTest {

    // --- formatDateTime ---

    @Test
    fun formatDateTime_knownEpoch() {
        val result = Formatters.formatDateTime(1_700_000_000_000L, Locale.US)
        assertThat(result).contains("2023")
        assertThat(result).contains("November")
        // Don't assert day — timezone-dependent
    }

    @Test
    fun formatDateTime_epochZero() {
        val result = Formatters.formatDateTime(0L, Locale.US)
        assertThat(result).contains("1970")
        // Don't assert month — timezone-dependent (Dec 31 1969 in negative UTC offsets)
    }

    @Test
    fun formatDateTime_frenchLocale() {
        val result = Formatters.formatDateTime(1_700_000_000_000L, Locale.FRENCH)
        assertThat(result).contains("novembre")
    }

    // --- formatDistance ---

    @Test
    fun formatDistance_kmBasic() {
        val result = Formatters.formatDistance(5000, DistanceUnit.KM, Locale.US)
        assertThat(result).isEqualTo("5 km")
    }

    @Test
    fun formatDistance_kmFractional() {
        val result = Formatters.formatDistance(1500, DistanceUnit.KM, Locale.US)
        assertThat(result).isEqualTo("1.5 km")
    }

    @Test
    fun formatDistance_milesBasic() {
        // 1609 meters ≈ 1.00 mile
        val result = Formatters.formatDistance(1609, DistanceUnit.MILE, Locale.US)
        assertThat(result).isEqualTo("1 mi")
    }

    @Test
    fun formatDistance_zeroMeters() {
        val result = Formatters.formatDistance(0, DistanceUnit.KM, Locale.US)
        assertThat(result).isEqualTo("0 km")
    }

    // --- toMetersFromUnit ---

    @Test
    fun toMetersFromUnit_km() {
        assertThat(Formatters.toMetersFromUnit(5.0, DistanceUnit.KM)).isEqualTo(5000L)
    }

    @Test
    fun toMetersFromUnit_miles() {
        // 1 mile = 1609.344 meters
        assertThat(Formatters.toMetersFromUnit(1.0, DistanceUnit.MILE)).isEqualTo(1609L)
    }

    @Test
    fun toMetersFromUnit_halfUpRounding() {
        // 0.5 km = 500 m exactly, no rounding needed
        // 0.0005 km = 0.5 m -> should round to 1 (HALF_UP)
        assertThat(Formatters.toMetersFromUnit(0.0005, DistanceUnit.KM)).isEqualTo(1L)
    }

    // --- majorToMinorCurrency ---

    @Test
    fun majorToMinorCurrency_wholeNumber() {
        assertThat(Formatters.majorToMinorCurrency(BigDecimal("10.00"))).isEqualTo(1000L)
    }

    @Test
    fun majorToMinorCurrency_fractionalRounding() {
        // 10.005 * 100 = 1000.5 -> HALF_UP -> 1001
        assertThat(Formatters.majorToMinorCurrency(BigDecimal("10.005"))).isEqualTo(1001L)
    }

    @Test
    fun majorToMinorCurrency_zero() {
        assertThat(Formatters.majorToMinorCurrency(BigDecimal.ZERO)).isEqualTo(0L)
    }
}
