#!/usr/bin/env python3
"""Regenerates the XenoPixels-specific additions to the panel atlas.

The upstream ``dmz_atlas_generator.py`` ships 24 base shapes x 4 palettes = 96 PNGs. This repo
needs a few sizes that set does not contain. Because the atlas is blitted 1:1 and never stretched
(see ``UPSTREAM-README.txt``), the answer to a missing size is to generate it, not to scale an
existing sprite at runtime. This file is the record of which extra sizes were added and why, so a
regeneration reproduces them instead of silently dropping them.

Usage, from this directory::

    python xeno_extra_specs.py --only xeno_quest_journal_panel xeno_quest_complete_rounded xeno_quest_complete_banner --out ../../src/generated/resources/assets/xenopixelsmod/textures/gui/atlas

Requires Pillow (``python -m pip install Pillow``). Without ``--out`` it writes to ``./xeno_panels``
so you can inspect before installing.

Verified 2026-09-21: re-running the upstream generator unmodified reproduces all 96 original PNGs
pixel-identically (only PNG encoder metadata differs), so extending it is safe.

Every shape added here must also be registered in ``XenoAtlasSprites`` with the same dimensions.
``XenoAtlasSpritesTest.everyRegisteredShapeHasAnExtractedPngForEveryTheme`` fails if a registered
shape has no PNG, which is the guard against the two drifting apart.
"""

import argparse
import importlib.util
import os
import shutil
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
GENERATOR = os.path.join(HERE, "dmz_atlas_generator.py")


def load_generator():
    """Executes the generator module body without running its __main__ block."""
    src = open(GENERATOR, encoding="utf-8").read()
    marker = src.find('if __name__ == "__main__"')
    if marker == -1:
        marker = src.find("if __name__ == '__main__'")
    if marker == -1:
        marker = len(src)
    namespace = {"__name__": "dmz_atlas_generator"}
    exec(compile(src[:marker], GENERATOR, "exec"), namespace)
    return namespace


