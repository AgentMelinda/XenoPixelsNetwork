package net.bullettrain.xenopixelsmod.mixin.compat.dmz;

import com.dragonminez.client.animation.CombatAnimationResolver;
import net.bullettrain.xenopixelsmod.api.anim.XenoAnimApi;
import net.bullettrain.xenopixelsmod.client.anim.XenoAnimClip;
import net.bullettrain.xenopixelsmod.client.anim.XenoStudioClipCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Pushed studio clips are not in DragonMineZ's shipped {@code AVAILABLE_RAW} set.
 * {@code toPlayableKey} then returns empty and {@code playMeleeAnimation} becomes
 * {@code fallback}. Resolve a baked {@code combat.xeno_*} cache name first.
 *
 * <p>{@code toPlayableKey(String)} verified with {@code javap} on
 * {@code libs/dragonminez-2.1.3.jar}.
 */
@Mixin(value = CombatAnimationResolver.class, remap = false)
public abstract class DmzStudioPlayableKeyMixin {

    @Inject(method = "toPlayableKey", at = @At("HEAD"), cancellable = true)
    private static void xenopixels$studioClip(String normalizedKey, CallbackInfoReturnable<String> cir) {
        String studio = studioName(normalizedKey);
        if (studio != null) {
            cir.setReturnValue(studio);
        }
    }

    private static String studioName(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (XenoStudioClipCache.has(trimmed)) {
            return trimmed;
        }
        if (!trimmed.startsWith("combat.") && XenoStudioClipCache.has("combat." + trimmed)) {
            return "combat." + trimmed;
        }
        String prefix = XenoAnimApi.PREFIX;
        String bare = trimmed.startsWith(prefix)
                ? trimmed.substring(prefix.length())
                : trimmed.startsWith("xeno_") ? trimmed.substring("xeno_".length()) : trimmed;
        String canonical = prefix + XenoAnimClip.sanitize(bare);
        return XenoStudioClipCache.has(canonical) ? canonical : null;
    }
}
