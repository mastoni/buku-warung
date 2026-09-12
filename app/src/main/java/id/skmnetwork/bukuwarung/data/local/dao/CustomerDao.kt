package id.skmnetwork.bukuwarung.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import id.skmnetwork.bukuwarung.data.local.entity.CustomerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCustomer(customer: CustomerEntity): Long

    @Update
    suspend fun updateCustomer(customer: CustomerEntity)

    @Delete
    suspend fun deleteCustomer(customer: CustomerEntity)

    @Query("UPDATE customers SET is_deleted = 1, deleted_at = :deletedAt, updated_at = :deletedAt WHERE id = :customerId")
    suspend fun softDeleteCustomer(customerId: Long, deletedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM customers WHERE is_deleted = 0 ORDER BY name ASC")
    fun getAllCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE id = :id AND is_deleted = 0")
    suspend fun getCustomerById(id: Long): CustomerEntity?

    @Query("SELECT * FROM customers WHERE uuid = :uuid AND is_deleted = 0 LIMIT 1")
    suspend fun getCustomerByUuid(uuid: String): CustomerEntity?

    @Query("SELECT * FROM customers WHERE name LIKE '%' || :query || '%' AND is_deleted = 0 ORDER BY name ASC")
    fun searchCustomers(query: String): Flow<List<CustomerEntity>>
}
