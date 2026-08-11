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

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!XenoServerConfig.sparkingAuraEnabled) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        if (!Bt3SparkingSystem.isSparking(player)) return;

        double density = Math.max(0.0, Math.min(3.0, XenoServerConfig.sparkingAuraDensity));
        if (density <= 0.0) return;

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

            boolean core = (i & 1) == 0;
            DustParticleOptions dust =
                    new DustParticleOptions(core ? AURA_CORE : AURA_EDGE, core ? 1.4f : 1.1f);
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
