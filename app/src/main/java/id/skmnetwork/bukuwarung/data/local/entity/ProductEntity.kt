package id.skmnetwork.bukuwarung.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "products",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["category_id"]),
        Index(value = ["barcode"]),
        Index(value = ["business_id"]),
        Index(value = ["is_deleted"])
    ]
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "uuid")
    val uuid: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "business_id")
    val businessId: String = "LEGACY_BUSINESS",

    @ColumnInfo(name = "category_id")
    val categoryId: Long,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "purchase_price")
    val purchasePrice: Long,

    @ColumnInfo(name = "selling_price")
    val sellingPrice: Long,

    @ColumnInfo(name = "stock")
    val stock: Double,

    @ColumnInfo(name = "minimum_stock")
    val minimumStock: Double = 0.0,

    @ColumnInfo(name = "unit")
    val unit: String = "pcs",

    @ColumnInfo(name = "item_type")
    val itemType: String = ItemType.PHYSICAL.name,

    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean = false,

    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "barcode")
    val barcode: String? = null,

    @ColumnInfo(name = "image_uri")
    val imageUri: String? = null
)
