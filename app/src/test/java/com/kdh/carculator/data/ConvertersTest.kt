package com.kdh.carculator.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ConvertersTest {
    private val c = Converters()

    @Test
    fun distanceUnit_roundtrip() {
        for (u in DistanceUnit.values()) {
            val s = c.distanceUnitToString(u)
            val back = c.stringToDistanceUnit(s)
            assertThat(back).isEqualTo(u)
        }
        assertThat(c.stringToDistanceUnit(null)).isNull()
    }

    @Test
    fun categoryKind_roundtrip() {
        for (k in CategoryKind.values()) {
            val s = c.categoryKindToString(k)
            val back = c.stringToCategoryKind(s)
            assertThat(back).isEqualTo(k)
        }
        assertThat(c.stringToCategoryKind(null)).isNull()
    }
}
