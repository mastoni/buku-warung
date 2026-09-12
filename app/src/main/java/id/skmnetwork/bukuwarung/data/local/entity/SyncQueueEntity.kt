package id.skmnetwork.bukuwarung.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "sync_queue",
    indices = [
        Index(value = ["sync_id"], unique = true),
        Index(value = ["status"]),
        Index(value = ["business_id"]),
        Index(value = ["created_at"]),
        Index(value = ["entity_type", "entity_uuid"])
    ]
)
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "sync_id")
    val syncId: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "business_id")
    val businessId: String = "LEGACY_BUSINESS",

    @ColumnInfo(name = "device_id")
    val deviceId: String = "LEGACY_DEVICE",

    @ColumnInfo(name = "entity_type")
    val entityType: String, // PRODUCT, CATEGORY, SALE, PURCHASE, CUSTOMER, SUPPLIER, DEBT_PAYMENT, SUPPLIER_PAYMENT, CASH_TRANSACTION, STOCK_ADJUSTMENT

    @ColumnInfo(name = "entity_uuid")
    val entityUuid: String,

    @ColumnInfo(name = "operation")
    val operation: String, // INSERT, UPDATE, DELETE

    @ColumnInfo(name = "payload_json")
    val payloadJson: String? = null,

    @ColumnInfo(name = "status")
    val status: String = "PENDING", // PENDING, PROCESSING, SYNCED, FAILED

    @ColumnInfo(name = "attempt_count")
    val attemptCount: Int = 0,

    @ColumnInfo(name = "last_error")
    val lastError: String? = null,

    @ColumnInfo(name = "next_attempt_at")
    val nextAttemptAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
