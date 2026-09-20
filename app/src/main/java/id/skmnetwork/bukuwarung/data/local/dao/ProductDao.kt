package id.skmnetwork.bukuwarung.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProduct(product: ProductEntity): Long

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Delete
    suspend fun deleteProduct(product: ProductEntity)

    @Query("UPDATE products SET is_deleted = 1, deleted_at = :deletedAt, updated_at = :deletedAt WHERE id = :productId AND business_id = :businessId")
    suspend fun softDeleteProduct(productId: Long, deletedAt: Long = System.currentTimeMillis(), businessId: String)

    @Query("SELECT * FROM products WHERE business_id = :businessId AND is_deleted = 0 ORDER BY name ASC")
    fun getAllProducts(businessId: String): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE id = :id AND business_id = :businessId AND is_deleted = 0")
    suspend fun getProductById(id: Long, businessId: String): ProductEntity?

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getProductByIdRaw(id: Long): ProductEntity?

    @Query("SELECT * FROM products WHERE uuid = :uuid AND business_id = :businessId AND is_deleted = 0 LIMIT 1")
    suspend fun getProductByUuid(uuid: String, businessId: String): ProductEntity?

    @Query("SELECT * FROM products WHERE uuid = :uuid LIMIT 1")
    suspend fun getProductByUuidRaw(uuid: String): ProductEntity?

    @Query("SELECT * FROM products WHERE barcode = :barcode AND barcode IS NOT NULL AND barcode != '' AND business_id = :businessId AND is_deleted = 0 LIMIT 1")
    suspend fun getProductByBarcode(barcode: String, businessId: String): ProductEntity?

    @Query("SELECT * FROM products WHERE business_id = :businessId AND category_id = :categoryId AND is_deleted = 0 ORDER BY name ASC")
    fun getProductsByCategory(businessId: String, categoryId: Long): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE business_id = :businessId AND stock <= minimum_stock AND is_deleted = 0 ORDER BY stock ASC")
    fun getLowStockProducts(businessId: String): Flow<List<ProductEntity>>

    @Query("UPDATE products SET stock = CASE WHEN (stock - :quantity) < 0 THEN 0 ELSE (stock - :quantity) END, updated_at = :updatedAt WHERE id = :productId AND business_id = :businessId")
    suspend fun deductProductStock(productId: Long, quantity: Double, updatedAt: Long = System.currentTimeMillis(), businessId: String)

    @Query("UPDATE products SET stock = stock + :quantity, updated_at = :updatedAt WHERE id = :productId AND business_id = :businessId")
    suspend fun addProductStock(productId: Long, quantity: Double, updatedAt: Long = System.currentTimeMillis(), businessId: String)

    @Query("SELECT SUM(CAST(stock AS REAL) * CAST(purchase_price AS INTEGER)) FROM products WHERE business_id = :businessId AND is_deleted = 0")
    fun getTotalStockValue(businessId: String): Flow<Long?>
}
