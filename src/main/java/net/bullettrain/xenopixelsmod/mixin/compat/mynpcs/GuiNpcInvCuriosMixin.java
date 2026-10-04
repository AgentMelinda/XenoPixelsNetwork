package net.bullettrain.xenopixelsmod.mixin.compat.mynpcs;

import espi.mynpcs.client.gui.mainmenu.GuiNPCInv;
import espi.mynpcs.shared.client.gui.components.GuiButtonNop;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCuriosInventory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Curios block on the My NPCs inventory tab, and the button that shows it.
 *
 * <p>The block is hidden by default. It occupies x 108-186, y 8-98, which is where My NPCs draws
 * Min Exp (108,29), Max Exp (108,63) and the Normal/Auto button (88,88) — so while it was always on
 * it sat directly on top of them, and the panel has no free region of that size to move it to. A
 * toggle is what makes both sets of controls reachable without either being covered.
 *
 * <p>Toggling changes only {@link Slot#isActive()}, never the slot count. The count is fixed on
 * purpose so the server and client agree on menu indices even when the Curios capability is missing
 * on one side; adding or removing slots to hide them would desync the menu.
 */
@Mixin(value = GuiNPCInv.class, remap = false)
public abstract class GuiNpcInvCuriosMixin {

    /** Its own id space; My NPCs owns the low ids on this screen. */
    private static final int XENO_CURIOS_TOGGLE = 7301;

    @Inject(method = "init", at = @At("RETURN"), require = 1)
    private void xenopixels$addCuriosToggle(CallbackInfo ci) {
        GuiNPCInv screen = (GuiNPCInv) (Object) this;
        // The narrow strip between the Exp fields (which end at x 168) and the NPC Inventory label
        // (x 191) is the only part of this panel neither mod already claims.
        screen.addButton(new GuiButtonNop(screen, XENO_CURIOS_TOGGLE, 170, 88, 18, 20, "C",
                button -> NpcCuriosInventory.setSlotsVisible(!NpcCuriosInventory.slotsVisible())));
    }

    @Inject(method = "renderBg", at = @At("RETURN"), require = 1)
    private void xenopixels$paintCuriosWells(GuiGraphics graphics, float partialTick,
                                             int mouseX, int mouseY, CallbackInfo ci) {
        if (!NpcCuriosInventory.slotsVisible()) {
            return;
        }
        GuiNPCInv screen = (GuiNPCInv) (Object) this;
        var slots = screen.getMenu().slots;
        // Opaque backing first. The Exp fields and the Normal button are behind the block and are
        // drawn by My NPCs after this, so without something solid under the wells the two sets of
        // controls read as one jumbled panel.
        int left = screen.guiLeft + NpcCuriosInventory.START_X - 3;
        int top = screen.guiTop + NpcCuriosInventory.START_Y - 3;
        graphics.fill(left, top, left + 84, top + 96, 0xFF2B2B2B);
        for (int i = NpcCuriosInventory.ORIGINAL_SLOT_COUNT; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            int x = screen.guiLeft + slot.x;
            int y = screen.guiTop + slot.y;
            graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFFB98235);
            graphics.fill(x, y, x + 16, y + 16, 0xFF101218);
        }
    }
}
