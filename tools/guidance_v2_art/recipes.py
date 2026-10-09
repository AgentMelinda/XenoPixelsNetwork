"""Dest jobs. Dest names must end in _fork or guidance_v2_."""
from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BLOCK = ROOT / "src/main/resources/assets/xenopixelsmod/textures/block"
ITEM = ROOT / "src/main/resources/assets/xenopixelsmod/textures/item"
GUI = ROOT / "src/main/resources/assets/xenopixelsmod/textures/gui"
LOOKDEV = ROOT / "tools/generated/guidance_v2_lookdev"

SIZE = 128
SCALE = 1  # painters already emit 128
LOOKDEV_SCALE = 2  # 128 → 256 lookdev sheets


@dataclass(frozen=True)
class Recipe:
    dest: Path
    painter: str


def _block(name: str, painter: str) -> Recipe:
    return Recipe(BLOCK / f"{name}.png", painter)


def _item(name: str, painter: str) -> Recipe:
    return Recipe(ITEM / f"{name}.png", painter)


RECIPES: tuple[Recipe, ...] = (
    _block("ship_vls_guidance_front_fork", "guidance_front"),
    _block("ship_vls_guidance_side_fork", "guidance_side"),
    _block("ship_vls_guidance_top_fork", "guidance_top"),
    _block("ship_vls_guidance_fork", "guidance"),
    _block("ship_thruster_front_fork", "thruster_front"),
    _block("ship_thruster_front_on_fork", "thruster_front_on"),
    _block("ship_thruster_back_fork", "thruster_back"),
    _block("ship_thruster_side_fork", "thruster_side"),
    _block("ship_thruster_fork", "thruster"),
    _block("ship_thruster_on_fork", "thruster_on"),
    _block("missile_tube_top_fork", "tube_top"),
    _block("missile_tube_side_fork", "tube_side"),
    _block("missile_tube_bottom_fork", "tube_bottom"),
    _block("missile_tube_fork", "tube"),
    _block("missile_chunk_loader_front_fork", "loader_front"),
    _block("missile_chunk_loader_side_fork", "loader_side"),
    _block("missile_chunk_loader_top_fork", "loader_top"),
    _block("missile_chunk_loader_fork", "loader"),
    _block("wing_panel_fork", "wing_panel"),
    _block("wing_flap_horizontal_fork", "wing_flap_h"),
    _block("wing_flap_vertical_fork", "wing_flap_v"),
    _block("copycat_wing_panel_fork", "copycat"),
    _block("copycat_wing_panel_lit_fork", "copycat_lit"),
    _block("copycat_wing_flap_horizontal_fork", "copycat"),
    _block("copycat_wing_flap_horizontal_lit_fork", "copycat_lit"),
    _block("copycat_wing_flap_vertical_fork", "copycat"),
    _block("copycat_wing_flap_vertical_lit_fork", "copycat_lit"),
    _item("target_tool_fork", "target_tool"),
    _item("panel_configurator_fork", "panel_configurator"),
    _block("pilot_seat_frame_fork", "seat_frame"),
    _block("pilot_seat_cushion_fork", "seat_cushion"),
)

ATLAS_DEST = GUI / "guidance_v2_atlas.png"
GECKO_DEST = BLOCK / "flight_controller_fork.png"
GECKO_GLOW_DEST = BLOCK / "flight_controller_glowmask_fork.png"


def dest_ok(path: Path) -> bool:
    stem = path.stem
    return stem.endswith("_fork") or stem.startswith("guidance_v2")
