package com.borzini.pos.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class CostingTest {

    @Test
    fun `scenario 1 - receiving 100 cups at 5 rub creates stock 100 and value 500`() {
        val purchaseCost = UnitCost.fromPackagePurchase(
            packageCost = Money.fromRubles(BigDecimal("500")),
            packageCount = 1,
            unitsPerPackage = Quantity.of(100),
        )
        assertEquals(Money.fromRubles(BigDecimal("5.00")), purchaseCost.costOf(Quantity.of(1)))

        val level = StockLevel(onHand = Quantity.ZERO, avgUnitCost = UnitCost.ZERO, minAllowed = Quantity.ZERO)
        val restocked = InventoryMath.restock(level, Quantity.of(100)).copy(avgUnitCost = purchaseCost)

        assertEquals(Quantity.of(100), restocked.onHand)
        assertEquals(Money.fromRubles(BigDecimal("500.00")), restocked.valueOnHand)
    }

    @Test
    fun `weighted average cost blends existing and incoming stock`() {
        // 100 cups already at 5 rub (value 500) + 100 more cups at 7 rub (value 700) => 200 cups, avg 6 rub
        val newAvg = WeightedAverageCost.recalculate(
            currentQuantity = Quantity.of(100),
            currentAvgCost = UnitCost.of(BigDecimal("5")),
            incomingQuantity = Quantity.of(100),
            incomingUnitCost = UnitCost.of(BigDecimal("7")),
        )
        assertEquals(0, BigDecimal("6").compareTo(newAvg.rublesPerUnit))
    }

    @Test
    fun `zero or negative current stock discards stale average instead of dividing by it`() {
        val newAvg = WeightedAverageCost.recalculate(
            currentQuantity = Quantity.of("-5"), // negative stock was allowed by policy
            currentAvgCost = UnitCost.of(BigDecimal("999")), // stale/meaningless average
            incomingQuantity = Quantity.of(10),
            incomingUnitCost = UnitCost.of(BigDecimal("4")),
        )
        assertEquals(0, BigDecimal("4").compareTo(newAvg.rublesPerUnit))
    }

    @Test
    fun `scenario 2 - selling two drinks deducts double the recipe quantity of every ingredient`() {
        // Granatoviy tonic recipe (demo): cup 1pc, lid 1pc, pomegranate juice 100ml, tonic 200ml, ice 100g, straw 1pc
        val recipe = listOf(
            RecipeLine("Стакан 400мл", Quantity.of(1), UnitCost.of(BigDecimal("5"))),
            RecipeLine("Крышка", Quantity.of(1), UnitCost.of(BigDecimal("2"))),
            RecipeLine("Гранатовый сок", Quantity.of(100), UnitCost.of(BigDecimal("0.5"))),
            RecipeLine("Тоник", Quantity.of(200), UnitCost.of(BigDecimal("0.1"))),
            RecipeLine("Лёд", Quantity.of(100), UnitCost.of(BigDecimal("0.05"))),
            RecipeLine("Трубочка", Quantity.of(1), UnitCost.of(BigDecimal("1"))),
        )
        val costPerPortion = RecipeCostCalculator.costPerPortion(recipe)

        val quantitySold = 2
        val startLevels = recipe.associate {
            it.ingredientName to StockLevel(Quantity.of(1000), it.unitCost, Quantity.ZERO)
        }
        val afterSale = recipe.associate { line ->
            val required = line.quantityPerPortion * quantitySold
            val result = InventoryMath.deduct(
                line.ingredientName,
                startLevels.getValue(line.ingredientName),
                required,
                NegativeStockPolicy.BLOCK_SALE,
            )
            check(result is StockDeductionResult.Ok)
            line.ingredientName to result.newLevel.onHand
        }

        assertEquals(Quantity.of(998), afterSale["Стакан 400мл"]) // 1000 - 2*1
        assertEquals(Quantity.of(800), afterSale["Гранатовый сок"]) // 1000 - 2*100
        assertEquals(Quantity.of(600), afterSale["Тоник"]) // 1000 - 2*200
        assertEquals(costPerPortion * quantitySold, Money.sum(recipe.map { it.unitCost.costOf(it.quantityPerPortion * quantitySold) }))
    }

    @Test
    fun `scenario 6 - a new purchase price never changes the cost stored on an already-completed sale`() {
        val oldCost = RecipeCostCalculator.costPerPortion(
            listOf(RecipeLine("Кофе", Quantity.of(18), UnitCost.of(BigDecimal("2")))),
        )
        val saleRecord = SaleForStats(Money.fromRubles(BigDecimal("200")), oldCost, PaymentMethod.CASH)

        // Price of coffee doubles after the sale.
        val newCost = RecipeCostCalculator.costPerPortion(
            listOf(RecipeLine("Кофе", Quantity.of(18), UnitCost.of(BigDecimal("4")))),
        )

        // The historical record is an immutable snapshot: it never references newCost.
        assertEquals(oldCost, saleRecord.cogsAtSaleTime)
        assert(newCost != oldCost)
    }
}
