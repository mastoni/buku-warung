package id.skmnetwork.bukuwarung.domain.checkout

import androidx.room.withTransaction
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.local.entity.FulfillmentMode
import id.skmnetwork.bukuwarung.data.repository.DigitalTransactionRepository
import id.skmnetwork.bukuwarung.data.repository.SaleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CheckoutOrchestrator(
    private val appDatabase: AppDatabase,
    private val saleRepository: SaleRepository,
    private val digitalTransactionRepository: DigitalTransactionRepository,
    private val beforeDigitalDrafts: suspend (List<Long>) -> Unit = {}
) {
    suspend fun processCheckout(
        cartLines: List<CartLine>,
        paymentMethod: String,
        customerId: Long? = null,
        discountAmount: Long = 0L
    ): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val normalizedLines = cartLines.aggregateByCheckoutIdentity()
            validateCheckout(normalizedLines, paymentMethod, customerId)
            appDatabase.withTransaction {
                val requests = normalizedLines.map { it.toRequest() }
                val commit = saleRepository.completeSaleInTransaction(
                    cartItems = requests,
                    paymentMethod = paymentMethod,
                    customerId = customerId,
                    discountAmount = discountAmount
                )
                beforeDigitalDrafts(commit.saleItemIds)
                normalizedLines.zip(commit.saleItemIds).forEach { (line, saleItemId) ->
                    if (line.isDigitalProvider()) {
                        digitalTransactionRepository.createDraft(
                            saleItemId = saleItemId,
                            providerId = line.product.digitalProviderId.orEmpty(),
                            providerProductCode = line.product.digitalProductCode.orEmpty(),
                            destinationNumber = line.destinationNumber?.trim().orEmpty(),
                            sellingPrice = line.product.sellingPrice * line.quantity.toLong(),
                            actualPurchasePrice = line.product.purchasePrice * line.quantity.toLong()
                        )
                    }
                }
                commit.saleId
            }
        }
    }

    private fun validateCheckout(
        cartLines: List<CartLine>,
        paymentMethod: String,
        customerId: Long?
    ) {
        require(cartLines.isNotEmpty()) { "Keranjang kosong" }
        require(paymentMethod in setOf("CASH", "QRIS", "CREDIT")) {
            "Metode pembayaran tidak didukung"
        }
        require(paymentMethod != "CREDIT" || customerId != null) {
            "Pelanggan hutang wajib dipilih"
        }
        cartLines.forEach { line ->
            require(line.quantity.isFinite() && line.quantity > 0.0) {
                "Quantity harus lebih besar dari 0"
            }
            if (line.isDigitalProvider()) {
                require(!line.destinationNumber.isNullOrBlank()) {
                    "Nomor tujuan digital wajib diisi"
                }
                require(line.product.fulfillmentMode == FulfillmentMode.PROVIDER.name)
                require(!line.product.digitalProviderId.isNullOrBlank()) {
                    "Provider digital produk belum dikonfigurasi"
                }
                require(!line.product.digitalProductCode.isNullOrBlank()) {
                    "Kode produk digital belum dikonfigurasi"
                }
            }
        }
    }
}
