package net.bullettrain.xenopixelsmod.compat.npc;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.Locale;

/**
 * Plays the custom sounds set on the Advanced tab: Living / Angry / Hurt / Death / Step.
 *
 * <p>The MyNPCs Advanced &gt; Sounds menu is five ids plus a "Has Pitch" flag, and this is that,
 * kept deliberately forgiving. An id is stored as free text because a profile is read and written
 * long before any registry is safe to touch, so it is resolved here, at play time, and an id that
 * does not resolve is <strong>silent rather than a crash</strong> - the same leniency
 * {@code client/ui/atlas/AtlasSound} uses for the DragonMineZ button clicks.
 *
 * <p>Server-side only. Sounds are a server decision broadcast to everyone nearby, like speech, so
 * two players watching the same NPC hear the same thing.
 */
public final class NpcCustomSounds {

    /** Range of the random pitch when "Has Pitch" is on, around 1.0. */
    private static final float PITCH_SPREAD = 0.2f;

    private NpcCustomSounds() {
    }

    /**
     * Plays the sound configured for {@code slot} on {@code npc}.
     *
     * @return true when a configured sound was found and played, so the caller can leave the
     *         vanilla sound alone; false when this NPC has nothing set for that slot and the
     *         default should play as before
     */
    public static boolean play(LivingEntity npc, String slot) {
        if (npc == null || npc.level().isClientSide() || !NpcCombatProfile.hasProfile(npc)) {
            return false;
        }
        return play(npc, NpcCombatProfile.readCached(npc), slot);
    }

    /** As {@link #play(LivingEntity, String)} with a profile already in hand. */
    public static boolean play(LivingEntity npc, NpcCombatProfile profile, String slot) {
        if (npc == null || profile == null || npc.level().isClientSide()) {
            return false;
        }
        SoundEvent sound = resolve(profile.soundFor(slot));
        if (sound == null) {
            return false;
        }
        float pitch = profile.soundHasPitch
                ? 1.0f + (npc.getRandom().nextFloat() - 0.5f) * 2.0f * PITCH_SPREAD
                : 1.0f;
        npc.level().playSound(null, npc.getX(), npc.getY(), npc.getZ(), sound,
                SoundSource.NEUTRAL, 1.0f, pitch);
        return true;
    }

    /**
     * Resolves a sound id, or null when it is blank or names nothing.
     *
     * <p>A bare id with no namespace is read as {@code minecraft:}, which is what a user typing
     * {@code entity.villager.ambient} into the field means.
     */
    public static SoundEvent resolve(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        ResourceLocation key = ResourceLocation.tryParse(id.trim().toLowerCase(Locale.ROOT));
        return key == null ? null : BuiltInRegistries.SOUND_EVENT.get(key);
    }

    /** Whether {@code id} names a real sound, for telling the editor a typo from a blank. */
    public static boolean isKnown(String id) {
        return resolve(id) != null;
    }
}
