package com.borzini.pos.data.backup

import androidx.room.withTransaction
import com.borzini.pos.data.db.BorziniDatabase
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.time.Instant

/** Bumped whenever the JSON shape below changes, so an old backup can be read/rejected on purpose instead of silently misparsed. */
const val BACKUP_FORMAT_VERSION = 1

data class BackupPreview(
    val formatVersion: Int,
    val exportedAtEpochMillis: Long,
    val counts: Map<String, Int>,
)

sealed class RestoreResult {
    data class Ok(val safetyBackupPath: String) : RestoreResult()
    data class IncompatibleFormat(val foundVersion: Int) : RestoreResult()
    data class Invalid(val message: String) : RestoreResult()
}

/**
 * Whole-database JSON backup: everything needed to fully recreate the ledger (catalog, stock,
 * every sale/return/purchase/expense/goal history). Restoring always writes a fresh safety copy
 * of the CURRENT data first (see [RestoreResult.Ok.safetyBackupPath]), so an accidental restore
 * is itself recoverable - the caller (Settings screen) is expected to show the backup's date and
 * contents and ask for confirmation before calling [restore].
 */
class BackupRepository(private val db: BorziniDatabase) {

    suspend fun export(destFile: File): BackupPreview {
        val json = JSONObject()
        json.put("formatVersion", BACKUP_FORMAT_VERSION)
        val now = Instant.now().toEpochMilli()
        json.put("exportedAt", now)

        val categories = db.categoryDao().observeAll().first()
        val products = db.productDao().observeAll().first()
        val variants = db.productVariantDao().observeAllActive().first()
        val items = db.inventoryItemDao().observeAll().first()
        val purchases = db.purchaseDao().observeAll().first()
        val expenses = db.expenseDao().observeAll().first()
        val goals = db.goalDao().getAll()

        json.put("categories", BackupCodec.arrayOf(categories, BackupCodec::toJson))
        json.put("products", BackupCodec.arrayOf(products, BackupCodec::toJson))
        json.put("productVariants", BackupCodec.arrayOf(variants, BackupCodec::toJson))
        json.put("inventoryItems", BackupCodec.arrayOf(items, BackupCodec::toJson))
        json.put("purchases", BackupCodec.arrayOf(purchases, BackupCodec::toJson))
        json.put("expenses", BackupCodec.arrayOf(expenses, BackupCodec::toJson))
        json.put("goals", BackupCodec.arrayOf(goals, BackupCodec::toJson))

        // Recipe lines, purchase lines, sales (+items/deductions), returns (+items) and stock
        // movements are gathered per-parent to reuse the existing DAOs rather than adding
        // bespoke "select everything" queries for tables that can grow large.
        val recipeLines = variants.flatMap { db.recipeLineDao().getForVariantOnce(it.id) }
        json.put("recipeLines", BackupCodec.arrayOf(recipeLines, BackupCodec::toJson))

        val purchaseLines = purchases.flatMap { db.purchaseDao().getLinesForPurchaseOnce(it.id) }
        json.put("purchaseLines", BackupCodec.arrayOf(purchaseLines, BackupCodec::toJson))

        val movements = items.flatMap { db.stockMovementDao().observeForItem(it.id).first() }
        json.put("stockMovements", BackupCodec.arrayOf(movements, BackupCodec::toJson))

        val allSales = db.saleDao().observeInRange(0L, Long.MAX_VALUE, true).first()
        json.put("sales", BackupCodec.arrayOf(allSales, BackupCodec::toJson))
        val allSaleItems = allSales.flatMap { db.saleDao().getItemsForSaleOnce(it.id) }
        json.put("saleItems", BackupCodec.arrayOf(allSaleItems, BackupCodec::toJson))
        val allDeductions = allSaleItems.flatMap { db.saleDao().getDeductionsForItem(it.id) }
        json.put("saleItemDeductions", BackupCodec.arrayOf(allDeductions, BackupCodec::toJson))

        val allReturns = db.returnDao().observeInRange(0L, Long.MAX_VALUE, true).first()
        json.put("returns", BackupCodec.arrayOf(allReturns, BackupCodec::toJson))
        val allReturnItems = allReturns.flatMap { db.returnDao().getItemsForReturn(it.id) }
        json.put("returnItems", BackupCodec.arrayOf(allReturnItems, BackupCodec::toJson))

        destFile.parentFile?.mkdirs()
        destFile.writeText(json.toString(), StandardCharsets.UTF_8)

        return BackupPreview(
            BACKUP_FORMAT_VERSION,
            now,
            mapOf(
                "Товары" to products.size,
                "Склад" to items.size,
                "Закупки" to purchases.size,
                "Чеки" to allSales.size,
                "Возвраты" to allReturns.size,
                "Расходы" to expenses.size,
            ),
        )
    }

