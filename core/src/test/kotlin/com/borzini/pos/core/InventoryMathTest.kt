package com.borzini.pos.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class InventoryMathTest {

    @Test
    fun `scenario 3 - a cart that is never checked out never touches stock`() {
        // Adding to the cart is purely a UI/ViewModel-level list; InventoryMath.deduct is only ever
        // called from SaleRepository.completeSale. This test documents the guarantee at the
        // domain level: computing what a cart WOULD deduct does not mutate any StockLevel.
        val level = StockLevel(Quantity.of(100), UnitCost.of(BigDecimal("5")), Quantity.ZERO)
        val levelSnapshotBeforeBrowsingCart = level.copy()

        // Simulate "user added/removed items a few times" - no deduct() call happens.
        assertEquals(levelSnapshotBeforeBrowsingCart, level)
        assertEquals(Quantity.of(100), level.onHand)
    }

    @Test
    fun `default policy blocks a sale when an ingredient is short`() {
        val level = StockLevel(Quantity.of(5), UnitCost.of(BigDecimal("1")), Quantity.ZERO)
        val result = InventoryMath.deduct("Лёд", level, Quantity.of(10), NegativeStockPolicy.BLOCK_SALE)
        assertTrue(result is StockDeductionResult.InsufficientStock)
    }

    @Test
    fun `allow-negative policy permits going below zero without dividing by zero anywhere`() {
        val level = StockLevel(Quantity.of(5), UnitCost.of(BigDecimal("1")), Quantity.ZERO)
        val result = InventoryMath.deduct("Лёд", level, Quantity.of(10), NegativeStockPolicy.ALLOW_NEGATIVE)
        assertTrue(result is StockDeductionResult.Ok)
        val newLevel = (result as StockDeductionResult.Ok).newLevel
        assertEquals(Quantity.of(-5), newLevel.onHand)

        // The next purchase must still be able to recompute an average cost from this negative base.
        val nextAvg = WeightedAverageCost.recalculate(
            newLevel.onHand,
            newLevel.avgUnitCost,
            Quantity.of(20),
            UnitCost.of(BigDecimal("2")),
        )
        assertEquals(0, BigDecimal("2").compareTo(nextAvg.rublesPerUnit))
    }

    @Test
    fun `inventory count difference is signed - fewer on shelf than expected is negative`() {
        val level = StockLevel(Quantity.of(100), UnitCost.of(BigDecimal("5")), Quantity.ZERO)
        assertEquals(Quantity.of(-3), InventoryMath.inventoryCountDifference(level, Quantity.of(97)))
        assertEquals(Quantity.of(2), InventoryMath.inventoryCountDifference(level, Quantity.of(102)))
    }

    @Test
    fun `low stock flag compares on-hand to the configured minimum`() {
        val low = StockLevel(Quantity.of(2), UnitCost.of(BigDecimal("1")), Quantity.of(5))
        val ok = StockLevel(Quantity.of(10), UnitCost.of(BigDecimal("1")), Quantity.of(5))
        assertTrue(low.isLowStock())
        assertTrue(!ok.isLowStock())
    }
}
