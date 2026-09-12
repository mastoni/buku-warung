package id.skmnetwork.bukuwarung.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "sale_return_transactions",
    foreignKeys = [
        ForeignKey(
            entity = SaleTransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sale_transaction_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customer_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["sale_transaction_id"]),
        Index(value = ["sale_uuid"]),
        Index(value = ["customer_id"]),
        Index(value = ["customer_uuid"]),
        Index(value = ["business_id"]),
        Index(value = ["return_date"])
    ]
)
data class SaleReturnTransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "uuid")
    val uuid: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "business_id")
    val businessId: String = "LEGACY_BUSINESS",

    @ColumnInfo(name = "device_id")
    val deviceId: String = "LEGACY_DEVICE",

    @ColumnInfo(name = "return_number")
    val returnNumber: String,

    @ColumnInfo(name = "return_date")
    val returnDate: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "sale_transaction_id")
    val saleTransactionId: Long,

    @ColumnInfo(name = "sale_uuid")
    val saleUuid: String = "",

    @ColumnInfo(name = "customer_id")
    val customerId: Long? = null,

    @ColumnInfo(name = "customer_uuid")
    val customerUuid: String? = null,

    @ColumnInfo(name = "total_refund_amount")
    val totalRefundAmount: Long,

    @ColumnInfo(name = "refund_method")
    val refundMethod: String = "CASH", // CASH, CREDIT

    @ColumnInfo(name = "reason")
    val reason: String? = null,

    @ColumnInfo(name = "notes")
    val notes: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
