package id.skmnetwork.bukuwarung.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import id.skmnetwork.bukuwarung.data.local.entity.SupplierEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SupplierDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSupplier(supplier: SupplierEntity): Long

    @Update
    suspend fun updateSupplier(supplier: SupplierEntity)

    @Delete
    suspend fun deleteSupplier(supplier: SupplierEntity)

    @Query("UPDATE suppliers SET is_deleted = 1, deleted_at = :deletedAt, updated_at = :deletedAt WHERE id = :supplierId AND business_id = :businessId")
    suspend fun softDeleteSupplier(supplierId: Long, deletedAt: Long = System.currentTimeMillis(), businessId: String)

    @Query("SELECT * FROM suppliers WHERE business_id = :businessId AND is_deleted = 0 ORDER BY name ASC")
    fun getAllSuppliers(businessId: String): Flow<List<SupplierEntity>>

    @Query("SELECT * FROM suppliers WHERE id = :id AND business_id = :businessId AND is_deleted = 0")
    suspend fun getSupplierById(id: Long, businessId: String): SupplierEntity?

    @Query("SELECT * FROM suppliers WHERE uuid = :uuid AND business_id = :businessId AND is_deleted = 0 LIMIT 1")
    suspend fun getSupplierByUuid(uuid: String, businessId: String): SupplierEntity?

    @Query("SELECT * FROM suppliers WHERE name LIKE '%' || :query || '%' AND business_id = :businessId AND is_deleted = 0 ORDER BY name ASC")
    fun searchSuppliers(query: String, businessId: String): Flow<List<SupplierEntity>>
}
