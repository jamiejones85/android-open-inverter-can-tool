package app.oic.can

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

object OiProber {
    private val custom: UsbSerialProber by lazy {
        val table = UsbSerialProber.getDefaultProbeTable()
        table.addProduct(0xad50, 0x60c4, CdcAcmSerialDriver::class.java) // CANable slcan
        table.addProduct(14337, 56780, CdcAcmSerialDriver::class.java) // PicoFlex
        UsbSerialProber(table)
    }

    fun probe(device: UsbDevice): UsbSerialDriver? =
        UsbSerialProber.getDefaultProber().probeDevice(device) ?: custom.probeDevice(device)
}

/** SLCAN adapter over USB CDC. Opens the bus in NORMAL mode: the SDO client has to transmit. */
class SlcanAdapter(private val usb: UsbManager) : CanBus, SerialInputOutputManager.Listener {

    private val _frames = MutableSharedFlow<CanFrame>(
        extraBufferCapacity = 4096,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val frames: SharedFlow<CanFrame> = _frames.asSharedFlow()

    var onDisconnected: ((String) -> Unit)? = null

    private var port: UsbSerialPort? = null
    private var io: SerialInputOutputManager? = null
    private val buf = StringBuilder()
    private val writeLock = Any()

    val isOpen: Boolean get() = port != null

    /** sCode: SLCAN bitrate code (S4=125k, S5=250k, S6=500k, S8=1M). */
    fun open(device: UsbDevice, sCode: Int) {
        close()
        val driver = OiProber.probe(device) ?: error("No serial driver for this USB device")
        val conn = usb.openDevice(driver.device) ?: error("USB permission not granted")
        val p = driver.ports[0]
        p.open(conn)
        p.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
        runCatching { p.dtr = true }
        port = p
        io = SerialInputOutputManager(p, this).also { it.start() }
        write("C")
        write("S$sCode")
        write("O")
    }

    fun close() {
        runCatching { write("C") }
        io?.stop(); io = null
        runCatching { port?.close() }
        port = null
        buf.setLength(0)
    }

    private fun write(cmd: String) {
        val p = port ?: return
        synchronized(writeLock) { p.write((cmd + "\r").toByteArray(), 200) }
    }

    override suspend fun send(frame: CanFrame) {
        withContext(Dispatchers.IO) { write(Slcan.encode(frame)) }
    }

    override fun onNewData(data: ByteArray) {
        for (b in data) {
            val c = (b.toInt() and 0xFF).toChar()
            when (c) {
                '\r' -> {
                    if (buf.isNotEmpty()) Slcan.parse(buf.toString())?.let { _frames.tryEmit(it) }
                    buf.setLength(0)
                }
                '\u0007' -> {}              // BEL = adapter rejected a command
                else -> if (buf.length < 64) buf.append(c)
            }
        }
    }

    override fun onRunError(e: Exception) {
        port = null
        onDisconnected?.invoke(e.message ?: "USB error")
    }
}
