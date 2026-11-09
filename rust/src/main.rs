// Simon with six coloured pads, on Raven, from Rust.
#![windows_subsystem = "windows"]

use raven::*;
use std::cell::RefCell;
use std::os::raw::c_void;

const XML: &str = r##"
<Window Title="Simon - Rust" Width="540" Height="480" Theme="RavenGlassDark">
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
</Window>"##;

/// dim colour, lit colour of each pad
const COLORS: [([f32; 3], [f32; 3]); 6] = [
    ([0.62, 0.12, 0.14], [1.00, 0.35, 0.35]), // red
    ([0.10, 0.52, 0.18], [0.35, 1.00, 0.45]), // green
    ([0.12, 0.26, 0.68], [0.40, 0.62, 1.00]), // blue
    ([0.66, 0.58, 0.08], [1.00, 0.95, 0.35]), // yellow
    ([0.48, 0.16, 0.60], [0.88, 0.45, 1.00]), // purple
    ([0.70, 0.34, 0.06], [1.00, 0.66, 0.25]), // orange
];

const EV_PLAY_STEP: usize = 1;
const EV_PAD_OFF: usize = 2;
const EV_NEXT_ROUND: usize = 3;

struct Game {
    pads: [Rid; 6],
    status: Rid,
    sequence: Vec<usize>,
    input: usize,
    step: usize,
    accepting: bool,
    rng: u32,
}

thread_local!(static GAME: RefCell<Option<Game>> = RefCell::new(None));

fn with<R>(f: impl FnOnce(&mut Game) -> R) -> R {
    GAME.with(|g| f(g.borrow_mut().as_mut().unwrap()))
}

fn set_pad(game: &Game, pad: usize, lit: bool) {
    let (dim, bright) = COLORS[pad];
    let (base, hover) = if lit { (bright, bright) } else { (dim, [dim[0] * 1.2, dim[1] * 1.2, dim[2] * 1.2]) };
    unsafe {
        let id = game.pads[pad];
        Rwidget_set_color(id, R_COLOR_CONTROL, base[0], base[1], base[2], 1.0);
        Rwidget_set_color(id, R_COLOR_CONTROL_HOVERED, hover[0], hover[1], hover[2], 1.0);
        Rwidget_set_color(id, R_COLOR_CONTROL_PRESSED, bright[0], bright[1], bright[2], 1.0);
    }
}

fn status(game: &Game, text: &str) {
    unsafe { Rlabel_set_text(game.status, cstr(text).as_ptr()) };
}

fn later(seconds: f64, event: usize, pad: usize) {
    unsafe { Rtimer_create(seconds, 0, Some(on_timer), (event * 16 + pad) as *mut c_void) };
}

fn next_random(game: &mut Game) -> usize {
    // xorshift32
    let mut x = game.rng;
    x ^= x << 13;
    x ^= x >> 17;
    x ^= x << 5;
    game.rng = x;
    (x % 6) as usize
}

fn next_round(game: &mut Game) {
    let pad = next_random(game);
    game.sequence.push(pad);
    game.input = 0;
    game.step = 0;
    game.accepting = false;
    status(game, &format!("Round {} - watch", game.sequence.len()));
    later(0.6, EV_PLAY_STEP, 0);
}

fn play_step(game: &mut Game) {
    let len = game.sequence.len();
    if game.step >= len * 2 {
        game.accepting = true;
        status(game, &format!("Round {} - your turn", len));
        return;
    }
    let pad = game.sequence[game.step / 2];
    let lit = game.step % 2 == 0;
    set_pad(game, pad, lit);
    game.step += 1;
    let on = (0.55 - len as f64 * 0.02).max(0.22);
    later(if lit { on } else { 0.15 }, EV_PLAY_STEP, 0);
}

fn press(game: &mut Game, pad: usize) {
    if !game.accepting {
        return;
    }
    set_pad(game, pad, true);
    later(0.2, EV_PAD_OFF, pad);
    if game.sequence[game.input] != pad {
        game.accepting = false;
        status(game, &format!("Wrong! Score: {} - press Start", game.sequence.len() - 1));
        return;
    }
    game.input += 1;
    if game.input == game.sequence.len() {
        game.accepting = false;
        status(game, "Correct!");
        later(0.8, EV_NEXT_ROUND, 0);
    }
}

unsafe extern "C" fn on_timer(_timer: Rid, data: *mut c_void) {
    let data = data as usize;
    let (event, pad) = (data / 16, data % 16);
    with(|game| match event {
        EV_PLAY_STEP => play_step(game),
        EV_PAD_OFF => set_pad(game, pad, false),
        EV_NEXT_ROUND => next_round(game),
        _ => {}
    });
}

fn main() {
    unsafe {
        if Rinit(R_BACKEND_VULKAN) == 0 {
            std::process::exit(1);
        }
        let window = window_from_xml(XML);
        let mut pads = [0; 6];
        for (i, pad) in pads.iter_mut().enumerate() {
            *pad = widget_find(window, &format!("pad{}", i));
        }
        let seed = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .map(|d| d.subsec_nanos() | 1)
            .unwrap_or(0x9E37_79B9);
        let game = Game { pads, status: widget_find(window, "status"), sequence: Vec::new(), input: 0, step: 0, accepting: false, rng: seed };
        for i in 0..6 {
            set_pad(&game, i, false);
        }
        GAME.with(|g| *g.borrow_mut() = Some(game));

        for i in 0..6 {
            action_bind(&format!("pad{}", i), move |_, _| with(|game| press(game, i)));
        }
        action_bind("start", |_, _| {
            with(|game| {
                game.sequence.clear();
                next_round(game);
            })
        });

        while Rwait_frame(-1.0) != 0 {}
        Rshutdown();
    }
}
