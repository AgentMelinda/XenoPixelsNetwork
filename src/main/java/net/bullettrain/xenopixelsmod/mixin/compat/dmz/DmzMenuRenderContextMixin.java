package net.bullettrain.xenopixelsmod.mixin.compat.dmz;

import net.bullettrain.xenopixelsmod.client.hud.DmzMenuThemeState;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Brackets the six real DragonMineZ V-menu render passes with their Xeno theme. */
@Mixin(targets = {
        "com.dragonminez.client.gui.character.CharacterStatsScreen",
        "com.dragonminez.client.gui.character.SkillsMenuScreen",
        "com.dragonminez.client.gui.character.QuestTreeScreen",
        "com.dragonminez.client.gui.character.MinigamesScreen",
        "com.dragonminez.client.gui.character.PartyMenuScreen",
        "com.dragonminez.client.gui.character.ConfigMenuScreen"
}, remap = false)
public abstract class DmzMenuRenderContextMixin {

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void xenopixels$beginTheme(GuiGraphics graphics, int mouseX, int mouseY,
                                       float partialTick, CallbackInfo ci) {
        DmzMenuThemeState.begin(this);
    }

    @Inject(method = "render", at = @At("RETURN"), require = 0)
    private void xenopixels$endTheme(GuiGraphics graphics, int mouseX, int mouseY,
                                     float partialTick, CallbackInfo ci) {
        DmzMenuThemeState.end();
    }
}
