package net.bullettrain.xenopixelsmod.mixin.compat.customnpcs;

import net.bullettrain.xenopixelsmod.client.compat.npc.gui.NpcWandPreview;
import net.minecraft.client.gui.GuiGraphics;
import noppes.npcs.client.gui.util.GuiNPCInterface2;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Parks the DMZ NPC visualizer on every CustomNPCs wand tab that uses {@link GuiNPCInterface2}.
 * Screens that already host a panel implement {@code NpcPreviewOwner} and are skipped.
 */
@Mixin(value = GuiNPCInterface2.class, remap = false)
public abstract class GuiNpcInterface2PreviewMixin {

    @Inject(method = "m_88315_", at = @At("RETURN"), require = 1)
    private void xenopixels$renderWandPreview(GuiGraphics graphics, int mouseX, int mouseY,
                                              float partialTick, CallbackInfo ci) {
        GuiNPCInterface2 screen = (GuiNPCInterface2) (Object) this;
        EntityNPCInterface npc = screen.npc;
        NpcWandPreview.render(screen, npc, graphics, screen.guiLeft, screen.guiTop, partialTick);
    }

    @Inject(method = "m_6375_", at = @At("HEAD"), cancellable = true, require = 1)
    private void xenopixels$clickWandPreview(double mouseX, double mouseY, int button,
                                             CallbackInfoReturnable<Boolean> cir) {
        GuiNPCInterface2 screen = (GuiNPCInterface2) (Object) this;
        if (NpcWandPreview.mouseClicked(screen, screen.npc, screen.guiLeft, screen.guiTop,
                mouseX, mouseY, button)) {
            cir.setReturnValue(true);
        }
    }
}
