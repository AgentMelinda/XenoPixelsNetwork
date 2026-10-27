package net.bullettrain.xenopixelsmod.space.event;

import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoSpaceConfig;
import net.bullettrain.xenopixelsmod.space.SpaceChartService;
import net.bullettrain.xenopixelsmod.space.SpaceOrbitClock;
import net.bullettrain.xenopixelsmod.space.SpaceTickScheduler;
import net.bullettrain.xenopixelsmod.space.chart.CelestialBody;
import net.bullettrain.xenopixelsmod.space.chart.ChartRegistry;
import net.bullettrain.xenopixelsmod.api.network.AddonNetwork;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-owned lifecycle state and bounded synchronization for chart-star visuals (§23.1 groundwork).
 *
 * <p>Change detection runs as a {@link SpaceTickScheduler.Pass} on the overworld physics system, like the other
 * recurring space work, rather than as another server-tick listener. It compares a compact stage/origin signature
 * first and builds the full stage snapshot only when that changes. Active wave ages are refreshed at this pass's
 * bounded cadence. Snapshot construction is exception-isolated so invalid data cannot break login or the physics pass.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class StellarLifecycleService implements SpaceTickScheduler.Pass {
    public enum StageUpdateResult { UPDATED, UNKNOWN_STAR, PRESERVED_DATA_LOCKED, ENTRY_CAP_REACHED, INVALID_REQUEST }
    private record StarSignature(String id, StellarStage stage, boolean hasWaveOrigin, long stageStartedAtTick) {}
    private record Signature(boolean enabled, boolean spaceEnabled, int rampTicks, int waveDurationTicks, double waveSpeed,
                             List<StarSignature> stars) {
        private Signature { stars = List.copyOf(stars); }

        boolean hasWave(long nowTick) {
            if (!enabled || !spaceEnabled) return false;
            for (StarSignature star : stars)
                if (star.stage() == StellarStage.SUPERNOVA && star.hasWaveOrigin()
                        && StellarBlastWaveMath.activeAt(star.stageStartedAtTick(), nowTick, waveDurationTicks))
                    return true;
            return false;
        }
    }
    private static final StellarLifecycleService PASS = new StellarLifecycleService();
    private static final Map<MinecraftServer, Signature> LAST_SIGNATURE = new IdentityHashMap<>();
    private static final Map<MinecraftServer, StellarSnapshotDeliveryLedger<Signature>> PLAYER_SIGNATURES = new IdentityHashMap<>();
    private static final Map<MinecraftServer, Map<String, StellarOriginRetryPolicy.Retry>> ORIGIN_RETRIES = new IdentityHashMap<>();
    private static final int SYNC_INTERVAL_TICKS = 20;
    private static final long ORIGIN_RETRY_BACKOFF_TICKS = 1_200L;
    /**
     * Last send failure logged, so a persistent failure warns once instead of every pass for every player. Cleared
     * after a fully successful fanout and at server stop, so the same failure recurring later is logged again.
     * Identical failure text from several players in one outage is reported once (the first player named).
     */
    private static volatile String lastSendFailure;

    private StellarLifecycleService() {}

    @Override public String name() { return "stellar-lifecycle"; }

    @Override public int intervalTicks() { return SYNC_INTERVAL_TICKS; }

    @Override public boolean appliesTo(SubLevelPhysicsSystem system) {
        ServerLevel level = system.getLevel();
        return level != null && level == level.getServer().overworld();
    }

    /** Broadcasts only when the id + stage signature changed; the clock and stage ages never trigger a send. */
    @Override public int run(SubLevelPhysicsSystem system, double deltaSeconds) {
        MinecraftServer server = system.getLevel().getServer();
        Signature signature;
        try {
            advanceDueStages(server);
            signature = signature(server);
        } catch (RuntimeException failure) {
            XenoPixelsMod.LOGGER.warn("Stellar lifecycle change check skipped: {}", failure.toString());
            return 0;
        }
        if (signature == null) return 0;
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        List<java.util.UUID> online = players.stream().map(ServerPlayer::getUUID).toList();
        StellarSnapshotDeliveryLedger<Signature> ledger = playerLedger(server);
        ledger.retainOnline(online);
        boolean changed = !signature.equals(LAST_SIGNATURE.get(server));
        boolean pending = changed || ledger.hasPending(online, signature);
        long now = server.overworld().getGameTime();
        if (!pending) {
            if (signature.hasWave(now)) StellarBlastWaveService.syncActiveAll(server);
            return 0;
        }
        var packet = trySnapshot(server);
        if (packet == null) {
            // Stage packet construction and wave packet delivery are independent best-effort paths.
            StellarBlastWaveService.syncAll(server);
            return 0;
        }
        LAST_SIGNATURE.put(server, signature);
        sendToPending(signature, packet, ledger, players);
        if (changed) StellarBlastWaveService.syncAll(server);
        else if (signature.hasWave(now)) StellarBlastWaveService.syncActiveAll(server);
        return packet.stars().size();
    }

    @SubscribeEvent public static void started(ServerStartedEvent event) {
        SpaceTickScheduler.register(PASS);
        reconcilePause(event.getServer());
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        SpaceTickScheduler.unregister(PASS);
        LAST_SIGNATURE.remove(event.getServer());
        PLAYER_SIGNATURES.remove(event.getServer());
        ORIGIN_RETRIES.remove(event.getServer());
        lastSendFailure = null;
    }

    @SubscribeEvent public static void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) send(player);
    }

    @SubscribeEvent public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) send(player);
    }

    @SubscribeEvent public static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        MinecraftServer server = event.getEntity().getServer();
        StellarSnapshotDeliveryLedger<Signature> ledger = server == null ? null : PLAYER_SIGNATURES.get(server);
        if (ledger != null) ledger.remove(event.getEntity().getUUID());
    }

    public static StageUpdateResult setStage(MinecraftServer server, String id, StellarStage stage) {
        if (server == null || id == null || stage == null) return StageUpdateResult.UNKNOWN_STAR;
        ChartRegistry chart = SpaceChartService.state(server).runtime.chart;
        CelestialBody body = chart.byId(id);
        if (body == null || body.kind() != CelestialBody.BodyKind.STAR) return StageUpdateResult.UNKNOWN_STAR;
        StellarLifecycleSavedData data = StellarLifecycleSavedData.get(server);
        if (data.mutationsLocked()) return StageUpdateResult.PRESERVED_DATA_LOCKED;
        if (data.fullFor(id)) return StageUpdateResult.ENTRY_CAP_REACHED;
        long now = server.overworld().getGameTime();
        var prior = data.entry(id);
        StellarStage previous = prior == null ? StellarStage.STABLE : prior.knownStage();
        StellarLifecycleSavedData.Origin origin = stage == StellarStage.SUPERNOVA
                ? currentOrigin(chart, id, now) : null;
        if (stage == StellarStage.SUPERNOVA && origin == null) return StageUpdateResult.INVALID_REQUEST;
        if (!data.setStage(id, stage, now, origin)) return StageUpdateResult.INVALID_REQUEST;
        Map<String, StellarOriginRetryPolicy.Retry> retries = ORIGIN_RETRIES.get(server);
        if (retries != null) retries.remove(id);
        syncAll(server);
        if (XenoSpaceConfig.supernovaEnabled() && StellarWarning.shouldAnnounce(previous, stage))
            server.getPlayerList().broadcastSystemMessage(net.minecraft.network.chat.Component.translatable(
                    announcementKey(stage), body.displayName()), false);
        return StageUpdateResult.UPDATED;
    }

    /**
     * The full snapshot. Throws when the overworld is absent or persisted/config data fails the packet's
     * invariants; recurring and per-player callers go through {@link #trySnapshot} instead.
     */
    public static StellarStageSnapshot snapshot(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) throw new IllegalStateException("overworld unavailable");
        ChartRegistry chart = SpaceChartService.state(server).runtime.chart;
        var data = StellarLifecycleSavedData.get(server);
        long now = overworld.getGameTime();
        List<StellarStageSnapshot.Entry> stars = new ArrayList<>();
        for (int i = 0; i < chart.size() && stars.size() < StellarStageSnapshot.MAX_STARS; i++) {
            CelestialBody body = chart.body(i);
            if (body.kind() != CelestialBody.BodyKind.STAR) continue;
            var saved = data.entry(body.id());
            StellarStage stage = saved == null ? StellarStage.STABLE : saved.knownStage();
            long age = saved == null || stage == null ? 0L : Math.max(0L, now - saved.startedAtTick());
            if (stage == null) stage = StellarStage.STABLE;
            stars.add(new StellarStageSnapshot.Entry(body.id(), body.displayName(), stage, age));
        }
        return new StellarStageSnapshot(XenoSpaceConfig.supernovaEnabled(),
                (int) XenoSpaceConfig.stellarStageRampTicks(), stars);
    }

    /** {@link #snapshot}, or null after logging when it cannot be built. */
    static StellarStageSnapshot trySnapshot(MinecraftServer server) {
        if (server == null) return null;
        try {
            return snapshot(server);
        } catch (RuntimeException failure) {
            XenoPixelsMod.LOGGER.warn("Stellar lifecycle snapshot not sent: {}", failure.toString());
            return null;
        }
    }

    public static void send(ServerPlayer player) {
        if (player == null || player.getServer() == null) return;
        MinecraftServer server = player.getServer();
        var packet = trySnapshot(server);
        if (packet != null) {
            try {
                Signature current = signature(server);
                if (current != null && sendSafely(player, packet)) {
                    playerLedger(server).markDelivered(player.getUUID(), current);
                    LAST_SIGNATURE.put(server, current);
                }
            } catch (RuntimeException failure) {
                XenoPixelsMod.LOGGER.warn("Stellar lifecycle signature not sent to {}: {}",
                        player.getScoreboardName(), failure.toString());
            }
        }
        StellarBlastWaveService.send(player);
    }

    /** Sends to every online player; the broadcast baseline advances only if every send succeeded. */
    public static void syncAll(MinecraftServer server) {
        if (server == null) return;
        reconcilePause(server);
        Signature current;
        try {
            current = signature(server);
        } catch (RuntimeException failure) {
            XenoPixelsMod.LOGGER.warn("Stellar lifecycle signature not captured during sync: {}", failure.toString());
            StellarBlastWaveService.syncAll(server);
            return;
        }
        var packet = trySnapshot(server);
        if (packet == null) {
            StellarBlastWaveService.syncAll(server);
            return;
        }
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        List<java.util.UUID> online = players.stream().map(ServerPlayer::getUUID).toList();
        StellarSnapshotDeliveryLedger<Signature> ledger = playerLedger(server);
        ledger.retainOnline(online);
        LAST_SIGNATURE.put(server, current);
        sendToPending(current, packet, ledger, players);
        StellarBlastWaveService.syncAll(server);
    }

    private static StellarSnapshotDeliveryLedger<Signature> playerLedger(MinecraftServer server) {
        return PLAYER_SIGNATURES.computeIfAbsent(server, ignored -> new StellarSnapshotDeliveryLedger<>());
    }

    /** Sends only to players whose current signature is not known delivered; failure remains a per-player retry. */
    private static boolean sendToPending(Signature signature, StellarStageSnapshot packet,
            StellarSnapshotDeliveryLedger<Signature> ledger, List<ServerPlayer> players) {
        boolean all = true;
        for (ServerPlayer player : players) {
            if (!ledger.needsDelivery(player.getUUID(), signature)) continue;
            if (sendSafely(player, packet)) ledger.markDelivered(player.getUUID(), signature);
            else all = false;
        }
        if (all) lastSendFailure = null;
        return all;
    }

    /**
     * {@link AddonNetwork#sendToPlayer} throws for an unregistered or wrong-direction packet; that must not abort a
     * login, a dimension change or the rest of a fanout.
     */
    static boolean sendSafely(ServerPlayer player, StellarStageSnapshot packet) {
        try {
            AddonNetwork.sendToPlayer(player, packet);
            return true;
        } catch (RuntimeException failure) {
            String message = failure.toString();
            if (!message.equals(lastSendFailure)) {
                lastSendFailure = message;
                XenoPixelsMod.LOGGER.warn("Stellar lifecycle snapshot not delivered to {}: {}",
                        player.getScoreboardName(), message);
            }
            return false;
        }
    }

    /** Applies persisted pause/resume state and advances only explicitly scripted stages. */
    private static void advanceDueStages(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) return;
        boolean enabled = XenoSpaceConfig.supernovaEnabled();
        long now = overworld.getGameTime();
        var data = StellarLifecycleSavedData.get(server);
        if (!data.reconcilePaused(!enabled, now) || !enabled || data.mutationsLocked()) return;
        var chartState = SpaceChartService.state(server).runtime;
        ChartRegistry chart = chartState.chart;
        int chartRevision = chartState.revision;
        Map<String, StellarOriginRetryPolicy.Retry> retries = ORIGIN_RETRIES.computeIfAbsent(server,
                ignored -> new java.util.HashMap<>());
        long rampTicks = XenoSpaceConfig.stellarStageRampTicks();
        for (int i = 0, processed = 0; i < chart.size() && processed < StellarStageSnapshot.MAX_STARS; i++) {
            CelestialBody body = chart.body(i);
            if (body.kind() != CelestialBody.BodyKind.STAR) continue;
            processed++;
            var saved = data.entry(body.id());
            StellarStage stage = saved == null ? null : saved.knownStage();
            if (saved != null && stage != null) {
            StellarStageTimeline.State advanced = StellarStageTimeline.advance(stage, saved.startedAtTick(), now,
                    rampTicks, XenoSpaceConfig.stellarWaveDurationTicks());
                StellarLifecycleSavedData.Origin origin = null;
                if (advanced.stage() != stage && advanced.stage() == StellarStage.SUPERNOVA) {
                    var retry = retries.get(body.id());
                    if (StellarOriginRetryPolicy.shouldAttempt(retry, chartRevision, now)) {
                        origin = currentOrigin(chart, body.id(), now);
                        if (origin == null) {
                            retries.put(body.id(), StellarOriginRetryPolicy.failed(chartRevision, now,
                                    ORIGIN_RETRY_BACKOFF_TICKS));
                            if (retry == null || retry.chartRevision() != chartRevision)
                                XenoPixelsMod.LOGGER.warn("Star {} has no valid supernova origin; retrying after {} game ticks or a chart revision",
                                        body.id(), ORIGIN_RETRY_BACKOFF_TICKS);
                        } else retries.remove(body.id());
                    }
                }
                long transitionTick = advanced.stage() == StellarStage.SUPERNOVA && stage != StellarStage.SUPERNOVA
                        ? now : advanced.startedAtTick();
                boolean stageChanged = advanced.stage() != stage && (advanced.stage() != StellarStage.SUPERNOVA || origin != null)
                        && data.setStage(body.id(), advanced.stage(), transitionTick, origin);
                if (stageChanged) {
                    if (StellarWarning.shouldAnnounce(stage, advanced.stage()))
                        server.getPlayerList().broadcastSystemMessage(net.minecraft.network.chat.Component.translatable(
                                announcementKey(advanced.stage()), body.displayName()), false);
                }
            }
        }
    }

    /** Captures a star's orbital position at the event tick so later orbit evaluations cannot move its wave origin. */
    private static StellarLifecycleSavedData.Origin currentOrigin(ChartRegistry chart, String id, long atTick) {
        if (chart == null || atTick < 0L) return null;
        chart.evaluate(SpaceOrbitClock.seconds(atTick));
        int slot = chart.slotOf(id);
        if (slot < 0 || chart.body(slot).kind() != CelestialBody.BodyKind.STAR) return null;
        try {
            return new StellarLifecycleSavedData.Origin(chart.x(slot), chart.y(slot), chart.z(slot));
        } catch (IllegalArgumentException malformedPosition) {
            return null;
        }
    }

    /** The id + stage signature straight from the chart and saved data, without building packet entries or mutating. */
    private static Signature signature(MinecraftServer server) {
        if (server.overworld() == null) return null;
        ChartRegistry chart = SpaceChartService.state(server).runtime.chart;
        var data = StellarLifecycleSavedData.get(server);
        List<StarSignature> stars = new ArrayList<>();
        for (int i = 0; i < chart.size() && stars.size() < StellarStageSnapshot.MAX_STARS; i++) {
            CelestialBody body = chart.body(i);
            if (body.kind() != CelestialBody.BodyKind.STAR) continue;
            var saved = data.entry(body.id());
            StellarStage stage = saved == null ? null : saved.knownStage();
            stars.add(new StarSignature(body.id(), stage == null ? StellarStage.STABLE : stage,
                    saved != null && saved.origin() != null, saved == null ? 0L : saved.startedAtTick()));
        }
        return new Signature(XenoSpaceConfig.supernovaEnabled(), XenoSpaceConfig.enabled(),
                (int) XenoSpaceConfig.stellarStageRampTicks(), XenoSpaceConfig.stellarWaveDurationTicks(),
                XenoSpaceConfig.stellarWaveSpeedBlocksPerTick(), stars);
    }

    /** Reconciles master-toggle state on startup, admin toggle, and config reload. */
    private static void reconcilePause(MinecraftServer server) {
        if (server == null || server.overworld() == null) return;
        var data = StellarLifecycleSavedData.get(server);
        if (!data.reconcilePaused(!XenoSpaceConfig.supernovaEnabled(), server.overworld().getGameTime()))
            XenoPixelsMod.LOGGER.warn("Stellar lifecycle clock pause state could not be changed; saved data is locked or invalid");
    }

    private static String announcementKey(StellarStage stage) {
        return switch (stage) {
            case DYING -> "message.xenopixelsmod.stellar.critical";
            case SUPERNOVA -> "message.xenopixelsmod.stellar.supernova";
            default -> "message.xenopixelsmod.stellar.unstable";
        };
    }
}
