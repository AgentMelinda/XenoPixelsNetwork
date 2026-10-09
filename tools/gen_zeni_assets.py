"""Generate the native Zeni cash item sprites and item-model JSON.

All artwork is deterministic and authored from shapes, with the NPC Wand's dark metal,
gold, and cyan palette. Run from the repository root with Python and Pillow installed.
"""

from __future__ import annotations

import json
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/generated/resources/assets/xenopixelsmod"
TEXTURES = ASSETS / "textures/item"
MODELS = ASSETS / "models/item"
FONT = ROOT / "assets/customnpcs/opensans.ttf"
SIZE = 256

COINS = (1, 10, 25, 50, 100, 200, 250, 500, 1000)
NOTES = (10_000, 100_000, 1_000_000)


def font(size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(str(FONT), size)


def centered(draw: ImageDraw.ImageDraw, y: int, text: str, face, fill, *, stroke=0):
    box = draw.textbbox((0, 0), text, font=face, stroke_width=stroke)
    x = (SIZE - (box[2] - box[0])) // 2 - box[0]
    draw.text((x, y), text, font=face, fill=fill, stroke_width=stroke,
              stroke_fill=(4, 12, 24, 255))


def coin(value: int) -> Image.Image:
    image = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    pixels = image.load()
    # A broad bevel, small facets, and offset light give depth at inventory scale.
    if value <= 50:
        metal = (182, 101, 40)
    elif value <= 250:
        metal = (171, 188, 202)
    else:
        metal = (242, 179, 49)
    for y in range(20, 236):
        for x in range(20, 236):
            dx, dy = x - 128, y - 128
            radius = math.hypot(dx, dy)
            if radius > 108:
                continue
            light = max(0.0, (-(dx + dy) / 153.0))
            bevel = 0.44 if radius > 99 else (0.74 if radius > 91 else 1.0)
            facet = 0.96 + 0.045 * math.sin(math.atan2(dy, dx) * 16)
            shade = (0.52 + 0.35 * light) * bevel * facet
            pixels[x, y] = tuple(min(255, int(c * shade + 22)) for c in metal) + (255,)
    draw = ImageDraw.Draw(image)
    draw.ellipse((20, 20, 235, 235), outline=(8, 16, 27, 255), width=7)
    draw.arc((28, 28, 227, 227), 188, 322, fill=(255, 241, 163, 255), width=6)
    draw.arc((28, 28, 227, 227), 10, 146, fill=(50, 33, 24, 255), width=5)
    draw.ellipse((44, 44, 211, 211), outline=(17, 28, 39, 255), width=7)
    draw.ellipse((53, 53, 202, 202), outline=(255, 215, 75, 255), width=3)
    # Cyan gem facets echo the Wand instead of reproducing a copyrighted emblem.
    draw.polygon([(128, 52), (139, 65), (128, 78), (117, 65)],
                 fill=(11, 231, 245, 255), outline=(6, 28, 50, 255))
    draw.polygon([(128, 178), (139, 191), (128, 204), (117, 191)],
                 fill=(11, 231, 245, 255), outline=(6, 28, 50, 255))
    centered(draw, 77, "Z", font(79), (245, 222, 92, 255), stroke=3)
    centered(draw, 153, str(value), font(29 if value < 1000 else 25),
             (226, 252, 255, 255), stroke=2)
    return image


def note(value: int) -> Image.Image:
    image = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    if value == 10_000:
        fill, edge = (8, 59, 86, 255), (31, 213, 239, 255)
    elif value == 100_000:
        fill, edge = (24, 37, 99, 255), (104, 178, 255, 255)
    else:
        fill, edge = (63, 29, 88, 255), (246, 176, 70, 255)
    draw.rounded_rectangle((11, 45, 244, 212), radius=18,
                           fill=(2, 9, 20, 255), outline=(2, 7, 14, 255), width=4)
    draw.rounded_rectangle((17, 49, 238, 205), radius=13, fill=fill, outline=edge, width=5)
    draw.rounded_rectangle((25, 57, 230, 197), radius=10,
                           outline=(226, 235, 239, 255), width=2)
    for offset in range(5):
        color = (20 + offset * 6, 91 + offset * 7, 116 + offset * 7, 255)
        draw.line([(36 + offset * 10, 72), (36 + offset * 10, 189)], fill=color, width=1)
        draw.line([(151 + offset * 10, 72), (151 + offset * 10, 189)], fill=color, width=1)
    draw.polygon([(127, 72), (174, 127), (127, 185), (80, 127)],
                 fill=(4, 16, 34, 255), outline=edge, width=4)
    draw.polygon([(127, 85), (163, 127), (127, 171), (91, 127)],
                 fill=(16, 60, 80, 255), outline=(247, 190, 54, 255), width=2)
    centered(draw, 85, "Z", font(67), (249, 215, 88, 255), stroke=3)
    label = f"{value:,}"
    draw.text((33, 65), "ZENI", font=font(19), fill=(230, 242, 253, 255))
    draw.text((34, 164), label, font=font(19 if value < 1_000_000 else 16),
              fill=(244, 226, 136, 255))
    draw.text((179, 164), "Z", font=font(20), fill=(244, 226, 136, 255))
    return image


def main() -> None:
    TEXTURES.mkdir(parents=True, exist_ok=True)
    MODELS.mkdir(parents=True, exist_ok=True)
    for value in COINS + NOTES:
        name = f"zeni_{value}" if value in COINS else f"zeni_note_{value}"
        art = coin(value) if value in COINS else note(value)
        art.save(TEXTURES / f"{name}.png", optimize=True)
        model = {"parent": "minecraft:item/generated",
                 "textures": {"layer0": f"xenopixelsmod:item/{name}"}}
        (MODELS / f"{name}.json").write_text(json.dumps(model, indent=2) + "\n",
                                               encoding="utf-8")


if __name__ == "__main__":
    main()
