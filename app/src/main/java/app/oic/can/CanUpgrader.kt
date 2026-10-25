package app.oic.can

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Arrays

const val DEVICE_CAN_ID = 0x7DE
const val UPGRADER_CAN_ID = 0x7DD
const val PAGE_SIZE = 1024
const val MAX_PAGES = 255

enum class DevicePacket(val value: Byte) {
    HELLO(0x33.toByte()), // '3'
    START(0x53.toByte()), // 'S'
    PAGE(0x50.toByte()),  // 'P'
    CRC(0x43.toByte()),   // 'C'
    DONE(0x44.toByte()),  // 'D'
    ERROR(0x45.toByte()); // 'E'

    companion object {
        fun from(b: Byte): DevicePacket? = entries.find { it.value == b }
    }
}

fun stmCrc(data: ByteArray): Int {
    var crc = 0xffffffff.toInt()
    val intBuf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer()
    val dataArray = IntArray(intBuf.remaining())
    intBuf.get(dataArray)
    for (word in dataArray) {
        crc = crc xor word
        for (i in 0 until 32) {
            crc = if ((crc and 0x80000000.toInt()) != 0) {
                ((crc shl 1) xor 0x04C11DB7)
            } else {
                crc shl 1
            }
        }
    }
    return crc
}

class Page(raw: ByteArray) {
    val data: ByteArray
    val crc: Int

    init {
        require(raw.size <= PAGE_SIZE) { "Page data exceeds PAGE_SIZE" }
        val padding = PAGE_SIZE - (raw.size % PAGE_SIZE)
        data = if (padding != PAGE_SIZE && padding > 0) {
            val padded = ByteArray(raw.size + padding)
            System.arraycopy(raw, 0, padded, 0, raw.size)
            padded
        } else {
            raw.copyOf()
        }
        crc = stmCrc(data)
    }
}

enum class State {
    START, HEADER, UPLOAD, CHECK_CRC, WAIT_FOR_DONE, FAILURE, COMPLETE
}

enum class Failure {
    PROTOCOL_ERROR, UPGRADE_IN_PROGRESS, PAGE_CRC_ERROR
}

data class StateUpdate(
    val state: State,
    val serialno: ByteArray?,
    val failure: Failure?,
    val progress: Float
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as StateUpdate
        return state == other.state &&
                Arrays.equals(serialno, other.serialno) &&
                failure == other.failure &&
                progress == other.progress
    }

    override fun hashCode(): Int {
        var result = state.hashCode()
        result = 31 * result + (serialno?.contentHashCode() ?: 0)
        result = 31 * result + (failure?.hashCode() ?: 0)
        result = 31 * result + progress.hashCode()
        return result
    }
}

