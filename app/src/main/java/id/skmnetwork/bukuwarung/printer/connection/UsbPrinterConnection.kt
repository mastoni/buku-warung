package id.skmnetwork.bukuwarung.printer.connection

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UsbPrinterConnection(
    private val usbManager: UsbManager,
    val usbDevice: UsbDevice
) : PrinterConnection {

    override val connectionType: String = "USB"
    override val deviceAddress: String get() = "VID:${usbDevice.vendorId}-PID:${usbDevice.productId}"

    private var connection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var endpointOut: UsbEndpoint? = null

    override val isConnected: Boolean
        get() = connection != null && endpointOut != null

    override suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        if (!usbManager.hasPermission(usbDevice)) {
            return@withContext Result.failure(SecurityException("Izin akses USB belum diberikan untuk ${usbDevice.deviceName}"))
        }

        try {
            // Find bulk transfer endpoint
            var targetInterface: UsbInterface? = null
            var targetEndpoint: UsbEndpoint? = null

            for (i in 0 until usbDevice.interfaceCount) {
                val iface = usbDevice.getInterface(i)
                for (j in 0 until iface.endpointCount) {
                    val ep = iface.getEndpoint(j)
                    if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK && ep.direction == UsbConstants.USB_DIR_OUT) {
                        targetInterface = iface
                        targetEndpoint = ep
                        break
                    }
                }
                if (targetEndpoint != null) break
            }

            if (targetInterface == null || targetEndpoint == null) {
                return@withContext Result.failure(IllegalStateException("Endpoint transfer USB printer tidak ditemukan"))
            }

            val conn = usbManager.openDevice(usbDevice)
                ?: return@withContext Result.failure(IllegalStateException("Gagal membuka koneksi USB ke ${usbDevice.deviceName}"))

            if (!conn.claimInterface(targetInterface, true)) {
                conn.close()
                return@withContext Result.failure(IllegalStateException("Gagal mengklaim interface USB printer"))
            }

            connection = conn
            usbInterface = targetInterface
            endpointOut = targetEndpoint

            Result.success(Unit)
        } catch (e: Exception) {
            disconnect()
            Result.failure(e)
        }
    }

    override suspend fun send(data: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        val conn = connection
        val ep = endpointOut

        if (conn == null || ep == null) {
            return@withContext Result.failure(IllegalStateException("Printer USB belum terhubung"))
        }

        try {
            val timeoutMillis = 5000
            val bytesTransferred = conn.bulkTransfer(ep, data, data.size, timeoutMillis)
            if (bytesTransferred < 0) {
                Result.failure(IllegalStateException("Gagal mengirim data ke printer USB (transfer error code $bytesTransferred)"))
            } else {
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        try {
            usbInterface?.let { connection?.releaseInterface(it) }
        } catch (_: Exception) {}
        usbInterface = null

        try {
            connection?.close()
        } catch (_: Exception) {}
        connection = null
        endpointOut = null
    }
}
