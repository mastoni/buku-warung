package id.skmnetwork.bukuwarung.domain.receipt

import id.skmnetwork.bukuwarung.data.local.entity.CustomerEntity
import id.skmnetwork.bukuwarung.data.local.entity.DebtEntity
import id.skmnetwork.bukuwarung.data.local.entity.SaleItemEntity
import id.skmnetwork.bukuwarung.data.local.entity.SaleTransactionEntity
import id.skmnetwork.bukuwarung.data.preferences.UserSettings

object ReceiptMapper {

    fun mapFromSale(
        sale: SaleTransactionEntity,
        items: List<SaleItemEntity>,
        customer: CustomerEntity? = null,
        debt: DebtEntity? = null,
        userSettings: UserSettings? = null,
        cashGiven: Long? = null
    ): ReceiptData {
        val resolvedProfile = if (userSettings != null) {
            id.skmnetwork.bukuwarung.domain.business.BusinessTaxonomyRegistry.resolve(
                userSettings.primaryBusinessType,
                userSettings.secondaryActivities
            )
        } else {
            null
        }

        val shopProfile = if (userSettings != null) {
            ShopProfile(
                shopName = userSettings.shopName,
                ownerName = userSettings.ownerName,
                phone = userSettings.phone,
                address = userSettings.address,
                footerMessage = userSettings.receiptFooterText,
                showShopName = userSettings.showShopNameOnReceipt,
                showAddress = userSettings.showAddressOnReceipt,
                showPhone = userSettings.showPhoneOnReceipt,
                showPaymentMethod = userSettings.showPaymentMethodOnReceipt,
                showChange = userSettings.showChangeOnReceipt,
                customerLabel = resolvedProfile?.terminology?.customerLabel ?: "Pelanggan"
            )
        } else {
            ShopProfile()
        }

        val cashier = userSettings?.deviceName ?: "Kasir"

        val receiptItems = items.map { item ->
            ReceiptItem(
                name = item.productName,
                quantity = item.quantity,
                unit = "pcs",
                price = item.price,
                subtotal = item.subtotal
            )
        }

        val methodUpper = sale.paymentMethod.uppercase()
        val changeAmount = if (methodUpper == "CASH" && cashGiven != null && cashGiven >= sale.totalAmount) {
            cashGiven - sale.totalAmount
        } else {
            null
        }

        val remainingDebt = if (methodUpper == "CREDIT") {
            debt?.let { it.totalDebt - it.paidAmount } ?: sale.totalAmount
        } else {
            null
        }

        val paymentInfo = ReceiptPaymentInfo(
            method = methodUpper,
            totalAmount = sale.totalAmount,
            payAmount = if (methodUpper == "CASH") cashGiven else null,
            changeAmount = changeAmount,
            customerName = customer?.name,
            remainingDebt = remainingDebt
        )

        return ReceiptData(
            receiptNumber = sale.transactionNumber,
            transactionUuid = sale.uuid,
            dateTimeMillis = sale.transactionDate,
            shopProfile = shopProfile,
            cashierName = cashier,
            items = receiptItems,
            paymentInfo = paymentInfo
        )
    }
}
