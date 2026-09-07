package com.borzini.pos.ui.common

import com.borzini.pos.core.BaseUnit
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun BigDecimal.formatQuantity(): String = stripTrailingZeros().toPlainString()

fun unitLabel(baseUnit: String): String = when (baseUnit) {
    BaseUnit.PIECE.name -> "шт"
    BaseUnit.GRAM.name -> "г"
    BaseUnit.MILLILITRE.name -> "мл"
    else -> baseUnit
}

fun formatDateTime(epochMillis: Long, zone: ZoneId): String =
    DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(zone).format(Instant.ofEpochMilli(epochMillis))

fun formatDate(epochMillis: Long, zone: ZoneId): String =
    DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(zone).format(Instant.ofEpochMilli(epochMillis))
