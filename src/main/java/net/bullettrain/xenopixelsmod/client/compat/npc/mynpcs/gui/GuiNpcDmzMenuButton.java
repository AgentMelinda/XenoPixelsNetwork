package net.bullettrain.xenopixelsmod.client.compat.npc.mynpcs.gui;

import net.minecraft.client.Minecraft;
import espi.mynpcs.entity.EntityNPCInterface;
import espi.mynpcs.shared.client.gui.components.GuiButtonNop;
import espi.mynpcs.shared.client.gui.components.GuiMenuTopButton;
import espi.mynpcs.shared.client.gui.components.GuiTextFieldNop;
import espi.mynpcs.shared.client.gui.listeners.IGuiInterface;

/**
 * Top-bar tab on the CustomNPCs wand editor. Opens {@link GuiNpcDmz} without a new
 * {@code EnumGuiType} (that enum is closed in CNPC).
 *
 * <p>The My NPCs twin of the CustomNPCs screen of the same name. These cannot be one class:
 * they extend {@code GuiNPCInterface2} and implement {@code ITextfieldListener}, and the two
 * mods ship those under different packages, so inheritance cannot be bridged reflectively
 * the way the rest of the NPC touchpoints are. Identical but for the types they name --
 * fix bugs in both.
 */
public final class GuiNpcDmzMenuButton extends GuiMenuTopButton {
    public static final int MENU_ID = 7;

    private final EntityNPCInterface npc;

    public GuiNpcDmzMenuButton(IGuiInterface gui, GuiButtonNop after, EntityNPCInterface npc) {
        super(gui, MENU_ID, after, "DMZ");
        this.npc = npc;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        GuiTextFieldNop.unfocus();
        if (this.gui != null) {
            this.gui.save();
        }
        if (npc != null) {
            Minecraft.getInstance().setScreen(new GuiNpcDmz(npc));
        }
    }
}
