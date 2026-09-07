package com.borzini.pos.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class IdempotencyTest {

    @Test
    fun `scenario 7 - repeated pay taps reuse the same key so no duplicate sale is created`() {
        val draft = CheckoutDraft.new()
        val firstTap = draft.keyFor()
        val doubleTapRetry = draft.keyFor()
        val afterAppRestartRetry = draft.keyFor() // same in-memory draft simulates "still same attempt"

        assertEquals(firstTap, doubleTapRetry)
        assertEquals(firstTap, afterAppRestartRetry)
    }

    @Test
    fun `a genuinely new order after reset gets a fresh key`() {
        val draft = CheckoutDraft.new()
        val first = draft.keyFor()
        draft.reset()
        val second = draft.keyFor()
        assertNotEquals(first, second)
    }
}
