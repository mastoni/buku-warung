package id.skmnetwork.bukuwarung.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import id.skmnetwork.bukuwarung.data.local.entity.PurchaseOrderEntity
import id.skmnetwork.bukuwarung.data.local.entity.PurchaseOrderItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PurchaseOrderDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPurchaseOrder(order: PurchaseOrderEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPurchaseOrderItems(items: List<PurchaseOrderItemEntity>)

    @Query("SELECT * FROM purchase_orders ORDER BY created_at DESC")
    fun getAllPurchaseOrders(): Flow<List<PurchaseOrderEntity>>

    @Query("SELECT * FROM purchase_orders ORDER BY created_at DESC")
    suspend fun getAllPurchaseOrdersList(): List<PurchaseOrderEntity>

    @Query("SELECT * FROM purchase_orders WHERE id = :id LIMIT 1")
    suspend fun getPurchaseOrderById(id: Long): PurchaseOrderEntity?

    @Query("SELECT * FROM purchase_orders WHERE uuid = :uuid LIMIT 1")
    suspend fun getPurchaseOrderByUuid(uuid: String): PurchaseOrderEntity?

    @Query("SELECT * FROM purchase_orders WHERE order_number = :orderNumber LIMIT 1")
    suspend fun getPurchaseOrderByOrderNumber(orderNumber: String): PurchaseOrderEntity?

    @Query("SELECT * FROM purchase_orders WHERE status = :status ORDER BY created_at DESC")
    fun getPurchaseOrdersByStatus(status: String): Flow<List<PurchaseOrderEntity>>

    @Query("SELECT * FROM purchase_orders WHERE supplier_id = :supplierId ORDER BY created_at DESC")
    fun getPurchaseOrdersBySupplier(supplierId: Long): Flow<List<PurchaseOrderEntity>>

    @Query("SELECT * FROM purchase_order_items WHERE purchase_order_id = :purchaseOrderId")
    suspend fun getItemsForPurchaseOrder(purchaseOrderId: Long): List<PurchaseOrderItemEntity>

    @Query("SELECT * FROM purchase_order_items WHERE po_uuid = :poUuid")
    suspend fun getItemsForPurchaseOrderByUuid(poUuid: String): List<PurchaseOrderItemEntity>

    @Query("DELETE FROM purchase_order_items WHERE purchase_order_id = :purchaseOrderId")
    suspend fun deleteItemsForPurchaseOrder(purchaseOrderId: Long)

    @Update
    suspend fun updatePurchaseOrder(order: PurchaseOrderEntity)

    @Query("UPDATE purchase_orders SET status = :status, updated_at = :updatedAt WHERE id = :id")
    suspend fun updatePurchaseOrderStatus(id: Long, status: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE purchase_orders SET status = :status, sent_at = :sentAt, updated_at = :updatedAt WHERE id = :id")
    suspend fun updatePurchaseOrderToOrdered(id: Long, status: String, sentAt: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE purchase_orders SET status = :status, received_at = :receivedAt, final_purchase_id = :finalPurchaseId, updated_at = :updatedAt WHERE id = :id")
    suspend fun updatePurchaseOrderToReceived(id: Long, status: String, receivedAt: Long, finalPurchaseId: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE purchase_order_items SET received_quantity = :receivedQuantity WHERE id = :id")
    suspend fun updatePurchaseOrderItemReceivedQuantity(id: Long, receivedQuantity: Double)

    @Delete
    suspend fun deletePurchaseOrder(order: PurchaseOrderEntity)
}
