package net.bullettrain.xenopixelsmod.npc.spawn;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.item.custom.XenoNpcPayload;
import net.bullettrain.xenopixelsmod.missile.ModEntities;
import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.bullettrain.xenopixelsmod.npc.XenoNpcRole;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcClones;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Places the world's own NPCs, from the rules in the {@code spawns/} store.
 *
 * <p>This is the reader {@code XenoNpcStoreCategory.SPAWNS} was reserved for: nothing placed NPCs
 * by rule before it.
 *
 * <p><b>What a rule may and may not do.</b> A rule references a saved clone, so the thing that
 * appears is exactly the NPC an operator authored — same role, appearance, behaviour, dialogue. It
 * is placed with {@code keepOwner} on, which hands it to whoever saved the template rather than to
 * whichever player happened to be standing there, because the player is incidental to a rule.
 *
 * <p><b>It is not immortal, and it does not come back.</b> Every Xeno NPC is normally permanent
 * ({@code XenoNpcEntity.removeWhenFarAway} returns false — the class doc is explicit that none of
 * them is ever meant to be spawned naturally), and every one of them respawns after death unless
 * {@code RespawnEnabled} is off. Both of those are right for a placed NPC and wrong for an ambient
 * one: a rule that fires while a town is busy would otherwise leave a crowd behind forever, and
 * each corpse would schedule a return. So a natural spawn has respawn switched off, and
 * {@link #cleanupAround} discards the ones nobody is near any more.
 *
 * <p><b>Bounded work.</b> One attempt per player every {@link #ATTEMPT_INTERVAL_TICKS}, one
 * placement per attempt, a per-rule live cap, and a chunk-loaded check before anything is read. The
 * scan radius is the ring the NPC may appear in, never the whole world — a spawn service that
 * iterates loaded chunks is a tick profiler flame graph waiting to happen.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class NpcNaturalSpawnService {

    /** Persistent-data key holding the id of the rule that placed an NPC. */
    public static final String MARKER = "XenoNaturalSpawn";

    /** Ticks between attempts for one player. Half a heart pulse; long enough to feel ambient. */
    static final int ATTEMPT_INTERVAL_TICKS = 40;

    /** Percentage chance that an attempt places anything at all. */
    static final int CHANCE_PERCENT = 25;

    /** The NPC appears in this ring around the player, never inside their personal space. */
    static final double RING_MIN = 24.0;
    static final double RING_MAX = 48.0;

    /** How many NPCs one rule may have alive near a player at once. */
    static final int MAX_ALIVE_PER_RULE = 6;

    /** Beyond this from every player, a natural spawn is cleaned up. */
    static final double DESPAWN_DISTANCE = 192.0;

    /** Ticks between cleanup passes. Cleanup is housekeeping, not gameplay; it can wait. */
    static final int CLEANUP_INTERVAL_TICKS = 200;

    private static long nextAttemptAt;
    private static long nextCleanupAt;

    private NpcNaturalSpawnService() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        // Game time, not a local counter: the counter restarts at 0 every launch, which is the exact
        // mistake XenoNpcRespawnData documents.
        if (now < nextAttemptAt) {
            return;
        }
        nextAttemptAt = now + ATTEMPT_INTERVAL_TICKS;
        boolean cleanup = now >= nextCleanupAt;
        if (cleanup) {
            nextCleanupAt = now + CLEANUP_INTERVAL_TICKS;
        }
        RandomSource random = server.overworld().getRandom();
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        for (ServerPlayer player : players) {
            if (!(player.level() instanceof ServerLevel level)) {
                continue;
            }
            if (cleanup) {
                cleanupAround(level, player, players);
            }
            attempt(level, player, random);
        }
    }

    /**
     * One spawn attempt for one player.
     *
     * @return the NPC that was placed, or null when nothing was (kept for tests and for callers that
     *         want to know whether the roll landed)
     */
    static XenoNpcEntity attempt(ServerLevel level, ServerPlayer player, RandomSource random) {
        if (NpcNaturalSpawns.all().isEmpty()) {
            return null;
        }
        // The same master switch vanilla uses. An operator who turned natural mob spawning off did
        // so deliberately, and an NPC is a mob.
        if (!level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) {
            return null;
        }
        if (random.nextInt(100) >= CHANCE_PERCENT) {
            return null;
        }
        BlockPos spot = candidateSpot(level, player, random);
        if (spot == null) {
            return null;
        }
        String biome = level.getBiome(spot).getRegisteredName();
        List<NpcNaturalSpawn> eligible = NpcNaturalSpawns.eligibleAt(biome, level.isDay());
        if (eligible.isEmpty()) {
            return null;
        }
        int roll = random.nextInt(Math.max(1, NpcNaturalSpawn.totalWeight(eligible)));
        NpcNaturalSpawn rule = NpcNaturalSpawn.pick(eligible, roll);
        if (rule == null || !allowedByCap(countAlive(level, player, rule.id()))) {
            return null;
        }
        return place(level, rule, spot);
    }

    /**
     * A loaded, on-surface position in the ring around the player.
     *
     * @return null when the ring is not loaded yet — which is normal at render distance edges and is
     *         not a reason to log anything
     */
    static BlockPos candidateSpot(ServerLevel level, ServerPlayer player, RandomSource random) {
        double angle = random.nextDouble() * Math.PI * 2.0;
        double radius = RING_MIN + random.nextDouble() * (RING_MAX - RING_MIN);
        int x = net.minecraft.util.Mth.floor(player.getX() + Math.cos(angle) * radius);
        int z = net.minecraft.util.Mth.floor(player.getZ() + Math.sin(angle) * radius);
        int y = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                new BlockPos(x, level.getMinBuildHeight(), z)).getY();
        BlockPos spot = new BlockPos(x, y, z);
        return level.hasChunkAt(spot) ? spot : null;
    }

    /**
     * Places one NPC from one rule.
     *
     * <p>Same restore path as {@code /xenoclone place}, the Cloner item and the respawn handler, so
     * a naturally spawned NPC and a hand-placed one cannot disagree about what the template means.
     */
    static XenoNpcEntity place(ServerLevel level, NpcNaturalSpawn rule, BlockPos spot) {
        CompoundTag payload = XenoNpcClones.load(rule.cloneTab(), rule.cloneId());
        if (payload == null) {
            // The clone was deleted from under the rule. Nothing is placed and nothing is retried
            // until the operator fixes it; the log says which rule, because that is the only way to
            // find out why an ambient NPC never appeared.
            XenoPixelsMod.LOGGER.warn("Natural spawn: rule {} points at clone {}/{} which is gone",
                    rule.id(), rule.cloneTab(), rule.cloneId());
            return null;
        }
        XenoNpcRole role = XenoNpcRole.byId(payload.getString("Role"));
        XenoNpcEntity npc = ModEntities.xenoNpcType(role).create(level);
        if (npc == null) {
            return null;
        }
        double x = spot.getX() + 0.5;
        double y = spot.getY();
        double z = spot.getZ() + 0.5;
        npc.moveTo(x, y, z, randomYaw(level.getRandom()), 0.0f);
        // keepOwner true: the placer is whoever saved the template, not whoever walked past.
        XenoNpcPayload.apply(npc, payload, null, x, y, z, true);
        // After apply, which restores RespawnEnabled from the template. An ambient NPC that came
        // back after every death would accumulate an unbounded population.
        npc.npcData().setRespawnEnabled(false);
        npc.getPersistentData().putString(MARKER, rule.id());
        if (!level.addFreshEntity(npc)) {
            return null;
        }
        XenoPixelsMod.LOGGER.debug("Natural spawn: {} from rule {} at {} {} {}",
                npc.getName().getString(), rule.id(), level.dimension().location(), x, z);
        return npc;
    }

    /** How many NPCs this rule currently has alive in the ring around the player. */
    static int countAlive(ServerLevel level, ServerPlayer player, String ruleId) {
        AABB box = new AABB(player.getX() - RING_MAX, player.getY() - 32.0, player.getZ() - RING_MAX,
                player.getX() + RING_MAX, player.getY() + 32.0, player.getZ() + RING_MAX);
        return countNatural(level.getEntitiesOfClass(XenoNpcEntity.class, box,
                npc -> ruleId.equals(markerOf(npc))), ruleId);
    }

    /** Drops natural spawns that nobody is near any more. */
    static void cleanupAround(ServerLevel level, ServerPlayer player, List<ServerPlayer> players) {
        double reach = DESPAWN_DISTANCE + 16.0;
        AABB box = new AABB(player.getX() - reach, player.getY() - 64.0, player.getZ() - reach,
                player.getX() + reach, player.getY() + 64.0, player.getZ() + reach);
        List<Vec3> near = new ArrayList<>();
        for (ServerPlayer candidate : players) {
            if (candidate.level() == level) {
                near.add(candidate.position());
            }
        }
        if (near.isEmpty()) {
            return;
        }
        for (XenoNpcEntity npc : beyondDistance(
                level.getEntitiesOfClass(XenoNpcEntity.class, box, npc -> markerOf(npc) != null),
                near, DESPAWN_DISTANCE)) {
            npc.discard();
        }
    }

    /** The rule that placed this NPC, or null when it was placed by hand. */
    public static String markerOf(XenoNpcEntity npc) {
        if (npc == null) {
            return null;
        }
        CompoundTag data = npc.getPersistentData();
        if (!data.contains(MARKER)) {
            return null;
        }
        String value = data.getString(MARKER);
        return value.isBlank() ? null : value;
    }

    /** Whether a rule that already has this many live NPCs may place another. */
    static boolean allowedByCap(int alive) {
        return alive < MAX_ALIVE_PER_RULE;
    }

    static int countNatural(List<XenoNpcEntity> nearby, String ruleId) {
        int count = 0;
        for (Entity entity : nearby) {
            if (entity instanceof XenoNpcEntity npc && ruleId.equals(markerOf(npc))) {
                count++;
            }
        }
        return count;
    }

    /**
     * The natural spawns that no player in the list is within {@code distance} of.
     *
     * <p>Pure so the rule can be tested without a level: an NPC that is far from the player whose
     * chunk query found it may still be well inside another player's reach, and deleting that one is
     * the bug this function exists to avoid.
     */
    static List<XenoNpcEntity> beyondDistance(List<XenoNpcEntity> candidates,
                                              List<Vec3> playerPositions, double distance) {
        List<XenoNpcEntity> out = new ArrayList<>();
        double squared = distance * distance;
        for (XenoNpcEntity npc : candidates) {
            boolean seen = false;
            for (Vec3 origin : playerPositions) {
                if (npc.position().distanceToSqr(origin) <= squared) {
                    seen = true;
                    break;
                }
            }
            if (!seen) {
                out.add(npc);
            }
        }
        return out;
    }

    /** Pure form of the same test, for the single-player case the cleanup pass starts from. */
    static boolean beyondDistance(XenoNpcEntity npc, Vec3 origin, double distance) {
        return npc.position().distanceToSqr(origin) > distance * distance;
    }

    private static float randomYaw(RandomSource random) {
        return random.nextFloat() * 360.0f - 180.0f;
    }
}