def extra_specs(PanelSpec):
    """The shapes this repo added, with the reason each one exists."""
    specs = []

    # The Xeno NPC editor frame. DragonMineZ's ScaledScreen gives a virtual canvas of roughly
    # 640x360 at 1080p GUI scale 2 and never smaller than 320x240, so panel_editor (842x471) can
    # never fit it. 600x320 holds the ten-tab MyNPCs-style strip plus the live-preview column.
    specs.append(PanelSpec(name="xeno_editor_panel", w=600, h=320, shape="rounded", radius=3,
                           palette="green", shine_corner="bottom_left"))

    # Companion panel for the vanilla inventory's faction and quest tabs. This is a real 1:1
    # Minecraft GUI surface, so keep the neon frame baked into the generated 248x166 sprite.
    specs.append(PanelSpec(name="xeno_inventory_panel", w=248, h=166, shape="rounded", radius=3,
                           palette="green", header_band=True, edge_bars=True,
                           shine_corner="bottom_left"))

    # Dedicated journal frame and quest-completion cards use fixed 1:1 sizes and all four palettes.
    specs.append(PanelSpec(name="xeno_quest_journal_panel", w=420, h=320, shape="rounded", radius=3,
                           palette="green", header_band=True, edge_bars=True,
                           shine_corner="bottom_left"))
    for frame_shape in ("rounded", "banner"):
        specs.append(PanelSpec(name=f"xeno_quest_complete_{frame_shape}", w=360, h=180,
                               shape=frame_shape, radius=4, palette="green", header_band=True,
                               edge_bars=True, shine_corner="bottom_left"))

    # New-quest notification toast. It uses a generated frame at native size rather than four
    # screen fills, keeping the same crisp Xeno atlas border as the journal and completion card.
    specs.append(PanelSpec(name="xeno_quest_toast", w=220, h=56, shape="rounded", radius=3,
                           palette="blue", header_band=True, edge_bars=True,
                           shine_corner="bottom_left"))

    # Dedicated equipment frames let the NPC gear menu share the Xeno neon treatment without
    # stretching MyNPCs' grey reference panel or its proportions.
    specs.append(PanelSpec(name="xeno_npc_gear_panel", w=176, h=222, shape="rounded", radius=3,
                           palette="green", header_band=True, edge_bars=True,
                           shine_corner="bottom_left"))
    specs.append(PanelSpec(name="xeno_npc_curios_panel", w=141, h=213, shape="rounded", radius=3,
                           palette="green", header_band=True, edge_bars=True,
                           shine_corner="bottom_left"))

    # The same frame, narrowed so the live visualizer can sit BESIDE it rather than inside it, the
    # way My NPCs arranges its own. The canvas is about 640x360, so 420 + 8 gap + the 176-wide
    # mynpcs_small_panel comes to 604 and leaves 28px of margin; the 600 frame left no room at all
    # and a panel placed beside it was clamped straight back on top.
    #
    # Generated at this width rather than scaled down from the 600 one: fittedSize resamples, and
    # the border rings in this art are baked in at generation (docs/atlas-ui-doco.md).
    # xeno_editor_panel stays - client/screen/XenoPartyScreen still uses it.
    specs.append(PanelSpec(name="xeno_editor_panel_w420", w=420, h=320, shape="rounded", radius=3,
                           palette="green", shine_corner="bottom_left"))

    # Shorter editor frames, so a page with few rows does not leave an empty band above the footer.
    # The frame cannot simply be drawn shorter: AtlasPanel resamples when asked for a size it does
    # not have, and the border rings here are baked in at generation. So the heights are generated,
    # the way the speech bubble and the docked tabs are, and the editor picks the shortest that
    # holds the page it is about to draw.
    for h in (200, 240, 280):
        specs.append(PanelSpec(name=f"xeno_editor_panel_w420_h{h}", w=420, h=h, shape="rounded",
                               radius=3, palette="green", shine_corner="bottom_left"))

    # Docked tabs at discrete widths. The reference MyNPCs menu has tabs that vary with their
    # label ("Inventory" is visibly wider than "AI"), so one fixed cell is wrong - but stretching
    # one cell per label distorts a border baked in at generation time. AtlasTabStrip picks the
    # narrowest of these that holds each label.
    for w in (20, 28, 36, 44, 52, 60, 68):
        specs.append(PanelSpec(name=f"tab_docked_w{w}", w=w, h=26, shape="tab", radius=3,
                               palette="green", header_band=False, shine_corner=None))

    # Dense toolbar chips. UiStudioScreen packs 13 type buttons plus a 10-button action row into
    # a 42px band; mynpcs_button_row (64x22) overflows it. Same knobs as that shape, just narrower
    # and 18 high. XenoAtlasSprites.chip(minWidth) resolves a requested width to one of these.
    for w in (20, 32, 40, 48, 56, 64, 112):
        specs.append(PanelSpec(name=f"ui_chip_w{w}", w=w, h=18, shape="rounded", radius=2,
                               palette="green", header_band=False, shine_corner=None))

    # MyNPCs editor row buttons are not one fixed width: hub buttons fill a half-column and the
    # Global list fills the whole body. These are generated siblings of mynpcs_button_row rather
    # than runtime-scaled copies, so their two-pixel frame and corner radius stay crisp.
    #
    # The narrow end matters as much as the wide one. A slot row is "number, [X], [ pick ]", and
    # its clear button is a fifth of the width of the pick beside it. With 64 as the narrowest
    # generated row, rowWithin(18) could only answer 64 - so the clear button was sized three and a
    # half times its slot and drew straight over the button next to it, which is what put the X
    # inside the pick frame in the editor.
    for w in (16, 20, 28, 40, 52, 96, 128, 176, 256, 360):
        specs.append(PanelSpec(name=f"mynpcs_button_row_w{w}", w=w, h=22,
                               shape="rounded", radius=2, palette="green",
                               header_band=False, shine_corner=None))

    # The speech bubble above an NPC's head. Unlike every other shape here this one is not sized
    # off a real texture from either mod - neither ships a dialogue bubble - so it is an original
    # built from the same border/fill code, and the upstream generator carries the shape renderer.
    specs.append(PanelSpec(name="speech_bubble", w=120, h=56, shape="speech_bubble", radius=3,
                           palette="green", shine_corner=None))

    # Sized bubbles, for the same reason tab_docked and ui_chip come in sizes: the art is blitted
    # 1:1 and its border is baked in at generation, so a long line cannot be handled by stretching
    # the one 120x56 cell. It used to not be handled at all - the text was drawn as a single
    # unwrapped line and simply ran out past both edges of the bubble.
    #
    # The heights are derived, not guessed. Minecraft's font is 9px tall and the renderer spaces
    # lines 10px apart, the body carries 16px of padding, and TAIL_HEIGHT_FRACTION reserves the
    # bottom 22% for the tail - so the cell for n lines is about (10n + 6 + 16) / 0.78, which gives
    # roughly 41 / 54 / 67 / 80 / 93 for one through five lines. Rounded to the values below.
    # SpeechBubbleRenderer picks the smallest cell the wrapped text fits in.
    for w in (120, 180, 256):
        for h in (42, 54, 68, 80, 94):
            specs.append(PanelSpec(name=f"speech_bubble_w{w}_h{h}", w=w, h=h,
                                   shape="speech_bubble", radius=3, palette="green",
                                   shine_corner=None))

    # Advanced > Marks: the six floating markers from the reference menu. My NPCs ships its own
    # icons for these; those are its art and are not reproduced. These are original glyphs drawn
    # from the same palette language as everything else here - see render_mark's docstring, which
    # says so in the generator itself rather than leaving it to be inferred.
    #
    # 32px, square. The speech bubble is 120px wide and renders at BUBBLE_SCALE into roughly one
    # and a half world units, so a 16px mark came out at about a fifth of a block - present but
    # unreadable. 32 gives the skull and the question mark enough room to be told apart.
    for glyph in ("cross", "exclamation", "pointer", "question", "skull", "star"):
        specs.append(PanelSpec(name=f"mark_{glyph}", w=32, h=32, shape="mark", radius=0,
                               palette="green", shine_corner=None))

    # ---- Script bubbles (2026-09-27): three more outlines beside the rounded speech bubble, on the
    # same size ladder, so npc.say(text, palette, shape) can pick one per line. Original shapes,
    # like speech_bubble and the marks: neither mod ships a thought cloud, shout burst or banner
    # plate. Rendered by install_renderers below, which reuses the upstream palette, ring widths
    # and crosshatch fill.
    for kind in ("thought", "shout", "banner"):
        for w in (120, 180, 256):
            for h in (42, 54, 68, 80, 94):
                specs.append(PanelSpec(name=f"speech_bubble_{kind}_w{w}_h{h}", w=w, h=h,
                                       shape=f"bubble_{kind}", radius=3, palette="green",
                                       shine_corner=None))

    # ---- Script screen, 1:1 with CustomNPCs' GuiScriptInterface (2026-09-27). That GUI is sized
    # as 88% of the screen width at a 0.56 aspect; the atlas is never stretched, so the frame comes
    # in a ladder and XenoScriptLayout picks the largest that fits, then derives every inner panel
    # with the reference's own formulas. The inner panels are generated at exactly those sizes.
    for w in SCRIPT_FRAME_WIDTHS:
        L = script_layout(w)
        specs.append(PanelSpec(name=f"xeno_script_frame_w{w}", w=w, h=L["h"], shape="rounded",
                               radius=3, palette="blue", shine_corner="bottom_left"))
        for part in ("code", "console", "files", "hooks", "consts"):
            pw, ph = L[part]
            specs.append(PanelSpec(name=f"xeno_script_{part}_w{pw}_h{ph}", w=pw, h=ph,
                                   shape="rounded", radius=2, palette="blue", header_band=False,
                                   edge_bars=False, shine_corner=None))
    # "Load Scripts" sub-panel (GuiScriptList: 346x216, two 140x180 lists).
    specs.append(PanelSpec(name="xeno_script_load_panel", w=346, h=216, shape="rounded",
                           radius=3, palette="blue", shine_corner="bottom_left"))
    specs.append(PanelSpec(name="xeno_script_list_w140_h180", w=140, h=180, shape="rounded",
                           radius=2, palette="blue", header_band=False, edge_bars=False,
                           shine_corner=None))
    # Buttons at the reference's exact sizes (60x20 Clear/Paste/Copy/Remove, 121x20 Functions /
    # Load Scripts, 80x20 language and links, 50x20 Enabled, 150x20 open folder, 55x20 the list
    # movers). The 90x24 pill drawn into 52px slots is what made the old toolbar overlap.
    for w in (50, 55, 60, 80, 121, 150):
        specs.append(PanelSpec(name=f"xeno_btn_w{w}_h20", w=w, h=20, shape="rounded", radius=2,
                               palette="blue", header_band=False, shine_corner=None))

    # ---- Inline colour picker (2026-09-27): a frame that opens over the editor instead of a new
    # screen, frames around the hue/saturation square and the value bar, and the square itself.
    specs.append(PanelSpec(name="xeno_color_picker_panel", w=210, h=150, shape="rounded",
                           radius=3, palette="gold", header_band=True, edge_bars=True,
                           shine_corner="bottom_left"))
    # The swatch on every colour row: blue at rest, gold while its picker is open.
    specs.append(PanelSpec(name="xeno_swatch_frame", w=16, h=18, shape="rounded", radius=2,
                           palette="blue", header_band=False, edge_bars=False, grid=False,
                           shine_corner=None))
    specs.append(PanelSpec(name="xeno_hsv_frame", w=108, h=108, shape="rounded", radius=2,
                           palette="blue", header_band=False, edge_bars=False, shine_corner=None))
    specs.append(PanelSpec(name="xeno_value_frame", w=18, h=108, shape="rounded", radius=2,
                           palette="blue", header_band=False, edge_bars=False, shine_corner=None))
    # Hue across, saturation down, full value; identical in every palette (a colour chart, not
    # themed chrome). The picker darkens it with a black overlay at alpha 1 - v, which is exactly
    # v * rgb, so one PNG serves every value.
    specs.append(PanelSpec(name="xeno_hsv_square", w=96, h=96, shape="hsv_square", radius=0,
                           palette="blue", shine_corner=None))

    # 1:1 CustomNPCs screens in the blue atlas (2026-09-28): the Nearby NPCs screen is 256x216 in
    # CustomNPCs (mynpcs_main_panel is 195 high), its buttons 80x20 and the scripter's Players /
    # Forge buttons 100x20. Same knobs as mynpcs_main_panel / mynpcs_button_row, generated at size
    # because AtlasPanel resamples and the borders are baked in.
    specs.append(PanelSpec(name="mynpcs_nearby_panel", w=256, h=216, shape="rounded", radius=3,
                           palette="green", shine_corner="bottom_left"))
    for w in (80, 100):
        specs.append(PanelSpec(name=f"mynpcs_button_{w}x20", w=w, h=20,
                               shape="rounded", radius=2, palette="green",
                               header_band=False, shine_corner=None))

    # ---- Unified Maker Studio (PR-D6c / 2026-10-03): exact Race / Form / Hair layouts.
    # Outer frame, style list (panel_tall 141x213), full-body preview (mynpcs_small_panel
    # 176x222), pills/rows/swatches already exist — add only sizes the three screens need
    # that are missing from panels_manifest (never stretch baked borders).
    #
    # Race Character Creator inside xeno_editor_panel 600x320:
    #   top race cards 72x56 (panel_small 90x70 only fits ~5 with gaps);
    #   category col 96x240 (panel_tall 141 is too wide for the 3-column body);
    #   part grid 240x220; right preview reuses 176x222.
    specs.append(PanelSpec(name="xeno_maker_race_card", w=72, h=56, shape="rounded", radius=3,
                           palette="green", header_band=True, edge_bars=True,
                           shine_corner="bottom_left"))
    specs.append(PanelSpec(name="xeno_maker_category_col", w=96, h=240, shape="rounded", radius=3,
                           palette="green", header_band=True, edge_bars=True,
                           shine_corner="bottom_left"))
    specs.append(PanelSpec(name="xeno_maker_part_grid", w=240, h=220, shape="rounded", radius=3,
                           palette="green", header_band=True, edge_bars=True,
                           shine_corner="bottom_left"))
    # Form Maker: tall form list + settings beside dual preview wells.
    # Script list 140x180 is too short; nearby 256x216 is too wide for list+settings+dual.
    specs.append(PanelSpec(name="xeno_maker_form_list", w=140, h=280, shape="rounded", radius=3,
                           palette="green", header_band=True, edge_bars=True,
                           shine_corner="bottom_left"))
    specs.append(PanelSpec(name="xeno_maker_form_settings", w=220, h=240, shape="rounded", radius=3,
                           palette="green", header_band=True, edge_bars=True,
                           shine_corner="bottom_left"))
    # Dual aura/form avatar wells; panel_square 150x150 cannot stack twice under h=240.
    specs.append(PanelSpec(name="xeno_maker_preview_sm", w=150, h=112, shape="rounded", radius=3,
                           palette="green", header_band=False, edge_bars=True,
                           shine_corner="bottom_left"))
    # Hair Editor large preview with glow headroom; panel_huge 280x360 misses the canvas,
    # panel_square 150 is too small for the owner hair-glow chrome.
    specs.append(PanelSpec(name="xeno_maker_hair_preview", w=280, h=240, shape="rounded", radius=3,
                           palette="green", header_band=True, edge_bars=True,
                           shine_corner="bottom_left"))

    # ---- XenoCombat v2 prompt (2026-10-05 owner: "use dmz atlases generator tool ... to create
    # new ones in different shapes", green). The combat prompt draws one plate per open
    # combo branch, and the shape is the meaning, so a branch can be read without reading its
    # label: pill = light, hex = heavy/kick, banner = grab and throw, arrow = rush and chase,
    # burst = the urgent ones (counter, break free).
    #
    # One text line each, so 22 high like mynpcs_button_row: the six ring pixels on each side
    # leave ten for a nine pixel font. The burst is taller because its spikes come out of the
    # same height. Widths are a ladder for the same reason the tabs and chips are - the art is
    # never stretched - and XenoAtlasSprites.combatPrompt(kind, minWidth) picks the narrowest
    # plate that holds the key name and label.
    for kind, shape, height in COMBAT_PROMPT_KINDS:
        for w in COMBAT_PROMPT_WIDTHS:
            specs.append(PanelSpec(name=f"combat_prompt_{kind}_w{w}", w=w, h=height, shape=shape,
                                   radius=3, palette="green", header_band=False, edge_bars=False,
                                   shine_corner=None))

    # ---- Pause-menu music player (2026-10-08 owner: "a small DMZ-style green player bottom-left,
    # from the tools generator, visible only on Escape"). One compact plate: a title line and a
    # row of ui_chip_w20 / ui_chip_w32 buttons (already generated) sit inside it at 1:1. Generated
    # at this exact size because the atlas is never stretched; the header band carries the title.
    specs.append(PanelSpec(name="xeno_music_panel", w=150, h=40, shape="rounded", radius=3,
                           palette="green", header_band=True, edge_bars=False,
                           shine_corner="bottom_left"))

    return specs


