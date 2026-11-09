// Renders the README figures from results.json into docs/img/*.svg.
//   node docs/generate-charts.mjs
// Plain SVG, no dependencies. Every figure carries its own light/dark styles
// (prefers-color-scheme), so it reads on GitHub in both themes.
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const data = JSON.parse(readFileSync(join(here, 'results.json'), 'utf8'));
const out = join(here, 'img');
mkdirSync(out, { recursive: true });

const MiB = 1024 * 1024;
const W = 760;
const esc = (s) => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
const mib = (b, d = 2) => (b / MiB).toFixed(d);
const kib = (b) => Math.round(b / 1024).toLocaleString('en-US');
const tw = (s, px = 12) => s.length * px * 0.56; // rough text width for layout

// ---- theme ------------------------------------------------------------------
// Categorical slots 1-4 of the reference palette (validated light + dark, adjacent order).
const STYLE = `
<style>
  text { font-family: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; }
  .surface { fill: #fcfcfb; } .ring { fill: none; stroke: rgba(11,11,11,0.10); }
  .ink { fill: #0b0b0b; } .ink2 { fill: #52514e; } .muted { fill: #898781; }
  .grid { stroke: #e1e0d9; } .axis { stroke: #c3c2b7; } .gap { stroke: #fcfcfb; }
  .s1 { fill: #2a78d6; } .s2 { fill: #eb6834; } .s3 { fill: #1baf7a; } .s4 { fill: #eda100; } .sn { fill: #c3c2b7; }
  .l1 { stroke: #2a78d6; } .l2 { stroke: #eb6834; }
  .box { fill: #f3f3f0; stroke: #c3c2b7; } .boxhi { fill: #eaf2fc; stroke: #2a78d6; }
  .pill1 { fill: #dbe9fa; } .pill2 { fill: #fde4d9; }
  .arrow { stroke: #898781; fill: none; } .arrowhead { fill: #898781; }
  @media (prefers-color-scheme: dark) {
    .surface { fill: #1a1a19; } .ring { stroke: rgba(255,255,255,0.10); }
    .ink { fill: #f0efec; } .ink2 { fill: #c3c2b7; }
    .grid { stroke: #2c2c2a; } .axis { stroke: #383835; } .gap { stroke: #1a1a19; }
    .s1 { fill: #3987e5; } .s2 { fill: #d95926; } .s3 { fill: #199e70; } .s4 { fill: #c98500; } .sn { fill: #5e5d58; }
    .l1 { stroke: #3987e5; } .l2 { stroke: #d95926; }
    .box { fill: #232321; stroke: #383835; } .boxhi { fill: #172538; stroke: #3987e5; }
    .pill1 { fill: #1d3354; } .pill2 { fill: #4a2416; }
  }
</style>`;

function svg(height, title, desc, body) {
    return `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${height}" viewBox="0 0 ${W} ${height}" role="img" aria-labelledby="t d">
<title id="t">${esc(title)}</title><desc id="d">${esc(desc)}</desc>${STYLE}
<rect class="surface" x="0.5" y="0.5" width="${W - 1}" height="${height - 1}" rx="12"/>
<rect class="ring" x="0.5" y="0.5" width="${W - 1}" height="${height - 1}" rx="12"/>
${body}
</svg>\n`;
}

const header = (title, subtitle) =>
    `<text class="ink" x="24" y="36" font-size="17" font-weight="600">${esc(title)}</text>
<text class="ink2" x="24" y="57" font-size="12.5">${esc(subtitle)}</text>`;

// rectangle whose right end is rounded (data end), the left end square on the baseline
function bar(x, y, w, h, cls, r = 4, tip = '') {
    w = Math.max(w, 0.5);
    const rr = Math.min(r, w, h / 2);
    const d = `M${x},${y}h${w - rr}a${rr},${rr} 0 0 1 ${rr},${rr}v${h - 2 * rr}a${rr},${rr} 0 0 1 -${rr},${rr}h-${w - rr}z`;
    return `<path class="${cls}" d="${d}">${tip ? `<title>${esc(tip)}</title>` : ''}</path>`;
}
const rect = (x, y, w, h, cls, tip = '') =>
    `<rect class="${cls}" x="${x}" y="${y}" width="${Math.max(w, 0.5)}" height="${h}">${tip ? `<title>${esc(tip)}</title>` : ''}</rect>`;

