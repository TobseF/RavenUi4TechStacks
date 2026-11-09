// Simon with six coloured pads, on Raven, from Java (Foreign Function & Memory API).
package simon;

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;

import static raven.R.*;

public final class Simon {
    static final String XML = """
        <Window Title="%s" Width="540" Height="480" Theme="RavenGlassDark">
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
        </Window>""";

    /** dim colour, lit colour of each pad */
    static final float[][][] COLORS = {
        {{0.62f, 0.12f, 0.14f}, {1.00f, 0.35f, 0.35f}}, // red
        {{0.10f, 0.52f, 0.18f}, {0.35f, 1.00f, 0.45f}}, // green
        {{0.12f, 0.26f, 0.68f}, {0.40f, 0.62f, 1.00f}}, // blue
        {{0.66f, 0.58f, 0.08f}, {1.00f, 0.95f, 0.35f}}, // yellow
        {{0.48f, 0.16f, 0.60f}, {0.88f, 0.45f, 1.00f}}, // purple
        {{0.70f, 0.34f, 0.06f}, {1.00f, 0.66f, 0.25f}}, // orange
    };

    static final int EV_PLAY_STEP = 1, EV_PAD_OFF = 2, EV_NEXT_ROUND = 3;

    static final int[] pads = new int[6];
    static int status;
    static final List<Integer> sequence = new ArrayList<>();
    static int input, step;
    static boolean accepting;
    static int rng;

    /** One callback object for every timer: the binding makes one upcall stub per object. */
    static final RTimerCallback TIMER = (timer, data) -> {
        int value = (int) data.address();
        int event = value / 16, pad = value % 16;
        switch (event) {
            case EV_PLAY_STEP -> playStep();
            case EV_PAD_OFF -> setPad(pad, false);
            case EV_NEXT_ROUND -> nextRound();
            default -> { }
        }
    };

    static void setPad(int pad, boolean lit) {
        float[] dim = COLORS[pad][0], bright = COLORS[pad][1];
        float[] base = lit ? bright : dim;
        float[] hover = lit ? bright : new float[] {dim[0] * 1.2f, dim[1] * 1.2f, dim[2] * 1.2f};
        int id = pads[pad];
        Rwidget_set_color(id, R_COLOR_CONTROL, base[0], base[1], base[2], 1f);
        Rwidget_set_color(id, R_COLOR_CONTROL_HOVERED, hover[0], hover[1], hover[2], 1f);
        Rwidget_set_color(id, R_COLOR_CONTROL_PRESSED, bright[0], bright[1], bright[2], 1f);
    }

    static void later(double seconds, int event, int pad) {
        Rtimer_create(seconds, 0, TIMER, MemorySegment.ofAddress(event * 16L + pad));
    }

    static int nextRandom() {
        int x = rng; // xorshift32
        x ^= x << 13;
        x ^= x >>> 17;
        x ^= x << 5;
        rng = x;
        return Integer.remainderUnsigned(x, 6);
    }

    static void nextRound() {
        sequence.add(nextRandom());
        input = 0;
        step = 0;
        accepting = false;
        Rlabel_set_text(status, "Round " + sequence.size() + " - watch");
        later(0.6, EV_PLAY_STEP, 0);
    }

    static void playStep() {
        int len = sequence.size();
        if (step >= len * 2) {
            accepting = true;
            Rlabel_set_text(status, "Round " + len + " - your turn");
            return;
        }
        int pad = sequence.get(step / 2);
        boolean lit = step % 2 == 0;
        setPad(pad, lit);
        step++;
        double on = Math.max(0.22, 0.55 - len * 0.02);
        later(lit ? on : 0.15, EV_PLAY_STEP, 0);
    }

    static void press(int pad) {
        if (!accepting) return;
        setPad(pad, true);
        later(0.2, EV_PAD_OFF, pad);
        if (sequence.get(input) != pad) {
            accepting = false;
            Rlabel_set_text(status, "Wrong! Score: " + (sequence.size() - 1) + " - press Start");
            return;
        }
        input++;
        if (input == sequence.size()) {
            accepting = false;
            Rlabel_set_text(status, "Correct!");
            later(0.8, EV_NEXT_ROUND, 0);
        }
    }

    public static void main(String[] args) {
        boolean nativeImage = System.getProperty("org.graalvm.nativeimage.imagecode") != null;
        String title = nativeImage ? "Simon - Java (GraalVM native)" : "Simon - Java";
        if (nativeImage && System.getProperty("raven.library") == null) {
            // raven.dll lies beside the exe (R looks in the working directory otherwise)
            ProcessHandle.current().info().command().ifPresent(exe ->
                System.setProperty("raven.library", java.nio.file.Path.of(exe).resolveSibling("raven.dll").toString()));
        }
        rng = (int) System.nanoTime() | 1;
        if (Rinit(R_BACKEND_VULKAN) == 0) System.exit(1);
        int window = Rwindow_create_from_xml(XML.formatted(title));
        for (int i = 0; i < 6; i++) {
            pads[i] = Rwidget_find(window, "pad" + i);
            setPad(i, false);
            int pad = i;
            Raction_bind("pad" + i, (sender, action, user) -> press(pad));
        }
        status = Rwidget_find(window, "status");
        Raction_bind("start", (sender, action, user) -> {
            sequence.clear();
            nextRound();
        });
        while (Rwait_frame(-1.0) != 0) { }
        Rshutdown();
    }
}
