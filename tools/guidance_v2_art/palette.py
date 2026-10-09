"""Void / neon fork palette. Not the stock gunmetal family."""
from __future__ import annotations

# Near-black indigo hull, saturated function colors, holo glass, heat only on thrust.
PALETTE = {
    "void_deep": (5, 3, 14),
    "void_hull": (12, 8, 28),
    "void_mid": (24, 16, 52),
    "void_edge": (40, 28, 78),
    "void_rim": (56, 40, 104),
    "chrome": (200, 210, 228),
    "chrome_mid": (138, 150, 174),
    "chrome_dark": (70, 78, 100),
    "cyan": (0, 236, 255),
    "cyan_mid": (0, 168, 214),
    "cyan_deep": (0, 58, 92),
    "magenta": (255, 40, 196),
    "magenta_mid": (196, 20, 140),
    "magenta_deep": (86, 6, 62),
    "amber": (255, 176, 24),
    "amber_hot": (255, 228, 148),
    "lime": (88, 255, 108),
    "lime_deep": (12, 72, 28),
    "holo": (36, 214, 226),
    "holo_dark": (2, 26, 38),
    "heat_core": (255, 252, 236),
    "heat_mid": (255, 132, 24),
    "heat_rim": (255, 56, 6),
    "warn": (255, 48, 72),
}

HULL = "hull"
DECK = "deck"
EMISSIVE = "emissive"
HEAT = "heat"
GLASS = "glass"
ALPHA = "alpha"