function xAxis(x0, x1, y0, y1, max, step, fmt) {
    let s = '';
    for (let v = 0; v <= max + 1e-9; v += step) {
        const x = x0 + (v / max) * (x1 - x0);
        s += `<line class="${v === 0 ? 'axis' : 'grid'}" x1="${x}" x2="${x}" y1="${y0}" y2="${y1}" stroke-width="1"/>`;
        s += `<text class="muted" x="${x}" y="${y1 + 16}" font-size="11" text-anchor="middle" style="font-variant-numeric:tabular-nums">${fmt(v)}</text>`;
    }
    return s;
}

const rowLabels = (v, y) =>
    `<text class="ink" x="24" y="${y - 2}" font-size="13" font-weight="600">${esc(v.name)}</text>
<text class="muted" x="24" y="${y + 13}" font-size="11">${esc(v.sub)}</text>`;

const V = data.variants;
const C = V.find((v) => v.id === 'c');

// ---- 1. distribution size ------------------------------------------------------
{
    const x0 = 196, x1 = 640, max = 30, top = 92, rowH = 46, bh = 20;
    let body = header('Distribution size', 'Everything needed to run on Windows x64 · MiB · lower is better');
    // legend
    body += `<rect class="s1" x="24" y="70" width="10" height="10" rx="2"/><text class="ink2" x="40" y="79" font-size="11.5">Raven library (static: incl. C runtime)</text>`;
    body += `<rect class="s2" x="270" y="70" width="10" height="10" rx="2"/><text class="ink2" x="286" y="79" font-size="11.5">Language stack (runtime, binding, game)</text>`;
    const yEnd = top + rowH * V.length;
    body += xAxis(x0, x1, top - 6, yEnd - 10, max, 5, (v) => v);
    V.forEach((v, i) => {
        const y = top + i * rowH + 6;
        const cy = y + bh / 2;
        body += rowLabels(v, cy);
        const wR = (v.raven / MiB / max) * (x1 - x0);
        const wT = (v.bytes / MiB / max) * (x1 - x0);
        const over = v.bytes - v.raven;
        if (over > 0) {
            body += bar(x0, y, wR, bh, 's1', 0, `${v.ravenLabel}: ${mib(v.raven)} MiB`);
            body += bar(x0 + wR + 2, y, Math.max(wT - wR - 2, 1.5), bh, 's2', 4, `Language stack: ${mib(over)} MiB`);
        } else {
            body += bar(x0, y, wR, bh, 's1', 4, `${v.ravenLabel}: ${mib(v.raven)} MiB`);
        }
        const delta = v.bytes - C.bytes;
        const deltaTxt = v.id === 'c' ? 'reference' : delta < MiB ? `+${kib(delta)} KiB vs C` : `+${mib(delta, 1)} MiB vs C`;
        const lx = x0 + wT + 8;
        body += `<text class="ink" x="${lx}" y="${cy + 1}" font-size="13" font-weight="600" style="font-variant-numeric:tabular-nums">${mib(v.bytes)} MiB</text>`;
        body += `<text class="muted" x="${lx}" y="${cy + 15}" font-size="11">${deltaTxt}</text>`;
    });
    writeFileSync(join(out, 'size.svg'), svg(yEnd + 24, 'Distribution size per variant',
        V.map((v) => `${v.name}: ${mib(v.bytes)} MiB`).join('; '), body));
}