# kind -> (upstream or install_renderers shape, height). XenoAtlasSprites carries the same table.
COMBAT_PROMPT_KINDS = (
    ("light", "pill", 22),
    ("heavy", "prompt_hex", 22),
    ("grab", "banner", 22),
    ("rush", "prompt_arrow", 22),
    ("alert", "prompt_burst", 32),
)
COMBAT_PROMPT_WIDTHS = (56, 72, 88, 104, 120)


# CustomNPCs GuiScriptInterface.init, reconstructed in logs/guiscript_iface_javap.txt:
#   imageHeight = imageWidth * 0.56, yoff = (int)(imageHeight * 0.02), bgScale = imageWidth / 400
#   code    : (imageWidth - 108 - yoff) x ((int)(imageHeight * 0.96) - 2*yoff)
#   console : (imageWidth - 160 - yoff) x ((int)(imageHeight * 0.92) - 2*yoff)
#   files   : (int)(104 + 16*bgScale)   x ((int)(imageHeight * 0.54) - 2*yoff)
#   hooks   : same width                x ((int)(imageHeight * 0.60) - 2*yoff)
#   consts  : same width                x ((int)(imageHeight * 0.32) - 2*yoff)
# XenoScriptLayout.java carries the same formulas; XenoScriptLayoutTest pins them together.
SCRIPT_FRAME_WIDTHS = (320, 400, 480, 560)


