package com.borzini.pos.data.backup

import com.borzini.pos.data.db.entities.CategoryEntity
import com.borzini.pos.data.db.entities.ExpenseEntity
import com.borzini.pos.data.db.entities.GoalEntity
import com.borzini.pos.data.db.entities.InventoryItemEntity
import com.borzini.pos.data.db.entities.ProductEntity
import com.borzini.pos.data.db.entities.ProductVariantEntity
import com.borzini.pos.data.db.entities.PurchaseEntity
import com.borzini.pos.data.db.entities.PurchaseLineEntity
import com.borzini.pos.data.db.entities.RecipeLineEntity
import com.borzini.pos.data.db.entities.ReturnEntity
import com.borzini.pos.data.db.entities.ReturnItemEntity
import com.borzini.pos.data.db.entities.SaleEntity
import com.borzini.pos.data.db.entities.SaleItemDeductionEntity
import com.borzini.pos.data.db.entities.SaleItemEntity
import com.borzini.pos.data.db.entities.StockMovementEntity
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal

/**
 * Explicit, boring JSON <-> entity mapping for every table the backup file covers. Deliberately
 * not reflection-based: it is easy to read, easy to audit field-by-field against BackupRepository
 * table lists when the schema changes, and needs no extra Gradle plugin (see MIGRATIONS.md).
 */
