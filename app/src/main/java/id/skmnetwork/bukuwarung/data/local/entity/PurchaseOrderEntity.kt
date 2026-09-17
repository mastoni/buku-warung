package id.skmnetwork.bukuwarung.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "purchase_orders",
    foreignKeys = [
        ForeignKey(
            entity = SupplierEntity::class,
            parentColumns = ["id"],
            childColumns = ["supplier_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["order_number"], unique = true),
        Index(value = ["supplier_id"]),
        Index(value = ["status"]),
        Index(value = ["business_id"]),
        Index(value = ["created_at"])
    ]
)
data class PurchaseOrderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "uuid")
    val uuid: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "business_id")
    val businessId: String = "LEGACY_BUSINESS",

    @ColumnInfo(name = "device_id")
    val deviceId: String = "LEGACY_DEVICE",

    @ColumnInfo(name = "order_number")
    val orderNumber: String,

    @ColumnInfo(name = "supplier_id")
    val supplierId: Long,

    @ColumnInfo(name = "supplier_name_snapshot")
    val supplierNameSnapshot: String,

    @ColumnInfo(name = "supplier_phone_snapshot")
    val supplierPhoneSnapshot: String? = null,

    @ColumnInfo(name = "status")
    val status: String = PurchaseOrderStatus.DRAFT.name,

    @ColumnInfo(name = "total_estimated_amount")
    val totalEstimatedAmount: Long = 0L,

    @ColumnInfo(name = "notes")
    val notes: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "sent_at")
    val sentAt: Long? = null,

    @ColumnInfo(name = "received_at")
    val receivedAt: Long? = null,

    @ColumnInfo(name = "final_purchase_id")
    val finalPurchaseId: Long? = null
)
