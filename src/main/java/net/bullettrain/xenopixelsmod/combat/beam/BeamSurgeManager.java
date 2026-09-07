package net.bullettrain.xenopixelsmod.combat.beam;

import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import com.dragonminez.common.init.entities.ki.KiBlastEntity;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Resources;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.combat.fx.CombatFx;
import net.bullettrain.xenopixelsmod.combat.fx.CombatFxKind;
import net.bullettrain.xenopixelsmod.combat.overcharge.KiProjectileApply;
import net.bullettrain.xenopixelsmod.combat.technique.KiGuidance;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.bullettrain.xenopixelsmod.features.progression.CombatSkills;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps a fired ki wave alive and growing while its owner holds the fire key.
 *
 * <p>DragonMineZ bakes a wave's whole life into the moment of release:
 * {@code TechniqueDispatcher.resolvePlayerMaxLifeTicks} gives it {@code 80 * chargeMultiplier}
 * ticks and {@code KiWaveEntity.tick} discards it once {@code tickCount > getMaxLife()}. Nothing
 * afterwards can extend it — which is why a kamehameha evaporates after a few seconds, and why
 * the only beams that last are the ones that happen to meet another beam. A clash is the sole
 * exception, because {@code BeamClash.keepAlive} rolls the death tick forward every tick.
 *
 * <p>This gives the owner that same power directly: keep feeding the beam and it keeps living,
 * growing thicker, hitting harder and reaching further, paid for in ki and stamina.
 *
 * <p><b>Nothing here is a DragonMineZ change.</b> Every value it touches is a public, synced
 * setter on {@code AbstractKiProjectile}, the same way {@code KiOverchargeHandler} already
 * scales a freshly spawned ki entity's size and damage from this mod.
 *
 * <p><b>Block destruction is not widened.</b> A surged beam is bigger and stronger but must never
 * break a block an unsurged one could not, so this deliberately never touches
 * {@code setBlockDestructionEnabled} — whatever the ki-griefing rules decided at spawn stands for
 * the beam's whole life.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class BeamSurgeManager {

    /**
     * Live surge per owner, per level.
     *
     * <p>Keyed by level rather than held in one global map: {@code BeamClashManager} keeps static
     * clash lists while its tick handler fires per level, so on a multi-dimension server every
     * level tick advances every clash. That quirk is not worth reproducing.
     */
    private static final Map<ResourceKeyed, Tracked> STATES = new HashMap<>();

    /** Owners who reported the key down this tick; cleared as the tick is processed. */
    private static final Map<UUID, Long> FEEDING = new HashMap<>();

    /** A feed report older than this many ticks is treated as released. */
    private static final long FEED_GRACE_TICKS = 5L;

    /** How far ahead of the current tick a fed beam's death is pushed. */
    private static final int KEEP_ALIVE_TICKS = 20;

    private BeamSurgeManager() {
    }

    /** Map key: one surge per owner per level. */
    private record ResourceKeyed(String level, UUID owner) {
    }

    /**
     * Client reported the fire key is still held.
     *
     * <p>This is the only thing the client is trusted for. Ownership, resources, growth and every
     * applied value are re-derived server-side; a spoofed packet can at most claim a key is down
     * for a player who has no beam, which does nothing.
     */
    public static void reportFeeding(ServerPlayer player) {
        if (player == null) return;
        FEEDING.put(player.getUUID(), (long) serverTick(player));
    }

    /**
     * Global monotonic server tick, shared by the feed window and its level-tick reader.
     * {@code level.getGameTime()} is per-dimension and non-comparable across a dimension change,
     * so a feed reported in a higher-time dimension read as "still holding" until the new
     * dimension's clock caught up. Defensive for client callers (server null → 0).
     */
    private static int serverTick(ServerPlayer p) {
        if (p == null || p.level() == null) return 0;
        MinecraftServer server = p.level().getServer();
        return server != null ? server.getTickCount() : 0;
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!XenoServerConfig.beamSurgeEnabled) {
            if (!STATES.isEmpty()) STATES.clear();
            return;
        }
        if (FEEDING.isEmpty() && STATES.isEmpty()) return;

        long now = level.getServer().getTickCount();
        String levelId = level.dimension().location().toString();

        for (ServerPlayer player : level.players()) {
            ResourceKeyed key = new ResourceKeyed(levelId, player.getUUID());

            // Two hash lookups before the entity query. Scanning a box this size per player per
            // tick to ask "is anyone beaming" costs the same whether anyone is or not, and almost
            // nobody ever is. A player is only interesting if they just reported the key down or
            // already has surge in flight that still needs to decay.
            if (!FEEDING.containsKey(player.getUUID()) && !STATES.containsKey(key)) continue;

            AbstractKiProjectile beam = findOwnedBeam(level, player);
            if (beam == null) {
                STATES.remove(key);
                continue;
            }

            // A clash already owns this beam's length and life, and returns from tick() before
            // the growth path runs. Surging alongside it would be fighting for the same fields.
            if (beam.isClashLocked()) continue;

            Tracked tracked = STATES.get(key);
            if (tracked == null || !beam.getUUID().equals(tracked.beamId)) {
                // First sight of this beam. Capture what DMZ gave it, so growth scales from the
                // charge-derived values rather than compounding on top of itself.
                tracked = new Tracked(beam.getUUID(), beam.getSize(), beam.getKiDamage(),
                        beam.getKiSpeed());
                STATES.put(key, tracked);
            }
            BeamSurgeState state = tracked.state;
            int mastery = CombatSkills.level(player, CombatSkills.BEAM);

            Long lastFed = FEEDING.get(player.getUUID());
            boolean holding = lastFed != null && now - lastFed <= FEED_GRACE_TICKS;
            boolean fed = holding && spend(player, state, mastery);

            int tierBefore = state.tier();
            state.tick(fed, mastery);
            apply(player, beam, tracked, fed);

            if (state.tier() > tierBefore) {
                // Cue only on a threshold crossing: a continuous effect scaled by surge is a
                // slider the player stops noticing, where a beat reads as the beam stepping up.
                CombatFx.cue(level, beam.position(), CombatFxKind.IMPACT_HEAVY,
                        0.4f + 0.2f * state.tier());
            }
        }

        FEEDING.entrySet().removeIf(entry -> now - entry.getValue() > FEED_GRACE_TICKS);
    }

    /**
     * Charge this tick's sustain to ki and stamina.
     *
     * @return false when the player cannot pay, which stops the feed and lets the beam decay
     */
    private static boolean spend(ServerPlayer player, BeamSurgeState state, int mastery) {
        Resources res = resources(player);
        if (res == null) return true;

        // Cost rises with how far the beam has already grown, so a big beam is a commitment
        // rather than something to hold open indefinitely.
        float scale = 1.0f + (float) state.surge() * XenoServerConfig.beamSurgeCostGrowth;
        float ki = XenoServerConfig.beamSurgeKiPerTick * scale;
        float stamina = XenoServerConfig.beamSurgeStaminaPerTick * scale;

        if (res.getCurrentEnergy() < ki || res.getCurrentStamina() < stamina) return false;
        res.removeEnergy(ki);
        res.removeStamina(stamina);
        return true;
    }

    /** Push the four growth axes onto the live beam. */
    private static void apply(ServerPlayer player, AbstractKiProjectile beam, Tracked tracked,
                              boolean fed) {
        BeamSurgeState state = tracked.state;
        float surge = (float) state.surge();

        // Duration. The rolling keep-alive is the actual fix for the vanishing beam, and it is
        // applied only while fed - release and the existing deadline reasserts itself, so the
        // beam runs out naturally instead of being cut off.
        if (fed) {
            beam.setMaxLife(beam.tickCount + KEEP_ALIVE_TICKS);
        }

        // Scale from the captured baseline every tick rather than multiplying the live value,
        // which would compound 20 times a second into an absurd beam within a couple of seconds.
        float size = tracked.size * (1.0f + surge * XenoServerConfig.beamSurgeSizeGain);
        float damage = tracked.damage * (1.0f + surge * XenoServerConfig.beamSurgeDamageGain);
        float speed = tracked.speed * (1.0f + surge * XenoServerConfig.beamSurgeReachGain);
        KiProjectileApply.size(beam, size);
        KiProjectileApply.damage(beam, damage);
        // Speed is length growth per tick. Restoring it from the baseline also undoes the 0.75
        // decay DMZ applies on every entity hit, which is what otherwise starves a beam that is
        // actually connecting with someone -- so this one is rewritten whenever it differs at all,
        // rather than being held to an epsilon.
        KiProjectileApply.speed(beam, speed);

        if (fed) {
            KiGuidance.aimAnchoredBeam(player, beam);
        }

        if (fed && state.sustainedTicks() % MASTERY_TICKS == 0) {
            CombatSkills.awardBeamProgress(player);
        }
    }

    /** Ticks of unbroken sustain per unit of mastery progress. */
    private static final int MASTERY_TICKS = 40;

    /**
     * One tracked beam: its surge, its identity, and the values it had before surge touched it.
     *
     * <p>The baseline lives here rather than in a map keyed by beam id so it is discarded with
     * the owner's entry when the beam ends — a separate map would accumulate an entry per beam
     * ever fired for the lifetime of the server.
     */
    private static final class Tracked {
        private final BeamSurgeState state = new BeamSurgeState();
        private final UUID beamId;
        private final float size;
        private final float damage;
        private final float speed;

        private Tracked(UUID beamId, float size, float damage, float speed) {
            this.beamId = beamId;
            this.size = size;
            this.damage = damage;
            this.speed = speed;
        }
    }

    /**
     * The player's own firing beam, or null.
     *
     * <p>Scans {@link AbstractKiProjectile} rather than {@code KiWaveEntity}: DMZ's
     * {@code TechniqueDispatcher} spawns a wave for WAVE techniques but a {@code KiLaserEntity} for
     * LASER and BEAM ones, so a wave-only scan silently excluded every laser and beam from surging.
     * {@code isFiring()} lives on the shared base, so one scan covers both.
     *
     * <p><b>Picks the youngest match, not the first.</b> A player can have more than one owned
     * beam alive at once — an old one that has not yet hit its natural {@code explodeAndDie} sitting
     * near a freshly fired one, since both can be within {@link XenoServerConfig#beamSurgeSearchRadius}
     * of the player at once. {@code getEntitiesOfClass} has no defined ordering, so returning its
     * first match could surge the stale beam instead of the one the player just fired: the moment
     * that happens, {@link #apply} pushes its baseline back to {@code tracked.beamId} and rolls its
     * death forward via {@code setMaxLife(tickCount + KEEP_ALIVE_TICKS)}, which both revives it
     * (reads as the old attack "respawning") and keeps it from ever expiring on schedule for as
     * long as the mistake keeps recurring. Preferring the lowest {@code tickCount} — the beam that
     * has existed for the least time — always resolves to the one just fired.
     */
    private static AbstractKiProjectile findOwnedBeam(ServerLevel level, ServerPlayer player) {
        // A beam is anchored at its origin and grows outward, so it stays near its owner - a
        // modest box around the player finds it without scanning the level.
        AABB box = player.getBoundingBox().inflate(XenoServerConfig.beamSurgeSearchRadius);
        AbstractKiProjectile youngest = null;
        for (AbstractKiProjectile beam : level.getEntitiesOfClass(AbstractKiProjectile.class, box,
                BeamSurgeManager::surgeable)) {
            // UUID identity, the same test the client's ownsFiringWave uses. getOwner() resolves
            // lazily from a stored UUID and can hand back a stale or null reference across a
            // respawn or dimension change, which silently stops the surge applying.
            if (!beam.isOwner(player)) continue;
            if (youngest == null || beam.tickCount < youngest.tickCount) youngest = beam;
        }
        return youngest;
    }

    /** Alive, firing, and a sustained beam rather than a thrown ki ball, which is not this feature. */
    private static boolean surgeable(AbstractKiProjectile candidate) {
        return candidate.isAlive() && candidate.isFiring() && !(candidate instanceof KiBlastEntity);
    }

    private static Resources resources(ServerPlayer player) {
        try {
            return StatsProvider.get(StatsCapability.INSTANCE, player)
                    .map(data -> data.getResources()).orElse(null);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Forget one player's surge.
     *
     * <p>{@link #onLevelTick} only removes a {@code STATES} entry while walking {@code level
     * .players()}, so a player who disconnects mid-beam is never visited again and their entry would
     * sit in the map for the lifetime of the server. Keyed by level id and UUID, so this clears the
     * player out of every dimension they might have left one in.
     */
    public static void forget(UUID playerId) {
        if (playerId == null) return;
        STATES.keySet().removeIf(key -> key.owner().equals(playerId));
        FEEDING.remove(playerId);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            forget(player.getUUID());
        }
    }

    /**
     * A player who changes dimension mid-beam is no longer in the old level's {@code players()},
     * so {@link #onLevelTick} never visits their old-dimension {@code STATES} entry again and it
     * would leak until logout. Their beam stays in the old level and runs out on its own, so it is
     * correct to drop the surge tracking entirely.
     */
    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            forget(player.getUUID());
        }
    }

    /** Drop everything: nothing here outlives the server it was collected on. */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        clear();
    }

    /** Drop everything for a level that is unloading. */
    public static void clear() {
        STATES.clear();
        FEEDING.clear();
    }
}