object BackupCodec {
    private fun JSONObject.optBigDecimal(key: String): BigDecimal = BigDecimal(getString(key))
    private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key)) null else optString(key, null)

    fun toJson(e: CategoryEntity) = JSONObject().apply {
        put("id", e.id); put("name", e.name); put("sortOrder", e.sortOrder)
        put("isPopularAuto", e.isPopularAuto); put("isArchived", e.isArchived); put("updatedAt", e.updatedAt)
    }
    fun categoryFromJson(j: JSONObject) = CategoryEntity(
        j.getString("id"), j.getString("name"), j.getInt("sortOrder"),
        j.optBoolean("isPopularAuto"), j.optBoolean("isArchived"), j.getLong("updatedAt"),
    )

    fun toJson(e: ProductEntity) = JSONObject().apply {
        put("id", e.id); put("categoryId", e.categoryId); put("name", e.name)
        put("description", e.description); put("photoUri", e.photoUri)
        put("isSimpleProduct", e.isSimpleProduct); put("simpleInventoryItemId", e.simpleInventoryItemId)
        put("isPinnedPopular", e.isPinnedPopular); put("isAvailableForSale", e.isAvailableForSale)
        put("isArchived", e.isArchived); put("sortOrder", e.sortOrder); put("isDemo", e.isDemo)
        put("createdAt", e.createdAt); put("updatedAt", e.updatedAt)
    }
    fun productFromJson(j: JSONObject) = ProductEntity(
        id = j.getString("id"), categoryId = j.getString("categoryId"), name = j.getString("name"),
        description = j.optStringOrNull("description"), photoUri = j.optStringOrNull("photoUri"),
        isSimpleProduct = j.optBoolean("isSimpleProduct"), simpleInventoryItemId = j.optStringOrNull("simpleInventoryItemId"),
        isPinnedPopular = j.optBoolean("isPinnedPopular"), isAvailableForSale = j.optBoolean("isAvailableForSale", true),
        isArchived = j.optBoolean("isArchived"), sortOrder = j.getInt("sortOrder"), isDemo = j.optBoolean("isDemo"),
        createdAt = j.getLong("createdAt"), updatedAt = j.getLong("updatedAt"),
    )

    fun toJson(e: ProductVariantEntity) = JSONObject().apply {
        put("id", e.id); put("productId", e.productId); put("name", e.name)
        put("priceKopecks", e.priceKopecks); put("sortOrder", e.sortOrder)
        put("isArchived", e.isArchived); put("updatedAt", e.updatedAt)
    }
    fun variantFromJson(j: JSONObject) = ProductVariantEntity(
        j.getString("id"), j.getString("productId"), j.optStringOrNull("name"),
        j.getLong("priceKopecks"), j.getInt("sortOrder"), j.optBoolean("isArchived"), j.getLong("updatedAt"),
    )

    fun toJson(e: RecipeLineEntity) = JSONObject().apply {
        put("id", e.id); put("variantId", e.variantId); put("inventoryItemId", e.inventoryItemId)
        put("quantityPerPortion", e.quantityPerPortion.toPlainString()); put("sortOrder", e.sortOrder)
    }
    fun recipeLineFromJson(j: JSONObject) = RecipeLineEntity(
        j.getString("id"), j.getString("variantId"), j.getString("inventoryItemId"),
        j.optBigDecimal("quantityPerPortion"), j.getInt("sortOrder"),
    )

    fun toJson(e: InventoryItemEntity) = JSONObject().apply {
        put("id", e.id); put("name", e.name); put("category", e.category); put("baseUnit", e.baseUnit)
        put("onHandAmount", e.onHandAmount.toPlainString()); put("avgUnitCostRubles", e.avgUnitCostRubles.toPlainString())
        put("minAllowedAmount", e.minAllowedAmount.toPlainString()); put("supplier", e.supplier)
        put("isArchived", e.isArchived); put("sortOrder", e.sortOrder); put("isDemo", e.isDemo); put("updatedAt", e.updatedAt)
    }
    fun inventoryItemFromJson(j: JSONObject) = InventoryItemEntity(
        id = j.getString("id"), name = j.getString("name"), category = j.getString("category"), baseUnit = j.getString("baseUnit"),
        onHandAmount = j.optBigDecimal("onHandAmount"), avgUnitCostRubles = j.optBigDecimal("avgUnitCostRubles"),
        minAllowedAmount = j.optBigDecimal("minAllowedAmount"), supplier = j.optStringOrNull("supplier"),
        isArchived = j.optBoolean("isArchived"), sortOrder = j.optInt("sortOrder"), isDemo = j.optBoolean("isDemo"),
        updatedAt = j.getLong("updatedAt"),
    )

    fun toJson(e: PurchaseEntity) = JSONObject().apply {
        put("id", e.id); put("date", e.date); put("invoiceNumber", e.invoiceNumber); put("supplier", e.supplier)
        put("totalKopecks", e.totalKopecks); put("comment", e.comment); put("attachmentUri", e.attachmentUri)
        put("isPosted", e.isPosted); put("isDemo", e.isDemo); put("createdAt", e.createdAt); put("postedAt", e.postedAt)
    }
    fun purchaseFromJson(j: JSONObject) = PurchaseEntity(
        j.getString("id"), j.getLong("date"), j.optStringOrNull("invoiceNumber"), j.getString("supplier"),
        j.getLong("totalKopecks"), j.optStringOrNull("comment"), j.optStringOrNull("attachmentUri"),
        j.optBoolean("isPosted"), j.optBoolean("isDemo"), j.getLong("createdAt"),
        if (j.isNull("postedAt")) null else j.getLong("postedAt"),
    )

    fun toJson(e: PurchaseLineEntity) = JSONObject().apply {
        put("id", e.id); put("purchaseId", e.purchaseId); put("inventoryItemId", e.inventoryItemId)
        put("packageCount", e.packageCount); put("unitsPerPackage", e.unitsPerPackage.toPlainString())
        put("packageCostKopecks", e.packageCostKopecks); put("totalCostKopecks", e.totalCostKopecks)
        put("totalBaseUnits", e.totalBaseUnits.toPlainString())
    }
    fun purchaseLineFromJson(j: JSONObject) = PurchaseLineEntity(
        j.getString("id"), j.getString("purchaseId"), j.getString("inventoryItemId"), j.getInt("packageCount"),
        j.optBigDecimal("unitsPerPackage"), j.getLong("packageCostKopecks"), j.getLong("totalCostKopecks"),
        j.optBigDecimal("totalBaseUnits"),
    )

    fun toJson(e: StockMovementEntity) = JSONObject().apply {
        put("id", e.id); put("inventoryItemId", e.inventoryItemId); put("reason", e.reason)
        put("quantityDelta", e.quantityDelta.toPlainString()); put("unitCostAtMovementRubles", e.unitCostAtMovementRubles.toPlainString())
        put("relatedPurchaseId", e.relatedPurchaseId); put("relatedSaleId", e.relatedSaleId); put("relatedReturnId", e.relatedReturnId)
        put("correctsMovementId", e.correctsMovementId); put("note", e.note); put("isDemo", e.isDemo); put("createdAt", e.createdAt)
    }
    fun movementFromJson(j: JSONObject) = StockMovementEntity(
        j.getString("id"), j.getString("inventoryItemId"), j.getString("reason"), j.optBigDecimal("quantityDelta"),
        j.optBigDecimal("unitCostAtMovementRubles"), j.optStringOrNull("relatedPurchaseId"), j.optStringOrNull("relatedSaleId"),
        j.optStringOrNull("relatedReturnId"), j.optStringOrNull("correctsMovementId"), j.optStringOrNull("note"),
        j.optBoolean("isDemo"), j.getLong("createdAt"),
    )

    fun toJson(e: SaleEntity) = JSONObject().apply {
        put("id", e.id); put("receiptNumber", e.receiptNumber); put("createdAt", e.createdAt)
        put("paymentMethod", e.paymentMethod); put("totalKopecks", e.totalKopecks)
        put("cashReceivedKopecks", e.cashReceivedKopecks); put("changeGivenKopecks", e.changeGivenKopecks)
        put("cogsKopecks", e.cogsKopecks); put("isFullyReturned", e.isFullyReturned)
        put("isPartiallyReturned", e.isPartiallyReturned); put("isDemo", e.isDemo)
    }
    fun saleFromJson(j: JSONObject) = SaleEntity(
        j.getString("id"), j.getLong("receiptNumber"), j.getLong("createdAt"), j.getString("paymentMethod"),
        j.getLong("totalKopecks"), if (j.isNull("cashReceivedKopecks")) null else j.getLong("cashReceivedKopecks"),
        if (j.isNull("changeGivenKopecks")) null else j.getLong("changeGivenKopecks"), j.getLong("cogsKopecks"),
        j.optBoolean("isFullyReturned"), j.optBoolean("isPartiallyReturned"), j.optBoolean("isDemo"),
    )

    fun toJson(e: SaleItemEntity) = JSONObject().apply {
        put("id", e.id); put("saleId", e.saleId); put("productId", e.productId); put("variantId", e.variantId)
        put("productNameSnapshot", e.productNameSnapshot); put("variantNameSnapshot", e.variantNameSnapshot)
        put("unitPriceKopecks", e.unitPriceKopecks); put("quantity", e.quantity)
        put("lineTotalKopecks", e.lineTotalKopecks); put("lineCogsKopecks", e.lineCogsKopecks)
        put("returnedQuantity", e.returnedQuantity)
    }
    fun saleItemFromJson(j: JSONObject) = SaleItemEntity(
        j.getString("id"), j.getString("saleId"), j.optStringOrNull("productId"), j.optStringOrNull("variantId"),
        j.getString("productNameSnapshot"), j.optStringOrNull("variantNameSnapshot"), j.getLong("unitPriceKopecks"),
        j.getInt("quantity"), j.getLong("lineTotalKopecks"), j.getLong("lineCogsKopecks"), j.optInt("returnedQuantity"),
    )

    fun toJson(e: SaleItemDeductionEntity) = JSONObject().apply {
        put("id", e.id); put("saleItemId", e.saleItemId); put("inventoryItemId", e.inventoryItemId)
        put("inventoryItemNameSnapshot", e.inventoryItemNameSnapshot); put("quantityDeducted", e.quantityDeducted.toPlainString())
        put("unitCostAtSaleRubles", e.unitCostAtSaleRubles.toPlainString()); put("lineCostKopecks", e.lineCostKopecks)
    }
    fun deductionFromJson(j: JSONObject) = SaleItemDeductionEntity(
        j.getString("id"), j.getString("saleItemId"), j.getString("inventoryItemId"), j.getString("inventoryItemNameSnapshot"),
        j.optBigDecimal("quantityDeducted"), j.optBigDecimal("unitCostAtSaleRubles"), j.getLong("lineCostKopecks"),
    )

    fun toJson(e: ReturnEntity) = JSONObject().apply {
        put("id", e.id); put("saleId", e.saleId); put("createdAt", e.createdAt); put("reason", e.reason)
        put("refundedKopecks", e.refundedKopecks); put("restockPolicy", e.restockPolicy); put("isDemo", e.isDemo)
    }
    fun returnFromJson(j: JSONObject) = ReturnEntity(
        j.getString("id"), j.getString("saleId"), j.getLong("createdAt"), j.getString("reason"),
        j.getLong("refundedKopecks"), j.getString("restockPolicy"), j.optBoolean("isDemo"),
    )

    fun toJson(e: ReturnItemEntity) = JSONObject().apply {
        put("id", e.id); put("returnId", e.returnId); put("saleItemId", e.saleItemId)
        put("quantityReturned", e.quantityReturned); put("refundedLineKopecks", e.refundedLineKopecks)
        put("restockedCogsKopecks", e.restockedCogsKopecks)
    }
    fun returnItemFromJson(j: JSONObject) = ReturnItemEntity(
        j.getString("id"), j.getString("returnId"), j.getString("saleItemId"), j.getInt("quantityReturned"),
        j.getLong("refundedLineKopecks"), j.getLong("restockedCogsKopecks"),
    )

    fun toJson(e: ExpenseEntity) = JSONObject().apply {
        put("id", e.id); put("date", e.date); put("category", e.category); put("amountKopecks", e.amountKopecks)
        put("comment", e.comment); put("isDemo", e.isDemo); put("createdAt", e.createdAt)
    }
    fun expenseFromJson(j: JSONObject) = ExpenseEntity(
        j.getString("id"), j.getLong("date"), j.getString("category"), j.getLong("amountKopecks"),
        j.optStringOrNull("comment"), j.optBoolean("isDemo"), j.getLong("createdAt"),
    )

    fun toJson(e: GoalEntity) = JSONObject().apply {
        put("id", e.id); put("type", e.type); put("periodKey", e.periodKey)
        put("targetKopecks", e.targetKopecks); put("updatedAt", e.updatedAt)
    }
    fun goalFromJson(j: JSONObject) = GoalEntity(
        j.getString("id"), j.getString("type"), j.getString("periodKey"), j.getLong("targetKopecks"), j.getLong("updatedAt"),
    )

    fun <T> arrayOf(items: List<T>, toJson: (T) -> JSONObject): JSONArray =
        JSONArray().apply { items.forEach { put(toJson(it)) } }

    fun <T> listFrom(array: JSONArray?, fromJson: (JSONObject) -> T): List<T> {
        if (array == null) return emptyList()
        return (0 until array.length()).map { fromJson(array.getJSONObject(it)) }
    }
}
