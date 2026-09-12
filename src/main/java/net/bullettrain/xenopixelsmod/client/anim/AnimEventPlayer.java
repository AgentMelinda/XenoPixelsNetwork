package net.bullettrain.xenopixelsmod.client.anim;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.api.event.AnimInstructionEvent;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Fires the sound, particle and instruction keyframes of a clip as playback crosses them.
 *
 * <p>GeckoLib parses all three from the animation file ({@code sound_effects},
 * {@code particle_effects}, {@code timeline}), but the handlers that would fire them live on an
 * {@code AnimationController}, and the controller posing a DragonMineZ player is DragonMineZ's, not
 * ours. So the playback paths this mod owns fire them here instead. A clip played as a bound combat
 * move goes through DMZ's controller and stays silent - that limit is written down in
 * {@code docs/xeno-anim-studio.md} rather than papered over.
 */
public final class AnimEventPlayer {

    private AnimEventPlayer() {}

    /**
     * Fires every event in {@code (from, to]}.
     *
     * <p>A half-open window means an event fires exactly once no matter how the playhead advances,
     * and a caller that wraps a loop simply calls twice - once to the end, once from the start.
     */
    public static void fire(LivingEntity entity, XenoAnimClip clip, double from, double to) {
        if (entity == null || clip == null || to <= from) return;
        for (XenoAnimClip.SoundEvent event : clip.sounds) {
            if (inWindow(event.time(), from, to)) playSound(entity, event.sound());
        }
        for (XenoAnimClip.ParticleEvent event : clip.particles) {
            if (inWindow(event.time(), from, to)) spawnParticle(entity, event);
        }
        for (XenoAnimClip.InstructionEvent event : clip.instructions) {
            if (inWindow(event.time(), from, to)) {
                NeoForge.EVENT_BUS.post(new AnimInstructionEvent(
                        entity, clip.name, event.instruction(), event.time()));
            }
        }
    }

    private static boolean inWindow(double time, double from, double to) {
        return time > from && time <= to;
    }

    private static void playSound(LivingEntity entity, String id) {
        ResourceLocation location = parse(id);
        if (location == null) return;
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(location);
        if (sound == null) {
            warnOnce("sound", id);
            return;
        }
        entity.level().playLocalSound(entity.getX(), entity.getY(), entity.getZ(),
                sound, SoundSource.PLAYERS, 1.0f, 1.0f, false);
    }

    /**
     * Spawns the effect at the entity.
     *
     * <p>The authored {@code locator} is kept in the file and round-trips, but a bone's world
     * position is only known inside the renderer, so it is not used to place the particle yet.
     * Only particle types that need no extra data can be spawned from an id alone.
     */
    private static void spawnParticle(LivingEntity entity, XenoAnimClip.ParticleEvent event) {
        ResourceLocation location = parse(event.effect());
        if (location == null) return;
        ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.get(location);
        if (!(type instanceof SimpleParticleType simple)) {
            warnOnce("particle", event.effect());
            return;
        }
        entity.level().addParticle(simple,
                entity.getX(), entity.getY() + entity.getBbHeight() * 0.6, entity.getZ(),
                0.0, 0.0, 0.0);
    }

    private static ResourceLocation parse(String id) {
        if (id == null || id.isBlank()) return null;
        return ResourceLocation.tryParse(id.trim());
    }

    private static void warnOnce(String kind, String id) {
        // A clip can name anything; say so once and keep playing rather than spamming per frame.
        if (WARNED.add(kind + ":" + id)) {
            XenoPixelsMod.LOGGER.warn("Studio clip names an unknown {}: {}", kind, id);
        }
    }

    private static final java.util.Set<String> WARNED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
}
