package com.kdh.carculator.util

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Locale

data class CsvExpenseRow(
    val head: String?,
    val dateDdMMyyyy: String?,
    val amount: String?,
    val vendor: String?,
    val odometer: String?,
    val notes: String?
)

object CsvUtils {
    private val header = listOf("Head", "Date", "Amount", "Vendor", "Odometer", "Notes")

    fun readExpensesCsv(input: InputStream, charset: Charset = Charsets.UTF_8): List<CsvExpenseRow> {
        val reader = BufferedReader(InputStreamReader(input, charset))
        val rows = mutableListOf<CsvExpenseRow>()
        var line: String?
        var isFirst = true
        while (true) {
            line = reader.readLine() ?: break
            if (line.isBlank()) continue
            val cols = parseCsvLine(line)
            if (isFirst) {
                isFirst = false
                // If header row, skip
                val lowered = cols.map { it.trim().lowercase(Locale.getDefault()) }
                if (lowered.size >= 2 && lowered[0] == "head" && lowered[1].startsWith("date")) continue
            }
            val c = cols + List(maxOf(0, header.size - cols.size)) { "" }
            rows.add(
                CsvExpenseRow(
                    head = c[0].trim().ifEmpty { null },
                    dateDdMMyyyy = c[1].trim().ifEmpty { null },
                    amount = c[2].trim().ifEmpty { null },
                    vendor = c[3].trim().ifEmpty { null },
                    odometer = c[4].trim().ifEmpty { null },
                    notes = c[5].trim().ifEmpty { null }
                )
            )
        }
        return rows
    }

    fun writeExpensesCsv(rows: List<CsvExpenseRow>, output: OutputStream, charset: Charset = Charsets.UTF_8) {
        val w = BufferedWriter(OutputStreamWriter(output, charset))
        w.write(header.joinToString(","))
        w.newLine()
        for (r in rows) {
            val vals = listOf(
                r.head.orEmpty(),
                r.dateDdMMyyyy.orEmpty(),
                r.amount.orEmpty(),
                r.vendor.orEmpty(),
                r.odometer.orEmpty(),
                r.notes.orEmpty()
            )
            w.write(vals.joinToString(",") { escapeCsv(it) })
            w.newLine()
        }
        w.flush()
    }

    fun parseDdMMyyyyToEpochMs(dateStr: String, locale: Locale = Locale.getDefault()): Long? {
        return try {
            val fmt = SimpleDateFormat("dd-MM-yyyy", locale)
            fmt.isLenient = false
            fmt.parse(dateStr)?.time
        } catch (_: Throwable) {
            null
        }
    }

    fun formatEpochMsToDdMMyyyy(epochMs: Long, locale: Locale = Locale.getDefault()): String {
        val fmt = SimpleDateFormat("dd-MM-yyyy", locale)
        return fmt.format(java.util.Date(epochMs))
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ch == ',' && !inQuotes -> {
                    result.add(sb.toString())
                    sb.setLength(0)
                }
                else -> sb.append(ch)
            }
            i++
        }
        result.add(sb.toString())
        return result
    }

    private fun escapeCsv(value: String): String {
        val needsQuotes = value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')
        val v = value.replace("\"", "\"\"")
        return if (needsQuotes) "\"$v\"" else v
    }
}


