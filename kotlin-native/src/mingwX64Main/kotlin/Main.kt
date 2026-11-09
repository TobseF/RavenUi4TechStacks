// Simon with six coloured pads, on Raven, from Kotlin/Native (cinterop).
@file:OptIn(ExperimentalForeignApi::class)

import kotlinx.cinterop.*
import raven.*

private val XML = """
<Window Title="Simon - Kotlin/Native" Width="540" Height="480" Theme="RavenGlassDark">
    <!-- glass look: round glossy pads (a capsule of glass is round when it is square),
         a two-layer highlight on top that lets the clicks through -->
    <VBox Spacing="14" Slot.Padding="18">
        <HBox Spacing="12">
            <Label Name="status" Text="Press Start" TextSize="20" Slot.Fill="1" Slot.VAlign="Center"/>
            <Button Text="Start" Style="Primary" OnClicked="start" Width="110" Height="38"/>
        </HBox>
        <UniformGrid Columns="3" Spacing="10" Slot.Fill="1">
            <Overlay Width="146" Height="146" Slot.HAlign="Center" Slot.VAlign="Center">
                <Button Name="pad0" Text="" OnClicked="pad0"/>
                <VBox HitTestable="false" Slot.HAlign="Center" Slot.VAlign="Top">
                    <Spacer Height="7"/>
                    <Overlay>
                        <Border Material="Solid" Width="100" Height="52" CornerRadius="26" Background="#FFFFFF10"/>
                        <VBox Slot.HAlign="Center"><Spacer Height="4"/><Border Material="Solid" Width="74" Height="28" CornerRadius="14" Background="#FFFFFF22"/></VBox>
                    </Overlay>
                </VBox>
            </Overlay>
            <Overlay Width="146" Height="146" Slot.HAlign="Center" Slot.VAlign="Center">
                <Button Name="pad1" Text="" OnClicked="pad1"/>
                <VBox HitTestable="false" Slot.HAlign="Center" Slot.VAlign="Top">
                    <Spacer Height="7"/>
                    <Overlay>
                        <Border Material="Solid" Width="100" Height="52" CornerRadius="26" Background="#FFFFFF10"/>
                        <VBox Slot.HAlign="Center"><Spacer Height="4"/><Border Material="Solid" Width="74" Height="28" CornerRadius="14" Background="#FFFFFF22"/></VBox>
                    </Overlay>
                </VBox>
            </Overlay>
            <Overlay Width="146" Height="146" Slot.HAlign="Center" Slot.VAlign="Center">
                <Button Name="pad2" Text="" OnClicked="pad2"/>
                <VBox HitTestable="false" Slot.HAlign="Center" Slot.VAlign="Top">
                    <Spacer Height="7"/>
                    <Overlay>
                        <Border Material="Solid" Width="100" Height="52" CornerRadius="26" Background="#FFFFFF10"/>
                        <VBox Slot.HAlign="Center"><Spacer Height="4"/><Border Material="Solid" Width="74" Height="28" CornerRadius="14" Background="#FFFFFF22"/></VBox>
                    </Overlay>
                </VBox>
            </Overlay>
            <Overlay Width="146" Height="146" Slot.HAlign="Center" Slot.VAlign="Center">
                <Button Name="pad3" Text="" OnClicked="pad3"/>
                <VBox HitTestable="false" Slot.HAlign="Center" Slot.VAlign="Top">
                    <Spacer Height="7"/>
                    <Overlay>
                        <Border Material="Solid" Width="100" Height="52" CornerRadius="26" Background="#FFFFFF10"/>
                        <VBox Slot.HAlign="Center"><Spacer Height="4"/><Border Material="Solid" Width="74" Height="28" CornerRadius="14" Background="#FFFFFF22"/></VBox>
                    </Overlay>
                </VBox>
            </Overlay>
            <Overlay Width="146" Height="146" Slot.HAlign="Center" Slot.VAlign="Center">
                <Button Name="pad4" Text="" OnClicked="pad4"/>
                <VBox HitTestable="false" Slot.HAlign="Center" Slot.VAlign="Top">
                    <Spacer Height="7"/>
                    <Overlay>
                        <Border Material="Solid" Width="100" Height="52" CornerRadius="26" Background="#FFFFFF10"/>
                        <VBox Slot.HAlign="Center"><Spacer Height="4"/><Border Material="Solid" Width="74" Height="28" CornerRadius="14" Background="#FFFFFF22"/></VBox>
                    </Overlay>
                </VBox>
            </Overlay>
            <Overlay Width="146" Height="146" Slot.HAlign="Center" Slot.VAlign="Center">
                <Button Name="pad5" Text="" OnClicked="pad5"/>
                <VBox HitTestable="false" Slot.HAlign="Center" Slot.VAlign="Top">
                    <Spacer Height="7"/>
                    <Overlay>
                        <Border Material="Solid" Width="100" Height="52" CornerRadius="26" Background="#FFFFFF10"/>
                        <VBox Slot.HAlign="Center"><Spacer Height="4"/><Border Material="Solid" Width="74" Height="28" CornerRadius="14" Background="#FFFFFF22"/></VBox>
                    </Overlay>
                </VBox>
            </Overlay>
        </UniformGrid>
    </VBox>
</Window>"""

