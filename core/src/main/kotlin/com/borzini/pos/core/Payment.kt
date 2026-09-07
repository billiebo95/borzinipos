package com.borzini.pos.core

enum class PaymentMethod { CASH, CASHLESS }

sealed class CashPaymentResult {
    data class Ok(val change: Money) : CashPaymentResult()
    data class InsufficientAmount(val shortfall: Money) : CashPaymentResult()
}

/**
 * Cash change calculation: exact integer-kopeck subtraction, no floating point involved.
 * A sale can only be completed when the amount received covers the total.
 */
object CashPaymentCalculator {
    fun evaluate(total: Money, received: Money): CashPaymentResult {
        return if (received < total) {
            CashPaymentResult.InsufficientAmount(total - received)
        } else {
            CashPaymentResult.Ok(received - total)
        }
    }

    fun canComplete(total: Money, received: Money): Boolean = received >= total
}
