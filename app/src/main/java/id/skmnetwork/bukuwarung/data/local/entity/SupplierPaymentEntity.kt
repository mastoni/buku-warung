package id.skmnetwork.bukuwarung.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "supplier_payments",
    foreignKeys = [
        ForeignKey(
            entity = SupplierPayableEntity::class,
            parentColumns = ["id"],
            childColumns = ["payable_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["payable_id"]),
        Index(value = ["payable_uuid"]),
        Index(value = ["business_id"]),
        Index(value = ["payment_date"])
    ]
)
data class SupplierPaymentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "uuid")
    val uuid: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "business_id")
    val businessId: String = "LEGACY_BUSINESS",

    @ColumnInfo(name = "device_id")
    val deviceId: String = "LEGACY_DEVICE",

    @ColumnInfo(name = "payable_id")
    val payableId: Long,

    @ColumnInfo(name = "payable_uuid")
    val payableUuid: String = "",

    @ColumnInfo(name = "amount")
    val amount: Long,

    @ColumnInfo(name = "payment_date")
    val paymentDate: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "note")
    val note: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
