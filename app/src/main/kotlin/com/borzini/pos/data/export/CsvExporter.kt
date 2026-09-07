package com.borzini.pos.data.export

import com.borzini.pos.core.Money
import com.borzini.pos.data.db.entities.ExpenseEntity
import com.borzini.pos.data.db.entities.InventoryItemEntity
import com.borzini.pos.data.db.entities.SaleEntity
import java.io.File
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * CSV export with a UTF-8 BOM: this is what makes Excel on Windows (the most common way a
 * coffee-shop owner in Russia opens a CSV) show Cyrillic text correctly instead of mojibake,
 * while any other spreadsheet app reads plain UTF-8 either way. Semicolon is used as the
 * delimiter, since Excel's Russian-locale CSV import expects ";" (its default list separator
 * where the decimal separator is a comma), not ",".
 */
object CsvExporter {
    private const val DELIMITER = ";"
    private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    private fun escape(value: String): String {
        val needsQuoting = value.contains(DELIMITER) || value.contains('"') || value.contains('\n')
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuoting) "\"$escaped\"" else escaped
    }

    private fun writeCsv(file: File, header: List<String>, rows: List<List<String>>) {
        file.parentFile?.mkdirs()
        file.outputStream().use { out ->
            out.write(UTF8_BOM)
            OutputStreamWriter(out, StandardCharsets.UTF_8).use { writer ->
                writer.write(header.joinToString(DELIMITER, transform = ::escape))
                writer.write("\r\n")
                for (row in rows) {
                    writer.write(row.joinToString(DELIMITER, transform = ::escape))
                    writer.write("\r\n")
                }
            }
        }
    }

    fun exportSales(file: File, sales: List<SaleEntity>, zone: ZoneId) {
        val fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(zone)
        writeCsv(
            file,
            listOf("Номер чека", "Дата и время", "Способ оплаты", "Сумма, ₽", "Себестоимость, ₽", "Возврат"),
            sales.map { s ->
                listOf(
                    s.receiptNumber.toString(),
                    fmt.format(Instant.ofEpochMilli(s.createdAt)),
                    if (s.paymentMethod == "CASH") "Наличные" else "Безналичные",
                    Money.ofKopecks(s.totalKopecks).rubles.toPlainString(),
                    Money.ofKopecks(s.cogsKopecks).rubles.toPlainString(),
                    when {
                        s.isFullyReturned -> "Полный возврат"
                        s.isPartiallyReturned -> "Частичный возврат"
                        else -> ""
                    },
                )
            },
        )
    }

    fun exportInventory(file: File, items: List<InventoryItemEntity>) {
        writeCsv(
            file,
            listOf("Название", "Категория", "Ед. изм.", "Остаток", "Средняя себестоимость за ед., ₽", "Стоимость остатка, ₽", "Мин. остаток", "Поставщик"),
            items.map { i ->
                val unit = when (i.baseUnit) {
                    "PIECE" -> "шт"; "GRAM" -> "г"; "MILLILITRE" -> "мл"; else -> i.baseUnit
                }
                listOf(
                    i.name, i.category, unit, i.onHandAmount.toPlainString(), i.avgUnitCostRubles.toPlainString(),
                    i.avgUnitCostRubles.multiply(i.onHandAmount).toPlainString(), i.minAllowedAmount.toPlainString(),
                    i.supplier ?: "",
                )
            },
        )
    }

    fun exportExpenses(file: File, expenses: List<ExpenseEntity>, zone: ZoneId) {
        val fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(zone)
        writeCsv(
            file,
            listOf("Дата", "Категория", "Сумма, ₽", "Комментарий"),
            expenses.map { e ->
                listOf(fmt.format(Instant.ofEpochMilli(e.date)), e.category, Money.ofKopecks(e.amountKopecks).rubles.toPlainString(), e.comment ?: "")
            },
        )
    }
}
