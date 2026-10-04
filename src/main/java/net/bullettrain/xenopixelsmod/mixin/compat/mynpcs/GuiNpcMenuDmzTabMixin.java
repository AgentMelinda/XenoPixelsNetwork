package net.bullettrain.xenopixelsmod.mixin.compat.mynpcs;

import net.bullettrain.xenopixelsmod.client.compat.npc.mynpcs.gui.GuiNpcDmzBrainMenuButton;
import net.bullettrain.xenopixelsmod.client.compat.npc.mynpcs.gui.GuiNpcDmzMenuButton;
import espi.mynpcs.client.gui.util.GuiNpcMenu;
import espi.mynpcs.entity.EntityNPCInterface;
import espi.mynpcs.shared.client.gui.components.GuiMenuTopButton;
import espi.mynpcs.shared.client.gui.listeners.IGuiInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Inserts top-level DMZ and Brain tabs on the wand editor (after Global, before Close).
 *
 * <p>The My NPCs twin of the CustomNPCs mixin of the same name. My NPCs is CustomNPCs with
 * its root package renamed, so the two are identical but for the types they name; this one
 * is gated on the {@code mynpcs} mod id and its twin on {@code customnpcs}, so exactly one
 * applies. Fix bugs in both.
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
        GuiMenuTopButton dmzButton = null;
        boolean hasBrain = false;
        int insertAfterGlobal = -1;
        int insertAfterDmz = -1;
        for (int i = 0; i < topButtons.length; i++) {
            GuiMenuTopButton button = topButtons[i];
            if (button == null) {
                continue;
            }
            if (button.id == GuiNpcDmzMenuButton.MENU_ID) {
                dmzButton = button;
                insertAfterDmz = i + 1;
            } else if (button.id == GuiNpcDmzBrainMenuButton.MENU_ID) {
                hasBrain = true;
            } else if (button.id == 6) {
                global = button;
                insertAfterGlobal = i + 1;
            }
        }
        if (dmzButton == null) {
            if (global == null || insertAfterGlobal < 0) {
                return;
            }
            GuiNpcDmzMenuButton dmz = new GuiNpcDmzMenuButton(parent, global, npc);
            dmz.active = activeMenu == GuiNpcDmzMenuButton.MENU_ID;
            GuiMenuTopButton[] withDmz = new GuiMenuTopButton[topButtons.length + 1];
            System.arraycopy(topButtons, 0, withDmz, 0, insertAfterGlobal);
            withDmz[insertAfterGlobal] = dmz;
            System.arraycopy(topButtons, insertAfterGlobal, withDmz, insertAfterGlobal + 1,
                    topButtons.length - insertAfterGlobal);
            topButtons = withDmz;
            dmzButton = dmz;
            insertAfterDmz = insertAfterGlobal + 1;
        }
        if (hasBrain || dmzButton == null || insertAfterDmz < 0) {
            return;
        }
        GuiNpcDmzBrainMenuButton brain = new GuiNpcDmzBrainMenuButton(parent, dmzButton, npc);
        brain.active = activeMenu == GuiNpcDmzBrainMenuButton.MENU_ID;
        GuiMenuTopButton[] withBrain = new GuiMenuTopButton[topButtons.length + 1];
        System.arraycopy(topButtons, 0, withBrain, 0, insertAfterDmz);
        withBrain[insertAfterDmz] = brain;
        System.arraycopy(topButtons, insertAfterDmz, withBrain, insertAfterDmz + 1,
                topButtons.length - insertAfterDmz);
        topButtons = withBrain;
    }
}
