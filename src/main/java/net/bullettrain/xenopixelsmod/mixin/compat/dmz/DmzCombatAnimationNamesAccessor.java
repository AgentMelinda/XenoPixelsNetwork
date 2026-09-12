package net.bullettrain.xenopixelsmod.mixin.compat.dmz;

import com.dragonminez.client.animation.CombatAnimationResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;

/**
 * Read access to the animation-name set DragonMineZ builds at resource reload.
 *
 * <p>{@link DmzCombatAnimationRegistryMixin} adds this mod's shipped names to that set once per
 * reload. Binding a studio clip to a combat intent happens in the middle of a session, long after
 * the last reload, so without a way to reach the same set the new name would resolve to nothing
 * until the player pressed F3+T. This accessor is how {@code /xenoanim bind} takes effect at once.
 *
 * <p>The set itself is mutated, never the field, so the field staying {@code final} is not a
 * problem. The same field is already shadowed by {@code DmzCombatAnimationRegistryMixin}, so this
 * adds no new coupling to DragonMineZ internals beyond what is there.
 */
@Mixin(value = CombatAnimationResolver.class, remap = false)
public interface DmzCombatAnimationNamesAccessor {

    @Accessor("AVAILABLE_RAW")
    static Set<String> xeno$availableRaw() {
        throw new AssertionError("Mixin accessor was not applied");
    }
}
