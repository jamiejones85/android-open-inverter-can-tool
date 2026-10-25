package app.oic.oi

import java.time.Duration

/** Values mirror src/openinverter_can_tool/constants.py */
object Oi {
    const val SERIALNO_INDEX = 0x5000
    const val PARAM_DB_CHECKSUM_SUB = 3
    const val STRINGS_INDEX = 0x5001
    const val PARAM_DB_SUB = 0
    const val COMMAND_INDEX = 0x5002

    const val CMD_SAVE = 0
    const val CMD_LOAD = 1
    const val CMD_RESET = 2
    const val CMD_DEFAULTS = 3
    const val CMD_START = 4
    const val CMD_STOP = 5

    const val MAP_TX_INDEX = 0x3000
    const val MAP_RX_INDEX = 0x3001
    const val MAP_LIST_TX_INDEX = 0x3100
    const val MAP_LIST_RX_INDEX = 0x3180
    const val EXT_FLAG = 0x20000000
    const val EXT_MASK = 0x1fffffff

    const val ERROR_NUM_INDEX = 0x5003
    const val ERROR_TIME_INDEX = 0x5004

    const val ABORT_NOT_AVAILABLE = 0x06020000L

    val START_MODES = linkedMapOf(
        "Normal" to 1, "Manual" to 2, "Boost" to 3, "Buck" to 4, "Sine" to 5, "ACHeat" to 6
    )
}

/** All OpenInverter values are 32-bit fixed point with 5 fractional bits. */
fun fixedFromFloat(v: Double): Int = (v * 32).toInt()
fun fixedToFloat(raw: Int): Double = raw / 32.0

enum class Direction(val listIndex: Int, val addIndex: Int) {
    TX(Oi.MAP_LIST_TX_INDEX, Oi.MAP_TX_INDEX),
    RX(Oi.MAP_LIST_RX_INDEX, Oi.MAP_RX_INDEX)
}

data class MapEntry(val paramId: Int, val position: Int, val length: Int, val gain: Double, val offset: Int)

data class CanMessage(
    val canId: Int,
    val extended: Boolean,
    val entries: List<MapEntry>,
    val listIndex: Int
)

class OiNode(private val sdo: SdoClient) {

    private fun pad4(b: ByteArray) = ByteArray(4).also { b.copyInto(it, 0, 0, minOf(4, b.size)) }
    private fun leInt(b: ByteArray): Int {
        val p = pad4(b)
        return (0..3).fold(0) { acc, i -> acc or ((p[i].toInt() and 0xFF) shl (8 * i)) }
    }
    private fun leU32(b: ByteArray): Long = leInt(b).toLong() and 0xFFFFFFFFL
    private fun le32(v: Int) = ByteArray(4) { (v shr (8 * it)).toByte() }

    // ---- parameters / values ----
    suspend fun readRaw(p: OiParam): Int = leInt(sdo.upload(p.index, p.sub))
    suspend fun read(p: OiParam): Double = fixedToFloat(readRaw(p))
    suspend fun writeRaw(p: OiParam, raw: Int) = sdo.download(p.index, p.sub, le32(raw))

    // ---- identity / database ----
    suspend fun serialNo(): String {
        val sb = StringBuilder()
        for (i in 2 downTo 0) {
            pad4(sdo.upload(Oi.SERIALNO_INDEX, i)).reversed().forEach { sb.append("%02X".format(it)) }
        }
        return sb.toString()
    }

    suspend fun dbChecksum(): Long = leU32(sdo.upload(Oi.SERIALNO_INDEX, Oi.PARAM_DB_CHECKSUM_SUB))

    suspend fun dbJson(): String {
        val bytes = sdo.upload(Oi.STRINGS_INDEX, Oi.PARAM_DB_SUB)
        // heavily loaded firmware can emit stray zero bytes
        return bytes.filter { it != 0.toByte() }.toByteArray().toString(Charsets.UTF_8)
    }

    // ---- commands ----
    private suspend fun command(sub: Int, arg: Int = 0) = sdo.download(Oi.COMMAND_INDEX, sub, le32(arg))
    suspend fun save() = command(Oi.CMD_SAVE)
    suspend fun load() = command(Oi.CMD_LOAD)
    suspend fun reset() = command(Oi.CMD_RESET)
    suspend fun loadDefaults() = command(Oi.CMD_DEFAULTS)
    suspend fun start(mode: Int) = command(Oi.CMD_START, mode)
    suspend fun stop() = command(Oi.CMD_STOP)

    // ---- error log ----
    suspend fun errors(db: ParamDb?): List<Pair<Duration, String>> {
        val lasterr = db?.byName?.get("lasterr")
        val tick = if (db?.byName?.get("uptime")?.unit == "sec") Duration.ofSeconds(1) else Duration.ofMillis(10)
        val out = ArrayList<Pair<Duration, String>>()
        for (i in 0 until 255) {
            val t = leU32(sdo.upload(Oi.ERROR_TIME_INDEX, i))
            if (t == 0L) break
            val n = leInt(sdo.upload(Oi.ERROR_NUM_INDEX, i))
            out += tick.multipliedBy(t) to (lasterr?.enums?.get(n) ?: "Unknown error $n")
        }
        return out
    }

