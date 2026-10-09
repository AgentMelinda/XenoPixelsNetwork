#!/usr/bin/env python3
"""
DragonMineZ-style GUI panel atlas generator — v3, re-derived at 6x zoom
against the real pixel grid of menubig.png (previous versions guessed
wrong on two structural details; this one fixes both).

Pixel-level findings from extracted/assets/dragonminez/textures/gui/menu/menubig.png:

  1. Border (measured on a horizontal scan through the fill, y=100):
     x=1-2 white(255,255,255) outer line
     x=3-4 blend(144,154,195)
     x=5   solid lavender(210,215,241)
     x=6   blend(144,154,195)
     -> ~6-7px total, antialiased (soft blend pixels, not stair-step).

  2. The fill is a REAL crosshatch grid, both axes, period 7px, same
     highlight value on both: base green (0,49,0) with 1px lines at
     (0,60,0) every 7th row AND every 7th column (confirmed by printing
     a 20x20 pixel block — horizontal lines at y=63,70,77... line up
     exactly like the vertical ones at x=21,28,35...). My first "HD"
     attempt only had vertical stripes; that was wrong.

  3. A distinct darker "header band" fills the top ~16% of the panel
     (base 0,33,0 / grid-line 0,44,0 — same grid pattern, just darker),
     ending in a full-width 1px divider line before the normal fill
     resumes.

  4. Immediately inside the border, on BOTH left and right edges, there
     is a solid BRIGHT vertical bar ~4px wide (measured (0,99,0) on the
     left, (0,75,0) on the right) — much brighter than the grid highlight
     — separated from the border by a single darker 1px seam line
     (0,33,0). This reads as an inner bevel/glow strip and was missing
     entirely from earlier attempts.

  5. The "shine" in the bottom-left corner is NOT a thin diagonal stroke.
     It's a hard-edged triangular wedge of brightness (values jump from
     ~99 to ~109 and the bright region visibly widens row by row going
     down) — i.e. a solid triangular highlight wedge, not a blurred line.

  6. The border ring itself has to be built as a true geometric
     concentric inset (same shape redrawn at a smaller size + smaller
     radius), NOT by image-eroding the full-size shape. Eroding a
     staircase corner shrinks the corner much faster than the straight
     edges, so the outer white "line" ballooned into a blob at each
     corner instead of staying a uniform ~2px stroke all the way around.
     build_inset_mask()'s docstring has the full explanation.

Usage:
    python3 dmz_atlas_generator.py

Output (next to this script):
    panels/<name>.png           - each panel as its OWN standalone PNG,
                                    exactly spec.w x spec.h, transparent
                                    background, ready to use directly —
                                    no shared sheet or UV math needed.
    panels_contact_sheet.png    - one labeled overview image of the whole
                                    set, for eyeballing only (not a texture
                                    to ship — use the individual files).

A build_atlas()/pack_atlas() pair is still included below if you ever
want everything packed onto one shared sheet instead (the way the game's
own menubig.png/menusmall.png/questmenu.png each hold several panels) —
just not what __main__ uses by default anymore.
"""

import json
from dataclasses import dataclass, replace
from typing import List, Tuple

from PIL import Image, ImageDraw, ImageFont, ImageFilter

# ---------------------------------------------------------------------------
# Palette + measurements sampled directly from the real menubig.png
# ---------------------------------------------------------------------------
GRID_PERIOD = 7             # a highlight row/column every 7px, both axes
HEADER_FRAC = 0.16          # dark header band = top 16% of panel height
EDGE_BAR_WIDTH = 4           # bright vertical bar width, just inside the border
BORDER_THICKNESS = 6

