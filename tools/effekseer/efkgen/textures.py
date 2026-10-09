"""Procedural HD textures for Effekseer effects (white or grey; the effect nodes colour them).

Every generator is deterministic (fixed seeds), so regenerating gives byte-identical files.
Sizes are powers of two, 1024 px at most; names must be lower case (Minecraft resource paths).
"""
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter


def fbm(w, h, octaves, seed, sx=1.0, sy=1.0, tile=True):
    """Tileable fractal value noise in 0..1."""
    rng = np.random.default_rng(seed)
    out = np.zeros((h, w), np.float64)
    amp, total = 1.0, 0.0
    for o in range(octaves):
        cw, ch = max(2, int(4 * sx * 2 ** o)), max(2, int(4 * sy * 2 ** o))
        grid = rng.random((ch, cw))
        ys = np.linspace(0, ch, h, endpoint=False)
        xs = np.linspace(0, cw, w, endpoint=False)
        y0 = np.floor(ys).astype(int); x0 = np.floor(xs).astype(int)
        ty = ys - y0; tx = xs - x0
        ty = ty * ty * (3 - 2 * ty); tx = tx * tx * (3 - 2 * tx)
        y1 = (y0 + 1) % ch if tile else np.minimum(y0 + 1, ch - 1)
        x1 = (x0 + 1) % cw if tile else np.minimum(x0 + 1, cw - 1)
        a = grid[np.ix_(y0, x0)]; b = grid[np.ix_(y0, x1)]
        c = grid[np.ix_(y1, x0)]; d = grid[np.ix_(y1, x1)]
        top = a + (b - a) * tx[None, :]; bot = c + (d - c) * tx[None, :]
        out += amp * (top + (bot - top) * ty[:, None])
        total += amp; amp *= 0.5
    return out / total


def save_rgba(path, rgb, alpha):
    rgb = np.clip(rgb, 0, 1); alpha = np.clip(alpha, 0, 1)
    arr = np.dstack([rgb, alpha[..., None]]) if rgb.ndim == 3 else np.dstack([rgb, rgb, rgb, alpha])
    Image.fromarray((arr * 255 + 0.5).astype(np.uint8), 'RGBA').save(path)


def radial(n):
    y, x = np.mgrid[0:n, 0:n]
    c = (n - 1) / 2.0
    return np.hypot(x - c, y - c) / c, np.arctan2(y - c, x - c)


def blur(a, radius):
    img = Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8))
    return np.asarray(img.filter(ImageFilter.GaussianBlur(radius)), np.float64) / 255.0


# ------------------------------------------------------------------ shared shapes

def streaks(path: Path, seed, w=256, h=512, streak_x=6.0, streak_y=0.5, gamma=1.6, bias=0.35):
    """Vertical flame tongues, tileable (they scroll along a veil or a jet)."""
    n = fbm(w, h, 5, seed, sx=2.0, sy=1.0)
    s = fbm(w, h, 4, seed + 1, sx=streak_x, sy=streak_y)
    v = np.clip((0.55 * n + 0.65 * s) - bias, 0, 1) / max(1e-6, 1.2 - bias - 0.2)
    v = np.clip(v, 0, 1) ** gamma
    save_rgba(path, v, v)


def mask(path: Path, seed, lo=0.25, span=0.55):
    """Soft, broken coverage for an alpha texture or alpha cutoff."""
    m = fbm(256, 256, 6, seed, sx=1.5, sy=1.5)
    m = np.clip((m - lo) / span, 0, 1)
    save_rgba(path, m, m)


def distortion(path: Path, seed, strength=6.0):
    """Normal map from a height field (Effekseer reads RG around 0.5)."""
    hgt = fbm(256, 256, 4, seed, sx=2.0, sy=2.0)
    gy, gx = np.gradient(hgt)
    rgb = np.dstack([0.5 + strength * gx, 0.5 + strength * gy, np.ones_like(hgt)])
    save_rgba(path, rgb, np.ones_like(hgt))


