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

    @Query("SELECT * FROM purchase_orders WHERE business_id = :businessId ORDER BY created_at DESC")
    fun getAllPurchaseOrders(businessId: String): Flow<List<PurchaseOrderEntity>>

    @Query("SELECT * FROM purchase_orders WHERE business_id = :businessId ORDER BY created_at DESC")
    suspend fun getAllPurchaseOrdersList(businessId: String): List<PurchaseOrderEntity>

    @Query("SELECT * FROM purchase_orders WHERE id = :id AND business_id = :businessId LIMIT 1")
    suspend fun getPurchaseOrderById(id: Long, businessId: String): PurchaseOrderEntity?

    @Query("SELECT * FROM purchase_orders WHERE uuid = :uuid AND business_id = :businessId LIMIT 1")
    suspend fun getPurchaseOrderByUuid(uuid: String, businessId: String): PurchaseOrderEntity?

    @Query("SELECT * FROM purchase_orders WHERE order_number = :orderNumber AND business_id = :businessId LIMIT 1")
    suspend fun getPurchaseOrderByOrderNumber(orderNumber: String, businessId: String): PurchaseOrderEntity?

    @Query("SELECT * FROM purchase_orders WHERE business_id = :businessId AND status = :status ORDER BY created_at DESC")
    fun getPurchaseOrdersByStatus(businessId: String, status: String): Flow<List<PurchaseOrderEntity>>

    @Query("SELECT * FROM purchase_orders WHERE business_id = :businessId AND supplier_id = :supplierId ORDER BY created_at DESC")
    fun getPurchaseOrdersBySupplier(businessId: String, supplierId: Long): Flow<List<PurchaseOrderEntity>>

    @Query("SELECT * FROM purchase_order_items WHERE purchase_order_id = :purchaseOrderId AND business_id = :businessId")
    suspend fun getItemsForPurchaseOrder(businessId: String, purchaseOrderId: Long): List<PurchaseOrderItemEntity>

    @Query("SELECT * FROM purchase_order_items WHERE po_uuid = :poUuid AND business_id = :businessId")
    suspend fun getItemsForPurchaseOrderByUuid(businessId: String, poUuid: String): List<PurchaseOrderItemEntity>

    @Query("DELETE FROM purchase_order_items WHERE purchase_order_id = :purchaseOrderId AND business_id = :businessId")
    suspend fun deleteItemsForPurchaseOrder(businessId: String, purchaseOrderId: Long)

    @Update
    suspend fun updatePurchaseOrder(order: PurchaseOrderEntity)

    @Query("UPDATE purchase_orders SET status = :status, updated_at = :updatedAt WHERE id = :id AND business_id = :businessId")
    suspend fun updatePurchaseOrderStatus(id: Long, status: String, updatedAt: Long = System.currentTimeMillis(), businessId: String)

    @Query("UPDATE purchase_orders SET status = :status, sent_at = :sentAt, updated_at = :updatedAt WHERE id = :id AND business_id = :businessId")
    suspend fun updatePurchaseOrderToOrdered(id: Long, status: String, sentAt: Long, updatedAt: Long = System.currentTimeMillis(), businessId: String)

    @Query("UPDATE purchase_orders SET status = :status, received_at = :receivedAt, final_purchase_id = :finalPurchaseId, updated_at = :updatedAt WHERE id = :id AND business_id = :businessId")
    suspend fun updatePurchaseOrderToReceived(id: Long, status: String, receivedAt: Long, finalPurchaseId: Long, updatedAt: Long = System.currentTimeMillis(), businessId: String)

    @Query("UPDATE purchase_order_items SET received_quantity = :receivedQuantity WHERE id = :id AND business_id = :businessId")
    suspend fun updatePurchaseOrderItemReceivedQuantity(id: Long, receivedQuantity: Double, businessId: String)

    @Delete
    suspend fun deletePurchaseOrder(order: PurchaseOrderEntity)
}
