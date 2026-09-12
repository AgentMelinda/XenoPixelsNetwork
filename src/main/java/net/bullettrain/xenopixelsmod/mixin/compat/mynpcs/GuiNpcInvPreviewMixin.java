package net.bullettrain.xenopixelsmod.mixin.compat.mynpcs;

import espi.mynpcs.client.gui.mainmenu.GuiNPCInv;
import net.bullettrain.xenopixelsmod.client.compat.npc.gui.NpcWandPreview;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GuiNPCInv.class, remap = false)
public abstract class GuiNpcInvPreviewMixin {

    @Inject(method = "render", at = @At("RETURN"), require = 1)
    private void xenopixels$renderInvPreview(GuiGraphics graphics, int mouseX, int mouseY,
                                             float partialTick, CallbackInfo ci) {
        GuiNPCInv screen = (GuiNPCInv) (Object) this;
        NpcWandPreview.render(screen, screen.npc, graphics, screen.guiLeft, screen.guiTop, partialTick);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
    private void xenopixels$clickInvPreview(double mouseX, double mouseY, int button,
                                            CallbackInfoReturnable<Boolean> cir) {
        GuiNPCInv screen = (GuiNPCInv) (Object) this;
        if (NpcWandPreview.mouseClicked(screen, screen.npc, screen.guiLeft, screen.guiTop,
                mouseX, mouseY, button)) {
            cir.setReturnValue(true);
        }
    }
}