// ---- 2. JVM breakdown ----------------------------------------------------------------
{
    const roles = { raven: 's1', vm: 's2', lib: 's3', app: 's4', other: 'sn' };
    const x0 = 196, x1 = 700, max = 30, bh = 26;
    const rows = [['graal', 'Java · GraalVM', 'native-image -Os'], ['java', 'Java · jlink', 'java.base only']];
    let body = header('Where the Java megabytes go', 'Composition of the two JVM-based distributions · MiB');
    const tops = [96, 196];
    rows.forEach(([id, name, sub], i) => {
        const parts = data.breakdown[id];
        const y = tops[i];
        body += rowLabels({ name, sub }, y + bh / 2);
        let x = x0;
        parts.forEach((p, j) => {
            const w = (p.bytes / MiB / max) * (x1 - x0);
            const last = j === parts.length - 1;
            body += bar(x, y, Math.max(w - (last ? 0 : 2), 1.5), bh, roles[p.role], last ? 4 : 0, `${p.label}: ${mib(p.bytes)} MiB`);
            // value inside the segment when it fits
            const t = mib(p.bytes);
            if (w > tw(t, 12) + 12) {
                const dark = p.role === 'app';
                body += `<text x="${x + 8}" y="${y + bh / 2 + 4}" font-size="12" font-weight="600" fill="${dark ? '#0b0b0b' : '#ffffff'}" style="font-variant-numeric:tabular-nums">${t}</text>`;
            }
            x += w;
        });
        const total = parts.reduce((a, p) => a + p.bytes, 0);
        body += `<text class="ink" x="${Math.min(x + 8, x1 - 60)}" y="${y - 6}" font-size="12" font-weight="600" text-anchor="start">${mib(total)} MiB</text>`;
        // inline legend under the bar: swatch + label + value, flowing left to right
        let lx = x0, ly = y + bh + 20;
        parts.forEach((p) => {
            const label = `${p.label} ${mib(p.bytes)}`;
            const w = 14 + tw(label, 11.5) + 16;
            if (lx + w > W - 16) { lx = x0; ly += 18; }
            body += `<rect class="${roles[p.role]}" x="${lx}" y="${ly - 9}" width="10" height="10" rx="2"/>`;
            body += `<text class="ink2" x="${lx + 14}" y="${ly}" font-size="11.5">${esc(p.label)} <tspan class="muted">${mib(p.bytes)}</tspan></text>`;
            lx += w;
        });
    });
    body += `<text class="muted" x="24" y="300" font-size="11">The empty Hello World was built with the same native-image flags; with and without -H:+ForeignAPISupport it is 10.88 MiB.</text>`;
    writeFileSync(join(out, 'jvm-breakdown.svg'), svg(318, 'Composition of the JVM-based distributions',
        'GraalVM: raven.dll 1.44, native image floor 10.88, game and FFM 5.21 MiB. jlink: raven.dll 1.44, jvm.dll 14.71, lib/modules 11.11, other runtime 1.06, app 0.19 MiB.', body));
}

// ---- 3. startup time -------------------------------------------------------------------
{
    const x0 = 196, x1 = 680, min = 400, max = 800, top = 92, rowH = 40;
    let body = header('Time to first window', 'Process start until the window appears over UI Automation · 3 cold runs each · ms (axis starts at 400)');
    const yEnd = top + rowH * V.length;
    const X = (t) => x0 + ((t - min) / (max - min)) * (x1 - x0);
    for (let v = min; v <= max; v += 50) {
        body += `<line class="${v === min ? 'axis' : 'grid'}" x1="${X(v)}" x2="${X(v)}" y1="${top - 4}" y2="${yEnd - 8}" stroke-width="1"/>`;
        body += `<text class="muted" x="${X(v)}" y="${yEnd + 8}" font-size="11" text-anchor="middle" style="font-variant-numeric:tabular-nums">${v}</text>`;
    }
    const cMean = C.startup.reduce((a, b) => a + b, 0) / C.startup.length;
    const cx = X(cMean);
    body += `<line class="l2" x1="${cx}" x2="${cx}" y1="${top - 4}" y2="${yEnd - 8}" stroke-width="1.5" stroke-dasharray="4 3"/>`;
    body += `<text class="ink2" x="${cx}" y="${top - 10}" font-size="11" text-anchor="middle">C baseline mean ${Math.round(cMean)} ms</text>`;
    V.forEach((v, i) => {
        const cy = top + i * rowH + 16;
        body += rowLabels(v, cy);
        const mean = v.startup.reduce((a, b) => a + b, 0) / v.startup.length;
        const lo = Math.min(...v.startup), hi = Math.max(...v.startup);
        body += `<line class="l1" x1="${X(lo)}" x2="${X(hi)}" y1="${cy}" y2="${cy}" stroke-width="2" stroke-linecap="round" opacity="0.45"/>`;
        v.startup.forEach((t, k) => {
            body += `<circle class="s1 gap" cx="${X(t)}" cy="${cy}" r="5" stroke-width="2"><title>${esc(v.name)} run ${k + 1}: ${t} ms</title></circle>`;
        });
        body += `<text class="ink" x="${X(hi) + 12}" y="${cy + 4}" font-size="12.5" font-weight="600" style="font-variant-numeric:tabular-nums">${Math.round(mean)} ms</text>`;
    });
    writeFileSync(join(out, 'startup.svg'), svg(yEnd + 26, 'Time to first window',
        V.map((v) => `${v.name}: ${v.startup.join(', ')} ms`).join('; '), body));
}

