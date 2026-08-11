"""Shared atlas layout, palette and element sizes for the XenoPixels HUD.

Imported by ``gen_hud_textures.py``. The element sizes below are the **same numbers** as
``client/hud/XenoHudLayout.java`` — they are paired by hand, and the Java file says so. Art is
generated at exactly these sizes rather than stretched, which is what lets a gradient fill and
a bevelled frame survive contact with a scaled HUD.

Bars are XV2-style skewed parallelograms, so 9-slicing does not apply cleanly; each bar is
emitted pre-skewed at its final size instead.
"""
from __future__ import annotations

# 512 so bars can be authored at their on-screen width. The HUD applies its own uniform
# scale (XenoHudConfig.scale, default 0.55), so a 1:1 texel mapping at scale 1 keeps the art
# crisp instead of stretching a small texture across a wide bar.
ATLAS = 512

# --- element sizes (mirrored in XenoHudLayout.java) ---
BAR_W = 300
BAR_H = 14
STM_SEG_W = 14
STM_SEG_H = 10
PORTRAIT = 64
SKEW = 4  # horizontal lean, in pixels, top edge relative to bottom
PANEL = 48  # backing plate source size; drawn 9-sliced, never stretched whole
PANEL_CORNER = 16  # 9-slice inset for the backing plate
BAR_TICKS = 4  # quarter marks etched into the empty track
BAR_TIP_W = 6  # width of the leading-edge cap stamped at a partial bar's fill boundary

# --- cooldown strip element sizes (mirrored in XenoHudLayout.java) ---
CD_CHIP = 32        # chip plate source square; nine-sliced to the real chip rectangle
CD_CHIP_CORNER = 8  # nine-slice inset holding the chip bevel
CD_BADGE_W = 24     # key badge plate, also nine-sliced
CD_BADGE_H = 14
CD_BADGE_CORNER = 4
CD_RAIL_W = 4       # accent rail down the chip's left edge, stretched vertically
CD_RAIL_H = 24
CD_METER_W = 48     # cooldown/charge meter, scaled to the chip's meter width
CD_METER_H = 6

# --- atlas regions: name -> (x, y, w, h) ---
REGIONS: dict[str, tuple[int, int, int, int]] = {
    # Bars: empty track and full fill, same footprint so a clipped blit lines up exactly.
    "hp_empty": (0, 0, BAR_W, BAR_H),
    "hp_full": (0, BAR_H, BAR_W, BAR_H),
    "ki_empty": (0, BAR_H * 2, BAR_W, BAR_H),
    "ki_full": (0, BAR_H * 3, BAR_W, BAR_H),
    "bar_frame": (0, BAR_H * 4, BAR_W, BAR_H),
    # Stamina segments.
    "stm_off": (0, BAR_H * 5, STM_SEG_W, STM_SEG_H),
    "stm_on": (STM_SEG_W, BAR_H * 5, STM_SEG_W, STM_SEG_H),
    # Critical-HP variant: swapped in below the low-health threshold so the bar shifts hot
    # rather than just shrinking. Same footprint so it drops in without layout changes.
    "hp_crit": (0, BAR_H * 6, BAR_W, BAR_H),
    # Brighter cap for the last lit stamina segment.
    "stm_tip": (STM_SEG_W * 2, BAR_H * 5, STM_SEG_W, STM_SEG_H),
    # Leading-edge cap for a partially filled bar. The gloss tip baked into the right end of
    # "*_full" is clipped away by any fill below 100%, so the cap has to be a separate sprite
    # the view stamps at the fill boundary instead.
    "bar_tip": (0, BAR_H * 7, BAR_TIP_W, BAR_H),
    # Portrait frame, and a 9-sliceable panel (16px corners) so the backing plate can cover
    # any cluster size without stretching its bevel.
    "portrait": (310, 0, PORTRAIT, PORTRAIT),
    "panel": (310, 68, PANEL, PANEL),
    # Sparking meter pips.
    "spark_off": (340, 128, 12, 12),
    "spark_on": (356, 128, 12, 12),

    # --- BT3 combat cooldown strip ---
    # Authored square rather than skewed: these are nine-sliced to arbitrary chip sizes, and a
    # baked lean cannot survive being stretched along one axis without shearing the bevel.
    # The three chip plates are neutral; the per-move accent colour is carried by the rail and
    # the meter fill, which are painted white and tinted at draw time.
    "cd_chip": (0, 210, CD_CHIP, CD_CHIP),
    "cd_chip_hot": (36, 210, CD_CHIP, CD_CHIP),
    "cd_chip_off": (72, 210, CD_CHIP, CD_CHIP),
    "cd_badge": (108, 210, CD_BADGE_W, CD_BADGE_H),
    "cd_rail": (108, 228, CD_RAIL_W, CD_RAIL_H),
    "cd_meter_track": (140, 210, CD_METER_W, CD_METER_H),
    "cd_meter_fill": (140, 218, CD_METER_W, CD_METER_H),
}

# XV2-ish palette: red HP, cyan KI, gold stamina, navy glass chrome.
PALETTE = {
    "hp_dark": (86, 14, 18),
    "hp_mid": (188, 34, 40),
    "hp_light": (240, 96, 92),
    "crit_dark": (120, 40, 8),
    "crit_mid": (230, 108, 24),
    "crit_light": (255, 196, 110),
    "ki_dark": (8, 46, 74),
    "ki_mid": (30, 140, 200),
    "ki_light": (128, 224, 255),
    "stm_dark": (58, 44, 10),
    "stm_mid": (198, 150, 32),
    "stm_light": (255, 226, 130),
    "track": (16, 20, 30),
    "track_light": (34, 42, 58),
    "chrome_dark": (12, 18, 30),
    "chrome_mid": (30, 42, 64),
    "chrome_light": (86, 112, 150),
    "accent": (104, 202, 230),
    "gloss": (255, 255, 255),
}
