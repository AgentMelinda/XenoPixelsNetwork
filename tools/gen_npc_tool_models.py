"""Generate true cuboid NPC tool item models and their shared neon material atlas.

Run from the repository root: python tools/gen_npc_tool_models.py
The source PNG silhouettes remain intact; these models are separate 3D geometry.
"""

from pathlib import Path
import json
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "src/generated/resources/assets/xenopixelsmod"
TEXTURES = BASE / "textures/item"
MODELS = BASE / "models/item"

COLORS = [
    (23, 28, 40), (76, 84, 104), (16, 224, 239), (248, 171, 24),
    (178, 229, 243), (16, 78, 143), (232, 66, 77), (41, 170, 193),
]


def material_atlas():
    # Four by four material swatches at 128 px each. UVs stay in Minecraft's 0..16 space.
    # The extra pixels preserve the steel bevels and cyan glints at higher UI scale.
    tile = 128
    image = Image.new("RGBA", (tile * 4, tile * 4), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    for idx, rgb in enumerate(COLORS):
        x, y = (idx % 4) * tile, (idx // 4) * tile
        dark = tuple(max(0, int(c * .54)) for c in rgb)
        light = tuple(min(255, int(c * 1.28)) for c in rgb)
        draw.rectangle((x, y, x + 127, y + 127), fill=(*dark, 255))
        draw.rectangle((x + 10, y + 10, x + 117, y + 117), fill=(*rgb, 255))
        draw.polygon(((x + 10, y + 10), (x + 117, y + 10),
                      (x + 83, y + 35), (x + 10, y + 53)), fill=(*light, 255))
        draw.line((x + 11, y + 12, x + 116, y + 12), fill=(*light, 255), width=5)
        draw.line((x + 12, y + 13, x + 12, y + 116), fill=(*light, 255), width=5)
        draw.line((x + 116, y + 115, x + 116, y + 28), fill=(*dark, 255), width=6)
        draw.line((x + 20, y + 116, x + 115, y + 116), fill=(*dark, 255), width=6)
        if idx in (2, 3, 4, 7):
            draw.line((x + 31, y + 36, x + 48, y + 22), fill=(255, 255, 255, 190), width=5)
    TEXTURES.mkdir(parents=True, exist_ok=True)
    image.save(TEXTURES / "xeno_npc_tool_materials.png")


def cube(lo, hi, material):
    u = (material % 4) * 4
    v = (material // 4) * 4
    face = {"texture": "#palette", "uv": [u, v, u + 4, v + 4]}
    return {"from": lo, "to": hi, "faces": {side: face for side in
            ("north", "south", "east", "west", "up", "down")}}


def build(name, boxes):
    model = {
        "textures": {"palette": "xenopixelsmod:item/xeno_npc_tool_materials",
                     "particle": "xenopixelsmod:item/xeno_npc_tool_materials"},
        "elements": [cube(*box) for box in boxes],
        "display": {
            "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [.92, .92, .92]},
            "ground": {"translation": [0, 2, 0], "scale": [.58, .58, .58]},
            "fixed": {"rotation": [0, 180, 0], "scale": [.75, .75, .75]},
            "thirdperson_righthand": {"rotation": [0, 90, -35], "translation": [0, 2, 1],
                                       "scale": [.85, .85, .85]},
            "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.1, 3.2, 1.2],
                                       "scale": [.82, .82, .82]},
            "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.1, 3.2, 1.2],
                                      "scale": [.82, .82, .82]},
        },
    }
    MODELS.mkdir(parents=True, exist_ok=True)
    (MODELS / f"{name}.json").write_text(json.dumps(model, indent=2) + "\n", encoding="utf-8")


def main():
    material_atlas()
    # 0 obsidian, 1 steel, 2 neon cyan, 3 gold, 4 ice-white, 5 navy, 6 red, 7 glass-blue.
    # Each piece occupies real volume, including the shafts, bezels, grips and cores.
    build("xeno_npc_wand", [
        ([7, 0, 7], [9, 11, 9], 0), ([6.5, 1, 6.5], [9.5, 2, 9.5], 3),
        ([6.5, 4, 6.5], [9.5, 5, 9.5], 1), ([6.5, 9, 6.5], [9.5, 10, 9.5], 3),
        ([4, 10, 7], [12, 11.5, 9], 1), ([4, 10.8, 6.6], [5, 14, 9.4], 0),
        ([11, 10.8, 6.6], [12, 14, 9.4], 0), ([5, 13, 7], [6, 15, 9], 3),
        ([10, 13, 7], [11, 15, 9], 3), ([6, 11, 6.3], [10, 16, 9.7], 0),
        ([6.7, 11.7, 5.8], [9.3, 15.3, 10.2], 2), ([7.2, 12.2, 5.5], [8.1, 14.7, 5.8], 4),
    ])
    build("xeno_npc_path_tool", [
        ([7, 0, 7], [9, 11, 9], 0), ([6.6, 1, 6.6], [9.4, 2, 9.4], 3),
        ([6.7, 5, 6.7], [9.3, 6, 9.3], 1), ([5, 10, 6.4], [11, 12, 9.6], 3),
        ([6, 11, 6], [10, 15, 10], 0), ([6.8, 11.8, 5.4], [9.2, 14.2, 10.6], 2),
        ([7.3, 12.3, 5.1], [8.1, 13.8, 5.4], 4), ([7, 15, 7], [9, 16, 9], 3),
    ])
    build("xeno_npc_cloner", [
        ([4, 1, 6], [12, 15, 10], 0), ([5, 2, 5.5], [11, 4, 6], 3),
        ([5, 5, 5.3], [11, 12, 6], 1), ([5.5, 5.5, 4.9], [10.5, 11.5, 5.3], 2),
        ([4.5, 13, 5.5], [11.5, 14, 10.5], 3), ([7, 14, 7], [9, 16, 9], 2),
        ([6, 2, 5], [7, 3, 5.5], 2), ([9, 2, 5], [10, 3, 5.5], 2),
    ])
    build("xeno_npc_jar", [
        ([4, 0, 4], [12, 2, 12], 0), ([5, 2, 5], [11, 12, 11], 7),
        ([4, 11, 4], [12, 13, 12], 3), ([5, 13, 5], [11, 15, 11], 0),
        ([6, 4, 4.6], [10, 9, 5], 2), ([7, 5, 4.2], [8, 8, 4.6], 4),
        ([6, 14, 6], [10, 16, 10], 1),
    ])
    build("xeno_npc_mounter", [
        ([7, 0, 7], [9, 10, 9], 0), ([6.5, 1, 6.5], [9.5, 2, 9.5], 3),
        ([5, 9, 6], [11, 11, 10], 1), ([4, 10, 6], [6, 15, 10], 0),
        ([10, 10, 6], [12, 15, 10], 0), ([4, 14, 6], [6, 16, 10], 3),
        ([10, 14, 6], [12, 16, 10], 3), ([6, 10, 7], [10, 13, 9], 2),
        ([7, 11, 6], [9, 12, 7], 4),
    ])
    build("xeno_npc_teleporter", [
        ([7, 0, 7], [9, 6, 9], 0), ([6, 1, 6], [10, 2, 10], 3),
        ([3, 5, 6], [13, 7, 10], 0), ([3, 13, 6], [13, 15, 10], 0),
        ([3, 6, 6], [5, 14, 10], 0), ([11, 6, 6], [13, 14, 10], 0),
        ([5, 7, 7], [11, 13, 9], 5), ([6, 8, 6], [10, 12, 10], 2),
        ([7, 9, 5.5], [8, 11, 6], 4),
        ([4, 13.7, 5.5], [12, 14.3, 10.5], 3),
    ])
    # The scripting tool: the wand's shaft carrying a floating script screen instead of a crown -
    # navy bezel, glass-blue display, neon code lines, an ice-white caret, and a gold stylus tip.
    build("xeno_npc_script_tool", [
        ([7, 0, 7], [9, 11, 9], 0), ([6.5, 1, 6.5], [9.5, 2, 9.5], 3),
        ([6.5, 4, 6.5], [9.5, 5, 9.5], 1), ([6.5, 9, 6.5], [9.5, 10, 9.5], 3),
        ([5.5, 10, 6.6], [10.5, 11, 9.4], 1),
        ([6, 11, 6.8], [10, 15.6, 9.2], 5),
        ([6.4, 11.5, 6.4], [9.6, 15.2, 6.8], 7),
        ([6.8, 14.3, 6.1], [8.8, 14.8, 6.4], 2),
        ([6.8, 13.0, 6.1], [9.2, 13.5, 6.4], 2),
        ([6.8, 11.8, 6.1], [7.6, 12.3, 6.4], 4),
        ([7.5, 15.6, 7.5], [8.5, 16, 8.5], 3),
    ])
    script_tool_icon()


def script_tool_icon():
    # 16x16 inventory icon, drawn at the size from the same palette as the 3D atlas. The other
    # tools' icons predate this script; this one is generated so the tool stays reproducible.
    obsidian, steel, cyan, gold, ice, navy, red, glass = COLORS
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.line((3, 14, 9, 8), fill=(*obsidian, 255), width=2)
    draw.line((4, 12, 5, 11), fill=(*gold, 255))
    draw.line((7, 10, 8, 9), fill=(*steel, 255))
    draw.rectangle((8, 1, 14, 8), fill=(*navy, 255))
    draw.rectangle((9, 2, 13, 7), fill=(*glass, 255))
    draw.line((10, 3, 12, 3), fill=(*cyan, 255))
    draw.line((10, 5, 13, 5), fill=(*cyan, 255))
    draw.point((10, 6), fill=(*ice, 255))
    draw.rectangle((10, 0, 12, 1), fill=(*gold, 255))
    (ROOT / "src/main/resources/assets/xenopixelsmod/textures/item"
     ).mkdir(parents=True, exist_ok=True)
    image.save(ROOT / "src/main/resources/assets/xenopixelsmod/textures/item"
               / "xeno_npc_script_tool.png")


if __name__ == "__main__":
    main()
