package id.skmnetwork.bukuwarung.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "purchase_order_items",
    foreignKeys = [
        ForeignKey(
            entity = PurchaseOrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["purchase_order_id"],
            onDelete = ForeignKey.CASCADE
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
        Index(value = ["purchase_order_id"]),
        Index(value = ["po_uuid"]),
        Index(value = ["product_id"]),
        Index(value = ["product_uuid"]),
        Index(value = ["business_id"])
    ]
)
data class PurchaseOrderItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "uuid")
    val uuid: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "business_id")
    val businessId: String = "LEGACY_BUSINESS",

    @ColumnInfo(name = "purchase_order_id")
    val purchaseOrderId: Long,

    @ColumnInfo(name = "po_uuid")
    val poUuid: String = "",

    @ColumnInfo(name = "product_id")
    val productId: Long,

    @ColumnInfo(name = "product_uuid")
    val productUuid: String = "",

    @ColumnInfo(name = "product_name")
    val productName: String,

    @ColumnInfo(name = "ordered_quantity")
    val orderedQuantity: Double,

    @ColumnInfo(name = "unit")
    val unit: String = "pcs",

    @ColumnInfo(name = "estimated_price")
    val estimatedPrice: Long = 0L,

    @ColumnInfo(name = "estimated_subtotal")
    val estimatedSubtotal: Long = 0L,

    @ColumnInfo(name = "received_quantity")
    val receivedQuantity: Double = 0.0,

    @ColumnInfo(name = "notes")
    val notes: String? = null
)
