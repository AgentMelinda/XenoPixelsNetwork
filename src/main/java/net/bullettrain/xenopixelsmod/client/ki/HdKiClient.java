package net.bullettrain.xenopixelsmod.client.ki;

import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import com.dragonminez.common.init.entities.ki.KiAreaEntity;
import com.dragonminez.common.init.entities.ki.KiBarrierEntity;
import com.dragonminez.common.init.entities.ki.KiBlastEntity;
import com.dragonminez.common.init.entities.ki.KiDiskEntity;
import com.dragonminez.common.init.entities.ki.KiExplosionEntity;
import com.dragonminez.common.init.entities.ki.KiLaserEntity;
import com.dragonminez.common.init.entities.ki.KiWaveEntity;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import mod.chloeprime.aaaparticles.api.common.AAALevel;
import mod.chloeprime.aaaparticles.api.common.ParticleEmitterInfo;
import mod.chloeprime.aaaparticles.api.client.EffectRegistry;
import mod.chloeprime.aaaparticles.client.installer.NativePlatform;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.bullettrain.xenopixelsmod.fx.ki.KiLook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

/**
 * Draws DragonMineZ's ki attacks with the HD ki effects instead of DragonMineZ's renderers
 * (/xenoaura ki, on by default).
 *
 * <p>Client only and visual only: nothing is sent, and DragonMineZ's projectiles, damage and
 * sounds are untouched. Every tick each ki projectile in view gets the effects for its kind, from
 * the colours, size and beam length it already syncs; {@link #hides} tells the renderer hook to
 * skip DragonMineZ's own drawing of the kinds handled here. Other DragonMineZ projectiles (the
 * Dragon Fist, the Oozaru fist, Majin skills) are left alone.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class HdKiClient {
    private static final double VIEW_RANGE = 160.0;
    /** DragonMineZ ball sizes from here up are its giant balls (Spirit Bomb, Supernova: 3). */
    private static final float GIANT_FROM = 2.5f;
    private static final Int2ObjectOpenHashMap<Seen> SEEN = new Int2ObjectOpenHashMap<>();
    private static final Set<ResourceLocation> READY = ConcurrentHashMap.newKeySet();
    private static final Set<ResourceLocation> LOADING = ConcurrentHashMap.newKeySet();
    private static final Set<ResourceLocation> MISSING = ConcurrentHashMap.newKeySet();
    private static boolean failed;
    private static volatile int generation;
    private static ClientLevel activeLevel;

    private HdKiClient() {}

    /** What has already been drawn for one projectile. */
    private static final class Seen {
        int firedAt = -1;
        int lengths;
        boolean burst;
        long lastTick;
    }

    public static boolean active() {
        return XenoClientConfig.hdKi && !failed && !NativePlatform.isRunningOnUnsupportedPlatform();
    }

    /** Whether DragonMineZ's own drawing of {@code entity} is replaced. */
    public static boolean hides(Entity entity) {
        Minecraft mc = Minecraft.getInstance();
        if (!(entity instanceof AbstractKiProjectile ki) || mc.level == null || mc.player == null
                || entity.level() != mc.level) return false;
        if (!allowsHd(ki.getTechniqueId())) return false;
        KiLook.Kind kind = kind(entity);
        boolean inRange = entity.position().distanceToSqr(mc.player.position()) <= VIEW_RANGE * VIEW_RANGE;
        return canSuppress(active(), inRange, kind != null && assetsReady(required(ki, kind)));
    }

    static boolean canSuppress(boolean active, boolean inRange, boolean assetsReady) {
        return active && inRange && assetsReady;
    }

    private static boolean allowsHd(String techniqueId) {
        return allowsHd(net.bullettrain.xenopixelsmod.client.XenoServerClientState.get().effekseerKiAttacks,
                net.bullettrain.xenopixelsmod.combat.v3.ki.V3NativeKi.owns(techniqueId));
    }

    static boolean allowsHd(boolean ownedHdEnabled, boolean owned) { return ownedHdEnabled || !owned; }

    static KiLook.Kind kind(Entity entity) {
        if (entity instanceof KiWaveEntity) return KiLook.Kind.WAVE;
        if (entity instanceof KiLaserEntity laser) {
            return KiLook.twoTone(laser.getColor(), laser.getColorBorder()) ? KiLook.Kind.BEAM : KiLook.Kind.LASER;
        }
        if (entity instanceof KiDiskEntity) return KiLook.Kind.DISK;
        if (entity instanceof KiExplosionEntity) return KiLook.Kind.EXPLOSION;
        if (entity instanceof KiBarrierEntity) return KiLook.Kind.SHIELD;
        if (entity instanceof KiAreaEntity) return KiLook.Kind.AREA;
        if (entity instanceof KiBlastEntity blast) {
            return blast.getSize() >= GIANT_FROM ? KiLook.Kind.GIANT_BALL : KiLook.Kind.MEDIUM_BALL;
        }
        return null;
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || mc.isPaused() || !active()) {
            if (!SEEN.isEmpty()) SEEN.clear();
            return;
        }
        if (activeLevel != level) {
            resetReadiness();
            activeLevel = level;
        }
        long now = level.getGameTime();
        Vec3 eye = mc.player.position();
        try {
            for (Entity entity : level.entitiesForRendering()) {
                if (!(entity instanceof AbstractKiProjectile ki) || ki.isRemoved()) continue;
                if (!allowsHd(ki.getTechniqueId())) continue;
                KiLook.Kind kind = kind(ki);
                if (kind == null || ki.position().distanceToSqr(eye) > VIEW_RANGE * VIEW_RANGE) continue;
                List<ResourceLocation> required = required(ki, kind);
                prepare(required);
                if (!assetsReady(required)) continue;
                Seen seen = SEEN.computeIfAbsent(ki.getId(), id -> new Seen());
                seen.lastTick = now;
                draw(level, ki, kind, seen);
            }
        } catch (RuntimeException | LinkageError e) {
            // Never break the client over a look: hand ki back to DragonMineZ for this session.
            failed = true;
            SEEN.clear();
            XenoPixelsMod.LOGGER.warn("HD ki off for this session ({})", e.toString());
            return;
        }
        if (now % 100 == 0) SEEN.values().removeIf(seen -> now - seen.lastTick > 100);
    }

    private static void draw(ClientLevel level, AbstractKiProjectile ki, KiLook.Kind kind, Seen seen) {
        int colour = ki.getColor();
        float scale = KiLook.nativeScale(kind, size(ki, kind));
        Vec3 centre = ki.position().add(0.0, ki.getBbHeight() * 0.5, 0.0);
        if (!ki.isFiring()) {
            // Still being charged: the gathering energy, once per copy's lifetime.
            if (ki.tickCount % 16 == 1 && kind != KiLook.Kind.SHIELD && kind != KiLook.Kind.AREA) {
                play(level, KiLook.Part.CHARGE, colour, centre, null, Math.max(0.6f, scale));
            }
            return;
        }
        if (seen.firedAt < 0) seen.firedAt = ki.tickCount;
        int age = ki.tickCount - seen.firedAt;
        switch (kind) {
            case WAVE, LASER, BEAM -> beam(level, ki, kind, seen, colour, scale, age);
            case GIANT_BALL -> {
                if (age % KiLook.GIANT_EVERY_TICKS == 0) play(level, KiLook.Part.GIANT, colour, centre, null, scale);
            }
            case EXPLOSION -> {
                if (!seen.burst) {
                    seen.burst = true;
                    play(level, KiLook.Part.EXPLOSION, colour, ki.position(), null, scale);
                }
            }
            case SHIELD, AREA -> {
                if (age % KiLook.HELD_EVERY_TICKS == 0) {
                    play(level, KiLook.flight(kind), colour, kind == KiLook.Kind.AREA ? ki.position() : centre, null, scale);
                }
            }
            default -> play(level, KiLook.flight(kind), colour, centre, null, scale);
        }
    }

    private static void beam(ClientLevel level, AbstractKiProjectile ki, KiLook.Kind kind, Seen seen, int colour,
                             float scale, int age) {
        float length;
        float yaw;
        float pitch;
        if (ki instanceof KiWaveEntity wave) {
            length = wave.getBeamLength();
            yaw = wave.getFixedYaw();
            pitch = wave.getFixedPitch();
        } else if (ki instanceof KiLaserEntity laser) {
            length = laser.getBeamLength();
            yaw = laser.getFixedYaw();
            pitch = laser.getFixedPitch();
        } else {
            return;
        }
        // The same direction DragonMineZ's own beam code uses.
        Vec3 direction = Vec3.directionFromRotation(pitch, yaw);
        Vec3 origin = ki.position();
        double spacing = KiLook.SEGMENT * scale;
        int lengths = KiLook.lengths(length, spacing);
        boolean spiral = kind == KiLook.Kind.BEAM;
        for (int i = KiLook.firstLength(age, seen.lengths, lengths); i < lengths; i++) {
            Vec3 at = origin.add(direction.scale(i * spacing));
            play(level, KiLook.flight(kind), colour, at, direction, scale);
            if (spiral) play(level, KiLook.Part.SPIRAL, ki.getColorBorder(), at, direction, scale);
        }
        seen.lengths = lengths;
        if (age % KiLook.MUZZLE_EVERY_TICKS == 0) {
            play(level, KiLook.Part.WAVE_MUZZLE, colour, origin, direction, kind == KiLook.Kind.WAVE ? scale : 0.35f * scale);
        }
        if (kind == KiLook.Kind.WAVE && length > 1.0f) {
            play(level, KiLook.Part.WAVE_HEAD, colour, origin.add(direction.scale(length)), direction, scale);
        }
    }

    /** The number DragonMineZ sizes this projectile by. */
    private static float size(AbstractKiProjectile ki, KiLook.Kind kind) {
        if (ki instanceof KiExplosionEntity explosion) return explosion.getMaxRadius();
        if (ki instanceof KiAreaEntity area) return area.getAreaRadius();
        if (ki instanceof KiBarrierEntity barrier) return barrier.getCurrentSize();
        return ki.getSize();
    }

    private static void play(ClientLevel level, KiLook.Part part, int colour, Vec3 pos, Vec3 forward, float scale) {
        ResourceLocation effect = effect(part, colour);
        if (!READY.contains(effect)) return;
        ParticleEmitterInfo info = ParticleEmitterInfo.create(level, effect);
        info.position(pos);
        if (forward != null && forward.lengthSqr() > 1.0e-9) info.rotationFromForward(forward);
        info.scale(scale);
        AAALevel.addParticle(level, info);
    }

    private static ResourceLocation effect(KiLook.Part part, int colour) {
        return ResourceLocation.fromNamespaceAndPath(XenoPixelsMod.MOD_ID, "ki/" + KiLook.asset(part, colour));
    }

    private static List<ResourceLocation> required(AbstractKiProjectile ki, KiLook.Kind kind) {
        List<ResourceLocation> out = new ArrayList<>(6);
        int colour = ki.getColor();
        out.add(effect(KiLook.Part.CHARGE, colour));
        out.add(effect(KiLook.flight(kind), colour));
        out.add(effect(KiLook.landing(kind), colour));
        if (kind == KiLook.Kind.WAVE || kind == KiLook.Kind.LASER || kind == KiLook.Kind.BEAM) {
            out.add(effect(KiLook.Part.WAVE_MUZZLE, colour));
        }
        if (kind == KiLook.Kind.WAVE) out.add(effect(KiLook.Part.WAVE_HEAD, colour));
        if (kind == KiLook.Kind.BEAM && ki instanceof KiLaserEntity laser) {
            out.add(effect(KiLook.Part.SPIRAL, laser.getColorBorder()));
        }
        return List.copyOf(out);
    }

    private static void prepare(List<ResourceLocation> effects) {
        int requestGeneration = generation;
        for (ResourceLocation effect : effects) {
            if (READY.contains(effect) || MISSING.contains(effect) || !LOADING.add(effect)) continue;
            EffectRegistry.tryLoad(effect).whenComplete((loaded, failure) -> {
                if (requestGeneration != generation) return;
                LOADING.remove(effect);
                if (failure == null && loaded != null && loaded.isPresent()) READY.add(effect);
                else MISSING.add(effect);
            });
        }
    }

    private static boolean assetsReady(List<ResourceLocation> effects) {
        return !effects.isEmpty() && READY.containsAll(effects);
    }

    private static void resetReadiness() {
        generation++;
        READY.clear();
        LOADING.clear();
        MISSING.clear();
        SEEN.clear();
        failed = false;
    }

    @EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModEvents {
        private ModEvents() {}

        @SubscribeEvent
        public static void reload(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) manager -> resetReadiness());
        }
    }
}
