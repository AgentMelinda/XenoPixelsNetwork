package net.bullettrain.xenopixelsmod.client.compat.npc.gui;

import net.minecraft.client.Minecraft;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiMenuTopButton;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.IGuiInterface;

/**
 * Top-bar tab on the CustomNPCs wand editor. Opens {@link GuiNpcDmz} without a new
 * {@code EnumGuiType} (that enum is closed in CNPC).
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
