package id.skmnetwork.bukuwarung.domain.receipt

import id.skmnetwork.bukuwarung.data.local.entity.CustomerEntity
import id.skmnetwork.bukuwarung.data.local.entity.DebtEntity
import id.skmnetwork.bukuwarung.data.local.entity.SaleItemEntity
import id.skmnetwork.bukuwarung.data.local.entity.SaleReturnItemEntity
import id.skmnetwork.bukuwarung.data.local.entity.SaleReturnTransactionEntity
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

        val grossSubtotal = items.sumOf { it.subtotal }
        val discountAmount = sale.discountAmount
        val subtotalAmount = if (discountAmount > 0L) grossSubtotal else null
        val finalDiscount = if (discountAmount > 0L) discountAmount else null
        val taxableBase = if (sale.taxAmountSnapshot > 0L) sale.taxableBaseSnapshot else null
        val taxAmount = if (sale.taxAmountSnapshot > 0L) sale.taxAmountSnapshot else null

        val paymentInfo = ReceiptPaymentInfo(
            method = methodUpper,
            totalAmount = sale.totalAmount,
            subtotalAmount = subtotalAmount,
            discountAmount = finalDiscount,
            taxableBase = taxableBase,
            taxAmount = taxAmount,
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

    fun mapFromReturn(
        returnTx: SaleReturnTransactionEntity,
        items: List<SaleReturnItemEntity>,
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

        val methodUpper = returnTx.refundMethod.uppercase()
        val changeAmount = if (methodUpper == "CASH" && cashGiven != null && cashGiven >= returnTx.totalRefundAmount) {
            cashGiven - returnTx.totalRefundAmount
        } else {
            null
        }

        val taxableBase = if (returnTx.taxAmountSnapshot > 0L) returnTx.taxableBaseSnapshot else null
        val taxAmount = if (returnTx.taxAmountSnapshot > 0L) returnTx.taxAmountSnapshot else null

        val paymentInfo = ReceiptPaymentInfo(
            method = methodUpper,
            totalAmount = returnTx.totalRefundAmount,
            taxableBase = taxableBase,
            taxAmount = taxAmount,
            payAmount = if (methodUpper == "CASH") cashGiven else null,
            changeAmount = changeAmount
        )

        return ReceiptData(
            receiptNumber = returnTx.returnNumber,
            transactionUuid = returnTx.uuid,
            dateTimeMillis = returnTx.returnDate,
            shopProfile = shopProfile,
            cashierName = cashier,
            items = receiptItems,
            paymentInfo = paymentInfo
        )
    }
}
