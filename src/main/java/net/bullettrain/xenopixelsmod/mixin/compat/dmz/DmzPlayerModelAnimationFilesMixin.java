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
import software.bernie.geckolib.cache.GeckoLibCache;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Adds this mod's animation file to the set GeckoLib searches for a DragonMineZ player animation,
 * but only when GeckoLib has already baked that file.
 *
 * <p>GeckoLib 4.9.2 {@code GeoModel.getAnimation} throws if the last fallback is missing from
 * {@code GeckoLibCache}. {@code combat.xeno_*} clips are resolved at HEAD in
 * {@link DmzGeoModelBt3AnimationMixin} instead, so an unbaked {@code bt3_combat.animation.json}
 * must never be advertised here. Both descriptors are hooked so either call path stays in sync.
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
        boolean baked = xeno$bt3FileBaked();
        ResourceLocation[] current = cir.getReturnValue();
        ResourceLocation[] extended = Bt3AnimationBinding.withAnimationFileIfBaked(current, baked);
        if (extended != current) {
            cir.setReturnValue(extended);
        }
        if (xeno$logged.compareAndSet(false, true)) {
            if (baked) {
                XenoPixelsMod.LOGGER.info("DMZ player model will also search {} ({} fallback files)",
                        Bt3AnimationBinding.DMZ_ANIMATION_FILE, extended.length);
            } else {
                XenoPixelsMod.LOGGER.info(
                        "Skipping unbaked {} as a GeckoLib fallback; combat.xeno_* uses GeoModel HEAD lookup",
                        Bt3AnimationBinding.DMZ_ANIMATION_FILE);
            }
        }
    }

    @Unique
    private static boolean xeno$bt3FileBaked() {
        try {
            return GeckoLibCache.getBakedAnimations().get(Bt3AnimationBinding.DMZ_ANIMATION_FILE) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