def mote(path: Path, size=64, core_width=0.16):
    """Hot core and a soft halo: sparks, embers, motes."""
    r, _ = radial(size)
    core = np.exp(-(r / core_width) ** 2)
    halo = np.clip(1 - r, 0, 1) ** 2.4
    save_rgba(path, np.clip(core + halo, 0, 1), np.clip(core + 0.7 * halo, 0, 1))


def glow(path: Path, size=128, power=2.2):
    r, _ = radial(size)
    g = np.clip(1 - r, 0, 1) ** power
    save_rgba(path, g, g)


def ring(path: Path, seed, radius=0.78, width=0.06, broken=0.35, size=256):
    r, _ = radial(size)
    band = np.exp(-((r - radius) / width) ** 2)
    noise = fbm(size, size, 4, seed, sx=3, sy=3)
    v = band * np.clip(broken + noise, 0, 1)
    save_rgba(path, v, v)


def shard(path: Path, seed, lum_base=0.3, rim_gain=1.4, tint=None, size=128):
    """An angular chip with a bright torn rim (Hakai flakes, rock chips)."""
    rng = np.random.default_rng(seed)
    k = 7
    angles = np.sort(rng.uniform(0, 2 * np.pi, k))
    radii = rng.uniform(0.45, 0.92, k) * size * 0.46
    pts = [(size / 2 + r * np.cos(t), size / 2 + r * 0.8 * np.sin(t)) for r, t in zip(radii, angles)]
    m = Image.new('L', (size, size), 0)
    ImageDraw.Draw(m).polygon(pts, fill=255)
    m = m.filter(ImageFilter.GaussianBlur(1.2))
    inside = np.asarray(m, np.float64) / 255.0
    rim = blur(np.clip(inside * (1 - inside) * 4.0, 0, 1), 1.5)
    grain = fbm(size, size, 4, seed + 1, sx=2, sy=2)
    lum = np.clip(lum_base + 0.45 * grain + rim_gain * rim, 0, 1)
    alpha = np.clip(inside * (0.6 + 0.4 * grain) + rim, 0, 1)
    if tint is None:
        save_rgba(path, lum, alpha)
    else:
        rgb = np.dstack([lum * tint[0], lum * tint[1], lum * tint[2]])
        save_rgba(path, rgb, alpha)


# ------------------------------------------------------------------ plume and smoke

def shock_diamond(path: Path, size=128):
    """A Mach disc: a bright lens-shaped core with a soft glow, seen side-on."""
    y, x = np.mgrid[0:size, 0:size]
    c = (size - 1) / 2.0
    dx = (x - c) / c; dy = (y - c) / c
    lens = np.clip(1 - (np.abs(dx) / 0.9) ** 1.5 - (np.abs(dy) / 0.35) ** 1.2, 0, 1)
    core = np.exp(-((dx / 0.35) ** 2 + (dy / 0.12) ** 2))
    v = np.clip(0.6 * lens + core, 0, 1)
    save_rgba(path, v, v)


def smoke_puff(path: Path, seed, size=256):
    """A billowing smoke puff: lumpy edge, lit from above, soft falloff."""
    r, th = radial(size)
    lump = fbm(size, size, 5, seed, sx=1.2, sy=1.2)
    detail = fbm(size, size, 6, seed + 1, sx=3.0, sy=3.0)
    edge = 0.72 + 0.22 * (lump - 0.5) * 2
    density = np.clip((edge - r) / 0.35, 0, 1) ** 1.3
    density *= np.clip(0.55 + 0.6 * detail, 0, 1)
    y = np.linspace(-1, 1, size)[:, None] * np.ones((1, size))
    light = np.clip(0.75 - 0.3 * y + 0.35 * (detail - 0.5), 0.3, 1.0)
    save_rgba(path, light, np.clip(density, 0, 1))


# ------------------------------------------------------------------ lightning

