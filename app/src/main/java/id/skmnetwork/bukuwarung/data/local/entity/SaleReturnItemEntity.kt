package id.skmnetwork.bukuwarung.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "sale_return_items",
    foreignKeys = [
        ForeignKey(
            entity = SaleReturnTransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["return_transaction_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SaleItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["sale_item_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["product_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["return_transaction_id"]),
        Index(value = ["sale_item_id"]),
        Index(value = ["product_id"]),
        Index(value = ["return_uuid"]),
        Index(value = ["sale_item_uuid"]),
        Index(value = ["product_uuid"]),
        Index(value = ["business_id"])
    ]
)
data class SaleReturnItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "uuid")
    val uuid: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "business_id")
    val businessId: String = "LEGACY_BUSINESS",

    @ColumnInfo(name = "return_uuid")
    val returnUuid: String = "",

    @ColumnInfo(name = "sale_item_uuid")
    val saleItemUuid: String = "",

    @ColumnInfo(name = "product_uuid")
    val productUuid: String = "",

    @ColumnInfo(name = "return_transaction_id")
    val returnTransactionId: Long,

    @ColumnInfo(name = "sale_item_id")
    val saleItemId: Long,

    @ColumnInfo(name = "product_id")
    val productId: Long,

    @ColumnInfo(name = "product_name")
    val productName: String,

    @ColumnInfo(name = "quantity")
    val quantity: Double,

    @ColumnInfo(name = "price")
    val price: Long,

    @ColumnInfo(name = "purchase_price")
    val purchasePrice: Long = 0L,

    @ColumnInfo(name = "subtotal")
    val subtotal: Long,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
