package id.skmnetwork.bukuwarung.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "purchase_items",
    foreignKeys = [
        ForeignKey(
            entity = PurchaseTransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transaction_id"],
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
        Index(value = ["transaction_id"]),
        Index(value = ["product_id"]),
        Index(value = ["purchase_uuid"]),
        Index(value = ["product_uuid"]),
        Index(value = ["business_id"])
    ]
)
data class PurchaseItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "uuid")
    val uuid: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "business_id")
    val businessId: String = "LEGACY_BUSINESS",

    @ColumnInfo(name = "purchase_uuid")
    val purchaseUuid: String = "",

    @ColumnInfo(name = "product_uuid")
    val productUuid: String = "",

    @ColumnInfo(name = "transaction_id")
    val transactionId: Long,

    @ColumnInfo(name = "product_id")
    val productId: Long,

    @ColumnInfo(name = "product_name")
    val productName: String,

    @ColumnInfo(name = "quantity")
    val quantity: Double,

    @ColumnInfo(name = "purchase_price")
    val purchasePrice: Long,

    @ColumnInfo(name = "subtotal")
    val subtotal: Long
)
