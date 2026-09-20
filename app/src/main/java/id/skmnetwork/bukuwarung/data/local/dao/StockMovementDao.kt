package id.skmnetwork.bukuwarung.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import id.skmnetwork.bukuwarung.data.local.entity.StockMovementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StockMovementDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMovement(movement: StockMovementEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMovements(movements: List<StockMovementEntity>)

    @Query("SELECT * FROM stock_movements WHERE business_id = :businessId AND product_uuid = :productUuid ORDER BY created_at ASC")
    fun getMovementsForProduct(businessId: String, productUuid: String): Flow<List<StockMovementEntity>>

    @Query("SELECT * FROM stock_movements WHERE business_id = :businessId AND product_uuid = :productUuid ORDER BY created_at ASC")
    suspend fun getMovementsListForProduct(businessId: String, productUuid: String): List<StockMovementEntity>

    @Query("SELECT COALESCE(SUM(delta_quantity), 0.0) FROM stock_movements WHERE business_id = :businessId AND product_uuid = :productUuid")
    suspend fun getCalculatedStockForProduct(businessId: String, productUuid: String): Double

    @Query("SELECT * FROM stock_movements WHERE business_id = :businessId ORDER BY created_at DESC")
    fun getAllMovements(businessId: String): Flow<List<StockMovementEntity>>

    @Query("SELECT COUNT(*) FROM stock_movements WHERE business_id = :businessId")
    suspend fun getMovementCount(businessId: String): Int
}
