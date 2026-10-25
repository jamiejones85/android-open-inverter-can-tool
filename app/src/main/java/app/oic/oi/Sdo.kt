package app.oic.oi

import app.oic.can.CanBus
import app.oic.can.CanFrame
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream

class SdoAbort(val code: Long) : Exception("SDO abort 0x%08X".format(code))
class SdoTimeout : Exception("SDO timeout")

/** Minimal CANopen SDO client: expedited + segmented upload, expedited download. */
class SdoClient(private val bus: CanBus) {
    @Volatile var nodeId: Int = 1
    @Volatile var timeoutMs: Long = 1000
    private val mutex = Mutex()

    private suspend fun transact(req: ByteArray): ByteArray = coroutineScope {
        val node = nodeId
        // Subscribe before sending so a fast reply cannot be missed
        val resp = async(start = CoroutineStart.UNDISPATCHED) {
            bus.frames.first { !it.extended && it.id == 0x580 + node && it.data.size == 8 }
        }
        bus.send(CanFrame(0x600 + node, false, false, req))
        val frame = withTimeoutOrNull(timeoutMs) { resp.await() }
        if (frame == null) {
            resp.cancel()
            throw SdoTimeout()
        }
        frame.data
    }

    private fun le32(b: ByteArray, o: Int): Long =
        (0..3).sumOf { (b[o + it].toLong() and 0xFF) shl (8 * it) }

    private fun frame(cmd: Int, index: Int, sub: Int, data: ByteArray = ByteArray(0)) =
        ByteArray(8).also {
            it[0] = cmd.toByte()
            it[1] = index.toByte()
            it[2] = (index shr 8).toByte()
            it[3] = sub.toByte()
            data.copyInto(it, 4, 0, minOf(4, data.size))
        }

    suspend fun upload(index: Int, sub: Int): ByteArray = mutex.withLock {
        val r = transact(frame(0x40, index, sub))
        val b0 = r[0].toInt() and 0xFF
        when (b0 shr 5) {
            4 -> throw SdoAbort(le32(r, 4))
            2 -> if (b0 and 0x02 != 0) {
                // expedited
                val unused = if (b0 and 1 != 0) (b0 shr 2) and 3 else 0
                r.copyOfRange(4, 8 - unused)
            } else {
                // segmented
                val out = ByteArrayOutputStream()
                var toggle = 0
                while (true) {
                    val s = transact(ByteArray(8).also { it[0] = (0x60 or (toggle shl 4)).toByte() })
                    val c = s[0].toInt() and 0xFF
                    if (c shr 5 == 4) throw SdoAbort(le32(s, 4))
                    check(c shr 5 == 0 && ((c shr 4) and 1) == toggle) { "Bad SDO segment" }
                    out.write(s, 1, 7 - ((c shr 1) and 7))
                    if (c and 1 != 0) break
                    toggle = toggle xor 1
                }
                out.toByteArray()
            }
            else -> error("Unexpected SDO response 0x%02X".format(b0))
        }
    }

    /**
     * Expedited download of 1..4 bytes. With lenient=true any non-abort reply is accepted
     * (the OpenInverter map-removal call answers with non-standard codes 0x20/0x23).
     */
    suspend fun download(index: Int, sub: Int, data: ByteArray, lenient: Boolean = false) =
        mutex.withLock {
            require(data.size in 1..4)
            val r = transact(frame(0x23 or ((4 - data.size) shl 2), index, sub, data))
            val b0 = r[0].toInt() and 0xFF
            if (b0 shr 5 == 4) throw SdoAbort(le32(r, 4))
            if (!lenient) check(b0 == 0x60) { "Unexpected SDO response 0x%02X".format(b0) }
        }
}
