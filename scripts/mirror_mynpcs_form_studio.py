"""Regenerate the MyNPCs form-studio screens from their CustomNPCs originals.

The two NPC mods expose the same GUI toolkit under different packages, so the MyNPCs copies
differ only in the package declaration and the noppes -> espi import swap.
"""
import pathlib, sys

SRC = pathlib.Path("src/main/java/net/bullettrain/xenopixelsmod/client/compat/npc/gui")
DST = pathlib.Path("src/main/java/net/bullettrain/xenopixelsmod/client/compat/npc/mynpcs/gui")
FILES = ["GuiNpcDmzForms.java", "GuiNpcDmzFormEditor.java", "GuiNpcDmzFormAuraPreview.java",
         "GuiNpcDmzTrainerPicker.java", "GuiNpcDmz.java", "GuiNpcDmzStack.java",
         "GuiNpcDmzSkills.java", "GuiNpcDmzSkillMaster.java"]

for name in FILES:
    text = (SRC / name).read_text(encoding="utf-8")
    text = text.replace(
        "package net.bullettrain.xenopixelsmod.client.compat.npc.gui;",
        "package net.bullettrain.xenopixelsmod.client.compat.npc.mynpcs.gui;")
    text = text.replace("noppes.npcs.client.gui.", "espi.mynpcs.client.gui.")
    text = text.replace("noppes.npcs.entity.", "espi.mynpcs.entity.")
    text = text.replace("noppes.npcs.shared.client.gui.", "espi.mynpcs.shared.client.gui.")
    # NpcPreviewPanel / NpcPreviewOwner are deliberately shared by both trees (they only touch
    # neutral types), so they live in the CustomNPCs package and the My NPCs copies import them.
    extra = []
    if "NpcPreviewPanel" in text and "import net.bullettrain.xenopixelsmod.client.compat.npc.gui.NpcPreviewPanel;" not in text:
        extra.append("import net.bullettrain.xenopixelsmod.client.compat.npc.gui.NpcPreviewPanel;\n")
    if "NpcPreviewOwner" in text and "import net.bullettrain.xenopixelsmod.client.compat.npc.gui.NpcPreviewOwner;" not in text:
        extra.append("import net.bullettrain.xenopixelsmod.client.compat.npc.gui.NpcPreviewOwner;\n")
    if extra:
        text = text.replace(
            "package net.bullettrain.xenopixelsmod.client.compat.npc.mynpcs.gui;\n",
            "package net.bullettrain.xenopixelsmod.client.compat.npc.mynpcs.gui;\n\n" + "".join(extra),
            1)
    if "noppes" in text:
        sys.exit("unmapped noppes reference left in " + name)
    (DST / name).write_text(text, encoding="utf-8", newline="\n")
    print("mirrored", name)
