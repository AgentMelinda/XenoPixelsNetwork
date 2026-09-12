package net.bullettrain.xenopixelsmod.mixin.compat.customnpcs;

import net.bullettrain.xenopixelsmod.compat.npc.NpcCuriosInventory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.Slot;
import noppes.npcs.client.gui.mainmenu.GuiNPCInv;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Paints wells for the extra Curios slots {@link ContainerNpcInvCuriosMixin} appended. */
@Mixin(value = GuiNPCInv.class, remap = false)
public abstract class GuiNpcInvCuriosMixin {

    @Inject(method = "m_7286_", at = @At("RETURN"), require = 1)
    private void xenopixels$paintCuriosWells(GuiGraphics graphics, float partialTick,
                                             int mouseX, int mouseY, CallbackInfo ci) {
        GuiNPCInv screen = (GuiNPCInv) (Object) this;
        var slots = screen.getMenu().slots;
        for (int i = NpcCuriosInventory.ORIGINAL_SLOT_COUNT; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            int x = screen.guiLeft + slot.x;
            int y = screen.guiTop + slot.y;
            graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFFB98235);
            graphics.fill(x, y, x + 16, y + 16, 0xFF101218);
        }
    }
}
