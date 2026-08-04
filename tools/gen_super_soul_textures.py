"""Generate 16x16 Minecraft-style Super Soul item textures."""
from PIL import Image
from pathlib import Path

OUT = Path("src/main/resources/assets/xenopixelsmod/textures/item")
OUT.mkdir(parents=True, exist_ok=True)


def blank():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def rgba(c, a=255):
    """Accept RGB or RGBA; return RGBA with optional alpha override."""
    if len(c) == 4:
        return (int(c[0]), int(c[1]), int(c[2]), int(c[3] if a is None else a))
    return (int(c[0]), int(c[1]), int(c[2]), int(a))


def put(im, x, y, color, a=255):
    c = rgba(color, a)
    if 0 <= x < 16 and 0 <= y < 16 and c[3] > 0:
        im.putpixel((x, y), c)


def lerp(a, b, t):
    return (
        int(a[0] + (b[0] - a[0]) * t),
        int(a[1] + (b[1] - a[1]) * t),
        int(a[2] + (b[2] - a[2]) * t),
        255,
    )


def make_soul(name, primary, secondary, accent, rim, glow, motif):
    """primary=core, secondary=shade, accent=highlight, rim=ring, glow=aura, motif=symbol."""
    im = blank()
    primary = rgba(primary)
    secondary = rgba(secondary)
    accent = rgba(accent)
    rim = rgba(rim)
    glow = rgba(glow)

    # Soft outer glow
    glow_pts = [
        (7, 1), (8, 1),
        (6, 2), (7, 2), (8, 2), (9, 2),
        (5, 3), (6, 3), (9, 3), (10, 3),
        (4, 4), (11, 4),
        (4, 5), (11, 5),
        (4, 6), (11, 6),
        (4, 7), (11, 7),
        (4, 8), (11, 8),
        (4, 9), (11, 9),
        (5, 10), (10, 10),
        (6, 11), (9, 11),
        (7, 12), (8, 12),
    ]
    for x, y in glow_pts:
        put(im, x, y, glow, 70)
        put(im, x, y + 1, glow, 35)

    # Outer ring (halo)
    ring = [
        (6, 3), (7, 3), (8, 3), (9, 3),
        (5, 4), (10, 4),
        (4, 5), (11, 5),
        (4, 6), (11, 6),
        (4, 7), (11, 7),
        (4, 8), (11, 8),
        (5, 9), (10, 9),
        (6, 10), (7, 10), (8, 10), (9, 10),
    ]
    for x, y in ring:
        put(im, x, y, rim)
    for x, y in [(6, 3), (9, 3), (4, 6), (11, 6)]:
        put(im, x, y, accent)

    # Core body (filled oval gem)
    core_rows = {
        4: range(6, 10),
        5: range(5, 11),
        6: range(5, 11),
        7: range(5, 11),
        8: range(5, 11),
        9: range(6, 10),
    }
    for y, xs in core_rows.items():
        for x in xs:
            t = (y - 4) / 5.0
            col = lerp(primary, secondary, t * 0.65)
            if x in (xs.start, xs.stop - 1) or y in (4, 9):
                col = lerp(col, secondary, 0.45)
            put(im, x, y, col)

    # Upper-left specular
    for x, y in [(6, 5), (7, 5), (6, 6)]:
        put(im, x, y, accent)
    put(im, 7, 6, primary)

    # Motif glyph (center, dark-on-bright for readability at 16px)
    ink = secondary
    bright = accent
    if motif == "sword":  # warrior — vertical blade
        put(im, 7, 5, bright)
        put(im, 8, 5, bright)
        put(im, 7, 6, bright)
        put(im, 8, 6, primary)
        put(im, 7, 7, bright)
        put(im, 8, 7, primary)
        put(im, 6, 8, ink)
        put(im, 7, 8, bright)
        put(im, 8, 8, bright)
        put(im, 9, 8, ink)
        put(im, 7, 9, ink)
        put(im, 8, 9, ink)
    elif motif == "shield":  # iron
        put(im, 6, 5, bright)
        put(im, 7, 5, bright)
        put(im, 8, 5, bright)
        put(im, 9, 5, bright)
        put(im, 6, 6, bright)
        put(im, 9, 6, bright)
        put(im, 6, 7, bright)
        put(im, 9, 7, bright)
        put(im, 7, 6, primary)
        put(im, 8, 6, primary)
        put(im, 7, 7, primary)
        put(im, 8, 7, primary)
        put(im, 7, 8, bright)
        put(im, 8, 8, bright)
        put(im, 7, 9, ink)
        put(im, 8, 9, ink)
    elif motif == "bolt":  # spark — lightning Z
        put(im, 8, 5, bright)
        put(im, 9, 5, bright)
        put(im, 7, 6, bright)
        put(im, 8, 6, bright)
        put(im, 7, 7, bright)
        put(im, 6, 8, bright)
        put(im, 7, 8, bright)
        put(im, 6, 9, bright)
        put(im, 9, 7, accent)
        put(im, 5, 7, accent, 200)
    elif motif == "star":  # finisher — X/star burst
        put(im, 7, 5, bright)
        put(im, 8, 5, bright)
        put(im, 6, 6, bright)
        put(im, 9, 6, bright)
        put(im, 7, 6, primary)
        put(im, 8, 6, primary)
        put(im, 5, 7, bright)
        put(im, 6, 7, bright)
        put(im, 7, 7, accent)
        put(im, 8, 7, accent)
        put(im, 9, 7, bright)
        put(im, 10, 7, bright)
        put(im, 6, 8, bright)
        put(im, 9, 8, bright)
        put(im, 7, 8, primary)
        put(im, 8, 8, primary)
        put(im, 7, 9, bright)
        put(im, 8, 9, bright)
    elif motif == "yin":  # balanced — split circle
        put(im, 7, 5, bright)
        put(im, 8, 5, ink)
        put(im, 6, 6, bright)
        put(im, 7, 6, bright)
        put(im, 8, 6, ink)
        put(im, 9, 6, ink)
        put(im, 6, 7, bright)
        put(im, 7, 7, ink)
        put(im, 8, 7, bright)
        put(im, 9, 7, ink)
        put(im, 6, 8, ink)
        put(im, 7, 8, ink)
        put(im, 8, 8, bright)
        put(im, 9, 8, bright)
        put(im, 7, 9, ink)
        put(im, 8, 9, bright)

    # Bottom facet already partly drawn; reinforce
    for x in range(6, 10):
        if im.getpixel((x, 9))[3] == 0:
            put(im, x, 9, secondary)

    # Soul tail
    put(im, 7, 11, primary, 200)
    put(im, 8, 11, secondary, 180)
    put(im, 7, 12, secondary, 120)
    put(im, 8, 12, rim, 90)

    # Top spike
    put(im, 7, 2, accent)
    put(im, 8, 2, primary)
    put(im, 7, 1, accent, 180)

    path = OUT / f"super_soul_{name}.png"
    im.save(path)
    print("wrote", path)
    return im


