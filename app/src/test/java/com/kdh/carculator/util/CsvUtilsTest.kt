package com.kdh.carculator.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Locale

class CsvUtilsTest {

    private fun inputOf(text: String) = ByteArrayInputStream(text.toByteArray())

    // --- readExpensesCsv ---

    @Test
    fun readExpensesCsv_headerSkipped() {
        val csv = "Head,Date,Amount,Vendor,Odometer,Notes\nFuel,01-01-2024,100,Shell,5000,fill"
        val rows = CsvUtils.readExpensesCsv(inputOf(csv))
        assertThat(rows).hasSize(1)
        assertThat(rows[0].head).isEqualTo("Fuel")
    }

    @Test
    fun readExpensesCsv_noHeader() {
        val csv = "Fuel,01-01-2024,100,Shell,5000,fill"
        val rows = CsvUtils.readExpensesCsv(inputOf(csv))
        assertThat(rows).hasSize(1)
        assertThat(rows[0].head).isEqualTo("Fuel")
    }

    @Test
    fun readExpensesCsv_quotedFieldsWithCommas() {
        val csv = "Fuel,01-01-2024,100,\"Shell, Inc.\",5000,fill"
        val rows = CsvUtils.readExpensesCsv(inputOf(csv))
        assertThat(rows[0].vendor).isEqualTo("Shell, Inc.")
    }

    @Test
    fun readExpensesCsv_escapedDoubleQuotes() {
        val csv = "Fuel,01-01-2024,100,\"She said \"\"hello\"\"\",5000,fill"
        val rows = CsvUtils.readExpensesCsv(inputOf(csv))
        assertThat(rows[0].vendor).isEqualTo("She said \"hello\"")
    }

    @Test
    fun readExpensesCsv_emptyFieldsToNull() {
        val csv = "Fuel,01-01-2024,100,,,"
        val rows = CsvUtils.readExpensesCsv(inputOf(csv))
        assertThat(rows[0].vendor).isNull()
        assertThat(rows[0].odometer).isNull()
        assertThat(rows[0].notes).isNull()
    }

    @Test
    fun readExpensesCsv_blankLinesSkipped() {
        val csv = "Fuel,01-01-2024,100,Shell,5000,fill\n\n\nService,02-01-2024,200,Garage,6000,oil"
        val rows = CsvUtils.readExpensesCsv(inputOf(csv))
        assertThat(rows).hasSize(2)
    }

    @Test
    fun readExpensesCsv_fewerColumnsPadded() {
        val csv = "Fuel,01-01-2024"
        val rows = CsvUtils.readExpensesCsv(inputOf(csv))
        assertThat(rows).hasSize(1)
        assertThat(rows[0].head).isEqualTo("Fuel")
        assertThat(rows[0].amount).isNull()
        assertThat(rows[0].notes).isNull()
    }

    @Test
    fun readExpensesCsv_emptyInput() {
        val rows = CsvUtils.readExpensesCsv(inputOf(""))
        assertThat(rows).isEmpty()
    }

    // --- writeExpensesCsv ---

    @Test
    fun writeExpensesCsv_basicRow() {
        val row = CsvExpenseRow("Fuel", "01-01-2024", "100", "Shell", "5000", "fill")
        val out = ByteArrayOutputStream()
        CsvUtils.writeExpensesCsv(listOf(row), out)
        val lines = out.toString().trim().lines()
        assertThat(lines[0]).isEqualTo("Head,Date,Amount,Vendor,Odometer,Notes")
        assertThat(lines[1]).isEqualTo("Fuel,01-01-2024,100,Shell,5000,fill")
    }

    @Test
    fun writeExpensesCsv_fieldWithCommaQuoted() {
        val row = CsvExpenseRow("Fuel", "01-01-2024", "100", "Shell, Inc.", "5000", "fill")
        val out = ByteArrayOutputStream()
        CsvUtils.writeExpensesCsv(listOf(row), out)
        val lines = out.toString().trim().lines()
        assertThat(lines[1]).contains("\"Shell, Inc.\"")
    }

    @Test
    fun writeExpensesCsv_fieldWithQuotesEscaped() {
        val row = CsvExpenseRow("Fuel", "01-01-2024", "100", "She said \"hello\"", "5000", "fill")
        val out = ByteArrayOutputStream()
        CsvUtils.writeExpensesCsv(listOf(row), out)
        val lines = out.toString().trim().lines()
        assertThat(lines[1]).contains("\"She said \"\"hello\"\"\"")
    }

    @Test
    fun writeExpensesCsv_emptyRowsHeaderOnly() {
        val out = ByteArrayOutputStream()
        CsvUtils.writeExpensesCsv(emptyList(), out)
        val lines = out.toString().trim().lines()
        assertThat(lines).hasSize(1)
        assertThat(lines[0]).isEqualTo("Head,Date,Amount,Vendor,Odometer,Notes")
    }

    @Test
    fun writeExpensesCsv_nullFieldsToEmpty() {
        val row = CsvExpenseRow("Fuel", null, null, null, null, null)
        val out = ByteArrayOutputStream()
        CsvUtils.writeExpensesCsv(listOf(row), out)
        val lines = out.toString().trim().lines()
        assertThat(lines[1]).isEqualTo("Fuel,,,,,")
    }

    // --- roundtrip ---

    @Test
    fun roundtrip_writeAndRead() {
        val original = listOf(
            CsvExpenseRow("Fuel", "01-01-2024", "100.50", "Shell, Inc.", "5000", "\"quoted\"")
        )
        val out = ByteArrayOutputStream()
        CsvUtils.writeExpensesCsv(original, out)
        val back = CsvUtils.readExpensesCsv(ByteArrayInputStream(out.toByteArray()))
        assertThat(back).hasSize(1)
        assertThat(back[0].head).isEqualTo(original[0].head)
        assertThat(back[0].vendor).isEqualTo(original[0].vendor)
        assertThat(back[0].notes).isEqualTo(original[0].notes)
    }

    // --- parseDdMMyyyyToEpochMs ---

    @Test
    fun parseDdMMyyyyToEpochMs_validDate() {
        val ms = CsvUtils.parseDdMMyyyyToEpochMs("15-03-2026", Locale.US)
        assertThat(ms).isNotNull()
        assertThat(ms).isGreaterThan(0)
    }

    @Test
    fun parseDdMMyyyyToEpochMs_invalidDate() {
        val ms = CsvUtils.parseDdMMyyyyToEpochMs("99-99-9999", Locale.US)
        assertThat(ms).isNull()
    }

    @Test
    fun parseDdMMyyyyToEpochMs_wrongFormat() {
        val ms = CsvUtils.parseDdMMyyyyToEpochMs("2024/01/01", Locale.US)
        assertThat(ms).isNull()
    }

    // --- formatEpochMsToDdMMyyyy ---

    @Test
    fun formatEpochMsToDdMMyyyy_knownEpoch() {
        // Parse a known date, then format it back
        val ms = CsvUtils.parseDdMMyyyyToEpochMs("15-03-2026", Locale.US)!!
        val result = CsvUtils.formatEpochMsToDdMMyyyy(ms, Locale.US)
        assertThat(result).isEqualTo("15-03-2026")
    }
}
