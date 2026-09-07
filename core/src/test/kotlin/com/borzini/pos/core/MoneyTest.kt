package com.borzini.pos.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class MoneyTest {

    @Test
    fun `addition is exact across many small amounts`() {
        // Classic binary-float trap: 0.1 + 0.2 != 0.3 in Double. Money must not repeat that.
        var total = Money.ZERO
        repeat(1000) { total += Money.fromRubles(BigDecimal("0.10")) }
        assertEquals(Money.fromRubles(BigDecimal("100.00")), total)
    }

    @Test
    fun `formatting groups thousands and uses comma decimal separator`() {
        // Plain ASCII space as thousands/pre-symbol separator (kept simple and unambiguous).
        assertEquals("1 234,56 ₽", Money.fromRubles(BigDecimal("1234.56")).format())
        assertEquals("350,00 ₽", Money.fromRubles(BigDecimal("350")).format())
        assertEquals("0,00 ₽", Money.ZERO.format())
    }

    @Test
    fun `times with fractional quantity rounds half up to nearest kopeck`() {
        // 3 kopecks per unit * 0.5 = 1.5 kopecks -> rounds to 2
        val unit = Money.fromRubles(BigDecimal("0.03"))
        assertEquals(Money.fromRubles(BigDecimal("0.02")), unit.times(BigDecimal("0.5")))
    }

    @Test
    fun `sum of empty list is zero`() {
        assertEquals(Money.ZERO, Money.sum(emptyList()))
    }
}
