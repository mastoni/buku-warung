package id.skmnetwork.bukuwarung.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "stock_movements",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["product_uuid"]),
        Index(value = ["business_id"]),
        Index(value = ["created_at"])
    ]
)
data class StockMovementEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "uuid")
    val uuid: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "business_id")
    val businessId: String = "LEGACY_BUSINESS",

    @ColumnInfo(name = "device_id")
    val deviceId: String = "LEGACY_DEVICE",

    @ColumnInfo(name = "product_uuid")
    val productUuid: String,

    @ColumnInfo(name = "movement_type")
    val movementType: String, // INITIAL, SALE, PURCHASE, ADJUSTMENT, RETURN, OPNAME

    @ColumnInfo(name = "delta_quantity")
    val deltaQuantity: Double,

    @ColumnInfo(name = "current_stock_snapshot")
    val currentStockSnapshot: Double,

    @ColumnInfo(name = "reference_uuid")
    val referenceUuid: String? = null,

    @ColumnInfo(name = "note")
    val note: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
