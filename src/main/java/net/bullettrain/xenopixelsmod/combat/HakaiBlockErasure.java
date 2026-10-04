package net.bullettrain.xenopixelsmod.combat;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.combat.fx.HakaiFx;
import net.bullettrain.xenopixelsmod.compat.sable.SableHakaiBlocks;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Area Hakai erasing blocks (2026-09-29 owner: "is it possible to make hakai destroy big buildings
 * and blocks", Sable ships "slowly fading block by block").
 *
 * <p>After an area Hakai lands, the blocks in its sphere - the world's, and the plot-local blocks of
 * any Sable ship inside it - are queued roof first. A few dozen a tick start to fade: the vanilla
 * crack overlay climbs its ten stages with a violet puff, then the block is gone, with no drops (it
 * is erased, not mined). The rate is capped so a building does not stall the server.
 *
 * <p>Every block is offered to the ordinary {@link BlockEvent.BreakEvent} with the caster as the
 * breaker, so plots, YAWP regions and claim mods can refuse it exactly as they refuse a pickaxe. If
 * the caster is gone there is nobody to ask, so the rest of that job stops (fail closed).
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class HakaiBlockErasure {
    private static final List<Job> JOBS = new ArrayList<>();
    /** Crack-overlay ids, negative so they never collide with an entity id (vanilla's breakers). */
    private static int nextBreakerId = -1_000_000;

    private HakaiBlockErasure() {
    }

    private record Fading(BlockPos pos, int breakerId, int[] elapsed) {
    }

    /**
     * Ticks between re-plays of the Hakai veil over the sphere while its blocks are erased
     * (2026-09-29 owner: "the hakai effect should stay till everything erased").
     */
    static final int VEIL_INTERVAL = 10;

    /** Positions looked at per job per tick, erasable or not: the work a tick may do. */
    private static final int SCAN_BUDGET = 32768;

    private static final class Job {
        final ServerLevel level;
        final UUID caster;
        /** The shape's positions, produced lazily (HakaiBlockScan): nothing is listed up front. */
        final Iterator<BlockPos> source;
        final int limit;
        final Vec3 centre;
        final double radius;
        int age;
        final List<Fading> fading = new ArrayList<>();
        /** What happened, told to the caster at the end so a refusal is never silent. */
        int taken;
        int erased;
        int refused;
        int solid;
        int structure;

        Job(ServerLevel level, UUID caster, Iterator<BlockPos> source, int limit, Vec3 centre, double radius) {
            this.level = level;
            this.caster = caster;
            this.source = source;
            this.limit = limit;
            this.centre = centre;
            this.radius = radius;
        }
    }

    /** Queues the blocks of an area Hakai's sphere. */
    public static void queue(ServerLevel level, ServerPlayer caster, Vec3 centre, double radius) {
        if (level == null || caster == null || centre == null || !XenoServerConfig.hakaiBlocks) return;
        // Game rules: the same mobGriefing gate the missiles obey (BallisticMissileEntity).
        if (!level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING)) {
            caster.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "\u00a77Hakai: blocks untouched (gamerule mobGriefing is false)"), false);
            return;
        }
        String dimension = level.dimension().location().toString();
        if (HakaiAreaRules.sparedDimension(dimension, XenoServerConfig.hakaiSparedDimensions)) {
            say(caster, "\u00a77Hakai: blocks untouched - " + dimension + " is protected (hakaiSparedDimensions)");
            return;
        }
        int limit = XenoServerConfig.hakaiBlockLimit;
        if (limit <= 0) return;
        boolean raze = HakaiAreaRules.parseShape(XenoServerConfig.hakaiBlockShape) == HakaiAreaRules.Shape.RAZE;
        List<Iterator<BlockPos>> parts = new ArrayList<>();
        HakaiBlockScan.World world = worldOf(level, Integer.MAX_VALUE);
        if (raze) {
            // Everything above the caster's feet (or the aim point, whichever is lower) is building,
            // even plain stone; only natural ground at or below that line stops a column (2026-09-29:
            // stone blocks in a build read as ground and left whole layers standing).
            int floor = Math.min((int) Math.floor(centre.y), caster.blockPosition().getY() - 1);
            world = worldOf(level, floor);
            int y = (int) Math.floor(centre.y);
            parts.add(HakaiBlockScan.raze(centre, radius,
                    Math.min(level.getMaxBuildHeight() - 1, y + XenoServerConfig.hakaiRazeHeight),
                    Math.max(level.getMinBuildHeight(), y - XenoServerConfig.hakaiRazeHeight), world, null));
        } else {
            parts.add(HakaiBlockScan.sphere(centre, radius, level.getMinBuildHeight(),
                    level.getMaxBuildHeight(), world, null));
        }
        if (XenoServerConfig.hakaiShips) {
            for (SableHakaiBlocks.Region ship : SableHakaiBlocks.regions(level, centre, radius)) {
                parts.add(HakaiBlockScan.sphere(ship.localCentre(), radius, level.getMinBuildHeight(),
                        level.getMaxBuildHeight(), world, ship.bounds()));
            }
        }
        say(caster, String.format("\u00a7dHakai: erasing up to %d blocks\u00a77 (%s, radius %.0f)",
                limit, raze ? "raze" : "sphere", radius));
        JOBS.add(new Job(level, caster.getUUID(), concat(parts), limit, centre, radius));
    }

    /** The level as the scan sees it: loaded chunks only, section emptiness from the chunk itself. */
    private static HakaiBlockScan.World worldOf(ServerLevel level, int groundAtOrBelow) {
        int minSection = level.getMinBuildHeight() >> 4;
        return new HakaiBlockScan.World() {
            @Override
            public boolean loaded(int chunkX, int chunkZ) {
                return level.getChunkSource().getChunkNow(chunkX, chunkZ) != null;
            }

            @Override
            public boolean sectionEmpty(int chunkX, int sectionY, int chunkZ) {
                net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) return true;
                int index = sectionY - minSection;
                var sections = chunk.getSections();
                return index < 0 || index >= sections.length || sections[index].hasOnlyAir();
            }

            @Override
            public boolean ground(BlockPos pos) {
                return pos.getY() <= groundAtOrBelow && isGround(level.getBlockState(pos));
            }

            @Override
            public boolean air(BlockPos pos) {
                return level.getBlockState(pos).isAir();
            }
        };
    }

    private static Iterator<BlockPos> concat(List<Iterator<BlockPos>> parts) {
        return new Iterator<>() {
            private int i;

            @Override
            public boolean hasNext() {
                while (i < parts.size()) {
                    if (parts.get(i).hasNext()) return true;
                    i++;
                }
                return false;
            }

            @Override
            public BlockPos next() {
                if (!hasNext()) throw new java.util.NoSuchElementException();
                return parts.get(i).next();
            }
        };
    }

    /**
     * Natural ground the raze shape stops at and keeps: dirt and grass, the base stones, sand,
     * terracotta, nylium, gravel, bedrock, and liquids. A building made of plain stone reads as
     * ground too - a known limit of telling a building from the land by its blocks.
     */
    static boolean isGround(BlockState state) {
        return state.is(net.minecraft.tags.BlockTags.DIRT)
                || state.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD)
                || state.is(net.minecraft.tags.BlockTags.BASE_STONE_NETHER)
                || state.is(net.minecraft.tags.BlockTags.SAND)
                || state.is(net.minecraft.tags.BlockTags.TERRACOTTA)
                || state.is(net.minecraft.tags.BlockTags.NYLIUM)
                || state.is(Blocks.GRAVEL) || state.is(Blocks.BEDROCK)
                || state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock;
    }

    private static void say(ServerPlayer caster, String message) {
        caster.displayClientMessage(net.minecraft.network.chat.Component.literal(message), false);
    }

    private static boolean erasableBlock(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!HakaiAreaRules.erasable(state.getDestroySpeed(level, pos), state.isAir(),
                state.is(Blocks.BEDROCK), XenoServerConfig.hakaiBlocksUnbreakable)) {
            return false;
        }
        // "no othercloud erase" (2026-09-29): the Otherworld cloud and any other listed block.
        return !HakaiAreaRules.listed(
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),
                XenoServerConfig.hakaiSparedBlocks);
    }

    /** Inside a piece of a spared (DragonMineZ) structure, by vanilla's own structure records. */
    private static boolean inSparedStructure(ServerLevel level, BlockPos pos) {
        if (!XenoServerConfig.hakaiSpareDmzStructures) return false;
        try {
            return level.structureManager().getStructureWithPieceAt(pos, holder -> holder.unwrapKey()
                    .map(key -> HakaiAreaRules.sparesStructure(key.location(), true))
                    .orElse(false)).isValid();
        } catch (RuntimeException e) {
            return true; // cannot tell: spare it
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (JOBS.isEmpty()) return;
        Iterator<Job> it = JOBS.iterator();
        while (it.hasNext()) {
            Job job = it.next();
            if (!tick(job)) {
                clearCracks(job);
                it.remove();
            }
        }
    }

    /** @return false when the job is finished (or has to stop). */
    private static boolean tick(Job job) {
        ServerPlayer caster = job.level.getServer().getPlayerList().getPlayer(job.caster);
        if (caster == null) return false;
        int fadeTicks = XenoServerConfig.hakaiBlockFadeTicks;
        if (job.age++ % VEIL_INTERVAL == 0) {
            // The veil stays over the sphere until the last block is gone.
            // Sent as far as the sphere reaches: its anchor sits a radius below the aim point,
            // which for a big Hakai was past the usual 64-block range ("not always running").
            net.bullettrain.xenopixelsmod.fx.effek.XenoEffects.playWide(job.level,
                    net.bullettrain.xenopixelsmod.fx.effek.EffectSlot.HAKAI_CHANNEL,
                    job.centre.subtract(0.0, job.radius, 0.0), new Vec3(0, 1, 0),
                    net.bullettrain.xenopixelsmod.combat.fx.HakaiEffectRules.areaScale(job.radius,
                            XenoServerConfig.hakaiAreaFxScale), job.radius * 2.0);
        }
        // Pull the next positions from the lazy scan: at most SCAN_BUDGET looked at, at most
        // hakaiBlocksPerTick started, never past the limit of real blocks.
        int scanned = 0;
        int started = 0;
        while (started < XenoServerConfig.hakaiBlocksPerTick && scanned < SCAN_BUDGET
                && job.taken < job.limit && job.source.hasNext()) {
            BlockPos p = job.source.next();
            scanned++;
            if (!erasableBlock(job.level, p)) continue;
            job.solid++;
            if (XenoServerConfig.hakaiSpareDmzStructures && inSparedStructure(job.level, p)) {
                job.structure++;
                continue;
            }
            job.fading.add(new Fading(p, nextBreakerId(), new int[]{0}));
            job.taken++;
            started++;
        }
        Iterator<Fading> it = job.fading.iterator();
        while (it.hasNext()) {
            Fading f = it.next();
            int elapsed = ++f.elapsed()[0];
            if (elapsed < fadeTicks) {
                job.level.destroyBlockProgress(f.breakerId(), f.pos(), HakaiAreaRules.crackStage(elapsed, fadeTicks));
                if ((elapsed & 3) == 0) {
                    HakaiFx.blockPuff(job.level, SableHakaiBlocks.worldCentre(job.level, f.pos()), true);
                }
                continue;
            }
            job.level.destroyBlockProgress(f.breakerId(), f.pos(), -1);
            if (erase(job.level, caster, f.pos())) job.erased++;
            else job.refused++;
            it.remove();
        }
        boolean more = (job.taken < job.limit && job.source.hasNext()) || !job.fading.isEmpty();
        if (!more) {
            if (job.erased == 0 && job.refused == 0) {
                say(caster, String.format("\u00a77Hakai: no blocks to erase (%d solid blocks in range, %d in"
                        + " DragonMineZ structures)", job.solid, job.structure));
            } else {
                say(caster, String.format("\u00a7dHakai erased %d blocks\u00a77%s%s", job.erased,
                        job.refused > 0 ? ", " + job.refused + " refused (DMZ protection or ki-griefing gamerules,"
                                + " YAWP, plot, claim, game mode, spawn protection)" : "",
                        job.structure > 0 ? ", " + job.structure + " in DragonMineZ structures spared" : ""));
            }
        }
        return more;
    }

    /** @return false when something refused it (protection, game mode) or it changed underneath. */
    private static boolean erase(ServerLevel level, ServerPlayer caster, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!erasableBlock(level, pos)) return false;
        // The rules a player's own pickaxe obeys: adventure/spectator mode, spawn protection.
        if (caster.blockActionRestricted(level, pos, caster.gameMode.getGameModeForPlayer())) return false;
        if (level.getServer().isUnderSpawnProtection(level, pos, caster)) return false;
        // The same stale-block-entity guard KiDestroyBlockSafetyMixin puts on DMZ's own grief.
        BlockEntity be = level.getBlockEntity(pos);
        if (be != null && !be.getType().isValid(state)) return false;
        // DragonMineZ's own protected areas, exactly as for a ki blast: its ki-griefing gamerules,
        // master structures and WorldGuard - and YAWP regions, which this mod adds to that same
        // check (YawpKiGriefing). 2026-09-29 owner: "never erase protected area of both dmz and yawp".
        try {
            if (!com.dragonminez.common.init.MainGameRules.canKiGrief(level, pos, caster)) return false;
        } catch (RuntimeException | LinkageError e) {
            return false; // cannot tell: spare it
        }
        BlockEvent.BreakEvent permission = new BlockEvent.BreakEvent(level, pos, state, caster);
        NeoForge.EVENT_BUS.post(permission);
        if (permission.isCanceled()) return false;
        try {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        } catch (IllegalStateException ignored) {
            return false;
        }
        HakaiFx.blockPuff(level, SableHakaiBlocks.worldCentre(level, pos), false);
        return true;
    }

    private static int nextBreakerId() {
        int id = nextBreakerId--;
        if (nextBreakerId < -2_000_000_000) nextBreakerId = -1_000_000;
        return id;
    }

    private static void clearCracks(Job job) {
        for (Fading f : job.fading) {
            job.level.destroyBlockProgress(f.breakerId(), f.pos(), -1);
        }
        job.fading.clear();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (Job job : JOBS) clearCracks(job);
        JOBS.clear();
    }
}
