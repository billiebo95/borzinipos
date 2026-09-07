package com.borzini.pos.sync

import com.borzini.pos.data.db.entities.SyncEntityType

/**
 * One tab per entity type, with a stable header row. Column A is always the row's permanent
 * unique id (never a name, never a spreadsheet row number - see spec section 9), which is what
 * lets [DriveSheetsClient.upsertRow] find and update "the same" row again later instead of
 * appending a duplicate every time something changes.
 */
data class SheetDefinition(val sheetName: String, val headers: List<String>)

object SheetsSchema {
    val CATEGORY = SheetDefinition("Категории", listOf("ID", "Название", "Порядок", "Архив"))
    val PRODUCT = SheetDefinition("Товары", listOf("ID", "ID категории", "Название", "Простой товар", "Варианты (название:цена:рецепт)"))
    val INVENTORY_ITEM = SheetDefinition(
        "Склад",
        listOf("ID", "Название", "Категория", "Ед. изм.", "Остаток", "Средняя себестоимость, ₽/ед.", "Мин. остаток", "Архив"),
    )
    val PURCHASE = SheetDefinition("Закупки", listOf("ID", "Дата", "Номер накладной", "Поставщик", "Сумма, ₽", "Комментарий", "Кол-во строк"))
    val STOCK_MOVEMENT = SheetDefinition(
        "Движения склада",
        listOf("ID", "ID позиции склада", "Причина", "Изменение количества", "Себестоимость ед., ₽", "Примечание", "Дата и время"),
    )
    val SALE = SheetDefinition("Чеки", listOf("ID", "Номер чека", "Дата и время", "Способ оплаты", "Сумма, ₽", "Себестоимость, ₽", "Состав (JSON)"))
    val RETURN = SheetDefinition("Возвраты", listOf("ID", "ID чека", "Дата и время", "Причина", "Сумма возврата, ₽", "Возврат ингредиентов"))
    val EXPENSE = SheetDefinition("Расходы", listOf("ID", "Дата", "Категория", "Сумма, ₽", "Комментарий"))
    val GOAL = SheetDefinition("Цели", listOf("ID", "Тип", "Период", "Цель, ₽"))

    val ALL: List<SheetDefinition> = listOf(CATEGORY, PRODUCT, INVENTORY_ITEM, PURCHASE, STOCK_MOVEMENT, SALE, RETURN, EXPENSE, GOAL)

    fun forType(type: SyncEntityType): SheetDefinition = when (type) {
        SyncEntityType.CATEGORY -> CATEGORY
        SyncEntityType.PRODUCT -> PRODUCT
        SyncEntityType.INVENTORY_ITEM -> INVENTORY_ITEM
        SyncEntityType.PURCHASE -> PURCHASE
        SyncEntityType.STOCK_MOVEMENT -> STOCK_MOVEMENT
        SyncEntityType.SALE -> SALE
        SyncEntityType.RETURN -> RETURN
        SyncEntityType.EXPENSE -> EXPENSE
        SyncEntityType.GOAL -> GOAL
    }
}
