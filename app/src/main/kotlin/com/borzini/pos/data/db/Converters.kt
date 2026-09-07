package com.borzini.pos.data.db

import androidx.room.TypeConverter
import java.math.BigDecimal

/**
 * Every money amount is stored as a Long (kopecks) - see [com.borzini.pos.core.Money] - and every
 * fractional ingredient quantity is stored as the plain string form of a [BigDecimal], never as
 * Double/Float, so nothing here can introduce binary floating point rounding into persisted data.
 */
class Converters {
    @TypeConverter
    fun bigDecimalToString(value: BigDecimal?): String? = value?.toPlainString()

    @TypeConverter
    fun stringToBigDecimal(value: String?): BigDecimal? = value?.let { BigDecimal(it) }
}
