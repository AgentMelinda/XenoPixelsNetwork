package net.bullettrain.xenopixelsmod.mixin.compat.customnpcs;

import net.bullettrain.xenopixelsmod.client.compat.npc.gui.NpcWandPreview;
import noppes.npcs.client.gui.util.GuiNPCInterface2;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Drag / release / scroll for the wand visualizer parked on {@link GuiNPCInterface2}. */
@Mixin(value = GuiBasic.class, remap = false)
public abstract class GuiBasicPreviewInputMixin {

    @Inject(method = "m_7979_", at = @At("HEAD"), cancellable = true, require = 1)
    private void xenopixels$dragWandPreview(double mouseX, double mouseY, int button,
                                            double dragX, double dragY,
                                            CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof GuiNPCInterface2 screen)) return;
        if (NpcWandPreview.mouseDragged(screen, screen.npc, mouseX, mouseY, button, dragX, dragY)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "m_6348_", at = @At("HEAD"), cancellable = true, require = 1)
    private void xenopixels$releaseWandPreview(double mouseX, double mouseY, int button,
                                               CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof GuiNPCInterface2 screen)) return;
        if (NpcWandPreview.mouseReleased(screen, screen.npc, button)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "m_6050_", at = @At("HEAD"), cancellable = true, require = 1)
    private void xenopixels$scrollWandPreview(double mouseX, double mouseY, double scrollY,
                                              CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof GuiNPCInterface2 screen)) return;
        if (NpcWandPreview.mouseScrolled(screen, screen.npc, screen.guiLeft, screen.guiTop,
                mouseX, mouseY, scrollY)) {
            cir.setReturnValue(true);
        }
    }
}
