package net.bullettrain.xenopixelsmod.compat.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Audible range for CustomNPCs script sounds.
 *
 * <p>CustomNPCs sends every script sound to a fixed 16-block radius (see
 * {@code WorldWrapperSoundRangeMixin} for the bytecode), so a loud NPC sound is simply never
 * delivered to anyone standing further away. Vanilla instead scales the range with volume, and
 * this reproduces that rule.
 */
public final class NpcScriptSound {
    /** Vanilla's base audible radius for a sound at volume 1 ({@code ServerLevel.playSeededSound}). */
    public static final int BASE_RADIUS = 16;
    /**
     * Ceiling on the derived radius. Sound packets go to every player inside it, so an
     * unbounded multiplier would let one scripted sound fan out server-wide.
     */
    public static final int MAX_RADIUS = 256;

    private NpcScriptSound() {}

    /**
     * Audible radius for a script sound, following vanilla's {@code volume > 1 ? 16 * volume : 16}.
     *
     * @param original the radius CustomNPCs would have used, kept as the floor so this can only
     *                 ever widen the range, never narrow it
     */
    public static int radiusFor(int original, float volume) {
        int base = Math.max(original, BASE_RADIUS);
        if (!Float.isFinite(volume) || volume <= 1.0f) {
            return base;
        }
        return Math.min(MAX_RADIUS, Math.max(base, Math.round(BASE_RADIUS * volume)));
    }

    /** Position a script sound should originate from for this NPC. */
    public static BlockPos originOf(Entity npc) {
        return npc == null ? BlockPos.ZERO : npc.blockPosition();
    }

    /** True when this entity is a live server-side NPC a sound can be played from. */
    public static boolean canPlayFrom(LivingEntity npc) {
        return npc != null && npc.isAlive() && !npc.level().isClientSide();
    }
}