// ---- 4. memory ----------------------------------------------------------------------------
{
    const x0 = 196, x1 = 640, max = 200, top = 84, rowH = 40, bh = 18;
    let body = header('Working set after one round', 'Resident memory · MiB · single sample · dominated by the Vulkan driver, not the language');
    const yEnd = top + rowH * V.length;
    body += xAxis(x0, x1, top - 4, yEnd - 8, max, 50, (v) => v);
    V.forEach((v, i) => {
        const y = top + i * rowH + 6, cy = y + bh / 2;
        body += rowLabels(v, cy + 2);
        const w = (v.workingSet / max) * (x1 - x0);
        body += bar(x0, y, w, bh, 's1', 4, `${v.name}: ${v.workingSet} MiB working set, ${v.private} MiB private`);
        body += `<text class="ink" x="${x0 + w + 8}" y="${cy + 4}" font-size="12.5" font-weight="600" style="font-variant-numeric:tabular-nums">${v.workingSet.toFixed(0)} MiB</text>`;
    });
    const j = V.find((v) => v.id === 'java');
    body += `<text class="muted" x="24" y="${yEnd + 26}" font-size="11">Java · jlink commits ${Math.round(j.private).toLocaleString('en-US')} MiB of private bytes: the JVM's default heap reservation (1/64 of RAM), not memory in use. Tunable with -Xmx/-Xms.</text>`;
    writeFileSync(join(out, 'memory.svg'), svg(yEnd + 42, 'Working set per variant',
        V.map((v) => `${v.name}: ${v.workingSet} MiB`).join('; '), body));
}

// ---- 5. hero tiles ----------------------------------------------------------------------------
{
    const tiles = V.filter((v) => v.id !== 'c');
    const tw4 = (W - 48 - 3 * 12) / 4;
    let body = `<text class="ink" x="24" y="40" font-size="22" font-weight="700">Raven × 4 language stacks</text>
<text class="ink2" x="24" y="62" font-size="13">One Simon game, one C GUI library, four bindings — what does each stack add to a Windows build?</text>`;
    tiles.forEach((v, i) => {
        const x = 24 + i * (tw4 + 12), y = 84;
        const delta = v.bytes - C.bytes;
        body += `<rect class="box" x="${x}" y="${y}" width="${tw4}" height="92" rx="10" stroke-width="1"/>`;
        body += `<rect class="s1" x="${x}" y="${y}" width="${tw4}" height="4" rx="2"/>`;
        body += `<text class="ink2" x="${x + 14}" y="${y + 26}" font-size="12" font-weight="600">${esc(v.name)}</text>`;
        body += `<text class="ink" x="${x + 14}" y="${y + 58}" font-size="28" font-weight="700">${mib(v.bytes, v.bytes < 10 * MiB ? 2 : 1)}<tspan font-size="14" font-weight="600" dx="4">MiB</tspan></text>`;
        body += `<text class="muted" x="${x + 14}" y="${y + 78}" font-size="11">${v.files} file${v.files > 1 ? 's' : ''} · ${delta < MiB ? '+' + kib(delta) + ' KiB' : '+' + mib(delta, 1) + ' MiB'} over C</text>`;
    });
    writeFileSync(join(out, 'hero.svg'), svg(196, 'Raven × 4 language stacks: distribution sizes',
        tiles.map((v) => `${v.name}: ${mib(v.bytes)} MiB`).join('; '), body));
}

