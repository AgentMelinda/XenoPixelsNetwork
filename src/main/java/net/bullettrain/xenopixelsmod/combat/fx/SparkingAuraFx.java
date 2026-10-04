package net.bullettrain.xenopixelsmod.combat.fx;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.combat.Bt3SparkingSystem;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.joml.Vector3f;

/**
 * The sparking aura: a rising shell of ki around the fighter, debris torn off the ground, and
 * lightning cracking up the body.
 *
 * <p>Sparking has been a live mechanic with real combat effects — a damage multiplier, i-frames
 * — and almost no presence. A state that changes how a fight works should be obvious from across
 * the arena, both to the player using it and to whoever is about to be hit by it.
 *
 * <p><b>Everything here is budgeted per tick and thinned by distance.</b> An aura is drawn every
 * tick for as long as sparking lasts, which is the opposite of an impact effect: a per-hit
 * flourish can afford to be expensive because it happens once, whereas anything sustained is
 * multiplied by hundreds of ticks and by every sparking player in view. The shell is a handful of
 * particles on a rotating ring rather than a dense cloud, and it stays legible because it moves.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class SparkingAuraFx {

    /** Aura core: hot gold, the same family the sparking HUD pips use. */
    private static final Vector3f AURA_CORE = new Vector3f(1.0f, 0.86f, 0.38f);
    /** Aura edge, cooler so the shell has depth rather than reading as a flat gold cylinder. */
    private static final Vector3f AURA_EDGE = new Vector3f(1.0f, 0.55f, 0.16f);
    private static final DustParticleOptions CORE_DUST = new DustParticleOptions(AURA_CORE, 1.4f);
    private static final DustParticleOptions EDGE_DUST = new DustParticleOptions(AURA_EDGE, 1.1f);

    /** Particles placed around the ring each tick. Deliberately small; see the class note. */
    private static final int RING_POINTS = 3;
    /** Ring radius as a fraction of the fighter's width. */
    private static final double RING_SCALE = 0.85;
    /** Rotation per tick, in radians. Fast enough that three points read as a continuous shell. */
    private static final double SPIN = 0.9;

    /** One in this many ticks throws an arc. */
    private static final int ARC_INTERVAL = 5;
    /** One in this many ticks lifts ground debris, when standing on something. */
    private static final int DEBRIS_INTERVAL = 3;

    private SparkingAuraFx() {
    }

    private static boolean playAura(ServerLevel level, ServerPlayer player, boolean flying) {
        float size = HakaiEffectRules.bodyScale(player.getBbHeight());
        if (flying) {
            return net.bullettrain.xenopixelsmod.fx.effek.XenoEffects.playBoundLook(level,
                    net.bullettrain.xenopixelsmod.fx.effek.EffectSlot.SPARKING_FLIGHT, player.position(),
                    player.getId(), size);
        }
        return net.bullettrain.xenopixelsmod.fx.effek.XenoEffects.playBound(level,
                groundAura(XenoServerConfig.effekseerSparkingSmooth), player.position(), player.getId(), size);
    }

    /** The classic aura unless the steadier one is switched on (/xenoset sparkingsmooth true). */
    static net.bullettrain.xenopixelsmod.fx.effek.EffectSlot groundAura(boolean smooth) {
        return smooth ? net.bullettrain.xenopixelsmod.fx.effek.EffectSlot.SPARKING_AURA_SMOOTH
                : net.bullettrain.xenopixelsmod.fx.effek.EffectSlot.SPARKING_AURA;
    }

    /**
     * DMZ's own test for the lying-flat flight pose (FlySkillEvent.isFlyingFast in DragonMineZ
     * 2.1.3): the fly skill is on, flight mode is not 1, and the player moves faster than 0.55
     * blocks a tick. Speed is measured here from the player's movement since the last tick,
     * since a server player's delta movement is not their real speed.
     */
    static boolean flying(ServerPlayer player) {
        Vec3 now = player.position();
        Vec3 last = LAST_POS.put(player.getId(), now);
        double speedSqr = last == null ? 0.0 : now.distanceToSqr(last);
        int mode = net.bullettrain.xenopixelsmod.api.dmz.DmzAccess.stats(player)
                .map(s -> s.getStatus() == null ? 0 : s.getStatus().getFlightMode()).orElse(0);
        return flyingFast(net.bullettrain.xenopixelsmod.api.dmz.DmzAccess.isSkillActive(player, "fly"),
                mode, speedSqr);
    }

    static boolean flyingFast(boolean flySkillActive, int flightMode, double speedSqrPerTick) {
        return flySkillActive && flightMode != 1 && speedSqrPerTick > FAST_FLIGHT_SPEED_SQR;
    }

    /** DMZ's fast-flight threshold: 0.55 blocks a tick, squared. */
    private static final double FAST_FLIGHT_SPEED_SQR = 0.3025;
    private static final java.util.Map<Integer, Vec3> LAST_POS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<Integer, Boolean> LAST_FLYING = new java.util.concurrent.ConcurrentHashMap<>();

    /** Per-player aura pulse timing (server thread only). */
    private static final HakaiFx.PulseClock AURA_PULSES = new HakaiFx.PulseClock();

    /**
     * The start burst: a gold flash, a ground shockwave, lightning and a rock spray. LOWEST
     * priority and not on cancelled events, so it plays only when Sparking really starts.
     */
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void onActivate(net.bullettrain.xenopixelsmod.api.event.SparkingEvent.Activate event) {
        if (event.isCanceled() || !XenoServerConfig.sparkingAuraEnabled) return;
        ServerPlayer player = event.getPlayer();
        if (player == null || !(player.level() instanceof ServerLevel level)) return;
        AURA_PULSES.forget(player.getId());
        // Bound too, so the burst goes with a player who moves off at once.
        net.bullettrain.xenopixelsmod.fx.effek.XenoEffects.playBound(level,
                net.bullettrain.xenopixelsmod.fx.effek.EffectSlot.SPARKING_BURST, player.position(),
                player.getId(), HakaiEffectRules.bodyScale(player.getBbHeight()));
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!XenoServerConfig.sparkingAuraEnabled) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        if (!Bt3SparkingSystem.isSparking(player)) {
            LAST_POS.remove(player.getId());
            LAST_FLYING.remove(player.getId());
            return;
        }

        double density = Math.max(0.0, Math.min(3.0, XenoServerConfig.sparkingAuraDensity));
        if (density <= 0.0) return;

        // The Effekseer aura (tools/effekseer/efkgen/effects/sparking.py) re-sent every 10 ticks
        // at the feet; the vanilla shell, debris and arcs below are only the fallback when it does
        // not play (effekseerSparking off, library failure).
        long now = level.getGameTime();
        // Bound to the player, so it moves with them every frame instead of being left behind.
        // In DMZ flight the body lies along the look, so the aura is the flight version, bound
        // to the eyes and turned with the look (DMZ's own fly skill id, verified in its jar).
        // Measured every tick; a change of pose re-sends the aura at once instead of at the next pulse.
        boolean fast = flying(player);
        Boolean before = LAST_FLYING.put(player.getId(), fast);
        if (before != null && before != fast) AURA_PULSES.forget(player.getId());
        if (AURA_PULSES.due(player.getId(), now) && playAura(level, player, fast)) {
            AURA_PULSES.showUntil(player.getId(), now + 12);
        }
        if (AURA_PULSES.showing(player.getId(), now)) return;

        shell(level, player, density);
        if (player.tickCount % DEBRIS_INTERVAL == 0) debris(level, player, density);
        if (player.tickCount % ARC_INTERVAL == 0) arcs(level, player, density);
    }

    /**
     * Rising ring of ki around the body.
     *
     * <p>The ring rotates with tick count, so three particles a tick trace a continuous spiral
     * rather than stacking in the same three places. That is what buys a legible shell at a
     * fraction of the particle count a static ring would need.
     */
    private static void shell(ServerLevel level, ServerPlayer player, double density) {
        float height = Math.max(0.5f, player.getBbHeight());
        double radius = Math.max(0.3f, player.getBbWidth()) * RING_SCALE;
        int points = Math.max(1, (int) Math.round(RING_POINTS * Math.min(1.0, density)));

        for (int i = 0; i < points; i++) {
            double angle = player.tickCount * SPIN + (Math.PI * 2.0 / points) * i;
            double x = player.getX() + Math.cos(angle) * radius;
            double z = player.getZ() + Math.sin(angle) * radius;
            // Spawn low and let the upward velocity carry it: an aura reads as ki rising off the
            // body, so the motion matters more than the placement.
            double y = player.getY() + height * 0.1;

            DustParticleOptions dust = (i & 1) == 0 ? CORE_DUST : EDGE_DUST;
            level.sendParticles(dust, x, y, z, 0, 0.0, 0.55, 0.0, 1.0);
        }

        // A single flame at the feet each tick anchors the column to the ground.
        level.sendParticles(ParticleTypes.FLAME,
                player.getX(), player.getY() + 0.05, player.getZ(), 1, 0.12, 0.02, 0.12, 0.01);
    }

    /**
     * Debris torn off whatever the fighter is standing on.
     *
     * <p>Uses the real block below, so sparking on stone throws stone and on sand throws sand.
     * A generic puff would read as smoke and lose the "the ground cannot take this" note that
     * makes the aura feel like pressure rather than decoration.
     */
    private static void debris(ServerLevel level, ServerPlayer player, double density) {
        if (!player.onGround()) return;
        BlockPos below = player.blockPosition().below();
        BlockState state = level.getBlockState(below);
        if (state.isAir()) return;

        int count = Math.max(1, (int) Math.round(2 * Math.min(1.0, density)));
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                player.getX(), player.getY() + 0.1, player.getZ(),
                count, 0.35, 0.05, 0.35, 0.25);
    }

    /** Electric arcs climbing the body, the visual tell that this is more than an aura. */
    private static void arcs(ServerLevel level, ServerPlayer player, double density) {
        float height = Math.max(0.5f, player.getBbHeight());
        int count = Math.max(1, (int) Math.round(2 * Math.min(1.0, density)));
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                player.getX(), player.getY() + height * 0.55, player.getZ(),
                count, 0.4, height * 0.4, 0.4, 0.06);
    }
}
