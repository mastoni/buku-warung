package id.skmnetwork.bukuwarung.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "digital_transactions",
    foreignKeys = [
        ForeignKey(
            entity = SaleItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["sale_item_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["sale_item_id"]),
        Index(value = ["status"]),
        Index(value = ["business_id"])
    ]
)
data class DigitalTransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "uuid")
    val uuid: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "business_id")
    val businessId: String = "LEGACY_BUSINESS",

    @ColumnInfo(name = "sale_item_id")
    val saleItemId: Long,

    @ColumnInfo(name = "provider_id")
    val providerId: String,

    @ColumnInfo(name = "provider_product_code")
    val providerProductCode: String,

    @ColumnInfo(name = "destination_number")
    val destinationNumber: String,

    @ColumnInfo(name = "selling_price")
    val sellingPrice: Long,

    @ColumnInfo(name = "actual_purchase_price")
    val actualPurchasePrice: Long,

    @ColumnInfo(name = "status")
    val status: String = DigitalTransactionStatus.DRAFT.name,

    @ColumnInfo(name = "provider_reference_id")
    val providerReferenceId: String? = null,

    @ColumnInfo(name = "sn_token")
    val snToken: String? = null,

    @ColumnInfo(name = "failure_reason")
    val failureReason: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
