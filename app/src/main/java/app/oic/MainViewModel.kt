package app.oic

import android.app.Activity
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.oic.can.CanFrame
import app.oic.can.SlcanAdapter
import app.oic.can.OiProber
import app.oic.can.CanUpgrader
import app.oic.can.State
import app.oic.oi.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import java.io.File
import java.io.Writer
import java.time.LocalDateTime

data class Ui(
    val connected: Boolean = false,
    val status: String = "Not connected",
    val bitrate: Int = 6,
    val nodeId: Int = 1,
    val nodes: List<Int> = emptyList(),
    val scanning: Boolean = false,
    val busy: Boolean = false,
    val serial: String? = null,
    val db: ParamDb? = null,
    val dbSource: String = "none",
    val values: Map<String, Double> = emptyMap(),
    val polling: Boolean = false,
    val logging: Boolean = false,
    val logRows: Int = 0,
    val mapTx: List<CanMessage> = emptyList(),
    val mapRx: List<CanMessage> = emptyList(),
    val errors: List<String>? = null,
    val upgrading: Boolean = false,
    val upgradeProgress: Float = 0f,
    val upgradeStatus: String = ""
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    companion object {
        const val ACTION_PERM = "app.oic.USB_PERMISSION"
        val BITRATES = linkedMapOf(4 to "125k", 5 to "250k", 6 to "500k", 8 to "1M")
    }

    private val ctx: Context get() = getApplication<Application>().applicationContext
    private val usb = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
    private val adapter = SlcanAdapter(usb)
    private val sdo = SdoClient(adapter)
    private val node = OiNode(sdo)
    private val billing = BillingManager(ctx)
    val hasDonated = billing.hasDonated

    fun donate(activity: Activity) {
        billing.launchBilling(activity)
    }

    private val _ui = MutableStateFlow(Ui())
    val ui = _ui.asStateFlow()
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val events = _events.asSharedFlow()

    init {
        adapter.onDisconnected = { msg ->
            stopPolling()
            _ui.update { it.copy(connected = false, status = "Disconnected: $msg") }
        }
    }

    private fun toast(msg: String) { _events.tryEmit(msg) }

    private fun op(block: suspend () -> Unit) {
        viewModelScope.launch {
            _ui.update { it.copy(busy = true) }
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: SdoAbort) {
                toast(if (e.code == Oi.ABORT_NOT_AVAILABLE) "Not supported by this device" else "Device error: ${e.message}")
            } catch (e: SdoTimeout) {
                toast("No response from node ${_ui.value.nodeId}")
            } catch (e: Exception) {
                toast(e.message ?: e.toString())
            } finally {
                _ui.update { it.copy(busy = false) }
            }
        }
    }

    // ---------------- connection ----------------
    fun setBitrate(code: Int) = _ui.update { it.copy(bitrate = code) }
    fun setNode(id: Int) {
        sdo.nodeId = id.coerceIn(1, 127)
        _ui.update { it.copy(nodeId = sdo.nodeId, serial = null) }
    }

    fun connect() {
        val dev = usb.deviceList.values.firstOrNull { OiProber.probe(it) != null }
            ?: return toast("No USB CAN adapter found")
        if (usb.hasPermission(dev)) {
            openDevice(dev)
        } else {
            val pi = PendingIntent.getBroadcast(
                ctx, 0, Intent(ACTION_PERM).setPackage(ctx.packageName), PendingIntent.FLAG_MUTABLE
            )
            usb.requestPermission(dev, pi)
        }
    }

    fun onPermission(dev: UsbDevice?, granted: Boolean) {
        if (granted && dev != null) openDevice(dev) else toast("USB permission denied")
    }

    private fun openDevice(dev: UsbDevice) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { adapter.open(dev, _ui.value.bitrate) }
                val label = BITRATES[_ui.value.bitrate]
                _ui.update { it.copy(connected = true, status = "Connected, $label bit/s, normal mode") }
            } catch (e: Exception) {
                toast(e.message ?: "Could not open adapter")
            }
        }
    }

    fun disconnect() {
        stopPolling()
        adapter.close()
        _ui.update { it.copy(connected = false, status = "Not connected", nodes = emptyList(), serial = null) }
    }

    // ---------------- scan ----------------
    fun scan() {
        if (!_ui.value.connected) return toast("Connect the adapter first")
        viewModelScope.launch {
            _ui.update { it.copy(scanning = true, nodes = emptyList()) }
            val found = sortedSetOf<Int>()
            val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                adapter.frames.collect { f: CanFrame ->
                    if (!f.extended && f.id in 0x581..0x5FF) found += f.id - 0x580
                }
            }
            // same probe as scanner.py: SDO read of 0x1000, paced to protect the adapter
            val probe = byteArrayOf(0x40, 0x00, 0x10, 0x00, 0, 0, 0, 0)
            for (id in 1..127) {
                adapter.send(CanFrame(0x600 + id, false, false, probe))
                delay(15)
            }
            delay(1500)
            collector.cancel()
            _ui.update { it.copy(scanning = false, nodes = found.toList()) }
            if (found.isEmpty()) {
                toast("No nodes responded")
            } else if (found.size == 1) {
                setNode(found.first())
            }
        }
    }

    // ---------------- identity / database ----------------
    fun readSerial() = op { _ui.update { it.copy(serial = node.serialNo()) } }

    fun loadDb() = op {
        val id = _ui.value.nodeId
        val checksum = node.dbChecksum()
        val dir = File(ctx.filesDir, "dbcache").apply { mkdirs() }
        val f = File(dir, "$id-$checksum.json")
        val cached = f.exists()
        val text = if (cached) f.readText() else node.dbJson()
        val db = ParamDbParser.parse(text)
        if (!cached) f.writeText(text)       // cache only after a successful parse
        _ui.update { it.copy(db = db, dbSource = "device ${if (cached) "(cached)" else "(downloaded)"}, ${db.params.size} items") }
    }

    fun loadDbFile(uri: Uri) = op {
        val text = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)!!.bufferedReader().readText() }
        val db = ParamDbParser.parse(text)
        _ui.update { it.copy(db = db, dbSource = "file, ${db.params.size} items") }
    }

    // ---------------- read / write ----------------
    fun readOne(name: String) = op {
        val p = _ui.value.db?.byName?.get(name) ?: return@op
        val v = node.read(p)
        _ui.update { it.copy(values = it.values + (name to v)) }
    }

    fun readAll(paramsOnly: Boolean) = op {
        val list = _ui.value.db?.params?.filter { !paramsOnly || it.isParam } ?: return@op
        val acc = HashMap<String, Double>()
        for ((i, p) in list.withIndex()) {
            acc[p.name] = node.read(p)
            if (i % 8 == 7) _ui.update { it.copy(values = it.values + acc) }
        }
        _ui.update { it.copy(values = it.values + acc) }
    }

    fun write(name: String, text: String) = op {
        val p = _ui.value.db?.byName?.get(name) ?: return@op
        val err = node.writeValue(p, text)
        if (err != null) return@op toast(err)
        val v = node.read(p)       // read back what the device actually stored
        _ui.update { it.copy(values = it.values + (name to v)) }
        toast("$name = ${formatValue(p, v)} (RAM only until you save to flash)")
    }

    // ---------------- commands ----------------
    fun command(name: String, mode: Int = 1) = op {
        when (name) {
            "save" -> node.save()
            "load" -> node.load()
            "defaults" -> node.loadDefaults()
            "start" -> node.start(mode)
            "stop" -> node.stop()
            "reset" -> try { node.reset() } catch (e: SdoTimeout) { /* device reboots, no reply */ }
        }
        toast("Command '$name' sent")
    }

    fun readErrors() = op {
        val list = node.errors(_ui.value.db).map { (t, msg) -> "%d.%03d s  %s".format(t.seconds, t.toMillis() % 1000, msg) }
        _ui.update { it.copy(errors = list) }
    }

    // ---------------- live values / CSV logging ----------------
    private var pollJob: Job? = null

    fun stopPolling() { pollJob?.cancel() }

    fun startPolling(names: List<String>, stepMs: Long, logUri: Uri?) {
        pollJob?.cancel()
        val db = _ui.value.db ?: return toast("Load a parameter database first")
        val vars = names.mapNotNull { db.byName[it] }
        if (vars.isEmpty()) return toast("Select at least one item")
        pollJob = viewModelScope.launch {
            var writer: Writer? = null
            try {
                if (logUri != null) {
                    writer = withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(logUri, "wt")!!.bufferedWriter() }
                    writer.write((listOf("timestamp") + vars.map { it.name }).joinToString(",") { quote(it) } + "\n")
                }
                _ui.update { it.copy(polling = true, logging = writer != null, logRows = 0) }
                while (isActive) {
                    val row = ArrayList<String>()
                    for (p in vars) {
                        val v = node.read(p)
                        _ui.update { it.copy(values = it.values + (p.name to v)) }
                        row += formatValue(p, v)
                    }
                    if (writer != null) {
                        val w = writer
                        withContext(Dispatchers.IO) {
                            w.write((listOf(LocalDateTime.now().toString()) + row).joinToString(",") { quote(it) } + "\n")
                            w.flush()
                        }
                        _ui.update { it.copy(logRows = it.logRows + 1) }
                    }
                    delay(stepMs)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SdoTimeout) {
                toast("Polling stopped: no response from node ${_ui.value.nodeId}")
            } catch (e: Exception) {
                toast(e.message ?: e.toString())
            } finally {
                withContext(NonCancellable) { runCatching { writer?.close() } }
                _ui.update { it.copy(polling = false, logging = false) }
            }
        }
    }

    private fun quote(s: String) = "\"" + s.replace("\"", "\"\"") + "\""

    // ---------------- save / load all parameters (JSON) ----------------
    fun saveParams(uri: Uri) = op {
        val db = _ui.value.db ?: return@op toast("Load a parameter database first")
        val vals = LinkedHashMap<String, Double>()
        for (p in db.params.filter { it.isParam }) vals[p.name] = node.read(p)
        val obj = buildJsonObject { vals.forEach { (k, v) -> put(k, v) } }
        val text = Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), obj)
        withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use { it.write(text) } }
        toast("Saved ${obj.size} parameters")
    }

    fun loadParams(uri: Uri) = op {
        val db = _ui.value.db ?: return@op toast("Load a parameter database first")
        val text = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)!!.bufferedReader().readText() }
        val doc = Json.parseToJsonElement(text).jsonObject
        var count = 0
        val problems = ArrayList<String>()
        for ((name, el) in doc) {
            val p = db.byName[name]
            if (p == null) { problems += "unknown: $name"; continue }
            val err = node.writeValue(p, el.jsonPrimitive.content)
            if (err != null) problems += err else count++
        }
        toast("Loaded $count parameters" + if (problems.isEmpty()) "" else ", ${problems.size} skipped: ${problems.first()}")
    }

    // ---------------- CAN map ----------------
    fun refreshMap() = op {
        val tx = node.listMap(Direction.TX)
        val rx = node.listMap(Direction.RX)
        _ui.update { it.copy(mapTx = tx, mapRx = rx) }
    }

    fun addMapEntry(dir: Direction, canId: Int, ext: Boolean, e: MapEntry) = op {
        try {
            node.addMapEntry(dir, canId, ext, e)
        } catch (ex: IllegalArgumentException) {
            return@op toast(ex.message ?: "Invalid map entry")
        }
        val tx = node.listMap(Direction.TX); val rx = node.listMap(Direction.RX)
        _ui.update { it.copy(mapTx = tx, mapRx = rx) }
        toast("Mapping added (save to flash to keep it)")
    }

    fun removeMapEntry(dir: Direction, canIndex: Int, paramIndex: Int) = op {
        node.removeMapEntry(dir, canIndex, paramIndex)
        val tx = node.listMap(Direction.TX); val rx = node.listMap(Direction.RX)
        _ui.update { it.copy(mapTx = tx, mapRx = rx) }
    }

    fun clearMap(dir: Direction) = op {
        node.clearMap(dir)
        val tx = node.listMap(Direction.TX); val rx = node.listMap(Direction.RX)
        _ui.update { it.copy(mapTx = tx, mapRx = rx) }
    }

    fun upgradeFirmware(uri: Uri) {
        if (!ui.value.connected) return toast("Connect the adapter first")
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                } ?: return@launch toast("Could not read firmware file")

                _ui.update { it.copy(upgrading = true, upgradeProgress = 0f, upgradeStatus = "Waiting for device boot...") }
                toast("Starting firmware upgrade. Reboot/power-cycle the device into bootloader mode.")

                val upgrader = CanUpgrader(adapter, null, bytes) { update ->
                    val statusText = when (update.state) {
                        State.START -> "Waiting for device boot..."
                        State.HEADER -> "Header negotiation (Serial: ${update.serialno?.joinToString("") { "%02X".format(it) } ?: "?"})"
                        State.UPLOAD -> "Uploading firmware (${update.progress.toInt()}%)..."
                        State.CHECK_CRC -> "Validating page CRC..."
                        State.WAIT_FOR_DONE -> "Finalizing upgrade..."
                        State.COMPLETE -> "Upgrade completed successfully!"
                        State.FAILURE -> "Upgrade failed: ${update.failure}"
                    }
                    _ui.update { it.copy(upgradeProgress = update.progress, upgradeStatus = statusText) }
                }

                val success = upgrader.run(this, 60000)
                if (success) {
                    toast("Firmware upgrade finished: ${upgrader.state}")
                } else {
                    toast("Firmware upgrade timed out")
                }
            } catch (e: Exception) {
                toast("Upgrade error: ${e.message}")
            } finally {
                _ui.update { it.copy(upgrading = false) }
            }
        }
    }

    private var canLogJob: Job? = null

    fun startCanLog(uri: Uri) {
        if (!ui.value.connected) return toast("Connect the adapter first")
        stopPolling()

        canLogJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                ctx.contentResolver.openOutputStream(uri, "w")?.bufferedWriter()?.use { writer ->
                    _ui.update { it.copy(logging = true, logRows = 0) }
                    var count = 0
                    val startTime = System.currentTimeMillis()
                    adapter.frames.collect { f ->
                        val timestamp = (System.currentTimeMillis() - startTime) / 1000.0
                        val hexData = f.data.joinToString(" ") { "%02X".format(it) }
                        val line = "%.4f 1 %03X [%d] %s\n".format(timestamp, f.id, f.data.size, hexData)
                        writer.write(line)
                        writer.flush()
                        count++
                        if (count % 50 == 0) {
                            _ui.update { it.copy(logRows = count) }
                        }
                    }
                }
            } catch (e: Exception) {
                toast("Logging error: ${e.message}")
            } finally {
                _ui.update { it.copy(logging = false) }
            }
        }
        toast("SavvyCAN streaming log started")
    }

    fun stopCanLog() {
        canLogJob?.cancel()
        canLogJob = null
        _ui.update { it.copy(logging = false) }
        toast("SavvyCAN streaming log stopped")
    }

    override fun onCleared() {
        pollJob?.cancel()
        canLogJob?.cancel()
        adapter.close()
    }
}
