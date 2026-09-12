package id.skmnetwork.bukuwarung.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "debts",
    foreignKeys = [
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customer_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = SaleTransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sale_transaction_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["customer_id"]),
        Index(value = ["sale_transaction_id"]),
        Index(value = ["customer_uuid"]),
        Index(value = ["sale_uuid"]),
        Index(value = ["business_id"])
    ]
)
data class DebtEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "uuid")
    val uuid: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "business_id")
    val businessId: String = "LEGACY_BUSINESS",

    @ColumnInfo(name = "customer_id")
    val customerId: Long,

    @ColumnInfo(name = "sale_transaction_id")
    val saleTransactionId: Long? = null,

    @ColumnInfo(name = "customer_uuid")
    val customerUuid: String = "",

    @ColumnInfo(name = "sale_uuid")
    val saleUuid: String = "",

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
