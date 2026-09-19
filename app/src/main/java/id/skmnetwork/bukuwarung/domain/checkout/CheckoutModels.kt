package id.skmnetwork.bukuwarung.domain.checkout

import id.skmnetwork.bukuwarung.data.local.entity.FulfillmentMode
import id.skmnetwork.bukuwarung.data.local.entity.ItemType
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import java.util.UUID

data class CartLine(
    val lineId: String = UUID.randomUUID().toString(),
    val product: ProductEntity,
    val quantity: Double,
    val destinationNumber: String? = null
)

data class CartLineKey(
    val productId: Long,
    val destinationNumber: String?
)

data class CartLineRequest(
    val productId: Long,
    val quantity: Double,
    val destinationNumber: String? = null
)

data class SaleCommitResult(
    val saleId: Long,
    val saleItemIds: List<Long>
)

fun CartLine.isDigitalProvider(): Boolean {
    return product.itemType == ItemType.DIGITAL.name &&
            product.fulfillmentMode == FulfillmentMode.PROVIDER.name
}

fun CartLine.checkoutKey(): CartLineKey {
    return if (isDigitalProvider()) {
        CartLineKey(
            productId = product.id,
            destinationNumber = destinationNumber?.trim()?.ifEmpty { null }
        )
    } else {
        CartLineKey(productId = product.id, destinationNumber = null)
    }
}

fun CartLine.toRequest(): CartLineRequest {
    return CartLineRequest(
        productId = product.id,
        quantity = quantity,
        destinationNumber = destinationNumber?.trim()?.ifEmpty { null }
    )
}

fun List<CartLine>.aggregateByCheckoutIdentity(): List<CartLine> {
    val aggregated = mutableListOf<CartLine>()
    forEach { line ->
        if (!line.quantity.isFinite() || line.quantity <= 0.0) {
            throw IllegalArgumentException("Quantity harus lebih besar dari 0")
        }
        val key = line.checkoutKey()
        val existingIndex = aggregated.indexOfFirst { it.checkoutKey() == key }
        if (existingIndex >= 0) {
            val existing = aggregated[existingIndex]
            aggregated[existingIndex] = existing.copy(quantity = existing.quantity + line.quantity)
        } else {
            aggregated.add(line)
        }
    }
    return aggregated
}
