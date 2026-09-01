package net.bullettrain.xenopixelsmod.mixin.compat.customnpcs;

import net.bullettrain.xenopixelsmod.client.compat.npc.gui.GuiNpcDmzMenuButton;
import noppes.npcs.client.gui.util.GuiNpcMenu;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiMenuTopButton;
import noppes.npcs.shared.client.gui.listeners.IGuiInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Inserts a top-level DMZ tab on the wand editor (after Global, before Close).
 */
@Mixin(value = GuiNpcMenu.class, remap = false)
public abstract class GuiNpcMenuDmzTabMixin {
    @Shadow
    @Mutable
    private GuiMenuTopButton[] topButtons;
    @Shadow
    private int activeMenu;
    @Shadow
    private IGuiInterface parent;
    @Shadow
    private EntityNPCInterface npc;

    @Inject(method = "initGui", at = @At("RETURN"), require = 1)
    private void xenopixels$addDmzTab(int guiLeft, int guiTop, int imageWidth, CallbackInfo ci) {
        if (npc == null || parent == null || topButtons == null || topButtons.length == 0) {
            return;
        }
        GuiMenuTopButton global = null;
        int insertAt = -1;
        for (int i = 0; i < topButtons.length; i++) {
            GuiMenuTopButton button = topButtons[i];
            if (button == null) {
                continue;
            }
            if (button.id == GuiNpcDmzMenuButton.MENU_ID) {
                return;
            }
            if (button.id == 6) {
                global = button;
                insertAt = i + 1;
            }
        }
        if (global == null || insertAt < 0) {
            return;
        }
        GuiNpcDmzMenuButton dmz = new GuiNpcDmzMenuButton(parent, global, npc);
        dmz.active = activeMenu == GuiNpcDmzMenuButton.MENU_ID;

        GuiMenuTopButton[] next = new GuiMenuTopButton[topButtons.length + 1];
        System.arraycopy(topButtons, 0, next, 0, insertAt);
        next[insertAt] = dmz;
        System.arraycopy(topButtons, insertAt, next, insertAt + 1, topButtons.length - insertAt);
        topButtons = next;
    }
}
