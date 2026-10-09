package net.bullettrain.xenopixelsmod.fx.effek;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.Set;

/**
 * Server entry point for Effekseer effects (Hakai, missiles, DMZ punches).
 *
 * <p>{@link Outcome#UNAVAILABLE} (config off, cap reached, library failure) means the caller draws
 * its vanilla particles instead, so something always shows. {@link Outcome#DUPLICATE} means this
 * target already has a punch effect this tick: draw nothing extra.
 */
public final class XenoEffects {
    public enum Outcome { PLAYED, DUPLICATE, UNAVAILABLE }

    private static final EffectGate GATE = new EffectGate();
    private static final Set<EffectSlot> FAILED_ONCE = EnumSet.noneOf(EffectSlot.class);
    /** Created on first use, so nothing loads AAA Particles classes until an effect is sent. */
    private static EffekSender sender;

    private XenoEffects() {}

    /** A one-shot effect at a point, facing {@code forward}; {@code targetId} -1 when none. */
    public static boolean play(ServerLevel level, EffectSlot slot, Vec3 pos, Vec3 forward, float scale, int targetId) {
        return attempt(level, slot, pos, forward, scale, targetId) == Outcome.PLAYED;
    }

    /**
     * A large effect (an area Hakai's veil): sent {@code extraRange} blocks further than the slot's
     * usual range, because its anchor can be far from a player who is well inside it.
     */
    public static boolean playWide(ServerLevel level, EffectSlot slot, Vec3 pos, Vec3 forward, float scale,
                                   double extraRange) {
        return attemptAt(level, level == null ? 0L : level.getGameTime(), slot, pos, forward, scale, -1, -1,
                EffekSender.Follow.NONE, Math.max(0.0, extraRange)) == Outcome.PLAYED;
    }

    public static Outcome attempt(ServerLevel level, EffectSlot slot, Vec3 pos, Vec3 forward, float scale,
                                  int targetId) {
        return attemptAt(level, level == null ? 0L : level.getGameTime(), slot, pos, forward, scale, targetId);
    }

    /**
     * An effect that rides on an entity: AAA moves it with the entity every frame, so it stays
     * with a moving player or missile. Directional slots also turn along the entity's velocity
     * (the missile plume); upright ones (the Sparking aura) only follow it. {@code pos} is the entity's position now,
     * used for the range check.
     */
    public static boolean playBound(ServerLevel level, EffectSlot slot, Vec3 pos, int entityId, float scale) {
        return attemptAt(level, level == null ? 0L : level.getGameTime(), slot, pos, null, scale, -1, entityId,
                slot.upright() ? EffekSender.Follow.POSITION : EffekSender.Follow.VELOCITY) == Outcome.PLAYED;
    }

    /**
     * Bound to the entity's eyes and turned along its look every frame: local +Z is where it
     * looks. For effects that lie along a flyer's body (DMZ flight points the body where you look).
     */
    public static boolean playBoundLook(ServerLevel level, EffectSlot slot, Vec3 pos, int entityId, float scale) {
        return attemptAt(level, level == null ? 0L : level.getGameTime(), slot, pos, null, scale, -1, entityId,
                EffekSender.Follow.LOOK) == Outcome.PLAYED;
    }

    /** Ki is seen from further away than a punch: a beam's far end is a long way from its caster. */
    private static final double KI_EXTRA_RANGE = 64.0;

    /**
     * One of Combat V3's ki effects ({@code effeks/ki/<asset>}), under effekseerKiAttacks. They come
     * in every colour, so they are named by asset and not by slot.
     *
     * @param forward where the effect's +Z points, or null for an upright effect
     */
    public static boolean playKi(ServerLevel level, String asset, Vec3 pos, Vec3 forward, float scale) {
        if (level == null || asset == null || pos == null || !XenoServerConfig.effekseerEnabled
                || !XenoServerConfig.effekseerKiAttacks) return false;
        return send(level, EffectSlot.KI_IMPACT, new EffekSender.Request(
                ResourceLocation.fromNamespaceAndPath(XenoPixelsMod.MOD_ID, "ki/" + asset), pos, forward, scale,
                GATE.range(EffectSlot.KI_IMPACT) + KI_EXTRA_RANGE));
    }

