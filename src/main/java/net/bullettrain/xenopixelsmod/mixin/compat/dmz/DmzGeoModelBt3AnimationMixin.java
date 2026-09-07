package net.bullettrain.xenopixelsmod.mixin.compat.dmz;

import com.dragonminez.client.model.DMZPlayerModel;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.combat.anim.Bt3AnimationBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.Animation;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.loading.object.BakedAnimations;
import software.bernie.geckolib.model.GeoModel;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Safety net: if GeckoLib's primary + DMZ fallback files do not contain a {@code combat.xeno_*}
 * clip, look it up in the file this mod already baked. GeckoLib loads every animation
 * JSON under {@code assets/<modid>/animations} at resource reload, so the clip is in
 * {@link GeckoLibCache} even when {@code getAnimationResourceFallbacks} never listed our file.
 */
@Mixin(value = GeoModel.class, remap = false)
public abstract class DmzGeoModelBt3AnimationMixin {

    @Unique
    private static final AtomicBoolean xeno$loggedMiss = new AtomicBoolean();

    @Unique
    private static final AtomicBoolean xeno$loggedHit = new AtomicBoolean();

    @Inject(
            method = "getAnimation(Lsoftware/bernie/geckolib/animatable/GeoAnimatable;Ljava/lang/String;)Lsoftware/bernie/geckolib/animation/Animation;",
            at = @At("RETURN"),
            cancellable = true)
    private void xeno$lookupBt3(GeoAnimatable animatable, String name,
                                CallbackInfoReturnable<Animation> cir) {
        if (cir.getReturnValue() != null || name == null || !name.startsWith("combat.xeno_")) {
            return;
        }
        if (!((Object) this instanceof DMZPlayerModel)) {
            return;
        }
        BakedAnimations baked = GeckoLibCache.getBakedAnimations()
                .get(Bt3AnimationBinding.DMZ_ANIMATION_FILE);
        if (baked == null) {
            if (xeno$loggedMiss.compareAndSet(false, true)) {
                XenoPixelsMod.LOGGER.warn(
                        "GeckoLib did not bake {} — combat.xeno_* clips cannot play",
                        Bt3AnimationBinding.DMZ_ANIMATION_FILE);
            }
            return;
        }
        Animation found = baked.getAnimation(name);
        if (found == null) {
            if (xeno$loggedMiss.compareAndSet(false, true)) {
                XenoPixelsMod.LOGGER.warn(
                        "Baked {} but it has no animation named {}",
                        Bt3AnimationBinding.DMZ_ANIMATION_FILE, name);
            }
            return;
        }
        if (xeno$loggedHit.compareAndSet(false, true)) {
            XenoPixelsMod.LOGGER.info("GeckoLib fallback lookup found {} in {}",
                    name, Bt3AnimationBinding.DMZ_ANIMATION_FILE);
        }
        cir.setReturnValue(found);
    }
}
