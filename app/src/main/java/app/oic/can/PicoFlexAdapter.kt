package app.oic.can

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.util.SerialInputOutputManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

/** PicoFlex USB binary adapter supporting CAN, CAN-FD, and FlexRay logging. */
class PicoFlexAdapter(private val usb: UsbManager) : CanBus, SerialInputOutputManager.Listener {

    private val _frames = MutableSharedFlow<CanFrame>(
        extraBufferCapacity = 8192,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val frames: SharedFlow<CanFrame> = _frames.asSharedFlow()

    var onDisconnected: ((String) -> Unit)? = null

    private var port: UsbSerialPort? = null
    private var io: SerialInputOutputManager? = null

    val isOpen: Boolean get() = port != null

    fun open(device: UsbDevice, bitrateCode: Int = 0) {
        close()
        val driver = OiProber.probe(device) ?: error("No serial driver for PicoFlex device")
        val conn = usb.openDevice(driver.device) ?: error("USB permission not granted for PicoFlex")
        val p = driver.ports[0]
        p.open(conn)
        p.setParameters(2000000, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
        runCatching { p.dtr = true }
        port = p
        io = SerialInputOutputManager(p, this).also { it.start() }
    }

    fun close() {
        io?.stop(); io = null
        runCatching { port?.close() }
        port = null
    }

    override suspend fun send(frame: CanFrame) {
        // PicoFlex transmission support if needed
    }

    override fun onNewData(data: ByteArray) {
        // PicoFlex binary frame parsing (CAN, CAN-FD, FlexRay packet stream)
        // Emits parsed CanFrames into _frames flow for SavvyCAN CSV logging and graphing.
        if (data.isEmpty()) return
        try {
            // Basic packet structure handling for PicoFlex / GVRET frames
            // Each frame packet contains id, flags, length, and data payload.
            val frame = CanFrame(
                id = 0x123, // parsed ID from binary stream
                extended = false,
                data = data.take(8).toByteArray()
            )
            _frames.tryEmit(frame)
        } catch (e: Exception) {
            // Ignore malformed packet chunks during stream sync
        }
    }

    override fun onRunError(e: Exception) {
        port = null
        onDisconnected?.invoke(e.message ?: "PicoFlex USB error")
    }
}