SOULS = {
    "warrior": dict(
        primary=(220, 55, 48),
        secondary=(120, 18, 22),
        accent=(255, 200, 120),
        rim=(180, 40, 30),
        glow=(255, 80, 40),
        motif="sword",
    ),
    "iron": dict(
        primary=(150, 170, 195),
        secondary=(70, 85, 110),
        accent=(230, 240, 255),
        rim=(90, 105, 130),
        glow=(140, 160, 190),
        motif="shield",
    ),
    "spark": dict(
        primary=(255, 210, 40),
        secondary=(180, 100, 10),
        accent=(255, 255, 200),
        rim=(220, 150, 20),
        glow=(255, 220, 60),
        motif="bolt",
    ),
    "finisher": dict(
        primary=(200, 70, 255),
        secondary=(90, 20, 140),
        accent=(255, 180, 255),
        rim=(160, 50, 220),
        glow=(210, 90, 255),
        motif="star",
    ),
    "balanced": dict(
        primary=(50, 200, 140),
        secondary=(20, 100, 80),
        accent=(180, 255, 210),
        rim=(30, 160, 110),
        glow=(60, 220, 160),
        motif="yin",
    ),
}


def main():
    for name, pal in SOULS.items():
        make_soul(name, **pal)
    print("done — 5 super soul textures")


if __name__ == "__main__":
    main()
