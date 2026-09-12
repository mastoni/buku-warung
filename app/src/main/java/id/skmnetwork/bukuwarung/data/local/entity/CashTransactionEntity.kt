package id.skmnetwork.bukuwarung.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "cash_transactions",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["business_id"]),
        Index(value = ["ref_id"]),
        Index(value = ["ref_uuid"]),
        Index(value = ["created_at"])
    ]
)
data class CashTransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "uuid")
    val uuid: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "business_id")
    val businessId: String = "LEGACY_BUSINESS",

    @ColumnInfo(name = "device_id")
    val deviceId: String = "LEGACY_DEVICE",

    @ColumnInfo(name = "type")
    val type: String, // INCOME or EXPENSE

    @ColumnInfo(name = "amount")
    val amount: Long,

    @ColumnInfo(name = "description")
    val description: String,

    @ColumnInfo(name = "ref_id")
    val refId: Long? = null,

    @ColumnInfo(name = "ref_uuid")
    val refUuid: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
