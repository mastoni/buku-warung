package id.skmnetwork.bukuwarung.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "sales_transactions",
    foreignKeys = [
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customer_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["customer_id"]),
        Index(value = ["business_id"]),
        Index(value = ["transaction_date"])
    ]
)
data class SaleTransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "uuid")
    val uuid: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "business_id")
    val businessId: String = "LEGACY_BUSINESS",

    @ColumnInfo(name = "device_id")
    val deviceId: String = "LEGACY_DEVICE",

    @ColumnInfo(name = "transaction_number")
    val transactionNumber: String,

    @ColumnInfo(name = "transaction_date")
    val transactionDate: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "total_amount")
    val totalAmount: Long,

    @ColumnInfo(name = "payment_method")
    val paymentMethod: String = "CASH",

    @ColumnInfo(name = "discount_amount")
    val discountAmount: Long = 0L,

    @ColumnInfo(name = "subtotal_amount")
    val subtotalAmount: Long = 0L,

    @ColumnInfo(name = "taxable_base_snapshot")
    val taxableBaseSnapshot: Long = 0L,

    @ColumnInfo(name = "tax_rate_snapshot")
    val taxRateSnapshot: Double = 0.0,

    @ColumnInfo(name = "tax_amount_snapshot")
    val taxAmountSnapshot: Long = 0L,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "customer_id")
    val customerId: Long? = null
)
