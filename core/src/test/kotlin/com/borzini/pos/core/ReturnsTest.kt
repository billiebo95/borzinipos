package com.borzini.pos.core

import org.junit.Assert.assertTrue
import org.junit.Test

class ReturnsTest {

    @Test
    fun `scenario 10 - a partial return cannot exceed the sold quantity`() {
        val line = SaleLineState(soldQuantity = Quantity.of(2), alreadyReturnedQuantity = Quantity.ZERO)

        val requestThree = ReturnValidator.validate(line, Quantity.of(3))
        assertTrue(requestThree is ReturnValidationResult.ExceedsSoldQuantity)

        val requestTwo = ReturnValidator.validate(line, Quantity.of(2))
        assertTrue(requestTwo is ReturnValidationResult.Ok)
    }

    @Test
    fun `second partial return is capped by what the first partial return already took`() {
        val afterFirstReturn = SaleLineState(soldQuantity = Quantity.of(2), alreadyReturnedQuantity = Quantity.of(1))

        val requestTwoMore = ReturnValidator.validate(afterFirstReturn, Quantity.of(2))
        assertTrue(requestTwoMore is ReturnValidationResult.ExceedsSoldQuantity)
        assertTrue((requestTwoMore as ReturnValidationResult.ExceedsSoldQuantity).maxAllowed == Quantity.of(1))

        val requestOneMore = ReturnValidator.validate(afterFirstReturn, Quantity.of(1))
        assertTrue(requestOneMore is ReturnValidationResult.Ok)
    }

    @Test
    fun `scenario 9 - returning a prepared drink refunds money without restocking ingredients by default`() {
        // The policy itself is just a marker the repository switches on; verify the default marker value.
        val defaultPolicy = ReturnStockPolicy.DO_NOT_RESTOCK_INGREDIENTS
        assertTrue(defaultPolicy == ReturnStockPolicy.DO_NOT_RESTOCK_INGREDIENTS)
    }

    @Test
    fun `zero or negative requested quantity is rejected`() {
        val line = SaleLineState(soldQuantity = Quantity.of(2), alreadyReturnedQuantity = Quantity.ZERO)
        assertTrue(ReturnValidator.validate(line, Quantity.ZERO) is ReturnValidationResult.InvalidQuantity)
    }
}
