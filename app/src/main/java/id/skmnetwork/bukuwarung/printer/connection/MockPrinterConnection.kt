package id.skmnetwork.bukuwarung.printer.connection

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.charset.Charset

class MockPrinterConnection(
    private val charset: Charset = Charsets.UTF_8,
    val mockAddress: String = "MOCK_ADDR"
) : PrinterConnection {

    override val connectionType: String = "MOCK"
    override val deviceAddress: String get() = mockAddress

    private var connected: Boolean = false
    private val mutex = Mutex()

    private val _receivedByteChunks = mutableListOf<ByteArray>()
    private val _receivedStrings = mutableListOf<String>()

    var simulateErrorOnConnect: Boolean = false
    var simulateErrorOnSend: Boolean = false
    var connectDelayMillis: Long = 0
    var sendDelayMillis: Long = 0

    override val isConnected: Boolean
        get() = connected

    val receivedByteChunks: List<ByteArray>
        get() = _receivedByteChunks.toList()

    val receivedStrings: List<String>
        get() = _receivedStrings.toList()

    val totalBytesReceived: Int
        get() = _receivedByteChunks.sumOf { it.size }

    val fullPrintedText: String
        get() = _receivedStrings.joinToString("")

    override suspend fun connect(): Result<Unit> = mutex.withLock {
        if (connectDelayMillis > 0) {
            kotlinx.coroutines.delay(connectDelayMillis)
        }
        if (simulateErrorOnConnect) {
            connected = false
            Result.failure(IllegalStateException("Simulated connection error in MockPrinterConnection"))
        } else {
            connected = true
            Result.success(Unit)
        }
    }

    override suspend fun send(data: ByteArray): Result<Unit> = mutex.withLock {
        if (!connected) {
            return@withLock Result.failure(IllegalStateException("MockPrinterConnection is not connected"))
        }
        if (sendDelayMillis > 0) {
            kotlinx.coroutines.delay(sendDelayMillis)
        }
        if (simulateErrorOnSend) {
            Result.failure(IllegalStateException("Simulated transmission error in MockPrinterConnection"))
        } else {
            _receivedByteChunks.add(data.copyOf())
            _receivedStrings.add(String(data, charset))
            Result.success(Unit)
        }
    }

    override suspend fun disconnect() = mutex.withLock {
        connected = false
    }

    fun clear() {
        _receivedByteChunks.clear()
        _receivedStrings.clear()
        simulateErrorOnConnect = false
        simulateErrorOnSend = false
        connected = false
    }
}
