package id.skmnetwork.bukuwarung.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import id.skmnetwork.bukuwarung.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCategory(category: CategoryEntity): Long

    @Update
    suspend fun updateCategory(category: CategoryEntity)

    @Delete
    suspend fun deleteCategory(category: CategoryEntity)

    @Query("UPDATE categories SET is_deleted = 1, deleted_at = :deletedAt WHERE id = :categoryId")
    suspend fun softDeleteCategory(categoryId: Long, deletedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM categories WHERE is_deleted = 0 ORDER BY name ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id AND is_deleted = 0")
    suspend fun getCategoryById(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE uuid = :uuid AND is_deleted = 0 LIMIT 1")
    suspend fun getCategoryByUuid(uuid: String): CategoryEntity?

    @Query("SELECT * FROM categories WHERE name = :name AND is_deleted = 0 LIMIT 1")
    suspend fun getCategoryByName(name: String): CategoryEntity?

    @Query("SELECT * FROM categories WHERE LOWER(name) = LOWER(:name) AND is_deleted = 0 LIMIT 1")
    suspend fun getCategoryByNameIgnoreCase(name: String): CategoryEntity?
}