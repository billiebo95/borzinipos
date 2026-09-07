package com.borzini.pos.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.borzini.pos.data.db.entities.SaleEntity
import com.borzini.pos.data.db.entities.SaleItemDeductionEntity
import com.borzini.pos.data.db.entities.SaleItemEntity
import kotlinx.coroutines.flow.Flow

data class TopProductRow(val productId: String?, val productNameSnapshot: String, val totalQuantity: Int)
data class CategorySalesRow(val categoryId: String?, val totalKopecks: Long)

@Dao
interface SaleDao {
    /**
     * Returns the inserted SQLite rowid, or -1 if the row was ignored because [SaleEntity.id]
     * (the checkout's idempotency key) already exists. Callers MUST check for -1 to tell
     * "this sale was just created" apart from "this sale already existed" - both are success
     * from the checkout screen's point of view, but only the first one should also insert items,
     * deductions and stock movements (see SaleRepository.completeSale).
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSaleIfAbsent(sale: SaleEntity): Long

    @Query("SELECT * FROM sales WHERE id = :id")
    suspend fun getById(id: String): SaleEntity?

    @Query("SELECT * FROM sales WHERE id = :id")
    fun observeById(id: String): Flow<SaleEntity?>

    @Query(
        "SELECT * FROM sales WHERE createdAt >= :start AND createdAt < :end AND (isDemo = 0 OR :includeDemo = 1) " +
            "ORDER BY createdAt DESC",
    )
    fun observeInRange(start: Long, end: Long, includeDemo: Boolean): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales WHERE receiptNumber = :receiptNumber LIMIT 1")
    suspend fun getByReceiptNumber(receiptNumber: Long): SaleEntity?

    @Query("SELECT COALESCE(MAX(receiptNumber), 0) + 1 FROM sales")
    suspend fun nextReceiptNumber(): Long

    @Insert
    suspend fun insertItems(items: List<SaleItemEntity>)

    @Query("SELECT * FROM sale_items WHERE saleId = :saleId")
    fun observeItemsForSale(saleId: String): Flow<List<SaleItemEntity>>

    @Query("SELECT * FROM sale_items WHERE saleId = :saleId")
    suspend fun getItemsForSaleOnce(saleId: String): List<SaleItemEntity>

    @Query("SELECT * FROM sale_items WHERE id = :id")
    suspend fun getItemById(id: String): SaleItemEntity?

    @Insert
    suspend fun insertDeductions(deductions: List<SaleItemDeductionEntity>)

    @Query("SELECT * FROM sale_item_deductions WHERE saleItemId = :saleItemId")
    suspend fun getDeductionsForItem(saleItemId: String): List<SaleItemDeductionEntity>

    @Query("UPDATE sales SET isFullyReturned = :fully, isPartiallyReturned = :partially WHERE id = :saleId")
    suspend fun updateReturnFlags(saleId: String, fully: Boolean, partially: Boolean)

    @Query("UPDATE sale_items SET returnedQuantity = :returnedQuantity WHERE id = :saleItemId")
    suspend fun updateItemReturnedQuantity(saleItemId: String, returnedQuantity: Int)

    @Query(
        "SELECT * FROM sales WHERE createdAt >= :start AND createdAt < :end AND (isDemo = 0 OR :includeDemo = 1) " +
            "AND (receiptNumber = :queryAsNumber OR id LIKE '%' || :query || '%') ORDER BY createdAt DESC",
    )
    fun searchByNumber(start: Long, end: Long, includeDemo: Boolean, query: String, queryAsNumber: Long): Flow<List<SaleEntity>>

    @Query(
        "SELECT si.productId as productId, si.productNameSnapshot as productNameSnapshot, SUM(si.quantity) as totalQuantity " +
            "FROM sale_items si JOIN sales s ON si.saleId = s.id " +
            "WHERE s.createdAt >= :start AND s.createdAt < :end AND (s.isDemo = 0 OR :includeDemo = 1) " +
            "GROUP BY si.productId, si.productNameSnapshot ORDER BY totalQuantity DESC LIMIT :limit",
    )
    suspend fun topProducts(start: Long, end: Long, includeDemo: Boolean, limit: Int): List<TopProductRow>

    @Query(
        "SELECT p.categoryId as categoryId, SUM(si.lineTotalKopecks) as totalKopecks " +
            "FROM sale_items si JOIN sales s ON si.saleId = s.id LEFT JOIN products p ON si.productId = p.id " +
            "WHERE s.createdAt >= :start AND s.createdAt < :end AND (s.isDemo = 0 OR :includeDemo = 1) " +
            "GROUP BY p.categoryId ORDER BY totalKopecks DESC",
    )
    suspend fun salesByCategory(start: Long, end: Long, includeDemo: Boolean): List<CategorySalesRow>
}