def script_layout(w):
    # Measured from the frame's inner edge (margin 8), with the reference's 121 px button column
    # and a 161 px settings column (two 80 px buttons). The first version copied the reference's
    # "imageWidth - 104" column, which only fits because its background texture is drawn wider
    # than its imageWidth; on our frame the buttons ran off the edge.
    margin, side, settings, gap, files_top = 8, 121, 161, 6, 96
    h = int(w * 0.56)
    lists = h - 2 * margin - 22 - 4
    hooks = int(lists * 0.62)
    return {
        "h": h,
        "code": (w - 2 * margin - side - gap, h - 2 * margin),
        "console": (w - 2 * margin - settings - gap, h - 2 * margin),
        "files": (side, h - margin - files_top),
        "hooks": (side, hooks),
        "consts": (side, lists - hooks - 4),
    }


def install_renderers(gen):
    """Adds this file's shapes to the upstream generator's render_panel without editing it.

    export_individual looks render_panel up in the generator's own namespace, so replacing the
    entry there routes the new shape names here and everything else to the original.
    """
    from PIL import Image, ImageDraw, ImageFilter
    import colorsys
    import math

    PALETTES = gen["PALETTES"]
    original = gen["render_panel"]
    paint_fill = gen["paint_fill"]
    replace = gen["replace"]

    def ringed(spec, silhouette):
        """Border rings + crosshatch fill over an arbitrary silhouette mask (L, 0/255).

        The upstream shapes recompute an exact inset per ring; an irregular outline has no closed
        form for that, so the rings here are erosions of the silhouette (2, 2, 1, 1 px, the same
        steps as paint_border), which stays uniform along scallops and spikes.
        """
        pal = PALETTES[spec.palette]
        w, h = spec.w, spec.h

        def eroded(depth):
            if depth <= 0:
                return silhouette
            return silhouette.filter(ImageFilter.MinFilter(2 * depth + 1))

        canvas = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        for depth, color in ((0, pal["outer"]), (2, pal["blend"]), (4, pal["inner"]),
                             (5, pal["blend"])):
            canvas.paste(Image.new("RGBA", (w, h), color), (0, 0), eroded(depth))
        fill = paint_fill(w, h, pal, replace(spec, header_band=False, edge_bars=False,
                                             shine_corner=None))
        canvas.paste(fill, (0, 0), eroded(6))
        return canvas

    def thought(spec):
        w, h = spec.w, spec.h
        tail_h = max(8, int(h * 0.22))
        body_h = h - tail_h
        mask = Image.new("L", (w, h), 0)
        d = ImageDraw.Draw(mask)
        # Puffs sized off the body height, spaced evenly along each edge so both corners end on a
        # puff: the cloud outline. The core fills between them so the text area stays solid.
        r = max(7, min(14, body_h // 3))
        d.rounded_rectangle([r // 2, r // 2, w - 1 - r // 2, body_h - 1 - r // 2],
                            radius=r, fill=255)

        def puffs(length, radius):
            count = max(2, int(round(length / (radius * 1.6))))
            return [radius + i * (length - 2 * radius) / (count - 1) for i in range(count)]

        for x in puffs(w, r):
            d.ellipse([x - r, 0, x + r, 2 * r], fill=255)
            d.ellipse([x - r, body_h - 1 - 2 * r, x + r, body_h - 1], fill=255)
        for y in puffs(body_h, r):
            d.ellipse([0, y - r, 2 * r, y + r], fill=255)
            d.ellipse([w - 1 - 2 * r, y - r, w - 1, y + r], fill=255)
        # Two trailing thought dots toward the speaker, big enough to survive the 6px rings.
        ax = int(w * 0.28)
        # Separate from the body: dots too small for all six ring pixels read as white beads,
        # which is how a comic thought trail looks anyway.
        big = max(3.0, tail_h * 0.34)
        small = max(2.0, tail_h * 0.22)
        by = body_h + 1
        d.ellipse([ax - big, by, ax + big, by + 2 * big], fill=255)
        sx = ax - big - small - 1
        d.ellipse([sx - small, h - 1 - 2 * small, sx + small, h - 1], fill=255)
        return ringed(spec, mask)

    def shout(spec):
        w, h = spec.w, spec.h
        tail_h = max(8, int(h * 0.22))
        body_h = h - tail_h
        mask = Image.new("L", (w, h), 0)
        d = ImageDraw.Draw(mask)
        # Spikes all the way round: walk the perimeter of the body box, alternating a point on
        # the outer edge with one pulled in by `depth`, so every side bursts, not just the ends.
        depth = max(6, min(10, body_h // 5))
        spacing = max(10, depth * 2)
        x0, y0, x1, y1 = 0, 0, w - 1, body_h - 1
        perimeter = []
        for side in range(4):
            if side == 0:
                a, b, n = (x0, y0), (x1, y0), (0, 1)
            elif side == 1:
                a, b, n = (x1, y0), (x1, y1), (-1, 0)
            elif side == 2:
                a, b, n = (x1, y1), (x0, y1), (0, -1)
            else:
                a, b, n = (x0, y1), (x0, y0), (1, 0)
            length = math.hypot(b[0] - a[0], b[1] - a[1])
            steps = max(2, int(length // spacing) * 2)
            for i in range(steps):
                t = i / float(steps)
                px_, py_ = a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t
                inward = depth if i % 2 else 0
                # Corners are valleys: a corner spike left hairline slivers after the ring erosion.
                if i == 0:
                    inward = depth
                perimeter.append((px_ + n[0] * inward,
                                  py_ + n[1] * inward))
        d.polygon(perimeter, fill=255)
        d.rectangle([depth, depth, w - 1 - depth, body_h - 1 - depth], fill=255)
        # Opening (erode then dilate) drops the hairline slivers where two sides meet at a
        # corner; they would otherwise survive as stray one-pixel white strokes.
        mask = mask.filter(ImageFilter.MinFilter(3)).filter(ImageFilter.MaxFilter(3))
        d = ImageDraw.Draw(mask)
        # A jagged bolt for the tail.
        ax = int(w * 0.28)
        mid = body_h + tail_h // 2
        bolt = [(ax - 6, body_h - 3), (ax + 5, body_h - 3), (ax + 1, mid), (ax + 6, mid),
                (ax - 4, h - 1), (ax - 1, mid + 1), (ax - 7, mid + 1)]
        d.polygon(bolt, fill=255)
        return ringed(spec, mask)

    def banner(spec):
        # The upstream banner plate for the body (its verified mask code), with a short centred
        # pointer instead of the comic tail: narration rather than speech.
        w, h = spec.w, spec.h
        pal = PALETTES[spec.palette]
        tail_h = max(8, int(h * 0.22))
        body_h = h - tail_h
        body = original(replace(spec, shape="banner", h=body_h, header_band=False))
        canvas = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        canvas.paste(body, (0, 0), body)
        px = canvas.load()
        mid = w // 2
        for i in range(tail_h):
            y = body_h - 2 + i
            half = 6 - i
            if half <= 0 or y >= h:
                break
            for x in range(mid - half, mid + half + 1):
                px[x, y] = pal["outer"] if x in (mid - half, mid + half) else pal["base"]
        return canvas

    def ringed_to_edge(spec, silhouette):
        """As ringed(), for a silhouette that runs along the edge of its own canvas.

        ringed() erodes the mask to find each ring, and an erosion cannot shrink a shape away from
        the edge of the image it is drawn on: there is nothing outside the canvas to erode from.
        The bubbles sit clear of their edges, so they never showed it, but a prompt plate fills its
        cell top to bottom and came out with no border along those two sides. Eroding on a canvas
        with a margin, then cropping the margin off, gives every side the same rings.
        """
        pal = PALETTES[spec.palette]
        w, h = spec.w, spec.h
        margin = 8
        padded = Image.new("L", (w + 2 * margin, h + 2 * margin), 0)
        padded.paste(silhouette, (margin, margin))

        def eroded(depth):
            layer = padded if depth <= 0 else padded.filter(ImageFilter.MinFilter(2 * depth + 1))
            return layer.crop((margin, margin, margin + w, margin + h))

        canvas = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        for depth, color in ((0, pal["outer"]), (2, pal["blend"]), (4, pal["inner"]),
                             (5, pal["blend"])):
            canvas.paste(Image.new("RGBA", (w, h), color), (0, 0), eroded(depth))
        fill = paint_fill(w, h, pal, replace(spec, header_band=False, edge_bars=False,
                                             shine_corner=None))
        canvas.paste(fill, (0, 0), eroded(6))
        return canvas

    def prompt_hex(spec):
        # The upstream hex cuts its points at 22% of the width, which is right for a square badge
        # and turns a wide plate into a spear. These ends are sized off the height instead, so
        # every width in the ladder has the same 45 degree points.
        w, h = spec.w, spec.h
        tip = h // 2
        mid = (h - 1) / 2.0
        mask = Image.new("L", (w, h), 0)
        ImageDraw.Draw(mask).polygon(
            [(tip, 0), (w - 1 - tip, 0), (w - 1, mid), (w - 1 - tip, h - 1), (tip, h - 1),
             (0, mid)], fill=255)
        return ringed_to_edge(spec, mask)

    def prompt_arrow(spec):
        # A plate that points: flat left end with a chevron notch, right end drawn to a tip. The
        # notch and tip are sized off the height so every width in the ladder has the same ends.
        w, h = spec.w, spec.h
        tip = h // 2
        notch = max(4, h // 3)
        mid = (h - 1) / 2.0
        mask = Image.new("L", (w, h), 0)
        ImageDraw.Draw(mask).polygon(
            [(0, 0), (w - 1 - tip, 0), (w - 1, mid), (w - 1 - tip, h - 1), (0, h - 1),
             (notch, mid)], fill=255)
        return ringed_to_edge(spec, mask)

    def prompt_burst(spec):
        # The shout bubble's spiked outline without its tail, with shallower spikes: at this size
        # the bubble's own depth would leave no room for a line of text between them.
        w, h = spec.w, spec.h
        depth = 5
        spacing = 10
        mask = Image.new("L", (w, h), 0)
        d = ImageDraw.Draw(mask)
        x0, y0, x1, y1 = 0, 0, w - 1, h - 1
        perimeter = []
        for side in range(4):
            if side == 0:
                a, b, n = (x0, y0), (x1, y0), (0, 1)
            elif side == 1:
                a, b, n = (x1, y0), (x1, y1), (-1, 0)
            elif side == 2:
                a, b, n = (x1, y1), (x0, y1), (0, -1)
            else:
                a, b, n = (x0, y1), (x0, y0), (1, 0)
            length = math.hypot(b[0] - a[0], b[1] - a[1])
            steps = max(2, int(length // spacing) * 2)
            for i in range(steps):
                t = i / float(steps)
                px_, py_ = a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t
                inward = depth if (i % 2 or i == 0) else 0
                perimeter.append((px_ + n[0] * inward, py_ + n[1] * inward))
        d.polygon(perimeter, fill=255)
        d.rectangle([depth, depth, w - 1 - depth, h - 1 - depth], fill=255)
        # Opening (erode then dilate), as the shout bubble does, drops the hairline slivers left
        # where two sides meet at a corner. Done on a padded copy for the reason ringed_to_edge
        # gives: the spike tips touch the edge of the canvas.
        pad = 2
        padded = Image.new("L", (w + 2 * pad, h + 2 * pad), 0)
        padded.paste(mask, (pad, pad))
        padded = padded.filter(ImageFilter.MinFilter(3)).filter(ImageFilter.MaxFilter(3))
        mask = padded.crop((pad, pad, pad + w, pad + h))
        return ringed_to_edge(spec, mask)

    def hsv_square(spec):
        w, h = spec.w, spec.h
        img = Image.new("RGBA", (w, h))
        px = img.load()
        for y in range(h):
            sat = 1.0 - y / float(h - 1)
            for x in range(w):
                r, g, b = colorsys.hsv_to_rgb(x / float(w), sat, 1.0)
                px[x, y] = (int(round(r * 255)), int(round(g * 255)), int(round(b * 255)), 255)
        return img

    def render_panel(spec):
        if spec.shape == "bubble_thought":
            return thought(spec)
        if spec.shape == "bubble_shout":
            return shout(spec)
        if spec.shape == "bubble_banner":
            return banner(spec)
        if spec.shape == "hsv_square":
            return hsv_square(spec)
        if spec.shape == "prompt_hex":
            return prompt_hex(spec)
        if spec.shape == "prompt_arrow":
            return prompt_arrow(spec)
        if spec.shape == "prompt_burst":
            return prompt_burst(spec)
        return original(spec)

    gen["render_panel"] = render_panel


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--out", default=None,
                        help="directory to install the PNGs into; defaults to ./xeno_panels")
    parser.add_argument("--only", nargs="*", default=None,
                        help="install only these exact shape names (all palettes); staging still regenerates the full atlas")
    args = parser.parse_args()

    try:
        gen = load_generator()
    except ImportError as exc:
        print(f"error: {exc}\nInstall Pillow first: python -m pip install Pillow", file=sys.stderr)
        return 1

    install_renderers(gen)
    specs = extra_specs(gen["PanelSpec"])
    all_specs = gen["expand_all_palettes"](specs)

    staging = os.path.join(HERE, "xeno_panels")
    os.makedirs(staging, exist_ok=True)
    gen["export_individual"](all_specs, out_dir=staging, write_json=True)

    all_pngs = sorted(f for f in os.listdir(staging) if f.endswith(".png"))
    selected = set(args.only or ())
    pngs = [name for name in all_pngs if not selected
            or any(name.startswith(shape + "_") for shape in selected)]
    print(f"generated {len(all_pngs)} PNGs from {len(specs)} shapes x 4 palettes")

    if args.out:
        os.makedirs(args.out, exist_ok=True)
        for name in pngs:
            shutil.copy2(os.path.join(staging, name), os.path.join(args.out, name))
        print(f"installed {len(pngs)} PNGs into {args.out}")
    else:
        print(f"left in {staging}; pass --out to install")

    print("\nremember: every shape here must also be registered in XenoAtlasSprites.java")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