/** dim colour, lit colour of each pad */
private val COLORS = arrayOf(
    floatArrayOf(0.62f, 0.12f, 0.14f) to floatArrayOf(1.00f, 0.35f, 0.35f), // red
    floatArrayOf(0.10f, 0.52f, 0.18f) to floatArrayOf(0.35f, 1.00f, 0.45f), // green
    floatArrayOf(0.12f, 0.26f, 0.68f) to floatArrayOf(0.40f, 0.62f, 1.00f), // blue
    floatArrayOf(0.66f, 0.58f, 0.08f) to floatArrayOf(1.00f, 0.95f, 0.35f), // yellow
    floatArrayOf(0.48f, 0.16f, 0.60f) to floatArrayOf(0.88f, 0.45f, 1.00f), // purple
    floatArrayOf(0.70f, 0.34f, 0.06f) to floatArrayOf(1.00f, 0.66f, 0.25f), // orange
)

private const val EV_PLAY_STEP = 1L
private const val EV_PAD_OFF = 2L
private const val EV_NEXT_ROUND = 3L

// The game state: Raven calls back on the UI thread only.
private val pads = UIntArray(6)
private var status = 0u
private val sequence = ArrayList<Int>()
private var input = 0
private var step = 0
private var accepting = false
private var rng = 0

private fun setPad(pad: Int, lit: Boolean) {
    val (dim, bright) = COLORS[pad]
    val base = if (lit) bright else dim
    val hover = if (lit) bright else floatArrayOf(dim[0] * 1.2f, dim[1] * 1.2f, dim[2] * 1.2f)
    val id = pads[pad]
    Rwidget_set_color(id, R_COLOR_CONTROL, base[0], base[1], base[2], 1f)
    Rwidget_set_color(id, R_COLOR_CONTROL_HOVERED, hover[0], hover[1], hover[2], 1f)
    Rwidget_set_color(id, R_COLOR_CONTROL_PRESSED, bright[0], bright[1], bright[2], 1f)
}

private fun setStatus(text: String) = Rlabel_set_text(status, text)

/** C callbacks cannot capture: the event and the pad travel in user_data. */
private val onTimer = staticCFunction { _: UInt, data: COpaquePointer? ->
    val value = data.toLong()
    when (value / 16) {
        EV_PLAY_STEP -> playStep()
        EV_PAD_OFF -> setPad((value % 16).toInt(), false)
        EV_NEXT_ROUND -> nextRound()
    }
}

private val onPad = staticCFunction { _: UInt, _: CPointer<ByteVar>?, data: COpaquePointer? ->
    press(data.toLong().toInt())
}

private val onStart = staticCFunction { _: UInt, _: CPointer<ByteVar>?, _: COpaquePointer? ->
    sequence.clear()
    nextRound()
}

private fun later(seconds: Double, event: Long, pad: Int = 0) {
    Rtimer_create(seconds, 0, onTimer, (event * 16 + pad).toCPointer<CPointed>())
}

private fun nextRandom(): Int {
    var x = rng // xorshift32
    x = x xor (x shl 13)
    x = x xor (x ushr 17)
    x = x xor (x shl 5)
    rng = x
    return x.toUInt().rem(6u).toInt()
}

private fun nextRound() {
    sequence += nextRandom()
    input = 0
    step = 0
    accepting = false
    setStatus("Round ${sequence.size} - watch")
    later(0.6, EV_PLAY_STEP)
}

private fun playStep() {
    val len = sequence.size
    if (step >= len * 2) {
        accepting = true
        setStatus("Round $len - your turn")
        return
    }
    val pad = sequence[step / 2]
    val lit = step % 2 == 0
    setPad(pad, lit)
    step++
    val on = maxOf(0.22, 0.55 - len * 0.02)
    later(if (lit) on else 0.15, EV_PLAY_STEP)
}

private fun press(pad: Int) {
    if (!accepting) return
    setPad(pad, true)
    later(0.2, EV_PAD_OFF, pad)
    if (sequence[input] != pad) {
        accepting = false
        setStatus("Wrong! Score: ${sequence.size - 1} - press Start")
        return
    }
    input++
    if (input == sequence.size) {
        accepting = false
        setStatus("Correct!")
        later(0.8, EV_NEXT_ROUND)
    }
}

fun main() {
    rng = (platform.posix.time(null).toInt() * 2654435761u.toInt()) or 1
    if (Rinit(R_BACKEND_VULKAN) == 0) return
    // cinterop passes a String as a zero-terminated UTF-8 copy
    val window = Rwindow_create_from_xml(XML, XML.encodeToByteArray().size.convert())
    for (i in 0 until 6) {
        pads[i] = Rwidget_find(window, "pad$i")
        setPad(i, false)
        Raction_bind("pad$i", onPad, i.toLong().toCPointer<CPointed>())
    }
    status = Rwidget_find(window, "status")
    Raction_bind("start", onStart, null)
    while (Rwait_frame(-1.0) != 0) { }
    Rshutdown()
}