class CanUpgrader(
    private val canBus: CanBus,
    private val targetSerialno: ByteArray?,
    firmwareBytes: ByteArray,
    private val callback: ((StateUpdate) -> Unit)? = null
) {
    private val _pages = mutableListOf<Page>()
    val pages: List<Page> get() = _pages

    private val statusChannel = Channel<StateUpdate>(Channel.UNLIMITED)
    private var _state: State = State.START
    private var _serialno: ByteArray? = null
    private var _failure: Failure? = null
    private var _progress: Float = 0f

    val state: State get() = _state
    val failure: Failure? get() = _failure
    val progress: Float get() = _progress
    val serialno: ByteArray? get() = _serialno

    init {
        if (firmwareBytes.size >= 4 && firmwareBytes[0] == 0x7f.toByte() && firmwareBytes[1] == 'E'.code.toByte() && firmwareBytes[2] == 'L'.code.toByte() && firmwareBytes[3] == 'F'.code.toByte()) {
            throw IllegalArgumentException("ELF firmware images are not supported. Select the binary image instead")
        }
        if (firmwareBytes.size >= 1 && firmwareBytes[0] == ':'.code.toByte()) {
            throw IllegalArgumentException("Intel HEX firmware images are not supported. Select the binary image instead")
        }

        var offset = 0
        var firstPage = true
        while (offset < firmwareBytes.size) {
            val len = minOf(PAGE_SIZE, firmwareBytes.size - offset)
            val chunk = firmwareBytes.copyOfRange(offset, offset + len)
            if (firstPage && chunk.size >= 4) {
                firstPage = false
            }
            _pages.add(Page(chunk))
            if (_pages.size > MAX_PAGES) {
                throw IllegalArgumentException("Firmware image too large")
            }
            offset += len
        }
    }

    suspend fun run(scope: CoroutineScope, timeoutMillis: Long): Boolean {
        val sm = StateMachine(canBus, targetSerialno, _pages, statusChannel)
        val job = scope.launch {
            sm.start()
        }
        try {
            val startTime = System.currentTimeMillis()
            while (true) {
                val remaining = timeoutMillis - (System.currentTimeMillis() - startTime)
                if (remaining <= 0) {
                    job.cancel()
                    return false
                }
                val update = withTimeoutOrNull(remaining) {
                    statusChannel.receive()
                } ?: return false

                _state = update.state
                _serialno = update.serialno
                _failure = update.failure
                _progress = update.progress

                callback?.invoke(update)

                if (_state == State.COMPLETE || _state == State.FAILURE) {
                    job.cancel()
                    return true
                }
            }
        } finally {
            job.cancel()
        }
    }
}

class StateMachine(
    private val canBus: CanBus,
    val targetSerialno: ByteArray?,
    val pages: List<Page>,
    private val statusChannel: Channel<StateUpdate>
) {
    private var currentState: InternalState = StartState()
    var serialno: ByteArray? = null
    var currentPageIndex = 0
    var confirmedPageIndex = -1

    suspend fun start() {
        canBus.frames.collect { frame ->
            if (frame.id == DEVICE_CAN_ID) {
                currentState.process(frame.data)
            }
        }
    }

    suspend fun transitionTo(newState: InternalState) {
        currentState = newState
        newState.sm = this
        statusChannel.send(newState.details())
    }

    fun progress(): Float {
        if (confirmedPageIndex < 0) return 0f
        return if (pages.isNotEmpty()) (confirmedPageIndex * 100f) / pages.size else 100f
    }

    suspend fun reply(data: ByteArray) {
        canBus.send(CanFrame(UPGRADER_CAN_ID, false, false, data))
    }

    val currentPage: Page
        get() = pages[currentPageIndex]

    fun advancePage() {
        currentPageIndex++
    }

    fun confirmPage() {
        confirmedPageIndex++
    }
}

interface InternalState {
    var sm: StateMachine
    suspend fun process(data: ByteArray)
    fun details(): StateUpdate
}

class StartState : InternalState {
    override lateinit var sm: StateMachine

    override suspend fun process(data: ByteArray) {
        if (data.size == 8 && data[0] == DevicePacket.HELLO.value) {
            val deviceSerialno = data.copyOfRange(4, 8).reversedArray()
            if (sm.targetSerialno == null || Arrays.equals(deviceSerialno, sm.targetSerialno)) {
                sm.serialno = deviceSerialno
                sm.transitionTo(HeaderState())
                sm.reply(data.copyOfRange(4, 8))
            }
        } else if (data.size == 1 && (data[0] == DevicePacket.START.value ||
                    data[0] == DevicePacket.PAGE.value ||
                    data[0] == DevicePacket.CRC.value ||
                    data[0] == DevicePacket.DONE.value)) {
            sm.transitionTo(FailureState(Failure.UPGRADE_IN_PROGRESS))
        } else {
            sm.transitionTo(FailureState(Failure.PROTOCOL_ERROR))
        }
    }

    override fun details(): StateUpdate = StateUpdate(State.START, sm.serialno, null, 0f)
}

class HeaderState : InternalState {
    override lateinit var sm: StateMachine

