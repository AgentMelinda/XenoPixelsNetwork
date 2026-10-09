#!/usr/bin/env python3
"""Generates PanelTexture.java (a 96-entry enum, one per generated PNG) directly
from panels_manifest.json, so the Java side can never drift from the actual
shipped files. Re-run this any time dmz_atlas_generator.py's base_specs change.
"""
import json

MOD_ID_PLACEHOLDER = "yourmodid"  # <-- change to your real mod id, or pass -Dmodid=

with open("panels_manifest.json") as f:
    manifest = json.load(f)

PALETTES = ["green", "blue", "red", "gold"]

lines = []
lines.append("package com.yourmod.client.gui.panels;")
lines.append("")
lines.append("import net.minecraft.client.gui.GuiGraphics;")
lines.append("import net.minecraft.resources.ResourceLocation;")
lines.append("")
lines.append("import java.util.HashMap;")
lines.append("import java.util.Map;")
lines.append("")
lines.append("/**")
lines.append(" * Auto-generated from panels_manifest.json — DO NOT hand-edit.")
lines.append(f" * {len(manifest)} entries ({len(manifest)//len(PALETTES)} shapes x {len(PALETTES)} palettes).")
lines.append(" * Regenerate with gen_java_enum.py whenever dmz_atlas_generator.py's")
lines.append(" * base_specs list changes.")
lines.append(" *")
lines.append(" * Drop the panels/*.png files into:")
lines.append(f" *   src/main/resources/assets/{MOD_ID_PLACEHOLDER}/textures/gui/panels/")
lines.append(" * (create the 'panels' folder if it doesn't exist)")
lines.append(" */")
lines.append("public enum PanelTexture {")
lines.append("")

entries = list(manifest.values())
for i, e in enumerate(entries):
    const_name = e["name"].upper()
    comma = "," if i < len(entries) - 1 else ";"
    lines.append(f'    {const_name}("{e["name"]}", {e["width"]}, {e["height"]}){comma}')

lines.append("")
lines.append("    public final String id;")
lines.append("    public final int width;")
lines.append("    public final int height;")
lines.append("    public final ResourceLocation texture;")
lines.append("")
lines.append("    PanelTexture(String id, int width, int height) {")
lines.append("        this.id = id;")
lines.append("        this.width = width;")
lines.append("        this.height = height;")
lines.append(f'        this.texture = ResourceLocation.fromNamespaceAndPath("{MOD_ID_PLACEHOLDER}", "textures/gui/panels/" + id + ".png");')
lines.append("    }")
lines.append("")
lines.append("    /**")
lines.append("     * Draws this panel at native size (1:1, no stretching — every file was")
lines.append("     * generated at its exact shipped size, so this is a straight full blit,")
lines.append("     * same as DragonMineZ's own menubig.png/menusmall.png blit calls).")
lines.append("     */")
lines.append("    public void blit(GuiGraphics graphics, int x, int y) {")
lines.append("        graphics.blit(texture, x, y, 0.0f, 0.0f, width, height, width, height);")
lines.append("    }")
lines.append("")
lines.append("    private static final Map<String, PanelTexture> BY_ID = new HashMap<>();")
lines.append("    static {")
lines.append("        for (PanelTexture p : values()) BY_ID.put(p.id, p);")
lines.append("    }")
lines.append("")
lines.append("    /** e.g. PanelTexture.of(\"mynpcs_button_row\", Palette.BLUE) */")
lines.append("    public static PanelTexture of(String shapeBaseName, Palette palette) {")
lines.append("        PanelTexture found = BY_ID.get(shapeBaseName + \"_\" + palette.id);")
lines.append("        if (found == null) {")
lines.append("            throw new IllegalArgumentException(")
lines.append('                "No panel texture \\"" + shapeBaseName + "_" + palette.id + "\\" (check the shape name is one of the *_green base names, no palette suffix)");')
lines.append("        }")
lines.append("        return found;")
lines.append("    }")
lines.append("")
lines.append("    public enum Palette {")
for i, p in enumerate(PALETTES):
    comma = "," if i < len(PALETTES) - 1 else ";"
    lines.append(f'        {p.upper()}("{p}"){comma}')
lines.append("        public final String id;")
lines.append("        Palette(String id) { this.id = id; }")
lines.append("    }")
lines.append("}")

out = "\n".join(lines) + "\n"
with open("PanelTexture.java", "w") as f:
    f.write(out)

print(f"Wrote PanelTexture.java ({len(entries)} enum constants, {out.count(chr(10))} lines)")
