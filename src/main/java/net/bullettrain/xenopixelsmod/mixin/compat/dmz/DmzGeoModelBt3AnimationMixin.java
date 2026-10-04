package net.bullettrain.xenopixelsmod.mixin.compat.dmz;

import com.dragonminez.client.model.DMZPlayerModel;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.anim.StudioAnimLookup;
import net.bullettrain.xenopixelsmod.client.anim.XenoStudioClipCache;
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
 * Studio / library clips win over a shipped {@code combat.xeno_*} of the same name so an
 * in-game edit can play without rewriting {@code bt3_combat.animation.json}.
 *
 * <p>Lookup is at HEAD for {@code DMZPlayerModel} so GeckoLib never walks fallbacks for these
 * names. GeckoLib 4.9.2 throws if the last fallback is missing from its baked cache; appending
 * {@code bt3_combat.animation.json} there froze the render thread the first time Combat Brain
 * (or a player punch) asked for a {@code combat.xeno_*} clip. RETURN remains for other GeoModels
 * and for a miss that GeckoLib handled as null rather than a throw.
 */
@Mixin(value = GeoModel.class, remap = false)
public abstract class DmzGeoModelBt3AnimationMixin {

    @Unique
    private static final AtomicBoolean xeno$loggedMiss = new AtomicBoolean();

    @Unique
    private static final AtomicBoolean xeno$loggedHit = new AtomicBoolean();

    @Inject(
            method = "getAnimation(Lsoftware/bernie/geckolib/animatable/GeoAnimatable;Ljava/lang/String;)Lsoftware/bernie/geckolib/animation/Animation;",
            at = @At("HEAD"),
            cancellable = true)
    private void xeno$lookupBt3Head(GeoAnimatable animatable, String name,
                                    CallbackInfoReturnable<Animation> cir) {
        if (!((Object) this instanceof DMZPlayerModel)) {
            return;
        }
        Animation found = xeno$resolveCombatXeno(name);
        if (found != null) {
            cir.setReturnValue(found);
        }
    }

    @Inject(
            method = "getAnimation(Lsoftware/bernie/geckolib/animatable/GeoAnimatable;Ljava/lang/String;)Lsoftware/bernie/geckolib/animation/Animation;",
            at = @At("RETURN"),
            cancellable = true)
    private void xeno$lookupBt3(GeoAnimatable animatable, String name,
                                CallbackInfoReturnable<Animation> cir) {
        if (name == null || !name.startsWith("combat.xeno_")) {
            return;
        }
        Animation studio = XenoStudioClipCache.get(name);
        if (studio != null) {
            cir.setReturnValue(StudioAnimLookup.preferStudio(name, studio, cir.getReturnValue()));
            return;
        }
        if (cir.getReturnValue() != null) {
            return;
        }
        Animation found = xeno$resolveCombatXeno(name);
        if (found != null) {
            cir.setReturnValue(found);
        }
    }

    @Unique
    private Animation xeno$resolveCombatXeno(String name) {
        if (name == null || !name.startsWith("combat.xeno_")) {
            return null;
        }
        Animation studio = XenoStudioClipCache.get(name);
        BakedAnimations baked = GeckoLibCache.getBakedAnimations()
                .get(Bt3AnimationBinding.DMZ_ANIMATION_FILE);
        Animation shipped = baked == null ? null : baked.getAnimation(name);
        Animation chosen = StudioAnimLookup.preferStudio(name, studio, shipped);
        if (chosen != null) {
            if (xeno$loggedHit.compareAndSet(false, true)) {
                XenoPixelsMod.LOGGER.info("Resolved {} before GeckoLib fallbacks from {}",
                        name, Bt3AnimationBinding.DMZ_ANIMATION_FILE);
            }
            return chosen;
        }
        if (baked == null && xeno$loggedMiss.compareAndSet(false, true)) {
            XenoPixelsMod.LOGGER.warn(
                    "GeckoLib did not bake {} — combat.xeno_* clips use studio cache only",
                    Bt3AnimationBinding.DMZ_ANIMATION_FILE);
        }
        return null;
    }
}