PALETTES = {
    # base = flat fill, grid = crosshatch highlight, header = dark top-band base,
    # header_grid = crosshatch highlight within the header band,
    # edge = bright inner bevel bar, seam = dark 1px line between border and edge bar,
    # shine = corner highlight wedge
    "green": dict(
        outer=(255, 255, 255, 255), blend=(144, 154, 195, 255), inner=(210, 215, 241, 255),
        base=(0, 49, 0, 255), grid=(0, 60, 0, 255),
        header=(0, 33, 0, 255), header_grid=(0, 44, 0, 255),
        edge=(0, 99, 0, 255), seam=(0, 33, 0, 255), shine=(0, 109, 0, 255),
    ),
    "blue": dict(
        outer=(255, 255, 255, 255), blend=(154, 164, 200, 255), inner=(210, 220, 245, 255),
        base=(0, 22, 66, 255), grid=(0, 33, 82, 255),
        header=(0, 12, 45, 255), header_grid=(0, 20, 58, 255),
        edge=(20, 70, 130, 255), seam=(0, 12, 45, 255), shine=(30, 90, 150, 255),
    ),
    "red": dict(
        outer=(255, 255, 255, 255), blend=(200, 154, 154, 255), inner=(241, 210, 210, 255),
        base=(66, 12, 12, 255), grid=(82, 20, 20, 255),
        header=(45, 6, 6, 255), header_grid=(58, 12, 12, 255),
        edge=(140, 30, 30, 255), seam=(45, 6, 6, 255), shine=(160, 40, 40, 255),
    ),
    "gold": dict(
        outer=(255, 255, 255, 255), blend=(200, 190, 154, 255), inner=(241, 232, 200, 255),
        base=(70, 54, 8, 255), grid=(88, 68, 14, 255),
        header=(45, 34, 4, 255), header_grid=(58, 44, 10, 255),
        edge=(150, 120, 20, 255), seam=(45, 34, 4, 255), shine=(180, 145, 30, 255),
    ),
}

SS = 4  # supersampling factor for smooth antialiased edges (real texture has AA, not stair-steps)


@dataclass
class PanelSpec:
    """One panel to draw. `shape` picks the silhouette; everything else is
    the size/style knob that varies between panels while the family look
    stays the same."""
    name: str
    w: int
    h: int
    shape: str = "rounded"      # "rounded" | "pill" | "hex" | "banner" | "tab" | "speech_bubble"
    radius: int = 5
    palette: str = "green"
    grid: bool = True
    edge_bars: bool = True
    header_band: bool = True
    shine_corner: str = "bottom_left"   # or None to disable


# ---------------------------------------------------------------------------
# Shape masks, drawn at SS x resolution then Lanczos-downsampled for the
# soft antialiasing the real texture actually has.
# ---------------------------------------------------------------------------
def _mask_rounded(w, h, radius):
    """Hard-edged pixel-art staircase corner, built to match the EXACT cut
    measured on the real menubig.png corner (radius=3 there: row0 cuts 3px,
    row1 cuts 2px, row2 cuts 1px, row3+ is full width — a blocky diagonal
    staircase, NOT a smoothed/antialiased quarter-circle). Earlier attempts
    used PIL's rounded_rectangle at 4x supersampling + Lanczos downsample,
    which produces a soft round corner — that was the single biggest visual
    mismatch, since DMZ's panels are hard pixel art with zero edge AA."""
    m = Image.new("L", (w, h), 255)
    px = m.load()
    r = max(0, int(radius))
    for i in range(r):
        cut = r - i
        for x in range(min(cut, w)):
            px[x, i] = 0                          # top-left
            px[w - 1 - x, i] = 0                  # top-right
            px[x, h - 1 - i] = 0                  # bottom-left
            px[w - 1 - x, h - 1 - i] = 0          # bottom-right
    return m


def _mask_pill(w, h):
    # a true pill end is a smooth semicircle even in the game's pixel-art
    # (no straight edge to stair-step against), so this one stays a plain
    # ellipse-rounded rect rather than the manual staircase.
    m = Image.new("L", (w, h), 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, w - 1, h - 1], radius=h // 2, fill=255)
    return m


def _mask_hex(w, h):
    m = Image.new("L", (w, h), 0)
    d = ImageDraw.Draw(m)
    cut = w * 0.22
    d.polygon([(cut, 0), (w - cut, 0), (w - 1, h / 2),
               (w - cut, h - 1), (cut, h - 1), (0, h / 2)], fill=255)
    return m


def _mask_banner(w, h, radius):
    # same hard staircase corners on top; bottom corners stay hard 90 deg.
    m = _mask_rounded(w, h, radius)
    px = m.load()
    r = max(0, int(radius))
    for i in range(r):
        cut = r - i
        for x in range(min(cut, w)):
            px[x, h - 1 - i] = 255                # undo the bottom stair-cut
            px[w - 1 - x, h - 1 - i] = 255
    d = ImageDraw.Draw(m)
    notch = max(6, int(h * 0.18))
    d.polygon([(0, h - 1), (notch, h - 1), (0, h - 1 - notch)], fill=0)
    d.polygon([(w - 1, h - 1), (w - 1 - notch, h - 1), (w - 1, h - 1 - notch)], fill=0)
    return m


