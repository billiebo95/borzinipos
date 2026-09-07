package com.borzini.pos.sync

import com.borzini.pos.data.db.entities.SyncEntityType
import org.json.JSONObject

/** Turns one sync-queue payload back into the flat row of cell values its sheet expects. */
object RowBuilders {
    fun build(type: SyncEntityType, p: JSONObject): List<Any?> = when (type) {
        SyncEntityType.CATEGORY -> listOf(
            p.getString("id"), p.getString("name"), p.optInt("sortOrder"),
            if (p.optBoolean("isArchived")) "Да" else "Нет",
        )
        SyncEntityType.PRODUCT -> {
            val variants = p.optJSONArray("variants")
            val variantsText = buildString {
                if (variants != null) {
                    for (i in 0 until variants.length()) {
                        val v = variants.getJSONObject(i)
                        if (i > 0) append(" | ")
                        append(v.optString("name", "—"))
                        append(":")
                        append(v.optLong("priceKopecks") / 100.0)
                        append("₽ [")
                        append(v.optString("recipe", ""))
                        append("]")
                    }
                }
            }
            listOf(p.getString("id"), p.getString("categoryId"), p.getString("name"), if (p.optBoolean("isSimpleProduct")) "Да" else "Нет", variantsText)
        }
        SyncEntityType.INVENTORY_ITEM -> listOf(
            p.getString("id"), p.getString("name"), p.getString("category"), p.getString("baseUnit"),
            p.getString("onHandAmount"), p.getString("avgUnitCostRubles"), p.getString("minAllowedAmount"),
            if (p.optBoolean("isArchived")) "Да" else "Нет",
        )
        SyncEntityType.PURCHASE -> listOf(
            p.getString("id"), p.optLong("date"), p.optString("invoiceNumber", ""), p.getString("supplier"),
            p.optLong("totalKopecks") / 100.0, p.optString("comment", ""), p.optInt("lineCount"),
        )
        SyncEntityType.STOCK_MOVEMENT -> listOf(
            p.getString("id"), p.getString("inventoryItemId"), p.getString("reason"), p.getString("quantityDelta"),
            p.getString("unitCostAtMovementRubles"), p.optString("note", ""), p.optLong("createdAt"),
        )
        SyncEntityType.SALE -> listOf(
            p.getString("id"), p.optLong("receiptNumber"), p.optLong("createdAt"), p.getString("paymentMethod"),
            p.optLong("totalKopecks") / 100.0, p.optLong("cogsKopecks") / 100.0, p.optJSONArray("items")?.toString() ?: "[]",
        )
        SyncEntityType.RETURN -> listOf(
            p.getString("id"), p.getString("saleId"), p.optLong("createdAt"), p.getString("reason"),
            p.optLong("refundedKopecks") / 100.0,
            if (p.optString("restockPolicy") == "RESTOCK_INGREDIENTS") "Да" else "Нет",
        )
        SyncEntityType.EXPENSE -> listOf(
            p.getString("id"), p.optLong("date"), p.getString("category"), p.optLong("amountKopecks") / 100.0, p.optString("comment", ""),
        )
        SyncEntityType.GOAL -> listOf(p.getString("id"), p.getString("type"), p.getString("periodKey"), p.optLong("targetKopecks") / 100.0)
    }
}