    /** As {@link #attempt}, for a hit recorded at {@code gameTick} (the punch hook plays later). */
    static Outcome attemptAt(ServerLevel level, long gameTick, EffectSlot slot, Vec3 pos, Vec3 forward, float scale,
                             int targetId) {
        return attemptAt(level, gameTick, slot, pos, forward, scale, targetId, -1, EffekSender.Follow.NONE, 0.0);
    }

    private static Outcome attemptAt(ServerLevel level, long gameTick, EffectSlot slot, Vec3 pos, Vec3 forward,
                                     float scale, int targetId, int boundEntity, EffekSender.Follow follow) {
        return attemptAt(level, gameTick, slot, pos, forward, scale, targetId, boundEntity, follow, 0.0);
    }

    private static Outcome attemptAt(ServerLevel level, long gameTick, EffectSlot slot, Vec3 pos, Vec3 forward,
                                     float scale, int targetId, int boundEntity, EffekSender.Follow follow,
                                     double extraRange) {
        if (slot == null || pos == null) return Outcome.UNAVAILABLE;
        EffectGate.Result result = GATE.check(slot, gameTick, targetId);
        if (result == EffectGate.Result.DUPLICATE) return Outcome.DUPLICATE;
        if (result != EffectGate.Result.OK) return Outcome.UNAVAILABLE;
        float size = scale * slot.defaultScale();
        // Not for the bound plume: its nozzle offset is inside the effect, so extra scale would move
        // the flame off the tail. Its size is built into the effect (tools/effekseer, thruster.py).
        if (follow != EffekSender.Follow.VELOCITY) size *= categoryScale(slot.category());
        if (slot == EffectSlot.MISSILE_EXPLOSION) size *= XenoServerConfig.effekseerExplosionScale;
        size *= XenoServerConfig.slotScale(slot);
        // Upright effects (Hakai, Sparking) are sent unrotated; see EffectSlot.upright().
        Vec3 facing = slot.upright() || boundEntity >= 0 ? null : forward;
        if (!send(level, slot, new EffekSender.Request(id(slot), pos, facing, size, GATE.range(slot) + extraRange, boundEntity,
                boundEntity >= 0 ? follow : EffekSender.Follow.NONE,
                follow == EffekSender.Follow.LOOK ? LOOK_ANCHOR : 0.0))) {
            return Outcome.UNAVAILABLE;
        }
        GATE.commit(slot, gameTick, targetId);
        return Outcome.PLAYED;
    }

    private static float categoryScale(EffectGate.Category category) {
        return switch (category) {
            case PUNCH -> XenoServerConfig.effekseerPunchScale;
            case HAKAI -> XenoServerConfig.effekseerHakaiScale;
            case MISSILE -> XenoServerConfig.effekseerMissileScale;
            case SPARKING -> XenoServerConfig.effekseerSparkingScale;
            case SHIP -> XenoServerConfig.effekseerThrusterScale;
            case KI -> XenoServerConfig.effekseerKiImpactScale;
        };
    }

    /**
     * Blocks behind the eyes that a look-bound effect is anchored at: the body's centre for a
     * flyer, so the effect grows around the body when scaled. AAA's head-space offset points
     * against the look (measured, AaaHeadSpaceOffsetTest), so +0.9 is behind the eyes.
     */
    static final double LOOK_ANCHOR = 0.9;

    private static boolean send(ServerLevel level, EffectSlot slot, EffekSender.Request request) {
        try {
            if (sender == null) sender = new AaaEffekSender();
            sender.send(level, request);
            return true;
        } catch (RuntimeException | LinkageError e) {
            synchronized (FAILED_ONCE) {
                if (FAILED_ONCE.add(slot)) {
                    XenoPixelsMod.LOGGER.warn("Effekseer effect {} could not play ({}); using vanilla particles",
                            slot, e.toString());
                }
            }
            return false;
        }
    }

    static ResourceLocation id(EffectSlot slot) {
        return ResourceLocation.fromNamespaceAndPath(XenoPixelsMod.MOD_ID, slot.path());
    }

    static void useSender(EffekSender testSender) {
        sender = testSender;
    }

    static void resetForTest() {
        sender = null;
        GATE.reset();
        synchronized (FAILED_ONCE) {
            FAILED_ONCE.clear();
        }
    }
}