def _mask_tab(w, h, radius):
    # hard staircase on the top two corners only; square bottom.
    m = Image.new("L", (w, h), 255)
    px = m.load()
    r = max(0, int(radius))
    for i in range(r):
        cut = r - i
        for x in range(min(cut, w)):
            px[x, i] = 0
            px[w - 1 - x, i] = 0
    return m


def build_shape_mask(w: int, h: int, shape: str, radius: int) -> Image.Image:
    """Build a shape mask at an EXACT (w, h, radius) — used both for the
    full-size panel and, at inset sizes, for each border ring (see
    build_inset_mask). No supersampling/antialiasing on any of these:
    that's what actually matches the source texture."""
    if shape == "rounded":
        return _mask_rounded(w, h, radius)
    if shape == "pill":
        return _mask_pill(w, h)
    if shape == "hex":
        return _mask_hex(w, h)
    if shape == "banner":
        return _mask_banner(w, h, radius)
    if shape == "tab":
        return _mask_tab(w, h, radius)
    raise ValueError(f"unknown shape {shape!r}")


def build_mask_supersampled(spec: PanelSpec) -> Image.Image:
    # name kept for interface stability from earlier versions.
    return build_shape_mask(spec.w, spec.h, spec.shape, spec.radius)


def build_inset_mask(spec: PanelSpec, depth: int) -> Image.Image:
    """The SAME shape, shrunk by `depth` px on every side, with its corner
    radius reduced by the same `depth` — i.e. a true geometric concentric
    inset, not an eroded one.

    This replaces an earlier MinFilter-erosion approach that produced
    visibly wrong results: eroding an already-staircase-shaped mask
    doesn't preserve uniform stroke width around a concave (stair-step)
    corner — the corner erodes much faster than the straight edges, so
    the outer "white" ring ballooned into a thick blob at the corner
    while staying a correct thin 2px line along the straight edges. A
    side-by-side crop against the real texture made the mismatch obvious:
    the real corner keeps a perfectly uniform-width white line all the
    way around, corner included. Recomputing the shape fresh at each
    inset size/radius guarantees that same uniform width everywhere,
    since it's exact geometry rather than an image-filter approximation.
    """
    w, h = spec.w, spec.h
    if depth <= 0:
        return build_shape_mask(w, h, spec.shape, spec.radius)
    iw, ih = w - 2 * depth, h - 2 * depth
    if iw <= 0 or ih <= 0:
        return Image.new("L", (w, h), 0)
    ir = max(0, spec.radius - depth)
    inner = build_shape_mask(iw, ih, spec.shape, ir)
    m = Image.new("L", (w, h), 0)
    m.paste(inner, (depth, depth))
    return m


def paint_border(spec: PanelSpec, pal: dict, thickness: int = BORDER_THICKNESS):
    """Ring widths measured directly off the left edge of the real panel
    (y=100 scan, away from any corner): white 2px, blend 2px, lavender
    1px, blend 1px, then fill — 6px total. Each step paints a geometric
    inset copy of the shape over the previous one, so the ring width is
    exactly the gap between consecutive depths, uniformly, corners
    included (see build_inset_mask's docstring for why that matters)."""
    w, h = spec.w, spec.h
    t = max(6, thickness)
    canvas = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    steps = [
        (0, pal["outer"]),   # ring [0,2) -> 2px white
        (2, pal["blend"]),   # ring [2,4) -> 2px blend
        (4, pal["inner"]),   # ring [4,5) -> 1px lavender
        (5, pal["blend"]),   # ring [5,6) -> 1px blend
    ]
    for depth, color in steps:
        layer_mask = build_inset_mask(spec, depth)
        canvas.paste(Image.new("RGBA", (w, h), color), (0, 0), layer_mask)
    interior_mask = build_inset_mask(spec, t)
    return canvas, interior_mask