    fun readPreview(file: File): BackupPreview? = runCatching {
        val json = JSONObject(file.readText(StandardCharsets.UTF_8))
        BackupPreview(
            json.getInt("formatVersion"),
            json.getLong("exportedAt"),
            mapOf(
                "Товары" to json.optJSONArray("products")?.length().orZero(),
                "Склад" to json.optJSONArray("inventoryItems")?.length().orZero(),
                "Закупки" to json.optJSONArray("purchases")?.length().orZero(),
                "Чеки" to json.optJSONArray("sales")?.length().orZero(),
                "Возвраты" to json.optJSONArray("returns")?.length().orZero(),
                "Расходы" to json.optJSONArray("expenses")?.length().orZero(),
            ),
        )
    }.getOrNull()

    private fun Int?.orZero() = this ?: 0

    suspend fun restore(file: File, safetyBackupFile: File): RestoreResult {
        val json = try {
            JSONObject(file.readText(StandardCharsets.UTF_8))
        } catch (e: Exception) {
            return RestoreResult.Invalid("Файл повреждён или не является резервной копией BORZINI: ${e.message}")
        }
        val version = json.optInt("formatVersion", -1)
        if (version != BACKUP_FORMAT_VERSION) return RestoreResult.IncompatibleFormat(version)

        // Safety copy of what is about to be overwritten.
        export(safetyBackupFile)

        db.withTransaction {
            db.clearAllTables()

            BackupCodec.listFrom(json.optJSONArray("categories"), BackupCodec::categoryFromJson)
                .forEach { db.categoryDao().upsert(it) }
            BackupCodec.listFrom(json.optJSONArray("products"), BackupCodec::productFromJson)
                .forEach { db.productDao().upsert(it) }
            BackupCodec.listFrom(json.optJSONArray("productVariants"), BackupCodec::variantFromJson)
                .forEach { db.productVariantDao().upsert(it) }
            BackupCodec.listFrom(json.optJSONArray("inventoryItems"), BackupCodec::inventoryItemFromJson)
                .forEach { db.inventoryItemDao().upsert(it) }
            BackupCodec.listFrom(json.optJSONArray("recipeLines"), BackupCodec::recipeLineFromJson)
                .groupBy { it.variantId }
                .forEach { (variantId, lines) -> db.recipeLineDao().replaceForVariant(variantId, lines) }
            BackupCodec.listFrom(json.optJSONArray("purchases"), BackupCodec::purchaseFromJson)
                .forEach { db.purchaseDao().upsert(it) }
            BackupCodec.listFrom(json.optJSONArray("purchaseLines"), BackupCodec::purchaseLineFromJson)
                .groupBy { it.purchaseId }
                .forEach { (_, lines) -> db.purchaseDao().insertLines(lines) }
            BackupCodec.listFrom(json.optJSONArray("stockMovements"), BackupCodec::movementFromJson)
                .let { db.stockMovementDao().insertAll(it) }
            BackupCodec.listFrom(json.optJSONArray("sales"), BackupCodec::saleFromJson)
                .forEach { db.saleDao().insertSaleIfAbsent(it) }
            BackupCodec.listFrom(json.optJSONArray("saleItems"), BackupCodec::saleItemFromJson)
                .let { db.saleDao().insertItems(it) }
            BackupCodec.listFrom(json.optJSONArray("saleItemDeductions"), BackupCodec::deductionFromJson)
                .let { db.saleDao().insertDeductions(it) }
            BackupCodec.listFrom(json.optJSONArray("returns"), BackupCodec::returnFromJson)
                .forEach { db.returnDao().insert(it) }
            BackupCodec.listFrom(json.optJSONArray("returnItems"), BackupCodec::returnItemFromJson)
                .let { db.returnDao().insertItems(it) }
            BackupCodec.listFrom(json.optJSONArray("expenses"), BackupCodec::expenseFromJson)
                .forEach { db.expenseDao().upsert(it) }
            BackupCodec.listFrom(json.optJSONArray("goals"), BackupCodec::goalFromJson)
                .forEach { db.goalDao().upsert(it) }
        }

        return RestoreResult.Ok(safetyBackupFile.absolutePath)
    }
}
