package app.oic.can

import kotlinx.coroutines.flow.SharedFlow

class CanFrame(
    val id: Int,
    val extended: Boolean = false,
    val rtr: Boolean = false,
    val data: ByteArray = ByteArray(0)
)

interface CanBus {
    val frames: SharedFlow<CanFrame>
    suspend fun send(frame: CanFrame)
}

/** SLCAN ASCII frame codec (t/T/r/R). */
object Slcan {
    fun parse(line: String): CanFrame? {
        if (line.isEmpty() || line[0] !in "tTrR") return null
        val ext = line[0] == 'T' || line[0] == 'R'
        val rtr = line[0] == 'r' || line[0] == 'R'
        val idLen = if (ext) 8 else 3
        if (line.length < 2 + idLen) return null
        return try {
            val id = line.substring(1, 1 + idLen).toInt(16)
            val dlc = line[1 + idLen].digitToInt(16)
            val start = 2 + idLen
            val data = if (rtr) ByteArray(0) else {
                if (dlc > 8 || line.length < start + dlc * 2) return null
                ByteArray(dlc) { i -> line.substring(start + i * 2, start + i * 2 + 2).toInt(16).toByte() }
            }
            CanFrame(id, ext, rtr, data)
        } catch (e: NumberFormatException) {
            null
        }
    }

    fun encode(f: CanFrame): String {
        val hex = f.data.joinToString("") { "%02X".format(it) }
        return when {
            f.extended && f.rtr -> "R%08X%d".format(f.id, f.data.size)
            f.extended -> "T%08X%d%s".format(f.id, f.data.size, hex)
            f.rtr -> "r%03X%d".format(f.id, f.data.size)
            else -> "t%03X%d%s".format(f.id, f.data.size, hex)
        }
    }
}
