package id.skmnetwork.bukuwarung.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "supplier_payables",
    foreignKeys = [
        ForeignKey(
            entity = SupplierEntity::class,
            parentColumns = ["id"],
            childColumns = ["supplier_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = PurchaseTransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["purchase_transaction_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["supplier_id"]),
        Index(value = ["purchase_transaction_id"]),
        Index(value = ["supplier_uuid"]),
        Index(value = ["purchase_uuid"]),
        Index(value = ["business_id"])
    ]
)
data class SupplierPayableEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "uuid")
    val uuid: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "business_id")
    val businessId: String = "LEGACY_BUSINESS",

    @ColumnInfo(name = "supplier_id")
    val supplierId: Long,

    @ColumnInfo(name = "purchase_transaction_id")
    val purchaseTransactionId: Long? = null,

    @ColumnInfo(name = "supplier_uuid")
    val supplierUuid: String = "",

    @ColumnInfo(name = "purchase_uuid")
    val purchaseUuid: String = "",

    @ColumnInfo(name = "total_debt")
    val totalDebt: Long,

    @ColumnInfo(name = "paid_amount")
    val paidAmount: Long = 0L,

    @ColumnInfo(name = "status")
    val status: String = "OPEN", // "OPEN", "PAID"

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
