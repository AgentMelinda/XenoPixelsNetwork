package net.bullettrain.xenopixelsmod.mixin.compat.mynpcs;

import espi.mynpcs.client.gui.util.GuiNPCInterface2;
import espi.mynpcs.entity.EntityNPCInterface;
import net.bullettrain.xenopixelsmod.client.compat.npc.gui.NpcWandPreview;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GuiNPCInterface2.class, remap = false)
public abstract class GuiNpcInterface2PreviewMixin {

    @Inject(method = "render", at = @At("RETURN"), require = 1)
    private void xenopixels$renderWandPreview(GuiGraphics graphics, int mouseX, int mouseY,
                                              float partialTick, CallbackInfo ci) {
        GuiNPCInterface2 screen = (GuiNPCInterface2) (Object) this;
        EntityNPCInterface npc = screen.npc;
        NpcWandPreview.render(screen, npc, graphics, screen.guiLeft, screen.guiTop, partialTick);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 1)
    private void xenopixels$clickWandPreview(double mouseX, double mouseY, int button,
                                             CallbackInfoReturnable<Boolean> cir) {
        GuiNPCInterface2 screen = (GuiNPCInterface2) (Object) this;
        if (NpcWandPreview.mouseClicked(screen, screen.npc, screen.guiLeft, screen.guiTop,
                mouseX, mouseY, button)) {
            cir.setReturnValue(true);
        }
    }
}
