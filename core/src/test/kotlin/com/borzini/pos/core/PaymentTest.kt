package com.borzini.pos.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class PaymentTest {

    @Test
    fun `scenario 4 - order 350 with 1000 received gives 650 change`() {
        val total = Money.fromRubles(BigDecimal("350"))
        val received = Money.fromRubles(BigDecimal("1000"))
        val result = CashPaymentCalculator.evaluate(total, received)
        assertTrue(result is CashPaymentResult.Ok)
        assertEquals(Money.fromRubles(BigDecimal("650")), (result as CashPaymentResult.Ok).change)
    }

    @Test
    fun `insufficient cash cannot complete the sale`() {
        val total = Money.fromRubles(BigDecimal("350"))
        val received = Money.fromRubles(BigDecimal("300"))
        assertFalse(CashPaymentCalculator.canComplete(total, received))
        val result = CashPaymentCalculator.evaluate(total, received)
        assertTrue(result is CashPaymentResult.InsufficientAmount)
        assertEquals(Money.fromRubles(BigDecimal("50")), (result as CashPaymentResult.InsufficientAmount).shortfall)
    }

    @Test
    fun `exact amount gives zero change and completes the sale`() {
        val total = Money.fromRubles(BigDecimal("350"))
        assertTrue(CashPaymentCalculator.canComplete(total, total))
        assertEquals(
            Money.ZERO,
            (CashPaymentCalculator.evaluate(total, total) as CashPaymentResult.Ok).change,
        )
    }
}
