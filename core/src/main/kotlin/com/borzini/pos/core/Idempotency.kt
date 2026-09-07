package com.borzini.pos.core

import java.util.UUID

/**
 * Stable identifier for one checkout attempt. The same key must be reused across retries
 * (double-tap on the pay button, app restart mid-sale, a retried sync upload) so the storage
 * layer can detect "this sale already exists" and refuse to create a duplicate, instead of
 * relying on timing or disabling the button (which a fast double-tap can still race).
 */
@JvmInline
value class IdempotencyKey(val value: String) {
    companion object {
        fun generate(): IdempotencyKey = IdempotencyKey(UUID.randomUUID().toString())
    }
}

/**
 * Holds the key for a checkout draft that has not yet been confirmed as saved. Call [keyFor]
 * once when the checkout screen is first shown, keep the returned key in the draft/ViewModel
 * state, and pass the SAME key again on every retry until the repository confirms the sale
 * was persisted (see SaleRepository.completeSale, which is expected to be idempotent on this key).
 */
class CheckoutDraft private constructor(private var key: IdempotencyKey?) {
    fun keyFor(): IdempotencyKey {
        val existing = key
        if (existing != null) return existing
        val fresh = IdempotencyKey.generate()
        key = fresh
        return fresh
    }

    /** Call after a fresh sale (not a retry) begins, e.g. cart cleared / new order started. */
    fun reset() {
        key = null
    }

    companion object {
        fun new(): CheckoutDraft = CheckoutDraft(null)
    }
}
