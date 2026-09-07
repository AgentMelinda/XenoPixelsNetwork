package net.bullettrain.xenopixelsmod.mixin.compat.dmz;

import com.dragonminez.client.model.DMZPlayerModel;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.combat.anim.Bt3AnimationBinding;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Adds this mod's animation file to the set GeckoLib searches for a DragonMineZ player animation.
 *
 * <p>GeckoLib 4.9.2 {@code GeoModel.getAnimation} calls
 * {@code getAnimationResourceFallbacks(GeoAnimatable)} — that is the synthetic bridge on
 * {@code DMZPlayerModel}. An earlier inject targeted only the {@code AbstractClientPlayer}
 * erasure; if that inject did not apply, every {@code combat.xeno_*} name resolved in
 * {@code CombatAnimationResolver} but GeckoLib never searched our file, so the attack controller
 * replayed the last successful punch. Both descriptors are hooked so either call path appends.
 */
@Mixin(value = DMZPlayerModel.class, remap = false)
public abstract class DmzPlayerModelAnimationFilesMixin {

    @Unique
    private static final AtomicBoolean xeno$logged = new AtomicBoolean();

    @Inject(
            method = {
                    "getAnimationResourceFallbacks(Lsoftware/bernie/geckolib/animatable/GeoAnimatable;)[Lnet/minecraft/resources/ResourceLocation;",
                    "getAnimationResourceFallbacks(Lnet/minecraft/client/player/AbstractClientPlayer;)[Lnet/minecraft/resources/ResourceLocation;"
            },
            at = @At("RETURN"),
            cancellable = true)
    private void xeno$appendBt3AnimationFile(CallbackInfoReturnable<ResourceLocation[]> cir) {
        ResourceLocation[] extended = Bt3AnimationBinding.withAnimationFile(cir.getReturnValue());
        cir.setReturnValue(extended);
        if (xeno$logged.compareAndSet(false, true)) {
            XenoPixelsMod.LOGGER.info("DMZ player model will also search {} ({} fallback files)",
                    Bt3AnimationBinding.DMZ_ANIMATION_FILE, extended.length);
        }
    }
}
