package net.bullettrain.xenopixelsmod.mixin.compat.dmz;

import com.dragonminez.client.animation.CombatAnimationResolver;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.combat.anim.Bt3AnimationBinding;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/**
 * Teaches DragonMineZ's animation resolver the names of the BT3 melee animations this mod ships.
 *
 * <p>{@code CombatAnimationResolver.reload} fills {@code AVAILABLE_RAW} from exactly one file,
 * {@code dragonminez:animations/entity/races/combat.animation.json}, and {@code toPlayableKey}
 * returns {@code ""} for any name that is not in that set. An empty result makes
 * {@code dragonminez$playMeleeAnimation} fall back to {@code "fallback"}, so without this every
 * XenoPixels animation would silently resolve to nothing no matter where the file lived.
 *
 * <p>Adding to the set rather than replacing DMZ's file keeps their 34 animations authoritative and
 * survives DMZ updating them - the alternative, shipping an overriding copy of their file, would
 * freeze whatever version we copied.
 */
@Mixin(value = CombatAnimationResolver.class, remap = false)
public abstract class DmzCombatAnimationRegistryMixin {

    @Shadow
    @Final
    private static Set<String> AVAILABLE_RAW;

    /**
     * Runs after every return from {@code reload}, including the early return taken when the base
     * file parsed successfully, so the names are registered on each resource reload rather than
     * only the first.
     */
    @Inject(method = "reload", at = @At("RETURN"))
    private static void xeno$registerBt3Animations(ResourceManager resourceManager, CallbackInfo ci) {
        Set<String> names = Bt3AnimationBinding.customAnimationNames();
        if (names.isEmpty() || AVAILABLE_RAW == null) {
            return;
        }
        if (AVAILABLE_RAW.addAll(names)) {
            XenoPixelsMod.LOGGER.info("Registered {} BT3 animations with DragonMineZ: {}",
                    names.size(), String.join(", ", names));
        }
    }
}