// ---- 6. build pipeline -----------------------------------------------------------------------------
{
    const box = (x, y, w, h, title, sub, cls = 'box') =>
        `<rect class="${cls}" x="${x}" y="${y}" width="${w}" height="${h}" rx="8" stroke-width="1"/>
<text class="ink" x="${x + 12}" y="${y + 20}" font-size="12.5" font-weight="600">${esc(title)}</text>
${sub ? `<text class="muted" x="${x + 12}" y="${y + 36}" font-size="11">${esc(sub)}</text>` : ''}`;
    const pill = (x, y, text, cls) => {
        const w = tw(text, 10.5) + 14;
        return `<rect class="${cls}" x="${x}" y="${y}" width="${w}" height="16" rx="8"/><text class="ink" x="${x + 7}" y="${y + 11.5}" font-size="10.5" font-weight="600">${esc(text)}</text>`;
    };
    const arrow = (x1, y1, x2, y2) => {
        const mx = (x1 + x2) / 2;
        return `<path class="arrow" d="M${x1},${y1} C${mx},${y1} ${mx},${y2} ${x2 - 7},${y2}" stroke-width="1.5"/>
<path class="arrowhead" d="M${x2},${y2} l-8,-4 v8 z"/>`;
    };
    let body = header('Build pipeline', 'Reproducible: Raven pinned, toolchains pinned, Linux containers cross-compile to Windows');
    // column 1
    body += box(24, 150, 150, 48, 'Raven source', 'pinned @ 32722ad');
    body += arrow(174, 174, 200, 174);
    body += box(200, 132, 160, 84, 'raven-lib', 'MinGW-w64 · MinSizeRel', 'boxhi');
    body += pill(212, 186, 'Docker', 'pill1');
    // artifacts
    body += arrow(360, 160, 392, 112);
    body += arrow(360, 190, 392, 254);
    body += box(392, 88, 138, 48, 'libraven.a', 'static · 3.6 MiB');
    body += box(392, 230, 138, 48, 'raven.dll', 'shared · 1.44 MiB');
    // variants
    const vx = 552, vw = 192;
    const vs = [
        [76, 'C baseline', 'gcc · static', 'Docker', 'pill1', 112],
        [124, 'Rust', 'cargo · windows-gnu', 'Docker', 'pill1', 112],
        [172, 'Kotlin/Native', 'Gradle · mingwX64', 'Docker', 'pill1', 112],
        [232, 'Java · jlink', 'javac + jlink + launcher', 'Docker', 'pill1', 254],
        [280, 'Java · GraalVM', 'native-image + MSVC', 'Windows host', 'pill2', 254],
    ];
    vs.forEach(([y, t, s, tag, cls, from]) => {
        body += arrow(530, from, vx, y + 20);
        body += `<rect class="box" x="${vx}" y="${y}" width="${vw}" height="40" rx="8" stroke-width="1"/>`;
        body += `<text class="ink" x="${vx + 12}" y="${y + 17}" font-size="12.5" font-weight="600">${esc(t)}</text>`;
        body += `<text class="muted" x="${vx + 12}" y="${y + 32}" font-size="10.5">${esc(s)}</text>`;
        body += pill(vx + vw - tw(tag, 10.5) - 22, y + 6, tag, cls);
    });
    body += `<text class="muted" x="24" y="${342}" font-size="11">Every exe is then driven by smoke-test.ps1 over UI Automation: Start → sequence plays → a pad is pressed → status is checked → window closed.</text>`;
    writeFileSync(join(out, 'pipeline.svg'), svg(362, 'Build pipeline',
        'Raven is cross-compiled once in Docker into libraven.a and raven.dll; C, Rust and Kotlin/Native link the static library in Docker, Java jlink uses the DLL in Docker, GraalVM native-image runs on the Windows host with MSVC.', body));
}

console.log('written to', out);