    // ---- CAN message mapping ----
    private suspend fun tryUpload(index: Int, sub: Int): ByteArray? = try {
        sdo.upload(index, sub)
    } catch (e: SdoAbort) {
        if (e.code == Oi.ABORT_NOT_AVAILABLE) null else throw e
    }

    private suspend fun mapEntry(idx: Int, sub: Int): MapEntry? {
        val a = tryUpload(idx, sub) ?: return null
        val b = tryUpload(idx, sub + 1) ?: return null
        val pa = pad4(a)
        val paramId = (pa[0].toInt() and 0xFF) or ((pa[1].toInt() and 0xFF) shl 8)
        val pos = pa[2].toInt() and 0xFF
        val len = pa[3].toInt()                       // signed: negative = big endian
        val pb = pad4(b)
        var g = (pb[0].toInt() and 0xFF) or ((pb[1].toInt() and 0xFF) shl 8) or ((pb[2].toInt() and 0xFF) shl 16)
        if (g and 0x800000 != 0) g = g or -0x1000000   // sign-extend 24 bit
        return MapEntry(paramId, pos, len, g / 1000.0, pb[3].toInt())
    }

    suspend fun listMap(dir: Direction): List<CanMessage> {
        val out = ArrayList<CanMessage>()
        var idx = dir.listIndex
        while (true) {
            val raw = tryUpload(idx, 0) ?: break
            val id = leInt(raw)
            val entries = ArrayList<MapEntry>()
            var sub = 1
            while (true) {
                val e = mapEntry(idx, sub) ?: break
                entries += e
                sub += 2
            }
            if (entries.isNotEmpty()) {
                out += CanMessage(id and Oi.EXT_MASK, id and Oi.EXT_FLAG != 0, entries, idx - dir.listIndex)
            }
            idx++
        }
        return out
    }

    suspend fun addMapEntry(dir: Direction, canId: Int, extended: Boolean, e: MapEntry) {
        require(canId in 0..(if (extended) Oi.EXT_MASK else 0x7FF)) { "CAN ID out of range" }
        require(e.position in 0..63) { "Position must be 0-63" }
        require(kotlin.math.abs(e.length) in 1..32) { "Length must be 1-32 (negative = big endian)" }
        require(e.gain in -8388.608..8388.607) { "Gain out of range" }
        require(e.offset in -128..127) { "Offset must be -128..127" }

        val idx = dir.addIndex
        sdo.download(idx, 0, le32(if (extended) canId or Oi.EXT_FLAG else canId))
        sdo.download(
            idx, 1,
            byteArrayOf(e.paramId.toByte(), (e.paramId shr 8).toByte(), e.position.toByte(), e.length.toByte())
        )
        val g = (e.gain * 1000).toInt()
        // writing sub 2 is what actually creates the mapping on the device
        sdo.download(idx, 2, byteArrayOf(g.toByte(), (g shr 8).toByte(), (g shr 16).toByte(), e.offset.toByte()))
    }

    /** Returns false when there is nothing at that position. */
    suspend fun removeMapEntry(dir: Direction, canIndex: Int, paramIndex: Int): Boolean = try {
        sdo.download(dir.listIndex + canIndex, 2 * (paramIndex + 1), le32(0), lenient = true)
        true
    } catch (e: SdoAbort) {
        if (e.code == Oi.ABORT_NOT_AVAILABLE) false else throw e
    }

    suspend fun clearMap(dir: Direction) {
        var guard = 0
        while (guard++ < 2000 && removeMapEntry(dir, 0, 0)) { /* device shuffles entries up */ }
    }
}

/** Mirrors ParamWriter in param_utils.py. Returns null on success, otherwise a message. */
suspend fun OiNode.writeValue(p: OiParam, text: String): String? {
    if (!p.isParam) return "${p.name} is a read-only spot value"
    val t = text.trim()
    val num = t.toDoubleOrNull()
    val raw: Int
    if (num != null) {
        raw = fixedFromFloat(num)
        val min = p.min
        val max = p.max
        if (min != null && raw < min) return "$t is below the minimum ${fixedToFloat(min).fmt()} for ${p.name}"
        if (max != null && raw > max) return "$t is above the maximum ${fixedToFloat(max).fmt()} for ${p.name}"
    } else if (p.enums.isNotEmpty()) {
        val hit = p.enums.entries.lastOrNull { it.value.equals(t, true) }
            ?: return "Unknown value '$t'. Valid: ${p.enums.values.joinToString()}"
        raw = fixedFromFloat(hit.key.toDouble())
    } else if (p.bits.isNotEmpty()) {
        var r = 0
        for (n in t.split(',')) {
            val hit = p.bits.entries.firstOrNull { it.value.equals(n.trim(), true) }
                ?: return "Unknown bit '${n.trim()}'. Valid: ${p.bits.values.joinToString()}"
            r = r or hit.key
        }
        raw = fixedFromFloat(r.toDouble())
    } else {
        return "Invalid value '$t' for ${p.name}"
    }
    writeRaw(p, raw)
    return null
}
