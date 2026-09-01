package net.bullettrain.xenopixelsmod.mixin.client;

import com.dragonminez.client.gui.character.CharacterCustomizationScreen;
import com.dragonminez.client.gui.character.CharacterStatsScreen;
import com.dragonminez.client.gui.character.MinigamesScreen;
import com.dragonminez.client.gui.character.RaceSelectionScreen;
import com.dragonminez.client.gui.character.SkillsMenuScreen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Target: DMZ {@code renderPlayerModel} previews (Skills / stats / race / etc.)
 * Reason: those menus write {@code setYRot}/{@code setXRot} on the live local player every
 *         frame so the inventory preview faces the cursor. On LocalPlayer those fields are
 *         the gameplay camera. The skills KI tab (create custom technique) is an unblurred
 *         screen — the world keeps rendering — so the look snap reads as a shaky camera.
 * Version: NeoForge 1.21.1 / DMZ 2.1.x
 * Side: client. Body and head yaw are still posed; only look rotation is left alone.
 */
@Mixin(value = {
        SkillsMenuScreen.class,
        CharacterStatsScreen.class,
        RaceSelectionScreen.class,
        MinigamesScreen.class,
        CharacterCustomizationScreen.class
}, remap = false)
public abstract class DmzMenuPreviewLookMixin {

    @Redirect(
            method = "renderPlayerModel",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;setYRot(F)V", remap = true),
            require = 0
    )
    private void xenopixels$keepLookYaw(Entity player, float yaw) {
    }

    @Redirect(
            method = "renderPlayerModel",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;setYRot(F)V", remap = true),
            require = 0
    )
    private void xenopixels$keepLookYawLiving(LivingEntity player, float yaw) {
    }

    @Redirect(
            method = "renderPlayerModel",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;setXRot(F)V", remap = true),
            require = 0
    )
    private void xenopixels$keepLookPitch(Entity player, float pitch) {
    }

    @Redirect(
            method = "renderPlayerModel",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;setXRot(F)V", remap = true),
            require = 0
    )
    private void xenopixels$keepLookPitchLiving(LivingEntity player, float pitch) {
    }
}
