package com.kdh.carculator.work

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Calendar

class AmortizationCalcTest {

    private fun cal(year: Int, month: Int, day: Int): Calendar =
        Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1) // Calendar months are 0-based
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

    // --- monthsBetween ---

    @Test
    fun monthsBetween_sameMonth() {
        val start = cal(2024, 3, 15)
        val end = cal(2024, 3, 20)
        assertThat(AmortizationCalc.monthsBetween(start, end)).isEqualTo(0)
    }

    @Test
    fun monthsBetween_exactOneMonth() {
        val start = cal(2024, 3, 15)
        val end = cal(2024, 4, 15)
        assertThat(AmortizationCalc.monthsBetween(start, end)).isEqualTo(1)
    }

    @Test
    fun monthsBetween_dayBeforeStartDay_subtractsOne() {
        val start = cal(2024, 3, 15)
        val end = cal(2024, 4, 14)
        assertThat(AmortizationCalc.monthsBetween(start, end)).isEqualTo(0)
    }

    @Test
    fun monthsBetween_crossYear() {
        val start = cal(2023, 11, 1)
        val end = cal(2024, 2, 1)
        assertThat(AmortizationCalc.monthsBetween(start, end)).isEqualTo(3)
    }

    // --- monthIndexToDate ---

    @Test
    fun monthIndexToDate_index1_sameMonth() {
        val start = cal(2024, 3, 15)
        val result = Calendar.getInstance().apply { timeInMillis = AmortizationCalc.monthIndexToDate(start, 1) }
        assertThat(result.get(Calendar.YEAR)).isEqualTo(2024)
        assertThat(result.get(Calendar.MONTH)).isEqualTo(Calendar.MARCH)
        assertThat(result.get(Calendar.DAY_OF_MONTH)).isEqualTo(1)
    }

    @Test
    fun monthIndexToDate_index12() {
        val start = cal(2024, 3, 15)
        val result = Calendar.getInstance().apply { timeInMillis = AmortizationCalc.monthIndexToDate(start, 12) }
        assertThat(result.get(Calendar.YEAR)).isEqualTo(2025)
        assertThat(result.get(Calendar.MONTH)).isEqualTo(Calendar.FEBRUARY)
        assertThat(result.get(Calendar.DAY_OF_MONTH)).isEqualTo(1)
    }

    @Test
    fun monthIndexToDate_crossYear() {
        val start = cal(2024, 11, 10)
        val result = Calendar.getInstance().apply { timeInMillis = AmortizationCalc.monthIndexToDate(start, 3) }
        assertThat(result.get(Calendar.YEAR)).isEqualTo(2025)
        assertThat(result.get(Calendar.MONTH)).isEqualTo(Calendar.JANUARY)
    }

    // --- amortizedAmountPerMonth ---

    @Test
    fun amortizedAmountPerMonth_evenDivision() {
        assertThat(AmortizationCalc.amortizedAmountPerMonth(12000, 12)).isEqualTo(1000)
    }

    @Test
    fun amortizedAmountPerMonth_unevenTruncation() {
        // 10000 / 3 = 3333 (integer division truncates)
        assertThat(AmortizationCalc.amortizedAmountPerMonth(10000, 3)).isEqualTo(3333)
    }

    @Test
    fun amortizedAmountPerMonth_zeroMonths() {
        assertThat(AmortizationCalc.amortizedAmountPerMonth(10000, 0)).isEqualTo(0)
    }
}