    override suspend fun process(data: ByteArray) {
        if (data.size == 1 && data[0] == DevicePacket.START.value) {
            try {
                sm.transitionTo(UploadState(sm.currentPage))
            } catch (e: IndexOutOfBoundsException) {
                sm.transitionTo(WaitForDoneState())
            }
            val sizeByte = ByteArray(1) { sm.pages.size.toByte() }
            sm.reply(sizeByte)
        } else if (data.size == 8 && data[0] == DevicePacket.HELLO.value) {
            // ignore
        } else {
            sm.transitionTo(FailureState(Failure.PROTOCOL_ERROR))
        }
    }

    override fun details(): StateUpdate = StateUpdate(State.HEADER, sm.serialno, null, 0f)
}

class UploadState(private val page: Page) : InternalState {
    override lateinit var sm: StateMachine
    private var pos = 0

    override suspend fun process(data: ByteArray) {
        if (data.size == 1 && data[0] == DevicePacket.PAGE.value) {
            if (pos == 0) {
                sm.confirmPage()
            }
            val chunk = page.data.copyOfRange(pos, minOf(pos + 8, page.data.size))
            sm.reply(chunk)
            pos += chunk.size

            if (pos >= PAGE_SIZE) {
                sm.transitionTo(CheckCrcState(page.crc))
            }
        } else if (data.size == 1 && data[0] == DevicePacket.ERROR.value) {
            sm.transitionTo(FailureState(Failure.PAGE_CRC_ERROR))
        } else if (data.size == 8 && data[0] == DevicePacket.HELLO.value) {
            // ignore
        } else {
            sm.transitionTo(FailureState(Failure.PROTOCOL_ERROR))
        }
    }

    override fun details(): StateUpdate = StateUpdate(State.UPLOAD, sm.serialno, null, sm.progress())
}

class CheckCrcState(private val crc: Int) : InternalState {
    override lateinit var sm: StateMachine

    override suspend fun process(data: ByteArray) {
        if (data.size == 1 && data[0] == DevicePacket.CRC.value) {
            val crcBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(crc).array()
            sm.reply(crcBytes)

            sm.advancePage()
            try {
                sm.transitionTo(UploadState(sm.currentPage))
            } catch (e: IndexOutOfBoundsException) {
                sm.transitionTo(WaitForDoneState())
            }
        } else if (data.size == 8 && data[0] == DevicePacket.HELLO.value) {
            // ignore
        } else {
            sm.transitionTo(FailureState(Failure.PROTOCOL_ERROR))
        }
    }

    override fun details(): StateUpdate = StateUpdate(State.CHECK_CRC, sm.serialno, null, sm.progress())
}

class WaitForDoneState : InternalState {
    override lateinit var sm: StateMachine

    override suspend fun process(data: ByteArray) {
        if (data.size == 1 && data[0] == DevicePacket.DONE.value) {
            sm.confirmPage()
            sm.transitionTo(CompleteState())
        } else if (data.size == 1 && data[0] == DevicePacket.ERROR.value) {
            sm.transitionTo(FailureState(Failure.PAGE_CRC_ERROR))
        } else if (data.size == 8 && data[0] == DevicePacket.HELLO.value) {
            // ignore
        } else {
            sm.transitionTo(FailureState(Failure.PROTOCOL_ERROR))
        }
    }

    override fun details(): StateUpdate = StateUpdate(State.WAIT_FOR_DONE, sm.serialno, null, sm.progress())
}

class FailureState(private val failure: Failure) : InternalState {
    override lateinit var sm: StateMachine
    override suspend fun process(data: ByteArray) {}
    override fun details(): StateUpdate = StateUpdate(State.FAILURE, sm.serialno, failure, sm.progress())
}

class CompleteState : InternalState {
    override lateinit var sm: StateMachine
    override suspend fun process(data: ByteArray) {}
    override fun details(): StateUpdate = StateUpdate(State.COMPLETE, sm.serialno, null, sm.progress())
}
