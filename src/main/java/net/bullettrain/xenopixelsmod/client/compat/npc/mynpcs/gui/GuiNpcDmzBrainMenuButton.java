package net.bullettrain.xenopixelsmod.client.compat.npc.mynpcs.gui;

import net.minecraft.client.Minecraft;
import espi.mynpcs.entity.EntityNPCInterface;
import espi.mynpcs.shared.client.gui.components.GuiButtonNop;
import espi.mynpcs.shared.client.gui.components.GuiMenuTopButton;
import espi.mynpcs.shared.client.gui.components.GuiTextFieldNop;
import espi.mynpcs.shared.client.gui.listeners.IGuiInterface;

/**
 * Top-bar Brain tab, inserted immediately after DMZ on the wand editor.
 *
 * <p>The My NPCs twin of the CustomNPCs button of the same name.
 */
public final class GuiNpcDmzBrainMenuButton extends GuiMenuTopButton {
    public static final int MENU_ID = 8;

    private final EntityNPCInterface npc;

    public GuiNpcDmzBrainMenuButton(IGuiInterface gui, GuiButtonNop after, EntityNPCInterface npc) {
        super(gui, MENU_ID, after, "Brain");
        this.npc = npc;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        GuiTextFieldNop.unfocus();
        if (this.gui != null) {
            this.gui.save();
        }
        if (npc != null) {
            Minecraft.getInstance().setScreen(new GuiNpcDmzBrain(npc,
                    net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile.read(npc),
                    GuiNpcDmzBrain.Origin.HUB));
        }
    }
}
