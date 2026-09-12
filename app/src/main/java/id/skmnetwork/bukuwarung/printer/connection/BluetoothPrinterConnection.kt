package id.skmnetwork.bukuwarung.printer.connection

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

class BluetoothPrinterConnection(
    val macAddress: String,
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
) : PrinterConnection {

    override val connectionType: String = "BLUETOOTH"
    override val deviceAddress: String get() = macAddress

    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    override val isConnected: Boolean
        get() = socket?.isConnected == true

    @SuppressLint("MissingPermission")
    override suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            return@withContext Result.failure(IllegalStateException("Bluetooth tidak aktif atau tidak didukung"))
        }

        try {
            val device: BluetoothDevice = bluetoothAdapter.getRemoteDevice(macAddress)
                ?: return@withContext Result.failure(IllegalStateException("Perangkat Bluetooth dengan MAC $macAddress tidak ditemukan"))

            // Standard SPP UUID
            val sppUuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
            val newSocket = device.createRfcommSocketToServiceRecord(sppUuid)

            // Cancel discovery before connecting as it slows down connection
            bluetoothAdapter.cancelDiscovery()

            newSocket.connect()
            socket = newSocket
            outputStream = newSocket.outputStream
            Result.success(Unit)
        } catch (e: SecurityException) {
            disconnect()
            Result.failure(e)
        } catch (e: IOException) {
            disconnect()
            Result.failure(e)
        } catch (e: Exception) {
            disconnect()
            Result.failure(e)
        }
    }

    override suspend fun send(data: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        val stream = outputStream
        if (socket?.isConnected != true || stream == null) {
            return@withContext Result.failure(IllegalStateException("Printer Bluetooth belum terhubung"))
        }

        try {
            stream.write(data)
            stream.flush()
            Result.success(Unit)
        } catch (e: IOException) {
            disconnect()
            Result.failure(e)
        } catch (e: Exception) {
            disconnect()
            Result.failure(e)
        }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        try {
            outputStream?.close()
        } catch (_: Exception) {}
        outputStream = null

        try {
            socket?.close()
        } catch (_: Exception) {}
        socket = null
    }
}
