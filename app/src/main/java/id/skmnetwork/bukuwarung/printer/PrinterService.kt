package id.skmnetwork.bukuwarung.printer

import id.skmnetwork.bukuwarung.domain.receipt.ReceiptData
import id.skmnetwork.bukuwarung.domain.receipt.ReceiptPaperWidth
import id.skmnetwork.bukuwarung.printer.connection.PrinterConnection
import id.skmnetwork.bukuwarung.printer.escpos.EscPosReceiptFormatter
import id.skmnetwork.bukuwarung.printer.escpos.PlainTextReceiptFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PrinterService(
    private var activeConnection: PrinterConnection? = null,
    private var paperWidth: ReceiptPaperWidth = ReceiptPaperWidth.WIDTH_58MM,
    private val escPosFormatter: EscPosReceiptFormatter = EscPosReceiptFormatter(),
    private val plainTextFormatter: PlainTextReceiptFormatter = PlainTextReceiptFormatter()
) {

    fun setConnection(connection: PrinterConnection?) {
        this.activeConnection = connection
    }

    suspend fun clearConnection() = withContext(Dispatchers.IO) {
        try {
            activeConnection?.disconnect()
        } catch (_: Exception) {}
        activeConnection = null
    }

    fun getConnection(): PrinterConnection? = activeConnection

    fun isMatchingConnection(type: String, address: String): Boolean {
        val conn = activeConnection ?: return false
        return conn.connectionType.equals(type, ignoreCase = true) &&
                conn.deviceAddress.equals(address, ignoreCase = true)
    }

    fun setPaperWidth(width: ReceiptPaperWidth) {
        this.paperWidth = width
    }

    fun getPaperWidth(): ReceiptPaperWidth = paperWidth

    val isConnected: Boolean
        get() = activeConnection?.isConnected == true

    suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        val connection = activeConnection
            ?: return@withContext Result.failure(IllegalStateException("Printer belum dikonfigurasi"))
        connection.connect()
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        activeConnection?.disconnect()
    }

    fun formatPreview(receipt: ReceiptData, width: ReceiptPaperWidth = paperWidth): String {
        return plainTextFormatter.format(receipt, width)
    }

    suspend fun printReceipt(
        receipt: ReceiptData,
        width: ReceiptPaperWidth = paperWidth,
        autoConnect: Boolean = true
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val connection = activeConnection
            ?: return@withContext Result.failure(IllegalStateException("Printer belum dikonfigurasi"))

        try {
            if (!connection.isConnected && autoConnect) {
                val connectResult = connection.connect()
                if (connectResult.isFailure) {
                    return@withContext Result.failure(
                        connectResult.exceptionOrNull() ?: IllegalStateException("Gagal menghubungkan ke printer")
                    )
                }
            }

            if (!connection.isConnected) {
                return@withContext Result.failure(IllegalStateException("Gagal cetak: Printer belum terhubung"))
            }

            val escPosBytes = escPosFormatter.format(receipt, width)
            val sendResult = connection.send(escPosBytes)
            sendResult
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun printTestReceipt(
        shopName: String = "BUKU WARUNG",
        width: ReceiptPaperWidth = paperWidth,
        autoConnect: Boolean = true
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val connection = activeConnection
            ?: return@withContext Result.failure(IllegalStateException("Printer belum dikonfigurasi"))

        try {
            if (!connection.isConnected && autoConnect) {
                val connectResult = connection.connect()
                if (connectResult.isFailure) {
                    return@withContext Result.failure(
                        connectResult.exceptionOrNull() ?: IllegalStateException("Gagal menghubungkan ke printer")
                    )
                }
            }

            if (!connection.isConnected) {
                return@withContext Result.failure(IllegalStateException("Gagal cetak: Printer belum terhubung"))
            }

            val escPosBytes = escPosFormatter.formatTestReceipt(shopName, width)
            val sendResult = connection.send(escPosBytes)
            sendResult
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
