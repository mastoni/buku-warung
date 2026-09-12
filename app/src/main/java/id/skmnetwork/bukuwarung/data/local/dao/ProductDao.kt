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

    @Query("UPDATE products SET is_deleted = 1, deleted_at = :deletedAt, updated_at = :deletedAt WHERE id = :productId")
    suspend fun softDeleteProduct(productId: Long, deletedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM products WHERE is_deleted = 0 ORDER BY name ASC")
    fun getAllProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE id = :id AND is_deleted = 0")
    suspend fun getProductById(id: Long): ProductEntity?

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getProductByIdRaw(id: Long): ProductEntity?

    @Query("SELECT * FROM products WHERE uuid = :uuid AND is_deleted = 0 LIMIT 1")
    suspend fun getProductByUuid(uuid: String): ProductEntity?

    @Query("SELECT * FROM products WHERE uuid = :uuid LIMIT 1")
    suspend fun getProductByUuidRaw(uuid: String): ProductEntity?

    @Query("SELECT * FROM products WHERE barcode = :barcode AND barcode IS NOT NULL AND barcode != '' AND is_deleted = 0 LIMIT 1")
    suspend fun getProductByBarcode(barcode: String): ProductEntity?

    @Query("SELECT * FROM products WHERE category_id = :categoryId AND is_deleted = 0 ORDER BY name ASC")
    fun getProductsByCategory(categoryId: Long): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE stock <= minimum_stock AND is_deleted = 0 ORDER BY stock ASC")
    fun getLowStockProducts(): Flow<List<ProductEntity>>

    @Query("UPDATE products SET stock = CASE WHEN (stock - :quantity) < 0 THEN 0 ELSE (stock - :quantity) END, updated_at = :updatedAt WHERE id = :productId")
    suspend fun deductProductStock(productId: Long, quantity: Double, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE products SET stock = stock + :quantity, updated_at = :updatedAt WHERE id = :productId")
    suspend fun addProductStock(productId: Long, quantity: Double, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT SUM(CAST(stock AS REAL) * CAST(purchase_price AS INTEGER)) FROM products WHERE is_deleted = 0")
    fun getTotalStockValue(): Flow<Long?>
}