def lightning(path: Path, seed, w=128, h=256, branches=3):
    """A jagged bolt top to bottom with a few forks, glow around a thin hot core."""
    rng = np.random.default_rng(seed)

    def bolt(p0, p1, depth, jitter):
        if depth == 0:
            return [p0, p1]
        mid = ((p0[0] + p1[0]) / 2 + rng.normal(0, jitter), (p0[1] + p1[1]) / 2)
        return bolt(p0, mid, depth - 1, jitter / 2)[:-1] + bolt(mid, p1, depth - 1, jitter / 2)

    img = Image.new('L', (w, h), 0)
    d = ImageDraw.Draw(img)
    main = bolt((w / 2, 0), (w / 2 + rng.normal(0, w * 0.1), h), 6, w * 0.22)
    d.line(main, fill=255, width=3)
    for _ in range(branches):
        i = int(rng.integers(len(main) // 5, len(main) * 3 // 4))
        start = main[i]
        end = (start[0] + rng.normal(0, w * 0.35), min(h, start[1] + rng.uniform(h * 0.15, h * 0.4)))
        d.line(bolt(start, end, 4, w * 0.12), fill=200, width=2)
    core = np.asarray(img, np.float64) / 255.0
    halo = blur(core, 5) * 2.2 + blur(core, 12) * 1.6
    v = np.clip(core + halo, 0, 1)
    save_rgba(path, v, v)


def dust_ring(path: Path, seed, size=256):
    """A broad, dusty ground ring that fades inward and outward."""
    r, _ = radial(size)
    band = np.exp(-((r - 0.7) / 0.18) ** 2)
    noise = fbm(size, size, 5, seed, sx=4, sy=4)
    v = np.clip(band * (0.3 + 0.9 * noise), 0, 1)
    save_rgba(path, np.clip(0.6 + 0.4 * noise, 0, 1), v)


# ------------------------------------------------------------------ auras

def flame_tongue(path: Path, seed, w=128, h=256):
    """A single licking flame tongue: wide soft base, ragged tapering tip, streaky inside."""
    y, x = np.mgrid[0:h, 0:w]
    u = (x - (w - 1) / 2.0) / ((w - 1) / 2.0)        # -1..1 across
    v = 1.0 - y / (h - 1.0)                          # 0 at the base, 1 at the tip
    wobble = (fbm(w, h, 4, seed, sx=1.0, sy=2.0, tile=False) - 0.5) * 0.35 * v
    width = 0.95 * (1.0 - v) ** 1.15 + 0.01            # tapers to a point
    body = np.clip(1.0 - np.abs(u + wobble) / width, 0, 1) ** 0.8
    body *= np.clip(v * 6.0, 0, 1)                   # soft base
    streak = fbm(w, h, 5, seed + 1, sx=3.0, sy=0.6, tile=False)
    edge = fbm(w, h, 5, seed + 2, sx=2.0, sy=2.0, tile=False)
    body *= np.clip(0.45 + 0.8 * streak, 0, 1)
    body *= np.clip((edge + 0.8 - v * 0.35) * 1.6, 0, 1)   # ragged sides, the point kept
    save_rgba(path, np.clip(body * 1.2, 0, 1), np.clip(body, 0, 1))


def spiked_flame(path: Path, seed, size=512):
    """The whole aura as one cel-shaded silhouette, after DragonMineZ's own aura sheet
    (kakarot_aura.png): an egg of flame, widest below the middle and drawn to a point on top,
    with a ragged crown of outward spikes, a white-hot rim and flat darker bands toward a nearly
    clear centre, so the fighter shows through. Each seed is one frame of the flicker."""
    rng = np.random.default_rng(seed)
    y, x = np.mgrid[0:size, 0:size]
    u = (x - (size - 1) / 2.0) / ((size - 1) / 2.0)          # -1..1 across
    v = 1.0 - y / (size - 1.0)                               # 0 at the feet, 1 at the tip
    cy = 0.40
    dv = v - cy
    # Egg: a short rounded belly below the centre, a long taper above it.
    ry = np.where(dv >= 0, 0.56, 0.36)
    r = np.sqrt((u / 0.80) ** 2 + (dv / ry) ** 2)
    theta = np.arctan2(dv / ry, u / 0.80)                    # 0 right, pi/2 up
    up = np.clip(np.sin(theta), -1, 1)                       # 1 at the top, -1 at the bottom
    # Spikes: three sawtooth combs of different pitch and phase round the outline, longest on top.
    spikes = np.zeros_like(r)
    for teeth, depth in ((23, 0.20), (41, 0.12), (67, 0.07)):
        phase = rng.random() * 2 * np.pi
        saw = np.abs(((theta * teeth / (2 * np.pi) + phase) % 1.0) * 2.0 - 1.0)   # 1 at a tip
        jitter = 0.6 + 0.4 * np.sin(theta * (teeth // 3) + rng.random() * 6.0)
        spikes = np.maximum(spikes, depth * saw ** 2.2 * jitter)
    reach = 0.74 + spikes * (0.75 + 0.55 * np.clip(up, 0, 1)) * np.where(up < -0.5, 0.45, 1.0)
    inside = reach - r                                        # > 0 inside the silhouette
    px = 2.5 / size

    def step(edge):
        return np.clip((inside - edge) / px + 0.5, 0, 1)

    shape = step(0.0)
    band1 = step(0.05)       # inside the rim
    band2 = step(0.15)
    band3 = step(0.32)
    lum = shape * 1.0 - band1 * 0.22 - band2 * 0.18 - band3 * 0.15
    alpha = shape * 1.0 - band1 * 0.25 - band2 * 0.25 - band3 * 0.22
    # A few bright inner flecks low in the body, as in DMZ's frames.
    fleck = fbm(size, size, 4, seed + 7, sx=9.0, sy=1.2, tile=False)
    flecks = np.clip((fleck - 0.68) * 6.0, 0, 1) * band2 * np.clip(1.0 - np.abs(dv + 0.12) / 0.2, 0, 1)
    lum = np.clip(lum + flecks * 0.5, 0, 1)
    alpha = np.clip(alpha + flecks * 0.4, 0, 1)
    save_rgba(path, lum, alpha)


def star(path: Path, size=64):
    """A four-point twinkle (the sparkles inside an anime aura)."""
    y, x = np.mgrid[0:size, 0:size]
    c = (size - 1) / 2.0
    dx = np.abs(x - c) / c
    dy = np.abs(y - c) / c
    rays = np.exp(-(dx / 0.05) ** 2) * np.clip(1 - dy, 0, 1) ** 3 + np.exp(-(dy / 0.05) ** 2) * np.clip(1 - dx, 0, 1) ** 3
    core = np.exp(-((dx ** 2 + dy ** 2) / 0.01))
    v = np.clip(rays + core, 0, 1)
    save_rgba(path, v, v)


def fire_billow(path: Path, seed, size=256):
    """A billowing puff of fire (the Jiren-style aura is built from hundreds of these): a lumpy,
    ragged-edged cloud shot through with upward flame fibres, brightest low in the middle."""
    y, x = np.mgrid[0:size, 0:size]
    c = (size - 1) / 2.0
    dx = (x - c) / c
    dy = (y - c) / c
    lump = fbm(size, size, 5, seed, sx=1.4, sy=1.4)
    rag = fbm(size, size, 6, seed + 1, sx=4.0, sy=4.0)
    r = np.sqrt(dx * dx + (dy * 1.05) ** 2) + (lump - 0.5) * 0.55 + (rag - 0.5) * 0.18
    shape = np.clip((0.78 - r) / 0.22, 0, 1)
    fibres = fbm(size, size, 5, seed + 2, sx=7.0, sy=0.9)
    billows = fbm(size, size, 5, seed + 3, sx=2.2, sy=2.2)
    inner = np.clip(1.0 - np.sqrt(dx * dx + (dy - 0.25) ** 2) / 0.9, 0, 1)
    lum = np.clip(0.08 + 0.75 * billows ** 1.6 + 0.55 * fibres ** 2.2 + 0.35 * inner, 0, 1) * shape
    alpha = np.clip(shape * (0.3 + 0.7 * billows ** 1.2) + 0.2 * fibres ** 2 * shape, 0, 1)
    save_rgba(path, np.clip(lum * 1.15, 0, 1), alpha)


# ------------------------------------------------------------------ ki attacks (HD)

def energy_flow(path: Path, seed, w=512, h=1024):
    """Tileable plasma for a beam's body: bright filaments running along it over a soft turbulent
    fill. It scrolls along the beam, so both edges wrap."""
    fill = fbm(w, h, 5, seed, sx=2.0, sy=1.0)
    a = fbm(w, h, 5, seed + 1, sx=6.0, sy=0.5)
    b = fbm(w, h, 5, seed + 2, sx=11.0, sy=0.8)
    ridge = (1.0 - np.abs(2.0 * a - 1.0)) ** 5 + 0.6 * (1.0 - np.abs(2.0 * b - 1.0)) ** 7
    v = np.clip(0.28 + 0.55 * fill ** 1.5 + 0.9 * ridge, 0, 1)
    save_rgba(path, v, np.clip(0.35 + 0.65 * v, 0, 1))


def corona(path: Path, seed, size=512):
    """A ragged halo of fine rays round a clear centre: the flare around an orb of ki."""
    rng = np.random.default_rng(seed)
    r, theta = radial(size)
    rays = np.zeros_like(r)
    for teeth, depth in ((37, 0.5), (71, 0.3), (113, 0.2)):
        phase = rng.random() * 2 * np.pi
        rays += depth * np.abs(np.sin(theta * teeth / 2.0 + phase)) ** 6
    reach = 0.42 + 0.5 * rays
    body = np.clip((reach - r) / 0.3, 0, 1) ** 1.4
    hollow = np.clip((r - 0.12) / 0.3, 0, 1)
    soft = np.clip(1 - r, 0, 1) ** 2.0
    v = np.clip(body * hollow * 0.9 + soft * 0.35, 0, 1)
    save_rgba(path, v, v)


def shock_ring_hd(path: Path, seed, size=512):
    """A sharp expanding shock ring with a soft wake inside it and fine radial streaks."""
    r, theta = radial(size)
    edge = np.exp(-((r - 0.86) / 0.025) ** 2)
    wake = np.clip((r - 0.45) / 0.41, 0, 1) ** 3 * (r < 0.86)
    streak = fbm(size, size, 5, seed, sx=10.0, sy=10.0)
    v = np.clip(edge + 0.45 * wake * (0.5 + streak), 0, 1)
    save_rgba(path, v, v)


def saw_disc(path: Path, size=512, teeth=28):
    """Kienzan seen face on: a thin disc with a white-hot saw-toothed rim and a faint swirl."""
    r, theta = radial(size)
    saw = ((theta * teeth / (2 * np.pi)) % 1.0)
    rim_r = 0.78 + 0.1 * saw
    px = 3.0 / size
    inside = np.clip((rim_r - r) / px + 0.5, 0, 1)
    rim = inside * np.clip((r - (rim_r - 0.09)) / 0.09, 0, 1) ** 2
    swirl = 0.5 + 0.5 * np.sin(theta * 3.0 + r * 22.0)
    body = inside * (0.16 + 0.22 * swirl * np.clip(r / 0.8, 0, 1))
    halo = np.clip(1 - np.abs(r - 0.84) / 0.14, 0, 1) ** 2 * 0.35
    v = np.clip(rim + body + halo, 0, 1)
    save_rgba(path, v, v)


def speed_lines(path: Path, seed, size=512):
    """Thin radial lines round an empty centre: energy rushing in (or out)."""
    rng = np.random.default_rng(seed)
    r, theta = radial(size)
    lines = np.zeros_like(r)
    for _ in range(64):
        angle = rng.random() * 2 * np.pi
        width = rng.uniform(0.006, 0.02)
        start = rng.uniform(0.25, 0.6)
        length = rng.uniform(0.2, 0.4)
        d = np.abs(np.angle(np.exp(1j * (theta - angle))))
        along = np.clip((r - start) / length, 0, 1)
        lines = np.maximum(lines, np.exp(-(d / width) ** 2) * np.sin(along * np.pi) ** 0.7 * (r < start + length))
    save_rgba(path, lines, lines)