# ---------------------------------------------------------------------------
# Fill: crosshatch grid (both axes) + darker header band (same grid,
# darker tones) + bright inner-edge bevel bars + a triangular corner-shine
# wedge. This is the part v1/v2 got wrong; rebuilt from the pixel samples.
# ---------------------------------------------------------------------------
def paint_fill(w, h, pal: dict, spec: PanelSpec) -> Image.Image:
    fill = Image.new("RGBA", (w, h), pal["base"])
    px = fill.load()

    header_h = int(h * HEADER_FRAC) if spec.header_band else 0

    for y in range(h):
        in_header = y < header_h
        on_hline = spec.grid and (y % GRID_PERIOD == 0)
        row_base = pal["header"] if in_header else pal["base"]
        row_grid = pal["header_grid"] if in_header else pal["grid"]
        for x in range(w):
            on_vline = spec.grid and (x % GRID_PERIOD == 0)
            px[x, y] = row_grid if (on_hline or on_vline) else row_base

    if header_h > 0:
        # 1px full-width divider line where the header band meets the body
        for x in range(w):
            if header_h < h:
                px[x, header_h] = pal["header_grid"]

    if spec.edge_bars:
        # NOTE: fill is only ever visible where render_panel's interior_mask
        # keeps it, i.e. from BORDER_THICKNESS px inward (see build_inset_mask/
        # paint_border) -- everything drawn at fill-local x < BORDER_THICKNESS
        # gets clipped away entirely. Earlier versions drew this bar at x=0,
        # which meant it was invisible in every rendered panel (checked: true
        # of all 96 previously-generated files, not just new ones). Anchoring
        # it at BORDER_THICKNESS instead puts it right where the interior
        # actually starts.
        t = BORDER_THICKNESS
        bar = min(EDGE_BAR_WIDTH, max(1, w // 8))
        for y in range(h):
            # seam (1px) then bright bar, just inside the border, on the left...
            if 0 <= t < w:
                px[t, y] = pal["seam"]
            for i in range(1, 1 + bar):
                xx = t + i
                if xx < w:
                    px[xx, y] = pal["edge"]
            # ...and mirrored on the right
            if 0 <= w - 1 - t < w:
                px[w - 1 - t, y] = pal["seam"]
            for i in range(1, 1 + bar):
                xx = w - 1 - t - i
                if xx >= 0:
                    px[xx, y] = pal["edge"]

    if spec.shine_corner:
        _paint_shine_wedge(px, w, h, pal["shine"], spec.shine_corner)

    return fill


def _paint_shine_wedge(px, w, h, color, corner: str):
    """Hard-edged triangular highlight wedge, growing linearly from the
    corner — matches the measured widening-bright-region pattern, not a
    blurred diagonal stroke. Kept small/subtle like the real texture's
    accent (it reads as a glint, not a big flat wedge)."""
    size = max(6, int(min(w, h) * 0.10))
    for i in range(size):
        # how many pixels wide the bright band is on this row/col step
        band = max(1, int((i + 1) * 0.45))
        if corner == "bottom_left":
            y = h - 1 - i
            if y < 0:
                break
            for bx in range(min(band, w)):
                px[bx, y] = color
        elif corner == "bottom_right":
            y = h - 1 - i
            if y < 0:
                break
            for bx in range(min(band, w)):
                px[w - 1 - bx, y] = color
        elif corner == "top_left":
            y = i
            if y >= h:
                break
            for bx in range(min(band, w)):
                px[bx, y] = color
        elif corner == "top_right":
            y = i
            if y >= h:
                break
            for bx in range(min(band, w)):
                px[w - 1 - bx, y] = color


def render_speech_bubble(spec: PanelSpec) -> Image.Image:
    """A comic-style talk/speech bubble: the body is rendered through the
    exact same verified render_panel() path as every other "rounded" panel
    (unchanged border-ring + crosshatch-fill code), with a small triangular
    tail added underneath.

    Unlike every other shape in this file, this silhouette is NOT measured
    off a real texture. Neither DragonMineZ nor My NPCs ships anything
    resembling a speech/dialogue bubble -- checked directly (no "bubble",
    "speech", or "dialogue" asset exists in either mod's extracted
    textures) rather than assumed. This is an original shape built from
    the same verified palette/border/corner language as everything else
    here, not a reproduction of something that exists in either mod.
    """
    w, h = spec.w, spec.h
    pal = PALETTES[spec.palette]

    tail_h = max(8, int(h * 0.22))
    body_h = h - tail_h
    tail_base = max(10, int(w * 0.16))
    anchor = int(w * 0.28)  # off-center, classic comic-bubble tail placement

    body_img = render_panel(replace(spec, shape="rounded", h=body_h))

    canvas = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    canvas.paste(body_img, (0, 0), body_img)
    px = canvas.load()

    # hard pixel-stair triangle (same "no smooth AA" language as every
    # other corner in this file), 1px white stroke over the base fill
    # color, tapering from tail_base width down to a point. Starts 2px
    # inside the body's own bottom edge so it reads as attached, not
    # floating below a visible seam.
    overlap = 2
    top_y = body_h - overlap
    for i in range(tail_h + overlap):
        y = top_y + i
        if not (0 <= y < h):
            continue
        frac = max(0.0, 1.0 - i / float(tail_h))
        half = int((tail_base / 2) * frac)
        if half <= 0:
            continue
        x0, x1 = max(0, anchor - half), min(w - 1, anchor + half)
        for x in range(x0, x1 + 1):
            px[x, y] = pal["outer"] if (x == x0 or x == x1) else pal["base"]

    return canvas


MARK_GLYPHS = ("cross", "exclamation", "pointer", "question", "skull", "star")


def render_mark(spec: PanelSpec) -> Image.Image:
    """A small floating marker icon drawn above an NPC's head.

    Like render_speech_bubble, and unlike every panel shape in this file, these
    silhouettes are NOT measured off a real texture. My NPCs ships its own mark
    icons; those are its art and are not reproduced here. These are original
    glyphs drawn from the same palette language as the rest of the atlas -- a
    white body over a dark outline, so they stay readable against sky, stone or
    foliage at any distance.

    The glyph is chosen by the part of the spec name after "mark_".
    """
    pal = PALETTES[spec.palette]
    body = pal["outer"]
    outline = pal["base"]

    w, h = spec.w, spec.h
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # expand_all_palettes appends "_<palette>" to every name, so strip that back off before
    # reading which glyph this is.
    glyph = spec.name.split("mark_", 1)[-1]
    for palette_name in PALETTES:
        suffix = "_" + palette_name
        if glyph.endswith(suffix):
            glyph = glyph[: -len(suffix)]
            break
    # Work on a unit square so a mark can be generated at any size without the
    # proportions drifting.
    def px(fx, fy):
        return (fx * (w - 1), fy * (h - 1))

    if glyph == "cross":
        d.line([px(0.18, 0.18), px(0.82, 0.82)], fill=body, width=max(2, w // 6))
        d.line([px(0.82, 0.18), px(0.18, 0.82)], fill=body, width=max(2, w // 6))
    elif glyph == "exclamation":
        d.rectangle([px(0.40, 0.12), px(0.60, 0.62)], fill=body)
        d.rectangle([px(0.40, 0.74), px(0.60, 0.92)], fill=body)
    elif glyph == "question":
        d.arc([px(0.22, 0.08), px(0.78, 0.56)], start=170, end=20, fill=body,
              width=max(2, w // 7))
        d.rectangle([px(0.43, 0.42), px(0.57, 0.64)], fill=body)
        d.rectangle([px(0.41, 0.76), px(0.59, 0.92)], fill=body)
    elif glyph == "pointer":
        # A downward marker, the shape a waypoint pin makes.
        d.polygon([px(0.5, 0.94), px(0.16, 0.30), px(0.84, 0.30)], fill=body)
        d.rectangle([px(0.38, 0.06), px(0.62, 0.32)], fill=body)
    elif glyph == "skull":
        d.ellipse([px(0.14, 0.10), px(0.86, 0.68)], fill=body)
        d.rectangle([px(0.34, 0.62), px(0.66, 0.90)], fill=body)
        d.rectangle([px(0.28, 0.30), px(0.44, 0.48)], fill=outline)
        d.rectangle([px(0.56, 0.30), px(0.72, 0.48)], fill=outline)
        d.rectangle([px(0.46, 0.68), px(0.54, 0.88)], fill=outline)
    elif glyph == "star":
        import math
        pts = []
        for i in range(10):
            ang = -math.pi / 2 + i * math.pi / 5
            r = 0.48 if i % 2 == 0 else 0.20
            pts.append(px(0.5 + r * math.cos(ang), 0.5 + r * math.sin(ang)))
        d.polygon(pts, fill=body)
    else:
        raise ValueError(f"unknown mark glyph {glyph!r}")

    # One-pixel dark outline around whatever was drawn, so the glyph never
    # disappears against a light sky.
    alpha = img.split()[3]
    grown = alpha.filter(ImageFilter.MaxFilter(3))
    ring = Image.new("RGBA", (w, h), outline)
    ring.putalpha(Image.eval(grown, lambda v: 255 if v > 0 else 0))
    out = Image.alpha_composite(ring, img)
    return out


def render_panel(spec: PanelSpec) -> Image.Image:
    if spec.shape == "speech_bubble":
        return render_speech_bubble(spec)
    if spec.shape == "mark":
        return render_mark(spec)
    pal = PALETTES[spec.palette]
    border_layer, interior_mask = paint_border(spec, pal)
    fill = paint_fill(spec.w, spec.h, pal, spec)
    out = border_layer.copy()
    out.paste(fill, (0, 0), interior_mask)
    return out


# ---------------------------------------------------------------------------
# Shelf packer — lay panels left to right, wrap rows, exactly like the real
# menubig.png packs a big panel + header strip + icon buttons on one sheet.
# ---------------------------------------------------------------------------
def pack_atlas(specs: List[PanelSpec], max_width: int = 512, padding: int = 4):
    x = y = padding
    row_h = 0
    placements = []
    for spec in specs:
        if x + spec.w + padding > max_width:
            x = padding
            y += row_h + padding
            row_h = 0
        placements.append((spec, x, y))
        x += spec.w + padding
        row_h = max(row_h, spec.h)
    atlas_h_needed = y + row_h + padding
    atlas_w = next(c for c in (256, 512, 1024, 2048) if c >= max_width)
    atlas_h = next(c for c in (256, 512, 1024, 2048) if c >= atlas_h_needed)
    return placements, atlas_w, atlas_h


def build_atlas(specs: List[PanelSpec], out_prefix: str, max_width: int = 512):
    placements, atlas_w, atlas_h = pack_atlas(specs, max_width=max_width)
    atlas = Image.new("RGBA", (atlas_w, atlas_h), (0, 0, 0, 0))
    manifest = {}
    for spec, x, y in placements:
        panel_img = render_panel(spec)
        atlas.alpha_composite(panel_img, (x, y))
        manifest[spec.name] = {
            "u": x, "v": y, "width": spec.w, "height": spec.h,
            "atlas_w": atlas_w, "atlas_h": atlas_h,
            "blit_call": f'graphics.blit(TEXTURE, screenX, screenY, {x}.0f, {y}.0f, {spec.w}, {spec.h}, {atlas_w}, {atlas_h})',
        }
    atlas.save(f"{out_prefix}.png")
    with open(f"{out_prefix}.json", "w") as f:
        json.dump(manifest, f, indent=2)

    preview = atlas.convert("RGB").copy()
    pd = ImageDraw.Draw(preview)
    font = ImageFont.load_default()
    for spec, x, y in placements:
        pd.rectangle([x, y, x + spec.w - 1, y + spec.h - 1], outline=(255, 0, 0), width=1)
        pd.text((x + 2, y + 2), spec.name, fill=(255, 0, 0), font=font)
    preview.save(f"{out_prefix}_preview.png")
    return manifest, (atlas_w, atlas_h)


# ---------------------------------------------------------------------------
# Individual-file export — each panel saved as its OWN PNG, exactly
# spec.w x spec.h, transparent background, no shared canvas/UV math
# needed to use it. Use this when you want standalone textures rather
# than one packed sheet.
# ---------------------------------------------------------------------------
def export_individual(specs: List[PanelSpec], out_dir: str = "panels", write_json: bool = True) -> dict:
    """Renders each spec to its own PNG. When write_json is true (the
    default), also writes a companion <name>.json next to it — the same
    idea as the earlier packed-atlas's dmz_custom_atlas.json manifest,
    just adapted to a standalone file: since each PNG IS the whole
    texture now, its own u/v are always 0 and its atlas_w/atlas_h are
    just its own width/height, so the blit_call already has every number
    filled in and ready to paste into Java. A combined
    panels_manifest.json covering every file is also written to out_dir's
    parent so you don't have to open 56 files to see what's in the set."""
    import os
    os.makedirs(out_dir, exist_ok=True)
    manifest = {}
    for spec in specs:
        img = render_panel(spec)
        png_path = os.path.join(out_dir, f"{spec.name}.png")
        img.save(png_path)

        entry = {
            "name": spec.name,
            "file": f"{spec.name}.png",
            "width": spec.w,
            "height": spec.h,
            "shape": spec.shape,
            "radius": spec.radius,
            "palette": spec.palette,
            "grid": spec.grid,
            "edge_bars": spec.edge_bars,
            "header_band": spec.header_band,
            "shine_corner": spec.shine_corner,
            # standalone file -> the whole image is the sprite: u=0, v=0,
            # and the "atlas" width/height are just this file's own size.
            "u": 0, "v": 0,
            "atlas_w": spec.w, "atlas_h": spec.h,
            "blit_call": f'graphics.blit(TEXTURE, screenX, screenY, 0.0f, 0.0f, {spec.w}, {spec.h}, {spec.w}, {spec.h})',
        }
        manifest[spec.name] = entry

        if write_json:
            json_path = os.path.join(out_dir, f"{spec.name}.json")
            with open(json_path, "w") as f:
                json.dump(entry, f, indent=2)

    if write_json:
        combined_path = os.path.join(os.path.dirname(out_dir.rstrip("/")) or ".", "panels_manifest.json")
        with open(combined_path, "w") as f:
            json.dump(manifest, f, indent=2)

    return manifest


def build_contact_sheet(specs: List[PanelSpec], panel_dir: str, out_path: str, max_width: int = 700, padding: int = 14):
    """A labeled overview image only — NOT meant to be used as a game
    texture (that's what the individual files in panel_dir are for).
    Just makes it easy to eyeball the whole set at once."""
    import os
    x = y = padding
    row_h = 0
    placements = []
    for spec in specs:
        if x + spec.w + padding > max_width:
            x = padding
            y += row_h + padding + 14  # extra room for the label
            row_h = 0
        placements.append((spec, x, y))
        x += spec.w + padding
        row_h = max(row_h, spec.h)
    sheet_h = y + row_h + padding + 14
    sheet = Image.new("RGB", (max_width, sheet_h), (24, 24, 24))
    pd = ImageDraw.Draw(sheet)
    font = ImageFont.load_default()
    for spec, px_, py_ in placements:
        panel = Image.open(os.path.join(panel_dir, f"{spec.name}.png"))
        sheet.paste(panel, (px_, py_), panel)
        pd.rectangle([px_, py_, px_ + spec.w - 1, py_ + spec.h - 1], outline=(90, 90, 90), width=1)
        pd.text((px_, py_ + spec.h + 2), f"{spec.name} {spec.w}x{spec.h}", fill=(255, 255, 255), font=font)
    sheet.save(out_path)


def expand_all_palettes(base_specs: List[PanelSpec], palettes: Tuple[str, ...] = tuple(PALETTES.keys())) -> List[PanelSpec]:
    """Every shape/size in the base list, repeated once per palette, with
    the palette name appended to the filename (panel_tall_green.png,
    panel_tall_blue.png, ...) — so every element exists in every color,
    as its own file, none of them sharing a canvas."""
    import dataclasses
    out = []
    for spec in base_specs:
        for pal in palettes:
            out.append(dataclasses.replace(spec, name=f"{spec.name}_{pal}", palette=pal))
    return out


# ---------------------------------------------------------------------------
# Base set: a wide spread of shapes/sizes, from a small icon slot up to a
# big hero panel. expand_all_palettes() below then multiplies this by
# every palette so every element exists in every color — each still its
# own standalone transparent PNG (verified: alpha=0 outside the shape,
# not a viewer artifact — see the "not white" note above if it still
# LOOKS white in whatever app opens it, that app just doesn't checker
# transparent pixels).
# ---------------------------------------------------------------------------
if __name__ == "__main__":
    import shutil

    # radius=3 matches the real menubig.png corner exactly (measured stair
    # cuts: 3px, 2px, 1px, 0px). Kept at 3 across sizes since that's a
    # fixed pixel-art convention, not something that scales with the panel.
    # `palette` here is just each shape's starting color before expansion —
    # expand_all_palettes() overrides it with every palette anyway.
    base_specs = [
        # --- sized to match the REAL My NPCs mod's own GUI textures, pixel
        # for pixel (measured from mynpcs-neoforge-1.5.0.jar's
        # assets/mynpcs/textures/gui/*.png, trimmed to each sprite's actual
        # content bbox, not the 256x256 sheet each ships in) — same DMZ
        # green-fantasy look, but standing in for My NPCs' own flat-gray
        # panels/buttons at their exact dimensions:
        PanelSpec(name="mynpcs_main_panel", w=256, h=195, shape="rounded", radius=3, palette="green", shine_corner="bottom_left"),   # == standardbg.png
        PanelSpec(name="mynpcs_small_panel",w=176, h=222, shape="rounded", radius=3, palette="green", shine_corner="bottom_right"),  # == smallbg.png
        PanelSpec(name="mynpcs_tab",        w=28,  h=28,  shape="rounded", radius=2, palette="green", header_band=False, grid=False, edge_bars=False, shine_corner=None),  # == one tabs.png cell
        PanelSpec(name="mynpcs_top_button", w=200, h=61,  shape="rounded", radius=3, palette="green", header_band=False, shine_corner=None),  # == menutopbutton.png
        PanelSpec(name="mynpcs_side_button",w=197, h=66,  shape="rounded", radius=3, palette="green", header_band=False, shine_corner=None),  # == menusidebutton.png
        # --- more My NPCs button/element shapes, added for "even buttons? etc" ---
        # measured from components.png (connected-component + row-density scan of
        # the trimmed sprite sheet) and arrowbuttons.png / toasts.png the same way:
        PanelSpec(name="mynpcs_button_square", w=64,  h=64,  shape="rounded", radius=3, palette="green", header_band=False, shine_corner=None),   # == components.png big square action button (row 1)
        PanelSpec(name="mynpcs_button_row",    w=64,  h=22,  shape="rounded", radius=2, palette="green", header_band=False, shine_corner=None),   # == components.png list-row button (rows 2-4, "Show"/"Select Texture" style)
        PanelSpec(name="mynpcs_button_arrow",  w=22,  h=20,  shape="rounded", radius=2, palette="green", grid=False, edge_bars=False, header_band=False, shine_corner=None),  # == arrowbuttons.png (3 stacked scroll-arrow icons, all identical size)
        PanelSpec(name="mynpcs_toast",         w=160, h=32,  shape="rounded", radius=3, palette="green", header_band=False, shine_corner="bottom_right"),  # == toasts.png single notification popup (top of 2 stacked variants)
        # --- sized to match the attached NPC-editor screenshot exactly (842x471)
        PanelSpec(name="panel_editor",   w=842, h=471, shape="rounded", radius=3, palette="green", shine_corner="bottom_left"),
        PanelSpec(name="panel_huge",    w=280, h=360, shape="rounded", radius=3, palette="green", shine_corner="bottom_left"),
        PanelSpec(name="panel_tall",    w=141, h=213, shape="rounded", radius=3, palette="green", shine_corner="bottom_left"),
        PanelSpec(name="panel_square",  w=150, h=150, shape="rounded", radius=3, palette="green", shine_corner="bottom_right"),
        PanelSpec(name="panel_wide",    w=220, h=90,  shape="rounded", radius=3, palette="green", shine_corner="bottom_right"),
        PanelSpec(name="panel_small",   w=90,  h=70,  shape="rounded", radius=2, palette="green", shine_corner="top_left"),
        PanelSpec(name="header_strip",  w=180, h=22,  shape="rounded", radius=2, palette="green", header_band=False, shine_corner=None),
        PanelSpec(name="banner_top",    w=150, h=60,  shape="banner",  radius=4, palette="green", shine_corner="top_left"),
        PanelSpec(name="tab_docked",    w=70,  h=26,  shape="tab",     radius=3, palette="green", header_band=False, shine_corner=None),
        PanelSpec(name="pill_button",   w=90,  h=24,  shape="pill",    palette="green", header_band=False, shine_corner=None),
        PanelSpec(name="pill_button_lg",w=150, h=36,  shape="pill",    palette="green", header_band=False, shine_corner=None),
        PanelSpec(name="hex_badge",     w=40,  h=40,  shape="hex",     palette="green", header_band=False, edge_bars=False, shine_corner=None),
        PanelSpec(name="hex_badge_lg",  w=64,  h=64,  shape="hex",     palette="green", header_band=False, edge_bars=False, shine_corner=None),
        PanelSpec(name="icon_slot_sm",  w=20,  h=20,  shape="rounded", radius=2, palette="green", grid=False, edge_bars=False, header_band=False, shine_corner=None),
        PanelSpec(name="icon_slot_lg",  w=32,  h=32,  shape="rounded", radius=3, palette="green", grid=False, edge_bars=False, header_band=False, shine_corner=None),
        # --- comic-style talk/speech bubble for in-world NPC/mob dialogue.
        # An original shape, NOT measured off a real texture -- see
        # render_speech_bubble()'s docstring for why.
        PanelSpec(name="speech_bubble", w=120, h=56, shape="speech_bubble", radius=3, palette="green", shine_corner=None),
    ]

    all_specs = expand_all_palettes(base_specs)  # len(base_specs) shapes x 4 palettes

    manifest = export_individual(all_specs, out_dir="panels")
    print(f"Exported {len(manifest)} individual panel files to ./panels/ "
          f"({len(base_specs)} shapes x {len(PALETTES)} colors), all transparent PNG:")
    for name, info in manifest.items():
        print(f"  {info['file']}  ({info['width']}x{info['height']}, {info['shape']})")

    build_contact_sheet(all_specs, panel_dir="panels", out_path="panels_contact_sheet.png", max_width=760)
    print("Wrote panels_contact_sheet.png (overview only, not for use as a texture)")

    shutil.make_archive("panels_all_colors", "zip", root_dir=".", base_dir="panels")
    print(f"Wrote panels_all_colors.zip containing all {len(manifest)} individual files")
