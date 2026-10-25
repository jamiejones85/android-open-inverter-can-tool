package app.oic.oi

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal
import java.math.MathContext

data class OiParam(
    val name: String,
    val id: Int,
    val isParam: Boolean,
    val unit: String,
    val category: String?,
    val min: Int?,
    val max: Int?,
    val default: Int?,
    val enums: Map<Int, String>,
    val bits: Map<Int, String>
) {
    /** SDO index/subindex derived from the parameter id (see OIVariable in paramdb.py). */
    val index: Int get() = 0x2100 or (id shr 8)
    val sub: Int get() = id and 0xFF
}

class ParamDb(val params: List<OiParam>) {
    val byName: Map<String, OiParam> = params.associateBy { it.name }
    val byId: Map<Int, OiParam> = params.associateBy { it.id }
}

fun Double.fmt(): String =
    if (this == Math.rint(this) && kotlin.math.abs(this) < 1e15) toLong().toString()
    else BigDecimal(this).round(MathContext(6)).stripTrailingZeros().toPlainString()

private fun isPow2(n: Int) = n != 0 && (n and (n - 1)) == 0

private fun isBitfield(values: Map<Int, String>): Boolean {
    for (v in values.keys) if (v != 0 && !isPow2(v)) return false
    return values.size > 3
}

object ParamDbParser {
    fun parse(text: String): ParamDb {
        val root = Json.parseToJsonElement(text).jsonObject
        val out = ArrayList<OiParam>()
        for ((name, el) in root) {
            val o = el as? JsonObject ?: continue
            val id = o["id"]?.jsonPrimitive?.content?.toDoubleOrNull()?.toInt() ?: continue
            val isParam = o["isparam"]?.jsonPrimitive?.content.equals("true", ignoreCase = true)
            var unit = o["unit"]?.jsonPrimitive?.content ?: ""
            var enums: Map<Int, String> = emptyMap()
            var bits: Map<Int, String> = emptyMap()
            if ('=' in unit) {
                try {
                    val values = LinkedHashMap<Int, String>()
                    for (item in unit.trimEnd(',').split(Regex("[,\\s]")).filter { it.isNotEmpty() }) {
                        val parts = item.split('=')
                        if (parts.size != 2) throw NumberFormatException()
                        values[parts[0].toInt()] = parts[1]
                    }
                    if (isBitfield(values)) bits = values else enums = values
                } catch (e: NumberFormatException) {
                    unit += " [DB FORMAT ERROR]"
                }
            }
            fun fx(key: String): Int? =
                if (!isParam) null
                else o[key]?.jsonPrimitive?.content?.toDoubleOrNull()?.let { fixedFromFloat(it) }

            out += OiParam(
                name, id, isParam, unit, o["category"]?.jsonPrimitive?.content,
                fx("minimum"), fx("maximum"), fx("default"), enums, bits
            )
        }
        return ParamDb(out)
    }
}

/** Mirrors value_to_str() in paramdb.py */
fun formatValue(p: OiParam, v: Double, symbolic: Boolean = true): String {
    if (symbolic && p.enums.isNotEmpty()) {
        return p.enums[v.toInt()] ?: "${v.fmt()} (Unknown value)"
    }
    if (symbolic && p.bits.isNotEmpty()) {
        val iv = v.toInt()
        val s = p.bits.entries.filter { it.key and iv != 0 }.joinToString(", ") { it.value }
        return if (s.isNotEmpty()) s else (p.bits[iv] ?: "0")
    }
    return v.fmt()
}
