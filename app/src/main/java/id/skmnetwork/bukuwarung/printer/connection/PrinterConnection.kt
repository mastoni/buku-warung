package id.skmnetwork.bukuwarung.printer.connection

interface PrinterConnection {
    val isConnected: Boolean
    val connectionType: String
    val deviceAddress: String get() = ""

    suspend fun connect(): Result<Unit>
    suspend fun send(data: ByteArray): Result<Unit>
    suspend fun disconnect()
}
